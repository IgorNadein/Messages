package com.afkanerd.deku.messages.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val OneUiBlueLight = Color(0xFF006FFD)
private val OneUiBlueDark = Color(0xFF4DA3FF)

private val LightColors = lightColorScheme(
    primary = OneUiBlueLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEBFF),
    onPrimaryContainer = Color(0xFF002E69),
    background = Color(0xFFF7F7F7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFF0F0F2),
    onSurfaceVariant = Color(0xFF5F6067),
    outline = Color(0xFF8B8C92),
    outlineVariant = Color(0xFFE2E2E6),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = OneUiBlueDark,
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF004A9F),
    onPrimaryContainer = Color(0xFFD9E9FF),
    background = Color.Black,
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF151515),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF232326),
    onSurfaceVariant = Color(0xFFB9BAC1),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF34353A),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val MessagesTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 38.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 30.sp,
        lineHeight = 37.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 25.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

@Immutable
data class MessagesSpacing(
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val viewingZone: Dp = 48.dp,
)

@Immutable
data class MessagesDimensions(
    val minimumTouchTarget: Dp = 48.dp,
    val groupRadius: Dp = 26.dp,
    val sheetRadius: Dp = 30.dp,
    val composerRadius: Dp = 24.dp,
    val bubbleRadius: Dp = 19.dp,
    val bubbleTailRadius: Dp = 6.dp,
    val compactTopBarHeight: Dp = 64.dp,
    val inboxOuterMargin: Dp = 10.dp,
    val inboxSurfaceRadius: Dp = 30.dp,
    val inboxControlHeight: Dp = 64.dp,
    val inboxViewingZoneHeight: Dp = 222.dp,
    val inboxRowMinimumHeight: Dp = 84.dp,
    val inboxAvatarSize: Dp = 36.dp,
    val inboxFabSize: Dp = 56.dp,
    val inboxBottomNavigationHeight: Dp = 56.dp,
)

@Immutable
data class MessagesSemanticColors(
    val inboxListSurface: Color,
    val inboxControlSurface: Color,
    val inboxSelectedControlSurface: Color,
    val onInboxSelectedControl: Color,
    val inboxFloatingActionSurface: Color,
    val onInboxFloatingAction: Color,
    val unreadBadge: Color,
    val onUnreadBadge: Color,
    val inboxSelectionSurface: Color,
    val inboxSelectionAccent: Color,
    val onInboxSelectionAccent: Color,
)

private val LightSemanticColors = MessagesSemanticColors(
    inboxListSurface = Color.White,
    inboxControlSurface = Color(0xFFECECEE),
    inboxSelectedControlSurface = Color(0xFFDDE9F8),
    onInboxSelectedControl = Color(0xFF174D86),
    inboxFloatingActionSurface = Color(0xFFE4EEF9),
    onInboxFloatingAction = Color(0xFF1B5E9F),
    unreadBadge = Color(0xFFE85D26),
    onUnreadBadge = Color.White,
    inboxSelectionSurface = Color(0xFFDDE9F8),
    inboxSelectionAccent = Color(0xFF1B5E9F),
    onInboxSelectionAccent = Color.White,
)

private val DarkSemanticColors = MessagesSemanticColors(
    inboxListSurface = Color(0xFF1B1B1D),
    inboxControlSurface = Color(0xFF252527),
    inboxSelectedControlSurface = Color(0xFF303C4A),
    onInboxSelectedControl = Color(0xFFD8E9FF),
    inboxFloatingActionSurface = Color(0xFF1F2C3B),
    onInboxFloatingAction = Color(0xFFA9D1FF),
    unreadBadge = Color(0xFFF0642C),
    onUnreadBadge = Color.White,
    inboxSelectionSurface = Color(0xFF303C4A),
    inboxSelectionAccent = Color(0xFFA9D1FF),
    onInboxSelectionAccent = Color(0xFF17283A),
)

private val LocalMessagesSpacing = staticCompositionLocalOf { MessagesSpacing() }
private val LocalMessagesDimensions = staticCompositionLocalOf { MessagesDimensions() }
private val LocalMessagesSemanticColors = staticCompositionLocalOf { LightSemanticColors }

object MessagesTheme {
    val spacing: MessagesSpacing
        @Composable @ReadOnlyComposable get() = LocalMessagesSpacing.current
    val dimensions: MessagesDimensions
        @Composable @ReadOnlyComposable get() = LocalMessagesDimensions.current
    val semanticColors: MessagesSemanticColors
        @Composable @ReadOnlyComposable get() = LocalMessagesSemanticColors.current
    val colors: ColorScheme
        @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme
}

@Composable
fun MessagesAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicAccent: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        dynamicAccent && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            // Dynamic colour is deliberately opt-in. Semantic security/error colours remain
            // component-owned and are never derived from the wallpaper.
            val dynamic = if(darkTheme) dynamicDarkColorScheme(context)
                else dynamicLightColorScheme(context)
            (if(darkTheme) DarkColors else LightColors).copy(
                primary = dynamic.primary,
                onPrimary = dynamic.onPrimary,
                primaryContainer = dynamic.primaryContainer,
                onPrimaryContainer = dynamic.onPrimaryContainer,
            )
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if(!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalMessagesSpacing provides MessagesSpacing(),
        LocalMessagesDimensions provides MessagesDimensions(),
        LocalMessagesSemanticColors provides if(darkTheme) DarkSemanticColors
            else LightSemanticColors,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = MessagesTypography,
            shapes = Shapes(),
            content = content,
        )
    }
}
