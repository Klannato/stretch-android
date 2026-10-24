# Reference videos

Links to watch **before filming**, to see each stretch performed by a real person.
One primary and one alternative per stretch. All URLs were checked to resolve to the
title shown; the form in each video is the channel's, not ours — judge what fits the cue.

| Stretch | Primary | Alternative |
|---|---|---|
| Overhead side bend | [Standing Side Bend — FITBODY](https://www.youtube.com/watch?v=gTEBlnqcJ2c) | [Standing Overhead Side Bend — Timrileytraining](https://www.youtube.com/watch?v=MppP0BSxB6g) |
| Neck side release | [Ear to Shoulder Stretch — Dani Winks](https://www.youtube.com/watch?v=UbhOswck_yQ) | [Standing neck stretches — Due West](https://www.youtube.com/watch?v=N0PQ_QAc7L4) |
| Cross-body shoulder | [Standing Cross Body Shoulder Stretch — Live Lean TV](https://www.youtube.com/watch?v=q1QZ3h8HVtw) | [Cross Body Shoulder Stretch Tutorial — Denique Scott](https://www.youtube.com/watch?v=1Sfl3iYM1Jg) |
| Chest opener | [Standing Chest Opener — Becca Sagall](https://www.youtube.com/watch?v=fnUlfzHS7zM) | [Standing Chest Openers — Dr. Dawn Thomas PT](https://www.youtube.com/watch?v=NvhaqIhCV8w) |
| Overhead triceps | [Overhead Triceps Stretch — PureGym](https://www.youtube.com/watch?v=cPTrm13hSSo) | [Triceps Brachii Stretch — Human Movement Institute](https://www.youtube.com/watch?v=2w2IOcyhmOI) |
| Side lunge | [Supported Side Lunge — Human Movement Institute](https://www.youtube.com/watch?v=SmU0YMpPoAE) | [Side Lunge Stretch — Sears](https://www.youtube.com/watch?v=HHWuo86fbC4) |
| Standing hip flexor | [Standing Hip Flexor Stretch — Glen Abbey Physiotherapy](https://www.youtube.com/watch?v=fuOhzHlCidc) | [Standing Hip Flexor — VTS Performance](https://www.youtube.com/watch?v=dhVK4nsFSnQ) |
| Standing quad | [Standing Quad Stretch — PureGym](https://www.youtube.com/watch?v=kzAsm4WQqvQ) | [Standing Quad Stretch — Home Rat](https://www.youtube.com/watch?v=9pDrGoHMMms) |
| Calf press | [Standing Wall Calf Stretch — Slim League](https://www.youtube.com/watch?v=xixeCeyz3ZA) | [Calf Stretch — Louis Johnson](https://www.youtube.com/watch?v=J5-4ZB6dmKc) |
| Standing figure-four | [Standing Figure 4 Stretch — ShirleyT](https://www.youtube.com/watch?v=2hcWWdv7m-A) | [Standing Figure Four — FITBODY](https://www.youtube.com/watch?v=w0jShoWHMEA) |
| Standing twist | [Standing Torso Twist — Sears](https://www.youtube.com/watch?v=QQMri8bsjy8) | [Torso Twist Tutorial](https://www.youtube.com/watch?v=kx1uTFke-dI) |
| Sunrise reach | [Standing Overhead Reach — Live Lean TV](https://www.youtube.com/watch?v=_JGWt2BDCl4) | [Standing Calf Raise — fordummies](https://www.youtube.com/watch?v=6TRSvejbQ3c) |

## Filming notes

- **Hip flexor and calf press need a side angle.** A front-on view hides the whole point
  (back heel lifted, pelvis tucked, rear leg straight). Shoot at least one profile.
- **Two-sided stretches** (most of them) only need one clip if it shows both sides, since
  the app splits the 60-second block in half and cues the switch. If your clip covers
  only one side, film both and add them as angles 1 and 2.
- Keep the camera at roughly hip height and the whole body in frame — the player crops to
  fill, so anything near the edge gets cut.
- 15–30 seconds of the held position is plenty; the clip loops for the whole block.

## Adding the clips

```bash
tools/add_footage.sh side-bend ~/clips/side-bend-front.mov 1
tools/add_footage.sh side-bend ~/clips/side-bend-side.mov 2
gradle :app:assembleRelease
```
