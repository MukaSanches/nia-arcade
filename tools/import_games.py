#!/usr/bin/env python3
from __future__ import annotations

import html
import json
import re
import shutil
import sys
from pathlib import Path

UPSTREAM_REPO = "https://github.com/shendds/100htmlgameshub"
UPSTREAM_COMMIT = "cdf426cc619f3c713af1d73d485112444bace7c3"
EXPECTED_GAMES = 100

SAFE_NAMES = {
    "Pong": "Paddle Duel",
    "Breakout": "Brick Breaker",
    "Tetris": "Block Stack",
    "Space Invaders": "Star Defender",
    "Pac-Man": "Maze Chaser",
    "Frogger": "Road Hopper",
    "Asteroids": "Space Rocks",
    "Flappy Bird": "Sky Flap",
    "Whack-a-Mole": "Mole Smash",
    "Simon Says": "Memory Lights",
    "Duck Hunt": "Target Hunt",
    "Uno": "Color Cards",
    "Connect Four": "Four in a Row",
    "Battleship": "Fleet Battle",
    "Candy Crush": "Match Three",
    "Fruit Ninja": "Fruit Slice",
    "Doodle Jump": "Sky Jumper",
    "Temple Run": "Temple Dash",
    "Crossy Road": "Crossy Lane",
    "Angry Birds": "Bird Sling",
}

REMOTE_LINK_RE = re.compile(
    r"""(?:src|href)\s*=\s*["']https?://|url\(\s*["']?https?://""",
    re.IGNORECASE,
)

GAME_ROW_RE = re.compile(
    r"""\{\s*id:\s*(\d+),\s*name:\s*"([^"]+)",\s*icon:\s*"([^"]+)",\s*cat:\s*"([^"]+)"[^}]*ready:\s*true\s*\}"""
)

BRIDGE = r"""
<style id="nia-tv-bridge-style">
  .back, a[href="../../index.html"], a[href*="../index.html"] { display:none !important; }
  #__nia_cursor {
    position:fixed; left:50%; top:50%; width:24px; height:24px;
    margin:-12px 0 0 -12px; border:3px solid #38E8FF; border-radius:50%;
    box-shadow:0 0 0 2px rgba(9,11,16,.85), 0 0 18px rgba(56,232,255,.8);
    z-index:2147483647; pointer-events:none; display:none;
    transition:left .04s linear, top .04s linear;
  }
</style>
<div id="__nia_cursor" aria-hidden="true"></div>
<script id="nia-tv-bridge">
(function () {
  "use strict";
  var profile = "__NIA_PROFILE__";
  var cursor = document.getElementById("__nia_cursor");
  var x = Math.max(20, window.innerWidth / 2);
  var y = Math.max(20, window.innerHeight / 2);

  function visible(el) {
    if (!el) return false;
    var r = el.getBoundingClientRect();
    var s = window.getComputedStyle(el);
    return r.width > 0 && r.height > 0 && s.display !== "none" && s.visibility !== "hidden";
  }

  function update() {
    if (!cursor) return;
    cursor.style.left = x + "px";
    cursor.style.top = y + "px";
  }

  function firstActionable() {
    var list = document.querySelectorAll("button, [role=button], input[type=button], input[type=submit], .btn");
    for (var i = 0; i < list.length; i++) if (visible(list[i])) return list[i];
    return null;
  }

  window.__niaCursorMove = function (dx, dy) {
    x = Math.min(Math.max(16, x + dx), Math.max(16, window.innerWidth - 16));
    y = Math.min(Math.max(16, y + dy), Math.max(16, window.innerHeight - 16));
    update();
  };

  window.__niaCursorClick = function () {
    var target = document.elementFromPoint(x, y);
    if (!target) return;
    try {
      target.dispatchEvent(new MouseEvent("mousedown", {bubbles:true, clientX:x, clientY:y, button:0}));
      target.dispatchEvent(new MouseEvent("mouseup", {bubbles:true, clientX:x, clientY:y, button:0}));
      if (typeof target.click === "function") target.click();
      else target.dispatchEvent(new MouseEvent("click", {bubbles:true, clientX:x, clientY:y, button:0}));
    } catch (_) {}
  };

  window.addEventListener("load", function () {
    if (profile === "CURSOR" && cursor) {
      cursor.style.display = "block";
      var first = firstActionable();
      if (first) {
        var r = first.getBoundingClientRect();
        x = r.left + r.width / 2;
        y = r.top + r.height / 2;
      }
      update();
    } else {
      var first = firstActionable();
      if (first && typeof first.focus === "function") {
        try { first.focus({preventScroll:true}); } catch (_) { try { first.focus(); } catch (_) {} }
      }
    }
  });

  document.addEventListener("keydown", function (event) {
    if (profile !== "KEYBOARD") return;
    if (event.key !== "Enter" && event.key !== " ") return;
    var active = document.activeElement;
    if (active && active !== document.body && active !== document.documentElement) return;
    var first = firstActionable();
    if (first && typeof first.click === "function") {
      try { first.click(); } catch (_) {}
    }
  }, true);
})();
</script>
"""

