package ua.magerko.olxradar.ui.compose

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import ua.magerko.olxradar.service.MonitorService

/**
 * Доступные темы оформления.
 */
enum class ThemeMode(val title: String, val subtitle: String, val isDark: Boolean) {
    CYBER("Кибернетика", "Тёмная фирменная тема", true),
    GLASS("Liquid Glass", "Стекло с преломлением (Android 13+)", true),
    EMERALD("Изумруд", "Тёмная зелёная тема", true),
    AMETHYST("Аметист", "Тёмная фиолетовая тема", true),
    WHITE("Белая", "Светлая чистая тема", false),
    GRAY("Серая", "Мягкая нейтральная тема", false);

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.name == id } ?: CYBER
    }
}

/**
 * Палитра приложения. Все цвета — state-переменные: при смене темы
 * каждый экран, читающий их, автоматически перерисовывается.
 */
var CyberneticSurface by mutableStateOf(Color(0xFF0B1326))
var CyberneticSurfaceContainer by mutableStateOf(Color(0xFF171F33))
var CyberneticSurfaceContainerLow by mutableStateOf(Color(0xFF131B2E))
var CyberneticSurfaceContainerLowest by mutableStateOf(Color(0xFF060E20))
var CyberneticSurfaceContainerHigh by mutableStateOf(Color(0xFF222A3D))
var CyberneticSurfaceContainerHighest by mutableStateOf(Color(0xFF2D3449))
var CyberneticSurfaceBright by mutableStateOf(Color(0xFF31394D))

var CyberneticPrimary by mutableStateOf(Color(0xFF4CD7F6))
var CyberneticOnPrimary by mutableStateOf(Color(0xFF003640))
var CyberneticPrimaryContainer by mutableStateOf(Color(0xFF06B6D4))
var CyberneticOnPrimaryContainer by mutableStateOf(Color(0xFF00424F))

var CyberneticSecondary by mutableStateOf(Color(0xFF4EDEA3))
var CyberneticOnSecondary by mutableStateOf(Color(0xFF003824))
var CyberneticSecondaryContainer by mutableStateOf(Color(0xFF00A572))
var CyberneticOnSecondaryContainer by mutableStateOf(Color(0xFF00311F))

var CyberneticTertiary by mutableStateOf(Color(0xFFFFB95F))
var CyberneticOnTertiary by mutableStateOf(Color(0xFF472A00))
var CyberneticTertiaryContainer by mutableStateOf(Color(0xFFE79400))
var CyberneticOnTertiaryContainer by mutableStateOf(Color(0xFF563400))

var CyberneticError by mutableStateOf(Color(0xFFFFB4AB))
var CyberneticOnError by mutableStateOf(Color(0xFF690005))
var CyberneticErrorContainer by mutableStateOf(Color(0xFF93000A))
var CyberneticOnErrorContainer by mutableStateOf(Color(0xFFFFDAD6))

var CyberneticOnSurface by mutableStateOf(Color(0xFFDAE2FD))
var CyberneticOnSurfaceVariant by mutableStateOf(Color(0xFFBCC9CD))
var CyberneticOutline by mutableStateOf(Color(0xFF869397))
var CyberneticOutlineVariant by mutableStateOf(Color(0xFF3D494C))

/** Фон под стеклом: в Liquid Glass теме — мягкий многослойный градиент. */
var CyberneticBackgroundGradientTop by mutableStateOf(Color(0xFF0B1326))
var CyberneticBackgroundGradientBottom by mutableStateOf(Color(0xFF101B33))

/** Активна ли тема Liquid Glass (стекло рисуется только на API 31+). */
var isGlassTheme by mutableStateOf(false)

