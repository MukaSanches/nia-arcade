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
    with tempfile.TemporaryDirectory(prefix="nia-chrome-", ignore_cleanup_errors=True) as profile:
        cmd = [
            chrome,
            "--headless=new",
            "--no-sandbox",
            "--disable-gpu",
            "--disable-dev-shm-usage",
            "--allow-file-access-from-files",
            "--user-data-dir=" + profile,
            "--dump-dom",
            target,
        ]
        try:
            proc = subprocess.run(cmd, capture_output=True, text=True, timeout=12)
        except subprocess.TimeoutExpired:
            return game["slug"], False, "timeout"
        ok = proc.returncode == 0 and "<html" in proc.stdout.lower()
        msg = "" if ok else (proc.stderr[-500:] or "no DOM")
        return game["slug"], ok, msg

def main() -> int:
    chrome = find_chrome()
    if not chrome:
        print("WEB BOOT NOT VERIFIED: Chrome/Chromium not available.")
        return 2

    failures = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        futures = [pool.submit(check_one, chrome, game) for game in CATALOG]
        for future in concurrent.futures.as_completed(futures):
            slug, ok, msg = future.result()
            if not ok:
                failures.append((slug, msg))

    if failures:
        print("WEB BOOT FAILED:", len(failures))
        for slug, msg in failures[:20]:
            print(" -", slug, msg)
        return 1

    print("WEB BOOT VERIFIED: 100/100 games produced a DOM in headless Chrome.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
