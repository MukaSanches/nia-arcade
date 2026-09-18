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
    "02-pong","17-connect-four","18-tic-tac-toe","19-checkers",
    "20-chess","35-dots-and-boxes","36-reversi","76-ludo",
    "77-snakes-and-ladders",
}

VALID_LAYOUTS = {"CANVAS", "DOM"}
VALID_PLAY = {"SOLO", "VS_CPU"}
VALID_ORIENTATION = {"PORTRAIT", "LANDSCAPE", "SQUARE", "RESPONSIVE"}

def main() -> int:
    data = json.loads(CATALOG.read_text(encoding="utf-8"))
    errors: list[str] = []

    if len(data) != 100:
        errors.append(f"catálogo contém {len(data)} jogos; esperado: 100")

    ids = [x["id"] for x in data]
    slugs = [x["slug"] for x in data]
    if len(set(ids)) != len(ids):
        errors.append("IDs duplicados")
    if len(set(slugs)) != len(slugs):
        errors.append("slugs duplicados")

    for game in data:
        slug = game["slug"]
        path = ASSETS / "games" / slug / "index.html"
        if not path.exists():
            errors.append(slug + ": index.html ausente")
            continue

        text = path.read_text(encoding="utf-8")

        if REMOTE_LINK_RE.search(text):
            errors.append(slug + ": dependência remota detectada")
        if 'lang="pt-BR"' not in text and "lang='pt-BR'" not in text:
            errors.append(slug + ": documento não está marcado pt-BR")
        if "nia-tv-bridge" not in text:
            errors.append(slug + ": bridge NIA ausente")
        if "nia-layout-profile" not in text:
            errors.append(slug + ": perfil de layout ausente")
        if "__niaResetViewport" not in text:
            errors.append(slug + ": proteção de viewport ausente")
        if "__niaActivatePrimary" not in text:
            errors.append(slug + ": auto-start ausente")
        if "__niaTranslate" not in text:
            errors.append(slug + ": tradutor pt-BR ausente")

        if game.get("locale") != "pt-BR":
            errors.append(slug + ": locale do catálogo não é pt-BR")
        if game.get("inputProfile") not in {"KEYBOARD", "CURSOR"}:
            errors.append(slug + ": inputProfile inválido")
        if game.get("directionScheme") not in {"ARROWS", "WASD"}:
            errors.append(slug + ": directionScheme inválido")
        if game.get("playMode") not in VALID_PLAY:
            errors.append(slug + ": playMode inválido")
        if game.get("layoutType") not in VALID_LAYOUTS:
            errors.append(slug + ": layoutType inválido")
        if game.get("orientation") not in VALID_ORIENTATION:
            errors.append(slug + ": orientação inválida")
        if not isinstance(game.get("maxWidthVw"), int) or not 30 <= game["maxWidthVw"] <= 100:
            errors.append(slug + ": maxWidthVw inválido")
        if not isinstance(game.get("maxHeightVh"), int) or not 30 <= game["maxHeightVh"] <= 100:
            errors.append(slug + ": maxHeightVh inválido")
        if game.get("license") != "MIT":
            errors.append(slug + ": licença inesperada")
        if game.get("niaCertified") is not True:
            errors.append(slug + ": não certificado")

        if slug in CPU_PATCH_GAMES:
            if game.get("playMode") != "VS_CPU":
                errors.append(slug + ": jogo CPU não marcado VS_CPU")
            if 'id="nia-cpu-opponent"' not in text:
                errors.append(slug + ": patch CPU ausente")

        if slug in {"09-flappy-bird", "10-dino-runner"} and "init(); draw();" not in text:
            errors.append(slug + ": correção de inicialização não aplicada")

    license_path = ASSETS / "licenses" / "100htmlgameshub-MIT.txt"
    if not license_path.exists() or "MIT License" not in license_path.read_text(encoding="utf-8"):
        errors.append("texto integral da licença MIT ausente")

    if errors:
        print("VALIDATION FAILED")
        for err in errors:
            print(" -", err)
        return 1

    print("VALIDATION PASS")
    print("Catálogo: 100/100")
    print("Locale pt-BR: 100/100")
    print("Bridge NIA: 100/100")
    print("Perfis individuais: 100/100")
    print("Dependências remotas: 0")
    print("Flappy/Dino startup guard: PASS")
    print("Metadados de licença: 100/100")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
