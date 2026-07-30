# Making the Stalker move wrong

Research on limping, crawling, and uncanny locomotion for the Stalker entity.
No code changes — this is the menu and the argument for what to build in what order.

Everything marked **verified** was checked against the actual jars this project
builds against (`javap` on yarn 1.21.1+build.3 and geckolib-fabric-1.21.1:4.9.2),
not from memory. Everything marked **untested** is a technique I'm confident in
on paper that nobody has run in this repo yet.

---

## 1. Where we are now

The Stalker's locomotion today is three files:

- `src/main/resources/assets/unseen/animations/entity/stalker.animation.json` —
  `idle`, `stalk`, `twitch`
- `src/main/java/com/unseen/entity/StalkerEntity.java:310` — one main controller
  (`isMoving() ? STALK : IDLE`) plus a triggerable `twitch`
- `src/main/java/com/unseen/entity/StalkerHuntGoal.java:78` — vanilla
  `startMovingTo(..., 1.0)` on a 10-tick repath

The model is well set up for this. `stalker.geo.json` already has an
over-jointed skeleton: `root, hips, spine_lower, spine_mid, spine_upper, neck,
head, jaw, arm_*_upper, arm_*_fore, hand_*, leg_*_upper, leg_*_lower`. Three
spine segments and a separate neck is exactly what you need for the techniques
below. Nothing here requires remodelling.

Four things are actively working against the creep factor right now:

**The gait is perfectly symmetric.** In `stalk`, `leg_left_upper` runs
`-38 → 32` and `leg_right_upper` runs `32 → -38` over the same 1.2s. The arms
mirror at the same offset. The `root` bob peaks at 0.3 and 0.9 — evenly spaced.
This is a textbook biped walk cycle, and a textbook biped walk cycle reads as
"a mob," because every mob in the game has one. Symmetry is the single loudest
signal that the thing in front of you is fine.

**Nothing drives the jaw.** The bone exists in the geo and is referenced by zero
animations. Free real estate.

**Animation speed is fixed.** No `setAnimationSpeedHandler` on the controller, so
the 1.2s cycle plays at 1.2s regardless of how fast the entity is actually
travelling. Foot-slide is currently accidental. It can be made deliberate and
much worse.

**It only ever has one locomotion mode.** Speed is a flat `0.32` attribute
(`StalkerEntity.java:89`) and the goal always requests `1.0`. There is no crawl,
no lurch, no burst.

---

## 2. Why movement reads as wrong

Worth being precise about the mechanism, because it determines which levers are
worth pulling.

Human visual systems are extremely tuned to biological motion — the classic
result is that people identify a walking human from ~12 moving dots on an
otherwise black field, and that detection thresholds are roughly twice as good
for a coherent walker as for the same dots scrambled
([Johansson-style point-light work][pl], [threshold study][thresh]). You do not
need a rendered figure to trigger the "that's a person" circuit, and you do not
need a rendered figure to trigger the "that's a person and something is wrong
with them" circuit either.

That gives four usable levers, roughly in order of how cheaply they buy dread:

1. **Asymmetric timing.** A gait where the left step is longer than the right, or
   where the weight drop lands off-beat, still parses as walking — it just parses
   as walking *injured*. This is the limp, and it's the highest ratio of effect to
   effort available.

2. **Decoupled parts.** Head tracking a beat behind the body, or arms swinging on
   a period that doesn't divide the leg period. The pieces are individually
   plausible and collectively impossible. Horror animation guides describe this as
   deliberately corrupting an otherwise standard cycle rather than animating
   something overtly monstrous ([MoCap Online][mco]).

3. **Wrong-animal locomotion on a human skeleton.** Spider/quadruped/snake
   patterns driven through a humanoid rig. This is the crawl, and it's the biggest
   jump in perceived wrongness — also the biggest implementation cost, because it
   needs a hitbox and pathfinding story, not just keyframes.

4. **Velocity/animation mismatch.** Feet that don't match ground speed, or motion
   that doesn't decelerate. The brain has a physics model for bodies; violating it
   is what makes "it glided" so much worse than "it ran."

A fifth, orthogonal to animation entirely: **motion conditioned on observation.**
Nothing about the animation matters if the thing is only ever seen already still.
We already have the primitive for it — see §3.5.

