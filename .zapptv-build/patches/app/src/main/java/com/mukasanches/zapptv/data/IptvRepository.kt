package com.mukasanches.zapptv.data

import android.content.Context
import com.mukasanches.zapptv.model.Channel
import com.mukasanches.zapptv.model.ProgramInfo
import com.mukasanches.zapptv.model.ProgramSlot
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader

data class IptvSnapshot(
    val channels: List<Channel> = emptyList(),
    val programs: Map<String, ProgramInfo> = emptyMap(),
    val schedule: Map<String, List<ProgramSlot>> = emptyMap(),
    val epgUrls: List<String> = emptyList(),
    val lastUpdatedMillis: Long = 0L,
    val sourceCount: Int = 0,
    val healthySourceCount: Int = 0,
    val userSourceCount: Int = 0,
    val sourceHealth: Map<String, SourceHealth> = emptyMap(),
    val status: String = "Inicializando central IPTV"
)

class IptvRepository(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, "sanchestv-cache").apply { mkdirs() }
    private val legacyDirectory = File(appContext.filesDir, "zapptv-cache")
    private val userSources = UserM3uSourceStore(appContext)
    private val healthStore = SourceHealthStore(appContext)
    private val http = HttpDownloadEngine(appContext)
    private val epgCache = EpgDiskCache(appContext)

    init {
        seedBundledCache()
        migrateLegacyCache()
    }

    fun configuredSources(): List<IptvSource> = allSources()

    fun addUserSource(url: String): IptvSource? = userSources.add(url)

    fun removeUserSource(id: String) {
        val source = userSources.sources().firstOrNull { it.id == id }
        if (source != null) {
            playlistFile(source).delete()
            userSources.remove(id)
        }
    }

    fun loadCached(): IptvSnapshot {
        val sources = allSources().filter(IptvSource::enabled)
        val catalogs = cachedCatalogs(sources)
        val channels = mergeChannels(catalogs)
        val epg = readEpg(channels)
        val programs = epg.currentPrograms
        val updated = allCacheFiles(sources).filter(File::isFile).maxOfOrNull(File::lastModified) ?: 0L

        return snapshot(
            sources = sources,
            catalogs = catalogs,
            channels = channels,
            programs = programs,
            schedule = epg.timeline,
            updated = updated,
            cached = true
        )
    }

    fun refresh(force: Boolean = false): IptvSnapshot {
        val sources = allSources().filter(IptvSource::enabled)

        sources.forEach { source ->
            val file = playlistFile(source)
            val shouldRefresh = force || isStale(file, PLAYLIST_TTL_MS)
            if (shouldRefresh) {
                val downloaded = download(
                    url = source.url,
                    target = file,
                    maxBytes = MAX_PLAYLIST_BYTES,
                    validator = ::isValidPlaylist
                )

                if (!downloaded) {
                    healthStore.recordFailure(source.id)
                }
            }

            val parsed = if (file.isFile && file.length() > 0L) parsePlaylist(source, file) else null
            if (parsed != null && parsed.channels.isNotEmpty()) {
                healthStore.recordSuccess(source.id, parsed.channels.size)
            } else if (!shouldRefresh) {
                healthStore.recordFailure(source.id)
            }
        }

        val catalogs = cachedCatalogs(sources)
        val channels = mergeChannels(catalogs)

        IptvSourceRegistry.epg
            .sortedByDescending(EpgSource::priority)
            .forEach { source ->
                val file = epgFile(source)
                if (force || isStale(file, EPG_TTL_MS)) {
                    download(
                        url = source.url,
                        target = file,
                        maxBytes = MAX_EPG_BYTES,
                        validator = ::isValidEpg
                    )
                }
            }

        val epg = readEpg(channels)
        val programs = epg.currentPrograms
        val updated = allCacheFiles(sources).filter(File::isFile).maxOfOrNull(File::lastModified) ?: 0L

        return snapshot(
            sources = sources,
            catalogs = catalogs,
            channels = channels,
            programs = programs,
            schedule = epg.timeline,
            updated = updated,
            cached = false
        )
    }

    private fun snapshot(
        sources: List<IptvSource>,
        catalogs: List<Pair<IptvSource, ParsedIptvPlaylist>>,
        channels: List<Channel>,
        programs: Map<String, ProgramInfo>,
        schedule: Map<String, List<ProgramSlot>>,
        updated: Long,
        cached: Boolean
    ): IptvSnapshot {
        val userCount = sources.count { it.id.startsWith("user-") }
        val health = healthStore.snapshot(sources.map(IptvSource::id))
        val healthyCount = catalogs.size

        val status = when {
            channels.isEmpty() && cached -> "Sem cache IPTV; catálogo essencial ativo"
            channels.isEmpty() -> "Fontes indisponíveis; catálogo essencial ativo"
            healthyCount < sources.size ->
                "${channels.size} canais • ${healthyCount}/${sources.size} fontes válidas"
            programs.isEmpty() ->
                "${channels.size} canais • ${sources.size} fontes • EPG atualizando"
            else ->
                "${channels.size} canais • ${sources.size} fontes • EPG Agora/Depois"
        }

        return IptvSnapshot(
            channels = channels,
            programs = programs,
            schedule = schedule,
            epgUrls = IptvSourceRegistry.epg.map(EpgSource::url),
            lastUpdatedMillis = updated,
            sourceCount = sources.size,
            healthySourceCount = healthyCount,
            userSourceCount = userCount,
            sourceHealth = health,
            status = status
        )
    }

    private fun allSources(): List<IptvSource> =
        (IptvSourceRegistry.builtIn + userSources.sources())
            .distinctBy(IptvSource::id)

    private fun cachedCatalogs(
        sources: List<IptvSource>
    ): List<Pair<IptvSource, ParsedIptvPlaylist>> =
        sources.mapNotNull { source ->
            val file = playlistFile(source)
            if (!file.isFile || file.length() <= 0L) return@mapNotNull null

            val parsed = parsePlaylist(source, file)
            if (parsed.channels.isEmpty()) null else source to parsed
        }

    private fun parsePlaylist(source: IptvSource, file: File): ParsedIptvPlaylist =
        runCatching {
            BufferedReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8)).use { reader ->
                val parsed = IptvPlaylistParser.parse(reader)
                val filtered = source.groupFilter?.let { wanted ->
                    parsed.channels.filter { it.category.equals(wanted, ignoreCase = true) }
                } ?: parsed.channels

                parsed.copy(
                    channels = filtered.map { channel ->
                        channel.copy(
                            sourceName = source.name,
                            sourceNames = listOf(source.name)
                        )
                    }
                )
            }
        }.getOrDefault(ParsedIptvPlaylist(emptyList(), emptyList()))

    private fun mergeChannels(
        catalogs: List<Pair<IptvSource, ParsedIptvPlaylist>>
    ): List<Channel> {
        val merged = linkedMapOf<String, Channel>()
        val aliasToPrimary = mutableMapOf<String, String>()

        catalogs
            .sortedByDescending { (source, _) ->
                source.priority + healthStore.get(source.id).reliabilityScore() / 10
            }
            .forEach { (_, parsed) ->
                parsed.channels.forEach { candidate ->
                    val keys = identityKeys(candidate)
                    val existingPrimary = keys.firstNotNullOfOrNull { aliasToPrimary[it] }

                    if (existingPrimary == null) {
                        val primary = keys.first()
                        merged[primary] = candidate
                        keys.forEach { aliasToPrimary[it] = primary }
                    } else {
                        val existing = merged[existingPrimary] ?: return@forEach
                        val alternates = (
                            existing.backupUris +
                                listOfNotNull(candidate.uri) +
                                candidate.backupUris
                            )
                            .filter { it != existing.uri }
                            .distinct()

                        merged[existingPrimary] = existing.copy(
                            logoUri = existing.logoUri ?: candidate.logoUri,
                            epgId = existing.epgId ?: candidate.epgId,
                            sourceNames = (
                                existing.sourceNames +
                                    candidate.sourceNames +
                                    candidate.sourceName
                                )
                                .filter(String::isNotBlank)
                                .distinct(),
                            backupUris = alternates
                        )
                        keys.forEach { aliasToPrimary[it] = existingPrimary }
                    }
                }
            }

        return merged.values
            .sortedWith(
                compareBy<Channel> { categoryRank(ChannelClassifier.category(it)) }
                    .thenBy { ChannelResolver.normalize(it.name) }
            )
            .mapIndexed { index, channel ->
                channel.copy(number = (1000 + index).toString())
            }
    }

    private fun identityKeys(channel: Channel): List<String> {
        val keys = linkedSetOf<String>()

        channel.canonicalId
            ?.trim()
            ?.lowercase()
            ?.takeIf(String::isNotBlank)
            ?.let { keys += "canonical:$it" }

        val normalizedName = ChannelResolver.normalize(channel.name)
        if (normalizedName.isNotBlank()) keys += "name:$normalizedName"

        channel.epgId
            ?.substringBefore('@')
            ?.trim()
            ?.lowercase()
            ?.takeIf(String::isNotBlank)
            ?.let { keys += "epg:$it" }

        if (keys.isEmpty()) keys += "uri:${channel.uri.orEmpty()}"
        return keys.toList()
    }

    private fun categoryRank(category: String): Int =
        ChannelClassifier.primaryCategories.indexOf(category).takeIf { it >= 0 } ?: 99

    private fun readEpg(channels: List<Channel>): EpgSchedule {
        if (channels.isEmpty()) return EpgSchedule()

        val sourceVersion = IptvSourceRegistry.epg
            .sortedByDescending(EpgSource::priority)
            .joinToString("|") { source ->
                val file = epgFile(source)
                val token = http.versionToken(source.url)
                    ?: "${file.length()}:${file.lastModified()}"
                "${source.id}:$token"
            }

        epgCache.load(
            channels = channels,
            sourceVersion = sourceVersion
        )?.let { cached ->
            return cached
        }

        val current = linkedMapOf<String, ProgramInfo>()
        val timeline = linkedMapOf<String, MutableList<ProgramSlot>>()

        IptvSourceRegistry.epg
            .sortedByDescending(EpgSource::priority)
            .forEach { source ->
                val file = epgFile(source)
                if (file.isFile && file.length() > 0L) {
                    val parsed = XmlTvParser.parseSchedule(file, channels)
                    parsed.currentPrograms.forEach { (id, program) ->
                        current.putIfAbsent(id, program)
                    }
                    parsed.timeline.forEach { (id, programs) ->
                        timeline.getOrPut(id) { mutableListOf() }.addAll(programs)
                    }
                }
            }

        val result = EpgSchedule(
            currentPrograms = current,
            timeline = timeline.mapValues { (_, programs) ->
                programs
                    .distinctBy { "${it.startMillis}:${it.endMillis}:${it.title}" }
                    .sortedBy(ProgramSlot::startMillis)
                    .take(MAX_EPG_PROGRAMS_PER_CHANNEL)
            }
        )
        epgCache.save(result, sourceVersion)
        return result
    }


    private fun seedBundledCache() {
        val source = IptvSourceRegistry.builtIn
            .firstOrNull { it.id == BOOTSTRAP_SOURCE_ID }
            ?: return
        val target = playlistFile(source)

        val usable = runCatching {
            target.isFile &&
                target.length() > 0L &&
                parsePlaylist(source, target).channels.size >= MIN_BOOTSTRAP_CHANNELS
        }.getOrDefault(false)

        if (usable) return

        val temp = File(target.parentFile, "${target.name}.seed")
        runCatching {
            appContext.assets.open(BOOTSTRAP_ASSET).use { input ->
                FileOutputStream(temp).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }

            require(temp.length() > 0L) { "Catálogo inicial vazio" }
            if (!temp.renameTo(target)) {
                temp.copyTo(target, overwrite = true)
                temp.delete()
            }
            target.setLastModified(System.currentTimeMillis())
        }.onFailure {
            temp.delete()
        }
    }

    private fun migrateLegacyCache() {
        val legacyPlaylist = File(legacyDirectory, "br.m3u")
        val primary = playlistFile(IptvSourceRegistry.builtIn.first())
        if (!primary.exists() && legacyPlaylist.isFile && legacyPlaylist.length() > 0L) {
            runCatching { legacyPlaylist.copyTo(primary, overwrite = false) }
        }

        val legacyEpg = File(legacyDirectory, "br-epg.xml")
        val firstEpg = epgFile(IptvSourceRegistry.epg.first())
        if (!firstEpg.exists() && legacyEpg.isFile && legacyEpg.length() > 0L) {
            runCatching { legacyEpg.copyTo(firstEpg, overwrite = false) }
        }
    }

    private fun playlistFile(source: IptvSource): File =
        File(directory, "playlist-${source.id}.m3u")

    private fun epgFile(source: EpgSource): File =
        File(directory, "epg-${source.id}.xml")

    private fun allCacheFiles(sources: List<IptvSource>): List<File> =
        sources.map(::playlistFile) + IptvSourceRegistry.epg.map(::epgFile)

    private fun isStale(file: File, ttlMillis: Long): Boolean =
        !file.isFile ||
            file.length() == 0L ||
            System.currentTimeMillis() - file.lastModified() > ttlMillis

    private fun isValidPlaylist(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false

        val first = runCatching {
            file.bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readLine()?.removePrefix("\uFEFF")?.trim().orEmpty()
            }
        }.getOrDefault("")

        if (!first.startsWith("#EXTM3U", ignoreCase = true)) return false
        return parsePlaylist(
            IptvSource(
                id = "validator",
                name = "Validação",
                url = "https://localhost/",
                priority = 0,
                market = "validação"
            ),
            file
        ).channels.isNotEmpty()
    }

    private fun isValidEpg(file: File): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        val prefix = runCatching {
            file.inputStream().buffered().use { input ->
                val bytes = ByteArray(1024)
                val count = input.read(bytes)
                if (count <= 0) "" else String(bytes, 0, count, Charsets.UTF_8)
            }
        }.getOrDefault("")
        return prefix.contains("<tv", ignoreCase = true) || prefix.contains("<?xml", ignoreCase = true)
    }

    private fun download(
        url: String,
        target: File,
        maxBytes: Long,
        validator: ((File) -> Boolean)? = null
    ): Boolean =
        http.download(
            url = url,
            target = target,
            maxBytes = maxBytes,
            validator = validator
        ) != HttpDownloadResult.FAILED

    companion object {
        private const val PLAYLIST_TTL_MS = 3L * 60L * 60L * 1000L
        private const val EPG_TTL_MS = 3L * 60L * 60L * 1000L
        private const val MAX_PLAYLIST_BYTES = 64L * 1024L * 1024L
        private const val MAX_EPG_BYTES = 96L * 1024L * 1024L
        private const val MAX_EPG_PROGRAMS_PER_CHANNEL = 336
        private const val BOOTSTRAP_ASSET = "bootstrap-br.m3u"
        private const val BOOTSTRAP_SOURCE_ID = "iptv-org-br"
        private const val MIN_BOOTSTRAP_CHANNELS = 50
    }
}
