package ua.magerko.olxradar.ui.compose

import android.os.Build
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.backdrops.layerBackdrop

/**
 * Готовые Liquid Glass шейдеры из библиотеки Backdrop
 * (io.github.kyant0:backdrop, https://github.com/Kyant0/AndroidLiquidGlass).
 * Здесь только удобные обёртки над её публичным API.
 */

@Composable
fun rememberGlassBackdrop(background: Color): LayerBackdrop {
    return rememberLayerBackdrop {
        drawRect(background)
        drawContent()
    }
}

/** Есть ли вообще смысл рисовать стекло на этом устройстве. */
fun glassAvailable(isGlassTheme: Boolean): Boolean =
    isGlassTheme && isGlassFallbackSupported

/** Стеклянный модификатор: вибрация цвета + blur + (API 33+) линза-преломление. */
fun androidx.compose.ui.Modifier.glassEffect(
    backdrop: LayerBackdrop,
    shape: Shape,
    blurPx: Float,
    lensPx: Float,
    tint: Color = Color.White.copy(alpha = 0.14f)
): androidx.compose.ui.Modifier {
    val useLens = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    return drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur(blurPx)
            if (useLens && lensPx > 0f) {
                lens(lensPx, lensPx * 2f)
            }
        },
        onDrawSurface = { drawRect(tint) }
    )
}

/** Тонкая светлая кромка стеклянной панели (классический stroke жидкого стекла). */
fun DrawScope.drawGlassEdge(
    shape: Shape,
    edgeColor: Color = Color.White.copy(alpha = 0.35f)
) {
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            this.style = PaintingStyle.Stroke
            this.strokeWidth = 2f
            this.color = edgeColor
        }
        canvas.nativeCanvas.drawRoundRect(
            1f, 1f, size.width - 1f, size.height - 1f,
            24f, 24f, paint.asFrameworkPaint()
        )
    }
}

fun defaultGlassShape(cornerRadius: Dp = 28.dp): Shape {
    return RoundedCornerShape(cornerRadius)
}
