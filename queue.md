# Work queue — Stalker locomotion

Derived from [`docs/research/creepy-movement.md`](docs/research/creepy-movement.md).
Ordered by payoff per unit of effort. Q1–Q4 need no new classes and no new
dependencies.

**Goal:** make the Stalker's movement read as injured, wrong, and non-repeating,
without remodelling the rig or adding a dependency.

**Tech:** Fabric 1.21.1 · yarn 1.21.1+build.3 · GeckoLib 4.9.2 · Java 21.

## Global constraints

- Bone rotations in `.animation.json` are **degrees**. Bone rotations touched
  through `GeoBone` in Java are **radians** — convert with
  `MathHelper.RADIANS_PER_DEGREE`.
- One animation per `AnimationController`, and no two controllers may animate the
  same bone. Every controller added below owns a disjoint bone set.
- Anything touching rendering goes in `src/client/java` — `splitEnvironmentSourceSets()`
  is on, so client classes cannot be referenced from `src/main/java`.
- Pure-logic classes follow the existing self-check convention: a `main` with
  `assert`, no Minecraft imports, run via
  `java -ea -cp build/classes/java/main com.unseen.<Class>`.
- Build check for every task: `./gradlew build`.
- Animation check for every task that touches `stalker.animation.json`:
  `python3 tools/check_animations.py`. It fails on a bone name that is not in the
  geo, on a bone claimed by two controllers, and on cycle periods that share a
  factor. All three are silent at build time and painful to diagnose by eye.
- In-game check for every task: `./gradlew runClient`, then `/unseen spawn`.
  `/unseen phase PEAK` and `/unseen sanity <n>` force the states referenced below.
  Booting the client only proves resources load — the animation is not parsed
  until a Stalker first renders, so the visual steps below need a human watching.

## Status

| # | Item | Effort | Blocked on |
|---|------|--------|-----------|
| Q1 | Asymmetric limp | JSON only | **done — awaiting visual sign-off** |
| Q2 | Coprime cycle layering | JSON + ~25 lines | **done — awaiting visual sign-off** |
| Q3 | Jaw and micro-jitter | JSON only | **done — awaiting visual sign-off** |
| Q4 | Head/body decoupling | ~40 lines | Q2 |
| Q5 | `StalkerModel` + procedural distortion | new class, ~70 lines | Q2 |
| Q6 | Speed-broken animation | ~15 lines | Q2, Q5 |
| Q7 | Phase-gated crawl | ~60 lines + JSON | Q1 |
| Q8 | Observation-gated freeze | ~30 lines | **decision below** |

---

## Q1 — Asymmetric limp

**Why:** `stalk` is currently a symmetric biped walk cycle — left leg `-38 → 32`,
right leg `32 → -38` over the same 1.2s, arms mirrored, root bob evenly spaced at
0.3/0.9. Symmetry is the loudest single signal that the thing in front of you is
an ordinary mob. Breaking it costs four keyframe edits.

The design: the **right leg is the bad one**. Short stance phase on that side, a
deep weight-crash when it plants, a knee that never straightens, and a trunk that
leans away to compensate. That combination is what a real limp looks like.

**Files:** Modify `src/main/resources/assets/unseen/animations/entity/stalker.animation.json`
(the `stalk` block only).

- [ ] **Step 1: give the bad leg a short, early stance**

Replace the `leg_right_upper` and `leg_right_lower` entries in `stalk`:

```json
"leg_right_upper": {
  "rotation": { "0.0": [22, 0, 0], "0.45": [-24, 0, 0], "0.55": [-24, 0, 0], "1.2": [22, 0, 0] }
},
"leg_right_lower": {
  "rotation": { "0.0": [28, 0, 0], "0.6": [24, 0, 0], "1.2": [28, 0, 0] }
}
```

The `0.45 → 0.55` hold is the drag. The lower leg never returns below 24°, so the
knee never straightens — the rig has no foot bones, and a permanently bent knee is
how you fake a scraping foot without them.

- [ ] **Step 2: crash the weight onto the bad leg**

Replace the `root` entry in `stalk`. It currently bobs symmetrically
(`0.3` and `0.9` both `+0.6`):

