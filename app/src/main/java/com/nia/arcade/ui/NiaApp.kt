package com.nia.arcade.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.text.format.Formatter
import android.view.KeyEvent
import android.view.SoundEffectConstants
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nia.arcade.data.CatalogRepository
import com.nia.arcade.data.UserStateRepository
import com.nia.arcade.model.Game
import com.nia.arcade.model.PlayMode
import com.nia.arcade.runtime.GamePackManager
import com.nia.arcade.runtime.GameWebView
import kotlinx.coroutines.delay
import kotlin.random.Random

private val Void = Color(0xFF090B10)
private val Graphite = Color(0xFF151821)
private val Slate = Color(0xFF202532)
private val Cyan = Color(0xFF38E8FF)
private val Violet = Color(0xFF806BFF)
private val Snow = Color(0xFFF7F9FC)
private val Mist = Color(0xFFA4ACBA)
private val Success = Color(0xFF55E68A)
private val Warning = Color(0xFFFFCA55)
private val Error = Color(0xFFFF6470)

private enum class Screen { HOME, DETAILS, GAME, SETTINGS, DIAGNOSTICS, LICENSES }

@Composable
fun NiaApp() {
    var showSplash by remember { mutableStateOf(true) }
    if (showSplash) {
        NiaSplash(onFinished = { showSplash = false })
        return
    }

    val context = LocalContext.current
    val catalog = remember { runCatching { CatalogRepository(context).load() }.getOrDefault(emptyList()) }
    val state = remember { UserStateRepository(context) }

    var screen by remember { mutableStateOf(Screen.HOME) }
    var selected by remember { mutableStateOf<Game?>(null) }
    var profile by remember { mutableStateOf(state.activeProfile()) }
    var favorites by remember { mutableStateOf(state.favorites()) }
    var recents by remember { mutableStateOf(state.recents()) }

    fun launch(game: Game) {
        selected = game
        state.recordPlayed(game.id)
        state.markControlsSeen(game.id)
        recents = state.recents()
        screen = Screen.GAME
    }

    fun toggleProfile() {
        profile = state.toggleProfile()
        favorites = state.favorites()
        recents = state.recents()
    }

    Box(Modifier.fillMaxSize().background(Void)) {
        when (screen) {
            Screen.HOME -> HomeScreen(
                games = catalog,
                favorites = favorites,
                recents = recents,
                profile = profile,
                onGame = { selected = it; screen = Screen.DETAILS },
                onQuickPlay = { launch(it) },
                onProfileToggle = { toggleProfile() },
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
                        state = state,
                        favorite = game.id in favorites,
                        firstRun = !state.controlsSeen(game.id),
                        onBack = { screen = Screen.HOME },
                        onFavorite = {
                            state.setFavorite(game.id, game.id !in favorites)
                            favorites = state.favorites()
                        },
                        onPlay = { launch(game) }
                    )
                }
            }

            Screen.GAME -> {
                val game = selected
                if (game == null) {
                    screen = Screen.HOME
                } else {
                    GameScreen(
                        game = game,
                        state = state,
                        onExit = {
                            favorites = state.favorites()
                            recents = state.recents()
                            screen = Screen.HOME
                        }
                    )
                }
            }

            Screen.SETTINGS -> {
                BackHandler { screen = Screen.HOME }
                SettingsScreen(
                    state = state,
                    profile = profile,
                    gameCount = catalog.size,
                    onProfileToggle = { toggleProfile() },
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
    profile: String,
    onGame: (Game) -> Unit,
    onQuickPlay: (Game) -> Unit,
    onProfileToggle: () -> Unit,
    onSettings: () -> Unit
) {
    var filter by remember { mutableStateOf("all") }
    var highlighted by remember(games) { mutableStateOf(games.firstOrNull()) }
    var activityTick by remember { mutableIntStateOf(0) }
    var attract by remember { mutableStateOf(false) }

    LaunchedEffect(activityTick) {
        delay(60_000)
        attract = true
    }

    LaunchedEffect(attract, games) {
        if (!attract || games.isEmpty()) return@LaunchedEffect
        var index = games.indexOf(highlighted).coerceAtLeast(0)
        while (true) {
            delay(4_000)
            index = (index + 1) % games.size
            highlighted = games[index]
        }
    }

    val categories = remember(games) {
        listOf("all", "favorites", "recent", "az") + games.map { it.category }.distinct()
    }

    val visible = when (filter) {
        "favorites" -> games.filter { it.id in favorites }
        "recent" -> recents.mapNotNull { id -> games.firstOrNull { it.id == id } }
        "az" -> games.sortedBy { it.title }
        "all" -> games
        else -> games.filter { it.category == filter }
    }

    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent {
            activityTick++
            attract = false
            false
        }
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 46.dp, vertical = 24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    NiaText("NIA ARCADE", 32, FontWeight.Black, Snow)
                    NiaText("CONSOLE EDITION • " + games.size + " JOGOS • UM CONTROLE", 13, FontWeight.Bold, Cyan)
                }
                NiaButton("👤 " + profile, onProfileToggle, compact = true)
                Spacer(Modifier.size(8.dp))
                NiaButton("SISTEMA", onSettings, compact = true)
            }

            Spacer(Modifier.height(12.dp))
            highlighted?.let { HeroPanel(it) }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NiaButton(
                    "▶ JOGAR AGORA",
                    onClick = {
                        if (games.isNotEmpty()) onQuickPlay(games[Random.nextInt(games.size)])
                    }
                )

                val resume = recents.firstOrNull()?.let { id -> games.firstOrNull { it.id == id } }
                NiaButton(
                    if (resume != null) "↻ CONTINUAR" else "↻ SEM PARTIDA RECENTE",
                    onClick = { resume?.let(onQuickPlay) }
                )
            }

            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { category ->
                    val label = when (category) {
                        "all" -> "TODOS"
                        "favorites" -> "★ FAVORITOS"
                        "recent" -> "RECENTES"
                        "az" -> "A–Z"
                        else -> games.firstOrNull { it.category == category }?.categoryPt?.uppercase()
                            ?: category.uppercase()
                    }
                    FilterChip(label, selected = filter == category) { filter = category }
                }
            }

            Spacer(Modifier.height(14.dp))
            if (visible.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    NiaText("Nenhum jogo nesta seção.", 22, FontWeight.Medium, Mist)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    contentPadding = PaddingValues(bottom = 34.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    gridItems(visible, key = { it.id }) { game ->
                        GameCard(
                            game = game,
                            favorite = game.id in favorites,
                            onFocused = { highlighted = game },
                            onClick = { onGame(game) }
                        )
                    }
                }
            }
        }

        if (attract) {
            Box(
                Modifier.align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .background(Color(0xCC151821), RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                NiaText("MODO DEMONSTRAÇÃO • pressione qualquer botão", 11, FontWeight.Bold, Mist)
            }
        }
    }
}

