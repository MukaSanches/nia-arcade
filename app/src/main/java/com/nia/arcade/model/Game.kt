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

enum class LayoutType {
    CANVAS,
    DOM
}

enum class Difficulty {
    RELAXADO,
    NORMAL,
    DIFICIL;

    fun labelPt(): String = when (this) {
        RELAXADO -> "Relaxado"
        NORMAL -> "Normal"
        DIFICIL -> "Difícil"
    }

    fun next(): Difficulty = when (this) {
        RELAXADO -> NORMAL
        NORMAL -> DIFICIL
        DIFICIL -> RELAXADO
    }
}

data class Game(
    val id: Int,
    val slug: String,
    val title: String,
    val originalTitle: String,
    val icon: String,
    val category: String,
    val categoryPt: String,
    val inputProfile: InputProfile,
    val directionScheme: DirectionScheme,
    val actionKey: String,
    val controlsPt: String,
    val playMode: PlayMode,
    val autoStart: Boolean,
    val supportsDifficulty: Boolean,
    val layoutType: LayoutType,
    val canvasWidth: Int,
    val canvasHeight: Int,
    val orientation: String,
    val maxWidthVw: Int,
    val maxHeightVh: Int,
    val cursorStep: Int,
    val sourceUrl: String,
    val upstreamCommit: String,
    val license: String,
    val locale: String,
    val niaCertified: Boolean
)
