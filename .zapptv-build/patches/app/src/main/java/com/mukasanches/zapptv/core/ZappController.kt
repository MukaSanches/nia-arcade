package com.mukasanches.zapptv.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.mukasanches.zapptv.data.ChannelRepository
import com.mukasanches.zapptv.data.DefaultChannelCatalog
import com.mukasanches.zapptv.data.IptvRepository
import com.mukasanches.zapptv.data.IptvSnapshot
import com.mukasanches.zapptv.data.PreferencesRepository
import com.mukasanches.zapptv.data.SourceHealth
import com.mukasanches.zapptv.data.IptvSource
import com.mukasanches.zapptv.data.UpdateRepository
import com.mukasanches.zapptv.platform.HomeChannelPublisher
import com.mukasanches.zapptv.model.Channel
import com.mukasanches.zapptv.model.ProgramInfo
import com.mukasanches.zapptv.model.ProgramSlot
import com.mukasanches.zapptv.BuildConfig
import com.mukasanches.zapptv.playback.PlaybackTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppScreen {
    HOME,
    WATCH,
    LIVE,
    MOVIES,
    SERIES,
    SPORTS,
    GUIDE,
    FAVORITES,
    SEARCH,
    SOURCES,
    DIAGNOSTICS
}

data class AppUiState(
    val loading: Boolean = false,
    val channels: List<Channel> = emptyList(),
    val programs: Map<String, ProgramInfo> = emptyMap(),
    val schedule: Map<String, List<ProgramSlot>> = emptyMap(),
    val favorites: Set<String> = emptySet(),
    val recentChannelIds: List<String> = emptyList(),
    val mostWatchedIds: List<String> = emptyList(),
    val selectedIndex: Int = 0,
    val screen: AppScreen = AppScreen.HOME,
    val iptvChannelCount: Int = 0,
    val epgProgramCount: Int = 0,
    val sourceCount: Int = 0,
    val healthySourceCount: Int = 0,
    val userSourceCount: Int = 0,
    val sourceHealth: Map<String, SourceHealth> = emptyMap(),
    val playbackStatus: String = "Aguardando reprodução",
    val playbackResolution: String = "—",
    val activeStreamIndex: Int = 0,
    val activeStreamCount: Int = 0,
    val activeStreamScore: Int = 0,
    val firstFrameMs: Long = 0L,
    val bufferCount: Int = 0,
    val bitrateKbps: Int = 0,
    val frameRate: Float = 0f,
    val videoCodec: String = "—",
    val updateStatus: String = "Atualização não verificada",
    val updateAvailable: Boolean = false,
    val updateVersionName: String? = null,
    val updateUrl: String? = null,
    val updateSha256: String? = null,
    val lastOnlineRefreshMillis: Long = 0L,
    val onlineStatus: String = "Inicializando central IPTV",
    val message: String? = null
) {
    val currentChannel: Channel?
        get() = channels.getOrNull(selectedIndex)

    val currentProgram: ProgramInfo?
        get() = currentChannel?.let { programs[it.id] }
}

