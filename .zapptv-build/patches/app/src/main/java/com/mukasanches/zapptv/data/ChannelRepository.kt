package com.mukasanches.zapptv.data

import com.mukasanches.zapptv.model.Channel
import com.mukasanches.zapptv.model.ChannelSource
import com.mukasanches.zapptv.model.ProgramInfo

data class ChannelLoadResult(
    val channels: List<Channel>,
    val programs: Map<String, ProgramInfo>,
    val iptvChannelCount: Int
)

class ChannelRepository {
    fun load(
        iptvChannels: List<Channel> = emptyList(),
        iptvPrograms: Map<String, ProgramInfo> = emptyMap()
    ): ChannelLoadResult {
        val channels = buildList {
            addAll(iptvChannels)
            addAll(DefaultChannelCatalog.channels)
        }
            .distinctBy { channel ->
                channel.epgId?.substringBefore('@')?.lowercase()
                    ?: "${ChannelResolver.normalize(channel.name)}|${channel.uri.orEmpty()}"
            }

        val programs = iptvPrograms.toMutableMap()

        channels
            .filter {
                it.source == ChannelSource.HLS ||
                    it.source == ChannelSource.IPTV ||
                    it.source == ChannelSource.WEB
            }
            .forEach { channel ->
                programs.putIfAbsent(
                    channel.id,
                    ProgramInfo(
                        channelId = channel.id,
                        title = "Ao vivo",
                        description = "Transmissão pela internet"
                    )
                )
            }

        return ChannelLoadResult(
            channels = channels,
            programs = programs,
            iptvChannelCount = channels.count {
                it.source == ChannelSource.IPTV || it.source == ChannelSource.HLS
            }
        )
    }
}
