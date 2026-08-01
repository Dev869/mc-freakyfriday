# The Unseen Architecture — working log & queue

Minecraft **1.21.1 Fabric** horror modpack. Loop runs every 30 min; every action gets an entry below.

## The backstory (do not stray from this)

You spawn somewhere beautiful. A storybook meadow — flowers, rabbits, sunlight, cottages. It is the
best-looking world you have played in.

Then you find a portal. Cut into a cave wall, or hidden under a structure, ringed in bleached grey rot
that is slowly eating the stone around it. Through it is **the Hollow**: the same coordinates, the same
world, with the sun taken out. Nothing there is safe, ever.

Something lives in it. It hunts by sound, not sight, and looking at it costs you your mind. It cannot be
killed. When it catches you it takes your skin — hangs your face in its lair as a trophy, then puts it on
and walks back out wearing you, twitching and bleeding, to find your friends.

The beauty is the setup. The horror is the fall.

### The meadow does not end

You cannot walk out of the fairytale. Every land biome in the overworld is the Storybook Meadow, in
every direction, forever. There is no desert on the horizon to break the spell and no badlands to
remind you this is Minecraft.

The point is that the only way out is *down*. If the horizon can never give you anything else, then
the portal in the cellar is the only door in the world, and finding it is the whole first act.

Rivers, oceans and beaches stay. "Endless meadow" means every land biome, not literally one biome
everywhere: an ocean rendered as grass looks broken, and the footbridges need streams to cross.
Cave biomes stay too, since a portal cut into a lush-cave wall is worth more than one in plain stone.

### The rot has a source, and you can see it from the ground

Portals used to be sealed inside cottage cellars and well shafts, one in three, with nothing anywhere
pointing at them. In practice nobody found one, which means the first act had no second act.

The creepy mansion is now the epicentre. Cracks of Hollow Taint split the ground around it, thicker and
more frequent the closer you get, each one running back toward the house. Caves within its reach hold
portals cut into their walls. Follow the cracks, go down, find the door.

The corruption is a gradient, not a boundary: it thins with distance instead of stopping at a line, so
there is nowhere on the ground where the world obviously switches from safe to not.

### Living things belong to the meadow, and nowhere else

Peaceful mobs live on the meadow surface and nowhere else. Rabbits, sheep and bees, and the noise of
a place being alive, stop where the sunlight does. Underground is empty. The Hollow is empty.

Since the meadow is now endless, "outside the meadow" no longer means over the hill; it means *below*
and *through*. That is the version that matters anyway: the surface is alive, and everything you reach
by descending is not. The first thing that moves down there is the Stalker.

In daylight: peaceful only. The Stalker will not spawn in a lit place, so a sunlit meadow is safe by
construction rather than by a special case. Night on the surface is not safe and never was — that is
the Director's oldest rule and the endless meadow does not change it.

## Queue

Ordered. Top item is next.

### Needs eyes in-world (Devin)

Built and compiling, but neither can be verified headlessly — both need a real player ticking.

- [ ] **Impersonator skin** — does it render with the victim's actual face? Most likely thing to be
      subtly wrong. `/unseen skin` spawns one wearing yours.
- [ ] **The drag ending in a real lair** — in the Hollow, near a generated lair, `/unseen drag` should
      haul you into the trophy room and hang your face among the others. Mount placement is now verified
      headlessly (wall, eye level, among the existing skulls); what needs eyes is whether the whole
      sequence *reads* — the haul, the arrival, the moment you see the wall.
- [ ] **The drag** — `/unseen drag` spawns a Stalker, yanks you off your feet and hauls you to its lair.
      Watch for: does it path there, does the camera read as being dragged on your back, does the head
      end up mounted on a wall, does the Impersonator walk out afterwards.
- [ ] **Do the cottage and well look good?** `/unseen cottage` and `/unseen well` build one where you
      stand. Geometry is checked; whether they read as *storybook* is a judgement call I cannot make
      headlessly.

## Done

- Director AI (`PhaseMachine`, forced RELEASE), sanity, look-punishment, stare-vanish
- Stalker: vibration hearing, doors, gore anatomy, blood trail, unkillable
- Hallucinations (phantoms), heartbeat, PEAK drone, dread post-process shader
- Scenery corruption, Wardrobe + bed hiding, spawn suppression
- Mansion generator (spanning-tree layout, self-checked over 500 seeds)
- The Hollow dimension + portals in cave walls + spreading Hollow Taint
- Immersive Portals soft integration (both paths tested)
- Skin theft: player-head trophy with real profile + persistent Impersonator
- Audit: 5 defects found and fixed (taint scope, frame spam, map leak, portal dedup, dead config)
- Storybook Meadow generates — verified with `/locate biome`, 32 blocks from spawn
- Corpse-drag: the Stalker hauls you to its lair alive, then takes the face there
- Storybook cottages with hidden cellars, one in three holding a way into the Hollow
- Wishing wells with a chamber at the bottom of the shaft, one in three holding a way through
- ChoiceTheorem's Overhauled Village in the pack, with the meadow tagged so villages reach it
- Pack ships every mod `fabric.mod.json` requires — it did not before
- The lair: a trophy room in the Hollow, faces on every wall, built off caves so it can be found
- The drag ends at a real lair — positions recorded at worldgen, persisted, looked up when it takes you
- Your face hangs on the lair wall at eye level, in a gap in the existing row of trophies
- The thing wearing your face leaves the Hollow and surfaces in the overworld where you were taken
- Portals hidden in structures come pre-rotted, so the tell is there before you understand it
- Cottages have a fenced vegetable plot, a path and flowers that actually place
- Flower arches and plank footbridges, so the meadow reads as tended rather than merely pretty
- Portals built by worldgen actually take you somewhere with Immersive Portals installed
- The overworld is one endless Storybook Meadow; oceans, rivers, beaches and caves survive it
- The dark is empty: no bats, glow squid or axolotls underground, and nothing spawns in the Hollow
- All thirteen /code-review findings closed
- Tectonic for terrain shape, and a proper creepy mansion that generates in the meadow
- Cracks spreading from the mansion, widening into open gashes you look down into the Hollow through
- Portals always work: Immersive Portals gets first refusal, we carry anyone it leaves standing
- Portals cut into cave walls within the mansion's reach
- Ruins across the whole world, one in three holding a portal, so a way through is always within a walk
- Dead-end passages bored off caves, with a way into the Hollow at the end
- The mansion generates in the Hollow instead of only existing behind a debug command
- The pack builds its own jar, so it cannot ship code that is not in the tree