```json
"root": {
  "position": { "0.0": [0, 0, 0], "0.15": [0, -1.4, 0], "0.3": [0, -1.1, 0], "0.5": [0, 0.5, 0], "0.75": [0, 0.6, 0], "1.2": [0, 0, 0] }
}
```

One deep drop, one shallow rise. The body falls onto the bad side and is pushed
off the good one.

- [ ] **Step 3: lean the trunk away from the bad side**

Replace `spine_lower` in `stalk` (currently the constant `[14, 0, 0]`) and add a
`hips` entry, which is presently unanimated:

```json
"hips": {
  "rotation": { "0.0": [0, 0, 5], "0.15": [0, 0, 8], "0.6": [0, 0, 2], "1.2": [0, 0, 5] }
},
"spine_lower": {
  "rotation": { "0.0": [14, 0, -7], "0.15": [16, 0, -9], "0.6": [12, 0, -2], "1.2": [14, 0, -7] }
}
```

Hips tilt one way, spine counter-tilts — the trunk stays roughly upright while the
pelvis drops, which is the give-away detail of a real limp.

- [ ] **Step 4: build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL. A malformed animation JSON fails at runtime, not
compile time, so this only proves nothing else broke.

- [ ] **Step 5: look at it**

Run: `./gradlew runClient`, then `/unseen spawn` and walk backwards away from it.
Expected: it walks with a visible hitch, dipping on its right side. Watch for the
failure mode — if the drag reads as a *stutter* rather than a limp, the `0.45/0.55`
hold is too long; shorten to `0.45/0.5`.

- [ ] **Step 6: commit**

```bash
git add src/main/resources/assets/unseen/animations/entity/stalker.animation.json
git commit -m "feat(anim): asymmetric limp in the stalk cycle"
```

---

## Q2 — Coprime cycle layering

**Why:** any single looping animation eventually reads as a loop, and once the
player clocks the loop the thing becomes machinery. Splitting the body across
three controllers on periods that share no common factor means the composite pose
does not repeat.

1.2s / 1.7s / 2.9s is 24 / 34 / 58 ticks. LCM is 11,832 ticks — just under ten
minutes before the whole body returns to the same configuration.

**Constraint that shapes the whole task:** GeckoLib forbids two controllers
animating one bone, and the *idle* animation currently touches spine, neck, head
and arms. So idle has to be split on the same bone boundaries as stalk, or the
controllers collide the moment the entity stops. Six animations, three
controllers, each controller choosing between its own moving and idle variant.

Bone ownership — every bone appears exactly once:

| Controller | Bones | Period |
|---|---|---|
| `legs` | `root`, `hips`, **`spine_lower`**, `leg_left_upper`, `leg_left_lower`, `leg_right_upper`, `leg_right_lower` | 1.2s |
| `arms` | `spine_mid`, `spine_upper`, `arm_*_upper`, `arm_*_fore`, `hand_left`, `hand_right` | 1.7s |
| `head` | `neck`, `head`, `jaw` | 2.9s |

**`spine_lower` moved from `arms` to `legs` during implementation.** Q1 puts the
limp's trunk lean on `spine_lower`, timed to the leg plant at 0.15s. Leaving it on
the 1.7s arms cycle would have drifted the lean off the plant within one stride
and destroyed the limp. The pelvis and lower spine are part of the gait, not the
upper body. Q5 and Q7 assume this corrected table.

**Files:**
- Modify: `src/main/resources/assets/unseen/animations/entity/stalker.animation.json`
- Modify: `src/main/java/com/unseen/entity/StalkerEntity.java:50-52` (the
  `RawAnimation` constants) and `:310-320` (`registerControllers`)

- [ ] **Step 1: split the animations by bone set**

Replace the two animations `idle` and `stalk` with six. Move each existing bone
entry into the animation that owns that bone — do not retune values here, this
step is a pure split so any change in look is attributable to the split alone.

- `idle_legs` (length 4.0) — no bones from `idle` belong to the legs set, so this
  is `{ "loop": true, "animation_length": 4.0, "bones": {} }`. An empty animation
  is legal and holds the bind pose.
- `idle_arms` (length 4.0) — the `spine_lower`, `spine_mid`, `spine_upper`,
  `arm_left_upper`, `arm_right_upper`, `arm_left_fore`, `arm_right_fore` entries
  from `idle`, unchanged.
