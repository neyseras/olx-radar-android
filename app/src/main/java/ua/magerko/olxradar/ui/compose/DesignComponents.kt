package ua.magerko.olxradar.ui.compose

import android.text.format.DateUtils
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

/**
 * Общие быстрые форматтеры без создания объектов на каждом кадре скролла.
 */
object CachedFormatters {
    private val ukNumberFormat = ThreadLocal.withInitial {
        NumberFormat.getNumberInstance(Locale("uk", "UA"))
    }

    fun formatPrice(price: Int?, currency: String?): String {
        if (price == null || price <= 0) return "Договорная"
        val symbol = when (currency?.trim()?.uppercase()) {
            "USD", "$" -> "$"
            "EUR", "€" -> "€"
            "UAH", "ГРН", "ГРН.", "₴", null, "" -> "₴"
            else -> currency
        }
        val num = ukNumberFormat.get()?.format(price) ?: price.toString()
        return if (symbol == "$" || symbol == "€") "$symbol$num" else "$num $symbol"
    }

    fun formatRelativeTime(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        if (timestampMs <= 0L) return ""
        return DateUtils.getRelativeTimeSpanString(
            timestampMs,
            nowMs,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        ).toString()
    }

    fun formatShortAgo(timestampMs: Long?, nowMs: Long = System.currentTimeMillis()): String {
        if (timestampMs == null || timestampMs <= 0L) return "ожидает"
        val sec = ((nowMs - timestampMs) / 1000L).coerceAtLeast(0L)
        return when {
            sec < 10 -> "только что"
            sec < 60 -> "${sec}с назад"
            sec < 3600 -> "${sec / 60}м назад"
            sec < 86400 -> "${sec / 3600}ч назад"
            else -> "${sec / 86400}д назад"
        }
    }
}

/**
 * Лёгкий GPU-дружелюбный градиентный фон с мягкими радиальными орбами.
 * Придаёт глубину Liquid Glass и тёмным темам, оставаясь чистым на светлых темах.
 */
@Composable
fun AmbientRadarBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val top = CyberneticBackgroundGradientTop
    val bottom = CyberneticBackgroundGradientBottom
    val orb1 = CyberneticOrbPrimary
    val orb2 = CyberneticOrbSecondary

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val bgBrush = Brush.verticalGradient(listOf(top, bottom))
                val orb1Center = Offset(size.width * 0.85f, size.height * 0.10f)
                val orb1Radius = size.maxDimension * 0.52f
                val orb1Brush = Brush.radialGradient(
                    colors = listOf(orb1, Color.Transparent),
                    center = orb1Center,
                    radius = orb1Radius
                )
                val orb2Center = Offset(size.width * 0.12f, size.height * 0.82f)
                val orb2Radius = size.maxDimension * 0.48f
                val orb2Brush = Brush.radialGradient(
                    colors = listOf(orb2, Color.Transparent),
                    center = orb2Center,
                    radius = orb2Radius
                )
                onDrawBehind {
                    drawRect(bgBrush)
                    drawCircle(brush = orb1Brush, center = orb1Center, radius = orb1Radius)
                    drawCircle(brush = orb2Brush, center = orb2Center, radius = orb2Radius)
                }
            }
    ) {
        content()
    }
}

/**
 * Единая карточка с корректным контрастом для всех тем (Тёмные, Liquid Glass, AMOLED, Светлые).
 */
@Composable
fun RadarSurfaceCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    containerColor: Color = if (isGlassTheme) CyberneticSurfaceContainer.copy(alpha = 0.84f) else CyberneticSurfaceContainer,
    borderColor: Color = CyberneticCardBorder,
    borderWidth: Dp = 1.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val elevation = if (!currentThemeMode.isDark && !isGlassTheme) 2.dp else 0.dp
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = BorderStroke(borderWidth, borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
            content = content
        )
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = BorderStroke(borderWidth, borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
            content = content
        )
    }
}

/**
 * Живой пульсирующий индикатор статуса (отключает анимацию при включённом reduceAnimations).
 */
@Composable
fun StatusPulseDot(
    color: Color,
    isPulsing: Boolean,
    reduceAnimations: Boolean = false,
    size: Dp = 8.dp
) {
    val scale = if (isPulsing && !reduceAnimations) {
        val transition = rememberInfiniteTransition(label = "status_pulse")
        val animated by transition.animateFloat(
            initialValue = 0.82f,
            targetValue = 1.32f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot_scale"
        )
        animated
    } else {
        1f
    }

    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(color)
    )
}

/**
 * Бейдж локального рейтинга выгодности сделки (Deal Score 0..100).
 */
@Composable
fun DealScoreBadge(score: Int) {
    val (bg, fg, prefix) = when {
        score >= 80 -> Triple(CyberneticSecondary.copy(alpha = 0.22f), CyberneticSecondary, "ТОП")
        score >= 65 -> Triple(CyberneticPrimary.copy(alpha = 0.20f), CyberneticPrimary, "Выгодно")
        else -> return
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.35f))
    ) {
        Text(
            text = "$prefix $score",
            color = fg,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

/**
 * Плитка метрики для дашборда и диагностики.
 */
@Composable
fun MetricTile(
    label: String,
    value: String,
    subLabel: String? = null,
    accentColor: Color = CyberneticPrimary,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CyberneticSurfaceContainerLow,
        border = BorderStroke(1.dp, CyberneticCardBorderSoft),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    text = label,
                    color = CyberneticOnSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                color = CyberneticOnSurface,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            if (!subLabel.isNullOrBlank()) {
                Text(
                    text = subLabel,
                    color = accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
