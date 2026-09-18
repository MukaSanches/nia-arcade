#!/usr/bin/env python3
from __future__ import annotations

import concurrent.futures
import json
import re
import shutil
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"
CATALOG = json.loads((ASSETS / "catalog.json").read_text(encoding="utf-8"))

FATAL_JS = re.compile(
    r"(Uncaught\s+(?:TypeError|ReferenceError|SyntaxError|RangeError)|"
    r"Uncaught\s+Error|"
    r"CONSOLE.*(?:TypeError|ReferenceError|SyntaxError))",
    re.IGNORECASE,
)

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
            "--enable-logging=stderr",
            "--log-level=0",
            "--window-size=1920,1080",
            "--virtual-time-budget=1800",
            "--user-data-dir=" + profile,
            "--dump-dom",
            target,
        ]

        try:
            proc = subprocess.run(cmd, capture_output=True, text=True, timeout=20)
        except subprocess.TimeoutExpired:
            return game["slug"], False, "timeout"

        dom = proc.stdout
        stderr = proc.stderr
        checks = {
            "html": "<html" in dom.lower(),
            "ready": 'data-nia-ready="1"' in dom.lower(),
            "ptbr": 'lang="pt-br"' in dom.lower(),
            "bridge": 'id="nia-tv-bridge"' in dom.lower(),
        }

        fatal = FATAL_JS.search(stderr)
        ok = proc.returncode == 0 and all(checks.values()) and fatal is None
        if ok:
            return game["slug"], True, ""

        missing = [k for k, v in checks.items() if not v]
        msg = "missing=" + ",".join(missing)
        if fatal:
            msg += " js=" + fatal.group(0)
        if proc.returncode != 0:
            msg += " rc=" + str(proc.returncode)
        if stderr:
            msg += " stderr=" + stderr[-500:].replace("\n", " ")
        return game["slug"], False, msg

def main() -> int:
    chrome = find_chrome()
    if not chrome:
        print("WEB BOOT NOT VERIFIED: Chrome/Chromium indisponível.")
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
        for slug, msg in failures[:30]:
            print(" -", slug, msg)
        return 1

    print("WEB BOOT VERIFIED: 100/100")
    print("Viewport testado: 1920x1080")
    print("Bridge pronto: 100/100")
    print("pt-BR: 100/100")
    print("Erros JS fatais detectados: 0")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