- `idle_head` (length 4.0) — the `neck` and `head` entries from `idle`, unchanged.
- `stalk_legs` (length **1.2**) — the `root`, `hips`, `leg_*` entries from `stalk`
  as edited in Q1.
- `stalk_arms` (length **1.7**) — the `spine_*`, `arm_*` entries from `stalk`.
  Rescale their keyframe times by 1.7/1.2 so the shapes survive: a key at `0.6`
  becomes `0.85`, a key at `1.2` becomes `1.7`.
- `stalk_head` (length **2.9**) — the `head` entry from `stalk`, rescaled the same
  way: `0.6` → `1.45`, `1.2` → `2.9`. The `neck` constant `[-26, 0, 0]` carries
  over as-is.

Leave `twitch` alone. It is a triggered animation on its own controller and
overlaps these bone sets deliberately — see the risk note below.

- [ ] **Step 2: declare the six animations in Java**

In `StalkerEntity.java`, replace the `IDLE` and `STALK` constants (lines 50–51):

```java
private static final RawAnimation IDLE_LEGS = RawAnimation.begin().thenLoop("idle_legs");
private static final RawAnimation IDLE_ARMS = RawAnimation.begin().thenLoop("idle_arms");
private static final RawAnimation IDLE_HEAD = RawAnimation.begin().thenLoop("idle_head");
private static final RawAnimation STALK_LEGS = RawAnimation.begin().thenLoop("stalk_legs");
private static final RawAnimation STALK_ARMS = RawAnimation.begin().thenLoop("stalk_arms");
private static final RawAnimation STALK_HEAD = RawAnimation.begin().thenLoop("stalk_head");
```

- [ ] **Step 3: three controllers instead of one**

Replace the body of `registerControllers` (line 310) — keep the existing `twitch`
controller registration exactly as it is:

```java
@Override
public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    // Three periods sharing no common factor (24/34/58 ticks): the composite pose
    // does not repeat for ~10 minutes, so the body never settles into a readable loop.
    // Bone sets must stay disjoint — GeckoLib does not define behaviour for two
    // controllers animating one bone.
    controllers.add(new AnimationController<>(this, "legs", 6,
            state -> state.setAndContinue(state.isMoving() ? STALK_LEGS : IDLE_LEGS)));
    controllers.add(new AnimationController<>(this, "arms", 9,
            state -> state.setAndContinue(state.isMoving() ? STALK_ARMS : IDLE_ARMS)));
    controllers.add(new AnimationController<>(this, "head", 14,
            state -> state.setAndContinue(state.isMoving() ? STALK_HEAD : IDLE_HEAD)));

    // Fires on a trigger so the twitch reads as involuntary rather than looping wallpaper.
    controllers.add(new AnimationController<>(this, "twitch", 0, state -> PlayState.STOP)
            .triggerableAnim("twitch", TWITCH));
}
```

The three transition lengths (6 / 9 / 14) are deliberately different too, so the
parts do not even start and stop together.

- [ ] **Step 4: build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: look at it**

Run: `./gradlew runClient`, `/unseen spawn`, then watch it walk for a full minute.
Expected: no visible loop point. The specific failure to watch for is a bone
*snapping* between poses, which means two controllers are fighting over it —
recheck the ownership table.

- [ ] **Step 6: commit**

```bash
git add src/main/resources/assets/unseen/animations/entity/stalker.animation.json src/main/java/com/unseen/entity/StalkerEntity.java
git commit -m "feat(anim): split locomotion across three coprime controllers"
```

**Risk:** `twitch` animates `neck`, `head`, `spine_upper`, `spine_mid`,
`arm_*_upper` and `hand_*` — bones now owned by the `arms` and `head` controllers.
Triggered animations on a separate controller are the documented way to do this
and later-registered controllers win, so `twitch` should override cleanly while it
plays. If it instead blends into mush, the fallback is to narrow `twitch` to the
`head` controller's bones only and register it last.

---

## Q3 — Jaw and micro-jitter

**Why:** the `jaw` bone exists in `stalker.geo.json` and is animated by nothing.
And a figure that holds perfectly still reads as a statue; a figure that never
quite holds still reads as something holding still *on purpose*.