---

## 3. The menu

Ordered by layer, cheapest first. Each entry says where it plugs into this repo.

### 3.1 Animation data only — no Java

Pure `.json` edits to `stalker.animation.json`. No recompile of logic, no risk,
and this is where most of the win is.

**Limp.** Break the symmetry in `stalk`. Concretely: keep the 1.2s length, but
give the two legs different swing amplitudes and different timing splits. Instead
of left peaking at 0.0/1.2 and right peaking at 0.6, put the "bad" leg's plant at
0.45 and its lift at 0.55 — a short, hurried stance phase on that side and a long
drag on the other. Then drop `root` position asymmetrically: a deep `-1.5` dip
timed to the bad leg's plant only, and no dip on the good leg. Add a compensating
`spine_lower`/`spine_mid` lean toward the good side on the same beat. That
combination — short stance, deep drop, trunk lean away — is what a real limp looks
like, and it's four keyframe edits.

**Drag the trailing foot.** The rig has `leg_*_lower` but no foot bones, so a
proper toe-drag isn't available. The read can be faked by holding
`leg_right_lower` near `30` through the whole swing phase instead of straightening
it — a stiff leg that scrapes rather than steps.

**Coprime cycles.** GeckoLib runs one animation per controller and
"you cannot have two animations operating on the same bone" ([wiki][gwiki]). But
disjoint bone sets on separate controllers are fine and that's the trick: put the
legs on a 1.2s loop, the arms + spine on a *1.7s* loop, and the neck/head on a
*2.9s* loop, each as its own animation on its own controller touching only its own
bones. Nothing divides evenly, so the composite pose never repeats for ~6 minutes.
Every individual part looks fine. The whole never settles.

**Use the jaw.** A slow, arrhythmic open/close on `jaw` with long holds — open for
2.3s, snap shut, hold 4s, crack open again. Breathing that isn't breathing.

**Sub-threshold jitter.** 1–2° noise on `neck` and `spine_upper` at ~0.1s
intervals, riding under the main animation. Individually invisible; the effect is
that the figure never fully holds still, which is what separates "a statue" from
"something holding very still on purpose."

**Ease into snap.** GeckoLib ships a full easing set (**verified**: `LINEAR`,
`STEP`, `EASE_IN/OUT_*` for sine/quad/cubic/quart/quint/expo/circ/back/elastic/
bounce, `CATMULLROM`). `EASE_IN_EXPO` on the wind-up of a head turn and then a
hard `STEP` at the end gives the "slow… slow… *snap*" read that reads as
predatory rather than mechanical. Set per-controller via
`setOverrideEasingType(EasingType)` (**verified**), or per-keyframe in the JSON.

### 3.2 Animation controller — small Java

In `StalkerEntity#registerControllers` (`StalkerEntity.java:310`).

**Speed tied to actual velocity** — or deliberately not.
`setAnimationSpeedHandler(Function<T, Double>)` (**verified**) lets you scale
playback per-frame. Two opposite uses, both worth trying:

- *Sync it* — `speed = horizontalVelocity / expectedCycleDistance` kills foot-slide
  and makes the thing feel physically present. Good for the crawl.
- *Break it deliberately* — clamp playback to a constant while the entity
  accelerates. Feet cycling at walking pace while the body closes at a sprint is
  one of the most reliably horrifying things in the genre and costs one lambda.

**Transition length.** The controller's constructor takes `transitionTickTime`
(currently `6` on main, `0` on twitch). Long transitions (10–15) make state changes
read as a body *deciding*; `0` makes them read as a cut. Idle→stalk on 0 is
genuinely startling — the thing doesn't start walking, it is suddenly walking.

**More trigger anims.** The `twitch` pattern already in place
(`triggerableAnim` + `triggerAnim`, **verified** in 4.9.2) scales: add
`head_snap`, `stumble`, `stand_wrong`. Fire them from `tick()` on a random
low-probability roll rather than a timer, so they never establish a rhythm.

Constraint to respect: later-registered controllers override earlier ones for
shared bones, and the docs are explicit that two animations on one bone is
undefined behaviour. Keep the bone sets disjoint per controller and this is safe.

### 3.3 Procedural bone perturbation — the real power tool

