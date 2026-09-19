package com.mukasanches.zapptv.ui

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.mukasanches.zapptv.core.AppScreen
import com.mukasanches.zapptv.core.ZappController
import com.mukasanches.zapptv.data.UserPlaylistStore
import com.mukasanches.zapptv.model.Channel
import com.mukasanches.zapptv.platform.FeatureDownloads
import com.mukasanches.zapptv.platform.FeaturePreferences
import com.mukasanches.zapptv.platform.NotificationHelper
import com.mukasanches.zapptv.platform.PlaybackPreferences
import com.mukasanches.zapptv.platform.SanchesCastController
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class HubSection(val label: String) {
    HOME("Início"),
    LIVE("Ao vivo"),
    MOVIES("Filmes"),
    SERIES("Séries"),
    SPORTS("Esportes"),
    SHORTS("Shorts TV"),
    GUIDE("Guia"),
    SEARCH("Buscar"),
    FAVORITES("Favoritos"),
    HISTORY("Histórico"),
    DOWNLOADS("Downloads"),
    PLAYLISTS("Playlists"),
    SETTINGS("Configurações"),
    SOURCES("Fontes"),
    DIAGNOSTICS("Diagnóstico")
}

@Composable
fun ExpandedSanchesTvApp(
    registerKeyHandler: (((KeyEvent) -> Boolean)?) -> Unit
) {
    val context = LocalContext.current
    val controller = remember { ZappController(context) }
    val featurePreferences = remember { FeaturePreferences(context) }
    val downloads = remember { FeatureDownloads(context) }
    val playlists = remember { UserPlaylistStore(context) }

    var section by remember { mutableStateOf(HubSection.HOME) }
    var playbackPreferences by remember { mutableStateOf(featurePreferences.playback()) }
    var searchHistory by remember { mutableStateOf(featurePreferences.searchHistory()) }
    var splash by remember { mutableStateOf(true) }
    var bannerMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(controller) {
        controller.start()

        val handler: (KeyEvent) -> Boolean = { event ->
            if (event.action != KeyEvent.ACTION_DOWN) {
                false
            } else {
                when {
                    event.keyCode == KeyEvent.KEYCODE_SEARCH -> {
                        controller.open(AppScreen.HOME)
                        section = HubSection.SEARCH
                        true
                    }

                    event.keyCode == KeyEvent.KEYCODE_BOOKMARK -> {
                        controller.open(AppScreen.HOME)
                        section = HubSection.FAVORITES
                        true
                    }

                    controller.state.screen == AppScreen.WATCH &&
                        (
                            event.keyCode == KeyEvent.KEYCODE_GUIDE ||
                                event.keyCode == KeyEvent.KEYCODE_DPAD_UP
                            ) -> {
                        controller.open(AppScreen.HOME)
                        section = HubSection.GUIDE
                        true
                    }

                    controller.state.screen == AppScreen.WATCH &&
                        event.keyCode == KeyEvent.KEYCODE_SETTINGS -> {
                        controller.open(AppScreen.HOME)
                        section = HubSection.SETTINGS
                        true
                    }

                    else -> controller.handleKey(event)
                }
            }
        }

        registerKeyHandler(handler)
        onDispose {
            registerKeyHandler(null)
            controller.close()
        }
    }

    LaunchedEffect(Unit) {
        NotificationHelper.ensureChannel(context)
        delay(3500)
        splash = false
    }

    LaunchedEffect(controller.state.loading) {
        if (!controller.state.loading) {
            delay(180)
            splash = false
        }
    }

    LaunchedEffect(bannerMessage) {
        if (bannerMessage != null) {
            delay(3500)
            bannerMessage = null
        }
    }

    SanchesTheme {
        when {
            splash -> ExpandedBrandSplash()

            controller.state.screen == AppScreen.WATCH -> ExpandedWatchScreen(
                controller = controller,
                preferences = playbackPreferences,
                downloads = downloads,
                onPreferencesChanged = { updated ->
                    playbackPreferences = updated
                    featurePreferences.savePlayback(updated)
                },
                onOpenSettings = {
                    controller.open(AppScreen.HOME)
                    section = HubSection.SETTINGS
                }
            )

            else -> ExpandedShell(
                controller = controller,
                section = section,
                onSection = { section = it },
                playbackPreferences = playbackPreferences,
                downloads = downloads,
                playlists = playlists,
                searchHistory = searchHistory,
                onSearch = { channel, query ->
                    if (query.isNotBlank()) {
                        featurePreferences.recordSearch(query)
                        searchHistory = featurePreferences.searchHistory()
                    }
                    controller.select(channel)
                },
                onClearSearch = {
                    featurePreferences.clearSearchHistory()
                    searchHistory = emptyList()
                },
                onPreferencesChanged = { updated ->
                    playbackPreferences = updated
                    featurePreferences.savePlayback(updated)
                },
                onBanner = { bannerMessage = it }
            )
        }

        bannerMessage?.let { message ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Text(
                    message,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xEE111820))
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    color = SanchesColors.PrimaryText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ExpandedShell(
    controller: ZappController,
    section: HubSection,
    onSection: (HubSection) -> Unit,
    playbackPreferences: PlaybackPreferences,
    downloads: FeatureDownloads,
    playlists: UserPlaylistStore,
    searchHistory: List<String>,
    onSearch: (Channel, String) -> Unit,
    onClearSearch: () -> Unit,
    onPreferencesChanged: (PlaybackPreferences) -> Unit,
    onBanner: (String) -> Unit
) {
    val state = controller.state

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(SanchesColors.Background)
    ) {
        ExpandedNavRail(
            selected = section,
            onNavigate = onSection
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF0B151D), SanchesColors.Background)
                    )
                )
        ) {
            when (section) {
                HubSection.HOME -> ExpandedHomeScreen(
                    state = state,
                    onSelect = controller::select,
                    onFavorite = controller::toggleFavorite
                )

                HubSection.LIVE -> ChannelNavigatorScreen(state, controller)

                HubSection.MOVIES -> ExpandedCollectionScreen(
                    title = "Filmes & Cinema",
                    subtitle = "Canais de filmes e cinema disponíveis nas fontes atuais",
                    channels = state.channels.filter(::isMovieChannelV23),
                    state = state,
                    onSelect = controller::select,
                    onFavorite = controller::toggleFavorite
                )

                HubSection.SERIES -> ExpandedCollectionScreen(
                    title = "Séries & Entretenimento",
                    subtitle = "Canais de séries, novelas e entretenimento",
                    channels = state.channels.filter(::isSeriesChannelV23),
                    state = state,
                    onSelect = controller::select,
                    onFavorite = controller::toggleFavorite
                )

                HubSection.SPORTS -> ExpandedSportsScreen(
                    state = state,
                    onSelect = controller::select,
                    onFavorite = controller::toggleFavorite
                )

                HubSection.SHORTS -> ExpandedShortsScreen(
                    state = state,
                    preferences = playbackPreferences,
                    onSelect = controller::select
                )

                HubSection.GUIDE -> ExpandedGuideScreen(
                    state = state,
                    onSelect = controller::select
                )

                HubSection.SEARCH -> ExpandedSearchScreen(
                    state = state,
                    history = searchHistory,
                    onSelect = onSearch,
                    onClearHistory = onClearSearch
                )

                HubSection.FAVORITES -> ExpandedCollectionScreen(
                    title = "Favoritos",
                    subtitle = "Sua seleção local, sem conta e sem login",
                    channels = state.channels.filter { it.id in state.favorites },
                    state = state,
                    onSelect = controller::select,
                    onFavorite = controller::toggleFavorite
                )

                HubSection.HISTORY -> ExpandedCollectionScreen(
                    title = "Histórico",
                    subtitle = "Canais assistidos recentemente neste dispositivo",
                    channels = channelsByIdsV23(state.channels, state.recentChannelIds),
                    state = state,
                    onSelect = controller::select,
                    onFavorite = controller::toggleFavorite
                )

                HubSection.DOWNLOADS -> ExpandedDownloadsScreen(
                    downloads = downloads,
                    currentChannel = state.currentChannel,
                    onDownloadCurrent = { result ->
                        onBanner(
                            result.fold(
                                onSuccess = { "Download iniciado (#$it)." },
                                onFailure = { it.message ?: "Não foi possível iniciar o download." }
                            )
                        )
                    }
                )

                HubSection.PLAYLISTS -> ExpandedPlaylistsScreen(
                    store = playlists,
                    onChanged = {
                        controller.refreshOnline(force = true, announce = true)
                    }
                )

                HubSection.SETTINGS -> ExpandedSettingsScreen(
                    value = playbackPreferences,
                    onChange = onPreferencesChanged
                )

                HubSection.SOURCES -> ExpandedSourcesScreen(
                    state = state,
                    store = playlists,
                    onRefresh = {
                        controller.refreshOnline(force = true, announce = true)
                    }
                )

                HubSection.DIAGNOSTICS -> ExpandedDiagnosticsScreen(
                    state = state,
                    store = playlists,
                    preferences = playbackPreferences
                )
            }
        }
    }
}

