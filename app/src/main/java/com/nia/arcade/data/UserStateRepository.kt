package com.nia.arcade.data

import android.content.Context
import com.nia.arcade.model.Difficulty

data class NiaStats(
    val totalLaunches: Int,
    val uniqueGames: Int,
    val totalPlayMs: Long,
    val runtimeFailures: Int
)

class UserStateRepository(context: Context) {
    private val prefs = context.getSharedPreferences("nia_state", Context.MODE_PRIVATE)

    fun activeProfile(): String = prefs.getString(KEY_PROFILE, PROFILE_PLAYER).orEmpty().ifBlank { PROFILE_PLAYER }

    fun toggleProfile(): String {
        val next = if (activeProfile() == PROFILE_PLAYER) PROFILE_GUEST else PROFILE_PLAYER
        prefs.edit().putString(KEY_PROFILE, next).apply()
        return next
    }

    private fun scoped(key: String): String = activeProfile() + "_" + key

    fun favorites(): Set<Int> =
        prefs.getStringSet(scoped(KEY_FAVORITES), emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    fun setFavorite(id: Int, enabled: Boolean) {
        val updated = favorites().toMutableSet().apply {
            if (enabled) add(id) else remove(id)
        }
        prefs.edit().putStringSet(scoped(KEY_FAVORITES), updated.map(Int::toString).toSet()).apply()
    }

    fun recents(): List<Int> =
        prefs.getString(scoped(KEY_RECENTS), "").orEmpty()
            .split(',')
            .mapNotNull { it.toIntOrNull() }
            .distinct()
            .take(MAX_RECENTS)

    fun recordPlayed(id: Int) {
        val updated = listOf(id) + recents().filterNot { it == id }
        val played = playedSet().toMutableSet().apply { add(id) }
        val launches = prefs.getInt(scoped(KEY_TOTAL_LAUNCHES), 0) + 1
        prefs.edit()
            .putString(scoped(KEY_RECENTS), updated.take(MAX_RECENTS).joinToString(","))
            .putStringSet(scoped(KEY_PLAYED_SET), played.map(Int::toString).toSet())
            .putInt(scoped(KEY_TOTAL_LAUNCHES), launches)
            .putInt(scoped("game_launches_$id"), prefs.getInt(scoped("game_launches_$id"), 0) + 1)
            .apply()
    }

    fun recordSession(id: Int, durationMs: Long) {
        if (durationMs <= 0) return
        val safe = durationMs.coerceAtMost(12L * 60L * 60L * 1000L)
        prefs.edit()
            .putLong(scoped(KEY_TOTAL_PLAY_MS), prefs.getLong(scoped(KEY_TOTAL_PLAY_MS), 0L) + safe)
            .putLong(scoped("game_time_$id"), prefs.getLong(scoped("game_time_$id"), 0L) + safe)
            .apply()
    }

    fun recordFailure(id: Int) {
        prefs.edit()
            .putInt(scoped(KEY_FAILURES), prefs.getInt(scoped(KEY_FAILURES), 0) + 1)
            .putInt(scoped("game_failures_$id"), prefs.getInt(scoped("game_failures_$id"), 0) + 1)
            .apply()
    }

    fun failureCount(id: Int): Int = prefs.getInt(scoped("game_failures_$id"), 0)

    fun difficulty(id: Int): Difficulty =
        runCatching { Difficulty.valueOf(prefs.getString(scoped("difficulty_$id"), Difficulty.NORMAL.name).orEmpty()) }
            .getOrDefault(Difficulty.NORMAL)

    fun setDifficulty(id: Int, difficulty: Difficulty) {
        prefs.edit().putString(scoped("difficulty_$id"), difficulty.name).apply()
    }

    fun controlsSeen(id: Int): Boolean = prefs.getBoolean(scoped("controls_seen_$id"), false)

    fun markControlsSeen(id: Int) {
        prefs.edit().putBoolean(scoped("controls_seen_$id"), true).apply()
    }

    fun stats(): NiaStats = NiaStats(
        totalLaunches = prefs.getInt(scoped(KEY_TOTAL_LAUNCHES), 0),
        uniqueGames = playedSet().size,
        totalPlayMs = prefs.getLong(scoped(KEY_TOTAL_PLAY_MS), 0L),
        runtimeFailures = prefs.getInt(scoped(KEY_FAILURES), 0)
    )

    fun achievements(): List<Pair<String, Boolean>> {
        val s = stats()
        return listOf(
            "Primeiro crédito" to (s.totalLaunches >= 1),
            "Explorador" to (s.uniqueGames >= 10),
            "Colecionador" to (s.uniqueGames >= 25),
            "Arcade veterano" to (s.totalLaunches >= 50),
            "Maratona" to (s.totalPlayMs >= 60L * 60L * 1000L)
        )
    }

    private fun playedSet(): Set<Int> =
        prefs.getStringSet(scoped(KEY_PLAYED_SET), emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    companion object {
        private const val KEY_PROFILE = "active_profile"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_RECENTS = "recents"
        private const val KEY_PLAYED_SET = "played_set"
        private const val KEY_TOTAL_LAUNCHES = "total_launches"
        private const val KEY_TOTAL_PLAY_MS = "total_play_ms"
        private const val KEY_FAILURES = "runtime_failures"
        private const val MAX_RECENTS = 30

        const val PROFILE_PLAYER = "Jogador"
        const val PROFILE_GUEST = "Convidado"
    }
}