## Mods in the pack

Fabric API · GeckoLib · Sound Physics Remastered · MAmbience · Immersive Portals · TerraBlender ·
Lithostitched · ChoiceTheorem's Overhauled Village · Tectonic · Dungeons and Taverns Woodland Mansion
Overhaul

TerraBlender and ctov are vendored in `libs/` for the dev runtime only — Modrinth's maven keys on version
number alone and both ship the same number for fabric and neoforge, so the plain coordinate silently
resolves to the NeoForge jar. `tools/build_mrpack.py` filters by loader, so the published pack is fine.

**Tectonic** shapes terrain and adds no biomes of its own — it is built to let TerraBlender mods place
theirs on top, which is the only reason it can be here. **Dungeons and Taverns Woodland Mansion
Overhaul** replaces the vanilla woodland mansion rather than adding a structure, so the meadow is
tagged for `woodland_mansion`; with no dark forest left in the world, nothing would have spawned one.

Adding more is fine if compatible with Fabric 1.21.1 and well reviewed. Rejected on purpose: Tense
Ambience, Server-Side Horror, Reactive Music — each runs its own tension timer and would talk over the
Director's forced RELEASE. **Terralith** — ~100 new biomes would make the Storybook Meadow rare, and
spawning in the meadow is the entire opening act. With the meadow now endless this is no longer a
matter of degree: any mod that adds surface biomes is fighting the pack's core rule and is out.

## Log

### 2026-07-31 — loop armed
Scheduled the 30-minute loop (job `2291bfb9`). Wrote this file. Starting on the Storybook biome, since a
broken opening act is the thing that strays furthest from the brief.

### 2026-07-31 — biome bug narrowed to one line
Instrumented `StorybookRegion.addBiomes`. It is never called. That single fact eliminates most of the
search space: the biome JSON, its registration, the registry key, and `Regions.register` all work, so the
fault is entirely on TerraBlender's side of the handshake. Also ruled out Immersive Portals by removing
its whole stack from the dev runtime — no change. Left the logging in place; it costs one line per world
load and it is the fastest signal for whether a fix worked. Stopped there rather than keep digging, and
moved the next hypothesis (TB mixins not applying to the vendored jar) into the queue.

### 2026-07-31 — Storybook biome fixed: the bug was in the test rig, not the mod

`run/server.properties` had `level-type=minecraft:flat`. TerraBlender's first gate in
`LevelUtils.shouldApplyToChunkGenerator` is `instanceof NoiseChunkGenerator`, so on a superflat overworld
it returns before it ever asks a region for its biomes. The nether stayed noise-based, which is exactly why
the log said `Initialized TerraBlender biomes for level stem minecraft:the_nether` and named no other
dimension — the one line that pointed straight at it, sitting in the log the whole time.

Set `level-type=minecraft:normal`. `addBiomes` now runs, claims 982 parameter points, and
`/locate biome unseen:storybook_meadow` finds it 32 blocks from spawn.

Two things worth keeping:

The mod was never broken. Every hypothesis I chased — region weight, the two TerraBlender APIs, Immersive
Portals, vendored-jar mixins — was a hypothesis about *my code*, because I never questioned the harness the
tests ran in. When a system this deep in the stack fails, check what you are testing *on* before checking
what you are testing.

The client save had the same fault. `UNSEEN testworld` was superflat too, so it could never have shown the
biome either. Regenerated it as a normal world with cheats on, so what I verified headlessly is what loads
in-game.

Audited the blast radius while I was here: plains, ocean, desert, jungle, cherry grove and snowy taiga all
still generate at normal distances, so the region claims a slice of the climate space rather than eating the
overworld. Stripped the instrumentation — `/locate` is the real check and it costs nothing to re-run.

### 2026-07-31 — audit of the skin-theft code, and one real defect

`/code-review` is user-triggered and billed, so I cannot launch it — that one is Devin's to run, and the
whole build since the baseline commit is sitting uncommitted for it to pick up. Audited the newest code
myself in the meantime, since `SkinTheft`, `ImpersonatorEntity` and `ImpersonatorRenderer` are the only
files that have never been through a pass.

**Defect: the trophy was the wrong block.** `hangTrophy` placed `Blocks.PLAYER_HEAD` — the floor head —
and `findMount` searched bottom-up for air with a solid block *below*. The comment said "on the wall" and
the story beat is that it *hangs* your face. A head on the ground reads as dropped loot; a head at eye
level on a wall reads as something put there deliberately, which is the entire point of the moment. Now
searches top-down from eye level, prefers a spot with a solid neighbour, and places `PLAYER_WALL_HEAD`
facing out from that wall, keeping the floor head only as a fallback for open ground.

Verified the blockstate is legal headlessly (`setblock … player_wall_head[facing=east]` places and reports
a `minecraft:skull` block entity, so `setOwner` still has something to attach the profile to). The
placement *search* is not verified in-world yet — it needs a real kill, so it rides along with the
Impersonator check already in the queue.

Noted but deliberately not changed: the trophy is hung at the Stalker's position, which is the kill site,
not a lair. That is the corpse-drag queue item, not a separate bug.

### 2026-07-31 — the drag

Implemented the corpse-drag. The queue said this needed a spectator camera state machine; it does not.
A dead player is stuck behind a death screen, so instead the killing blow *does not kill*. If the hit
would finish you, the Stalker takes hold instead: one point of health, no blindness, and you ride it.
Vanilla passenger mechanics carry the camera for free, and `updatePassengerPosition` puts you on the
ground 1.35 blocks behind it rather than on its back — so you are being hauled along on your spine,
looking up at the thing that has you. Watching is the only thing you still get to do, which is the beat.

`removePassenger` is overridden to refuse while the drag is running, so you cannot shrug it off. It
paths to its lair — wherever the Director first spawned it, persisted in NBT — and on arrival puts you
down, finishes you, and hangs the face there. A timeout ends it regardless, because an unreachable lair
must not hold someone forever. Reload mid-drag deliberately does not restore the drag: `dragTicks` is
not persisted, so an interrupted haul lets the victim go rather than resuming against a passenger the
world may no longer have.

The old kill-site theft is kept as the fallback for deaths it did not mean to cause — drowning, a fall
during the chase. The drag is the good version; that is the consolation one.