@Composable
private fun HeroPanel(game: Game) {
    Box(
        Modifier.fillMaxWidth()
            .height(118.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF112B34), Color(0xFF201B3B), Graphite)
                ),
                RoundedCornerShape(22.dp)
            )
            .border(1.dp, Color(0x334DEBFF), RoundedCornerShape(22.dp))
            .padding(horizontal = 24.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NiaText(game.icon, 52, FontWeight.Black, Snow)
            Spacer(Modifier.size(20.dp))
            Column(Modifier.weight(1f)) {
                NiaText(game.title, 28, FontWeight.Black, Snow)
                val opponent = if (game.playMode == PlayMode.VS_CPU) " • VOCÊ vs CPU" else ""
                NiaText(game.categoryPt.uppercase() + " • " + game.controlsPt + opponent, 13, FontWeight.Bold, Cyan)
                NiaText("NIA CERTIFIED • OFFLINE • PT-BR", 11, FontWeight.Bold, Success)
            }
        }
    }
}

@Composable
private fun GameCard(
    game: Game,
    favorite: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scaleValue by animateFloatAsState(if (focused) 1.055f else 1f, label = "card-scale")
    val view = LocalView.current

    Box(
        Modifier
            .height(112.dp)
            .scale(scaleValue)
            .onFocusChanged {
                if (it.isFocused && !focused) {
                    focused = true
                    onFocused()
                    runCatching { view.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN) }
                } else if (!it.isFocused) {
                    focused = false
                }
            }
            .border(if (focused) 3.dp else 1.dp, if (focused) Cyan else Color(0x334A5261), RoundedCornerShape(16.dp))
            .background(
                if (focused) Brush.linearGradient(listOf(Color(0xFF16303A), Color(0xFF211D3A)))
                else Brush.linearGradient(listOf(Graphite, Graphite)),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NiaText(game.icon, 28, FontWeight.Black, Snow)
                Spacer(Modifier.size(8.dp))
                if (favorite) NiaText("★", 16, FontWeight.Black, Warning)
            }
            Spacer(Modifier.height(7.dp))
            NiaText(game.title, 14, FontWeight.Bold, Snow)
            NiaText(
                if (game.playMode == PlayMode.VS_CPU) "VOCÊ vs CPU" else game.categoryPt.uppercase(),
                10,
                FontWeight.Bold,
                if (game.playMode == PlayMode.VS_CPU) Violet else Mist
            )
        }
    }
}

