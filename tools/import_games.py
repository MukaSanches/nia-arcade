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

CPU_GAMES = {
    "02-pong",
    "17-connect-four",
    "18-tic-tac-toe",
    "19-checkers",
    "20-chess",
    "35-dots-and-boxes",
    "36-reversi",
    "37-battleship",
    "49-air-hockey",
    "63-card-war",
    "64-go-fish",
    "65-snap",
    "70-old-maid",
    "71-speed",
    "72-rummy",
    "73-crazy-eights",
    "74-uno",
    "75-cribbage",
    "76-ludo",
    "77-snakes-and-ladders",
    "78-backgammon",
    "84-rock-paper-scissors",
}

NIA_CPU_PATCH_GAMES = {
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

REMOTE_LINK_RE = re.compile(
    r"""(?:src|href)\s*=\s*["']https?://|url\(\s*["']?https?://""",
    re.IGNORECASE,
)

GAME_ROW_RE = re.compile(
    r"""\{\s*id:\s*(\d+),\s*name:\s*"([^"]+)",\s*icon:\s*"([^"]+)",\s*cat:\s*"([^"]+)"[^}]*ready:\s*true\s*\}"""
)

CANVAS_RE = re.compile(
    r"""<canvas\b[^>]*\bwidth=["']?(\d+)["']?[^>]*\bheight=["']?(\d+)["']?[^>]*>""",
    re.IGNORECASE,
)

PRELUDE = r"""
<script id="nia-input-prelude">
(function () {
  "use strict";
  if (window.__niaInputPreludeInstalled) return;
  window.__niaInputPreludeInstalled = true;

  var pointerTypes = {
    click: true,
    dblclick: true,
    mousedown: true,
    mouseup: true,
    mousemove: true,
    pointerdown: true,
    pointerup: true,
    pointermove: true,
    touchstart: true,
    touchmove: true,
    touchend: true
  };

  var nativeAdd = EventTarget.prototype.addEventListener;

  function scalePoint(canvas, x, y) {
    var rect = canvas.getBoundingClientRect();
    if (!rect.width || !rect.height) return { x: x, y: y };
    return {
      x: rect.left + (x - rect.left) * (canvas.width / rect.width),
      y: rect.top + (y - rect.top) * (canvas.height / rect.height)
    };
  }

  function proxyTouch(canvas, touch) {
    if (!touch) return touch;
    var p = scalePoint(canvas, touch.clientX, touch.clientY);
    return new Proxy(touch, {
      get: function (target, prop) {
        if (prop === "clientX") return p.x;
        if (prop === "clientY") return p.y;
        var value = Reflect.get(target, prop, target);
        return typeof value === "function" ? value.bind(target) : value;
      }
    });
  }

  function proxyEvent(canvas, event) {
    var p = scalePoint(canvas, event.clientX || 0, event.clientY || 0);
    return new Proxy(event, {
      get: function (target, prop) {
        if (prop === "clientX") return p.x;
        if (prop === "clientY") return p.y;
        if (prop === "offsetX") return p.x - canvas.getBoundingClientRect().left;
        if (prop === "offsetY") return p.y - canvas.getBoundingClientRect().top;
        if (prop === "touches") return Array.prototype.map.call(target.touches || [], function (t) { return proxyTouch(canvas, t); });
        if (prop === "changedTouches") return Array.prototype.map.call(target.changedTouches || [], function (t) { return proxyTouch(canvas, t); });
        var value = Reflect.get(target, prop, target);
        return typeof value === "function" ? value.bind(target) : value;
      }
    });
  }

  EventTarget.prototype.addEventListener = function (type, listener, options) {
    if (this instanceof HTMLCanvasElement && pointerTypes[type] && typeof listener === "function") {
      var canvas = this;
      var wrapped = function (event) {
        return listener.call(this, proxyEvent(canvas, event));
      };
      return nativeAdd.call(this, type, wrapped, options);
    }
    return nativeAdd.call(this, type, listener, options);
  };
})();
</script>
"""

BRIDGE = r"""
<style id="nia-tv-bridge-style">
  .back,
  a[href="../../index.html"],
  a[href*="../index.html"],
  body > h1,
  .hint {
    display:none !important;
  }

  html {
    width:100% !important;
    height:100% !important;
    overflow:hidden !important;
    background:#05070b !important;
    overscroll-behavior:none !important;
  }

  body {
    margin:0 !important;
    padding:0 !important;
    overflow:visible !important;
    overscroll-behavior:none !important;
    scrollbar-width:none !important;
  }

  body::-webkit-scrollbar { display:none !important; }

  #__nia_cursor {
    position:fixed;
    left:50%;
    top:50%;
    width:24px;
    height:24px;
    margin:-12px 0 0 -12px;
    border:3px solid #38E8FF;
    border-radius:50%;
    box-shadow:0 0 0 2px rgba(9,11,16,.92), 0 0 20px rgba(56,232,255,.85);
    z-index:2147483647;
    pointer-events:none;
    display:none;
    transition:left .045s linear, top .045s linear;
  }
</style>
<div id="__nia_cursor" aria-hidden="true"></div>
<script id="nia-tv-bridge">
(function () {
  "use strict";

  var profile = "__NIA_PROFILE__";
  var gameSlug = "__NIA_GAME_SLUG__";
  var maxScale = Number("__NIA_MAX_SCALE__") || 2;
  var fitPadding = Number("__NIA_FIT_PADDING__") || 32;
  var cursor = document.getElementById("__nia_cursor");
  var x = Math.max(20, window.innerWidth / 2);
  var y = Math.max(20, window.innerHeight / 2);
  var gameStarted = false;
  var primaryUsed = false;
  var fitQueued = false;

  if (cursor && cursor.parentNode !== document.documentElement) {
    document.documentElement.appendChild(cursor);
  }

  function resetViewport() {
    try {
      window.scrollTo(0, 0);
      document.documentElement.scrollTop = 0;
      document.documentElement.scrollLeft = 0;
      if (document.body) {
        document.body.scrollTop = 0;
        document.body.scrollLeft = 0;
      }
    } catch (_) {}
  }

  window.__niaResetViewport = resetViewport;

  function prepareStage() {
    var body = document.body;
    if (!body) return;
    body.style.position = "fixed";
    body.style.left = "50%";
    body.style.top = "50%";
    body.style.width = "max-content";
    body.style.maxWidth = "none";
    body.style.minWidth = "0";
    body.style.height = "max-content";
    body.style.minHeight = "0";
    body.style.maxHeight = "none";
    body.style.transformOrigin = "center center";
    body.style.transition = "none";
  }

  function fitGame() {
    var body = document.body;
    if (!body) return;
    prepareStage();
    body.style.transform = "translate(-50%, -50%) scale(1)";
    resetViewport();

    var rect = body.getBoundingClientRect();
    var width = Math.max(1, body.scrollWidth, rect.width);
    var height = Math.max(1, body.scrollHeight, rect.height);
    var usableW = Math.max(1, window.innerWidth - fitPadding * 2);
    var usableH = Math.max(1, window.innerHeight - fitPadding * 2);
    var scale = Math.min(usableW / width, usableH / height, maxScale);

    if (!isFinite(scale) || scale <= 0) scale = 1;
    scale = Math.max(0.45, scale);

    body.style.transform = "translate(-50%, -50%) scale(" + scale.toFixed(4) + ")";
    document.documentElement.dataset.niaFit = "ready";
    document.documentElement.dataset.niaScale = scale.toFixed(4);
    document.documentElement.dataset.niaGame = gameSlug;
    resetViewport();
  }

  window.__niaFitGame = fitGame;

  function requestFit() {
    if (fitQueued) return;
    fitQueued = true;
    requestAnimationFrame(function () {
      fitQueued = false;
      fitGame();
    });
  }

  function visible(el) {
    if (!el) return false;
    var r = el.getBoundingClientRect();
    var s = window.getComputedStyle(el);
    return r.width > 0 && r.height > 0 && s.display !== "none" && s.visibility !== "hidden" && Number(s.opacity || "1") > 0;
  }

  function elementLabel(el) {
    if (!el) return "";
    return String(
      el.innerText ||
      el.textContent ||
      el.value ||
      el.getAttribute("aria-label") ||
      ""
    ).trim().toLowerCase();
  }

  function isPrimaryAction(el) {
    var label = elementLabel(el);
    return /^(play|start|start game|deal|jogar|iniciar|começar|comecar|begin|launch)(\b|$)/i.test(label);
  }

  function primaryAction() {
    var list = document.querySelectorAll("button, [role=button], input[type=button], input[type=submit], .btn");
    for (var i = 0; i < list.length; i++) {
      if (visible(list[i]) && isPrimaryAction(list[i])) return list[i];
    }
    return null;
  }

  function firstActionable() {
    var preferred = document.querySelectorAll(
      ".cell, .sq, .col-btn, canvas, .card, .choice-btn, .dice, #rollBtn, button, [role=button], input[type=button], input[type=submit]"
    );
    for (var i = 0; i < preferred.length; i++) {
      if (visible(preferred[i]) && !isPrimaryAction(preferred[i])) return preferred[i];
    }
    return primaryAction();
  }

  function moveCursorTo(el) {
    if (!el || !cursor) return;
    var r = el.getBoundingClientRect();
    if (!r.width || !r.height) return;
    x = Math.min(window.innerWidth - 16, Math.max(16, r.left + r.width / 2));
    y = Math.min(window.innerHeight - 16, Math.max(16, r.top + r.height / 2));
    updateCursor();
  }

  function updateCursor() {
    if (!cursor) return;
    cursor.style.left = x + "px";
    cursor.style.top = y + "px";
  }

  function afterGameStart() {
    gameStarted = true;
    resetViewport();
    requestFit();
    setTimeout(function () { resetViewport(); requestFit(); }, 40);
    setTimeout(function () { resetViewport(); requestFit(); }, 140);
    setTimeout(function () { resetViewport(); requestFit(); }, 320);
  }

  window.__niaActivatePrimary = function () {
    if (primaryUsed) return false;
    var primary = primaryAction();
    if (!primary) return false;

    primaryUsed = true;
    try {
      if (typeof primary.click === "function") primary.click();
      else primary.dispatchEvent(new MouseEvent("click", { bubbles:true, button:0 }));

      document.documentElement.dataset.niaAutostart = "done";
      if (primary.id === "startBtn") primary.style.display = "none";
      afterGameStart();
      return true;
    } catch (_) {
      primaryUsed = false;
      return false;
    }
  };

  window.__niaCursorMove = function (dx, dy) {
    x = Math.min(Math.max(16, x + dx), Math.max(16, window.innerWidth - 16));
    y = Math.min(Math.max(16, y + dy), Math.max(16, window.innerHeight - 16));
    updateCursor();
  };

  window.__niaCursorClick = function () {
    var target = document.elementFromPoint(x, y);
    if (!target) return;
    try {
      var actionable = target.closest ? target.closest("button, [role=button], input, .btn, a") : target;
      var startsGame = isPrimaryAction(actionable);

      target.dispatchEvent(new MouseEvent("mousedown", { bubbles:true, clientX:x, clientY:y, button:0 }));
      target.dispatchEvent(new MouseEvent("mouseup", { bubbles:true, clientX:x, clientY:y, button:0 }));

      if (typeof target.click === "function") target.click();
      else target.dispatchEvent(new MouseEvent("click", { bubbles:true, clientX:x, clientY:y, button:0 }));

      if (startsGame) {
        primaryUsed = true;
        afterGameStart();
      } else {
        requestFit();
      }
    } catch (_) {}
  };

  window.addEventListener("scroll", function () {
    requestAnimationFrame(resetViewport);
  }, { passive:true });

  window.addEventListener("resize", function () {
    requestFit();
  });

  window.addEventListener("load", function () {
    prepareStage();
    fitGame();

    setTimeout(function () {
      window.__niaActivatePrimary();
      fitGame();

      if (profile === "CURSOR" && cursor) {
        cursor.style.display = "block";
        moveCursorTo(firstActionable());
      } else if (cursor) {
        cursor.style.display = "none";
      }
    }, 90);

    setTimeout(function () {
      fitGame();
      if (profile === "CURSOR" && cursor) moveCursorTo(firstActionable());
    }, 260);
  });

  try {
    new MutationObserver(function () { requestFit(); }).observe(document.body, {
      childList:true,
      subtree:true,
      characterData:true
    });
  } catch (_) {}

  document.addEventListener("click", function (event) {
    var target = event.target;
    var actionable = target && target.closest ? target.closest("button, [role=button], input, .btn, a") : target;
    if (isPrimaryAction(actionable)) {
      primaryUsed = true;
      afterGameStart();
    } else {
      requestFit();
    }
  }, true);

  document.addEventListener("keydown", function (event) {
    if (profile !== "KEYBOARD") return;
    if (event.key !== "Enter" && event.key !== " ") return;

    var active = document.activeElement;
    if (active && active !== document.body && active !== document.documentElement) return;

    var first = firstActionable();
    if (first && typeof first.click === "function") {
      try {
        first.click();
        if (isPrimaryAction(first)) {
          primaryUsed = true;
          afterGameStart();
        } else {
          requestFit();
        }
      } catch (_) {}
    }
  }, true);
})();
</script>
"""

CPU_PATCHES = {
    "02-pong": r"""
(function () {
  "use strict";
  var niaBaseUpdate = update;

  update = function () {
    if (running) {
      var desired = ball.y - PAD_H / 2;
      var center = H / 2 - PAD_H / 2;
      var target = ball.vx < 0 ? desired : center;
      var speed = ball.vx < 0 ? 4.4 : 2.1;
      var delta = Math.max(-speed, Math.min(speed, target - pl.y));
      pl.y = Math.max(0, Math.min(H - PAD_H, pl.y + delta));
    }
    niaBaseUpdate();
  };

  var msg = document.getElementById("msg");
  if (msg) msg.textContent = "Você controla a raquete da direita. Oponente: CPU.";
  window.__niaCpuReady = true;
})();
""",

    "17-connect-four": r"""
(function () {
  "use strict";
  var niaBaseDrop = drop;

  function validColumns(b) {
    var out = [];
    for (var c = 0; c < COLS; c++) if (!b[0][c]) out.push(c);
    return out;
  }

  function place(b, col, player) {
    var copy = b.map(function (row) { return row.slice(); });
    for (var r = ROWS - 1; r >= 0; r--) {
      if (!copy[r][col]) {
        copy[r][col] = player;
        return copy;
      }
    }
    return copy;
  }

  function winner(b, player) {
    var dirs = [[0,1],[1,0],[1,1],[1,-1]];
    for (var r = 0; r < ROWS; r++) {
      for (var c = 0; c < COLS; c++) {
        if (b[r][c] !== player) continue;
        for (var d = 0; d < dirs.length; d++) {
          var dr = dirs[d][0], dc = dirs[d][1], ok = true;
          for (var k = 1; k < 4; k++) {
            var nr = r + dr * k, nc = c + dc * k;
            if (nr < 0 || nr >= ROWS || nc < 0 || nc >= COLS || b[nr][nc] !== player) {
              ok = false;
              break;
            }
          }
          if (ok) return true;
        }
      }
    }
    return false;
  }

  function windowScore(values) {
    var cpu = values.filter(function (v) { return v === 2; }).length;
    var human = values.filter(function (v) { return v === 1; }).length;
    var empty = 4 - cpu - human;
    if (cpu === 4) return 100000;
    if (human === 4) return -100000;
    if (cpu === 3 && empty === 1) return 120;
    if (cpu === 2 && empty === 2) return 18;
    if (human === 3 && empty === 1) return -150;
    if (human === 2 && empty === 2) return -12;
    return 0;
  }

  function evaluate(b) {
    if (winner(b, 2)) return 1000000;
    if (winner(b, 1)) return -1000000;

    var score = 0;
    for (var r = 0; r < ROWS; r++) if (b[r][3] === 2) score += 8;

    function addWindow(cells) {
      score += windowScore(cells);
    }

    for (var rr = 0; rr < ROWS; rr++) {
      for (var cc = 0; cc <= COLS - 4; cc++) addWindow([b[rr][cc], b[rr][cc+1], b[rr][cc+2], b[rr][cc+3]]);
    }
    for (var cc2 = 0; cc2 < COLS; cc2++) {
      for (var rr2 = 0; rr2 <= ROWS - 4; rr2++) addWindow([b[rr2][cc2], b[rr2+1][cc2], b[rr2+2][cc2], b[rr2+3][cc2]]);
    }
    for (var rr3 = 0; rr3 <= ROWS - 4; rr3++) {
      for (var cc3 = 0; cc3 <= COLS - 4; cc3++) addWindow([b[rr3][cc3], b[rr3+1][cc3+1], b[rr3+2][cc3+2], b[rr3+3][cc3+3]]);
    }
    for (var rr4 = 0; rr4 <= ROWS - 4; rr4++) {
      for (var cc4 = 3; cc4 < COLS; cc4++) addWindow([b[rr4][cc4], b[rr4+1][cc4-1], b[rr4+2][cc4-2], b[rr4+3][cc4-3]]);
    }
    return score;
  }

  function minimax(b, depth, maximizing, alpha, beta) {
    var cols = validColumns(b);
    if (depth === 0 || !cols.length || winner(b, 1) || winner(b, 2)) return evaluate(b);

    if (maximizing) {
      var best = -Infinity;
      for (var i = 0; i < cols.length; i++) {
        best = Math.max(best, minimax(place(b, cols[i], 2), depth - 1, false, alpha, beta));
        alpha = Math.max(alpha, best);
        if (beta <= alpha) break;
      }
      return best;
    }

    var worst = Infinity;
    for (var j = 0; j < cols.length; j++) {
      worst = Math.min(worst, minimax(place(b, cols[j], 1), depth - 1, true, alpha, beta));
      beta = Math.min(beta, worst);
      if (beta <= alpha) break;
    }
    return worst;
  }

  function chooseCpuColumn() {
    var cols = validColumns(board);
    if (!cols.length) return -1;

    for (var i = 0; i < cols.length; i++) if (winner(place(board, cols[i], 2), 2)) return cols[i];
    for (var j = 0; j < cols.length; j++) if (winner(place(board, cols[j], 1), 1)) return cols[j];

    var bestCol = cols[0], bestScore = -Infinity;
    var orderBias = [3,2,4,1,5,0,6];

    for (var k = 0; k < cols.length; k++) {
      var col = cols[k];
      var score = minimax(place(board, col, 2), 4, false, -Infinity, Infinity);
      score += (7 - Math.abs(3 - col)) * 0.35;
      score += (7 - orderBias.indexOf(col)) * 0.01;
      if (score > bestScore) {
        bestScore = score;
        bestCol = col;
      }
    }
    return bestCol;
  }

  function cpuMove() {
    if (gameOver || current !== 2) return;
    var col = chooseCpuColumn();
    if (col >= 0) niaBaseDrop(col);
  }

  drop = function (col) {
    if (gameOver || current !== 1) return;
    niaBaseDrop(col);
    if (!gameOver && current === 2) setTimeout(cpuMove, 260);
  };

  var p1 = document.getElementById("p1tag");
  var p2 = document.getElementById("p2tag");
  if (p1) p1.textContent = "🔴 Você";
  if (p2) p2.textContent = "🟡 CPU";
  window.__niaCpuReady = true;
})();
""",

    "18-tic-tac-toe": r"""
(function () {
  "use strict";
  setTimeout(function () {
    var ai = document.getElementById("pvcBtn");
    if (ai) ai.click();
    var modes = document.querySelector(".mode-btns");
    if (modes) modes.style.display = "none";
    window.__niaCpuReady = true;
    if (window.__niaFitGame) window.__niaFitGame();
  }, 0);
})();
""",

    "19-checkers": r"""
(function () {
  "use strict";
  var niaBaseSelect = select;

  function cpuCandidates() {
    var mustJump = hasJumps();
    var out = [];
    for (var r = 0; r < 8; r++) {
      for (var c = 0; c < 8; c++) {
        if (!isBlack(board[r][c])) continue;
        var moves = getMoves(r, c, board).filter(function (m) { return !mustJump || m.jump; });
        for (var i = 0; i < moves.length; i++) out.push({ from:{r:r,c:c}, move:moves[i] });
      }
    }
    return out;
  }

  function cpuScore(candidate) {
    var m = candidate.move;
    var piece = board[candidate.from.r][candidate.from.c];
    var score = Math.random() * 3;
    if (m.jump) score += 100;
    if (piece === 2 && m.r === 7) score += 45;
    score += 7 - Math.abs(3.5 - m.c);
    if (m.r >= 2 && m.r <= 5 && m.c >= 2 && m.c <= 5) score += 5;
    return score;
  }

  function cpuMove() {
    if (turn !== 2) return;
    var candidates = cpuCandidates();
    if (!candidates.length) {
      document.getElementById("msg").textContent = "🏆 Você venceu — CPU sem movimentos.";
      return;
    }

    candidates.sort(function (a, b) { return cpuScore(b) - cpuScore(a); });
    var choice = candidates[0];
    niaBaseSelect(choice.from.r, choice.from.c);
    setTimeout(function () {
      niaBaseSelect(choice.move.r, choice.move.c);
    }, 120);
  }

  select = function (r, c) {
    if (turn !== 1) return;
    var before = turn;
    niaBaseSelect(r, c);
    if (before === 1 && turn === 2) setTimeout(cpuMove, 260);
  };

  var indicator = document.getElementById("turn-indicator");
  if (indicator) indicator.textContent = "🔴 Sua vez";
  window.__niaCpuReady = true;
})();
""",

    "20-chess": r"""
(function () {
  "use strict";
  var niaBaseClick = click;
  var values = {
    "♙":1, "♟":1,
    "♘":3, "♞":3,
    "♗":3, "♝":3,
    "♖":5, "♜":5,
    "♕":9, "♛":9,
    "♔":1000, "♚":1000
  };

  function cpuMoves() {
    var out = [];
    for (var i = 0; i < 64; i++) {
      if (!isBlack(board[i])) continue;
      var moves = getMoves(i);
      for (var j = 0; j < moves.length; j++) out.push({ from:i, to:moves[j] });
    }
    return out;
  }

  function scoreMove(m) {
    var captured = board[m.to];
    var row = Math.floor(m.to / 8);
    var col = m.to % 8;
    var score = (values[captured] || 0) * 100;
    score += 7 - Math.abs(3.5 - row) - Math.abs(3.5 - col);
    if (board[m.from] === BP && row === 7) score += 80;
    score += Math.random() * 2;
    return score;
  }

  function cpuMove() {
    if (turn !== "black") return;
    var moves = cpuMoves();
    if (!moves.length) {
      document.getElementById("msg").textContent = "🏆 Você venceu — CPU sem movimentos.";
      return;
    }

    moves.sort(function (a, b) { return scoreMove(b) - scoreMove(a); });
    var best = moves[0];
    niaBaseClick(best.from);
    setTimeout(function () { niaBaseClick(best.to); }, 140);
  }

  click = function (idx) {
    if (turn !== "white") return;
    var before = turn;
    niaBaseClick(idx);
    if (before === "white" && turn === "black") setTimeout(cpuMove, 300);
  };

  var turnDisp = document.getElementById("turnDisp");
  if (turnDisp) turnDisp.textContent = "⬜ Sua vez";
  window.__niaCpuReady = true;
})();
""",

    "35-dots-and-boxes": r"""
(function () {
  "use strict";
  var cpuBusy = false;

  function sideCount(r, col, candidate) {
    function h(rr, cc) {
      return hLines[rr][cc] >= 0 || (candidate.type === "h" && candidate.r === rr && candidate.c === cc);
    }
    function v(rr, cc) {
      return vLines[rr][cc] >= 0 || (candidate.type === "v" && candidate.r === rr && candidate.c === cc);
    }
    var n = 0;
    if (h(r, col)) n++;
    if (h(r + 1, col)) n++;
    if (v(r, col)) n++;
    if (v(r, col + 1)) n++;
    return n;
  }

  function candidates() {
    var out = [];
    for (var r = 0; r <= N; r++) {
      for (var col = 0; col < N; col++) if (hLines[r][col] < 0) out.push({type:"h",r:r,c:col});
    }
    for (var r2 = 0; r2 < N; r2++) {
      for (var col2 = 0; col2 <= N; col2++) if (vLines[r2][col2] < 0) out.push({type:"v",r:r2,c:col2});
    }
    return out;
  }

  function candidateScore(line) {
    var complete = 0, danger = 0;
    var boxesToCheck = [];

    if (line.type === "h") {
      if (line.r > 0) boxesToCheck.push([line.r - 1, line.c]);
      if (line.r < N) boxesToCheck.push([line.r, line.c]);
    } else {
      if (line.c > 0) boxesToCheck.push([line.r, line.c - 1]);
      if (line.c < N) boxesToCheck.push([line.r, line.c]);
    }

    for (var i = 0; i < boxesToCheck.length; i++) {
      var br = boxesToCheck[i][0], bc = boxesToCheck[i][1];
      if (boxes[br][bc] >= 0) continue;
      var sides = sideCount(br, bc, line);
      if (sides === 4) complete++;
      else if (sides === 3) danger++;
    }

    return complete * 1000 - danger * 60 + Math.random() * 8;
  }

  function dispatchLine(line) {
    var rect = c.getBoundingClientRect();
    var lx, ly;
    if (line.type === "h") {
      lx = PAD + line.c * GAP + GAP / 2;
      ly = PAD + line.r * GAP;
    } else {
      lx = PAD + line.c * GAP;
      ly = PAD + line.r * GAP + GAP / 2;
    }

    var clientX = rect.left + lx * (rect.width / c.width);
    var clientY = rect.top + ly * (rect.height / c.height);
    c.dispatchEvent(new MouseEvent("click", {
      bubbles:true,
      cancelable:true,
      clientX:clientX,
      clientY:clientY,
      button:0
    }));
  }

  function cpuTurn() {
    if (turn !== 1 || cpuBusy) return;
    var list = candidates();
    if (!list.length) return;

    list.sort(function (a, b) { return candidateScore(b) - candidateScore(a); });
    cpuBusy = true;
    setTimeout(function () {
      dispatchLine(list[0]);
      cpuBusy = false;
      if (turn === 1) setTimeout(cpuTurn, 180);
    }, 230);
  }

  c.addEventListener("click", function () {
    if (turn === 1 && !cpuBusy) setTimeout(cpuTurn, 180);
  });

  var sp1 = document.getElementById("sp1");
  var sp2 = document.getElementById("sp2");
  if (sp1) sp1.firstChild.textContent = "🟣 Você: ";
  if (sp2) sp2.firstChild.textContent = "🔵 CPU: ";
  window.__niaCpuReady = true;
})();
""",

    "36-reversi": r"""
(function () {
  "use strict";
  setTimeout(function () {
    var ai = document.getElementById("aiMode");
    if (ai) ai.click();
    var modes = document.querySelector(".mode-btns");
    if (modes) modes.style.display = "none";
    window.__niaCpuReady = true;
    if (window.__niaFitGame) window.__niaFitGame();
  }, 0);
})();
""",

    "76-ludo": r"""
(function () {
  "use strict";
  var diceEl = document.getElementById("dice");

  if (diceEl) {
    diceEl.addEventListener("click", function (event) {
      if (turn === 1) {
        event.preventDefault();
        event.stopImmediatePropagation();
      }
    }, true);
  }

  setInterval(function () {
    if (tokens && turn === 1 && !rolled && !tokens[1].done) {
      rollDice();
    }
  }, 650);

  var info = document.querySelector(".info");
  if (info) info.innerHTML = '<span style="color:#ef4444">🔴 Você: <b id="s0">Home</b></span><span style="color:#22c55e">🟢 CPU: <b id="s1">Home</b></span>';
  window.__niaCpuReady = true;
})();
""",

    "77-snakes-and-ladders": r"""
(function () {
  "use strict";
  var rollBtn = document.getElementById("rollBtn");

  if (players && players.length >= 2) {
    players[0].name = "VOCÊ";
    players[1].name = "CPU";
    renderPlayers();
  }

  if (rollBtn) {
    rollBtn.addEventListener("click", function (event) {
      if (current === 1) {
        event.preventDefault();
        event.stopImmediatePropagation();
      }
    }, true);
  }

  setInterval(function () {
    if (players && players.length >= 2) {
      players[0].name = "VOCÊ";
      players[1].name = "CPU";
    }
    if (current === 1 && !rolling && players && players[1].pos < 100) {
      roll();
    }
  }, 700);

  window.__niaCpuReady = true;
})();
""",
}

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

def layout_profile(text: str, category: str) -> dict:
    canvas = CANVAS_RE.search(text)
    native_w = int(canvas.group(1)) if canvas else 0
    native_h = int(canvas.group(2)) if canvas else 0

    if canvas:
        kind = "CANVAS"
        ratio = native_w / max(1, native_h)
        if ratio < 0.8:
            max_scale = 1.75
        elif ratio > 1.35:
            max_scale = 2.15
        else:
            max_scale = 1.95
        padding = 30
    elif 'id="board"' in text or "id='board'" in text:
        kind = "BOARD"
        max_scale = 1.85
        padding = 34
    elif category == "card":
        kind = "CARD"
        max_scale = 1.55
        padding = 38
    else:
        kind = "DOM"
        max_scale = 1.70
        padding = 38

    return {
        "kind": kind,
        "nativeWidth": native_w,
        "nativeHeight": native_h,
        "maxScale": max_scale,
        "padding": padding,
    }

def inject_prelude(text: str) -> str:
    if re.search(r"</head\s*>", text, flags=re.IGNORECASE):
        return re.sub(r"</head\s*>", PRELUDE + "\n</head>", text, count=1, flags=re.IGNORECASE)
    return PRELUDE + "\n" + text

def inject_bridge(text: str, profile: str, slug: str, layout: dict) -> str:
    bridge = (
        BRIDGE
        .replace("__NIA_PROFILE__", profile)
        .replace("__NIA_GAME_SLUG__", slug)
        .replace("__NIA_MAX_SCALE__", str(layout["maxScale"]))
        .replace("__NIA_FIT_PADDING__", str(layout["padding"]))
    )

    cpu_patch = CPU_PATCHES.get(slug)
    if cpu_patch:
        bridge += '\n<script id="nia-cpu-opponent">\n' + cpu_patch + '\n</script>\n'

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

        input_profile, scheme, action = classify_input(text)
        layout = layout_profile(text, item["category"])

        text = inject_prelude(text)
        text = inject_bridge(text, input_profile, slug, layout)

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
            "inputProfile": input_profile,
            "directionScheme": scheme,
            "actionKey": action,
            "playMode": "VS_CPU" if slug in CPU_GAMES else "SOLO",
            "layoutProfile": layout["kind"],
            "nativeWidth": layout["nativeWidth"],
            "nativeHeight": layout["nativeHeight"],
            "fitMaxScale": layout["maxScale"],
            "fitPadding": layout["padding"],
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

    missing_cpu_patch = sorted(NIA_CPU_PATCH_GAMES - set(CPU_PATCHES))
    if missing_cpu_patch:
        raise SystemExit("Missing CPU patches: " + ", ".join(missing_cpu_patch))

    (assets / "catalog.json").write_text(
        json.dumps(catalog, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    (licenses_out / "100htmlgameshub-MIT.txt").write_text(
        license_text,
        encoding="utf-8",
    )

    notice = f"""# Third-party notices

## 100 HTML Games Collection

- Upstream: {UPSTREAM_REPO}
- Pinned commit: {UPSTREAM_COMMIT}
- Copyright: Copyright (c) 2026 Can
- License: MIT
- NIA changes: local-only packaging, remote-font removal, TV remote input bridge, per-game fullscreen AutoFit, canvas pointer normalization, automatic start flow, and local CPU-opponent adaptations for TV use.

The full MIT license text is packaged at:
app/src/main/assets/licenses/100htmlgameshub-MIT.txt

NIA Arcade does not claim ownership of the upstream game implementations.
"""
    (root / "THIRD_PARTY_NOTICES.md").write_text(notice, encoding="utf-8")

    keyboard = sum(1 for x in catalog if x["inputProfile"] == "KEYBOARD")
    cursor = sum(1 for x in catalog if x["inputProfile"] == "CURSOR")
    vs_cpu = sum(1 for x in catalog if x["playMode"] == "VS_CPU")
    canvas = sum(1 for x in catalog if x["layoutProfile"] == "CANVAS")

    print(
        f"Imported {len(catalog)} games. "
        f"KEYBOARD={keyboard} CURSOR={cursor} VS_CPU={vs_cpu} CANVAS={canvas}."
    )
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
