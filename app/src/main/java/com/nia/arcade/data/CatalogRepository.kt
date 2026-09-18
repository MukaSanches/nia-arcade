package com.nia.arcade.data

import android.content.Context
import com.nia.arcade.model.DirectionScheme
import com.nia.arcade.model.Game
import com.nia.arcade.model.InputProfile
import com.nia.arcade.model.LayoutType
import com.nia.arcade.model.PlayMode
import org.json.JSONArray

class CatalogRepository(private val context: Context) {
    fun load(): List<Game> {
        val text = context.assets.open("catalog.json").bufferedReader().use { it.readText() }
        val array = JSONArray(text)
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    Game(
                        id = item.getInt("id"),
                        slug = item.getString("slug"),
                        title = item.getString("title"),
                        originalTitle = item.getString("originalTitle"),
                        icon = item.optString("icon", "🎮"),
                        category = item.getString("category"),
                        categoryPt = item.optString("categoryPt", item.getString("category")),
                        inputProfile = InputProfile.valueOf(item.getString("inputProfile")),
                        directionScheme = DirectionScheme.valueOf(item.getString("directionScheme")),
                        actionKey = item.optString("actionKey", "Enter"),
                        controlsPt = item.optString("controlsPt", "Setas + OK"),
                        playMode = PlayMode.valueOf(item.optString("playMode", "SOLO")),
                        autoStart = item.optBoolean("autoStart", false),
                        supportsDifficulty = item.optBoolean("supportsDifficulty", false),
                        layoutType = LayoutType.valueOf(item.optString("layoutType", "DOM")),
                        canvasWidth = item.optInt("canvasWidth", 0),
                        canvasHeight = item.optInt("canvasHeight", 0),
                        orientation = item.optString("orientation", "RESPONSIVE"),
                        maxWidthVw = item.optInt("maxWidthVw", 94),
                        maxHeightVh = item.optInt("maxHeightVh", 90),
                        cursorStep = item.optInt("cursorStep", 48),
                        sourceUrl = item.getString("sourceUrl"),
                        upstreamCommit = item.getString("upstreamCommit"),
                        license = item.getString("license"),
                        locale = item.optString("locale", "pt-BR"),
                        niaCertified = item.optBoolean("niaCertified", false)
                    )
                )
            }
        }.sortedBy { it.id }
    }
}
