package ir.pargartech.paymentrecorder.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ir.pargartech.paymentrecorder.R

// Brand: deep emerald/teal. Fixed on purpose (no Material You) so the app looks the same on every phone.
private val Light = lightColorScheme(
    primary = Color(0xFF0B7A66),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFE6),
    onPrimaryContainer = Color(0xFF00382D),
    secondary = Color(0xFF3F6259),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEBE6),
    onSecondaryContainer = Color(0xFF14302A),
    tertiary = Color(0xFFB7791F),
    tertiaryContainer = Color(0xFFFCEBCB),
    onTertiaryContainer = Color(0xFF4A2F00),
    background = Color(0xFFF4F7F6),
    onBackground = Color(0xFF151C1A),
    surface = Color(0xFFF4F7F6),
    onSurface = Color(0xFF151C1A),
    surfaceVariant = Color(0xFFE2EAE7),
    onSurfaceVariant = Color(0xFF55625E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFDFC),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEDF2F0),
    surfaceContainerHighest = Color(0xFFE5ECE9),
    outline = Color(0xFF8A9893),
    outlineVariant = Color(0xFFD5DEDA),
    error = Color(0xFFC62828),
    errorContainer = Color(0xFFFDE2E1),
    onErrorContainer = Color(0xFF5F0E0E),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF5FD6BB),
    onPrimary = Color(0xFF00382D),
    primaryContainer = Color(0xFF0C5144),
    onPrimaryContainer = Color(0xFFCDEFE6),
    secondary = Color(0xFFA9C9C0),
    onSecondary = Color(0xFF14302A),
    secondaryContainer = Color(0xFF253B35),
    onSecondaryContainer = Color(0xFFDCEBE6),
    tertiary = Color(0xFFF2C46B),
    tertiaryContainer = Color(0xFF4A3510),
    onTertiaryContainer = Color(0xFFFCEBCB),
    background = Color(0xFF0B1210),
    onBackground = Color(0xFFE1E8E5),
    surface = Color(0xFF0B1210),
    onSurface = Color(0xFFE1E8E5),
    surfaceVariant = Color(0xFF1F2B28),
    onSurfaceVariant = Color(0xFFA6B4AF),
    surfaceContainerLowest = Color(0xFF070C0B),
    surfaceContainerLow = Color(0xFF111A18),
    surfaceContainer = Color(0xFF14201D),
    surfaceContainerHigh = Color(0xFF1B2825),
    surfaceContainerHighest = Color(0xFF22302C),
    outline = Color(0xFF6F7E79),
    outlineVariant = Color(0xFF2B3935),
    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF5C1714),
    onErrorContainer = Color(0xFFFDE2E1),
)

/** Colours Material doesn't have a slot for: money direction and the hero gradient. */
@Immutable
data class AppColors(
    val deposit: Color,
    val withdrawal: Color,
    val depositContainer: Color,
    val withdrawalContainer: Color,
    val hero: Brush,
    val onHero: Color,
)

private val LightApp = AppColors(
    deposit = Color(0xFF128A4A),
    withdrawal = Color(0xFFD03A2F),
    depositContainer = Color(0xFFDDF4E6),
    withdrawalContainer = Color(0xFFFCE4E1),
    hero = Brush.linearGradient(listOf(Color(0xFF064E40), Color(0xFF0B7A66), Color(0xFF17A589))),
    onHero = Color.White,
)

private val DarkApp = AppColors(
    deposit = Color(0xFF5BD68E),
    withdrawal = Color(0xFFFF7D72),
    depositContainer = Color(0xFF123A25),
    withdrawalContainer = Color(0xFF45201C),
    hero = Brush.linearGradient(listOf(Color(0xFF03322A), Color(0xFF0A5A4B), Color(0xFF0F7E69))),
    onHero = Color.White,
)

private val LocalAppColors = staticCompositionLocalOf { LightApp }

object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current
}

private val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
    Font(R.font.vazirmatn_black, FontWeight.Black),
)

private fun Typography.withFont(family: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = family)
    return copy(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f().copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.f().copy(fontWeight = FontWeight.Bold),
        titleSmall = titleSmall.f().copy(fontWeight = FontWeight.Medium),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f().copy(fontWeight = FontWeight.Medium),
        labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}

private val AppTypography = Typography().withFont(Vazirmatn)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Material 3 theme with the brand palette, always right-to-left (the UI is Persian only). */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, shapes = AppShapes) {
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl,
            LocalAppColors provides if (dark) DarkApp else LightApp,
            content = content,
        )
    }
}
