package ua.magerko.olxradar.ui.compose

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import ua.magerko.olxradar.service.MonitorService

/**
 * Доступные темы оформления приложения.
 */
enum class ThemeMode(
    val title: String,
    val subtitle: String,
    val isDark: Boolean,
    val primaryPreview: Color,
    val surfacePreview: Color
) {
    CYBER("Кибернетика", "Неоновый тёмный контраст", true, Color(0xFF00E5FF), Color(0xFF0A0F1D)),
    GLASS("Liquid Glass", "Полупрозрачное стекло и градиент", true, Color(0xFF38BDF8), Color(0xFF0F172A)),
    EMERALD("Изумруд", "Глубокая ночная зелень", true, Color(0xFF10B981), Color(0xFF091410)),
    AMETHYST("Аметист", "Космический фиолетовый", true, Color(0xFFA855F7), Color(0xFF100C1E)),
    WHITE("Светлая", "Чистая контрастная классика", false, Color(0xFF2563EB), Color(0xFFFFFFFF)),
    GRAY("Графит", "Скандинавский минимализм", false, Color(0xFF4B5563), Color(0xFFF3F4F6));

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.name == id } ?: CYBER
    }
}

/** Текущий активный режим темы (Compose State) */
var currentThemeMode by mutableStateOf(ThemeMode.CYBER)

/**
 * Палитра приложения (реактивные цвета для Compose компонентов).
 */
var CyberneticSurface by mutableStateOf(Color(0xFF0A0F1D))
var CyberneticSurfaceContainer by mutableStateOf(Color(0xFF141C30))
var CyberneticSurfaceContainerLow by mutableStateOf(Color(0xFF0E1526))
var CyberneticSurfaceContainerLowest by mutableStateOf(Color(0xFF070B16))
var CyberneticSurfaceContainerHigh by mutableStateOf(Color(0xFF1C2742))
var CyberneticSurfaceContainerHighest by mutableStateOf(Color(0xFF263456))
var CyberneticSurfaceBright by mutableStateOf(Color(0xFF2E3E66))

var CyberneticPrimary by mutableStateOf(Color(0xFF00E5FF))
var CyberneticOnPrimary by mutableStateOf(Color(0xFF00363D))
var CyberneticPrimaryContainer by mutableStateOf(Color(0xFF005662))
var CyberneticOnPrimaryContainer by mutableStateOf(Color(0xFF8CF4FF))

var CyberneticSecondary by mutableStateOf(Color(0xFF00E676))
var CyberneticOnSecondary by mutableStateOf(Color(0xFF003919))
var CyberneticSecondaryContainer by mutableStateOf(Color(0xFF005929))
var CyberneticOnSecondaryContainer by mutableStateOf(Color(0xFF70FF9E))

var CyberneticTertiary by mutableStateOf(Color(0xFFFFB300))
var CyberneticOnTertiary by mutableStateOf(Color(0xFF452B00))
var CyberneticTertiaryContainer by mutableStateOf(Color(0xFF6B4300))
var CyberneticOnTertiaryContainer by mutableStateOf(Color(0xFFFFDF9E))

var CyberneticError by mutableStateOf(Color(0xFFFF5252))
var CyberneticOnError by mutableStateOf(Color(0xFF490005))
var CyberneticErrorContainer by mutableStateOf(Color(0xFF93000A))
var CyberneticOnErrorContainer by mutableStateOf(Color(0xFFFFDAD6))

var CyberneticOnSurface by mutableStateOf(Color(0xFFF1F5F9))
var CyberneticOnSurfaceVariant by mutableStateOf(Color(0xFF94A3B8))
var CyberneticOutline by mutableStateOf(Color(0xFF64748B))
var CyberneticOutlineVariant by mutableStateOf(Color(0xFF334155))

/** Градиент фона */
var CyberneticBackgroundGradientTop by mutableStateOf(Color(0xFF0A0F1D))
var CyberneticBackgroundGradientBottom by mutableStateOf(Color(0xFF111827))

/** Флаг темы Liquid Glass */
var isGlassTheme by mutableStateOf(false)

val isLiquidGlassSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

val isGlassFallbackSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private fun applyCyber() {
    isGlassTheme = false
    currentThemeMode = ThemeMode.CYBER

    CyberneticSurface = Color(0xFF0A0F1D)
    CyberneticSurfaceContainer = Color(0xFF141C30)
    CyberneticSurfaceContainerLow = Color(0xFF0E1526)
    CyberneticSurfaceContainerLowest = Color(0xFF070B16)
    CyberneticSurfaceContainerHigh = Color(0xFF1C2742)
    CyberneticSurfaceContainerHighest = Color(0xFF263456)
    CyberneticSurfaceBright = Color(0xFF2E3E66)

    CyberneticPrimary = Color(0xFF00E5FF)
    CyberneticOnPrimary = Color(0xFF00363D)
    CyberneticPrimaryContainer = Color(0xFF005662)
    CyberneticOnPrimaryContainer = Color(0xFF8CF4FF)

    CyberneticSecondary = Color(0xFF00E676)
    CyberneticOnSecondary = Color(0xFF003919)
    CyberneticSecondaryContainer = Color(0xFF005929)
    CyberneticOnSecondaryContainer = Color(0xFF70FF9E)

    CyberneticTertiary = Color(0xFFFFB300)
    CyberneticOnTertiary = Color(0xFF452B00)
    CyberneticTertiaryContainer = Color(0xFF6B4300)
    CyberneticOnTertiaryContainer = Color(0xFFFFDF9E)

    CyberneticError = Color(0xFFFF5252)
    CyberneticOnError = Color(0xFF490005)
    CyberneticErrorContainer = Color(0xFF93000A)
    CyberneticOnErrorContainer = Color(0xFFFFDAD6)

    CyberneticOnSurface = Color(0xFFF1F5F9)
    CyberneticOnSurfaceVariant = Color(0xFF94A3B8)
    CyberneticOutline = Color(0xFF64748B)
    CyberneticOutlineVariant = Color(0xFF334155)

    CyberneticBackgroundGradientTop = Color(0xFF070B16)
    CyberneticBackgroundGradientBottom = Color(0xFF10172A)
}

private fun applyGlass() {
    isGlassTheme = true
    currentThemeMode = ThemeMode.GLASS

    CyberneticSurface = Color(0xFF0B1120)
    CyberneticSurfaceContainer = Color(0xB81E293B)
    CyberneticSurfaceContainerLow = Color(0x940F172A)
    CyberneticSurfaceContainerLowest = Color(0x70020617)
    CyberneticSurfaceContainerHigh = Color(0xCC334155)
    CyberneticSurfaceContainerHighest = Color(0xE0475569)
    CyberneticSurfaceBright = Color(0xFF64748B)

    CyberneticPrimary = Color(0xFF38BDF8)
    CyberneticOnPrimary = Color(0xFF003247)
    CyberneticPrimaryContainer = Color(0xFF0284C7)
    CyberneticOnPrimaryContainer = Color(0xFFE0F2FE)

    CyberneticSecondary = Color(0xFF34D399)
    CyberneticOnSecondary = Color(0xFF003822)
    CyberneticSecondaryContainer = Color(0xFF059669)
    CyberneticOnSecondaryContainer = Color(0xFFD1FAE5)

    CyberneticTertiary = Color(0xFFFBBF24)
    CyberneticOnTertiary = Color(0xFF452B00)
    CyberneticTertiaryContainer = Color(0xFFD97706)
    CyberneticOnTertiaryContainer = Color(0xFFFEF3C7)

    CyberneticError = Color(0xFFF87171)
    CyberneticOnError = Color(0xFF500000)
    CyberneticErrorContainer = Color(0xFFDC2626)
    CyberneticOnErrorContainer = Color(0xFFFEE2E2)

    CyberneticOnSurface = Color(0xFFF8FAFC)
    CyberneticOnSurfaceVariant = Color(0xFFCBD5E1)
    CyberneticOutline = Color(0xFF94A3B8)
    CyberneticOutlineVariant = Color(0xFF475569)

    CyberneticBackgroundGradientTop = Color(0xFF090D1A)
    CyberneticBackgroundGradientBottom = Color(0xFF1E293B)
}