**Files:** Modify `stalker.animation.json` (`idle_head` and `stalk_head` only —
both bones belong to the `head` controller after Q2).

- [ ] **Step 1: arrhythmic jaw**

Add to `idle_head`, extending its length to 9.0 so the jaw cycle does not divide
into the arms or legs periods either:

```json
"jaw": {
  "rotation": { "0.0": [0, 0, 0], "2.3": [17, 0, 0], "2.45": [1, 0, 0], "6.4": [1, 0, 0], "7.1": [12, 0, 0], "9.0": [0, 0, 0] }
}
```

Slow open, hard snap shut, a long hold, then it cracks open again. Breathing that
isn't breathing.

- [ ] **Step 2: sub-threshold jitter on the neck**

Add to `idle_head` — amplitudes of 1–2° are individually invisible:

```json
"neck": {
  "rotation": { "0.0": [-14, 0, 0], "0.9": [-15, 1, 0], "1.7": [-13, -1, 1], "2.6": [-14, 2, 0], "3.8": [-15, 0, -1], "5.1": [-13, 1, 1], "6.7": [-14, -2, 0], "9.0": [-14, 0, 0] }
}
```

Keep the existing `1.2`/`3.1` neck snap keyframes from the original `idle` if they
survived the Q2 split; interleave rather than replace.

- [ ] **Step 3: build and look**

Run: `./gradlew build && ./gradlew runClient`, `/unseen spawn`, stand still and
watch it stand still.
Expected: it never fully settles, and the jaw movement has no findable rhythm.

- [ ] **Step 4: commit**

```bash
git add src/main/resources/assets/unseen/animations/entity/stalker.animation.json
git commit -m "feat(anim): arrhythmic jaw and sub-threshold neck jitter"
```

---

## Q4 — Head/body decoupling

**Why:** the owl-neck — it keeps walking away from you while its face stays on
you. Cheap, and it composes with everything else.

**This is not a pure win and the plan has to say so.** `StalkerRenderer` uses the
one-arg `DefaultedEntityGeoModel` constructor, which leaves `headBone` null, so
the Stalker does **not** head-track today; its head moves only by keyframes.
Turning tracking on is one word — but `DefaultedEntityGeoModel.setCustomAnimations`
calls `head.setRotX(...)` / `setRotY(...)`, overwriting rather than adding. Enable
it naively and every head keyframe in `stalk_head` and `twitch` silently stops
mattering.

The fix is to give the two jobs two bones: **`head` becomes the tracking bone and
carries no keyframes; all head animation moves onto `neck`.**

**Files:**
- Modify: `src/client/java/com/unseen/client/StalkerRenderer.java:16`
- Modify: `src/main/resources/assets/unseen/animations/entity/stalker.animation.json`
- Modify: `src/main/java/com/unseen/entity/StalkerEntity.java`

- [ ] **Step 1: move head keyframes onto the neck**

In `stalk_head`, `idle_head` and `twitch`, delete every `"head"` bone entry and
fold its rotation values into that animation's `"neck"` entry by adding them
component-wise at matching timestamps. Where only one of the two has a key at a
given time, keep the other's value at that time unchanged.

- [ ] **Step 2: turn head-tracking on**

In `StalkerRenderer.java`, replace the model construction:

```java
super(context, new DefaultedEntityGeoModel<>(UnseenMod.id("stalker"), "head"));
```

- [ ] **Step 3: let the head outrun the body**

In `StalkerEntity.java`, add:

```java
/**
 * Vanilla clamps how far a head may turn relative to the body and snaps the body around to
 * follow. Removing the clamp is half the owl-neck; {@link #createBodyControl()} is the other.
 */
@Override
protected float getMaxRelativeHeadRotation() {
    return 180f;
}

/**
 * The body converges on the head one tick in eight. It keeps walking away from you while its
 * face stays on you, and only reluctantly remembers which way it is pointed.
 */
@Override
protected BodyControl createBodyControl() {
    return new BodyControl(this) {
        private int skip;

        @Override
        public void tick() {
            if (++this.skip % 8 == 0) {
                super.tick();
            }
        }
    };
}
```

Imports needed: `net.minecraft.entity.ai.control.BodyControl`.

- [ ] **Step 4: build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: look at it**

