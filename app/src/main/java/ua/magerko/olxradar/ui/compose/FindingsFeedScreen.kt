package ua.magerko.olxradar.ui.compose

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import ua.magerko.olxradar.data.model.Finding
import ua.magerko.olxradar.data.model.Watch
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindingsFeedScreen(
    findings: List<Finding>,
    rejectedFindings: List<Finding>,
    watches: List<Watch>,
    selectedWatchId: Long?,
    isRejectedTab: Boolean,
    onSelectTab: (Boolean) -> Unit,
    onFilterByWatch: (Long?) -> Unit,
    onFindingClick: (Finding) -> Unit,
    onDeleteFinding: (Finding) -> Unit,
    onNavigateToSettings: () -> Unit,
    marketPricesByWatch: Map<Long, Int> = emptyMap()
) {
    val context = LocalContext.current
    val currentList = if (isRejectedTab) rejectedFindings else findings

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isGlassTheme) Color.Transparent else CyberneticSurface)
    ) {
        // TOP HEADER
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
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(CyberneticPrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = null,
                        tint = CyberneticPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "OLX Радар",
                            color = CyberneticOnSurface,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(CyberneticSecondary)
                        )
                    }
                    Text(
                        text = "Фоновый поиск активен · ${currentList.size} найдено",
                        color = CyberneticSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Настройки",
                    tint = CyberneticOnSurface,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // SEGMENTED TAB SELECTOR: "Подходящие" vs "Отфильтрованные"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CyberneticSurfaceContainerLowest)
                .padding(4.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                // Tab: Matches
                Surface(
                    onClick = { onSelectTab(false) },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = if (!isRejectedTab) CyberneticSurfaceContainerHigh else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Подходящие",
                            color = if (!isRejectedTab) CyberneticPrimary else CyberneticOnSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (!isRejectedTab) CyberneticPrimary.copy(alpha = 0.2f) else CyberneticSurfaceContainerHighest)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${findings.size}",
                                color = if (!isRejectedTab) CyberneticPrimary else CyberneticOnSurfaceVariant,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Tab: Filtered / Rejected
                Surface(
                    onClick = { onSelectTab(true) },
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = if (isRejectedTab) CyberneticSurfaceContainerHigh else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Отфильтрованные",
                            color = if (isRejectedTab) CyberneticPrimary else CyberneticOnSurfaceVariant,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isRejectedTab) CyberneticPrimary.copy(alpha = 0.2f) else CyberneticSurfaceContainerHighest)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${rejectedFindings.size}",
                                color = if (isRejectedTab) CyberneticPrimary else CyberneticOnSurfaceVariant,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // HORIZONTAL RADAR FILTER CHIPS
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // "Все" chip
            item {
                val isSelected = selectedWatchId == null
                Surface(
                    onClick = { onFilterByWatch(null) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) CyberneticPrimary else CyberneticSurfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Все",
                            color = if (isSelected) CyberneticOnPrimary else CyberneticOnSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isSelected) CyberneticOnPrimary.copy(alpha = 0.2f) else CyberneticSurfaceContainerHighest)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "${currentList.size}",
                                color = if (isSelected) CyberneticOnPrimary else CyberneticSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Radar chips
            items(watches) { watch ->
                val isSelected = selectedWatchId == watch.id
                Surface(
                    onClick = { onFilterByWatch(watch.id) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) CyberneticPrimary else CyberneticSurfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = watch.title,
                            color = if (isSelected) CyberneticOnPrimary else CyberneticOnSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // DEALS FEED LIST
        if (currentList.isEmpty()) {
            val selectedWatch = watches.find { it.id == selectedWatchId }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Icon(
                        imageVector = if (isRejectedTab) Icons.Default.FilterAltOff else Icons.Default.Radar,
                        contentDescription = null,
                        tint = CyberneticOutline,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = when {
                            isRejectedTab && selectedWatch != null -> "Нет отклоненных для «${selectedWatch.title}»"
                            isRejectedTab -> "Нет отклоненных объявлений"
                            selectedWatch != null -> "Для «${selectedWatch.title}» пока нет находок"
                            else -> "Пока нет новых находок"
                        },
                        color = CyberneticOnSurfaceVariant,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Радар продолжает сканирование в фоновом режиме",
                        color = CyberneticOutline,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            AdaptiveFindingsList(
                findings = currentList,
                onFindingClick = onFindingClick,
                onDeleteFinding = onDeleteFinding,
                onOpenUrl = { url ->
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
                        ua.magerko.olxradar.util.AppLog.w("FindingsScreen", "Не удалось открыть ссылку: $url", e)
                    }
                },
                marketPricesByWatch = marketPricesByWatch
            )
        }
    }
}

@Composable
fun AdaptiveFindingsList(
    findings: List<Finding>,
    onFindingClick: (Finding) -> Unit,
    onDeleteFinding: (Finding) -> Unit,
    onOpenUrl: (String) -> Unit,
    marketPricesByWatch: Map<Long, Int> = emptyMap()
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(findings, key = { it.id }) { finding ->
                FindingCard(
                    finding = finding,
                    onClick = { onFindingClick(finding) },
                    onDelete = { onDeleteFinding(finding) },
                    onOpenUrl = onOpenUrl,
                    marketPrice = marketPricesByWatch[finding.watchId]
                )
            }

            // Status Footer
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(CyberneticSecondary)
                        )
                        Text(
                            text = "Фоновый сканер OLX активен",
                            color = CyberneticOnSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                    Text(
                        text = "Обновление базы каждые 20-30 секунд",
                        color = CyberneticOutline,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun FindingCard(
    finding: Finding,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onOpenUrl: (String) -> Unit,
    marketPrice: Int? = null
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberneticOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CyberneticSurfaceContainer)
    ) {
        Column {
            // Photo Banner with badges
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(CyberneticSurfaceContainerLowest)
            ) {
                if (!finding.photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = finding.photoUrl,
                        contentDescription = finding.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = CyberneticOutline,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                // Delete button in top overlay
                Surface(
                    onClick = onDelete,
                    shape = CircleShape,
                    color = CyberneticSurfaceContainerLowest.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Удалить находку",
                            tint = CyberneticError,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Top Badges (с отступом справа, чтобы не наползать на кнопку удаления и не ломать текст вертикально)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 10.dp, top = 10.dp, end = 52.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    finding.aiScore?.let { score ->
                        val isBargain = score >= 80
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isBargain) CyberneticSecondaryContainer.copy(alpha = 0.95f) else CyberneticSurfaceContainerLowest.copy(alpha = 0.85f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                if (isBargain) {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = null,
                                        tint = CyberneticOnSecondaryContainer,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                                Text(
                                    text = if (isBargain) "Выгодная цена ($score%)" else "ИИ: $score%",
                                    color = if (isBargain) CyberneticOnSecondaryContainer else CyberneticPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    // Пульс состояния (Condition Sensor)
                    finding.conditionScore?.let { pulse ->
                        val (pulseColor, pulseLabel) = when {
                            pulse >= 60 -> Color(0xFF2E7D32) to "Рабочий"
                            pulse <= 40 -> Color(0xFFC62828) to "Сломан?"
                            else -> Color(0xFF8D6E63) to "Неясно"
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = pulseColor.copy(alpha = 0.85f)
                        ) {
                            Text(
                                text = "Пульс $pulseLabel",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Bottom Meta Strip over image
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CyberneticSurfaceContainerLowest.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = CyberneticPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            val timeAgo = DateUtils.getRelativeTimeSpanString(
                                finding.foundAt,
                                System.currentTimeMillis(),
                                DateUtils.MINUTE_IN_MILLIS,
                                DateUtils.FORMAT_ABBREV_RELATIVE
                            ).toString()
                            Text(
                                text = timeAgo,
                                color = CyberneticOnSurface,
                                fontSize = 11.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = CyberneticOutline,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = finding.cityName.ifBlank { "Украина" },
                                color = CyberneticOnSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Card Body
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Title
                Text(
                    text = finding.title,
                    color = CyberneticOnSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )

                // Price and Market Row (реальный расчет рынка по медиане цен радара)
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val priceFormatted = if (finding.price != null && finding.price > 0) {
                        "${NumberFormat.getNumberInstance(Locale("uk", "UA")).format(finding.price)} ₴"
                    } else {
                        "Договорная"
                    }
                    Text(
                        text = priceFormatted,
                        color = CyberneticOnSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        softWrap = false
                    )
                    if (marketPrice != null && marketPrice > 0 && finding.price != null && finding.price > 0) {
                        val diffPercent = ((marketPrice - finding.price).toDouble() / marketPrice * 100).toInt()
                        val marketText = if (diffPercent > 0) {
                            "Рынок: ~${NumberFormat.getNumberInstance(Locale("uk", "UA")).format(marketPrice)} ₴ (-$diffPercent%)"
                        } else {
                            "Рынок: ~${NumberFormat.getNumberInstance(Locale("uk", "UA")).format(marketPrice)} ₴"
                        }
                        Text(
                            text = marketText,
                            color = if (diffPercent > 0) CyberneticSecondary else CyberneticOnSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = if (diffPercent > 0) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }

                // Groq Vision Inspection Box (only when AI analysis exists or has reasons)
                val reasonsList = remember(finding.aiReasons, finding.aiProduct) {
                    if (!finding.aiReasons.isNullOrBlank()) {
                        finding.aiReasons.split("\n", ";").map { it.replace("•", "").trim() }.filter { it.isNotBlank() }.take(3)
                    } else if (!finding.aiProduct.isNullOrBlank()) {
                        listOf(finding.aiProduct.trim())
                    } else {
                        emptyList()
                    }
                }

                if (finding.aiStatus != null || reasonsList.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CyberneticSurfaceContainerLow,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberneticOutlineVariant.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = CyberneticPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Проверка фото (Groq Vision):",
                                    color = CyberneticPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (reasonsList.isNotEmpty()) {
                                reasonsList.forEach { reason ->
                                    Text(
                                        text = "• ${reason.trim()}",
                                        color = CyberneticOnSurfaceVariant,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else {
                                Text(
                                    text = when (finding.aiStatus) {
                                        "queued" -> "• В очереди ИИ (> 2 мин)"
                                        "analyzing" -> "• ИИ анализирует лот..."
                                        else -> "• Анализ завершён (${finding.aiStatus})"
                                    },
                                    color = if (finding.aiStatus == "queued") CyberneticTertiary else CyberneticOnSurfaceVariant,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // CTA Row: OLX Button + Delete Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberneticPrimary,
                            contentColor = CyberneticOnPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        Text(
                            text = "Инспектор сделки / OLX",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    FilledTonalIconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(42.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = CyberneticError.copy(alpha = 0.15f),
                            contentColor = CyberneticError
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Удалить находку",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
