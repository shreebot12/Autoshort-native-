package com.jaywebtech.autoshorts.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF1F2822)
val Leaf = Color(0xFFB5E85A)
val Paper = Color(0xFFF3F6F0)
val MutedInk = Color(0xFF6D776F)
val Line = Color(0xFFDCE3D9)
val Coral = Color(0xFFDB705A)
val White = Color(0xFFFFFFFF)

@Composable
fun AutoShortsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Ink,
            onPrimary = White,
            secondary = Leaf,
            onSecondary = Ink,
            background = Paper,
            surface = White,
            onSurface = Ink,
            onSurfaceVariant = MutedInk,
            outline = Line,
            error = Coral,
        ),
        content = content,
    )
}