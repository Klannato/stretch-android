# Stretch

A small Android app that nudges you to stretch when you wake up and when you go to
bed. Sessions are **five minutes** (5 × 60 seconds), all **standing**, and they
**rotate** through 12 different stretches so tomorrow's set isn't today's.

Each stretch is drawn as an **animated vector stick figure** — no images, no network.

## Download

**https://github.com/Klannato/stretch-android/releases/download/v1.0/app-release.apk**

Open that link in your phone's browser and tap **Install** when the download
finishes. Android flags it as an unknown app: choose **Install anyway** and allow
it from that app. The warning is expected — it's signed with a local key, not a Play
Store key.

If you'd rather build from source, or install over USB, see the [Install](#install)
section below.

## Install

Option A — straight to your phone over USB (the APK is already built):

```bash
adb install -r app-release.apk
```

Option B — copy `app-release.apk` onto the phone, open it in a file manager, and
tap **Install**. Android will warn you because the app doesn't come from the Play
Store: choose **Install anyway**, and if asked, allow installs from that app
("unknown sources"). The app is signed with a throwaway local keystore, so the
warning is expected.

Minimum Android version: **8.0 (API 26)**. Anything newer, including Android 14/15,
works.

## What it does

| Feature | Behaviour |
|---|---|
| Wake and bedtime reminders | Two independent daily alarms (default 7:00 am / 10:30 pm), each with its own switch and time picker |
| Reminder content | A notification with the app's name, a title, and two actions: **Start 5 min** and **Snooze 10 min** |
| Tap the notification | Opens the app straight into the five-minute session for that time of day |
| Session | 5 blocks × 60s. One stretch per block, animated figure, countdown, pause, skip, back |
| Reference loop | A short low-framerate illustration of the same stretch plays beside the figure, so the pose is unambiguous |
| Your own footage | Import clips of a real person; the session plays them instead of the drawings. Several angles per stretch, tap to switch |
| Two-sided stretches | 30 seconds per side, with a "Switch sides" cue in the middle |
| Rotation | A pointer advances by 5 each session. Morning and evening draw from separate pools |
| After a session | A recap list and a running session count on the home screen |

Everything works offline. No accounts, no analytics, no trackers — the app does not
even declare the `INTERNET` permission, so it is incapable of sending anything anywhere.
See **[PRIVACY.md](PRIVACY.md)** for the full audit: what is stored, every permission
and why, and how the shipped APK was checked.

## First-run permissions

1. **Notifications** — Android asks the first time you open the app. Say yes, or the
   reminders silently won't appear. You can change it later in *Settings → Apps →
   Stretch → Notifications*.
2. **Precise alarms** — the app schedules the reminder exactly at the time you set.
   Android 12+ can throttle this; the app shows a notice on the home screen if it
   detects throttling, with a button that opens the right settings page.

If a nudge arrives late or not at all, also check
*Settings → Apps → Stretch → Battery* and set it to **Unrestricted**.

## Stretches

1. Overhead side bend (both sides)
2. Neck side release (both sides)
3. Cross-body shoulder (both sides)
4. Chest opener
5. Overhead triceps (both sides)
6. Side lunge (both sides)
7. Standing hip flexor (both sides)
8. Standing quad (both sides)
9. Calf press at a wall (both sides)
10. Standing figure-four (both sides)
11. Standing twist
12. Sunrise reach

## Project layout

```
app/src/main/assets/stretches.json     # all pose data (names, cues, limb angles)
app/src/main/java/com/nous/stretchup/
  MainActivity.kt        # activity, permission flow, root composable
  AppViewModel.kt        # settings + session state
  Reminders.kt           # alarm scheduling, notification building
  ReminderReceiver.kt    # fires the notification, re-arms the next day
  BootReceiver.kt        # re-arms after reboot / app update
  model/Stretch.kt       # pose data classes, JSON loader, angle interpolation
  session/SessionPlanner.kt  # 5×60s plan, rotation pointer, block timing
  ui/StickFigure.kt      # forward kinematics + Compose Canvas renderer
  ui/HomeScreen.kt, ui/SessionScreen.kt, ui/Theme.kt
```

Poses are authored as **angles, not pixel coordinates**: each limb is a direction in
degrees (0 = right, 90 = down, 270 = up), and the renderer walks outward from the hip
to place each joint. To change or add a stretch, edit `stretches.json` and rebuild —
no code changes needed.

