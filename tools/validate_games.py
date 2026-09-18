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

    for game in data:
        path = ASSETS / "games" / game["slug"] / "index.html"
        if not path.exists():
            errors.append(game["slug"] + ": missing index.html")
            continue
        text = path.read_text(encoding="utf-8")
        if REMOTE_LINK_RE.search(text):
            errors.append(game["slug"] + ": remote dependency detected")
        if "nia-tv-bridge" not in text:
            errors.append(game["slug"] + ": NIA input bridge missing")
        if "__niaResetViewport" not in text:
            errors.append(game["slug"] + ": viewport guard missing")
        if "__niaActivatePrimary" not in text:
            errors.append(game["slug"] + ": smart start bridge missing")
        if game.get("inputProfile") not in valid_profiles:
            errors.append(game["slug"] + ": invalid input profile")
        if game.get("directionScheme") not in valid_schemes:
            errors.append(game["slug"] + ": invalid direction scheme")
        if game.get("license") != "MIT":
            errors.append(game["slug"] + ": unexpected license")
        if game.get("niaCertified") is not True:
            errors.append(game["slug"] + ": not certified")

    license_path = ASSETS / "licenses" / "100htmlgameshub-MIT.txt"
    if not license_path.exists() or "MIT License" not in license_path.read_text(encoding="utf-8"):
        errors.append("MIT license text missing from APK assets")

    if errors:
        print("VALIDATION FAILED")
        for err in errors:
            print(" -", err)
        return 1

    print("VALIDATION PASS")
    print("Catalog: 100")
    print("Offline remote dependencies: 0")
    print("NIA input bridge: 100/100")
    print("License metadata: 100/100")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
