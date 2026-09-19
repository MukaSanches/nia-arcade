#!/usr/bin/env bash
set -euo pipefail
ROOT="$(pwd)/.zapptv-build"
WORK="$ROOT/workspace"
rm -rf "$WORK"
mkdir -p "$WORK"

python3 - <<'PY'
import json, pathlib
root = pathlib.Path(".zapptv-build")
work = root / "workspace"
count = 0
for part in sorted(root.glob("part*.json")):
    entries = json.loads(part.read_text(encoding="utf-8"))
    for entry in entries:
        path = work / entry["path"]
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(entry["content"], encoding="utf-8")
        count += 1
print(f"reconstructed {count} files")
PY

if [[ -d "$ROOT/patches" ]]; then
  echo "overlaying SANCHESTV release patches"
  cp -R "$ROOT/patches/." "$WORK/"
fi

cd "$WORK"
chmod +x render-build.sh
bash render-build.sh