The skeleton has a bend point in the spine, so fields are:

| field | meaning |
|---|---|
| `torso` | hip → lumbar direction |
| `spine` | lumbar → neck direction (defaults to `torso`). A side bend is `torso` upright with `spine` tilted |
| `pelvis`, `shoulders` | width of the hip bar and chest bar |
| `pelvisAngle`, `chestAngle` | bar directions, defaulting to `torso`/`spine`. Differing values are what make a twist visible |
| `footL`, `footR` | foot direction in degrees |
| `armL/armR`, `legL/legR` | `[proximal, distal]` directions. `legL` hangs from the hip on screen-right, so each leg must angle *outward* or they cross into an X |

`PoseGeometryTest` reads this file and asserts the invariants (both feet never cross
at once, hands stay off the floor), which is what caught a pair of swapped legs.

## Adding footage

Footage is added **at build time**, so the shipped app needs no permissions, no file
picker and no storage access — a clip is just an asset:

```bash
tools/add_footage.sh quad ~/Videos/quad-front.mov 1     # front angle
tools/add_footage.sh quad ~/Videos/quad-side.mov 2      # second angle
gradle :app:assembleRelease
```

The script transcodes whatever the camera produced into H.264/MP4 with no audio track,
`+faststart`, and a 1280 px height cap, so an imported 4K phone clip doesn't balloon
the APK. Files land in `app/src/main/assets/videos/<stretch-id>[_<angle>].mp4`; drop
them there by hand if you prefer. Nothing else to configure — the session screen picks
up a clip automatically and plays it instead of the drawings.

Stretches with no footage keep the drawn figure and the illustration, so you can film
them one at a time.

### Runtime import (debug builds only)

Debug builds also carry an **Your own footage** card for trying a clip without
rebuilding. Files named after a stretch (`side-bend.mp4`, `side-bend_2.mp4`) are matched
automatically; anything else can be assigned by hand. Release builds omit this card
entirely — it is a development aid, not an end-user feature.

Whatever the source, footage plays silent and looping, cropped to fill the card. With
more than one angle, the label in the corner cycles them on tap.

Notes:

- The app stores only a `content://` reference — your video is **not copied** into the
  app, so it stays wherever you filed it. Deleting or moving the original breaks the
  link, and the app falls back to the drawn figure rather than showing a black box.
- Access is persisted across reboots via a persisted URI grant.
- `docs/guide/*.mp4` are **filming guides**, not footage: a 6-second loop of the
  pose from the pose data, with the cue and the angles to shoot. They live outside the
  app on purpose, so they don't inflate the APK. Regenerate with
  `python3 tools/render_reference_videos.py`.

## Reference illustrations

`app/src/main/assets/ref/*.webp` are 12 low-framerate loops (3 keyframes, ping-ponged
at 3 fps, 156 KB total). They are AI-generated flat illustrations, not photographs or
third-party artwork — regenerate or delete them freely; the app hides the panel when
an asset is missing.

To rebuild them from keyframes in `tmp/keyframes/<id>_1..3.png`:

```bash
python3 tools/build_reference_loops.py     # needs Pillow + ffmpeg
```

The script crops every loop to the union of its three keyframes (cropping per frame
makes the loop jitter) and pads to a 3:4 panel.

## Build

The toolchain lives in `~/.hermes/tools` because this machine has no system JDK and
no passwordless sudo. Point the build at it:

```bash
export JAVA_HOME=$HOME/.hermes/tools/jdk-17
export ANDROID_HOME=$HOME/.hermes/tools/Android/Sdk
export PATH="$JAVA_HOME/bin:$PATH"
$HOME/.hermes/tools/gradle-8.9/bin/gradle --no-daemon :app:assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`.

Tests (pure JVM, no device needed):

```bash
$HOME/.hermes/tools/gradle-8.9/bin/gradle --no-daemon :app:testDebugUnitTest
```

Release APKs are signed with a throwaway keystore kept outside this repository
(`keystore/stretch.jks`, plus its credentials in the gitignored
`keystore/keystore.properties`). A fresh clone therefore builds debug builds only —
copy that directory in to sign releases. Keep the key if you ever want to update the
published app, otherwise Android will refuse the update.

## Notes on reminders

- Alarms are re-armed every time one fires, so you get one nudge per day per slot.
- They survive reboots and app updates via the boot receiver.
- Turning a reminder off and on again re-arms it for the next occurrence.
