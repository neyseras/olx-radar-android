package ua.magerko.olxradar.ui.compose

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import ua.magerko.olxradar.ai.AiRuntime
import ua.magerko.olxradar.ai.LiveAiItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiLiveStreamScreen(
    onNavigateToSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val aiActivity by AiRuntime.activity.collectAsState()

    var filterMode by remember { mutableStateOf("all") } // "all", "approved", "rejected", "other"
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    // Текстовый таймер для отображения живых секунд в карточке
    var ticker by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(aiActivity.isBusy) {
        while (aiActivity.isBusy) {
            ticker = System.currentTimeMillis()
            kotlinx.coroutines.delay(500)
        }
    }

    val history = aiActivity.history
    val filteredHistory = remember(history, filterMode) {
        when (filterMode) {
            "approved" -> history.filter { it.status.equals("APPROVED", ignoreCase = true) || it.status.equals("MATCH", ignoreCase = true) }
            "rejected" -> history.filter { it.status.equals("REJECTED", ignoreCase = true) }
            "other" -> history.filter { 
                !it.status.equals("APPROVED", ignoreCase = true) && 
                !it.status.equals("MATCH", ignoreCase = true) && 
                !it.status.equals("REJECTED", ignoreCase = true) 
            }
            else -> history
        }
    }

    fun openUrl(url: String) {
        if (url.isBlank()) {
            Toast.makeText(context, "Ссылка отсутствует", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Не удалось открыть ссылку: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun copyUrl(url: String) {
        if (url.isBlank()) return
        clipboard.setText(AnnotatedString(url))
        Toast.makeText(context, "Ссылка скопирована в буфер", Toast.LENGTH_SHORT).show()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isGlassTheme) Color.Transparent else CyberneticSurface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // TOP BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyberneticPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = CyberneticPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "ИИ-поток (Groq Vision)",
                            color = CyberneticOnSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Pulsing live indicator
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                            val pulseScale by infiniteTransition.animateFloat(
                                initialValue = 0.8f,
                                targetValue = 1.25f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(800, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "scale"
                            )

                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .scale(if (aiActivity.isBusy) pulseScale else 1f)
                                    .clip(CircleShape)
                                    .background(if (aiActivity.isBusy) CyberneticPrimary else CyberneticSecondary)
                            )
                            Text(
                                text = if (aiActivity.isBusy) "Анализирует ссылку..." else "Ожидание новых ссылок",
                                color = if (aiActivity.isBusy) CyberneticPrimary else CyberneticOnSurfaceVariant,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (history.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearConfirmDialog = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Очистить историю",
                                tint = CyberneticOnSurfaceVariant
                            )
                        }
                    }

                    if (onNavigateToSettings != null) {
                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Настройки ИИ",
                                tint = CyberneticOnSurfaceVariant
                            )
                        }
                    }
                }
            }

            // MAIN SCROLLABLE STREAM
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {

                    // HERO: CURRENTLY PROCESSING ITEM
                    item {
                        ActiveAiProcessingHero(
                            activity = aiActivity,
                            currentMs = ticker,
                            onOpenUrl = { openUrl(it) },
                            onCopyUrl = { copyUrl(it) }
                        )
                    }

                    // METRICS STRIP
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MetricSmallCard(
                                title = "Обработано",
                                value = "${aiActivity.totalProcessed}",
                                color = CyberneticPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            MetricSmallCard(
                                title = "Одобрено",
                                value = "${aiActivity.approvedCount}",
                                color = CyberneticSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            MetricSmallCard(
                                title = "Отклонено",
                                value = "${aiActivity.rejectedCount}",
                                color = CyberneticError,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // FILTER CHIPS ROW
                    item {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = filterMode == "all",
                                    onClick = { filterMode = "all" },
                                    label = { Text("Все (${history.size})") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyberneticPrimary.copy(alpha = 0.2f),
                                        selectedLabelColor = CyberneticPrimary,
                                        containerColor = CyberneticSurfaceContainerHigh,
                                        labelColor = CyberneticOnSurfaceVariant
                                    )
                                )
                            }
                            item {
                                val approvedCount = history.count { it.status.equals("APPROVED", ignoreCase = true) || it.status.equals("MATCH", ignoreCase = true) }
                                FilterChip(
                                    selected = filterMode == "approved",
                                    onClick = { filterMode = "approved" },
                                    label = { Text("✓ Подходят ($approvedCount)") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyberneticSecondary.copy(alpha = 0.2f),
                                        selectedLabelColor = CyberneticSecondary,
                                        containerColor = CyberneticSurfaceContainerHigh,
                                        labelColor = CyberneticOnSurfaceVariant
                                    )
                                )
                            }
                            item {
                                val rejectedCount = history.count { it.status.equals("REJECTED", ignoreCase = true) }
                                FilterChip(
                                    selected = filterMode == "rejected",
                                    onClick = { filterMode = "rejected" },
                                    label = { Text("✗ Отклонено ($rejectedCount)") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyberneticError.copy(alpha = 0.2f),
                                        selectedLabelColor = CyberneticError,
                                        containerColor = CyberneticSurfaceContainerHigh,
                                        labelColor = CyberneticOnSurfaceVariant
                                    )
                                )
                            }
                            item {
                                val otherCount = history.count { 
                                    !it.status.equals("APPROVED", ignoreCase = true) && 
                                    !it.status.equals("MATCH", ignoreCase = true) && 
                                    !it.status.equals("REJECTED", ignoreCase = true) 
                                }
                                FilterChip(
                                    selected = filterMode == "other",
                                    onClick = { filterMode = "other" },
                                    label = { Text("Прочее ($otherCount)") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyberneticTertiary.copy(alpha = 0.2f),
                                        selectedLabelColor = CyberneticTertiary,
                                        containerColor = CyberneticSurfaceContainerHigh,
                                        labelColor = CyberneticOnSurfaceVariant
                                    )
                                )
                            }
                        }
                    }

                    // EMPTY STATE
                    if (filteredHistory.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TravelExplore,
                                        contentDescription = null,
                                        tint = CyberneticOutline,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        text = if (history.isEmpty()) "История ссылок пуста" else "Нет лотов под выбранный фильтр",
                                        color = CyberneticOnSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (history.isEmpty())
                                            "Когда радары обнаружат новые объявления, они автоматически отправятся в нейросеть и появятся здесь с прямыми ссылками."
                                        else
                                            "Переключите фильтр для просмотра всех проверенных ссылок.",
                                        color = CyberneticOnSurfaceVariant,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 32.dp),
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredHistory, key = { it.id }) { item ->
                            ProcessedAiItemCard(
                                item = item,
                                onOpenUrl = { openUrl(it) },
                                onCopyUrl = { copyUrl(it) }
                            )
                        }
                    }
                }
            }
        }
    }

    // DIALOG: CLEAR HISTORY CONFIRMATION
    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Очистить историю ссылок?") },
            text = { Text("Это удалит записи о недавно проверенных ссылках из текущего списка. Кэш базы данных затронут не будет.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        AiRuntime.clearHistory()
                        showClearConfirmDialog = false
                        Toast.makeText(context, "История очищена", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Очистить", color = CyberneticError)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

/**
 * Верхняя карточка активной обработки ссылки:
 * Показывает живой лот, если ИИ занят, или статус готовности, если свободен.
 */
@Composable
private fun ActiveAiProcessingHero(
    activity: AiRuntime.Activity,
    currentMs: Long,
    onOpenUrl: (String) -> Unit,
    onCopyUrl: (String) -> Unit
) {
    val current = activity.currentItem
    val isBusy = activity.isBusy
    val busySeconds = if (isBusy && activity.startedAtMs > 0) {
        (((currentMs.takeIf { it > 0 } ?: System.currentTimeMillis()) - activity.startedAtMs).coerceAtLeast(0) / 1000).toInt()
    } else 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isBusy) CyberneticPrimary.copy(alpha = 0.6f) else CyberneticOutlineVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isBusy) CyberneticSurfaceContainerHigh else CyberneticSurfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = CyberneticPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = CyberneticSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = if (isBusy) "ОБРАБАТЫВАЕТСЯ ПРЯМО СЕЙЧАС" else "НЕЙРОСЕТЬ ОЖИДАЕТ НОВЫХ ЛОТОВ",
                        color = if (isBusy) CyberneticPrimary else CyberneticOnSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                if (isBusy) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = CyberneticPrimary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "$busySeconds сек",
                            color = CyberneticPrimary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (isBusy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Thumbnail if available
                    if (!current?.photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = current?.photoUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyberneticSurfaceContainerLowest),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyberneticPrimary.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = CyberneticPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = current?.title ?: activity.title ?: "Объявление OLX",
                            color = CyberneticOnSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = CyberneticSurfaceContainer
                            ) {
                                Text(
                                    text = activity.radar ?: "Радар",
                                    color = CyberneticPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (activity.photosTotal > 0) {
                                Text(
                                    text = "Фото: ${activity.photosTotal}",
                                    color = CyberneticOnSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                            current?.price?.let { pr ->
                                Text(
                                    text = "$pr ${current.currency ?: "грн"}",
                                    color = CyberneticTertiary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Interactive URL Box
                val url = current?.url ?: ""
                if (url.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CyberneticSurfaceContainerLowest,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenUrl(url) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    tint = CyberneticPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = url,
                                    color = CyberneticPrimary,
                                    fontSize = 12.sp,
                                    textDecoration = TextDecoration.Underline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(
                                    onClick = { onCopyUrl(url) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Копировать ссылку",
                                        tint = CyberneticOnSurfaceVariant,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onOpenUrl(url) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInNew,
                                        contentDescription = "Открыть",
                                        tint = CyberneticPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "Мониторинг активен. Как только радары найдут объявления, их ссылки и анализ отобразятся здесь мгновенно.",
                    color = CyberneticOnSurfaceVariant,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

/**
 * Карточка проверенной ссылки из истории.
 */
@Composable
private fun ProcessedAiItemCard(
    item: LiveAiItem,
    onOpenUrl: (String) -> Unit,
    onCopyUrl: (String) -> Unit
) {
    val isApproved = item.status.equals("APPROVED", ignoreCase = true) || item.status.equals("MATCH", ignoreCase = true)
    val isRejected = item.status.equals("REJECTED", ignoreCase = true)
    val isCached = item.status.equals("CACHED", ignoreCase = true)
    val isError = item.status.equals("ERROR", ignoreCase = true)

    val statusBadgeColor = when {
        isApproved -> CyberneticSecondary
        isRejected -> CyberneticError
        isCached -> CyberneticPrimary
        isError -> CyberneticError
        else -> CyberneticTertiary
    }

    val statusBadgeText = when {
        isApproved -> "✓ Подходит ${item.confidence?.let { "$it%" } ?: ""}"
        isRejected -> "✗ Отклонено ${item.confidence?.let { "$it%" } ?: ""}"
        isCached -> "⚡ Из кэша"
        isError -> "⚠ Ошибка"
        else -> "⚠ На проверку ${item.confidence?.let { "$it%" } ?: ""}"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberneticOutlineVariant.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CyberneticSurfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row: Radar + Time + Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = CyberneticSurfaceContainerHigh
                    ) {
                        Text(
                            text = item.radarTitle ?: "Радар",
                            color = CyberneticOnSurfaceVariant,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    val agoSec = ((System.currentTimeMillis() - item.finishedAtMs) / 1000).coerceAtLeast(0)
                    val agoText = when {
                        agoSec < 10 -> "только что"
                        agoSec < 60 -> "${agoSec}с назад"
                        agoSec < 3600 -> "${agoSec / 60}м назад"
                        else -> "${agoSec / 3600}ч назад"
                    }
                    val durationText = if (!isCached && item.startedAtMs > 0 && item.finishedAtMs >= item.startedAtMs) {
                        String.format(java.util.Locale.US, "%.1fс", item.durationSec)
                    } else if (isCached) {
                        "кэш"
                    } else null

                    Text(
                        text = "· $agoText" + (durationText?.let { " ($it)" } ?: ""),
                        color = CyberneticOnSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusBadgeColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = statusBadgeText,
                        color = statusBadgeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Body: Thumbnail + Title + Price
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!item.photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.photoUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CyberneticSurfaceContainerLowest),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CyberneticSurfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = CyberneticOutline,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = CyberneticOnSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    item.price?.let { pr ->
                        Text(
                            text = "$pr ${item.currency ?: "грн"}",
                            color = CyberneticTertiary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            // AI Verdict Reason
            if (!item.reasons.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CyberneticSurfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = statusBadgeColor,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(top = 2.dp)
                        )
                        Text(
                            text = item.reasons,
                            color = CyberneticOnSurfaceVariant,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // Footer: URL and action buttons
            if (item.url.isNotBlank()) {
                Divider(color = CyberneticOutlineVariant.copy(alpha = 0.2f), thickness = 0.8.dp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenUrl(item.url) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = CyberneticPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = item.url,
                            color = CyberneticPrimary,
                            fontSize = 11.sp,
                            textDecoration = TextDecoration.Underline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(
                            onClick = { onCopyUrl(item.url) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Копировать ссылку",
                                tint = CyberneticOnSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        IconButton(
                            onClick = { onOpenUrl(item.url) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Открыть на OLX",
                                tint = CyberneticSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricSmallCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = CyberneticSurfaceContainer,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = title,
                color = CyberneticOnSurfaceVariant,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                color = color,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