class ZappController(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs = PreferencesRepository(appContext)
    private val channelRepository = ChannelRepository()
    private val iptvRepository = IptvRepository(appContext)
    private val updateRepository = UpdateRepository(appContext)
    private val homeChannelPublisher = HomeChannelPublisher(appContext)

    private val immediateChannels = DefaultChannelCatalog.channels
    private var pendingRestoreId: String? = prefs.lastChannelId()
    private var started = false
    private var refreshJob: Job? = null
    private var numericJob: Job? = null
    private var messageJob: Job? = null
    private var numericBuffer = ""
    private var previousChannelId: String? = null
    private var lastZapAt = 0L

    var state by mutableStateOf(
        AppUiState(
            loading = true,
            channels = immediateChannels,
            favorites = prefs.favorites(),
            recentChannelIds = prefs.recentChannelIds(),
            mostWatchedIds = mostWatchedIds(),
            selectedIndex = immediateChannels.indexOfFirst { it.id == pendingRestoreId }
                .takeIf { it >= 0 } ?: 0
        )
    )
        private set

    var overlayGeneration by mutableIntStateOf(0)
        private set

    var overlayVisible by mutableStateOf(true)
        private set

    var quickGuideVisible by mutableStateOf(false)
        private set

    var captionsEnabled by mutableStateOf(prefs.captionsEnabled())
        private set

    var startInLastChannel by mutableStateOf(prefs.startInLastChannel())
        private set

    var preferredAudioLanguage by mutableStateOf(prefs.preferredAudioLanguage())
        private set

    var maxVideoHeight by mutableIntStateOf(prefs.maxVideoHeight())
        private set

    var playbackPanelVisible by mutableStateOf(false)
        private set

    var navigatorCategory by mutableStateOf("Todos")
    var navigatorFocusId by mutableStateOf<String?>(null)

    fun start() {
        if (started) return
        started = true

        scope.launch {
            val cached = withContext(Dispatchers.IO) { iptvRepository.loadCached() }
            applySnapshot(cached)
            if (startInLastChannel && state.currentChannel != null) {
                state = state.copy(screen = AppScreen.WATCH)
                showOverlay()
            }
            refreshOnline(force = false)
            checkForUpdates(announce = false)

            while (isActive) {
                delay(EPG_REEVALUATION_MS)
                refreshOnline(force = false)
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    fun handleKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false

        if (event.keyCode == KeyEvent.KEYCODE_PROG_RED && event.repeatCount == 0) {
            refreshOnline(force = true, announce = true)
            return true
        }

        if (event.keyCode == KeyEvent.KEYCODE_SEARCH && event.repeatCount == 0) {
            quickGuideVisible = false
            open(AppScreen.SEARCH)
            return true
        }

        if (event.keyCode == KeyEvent.KEYCODE_BOOKMARK && event.repeatCount == 0) {
            quickGuideVisible = false
            open(AppScreen.FAVORITES)
            return true
        }

        if (state.screen != AppScreen.WATCH) return false

        if (
            event.repeatCount == 0 &&
            (event.keyCode == KeyEvent.KEYCODE_SETTINGS ||
                event.keyCode == KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK)
        ) {
            playbackPanelVisible = !playbackPanelVisible
            if (playbackPanelVisible) {
                quickGuideVisible = false
                overlayVisible = false
            } else {
                showOverlay()
            }
            return true
        }

        if (event.keyCode == KeyEvent.KEYCODE_CAPTIONS && event.repeatCount == 0) {
            captionsEnabled = !captionsEnabled
            prefs.setCaptionsEnabled(captionsEnabled)
            showMessage(
                if (captionsEnabled) "Legendas ativadas" else "Legendas desativadas",
                1800
            )
            return true
        }

        if (playbackPanelVisible) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0) {
                playbackPanelVisible = false
                showOverlay()
                return true
            }
            return false
        }

        if (quickGuideVisible) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0) {
                quickGuideVisible = false
                showOverlay()
                return true
            }
            return false
        }

        val digit = digitForKey(event.keyCode)
        if (digit != null) {
            if (event.repeatCount == 0) enterChannelDigit(digit)
            return true
        }

        val zapDelta = when (event.keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> 1

            KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_DPAD_LEFT -> -1

            else -> 0
        }

        if (zapDelta != 0) {
            val now = SystemClock.elapsedRealtime()
            if (event.repeatCount == 0 || now - lastZapAt >= ZAP_REPEAT_THROTTLE_MS) {
                lastZapAt = now
                zap(zapDelta)
            }
            return true
        }

        if (event.repeatCount > 0) return true

        return when (event.keyCode) {
            KeyEvent.KEYCODE_LAST_CHANNEL -> {
                lastChannel()
                true
            }
            KeyEvent.KEYCODE_GUIDE,
            KeyEvent.KEYCODE_DPAD_UP -> {
                open(AppScreen.GUIDE)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                quickGuideVisible = true
                overlayVisible = false
                true
            }
            KeyEvent.KEYCODE_INFO -> {
                showOverlay()
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                open(AppScreen.HOME)
                true
            }
            else -> false
        }
    }

    fun zap(delta: Int) {
        val channels = state.channels
        if (channels.isEmpty()) return
        val size = channels.size
        selectIndex((state.selectedIndex + delta).floorMod(size))
    }

    fun select(channel: Channel) {
        val index = state.channels.indexOfFirst { it.id == channel.id }
        if (index >= 0) selectIndex(index)
    }

    fun openChannelById(channelId: String) {
        pendingRestoreId = channelId
        val index = state.channels.indexOfFirst { it.id == channelId }
        if (index >= 0) {
            pendingRestoreId = null
            selectIndex(index)
        }
    }

    fun selectFromQuickGuide(channel: Channel) {
        quickGuideVisible = false
        select(channel)
    }

    fun closeQuickGuide() {
        quickGuideVisible = false
        showOverlay()
    }

    fun lastChannel() {
        val id = previousChannelId ?: return
        val index = state.channels.indexOfFirst { it.id == id }
        if (index >= 0) selectIndex(index)
    }

    fun toggleFavorite(channel: Channel) {
        val isFavorite = channel.id in state.favorites
        prefs.setFavorite(channel.id, !isFavorite)
        state = state.copy(favorites = prefs.favorites())
    }

    fun open(screen: AppScreen) {
        quickGuideVisible = false
        state = state.copy(screen = screen)
        if (screen == AppScreen.WATCH) showOverlay()
    }

    fun rememberNavigator(category: String, focusId: String?) {
        navigatorCategory = category
        navigatorFocusId = focusId
    }

    fun configuredSources(): List<IptvSource> = iptvRepository.configuredSources()

    fun setAudioMode(language: String?) {
        preferredAudioLanguage = language
        prefs.setPreferredAudioLanguage(language)
        showMessage(
            if (language.isNullOrBlank()) "Áudio: original/automático" else "Áudio: Português",
            1800
        )
    }

    fun setVideoHeight(height: Int) {
        maxVideoHeight = height.coerceAtLeast(0)
        prefs.setMaxVideoHeight(maxVideoHeight)
        showMessage(
            if (maxVideoHeight == 0) "Qualidade: automática" else "Qualidade: até ${maxVideoHeight}p",
            1800
        )
    }

    fun closePlaybackPanel() {
        playbackPanelVisible = false
        showOverlay()
    }

    fun toggleStartInLastChannel() {
        startInLastChannel = !startInLastChannel
        prefs.setStartInLastChannel(startInLastChannel)
        showMessage(
            if (startInLastChannel) "Inicialização: último canal" else "Inicialização: Início",
            2200
        )
    }

    fun addUserSource(url: String): Boolean {
        val added = iptvRepository.addUserSource(url) ?: return false
        showMessage("Fonte adicionada: " + added.name, 2600)
        refreshOnline(force = true)
        return true
    }

    fun removeUserSource(id: String) {
        iptvRepository.removeUserSource(id)
        showMessage("Fonte removida", 2200)
        refreshOnline(force = true)
    }

    fun updatePlaybackTelemetry(telemetry: PlaybackTelemetry) {
        state = state.copy(
            playbackStatus = telemetry.status,
            playbackResolution = telemetry.resolution,
            activeStreamIndex = telemetry.streamIndex,
            activeStreamCount = telemetry.streamCount,
            activeStreamScore = telemetry.score,
            firstFrameMs = telemetry.firstFrameMs,
            bufferCount = telemetry.bufferCount,
            bitrateKbps = telemetry.bitrateKbps,
            frameRate = telemetry.frameRate,
            videoCodec = telemetry.videoCodec
        )
    }

    fun checkForUpdates(announce: Boolean = true) {
        scope.launch {
            val check = withContext(Dispatchers.IO) { updateRepository.check() }
            state = state.copy(
                updateStatus = check.status,
                updateAvailable = check.available,
                updateVersionName = check.latestVersion,
                updateUrl = check.apkUrl,
                updateSha256 = check.sha256
            )
            if (announce) showMessage(check.status, 2800)
        }
    }

    fun openAvailableUpdate() {
        val url = state.updateUrl ?: return
        val sha256 = state.updateSha256 ?: run {
            showMessage("Atualização sem hash de segurança", 2400)
            return
        }

        if (!appContext.packageManager.canRequestPackageInstalls()) {
            runCatching {
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${appContext.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(settingsIntent)
                showMessage("Autorize instalar apps e tente novamente", 3500)
            }.onFailure {
                showMessage("Não foi possível abrir a permissão de instalação", 2500)
            }
            return
        }

        scope.launch {
            state = state.copy(updateStatus = "Baixando atualização…")
            val apk = withContext(Dispatchers.IO) {
                updateRepository.downloadVerified(url, sha256)
            }

            if (apk == null) {
                state = state.copy(updateStatus = "Falha na validação da atualização")
                showMessage("Download inválido ou SHA-256 não confere", 3200)
                return@launch
            }

            val uri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.files",
                apk
            )

            runCatching {
                val install = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                appContext.startActivity(install)
                state = state.copy(updateStatus = "APK verificado • instalador aberto")
            }.onFailure {
                state = state.copy(updateStatus = "Não foi possível abrir o instalador")
                showMessage("APK validado, mas o instalador não abriu", 2800)
            }
        }
    }

    fun copyDiagnostics() {
        val channel = state.currentChannel
        val text = buildString {
            appendLine("SANCHESTV ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Build: " + if (BuildConfig.DEBUG) "debug" else "release")
            appendLine("Canal: ${channel?.name ?: "—"}")
            appendLine("Playback: ${state.playbackStatus}")
            appendLine("Resolução: ${state.playbackResolution}")
            appendLine("Stream: ${state.activeStreamIndex}/${state.activeStreamCount}")
            appendLine("Saúde: ${state.activeStreamScore}%")
            appendLine("Primeiro frame: ${state.firstFrameMs} ms")
            appendLine("Rebuffers: ${state.bufferCount}")
            appendLine("Bitrate: ${state.bitrateKbps} kbps")
            appendLine("FPS: ${state.frameRate}")
            appendLine("Codec: ${state.videoCodec}")
            appendLine("Canais: ${state.channels.size}")
            appendLine("Fontes: ${state.healthySourceCount}/${state.sourceCount}")
            appendLine("EPG atual: ${state.epgProgramCount}")
            appendLine("Atualização: ${state.updateStatus}")
        }

        val clipboard = appContext.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("SANCHESTV diagnóstico", text))
        showMessage("Diagnóstico copiado", 1800)
    }

    fun showOverlay() {
        overlayVisible = true
        overlayGeneration += 1
        val generation = overlayGeneration
        scope.launch {
            delay(OVERLAY_TIMEOUT_MS)
            if (
                overlayGeneration == generation &&
                state.screen == AppScreen.WATCH &&
                !quickGuideVisible
            ) {
                overlayVisible = false
            }
        }
    }

    fun refreshOnline(force: Boolean, announce: Boolean = false) {
        if (refreshJob?.isActive == true) return
        if (announce) showMessage("Atualizando central IPTV…", 2500)

        refreshJob = scope.launch {
            val refreshed = withContext(Dispatchers.IO) { iptvRepository.refresh(force) }
            applySnapshot(refreshed)
            if (announce) showMessage(refreshed.status, 3500)
        }
    }

    private fun applySnapshot(snapshot: IptvSnapshot) {
        val loaded = channelRepository.load(snapshot.channels, snapshot.programs)
        val currentId = state.currentChannel?.id

        val restoreIndex = pendingRestoreId
            ?.let { wanted -> loaded.channels.indexOfFirst { it.id == wanted } }
            ?.takeIf { it >= 0 }

        val currentIndex = currentId
            ?.let { wanted -> loaded.channels.indexOfFirst { it.id == wanted } }
            ?.takeIf { it >= 0 }

        val selected = restoreIndex ?: currentIndex ?: 0
        if (restoreIndex != null) pendingRestoreId = null

        val homeChannels = loaded.channels
        val homePrograms = loaded.programs
        val homeFavorites = prefs.favorites()
        val homeRecents = prefs.recentChannelIds()

        state = state.copy(
            loading = false,
            channels = loaded.channels,
            programs = loaded.programs,
            schedule = snapshot.schedule,
            favorites = prefs.favorites(),
            recentChannelIds = prefs.recentChannelIds(),
            mostWatchedIds = mostWatchedIds(),
            selectedIndex = selected.coerceIn(0, (loaded.channels.size - 1).coerceAtLeast(0)),
            iptvChannelCount = loaded.iptvChannelCount,
            epgProgramCount = snapshot.programs.size,
            sourceCount = snapshot.sourceCount,
            healthySourceCount = snapshot.healthySourceCount,
            userSourceCount = snapshot.userSourceCount,
            sourceHealth = snapshot.sourceHealth,
            lastOnlineRefreshMillis = snapshot.lastUpdatedMillis,
            onlineStatus = snapshot.status
        )

        scope.launch(Dispatchers.IO) {
            homeChannelPublisher.publish(
                channels = homeChannels,
                programs = homePrograms,
                favorites = homeFavorites,
                recentIds = homeRecents
            )
        }
    }

    private fun enterChannelDigit(digit: Int) {
        numericBuffer = (numericBuffer + digit.toString()).takeLast(5)
        showMessage("Canal $numericBuffer", NUMERIC_COMMIT_DELAY_MS + 900)
        numericJob?.cancel()
        numericJob = scope.launch {
            delay(NUMERIC_COMMIT_DELAY_MS)
            commitNumericChannel()
        }
    }

    private fun commitNumericChannel() {
        val typed = numericBuffer
        numericBuffer = ""
        if (typed.isBlank()) return

        val exact = state.channels.indexOfFirst { it.number.trim() == typed }
        if (exact >= 0) {
            selectIndex(exact)
        } else {
            showMessage("Canal $typed não encontrado", 1800)
        }
    }

    private fun showMessage(text: String, durationMs: Long) {
        messageJob?.cancel()
        state = state.copy(message = text)
        showOverlay()
        messageJob = scope.launch {
            delay(durationMs)
            if (state.message == text) state = state.copy(message = null)
        }
    }

    private fun selectIndex(index: Int) {
        val old = state.currentChannel
        val next = state.channels.getOrNull(index) ?: return
        if (old?.id != next.id) previousChannelId = old?.id

        prefs.setLastChannel(next.id)
        if (old?.id != next.id) prefs.recordTune(next.id)

        state = state.copy(
            selectedIndex = index,
            screen = AppScreen.WATCH,
            message = null,
            recentChannelIds = prefs.recentChannelIds(),
            mostWatchedIds = mostWatchedIds()
        )
        showOverlay()
    }

    private fun mostWatchedIds(): List<String> =
        prefs.watchCounts()
            .entries
            .sortedByDescending { it.value }
            .take(50)
            .map { it.key }

    private fun digitForKey(keyCode: Int): Int? = when (keyCode) {
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> keyCode - KeyEvent.KEYCODE_0
        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 ->
            keyCode - KeyEvent.KEYCODE_NUMPAD_0
        else -> null
    }

    private fun Int.floorMod(size: Int): Int {
        val value = this % size
        return if (value < 0) value + size else value
    }

    companion object {
        private const val ZAP_REPEAT_THROTTLE_MS = 150L
        private const val NUMERIC_COMMIT_DELAY_MS = 1100L
        private const val EPG_REEVALUATION_MS = 30L * 60L * 1000L
        private const val OVERLAY_TIMEOUT_MS = 4500L
    }
}