private fun applyEmerald() {
    isGlassTheme = false
    currentThemeMode = ThemeMode.EMERALD

    CyberneticSurface = Color(0xFF091410)
    CyberneticSurfaceContainer = Color(0xFF10241D)
    CyberneticSurfaceContainerLow = Color(0xFF0C1B15)
    CyberneticSurfaceContainerLowest = Color(0xFF050C0A)
    CyberneticSurfaceContainerHigh = Color(0xFF18362B)
    CyberneticSurfaceContainerHighest = Color(0xFF224A3B)
    CyberneticSurfaceBright = Color(0xFF2F6350)

    CyberneticPrimary = Color(0xFF10B981)
    CyberneticOnPrimary = Color(0xFF003824)
    CyberneticPrimaryContainer = Color(0xFF059669)
    CyberneticOnPrimaryContainer = Color(0xFFA7F3D0)

    CyberneticSecondary = Color(0xFF38BDF8)
    CyberneticOnSecondary = Color(0xFF00354A)
    CyberneticSecondaryContainer = Color(0xFF0284C7)
    CyberneticOnSecondaryContainer = Color(0xFFBAE6FD)

    CyberneticTertiary = Color(0xFFF59E0B)
    CyberneticOnTertiary = Color(0xFF452B00)
    CyberneticTertiaryContainer = Color(0xFFB45309)
    CyberneticOnTertiaryContainer = Color(0xFFFDE68A)

    CyberneticError = Color(0xFFEF4444)
    CyberneticOnError = Color(0xFF490005)
    CyberneticErrorContainer = Color(0xFFB91C1C)
    CyberneticOnErrorContainer = Color(0xFFFEE2E2)

    CyberneticOnSurface = Color(0xFFECFDF5)
    CyberneticOnSurfaceVariant = Color(0xFFA7F3D0)
    CyberneticOutline = Color(0xFF6EE7B7)
    CyberneticOutlineVariant = Color(0xFF065F46)

    CyberneticBackgroundGradientTop = Color(0xFF060D0A)
    CyberneticBackgroundGradientBottom = Color(0xFF122820)
}

private fun applyAmethyst() {
    isGlassTheme = false
    currentThemeMode = ThemeMode.AMETHYST

    CyberneticSurface = Color(0xFF100C1E)
    CyberneticSurfaceContainer = Color(0xFF1B1533)
    CyberneticSurfaceContainerLow = Color(0xFF140F26)
    CyberneticSurfaceContainerLowest = Color(0xFF0B0815)
    CyberneticSurfaceContainerHigh = Color(0xFF261D47)
    CyberneticSurfaceContainerHighest = Color(0xFF34285F)
    CyberneticSurfaceBright = Color(0xFF45367D)

    CyberneticPrimary = Color(0xFFA855F7)
    CyberneticOnPrimary = Color(0xFF380068)
    CyberneticPrimaryContainer = Color(0xFF7E22CE)
    CyberneticOnPrimaryContainer = Color(0xFFF3E8FF)

    CyberneticSecondary = Color(0xFF06B6D4)
    CyberneticOnSecondary = Color(0xFF003640)
    CyberneticSecondaryContainer = Color(0xFF0891B2)
    CyberneticOnSecondaryContainer = Color(0xFFCFFAFE)

    CyberneticTertiary = Color(0xFFEC4899)
    CyberneticOnTertiary = Color(0xFF4A0028)
    CyberneticTertiaryContainer = Color(0xFFBE185D)
    CyberneticOnTertiaryContainer = Color(0xFFFCE7F3)

    CyberneticError = Color(0xFFFF5252)
    CyberneticOnError = Color(0xFF490005)
    CyberneticErrorContainer = Color(0xFF93000A)
    CyberneticOnErrorContainer = Color(0xFFFFDAD6)

    CyberneticOnSurface = Color(0xFFF5F3FF)
    CyberneticOnSurfaceVariant = Color(0xFFDDD6FE)
    CyberneticOutline = Color(0xFFA78BFA)
    CyberneticOutlineVariant = Color(0xFF581C87)

    CyberneticBackgroundGradientTop = Color(0xFF0B0815)
    CyberneticBackgroundGradientBottom = Color(0xFF1E1738)
}

