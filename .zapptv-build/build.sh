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
current_parts = sorted(root.glob("current-part*.json"))
parts = current_parts if current_parts else sorted(root.glob("part*.json"))

if not parts:
    raise SystemExit("No SANCHESTV source parts found")

count = 0
for part in parts:
    entries = json.loads(part.read_text(encoding="utf-8"))
    for entry in entries:
        path = work / entry["path"]
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(entry["content"], encoding="utf-8")
        count += 1

mode = "current snapshot" if current_parts else "legacy snapshot"
print(f"reconstructed {count} files from {mode}")
PY

# Legacy patches are only needed by the old relay snapshot. The current snapshot
# is complete and must not be overwritten by stale patch files.
if ! compgen -G "$ROOT/current-part*.json" >/dev/null && [[ -d "$ROOT/patches" ]]; then
  echo "overlaying legacy SANCHESTV release patches"
  cp -R "$ROOT/patches/." "$WORK/"
fi

cd "$WORK"
chmod +x render-build.sh
bash render-build.sh
