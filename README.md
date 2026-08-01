# The Unseen Architecture

A psychological horror layer for Minecraft **1.21.1** (Fabric).

Minecraft's hostile mobs stopped being frightening because they are predictable and killable. This
mod removes them and replaces them with one thing that is neither: a Stalker that hunts by sound,
punishes you for looking at it, and is taken away from you before you can get used to it.

## Before the first dev run

Two dependencies are used from `libs/` rather than a maven coordinate, and are **not** in this repo.
Drop them in yourself:

| File | Where |
|---|---|
| `TerraBlender-fabric-1.21.1-4.1.0.8.jar` | https://modrinth.com/mod/terrablender |
| `ctov-3.6.3-fabric.jar` | https://modrinth.com/mod/ct-overhaul-village |

Take the **Fabric** build of each, and check the filename says so. Modrinth's maven keys on version
number alone, and both of these ship the same version number for Fabric and NeoForge, so the plain
gradle coordinate silently resolves to the NeoForge jar — which loads without complaint and then does
nothing. That failure is invisible unless you grep the loaded-mod list, which is how it was found.

Without these two jars the dev runtime will not have the biome or the villages. The published pack is
unaffected: `tools/build_mrpack.py` resolves both from Modrinth filtered by loader, and redistributes
neither.

## The systems

**Director AI** (`TensionManager` / `PhaseMachine`) — tracks hidden stress per player and moves
through BUILD_UP → PEAK → RELEASE. The load-bearing rule is that a PEAK *cannot* outlast
`peakMaxTicks`: when it expires the Stalker is despawned whether or not you ever saw it. A monster
that is always present stops being a monster and becomes scenery, so the Director spends your
attention like a budget instead of letting it drain to zero.

**Sanity** (`SanityManager`) — draining when the Stalker is inside a 30° cone of your crosshair *and*
actually visible, so walls protect you. Low sanity brings nausea, then darkness. It recovers when
you are in light, alone, and undamaged — which means lighting your world is the defence, and that is
already the game's core loop.

**The Stalker** (`StalkerEntity`) — effectively unkillable, so weapons are not an answer. It hears
you rather than sees you, opens doors, and bleeds constantly. The body is flesh and bone: an open
ribcage you can see through, an unhinged jaw, one arm skinned down to bone and longer than the other,
exposed vertebrae. It leaves a blood trail, which doubles as a mechanic — a trail you can read after it
has gone, and a slow drip that gives away something standing still nearby.

**Fog** (`BackgroundRendererMixin`) — the world closes in during PEAK and as sanity falls.

**Heartbeat** (`HeartbeatManager`) — rises as the Stalker closes. Since looking at it is punished, the
heartbeat is usually the *only* information the player gets: distance and nothing else, no direction,
no confirmation. Non-positional on purpose — it is in the player's head, so the wall they are hiding
behind must not muffle it.

**Scenery corruption** (`SceneryManipulator`) — below 40 sanity the world quietly goes wrong: stone
turns to cobble, bricks crack, torches burn soul-blue. Client-side, sparse, capped, and applied only
*outside* the player's view, so corruption is something they turn around and discover rather than watch
happen. Every change records what it replaced and is reverted on recovery, on walking away, and on
disconnect — the server is never told, so no save can be corrupted by it.

**Stare punishment** (`SanityManager`) — hold the Stalker in view too long and it is simply gone, at a
heavy sanity cost. This is what keeps it a glimpse instead of a model the player can stand and study.

**Doors** (`LongDoorInteractGoal`) — the Stalker paths through doors and leaves them standing open. An
open door you did not open is better evidence than any sighting: it is proof, it is behind you, and it
arrived in silence. Flip the goal's flag to `true` if you would rather it closed them behind itself.

**Dread drone** (`HeartbeatManager#tickDrone`) — a low, pitched-down cave drone under a PEAK phase, so
the Director's escalation is felt rather than announced. Non-positional, like the heartbeat: it is dread,
not a thing in the room. Re-triggered on a long interval rather than held as a looping instance, because
a one-shot cannot leak a stuck loop across a world transition.

**Hallucinations** (`StalkerEntity#isPhantom`) — below 45 sanity you begin seeing Stalkers that are not
there. A phantom is identical to look at, deaf, harmless, stands still and twitches, stares back, and is
simply *gone* if you get within nine blocks. It costs you sanity to look at, exactly like the real thing.
The purpose is to poison the player's evidence: once a phantom has vanished on them once, every later
sighting is ambiguous, and confirming it means walking toward it. Phantoms are driven by sanity rather
than by the Director's phase, so a deteriorating player sees things even during a RELEASE — the calm
stops being trustworthy without the Director having to break its own promise.

**Dread shader** (`DreadShader`) — a post-process effect fading in below 55 sanity: chromatic
separation worse at the edges of vision, a bleed toward sickly grey, a closing vignette and a slow
breathing pulse. Restrained on purpose — the screen should feel wrong, not announce that a filter is
running.

