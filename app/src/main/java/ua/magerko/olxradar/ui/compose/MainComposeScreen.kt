package ua.magerko.olxradar.ui.compose

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ua.magerko.olxradar.data.backup.PersistentStorageManager
import ua.magerko.olxradar.data.backup.RadarBackup
import ua.magerko.olxradar.data.model.Finding
import ua.magerko.olxradar.ui.FindingsViewModel
import ua.magerko.olxradar.ui.StatusViewModel
import ua.magerko.olxradar.ui.WatchesViewModel
import ua.magerko.olxradar.util.AppLog

enum class NavigationTab(val title: String, val icon: ImageVector) {
    FINDINGS("Находки", Icons.Default.Radar),
    REJECTED("Архив", Icons.Default.FilterAltOff),
    RADARS("Радары", Icons.Default.TrackChanges),
    AI_LIVE("ИИ-поток", Icons.Default.AutoAwesome),
    SETTINGS("Настройки", Icons.Default.Tune)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainComposeScreen(
    findingsViewModel: FindingsViewModel,
    watchesViewModel: WatchesViewModel,
    statusViewModel: StatusViewModel,
    initialTab: NavigationTab = NavigationTab.FINDINGS,
    initialFindingToOpen: Finding? = null,
    onClearInitialFinding: (() -> Unit)? = null,
    initialSharedOlxUrl: String? = null,
    onClearSharedOlxUrl: (() -> Unit)? = null
) {
    var currentTab by remember { mutableStateOf(initialTab) }
    var inspectFinding by remember { mutableStateOf<Finding?>(initialFindingToOpen) }

    LaunchedEffect(initialTab) {
        currentTab = initialTab
    }

    LaunchedEffect(initialFindingToOpen) {
        if (initialFindingToOpen != null) {
            inspectFinding = initialFindingToOpen
        }
    }

    LaunchedEffect(initialSharedOlxUrl) {
        if (!initialSharedOlxUrl.isNullOrBlank()) {
            currentTab = NavigationTab.RADARS
        }
    }

    val activeFindings by findingsViewModel.activeFindings.collectAsState()
    val queuedFindings by findingsViewModel.queuedFindings.collectAsState()
    val rejectedFindings by findingsViewModel.rejectedFindings.collectAsState()
    val selectedFeedTab by findingsViewModel.selectedFeedTabState.collectAsState()
    val watches by findingsViewModel.watches.collectAsState()
    val watchesLoaded by findingsViewModel.watchesLoaded.collectAsState()
    val selectedWatchId by findingsViewModel.selectedWatchIdState.collectAsState()
    val unreadCount by findingsViewModel.unreadCount.collectAsState()
    val marketPricesByWatch by findingsViewModel.marketPricesByWatch.collectAsState()
    val sortMode by findingsViewModel.sortModeState.collectAsState()
    val searchQuery by findingsViewModel.searchQueryState.collectAsState()
    val quickFilter by findingsViewModel.quickFilterState.collectAsState()
    val aiRetryingIds by findingsViewModel.aiRetryingIds.collectAsState()

    val watchesUi by watchesViewModel.watchesUi.collectAsState()
    val radarStatus by watchesViewModel.radarStatus.collectAsState()
    val currentInterval by statusViewModel.currentInterval.collectAsState()
    val appSettings by statusViewModel.appSettings.collectAsState()
    val aiActivity by ua.magerko.olxradar.ai.AiRuntime.activity.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Автообнаружение выжившего бэкапа после переустановки (только после загрузки Room!)
    var survivingBackup by remember { mutableStateOf<RadarBackup.FullBackupData?>(null) }
    var backupBannerDismissed by rememberSaveable { mutableStateOf(false) }
    var backupCheckDone by rememberSaveable { mutableStateOf(false) }

    val quickRestoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use {
                            it.readBytes().toString(Charsets.UTF_8)
                        }
                    } ?: error("Файл недоступен")
                    val res = watchesViewModel.importBackupJson(json)
                    statusViewModel.refreshSettingsState()
                    survivingBackup = null
                    backupBannerDismissed = true
                    Toast.makeText(
                        context,
                        "Восстановлено радаров: ${res.first}",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Ошибка восстановления: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(watchesLoaded, watches.size) {
        if (watchesLoaded && watches.isEmpty() && !backupBannerDismissed && !backupCheckDone) {
            backupCheckDone = true
            val found = watchesViewModel.discoverSurvivingBackup()
            if (found != null && found.watches.isNotEmpty()) {
                survivingBackup = found
            }
        } else if (watches.isNotEmpty()) {
            survivingBackup = null
        }
    }

    val screenKey = if (currentTab == NavigationTab.REJECTED) NavigationTab.FINDINGS else currentTab
    val visitedTabs = remember { mutableStateListOf(screenKey) }

    LaunchedEffect(screenKey) {
        if (screenKey !in visitedTabs) visitedTabs.add(screenKey)
    }

    fun selectTab(tab: NavigationTab) {
        val targetKey = if (tab == NavigationTab.REJECTED) NavigationTab.FINDINGS else tab
        if (targetKey !in visitedTabs) visitedTabs.add(targetKey)
        currentTab = tab
        when (tab) {
            NavigationTab.FINDINGS -> {
                if (selectedFeedTab == ua.magerko.olxradar.ui.FindingsFeedTab.REJECTED) {
                    findingsViewModel.setFeedTab(ua.magerko.olxradar.ui.FindingsFeedTab.MATCHES)
                }
            }
            NavigationTab.REJECTED -> {
                findingsViewModel.setFeedTab(ua.magerko.olxradar.ui.FindingsFeedTab.REJECTED)
            }
            else -> {}
        }
    }

    fun openExternalUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
            AppLog.w("MainScreen", "Не удалось открыть ссылку: $url", e)
        }
    }

    AmbientRadarBackdrop {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isTablet = maxWidth >= 840.dp

            Row(modifier = Modifier.fillMaxSize()) {
                if (isTablet) {
                    TabletNavigationRail(
                        currentTab = currentTab,
                        unreadCount = unreadCount,
                        aiIsBusy = aiActivity.isBusy,
                        onSelect = { selectTab(it) }
                    )
                }

                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = if (isTablet) {
                        WindowInsets.statusBars.union(WindowInsets.navigationBars)
                    } else {
                        WindowInsets.statusBars
                    },
                    bottomBar = {
                        if (!isTablet) {
                            ModernFloatingBottomBar(
                                currentTab = currentTab,
                                unreadCount = unreadCount,
                                aiIsBusy = aiActivity.isBusy,
                                onSelect = { selectTab(it) }
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) { paddingValues ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        // Баннер восстановления бэкапа после удаления приложения (автонайденный или выбор из Documents/OLXRadar)
                        val backupToOffer = survivingBackup
                        val showManualRestorePrompt = watchesLoaded && watches.isEmpty() && backupToOffer == null && !backupBannerDismissed
                        AnimatedVisibility(visible = (backupToOffer != null || showManualRestorePrompt) && !backupBannerDismissed) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = CyberneticPrimaryContainer.copy(alpha = 0.25f),
                                border = BorderStroke(1.dp, CyberneticPrimary.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Restore,
                                        contentDescription = null,
                                        tint = CyberneticPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (backupToOffer != null) "Найден сохранённый бэкап" else "Переустановили приложение?",
                                            color = CyberneticOnSurface,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (backupToOffer != null) {
                                                "Радаров: ${backupToOffer.watches.size} · Находок: ${backupToOffer.findings.size}"
                                            } else {
                                                "Выберите файл из Documents/OLXRadar для восстановления"
                                            },
                                            color = CyberneticOnSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            if (backupToOffer != null) {
                                                scope.launch {
                                                    val res = watchesViewModel.restoreSurvivingBackup()
                                                    statusViewModel.refreshSettingsState()
                                                    survivingBackup = null
                                                    backupBannerDismissed = true
                                                    if (res != null) {
                                                        Toast.makeText(
                                                            context,
                                                            "Восстановлено радаров: ${res.watchesImported}, находок: ${res.findingsImported}",
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                    }
                                                }
                                            } else {
                                                quickRestoreLauncher.launch(arrayOf("application/json", "*/*"))
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Text(
                                            text = if (backupToOffer != null) "Восстановить" else "Выбрать файл",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    IconButton(
                                        onClick = { backupBannerDismissed = true },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Скрыть",
                                            tint = CyberneticOnSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        val tabAlpha = remember { Animatable(1f) }
                        LaunchedEffect(screenKey, appSettings.reduceAnimations) {
                            if (appSettings.reduceAnimations) {
                                tabAlpha.snapTo(1f)
                            } else {
                                tabAlpha.snapTo(0.88f)
                                tabAlpha.animateTo(1f, tween(durationMillis = 85, easing = FastOutSlowInEasing))
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .graphicsLayer { alpha = tabAlpha.value }
                        ) {
                            visitedTabs.forEach { tab ->
                                val isSelected = tab == screenKey
                                key(tab) {
                                    Box(
                                        modifier = if (isSelected) {
                                            Modifier.fillMaxSize()
                                        } else {
                                            Modifier
                                                .fillMaxSize()
                                                .clearAndSetSemantics {}
                                                .layout { _, _ -> layout(0, 0) {} }
                                        }
                                    ) {
                                        when (tab) {
                                            NavigationTab.FINDINGS, NavigationTab.REJECTED -> {
                                                FindingsFeedScreen(
                                                    findings = activeFindings,
                                                    rejectedFindings = rejectedFindings,
                                                    queuedFindings = queuedFindings,
                                                    watches = watches,
                                                    selectedWatchId = selectedWatchId,
                                                    currentFeedTab = selectedFeedTab,
                                                    sortMode = sortMode,
                                                    searchQuery = searchQuery,
                                                    quickFilter = quickFilter,
                                                    compactMode = appSettings.compactCards,
                                                    showMarketDiff = appSettings.showMarketDiff,
                                                    reduceAnimations = appSettings.reduceAnimations || !isSelected,
                                                    radarStatus = radarStatus,
                                                    onTogglePause = { statusViewModel.toggleMonitoringPause() },
                                                    onSetSortMode = { findingsViewModel.setSortMode(it) },
                                                    onSetSearchQuery = { findingsViewModel.setSearchQuery(it) },
                                                    onSetQuickFilter = { findingsViewModel.setQuickFilter(it) },
                                                    onSelectFeedTab = { feedTab ->
                                                        findingsViewModel.setFeedTab(feedTab)
                                                        currentTab = if (feedTab == ua.magerko.olxradar.ui.FindingsFeedTab.REJECTED) {
                                                            NavigationTab.REJECTED
                                                        } else {
                                                            NavigationTab.FINDINGS
                                                        }
                                                    },
                                                    onFilterByWatch = { findingsViewModel.filterByWatch(it) },
                                                    onFindingClick = { finding ->
                                                        findingsViewModel.markAsRead(finding)
                                                        inspectFinding = finding
                                                    },
                                                    onToggleFavorite = { finding ->
                                                        findingsViewModel.toggleFavorite(finding) { isFav ->
                                                            PersistentStorageManager.scheduleAutoBackup(context)
                                                            Toast.makeText(
                                                                context,
                                                                if (isFav) "Добавлено в избранное" else "Убрано из избранного",
                                                                Toast.LENGTH_SHORT
                                                            ).show()
                                                        }
                                                    },
                                                    onDeleteFinding = { findingsViewModel.deleteFinding(it) },
                                                    onRestoreFinding = { findingsViewModel.restoreFinding(it) },
                                                    unreadCount = unreadCount,
                                                    onMarkAllRead = { findingsViewModel.markAllAsRead() },
                                                    onNavigateToSettings = { selectTab(NavigationTab.SETTINGS) },
                                                    marketPricesByWatch = marketPricesByWatch
                                                )
                                            }

                                            NavigationTab.RADARS -> {
                                                RadarsManagementScreen(
                                                    watchesUi = watchesUi,
                                                    radarStatus = radarStatus,
                                                    globalIntervalSec = currentInterval,
                                                    reduceAnimations = appSettings.reduceAnimations || !isSelected,
                                                    initialSharedOlxUrl = initialSharedOlxUrl,
                                                    onClearSharedOlxUrl = onClearSharedOlxUrl,
                                                    onToggleActive = { watch, active ->
                                                        watchesViewModel.toggleActive(watch, active)
                                                    },
                                                    onToggleGlobalPause = { statusViewModel.toggleMonitoringPause() },
                                                    onDeleteWatch = { watch ->
                                                        watchesViewModel.deleteWatch(watch)
                                                    },
                                                    onDuplicateWatch = { watch ->
                                                        watchesViewModel.duplicateWatch(watch)
                                                    },
                                                    onSaveWatch = { watch ->
                                                        watchesViewModel.saveWatch(watch)
                                                    },
                                                    onTestWatch = { watch ->
                                                        watchesViewModel.checkListings(watch)
                                                    },
                                                    onExportBackup = { watchesViewModel.buildBackupJson() },
                                                    onImportBackup = { json ->
                                                        val res = watchesViewModel.importBackupJson(json)
                                                        statusViewModel.refreshSettingsState()
                                                        res
                                                    }
                                                )
                                            }

                                            NavigationTab.AI_LIVE -> {
                                                AiLiveStreamScreen(
                                                    reduceAnimations = appSettings.reduceAnimations || !isSelected,
                                                    onNavigateToSettings = { selectTab(NavigationTab.SETTINGS) }
                                                )
                                            }

                                            NavigationTab.SETTINGS -> {
                                                StatusAiSettingsScreen(
                                                    statusViewModel = statusViewModel,
                                                    onBack = null,
                                                    isTabVisible = isSelected
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Deal Inspector Modal (BottomSheet)
            inspectFinding?.let { finding ->
                val observedFinding by remember(finding.id) {
                    findingsViewModel.observeFindingById(finding.id)
                }.collectAsState(initial = finding)
                val liveFinding = observedFinding ?: finding
                val priceHistory by remember(liveFinding.listingId) {
                    findingsViewModel.observePriceHistory(liveFinding.listingId)
                }.collectAsState(initial = emptyList())
                val associatedWatch = watches.find { it.id == liveFinding.watchId }
                DealInspectorModal(
                    finding = liveFinding,
                    watchTitle = associatedWatch?.title?.ifBlank { associatedWatch.query } ?: associatedWatch?.query,
                    priceHistory = priceHistory,
                    onBlockSeller = { sellerName ->
                        findingsViewModel.blockSeller(sellerName)
                        inspectFinding = null
                        onClearInitialFinding?.invoke()
                        Toast.makeText(context, "Продавец «$sellerName» добавлен в чёрный список", Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = {
                        inspectFinding = null
                        onClearInitialFinding?.invoke()
                    },
                    onDelete = {
                        findingsViewModel.deleteFinding(liveFinding)
                        inspectFinding = null
                        onClearInitialFinding?.invoke()
                    },
                    onToggleFavorite = {
                        findingsViewModel.toggleFavorite(liveFinding) {
                            PersistentStorageManager.scheduleAutoBackup(context)
                        }
                    },
                    onOpenUrl = { url -> openExternalUrl(url) },
                    isRetryingAi = liveFinding.id in aiRetryingIds,
                    onRetryAi = {
                        associatedWatch?.let { watch ->
                            findingsViewModel.retryAiAnalysis(liveFinding, watch)
                        }
                    },
                    marketPrice = marketPricesByWatch[liveFinding.watchId]
                )
            }
        }
    }
}

/**
 * Парящая современная навигационная капсула с поддержкой светлых, тёмных и Liquid Glass тем.
 */
@Composable
private fun ModernFloatingBottomBar(
    currentTab: NavigationTab,
    unreadCount: Int,
    aiIsBusy: Boolean,
    onSelect: (NavigationTab) -> Unit
) {
    val isGlass = isGlassTheme
    val isLight = !currentThemeMode.isDark && !isGlass

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .height(64.dp)
                .shadow(
                    elevation = if (isLight) 10.dp else 14.dp,
                    shape = RoundedCornerShape(30.dp),
                    spotColor = CyberneticPrimary.copy(alpha = if (isLight) 0.14f else 0.25f)
                ),
            shape = RoundedCornerShape(30.dp),
            color = when {
                isGlass -> CyberneticSurfaceContainerHigh.copy(alpha = 0.88f)
                isLight -> CyberneticSurfaceContainer.copy(alpha = 0.96f)
                else -> CyberneticSurfaceContainer.copy(alpha = 0.94f)
            },
            border = BorderStroke(1.dp, CyberneticCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                NavigationTab.entries.forEach { tab ->
                    key(tab) {
                        val isSelected = currentTab == tab
                        val interactionSource = remember { MutableInteractionSource() }

                        val itemColor by animateColorAsState(
                            targetValue = if (isSelected) CyberneticPrimary else CyberneticOnSurfaceVariant,
                            animationSpec = tween(durationMillis = 120),
                            label = "tab_color"
                        )

                        val pillBgColor by animateColorAsState(
                            targetValue = if (isSelected) CyberneticPrimary.copy(alpha = 0.14f) else Color.Transparent,
                            animationSpec = tween(durationMillis = 120),
                            label = "tab_pill_bg"
                        )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(22.dp))
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) { onSelect(tab) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        BadgedBox(
                            badge = {
                                if (tab == NavigationTab.FINDINGS && unreadCount > 0) {
                                    Badge(
                                        containerColor = CyberneticPrimary,
                                        contentColor = CyberneticOnPrimary,
                                        modifier = Modifier.offset(x = (-4).dp, y = 2.dp)
                                    ) {
                                        Text(
                                            text = if (unreadCount > 99) "99+" else "$unreadCount",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                } else if (tab == NavigationTab.AI_LIVE && aiIsBusy) {
                                    Badge(
                                        containerColor = CyberneticSecondary,
                                        modifier = Modifier
                                            .size(7.dp)
                                            .offset(x = (-2).dp, y = 2.dp)
                                    ) {}
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(pillBgColor)
                                    .padding(horizontal = 13.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    tint = itemColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Text(
                            text = tab.title,
                            color = itemColor,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            letterSpacing = (-0.1).sp
                        )
                    }
                    }
                }
            }
        }
    }
}

/**
 * Боковая панель для планшетов.
 */
@Composable
private fun TabletNavigationRail(
    currentTab: NavigationTab,
    unreadCount: Int,
    aiIsBusy: Boolean,
    onSelect: (NavigationTab) -> Unit
) {
    NavigationRail(
        containerColor = CyberneticSurfaceContainerLow.copy(alpha = 0.92f),
        contentColor = CyberneticOnSurface,
        modifier = Modifier
            .fillMaxHeight()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        NavigationTab.entries.forEach { tab ->
            val isSelected = currentTab == tab
            NavigationRailItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                icon = {
                    BadgedBox(
                        badge = {
                            if (tab == NavigationTab.FINDINGS && unreadCount > 0) {
                                Badge(containerColor = CyberneticPrimary, contentColor = CyberneticOnPrimary) {
                                    Text(if (unreadCount > 99) "99+" else "$unreadCount")
                                }
                            } else if (tab == NavigationTab.AI_LIVE && aiIsBusy) {
                                Badge(containerColor = CyberneticSecondary) {}
                            }
                        }
                    ) {
                        Icon(tab.icon, contentDescription = tab.title)
                    }
                },
                label = {
                    Text(
                        tab.title,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = CyberneticPrimary,
                    selectedTextColor = CyberneticPrimary,
                    indicatorColor = CyberneticPrimary.copy(alpha = 0.16f),
                    unselectedIconColor = CyberneticOnSurfaceVariant,
                    unselectedTextColor = CyberneticOnSurfaceVariant
                )
            )
        }
    }
}
