package com.jayr91.vdr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF1B8F5A)
private val Ink = Color(0xFF0E1A14)

private val Light = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    background = Color(0xFFF4F7F5),
    surface = Color.White,
    onBackground = Ink,
    onSurface = Ink,
)

private val Dark = darkColorScheme(
    primary = Color(0xFF4CC38A),
    onPrimary = Ink,
    background = Color(0xFF0B1210),
    surface = Color(0xFF15201B),
    onBackground = Color(0xFFE6F2EB),
    onSurface = Color(0xFFE6F2EB),
)

@Composable
fun VdrTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
