#!/usr/bin/env bash
# Add footage to the app at build time.
#
#   tools/add_footage.sh <stretch-id> <file> [angle]
#
#   tools/add_footage.sh quad ~/Videos/quad-front.mov 1
#   tools/add_footage.sh quad ~/Videos/quad-side.mov 2
#
# Clips land in app/src/main/assets/videos/ and are played instead of the drawn figure
# and the illustration. Transcoding normalises whatever the camera produced: H.264 in
# MP4, no audio track, faststart, and a height cap so the APK stays reasonable.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DST="$ROOT/app/src/main/assets/videos"
FFMPEG="$HOME/.hermes/tools/ffmpeg-9.0.1-linux-x64/bin/ffmpeg"
MAX_HEIGHT=1280   # 720p-ish portrait; raise if text/limbs need the detail

if [ $# -lt 2 ]; then
  echo "usage: $0 <stretch-id> <file> [angle]" >&2
  echo "stretch ids:" >&2
  grep -o '"id": "[^"]*"' "$ROOT/app/src/main/assets/stretches.json" | sed 's/"id": //;s/"//g' | sed 's/^/  /' >&2
  exit 2
fi

ID="$1"; SRC="$2"; ANGLE="${3:-}"
[ -f "$SRC" ] || { echo "no such file: $SRC" >&2; exit 1; }
grep -q "\"id\": \"$ID\"" "$ROOT/app/src/main/assets/stretches.json" || {
  echo "'$ID' is not a stretch id in stretches.json" >&2; exit 1; }

mkdir -p "$DST"
OUT="$DST/$ID.mp4"
[ -n "$ANGLE" ] && OUT="$DST/${ID}_${ANGLE}.mp4"

DUR=$("$FFMPEG" -v error -i "$SRC" -show_entries format=duration -of csv=p=0 2>/dev/null | cut -d. -f1 || echo 0)
echo "transcoding $(basename "$SRC") (${DUR}s) -> $(basename "$OUT")"

# No audio, no rotation metadata surprises, and a loop-friendly bitrate for flat footage.
"$FFMPEG" -y -loglevel error -i "$SRC" \
  -vf "scale=-2:'min($MAX_HEIGHT,ih)',fps=30" \
  -c:v libx264 -profile:v high -pix_fmt yuv420p -crf 24 \
  -movflags +faststart -an \
  "$OUT"

SIZE=$(du -h "$OUT" | cut -f1)
echo "added $OUT ($SIZE)"
echo
echo "Current bundled footage:"
ls -la "$DST" | awk 'NR>1 {printf "  %-28s %s\n", $9, $5}'
echo
echo "Now: gradle :app:assembleRelease  — and update docs if you publish."