Audited my own change and fixed two things before logging it. `beginDrag` was setting the victim's
health to 1 and applying effects *before* calling `startRiding`, so a failed grab — already riding
something — would leave them gutted and standing there with nothing holding them. Takes hold first now,
and does nothing at all if that fails. And the finishing blow used `Float.MAX_VALUE`, which gets
multiplied through armour and resistance in the damage pipeline; swapped for a big finite number so an
infinity cannot land in someone's health.

Added `/unseen drag` so this is testable without dying repeatedly — it spawns a Stalker at real distance,
keeps that spawn as the lair, closes the gap and grabs you.

Known and left alone: the lethality check is `health - contactDamage <= 0`, which ignores armour, so a
well-armoured player gets grabbed on a hit they would have survived. Defensible — the Stalker is
unkillable and armour is not meant to be an answer to it — but it is a real behaviour, not an oversight,
and worth a second look if the grab starts firing too often in play.

Compiles, and a Stalker summons and ticks cleanly on a headless server. The drag itself cannot be
verified without a real player, so it goes to Devin with the Impersonator skin check.

### 2026-07-31 — cottages, and a worldgen deadlock that was hiding behind the rarity roll

Built the storybook cottage. Same split as the mansion: `CottagePlan` holds every coordinate and no
Minecraft types, so it runs standalone — 500 seeds asserting the walls have no gaps, the roof covers
every column, there is exactly one door and one way down, the trapdoor opens onto the shaft, the ladder
reaches the floor, and the cellar void is fully shelled so it never opens into raw stone. `CottageFeature`
only decides what block each part is made of. Birch and oak timber, glass panes, a dark oak gable, a
hanging lantern, flowers and a dirt path to the door.

Under the floorboards, in the corner of the room, a rug. Under the rug, a trapdoor. Under that, a stone
cellar — always, in every cottage, because an empty stone room under a flower cottage is already wrong.
One in three has a way into the Hollow cut into the far wall, facing the ladder, so it is the first thing
you see when you turn around at the bottom.

**The real find: calling `HollowPortal.buildFrame` from a feature deadlocks the server.** It takes a
`ServerWorld`, and `ServerWorld.setBlockState` during chunk generation waits for a chunk that is itself
waiting on the feature to finish. Split out `buildFrameBlocks`, which takes a `ModifiableWorld` and writes
through whatever chunk region the caller was handed, with no Immersive Portals entity — spawning entities
mid-generation is the same class of mistake. The portal block handles travel on its own; the see-through
view can be made later.

This one nearly got shipped. At one portal cottage per seventy-two chunks it would have looked like a rare
mystery hang, in someone else's world, days later. It only became obvious because I forced the rarity to
every chunk to get a decisive count — the bug was always there, the odds were just hiding it.

Verified: cottage parts all land where the plan says (lantern, trapdoor, rug, cellar, foundation, floor —
six for six via `execute if block`), portals appear at roughly the stated rate across several placements,
and with rarity forced to 1 a fresh world generates them naturally without hanging. Then restored rarity
to 24 and confirmed a normal run survives chunk generation with the watchdog back on.

Three bad test rigs in two iterations now, so: the dev server is on `level-seed=unseenarch` for
reproducibility, `level-type=minecraft:normal`, and `max-tick-time` back at its default 60000. I disabled
the watchdog to get through the deadlock investigation and put it straight back, because the last three
false leads were all non-default rig settings and I am not leaving a fourth one lying around.

`WORLD_SURFACE_WG` is worldgen-time only and does not track later block edits — building a flat test
platform and expecting the slope check to see it does not work. In real generation the cottage runs at
SURFACE_STRUCTURES, before trees exist, so the check reads clean ground. `/place feature` on
already-decorated chunks reads tree tops as slope and rejects almost everything; that is the command's
limitation, not the feature's.

Added `/unseen cottage [pos]` alongside `/unseen mansion`, and refreshed the client test world.

### 2026-07-31 — wells, and the roof was inverted

Fixed the roof first. I flagged the stair facing as suspect last iteration and it was wrong: stairs
ascend *toward* their facing, and both slopes climb inward to the ridge, so the row on the north edge
has to face south and the row on the south edge north. I had them facing outward, which turns the gable
into a groove — the roof would have read as a dent in the middle of every cottage. Verified both courses
now with `execute if block ... dark_oak_stairs[facing=...]`.

Added the wishing well. Kerb of cobble and mossy cobble with the mouth open, four fence posts, a slab
roof, a lantern hanging in it, and a ladder going nine blocks down into a 5x5 stone chamber. One in
three has a way into the Hollow against the far wall, facing you as you come off the ladder.

Deliberately dry. Water would look better from above, but the beat is climbing down a well in a meadow
full of flowers and finding a room that should not be there, and you cannot climb down a well full of
water.

Caught one defect in my own design before it shipped. The chamber was centred on the shaft, which left
the last four ladder rungs floating in the middle of an open room with nothing behind them — they would
have looked wrong and broken on the first block update. Hung the chamber off the shaft instead so the
well comes down in its corner and the ladder has a wall the whole way. Added an assertion to the
self-check that every ladder inside the chamber has a wall behind it, so it cannot come back.

The plan self-checks the same way the cottage does: deterministic, four posts, roof covers the kerb, the
mouth is open, the shaft and ladder are continuous from the surface to the floor, and the chamber void is
fully shelled so it never opens into raw stone.

The well uses `buildFrameBlocks`, not `buildFrame` — the deadlock fixed last iteration would have come
straight back otherwise, and this is exactly the kind of thing that gets copied without thinking.

Verified headlessly: rim, post, roof, both ladder stretches, the wall backing the ladder, the far corner
of the chamber and its floor all land where the plan says. Both `unseen:cottage` and `unseen:well`
resolve as placed features, and a fresh world generates with both active without hanging or tripping the
watchdog. Added `/unseen well [pos]`, and folded the cottage command into a shared `placeFeature` helper
rather than copying it.

### 2026-07-31 — leaned on a mod instead of hand-building, and found the pack was unshippable

The queue wanted more fairytale structures. Rather than hand-code a third one, went looking for something
well reviewed, which is what the brief asks for.

**Took ChoiceTheorem's Overhauled Village** (9.6M downloads). It replaces the village *buildings* without
touching biome distribution, so the meadow stays exactly as common as it was, and pretty villages are
worth far more to "somewhere you would hate to lose" than another one of my 7x7 huts. Needed
Lithostitched, so that came too.

**Rejected Terralith** despite it being the obvious "make the overworld gorgeous" pick. It adds around a
hundred biomes, which would make the Storybook Meadow rare — and spawning in the meadow *is* the opening
act. A prettier world that buries the one biome the story depends on is a bad trade.