@Composable
private fun DetailsScreen(
    game: Game,
    state: UserStateRepository,
    favorite: Boolean,
    firstRun: Boolean,
    onBack: () -> Unit,
    onFavorite: () -> Unit,
    onPlay: () -> Unit
) {
    var difficulty by remember(game.id) { mutableStateOf(state.difficulty(game.id)) }

    Column(Modifier.fillMaxSize().padding(54.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NiaText(game.icon, 70, FontWeight.Black, Snow)
            Spacer(Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                NiaText(game.title, 42, FontWeight.Black, Snow)
                val mode = if (game.playMode == PlayMode.VS_CPU) "VOCÊ vs CPU" else "1 JOGADOR"
                NiaText(game.categoryPt.uppercase() + " • " + mode + " • PT-BR", 15, FontWeight.Bold, Cyan)
                NiaText("NIA Certified • offline • perfil individual de TV", 13, FontWeight.Bold, Success)
            }
        }

        Spacer(Modifier.height(26.dp))
        InfoBox("CONTROLES", game.controlsPt)
        Spacer(Modifier.height(10.dp))
        InfoBox(
            "TELA",
            game.orientation.lowercase().replaceFirstChar { it.uppercase() } +
                " • perfil " + game.layoutType.name.lowercase() +
                " • " + game.maxWidthVw + "% × " + game.maxHeightVh + "%"
        )

        if (firstRun) {
            Spacer(Modifier.height(10.dp))
            InfoBox("PRIMEIRA PARTIDA", "Use somente o controle da TV. BACK abre o Menu NIA durante o jogo.")
        }

        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NiaButton("▶ INICIAR", onPlay)
            NiaButton(if (favorite) "★ FAVORITO" else "☆ FAVORITAR", onFavorite)

            if (game.supportsDifficulty) {
                NiaButton(
                    "DIFICULDADE: " + difficulty.labelPt().uppercase(),
                    onClick = {
                        difficulty = difficulty.next()
                        state.setDifficulty(game.id, difficulty)
                    }
                )
            }

            NiaButton("← VOLTAR", onBack)
        }
    }
}

