#!/usr/bin/env python3
"""Render one reference video per stretch, for use as a filming guide.

  app/src/main/assets/stretches.json  ->  docs/guide/<id>.mp4

Each clip is a single breathing cycle of the pose (start -> stretched -> start), so it
shows the full range of motion rather than one frozen frame. Text on screen carries the
stretch name, the cue, and the angles to record, because these clips are meant to be
read while setting up a camera.
"""
import json
import math
import pathlib
import subprocess

from PIL import Image, ImageDraw, ImageFont

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "app" / "src" / "main" / "assets" / "stretches.json"
OUT = ROOT / "docs" / "guide"
FRAMES = ROOT / "tmp" / "guideframes"
FFMPEG = pathlib.Path.home() / ".hermes/tools/ffmpeg-9.0.1-linux-x64/bin/ffmpeg"

# --- skeleton proportions: must match ui/StickFigure.kt -----------------------
TORSO_LOW, TORSO_UP = 26, 22
UPPER_ARM, FOREARM = 24, 22
THIGH, SHIN = 26, 26
NECK_HEAD, HEAD_R, FOOT = 13, 8.5, 9

W, H = 600, 800
FPS = 15
DURATION = 6.0
PAPER = (247, 243, 236)
INK = (31, 42, 38)
ACCENT = (31, 111, 92)
HAIRLINE = (227, 221, 210)
MUTED = (110, 122, 116)

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"


def unit(deg):
    r = math.radians(deg)
    return (math.cos(r), math.sin(r))


def lerp(a, b, t):
    d = (b - a) % 360
    if d > 180:
        d -= 360
    if d < -180:
        d += 360
    return a + d * t


def lerp_pose(a, b, t):
    out = {}
    for k in ("torso", "spine", "head", "footL", "footR"):
        out[k] = lerp(a.get(k, a["torso"] if k == "spine" else 0), b.get(k, b["torso"] if k == "spine" else 0), t)
    for k in ("pelvis", "shoulders"):
        out[k] = a.get(k, 9 if k == "pelvis" else 10) + (
            b.get(k, 9 if k == "pelvis" else 10) - a.get(k, 9 if k == "pelvis" else 10)
        ) * t
    for k in ("armL", "armR", "legL", "legR"):
        out[k] = [lerp(a[k][0], b[k][0], t), lerp(a[k][1], b[k][1], t)]
    out["hipY"] = a.get("hipY", 0) + (b.get("hipY", 0) - a.get("hipY", 0)) * t
    out["hipX"] = a.get("hipX", 0) + (b.get("hipX", 0) - a.get("hipX", 0)) * t
    out["pelvisAngle"] = a.get("pelvisAngle") if a.get("pelvisAngle") is not None else out["torso"]
    if b.get("pelvisAngle") is not None and a.get("pelvisAngle") is not None:
        out["pelvisAngle"] = lerp(a["pelvisAngle"], b["pelvisAngle"], t)
    out["chestAngle"] = a.get("chestAngle") if a.get("chestAngle") is not None else out["spine"]
    if b.get("chestAngle") is not None and a.get("chestAngle") is not None:
        out["chestAngle"] = lerp(a["chestAngle"], b["chestAngle"], t)
    return out


def joints(p):
    perp = lambda v: (-v[1], v[0])
    hip = (p.get("hipX", 0), -p.get("hipY", 0))
    lumbar = (hip[0] + unit(p["torso"])[0] * TORSO_LOW, hip[1] + unit(p["torso"])[1] * TORSO_LOW)
    neck = (lumbar[0] + unit(p["spine"])[0] * TORSO_UP, lumbar[1] + unit(p["spine"])[1] * TORSO_UP)
    head = (neck[0] + unit(p["head"])[0] * (NECK_HEAD + HEAD_R),
            neck[1] + unit(p["head"])[1] * (NECK_HEAD + HEAD_R))
    pd = unit(p.get("pelvisAngle") if p.get("pelvisAngle") is not None else p.get("spine", p["torso"]))
    cd = unit(p.get("chestAngle") if p.get("chestAngle") is not None else p.get("spine", p["torso"]))
    pw, sw = p.get("pelvis", 9) / 2, p.get("shoulders", 10) / 2
    hipL = (hip[0] + perp(pd)[0] * pw, hip[1] + perp(pd)[1] * pw)
    hipR = (hip[0] - perp(pd)[0] * pw, hip[1] - perp(pd)[1] * pw)
    shR = (neck[0] + perp(cd)[0] * sw, neck[1] + perp(cd)[1] * sw)
    shL = (neck[0] - perp(cd)[0] * sw, neck[1] - perp(cd)[1] * sw)

    def limb(start, a_deg, a_len, b_deg, b_len):
        mid = (start[0] + unit(a_deg)[0] * a_len, start[1] + unit(a_deg)[1] * a_len)
        end = (mid[0] + unit(b_deg)[0] * b_len, mid[1] + unit(b_deg)[1] * b_len)
        return mid, end

    eL, hL = limb(shL, p["armL"][0], UPPER_ARM, p["armL"][1], FOREARM)
    eR, hR = limb(shR, p["armR"][0], UPPER_ARM, p["armR"][1], FOREARM)
    kL, aL = limb(hipL, p["legL"][0], THIGH, p["legL"][1], SHIN)
    kR, aR = limb(hipR, p["legR"][0], THIGH, p["legR"][1], SHIN)
    toeL = (aL[0] + unit(p.get("footL", 180))[0] * FOOT, aL[1] + unit(p.get("footL", 180))[1] * FOOT)
    toeR = (aR[0] + unit(p.get("footR", 0))[0] * FOOT, aR[1] + unit(p.get("footR", 0))[1] * FOOT)
    return dict(hip=hip, lumbar=lumbar, neck=neck, head=head, hipL=hipL, hipR=hipR,
                shL=shL, shR=shR, eL=eL, hL=hL, eR=eR, hR=hR, kL=kL, aL=aL, kR=kR, aR=aR,
                toeL=toeL, toeR=toeR)


