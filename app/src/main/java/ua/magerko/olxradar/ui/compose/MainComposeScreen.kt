package ua.magerko.olxradar.ui.compose

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onClearInitialFinding: (() -> Unit)? = null
) {
    var currentTab by remember { mutableStateOf(initialTab) }
    var inspectFinding by remember { mutableStateOf<Finding?>(initialFindingToOpen) }

    // Реактивное обновление при открытии через системное пуш-уведомление (onNewIntent)
    LaunchedEffect(initialTab) {
        currentTab = initialTab
    }

    LaunchedEffect(initialFindingToOpen) {
        if (initialFindingToOpen != null) {
            inspectFinding = initialFindingToOpen
        }
    }

    val activeFindings by findingsViewModel.activeFindings.collectAsState()
    val queuedFindings by findingsViewModel.queuedFindings.collectAsState()
    val rejectedFindings by findingsViewModel.rejectedFindings.collectAsState()
    val selectedFeedTab by findingsViewModel.selectedFeedTabState.collectAsState()
    val watches by findingsViewModel.watches.collectAsState()
    val selectedWatchId by findingsViewModel.selectedWatchIdState.collectAsState()
    val unreadCount by findingsViewModel.unreadCount.collectAsState()
    val marketPricesByWatch by findingsViewModel.marketPricesByWatch.collectAsState()
    val sortMode by findingsViewModel.sortModeState.collectAsState()
    val searchQuery by findingsViewModel.searchQueryState.collectAsState()

    val watchesUi by watchesViewModel.watchesUi.collectAsState()
    val radarStatus by watchesViewModel.radarStatus.collectAsState()
    val aiActivity by ua.magerko.olxradar.ai.AiRuntime.activity.collectAsState()

    val context = LocalContext.current

    fun selectTab(tab: NavigationTab) {
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(CyberneticBackgroundGradientTop, CyberneticBackgroundGradientBottom)
                )
            )
    ) {
        val isTablet = maxWidth >= 840.dp

        Row(modifier = Modifier.fillMaxSize()) {
            // Tablet Navigation Rail
            if (isTablet) {
                TabletNavigationRail(
                    currentTab = currentTab,
                    unreadCount = unreadCount,
                    aiIsBusy = aiActivity.isBusy,
                    onSelect = { selectTab(it) }
                )
            }

            // Main Content Area with Scaffold
            Scaffold(
                containerColor = Color.Transparent,
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    when (currentTab) {
                        NavigationTab.FINDINGS -> {
                            FindingsFeedScreen(
                                findings = activeFindings,
                                rejectedFindings = rejectedFindings,
                                queuedFindings = queuedFindings,
                                watches = watches,
                                selectedWatchId = selectedWatchId,
                                currentFeedTab = selectedFeedTab,
                                sortMode = sortMode,
                                searchQuery = searchQuery,
                                onSetSortMode = { findingsViewModel.setSortMode(it) },
                                onSetSearchQuery = { findingsViewModel.setSearchQuery(it) },
                                onSelectFeedTab = { tab ->
                                    findingsViewModel.setFeedTab(tab)
                                    if (tab == ua.magerko.olxradar.ui.FindingsFeedTab.REJECTED) {
                                        currentTab = NavigationTab.REJECTED
                                    }
                                },
                                onFilterByWatch = { findingsViewModel.filterByWatch(it) },
                                onFindingClick = { finding ->
                                    findingsViewModel.markAsRead(finding)
                                    inspectFinding = finding
                                },
                                onDeleteFinding = { findingsViewModel.deleteFinding(it) },
                                onNavigateToSettings = { currentTab = NavigationTab.SETTINGS },
                                marketPricesByWatch = marketPricesByWatch
                            )
                        }

                        NavigationTab.REJECTED -> {
                            FindingsFeedScreen(
                                findings = activeFindings,
                                rejectedFindings = rejectedFindings,
                                queuedFindings = queuedFindings,
                                watches = watches,
                                selectedWatchId = selectedWatchId,
                                currentFeedTab = selectedFeedTab,
                                sortMode = sortMode,
                                searchQuery = searchQuery,
                                onSetSortMode = { findingsViewModel.setSortMode(it) },
                                onSetSearchQuery = { findingsViewModel.setSearchQuery(it) },
                                onSelectFeedTab = { tab ->
                                    findingsViewModel.setFeedTab(tab)
                                    if (tab != ua.magerko.olxradar.ui.FindingsFeedTab.REJECTED) {
                                        currentTab = NavigationTab.FINDINGS
                                    }
                                },
                                onFilterByWatch = { findingsViewModel.filterByWatch(it) },
                                onFindingClick = { finding ->
                                    inspectFinding = finding
                                },
                                onDeleteFinding = { findingsViewModel.deleteFinding(it) },
                                onNavigateToSettings = { currentTab = NavigationTab.SETTINGS },
                                marketPricesByWatch = marketPricesByWatch
                            )
                        }

                        NavigationTab.RADARS -> {
                            RadarsManagementScreen(
                                watchesUi = watchesUi,
                                radarStatus = radarStatus,
                                onToggleActive = { watch, active ->
                                    watchesViewModel.toggleActive(watch, active)
                                },
                                onDeleteWatch = { watch ->
                                    watchesViewModel.deleteWatch(watch)
                                },
                                onDuplicateWatch = { watch ->
                                    watchesViewModel.duplicateWatch(watch)
                                },
                                onSaveWatch = { watch ->
                                    watchesViewModel.saveWatch(watch)
                                },
                                onExportBackup = { watchesViewModel.buildBackupJson() },
                                onImportBackup = { json -> watchesViewModel.importBackupJson(json) }
                            )
                        }

                        NavigationTab.AI_LIVE -> {
                            AiLiveStreamScreen(
                                onNavigateToSettings = { currentTab = NavigationTab.SETTINGS }
                            )
                        }

                        NavigationTab.SETTINGS -> {
                            StatusAiSettingsScreen(
                                statusViewModel = statusViewModel,
                                onBack = null
                            )
                        }
                    }
                }
            }
        }

        // Deal Inspector Modal (BottomSheet)
        inspectFinding?.let { finding ->
            val associatedWatch = watches.find { it.id == finding.watchId }
            DealInspectorModal(
                finding = finding,
                watchTitle = associatedWatch?.title?.ifBlank { associatedWatch.query } ?: associatedWatch?.query,
                onDismiss = {
                    inspectFinding = null
                    onClearInitialFinding?.invoke()
                },
                onDelete = {
                    findingsViewModel.deleteFinding(finding)
                    inspectFinding = null
                    onClearInitialFinding?.invoke()
                },
                onOpenUrl = { url ->
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(
                            context,
                            "Не удалось открыть ссылку",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        AppLog.w("MainScreen", "Не удалось открыть ссылку: $url", e)
                    }
                },
                isRetryingAi = finding.id in findingsViewModel.aiRetryingIds.collectAsState().value,
                onRetryAi = {
                    watches.find { it.id == finding.watchId }?.let { watch ->
                        findingsViewModel.retryAiAnalysis(finding, watch)
                    }
                },
                marketPrice = marketPricesByWatch[finding.watchId]
            )
        }
    }
}

