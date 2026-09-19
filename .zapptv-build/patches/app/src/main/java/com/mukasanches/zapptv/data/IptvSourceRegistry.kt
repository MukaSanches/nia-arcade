package com.mukasanches.zapptv.data

data class IptvSource(
    val id: String,
    val name: String,
    val url: String,
    val priority: Int,
    val market: String,
    val enabled: Boolean = true,
    val groupFilter: String? = null,
    val healthChecked: Boolean = false
)

data class EpgSource(
    val id: String,
    val name: String,
    val url: String,
    val priority: Int
)

object IptvSourceRegistry {
    /**
     * Public/free IPTV sources only.
     *
     * dearbulut-online is intentionally first: it is health-checked upstream and
     * provides a broad, working snapshot for first-run bootstrap and recovery.
     * Regional/language-specific sources then enrich metadata and provide
     * alternate streams for the same canonical channels.
     */
    val builtIn: List<IptvSource> = listOf(
        IptvSource(
            id = "dearbulut-online",
            name = "SANCHESTV Working Channels",
            url = "https://dearbulut.github.io/iptv/playlists/online.m3u",
            priority = 150,
            market = "Mundo • streams verificados",
            healthChecked = true
        ),
        IptvSource(
            id = "iptv-org-br",
            name = "IPTV-org Brasil",
            url = "https://iptv-org.github.io/iptv/countries/br.m3u",
            priority = 145,
            market = "Brasil"
        ),
        IptvSource(
            id = "iptv-com-br",
            name = "IPTV-com Brasil",
            url = "https://github.com/iptv-com/iptv/raw/refs/heads/main/lists/brazil.m3u",
            priority = 142,
            market = "Brasil"
        ),
        IptvSource(
            id = "pluto-br",
            name = "Pluto TV Brasil",
            url = "https://raw.githubusercontent.com/BuddyChewChew/app-m3u-generator/main/playlists/plutotv_br.m3u",
            priority = 138,
            market = "Brasil • FAST"
        ),
        IptvSource(
            id = "m3upt-tv",
            name = "M3UPT",
            url = "https://m3upt.com/iptv",
            priority = 132,
            market = "Portugal • lusófono",
            groupFilter = "TV"
        ),
        IptvSource(
            id = "iptv-org-pt",
            name = "IPTV-org Português",
            url = "https://iptv-org.github.io/iptv/languages/por.m3u",
            priority = 128,
            market = "Português"
        ),
        IptvSource(
            id = "pluto-fast",
            name = "Pluto TV FAST",
            url = "https://raw.githubusercontent.com/BuddyChewChew/app-m3u-generator/main/playlists/plutotv_all.m3u",
            priority = 120,
            market = "FAST • global"
        ),
        IptvSource(
            id = "plex-fast",
            name = "Plex TV FAST",
            url = "https://raw.githubusercontent.com/BuddyChewChew/app-m3u-generator/main/playlists/plex_all.m3u",
            priority = 118,
            market = "FAST • global"
        ),
        IptvSource(
            id = "samsung-fast",
            name = "Samsung TV Plus FAST",
            url = "https://raw.githubusercontent.com/BuddyChewChew/app-m3u-generator/main/playlists/samsungtvplus_all.m3u",
            priority = 116,
            market = "FAST • global"
        ),
        IptvSource(
            id = "roku-fast",
            name = "Roku FAST",
            url = "https://raw.githubusercontent.com/BuddyChewChew/app-m3u-generator/main/playlists/roku_all.m3u",
            priority = 114,
            market = "FAST • global"
        ),
        IptvSource(
            id = "tubi-fast",
            name = "Tubi FAST",
            url = "https://raw.githubusercontent.com/BuddyChewChew/app-m3u-generator/main/playlists/tubi_all.m3u",
            priority = 112,
            market = "FAST • global"
        ),
        IptvSource(
            id = "free-tv",
            name = "Free-TV",
            url = "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8",
            priority = 110,
            market = "Mundo • gratuito"
        ),
        IptvSource(
            id = "iptv-org-global",
            name = "IPTV-org Global",
            url = "https://iptv-org.github.io/iptv/index.m3u",
            priority = 85,
            market = "Mundo"
        ),
        IptvSource(
            id = "dearbulut-best",
            name = "Health-checked fallback",
            url = "https://dearbulut.github.io/iptv/playlists/best.m3u",
            priority = 80,
            market = "Fallback • verificado",
            healthChecked = true
        )
    )

    val epg: List<EpgSource> = listOf(
        EpgSource(
            id = "dearbulut-br",
            name = "SANCHESTV EPG Brasil",
            url = "https://dearbulut.github.io/iptv/epg/br.xml.gz",
            priority = 140
        ),
        EpgSource(
            id = "iptv-com-br",
            name = "IPTV-com EPG Brasil",
            url = "https://raw.githubusercontent.com/iptv-com/epg/main/guides/brazil.xml",
            priority = 120
        ),
        EpgSource(
            id = "iptv-org-br",
            name = "IPTV-org EPG Brasil",
            url = "https://iptv-org.github.io/epg/guides/br/mi.tv.epg.xml",
            priority = 110
        )
    )
}
