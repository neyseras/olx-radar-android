package ua.magerko.olxradar.ui.compose

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.magerko.olxradar.data.backup.RadarBackup
import ua.magerko.olxradar.data.model.ConditionAnalyzer
import ua.magerko.olxradar.data.model.Listing
import ua.magerko.olxradar.data.model.OlxCategory
import ua.magerko.olxradar.data.model.Watch
import ua.magerko.olxradar.data.network.OlxCities
import ua.magerko.olxradar.data.network.OlxUrlImporter
import ua.magerko.olxradar.service.ServiceStatus
import ua.magerko.olxradar.ui.WatchItemUi
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RadarsManagementScreen(
    watchesUi: List<WatchItemUi>,
    radarStatus: ServiceStatus = ServiceStatus(),
    globalIntervalSec: Int = 25,
    reduceAnimations: Boolean = false,
    initialSharedOlxUrl: String? = null,
    onClearSharedOlxUrl: (() -> Unit)? = null,
    onToggleActive: (Watch, Boolean) -> Unit,
    onToggleGlobalPause: (() -> Unit)? = null,
    onDeleteWatch: (Watch) -> Unit,
    onDuplicateWatch: (Watch) -> Unit,
    onSaveWatch: (Watch) -> Unit,
    onTestWatch: (suspend (Watch) -> Result<List<Listing>>)? = null,
    onExportBackup: suspend () -> String,
    onImportBackup: suspend (String) -> Pair<Int, Int>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var filterMode by remember { mutableStateOf("all") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingWatch by remember { mutableStateOf<Watch?>(null) }
    var pendingSharedUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialSharedOlxUrl) {
        if (!initialSharedOlxUrl.isNullOrBlank()) {
            pendingSharedUrl = initialSharedOlxUrl
            editingWatch = null
            showAddDialog = true
            onClearSharedOlxUrl?.invoke()
        }
    }

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(10_000L)
            nowMs = System.currentTimeMillis()
        }
    }

    val activeCount = remember(watchesUi) { watchesUi.count { it.watch.isActive } }
    val totalFindings = remember(watchesUi) { watchesUi.sumOf { it.findingsCount } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = onExportBackup()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                            os.write(json.toByteArray(Charsets.UTF_8))
                            os.flush()
                        } ?: error("Поток записи недоступен")
                    }
                    Toast.makeText(context, "Список радаров сохранён", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Ошибка сохранения: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use {
                            it.readBytes().toString(Charsets.UTF_8)
                        } ?: error("Поток чтения недоступен")
                    }
                    val (imported, skipped) = onImportBackup(json)
                    Toast.makeText(
                        context,
                        if (imported == 0 && skipped == 0) "Файл не содержит радаров"
                        else "Импортировано: $imported · пропущено дубликатов: $skipped",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Не удалось прочитать файл: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val filteredList = remember(watchesUi, filterMode) {
        when (filterMode) {
            "active" -> watchesUi.filter { it.watch.isActive }
            "paused" -> watchesUi.filter { !it.watch.isActive }
            "new" -> watchesUi.filter { it.findingsCount > 0 }
            else -> watchesUi
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // TOP HEADER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Мои радары",
                        color = CyberneticOnSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.4).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusPulseDot(
                            color = if (activeCount > 0 && !radarStatus.isPaused) CyberneticSecondary else CyberneticOutline,
                            isPulsing = activeCount > 0 && radarStatus.isRunning && !radarStatus.isPaused,
                            reduceAnimations = reduceAnimations,
                            size = 7.dp
                        )
                        Text(
                            text = if (radarStatus.isPaused) "Сканер на паузе · $activeCount активных" else "$activeCount из ${watchesUi.size} активны",
                            color = if (radarStatus.isPaused) CyberneticTertiary else CyberneticSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (onToggleGlobalPause != null) {
                        IconButton(onClick = onToggleGlobalPause) {
                            Icon(
                                imageVector = if (radarStatus.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (radarStatus.isPaused) "Возобновить" else "Пауза",
                                tint = if (radarStatus.isPaused) CyberneticSecondary else CyberneticTertiary
                            )
                        }
                    }
                    IconButton(onClick = { exportLauncher.launch(RadarBackup.DEFAULT_FILE_NAME) }) {
                        Icon(
                            imageVector = Icons.Default.IosShare,
                            contentDescription = "Экспорт радаров",
                            tint = CyberneticOnSurfaceVariant
                        )
                    }
                    IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Импорт радаров",
                            tint = CyberneticOnSurfaceVariant
                        )
                    }
                }
            }

            // SUMMARY HERO DASHBOARD CARD
            RadarSurfaceCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                shape = RoundedCornerShape(18.dp),
                containerColor = CyberneticSurfaceContainerHigh.copy(alpha = if (isGlassTheme) 0.8f else 1f)
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
                            Icon(
                                imageVector = Icons.Default.TrackChanges,
                                contentDescription = null,
                                tint = CyberneticPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Сводка мониторинга",
                                color = CyberneticOnSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Switch(
                            checked = activeCount > 0,
                            onCheckedChange = { enableAll ->
                                watchesUi.forEach { onToggleActive(it.watch, enableAll) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyberneticOnSecondary,
                                checkedTrackColor = CyberneticSecondary,
                                uncheckedThumbColor = CyberneticOutline,
                                uncheckedTrackColor = CyberneticSurfaceContainerHighest
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricTile(
                            label = "Радары",
                            value = "${watchesUi.size}",
                            subLabel = "$activeCount активных",
                            accentColor = CyberneticPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = "Проверено",
                            value = "${radarStatus.checksCount}",
                            subLabel = CachedFormatters.formatShortAgo(radarStatus.lastCheckedAt, nowMs),
                            accentColor = CyberneticSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = "Находок",
                            value = "$totalFindings",
                            subLabel = "${watchesUi.count { it.findingsCount > 0 }} с лотами",
                            accentColor = CyberneticTertiary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // FILTER CHIPS ROW
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "f_all") {
                    FilterChip(
                        selected = filterMode == "all",
                        onClick = { filterMode = "all" },
                        label = { Text("Все (${watchesUi.size})", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberneticPrimary.copy(alpha = 0.18f),
                            selectedLabelColor = CyberneticPrimary,
                            containerColor = CyberneticSurfaceContainerLow,
                            labelColor = CyberneticOnSurfaceVariant
                        )
                    )
                }
                item(key = "f_active") {
                    FilterChip(
                        selected = filterMode == "active",
                        onClick = { filterMode = "active" },
                        label = { Text("Активные ($activeCount)", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberneticPrimary.copy(alpha = 0.18f),
                            selectedLabelColor = CyberneticPrimary,
                            containerColor = CyberneticSurfaceContainerLow,
                            labelColor = CyberneticOnSurfaceVariant
                        )
                    )
                }
                item(key = "f_paused") {
                    FilterChip(
                        selected = filterMode == "paused",
                        onClick = { filterMode = "paused" },
                        label = { Text("На паузе (${watchesUi.size - activeCount})", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberneticPrimary.copy(alpha = 0.18f),
                            selectedLabelColor = CyberneticPrimary,
                            containerColor = CyberneticSurfaceContainerLow,
                            labelColor = CyberneticOnSurfaceVariant
                        )
                    )
                }
                item(key = "f_new") {
                    FilterChip(
                        selected = filterMode == "new",
                        onClick = { filterMode = "new" },
                        label = { Text("С находками", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberneticSecondary.copy(alpha = 0.18f),
                            selectedLabelColor = CyberneticSecondary,
                            containerColor = CyberneticSurfaceContainerLow,
                            labelColor = CyberneticOnSurfaceVariant
                        )
                    )
                }
            }

            // RADARS LIST
            if (filteredList.isEmpty()) {
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
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = null,
                            tint = CyberneticOutline,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = if (watchesUi.isEmpty()) "Создайте первый радар" else "Радары не найдены",
                            color = CyberneticOnSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Нажмите «Добавить радар» или вставьте готовую ссылку с OLX.ua.",
                            color = CyberneticOnSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                AdaptiveRadarList(
                    items = filteredList,
                    radarStatus = radarStatus,
                    globalIntervalSec = globalIntervalSec,
                    nowMs = nowMs,
                    modifier = Modifier.weight(1f),
                    onItemClick = { editingWatch = it.watch },
                    onToggleActive = { watch, active -> onToggleActive(watch, active) },
                    onDuplicate = { onDuplicateWatch(it) },
                    onDelete = { onDeleteWatch(it) }
                )
            }
        }

        // FAB (без лишнего двойного отступа снизу!)
        ExtendedFloatingActionButton(
            onClick = { showAddDialog = true },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Добавить радар", fontWeight = FontWeight.Bold) },
            containerColor = CyberneticSecondary,
            contentColor = CyberneticOnSecondary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 14.dp, end = 16.dp)
        )
    }

    if (showAddDialog || editingWatch != null) {
        AddEditRadarDialog(
            initialWatch = editingWatch,
            initialSharedUrl = if (editingWatch == null) pendingSharedUrl else null,
            onDismiss = {
                showAddDialog = false
                editingWatch = null
                pendingSharedUrl = null
            },
            onTestWatch = onTestWatch,
            onSave = { saved ->
                onSaveWatch(saved)
                showAddDialog = false
                editingWatch = null
                pendingSharedUrl = null
            }
        )
    }
}

@Composable
fun AdaptiveRadarList(
    items: List<WatchItemUi>,
    radarStatus: ServiceStatus = ServiceStatus(),
    globalIntervalSec: Int = 25,
    nowMs: Long = System.currentTimeMillis(),
    modifier: Modifier = Modifier,
    onItemClick: (WatchItemUi) -> Unit,
    onToggleActive: (Watch, Boolean) -> Unit,
    onDuplicate: (Watch) -> Unit,
    onDelete: (Watch) -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 84.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = { it.watch.id }, contentType = { "radar_card" }) { itemUi ->
                RadarCard(
                    itemUi = itemUi,
                    radarStatus = radarStatus,
                    globalIntervalSec = globalIntervalSec,
                    nowMs = nowMs,
                    onToggleActive = { active -> onToggleActive(itemUi.watch, active) },
                    onEdit = { onItemClick(itemUi) },
                    onDuplicate = { onDuplicate(itemUi.watch) },
                    onDelete = { onDelete(itemUi.watch) }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RadarCard(
    itemUi: WatchItemUi,
    radarStatus: ServiceStatus = ServiceStatus(),
    globalIntervalSec: Int = 25,
    nowMs: Long = System.currentTimeMillis(),
    onToggleActive: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val watch = itemUi.watch
    RadarSurfaceCard(
        onClick = onEdit,
        shape = RoundedCornerShape(18.dp),
        containerColor = if (watch.isActive) CyberneticSurfaceContainer else CyberneticSurfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(
                                if (watch.isActive) CyberneticPrimary.copy(alpha = 0.14f)
                                else CyberneticSurfaceContainerHighest
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = null,
                            tint = if (watch.isActive) CyberneticPrimary else CyberneticOutline,
                            modifier = Modifier.size(22.dp)
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (watch.isActive) CyberneticSecondary else CyberneticOutline)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = watch.title,
                            color = CyberneticOnSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "\"${watch.query}\"",
                            color = CyberneticOnSurfaceVariant,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Switch(
                    checked = watch.isActive,
                    onCheckedChange = onToggleActive,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyberneticOnSecondary,
                        checkedTrackColor = CyberneticSecondary,
                        uncheckedThumbColor = CyberneticOutline,
                        uncheckedTrackColor = CyberneticSurfaceContainerHighest
                    )
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                val currSymbol = when (watch.priceCurrency.trim().uppercase()) {
                    "USD", "$" -> "$"
                    "EUR", "€" -> "€"
                    else -> "₴"
                }
                val priceText = when {
                    watch.priceFrom != null && watch.priceTo != null -> "${watch.priceFrom}–${watch.priceTo} $currSymbol"
                    watch.priceFrom != null -> "от ${watch.priceFrom} $currSymbol"
                    watch.priceTo != null -> "до ${watch.priceTo} $currSymbol"
                    else -> "Любая цена"
                }
                RadarBadgeChip(icon = Icons.Default.Payments, text = priceText, accent = CyberneticTertiary)

                RadarBadgeChip(
                    icon = Icons.Default.LocationOn,
                    text = watch.cityName ?: "Вся Украина",
                    accent = CyberneticPrimary
                )

                val catLabel = OlxCategory.entries.firstOrNull { it.id == watch.categoryId }?.label
                if (catLabel != null && watch.categoryId != null) {
                    RadarBadgeChip(icon = Icons.Default.Category, text = catLabel, accent = CyberneticOnSurfaceVariant)
                }

                if (watch.ownerType == "private") {
                    RadarBadgeChip(icon = Icons.Default.Person, text = "Частные", accent = CyberneticSecondary)
                } else if (watch.ownerType == "business") {
                    RadarBadgeChip(icon = Icons.Default.Store, text = "Бизнес", accent = CyberneticOnSurfaceVariant)
                }

                if (watch.state == "new") {
                    RadarBadgeChip(icon = Icons.Default.NewReleases, text = "Новое", accent = CyberneticSecondary)
                } else if (watch.state == "used") {
                    RadarBadgeChip(icon = Icons.Default.History, text = "Б/у", accent = CyberneticOnSurfaceVariant)
                }

                if (watch.mustWords.isNotBlank()) {
                    val modeLabel = if (watch.mustWordsMode == "all") "И" else "ИЛИ"
                    RadarBadgeChip(
                        icon = null,
                        text = "[$modeLabel] ${watch.mustWords}",
                        accent = CyberneticPrimary
                    )
                }

                if (watch.publicationAge.isNotBlank() && watch.publicationAge != "any") {
                    val ageLabel = when (watch.publicationAge) {
                        "fresh" -> "<24ч"
                        "recent" -> "<3дн"
                        "week" -> "<7дн"
                        "old" -> ">7дн"
                        "very_old" -> ">14дн"
                        else -> watch.publicationAge
                    }
                    RadarBadgeChip(icon = Icons.Default.Schedule, text = ageLabel, accent = CyberneticTertiary)
                }

                if (itemUi.findingsCount > 0) {
                    RadarBadgeChip(
                        icon = Icons.Default.Bolt,
                        text = "+${itemUi.findingsCount}",
                        accent = CyberneticSecondary
                    )
                }
            }

            HorizontalDivider(color = CyberneticCardBorderSoft, thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val watchLive = radarStatus.perWatch[watch.id]
                    val errorMsg = watchLive?.lastError ?: watch.lastError
                    val hasError = !errorMsg.isNullOrBlank()
                    val is500 = errorMsg?.contains("500") == true
                    val lastSuccess = watchLive?.lastSuccessAt ?: watch.lastSuccessAt
                    val interval = watch.intervalSecOverride ?: globalIntervalSec

                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    !watch.isActive -> CyberneticOutline
                                    is500 -> CyberneticTertiary
                                    hasError -> CyberneticError
                                    else -> CyberneticSecondary
                                }
                            )
                    )
                    if (!watch.isActive) {
                        Text(
                            text = "Пауза · ${interval}с",
                            color = CyberneticOutline,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    } else if (hasError) {
                        val displayMsg = if (is500) "OLX занят (500) · повтор..." else "Ошибка: ${errorMsg?.take(22)}"
                        Text(
                            text = displayMsg,
                            color = if (is500) CyberneticTertiary else CyberneticError,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        val agoStr = CachedFormatters.formatShortAgo(lastSuccess, nowMs)
                        Text(
                            text = "Каждые ${interval}с · $agoStr",
                            color = CyberneticOnSurfaceVariant,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onDuplicate, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Дублировать",
                            tint = CyberneticOnSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Удалить",
                            tint = CyberneticError,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RadarBadgeChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    text: String,
    accent: Color
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = CyberneticSurfaceContainerLow,
        border = BorderStroke(1.dp, CyberneticCardBorderSoft)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(11.dp))
            }
            Text(
                text = text,
                color = CyberneticOnSurface,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

val DEFAULT_EXCLUSION_WORDS = listOf(
    "нерабочий",
    "разбит",
    "на запчасти",
    "дефект",
    "треснут",
    "не включается",
    "после ремонта"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditRadarDialog(
    initialWatch: Watch?,
    initialSharedUrl: String? = null,
    onDismiss: () -> Unit,
    onTestWatch: (suspend (Watch) -> Result<List<Listing>>)? = null,
    onSave: (Watch) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var olxUrlInput by remember { mutableStateOf(initialSharedUrl ?: "") }
    var showUrlImporter by remember { mutableStateOf(initialWatch == null) }

    var title by remember { mutableStateOf(initialWatch?.title ?: "") }
    var query by remember { mutableStateOf(initialWatch?.query ?: "") }
    var priceFrom by remember { mutableStateOf(initialWatch?.priceFrom?.toString() ?: "") }
    var priceTo by remember { mutableStateOf(initialWatch?.priceTo?.toString() ?: "") }
    var priceCurrency by remember { mutableStateOf(initialWatch?.priceCurrency ?: "UAH") }
    var includeNoPrice by remember { mutableStateOf(initialWatch?.includeNoPrice ?: true) }
    var cityName by remember { mutableStateOf(initialWatch?.cityName ?: "") }
    var categoryId by remember { mutableStateOf(initialWatch?.categoryId ?: OlxCategory.ALL.id) }
    var ownerType by remember { mutableStateOf(initialWatch?.ownerType ?: "any") }
    var productState by remember { mutableStateOf(initialWatch?.state ?: "any") }
    var notifyMode by remember { mutableStateOf(initialWatch?.notifyMode ?: "new_pushup") }
    var intervalOverride by remember { mutableStateOf(initialWatch?.intervalSecOverride) }

    var stopWords by remember { mutableStateOf(initialWatch?.stopWords ?: "") }
    var mustWords by remember { mutableStateOf(initialWatch?.mustWords ?: "") }
    var mustWordsMode by remember { mutableStateOf(initialWatch?.mustWordsMode ?: "any") }
    var publicationAge by remember { mutableStateOf(initialWatch?.publicationAge ?: "any") }
    var conditionTarget by remember { mutableStateOf(initialWatch?.conditionTarget ?: "any") }
    var conditionThreshold by remember { mutableFloatStateOf((initialWatch?.conditionThreshold ?: 0).coerceIn(0, 100).toFloat()) }

    var isTestingSearch by remember { mutableStateOf(false) }
    var testResults by remember { mutableStateOf<List<Listing>?>(null) }
    var testError by remember { mutableStateOf<String?>(null) }

    fun applySharedUrl(rawUrl: String, notifyUser: Boolean) {
        val parsed = OlxUrlImporter.parseOlxUrl(rawUrl)
        if (parsed == null) {
            if (notifyUser) {
                Toast.makeText(context, "Не удалось распознать ссылку OLX", Toast.LENGTH_SHORT).show()
            }
        } else {
            title = parsed.title
            query = parsed.query
            priceFrom = parsed.priceFrom?.toString() ?: ""
            priceTo = parsed.priceTo?.toString() ?: ""
            priceCurrency = parsed.priceCurrency
            cityName = parsed.cityName ?: ""
            categoryId = parsed.categoryId ?: OlxCategory.ALL.id
            productState = parsed.state ?: "any"
            ownerType = parsed.ownerType ?: "any"
            if (notifyUser) {
                Toast.makeText(context, "Параметры из ссылки применены!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(initialSharedUrl) {
        if (!initialSharedUrl.isNullOrBlank() && initialWatch == null) {
            applySharedUrl(initialSharedUrl, notifyUser = true)
        }
    }

    fun buildDraftWatch(): Watch = (initialWatch ?: Watch(title = "", query = "")).copy(
        title = title.ifBlank { query },
        query = query.trim(),
        priceFrom = priceFrom.toIntOrNull(),
        priceTo = priceTo.toIntOrNull(),
        priceCurrency = priceCurrency,
        includeNoPrice = includeNoPrice,
        cityName = cityName.trim().ifBlank { null },
        categoryId = if (categoryId == OlxCategory.ALL.id) null else categoryId,
        ownerType = ownerType.takeIf { it != "any" },
        state = productState.takeIf { it != "any" },
        notifyMode = notifyMode,
        intervalSecOverride = intervalOverride,
        stopWords = stopWords,
        mustWords = mustWords,
        mustWordsMode = mustWordsMode,
        publicationAge = publicationAge,
        conditionTarget = conditionTarget,
        conditionThreshold = if (conditionTarget == "any") 0 else conditionThreshold.roundToInt()
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberneticModalSurface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initialWatch == null) "Новый радар" else "Редактировать радар",
                    color = CyberneticOnSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = CyberneticOnSurfaceVariant)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ИМПОРТ ИЗ ССЫЛКИ OLX
                if (showUrlImporter) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CyberneticSurfaceContainerLow,
                        border = BorderStroke(1.dp, CyberneticPrimary.copy(alpha = 0.25f))
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Быстрый импорт из ссылки OLX.ua",
                                color = CyberneticPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedTextField(
                                    value = olxUrlInput,
                                    onValueChange = { olxUrlInput = it },
                                    placeholder = { Text("https://www.olx.ua/...", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                FilledTonalButton(
                                    onClick = { applySharedUrl(olxUrlInput, notifyUser = true) },
                                    enabled = olxUrlInput.isNotBlank(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Ок", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название радара") },
                    placeholder = { Text("Например: iPhone 13 Pro") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Поисковый запрос OLX") },
                    placeholder = { Text("iphone 13 pro -max -чехол") },
                    supportingText = { Text("Поддерживает минус-слова прямо в запросе (через -)", fontSize = 10.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // КАТЕГОРИЯ (FlowRow — не обрезается!)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Категория поиска", color = CyberneticOnSurfaceVariant, fontSize = 12.sp)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OlxCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = categoryId == cat.id,
                                onClick = { categoryId = cat.id },
                                label = { Text(cat.label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyberneticPrimary.copy(alpha = 0.2f),
                                    selectedLabelColor = CyberneticPrimary
                                )
                            )
                        }
                    }
                }

                // ВАЛЮТА И ЦЕНА
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Цена и валюта", color = CyberneticOnSurfaceVariant, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("UAH" to "₴", "USD" to "$", "EUR" to "€").forEach { (code, sym) ->
                                val selected = priceCurrency == code
                                Surface(
                                    onClick = { priceCurrency = code },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) CyberneticPrimary.copy(alpha = 0.2f) else CyberneticSurfaceContainerLow,
                                    border = BorderStroke(1.dp, if (selected) CyberneticPrimary else CyberneticCardBorderSoft)
                                ) {
                                    Text(
                                        text = "$code ($sym)",
                                        color = if (selected) CyberneticPrimary else CyberneticOnSurfaceVariant,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = priceFrom,
                            onValueChange = { if (it.all(Char::isDigit)) priceFrom = it },
                            label = { Text("От") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = priceTo,
                            onValueChange = { if (it.all(Char::isDigit)) priceTo = it },
                            label = { Text("До") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Показывать объявления без цены", color = CyberneticOnSurfaceVariant, fontSize = 12.sp)
                        Switch(checked = includeNoPrice, onCheckedChange = { includeNoPrice = it })
                    }
                }

                // ГОРОД + БЫСТРЫЕ ГОРОДА
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = cityName,
                        onValueChange = { cityName = it },
                        label = { Text("Города (через запятую, UA/RU)") },
                        placeholder = { Text("Вся Украина или Киев, Бровары") },
                        singleLine = true,
                        trailingIcon = {
                            if (cityName.isNotBlank()) {
                                IconButton(onClick = { cityName = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(OlxCities.POPULAR_CITIES, key = { it }) { c ->
                            val currentCities = OlxCities.splitCities(cityName)
                            val selected = currentCities.any { OlxCities.citiesMatchExact(it, c) }
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val updated = if (selected) {
                                        currentCities.filterNot { OlxCities.citiesMatchExact(it, c) }
                                    } else {
                                        currentCities + c
                                    }
                                    cityName = updated.joinToString(", ")
                                },
                                label = { Text(c, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // ПРОДАВЕЦ И СОСТОЯНИЕ
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Продавец и состояние", color = CyberneticOnSurfaceVariant, fontSize = 12.sp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("any" to "Все продавцы", "private" to "Частные", "business" to "Бизнес").forEach { (k, lbl) ->
                            FilterChip(selected = ownerType == k, onClick = { ownerType = k }, label = { Text(lbl, fontSize = 11.sp) })
                        }
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("any" to "Любое сост.", "used" to "Б/у", "new" to "Новое").forEach { (k, lbl) ->
                            FilterChip(selected = productState == k, onClick = { productState = k }, label = { Text(lbl, fontSize = 11.sp) })
                        }
                    }
                }

                // РЕЖИМ УВЕДОМЛЕНИЙ И ИНТЕРВАЛ
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Уведомления и частота опроса", color = CyberneticOnSurfaceVariant, fontSize = 12.sp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = notifyMode == "new_pushup",
                            onClick = { notifyMode = "new_pushup" },
                            label = { Text("Новые + Поднятые", fontSize = 11.sp) }
                        )
                        FilterChip(
                            selected = notifyMode == "new",
                            onClick = { notifyMode = "new" },
                            label = { Text("Только новые", fontSize = 11.sp) }
                        )
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(null to "Авто", 15 to "15с", 25 to "25с", 60 to "60с", 300 to "5м").forEach { (sec, lbl) ->
                            FilterChip(
                                selected = intervalOverride == sec,
                                onClick = { intervalOverride = sec },
                                label = { Text(lbl, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // ОБЯЗАТЕЛЬНЫЕ СЛОВА
                OutlinedTextField(
                    value = mustWords,
                    onValueChange = { mustWords = it },
                    label = { Text("Обязательные слова (через запятую)") },
                    placeholder = { Text("128gb, pro") },
                    modifier = Modifier.fillMaxWidth()
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = mustWordsMode == "any",
                        onClick = { mustWordsMode = "any" },
                        label = { Text("ИЛИ (хотя бы одно)", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = mustWordsMode == "all",
                        onClick = { mustWordsMode = "all" },
                        label = { Text("И (все слова)", fontSize = 11.sp) }
                    )
                }

                // СВЕЖЕСТЬ ОБЪЯВЛЕНИЯ
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Свежесть публикации на OLX", color = CyberneticOnSurfaceVariant, fontSize = 12.sp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            "any" to "Любое",
                            "fresh" to "< 24ч",
                            "recent" to "< 3 дн",
                            "week" to "< 7 дн",
                            "old" to "Висяки >7дн"
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = publicationAge == key,
                                onClick = { publicationAge = key },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // МИНУС-СЛОВА + БЫСТРЫЕ ИСКЛЮЧЕНИЯ
                OutlinedTextField(
                    value = stopWords,
                    onValueChange = { stopWords = it },
                    label = { Text("Минус-слова (через запятую)") },
                    placeholder = { Text("коробка, чехол, запчасти") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(DEFAULT_EXCLUSION_WORDS, key = { it }) { word ->
                        val alreadyThere = stopWords.split(",").map { it.trim().lowercase() }.contains(word)
                        Surface(
                            onClick = {
                                stopWords = if (!alreadyThere) {
                                    if (stopWords.isBlank()) word else "$stopWords, $word"
                                } else {
                                    stopWords.split(",").map { it.trim() }.filterNot { it.lowercase() == word }.joinToString(", ")
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (alreadyThere) CyberneticSecondary.copy(alpha = 0.2f) else CyberneticSurfaceContainerLow,
                            border = BorderStroke(1.dp, CyberneticCardBorderSoft)
                        ) {
                            Text(
                                text = if (alreadyThere) "✓ $word" else "+ $word",
                                color = if (alreadyThere) CyberneticSecondary else CyberneticOnSurfaceVariant,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // СЕНСОР СОСТОЯНИЯ
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Сенсор состояния текста", color = CyberneticOnSurfaceVariant, fontSize = 12.sp)
                    Text(
                        text = if (conditionTarget == "any") "Выключен" else "Порог ${conditionThreshold.roundToInt()}% · ${ConditionAnalyzer.explain(conditionTarget, conditionThreshold.roundToInt())}",
                        color = CyberneticPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("any" to "Любое", "working" to "Рабочий", "broken" to "Нерабочий").forEach { (key, label) ->
                            FilterChip(
                                selected = conditionTarget == key,
                                onClick = {
                                    conditionTarget = key
                                    if (key != "any" && conditionThreshold == 0f) conditionThreshold = 50f
                                },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                    if (conditionTarget != "any") {
                        Slider(
                            value = conditionThreshold,
                            onValueChange = { conditionThreshold = it },
                            valueRange = 0f..100f
                        )
                    }
                }

                // ЖИВАЯ ПРОВЕРКА ПОИСКА НА OLX ПРЯМО В РЕДАКТОРЕ
                if (onTestWatch != null) {
                    OutlinedButton(
                        onClick = {
                            isTestingSearch = true
                            testError = null
                            testResults = null
                            scope.launch {
                                val res = onTestWatch(buildDraftWatch())
                                isTestingSearch = false
                                res.onSuccess { testResults = it }
                                res.onFailure { testError = it.message ?: "Ошибка запроса" }
                            }
                        },
                        enabled = query.isNotBlank() && !isTestingSearch,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isTestingSearch) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Проверяем выдачу OLX...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.TravelExplore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Проверить сейчас (тест поиска)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    testError?.let { err ->
                        Text(text = "Ошибка: $err", color = CyberneticError, fontSize = 11.sp)
                    }
                    AnimatedVisibility(visible = testResults != null) {
                        val list = testResults ?: emptyList()
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = CyberneticSurfaceContainerLow,
                            border = BorderStroke(1.dp, CyberneticCardBorderSoft),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (list.isEmpty()) "Совпадений пока нет — попробуйте ослабить фильтры"
                                    else "Найдено совпадений: ${list.size}",
                                    color = if (list.isEmpty()) CyberneticTertiary else CyberneticSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                list.take(5).forEach { l ->
                                    Text(
                                        text = "• ${l.title} — ${CachedFormatters.formatPrice(l.price, l.currency)} (${l.cityName})",
                                        color = CyberneticOnSurfaceVariant,
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(buildDraftWatch()) },
                enabled = query.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberneticPrimary,
                    contentColor = CyberneticOnPrimary
                )
            ) {
                Text("Сохранить", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = CyberneticOnSurfaceVariant)
            }
        }
    )
}
