package com.nia.arcade.model

enum class InputProfile {
    KEYBOARD,
    CURSOR
}

enum class DirectionScheme {
    ARROWS,
    WASD
}

data class Game(
    val id: Int,
    val slug: String,
    val title: String,
    val originalTitle: String,
    val icon: String,
    val category: String,
    val inputProfile: InputProfile,
    val directionScheme: DirectionScheme,
    val actionKey: String,
    val sourceUrl: String,
    val upstreamCommit: String,
    val license: String,
    val niaCertified: Boolean
)
