package com.ivangames.notebox

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun NoteBoxTheme(themeMode: Int = 0, content: @Composable () -> Unit) {
    val darkTheme = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = Color(0xFF3F51B5), onPrimary = Color.White, primaryContainer = Color(0xFF1A237E),
            secondary = Color(0xFF7986CB), background = Color(0xFF0F111A), onBackground = Color.White,
            surface = Color(0xFF1A1F2B), onSurface = Color.White, surfaceVariant = Color(0xFF2A3040),
            onSurfaceVariant = Color(0xFFB0BEC5), error = Color(0xFFEF5350), errorContainer = Color(0xFF4A1515)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF3F51B5), onPrimary = Color.White, primaryContainer = Color(0xFFC5CAE9),
            secondary = Color(0xFF5C6BC0), background = Color(0xFFF5F6FA), onBackground = Color(0xFF1A1F2B),
            surface = Color.White, onSurface = Color(0xFF1A1F2B), surfaceVariant = Color(0xFFE8EAF6),
            onSurfaceVariant = Color(0xFF3F51B5), error = Color(0xFFB00020), errorContainer = Color(0xFFFFDAD6)
        )
    }

    // ДОБАВЛЯЕМ МЯГКИЕ УГЛЫ (скругления)
    val shapes = Shapes(
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp)
    )

    MaterialTheme(colorScheme = colorScheme, shapes = shapes, content = content)
}