CTOV builds on vanilla's village structures, and those only generate in tagged biomes, so a brand-new
biome gets nothing. Added `unseen:storybook_meadow` to `minecraft:has_structure/village_plains` and to
`is_overworld`. Confirmed `minecraft:village_plains` still exists and generates with ctov loaded, so the
tag is doing real work.

**ctov did not load at first, and it was the TerraBlender trap again.** Modrinth's maven keys only on
version number; ctov ships `3.6.3` for both fabric and neoforge, so the coordinate silently resolved to
the NeoForge jar. Gradle was happy, the build passed, and the mod simply was not there at runtime — no
error anywhere. Caught it only by grepping the loaded-mod list rather than trusting the build. Vendored
the Fabric jar like TerraBlender. This is the second mod to do this; assume it is the rule, not the
exception, and check the mod list every time.

**The real find: the published pack could never have launched.** `fabric.mod.json` requires
`terrablender >=4.1.0`, and `tools/build_mrpack.py` never shipped it — TerraBlender was only ever a
vendored dev-runtime jar. Fabric Loader hard-fails on a missing required dependency, so the `.mrpack` was
not "missing a biome", it was dead on arrival. Added it, then wrote a check that walks every entry in
`fabric.mod.json`'s `depends` and confirms it appears in the built pack, so this cannot silently happen
again.

Verified the pack ships the *Fabric* builds and not the NeoForge ones: both TerraBlender and ctov in the
index are byte-identical to the vendored Fabric jars, and the CDN filenames agree. Given that this exact
collision has now bitten twice, size-matching the shipped file against a known-good jar is worth the ten
seconds.

### 2026-07-31 — the Hollow was empty, so I built the lair

Went to add a flower arch and checked the other side of the portals first. The Hollow had ores, lava
lakes and vanilla monster rooms — and nothing else. The backstory's central payoff, *hangs your face in
its lair as a trophy*, had nowhere to happen. The drag hauls you to "wherever the Stalker spawned",
which is a random dark spot. That is a bigger hole than a missing flower arch, and the queue header says
the backstory is the thing not to stray from, so I built the lair instead.

An 11x11 room buried in the Hollow: deepslate brick walls, bone and soul soil underfoot, a nest of bone
in the middle, Hollow Taint creeping across the floor, and faces along every wall at eye height. Mostly
skeleton skulls — old ones — with roughly one in three a player head, which renders as a person and is
the one that makes the row land. The trophies are evenly spaced on purpose: a random scatter reads as
decoration, a row reads as a collection.

One way in and no other way out. The doorway is never blocked by a trophy, because whatever walks
through would destroy it.

**Caught the worst defect in my own plan before it generated once.** The first version never hollowed
out the interior — it placed a floor, walls, a ceiling and trophies, and would have produced a solid
block of stone with skulls buried inside it. The self-check did not catch it because I had asserted the
floor was complete and the walls had no holes, but never that the room was a *room*. Added the carve and
an assertion that every interior cell is open. This is the same shape of mistake as the mansion Y bug:
the checks proved the parts existed, not that the thing worked.

The other real decision is placement. A sealed room in the middle of the stone is worth nothing, so the
feature searches downward for open air standing on solid ground and builds off that — the doorway opens
onto a cave a player can actually arrive through. If it cannot find one it refuses to build rather than
entombing itself.

Verified in the Hollow: interior carved, nest, wall, ceiling and trophies all land where the plan says,
and a count came back fourteen faces — ten skulls and four player heads, right on the one-in-three. A
fresh 60x60 block region of the Hollow generates with the feature active without hanging or tripping the
watchdog. Added `/unseen lair [pos]`.

Queued the follow-up that makes this pay off: the drag should haul you to a *generated* lair and hang
your face on a wall that already has thirteen others on it. Right now the room and the drag are two good
things that do not know about each other.

### 2026-07-31 — the drag now ends somewhere that has been used before

The lair and the drag existed but did not know about each other. They do now.

The awkward part is that `LairFeature` knows exactly where it just built a lair, but runs on worldgen
threads and must not touch `ServerWorld` — that is the deadlock that already cost an iteration. So the
feature drops a dimension key and a position into a concurrent queue and walks away, and the server
drains that queue on the main thread into a `PersistentState`. Nothing on the worldgen side ever touches
the world. When a Stalker takes hold of someone it asks that registry for the nearest lair within 256
blocks and hauls them there; with none in range it falls back to the dark spot it spawned in, as before.

Verified end to end. Placing two lairs by command in a fresh Hollow left the registry holding **four** —
the two I placed plus two that generated naturally during chunk generation, which is the better result,
because it proves the worldgen path records without reaching for the world. The overworld reported zero,
so the per-dimension split is real. Stopped the server and started it again: still four, so it survives
a restart, which is the entire reason for using `PersistentState` rather than a static map.

Audited my own change and fixed one real defect. The Stalker was choosing its lair on its first tick and
caching it, so one that spawned before any lair was known would settle for an anonymous patch of dark
and never reconsider — permanently, since the value was persisted. It now decides when it takes hold of
someone, which is the only moment the answer matters. That also made the lair's NBT dead, so it is gone;
a drag never survives a reload anyway.

Added a dedupe guard on recording. Being straight about what it is worth: it only catches exactly equal
positions, and the obvious test — running the debug command on the same spot twice — does not exercise
it, because the first lair carves the room and the second placement then finds a different floor. Natural
generation runs once per chunk so it will not produce exact duplicates either. It is a cheap guard
against a list that grows without bound, not something I have seen fire.

One thing left deliberately: the site list is linear and unbounded. At one lair per eighteen Hollow
chunks, heavy exploration might reach a few hundred entries, and the lookup runs once when someone is
grabbed rather than on a tick, so it does not matter yet. If the Hollow ever gets explored at scale this
wants a spatial index.

Added `/unseen lairs <pos>`, which reports how many lairs the current dimension knows about and the
nearest to a position — the same call the Stalker makes, so it is a real check and not a parallel one.

### 2026-07-31 — the face now actually lands on the wall

Last iteration wired the drag to end at a real lair and I called that the payoff shot. It would not have
landed. `findMount` only looked one block out from the lair position, and the drag puts you down in the
middle of a nine-block-wide room — every position it checked was open air, so the head would have been
left hovering over the nest instead of hung up with the others. The sequence would have read as a bug,
not a reveal.

