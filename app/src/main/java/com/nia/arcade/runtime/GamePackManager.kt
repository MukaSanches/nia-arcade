package com.nia.arcade.runtime

import android.content.Context
import java.io.File

data class GamePackInfo(
    val id: String,
    val displayName: String,
    val gameCount: Int,
    val source: String
)

class GamePackManager(private val context: Context) {
    fun bundledPack(): GamePackInfo = GamePackInfo(
        id = "nia-bundled-100",
        displayName = "Coleção NIA 100",
        gameCount = 100,
        source = "APK"
    )

    fun discoverLocalPacks(): List<GamePackInfo> {
        val root = File(context.getExternalFilesDir(null), "packs")
        if (!root.exists() || !root.isDirectory) return emptyList()
        return root.listFiles()
            .orEmpty()
            .filter { it.isDirectory }
            .mapNotNull { dir ->
                val games = dir.resolve("games")
                if (!games.isDirectory) return@mapNotNull null
                GamePackInfo(
                    id = dir.name,
                    displayName = dir.name.replace('-', ' ').replaceFirstChar { it.uppercase() },
                    gameCount = games.listFiles().orEmpty().count { it.isDirectory },
                    source = "Armazenamento local"
                )
            }
            .sortedBy { it.displayName }
    }
}
