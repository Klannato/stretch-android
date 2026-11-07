# HANDOFF: Vector poses for Stretch app

## Goal
Bring back **vector (drawn) poses** for each stretch in the Stretch Android app
(repo root, branch `main`, remote `Klannato/stretch-android`).

The app currently plays a video clip per stretch (v2.0). The user wants a drawn
stick-figure pose shown alongside/instead of footage, so a stretch is visible even
with no clip filmed yet. Claude Code should design + implement these vector poses.

## Project context
- Kotlin + Jetpack Compose, min SDK 26, target/compile 34.
- Build: `./gradlew assembleDebug` (Gradle 8.9, Java 17). Needs a JDK 17 + Android SDK 34.
- Key files: `app/src/main/java/com/nous/stretchup/model/Stretch.kt`,
  `app/src/main/assets/stretches.json` (12 stretches, ids listed below),
  `app/src/main/java/com/nous/stretchup/ui/SessionScreen.kt`.
- Tests: `app/src/test/...` (run `./gradlew test`).

## Stretches (12)
side-bend, neck-side, shoulder-cross, chest-open, triceps, side-lunge, hip-flexor,
quad, calf, fig-four, twist, sunrise. Each has `twoSided` (most true).

## History you MUST read first
v1.1 (`a55e410`) introduced a stick-figure renderer + pose model + reference
illustrations; v2.0 (`64fb19f`) deleted them ("video only"). The deleted code is
the best spec for the pose format and geometry invariants.

Recover the old implementation from git (do NOT recreate from scratch blindly):
```
git show a55e410:app/src/main/java/com/nous/stretchup/ui/StickFigure.kt
git show a55e410:app/src/test/java/com/nous/stretchup/PoseGeometryTest.kt
git show a55e410 -- app/src/main/java/com/nous/stretchup/model/Stretch.kt
git diff 64fb19f^ 64fb19f -- app/src/main/java/com/nous/stretchup/model/Stretch.kt
```
Also inspect the v1.1 poses data embedded in stretches.json at that commit
(`git show a55e410:app/src/main/assets/stretches.json`).

## What "vector poses" means here
- A pose = a stick figure (spine with lumbar bend, two hip joints, pelvis/chest
  bars, feet with direction, limb thickness following canvas scale) in a specific
  stretch position.
- Each stretch needs one pose (two-sided stretches may need a mirrored variant).
- Rendered in Compose Canvas, offline, no network.
- Should integrate into SessionScreen where a missing clip currently shows
  "name + cue + how to add footage" — replace/augment with the drawn pose.
- Keep PoseGeometryTest-style invariants (e.g. side-lunge legs not crossing).

## Reference for validating poses
Claude must check work against **real human reference**, not just geometry:
- `docs/reference-videos.md` lists one primary + one alternative YouTube video per stretch.
- For each pose, open the reference video, confirm the limb arrangement, joint angles,
  and which side is loaded match a real person performing the stretch.
- If a pose looks wrong vs. the video, fix the pose data — do not weaken invariants.
- Optional: extract a keyframe (`yt-dlp` + ffmpeg) into `tmp/` for side-by-side
  comparison; `tmp/` is gitignored.
- No INTERNET permission (app must stay offline).
- Don't break session/rotation/reminder logic.
- Keep existing tests green; add PoseGeometryTest back.
- Prefer vector drawables / Compose Canvas over raster.

## Suggested approach for Claude
1. Read the recovered v1.1 code + pose data to understand the model.
2. Re-introduce a `Pose`/`Limb` model and `StickFigure.kt` renderer.
3. Add pose data per stretch (in stretches.json or a Kotlin file).
4. Wire into SessionScreen fallback (and optionally as primary).
5. Restore PoseGeometryTest with invariants.
6. Build + run tests; fix compile errors.

## Notes
- User prefers GUI editors; avoid interactive prompts.
- Report cost/changes. Treat restarts as free.
- Remote: Sam pushes directly to master sometimes — `git fetch` first. (Here branch is `main`.)