/**
 * Парящая современная навигационная панель (Material 3 + Liquid Glass).
 */
@Composable
private fun ModernFloatingBottomBar(
    currentTab: NavigationTab,
    unreadCount: Int,
    aiIsBusy: Boolean,
    onSelect: (NavigationTab) -> Unit
) {
    val isGlass = isGlassTheme

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(
                    elevation = if (isGlass) 12.dp else 8.dp,
                    shape = RoundedCornerShape(32.dp),
                    spotColor = CyberneticPrimary.copy(alpha = 0.25f)
                ),
            shape = RoundedCornerShape(32.dp),
            color = if (isGlass) {
                CyberneticSurfaceContainerHigh.copy(alpha = 0.85f)
            } else {
                CyberneticSurfaceContainer.copy(alpha = 0.95f)
            },
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (isGlass) 0.35f else 0.15f),
                        Color.White.copy(alpha = if (isGlass) 0.08f else 0.03f)
                    )
                )
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                NavigationTab.entries.forEach { tab ->
                    val isSelected = currentTab == tab
                    val interactionSource = remember { MutableInteractionSource() }

                    val itemColor by animateColorAsState(
                        targetValue = if (isSelected) CyberneticPrimary else CyberneticOnSurfaceVariant,
                        label = "tab_color"
                    )

                    val pillBgColor by animateColorAsState(
                        targetValue = if (isSelected) CyberneticPrimary.copy(alpha = 0.14f) else Color.Transparent,
                        label = "tab_pill_bg"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(20.dp))
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
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    tint = itemColor,
                                    modifier = Modifier.size(21.dp)
                                )
                            }
                        }

                        Text(
                            text = tab.title,
                            color = itemColor,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            letterSpacing = (-0.1).sp
                        )
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
        containerColor = CyberneticSurfaceContainerLow.copy(alpha = 0.95f),
        contentColor = CyberneticOnSurface,
        modifier = Modifier.fillMaxHeight()
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
                label = { Text(tab.title, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
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
