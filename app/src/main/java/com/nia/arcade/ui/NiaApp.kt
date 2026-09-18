package com.nia.arcade.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.text.format.Formatter
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nia.arcade.data.CatalogRepository
import com.nia.arcade.data.UserStateRepository
import com.nia.arcade.model.Game
import com.nia.arcade.runtime.GameWebView

private val Void = Color(0xFF090B10)
private val Graphite = Color(0xFF151821)
private val Slate = Color(0xFF202532)
private val Cyan = Color(0xFF38E8FF)
private val Snow = Color(0xFFF7F9FC)
private val Mist = Color(0xFFA4ACBA)

private enum class Screen { HOME, DETAILS, GAME, SETTINGS, DIAGNOSTICS, LICENSES }

@Composable
fun NiaApp() {
    val context = LocalContext.current
    val catalog = remember { runCatching { CatalogRepository(context).load() }.getOrDefault(emptyList()) }
    val state = remember { UserStateRepository(context) }

    var screen by remember { mutableStateOf(Screen.HOME) }
    var selected by remember { mutableStateOf<Game?>(null) }
    var favorites by remember { mutableStateOf(state.favorites()) }
    var recents by remember { mutableStateOf(state.recents()) }

    Box(Modifier.fillMaxSize().background(Void)) {
        when (screen) {
            Screen.HOME -> HomeScreen(
                games = catalog,
                favorites = favorites,
                recents = recents,
                onGame = { selected = it; screen = Screen.DETAILS },
                onSettings = { screen = Screen.SETTINGS }
            )
            Screen.DETAILS -> {
                val game = selected
                if (game == null) {
                    screen = Screen.HOME
                } else {
                    BackHandler { screen = Screen.HOME }
                    DetailsScreen(
                        game = game,
                        favorite = game.id in favorites,
                        onBack = { screen = Screen.HOME },
                        onFavorite = {
                            val next = game.id !in favorites
                            state.setFavorite(game.id, next)
                            favorites = state.favorites()
                        },
                        onPlay = {
                            state.recordPlayed(game.id)
                            recents = state.recents()
                            screen = Screen.GAME
                        }
                    )
                }
            }
            Screen.GAME -> {
                val game = selected
                if (game == null) {
                    screen = Screen.HOME
                } else {
                    GameScreen(game = game, onExit = { screen = Screen.HOME })
                }
            }
            Screen.SETTINGS -> {
                BackHandler { screen = Screen.HOME }
                SettingsScreen(
                    onBack = { screen = Screen.HOME },
                    onDiagnostics = { screen = Screen.DIAGNOSTICS },
                    onLicenses = { screen = Screen.LICENSES }
                )
            }
            Screen.DIAGNOSTICS -> {
                BackHandler { screen = Screen.SETTINGS }
                DiagnosticsScreen(onBack = { screen = Screen.SETTINGS })
            }
            Screen.LICENSES -> {
                BackHandler { screen = Screen.SETTINGS }
                LicensesScreen(onBack = { screen = Screen.SETTINGS })
            }
        }
    }
}

@Composable
private fun HomeScreen(
    games: List<Game>,
    favorites: Set<Int>,
    recents: List<Int>,
    onGame: (Game) -> Unit,
    onSettings: () -> Unit
) {
    var filter by remember { mutableStateOf("all") }
    val categories = remember(games) {
        listOf("all", "favorites", "recent") + games.map { it.category }.distinct()
    }
    val visible = when (filter) {
        "favorites" -> games.filter { it.id in favorites }
        "recent" -> recents.mapNotNull { id -> games.firstOrNull { it.id == id } }
        "all" -> games
        else -> games.filter { it.category == filter }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                NiaText("NIA ARCADE", 34, FontWeight.Black, Snow)
                NiaText(games.size.toString() + " GAMES. ONE REMOTE.", 15, FontWeight.Bold, Cyan)
            }
            NiaButton("SYSTEM", onSettings, compact = true)
        }

        Spacer(Modifier.height(22.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(categories) { category ->
                val label = when (category) {
                    "all" -> "TODOS"
                    "favorites" -> "★ FAVORITOS"
                    "recent" -> "RECENTES"
                    else -> category.uppercase()
                }
                FilterChip(label, selected = filter == category) { filter = category }
            }
        }

        Spacer(Modifier.height(22.dp))
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                NiaText("Nenhum jogo nesta seção.", 22, FontWeight.Medium, Mist)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                gridItems(visible, key = { it.id }) { game ->
                    GameCard(game = game, favorite = game.id in favorites, onClick = { onGame(game) })
                }
            }
        }
    }
}

