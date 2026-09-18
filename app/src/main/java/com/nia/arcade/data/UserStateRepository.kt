package com.nia.arcade.data

import android.content.Context

class UserStateRepository(context: Context) {
    private val prefs = context.getSharedPreferences("nia_state", Context.MODE_PRIVATE)

    fun favorites(): Set<Int> =
        prefs.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    fun setFavorite(id: Int, enabled: Boolean) {
        val updated = favorites().toMutableSet().apply {
            if (enabled) add(id) else remove(id)
        }
        prefs.edit().putStringSet(KEY_FAVORITES, updated.map(Int::toString).toSet()).apply()
    }

    fun recents(): List<Int> =
        prefs.getString(KEY_RECENTS, "").orEmpty()
            .split(',')
            .mapNotNull { it.toIntOrNull() }
            .distinct()
            .take(MAX_RECENTS)

    fun recordPlayed(id: Int) {
        val updated = listOf(id) + recents().filterNot { it == id }
        prefs.edit().putString(KEY_RECENTS, updated.take(MAX_RECENTS).joinToString(",")).apply()
    }

    companion object {
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_RECENTS = "recents"
        private const val MAX_RECENTS = 20
    }
}