Widened the search: eye level first and working down, then outward ring by ring to six blocks, still
preferring a spot with something solid behind it. Cheap, and it only runs when somebody dies.

Verified on a clean lair, because the first attempt went into a test world contaminated by earlier runs —
overlapping lairs stacked at the same coordinates and a fill-count that had already stripped the skulls
out. Built a fresh cavity, generated a lair in it, and hung a face: it landed at relative
(-4, +2, -4), which is the inner wall face at eye height, in an empty corner between the trophies the
room already had. Confirmed it is a `player_wall_head` and not the floor fallback, and a count came back
nine skeleton skulls in that quadrant, so it genuinely joined a row rather than decorating an empty room.

Added `/unseen trophy <pos>`, which hangs an unclaimed face using exactly the search the real theft uses.
This one is worth keeping rather than deleting: the mount search could otherwise only be exercised by
dying, and every time I have assumed a placement was fine without running it, it was not.

Audited the change. The wider radius means in a bare cave the head can now end up as much as six blocks
from where you fell, possibly on the far side of a wall from your body. That is a real behaviour change
and I am leaving it: a head on a wall six blocks away still reads as deliberate, and a head lying on the
floor next to your bones reads as loot. Cost is about two and a half thousand block lookups on death,
which is nothing for something that happens once.

### 2026-07-31 — the wearer was trapped in the Hollow

Checked the story chain end to end before adding another structure, and found the last link was broken.
The backstory says it *walks back out wearing you, to find your friends*. But the theft now happens in a
lair, and lairs are in the Hollow — so the Impersonator was spawning down there, in a dead dimension with
nobody in it, and would never have been seen by anyone. The most distinctive idea in the whole pack was
sitting in a room underground where it could not be used.

The trophy stays in the lair. The wearer does not: it surfaces in the overworld at the same coordinates
it took you from. The Hollow is this world with the sun taken out, so coming back up in the light at the
spot where you were killed is the shortest honest reading of the story, and it puts the thing wearing
your face exactly where your friends were last standing.

**Two bugs, both caught by actually running it.** The first attempt reported the wearer coming out at
y=-64 and left no entity behind at all. `WorldView.getTopY` answers with the *bottom of the world* for a
chunk that is not loaded instead of loading it — and the overworld column above a Hollow lair is
precisely the kind of place nobody has ever been. So it spawned into the void of an ungenerated chunk and
fell. The log even caught one of the broken ones mid-fall: an impersonator at y=-417 dying with "fell out
of the world". Fixed by loading the chunk first, with a fallback to the lair if the column is still
empty. Also made a failed spawn return null rather than reporting success.

**The bigger lesson was about the testing, not the code.** I spent several runs chasing contradictory
`@e` selector results — the overworld looked empty, the Hollow looked occupied, and the two disagreed
between runs. Some of it was a test world carrying entities from earlier broken attempts, and some was a
grep that missed `data get entity` output because it prints "has the following entity data" and never the
word I was matching on. None of it was the code. One `LOGGER` line inside the spawn settled it in a
single run: `spawn=true world=minecraft:overworld entityWorld=minecraft:overworld`. When a console check
and the code disagree, ask the code — and delete the world before believing anything about entities.
Downgraded that line to debug once it had done its job.

Added `/unseen impersonate <pos>`, which sends out a wearer through the same path a real theft uses, so
this is testable without dying.

### 2026-07-31 — the hidden portals had no rot on them

The backstory describes a portal as "ringed in bleached grey rot that is slowly eating the stone around
it". That rot is the tell — it is what separates a way into the Hollow from a doorway somebody built.
Checked which code paths actually seed it: the runtime cave portals and the debug command, and not the
cottage or the well. So the portals the story centres on, the ones that hide in structures, were
generating perfectly clean.

First fix was to let worldgen call `spreadTaint` at all, which meant widening it from `ServerWorld` to
`WorldAccess` — the same deadlock rule as everything else in a feature. That got two taint blocks per
portal, which was worth chasing rather than accepting.

The reason is a rule I put in on purpose two audits ago: `canCorrupt` only allows natural stone, dirt and
sand, so creeping taint can never eat somebody's house. A cottage cellar is stone brick. The rot had
nothing it was permitted to touch.

Rather than loosen that rule, added `encrustTaint`, which is worldgen-only: it cakes the structure's own
blocks once, at generation, and does not creep. The distinction matters and is the whole point — a
generated portal arrives already rotting into the room it was cut through, but the taint that spreads
from it afterwards still obeys `canCorrupt` and cannot climb up into the cottage or anything a player
builds. Never eats the frame or the portal itself either.

Verified with eight cottages in a row and non-overlapping count windows: the seven without a portal have
zero taint, the one with a portal has twenty-four. Before the fix that number was two.

Worth recording about the testing: my first attempt at this used overlapping fill regions to count, and
`fill ... replace` destroys what it counts, so the first window consumed the blocks the next one was
supposed to find and the numbers moved between cottages. The counts looked contradictory and the code was
fine. Non-overlapping windows and a per-cottage marker line made it obvious in one run.

### 2026-07-31 — gardens, and the flowers had never existed

Did the queue item this time. Every cottage now has a fenced vegetable plot on the far side of the house
from the door: a seven-square oak fence with one gate facing back toward the front, twenty-four beds of
farmland around a single water source, and wheat, carrots, potatoes or beetroot at random growth. It is
the cheapest thing that makes the meadow read as lived in rather than decorated. A cottage on its own is
scenery; a cottage with somebody's carrots behind it is a home, and the cellar underneath is worse for it.

Built into the cottage rather than as a separate feature, so it reuses the placement checks that are
already verified and a plot never turns up orphaned in an empty field.

**Then the plot placed nothing, and the reason was older than this iteration.** Ground level outside the
house is y = -1, not y = 0 — the cottage floor sits at y = 0 because that is the first *air* block above
the terrain, and the foundation at y = -1 is what replaces the grass. The fence was being tested one
block too high, so every post failed its ground check.

The same off-by-one was already in the flowers and the path, from the first cottage iteration. They have
never placed a single block. I wrote them, said the cottage was verified, and counted lanterns and
trapdoors and cellars — all of which are inside the house and use the same relative frame as each other,
so they were all consistently right. Nothing I checked was outside the walls. The prettiest part of the
"gorgeous fairytale lands" brief has been silently absent this whole time.

