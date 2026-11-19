package ua.magerko.olxradar.ui.compose

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import ua.magerko.olxradar.data.backup.RadarBackup
import ua.magerko.olxradar.data.model.ConditionAnalyzer
import ua.magerko.olxradar.data.model.OlxCategory
import ua.magerko.olxradar.data.model.Watch
import ua.magerko.olxradar.service.ServiceStatus
import ua.magerko.olxradar.ui.WatchItemUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarsManagementScreen(
    watchesUi: List<WatchItemUi>,
    radarStatus: ServiceStatus = ServiceStatus(),
    globalIntervalSec: Int = 20,
    onToggleActive: (Watch, Boolean) -> Unit,
    onDeleteWatch: (Watch) -> Unit,
    onDuplicateWatch: (Watch) -> Unit,
    onSaveWatch: (Watch) -> Unit,
    onExportBackup: suspend () -> String,
    onImportBackup: suspend (String) -> Pair<Int, Int>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var filterMode by remember { mutableStateOf("all") } // "all", "active", "paused", "new"
    var showAddDialog by remember { mutableStateOf(false) }
    var editingWatch by remember { mutableStateOf<Watch?>(null) }

    val activeCount = watchesUi.count { it.watch.isActive }
    val totalFindings = watchesUi.sumOf { it.findingsCount }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = onExportBackup()
                    context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                        os.write(json.toByteArray(Charsets.UTF_8))
                        os.flush()
                    } ?: error("Поток записи недоступен")
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
                    val json = context.contentResolver.openInputStream(uri)?.use {
                        it.readBytes().toString(Charsets.UTF_8)
                    } ?: error("Поток чтения недоступен")
                    val (imported, skipped) = onImportBackup(json)
                    Toast.makeText(
                        context,
                        if (imported == 0 && skipped == 0) "Файл не содержит радаров"
                        else "Импортировано: $imported · дубликатов пропущено: $skipped",
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
            .background(CyberneticSurface)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // TOP HEADER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Мои радары",
                        color = CyberneticOnSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    )
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
                            text = "$activeCount из ${watchesUi.size} активны",
                            color = CyberneticSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(onClick = { exportLauncher.launch(RadarBackup.DEFAULT_FILE_NAME) }) {
                        Icon(
                            imageVector = Icons.Default.IosShare,
                            contentDescription = "Сохранить радары (list.config)",
                            tint = CyberneticOnSurfaceVariant
                        )
                    }
                    IconButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Загрузить радары (list.config)",
                            tint = CyberneticOnSurfaceVariant
                        )
                    }
                }
            }

            // SUMMARY HERO DASHBOARD CARD
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .border(1.dp, CyberneticOutlineVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CyberneticSurfaceContainerHigh)
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
                                imageVector = Icons.Default.Radar,
                                contentDescription = null,
                                tint = CyberneticPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Сводка мониторинга",
                                color = CyberneticOnSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Master toggle switch
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

                    // 3-Column Metrics
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Metric 1: Limit & active
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CyberneticSurfaceContainer,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Радары",
                                    color = CyberneticOnSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${watchesUi.size}/10",
                                    color = CyberneticOnSurface,
                                    fontSize = 16.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$activeCount акт.",
                                    color = if (activeCount > 0) CyberneticSecondary else CyberneticOutline,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // Metric 2: Checked count (живой счётчик запросов)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CyberneticSurfaceContainer,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Проверено",
                                    color = CyberneticOnSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                val checksCountFormatted = java.text.NumberFormat.getIntegerInstance().format(radarStatus.checksCount)
                                Text(
                                    text = checksCountFormatted,
                                    color = CyberneticSecondary,
                                    fontSize = 16.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                val lastPollText = radarStatus.lastCheckedAt?.let {
                                    val sec = ((System.currentTimeMillis() - it) / 1000).coerceAtLeast(0)
                                    when {
                                        sec < 5 -> "только что"
                                        sec < 60 -> "$sec с назад"
                                        sec < 3600 -> "${sec / 60}м назад"
                                        else -> "${sec / 3600}ч назад"
                                    }
                                } ?: if (radarStatus.isRunning) "опрос..." else "остановлен"
                                Text(
                                    text = lastPollText,
                                    color = CyberneticOnSurfaceVariant.copy(alpha = 0.75f),
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        // Metric 3: Total deals
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CyberneticSurfaceContainer,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Находок",
                                    color = CyberneticOnSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "$totalFindings",
                                    color = CyberneticTertiary,
                                    fontSize = 16.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                val activeWithFindings = watchesUi.count { it.findingsCount > 0 }
                                Text(
                                    text = "$activeWithFindings с лотами",
                                    color = CyberneticOnSurfaceVariant.copy(alpha = 0.75f),
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // FILTER CHIPS ROW
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = filterMode == "all",
                        onClick = { filterMode = "all" },
                        label = { Text("Все (${watchesUi.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberneticPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = CyberneticPrimary,
                            containerColor = CyberneticSurfaceContainerHigh,
                            labelColor = CyberneticOnSurfaceVariant
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = filterMode == "active",
                        onClick = { filterMode = "active" },
                        label = { Text("Активные ($activeCount)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberneticPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = CyberneticPrimary,
                            containerColor = CyberneticSurfaceContainerHigh,
                            labelColor = CyberneticOnSurfaceVariant
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = filterMode == "paused",
                        onClick = { filterMode = "paused" },
                        label = { Text("На паузе (${watchesUi.size - activeCount})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberneticPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = CyberneticPrimary,
                            containerColor = CyberneticSurfaceContainerHigh,
                            labelColor = CyberneticOnSurfaceVariant
                        )
                    )
                }
                item {
                    FilterChip(
                        selected = filterMode == "new",
                        onClick = { filterMode = "new" },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(CyberneticSecondary)
                                )
                                Text("С находками")
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberneticPrimary.copy(alpha = 0.2f),
                            selectedLabelColor = CyberneticPrimary,
                            containerColor = CyberneticSurfaceContainerHigh,
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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = null,
                            tint = CyberneticOutline,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Радары не найдены",
                            color = CyberneticOnSurfaceVariant,
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                AdaptiveRadarList(
                    items = filteredList,
                    radarStatus = radarStatus,
                    globalIntervalSec = globalIntervalSec,
                    modifier = Modifier.weight(1f),
                    onItemClick = { editingWatch = it.watch },
                    onToggleActive = { watch, active -> onToggleActive(watch, active) },
                    onDuplicate = { onDuplicateWatch(it) },
                    onDelete = { onDeleteWatch(it) }
                )
            }
        }

        // FAB: "Добавить радар"
        ExtendedFloatingActionButton(
            onClick = { showAddDialog = true },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Добавить радар", fontWeight = FontWeight.Bold) },
            containerColor = CyberneticSecondary,
            contentColor = CyberneticOnSecondary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 76.dp, end = 16.dp)
        )
    }

    // Add / Edit Radar Dialog
    if (showAddDialog || editingWatch != null) {
        val watchToEdit = editingWatch
        AddEditRadarDialog(
            initialWatch = watchToEdit,
            onDismiss = {
                showAddDialog = false
                editingWatch = null
            },
            onSave = { saved ->
                onSaveWatch(saved)
                showAddDialog = false
                editingWatch = null
            }
        )
    }
}


@Composable
fun AdaptiveRadarList(
    items: List<WatchItemUi>,
    radarStatus: ServiceStatus = ServiceStatus(),
    globalIntervalSec: Int = 20,
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
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items, key = { it.watch.id }) { itemUi ->
                RadarCard(
                    itemUi = itemUi,
                    radarStatus = radarStatus,
                    globalIntervalSec = globalIntervalSec,
                    onToggleActive = { active -> onToggleActive(itemUi.watch, active) },
                    onEdit = { onItemClick(itemUi) },
                    onDuplicate = { onDuplicate(itemUi.watch) },
                    onDelete = { onDelete(itemUi.watch) }
                )
            }
        }
    }
}