private fun applyWhite() {
    isGlassTheme = false
    currentThemeMode = ThemeMode.WHITE

    CyberneticSurface = Color(0xFFFFFFFF)
    CyberneticSurfaceContainer = Color(0xFFF8FAFC)
    CyberneticSurfaceContainerLow = Color(0xFFFFFFFF)
    CyberneticSurfaceContainerLowest = Color(0xFFF1F5F9)
    CyberneticSurfaceContainerHigh = Color(0xFFE2E8F0)
    CyberneticSurfaceContainerHighest = Color(0xFFCBD5E1)
    CyberneticSurfaceBright = Color(0xFFFFFFFF)

    CyberneticPrimary = Color(0xFF2563EB)
    CyberneticOnPrimary = Color(0xFFFFFFFF)
    CyberneticPrimaryContainer = Color(0xFFDBEAFE)
    CyberneticOnPrimaryContainer = Color(0xFF1E40AF)

    CyberneticSecondary = Color(0xFF059669)
    CyberneticOnSecondary = Color(0xFFFFFFFF)
    CyberneticSecondaryContainer = Color(0xFFD1FAE5)
    CyberneticOnSecondaryContainer = Color(0xFF065F46)

    CyberneticTertiary = Color(0xFFD97706)
    CyberneticOnTertiary = Color(0xFFFFFFFF)
    CyberneticTertiaryContainer = Color(0xFFFEF3C7)
    CyberneticOnTertiaryContainer = Color(0xFF92400E)

    CyberneticError = Color(0xFFDC2626)
    CyberneticOnError = Color(0xFFFFFFFF)
    CyberneticErrorContainer = Color(0xFFFEE2E2)
    CyberneticOnErrorContainer = Color(0xFF991B1B)

    CyberneticOnSurface = Color(0xFF0F172A)
    CyberneticOnSurfaceVariant = Color(0xFF475569)
    CyberneticOutline = Color(0xFF94A3B8)
    CyberneticOutlineVariant = Color(0xFFE2E8F0)

    CyberneticBackgroundGradientTop = Color(0xFFFFFFFF)
    CyberneticBackgroundGradientBottom = Color(0xFFF8FAFC)
}

private fun applyGray() {
    isGlassTheme = false
    currentThemeMode = ThemeMode.GRAY

    CyberneticSurface = Color(0xFFF3F4F6)
    CyberneticSurfaceContainer = Color(0xFFE5E7EB)
    CyberneticSurfaceContainerLow = Color(0xFFF9FAFB)
    CyberneticSurfaceContainerLowest = Color(0xFFFFFFFF)
    CyberneticSurfaceContainerHigh = Color(0xFFD1D5DB)
    CyberneticSurfaceContainerHighest = Color(0xFF9CA3AF)
    CyberneticSurfaceBright = Color(0xFFFFFFFF)

    CyberneticPrimary = Color(0xFF374151)
    CyberneticOnPrimary = Color(0xFFFFFFFF)
    CyberneticPrimaryContainer = Color(0xFFE5E7EB)
    CyberneticOnPrimaryContainer = Color(0xFF111827)

    CyberneticSecondary = Color(0xFF10B981)
    CyberneticOnSecondary = Color(0xFFFFFFFF)
    CyberneticSecondaryContainer = Color(0xFFD1FAE5)
    CyberneticOnSecondaryContainer = Color(0xFF065F46)

    CyberneticTertiary = Color(0xFFD97706)
    CyberneticOnTertiary = Color(0xFFFFFFFF)
    CyberneticTertiaryContainer = Color(0xFFFEF3C7)
    CyberneticOnTertiaryContainer = Color(0xFF92400E)

    CyberneticError = Color(0xFFDC2626)
    CyberneticOnError = Color(0xFFFFFFFF)
    CyberneticErrorContainer = Color(0xFFFEE2E2)
    CyberneticOnErrorContainer = Color(0xFF991B1B)

    CyberneticOnSurface = Color(0xFF111827)
    CyberneticOnSurfaceVariant = Color(0xFF4B5563)
    CyberneticOutline = Color(0xFF6B7280)
    CyberneticOutlineVariant = Color(0xFFD1D5DB)

    CyberneticBackgroundGradientTop = Color(0xFFF9FAFB)
    CyberneticBackgroundGradientBottom = Color(0xFFE5E7EB)
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

@Composable
fun CyberneticRadarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val isDark = currentThemeMode.isDark || isGlassTheme || darkTheme
    val colorScheme = if (isDark) {
        darkColorScheme(
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
    } else {
        lightColorScheme(
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
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
