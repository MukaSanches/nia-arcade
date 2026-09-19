package com.mukasanches.zapptv.data

import com.mukasanches.zapptv.model.Channel
import com.mukasanches.zapptv.model.ChannelSource
import java.io.BufferedReader

data class ParsedIptvPlaylist(
    val channels: List<Channel>,
    val epgUrls: List<String>
)

object IptvPlaylistParser {
    private val attributeRegex = Regex("""([\w-]+)="([^"]*)"""")
    private val epgUrlRegex = Regex("""(?:x-tvg-url|url-tvg)="([^"]+)"""", RegexOption.IGNORE_CASE)

    fun parse(reader: BufferedReader): ParsedIptvPlaylist {
        val channels = mutableListOf<Channel>()
        val epgUrls = linkedSetOf<String>()
        var pendingInfo: String? = null
        var sequence = 0

        reader.forEachLine { rawLine ->
            val line = rawLine.trim()
            when {
                line.isBlank() -> Unit
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    epgUrlRegex.findAll(line).forEach { match ->
                        match.groupValues[1]
                            .split(',', ';')
                            .map { it.trim() }
                            .filter { it.startsWith("https://", ignoreCase = true) }
                            .forEach(epgUrls::add)
                    }
                }

                line.startsWith("#EXTINF", ignoreCase = true) -> pendingInfo = line

                line.startsWith("#") -> Unit

                pendingInfo != null && isSupportedStream(line) -> {
                    val info = pendingInfo.orEmpty()
                    val attributes = attributeRegex.findAll(info)
                        .associate { it.groupValues[1].lowercase() to it.groupValues[2].trim() }

                    val rawName = info.substringAfter(',', "Canal IPTV").trim()
                    val name = cleanName(rawName).ifBlank { "Canal IPTV" }
                    val tvgId = attributes["tvg-id"]?.takeIf { it.isNotBlank() }
                    val group = attributes["group-title"]?.takeIf { it.isNotBlank() } ?: "IPTV"
                    val logo = attributes["tvg-logo"]?.takeIf { it.isNotBlank() }
                    val unique = "${tvgId ?: ChannelResolver.normalize(name)}:${line.hashCode()}"

                    channels += Channel(
                        id = "iptv:$unique",
                        number = (1000 + sequence).toString(),
                        name = name,
                        source = ChannelSource.IPTV,
                        canonicalId = ChannelResolver.canonicalId(name),
                        category = group,
                        uri = line,
                        epgId = tvgId,
                        logoUri = logo
                    )
                    sequence += 1
                    pendingInfo = null
                }

                pendingInfo != null -> pendingInfo = null
            }
        }

        return ParsedIptvPlaylist(
            channels = channels.distinctBy { it.id },
            epgUrls = epgUrls.toList()
        )
    }

    private fun cleanName(value: String): String =
        value
            .replace(Regex("""\s+\((?:\d{3,4}p|SD|HD|FHD|UHD|4K)\)\s*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+\[(?:Geo-blocked|Not 24/7|Geo|Offline)\]\s*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(?:FHD|UHD|4K)\s*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()

    private fun isSupportedStream(value: String): Boolean =
        value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true)
}
