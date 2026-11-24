package ua.magerko.olxradar.ui.compose

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import ua.magerko.olxradar.data.model.Finding
import ua.magerko.olxradar.data.model.Watch
import ua.magerko.olxradar.ui.FindingsFeedTab
import ua.magerko.olxradar.ui.FindingsSortMode
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindingsFeedScreen(
    findings: List<Finding>,
    rejectedFindings: List<Finding>,
    watches: List<Watch>,
    selectedWatchId: Long?,
    isRejectedTab: Boolean = false,
    onSelectTab: ((Boolean) -> Unit)? = null,
    onFilterByWatch: (Long?) -> Unit,
    onFindingClick: (Finding) -> Unit,
    onDeleteFinding: (Finding) -> Unit,
    onNavigateToSettings: () -> Unit,
    marketPricesByWatch: Map<Long, Int> = emptyMap(),
    queuedFindings: List<Finding> = emptyList(),
    currentFeedTab: FindingsFeedTab = if (isRejectedTab) FindingsFeedTab.REJECTED else FindingsFeedTab.MATCHES,
    onSelectFeedTab: ((FindingsFeedTab) -> Unit)? = null,
    sortMode: FindingsSortMode = FindingsSortMode.NEWEST,
    searchQuery: String = "",
    onSetSortMode: ((FindingsSortMode) -> Unit)? = null,
    onSetSearchQuery: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val effectiveTab = currentFeedTab
    val currentList = when (effectiveTab) {
        FindingsFeedTab.MATCHES -> findings
        FindingsFeedTab.QUEUED -> queuedFindings
        FindingsFeedTab.REJECTED -> rejectedFindings
    }

    var isSearchExpanded by remember { mutableStateOf(searchQuery.isNotBlank()) }
    var showSortMenu by remember { mutableStateOf(false) }

    val selectTabHandler: (FindingsFeedTab) -> Unit = { tab ->
        if (onSelectFeedTab != null) {
            onSelectFeedTab(tab)
        } else {
            onSelectTab?.invoke(tab == FindingsFeedTab.REJECTED)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isGlassTheme) Color.Transparent else CyberneticSurface)
    ) {
        // MODERN TOP APP BAR
        Surface(
            color = if (isGlassTheme) CyberneticSurfaceContainerHigh.copy(alpha = 0.5f) else CyberneticSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Logo + Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CyberneticPrimary.copy(alpha = 0.16f)),
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
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.4).sp
                                )
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(CyberneticSecondary)
                                )
                            }
                            Text(
                                text = when (effectiveTab) {
                                    FindingsFeedTab.MATCHES -> "Найдено: ${findings.size} лотов"
                                    FindingsFeedTab.QUEUED -> "В очереди ИИ: ${queuedFindings.size} лотов"
                                    FindingsFeedTab.REJECTED -> "Отклонено: ${rejectedFindings.size} лотов"
                                },
                                color = when (effectiveTab) {
                                    FindingsFeedTab.MATCHES -> CyberneticSecondary
                                    FindingsFeedTab.QUEUED -> CyberneticTertiary
                                    FindingsFeedTab.REJECTED -> CyberneticOutline
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Top Action Buttons: Search, Sort, Settings
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Search Toggle
                        IconButton(
                            onClick = {
                                isSearchExpanded = !isSearchExpanded
                                if (!isSearchExpanded) {
                                    onSetSearchQuery?.invoke("")
                                }
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Поиск",
                                tint = if (searchQuery.isNotBlank() || isSearchExpanded) CyberneticPrimary else CyberneticOnSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Sort Menu Trigger
                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Sort,
                                    contentDescription = "Сортировка",
                                    tint = if (sortMode != FindingsSortMode.NEWEST) CyberneticPrimary else CyberneticOnSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                modifier = Modifier.background(CyberneticSurfaceContainerHighest)
                            ) {
                                FindingsSortMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (sortMode == mode) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = CyberneticPrimary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                } else {
                                                    Spacer(modifier = Modifier.size(16.dp))
                                                }
                                                Text(
                                                    text = mode.label,
                                                    color = if (sortMode == mode) CyberneticPrimary else CyberneticOnSurface,
                                                    fontWeight = if (sortMode == mode) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        },
                                        onClick = {
                                            onSetSortMode?.invoke(mode)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Settings Icon
                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Настройки",
                                tint = CyberneticOnSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Expandable Search Field
                AnimatedVisibility(
                    visible = isSearchExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { onSetSearchQuery?.invoke(it) },
                        placeholder = { Text("Поиск по заголовку или городу...", fontSize = 13.sp, color = CyberneticOutline) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = CyberneticOutline, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSetSearchQuery?.invoke("") }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Clear, contentDescription = "Очистить", tint = CyberneticOutline, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberneticPrimary,
                            unfocusedBorderColor = CyberneticOutlineVariant.copy(alpha = 0.5f),
                            focusedContainerColor = CyberneticSurfaceContainerLow,
                            unfocusedContainerColor = CyberneticSurfaceContainerLow,
                            focusedTextColor = CyberneticOnSurface,
                            unfocusedTextColor = CyberneticOnSurface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .height(50.dp)
                    )
                }
            }
        }

        // MODERN SEGMENTED TABS: "Подходящие" | "В очереди" | "Отклонённые"
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            color = CyberneticSurfaceContainerLowest
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Tab: Matches
                ModernFeedTabPill(
                    title = "Подходящие",
                    count = findings.size,
                    isSelected = effectiveTab == FindingsFeedTab.MATCHES,
                    activeColor = CyberneticPrimary,
                    modifier = Modifier.weight(1f),
                    onClick = { selectTabHandler(FindingsFeedTab.MATCHES) }
                )

                // Tab: Queued
                ModernFeedTabPill(
                    title = "В очереди",
                    count = queuedFindings.size,
                    isSelected = effectiveTab == FindingsFeedTab.QUEUED,
                    activeColor = CyberneticTertiary,
                    modifier = Modifier.weight(1f),
                    onClick = { selectTabHandler(FindingsFeedTab.QUEUED) }
                )

                // Tab: Rejected
                ModernFeedTabPill(
                    title = "Архив",
                    count = rejectedFindings.size,
                    isSelected = effectiveTab == FindingsFeedTab.REJECTED,
                    activeColor = CyberneticError,
                    modifier = Modifier.weight(1f),
                    onClick = { selectTabHandler(FindingsFeedTab.REJECTED) }
                )
            }
        }

        // HORIZONTAL RADAR FILTER CHIPS
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // "Все радары" Chip
            item {
                val isSelected = selectedWatchId == null
                FilterChip(
                    selected = isSelected,
                    onClick = { onFilterByWatch(null) },
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Все",
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) CyberneticPrimary.copy(alpha = 0.25f) else CyberneticSurfaceContainerHighest
                            ) {
                                Text(
                                    text = "${currentList.size}",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) CyberneticPrimary else CyberneticOnSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberneticPrimary.copy(alpha = 0.16f),
                        selectedLabelColor = CyberneticPrimary,
                        containerColor = CyberneticSurfaceContainerLow,
                        labelColor = CyberneticOnSurfaceVariant
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) CyberneticPrimary.copy(alpha = 0.6f) else CyberneticOutlineVariant.copy(alpha = 0.3f)
                    )
                )
            }

            // Radar chips
            items(watches, key = { it.id }) { watch ->
                val isSelected = selectedWatchId == watch.id
                FilterChip(
                    selected = isSelected,
                    onClick = { onFilterByWatch(watch.id) },
                    label = {
                        Text(
                            text = watch.title.ifBlank { watch.query },
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyberneticPrimary.copy(alpha = 0.16f),
                        selectedLabelColor = CyberneticPrimary,
                        containerColor = CyberneticSurfaceContainerLow,
                        labelColor = CyberneticOnSurfaceVariant
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) CyberneticPrimary.copy(alpha = 0.6f) else CyberneticOutlineVariant.copy(alpha = 0.3f)
                    )
                )
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
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(CyberneticSurfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (effectiveTab) {
                                FindingsFeedTab.MATCHES -> Icons.Default.Radar
                                FindingsFeedTab.QUEUED -> Icons.Default.Schedule
                                FindingsFeedTab.REJECTED -> Icons.Default.FilterAltOff
                            },
                            contentDescription = null,
                            tint = when (effectiveTab) {
                                FindingsFeedTab.MATCHES -> CyberneticPrimary
                                FindingsFeedTab.QUEUED -> CyberneticTertiary
                                FindingsFeedTab.REJECTED -> CyberneticOutline
                            },
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Text(
                        text = when {
                            searchQuery.isNotBlank() -> "Ничего не найдено по запросу «$searchQuery»"
                            effectiveTab == FindingsFeedTab.MATCHES -> {
                                if (selectedWatch != null) "Для «${selectedWatch.title}» пока нет новых находок"
                                else "Пока нет новых находок"
                            }
                            effectiveTab == FindingsFeedTab.QUEUED -> "Очередь ИИ пуста"
                            effectiveTab == FindingsFeedTab.REJECTED -> "Архив отклонённых объявлений пуст"
                            else -> "Список пуст"
                        },
                        color = CyberneticOnSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Text(
                        text = when (effectiveTab) {
                            FindingsFeedTab.MATCHES -> "Фоновый сканер проверяет OLX в автоматическом режиме"
                            FindingsFeedTab.QUEUED -> "Сюда попадают тяжелые объявления, ожидающие анализа нейросетью"
                            FindingsFeedTab.REJECTED -> "Здесь сохраняются предложения, отсеянные по стоп-словам или вердикту ИИ"
                        },
                        color = CyberneticOnSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(currentList, key = { it.id }) { finding ->
                    FindingCard(
                        finding = finding,
                        onClick = { onFindingClick(finding) },
                        onDelete = { onDeleteFinding(finding) },
                        onOpenUrl = { url ->
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
                            }
                        },
                        marketPrice = marketPricesByWatch[finding.watchId]
                    )
                }

                // Footer status indicator
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(CyberneticSecondary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Фоновый сканер OLX активен",
                            color = CyberneticOutline,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Кнопка-пилюля сегментированного переключателя лент.
 */
@Composable
private fun ModernFeedTabPill(
    title: String,
    count: Int,
    isSelected: Boolean,
    activeColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) CyberneticSurfaceContainerHigh else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = if (isSelected) activeColor else CyberneticOnSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(5.dp))
            Surface(
                shape = CircleShape,
                color = if (isSelected) activeColor.copy(alpha = 0.2f) else CyberneticSurfaceContainerHighest
            ) {
                Text(
                    text = "$count",
                    color = if (isSelected) activeColor else CyberneticOnSurfaceVariant,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
    }
}

/**
 * Премиальная карточка находки с правильной валютой, бейджами и фото.
 */
@Composable
fun FindingCard(
    finding: Finding,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onOpenUrl: (String) -> Unit,
    marketPrice: Int? = null
) {
    val isGlass = isGlassTheme

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (isGlass) 0.3f else 0.12f),
                        Color.White.copy(alpha = if (isGlass) 0.05f else 0.02f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGlass) CyberneticSurfaceContainer.copy(alpha = 0.85f) else CyberneticSurfaceContainer
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Photo Container (16:9) with Overlays
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
                            tint = CyberneticOutline.copy(alpha = 0.5f),
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                // Gradient Vignette overlay for contrast
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.45f),
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.65f)
                                )
                            )
                        )
                )

                // Top Left Badges: AI Rating, Condition, Delivery
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // AI Match Badge
                    finding.aiScore?.let { score ->
                        val isHighMatch = score >= 80
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isHighMatch) CyberneticSecondaryContainer else CyberneticSurfaceContainerHighest.copy(alpha = 0.9f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isHighMatch) Icons.Default.Verified else Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (isHighMatch) CyberneticOnSecondaryContainer else CyberneticPrimary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "ИИ $score%",
                                    color = if (isHighMatch) CyberneticOnSecondaryContainer else CyberneticPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // OLX Delivery Badge
                    if (finding.isDelivery) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CyberneticTertiaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalShipping,
                                    contentDescription = null,
                                    tint = CyberneticOnTertiaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Доставка",
                                    color = CyberneticOnTertiaryContainer,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Condition Pulse
                    finding.conditionScore?.let { pulse ->
                        val (label, bgColor) = when {
                            pulse >= 65 -> "Рабочий" to Color(0xFF15803D)
                            pulse <= 35 -> "Дефект" to Color(0xFFDC2626)
                            else -> null to null
                        }
                        if (label != null && bgColor != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = bgColor.copy(alpha = 0.9f)
                            ) {
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                // Top Right: Quick Delete Button
                Surface(
                    onClick = onDelete,
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Удалить",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Bottom Strip over Photo: City and Time
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = finding.cityName.ifBlank { "Украина" },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(13.dp)
                        )
                        val relativeTime = DateUtils.getRelativeTimeSpanString(
                            finding.foundAt,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                            DateUtils.FORMAT_ABBREV_RELATIVE
                        ).toString()
                        Text(
                            text = relativeTime,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Card Body Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Title
                Text(
                    text = finding.title,
                    color = CyberneticOnSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )

                // Price Row with Dynamic Currency Formatting (FIXED BUG)
                val currencySymbol = when (finding.currency?.trim()?.uppercase()) {
                    "USD", "$" -> "$"
                    "EUR", "€" -> "€"
                    "UAH", "ГРН", "ГРН." -> "₴"
                    null, "" -> "₴"
                    else -> finding.currency
                }

                val priceFormatted = if (finding.price != null && finding.price > 0) {
                    val num = NumberFormat.getNumberInstance(Locale("uk", "UA")).format(finding.price)
                    if (currencySymbol == "$" || currencySymbol == "€") "$currencySymbol$num" else "$num $currencySymbol"
                } else {
                    "Договорная"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = priceFormatted,
                            color = CyberneticOnSurface,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        // Market Price Diff Indicator
                        if (marketPrice != null && marketPrice > 0 && finding.price != null && finding.price > 0) {
                            val diffPercent = ((marketPrice - finding.price).toDouble() / marketPrice * 100).toInt()
                            if (diffPercent > 0) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = CyberneticSecondary.copy(alpha = 0.16f),
                                    modifier = Modifier.padding(bottom = 2.dp)
                                ) {
                                    Text(
                                        text = "-$diffPercent% от рынка",
                                        color = CyberneticSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // AI Product Tag if detected
                if (!finding.aiProduct.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CyberneticSurfaceContainerLow,
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberneticOutlineVariant.copy(alpha = 0.25f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = CyberneticPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "${finding.aiProduct}${finding.aiModel?.let { " · $it" } ?: ""}",
                                color = CyberneticOnSurfaceVariant,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Action Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberneticPrimary,
                            contentColor = CyberneticOnPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                    ) {
                        Text(
                            text = "Инспектор лота",
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

                    OutlinedIconButton(
                        onClick = { onOpenUrl(finding.url) },
                        modifier = Modifier.size(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberneticPrimary.copy(alpha = 0.4f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Launch,
                            contentDescription = "OLX",
                            tint = CyberneticPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
