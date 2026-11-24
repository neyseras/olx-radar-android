package ua.magerko.olxradar.ui.compose

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import ua.magerko.olxradar.service.MonitorService

/**
 * Доступные темы оформления приложения.
 */
enum class ThemeMode(
    val title: String,
    val subtitle: String,
    val isDark: Boolean,
    val primaryPreview: Color,
    val surfacePreview: Color,
    val secondaryPreview: Color
) {
    CYBER("Кибернетика", "Неоновый тёмный контраст", true, Color(0xFF00E5FF), Color(0xFF0A0F1D), Color(0xFF00E676)),
    GLASS("Liquid Glass", "Полупрозрачное стекло и сияние", true, Color(0xFF38BDF8), Color(0xFF0B1120), Color(0xFF34D399)),
    AMOLED("AMOLED Ночь", "Чистый чёрный для OLED", true, Color(0xFF22D3EE), Color(0xFF000000), Color(0xFF10B981)),
    EMERALD("Изумруд", "Глубокая ночная зелень", true, Color(0xFF10B981), Color(0xFF091410), Color(0xFF38BDF8)),
    AMETHYST("Аметист", "Космический фиолетовый", true, Color(0xFFA855F7), Color(0xFF100C1E), Color(0xFF06B6D4)),
    WHITE("Светлая", "Чистая воздушная классика", false, Color(0xFF2563EB), Color(0xFFF8FAFC), Color(0xFF059669)),
    GRAY("Графит", "Скандинавский минимализм", false, Color(0xFF374151), Color(0xFFF1F3F5), Color(0xFF10B981));

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

/** Семантические цвета обводки карточек и фоновых свечений (корректны и для светлых, и для тёмных тем) */
var CyberneticCardBorder by mutableStateOf(Color(0x24FFFFFF))
var CyberneticCardBorderSoft by mutableStateOf(Color(0x10FFFFFF))
var CyberneticOrbPrimary by mutableStateOf(Color(0x2400E5FF))
var CyberneticOrbSecondary by mutableStateOf(Color(0x1800E676))

/** Градиент фона */
var CyberneticBackgroundGradientTop by mutableStateOf(Color(0xFF070B16))
var CyberneticBackgroundGradientBottom by mutableStateOf(Color(0xFF10172A))

/** Флаг темы Liquid Glass */
var isGlassTheme by mutableStateOf(false)

/** Непрозрачный цвет для модальных окон, диалогов и меню (даже в Liquid Glass режиме) */
val CyberneticModalSurface: Color
    get() = if (isGlassTheme) Color(0xF5151F33) else CyberneticSurfaceContainer

val isLiquidGlassSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

val isGlassFallbackSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private fun applyCyber() {
    isGlassTheme = false
    currentThemeMode = ThemeMode.CYBER

    CyberneticSurface = Color(0xFF090E1A)
    CyberneticSurfaceContainer = Color(0xFF131B2E)
    CyberneticSurfaceContainerLow = Color(0xFF0D1424)
    CyberneticSurfaceContainerLowest = Color(0xFF060A14)
    CyberneticSurfaceContainerHigh = Color(0xFF1B2640)
    CyberneticSurfaceContainerHighest = Color(0xFF243254)
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

    CyberneticCardBorder = Color(0x24FFFFFF)
    CyberneticCardBorderSoft = Color(0x0FFFFFFF)
    CyberneticOrbPrimary = Color(0x2200E5FF)
    CyberneticOrbSecondary = Color(0x1A00E676)

    CyberneticBackgroundGradientTop = Color(0xFF060A14)
    CyberneticBackgroundGradientBottom = Color(0xFF0F172A)
}

private fun applyGlass() {
    isGlassTheme = true
    currentThemeMode = ThemeMode.GLASS

    CyberneticSurface = Color(0xFF080E1E)
    CyberneticSurfaceContainer = Color(0xA61A253B)
    CyberneticSurfaceContainerLow = Color(0x800F172A)
    CyberneticSurfaceContainerLowest = Color(0x66040814)
    CyberneticSurfaceContainerHigh = Color(0xC2283754)
    CyberneticSurfaceContainerHighest = Color(0xD63B4D6E)
    CyberneticSurfaceBright = Color(0xFF64748B)

    CyberneticPrimary = Color(0xFF38BDF8)
    CyberneticOnPrimary = Color(0xFF002B40)
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

    CyberneticCardBorder = Color(0x47FFFFFF)
    CyberneticCardBorderSoft = Color(0x1AFFFFFF)
    CyberneticOrbPrimary = Color(0x3838BDF8)
    CyberneticOrbSecondary = Color(0x2834D399)

    CyberneticBackgroundGradientTop = Color(0xFF070C1B)
    CyberneticBackgroundGradientBottom = Color(0xFF17233B)
}

private fun applyAmoled() {
    isGlassTheme = false
    currentThemeMode = ThemeMode.AMOLED

    CyberneticSurface = Color(0xFF000000)
    CyberneticSurfaceContainer = Color(0xFF0B0E14)
    CyberneticSurfaceContainerLow = Color(0xFF06080C)
    CyberneticSurfaceContainerLowest = Color(0xFF000000)
    CyberneticSurfaceContainerHigh = Color(0xFF131822)
    CyberneticSurfaceContainerHighest = Color(0xFF1E2636)
    CyberneticSurfaceBright = Color(0xFF2B354A)

    CyberneticPrimary = Color(0xFF22D3EE)
    CyberneticOnPrimary = Color(0xFF00333B)
    CyberneticPrimaryContainer = Color(0xFF0E7490)
    CyberneticOnPrimaryContainer = Color(0xFFCFFAFE)

    CyberneticSecondary = Color(0xFF10B981)
    CyberneticOnSecondary = Color(0xFF003824)
    CyberneticSecondaryContainer = Color(0xFF047857)
    CyberneticOnSecondaryContainer = Color(0xFFA7F3D0)

    CyberneticTertiary = Color(0xFFF59E0B)
    CyberneticOnTertiary = Color(0xFF452B00)
    CyberneticTertiaryContainer = Color(0xFFB45309)
    CyberneticOnTertiaryContainer = Color(0xFFFEF3C7)

    CyberneticError = Color(0xFFFB7185)
    CyberneticOnError = Color(0xFF4C0519)
    CyberneticErrorContainer = Color(0xFF9F1239)
    CyberneticOnErrorContainer = Color(0xFFFFE4E6)

    CyberneticOnSurface = Color(0xFFF8FAFC)
    CyberneticOnSurfaceVariant = Color(0xFF94A3B8)
    CyberneticOutline = Color(0xFF64748B)
    CyberneticOutlineVariant = Color(0xFF1E293B)

    CyberneticCardBorder = Color(0x22FFFFFF)
    CyberneticCardBorderSoft = Color(0x0DFFFFFF)
    CyberneticOrbPrimary = Color(0x1622D3EE)
    CyberneticOrbSecondary = Color(0x1210B981)

    CyberneticBackgroundGradientTop = Color(0xFF000000)
    CyberneticBackgroundGradientBottom = Color(0xFF04060A)
}

private fun applyEmerald() {
    isGlassTheme = false
    currentThemeMode = ThemeMode.EMERALD

    CyberneticSurface = Color(0xFF07120E)
    CyberneticSurfaceContainer = Color(0xFF0F211A)
    CyberneticSurfaceContainerLow = Color(0xFF0B1914)
    CyberneticSurfaceContainerLowest = Color(0xFF040B08)
    CyberneticSurfaceContainerHigh = Color(0xFF163228)
    CyberneticSurfaceContainerHighest = Color(0xFF1F4436)
    CyberneticSurfaceBright = Color(0xFF2B5B49)

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

    CyberneticCardBorder = Color(0x286EE7B7)
    CyberneticCardBorderSoft = Color(0x126EE7B7)
    CyberneticOrbPrimary = Color(0x2210B981)
    CyberneticOrbSecondary = Color(0x1A38BDF8)

    CyberneticBackgroundGradientTop = Color(0xFF050C09)
    CyberneticBackgroundGradientBottom = Color(0xFF10241C)
}

private fun applyAmethyst() {
    isGlassTheme = false
    currentThemeMode = ThemeMode.AMETHYST

    CyberneticSurface = Color(0xFF0E0A1B)
    CyberneticSurfaceContainer = Color(0xFF191330)
    CyberneticSurfaceContainerLow = Color(0xFF130E24)
    CyberneticSurfaceContainerLowest = Color(0xFF090612)
    CyberneticSurfaceContainerHigh = Color(0xFF241B44)
    CyberneticSurfaceContainerHighest = Color(0xFF31255A)
    CyberneticSurfaceBright = Color(0xFF423378)

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

    CyberneticCardBorder = Color(0x2AA78BFA)
    CyberneticCardBorderSoft = Color(0x14A78BFA)
    CyberneticOrbPrimary = Color(0x28A855F7)
    CyberneticOrbSecondary = Color(0x1E06B6D4)

    CyberneticBackgroundGradientTop = Color(0xFF090612)
    CyberneticBackgroundGradientBottom = Color(0xFF1C1435)
}

private fun applyWhite() {
    isGlassTheme = false
    currentThemeMode = ThemeMode.WHITE

    CyberneticSurface = Color(0xFFF8FAFC)
    CyberneticSurfaceContainer = Color(0xFFFFFFFF)
    CyberneticSurfaceContainerLow = Color(0xFFF1F5F9)
    CyberneticSurfaceContainerLowest = Color(0xFFE2E8F0)
    CyberneticSurfaceContainerHigh = Color(0xFFEEF2F6)
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
    CyberneticOutline = Color(0xFF64748B)
    CyberneticOutlineVariant = Color(0xFFCBD5E1)

    CyberneticCardBorder = Color(0xFFDCE3ED)
    CyberneticCardBorderSoft = Color(0xFFE9EEF5)
    CyberneticOrbPrimary = Color(0x142563EB)
    CyberneticOrbSecondary = Color(0x10059669)

    CyberneticBackgroundGradientTop = Color(0xFFF8FAFC)
    CyberneticBackgroundGradientBottom = Color(0xFFEFF4FB)
}

private fun applyGray() {
    isGlassTheme = false
    currentThemeMode = ThemeMode.GRAY

    CyberneticSurface = Color(0xFFF1F3F5)
    CyberneticSurfaceContainer = Color(0xFFFFFFFF)
    CyberneticSurfaceContainerLow = Color(0xFFE9ECEF)
    CyberneticSurfaceContainerLowest = Color(0xFFDEE2E6)
    CyberneticSurfaceContainerHigh = Color(0xFFE5E7EB)
    CyberneticSurfaceContainerHighest = Color(0xFFCED4DA)
    CyberneticSurfaceBright = Color(0xFFFFFFFF)

    CyberneticPrimary = Color(0xFF1F2937)
    CyberneticOnPrimary = Color(0xFFFFFFFF)
    CyberneticPrimaryContainer = Color(0xFFE5E7EB)
    CyberneticOnPrimaryContainer = Color(0xFF111827)

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

    CyberneticOnSurface = Color(0xFF111827)
    CyberneticOnSurfaceVariant = Color(0xFF4B5563)
    CyberneticOutline = Color(0xFF6B7280)
    CyberneticOutlineVariant = Color(0xFFD1D5DB)

    CyberneticCardBorder = Color(0xFFD1D5DB)
    CyberneticCardBorderSoft = Color(0xFFE5E7EB)
    CyberneticOrbPrimary = Color(0x12374151)
    CyberneticOrbSecondary = Color(0x10059669)

    CyberneticBackgroundGradientTop = Color(0xFFF3F4F6)
    CyberneticBackgroundGradientBottom = Color(0xFFE5E7EB)
}

fun applyThemeMode(mode: ThemeMode) {
    when (mode) {
        ThemeMode.CYBER -> applyCyber()
        ThemeMode.GLASS -> applyGlass()
        ThemeMode.AMOLED -> applyAmoled()
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
        AppCompatDelegate.setDefaultNightMode(
            if (mode.isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun CyberneticRadarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Используем именно isDark выбранной пользователем темы, а не системный флаг для светлых тем!
    val isDark = currentThemeMode.isDark || isGlassTheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window
            if (window != null) {
                window.statusBarColor = Color.Transparent.toArgb()
                window.navigationBarColor = Color.Transparent.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

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
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