@Composable
fun RadarCard(
    itemUi: WatchItemUi,
    radarStatus: ServiceStatus = ServiceStatus(),
    globalIntervalSec: Int = 20,
    onToggleActive: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val watch = itemUi.watch
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CyberneticOutlineVariant.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .clickable { onEdit() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (watch.isActive) CyberneticSurfaceContainer else CyberneticSurfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Row: Avatar + Title & Query + Switch
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
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CyberneticSurfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = null,
                            tint = if (watch.isActive) CyberneticPrimary else CyberneticOutline,
                            modifier = Modifier.size(24.dp)
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(2.dp)
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
                            text = "Запрос: \"${watch.query}\"",
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

            // Badges Strip (FlowRow so badges wrap neatly without clipping or creating blank spaces)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Price Range Badge
                val priceText = when {
                    watch.priceFrom != null && watch.priceTo != null -> "${watch.priceFrom} – ${watch.priceTo} ₴"
                    watch.priceFrom != null -> "от ${watch.priceFrom} ₴"
                    watch.priceTo != null -> "до ${watch.priceTo} ₴"
                    else -> "Любая цена"
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CyberneticSurfaceContainerHighest
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Payments,
                            contentDescription = null,
                            tint = CyberneticTertiary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = priceText,
                            color = CyberneticOnSurface,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Delivery / Region
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CyberneticSurfaceContainerHighest
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = CyberneticPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = watch.cityName ?: "OLX Доставка",
                            color = CyberneticOnSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Must Words Badge
                if (watch.mustWords.isNotBlank()) {
                    val modeLabel = if (watch.mustWordsMode == "all") "И" else "ИЛИ"
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CyberneticPrimary.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "[$modeLabel] ${watch.mustWords}",
                            color = CyberneticPrimary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                // Publication Age Badge (Свежесть объявления)
                if (watch.publicationAge.isNotBlank() && watch.publicationAge != "any") {
                    val (ageLabel, ageColor) = when (watch.publicationAge) {
                        "fresh" -> "Свежие (<24ч)" to Color(0xFF2E7D32)
                        "recent" -> "Недавние (<3дн)" to CyberneticPrimary
                        "week" -> "За неделю" to CyberneticOnSurfaceVariant
                        "old" -> "Висяки (>7дн)" to CyberneticTertiary
                        "very_old" -> "Висяки (>14дн)" to CyberneticTertiary
                        else -> watch.publicationAge to CyberneticPrimary
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ageColor.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = ageColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = ageLabel,
                                color = ageColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Findings Count
                if (itemUi.findingsCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CyberneticSecondary.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = CyberneticSecondary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "+${itemUi.findingsCount} находок",
                                color = CyberneticSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Bottom Telemetry Strip & Actions
            Divider(color = CyberneticOutlineVariant.copy(alpha = 0.2f), thickness = 1.dp)

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
                    val hasError = !watchLive?.lastError.isNullOrBlank() || !watch.lastError.isNullOrBlank()
                    val errorMsg = watchLive?.lastError ?: watch.lastError
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
                        val displayMsg = if (is500) "OLX занят (500) · повтор..." else "Ошибка: ${errorMsg?.take(22)}..."
                        Text(
                            text = displayMsg,
                            color = if (is500) CyberneticTertiary else CyberneticError,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        val agoStr = lastSuccess?.let {
                            val sec = ((System.currentTimeMillis() - it) / 1000).coerceAtLeast(0)
                            when {
                                sec < 10 -> "только что"
                                sec < 60 -> "${sec}с назад"
                                sec < 3600 -> "${sec / 60}м назад"
                                else -> "${sec / 3600}ч назад"
                            }
                        } ?: "ожидает"
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

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onDuplicate,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Дублировать",
                            tint = CyberneticOnSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Удалить",
                            tint = CyberneticError,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
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

@Composable
fun AddEditRadarDialog(
    initialWatch: Watch?,
    onDismiss: () -> Unit,
    onSave: (Watch) -> Unit
) {
    var title by remember { mutableStateOf(initialWatch?.title ?: "") }
    var query by remember { mutableStateOf(initialWatch?.query ?: "") }
    var priceFrom by remember { mutableStateOf(initialWatch?.priceFrom?.toString() ?: "") }
    var priceTo by remember { mutableStateOf(initialWatch?.priceTo?.toString() ?: "") }
    var stopWords by remember { mutableStateOf(initialWatch?.stopWords ?: "") }
    var mustWords by remember { mutableStateOf(initialWatch?.mustWords ?: "") }
    var mustWordsMode by remember { mutableStateOf(initialWatch?.mustWordsMode ?: "any") }
    var publicationAge by remember { mutableStateOf(initialWatch?.publicationAge ?: "any") }
    var categoryId by remember { mutableStateOf(initialWatch?.categoryId ?: OlxCategory.ALL.id) }
    var conditionTarget by remember { mutableStateOf(initialWatch?.conditionTarget ?: "any") }
    var conditionThreshold by remember { mutableFloatStateOf((initialWatch?.conditionThreshold ?: 0).coerceIn(0, 100).toFloat()) }
    var cityName by remember { mutableStateOf(initialWatch?.cityName ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberneticSurfaceContainer,
        title = {
            Text(
                text = if (initialWatch == null) "Новый радар" else "Редактировать радар",
                color = CyberneticOnSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название радара") },
                    placeholder = { Text("Например: iPhone 13 Pro") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CyberneticOnSurface,
                        unfocusedTextColor = CyberneticOnSurface,
                        focusedBorderColor = CyberneticPrimary,
                        unfocusedBorderColor = CyberneticOutlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Поисковый запрос OLX") },
                    placeholder = { Text("iphone 13 pro -max") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CyberneticOnSurface,
                        unfocusedTextColor = CyberneticOnSurface,
                        focusedBorderColor = CyberneticPrimary,
                        unfocusedBorderColor = CyberneticOutlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // КАТЕГОРИЯ
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Категория поиска",
                        color = CyberneticOnSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = priceFrom,
                        onValueChange = { priceFrom = it },
                        label = { Text("Цена от (₴)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = CyberneticOnSurface,
                            unfocusedTextColor = CyberneticOnSurface,
                            focusedBorderColor = CyberneticPrimary,
                            unfocusedBorderColor = CyberneticOutlineVariant
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = priceTo,
                        onValueChange = { priceTo = it },
                        label = { Text("Цена до (₴)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = CyberneticOnSurface,
                            unfocusedTextColor = CyberneticOnSurface,
                            focusedBorderColor = CyberneticPrimary,
                            unfocusedBorderColor = CyberneticOutlineVariant
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = cityName,
                    onValueChange = { cityName = it },
                    label = { Text("Город / Регион (опционально)") },
                    placeholder = { Text("Киев") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CyberneticOnSurface,
                        unfocusedTextColor = CyberneticOnSurface,
                        focusedBorderColor = CyberneticPrimary,
                        unfocusedBorderColor = CyberneticOutlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // ОБЯЗАТЕЛЬНЫЕ СЛОВА
                OutlinedTextField(
                    value = mustWords,
                    onValueChange = { mustWords = it },
                    label = { Text("Обязательные слова (через запятую)") },
                    placeholder = { Text("робочий, идеальное состояние") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CyberneticOnSurface,
                        unfocusedTextColor = CyberneticOnSurface,
                        focusedBorderColor = CyberneticPrimary,
                        unfocusedBorderColor = CyberneticOutlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // РЕЖИМ СОВПАДЕНИЯ ОБЯЗАТЕЛЬНЫХ СЛОВ (И / ИЛИ)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Правило обязательных слов:",
                        color = CyberneticOnSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = mustWordsMode == "any",
                            onClick = { mustWordsMode = "any" },
                            label = { Text("ИЛИ (хотя бы одно)", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberneticPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = CyberneticPrimary,
                                containerColor = CyberneticSurfaceContainerLow,
                                labelColor = CyberneticOnSurfaceVariant
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = mustWordsMode == "any",
                                borderColor = CyberneticOutlineVariant,
                                selectedBorderColor = CyberneticPrimary.copy(alpha = 0.5f)
                            )
                        )
                        FilterChip(
                            selected = mustWordsMode == "all",
                            onClick = { mustWordsMode = "all" },
                            label = { Text("И (все слова)", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberneticPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = CyberneticPrimary,
                                containerColor = CyberneticSurfaceContainerLow,
                                labelColor = CyberneticOnSurfaceVariant
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = mustWordsMode == "all",
                                borderColor = CyberneticOutlineVariant,
                                selectedBorderColor = CyberneticPrimary.copy(alpha = 0.5f)
                            )
                        )
                    }
                }

                // СВЕЖЕСТЬ ОБЪЯВЛЕНИЯ (ДАТА ПУБЛИКАЦИИ НА OLX)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Свежесть объявления (дата публикации):",
                        color = CyberneticOnSurfaceVariant,
                        fontSize = 12.sp
                    )
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            "any" to "Все (любое)",
                            "fresh" to "Свежие (< 24 ч)",
                            "recent" to "Недавние (< 3 дн)",
                            "week" to "За неделю (< 7 дн)",
                            "old" to "Висяки (> 7 дн)"
                        ).forEach { (key, label) ->
                            FilterChip(
                                selected = publicationAge == key,
                                onClick = { publicationAge = key },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CyberneticPrimary.copy(alpha = 0.2f),
                                    selectedLabelColor = CyberneticPrimary,
                                    containerColor = CyberneticSurfaceContainerLow,
                                    labelColor = CyberneticOnSurfaceVariant
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = publicationAge == key,
                                    borderColor = CyberneticOutlineVariant,
                                    selectedBorderColor = CyberneticPrimary.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }

                // БЫСТРЫЕ ИСКЛЮЧЕНИЯ
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Быстрые исключения — нажмите, чтобы добавить к минус-словам",
                        color = CyberneticOnSurfaceVariant,
                        fontSize = 11.sp
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(DEFAULT_EXCLUSION_WORDS) { word ->
                            val alreadyThere = stopWords.split(",")
                                .map { it.trim().lowercase() }
                                .contains(word)
                            Surface(
                                onClick = {
                                    if (!alreadyThere) {
                                        stopWords = if (stopWords.isBlank()) word else "$stopWords, $word"
                                    } else {
                                        val cleaned = stopWords.split(",")
                                            .map { it.trim() }
                                            .filterNot { it.lowercase() == word }
                                            .joinToString(", ")
                                        stopWords = cleaned
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (alreadyThere) CyberneticSecondary.copy(alpha = 0.2f) else CyberneticSurfaceContainerHigh,
                                border = if (alreadyThere) null else BorderStroke(1.dp, CyberneticOutlineVariant.copy(alpha = 0.5f))
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
                }

                // СЕНСОР СОСТОЯНИЯ
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Сенсор состояния",
                        color = CyberneticOnSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Text(
                        text = if (conditionTarget == "any")
                            "Выключен — все объявления проходят"
                        else
                            "Порог ${conditionThreshold.roundToInt()}% · ${ConditionAnalyzer.explain(conditionTarget, conditionThreshold.roundToInt())}",
                        color = CyberneticPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("any" to "Любое", "working" to "Рабочий", "broken" to "Нерабочий")
                            .forEach { (key, label) ->
                                FilterChip(
                                    selected = conditionTarget == key,
                                    onClick = {
                                        conditionTarget = key
                                        if (key != "any" && conditionThreshold == 0f) conditionThreshold = 50f
                                    },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyberneticPrimary.copy(alpha = 0.2f),
                                        selectedLabelColor = CyberneticPrimary
                                    )
                                )
                            }
                    }
                    if (conditionTarget != "any") {
                        Slider(
                            value = conditionThreshold,
                            onValueChange = { conditionThreshold = it },
                            valueRange = 0f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = CyberneticPrimary,
                                activeTrackColor = CyberneticPrimary,
                                inactiveTrackColor = CyberneticSurfaceContainerHighest
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = stopWords,
                    onValueChange = { stopWords = it },
                    label = { Text("Минус-слова (через запятую)") },
                    placeholder = { Text("коробка, чехол, запчасти") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CyberneticOnSurface,
                        unfocusedTextColor = CyberneticOnSurface,
                        focusedBorderColor = CyberneticPrimary,
                        unfocusedBorderColor = CyberneticOutlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalWatch = (initialWatch ?: Watch(title = "", query = "")).copy(
                        title = title.ifBlank { query },
                        query = query,
                        priceFrom = priceFrom.toIntOrNull(),
                        priceTo = priceTo.toIntOrNull(),
                        cityName = cityName.ifBlank { null },
                        stopWords = stopWords,
                        mustWords = mustWords,
                        mustWordsMode = mustWordsMode,
                        publicationAge = publicationAge,
                        categoryId = if (categoryId == OlxCategory.ALL.id) null else categoryId,
                        conditionTarget = conditionTarget,
                        conditionThreshold = if (conditionTarget == "any") 0 else conditionThreshold.roundToInt()
                    )
                    onSave(finalWatch)
                },
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