`GeoModel#setCustomAnimations(T animatable, long instanceId, AnimationState<T>)`
(**verified**) runs every frame *after* the baked animation is applied. Override
it, call `super`, then reach into `getBone("neck")` and add to what the animation
already computed. `GeoBone` exposes `getRotX/setRotX` and friends
(**verified**), so this is additive and composes with the JSON work above rather
than replacing it.

This needs a real model class. `StalkerRenderer` currently uses
`DefaultedEntityGeoModel` inline (`StalkerRenderer.java:16`); it would become a
`StalkerModel extends DefaultedEntityGeoModel<StalkerEntity>` overriding
`setCustomAnimations`. `DefaultedEntityGeoModel` already overrides that method to
do head-tracking (**verified**: it holds `headBone` + `turnsHead`), so calling
`super` first is required, not optional.

What this unlocks that keyframes can't:

- **Perlin/sine jitter that never loops.** Drive off
  `state.getAnimationTick()` + entity id so two Stalkers in frame are never in
  phase.
- **Physically-driven lurch.** Read actual velocity and lean the spine into it
  with a lag, so acceleration visibly *throws* the body.
- **Sanity-scaled distortion.** The mod already tracks per-player sanity
  (`SanityManager`, `HorrorState`). Scale neck hyperextension and limb jitter by
  the observer's sanity, so the thing physically deforms the worse the player is
  doing. This is the strongest idea in this document and it is only possible at
  this layer.
- **Non-uniform scale.** `setScaleX/Y/Z` per bone. Stretching `neck` and
  `arm_*_fore` by 1.15 while leaving everything else alone gives proportions that
  are wrong without being cartoonish.

Cost: one new class, ~60 lines. Everything is client-side, so it goes in
`src/client/java`.

### 3.4 Entity locomotion — crawl, lurch, burst

**Crawl.** The reference implementation is the Cave Dweller mod
([SiverDX/cave_dweller][cd]), which is a useful read because it's the same problem
on the same animation library. Its approach:

- Probe the blocks around the entity each tick (above, facing, facing-above,
  two-above) and derive `shouldCrouch` / `shouldCrawl` from the geometry —
  crawling is a *reaction to the ceiling*, not an AI decision
  (`CaveDwellerEntity.java:245`)
- Store it in tracked entity data so the client renderer can switch animations
- Override `getDimensions(pose)` to return `0.5 × 0.5` while crawling, and call
  `calculateDimensions()` on change
- Scale nav speed by mode: `0.35` crawling, `0.6` crouching, `0.85` upright
- **A mixin on `GroundPathNavigation#canUpdatePath` to force-allow pathing while
  crawling** — without it the entity will not path through 1-block gaps

That last point is the hidden cost. Vanilla ground navigation will not route a
mob through a gap shorter than its standing height, so a crawl that's meant to let
it follow you into a 1×1 hole needs a mixin regardless of how good the animation
is. We already have `unseen.mixins.json` set up, so the infrastructure is there.

For 1.21.1 the API surface is confirmed: `Entity#getDimensions(EntityPose)`,
`calculateDimensions()`, `EntityDimensions.changing(w, h)`, `setPose`, and
`EntityPose.SWIMMING` (the pose vanilla uses for crawling) all exist as expected
(**verified**).

A cheaper 80% version: skip the geometry probes and make crawling a *phase*
behaviour — it crawls during PEAK, walks otherwise. No mixin needed if it never
has to fit through a gap it couldn't walk through; you get the visual without the
pathfinding project. **Recommended as the first cut.**

**Head/body decoupling.** `MobEntity#createBodyControl()` is overridable
(**verified**), as are `LivingEntity#turnHead(float, float)` and
`getMaxRelativeHeadRotation()` (**verified**). Default vanilla behaviour snaps the
body to follow the head within a limit. Raising the limit to 180° and slowing the
body's catch-up gives an owl-neck: it keeps walking away from you while its face
stays on you. Very cheap, very effective, and it composes with everything else.

**Lurch instead of glide.** Vanilla `MoveControl` produces smooth constant-speed
motion. Overriding `createMoveControl()` — or just modulating the
`GENERIC_MOVEMENT_SPEED` attribute in `tick()` — with a sawtooth (fast lunge,
near-stop, fast lunge) makes approach unpredictable in a way that defeats the
player's ability to time a retreat. The attribute-modulation version is about six
lines and needs no new class.