def strip_remote_fonts(text: str) -> str:
    text = re.sub(
        r"""<link\b[^>]*href=["']https://fonts\.googleapis\.com[^>]*>\s*""",
        "",
        text,
        flags=re.IGNORECASE,
    )
    text = re.sub(
        r"""<link\b[^>]*href=["']https://fonts\.gstatic\.com[^>]*>\s*""",
        "",
        text,
        flags=re.IGNORECASE,
    )
    text = re.sub(
        r"""<link\b[^>]*rel=["']preconnect["'][^>]*fonts\.(?:googleapis|gstatic)\.com[^>]*>\s*""",
        "",
        text,
        flags=re.IGNORECASE,
    )
    text = re.sub(
        r"""@import\s+url\([^)]*fonts\.googleapis\.com[^)]*\)\s*;?""",
        "",
        text,
        flags=re.IGNORECASE,
    )
    return text

def classify_input(text: str) -> tuple[str, str, str]:
    keyboard = bool(re.search(
        r"""keydown|keyup|keyCode|ArrowUp|ArrowDown|ArrowLeft|ArrowRight|event\.key|\.key\s*={2,3}""",
        text,
        re.IGNORECASE,
    ))
    profile = "KEYBOARD" if keyboard else "CURSOR"

    arrows = bool(re.search(r"""ArrowUp|ArrowDown|ArrowLeft|ArrowRight|keyCode\s*={2,3}\s*(37|38|39|40)""", text))
    wasd = bool(re.search(r"""KeyW|keyCode\s*={2,3}\s*(65|68|83|87)|["'][wasd]["']""", text))
    scheme = "WASD" if wasd and not arrows else "ARROWS"

    space = bool(re.search(r"""Space|keyCode\s*={2,3}\s*32|which\s*={2,3}\s*32""", text))
    enter = bool(re.search(r"""Enter|keyCode\s*={2,3}\s*13|which\s*={2,3}\s*13""", text))
    action = "Space" if space else ("Enter" if enter else "Enter")
    return profile, scheme, action

def inject_bridge(text: str, profile: str) -> str:
    bridge = BRIDGE.replace("__NIA_PROFILE__", profile)
    if re.search(r"</body\s*>", text, flags=re.IGNORECASE):
        return re.sub(r"</body\s*>", bridge + "\n</body>", text, count=1, flags=re.IGNORECASE)
    return text + "\n" + bridge

