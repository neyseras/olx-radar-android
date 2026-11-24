package ua.magerko.olxradar.ui.compose

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import kotlinx.coroutines.launch
import ua.magerko.olxradar.data.model.Finding
import ua.magerko.olxradar.data.model.Watch
import ua.magerko.olxradar.service.ServiceStatus
import ua.magerko.olxradar.ui.FindingsFeedTab
import ua.magerko.olxradar.ui.FindingsSortMode
import ua.magerko.olxradar.ui.QuickFeedFilter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    onToggleFavorite: (Finding) -> Unit = {},
    onDeleteFinding: (Finding) -> Unit,
    onNavigateToSettings: () -> Unit,
    marketPricesByWatch: Map<Long, Int> = emptyMap(),
    queuedFindings: List<Finding> = emptyList(),
    currentFeedTab: FindingsFeedTab = if (isRejectedTab) FindingsFeedTab.REJECTED else FindingsFeedTab.MATCHES,
    onSelectFeedTab: ((FindingsFeedTab) -> Unit)? = null,
    sortMode: FindingsSortMode = FindingsSortMode.NEWEST,
    searchQuery: String = "",
    quickFilter: QuickFeedFilter = QuickFeedFilter.ALL,
    compactMode: Boolean = false,
    showMarketDiff: Boolean = true,
    reduceAnimations: Boolean = false,
    radarStatus: ServiceStatus = ServiceStatus(),
    onTogglePause: (() -> Unit)? = null,
    onSetSortMode: ((FindingsSortMode) -> Unit)? = null,
    onSetSearchQuery: ((String) -> Unit)? = null,
    onSetQuickFilter: ((QuickFeedFilter) -> Unit)? = null,
    onRestoreFinding: ((Finding) -> Unit)? = null,
    unreadCount: Int = 0,
    onMarkAllRead: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val effectiveTab = currentFeedTab
    val currentList = when (effectiveTab) {
        FindingsFeedTab.MATCHES -> findings
        FindingsFeedTab.QUEUED -> queuedFindings
        FindingsFeedTab.REJECTED -> rejectedFindings
    }

    var isSearchExpanded by remember { mutableStateOf(searchQuery.isNotBlank()) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Общий таймер для относительного времени (1 раз в 30с вместо вызова на каждый кадр скролла)
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000L)
            nowMs = System.currentTimeMillis()
        }
    }

    val selectTabHandler: (FindingsFeedTab) -> Unit = { tab ->
        if (onSelectFeedTab != null) {
            onSelectFeedTab(tab)
        } else {
            onSelectTab?.invoke(tab == FindingsFeedTab.REJECTED)
        }
    }

    val openListingUrl: (String) -> Unit = remember(context) {
        { url ->
            if (url.isBlank()) {
                Toast.makeText(context, "Ссылка отсутствует", Toast.LENGTH_SHORT).show()
            } else {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
        ) {
        // MODERN TOP BAR
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = CyberneticPrimary.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, CyberneticPrimary.copy(alpha = 0.25f)),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Radar,
                                contentDescription = null,
                                tint = CyberneticPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "OLX Радар",
                            color = CyberneticOnSurface,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.4).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val isPaused = radarStatus.isPaused
                            val isActive = radarStatus.isRunning && !isPaused
                            val statusColor = when {
                                isPaused -> CyberneticTertiary
                                isActive -> CyberneticSecondary
                                else -> CyberneticOutline
                            }
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = statusColor.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
                                modifier = Modifier.clickable(enabled = onTogglePause != null) {
                                    onTogglePause?.invoke()
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    StatusPulseDot(
                                        color = statusColor,
                                        isPulsing = isActive,
                                        reduceAnimations = reduceAnimations,
                                        size = 6.dp
                                    )
                                    Text(
                                        text = when {
                                            isPaused -> "Пауза"
                                            isActive -> "Скан"
                                            else -> "Стоп"
                                        },
                                        color = statusColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            Text(
                                text = when (effectiveTab) {
                                    FindingsFeedTab.MATCHES -> "Подходящих: ${findings.size}"
                                    FindingsFeedTab.QUEUED -> "Очередь: ${queuedFindings.size}"
                                    FindingsFeedTab.REJECTED -> "В архиве: ${rejectedFindings.size}"
                                },
                                color = CyberneticOnSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Top Action Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    IconButton(
                        onClick = {
                            isSearchExpanded = !isSearchExpanded
                            if (!isSearchExpanded) {
                                onSetSearchQuery?.invoke("")
                            }
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Поиск",
                            tint = if (searchQuery.isNotBlank() || isSearchExpanded) CyberneticPrimary else CyberneticOnSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.size(34.dp)
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
                            modifier = Modifier.background(CyberneticModalSurface)
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

                    if (onMarkAllRead != null && effectiveTab == FindingsFeedTab.MATCHES) {
                        IconButton(
                            onClick = {
                                onMarkAllRead()
                                Toast.makeText(context, "Все находки прочитаны", Toast.LENGTH_SHORT).show()
                            },
                            enabled = unreadCount > 0,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = "Прочитать все",
                                tint = if (unreadCount > 0) CyberneticSecondary else CyberneticOutline,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.size(34.dp)
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
                    placeholder = {
                        Text(
                            "Поиск по заголовку, модели или городу...",
                            fontSize = 13.sp,
                            color = CyberneticOutline
                        )
                    },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = CyberneticOutline,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { onSetSearchQuery?.invoke("") },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Очистить",
                                    tint = CyberneticOutline,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberneticPrimary,
                        unfocusedBorderColor = CyberneticCardBorder,
                        focusedContainerColor = CyberneticSurfaceContainer,
                        unfocusedContainerColor = CyberneticSurfaceContainerLow,
                        focusedTextColor = CyberneticOnSurface,
                        unfocusedTextColor = CyberneticOnSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .heightIn(min = 50.dp)
                )
            }
        }

        // SEGMENTED FEED TABS: "Подходящие" | "В очереди" | "Архив"
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            color = CyberneticSurfaceContainerLow,
            border = BorderStroke(1.dp, CyberneticCardBorderSoft)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ModernFeedTabPill(
                    title = "Подходящие",
                    count = findings.size,
                    isSelected = effectiveTab == FindingsFeedTab.MATCHES,
                    activeColor = CyberneticPrimary,
                    modifier = Modifier.weight(1f),
                    onClick = { selectTabHandler(FindingsFeedTab.MATCHES) }
                )

                ModernFeedTabPill(
                    title = "Очередь",
                    count = queuedFindings.size,
                    isSelected = effectiveTab == FindingsFeedTab.QUEUED,
                    activeColor = CyberneticTertiary,
                    modifier = Modifier.weight(1f),
                    onClick = { selectTabHandler(FindingsFeedTab.QUEUED) }
                )

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

        // QUICK FILTERS + RADAR FILTER CHIPS
        val quickFilters = remember { QuickFeedFilter.entries.filter { it != QuickFeedFilter.ALL } }
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (onSetQuickFilter != null && effectiveTab != FindingsFeedTab.QUEUED) {
                items(quickFilters, key = { "qf_${it.name}" }) { qf ->
                    val isSelected = quickFilter == qf
                    val chipAccent = when (qf) {
                        QuickFeedFilter.FAVORITES -> CyberneticError
                        QuickFeedFilter.DELIVERY -> CyberneticTertiary
                        QuickFeedFilter.HOT_DEALS -> CyberneticSecondary
                        QuickFeedFilter.ALL -> CyberneticPrimary
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSetQuickFilter(if (isSelected) QuickFeedFilter.ALL else qf) },
                        leadingIcon = {
                            Icon(
                                imageVector = when (qf) {
                                    QuickFeedFilter.FAVORITES -> Icons.Default.Favorite
                                    QuickFeedFilter.DELIVERY -> Icons.Default.LocalShipping
                                    QuickFeedFilter.HOT_DEALS -> Icons.Default.LocalFireDepartment
                                    QuickFeedFilter.ALL -> Icons.Default.FilterList
                                },
                                contentDescription = null,
                                tint = if (isSelected) chipAccent else CyberneticOnSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        label = {
                            Text(
                                text = qf.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = chipAccent.copy(alpha = 0.16f),
                            selectedLabelColor = chipAccent,
                            containerColor = CyberneticSurfaceContainerLow,
                            labelColor = CyberneticOnSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = CyberneticCardBorderSoft,
                            selectedBorderColor = chipAccent.copy(alpha = 0.55f)
                        )
                    )
                }
            }

            item(key = "watch_all") {
                val isSelected = selectedWatchId == null
                FilterChip(
                    selected = isSelected,
                    onClick = { onFilterByWatch(null) },
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "Все радары",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                            Surface(
                                shape = CircleShape,
                                color = if (isSelected) CyberneticPrimary.copy(alpha = 0.22f) else CyberneticSurfaceContainerHigh
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
                        borderColor = CyberneticCardBorderSoft,
                        selectedBorderColor = CyberneticPrimary.copy(alpha = 0.55f)
                    )
                )
            }

            items(watches, key = { "watch_${it.id}" }) { watch ->
                val isSelected = selectedWatchId == watch.id
                FilterChip(
                    selected = isSelected,
                    onClick = { onFilterByWatch(watch.id) },
                    label = {
                        Text(
                            text = watch.title.ifBlank { watch.query },
                            fontSize = 11.sp,
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
                        borderColor = CyberneticCardBorderSoft,
                        selectedBorderColor = CyberneticPrimary.copy(alpha = 0.55f)
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
                    Surface(
                        modifier = Modifier.size(76.dp),
                        shape = CircleShape,
                        color = CyberneticSurfaceContainer,
                        border = BorderStroke(1.dp, CyberneticCardBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when {
                                    quickFilter == QuickFeedFilter.FAVORITES -> Icons.Default.FavoriteBorder
                                    effectiveTab == FindingsFeedTab.MATCHES -> Icons.Default.Radar
                                    effectiveTab == FindingsFeedTab.QUEUED -> Icons.Default.Schedule
                                    else -> Icons.Default.FilterAltOff
                                },
                                contentDescription = null,
                                tint = when (effectiveTab) {
                                    FindingsFeedTab.MATCHES -> CyberneticPrimary
                                    FindingsFeedTab.QUEUED -> CyberneticTertiary
                                    FindingsFeedTab.REJECTED -> CyberneticOutline
                                },
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    Text(
                        text = when {
                            searchQuery.isNotBlank() -> "Ничего не найдено по запросу «$searchQuery»"
                            quickFilter != QuickFeedFilter.ALL -> "Нет лотов под фильтр «${quickFilter.label}»"
                            effectiveTab == FindingsFeedTab.MATCHES -> {
                                if (selectedWatch != null) "Для «${selectedWatch.title}» пока нет находок"
                                else "Лента находок пока пуста"
                            }
                            effectiveTab == FindingsFeedTab.QUEUED -> "Очередь ИИ пуста"
                            else -> "Архив отклонённых объявлений пуст"
                        },
                        color = CyberneticOnSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Text(
                        text = when (effectiveTab) {
                            FindingsFeedTab.MATCHES -> "Радар сканирует OLX автоматически. Новые выгодные предложения появятся здесь мгновенно."
                            FindingsFeedTab.QUEUED -> "Сюда попадают лоты, чей ИИ-анализ отложен в фоновую очередь."
                            FindingsFeedTab.REJECTED -> "Здесь сохраняются предложения, отсеянные фильтрами или ИИ."
                        },
                        color = CyberneticOnSurfaceVariant,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 17.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 6.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (compactMode) 10.dp else 14.dp)
            ) {
                items(
                    items = currentList,
                    key = { it.id },
                    contentType = { if (compactMode) "finding_compact" else "finding_rich" }
                ) { finding ->
                    val currentFinding by rememberUpdatedState(finding)
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            when (value) {
                                SwipeToDismissBoxValue.StartToEnd -> {
                                    onToggleFavorite(currentFinding)
                                    false
                                }
                                SwipeToDismissBoxValue.EndToStart -> {
                                    val removed = currentFinding
                                    onDeleteFinding(removed)
                                    if (onRestoreFinding != null) {
                                        scope.launch {
                                            snackbarHostState.currentSnackbarData?.dismiss()
                                            val res = snackbarHostState.showSnackbar(
                                                message = "Удалено: ${removed.title.take(28)}",
                                                actionLabel = "Отменить",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (res == SnackbarResult.ActionPerformed) {
                                                onRestoreFinding(removed)
                                            }
                                        }
                                    }
                                    true
                                }
                                else -> false
                            }
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            val dir = dismissState.dismissDirection
                            val bgColor = when (dir) {
                                SwipeToDismissBoxValue.StartToEnd -> CyberneticError.copy(alpha = 0.18f)
                                SwipeToDismissBoxValue.EndToStart -> CyberneticOutlineVariant.copy(alpha = 0.25f)
                                else -> Color.Transparent
                            }
                            Surface(
                                shape = RoundedCornerShape(if (compactMode) 16.dp else 20.dp),
                                color = bgColor,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 20.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (dir == SwipeToDismissBoxValue.StartToEnd) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(
                                                imageVector = if (finding.isFavorite) Icons.Default.HeartBroken else Icons.Default.Favorite,
                                                contentDescription = null,
                                                tint = CyberneticError
                                            )
                                            Text(
                                                text = if (finding.isFavorite) "Убрать из избранного" else "В избранное",
                                                color = CyberneticError,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.width(1.dp))
                                    }
                                    if (dir == SwipeToDismissBoxValue.EndToStart) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = "Удалить",
                                                color = CyberneticError,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = null,
                                                tint = CyberneticError
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    ) {
                        if (compactMode) {
                            CompactFindingCard(
                                finding = finding,
                                nowMs = nowMs,
                                showMarketDiff = showMarketDiff,
                                marketPrice = marketPricesByWatch[finding.watchId],
                                onClick = { onFindingClick(finding) },
                                onToggleFavorite = { onToggleFavorite(finding) },
                                onDelete = { onDeleteFinding(finding) },
                                onOpenUrl = openListingUrl
                            )
                        } else {
                            FindingCard(
                                finding = finding,
                                nowMs = nowMs,
                                showMarketDiff = showMarketDiff,
                                onClick = { onFindingClick(finding) },
                                onToggleFavorite = { onToggleFavorite(finding) },
                                onDelete = { onDeleteFinding(finding) },
                                onOpenUrl = openListingUrl,
                                marketPrice = marketPricesByWatch[finding.watchId]
                            )
                        }
                    }
                }

                item(key = "feed_footer", contentType = "footer") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPulseDot(
                            color = if (radarStatus.isPaused) CyberneticTertiary else CyberneticSecondary,
                            isPulsing = radarStatus.isRunning && !radarStatus.isPaused,
                            reduceAnimations = reduceAnimations,
                            size = 6.dp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (radarStatus.isPaused) "Сканер OLX на паузе" else "Фоновый сканер OLX активен",
                            color = CyberneticOutline,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}

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
        color = if (isSelected) CyberneticSurfaceContainerHigh else Color.Transparent,
        border = if (isSelected) BorderStroke(1.dp, activeColor.copy(alpha = 0.25f)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = if (isSelected) activeColor else CyberneticOnSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Surface(
                shape = CircleShape,
                color = if (isSelected) activeColor.copy(alpha = 0.18f) else CyberneticSurfaceContainerHighest.copy(alpha = 0.7f)
            ) {
                Text(
                    text = "$count",
                    color = if (isSelected) activeColor else CyberneticOnSurfaceVariant,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
    }
}

/**
 * Премиальная карточка находки (полный режим с фото, рейтингом сделки, скидкой и быстрыми действиями).
 */
@Composable
fun FindingCard(
    finding: Finding,
    nowMs: Long = System.currentTimeMillis(),
    showMarketDiff: Boolean = true,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    onDelete: () -> Unit,
    onOpenUrl: (String) -> Unit,
    marketPrice: Int? = null
) {
    val context = LocalContext.current
    val priceFormatted = remember(finding.price, finding.currency) {
        CachedFormatters.formatPrice(finding.price, finding.currency)
    }
    val oldPriceFormatted = remember(finding.oldPrice, finding.currency) {
        finding.oldPrice?.let { CachedFormatters.formatPrice(it, finding.currency) }
    }
    val relativeTime = remember(finding.foundAt, nowMs) {
        CachedFormatters.formatRelativeTime(finding.foundAt, nowMs)
    }

    val imageRequest = remember(finding.photoUrl) {
        if (!finding.photoUrl.isNullOrBlank()) {
            ImageRequest.Builder(context)
                .data(finding.photoUrl)
                .size(720, 440)
                .precision(Precision.INEXACT)
                .crossfade(true)
                .build()
        } else null
    }

    val scrimBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.42f),
                Color.Transparent,
                Color.Black.copy(alpha = 0.68f)
            )
        )
    }

    RadarSurfaceCard(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        borderColor = if (finding.isFavorite) CyberneticError.copy(alpha = 0.45f) else CyberneticCardBorder,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Photo Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .background(CyberneticSurfaceContainerLowest)
            ) {
                if (imageRequest != null) {
                    AsyncImage(
                        model = imageRequest,
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
                            tint = CyberneticOutline.copy(alpha = 0.45f),
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(scrimBrush)
                )

                // Top Left Badges
                FlowRow(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .fillMaxWidth(0.78f),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (finding.reason == "price_drop" && finding.oldPrice != null && finding.price != null && finding.oldPrice > finding.price) {
                        val dropPct = (((finding.oldPrice - finding.price).toDouble() / finding.oldPrice) * 100).toInt()
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF059669)
                        ) {
                            Text(
                                text = "↓ -$dropPct%",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    finding.dealScore?.let { ds ->
                        if (ds >= 65) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.Black.copy(alpha = 0.74f),
                                border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.75f))
                            ) {
                                Text(
                                    text = "★ $ds",
                                    color = Color(0xFF34D399),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    finding.aiScore?.let { score ->
                        val isHighMatch = score >= 80
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isHighMatch) Color(0xFF065F46).copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.74f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = if (isHighMatch) Icons.Default.Verified else Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (isHighMatch) Color(0xFFA7F3D0) else Color(0xFF38BDF8),
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "ИИ $score%",
                                    color = if (isHighMatch) Color(0xFFA7F3D0) else Color(0xFF38BDF8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    if (finding.isDelivery) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CyberneticTertiaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalShipping,
                                contentDescription = "OLX Доставка",
                                tint = CyberneticOnTertiaryContainer,
                                modifier = Modifier
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                                    .size(12.dp)
                            )
                        }
                    }
                }

                // Top Right: Favorite + Quick Delete
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        onClick = onToggleFavorite,
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (finding.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Избранное",
                                tint = if (finding.isFavorite) Color(0xFFFF5252) else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = onDelete,
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier.size(32.dp)
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
                }

                // Bottom Strip over Photo: City, Condition and Time
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = finding.cityName.ifBlank { "Украина" },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        finding.conditionScore?.let { pulse ->
                            val (label, bgColor) = when {
                                pulse >= 65 -> "Рабочий" to Color(0xFF15803D)
                                pulse <= 35 -> "Дефект" to Color(0xFFDC2626)
                                else -> null to null
                            }
                            if (label != null && bgColor != null) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = bgColor.copy(alpha = 0.92f)
                                ) {
                                    Text(
                                        text = label,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        if (relativeTime.isNotBlank()) {
                            Text(
                                text = relativeTime,
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Card Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = finding.title,
                    color = CyberneticOnSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = priceFormatted,
                            color = CyberneticOnSurface,
                            fontSize = 20.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold
                        )

                        if (oldPriceFormatted != null) {
                            Text(
                                text = oldPriceFormatted,
                                color = CyberneticOutline,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                textDecoration = TextDecoration.LineThrough,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }

                        if (showMarketDiff && marketPrice != null && marketPrice > 0 && finding.price != null && finding.price > 0) {
                            val diffPercent = ((marketPrice - finding.price).toDouble() / marketPrice * 100).toInt()
                            if (diffPercent > 0) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = CyberneticSecondary.copy(alpha = 0.16f),
                                    modifier = Modifier.padding(bottom = 2.dp)
                                ) {
                                    Text(
                                        text = "-$diffPercent% от рынка",
                                        color = CyberneticSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (!finding.isRead) {
                        Surface(
                            shape = CircleShape,
                            color = CyberneticPrimary.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "NEW",
                                color = CyberneticPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (!finding.aiProduct.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CyberneticSurfaceContainerLow,
                        border = BorderStroke(1.dp, CyberneticCardBorderSoft),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = CyberneticPrimary,
                                modifier = Modifier.size(14.dp)
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

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
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
                            .height(40.dp)
                    ) {
                        Text(
                            text = "Инспектор лота",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    OutlinedIconButton(
                        onClick = { onOpenUrl(finding.url) },
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CyberneticPrimary.copy(alpha = 0.45f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Открыть на OLX",
                            tint = CyberneticPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Компактная карточка находки для плотного сканирования ленты.
 */
@Composable
fun CompactFindingCard(
    finding: Finding,
    nowMs: Long = System.currentTimeMillis(),
    showMarketDiff: Boolean = true,
    marketPrice: Int? = null,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val context = LocalContext.current
    val priceFormatted = remember(finding.price, finding.currency) {
        CachedFormatters.formatPrice(finding.price, finding.currency)
    }
    val relativeTime = remember(finding.foundAt, nowMs) {
        CachedFormatters.formatRelativeTime(finding.foundAt, nowMs)
    }
    val imageRequest = remember(finding.photoUrl) {
        if (!finding.photoUrl.isNullOrBlank()) {
            ImageRequest.Builder(context)
                .data(finding.photoUrl)
                .size(220, 220)
                .precision(Precision.INEXACT)
                .crossfade(true)
                .build()
        } else null
    }

    RadarSurfaceCard(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        borderColor = if (finding.isFavorite) CyberneticError.copy(alpha = 0.45f) else CyberneticCardBorder,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyberneticSurfaceContainerLowest)
            ) {
                if (imageRequest != null) {
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = finding.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = CyberneticOutline.copy(alpha = 0.4f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                finding.dealScore?.let { ds ->
                    if (ds >= 65) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.78f),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                        ) {
                            Text(
                                text = "★$ds",
                                color = Color(0xFF34D399),
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = finding.title,
                    color = CyberneticOnSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = priceFormatted,
                        color = CyberneticPrimary,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold
                    )

                    if (showMarketDiff && marketPrice != null && marketPrice > 0 && finding.price != null && finding.price > 0) {
                        val diffPct = ((marketPrice - finding.price).toDouble() / marketPrice * 100).toInt()
                        if (diffPct > 0) {
                            Text(
                                text = "-$diffPct%",
                                color = CyberneticSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (finding.isDelivery) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = "Доставка",
                            tint = CyberneticTertiary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Text(
                    text = "${finding.cityName.ifBlank { "Украина" }} · $relativeTime",
                    color = CyberneticOnSurfaceVariant,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (finding.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Избранное",
                        tint = if (finding.isFavorite) CyberneticError else CyberneticOnSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = { onOpenUrl(finding.url) }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.OpenInNew,
                        contentDescription = "OLX",
                        tint = CyberneticPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Удалить",
                        tint = CyberneticOutline,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}