Fixed all of it to one convention and wrote it into the self-check: anything replacing terrain sits at
y = -1, anything standing on it at y = 0, asserted per part. A convention that only lives in someone's
head is how this happened.

Verified by counting on genuinely clean ground, which took two attempts — the first location was full of
cottages from earlier tests, so the slope check correctly refused to build and I briefly thought the
feature was broken again. Final counts: farmland 24, exactly the 5x5 interior minus its water source;
water 1; gate 1; fence 21; path 3; flowers 8, against 0 before the fix.

### 2026-07-31 — the mansion was dead content

Went looking for the same class of blind spot as last time — things that exist but are never reached —
and found the biggest one. `MansionBuilder` has been in the repo since early on, self-checked over 500
seeds, and was only ever reachable through `/unseen mansion`. The one thing in this pack that was
explicitly asked for as a *level* could not be found by playing the game. It has been finished and
unreachable for most of this project.

It now generates in the Hollow, rarely. A derelict house standing in a world with the sun taken out is a
landmark; one on every hill is scenery.

**Had to shrink it first, and the reason is worth keeping.** A worldgen feature may only write inside a
3x3 chunk region — 48 blocks — and writes outside that are *silently dropped*, not refused. At GRID 5 the
footprint is 41, so a mansion straddling a chunk boundary would have lost part of itself with no error
anywhere: exactly the kind of failure I keep finding by accident. GRID 4 gives 33, which provably fits
whatever chunk the origin lands in. Sixteen rooms over two floors instead of twenty-five. Confirmed with
the rarity forced to every chunk that a whole region generates with **zero** far-chunk errors, which was
the entire point of the change.

Two other things it took to make it real. `MansionBuilder` took `World`, which a feature cannot supply —
widened to `WorldAccess`, same rule as everything else that generates. And the flatness check rejected
every single chunk on the first attempt: I allowed four blocks of variation across a thirty-three block
span, which natural terrain essentially never satisfies. Raised to eight, and it now builds off the
*lowest* corner rather than the average, so the house sits into a hill rather than leaving a corner
hanging in the air.

Verified by counting: 126 mansion bookshelves across the generated region, where before the fix there
were none. Also re-ran the plan self-check, which needed `-ea` to mean anything — it says so itself if
you forget, which is the only reason I noticed.

Restored the rarity to 220 afterwards.

### 2026-07-31 — three clean audits, and a hole in the release path

Kept sweeping for things that exist but silently do nothing, since that has found a real bug two
iterations running. Three came back clean, which is worth recording so they do not get re-checked:

The custom sounds all point at vanilla Warden and cave assets rather than shipping `.ogg` files, so there
is nothing missing — a sound that resolves to nothing would have been silent with no error. The Wardrobe
is registered with a block item, sits in the Functional creative tab, and is placed by `MansionBuilder`,
which means last iteration's fix quietly made wardrobes findable in normal play as a side effect. And the
Hollow Taint really does creep: `.ticksRandomly()` is set on the block settings, `hasRandomTicks` is gated
on vigour, and `randomTick` spreads with the vigour decremented, so "slowly eating the stone around it"
is real behaviour and not just a comment.

The one that was not clean is the release path. `build_mrpack.py` copied `build/libs/unseen-0.1.0.jar`
and only checked that it *existed*. A stale jar ships silently: the pack assembles, uploads and installs
perfectly, and simply is not the code in the tree.

First attempt at fixing it was worse than the bug. I compared the jar's timestamp against the newest
source file and refused if it was older — which fires correctly, but Gradle skips the jar task when
content has not actually changed, so the error could not be cleared by rebuilding. A guard the user
cannot satisfy is worse than no guard at all, and I only found that out because I tested the *recovery*
path rather than just the trigger.

Replaced it with the obvious thing: the script runs `./gradlew build` itself and refuses to write a pack
around a jar that did not compile. Verified both directions — a normal run now builds and packs, and a
deliberately broken source makes it stop with the reason. The pack went from 146KB to 166KB on the first
correct run, which is the new worldgen code that had never been in it.

### 2026-07-31 — bridges and arches, and the portals were inert in the shipped pack

Cleared the last buildable queue item. Two more things to find in the meadow.

**The flower arch** is five blocks: two fence posts, a lintel, a flowering-azalea canopy, a dirt path
under it and flowers crowding the feet. Small on purpose — the meadow already has things to look at,
what it lacked was anything that reads as *tended*, as opposed to merely pretty.

**The footbridge** crosses a stream on planks with fence rails and a lantern at each corner. The only
real logic is deciding where it goes, so that lives in `BridgePlan.span` as a pure function over a
boolean array and is checked without a game running: centred, off-centre, dry land, a one-block puddle,
an unbroken lake, and a bank on only one side. A run of water that reaches the edge of the search window
is refused rather than half-bridged.

Verified both headlessly, and the verification is what found the bug in each.

The bridge is exact — 21 planks, 14 rails, 4 lanterns on a hand-built stream. Every check it makes is a
`getBlockState`, never the worldgen heightmap, so a plate built with `/fill` is a fair test for it.

The arch was not. **Counting azalea leaves across the biome proved nothing**: lush caves grow azalea
trees up through the surface, so the block I was identifying the arch by also appears for free. Rebuilt
the check to find one column's real surface by bisection, place a single arch there, and count that arch
alone — and it came back 5 canopy, 11 fence, and **3 path where there should have been 5**. A dirt path
with anything solid on top of it reverts to plain dirt the instant it is placed, so the two blocks under
the posts were being written and immediately lost. It now lays path only where you actually walk.

**The bigger find came out of the code review.** `handlesTravel()` stood the native dwell-teleport down
whenever Immersive Portals was *installed* — but worldgen portals are built during chunk generation,
which has no `ServerWorld` and so can never spawn IP's view entity. With IP present, and it is a
required dependency of the shipped pack, **every naturally generated portal was completely inert**. The
cottage cellar, the well chamber: walk in, stand there, nothing. The one link in the story chain that
the whole first act exists to deliver.

The fix makes the hand-off per portal instead of per install. Standing in an unlinked frame is the first
moment there *is* a `ServerWorld`, so it links right then and only defers if that worked. If Immersive
Portals is absent, or its call throws, the native teleport carries on. That also let the `LINKED` set go
entirely: the question "is this frame already linked" is now asked of the world, which saves portal
entities with the chunk, instead of a process-local set that forgot everything on restart and stacked a
second portal on top of the first.

