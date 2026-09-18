package com.nia.arcade.data

import android.content.Context
import com.nia.arcade.model.DirectionScheme
import com.nia.arcade.model.Game
import com.nia.arcade.model.InputProfile
import com.nia.arcade.model.LayoutProfile
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
                        inputProfile = InputProfile.valueOf(item.getString("inputProfile")),
                        directionScheme = DirectionScheme.valueOf(item.getString("directionScheme")),
                        actionKey = item.optString("actionKey", "Enter"),
                        playMode = PlayMode.valueOf(item.optString("playMode", "SOLO")),
                        layoutProfile = LayoutProfile.valueOf(item.optString("layoutProfile", "DOM")),
                        nativeWidth = item.optInt("nativeWidth", 0),
                        nativeHeight = item.optInt("nativeHeight", 0),
                        fitMaxScale = item.optDouble("fitMaxScale", 1.0),
                        fitPadding = item.optInt("fitPadding", 32),
                        sourceUrl = item.getString("sourceUrl"),
                        upstreamCommit = item.getString("upstreamCommit"),
                        license = item.getString("license"),
                        niaCertified = item.optBoolean("niaCertified", false)
                    )
                )
            }
        }.sortedBy { it.id }
    }
}