Run: `./gradlew runClient`, `/unseen spawn`, then circle it at a distance of ~8
blocks.
Expected: the head holds on you well past the point the body should have turned,
then the body catches up in a lurch. Failure mode: if the head visibly *jitters*
between tracked and animated positions, a head keyframe survived Step 1.

- [ ] **Step 6: commit**

```bash
git add src/client/java/com/unseen/client/StalkerRenderer.java src/main/java/com/unseen/entity/StalkerEntity.java src/main/resources/assets/unseen/animations/entity/stalker.animation.json
git commit -m "feat(entity): decouple head tracking from body yaw"
```

---

## Q5 — `StalkerModel` and procedural distortion

**Why:** this is the platform everything procedural needs, and it enables the
strongest single idea in the research: **the Stalker physically deforms as the
observer's sanity drops.** Keyframes cannot do that, because keyframes do not know
who is watching.

`HorrorState` is a self-syncing attachment (`ModAttachments.syncWith`), and
`UnseenClient.state()` already reads it client-side — so sanity is available in
the renderer with no networking at all.

**Files:**
- Create: `src/client/java/com/unseen/client/StalkerModel.java`
- Modify: `src/client/java/com/unseen/client/StalkerRenderer.java:16`

**Interfaces:**
- Consumes: `UnseenClient.state()` → `HorrorState` (has `sanity()`, `phase()`);
  `GeoModel#getBone(String)` → `Optional<GeoBone>`; `GeoBone#getRotX/setRotX` etc.
- Produces: `StalkerModel` — used only by `StalkerRenderer`.

- [ ] **Step 1: write the model**

Create `src/client/java/com/unseen/client/StalkerModel.java`:

```java
package com.unseen.client;

import com.unseen.UnseenMod;
import com.unseen.entity.StalkerEntity;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * Adds distortion on top of the baked animation, scaled by how badly the watching player is doing.
 * <p>
 * Keyframes cannot express this: they do not know who is looking. Sanity arrives client-side for
 * free because {@code HorrorState} is a self-syncing attachment, so there is no networking here.
 */
public class StalkerModel extends DefaultedEntityGeoModel<StalkerEntity> {

	/** Peak extra rotation, in degrees, applied at zero sanity. */
	private static final float MAX_DISTORTION_DEGREES = 9f;

	public StalkerModel() {
		// "head" is the tracking bone and carries no keyframes; head animation lives on "neck".
		super(UnseenMod.id("stalker"), "head");
	}

	@Override
	public void setCustomAnimations(StalkerEntity entity, long instanceId,
	                                AnimationState<StalkerEntity> state) {
		super.setCustomAnimations(entity, instanceId, state);

		// 0 at full sanity, 1 at zero.
		float dread = 1f - MathHelper.clamp(UnseenClient.state().sanity() / 100f, 0f, 1f);
		if (dread <= 0.01f) {
			return;
		}
		float amount = dread * MAX_DISTORTION_DEGREES * MathHelper.RADIANS_PER_DEGREE;

		// Offset by entity id so two Stalkers in one frame never move in lockstep.
		double t = state.getAnimationTick() + entity.getId() * 37.0;

		// Rotations here are radians. The JSON is degrees; these are not the same units.
		addRot("neck", amount * sin(t, 0.70), 0f, amount * sin(t, 1.31));
		addRot("spine_upper", 0f, amount * 0.6f * sin(t, 0.53), amount * 0.4f * sin(t, 1.07));
		addRot("arm_left_fore", amount * 1.4f * sin(t, 0.89), 0f, 0f);
		addRot("arm_right_fore", amount * 1.4f * sin(t, 1.17), 0f, 0f);

		// Proportions go wrong before the pose does.
		getBone("neck").ifPresent(b -> b.setScaleY(1f + dread * 0.15f));
		getBone("arm_left_fore").ifPresent(b -> b.setScaleY(1f + dread * 0.12f));
		getBone("arm_right_fore").ifPresent(b -> b.setScaleY(1f + dread * 0.12f));
	}

	/** Three irrational-ish frequencies, so no two axes ever line up. */
	private static float sin(double t, double freq) {
		return (float) Math.sin(t * freq);
	}

	private void addRot(String bone, float x, float y, float z) {
		getBone(bone).ifPresent((GeoBone b) -> {
			b.setRotX(b.getRotX() + x);
			b.setRotY(b.getRotY() + y);
			b.setRotZ(b.getRotZ() + z);
		});
	}
}
```