def main() -> int:
    if len(sys.argv) != 2:
        print("usage: import_games.py <upstream-checkout>", file=sys.stderr)
        return 2

    upstream = Path(sys.argv[1]).resolve()
    root = Path(__file__).resolve().parents[1]
    assets = root / "app" / "src" / "main" / "assets"
    games_out = assets / "games"
    licenses_out = assets / "licenses"

    if not (upstream / "LICENSE").exists() or not (upstream / "index.html").exists():
        raise SystemExit("Upstream checkout is incomplete.")

    license_text = (upstream / "LICENSE").read_text(encoding="utf-8")
    if "MIT License" not in license_text or "Copyright (c) 2026 Can" not in license_text:
        raise SystemExit("Upstream license identity did not match the pinned audited source.")

    index_text = (upstream / "index.html").read_text(encoding="utf-8")
    meta = {
        int(m.group(1)): {
            "name": html.unescape(m.group(2)),
            "icon": html.unescape(m.group(3)),
            "category": m.group(4).lower(),
        }
        for m in GAME_ROW_RE.finditer(index_text)
    }

    sources = sorted(
        (upstream / "games").glob("*/index.html"),
        key=lambda p: int(p.parent.name.split("-", 1)[0]),
    )
    if len(sources) != EXPECTED_GAMES:
        raise SystemExit(f"Expected {EXPECTED_GAMES} games, found {len(sources)}.")
    if len(meta) != EXPECTED_GAMES:
        raise SystemExit(f"Expected {EXPECTED_GAMES} metadata rows, found {len(meta)}.")

    if games_out.exists():
        shutil.rmtree(games_out)
    games_out.mkdir(parents=True, exist_ok=True)
    licenses_out.mkdir(parents=True, exist_ok=True)

    catalog = []
    rejected = []

    for source in sources:
        slug = source.parent.name
        game_id = int(slug.split("-", 1)[0])
        item = meta[game_id]
        original_title = item["name"]
        title = SAFE_NAMES.get(original_title, original_title)

        text = source.read_text(encoding="utf-8")
        text = strip_remote_fonts(text)
        if original_title != title:
            text = text.replace(original_title, title)

        profile, scheme, action = classify_input(text)
        text = inject_bridge(text, profile)

        remotes = REMOTE_LINK_RE.findall(text)
        if remotes:
            rejected.append((slug, "remote dependency remained"))
            continue

        dest = games_out / slug
        dest.mkdir(parents=True, exist_ok=True)
        (dest / "index.html").write_text(text, encoding="utf-8", newline="\n")

        catalog.append({
            "id": game_id,
            "slug": slug,
            "title": title,
            "originalTitle": original_title,
            "icon": item["icon"],
            "category": item["category"],
            "inputProfile": profile,
            "directionScheme": scheme,
            "actionKey": action,
            "sourceUrl": UPSTREAM_REPO + "/tree/" + UPSTREAM_COMMIT + "/games/" + slug,
            "upstreamCommit": UPSTREAM_COMMIT,
            "license": "MIT",
            "niaCertified": True,
        })

    if rejected:
        details = ", ".join(name + ": " + why for name, why in rejected)
        raise SystemExit("Import rejected games: " + details)

    if len(catalog) != EXPECTED_GAMES:
        raise SystemExit(f"Catalog size mismatch: {len(catalog)}")

    (assets / "catalog.json").write_text(
        json.dumps(catalog, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    (licenses_out / "100htmlgameshub-MIT.txt").write_text(license_text, encoding="utf-8")

    notice = f"""# Third-party notices

## 100 HTML Games Collection

- Upstream: {UPSTREAM_REPO}
- Pinned commit: {UPSTREAM_COMMIT}
- Copyright: Copyright (c) 2026 Can
- License: MIT
- NIA changes: local-only packaging, remote-font removal, TV remote input bridge, presentation/title safety adjustments.

The full MIT license text is packaged at:
`app/src/main/assets/licenses/100htmlgameshub-MIT.txt`.

NIA Arcade does not claim ownership of the upstream game implementations.
"""
    (root / "THIRD_PARTY_NOTICES.md").write_text(notice, encoding="utf-8")

    keyboard = sum(1 for x in catalog if x["inputProfile"] == "KEYBOARD")
    cursor = sum(1 for x in catalog if x["inputProfile"] == "CURSOR")
    print(f"Imported {len(catalog)} games. KEYBOARD={keyboard} CURSOR={cursor}.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