@Composable
private fun ExpandedNavRail(
    selected: HubSection,
    onNavigate: (HubSection) -> Unit
) {
    Column(
        modifier = Modifier
            .width(196.dp)
            .fillMaxHeight()
            .background(Color(0xFF06090D))
            .padding(horizontal = 14.dp, vertical = 20.dp)
    ) {
        Text(
            "S▶",
            color = SanchesColors.Accent,
            fontSize = 29.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            "SANCHES TV",
            color = SanchesColors.PrimaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        Spacer(Modifier.height(13.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            items(HubSection.entries, key = { it.name }) { item ->
                ExpandedNavItem(
                    label = item.label,
                    selected = item == selected,
                    onClick = { onNavigate(item) }
                )
            }
        }
    }
}

@Composable
private fun ExpandedNavItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(39.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    focused -> SanchesColors.Focus
                    selected -> Color(0xFF173546)
                    else -> Color.Transparent
                }
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            label,
            color = if (focused) SanchesColors.FocusText else SanchesColors.PrimaryText,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ExpandedWatchScreen(
    controller: ZappController,
    preferences: PlaybackPreferences,
    downloads: FeatureDownloads,
    onPreferencesChanged: (PlaybackPreferences) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val state = controller.state
    val channel = state.currentChannel
    val program = state.currentProgram

    if (channel == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Nenhum canal selecionado",
                color = SanchesColors.SecondaryText,
                fontSize = 16.sp
            )
        }
        return
    }

    var localMessage by remember(channel.id) { mutableStateOf<String?>(null) }
    var streamIndex by remember(channel.id) { androidx.compose.runtime.mutableIntStateOf(0) }

    LaunchedEffect(localMessage) {
        if (localMessage != null) {
            delay(3200)
            localMessage = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AdvancedPlayerSurface(
            channel = channel,
            preferences = preferences,
            muted = false,
            preferredCandidateIndex = streamIndex
        )

        if (controller.quickGuideVisible) {
            Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                QuickGuideOverlay(
                    state = state,
                    onSelect = controller::selectFromQuickGuide,
                    onFavorite = controller::toggleFavorite,
                    onClose = controller::closeQuickGuide
                )
            }
        }

        AnimatedVisibility(
            visible = controller.overlayVisible && !controller.quickGuideVisible,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xF205080C))
                        )
                    )
                    .padding(horizontal = 38.dp, vertical = 26.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SanchesChannelLogo(
                        channel = channel,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(Modifier.width(12.dp))

                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                channel.number,
                                color = SanchesColors.Accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(
                                channel.name,
                                color = SanchesColors.PrimaryText,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Text(
                            program?.title ?: "Ao vivo",
                            color = SanchesColors.PrimaryText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        val details = buildList {
                            programTimeExpanded(program?.startMillis, program?.endMillis)?.let(::add)
                            channel.category.takeIf { it.isNotBlank() }?.let(::add)
                            channel.sourceName.takeIf { it.isNotBlank() }?.let(::add)
                            if (channel.playbackUris().size > 1) {
                                add("${channel.playbackUris().size} streams de fallback")
                            }
                        }.joinToString("  •  ")

                        if (details.isNotBlank()) {
                            Text(
                                details,
                                color = SanchesColors.SecondaryText,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        CastRouteButtonV23()
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "CAST",
                            color = SanchesColors.Muted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    ExpandedChip("Transmitir", false) {
                        localMessage = SanchesCastController.cast(context, channel)
                            .fold(
                                onSuccess = { "Transmitindo para o dispositivo Cast." },
                                onFailure = { it.message ?: "Não foi possível transmitir." }
                            )
                    }

                    ExpandedChip(
                        if (preferences.subtitlesEnabled) "CC: ligado" else "CC: desligado",
                        preferences.subtitlesEnabled
                    ) {
                        onPreferencesChanged(
                            preferences.copy(subtitlesEnabled = !preferences.subtitlesEnabled)
                        )
                    }

                    if (channel.playbackUris().size > 1) {
                        ExpandedChip(
                            "Fonte ${streamIndex + 1}/${channel.playbackUris().size}",
                            false
                        ) {
                            streamIndex = (streamIndex + 1) % channel.playbackUris().size
                            localMessage = "Fonte alternativa ${streamIndex + 1} selecionada."
                        }
                    }

                    ExpandedChip("Baixar", false) {
                        localMessage = downloads.enqueue(channel)
                            .fold(
                                onSuccess = { "Download iniciado (#$it)." },
                                onFailure = { it.message ?: "Mídia não baixável." }
                            )
                    }

                    ExpandedChip(
                        if (channel.id in state.favorites) "★ Favorito" else "☆ Favoritar",
                        channel.id in state.favorites
                    ) {
                        controller.toggleFavorite(channel)
                    }

                    ExpandedChip("Configurações", false, onOpenSettings)

                    Spacer(Modifier.weight(1f))

                    Text(
                        "CH+/CH− trocar • ↑ guia • ↓/OK canais • LAST voltar",
                        color = SanchesColors.Muted,
                        fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }

                (localMessage ?: state.message)?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        color = SanchesColors.Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CastRouteButtonV23() {
    val context = LocalContext.current
    AndroidView(
        factory = {
            MediaRouteButton(it).apply {
                runCatching {
                    CastButtonFactory.setUpMediaRouteButton(context, this)
                }
            }
        },
        modifier = Modifier.size(42.dp)
    )
}

@Composable
private fun ExpandedBrandSplash() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF123044), SanchesColors.Background),
                    radius = 1200f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "S▶",
                color = SanchesColors.Accent,
                fontSize = 70.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.height(7.dp))
            Text(
                "SANCHESTV",
                color = SanchesColors.PrimaryText,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp
            )
            Text(
                "AO VIVO • CINEMA • ESPORTES • CAST",
                color = SanchesColors.SecondaryText,
                fontSize = 12.sp,
                letterSpacing = 1.sp
            )
        }
    }
}

private fun programTimeExpanded(start: Long?, end: Long?): String? {
    if (start == null || end == null) return null
    return "${formatTimeExpanded(start)} – ${formatTimeExpanded(end)}"
}

private fun formatTimeExpanded(value: Long): String =
    SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(value))
