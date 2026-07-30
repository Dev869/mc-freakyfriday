#version 150

// The Unseen Architecture — low-sanity screen effect.
// Chromatic separation at the edges, a sickly desaturation, a closing vignette and a slow breathing
// pulse. Deliberately restrained: the goal is a screen that feels subtly wrong rather than one that
// announces a filter is running.

uniform sampler2D DiffuseSampler;

in vec2 texCoord;

uniform float DreadAmount;
uniform float DreadTime;

out vec4 fragColor;

void main() {
    float d = clamp(DreadAmount, 0.0, 1.0);
    vec2 uv = texCoord;
    vec2 fromCenter = uv - vec2(0.5);
    float dist = length(fromCenter);

    // Slow breath, so the effect never sits perfectly still.
    float pulse = 0.5 + 0.5 * sin(DreadTime * 1.7);
    float amount = d * (0.78 + 0.22 * pulse);

    // Chromatic separation, worse toward the edges of vision.
    vec2 offset = fromCenter * amount * 0.018 * (0.35 + dist);
    float r = texture(DiffuseSampler, uv + offset).r;
    float g = texture(DiffuseSampler, uv).g;
    float b = texture(DiffuseSampler, uv - offset).b;
    vec3 color = vec3(r, g, b);

    // Bleed toward a sickly grey rather than a neutral one.
    float luma = dot(color, vec3(0.299, 0.587, 0.114));
    vec3 sick = vec3(luma * 1.04, luma * 0.94, luma * 0.90);
    color = mix(color, sick, amount * 0.85);

    // Vignette closing in.
    float vignette = smoothstep(0.88, 0.22, dist);
    color *= mix(1.0, vignette, amount * 0.9);

    // Overall dimming.
    color *= mix(1.0, 0.82, amount);

    fragColor = vec4(color, 1.0);
}