Proving it took four runs, and **three of them were my test rig lying**. `execute in <dimension> if
entity @e[tag=…]` is not dimension-scoped without a positional constraint, so the probe reported the pig
in the overworld and the Hollow at once — and I nearly logged a pass on it. Then a `NoAI` pig spawned
standing inside the portal never fired the block-collision callback at all, because those only fire on
movement. Dropped from three blocks up so it actually falls, the seamless portal appears on contact: no
portal entity before, one after. Same lesson as the superflat `server.properties` and the `WORLD_SURFACE_WG`
platform — when a console check and the code disagree, ask the code.

The review's other six fixes are in. The seven it left are decisions rather than patches, so they are at
the top of the queue with file and line.

### 2026-07-31 — the meadow has no outside

Two spec changes landed together, and they turned out to be one change.

**The meadow is endless.** `StorybookRegion` claimed five soft biomes and left deserts and badlands
generating normally, which I verified on purpose a few iterations ago and which is now exactly
backwards. It uses `addModifiedVanillaOverworldBiomes` instead: vanilla's entire overworld parameter
list, with all 36 land biomes swapped for the meadow inside it. Claiming the parameter space directly
would have been simpler and would have swallowed the oceans with the land.

Water and caves survive deliberately. An ocean rendered as grass looks broken rather than dreamlike,
the footbridges built this morning need streams to cross, and a portal cut into a lush-cave wall is
worth more than one in plain stone.

**The mod alone was not enough.** TerraBlender's `vanilla_overworld_region_weight` defaults to 10, so
vanilla regions keep a share of the world no matter what our region claims — a desert would still have
been out there. That is a config file, not code, so the pack now ships `terrablender.toml` in overrides
and `build_mrpack.py` refuses to write a pack whose copy does not zero it. A premise that depends on a
config value the pack does not ship is a premise that only works on my machine.

Verified against a fresh world, twelve checks: the meadow is found, `plains`, `desert`, `badlands` and
`cherry_grove` are all **absent**, and `ocean`, `river`, `beach`, `lush_caves`, `dripstone_caves`,
`deep_dark` and ctov's `village_plains` are all still found. The absences are the real proof.

**The dark is empty.** Land animals already need grass and light 9, so they were surface-only without
any rule; what made a cave feel inhabited was bats, glow squid and axolotls. Those three groups are
cancelled underground, and the Hollow now spawns nothing at all, including harmless things — a cow down
there would be the only company you have, and that is not the feeling. Config-gated as
`suppressUndergroundLife`.

**Two things I decided rather than built.** Animals can still wander into a cave mouth; a despawn sweep
to stop that costs more than the drift does, so it is accepted, not overlooked. And the Stalker was
already forbidden from lit ground by the Director's light check, so "safe in daylight" needed no code —
it needed the spec corrected, because what I wrote yesterday said the surface was safe *always*, which
would have quietly repealed the oldest rule in the pack.

Not verified headlessly: natural spawning needs a player in the chunk, so a serverside test with nobody
online proves nothing about bats either way. The change is a group test, it compiles, and it is one
`if`. Worth a glance in-world.

**Density is now a live question.** Cottages and bridges are 1-in-24 chunks, wells 1-in-40, arches
1-in-60. Per chunk that is unchanged, but it used to apply only inside an occasional meadow and now
applies everywhere. Left alone for the moment: an inhabited world suits the fairytale. If it reads as
crowded once you walk it, those four numbers are the only knob.

### 2026-07-31 — the last six review findings

The review left seven items that needed a decision rather than a patch. Six were fixes once decided;
the seventh turned out to be the guard for the first.

**The way home was built one block too low.** `findLanding` returns the first air block standing on
solid ground, and `travel` then built the return frame at `landing.down()` — putting the frame's
interior through the very block it had just confirmed was solid, and dropping the arriving entity into
a hole with nothing under it. The interior belongs at the landing itself. You now arrive standing in
the doorway, which is where you should arrive, and the existing cooldown stops it sending you back.

**And it built that frame through people's houses.** Coordinates are shared one-to-one between the
worlds, so the spot you return to is very often somewhere you have already built. `clearFor` — the
seventh finding, flagged as dead code — was written for exactly this and never wired up. It is the
guard now. Two findings that fixed each other.

**The flint and steel was free.** Lighting a portal never damaged it, so the first one you craft opens
every portal you will ever open. It also only answered on the server, so the client predicted a fire
block in the frame and showed a ghost flame until the server corrected it. Frame detection only reads
blockstates, which the client has, so `tryIgnite` now answers on both sides and only *builds* on one.

**`maybePlaceNear` could stall a tick.** Forty attempts, each a column scan up to 64 blocks out, all
inside one server tick — and positions that far out are routinely in chunks that are not loaded, where
reading a block generates the chunk synchronously. Sixteen attempts now, and it skips unloaded chunks
rather than paging them in.

**A nameless Impersonator wore a blank nameplate.** Loading one saved without a victim called
`setVictim(null, null)`, which set an empty custom name and then turned the label *on*.

**The trophy could hang in mid-air.** `findMount`'s last resort accepted the lair centre on the
strength of being air, with no floor under it. Deleted rather than patched: both callers already handle
"nowhere to hang it" by not placing one, and no trophy is better than a floating one.

Verified that last one both ways, which is the only one of the six that has a command to drive it.
In open air with the floor stripped out: *nowhere to hang a face near 0, 110, 0*. In a real lair carved
into stone: *hung a face at -4, 69, -4*. The refusal is the half that matters — it is the behaviour
that used to be a floating head.

The other five are read-and-reason plus a clean build. The ignite path needs a person holding flint and
steel and the landing fix needs someone walking back out of the Hollow, so both ride along with the
in-world checks already waiting.

### 2026-07-31 — two mods instead of two features

Asked for more hills and a creepy mansion, and told not to build either myself. Searched Modrinth's API
rather than trusting what I thought I knew about what exists for Fabric 1.21.1.

**Tectonic** (14.9M downloads, MIT) shapes terrain and adds no biomes of its own — it exists to let
TerraBlender mods place theirs on top, which is the only reason it is admissible. Anything that adds
surface biomes fights the endless meadow.

**Dungeons and Taverns Woodland Mansion Overhaul** (1.4M downloads, 184 rooms) replaces the vanilla
woodland mansion rather than adding a structure, so it costs no extra worldgen surface.

Three things would have shipped broken, and running it is the only reason none of them did.