**Repath cadence.** `StalkerHuntGoal` repaths every 10 ticks
(`StalkerHuntGoal.java:79`). Randomising that to 6–30 makes the path visibly
hesitate and re-commit.

### 3.5 Observation-gated movement

The mod already has `SanityManager.isLookingAt(player, stalker, coneDegrees)`
(`SanityManager.java:102`) and `player.canSee(stalker)`. That's everything needed
for the Weeping Angel / SCP-173 pattern: hard-freeze all movement and animation
while any player has line of sight, and move only in the gaps.

This is the highest-leverage single behaviour on this list, because it converts
every technique above from "the player watches it move wrong" into "the player
infers it moved wrong." It also solves the animation-quality problem for free —
motion you never see doesn't need to be convincing.

It interacts with the existing stare-punishment system
(`Config.stareTicksBeforeVanish = 12`), which currently makes it *vanish* when
stared at. Freeze-vs-vanish are two different games and probably shouldn't both
be on for the same entity in the same phase. Worth deciding deliberately: freeze
during STALK, vanish during PEAK, or gate on sanity.

---

## 4. What I'd actually build, in order

1. **Asymmetric limp in `stalk`** (JSON only). Biggest read-change per unit of
   effort. Nothing else on this list changes the silhouette as much.
2. **Coprime multi-controller layering** — legs 1.2s, arms/spine 1.7s, neck/head
   2.9s. JSON plus ~10 lines in `registerControllers`. Kills the loop.
3. **Jaw + sub-threshold jitter** (JSON). Free.
4. **Head/body decoupling** via `createBodyControl` + `getMaxRelativeHeadRotation`.
   ~20 lines, no new assets.
5. **`StalkerModel` with `setCustomAnimations`**, initially just sanity-scaled
   jitter and spine lean. This is the platform for everything procedural later.
6. **Speed-broken animation** during PEAK — feet at walk pace, body at sprint.
7. **Phase-gated crawl** (no geometry probes, no mixin). Reassess whether the full
   Cave Dweller treatment is worth it after seeing the cheap version in-game.
8. **Observation-gated freeze**, once the stare/vanish interaction is decided.

Steps 1–4 are a day and need no new classes. Step 5 is the inflection point.

## 5. What I'd skip

- **A second rig / remodelling.** The existing skeleton has three spine segments,
  a neck, and a jaw. That is more than enough joints. Nothing here is blocked on
  the model.
- **Full geometry-probe crawl with the nav mixin** — until the phase-gated version
  has been seen in-game. It's a real project (probes, tracked data, dimensions,
  mixin, sync bugs) and the visual payoff may be indistinguishable.
- **IK / procedural foot placement.** No foot bones, and GeckoLib gives no IK
  solver. Would mean writing one. The limp fakes it well enough.
- **Custom `EasingType` registration.** GeckoLib supports it
  (`GeckoLibUtil#addCustomEasingType`), but the 30+ built-ins cover every curve
  described above.

## 6. Open questions

- **Freeze or vanish under observation?** They're mutually exclusive readings of
  the same creature. Needs a call before §3.5 gets built.
- **Does the limp survive the speed-modifier work?** An asymmetric cycle whose
  playback rate is being scaled by velocity may read as a stutter rather than a
  limp. Untested; try in that order and check.
- **Multiple Stalkers in frame.** Any procedural jitter must be seeded off entity
  id or two of them will move in perfect lockstep, which is either much scarier or
  completely broken and I don't know which without seeing it.
- **Does the crawl hitbox break the vibration hearing?** `EntityPositionSource`
  uses `getStandingEyeHeight()` (`StalkerEntity.java:330`), which moves when
  dimensions change.

---

[pl]: https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0025867
[thresh]: https://pubmed.ncbi.nlm.nih.gov/19227372/
[mco]: https://mocaponline.com/blogs/mocap-news/horror-game-animation-guide
[gwiki]: https://github.com/bernie-g/geckolib/wiki/The-Animation-Controller-(Geckolib4)
[cd]: https://github.com/SiverDX/cave_dweller