**Portals** (`HollowPortal`, `HollowPortalBlock`) — ways into the Hollow open on their own near a player
who is already frightened (stress past a threshold), cut into cave walls and rock faces at the player's
own depth rather than standing free in the open. You find one by turning around in a tunnel you have
already walked. They are permanent, so
a world accumulates them. You can also build one yourself: a 2x3 dark oak log frame, lit with flint and
steel. Stepping through builds a return frame on the far side — a one-way trip is a softlock, not a scare.
Coordinates map one-to-one between worlds, so being lost in the Hollow and lost at home are the same
problem. The portal emits no light: it is an absence, something you walk into rather than see coming.

**Hollow Taint** (`HollowTaintBlock`) — bleached grey rot that leaks out of every portal and slowly eats
the rock around it. Standing on it costs sanity: it is a piece of the Hollow sitting in your world.
Spread is bounded *by construction* rather than by a radius check — each block carries a `vigour`, a
portal seeds it at maximum, and every block it infects gets one less until it stops. So it always dies
out on its own, can never creep across a world while you are away, and needs no origin tracking, tick
scheduler or persistent bookkeeping: the bound lives in the blockstate.

**The Hollow** (`data/unseen/dimension/`) — a sunless dead forest, reached through a portal or with
`/unseen hollow`.
`has_skylight: false` and `ambient_light: 0.0` mean the surface is as dark as a cave, so the Director
treats the entire dimension as threatening and the Stalker is never off duty there. Defined purely as
datapack JSON: a dimension needs no code and no library, whereas injecting a biome into the *overworld*
would have meant a hard dependency on TerraBlender. It is also the better horror — there is no daylight
to run to.

**The mansion** (`MansionPlan`, `MansionBuilder`) — a 41x41 procedural manor: basement, two floors,
25 rooms, cobwebs, bookshelves, viscera, and wardrobes and beds scattered as hiding places so the
mechanic is available in a panic. Built with `/unseen mansion` rather than generated by worldgen, because
authoring a horror world means placing the set deliberately. Most openings get a real door, since the
Stalker opens doors and leaves them standing open. Lit far too sparsely to be safe, on purpose.

Layout lives in `MansionPlan`, which has no Minecraft imports and carves doorways along a randomised
spanning tree — every room is reachable *by construction*, not by luck. Its self-check proves that over
500 seeds, because a sealed room is the classic procgen bug and the one least likely to be noticed: you
cannot see the room you cannot get into.

**Hiding** (`Hiding`, `WardrobeBlock`, `SeatEntity`) — climb into a wardrobe, or sneak-use a bed to get
under it. There is no invulnerability and no timer that saves you: hiding works only because the
Stalker hunts noise, and a hidden player emits no vibration it will accept. Step out while it is still
searching and you are exactly as exposed as before. Safe but blind, and having to decide when to leave,
is the whole mechanic.

## Four places this deviates from a naive reading of the design

**Hearing is not custom.** The Stalker plugs into the same vanilla vibration graph the Warden uses,
so block-breaking, sprinting, chest-opening and ~40 other signals arrive already occluded by terrain.
Writing a bespoke noise system would have been more code and worse physics.

**There is no networking layer.** Fabric's Data Attachment API syncs the player's state to their own
client on its own (`syncWith(..., targetOnly())`), so there is no payload record, no registration and
no receiver.

**The shader ships into the `minecraft` namespace.** Minecraft 1.21.1 resolves post-process *programs*
with `Identifier.ofVanilla`, so a custom namespace silently cannot work. The chain lives at
`assets/unseen/shaders/post/dread.json`, but the program and GLSL must sit in
`assets/minecraft/shaders/program/` — hence the `unseen_dread` prefix, to avoid colliding with other
mods doing the same thing.

**In Control! is not a dependency.** It has no Fabric build — it is Forge/NeoForge only. Vanilla
hostile spawning is suppressed by a single mixin on `SpawnHelper#spawnEntitiesInChunk` instead, which
is configurable via `suppressVanillaHostiles` and does not lock the mod to another project's release
schedule.

## Companion mods

Both suggested, neither required.

**Sound Physics Remastered** hooks Minecraft's sound engine below this mod, so it needs no integration
code — only a discipline. Every Stalker sound is emitted through `Entity#playSound`, which is
positional, so occlusion and reverb come for free and footsteps genuinely echo down a tunnel. The rule
that preserves it: **never play Stalker audio as a global or UI sound.** A global sound bypasses
attenuation entirely and Sound Physics can do nothing with it.

**Immersive Portals** is wired in properly: when it is installed, lighting a Hollow portal also spawns a
real see-through IP portal linked to the matching coordinates in the other world, both ways, and the
mod's own dwell-teleport stands down so the two never both fire. Without it, the built-in teleport takes
over and nothing is lost but the view.