def bounds(poses):
    pts = []
    for p in poses:
        for mirrored in (False, True):
            for v in joints(p).values():
                pts.append((-v[0], v[1]) if mirrored else v)
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    return min(xs), max(xs), min(ys), max(ys)


def wrap(draw, text, font, max_w):
    words, lines, cur = text.split(), [], ""
    for w in words:
        trial = f"{cur} {w}".strip()
        if draw.textlength(trial, font=font) <= max_w:
            cur = trial
        else:
            lines.append(cur)
            cur = w
    if cur:
        lines.append(cur)
    return lines


def draw_frame(p, mirrored, layout, fonts):
    name_font, cue_font, small_font = fonts
    img = Image.new("RGB", (W, H), PAPER)
    d = ImageDraw.Draw(img)

    d.text((W / 2, 40), layout["name"], font=name_font, fill=INK, anchor="ma")
    d.line([(40, 68), (W - 40, 68)], fill=HAIRLINE, width=2)

    scale, foot_y = layout["scale"], layout["footY"]
    cx = W / 2
    j = joints(p)

    def P(o):
        x = -o[0] if mirrored else o[0]
        return (cx + (x - layout["centerX"]) * scale, foot_y + (o[1] - layout["maxY"]) * scale)

    sw = max(2.0, scale * 0.052)
    seg = lambda a, b, w=sw: d.line([P(a), P(b)], fill=INK, width=max(2, round(w)), joint="curve")
    seg(j["hip"], j["lumbar"])
    seg(j["lumbar"], j["neck"])
    seg(j["hipL"], j["hipR"], sw * 0.8)
    seg(j["shL"], j["shR"], sw * 0.85)
    seg(j["neck"], (j["neck"][0] + (j["head"][0] - j["neck"][0]) * 0.55,
                    j["neck"][1] + (j["head"][1] - j["neck"][1]) * 0.55))
    seg(j["shL"], j["eL"]); seg(j["eL"], j["hL"])
    seg(j["shR"], j["eR"]); seg(j["eR"], j["hR"])
    seg(j["hipL"], j["kL"]); seg(j["kL"], j["aL"])
    seg(j["hipR"], j["kR"]); seg(j["kR"], j["aR"])
    seg(j["aL"], j["toeL"]); seg(j["aR"], j["toeR"])
    for pt in (j["hL"], j["hR"]):
        r = max(1.5, scale * 0.034)
        x, y = P(pt)
        d.ellipse([x - r, y - r, x + r, y + r], fill=ACCENT)
    hx, hy = P(j["head"])
    d.ellipse([hx - HEAD_R * scale, hy - HEAD_R * scale, hx + HEAD_R * scale, hy + HEAD_R * scale],
              outline=INK, width=max(2, round(sw * 0.95)))

    d.line([(50, foot_y), (W - 50, foot_y)], fill=INK, width=2)

    y = H - 175
    for line in wrap(d, layout["cue"], cue_font, W - 80):
        d.text((W / 2, y), line, font=cue_font, fill=MUTED, anchor="ma")
        y += 22
    d.line([(40, H - 56), (W - 40, H - 56)], fill=HAIRLINE, width=2)
    d.text((W / 2, H - 34), "film: front  ·  side  ·  three-quarter", font=small_font, fill=MUTED, anchor="ma")
    return img


def main():
    data = json.loads(SRC.read_text())
    OUT.mkdir(parents=True, exist_ok=True)
    FRAMES.mkdir(parents=True, exist_ok=True)
    fonts = (ImageFont.truetype(FONT_BOLD, 30), ImageFont.truetype(FONT_REG, 17),
             ImageFont.truetype(FONT_REG, 15))

    for st in data["stretches"]:
        poses = [st["poses"][0], st["poses"][1]]
        minx, maxx, miny, maxy = bounds(poses)
        cx = (minx + maxx) / 2
        avail_w, avail_h = W * 0.80, 470
        scale = min(avail_w / (maxx - minx + 12), avail_h / (maxy - miny + 12))
        layout = dict(name=st["name"], cue=st["cue"], scale=scale, centerX=cx, maxY=maxy,
                      footY=600)
        n_frames = int(FPS * DURATION)
        for i in range(n_frames):
            x = i / n_frames
            t = (1 - math.cos(2 * math.pi * x)) / 2
            pose = lerp_pose(poses[0], poses[1], t)
            draw_frame(pose, st["sides"] == "two" and x > 0.5, layout, fonts).save(
                FRAMES / f"{st['id']}_{i:03d}.png")
        mp4 = OUT / f"{st['id']}.mp4"
        subprocess.run([
            str(FFMPEG), "-y", "-loglevel", "error", "-framerate", str(FPS),
            "-i", str(FRAMES / f"{st['id']}_%03d.png"),
            "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "26", "-movflags", "+faststart",
            str(mp4),
        ], check=True)
        print(f"{st['name']:22} {mp4.stat().st_size // 1024:5} KB")


if __name__ == "__main__":
    main()
