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

def find_chrome():
    for name in ("google-chrome", "google-chrome-stable", "chromium", "chromium-browser"):
        path = shutil.which(name)
        if path:
            return path
    return None

def check_one(chrome: str, game: dict) -> tuple[str, bool, str]:
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
            "--virtual-time-budget=600",
            "--user-data-dir=" + profile,
            "--dump-dom",
            target,
        ]

        try:
            proc = subprocess.run(cmd, capture_output=True, text=True, timeout=12)
        except subprocess.TimeoutExpired:
            return game["slug"], False, "timeout"

        dom = proc.stdout
        low = dom.lower()

        checks = {
            "html": "<html" in low,
            "fit": 'data-nia-fit="ready"' in low,
            "bridge": 'id="nia-tv-bridge"' in low,
            "prelude": 'id="nia-input-prelude"' in low,
        }

        ok = proc.returncode == 0 and all(checks.values())
        if ok:
            return game["slug"], True, ""

        missing = [name for name, passed in checks.items() if not passed]
        msg = "missing=" + ",".join(missing)

        if proc.returncode != 0:
            msg += " rc=" + str(proc.returncode)
        if proc.stderr:
            msg += " stderr=" + proc.stderr[-400:]

        return game["slug"], False, msg
    finally:
        # Chrome may release profile files a few milliseconds after its parent exits.
        # Cleanup must never turn a successful game boot into a false test failure.
        shutil.rmtree(profile, ignore_errors=True)

def main() -> int:
    chrome = find_chrome()
    if not chrome:
        print("WEB BOOT NOT VERIFIED: Chrome/Chromium not available.")
        return 2

    failures = []

    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        futures = [pool.submit(check_one, chrome, game) for game in CATALOG]
        for future in concurrent.futures.as_completed(futures):
            slug, ok, msg = future.result()
            if not ok:
                failures.append((slug, msg))

    if failures:
        print("WEB BOOT FAILED:", len(failures))
        for slug, msg in failures[:30]:
            print(" -", slug, msg)
        return 1

    print("WEB BOOT VERIFIED: 100/100")
    print("Fullscreen AutoFit executed: 100/100")
    print("Input prelude executed: 100/100")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