- [ ] **Step 2: use it**

In `StalkerRenderer.java`, replace the model construction:

```java
super(context, new StalkerModel());
```

Remove the now-unused `DefaultedEntityGeoModel` import. Update the class javadoc,
which currently explains that `DefaultedEntityGeoModel` resolves assets by
convention — still true, but now one level up the hierarchy.

- [ ] **Step 3: build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: check both ends of the range**

Run: `./gradlew runClient`, `/unseen spawn`, then `/unseen sanity 100`.
Expected: no distortion at all — identical to Q4's result.

Then `/unseen sanity 10`.
Expected: the neck stretches and wanders, the forearms lengthen and drift, and
none of it repeats. Failure mode: distortion visible at sanity 100 means the
`dread <= 0.01f` early-out is not firing, i.e. `UnseenClient.state()` returned
`HorrorState.INITIAL` for the wrong player.

- [ ] **Step 5: two at once**

`/unseen spawn` twice and get both in frame.
Expected: they move differently. If they are in lockstep the `entity.getId()`
offset is not reaching the phase.

- [ ] **Step 6: commit**

```bash
git add src/client/java/com/unseen/client/StalkerModel.java src/client/java/com/unseen/client/StalkerRenderer.java
git commit -m "feat(render): sanity-scaled procedural distortion"
```

---

## Q6 — Speed-broken animation

**Why:** the controller has no speed handler, so the 1.2s leg cycle plays at 1.2s
regardless of how fast the entity is actually travelling. Foot-slide is currently
an accident. Making it deliberate — feet cycling at a walk while the body closes
at a sprint — is one of the most reliably horrifying things in the genre and costs
one lambda.

**Files:** Modify `src/main/java/com/unseen/entity/StalkerEntity.java` (the `legs`
controller from Q2, and `tick`).

- [ ] **Step 1: legs that ignore how fast the body is going**

Chain a speed handler onto the `legs` controller registered in Q2:

```java
controllers.add(new AnimationController<>(this, "legs", 6,
        state -> state.setAndContinue(state.isMoving() ? STALK_LEGS : IDLE_LEGS))
        // Outside PEAK the cycle tracks ground speed, so it looks physically present.
        // Inside PEAK it is pinned slow while the body accelerates: the feet stop
        // explaining the motion, which is the point.
        .setAnimationSpeedHandler(entity -> {
            if (entity.isSprintPhase()) {
                return 0.55; // pinned slow — see isSprintPhase(), added in Step 2
            }
            double speed = entity.getVelocity().horizontalLength();
            return MathHelper.clamp(speed / 0.14, 0.35, 2.0);
        }));
```

Imports needed: `net.minecraft.util.math.MathHelper`.

- [ ] **Step 2: expose the phase to the handler**

`AnimationController` runs client-side, so this value must actually reach the
client. **Do not copy the existing `phantom` field for this** — `phantom` is a
plain `boolean` persisted to NBT, never synced, and a plain field here would read
as `false` on the client forever. It needs real tracked data.

Add to `StalkerEntity`:

```java
private static final TrackedData<Boolean> SPRINT_PHASE =
        DataTracker.registerData(StalkerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

@Override
protected void initDataTracker(DataTracker.Builder builder) {
    super.initDataTracker(builder);
    builder.add(SPRINT_PHASE, false);
}

/** True while the Director has the nearest player in PEAK. Synced: the animation controller is client-side. */
public boolean isSprintPhase() {
    return this.getDataTracker().get(SPRINT_PHASE);
}
```

and set it server-side in `tick()`, inside the existing `ServerWorld` branch:

```java
PlayerEntity nearest = serverWorld.getClosestPlayer(this, 64.0);
this.getDataTracker().set(SPRINT_PHASE,
        nearest != null && ModAttachments.get(nearest).phase() == Phase.PEAK);
```

Imports: `net.minecraft.entity.data.DataTracker`, `net.minecraft.entity.data.TrackedData`,
`net.minecraft.entity.data.TrackedDataHandlerRegistry`, `com.unseen.Phase`.

Note that `StalkerEntity` does not currently override `initDataTracker` at all, so
this adds the method rather than extending one.