**Tectonic died on startup twice.** It bundles apollib; apollib bundles json5. Fabric Loader unpacks
nested jars from a real mods/ folder, so players never see this — a dev run does not, and dies one
library at a time. Rather than chase each crash to the next library with no download page of its own,
the build unpacks the whole nested tree recursively.

**The mansion does not exist under the name you would expect.** `/locate structure
minecraft:woodland_mansion` answers *there is no structure with that type*: the mod deletes the vanilla
structure and registers `nova_structures:illager_manor` behind its own biome tag. My first tag file
aimed at the vanilla name and was dead the moment it was written. The meadow is now in
`nova_structures:collections/spooky_forests`, which is what the structure actually reads.

**It ships the same filename for three loaders with three different hashes** — exactly the trap that
got TerraBlender and ctov vendored. Addressed by Modrinth *version id* instead, which names one file
and cannot be ambiguous, so it needs no jar in libs/. That is the better answer to a problem already
solved the worse way twice, and the older two are worth revisiting the same way.

Verified: thirteen checks pass. Tectonic adds no biomes — `plains`, `desert`, `badlands` and
`cherry_grove` are still absent, which was the whole risk — and `nova_structures:illager_manor` is
found, so the creepy house really does stand in the meadow.

**Not yet verified: whether the world is actually hillier.** The first measurement said range 16 over
64 columns, which would mean Tectonic does nothing. I do not believe it: the sample covered 224 blocks
around spawn, spawn is chosen on flat ground, and Tectonic's landforms are kilometre-scale. The mod
does ship its terrain as a built-in datapack and the log shows it loading, so it is applied. Measuring
again over six patches spread across 6km. If that comes back flat too then Tectonic is not earning its
place and it should come out rather than ship on reputation.

### 2026-08-01 — the cracks are the portals, and five bad measurements

The cracks now split the ground around the creepy mansion and run back toward it, and a few of them are
open: flat portals lying in the earth that you look down through into the Hollow. The rest is rot. Caves
within the mansion's reach hold doorways cut into their walls.

Verified against a generated world: 36 open crack portals, 624 blocks of rot and 3 cave portals within
48 blocks of the mansion.

**The one bug that mattered was in arithmetic nothing else checks.** Features cannot ask where a
structure is, so `MansionSites` recomputes the placement from the world seed the way `/locate` does. I
used `nextInt(spread + 1)` where vanilla uses `nextInt(spread)` — which does not nudge a mansion by a
block, it draws a different random stream and puts every site somewhere else entirely. `/unseen
mansion-site` said *no mansion within reach* while standing on one. Without that command it would have
shipped as "cracks don't seem to generate much" and I would have gone looking in the feature.

**Then the harness lied five times in a row**, which is worth writing down because every single failure
looked like the feature being broken:

- `/locate` from spawn, 846 blocks from the mansion, is outside the 480-block reach — so the check for
  whether the arithmetic worked could not run at all, and reported nothing rather than nothing found.
- A 193x193 fill is 37k blocks, over `/fill`'s 32768 ceiling. Every count errored and I read the error
  as a zero. The counter now refuses to treat a failed command as an absence.
- The ground whitelist was grass, dirt and stone, which quietly excluded every hillside: Tectonic pushes
  the surface past y=120 and up there the ground is snow.
- I counted y=55..110 while the terrain by the mansion is at 119-136, so the cracks were above the
  window I was looking through.
- And the first tuning put 537 open portals within 48 blocks of the house — the ground was more hole
  than grass. Open seams are now 8% at the doorstep, falling off squared.

**A correction I owe the terrain question.** I reported Tectonic as producing a flat world twice, off 54
columns on a 64-block grid. The instrumentation shows ground at y=119-136 near the mansion. The sample
was too sparse to hit a hill, and "measured flat" was wrong both times.

### 2026-08-01 — ruins and dead ends, and the last of the discoverability gap

Two more creepy structures, and between them they close the hole the cracks left.

**Ruins.** Four broken walls, no roof, rubble where the roof went, and one in three has a portal
standing in the middle of it. Deliberately not pretty: the cottages are the fairytale, and this is what
the fairytale is built on top of. It was here first.

They are scattered across the whole world rather than tied to the mansion's reach, and that is the
point. Mansions sit roughly 1280 blocks apart while the cracks only carry 480, so the test seed put
spawn 846 blocks from any hint that anything was wrong. Ruins mean wherever you land, something old and
broken is within a walk, and a third of them are doors.

**Dead ends.** A passage bored off an existing cave that goes nowhere, with a way through at the end of
it. A portal in an open cavern is scenery you walk past; a side passage is a decision — obviously cut
rather than eroded, too straight, only goes one way. You follow it because a dead end normally means
somebody mined here, and at the end of it the wall is open. Only ever cut from a cave that already
exists: a chamber nobody can reach is the same as no chamber at all.

Verified at spawn, 846 blocks from the only mansion, so nothing mansion-gated could fire and anything
found is these two alone. Within 48 blocks: **one portal** where there were none before, and 242 blocks
of ruin wall. That is the answer to "I cannot find any of the portals".

### 2026-08-01 — portals that always work, and cracks with a width

Two reports from actually playing it, which is where the last two rounds of headless verification could
not reach.

**The portals did not work, and Immersive Portals said it needed configuring.** I could not find that
string in its language files, so rather than guess at its config I removed the dependency on the
answer. We used to stand down entirely wherever Immersive Portals had a portal — which means any reason
it declines to carry someone (a setting it wants, a version quirk, a portal it made but will not use)
left the way through silently inert. It now gets *first refusal* rather than the only say: three times
the normal dwell to do the job, and if you are still standing in the portal after that, we take you
ourselves. A portal that always works and is occasionally less pretty beats one that is beautiful and
sometimes a wall.

**The cracks were 1x1 and lame, and they were.** Two mistakes. Openness was rolled per *block*, so a
crack that went through was a dotted line of unrelated single holes rather than a split. And every open
block became its own Immersive Portals window, so even a long gash was a row of little separate
teleports. Now the decision is made per crack — does this one go through, and which stretch of it —
producing one contiguous opening; cracks have width, two or three across the middle tapering at the
ends, laid perpendicular to the direction of travel; and a flat portal flood-fills its whole contiguous
split and creates a single window over the bounding box.

Near the mansion: 93 open blocks in runs (was 36 scattered singles), 1366 of rot, 3 cave portals.

**Still unknown: the exact Immersive Portals message.** The travel fix does not depend on it, but the
see-through view might. If portals now work but do not show the Hollow through them, that message is
the next thread to pull and it needs quoting exactly.
