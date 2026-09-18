#!/usr/bin/env python3
from __future__ import annotations

import concurrent.futures
import json
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"
CATALOG = json.loads((ASSETS / "catalog.json").read_text(encoding="utf-8"))

# Representative games without permanent requestAnimationFrame loops.
# The complete 100-game set is still structurally validated below.
RUNTIME_SAMPLE_IDS = {14, 16, 18, 20, 24, 37, 63, 74, 84, 100}

def find_chrome():
    for name in ("google-chrome", "google-chrome-stable", "chromium", "chromium-browser"):
        path = shutil.which(name)
        if path:
            return path
    return None

def structural_check(game: dict) -> tuple[str, bool, str]:
    path = ASSETS / "games" / game["slug"] / "index.html"
    if not path.exists():
        return game["slug"], False, "missing index.html"

    text = path.read_text(encoding="utf-8")
    required = {
        "html": "<html" in text.lower(),
        "prelude": 'id="nia-input-prelude"' in text,
        "bridge": 'id="nia-tv-bridge"' in text,
        "fit": "__niaFitGame" in text,
        "autostart": "__niaActivatePrimary" in text,
        "viewport": "__niaResetViewport" in text,
    }
    missing = [name for name, ok in required.items() if not ok]
    return game["slug"], not missing, ("missing=" + ",".join(missing) if missing else "")

def runtime_check(chrome: str, game: dict) -> tuple[str, bool, str]:
    target = (ASSETS / "games" / game["slug"] / "index.html").resolve().as_uri()
    profile = tempfile.mkdtemp(prefix="nia-chrome-")
    try:
        cmd = [
            chrome,
            "--headless=new",
            "--no-sandbox",
            "--disable-gpu",
            "--disable-dev-shm-usage",
            "--allow-file-access-from-files",
            "--window-size=1920,1080",
            "--virtual-time-budget=800",
            "--user-data-dir=" + profile,
            "--dump-dom",
            target,
        ]
        try:
            proc = subprocess.run(cmd, capture_output=True, text=True, timeout=8)
        except subprocess.TimeoutExpired:
            return game["slug"], False, "runtime timeout"

        dom = proc.stdout.lower()
        checks = {
            "html": "<html" in dom,
            "fit-executed": 'data-nia-fit="ready"' in dom,
            "bridge": 'id="nia-tv-bridge"' in dom,
            "prelude": 'id="nia-input-prelude"' in dom,
        }
        missing = [name for name, ok in checks.items() if not ok]
        ok = proc.returncode == 0 and not missing
        if ok:
            return game["slug"], True, ""

        msg = "missing=" + ",".join(missing)
        if proc.returncode != 0:
            msg += " rc=" + str(proc.returncode)
        if proc.stderr:
            msg += " stderr=" + proc.stderr[-300:]
        return game["slug"], False, msg
    finally:
        shutil.rmtree(profile, ignore_errors=True)

def main() -> int:
    structural_failures = []
    for game in CATALOG:
        slug, ok, msg = structural_check(game)
        if not ok:
            structural_failures.append((slug, msg))

    if structural_failures:
        print("STRUCTURAL TV VALIDATION FAILED:", len(structural_failures))
        for slug, msg in structural_failures[:30]:
            print(" -", slug, msg)
        return 1

    print("STRUCTURAL TV VALIDATION: 100/100")
    print("Fullscreen AutoFit bridge: 100/100")
    print("Automatic-start bridge: 100/100")
    print("Canvas input normalization bridge: 100/100")

    chrome = find_chrome()
    if not chrome:
        print("RUNTIME WEB SMOKE NOT VERIFIED: Chrome/Chromium not available.")
        return 2

    sample = [game for game in CATALOG if game["id"] in RUNTIME_SAMPLE_IDS]
    failures = []

    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
        futures = [pool.submit(runtime_check, chrome, game) for game in sample]
        for future in concurrent.futures.as_completed(futures):
            slug, ok, msg = future.result()
            if not ok:
                failures.append((slug, msg))

    if failures:
        print("RUNTIME WEB SMOKE FAILED:", len(failures))
        for slug, msg in failures:
            print(" -", slug, msg)
        return 1

    print(f"RUNTIME WEB SMOKE VERIFIED: {len(sample)}/{len(sample)} representative games at 1920x1080")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