@Composable
private fun InfoBox(title: String, value: String) {
    Column(
        Modifier.fillMaxWidth()
            .background(Graphite, RoundedCornerShape(14.dp))
            .border(1.dp, Slate, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        NiaText(title, 11, FontWeight.Black, Cyan)
        Spacer(Modifier.height(5.dp))
        NiaText(value, 16, FontWeight.Medium, Snow)
    }
}

@Composable
private fun GameScreen(
    game: Game,
    state: UserStateRepository,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val webView = remember(game.id) { GameWebView(context) }
    val pauseFocus = remember { FocusRequester() }
    val sessionStarted = remember(game.id) { SystemClock.elapsedRealtime() }

    var paused by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var retryCount by remember { mutableIntStateOf(0) }
    var fatalMessage by remember { mutableStateOf<String?>(null) }
    var showControls by remember { mutableStateOf(false) }
    var difficulty by remember(game.id) { mutableStateOf(state.difficulty(game.id)) }
    var favorite by remember(game.id) { mutableStateOf(game.id in state.favorites()) }

    DisposableEffect(webView) {
        webView.onBackRequested = { paused = true }
        webView.onPauseRequested = { paused = true }
        webView.onGameReady = {
            ready = true
            fatalMessage = null
            webView.setDifficulty(difficulty)
        }
        webView.onRuntimeError = { message ->
            if (!ready) fatalMessage = message
        }
        webView.load(game)

        onDispose {
            state.recordSession(game.id, SystemClock.elapsedRealtime() - sessionStarted)
            webView.destroySafely()
        }
    }

    LaunchedEffect(game.id, retryCount, ready) {
        if (ready) return@LaunchedEffect
        delay(4_000)
        if (!ready) {
            if (retryCount == 0) {
                retryCount = 1
                fatalMessage = "Recuperando o jogo..."
                webView.restart()
            } else {
                state.recordFailure(game.id)
                fatalMessage = "O jogo não respondeu corretamente."
            }
        }
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
        if (paused) onExit() else paused = true
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize().focusable())

        if (!ready && !paused) {
            Box(
                Modifier.align(Alignment.TopCenter)
                    .padding(top = 18.dp)
                    .background(Color(0xDD090B10), RoundedCornerShape(999.dp))
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            ) {
                NiaText(
                    if (retryCount == 0) "PREPARANDO " + game.title.uppercase() + "..." else "RECUPERANDO JOGO...",
                    12,
                    FontWeight.Bold,
                    Cyan
                )
            }
        }

        if (fatalMessage != null && retryCount > 0 && !ready && !paused) {
            Box(
                Modifier.align(Alignment.Center)
                    .background(Color(0xF0090B10), RoundedCornerShape(18.dp))
                    .border(1.dp, Error, RoundedCornerShape(18.dp))
                    .padding(28.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NiaText("PROTEÇÃO NIA", 12, FontWeight.Black, Error)
                    Spacer(Modifier.height(8.dp))
                    NiaText(fatalMessage.orEmpty(), 18, FontWeight.Bold, Snow)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NiaButton("↻ TENTAR DE NOVO", {
                            fatalMessage = null
                            retryCount = 0
                            ready = false
                            webView.restart()
                        })
                        NiaButton("⌂ VOLTAR AOS JOGOS", onExit)
                    }
                }
            }
        }

        if (showControls && !paused) {
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .padding(bottom = 18.dp)
                    .background(Color(0xEE090B10), RoundedCornerShape(16.dp))
                    .border(1.dp, Cyan, RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    NiaText(game.controlsPt, 14, FontWeight.Bold, Snow)
                    NiaButton("FECHAR", { showControls = false }, compact = true)
                }
            }
        }

        if (paused) {
            Box(
                Modifier.fillMaxSize()
                    .background(Color(0xEE090B10))
                    .onPreviewKeyEvent { event ->
                        val native = event.nativeKeyEvent
                        if (
                            native.action == KeyEvent.ACTION_UP &&
                            (
                                native.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                                    native.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                                    native.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
                                    native.keyCode == KeyEvent.KEYCODE_MENU ||
                                    native.keyCode == KeyEvent.KEYCODE_BUTTON_START
                                )
                        ) {
                            paused = false
                            true
                        } else {
                            false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    NiaText("MENU NIA", 14, FontWeight.Black, Cyan)
                    Spacer(Modifier.height(4.dp))
                    NiaText(game.title, 32, FontWeight.Black, Snow)
                    NiaText("PAUSADO", 15, FontWeight.Bold, Mist)
                    Spacer(Modifier.height(20.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NiaButton("▶ CONTINUAR", { paused = false }, modifier = Modifier.focusRequester(pauseFocus))
                        NiaButton("↻ REINICIAR", {
                            ready = false
                            retryCount = 0
                            fatalMessage = null
                            webView.restart()
                            paused = false
                        })
                        NiaButton("🎮 CONTROLES", {
                            showControls = true
                            paused = false
                        })
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (game.supportsDifficulty) {
                            NiaButton("DIFICULDADE: " + difficulty.labelPt().uppercase(), {
                                difficulty = difficulty.next()
                                state.setDifficulty(game.id, difficulty)
                                webView.setDifficulty(difficulty)
                            })
                        }

                        NiaButton(if (favorite) "★ FAVORITO" else "☆ FAVORITAR", {
                            favorite = !favorite
                            state.setFavorite(game.id, favorite)
                        })

                        NiaButton("⌂ JOGOS", onExit)
                    }

                    Spacer(Modifier.height(14.dp))
                    NiaText("BACK novamente volta imediatamente para a biblioteca.", 12, FontWeight.Bold, Mist)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: UserStateRepository,
    profile: String,
    gameCount: Int,
    onProfileToggle: () -> Unit,
    onBack: () -> Unit,
    onDiagnostics: () -> Unit,
    onLicenses: () -> Unit
) {
    val context = LocalContext.current
    val stats = state.stats()
    val achievements = state.achievements()
    val packManager = remember { GamePackManager(context) }
    val packs = remember { packManager.discoverLocalPacks() }
    val bundled = remember { packManager.bundledPack() }
    val hours = stats.totalPlayMs / 3_600_000.0

    Column(Modifier.fillMaxSize().padding(50.dp)) {
        NiaText("SISTEMA NIA", 36, FontWeight.Black, Snow)
        NiaText("Console Edition • perfil local: " + profile, 14, FontWeight.Bold, Cyan)
        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("JOGOS", gameCount.toString())
            StatCard("PARTIDAS", stats.totalLaunches.toString())
            StatCard("EXPLORADOS", stats.uniqueGames.toString())
            StatCard("HORAS", String.format("%.1f", hours))
            StatCard("FALHAS", stats.runtimeFailures.toString())
        }

        Spacer(Modifier.height(20.dp))
        NiaText("CONQUISTAS LOCAIS", 13, FontWeight.Black, Cyan)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            achievements.forEach { achievement ->
                val name = achievement.first
                val unlocked = achievement.second
                Box(
                    Modifier.background(if (unlocked) Color(0xFF173A2B) else Graphite, RoundedCornerShape(12.dp))
                        .border(1.dp, if (unlocked) Success else Slate, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp)
                ) {
                    NiaText((if (unlocked) "✓ " else "○ ") + name, 11, FontWeight.Bold, if (unlocked) Success else Mist)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        NiaText("PACOTES DE JOGOS", 13, FontWeight.Black, Cyan)
        NiaText(bundled.displayName + ": " + bundled.gameCount + " jogos • " + bundled.source, 14, FontWeight.Bold, Snow)
        NiaText("Pacotes locais detectados: " + packs.size, 12, FontWeight.Medium, Mist)

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NiaButton("👤 TROCAR PERFIL", onProfileToggle)
            NiaButton("DIAGNÓSTICO DO CONTROLE", onDiagnostics)
            NiaButton("LICENÇAS", onLicenses)
            NiaButton("← VOLTAR", onBack)
        }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Column(
        Modifier.size(width = 142.dp, height = 84.dp)
            .background(Graphite, RoundedCornerShape(14.dp))
            .border(1.dp, Slate, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        NiaText(label, 10, FontWeight.Black, Mist)
        Spacer(Modifier.height(5.dp))
        NiaText(value, 24, FontWeight.Black, Snow)
    }
}

@Composable
private fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    var pressed by remember { mutableStateOf(setOf<Int>()) }
    var lastCode by remember { mutableIntStateOf(-1) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val activity = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memory = ActivityManager.MemoryInfo().also(activity::getMemoryInfo)
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val display = runCatching { wm.defaultDisplay.mode }.getOrNull()

    val keys = listOf(
        KeyEvent.KEYCODE_DPAD_UP to "↑",
        KeyEvent.KEYCODE_DPAD_DOWN to "↓",
        KeyEvent.KEYCODE_DPAD_LEFT to "←",
        KeyEvent.KEYCODE_DPAD_RIGHT to "→",
        KeyEvent.KEYCODE_DPAD_CENTER to "OK"
    )

    Column(
        Modifier.fillMaxSize()
            .padding(46.dp)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.action == KeyEvent.ACTION_DOWN && native.keyCode != KeyEvent.KEYCODE_BACK) {
                    lastCode = native.keyCode
                    pressed = pressed + native.keyCode
                    true
                } else {
                    false
                }
            }
    ) {
        NiaText("DIAGNÓSTICO NIA", 34, FontWeight.Black, Snow)
        NiaText(
            "Modelo: " + Build.MANUFACTURER + " " + Build.MODEL +
                " • Android " + Build.VERSION.RELEASE + " • SDK " + Build.VERSION.SDK_INT,
            13,
            FontWeight.Bold,
            Cyan
        )
        NiaText(
            "RAM total: " + Formatter.formatFileSize(context, memory.totalMem) +
                " • Low RAM: " + activity.isLowRamDevice,
            12,
            FontWeight.Medium,
            Mist
        )
        if (display != null) {
            NiaText(
                "Tela: " + display.physicalWidth + "×" + display.physicalHeight +
                    " • " + String.format("%.1f", display.refreshRate) + " Hz",
                12,
                FontWeight.Medium,
                Mist
            )
        }
        NiaText("Último keycode: " + lastCode, 12, FontWeight.Medium, Warning)

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            keys.forEach { item ->
                val code = item.first
                val label = item.second
                Box(
                    Modifier.size(92.dp)
                        .background(if (code in pressed) Color(0xFF164C33) else Graphite, RoundedCornerShape(16.dp))
                        .border(2.dp, if (code in pressed) Success else Slate, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        NiaText(label, 22, FontWeight.Black, Snow)
                        NiaText(if (code in pressed) "OK" else "TESTAR", 9, FontWeight.Bold, if (code in pressed) Success else Mist)
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        NiaButton("← VOLTAR", onBack)
    }
}

@Composable
private fun LicensesScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(52.dp)) {
        NiaText("SOBRE E LICENÇAS", 34, FontWeight.Black, Snow)
        NiaText("NIA Arcade 1.2.0 • Console Edition", 15, FontWeight.Bold, Cyan)
        Spacer(Modifier.height(18.dp))
        NiaText("100 jogos certificados • offline • interface e adaptação pt-BR • feitos para controle de TV.", 17, FontWeight.Medium, Snow)
        Spacer(Modifier.height(12.dp))
        NiaText("Coleção-base: 100 HTML Games Collection — MIT License — Copyright (c) 2026 Can.", 14, FontWeight.Medium, Mist)
        NiaText("O APK inclui o texto integral da licença e preserva a atribuição do projeto original.", 13, FontWeight.Medium, Mist)
        Spacer(Modifier.height(24.dp))
        NiaButton("← VOLTAR", onBack)
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .onFocusChanged { focused = it.isFocused }
            .background(
                when {
                    selected -> Color(0xFF16303A)
                    focused -> Color(0xFF202B36)
                    else -> Graphite
                },
                RoundedCornerShape(999.dp)
            )
            .border(
                if (selected || focused) 2.dp else 1.dp,
                if (selected) Cyan else if (focused) Snow else Slate,
                RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp)
    ) {
        NiaText(text, 11, FontWeight.Bold, if (selected) Cyan else Snow)
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
    val scaleValue by animateFloatAsState(if (focused) 1.055f else 1f, label = "button-scale")
    val view = LocalView.current

    Box(
        modifier
            .scale(scaleValue)
            .onFocusChanged {
                if (it.isFocused && !focused) {
                    focused = true
                    runCatching { view.playSoundEffect(SoundEffectConstants.NAVIGATION_DOWN) }
                } else if (!it.isFocused) {
                    focused = false
                }
            }
            .border(if (focused) 3.dp else 1.dp, if (focused) Cyan else Color(0x334A5261), RoundedCornerShape(14.dp))
            .background(if (focused) Color(0xFF16303A) else Graphite, RoundedCornerShape(14.dp))
            .clickable {
                runCatching { view.playSoundEffect(SoundEffectConstants.CLICK) }
                onClick()
            }
            .padding(
                horizontal = if (compact) 15.dp else 20.dp,
                vertical = if (compact) 9.dp else 13.dp
            )
    ) {
        NiaText(text, if (compact) 11 else 13, FontWeight.Bold, Snow)
    }
}

@Composable
private fun NiaText(text: String, size: Int, weight: FontWeight, color: Color) {
    BasicText(text, style = TextStyle(color = color, fontSize = size.sp, fontWeight = weight))
}