/** Доступен ли настоящий шейдер преломления (Liquid Glass lens, API 33+). */
val isLiquidGlassSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** Возможен ли хотя бы blur-стекло (API 31+). */
val isGlassFallbackSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private fun applyCyber() {
    isGlassTheme = false
    CyberneticSurface = Color(0xFF0B1326)
    CyberneticSurfaceContainer = Color(0xFF171F33)
    CyberneticSurfaceContainerLow = Color(0xFF131B2E)
    CyberneticSurfaceContainerLowest = Color(0xFF060E20)
    CyberneticSurfaceContainerHigh = Color(0xFF222A3D)
    CyberneticSurfaceContainerHighest = Color(0xFF2D3449)
    CyberneticSurfaceBright = Color(0xFF31394D)

    CyberneticPrimary = Color(0xFF4CD7F6)
    CyberneticOnPrimary = Color(0xFF003640)
    CyberneticPrimaryContainer = Color(0xFF06B6D4)
    CyberneticOnPrimaryContainer = Color(0xFF00424F)

    CyberneticSecondary = Color(0xFF4EDEA3)
    CyberneticOnSecondary = Color(0xFF003824)
    CyberneticSecondaryContainer = Color(0xFF00A572)
    CyberneticOnSecondaryContainer = Color(0xFF00311F)

    CyberneticTertiary = Color(0xFFFFB95F)
    CyberneticOnTertiary = Color(0xFF472A00)
    CyberneticTertiaryContainer = Color(0xFFE79400)
    CyberneticOnTertiaryContainer = Color(0xFF563400)

    CyberneticError = Color(0xFFFFB4AB)
    CyberneticOnError = Color(0xFF690005)
    CyberneticErrorContainer = Color(0xFF93000A)
    CyberneticOnErrorContainer = Color(0xFFFFDAD6)

    CyberneticOnSurface = Color(0xFFDAE2FD)
    CyberneticOnSurfaceVariant = Color(0xFFBCC9CD)
    CyberneticOutline = Color(0xFF869397)
    CyberneticOutlineVariant = Color(0xFF3D494C)

    CyberneticBackgroundGradientTop = Color(0xFF0B1326)
    CyberneticBackgroundGradientBottom = Color(0xFF101B33)
}

private fun applyGlass() {
    isGlassTheme = true
    // Полупрозрачные "стеклянные" контейнеры поверх живого градиента.
    CyberneticSurface = Color(0xFF0E1220)
    CyberneticSurfaceContainer = Color(0xB3222838)
    CyberneticSurfaceContainerLow = Color(0x99191F2E)
    CyberneticSurfaceContainerLowest = Color(0x66121620)
    CyberneticSurfaceContainerHigh = Color(0xCC2B3346)
    CyberneticSurfaceContainerHighest = Color(0xE6384158)
    CyberneticSurfaceBright = Color(0xFF46516B)

    CyberneticPrimary = Color(0xFF6FE3FF)
    CyberneticOnPrimary = Color(0xFF003443)
    CyberneticPrimaryContainer = Color(0xFF22C3E6)
    CyberneticOnPrimaryContainer = Color(0xFF004250)

    CyberneticSecondary = Color(0xFF67E8AC)
    CyberneticOnSecondary = Color(0xFF003824)
    CyberneticSecondaryContainer = Color(0xFF00A572)
    CyberneticOnSecondaryContainer = Color(0xFF00311F)

    CyberneticTertiary = Color(0xFFFFC278)
    CyberneticOnTertiary = Color(0xFF472A00)
    CyberneticTertiaryContainer = Color(0xFFE79400)
    CyberneticOnTertiaryContainer = Color(0xFF563400)

    CyberneticError = Color(0xFFFFB4AB)
    CyberneticOnError = Color(0xFF690005)
    CyberneticErrorContainer = Color(0xFF93000A)
    CyberneticOnErrorContainer = Color(0xFFFFDAD6)

    CyberneticOnSurface = Color(0xFFE6ECFF)
    CyberneticOnSurfaceVariant = Color(0xFFC4CFD6)
    CyberneticOutline = Color(0xFF9AA6AC)
    CyberneticOutlineVariant = Color(0xFF49545E)

    CyberneticBackgroundGradientTop = Color(0xFF0A0F1E)
    CyberneticBackgroundGradientBottom = Color(0xFF1B2B4D)
}

