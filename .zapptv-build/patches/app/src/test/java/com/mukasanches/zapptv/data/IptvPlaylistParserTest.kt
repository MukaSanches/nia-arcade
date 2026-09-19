package com.mukasanches.zapptv.data

import com.mukasanches.zapptv.model.ChannelSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IptvPlaylistParserTest {
    @Test
    fun parsesBrazilianM3uMetadataAndGuide() {
        val m3u = """
            #EXTM3U x-tvg-url="https://example.test/guide.xml"
            #EXTINF:-1 tvg-id="TVBrasil.br" tvg-logo="https://example.test/logo.png" group-title="General",TV Brasil (720p)
            https://example.test/live.m3u8
        """.trimIndent()

        val parsed = m3u.byteInputStream().bufferedReader().use(IptvPlaylistParser::parse)

        assertEquals(listOf("https://example.test/guide.xml"), parsed.epgUrls)
        assertEquals(1, parsed.channels.size)
        val channel = parsed.channels.single()
        assertEquals("TV Brasil", channel.name)
        assertEquals("TVBrasil.br", channel.epgId)
        assertEquals("General", channel.category)
        assertEquals(ChannelSource.IPTV, channel.source)
        assertTrue(channel.number.toInt() >= 1000)
    }
    @Test
    fun preservesAlternateStreamsWithSameEpgIdForRepositoryFallback() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="TVBrasil.br",TV Brasil
            https://example.test/primary.m3u8
            #EXTINF:-1 tvg-id="TVBrasil.br",TV Brasil
            https://example.test/backup.m3u8
        """.trimIndent()

        val parsed = m3u.byteInputStream().bufferedReader().use(IptvPlaylistParser::parse)

        assertEquals(2, parsed.channels.size)
        assertEquals(
            setOf(
                "https://example.test/primary.m3u8",
                "https://example.test/backup.m3u8"
            ),
            parsed.channels.mapNotNull { it.uri }.toSet()
        )
    }
}