@Composable
private fun GameCard(game: Game, favorite: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.055f else 1f, label = "game-card")

    Box(
        Modifier
            .height(138.dp)
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .border(if (focused) 3.dp else 1.dp, if (focused) Cyan else Color(0x334A5261), RoundedCornerShape(18.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = if (focused) listOf(Color(0xFF1D2936), Graphite) else listOf(Slate, Graphite)
                ),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NiaText(game.icon, 28, FontWeight.Normal, Snow)
                Spacer(Modifier.weight(1f))
                if (favorite) NiaText("★", 18, FontWeight.Bold, Cyan)
            }
            Column {
                NiaText(game.title, 18, FontWeight.Bold, Snow)
                NiaText(game.category.uppercase(), 11, FontWeight.Bold, Mist)
            }
        }
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        selected -> Color(0xFF12313A)
        focused -> Slate
        else -> Graphite
    }
    Box(
        Modifier
            .onFocusChanged { focused = it.isFocused }
            .border(if (focused) 2.dp else 1.dp, if (focused) Cyan else Color(0x334A5261), RoundedCornerShape(999.dp))
            .background(bg, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        NiaText(text, 13, FontWeight.Bold, if (selected || focused) Snow else Mist)
    }
}

@Composable
private fun DetailsScreen(
    game: Game,
    favorite: Boolean,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onPlay: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(58.dp),
        verticalArrangement = Arrangement.Center
    ) {
        NiaText(game.icon, 64, FontWeight.Normal, Snow)
        Spacer(Modifier.height(14.dp))
        NiaText(game.title, 42, FontWeight.Black, Snow)
        val controls = if (game.inputProfile.name == "CURSOR") "CURSOR NIA" else "D-PAD"
        NiaText(game.category.uppercase() + "  •  " + controls, 14, FontWeight.Bold, Cyan)
        Spacer(Modifier.height(18.dp))
        NiaText("Jogue offline usando apenas o controle da TV. BACK abre o menu NIA.", 19, FontWeight.Normal, Mist)
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            NiaButton("▶ JOGAR", onPlay)
            NiaButton(if (favorite) "★ FAVORITO" else "☆ FAVORITAR", onFavorite)
            NiaButton("← VOLTAR", onBack)
        }
        Spacer(Modifier.height(24.dp))
        NiaText("Fonte: 100 HTML Games Collection • " + game.license, 12, FontWeight.Normal, Mist)
    }
}

@Composable
private fun GameScreen(game: Game, onExit: () -> Unit) {
    val context = LocalContext.current
    val webView = remember(game.id) { GameWebView(context).apply { load(game) } }
    var paused by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    val pauseFocus = remember { FocusRequester() }

    fun openPause() {
        if (!paused) paused = true
    }

    fun resume() {
        paused = false
        webView.resumeGame()
    }

    DisposableEffect(webView) {
        webView.onBackRequested = { openPause() }
        webView.onPauseRequested = { openPause() }
        onDispose { webView.destroySafely() }
    }

    LaunchedEffect(paused) {
        if (paused) {
            webView.pauseGame()
            pauseFocus.requestFocus()
        } else {
            webView.resumeGame()
        }
    }

    BackHandler {
        if (paused) onExit() else openPause()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize().focusable())

        if (showControls && !paused) {
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
                    .background(Color(0xCC090B10), RoundedCornerShape(999.dp))
                    .clickable { showControls = false }
                    .padding(horizontal = 20.dp, vertical = 9.dp)
            ) {
                val help = if (game.inputProfile.name == "CURSOR") {
                    "D-PAD move • OK seleciona • BACK = MENU • PLAY/PAUSE = PAUSAR"
                } else {
                    "D-PAD joga • OK ação • BACK = MENU • PLAY/PAUSE = PAUSAR"
                }
                NiaText(help, 13, FontWeight.Bold, Snow)
            }
        }

        if (paused) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color(0xEE090B10))
                    .onPreviewKeyEvent { event ->
                        val native = event.nativeKeyEvent
                        if (native.action == KeyEvent.ACTION_UP &&
                            (native.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                             native.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                             native.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
                             native.keyCode == KeyEvent.KEYCODE_MENU ||
                             native.keyCode == KeyEvent.KEYCODE_BUTTON_START)
                        ) {
                            resume()
                            true
                        } else {
                            false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NiaText("MENU NIA", 15, FontWeight.Bold, Cyan)
                    Spacer(Modifier.height(6.dp))
                    NiaText("PAUSADO", 36, FontWeight.Black, Snow)
                    Spacer(Modifier.height(8.dp))
                    NiaText("BACK novamente volta para a biblioteca de jogos.", 14, FontWeight.Medium, Mist)
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NiaButton("▶ CONTINUAR", { resume() }, modifier = Modifier.focusRequester(pauseFocus))
                        NiaButton("↻ REINICIAR", {
                            webView.restart()
                            paused = false
                            webView.resumeGame()
                        })
                        NiaButton("⌂ JOGOS", onExit)
                    }
                }
            }
        }
    }
}
@Composable
private fun SettingsScreen(onBack: () -> Unit, onDiagnostics: () -> Unit, onLicenses: () -> Unit) {
    val context = LocalContext.current
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mi = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
    val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        context.display
    } else {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
    }
    val mode = display?.mode
    val refresh = display?.refreshRate ?: 0f

    Column(Modifier.fillMaxSize().padding(58.dp)) {
        NiaText("SYSTEM", 36, FontWeight.Black, Snow)
        NiaText("Informações reais fornecidas pelo Android", 15, FontWeight.Bold, Cyan)
        Spacer(Modifier.height(24.dp))

        val displayText = if (mode == null) "indisponível" else {
            mode.physicalWidth.toString() + "×" + mode.physicalHeight.toString() + " @ " + String.format("%.2f", refresh) + " Hz"
        }
        val rows = listOf(
            "Fabricante" to Build.MANUFACTURER,
            "Modelo" to Build.MODEL,
            "Android" to Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")",
            "RAM total" to Formatter.formatFileSize(context, mi.totalMem),
            "RAM disponível" to Formatter.formatFileSize(context, mi.availMem),
            "Low RAM device" to am.isLowRamDevice.toString(),
            "Display" to displayText
        )
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                NiaText(row.first, 16, FontWeight.Bold, Mist)
                Spacer(Modifier.weight(1f))
                NiaText(row.second, 16, FontWeight.Medium, Snow)
            }
        }
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NiaButton("REMOTE DIAGNOSTICS", onDiagnostics)
            NiaButton("LICENÇAS", onLicenses)
            NiaButton("← VOLTAR", onBack)
        }
    }
}