private fun applyWhite() {
    isGlassTheme = false
    CyberneticSurface = Color(0xFFFFFFFF)
    CyberneticSurfaceContainer = Color(0xFFF5F7FA)
    CyberneticSurfaceContainerLow = Color(0xFFFAFBFD)
    CyberneticSurfaceContainerLowest = Color(0xFFFFFFFF)
    CyberneticSurfaceContainerHigh = Color(0xFFEFF2F6)
    CyberneticSurfaceContainerHighest = Color(0xFFE4E9F0)
    CyberneticSurfaceBright = Color(0xFFEFF2F6)

    CyberneticPrimary = Color(0xFF0A84D6)
    CyberneticOnPrimary = Color(0xFFFFFFFF)
    CyberneticPrimaryContainer = Color(0xFF3FB2F5)
    CyberneticOnPrimaryContainer = Color(0xFF00344F)

    CyberneticSecondary = Color(0xFF0FA968)
    CyberneticOnSecondary = Color(0xFFFFFFFF)
    CyberneticSecondaryContainer = Color(0xFF35D28F)
    CyberneticOnSecondaryContainer = Color(0xFF00331F)

    CyberneticTertiary = Color(0xFFF29B1B)
    CyberneticOnTertiary = Color(0xFFFFFFFF)
    CyberneticTertiaryContainer = Color(0xFFFFB95F)
    CyberneticOnTertiaryContainer = Color(0xFF4A2E00)

    CyberneticError = Color(0xFFC62828)
    CyberneticOnError = Color(0xFFFFFFFF)
    CyberneticErrorContainer = Color(0xFFFFDAD6)
    CyberneticOnErrorContainer = Color(0xFF410002)

    CyberneticOnSurface = Color(0xFF161B22)
    CyberneticOnSurfaceVariant = Color(0xFF48505C)
    CyberneticOutline = Color(0xFF79818D)
    CyberneticOutlineVariant = Color(0xFFD8DEE6)

    CyberneticBackgroundGradientTop = Color(0xFFFFFFFF)
    CyberneticBackgroundGradientBottom = Color(0xFFF2F5F9)
}

private fun applyGray() {
    isGlassTheme = false
    CyberneticSurface = Color(0xFFECECEE)
    CyberneticSurfaceContainer = Color(0xFFE0E1E5)
    CyberneticSurfaceContainerLow = Color(0xFFF1F1F4)
    CyberneticSurfaceContainerLowest = Color(0xFFFFFFFF)
    CyberneticSurfaceContainerHigh = Color(0xFFD6D7DC)
    CyberneticSurfaceContainerHighest = Color(0xFFC9CACF)
    CyberneticSurfaceBright = Color(0xFFF4F4F6)

    CyberneticPrimary = Color(0xFF4C566A)
    CyberneticOnPrimary = Color(0xFFFFFFFF)
    CyberneticPrimaryContainer = Color(0xFF7B87A3)
    CyberneticOnPrimaryContainer = Color(0xFF1E2430)

    CyberneticSecondary = Color(0xFF5A8774)
    CyberneticOnSecondary = Color(0xFFFFFFFF)
    CyberneticSecondaryContainer = Color(0xFF83B39E)
    CyberneticOnSecondaryContainer = Color(0xFF1E2A24)

    CyberneticTertiary = Color(0xFFA9814B)
    CyberneticOnTertiary = Color(0xFFFFFFFF)
    CyberneticTertiaryContainer = Color(0xFFCBA878)
    CyberneticOnTertiaryContainer = Color(0xFF2E2312)

    CyberneticError = Color(0xFFB3403A)
    CyberneticOnError = Color(0xFFFFFFFF)
    CyberneticErrorContainer = Color(0xFFE8B9B4)
    CyberneticOnErrorContainer = Color(0xFF3A0A07)

    CyberneticOnSurface = Color(0xFF1D1F23)
    CyberneticOnSurfaceVariant = Color(0xFF4A4D55)
    CyberneticOutline = Color(0xFF7B7E86)
    CyberneticOutlineVariant = Color(0xFFC6C8CD)

    CyberneticBackgroundGradientTop = Color(0xFFECECEE)
    CyberneticBackgroundGradientBottom = Color(0xFFE2E3E7)
}

