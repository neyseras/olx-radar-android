package ua.magerko.olxradar.ui.compose

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.text.Html
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import ua.magerko.olxradar.ai.AiAnalysisResult
import ua.magerko.olxradar.data.model.ConditionAnalyzer
import ua.magerko.olxradar.data.model.Finding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealInspectorModal(
    finding: Finding,
    watchTitle: String? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    onDismiss: () -> Unit,
    onDelete: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {},
    onRetryAi: () -> Unit = {},
    isRetryingAi: Boolean = false,
    marketPrice: Int? = null
) {
    val context = LocalContext.current
    var isDescExpanded by remember { mutableStateOf(false) }
    var isFavorite by remember { mutableStateOf(false) }

    val localeRu = remember { Locale("ru") }

    val createdDateFormatted = remember(finding.createdAt) {
        if (finding.createdAt > 0) {
            val sdf = SimpleDateFormat("d MMMM yyyy, HH:mm", localeRu)
            sdf.format(Date(finding.createdAt))
        } else {
            "Не указана"
        }
    }

    val foundDateFormatted = remember(finding.foundAt) {
        if (finding.foundAt > 0) {
            val sdf = SimpleDateFormat("d MMMM yyyy, HH:mm:ss", localeRu)
            sdf.format(Date(finding.foundAt))
        } else {
            "Не указано"
        }
    }

    val relativeFoundTime = remember(finding.foundAt) {
        if (finding.foundAt > 0) {
            DateUtils.getRelativeTimeSpanString(
                finding.foundAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            ).toString()
        } else {
            ""
        }
    }

    val parsedAiResult = remember(finding.aiDetailsJson) {
        finding.aiDetailsJson?.let { AiAnalysisResult.fromJson(it) }
    }

    val cleanDesc = remember(finding.description) {
        if (finding.description.isBlank()) {
            "Продавец не указал подробного текстового описания. Нажмите кнопку «Открыть на OLX», чтобы просмотреть полное объявление и связаться с продавцом напрямую."
        } else {
            try {
                val htmlParsed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Html.fromHtml(finding.description, Html.FROM_HTML_MODE_COMPACT).toString()
                } else {
                    @Suppress("DEPRECATION")
                    Html.fromHtml(finding.description).toString()
                }
                htmlParsed.trim()
            } catch (e: Exception) {
                finding.description.replace(Regex("<[^>]*>"), " ").trim()
            }
        }
    }

    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    }

    fun copyToClipboard(text: String, label: String) {
        val clip = ClipData.newPlainText(label, text)
        clipboardManager?.setPrimaryClip(clip)
        Toast.makeText(context, "$label скопировано в буфер", Toast.LENGTH_SHORT).show()
    }

    fun openUrlDirectly() {
        onOpenUrl(finding.url)
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(finding.url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CyberneticSurfaceContainer,
        contentColor = CyberneticOnSurface,
        dragHandle = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(CyberneticOutlineVariant.copy(alpha = 0.6f))
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
        ) {
            // Content scroll area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                // Top telemetry bar: Listing ID + Radar badge + AI status + Close
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CyberneticSurfaceContainerHigh,
                            modifier = Modifier.clickable {
                                copyToClipboard(finding.listingId.toString(), "ID объявления")
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "№ ${finding.listingId}",
                                    color = CyberneticPrimary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Копировать ID",
                                    tint = CyberneticPrimary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        if (!watchTitle.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = CyberneticPrimary.copy(alpha = 0.12f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Radar,
                                        contentDescription = null,
                                        tint = CyberneticPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = watchTitle,
                                        color = CyberneticPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val (statusBadgeText, statusBadgeColor) = when {
                            finding.aiStatus == "rejected" -> "Отклонено ИИ" to CyberneticError
                            finding.aiStatus == "error" -> "Ошибка ИИ" to CyberneticTertiary
                            finding.aiStatus == "analyzing" -> "Анализ ИИ..." to CyberneticPrimary
                            finding.aiScore != null -> "ИИ: ${finding.aiScore}%" to CyberneticSecondary
                            else -> "Активно" to CyberneticSecondary
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = statusBadgeColor.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(statusBadgeColor)
                                )
                                Text(
                                    text = statusBadgeText,
                                    color = statusBadgeColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть",
                                tint = CyberneticOnSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Image Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .clip(RoundedCornerShape(16.dp))
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
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = CyberneticOutline,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "Фото лота отсутствует",
                                    color = CyberneticOnSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Condition Badge overlay
                    if (finding.conditionScore != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CyberneticSurfaceContainerLowest.copy(alpha = 0.88f),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = if (finding.conditionScore >= 60) CyberneticSecondary else CyberneticError,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = ConditionAnalyzer.explainScore(finding.conditionScore),
                                    color = if (finding.conditionScore >= 60) CyberneticSecondary else CyberneticError,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Photo counter chip
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CyberneticSurfaceContainerLowest.copy(alpha = 0.85f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = CyberneticPrimary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "OLX Фото",
                                color = CyberneticOnSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Price and Quick Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val priceFormatted = if (finding.price != null && finding.price > 0) {
                        val formattedNum = NumberFormat.getNumberInstance(Locale("uk", "UA")).format(finding.price)
                        "$formattedNum ${finding.currency ?: "₴"}"
                    } else {
                        "Договорная"
                    }
                    Column {
                        Text(
                            text = priceFormatted,
                            color = CyberneticOnSurface,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
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
                                fontSize = 13.sp,
                                fontWeight = if (diffPercent > 0) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }

                    Button(
                        onClick = { openUrlDirectly() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberneticPrimaryContainer.copy(alpha = 0.25f),
                            contentColor = CyberneticPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = "OLX лот",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Title
                Text(
                    text = finding.title,
                    color = CyberneticOnSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 24.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Location & Meta Info Chips Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = CyberneticPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = finding.cityName.ifBlank { "Вся Украина" },
                        color = CyberneticOnSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = "•",
                        color = CyberneticOutlineVariant
                    )

                    if (relativeFoundTime.isNotBlank()) {
                        Text(
                            text = relativeFoundTime,
                            color = CyberneticOnSurfaceVariant,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "•",
                            color = CyberneticOutlineVariant
                        )
                    }

                    Text(
                        text = if (finding.reason == "pushup") "ТОП / Pushup" else "Свежее",
                        color = if (finding.reason == "pushup") CyberneticTertiary else CyberneticSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Feature chips row (OLX Доставка & Безопасная сделка - только если реально включена доставка)
                if (finding.isDelivery) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CyberneticSurfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalShipping,
                                    contentDescription = null,
                                    tint = CyberneticSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "OLX Доставка",
                                    color = CyberneticOnSurface,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CyberneticSurfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = CyberneticPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Безопасная сделка",
                                    color = CyberneticOnSurface,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                } else {
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // GROQ VISION AI CARD (RICH AI DETAILS OR INFORMATIVE STATUS)
                val hasAiSuccess = finding.aiScore != null || (parsedAiResult != null && parsedAiResult.confidence > 0)
                val isAiRejected = finding.aiStatus == "rejected"
                val isAiError = finding.aiStatus == "error"
                val isAiAnalyzing = finding.aiStatus == "analyzing"

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            when {
                                isAiRejected -> CyberneticError.copy(alpha = 0.5f)
                                isAiError -> CyberneticTertiary.copy(alpha = 0.5f)
                                hasAiSuccess -> CyberneticSecondary.copy(alpha = 0.4f)
                                else -> CyberneticOutlineVariant.copy(alpha = 0.35f)
                            },
                            RoundedCornerShape(16.dp)
                        ),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberneticSurfaceContainerLow)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // AI Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            when {
                                                isAiRejected -> CyberneticError.copy(alpha = 0.15f)
                                                isAiError -> CyberneticTertiary.copy(alpha = 0.15f)
                                                hasAiSuccess -> CyberneticSecondary.copy(alpha = 0.15f)
                                                else -> CyberneticPrimary.copy(alpha = 0.15f)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when {
                                            isAiRejected -> Icons.Default.Warning
                                            isAiError -> Icons.Default.ErrorOutline
                                            hasAiSuccess -> Icons.Default.AutoAwesome
                                            else -> Icons.Default.FilterList
                                        },
                                        contentDescription = null,
                                        tint = when {
                                            isAiRejected -> CyberneticError
                                            isAiError -> CyberneticTertiary
                                            hasAiSuccess -> CyberneticSecondary
                                            else -> CyberneticPrimary
                                        },
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = when {
                                            isAiRejected -> "Отклонено Groq Vision"
                                            isAiError -> "ИИ-анализ: Ошибка проверки API"
                                            isAiAnalyzing -> "Groq Vision: Анализ лота..."
                                            hasAiSuccess -> "Результат проверки Groq Vision"
                                            else -> "Фильтр радара (Базовый)"
                                        },
                                        color = CyberneticOnSurface,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = when {
                                            isAiRejected -> "Не соответствует условиям поиска"
                                            isAiError -> "Сбой связи или исчерпан лимит Groq"
                                            isAiAnalyzing -> "ИИ сопоставляет фото и описание..."
                                            hasAiSuccess -> "Анализ фото, комплектации и соответствия"
                                            else -> "Проверка по ключевым словам и цене"
                                        },
                                        color = CyberneticOnSurfaceVariant,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (hasAiSuccess) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = CyberneticSecondary.copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = CyberneticSecondary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Проверено ИИ",
                                            color = CyberneticSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        // Content branch 1: AI evaluated successfully
                        if (hasAiSuccess) {
                            val score = finding.aiScore ?: parsedAiResult?.confidence ?: 0

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CyberneticSurfaceContainer
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (score >= 70) CyberneticSecondary.copy(alpha = 0.2f)
                                                else CyberneticTertiary.copy(alpha = 0.2f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$score%",
                                            color = if (score >= 70) CyberneticSecondary else CyberneticTertiary,
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (score >= 80) "ВЫСОКАЯ ТОЧНОСТЬ" else "ТРЕБУЕТ ВНИМАНИЯ",
                                            color = if (score >= 80) CyberneticSecondary else CyberneticTertiary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            text = if (score >= 80)
                                                "Товар и фото полностью соответствуют запросу"
                                            else
                                                "Возможны неточности в спецификации или неполная комплектация",
                                            color = CyberneticOnSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            // Detected Product & Model
                            val detectedProduct = finding.aiProduct ?: parsedAiResult?.detectedProduct
                            val detectedModel = finding.aiModel ?: parsedAiResult?.detectedModel

                            if (!detectedProduct.isNullOrBlank() || !detectedModel.isNullOrBlank()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(CyberneticSurfaceContainer.copy(alpha = 0.6f))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (!detectedProduct.isNullOrBlank()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Товар:",
                                                color = CyberneticOnSurfaceVariant,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = detectedProduct,
                                                color = CyberneticOnSurface,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    if (!detectedModel.isNullOrBlank()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "Модель:",
                                                color = CyberneticOnSurfaceVariant,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = detectedModel,
                                                color = CyberneticPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            // Photo match status
                            val photoMatch = finding.aiPhotoMatch ?: parsedAiResult?.photoMatchesTitle
                            if (photoMatch != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (photoMatch) CyberneticSecondary.copy(alpha = 0.1f)
                                            else CyberneticError.copy(alpha = 0.15f)
                                        )
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (photoMatch) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (photoMatch) CyberneticSecondary else CyberneticError,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (photoMatch)
                                            "Фото лота подтверждено: совпадает с описанием"
                                        else
                                            "Внимание: фото лота отличается от описания товара",
                                        color = if (photoMatch) CyberneticSecondary else CyberneticError,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // AI Condition & Completeness
                            val aiCondition = parsedAiResult?.condition
                            val aiCompleteness = parsedAiResult?.completeness
                            if (!aiCondition.isNullOrBlank() || !aiCompleteness.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CyberneticSurfaceContainer.copy(alpha = 0.6f))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FactCheck,
                                        contentDescription = null,
                                        tint = CyberneticPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = buildString {
                                            if (!aiCondition.isNullOrBlank()) append("Состояние: $aiCondition")
                                            if (!aiCondition.isNullOrBlank() && !aiCompleteness.isNullOrBlank()) append(" • ")
                                            if (!aiCompleteness.isNullOrBlank()) append("Комплект: $aiCompleteness")
                                        },
                                        color = CyberneticOnSurface,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Visible defects (if detected by AI)
                            val defects = parsedAiResult?.visibleDefects ?: emptyList()
                            if (defects.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CyberneticError.copy(alpha = 0.15f))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = CyberneticError,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Замеченные дефекты: ${defects.joinToString(", ")}",
                                        color = CyberneticError,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // AI Reasons checklist
                            val reasonsList = remember(finding.aiReasons, parsedAiResult?.reasons) {
                                val src = finding.aiReasons
                                    ?: parsedAiResult?.reasons?.joinToString("\n• ", prefix = "• ")
                                    ?: ""
                                src.split("\n", ";")
                                    .map { it.replace("•", "").trim() }
                                    .filter { it.isNotBlank() }
                            }

                            if (reasonsList.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    reasonsList.forEach { reasonText ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(CyberneticSurfaceContainer.copy(alpha = 0.6f))
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.Top,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = CyberneticSecondary,
                                                modifier = Modifier
                                                    .size(15.dp)
                                                    .padding(top = 1.dp)
                                            )
                                            Text(
                                                text = reasonText,
                                                color = CyberneticOnSurface,
                                                fontSize = 12.sp,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // Reject reason warning (if any)
                            val reject = finding.aiRejectReason ?: parsedAiResult?.rejectReason
                            if (!reject.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CyberneticErrorContainer.copy(alpha = 0.3f))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = CyberneticError,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "Причина отклонения: $reject",
                                        color = CyberneticError,
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        } else if (isAiRejected) {
                            // Content branch 2: AI Rejected
                            val reject = finding.aiRejectReason ?: parsedAiResult?.rejectReason ?: "Не соответствует ключевым критериям радара"
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CyberneticError.copy(alpha = 0.12f))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = null,
                                        tint = CyberneticError,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Объявление отклонено ИИ",
                                        color = CyberneticError,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = reject,
                                    color = CyberneticOnSurface,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        } else if (isAiError) {
                            // Content branch 3: AI Error
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CyberneticTertiary.copy(alpha = 0.12f))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = CyberneticTertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "ИИ-анализ временно недоступен",
                                        color = CyberneticTertiary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "Groq Vision не смог проанализировать объявление (возможно, исчерпан лимит или обновляется модель). Объявление добавлено по базовому совпадению текста и цены.",
                                    color = CyberneticOnSurfaceVariant,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                                Button(
                                    onClick = onRetryAi,
                                    enabled = !isRetryingAi,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CyberneticPrimary,
                                        contentColor = CyberneticOnPrimary
                                    )
                                ) {
                                    if (isRetryingAi) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = CyberneticOnPrimary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Анализируем...", fontSize = 13.sp)
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Повторить ИИ-анализ", fontSize = 13.sp)
                                    }
                                }
                            }
                        } else {
                            // Content branch 4: Baseline Radar Match (AI skipped or not run)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CyberneticSurfaceContainer.copy(alpha = 0.6f))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = CyberneticPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Совпадение по условиям радара",
                                        color = CyberneticPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "Объявление отобрано поисковым радаром по ключевым словам, цене и локации. Глубокий визуальный анализ Groq Vision для этого лота не запускался.",
                                    color = CyberneticOnSurfaceVariant,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // NEW: DETAILED SPECIFICATIONS & PARAMETERS CARD
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyberneticOutlineVariant.copy(alpha = 0.35f), RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberneticSurfaceContainerLow)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = CyberneticPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Характеристики и параметры",
                                color = CyberneticOnSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Param 1: Radar
                        InspectorParamRow(
                            icon = Icons.Default.Radar,
                            label = "Поисковый радар",
                            value = watchTitle ?: "Радар #${finding.watchId}"
                        )

                        // Param 2: OLX ID
                        InspectorParamRow(
                            icon = Icons.Default.Tag,
                            label = "Номер лота OLX",
                            value = finding.listingId.toString(),
                            onCopy = { copyToClipboard(finding.listingId.toString(), "ID объявления") }
                        )

                        // Param 3: Published on OLX
                        InspectorParamRow(
                            icon = Icons.Default.DateRange,
                            label = "Опубликовано",
                            value = createdDateFormatted
                        )

                        // Param 4: Discovered by Radar
                        InspectorParamRow(
                            icon = Icons.Default.Schedule,
                            label = "Поймано радаром",
                            value = "$foundDateFormatted (${relativeFoundTime})"
                        )

                        // Param 5: Location
                        InspectorParamRow(
                            icon = Icons.Default.LocationOn,
                            label = "Локация / Город",
                            value = finding.cityName.ifBlank { "Вся Украина" }
                        )

                        // Param 6: Condition Sensor
                        if (finding.conditionScore != null) {
                            InspectorParamRow(
                                icon = Icons.Default.VerifiedUser,
                                label = "Сенсор состояния",
                                value = "${ConditionAnalyzer.explainScore(finding.conditionScore)} (${finding.conditionScore}%)"
                            )
                        }

                        // Param 7: Reason / Event
                        InspectorParamRow(
                            icon = Icons.Default.TrendingUp,
                            label = "Событие публикации",
                            value = if (finding.reason == "pushup") "Поднято в ТОП (Pushup)" else "Свежее объявление"
                        )

                        // Param 8: Direct Link
                        InspectorParamRow(
                            icon = Icons.Default.Link,
                            label = "Ссылка на OLX",
                            value = finding.url,
                            onCopy = { copyToClipboard(finding.url, "Ссылка на объявление") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Seller Description Box
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = CyberneticSurfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = CyberneticOnSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Описание продавца",
                                    color = CyberneticOnSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Оригинал",
                                color = CyberneticOnSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }

                        Text(
                            text = cleanDesc,
                            color = CyberneticOnSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            maxLines = if (isDescExpanded) Int.MAX_VALUE else 5,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.animateContentSize()
                        )

                        if (cleanDesc.length > 150) {
                            TextButton(
                                onClick = { isDescExpanded = !isDescExpanded },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = if (isDescExpanded) "Свернуть" else "Показать полностью",
                                    color = CyberneticPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    imageVector = if (isDescExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = CyberneticPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }

            // STICKY BOTTOM ACTION BAR
            Surface(
                color = CyberneticSurface.copy(alpha = 0.95f),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Favorite button
                    IconButton(
                        onClick = { isFavorite = !isFavorite },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CyberneticSurfaceContainerHigh)
                            .border(1.dp, CyberneticOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Избранное",
                            tint = if (isFavorite) CyberneticError else CyberneticOnSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Copy Link button
                    IconButton(
                        onClick = {
                            copyToClipboard(finding.url, "Ссылка на объявление")
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CyberneticSurfaceContainerHigh)
                            .border(1.dp, CyberneticOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Скопировать ссылку",
                            tint = CyberneticOnSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Share button
                    IconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, finding.title)
                                putExtra(Intent.EXTRA_TEXT, "${finding.title}\n${finding.url}")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Поделиться объявлением"))
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CyberneticSurfaceContainerHigh)
                            .border(1.dp, CyberneticOutlineVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Поделиться",
                            tint = CyberneticOnSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Delete button
                    IconButton(
                        onClick = {
                            onDelete()
                            onDismiss()
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CyberneticError.copy(alpha = 0.12f))
                            .border(1.dp, CyberneticError.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Удалить находку",
                            tint = CyberneticError,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Open on OLX button
                    Button(
                        onClick = {
                            openUrlDirectly()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberneticPrimary,
                            contentColor = CyberneticOnPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                    ) {
                        Text(
                            text = "Открыть на OLX",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectorParamRow(
    icon: ImageVector,
    label: String,
    value: String,
    onCopy: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CyberneticSurfaceContainer.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(0.42f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = CyberneticPrimary,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                color = CyberneticOnSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Row(
            modifier = Modifier.weight(0.58f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = value,
                color = CyberneticOnSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (onCopy != null) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Скопировать",
                        tint = CyberneticPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