@Composable
private fun DiagnosticsScreen(onBack: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    var pressed by remember { mutableStateOf(setOf<Int>()) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val keys = listOf(
        KeyEvent.KEYCODE_DPAD_UP to "↑",
        KeyEvent.KEYCODE_DPAD_DOWN to "↓",
        KeyEvent.KEYCODE_DPAD_LEFT to "←",
        KeyEvent.KEYCODE_DPAD_RIGHT to "→",
        KeyEvent.KEYCODE_DPAD_CENTER to "OK"
    )

    Column(
        Modifier.fillMaxSize()
            .padding(58.dp)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.action == KeyEvent.ACTION_DOWN && native.keyCode != KeyEvent.KEYCODE_BACK) {
                    pressed = pressed + native.keyCode
                    true
                } else {
                    false
                }
            }
    ) {
        NiaText("REMOTE DIAGNOSTICS", 36, FontWeight.Black, Snow)
        NiaText("Pressione cada botão do controle.", 16, FontWeight.Bold, Cyan)
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            keys.forEach { item ->
                val code = item.first
                val label = item.second
                Box(
                    Modifier.size(110.dp)
                        .background(if (code in pressed) Color(0xFF164C33) else Graphite, RoundedCornerShape(18.dp))
                        .border(2.dp, if (code in pressed) Color(0xFF55E68A) else Slate, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NiaText(label, 28, FontWeight.Black, Snow)
                        NiaText(if (code in pressed) "PASS" else "AGUARDANDO", 10, FontWeight.Bold, if (code in pressed) Color(0xFF55E68A) else Mist)
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        NiaButton("← VOLTAR", onBack)
    }
}

@Composable
private fun LicensesScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(58.dp)) {
        NiaText("LICENÇAS", 36, FontWeight.Black, Snow)
        NiaText("NIA Arcade preserva a atribuição dos projetos incorporados.", 16, FontWeight.Bold, Cyan)
        Spacer(Modifier.height(24.dp))
        NiaText("Jogos: 100 HTML Games Collection — MIT License — Copyright (c) 2026 Can.", 18, FontWeight.Medium, Snow)
        Spacer(Modifier.height(10.dp))
        NiaText("O texto integral da licença está incluído no APK e em THIRD_PARTY_NOTICES.md.", 15, FontWeight.Normal, Mist)
        Spacer(Modifier.height(30.dp))
        NiaButton("← VOLTAR", onBack)
    }
}

@Composable
private fun NiaButton(
    text: String,
    onClick: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "button")
    Box(
        modifier
            .scale(scale)
            .onFocusChanged { focused = it.isFocused }
            .border(if (focused) 3.dp else 1.dp, if (focused) Cyan else Color(0x334A5261), RoundedCornerShape(14.dp))
            .background(if (focused) Color(0xFF16303A) else Graphite, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 16.dp else 22.dp, vertical = if (compact) 10.dp else 14.dp)
    ) {
        NiaText(text, if (compact) 12 else 14, FontWeight.Bold, Snow)
    }
}

@Composable
private fun NiaText(text: String, size: Int, weight: FontWeight, color: Color) {
    BasicText(text, style = TextStyle(color = color, fontSize = size.sp, fontWeight = weight))
}