private fun applyEmerald() {
    isGlassTheme = false
    CyberneticSurface = Color(0xFF0A1512)
    CyberneticSurfaceContainer = Color(0xFF16241E)
    CyberneticSurfaceContainerLow = Color(0xFF122019)
    CyberneticSurfaceContainerLowest = Color(0xFF050D0A)
    CyberneticSurfaceContainerHigh = Color(0xFF1E3027)
    CyberneticSurfaceContainerHighest = Color(0xFF273A30)
    CyberneticSurfaceBright = Color(0xFF2B4034)

    CyberneticPrimary = Color(0xFF5BE49B)
    CyberneticOnPrimary = Color(0xFF00391E)
    CyberneticPrimaryContainer = Color(0xFF00B86B)
    CyberneticOnPrimaryContainer = Color(0xFF00291A)

    CyberneticSecondary = Color(0xFF7DD3FC)
    CyberneticOnSecondary = Color(0xFF003251)
    CyberneticSecondaryContainer = Color(0xFF0090D4)
    CyberneticOnSecondaryContainer = Color(0xFF00253C)

    CyberneticTertiary = Color(0xFFFFC46B)
    CyberneticOnTertiary = Color(0xFF472A00)
    CyberneticTertiaryContainer = Color(0xFFE79400)
    CyberneticOnTertiaryContainer = Color(0xFF563400)

    CyberneticError = Color(0xFFFFB4AB)
    CyberneticOnError = Color(0xFF690005)
    CyberneticErrorContainer = Color(0xFF93000A)
    CyberneticOnErrorContainer = Color(0xFFFFDAD6)

    CyberneticOnSurface = Color(0xFFDCEFE3)
    CyberneticOnSurfaceVariant = Color(0xFFB7C9BF)
    CyberneticOutline = Color(0xFF82958B)
    CyberneticOutlineVariant = Color(0xFF384A41)

    CyberneticBackgroundGradientTop = Color(0xFF08130F)
    CyberneticBackgroundGradientBottom = Color(0xFF143324)
}

private fun applyAmethyst() {
    isGlassTheme = false
    CyberneticSurface = Color(0xFF120E1F)
    CyberneticSurfaceContainer = Color(0xFF1E1830)
    CyberneticSurfaceContainerLow = Color(0xFF191428)
    CyberneticSurfaceContainerLowest = Color(0xFF0A0714)
    CyberneticSurfaceContainerHigh = Color(0xFF282040)
    CyberneticSurfaceContainerHighest = Color(0xFF322A50)
    CyberneticSurfaceBright = Color(0xFF362E55)

    CyberneticPrimary = Color(0xFFC4A5FF)
    CyberneticOnPrimary = Color(0xFF2E1065)
    CyberneticPrimaryContainer = Color(0xFF8B5CF6)
    CyberneticOnPrimaryContainer = Color(0xFF230A4E)

    CyberneticSecondary = Color(0xFFF9A8D4)
    CyberneticOnSecondary = Color(0xFF3F0D2B)
    CyberneticSecondaryContainer = Color(0xFFDB2777)
    CyberneticOnSecondaryContainer = Color(0xFF330A24)

    CyberneticTertiary = Color(0xFF67E8F9)
    CyberneticOnTertiary = Color(0xFF003444)
    CyberneticTertiaryContainer = Color(0xFF0891B2)
    CyberneticOnTertiaryContainer = Color(0xFF002733)

    CyberneticError = Color(0xFFFFB4AB)
    CyberneticOnError = Color(0xFF690005)
    CyberneticErrorContainer = Color(0xFF93000A)
    CyberneticOnErrorContainer = Color(0xFFFFDAD6)

    CyberneticOnSurface = Color(0xFFE9E4FA)
    CyberneticOnSurfaceVariant = Color(0xFFC6BDDD)
    CyberneticOutline = Color(0xFF948BA8)
    CyberneticOutlineVariant = Color(0xFF433D58)

    CyberneticBackgroundGradientTop = Color(0xFF0E0A1A)
    CyberneticBackgroundGradientBottom = Color(0xFF261A46)
}