It is a *soft* dependency — compiled against, never required — and the split between
`ImmersivePortalsBridge` and `ImmersivePortalsLink` is what makes that safe. The JVM verifier resolves
the types a class references when it links that class, so a guard sitting in the same class as the API
calls throws `NoClassDefFoundError` the moment it is reached on an install without the mod: the guard
runs too late to protect anything. The bridge therefore names no Immersive Portals type at all. Both
paths are tested — with the mod present a real IP portal entity spawns, and with it absent the native
teleport still moves entities between worlds.

**MAmbience** adds environmental ambience without dramatic pacing of its own, so it layers texture
underneath the Director rather than arguing with it.

### Deliberately not paired

**Tense Ambience**, **Server-Side Horror** and **Reactive Music** each run their own tension timer.
Stacked on top of the Director they play ominous audio during the exact window a forced RELEASE is
trying to convince the player they are safe, which is the one thing this design cannot tolerate. Use
them *instead of* the Director's pacing, not alongside it.

## Sounds

Six events — `stalker.breath`, `stalker.step`, `stalker.notice`, `stalker.lunge`, `heartbeat` and
`dread.drone` — in `assets/unseen/sounds.json`. They point at pitched-down vanilla Warden and cave audio
rather than shipping new files, so the mod is audible immediately. The event ids are stable: dropping real `.ogg` files into
`assets/unseen/sounds/` later is a resource change, not a code change.

`notice` fires only when the Stalker picks up a *cold* trail, so it does not chirp at every footstep
you take.

## Building

Requires JDK 21. `gradle.properties` already points Gradle at
`/opt/homebrew/opt/openjdk@21`; change `org.gradle.java.home` if yours lives elsewhere.

```
./gradlew build          # jar in build/libs/
./gradlew runClient
./gradlew runServer
```

Check the pure logic without launching the game:

```
java -ea -cp build/classes/java/main com.unseen.PhaseMachine    # Director pacing
java -ea -cp build/classes/java/main com.unseen.mansion.MansionPlan   # mansion connectivity, 500 seeds
```

Regenerate the Stalker's texture (the layout matches UVs in `stalker.geo.json` — change one, change both):

```
python3 tools/gen_stalker_texture.py
```

## Modpack

`dist/unseen-architecture-0.1.0.mrpack` is a Modrinth modpack, rebuildable with:

```
./gradlew build
python3 tools/build_mrpack.py
```

Modrinth packs may not redistribute other people's jars, so Fabric API, GeckoLib, Sound Physics
Remastered and MAmbience are referenced by URL and hash in `modrinth.index.json` and fetched at install
time. This mod is not on Modrinth, so it ships inside `overrides/mods/` — the sanctioned route for a
pack's own content — alongside `overrides/config/unseen.json` so the pack arrives pre-tuned.

Sound Physics Remastered and MAmbience are marked `server: unsupported`: they change what you hear, not
what the server simulates, so a dedicated server will not try to install them.

To publish: create a project on Modrinth with type **Modpack**, upload the `.mrpack`, and tag it Fabric
+ 1.21.1. The build script refuses to write a pack if any dependency has no 1.21.1 Fabric build, so a
green run means the dependency set is installable.

If you later publish the mod as its own Modrinth project, move it out of `overrides/` into
`modrinth.index.json` — updates then flow without rebuilding the pack.

## Configuration

Written to `config/unseen.json` on first run. Every number that decides how frightening this is lives
there, because none of them are knowable in advance — tuning is a file edit and a world reload.

`peakMaxTicks` is the one to reach for first: too high and players acclimatise to the Stalker, too low
and it never gets to be scary.

## Debug commands

Requires permission level 2.

```
/unseen status              current sanity, stress, phase, whether the location is threatening
/unseen sanity <0-100>
/unseen stress <0-100>
/unseen phase <BUILD_UP|PEAK|RELEASE>
/unseen spawn               force a Stalker into a valid dark spot nearby
/unseen phantom             force a hallucination out of sight nearby
/unseen mansion [<x y z>]   build a mansion (centred on you, or at a corner from console)
/unseen hollow              travel to the Hollow, or back out of it
/unseen portal [<x y z>]    force a way in to open nearby, or build one at a position
/unseen clear               remove nearby Stalkers
```

Useful pairs while testing: `/unseen sanity 10` to force the shader and scenery corruption on, then
`/unseen sanity 100` to watch everything revert.

## Known state

The Stalker's model is hand-authored placeholder geometry — an over-jointed humanoid whose skeleton is
built so the twitch animation reads as wrong. It animates correctly and looks like programmer art.
Open `assets/unseen/geo/entity/stalker.geo.json` in Blockbench to sculpt it; keep the bone names and
the animations keep working.

The Stalker's model is hand-authored and procedurally textured — anatomically deliberate but still
programmer art. Open the `.geo.json` in Blockbench to sculpt it; the bone names are what the animations
bind to, so keep them and the animations keep working.

Not built: overworld biome injection (would need TerraBlender), and natural worldgen placement of the
mansion (`MansionBuilder` is already the hard part — a `Structure` registration could call it later).