- [ ] **Step 3: make the body actually faster during PEAK**

In `tick()`, alongside the existing phase read:

```java
this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)
        .setBaseValue(this.sprintPhase ? 0.46 : 0.32);
```

The mismatch is the whole effect: 0.46 with legs pinned at 0.55× cycle speed.

- [ ] **Step 4: build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: look at it**

Run: `./gradlew runClient`, `/unseen spawn`, `/unseen phase PEAK`, make noise and
retreat.
Expected: it closes faster than its legs account for. Failure mode: if it reads as
*floating* rather than wrong, 0.55 is too low — try 0.7.

- [ ] **Step 6: commit**

```bash
git add src/main/java/com/unseen/entity/StalkerEntity.java
git commit -m "feat(entity): decouple leg cycle from ground speed during PEAK"
```

---

## Q7 — Phase-gated crawl

**Why:** quadruped locomotion on a humanoid skeleton is the single biggest jump in
perceived wrongness available. It is also the biggest implementation cost, so this
is deliberately the **cheap 80% version**.

The reference implementation ([SiverDX/cave_dweller][cd]) derives crawling from
per-tick geometry probes so the entity ducks under real ceilings — and needs a
mixin on `GroundPathNavigation#canUpdatePath` to path through 1-block gaps at all,
because vanilla ground nav will not route a mob through a gap shorter than its
standing height.

**We skip both.** Crawling here is a *phase* behaviour, not a reaction to ceilings.
It crawls during PEAK because that is more frightening, never to fit somewhere it
could not otherwise go. No probes, no mixin, no pathfinding project. Reassess
after seeing it.

**Files:**
- Modify: `src/main/resources/assets/unseen/animations/entity/stalker.animation.json`
- Modify: `src/main/java/com/unseen/entity/StalkerEntity.java`

- [ ] **Step 1: a crawl cycle**

Add three animations on the Q2 bone boundaries, so the existing controllers can
select them: `crawl_legs` (1.6s), `crawl_arms` (1.6s), `crawl_head` (1.6s). Unlike
the walk, these three *do* share a period — a crawl is a coordinated four-limb gait
and desynchronising it just looks broken.

Shape: `root` dropped to `[0, -9, 0]`; `spine_lower` `[68, 0, 0]` so the trunk is
near-horizontal; `neck` `[-52, 0, 0]` so the head still faces forward from a
horizontal spine; arms reaching and pulling in opposition to the legs
(`arm_left_upper` `0.0 [-95, 0, 8]` → `0.8 [-20, 0, 8]` → `1.6 [-95, 0, 8]`, right
mirrored at the half-phase). Keep the Q1 asymmetry: the bad leg trails.

- [ ] **Step 2: tracked crawling state**

Same rule as Q6 Step 2: the animation selection is client-side, so this must be
tracked data, not a plain field. Add alongside `SPRINT_PHASE`:

```java
private static final TrackedData<Boolean> CRAWLING =
        DataTracker.registerData(StalkerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
```

register it in the `initDataTracker` added in Q6 (`builder.add(CRAWLING, false)`),
and expose it:

```java
public boolean isCrawling() {
    return this.getDataTracker().get(CRAWLING);
}
```

Drive it from the phase in `tick()`, immediately after the `SPRINT_PHASE` set:

```java
boolean shouldCrawl = this.isSprintPhase();
if (shouldCrawl != this.isCrawling()) {
    this.getDataTracker().set(CRAWLING, shouldCrawl);
    this.calculateDimensions();
}
```

- [ ] **Step 3: a hitbox that matches**

```java
/**
 * Deliberately not derived from surrounding geometry: this crawls because PEAK is frightening,
 * never to fit through a gap it could not walk through. That restriction is what lets this work
 * without a GroundPathNavigation mixin.
 */
@Override
public EntityDimensions getDimensions(EntityPose pose) {
    return this.isCrawling() ? EntityDimensions.changing(0.9f, 0.6f) : super.getDimensions(pose);
}
```

Imports: `net.minecraft.entity.EntityDimensions`, `net.minecraft.entity.EntityPose`.

- [ ] **Step 4: slow it down**

A crawl that moves at walking pace looks weightless. In `tick()`, fold into the Q6
speed line:

```java
double base = this.isSprintPhase() ? 0.46 : 0.32;
this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED)
        .setBaseValue(this.isCrawling() ? base * 0.55 : base);
```

- [ ] **Step 5: fix the hearing regression this causes**

`HearingCallback` builds its `EntityPositionSource` once, in a field initialiser,
from `getStandingEyeHeight()` (`StalkerEntity.java:330`). Eye height changes with
the crawl, so that source is now stale and the thing will localise noise as though
its head were still 1.7 blocks up.

Rebuild the position source whenever `crawling` flips — in the same branch as the
`calculateDimensions()` call in Step 2. Make the field non-final.

- [ ] **Step 6: select the crawl animations**

In each of the three controllers from Q2, extend the selection:

```java
state -> state.setAndContinue(entity.isCrawling() ? CRAWL_LEGS
        : state.isMoving() ? STALK_LEGS : IDLE_LEGS)
```

- [ ] **Step 7: build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: look at it**

Run: `./gradlew runClient`, `/unseen spawn`, `/unseen phase PEAK`.
Expected: it drops to all fours and comes at you slowly. Then `/unseen phase BUILD_UP`
and confirm it stands back up without the hitbox getting stuck — the classic
failure here is desync between the client's visual state and the server's hitbox,
which shows up as the player being blocked by nothing.

- [ ] **Step 9: verify hearing still works crawling**

In PEAK, break a block ~20 away from it while crouched out of sight.
Expected: it paths to the noise as before. This is the regression Step 5 guards.

- [ ] **Step 10: commit**

```bash
git add src/main/java/com/unseen/entity/StalkerEntity.java src/main/resources/assets/unseen/animations/entity/stalker.animation.json
git commit -m "feat(entity): phase-gated crawl during PEAK"
```

---

## Q8 — Observation-gated freeze · BLOCKED

**Why:** the highest-leverage single behaviour on this list, because it converts
every technique above from "the player watches it move wrong" into "the player
*infers* it moved wrong". It also makes animation quality matter less — motion you
never see does not have to be convincing.

Both primitives already exist: `SanityManager.isLookingAt(player, stalker, coneDegrees)`
(`SanityManager.java:102`) and `player.canSee(stalker)`.

**Blocked on a design decision, not on code.** The existing stare-punishment system
makes the Stalker **vanish** when looked at for `stareTicksBeforeVanish = 12`
(`Config.java:84`). Freeze and vanish are two different monsters and shipping both
on the same entity in the same phase makes it read as neither:

- **Freeze** — an SCP-173. It is a physical thing in the room with you and looking
  away is a mistake. Rewards nerve.
- **Vanish** — a hallucination. It is not reliably there at all and looking at it
  proves nothing. Rewards paranoia. This is what the mod does today, and it is
  what the phantom system (`StalkerEntity#tickPhantom`) is built around.

Three coherent resolutions, in my order of preference:

1. **Split by phase.** Freeze during BUILD_UP, vanish during PEAK. The creature
   changes character as the Director escalates: first a thing you can pin down,
   then a thing you cannot. Most work, best result.
2. **Split by sanity.** Freeze while sanity is high, vanish once it is low, so the
   creature becomes less real as the player becomes less reliable. Fits the
   existing sanity architecture most naturally.
3. **Freeze only, drop stare-vanish.** Cheapest and most coherent, but throws away
   a mechanic that already works and undercuts the phantom system.

Nothing to implement until one is picked. Once picked it is ~30 lines in
`StalkerEntity#tick`: a line-of-sight scan over nearby players, and if any is
looking, zero the velocity, `getNavigation().stop()`, and return before the
movement goals run.

---

## Open questions

- **Does the limp survive Q6?** An asymmetric cycle whose playback rate is being
  scaled by velocity may read as a stutter rather than a limp. Untested. Q1 before
  Q6 is deliberate so this is observable rather than confounded.
- **Is `twitch` still legible after Q2?** It overlaps two of the three new
  controllers' bone sets. Fallback in the Q2 risk note.
- **Is the full geometry-probe crawl worth it after Q7?** Deliberately deferred.
  It is a real project — probes, mixin, dimension sync — and the visual payoff over
  the phase-gated version may be nil.

[cd]: https://github.com/SiverDX/cave_dweller
