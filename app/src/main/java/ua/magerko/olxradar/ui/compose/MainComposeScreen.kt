package ua.magerko.olxradar.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import ua.magerko.olxradar.data.model.Finding
import ua.magerko.olxradar.ui.FindingsViewModel
import ua.magerko.olxradar.ui.StatusViewModel
import ua.magerko.olxradar.ui.WatchesViewModel

enum class NavigationTab(val title: String, val icon: ImageVector) {
    FINDINGS("Находки", Icons.Default.Radar),
    REJECTED("Отклонено", Icons.Default.FilterAltOff),
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
    initialFindingToOpen: Finding? = null
) {
    var currentTab by remember { mutableStateOf(initialTab) }
    var inspectFinding by remember { mutableStateOf<Finding?>(initialFindingToOpen) }

    val activeFindings by findingsViewModel.activeFindings.collectAsState()
    val rejectedFindings by findingsViewModel.rejectedFindings.collectAsState()
    val watches by findingsViewModel.watches.collectAsState()
    val selectedWatchId by findingsViewModel.selectedWatchIdState.collectAsState()
    val isRejectedTabState by findingsViewModel.isRejectedTabState.collectAsState()
    val unreadCount by findingsViewModel.unreadCount.collectAsState()

    val watchesUi by watchesViewModel.watchesUi.collectAsState()
    val radarStatus by watchesViewModel.radarStatus.collectAsState()
    val aiActivity by ua.magerko.olxradar.ai.AiRuntime.activity.collectAsState()

    val context = LocalContext.current
    val density = LocalDensity.current
    val glassFxEnabled = remember(isGlassTheme) { ThemePreferences.isGlassEffectsEnabled(context) }
    val useGlass = glassAvailable(isGlassTheme) && glassFxEnabled
    val glassBarHeight = with(density) { 68.dp.toPx() }
    val glassLens = with(density) { 14.dp.toPx() }

    fun selectTab(tab: NavigationTab) {
        currentTab = tab
        when (tab) {
            NavigationTab.FINDINGS -> findingsViewModel.setRejectedTab(false)
            NavigationTab.REJECTED -> findingsViewModel.setRejectedTab(true)
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(CyberneticBackgroundGradientTop, CyberneticBackgroundGradientBottom)
                )
            )
    ) {
        // Живой фон для настоящего Liquid Glass (шейдеры преломления из библиотеки Backdrop)
        val backdrop = if (useGlass) rememberGlassBackdrop(CyberneticBackgroundGradientTop) else null
        val contentModifier: Modifier = backdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Планшеты (ширина от 840dp): боковая навигация + двухколоночные списки
            val isTablet = maxWidth >= 840.dp

            Row(modifier = Modifier.fillMaxSize()) {
                if (isTablet && backdrop != null) {
                    GlassNavRail(
                        backdrop = backdrop,
                        currentTab = currentTab,
                        unreadCount = unreadCount,
                        aiIsBusy = aiActivity.isBusy,
                        onSelect = { selectTab(it) }
                    )
                } else if (isTablet) {
                    NavigationRail(
                        containerColor = CyberneticSurfaceContainerLow.copy(alpha = 0.9f)
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        NavigationTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = currentTab == tab,
                                onClick = { selectTab(tab) },
                                icon = {
                                    BadgedBox(badge = {
                                        if (tab == NavigationTab.FINDINGS && unreadCount > 0) {
                                            Badge { Text(if (unreadCount > 99) "99+" else "$unreadCount") }
                                        } else if (tab == NavigationTab.AI_LIVE && aiActivity.isBusy) {
                                            Badge(containerColor = CyberneticPrimary) {}
                                        }
                                    }) {
                                        Icon(tab.icon, contentDescription = tab.title)
                                    }
                                },
                                label = { Text(tab.title, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Box(
                    modifier = contentModifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    when (currentTab) {
                        NavigationTab.FINDINGS -> {
                            FindingsFeedScreen(
                                findings = activeFindings,
                                rejectedFindings = rejectedFindings,
                                watches = watches,
                                selectedWatchId = selectedWatchId,
                                isRejectedTab = false,
                                onSelectTab = { isRejected ->
                                    if (isRejected) {
                                        currentTab = NavigationTab.REJECTED
                                        findingsViewModel.setRejectedTab(true)
                                    }
                                },
                                onFilterByWatch = { findingsViewModel.filterByWatch(it) },
                                onFindingClick = { finding ->
                                    findingsViewModel.markAsRead(finding)
                                    inspectFinding = finding
                                },
                                onDeleteFinding = { findingsViewModel.deleteFinding(it) },
                                onNavigateToSettings = { currentTab = NavigationTab.SETTINGS }
                            )
                        }

                        NavigationTab.REJECTED -> {
                            FindingsFeedScreen(
                                findings = activeFindings,
                                rejectedFindings = rejectedFindings,
                                watches = watches,
                                selectedWatchId = selectedWatchId,
                                isRejectedTab = true,
                                onSelectTab = { isRejected ->
                                    if (!isRejected) {
                                        currentTab = NavigationTab.FINDINGS
                                        findingsViewModel.setRejectedTab(false)
                                    }
                                },
                                onFilterByWatch = { findingsViewModel.filterByWatch(it) },
                                onFindingClick = { finding ->
                                    inspectFinding = finding
                                },
                                onDeleteFinding = { findingsViewModel.deleteFinding(it) },
                                onNavigateToSettings = { currentTab = NavigationTab.SETTINGS }
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

            // Нижняя навигация: стеклянная в теме Liquid Glass, обычная в остальных
            if (isTablet) {
                // На планшете навигация сбоку — нижняя панель не нужна
            } else if (backdrop != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .fillMaxWidth()
                        .height(64.dp)
                        .glassEffect(
                            backdrop = backdrop,
                            shape = RoundedCornerShape(32.dp),
                            blurPx = with(density) { 8.dp.toPx() },
                            lensPx = glassLens
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NavigationTab.entries.forEach { tab ->
                            val isSelected = currentTab == tab
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable { selectTab(tab) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                BadgedBox(badge = {
                                    if (tab == NavigationTab.FINDINGS && unreadCount > 0) {
                                        Badge(containerColor = CyberneticPrimary, contentColor = CyberneticOnPrimary) {
                                            Text(
                                                text = if (unreadCount > 99) "99+" else "$unreadCount",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else if (tab == NavigationTab.AI_LIVE && aiActivity.isBusy) {
                                        Badge(containerColor = CyberneticPrimary) {}
                                    }
                                }) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title,
                                        tint = if (isSelected) CyberneticPrimary else CyberneticOnSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Text(
                                    text = tab.title,
                                    color = if (isSelected) CyberneticPrimary else CyberneticOnSurfaceVariant,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            } else {
                NavigationBar(
                    containerColor = CyberneticSurfaceContainerLow.copy(alpha = 0.95f),
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .height(68.dp)
                ) {
                    NavigationTab.entries.forEach { tab ->
                        val isSelected = currentTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selectTab(tab) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (tab == NavigationTab.FINDINGS && unreadCount > 0) {
                                            Badge(
                                                containerColor = CyberneticPrimary,
                                                contentColor = CyberneticOnPrimary
                                            ) {
                                                Text(
                                                    text = if (unreadCount > 99) "99+" else "$unreadCount",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        } else if (tab == NavigationTab.AI_LIVE && aiActivity.isBusy) {
                                            Badge(containerColor = CyberneticPrimary) {}
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CyberneticPrimary,
                                selectedTextColor = CyberneticPrimary,
                                unselectedIconColor = CyberneticOnSurfaceVariant,
                                unselectedTextColor = CyberneticOnSurfaceVariant,
                                indicatorColor = CyberneticPrimary.copy(alpha = 0.12f)
                            )
                        )
                    }
                }
            }

            // Native Deal Inspector Modal (BottomSheet)
            inspectFinding?.let { finding ->
                DealInspectorModal(
                    finding = finding,
                    onDismiss = { inspectFinding = null },
                    onDelete = {
                        findingsViewModel.deleteFinding(finding)
                        inspectFinding = null
                    },
                    onOpenUrl = { }
                )
            }
        }
    }
}

/** Стеклянная боковая навигация для планшетов в теме Liquid Glass. */
@Composable
private fun GlassNavRail(
    backdrop: LayerBackdrop,
    currentTab: NavigationTab,
    unreadCount: Int,
    aiIsBusy: Boolean,
    onSelect: (NavigationTab) -> Unit
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(84.dp)
            .padding(8.dp)
            .glassEffect(
                backdrop = backdrop,
                shape = RoundedCornerShape(28.dp),
                blurPx = with(density) { 8.dp.toPx() },
                lensPx = with(density) { 12.dp.toPx() }
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            NavigationTab.entries.forEach { tab ->
                val isSelected = currentTab == tab
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(tab) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BadgedBox(badge = {
                        if (tab == NavigationTab.FINDINGS && unreadCount > 0) {
                            Badge(containerColor = CyberneticPrimary, contentColor = CyberneticOnPrimary) {
                                Text(if (unreadCount > 99) "99+" else "$unreadCount")
                            }
                        } else if (tab == NavigationTab.AI_LIVE && aiIsBusy) {
                            Badge(containerColor = CyberneticPrimary) {}
                        }
                    }) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = if (isSelected) CyberneticPrimary else CyberneticOnSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = tab.title,
                        color = if (isSelected) CyberneticPrimary else CyberneticOnSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
