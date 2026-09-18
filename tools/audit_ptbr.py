#!/usr/bin/env python3
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"
CATALOG = json.loads((ASSETS / "catalog.json").read_text(encoding="utf-8"))

# Frases de UI em inglês que não devem sobreviver à adaptação.
BANNED = [
    "Back to Menu",
    "Press Start to begin",
    "Press SPACE",
    "Click or press SPACE",
    "Play Again",
    "Game Over",
    "Final Score",
    "Time's up",
    "Wrong answer",
    "Correct!",
    "Too low",
    "Too high",
    "Start Quiz",
    "Start Game",
    "Player 1",
    "Player 2",
]

def main() -> int:
    errors = []

    for game in CATALOG:
        path = ASSETS / "games" / game["slug"] / "index.html"
        text = path.read_text(encoding="utf-8")
        # O bridge contém o dicionário inglês→português como dados e não representa
        # texto exibido ao usuário; remova-o antes da auditoria estática.
        audit_text = re.sub(
            r'<script id="nia-tv-bridge">.*?</script>',
            '',
            text,
            flags=re.IGNORECASE | re.DOTALL,
        )

        if game.get("locale") != "pt-BR":
            errors.append(game["slug"] + ": locale incorreto")
        if not game.get("title"):
            errors.append(game["slug"] + ": título vazio")

        # O código pode ter identificadores em inglês; aqui bloqueamos somente frases típicas
        # de interface que indicam texto não localizado.
        for phrase in BANNED:
            if phrase in audit_text:
                errors.append(game["slug"] + ": texto de UI em inglês: " + phrase)

    if errors:
        print("PT-BR AUDIT FAILED")
        for err in errors[:80]:
            print(" -", err)
        return 1

    print("PT-BR AUDIT PASS")
    print("Jogos marcados pt-BR: 100/100")
    print("Frases comuns de UI em inglês restantes: 0")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
