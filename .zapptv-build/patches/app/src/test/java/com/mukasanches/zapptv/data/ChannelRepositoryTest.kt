package com.mukasanches.zapptv.data

import com.mukasanches.zapptv.model.Channel
import com.mukasanches.zapptv.model.ChannelSource
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelRepositoryTest {
    @Test
    fun keepsDistinctChannelsWithoutEpgId() {
        val input = listOf(
            Channel(
                id = "iptv:one",
                number = "1000",
                name = "Canal Um",
                source = ChannelSource.IPTV,
                uri = "https://example.test/one.m3u8"
            ),
            Channel(
                id = "iptv:two",
                number = "1001",
                name = "Canal Dois",
                source = ChannelSource.IPTV,
                uri = "https://example.test/two.m3u8"
            )
        )

        val result = ChannelRepository().load(iptvChannels = input)

        assertTrue(result.channels.any { it.id == "iptv:one" })
        assertTrue(result.channels.any { it.id == "iptv:two" })
    }
}
