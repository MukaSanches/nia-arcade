#!/usr/bin/env python3
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"
CATALOG = ASSETS / "catalog.json"

REMOTE_LINK_RE = re.compile(
    r"""(?:src|href)\s*=\s*["']https?://|url\(\s*["']?https?://""",
    re.IGNORECASE,
)

CPU_PATCH_GAMES = {
    "02-pong",
    "17-connect-four",
    "18-tic-tac-toe",
    "19-checkers",
    "20-chess",
    "35-dots-and-boxes",
    "36-reversi",
    "76-ludo",
    "77-snakes-and-ladders",
}

def main() -> int:
    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    errors = []

    if len(data) != 100:
        errors.append(f"catalog contains {len(data)} games; expected 100")

    ids = [x["id"] for x in data]
    slugs = [x["slug"] for x in data]

    if len(set(ids)) != len(ids):
        errors.append("duplicate game ids")
    if len(set(slugs)) != len(slugs):
        errors.append("duplicate slugs")

    valid_profiles = {"KEYBOARD", "CURSOR"}
    valid_schemes = {"ARROWS", "WASD"}
    valid_play_modes = {"SOLO", "VS_CPU"}
    valid_layouts = {"CANVAS", "BOARD", "CARD", "DOM"}

    for game in data:
        slug = game["slug"]
        path = ASSETS / "games" / slug / "index.html"

        if not path.exists():
            errors.append(slug + ": missing index.html")
            continue

        text = path.read_text(encoding="utf-8")

        if REMOTE_LINK_RE.search(text):
            errors.append(slug + ": remote dependency detected")
        if "nia-input-prelude" not in text:
            errors.append(slug + ": input normalization prelude missing")
        if "nia-tv-bridge" not in text:
            errors.append(slug + ": NIA input bridge missing")
        if "__niaResetViewport" not in text:
            errors.append(slug + ": viewport guard missing")
        if "__niaActivatePrimary" not in text:
            errors.append(slug + ": automatic-start bridge missing")
        if "__niaFitGame" not in text:
            errors.append(slug + ": fullscreen AutoFit missing")

        if game.get("inputProfile") not in valid_profiles:
            errors.append(slug + ": invalid input profile")
        if game.get("directionScheme") not in valid_schemes:
            errors.append(slug + ": invalid direction scheme")
        if game.get("playMode") not in valid_play_modes:
            errors.append(slug + ": invalid play mode")
        if game.get("layoutProfile") not in valid_layouts:
            errors.append(slug + ": invalid layout profile")

        scale = game.get("fitMaxScale")
        padding = game.get("fitPadding")
        if not isinstance(scale, (int, float)) or scale <= 0:
            errors.append(slug + ": invalid fit scale")
        if not isinstance(padding, int) or padding < 0:
            errors.append(slug + ": invalid fit padding")

        if slug in CPU_PATCH_GAMES:
            if game.get("playMode") != "VS_CPU":
                errors.append(slug + ": CPU patch game not marked VS_CPU")
            if 'id="nia-cpu-opponent"' not in text:
                errors.append(slug + ": CPU opponent patch missing")

        if game.get("license") != "MIT":
            errors.append(slug + ": unexpected license")
        if game.get("niaCertified") is not True:
            errors.append(slug + ": not certified")

    license_path = ASSETS / "licenses" / "100htmlgameshub-MIT.txt"
    if not license_path.exists() or "MIT License" not in license_path.read_text(encoding="utf-8"):
        errors.append("MIT license text missing from APK assets")

    if errors:
        print("VALIDATION FAILED")
        for err in errors:
            print(" -", err)
        return 1

    cpu_count = sum(1 for game in data if game.get("playMode") == "VS_CPU")
    canvas_count = sum(1 for game in data if game.get("layoutProfile") == "CANVAS")

    print("VALIDATION PASS")
    print("Catalog: 100")
    print("Offline remote dependencies: 0")
    print("NIA input bridge: 100/100")
    print("Fullscreen AutoFit: 100/100")
    print("Canvas pointer normalization: 100/100")
    print(f"VS CPU games: {cpu_count}")
    print(f"Canvas-specific profiles: {canvas_count}")
    print("License metadata: 100/100")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
