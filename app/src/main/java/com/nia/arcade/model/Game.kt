package com.nia.arcade.model

enum class InputProfile {
    KEYBOARD,
    CURSOR
}

enum class DirectionScheme {
    ARROWS,
    WASD
}

enum class PlayMode {
    SOLO,
    VS_CPU
}

enum class LayoutProfile {
    CANVAS,
    BOARD,
    CARD,
    DOM
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
    val playMode: PlayMode,
    val layoutProfile: LayoutProfile,
    val nativeWidth: Int,
    val nativeHeight: Int,
    val fitMaxScale: Double,
    val fitPadding: Int,
    val sourceUrl: String,
    val upstreamCommit: String,
    val license: String,
    val niaCertified: Boolean
)