fun applyThemeMode(mode: ThemeMode) {
    when (mode) {
        ThemeMode.CYBER -> applyCyber()
        ThemeMode.GLASS -> applyGlass()
        ThemeMode.EMERALD -> applyEmerald()
        ThemeMode.AMETHYST -> applyAmethyst()
        ThemeMode.WHITE -> applyWhite()
        ThemeMode.GRAY -> applyGray()
    }
}

object ThemePreferences {
    private const val KEY = "app_theme_mode"

    fun get(context: Context): ThemeMode {
        val prefs: SharedPreferences =
            context.applicationContext.getSharedPreferences(MonitorService.PREFS_NAME, Context.MODE_PRIVATE)
        return ThemeMode.fromId(prefs.getString(KEY, null))
    }

    fun set(context: Context, mode: ThemeMode) {
        val prefs: SharedPreferences =
            context.applicationContext.getSharedPreferences(MonitorService.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY, mode.name).apply()
        applyThemeMode(mode)
    }


}

private val CyberneticDarkColorScheme = darkColorScheme(
    primary = CyberneticPrimary,
    onPrimary = CyberneticOnPrimary,
    primaryContainer = CyberneticPrimaryContainer,
    onPrimaryContainer = CyberneticOnPrimaryContainer,
    secondary = CyberneticSecondary,
    onSecondary = CyberneticOnSecondary,
    secondaryContainer = CyberneticSecondaryContainer,
    onSecondaryContainer = CyberneticOnSecondaryContainer,
    tertiary = CyberneticTertiary,
    onTertiary = CyberneticOnTertiary,
    tertiaryContainer = CyberneticTertiaryContainer,
    onTertiaryContainer = CyberneticOnTertiaryContainer,
    error = CyberneticError,
    onError = CyberneticOnError,
    errorContainer = CyberneticErrorContainer,
    onErrorContainer = CyberneticOnErrorContainer,
    background = CyberneticSurface,
    onBackground = CyberneticOnSurface,
    surface = CyberneticSurface,
    onSurface = CyberneticOnSurface,
    surfaceVariant = CyberneticSurfaceContainerHighest,
    onSurfaceVariant = CyberneticOnSurfaceVariant,
    outline = CyberneticOutline,
    outlineVariant = CyberneticOutlineVariant
)

private val LightColorScheme = lightColorScheme(
    primary = CyberneticPrimary,
    onPrimary = CyberneticOnPrimary,
    primaryContainer = CyberneticPrimaryContainer,
    onPrimaryContainer = CyberneticOnPrimaryContainer,
    secondary = CyberneticSecondary,
    onSecondary = CyberneticOnSecondary,
    tertiary = CyberneticTertiary,
    onTertiary = CyberneticOnTertiary,
    error = CyberneticError,
    onError = CyberneticOnError,
    background = CyberneticSurface,
    onBackground = CyberneticOnSurface,
    surface = CyberneticSurface,
    onSurface = CyberneticOnSurface,
    surfaceVariant = CyberneticSurfaceContainerHighest,
    onSurfaceVariant = CyberneticOnSurfaceVariant,
    outline = CyberneticOutline,
    outlineVariant = CyberneticOutlineVariant
)

@Composable
fun CyberneticRadarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (isGlassTheme || darkTheme) CyberneticDarkColorScheme else LightColorScheme,
        content = content
    )
}
