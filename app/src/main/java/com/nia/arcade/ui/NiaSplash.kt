package com.nia.arcade.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val SplashVoid = Color(0xFF05070B)
private val SplashCyan = Color(0xFF38E8FF)
private val SplashViolet = Color(0xFF806BFF)
private val SplashSnow = Color(0xFFF7F9FC)

@Composable
fun NiaSplash(onFinished: () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    val density = LocalDensity.current.density

    val rotationY by animateFloatAsState(
        targetValue = if (entered) 0f else -72f,
        animationSpec = tween(950, easing = FastOutSlowInEasing),
        label = "nia-splash-rotation"
    )
    val rotationX by animateFloatAsState(
        targetValue = if (entered) 0f else 18f,
        animationSpec = tween(950, easing = FastOutSlowInEasing),
        label = "nia-splash-tilt"
    )
    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.62f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "nia-splash-scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(620),
        label = "nia-splash-alpha"
    )
    val subtitleAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(1150, delayMillis = 420),
        label = "nia-splash-subtitle"
    )

    LaunchedEffect(Unit) {
        entered = true
        delay(1850)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        SplashCyan.copy(alpha = 0.14f),
                        SplashViolet.copy(alpha = 0.06f),
                        SplashVoid
                    ),
                    radius = 980f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.graphicsLayer {
                    this.rotationY = rotationY
                    this.rotationX = rotationX
                    cameraDistance = 28f * density
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
            ) {
                repeat(10) { depth ->
                    BasicText(
                        text = "NIA",
                        modifier = Modifier.offset(
                            x = (depth * 1.6f).dp,
                            y = (depth * 1.1f).dp
                        ),
                        style = TextStyle(
                            color = Color(
                                red = 0x0B,
                                green = (0x3A + depth * 3).coerceAtMost(0x58),
                                blue = (0x52 + depth * 5).coerceAtMost(0x84)
                            ),
                            fontSize = 96.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 3.sp
                        )
                    )
                }

                BasicText(
                    text = "NIA",
                    style = TextStyle(
                        brush = Brush.linearGradient(
                            colors = listOf(SplashSnow, SplashCyan, SplashViolet)
                        ),
                        fontSize = 96.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp
                    )
                )
            }

            Spacer(Modifier.height(10.dp))

            BasicText(
                text = "A R C A D E",
                modifier = Modifier.graphicsLayer { this.alpha = subtitleAlpha },
                style = TextStyle(
                    color = SplashSnow,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 8.sp
                )
            )

            Spacer(Modifier.height(16.dp))

            BasicText(
                text = "100 JOGOS  •  UM CONTROLE",
                modifier = Modifier.graphicsLayer { this.alpha = subtitleAlpha * 0.86f },
                style = TextStyle(
                    color = SplashCyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            )
        }
    }
}
