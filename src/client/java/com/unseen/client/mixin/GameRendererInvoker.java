package com.unseen.client.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** {@code loadPostProcessor} is private in vanilla; this is the only way to drive our own effect. */
@Mixin(GameRenderer.class)
public interface GameRendererInvoker {
	@Invoker("loadPostProcessor")
	void unseen$loadPostProcessor(Identifier id);
}
