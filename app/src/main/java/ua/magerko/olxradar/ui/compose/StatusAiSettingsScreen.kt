package ua.magerko.olxradar.ui.compose

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.magerko.olxradar.BuildConfig
import ua.magerko.olxradar.ai.AiProvider
import ua.magerko.olxradar.ai.AiRuntime
import ua.magerko.olxradar.ai.AiStatus
import ua.magerko.olxradar.ai.GroqVisionClient
import ua.magerko.olxradar.data.backup.PersistentStorageManager
import ua.magerko.olxradar.data.backup.RadarBackup
import ua.magerko.olxradar.data.model.SellerBlacklist
import ua.magerko.olxradar.data.prefs.AppPreferences
import ua.magerko.olxradar.service.MonitorWatchdogWorker
import ua.magerko.olxradar.service.ServiceStatus
import ua.magerko.olxradar.service.TelegramNotifier
import ua.magerko.olxradar.ui.StatusViewModel
import ua.magerko.olxradar.util.AppLog
import java.util.Locale

enum class SettingsCategory(val label: String, val icon: ImageVector) {
    ALL("Все", Icons.Default.Apps),
    APPEARANCE("Дизайн", Icons.Default.Palette),
    SEARCH("Поиск", Icons.Default.TravelExplore),
    NOTIFICATIONS("Пуши", Icons.Default.NotificationsActive),
    AI("Нейросеть", Icons.Default.AutoAwesome),
    STORAGE("Файлы", Icons.Default.FolderShared)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatusAiSettingsScreen(
    statusViewModel: StatusViewModel,
    onBack: (() -> Unit)? = null,
    isTabVisible: Boolean = true
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val appSettings by statusViewModel.appSettings.collectAsState()
    val radarStatus by statusViewModel.radarStatus.collectAsState()
    val watches by statusViewModel.watches.collectAsState()
    val currentInterval by statusViewModel.currentInterval.collectAsState()

    val quietEnabled by statusViewModel.quietHoursEnabled.collectAsState()
    val quietFromHour by statusViewModel.quietHoursFromHour.collectAsState()
    val quietFromMin by statusViewModel.quietHoursFromMin.collectAsState()
    val quietToHour by statusViewModel.quietHoursToHour.collectAsState()
    val quietToMin by statusViewModel.quietHoursToMin.collectAsState()

    val aiEnabled by statusViewModel.aiEnabled.collectAsState()
    val aiProvider by statusViewModel.aiProvider.collectAsState()
    val aiBaseUrl by statusViewModel.aiBaseUrl.collectAsState()
    val aiApiKeyMasked by statusViewModel.aiApiKeyMasked.collectAsState()
    val aiModelId by statusViewModel.aiModelId.collectAsState()
    val isModelVerified by statusViewModel.isModelVerified.collectAsState()

    val isBackupAiEnabled by statusViewModel.isBackupAiEnabled.collectAsState()
    val backupProvider by statusViewModel.backupProvider.collectAsState()
    val backupBaseUrl by statusViewModel.backupBaseUrl.collectAsState()
    val backupApiKeyMasked by statusViewModel.backupApiKeyMasked.collectAsState()
    val backupModelId by statusViewModel.backupModelId.collectAsState()
    val isBackupModelVerified by statusViewModel.isBackupModelVerified.collectAsState()

    val aiConfidence by statusViewModel.aiConfidence.collectAsState()
    val aiMaxPhotos by statusViewModel.aiMaxPhotos.collectAsState()
    val aiCheckSuspicious by statusViewModel.aiCheckSuspicious.collectAsState()
    val aiStrictMode by statusViewModel.aiStrictMode.collectAsState()
    val aiManualReview by statusViewModel.aiManualReview.collectAsState()
    val aiNotifyRejected by statusViewModel.aiNotifyRejected.collectAsState()
    val aiMaxConcurrent by statusViewModel.aiMaxConcurrent.collectAsState()
    val aiTimeoutSec by statusViewModel.aiTimeoutSec.collectAsState()

    var selectedCategory by remember { mutableStateOf(SettingsCategory.ALL) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var processPssMb by remember { mutableIntStateOf(32) }
    LaunchedEffect(isTabVisible) {
        if (!isTabVisible) return@LaunchedEffect
        while (true) {
            val pss = withContext(Dispatchers.IO) {
                val mi = Debug.MemoryInfo()
                Debug.getMemoryInfo(mi)
                (mi.totalPss / 1024).coerceAtLeast(1)
            }
            processPssMb = pss
            nowMs = System.currentTimeMillis()
            delay(5000)
        }
    }

    val prefsDev = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    var devMode by remember { mutableStateOf(prefsDev.getBoolean("dev_mode", false)) }

    var showProviderConfigDialog by remember { mutableStateOf(false) }
    var showBackupProviderConfigDialog by remember { mutableStateOf(false) }
    var showAiThoughtsDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var totalDbFindings by remember { mutableIntStateOf(0) }
    LaunchedEffect(selectedCategory) {
        totalDbFindings = statusViewModel.getTotalFindingsCount()
    }

    val aiThoughtLogs by ua.magerko.olxradar.ai.AiThoughtLogger.logsFlow.collectAsState()
    val blockedSellers by statusViewModel.blockedSellers.collectAsState()

    var showExportPasswordDialog by remember { mutableStateOf(false) }
    var pendingExportPassword by remember { mutableStateOf<String?>(null) }
    var pendingEncryptedBackupJson by remember { mutableStateOf<String?>(null) }

    val exportFullLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val json = statusViewModel.buildFullBackupJson(password = pendingExportPassword)
                    pendingExportPassword = null
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                            os.write(json.toByteArray(Charsets.UTF_8))
                            os.flush()
                        } ?: error("Поток записи недоступен")
                    }
                    Toast.makeText(context, "Полный бэкап экспортирован!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    pendingExportPassword = null
                    Toast.makeText(context, "Ошибка экспорта: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } else {
            pendingExportPassword = null
        }
    }

    val importFullLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val json = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use {
                            it.readBytes().toString(Charsets.UTF_8)
                        } ?: error("Поток чтения недоступен")
                    }
                    if (RadarBackup.isEncryptedBackup(json)) {
                        pendingEncryptedBackupJson = json
                    } else {
                        val res = statusViewModel.restoreFromFullBackupJson(json)
                        totalDbFindings = statusViewModel.getTotalFindingsCount()
                        Toast.makeText(
                            context,
                            "Восстановлено радаров: ${res.watchesImported}, находок: ${res.findingsImported}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Ошибка восстановления: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val safFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, flags)
                AppPreferences.setSafBackupTreeUri(context, uri.toString())
                coroutineScope.launch {
                    val res = statusViewModel.runBackupNow()
                    Toast.makeText(
                        context,
                        if (res.success) "Папка привязана и бэкап сохранён!" else "Папка привязана (${res.error})",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Не удалось закрепить папку: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun showSection(cat: SettingsCategory): Boolean =
        selectedCategory == SettingsCategory.ALL || selectedCategory == cat

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        StatusAiTopHeader(onBack = onBack)

        // Category Filter Pills
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(SettingsCategory.entries, key = { it.name }) { cat ->
                val isSelected = selectedCategory == cat
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedCategory = cat },
                    leadingIcon = {
                        Icon(
                            imageVector = cat.icon,
                            contentDescription = null,
                            tint = if (isSelected) CyberneticPrimary else CyberneticOnSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    label = {
                        Text(
                            text = cat.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
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

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (showSection(SettingsCategory.APPEARANCE)) {
                item(key = "appearance") {
                    AppearanceAndUiCard(
                        context = context,
                        appSettings = appSettings,
                        onChanged = { PersistentStorageManager.scheduleAutoBackup(context) }
                    )
                }
            }

            if (showSection(SettingsCategory.SEARCH)) {
                item(key = "diagnostics") {
                    DiagnosticsAndMonitoringCard(
                        context = context,
                        radarStatus = radarStatus,
                        watches = watches,
                        checksCount = radarStatus.checksCount,
                        pssMb = processPssMb,
                        currentIntervalSec = currentInterval,
                        nowMs = nowMs,
                        onSelectInterval = { statusViewModel.setInterval(it) },
                        onTogglePause = { statusViewModel.toggleMonitoringPause() }
                    )
                }

                item(key = "smart_search") {
                    SmartSearchSettingsCard(
                        context = context,
                        appSettings = appSettings,
                        onChanged = { PersistentStorageManager.scheduleAutoBackup(context) }
                    )
                }

                item(key = "bg_stability") {
                    BackgroundStabilityCard(
                        context = context,
                        appSettings = appSettings
                    )
                }

                item(key = "seller_blacklist") {
                    SellerBlacklistCard(
                        blockedSellers = blockedSellers,
                        onUnblock = { statusViewModel.unblockSeller(it) },
                        onClearAll = { statusViewModel.clearSellerBlacklist() }
                    )
                }
            }

            if (showSection(SettingsCategory.NOTIFICATIONS)) {
                item(key = "notifications") {
                    NotificationsAndQuietHoursCard(
                        context = context,
                        appSettings = appSettings,
                        quietEnabled = quietEnabled,
                        quietFromHour = quietFromHour,
                        quietFromMin = quietFromMin,
                        quietToHour = quietToHour,
                        quietToMin = quietToMin,
                        onToggleQuiet = { statusViewModel.setQuietHoursEnabled(it) },
                        onSetQuietFrom = { h, m -> statusViewModel.setQuietHoursFrom(h, m) },
                        onSetQuietTo = { h, m -> statusViewModel.setQuietHoursTo(h, m) },
                        onChanged = { PersistentStorageManager.scheduleAutoBackup(context) }
                    )
                }

                item(key = "telegram_forwarding") {
                    TelegramForwardingCard(
                        context = context,
                        appSettings = appSettings,
                        onChanged = { PersistentStorageManager.scheduleAutoBackup(context) }
                    )
                }
            }

            if (showSection(SettingsCategory.AI)) {
                item(key = "ai_activity") {
                    AiLiveActivityCard(aiEnabled = aiEnabled, nowMs = nowMs)
                }

                item(key = "ai_settings") {
                    AiSettingsCard(
                        aiEnabled = aiEnabled,
                        onToggleAiEnabled = { statusViewModel.setAiEnabled(it) },
                        aiProvider = aiProvider,
                        aiModelId = aiModelId,
                        aiBaseUrl = aiBaseUrl,
                        aiApiKeyMasked = aiApiKeyMasked,
                        isModelVerified = isModelVerified,
                        onOpenProviderConfig = { showProviderConfigDialog = true },
                        onTestPrimaryApi = { statusViewModel.testGroqApi() },
                        isBackupAiEnabled = isBackupAiEnabled,
                        onToggleBackupAi = { statusViewModel.setBackupAiEnabled(it) },
                        backupProvider = backupProvider,
                        backupModelId = backupModelId,
                        backupBaseUrl = backupBaseUrl,
                        backupApiKeyMasked = backupApiKeyMasked,
                        isBackupModelVerified = isBackupModelVerified,
                        onOpenBackupConfig = { showBackupProviderConfigDialog = true },
                        onTestBackupApi = { statusViewModel.testBackupApi() },
                        aiConfidence = aiConfidence,
                        onSelectConfidence = { statusViewModel.setConfidence(it) },
                        aiMaxPhotos = aiMaxPhotos,
                        onSelectMaxPhotos = { statusViewModel.setMaxPhotos(it) },
                        aiMaxConcurrent = aiMaxConcurrent,
                        onSelectMaxConcurrent = { statusViewModel.setMaxConcurrent(it) },
                        aiTimeoutSec = aiTimeoutSec,
                        onSelectTimeoutSec = { statusViewModel.setTimeoutSec(it) }
                    )
                }

                item(key = "filtration_rules") {
                    FiltrationRulesCard(
                        aiCheckSuspicious = aiCheckSuspicious,
                        aiStrictMode = aiStrictMode,
                        aiManualReview = aiManualReview,
                        aiNotifyRejected = aiNotifyRejected,
                        onCheckSuspiciousChange = { statusViewModel.setCheckSuspicious(it) },
                        onStrictModeChange = { statusViewModel.setStrictMode(it) },
                        onManualReviewChange = { statusViewModel.setManualReview(it) },
                        onNotifyRejectedChange = { statusViewModel.setAiNotifyRejected(it) }
                    )
                }
            }

            if (showSection(SettingsCategory.STORAGE)) {
                item(key = "storage") {
                    StorageAndUninstallBackupCard(
                        context = context,
                        appSettings = appSettings,
                        totalDbFindings = totalDbFindings,
                        nowMs = nowMs,
                        onBackupNow = {
                            coroutineScope.launch {
                                val res = statusViewModel.runBackupNow()
                                Toast.makeText(
                                    context,
                                    if (res.success) "Бэкап сохранён в ${res.locationDescription}"
                                    else "Ошибка бэкапа: ${res.error}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        onPickSafFolder = { safFolderLauncher.launch(null) },
                        onExportFull = { showExportPasswordDialog = true },
                        onImportFull = { importFullLauncher.launch(arrayOf("*/*")) },
                        onClearAiCache = {
                            statusViewModel.clearAiCacheOnly()
                            Toast.makeText(context, "ИИ-кэш очищен", Toast.LENGTH_SHORT).show()
                        },
                        onClearRejected = {
                            statusViewModel.clearRejectedFindings()
                            Toast.makeText(context, "Архив отклонённых очищен", Toast.LENGTH_SHORT).show()
                        },
                        onClearAllFindings = { showClearAllDialog = true }
                    )
                }
            }

            item(key = "about") {
                AboutAppCard(
                    context = context,
                    prefsDev = prefsDev,
                    devMode = devMode,
                    onDevModeChange = { devMode = it }
                )
            }

            if (devMode) {
                item(key = "dev_logs") {
                    DevLogsCard(
                        context = context,
                        aiThoughtLogsCount = aiThoughtLogs.size,
                        onOpenAiThoughts = { showAiThoughtsDialog = true }
                    )
                }
            }
        }
    }

    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            containerColor = CyberneticModalSurface,
            title = { Text("Удалить все находки?", color = CyberneticOnSurface, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Вся история найденных объявлений и ИИ-кэш будут удалены из локальной базы данных. Радары останутся.",
                    color = CyberneticOnSurfaceVariant,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        statusViewModel.clearAllFindings()
                        totalDbFindings = 0
                        showClearAllDialog = false
                        Toast.makeText(context, "Находки очищены", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberneticError, contentColor = CyberneticOnError)
                ) {
                    Text("Удалить", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("Отмена", color = CyberneticOnSurfaceVariant)
                }
            }
        )
    }

    if (showExportPasswordDialog) {
        var exportPwd by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showExportPasswordDialog = false },
            containerColor = CyberneticModalSurface,
            title = { Text("Экспорт резервной копии", color = CyberneticOnSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Оставьте пароль пустым для обычного JSON, или введите пароль для шифрования AES-256 (защищает API-ключи и данные).",
                        color = CyberneticOnSurfaceVariant,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = exportPwd,
                        onValueChange = { exportPwd = it },
                        label = { Text("Пароль (необязательно)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    pendingExportPassword = exportPwd.trim().takeIf { it.isNotEmpty() }
                    showExportPasswordDialog = false
                    exportFullLauncher.launch(RadarBackup.FULL_BACKUP_FILE_NAME)
                }) {
                    Text(if (exportPwd.isBlank()) "Экспорт JSON" else "Зашифровать и сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportPasswordDialog = false }) { Text("Отмена") }
            }
        )
    }

    pendingEncryptedBackupJson?.let { encryptedJson ->
        var importPwd by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingEncryptedBackupJson = null },
            containerColor = CyberneticModalSurface,
            title = { Text("Зашифрованный бэкап", color = CyberneticOnSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Введите пароль, которым был зашифрован этот файл:", color = CyberneticOnSurfaceVariant, fontSize = 12.sp)
                    OutlinedTextField(
                        value = importPwd,
                        onValueChange = { importPwd = it },
                        label = { Text("Пароль") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = importPwd.isNotBlank(),
                    onClick = {
                        coroutineScope.launch {
                            try {
                                val res = statusViewModel.restoreFromFullBackupJson(encryptedJson, importPwd.trim())
                                totalDbFindings = statusViewModel.getTotalFindingsCount()
                                pendingEncryptedBackupJson = null
                                Toast.makeText(
                                    context,
                                    "Восстановлено радаров: ${res.watchesImported}, находок: ${res.findingsImported}",
                                    Toast.LENGTH_LONG
                                ).show()
                            } catch (_: Exception) {
                                Toast.makeText(context, "Неверный пароль или повреждённый файл", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) { Text("Расшифровать") }
            },
            dismissButton = {
                TextButton(onClick = { pendingEncryptedBackupJson = null }) { Text("Отмена") }
            }
        )
    }

    if (showProviderConfigDialog) {
        AiProviderConfigDialog(
            dialogTitle = "Основной провайдер и модель",
            currentProvider = aiProvider,
            currentBaseUrl = aiBaseUrl,
            currentModelId = aiModelId,
            currentApiKeyMasked = aiApiKeyMasked,
            onDismiss = { showProviderConfigDialog = false },
            onApply = { provider, baseUrl, apiKey, modelId ->
                statusViewModel.applyAiConfig(provider, baseUrl, apiKey, modelId)
                showProviderConfigDialog = false
                Toast.makeText(context, "ИИ-конфигурация сохранена!", Toast.LENGTH_SHORT).show()
            },
            onTestVision = { provider, baseUrl, apiKey, modelId ->
                statusViewModel.testVisionCapability(provider, baseUrl, apiKey, modelId)
            },
            onFetchModels = { baseUrl, apiKey ->
                statusViewModel.fetchModels(baseUrl, apiKey)
            }
        )
    }

    if (showBackupProviderConfigDialog) {
        AiProviderConfigDialog(
            dialogTitle = "Резервный провайдер (Failover)",
            currentProvider = backupProvider,
            currentBaseUrl = backupBaseUrl,
            currentModelId = backupModelId,
            currentApiKeyMasked = backupApiKeyMasked,
            onDismiss = { showBackupProviderConfigDialog = false },
            onApply = { provider, baseUrl, apiKey, modelId ->
                statusViewModel.applyBackupAiConfig(provider, baseUrl, apiKey, modelId)
                showBackupProviderConfigDialog = false
                Toast.makeText(context, "Резервный провайдер сохранён!", Toast.LENGTH_SHORT).show()
            },
            onTestVision = { provider, baseUrl, apiKey, modelId ->
                statusViewModel.testVisionCapability(provider, baseUrl, apiKey, modelId)
            },
            onFetchModels = { baseUrl, apiKey ->
                statusViewModel.fetchModels(baseUrl, apiKey)
            }
        )
    }

    if (showAiThoughtsDialog) {
        AiThoughtsDialog(
            logs = aiThoughtLogs,
            onDismiss = { showAiThoughtsDialog = false }
        )
    }
}

@Composable
private fun StatusAiTopHeader(onBack: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = CyberneticOnSurface
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Центр управления",
                color = CyberneticOnSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.4).sp
            )
            Text(
                text = "Дизайн · Поиск OLX · Пуши · ИИ · Бэкапы",
                color = CyberneticOnSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun AppearanceAndUiCard(
    context: Context,
    appSettings: AppPreferences.Snapshot,
    onChanged: () -> Unit
) {
    val activeMode = currentThemeMode

    RadarSurfaceCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ДИЗАЙН И ИНТЕРФЕЙС",
                    color = CyberneticPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
                Text(
                    text = activeMode.title,
                    color = CyberneticOnSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Горизонтальная галерея тем оформления (7 тем)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(ThemeMode.entries, key = { it.name }) { mode ->
                    val isSelected = activeMode == mode
                    Surface(
                        onClick = {
                            ThemePreferences.set(context, mode)
                            onChanged()
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) CyberneticPrimary.copy(alpha = 0.14f) else CyberneticSurfaceContainerLow,
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) CyberneticPrimary else CyberneticCardBorderSoft
                        ),
                        modifier = Modifier.width(126.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(mode.surfacePreview)
                                        .border(1.dp, CyberneticCardBorder, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(9.dp)
                                                .clip(CircleShape)
                                                .background(mode.primaryPreview)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(9.dp)
                                                .clip(CircleShape)
                                                .background(mode.secondaryPreview)
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = CyberneticPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = mode.title,
                                color = CyberneticOnSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = mode.subtitle,
                                color = CyberneticOnSurfaceVariant,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = CyberneticCardBorderSoft, thickness = 1.dp)

            RuleToggleItem(
                title = "Компактные карточки находок",
                description = "Показывает мини-карточки в одну строку — больше лотов на экране и меньше прокрутки",
                checked = appSettings.compactCards,
                onCheckedChange = {
                    AppPreferences.setCompactCards(context, it)
                    onChanged()
                }
            )
            HorizontalDivider(color = CyberneticCardBorderSoft, thickness = 1.dp)

            RuleToggleItem(
                title = "Показывать выгоду от рынка (-%)",
                description = "Рассчитывает медианную цену радара и выделяет дешевые предложения",
                checked = appSettings.showMarketDiff,
                onCheckedChange = {
                    AppPreferences.setShowMarketDiff(context, it)
                    onChanged()
                }
            )
            HorizontalDivider(color = CyberneticCardBorderSoft, thickness = 1.dp)

            RuleToggleItem(
                title = "Снизить анимации (Ускорение UI)",
                description = "Отключает пульсации и плавные переходы для максимальной скорости и экономии заряда",
                checked = appSettings.reduceAnimations,
                onCheckedChange = {
                    AppPreferences.setReduceAnimations(context, it)
                    onChanged()
                }
            )
            HorizontalDivider(color = CyberneticCardBorderSoft, thickness = 1.dp)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Глубина отображения ленты: ${appSettings.feedLimit} лотов",
                    color = CyberneticOnSurfaceVariant,
                    fontSize = 12.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(50, 100, 150, 300, 500).forEach { lim ->
                        val isSel = appSettings.feedLimit == lim
                        Surface(
                            onClick = {
                                AppPreferences.setFeedLimit(context, lim)
                                onChanged()
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSel) CyberneticPrimary.copy(alpha = 0.16f) else CyberneticSurfaceContainerLow,
                            border = BorderStroke(1.dp, if (isSel) CyberneticPrimary else CyberneticCardBorderSoft),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(modifier = Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "$lim",
                                    color = if (isSel) CyberneticPrimary else CyberneticOnSurface,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsAndMonitoringCard(
    context: Context,
    radarStatus: ServiceStatus,
    watches: List<ua.magerko.olxradar.data.model.Watch>,
    checksCount: Long,
    pssMb: Int,
    currentIntervalSec: Int,
    nowMs: Long,
    onSelectInterval: (Int) -> Unit,
    onTogglePause: () -> Unit
) {
    val activeRadarCount = watches.count { it.isActive }
    val activeRatio = if (watches.isEmpty()) 0f else activeRadarCount.toFloat() / watches.size
    val lastPollText = CachedFormatters.formatShortAgo(radarStatus.lastCheckedAt, nowMs)
    val serviceBadgeText = when {
        radarStatus.isPaused -> "На паузе"
        radarStatus.isRunning -> "Служба активна"
        else -> "Остановлена"
    }
    val serviceDotColor = when {
        radarStatus.isPaused -> CyberneticTertiary
        radarStatus.isRunning -> CyberneticSecondary
        else -> CyberneticOutline
    }

    RadarSurfaceCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(serviceDotColor)
                    )
                    Text(
                        text = serviceBadgeText,
                        color = CyberneticOnSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (radarStatus.isPaused) CyberneticSecondary.copy(alpha = 0.15f) else CyberneticTertiary.copy(alpha = 0.15f),
                    modifier = Modifier.clickable { onTogglePause() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (radarStatus.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            tint = if (radarStatus.isPaused) CyberneticSecondary else CyberneticTertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (radarStatus.isPaused) "Возобновить" else "Пауза",
                            color = if (radarStatus.isPaused) CyberneticSecondary else CyberneticTertiary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile(
                    label = "Последний опрос",
                    value = lastPollText,
                    accentColor = CyberneticPrimary,
                    icon = Icons.Default.Schedule,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Активные радары",
                    value = "$activeRadarCount / ${watches.size}",
                    accentColor = CyberneticSecondary,
                    icon = Icons.Default.Speed,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricTile(
                    label = "Всего проверок",
                    value = "$checksCount",
                    accentColor = CyberneticPrimary,
                    icon = Icons.Default.Sync,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Память процесса",
                    value = "$pssMb МБ",
                    accentColor = CyberneticTertiary,
                    icon = Icons.Default.Memory,
                    modifier = Modifier.weight(1f)
                )
            }

            // Глобальный интервал опроса
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Глобальный интервал опроса",
                        color = CyberneticOnSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "${currentIntervalSec} сек",
                        color = CyberneticPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(15 to "15с", 25 to "25с", 45 to "45с", 60 to "60с", 300 to "5м Эко").forEach { (sec, lbl) ->
                        val isSel = currentIntervalSec == sec
                        Surface(
                            onClick = { onSelectInterval(sec) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSel) CyberneticPrimary.copy(alpha = 0.16f) else CyberneticSurfaceContainerLow,
                            border = BorderStroke(1.dp, if (isSel) CyberneticPrimary else CyberneticCardBorderSoft),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(modifier = Modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = lbl,
                                    color = if (isSel) CyberneticPrimary else CyberneticOnSurface,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            LinearProgressIndicator(
                progress = { activeRatio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = CyberneticPrimary,
                trackColor = CyberneticSurfaceContainerHighest
            )

            OutlinedButton(
                onClick = {
                    try {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "Откройте настройки батареи в системе", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, CyberneticCardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.BatteryChargingFull, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Оптимизация батареи (для работы 24/7)", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SmartSearchSettingsCard(
    context: Context,
    appSettings: AppPreferences.Snapshot,
    onChanged: () -> Unit
) {
    var usdRateInput by remember {
        mutableStateOf(String.format(Locale.US, "%.1f", appSettings.usdToUahRate))
    }
    var eurRateInput by remember {
        mutableStateOf(String.format(Locale.US, "%.1f", appSettings.eurToUahRate))
    }
    LaunchedEffect(appSettings.usdToUahRate) {
        val parsed = usdRateInput.toFloatOrNull()
        if (parsed == null || kotlin.math.abs(parsed - appSettings.usdToUahRate) > 0.001f) {
            usdRateInput = String.format(Locale.US, "%.1f", appSettings.usdToUahRate)
        }
    }
    LaunchedEffect(appSettings.eurToUahRate) {
        val parsed = eurRateInput.toFloatOrNull()
        if (parsed == null || kotlin.math.abs(parsed - appSettings.eurToUahRate) > 0.001f) {
            eurRateInput = String.format(Locale.US, "%.1f", appSettings.eurToUahRate)
        }
    }

    RadarSurfaceCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "УМНЫЙ ПОИСК И ОЦЕНКА СДЕЛОК",
                color = CyberneticSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )

            // Глубина сканирования
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Глубина сканирования OLX за цикл",
                    color = CyberneticOnSurfaceVariant,
                    fontSize = 12.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1 to "1 стр (40)", 2 to "2 стр (80)", 3 to "3 стр (120)").forEach { (pages, label) ->
                        val isSel = appSettings.scanDepthPages == pages
                        Surface(
                            onClick = {
                                AppPreferences.setScanDepthPages(context, pages)
                                onChanged()
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSel) CyberneticSecondary.copy(alpha = 0.16f) else CyberneticSurfaceContainerLow,
                            border = BorderStroke(1.dp, if (isSel) CyberneticSecondary else CyberneticCardBorderSoft),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    color = if (isSel) CyberneticSecondary else CyberneticOnSurface,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = CyberneticCardBorderSoft, thickness = 1.dp)

            RuleToggleItem(
                title = "Отслеживать снижение цены",
                description = "Ловит лоты, на которые продавец снизил цену — лучший способ найти горячие скидки",
                checked = appSettings.trackPriceDrops,
                onCheckedChange = {
                    AppPreferences.setTrackPriceDrops(context, it)
                    onChanged()
                }
            )

            if (appSettings.trackPriceDrops) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Мин. скидка:",
                        color = CyberneticOnSurfaceVariant,
                        fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    listOf(1, 5, 10, 15).forEach { pct ->
                        val isSel = appSettings.minPriceDropPercent == pct
                        FilterChip(
                            selected = isSel,
                            onClick = {
                                AppPreferences.setMinPriceDropPercent(context, pct)
                                onChanged()
                            },
                            label = { Text("-$pct%", fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                        )
                    }
                }
            }

            HorizontalDivider(color = CyberneticCardBorderSoft, thickness = 1.dp)

            RuleToggleItem(
                title = "Умный анти-спам фильтр",
                description = "Автоматически отсекает «скупка, выкуп, аренда, ремонт, под заказ, реплика, ломбард»",
                checked = appSettings.smartAntiSpam,
                onCheckedChange = {
                    AppPreferences.setSmartAntiSpam(context, it)
                    onChanged()
                }
            )
            HorizontalDivider(color = CyberneticCardBorderSoft, thickness = 1.dp)

            RuleToggleItem(
                title = "Строгое совпадение слов запроса",
                description = "Требует наличия всех слов запроса в тексте, отсекая «похожие» рекомендации OLX",
                checked = appSettings.strictQueryWordMatch,
                onCheckedChange = {
                    AppPreferences.setStrictQueryWordMatch(context, it)
                    onChanged()
                }
            )
            HorizontalDivider(color = CyberneticCardBorderSoft, thickness = 1.dp)

            RuleToggleItem(
                title = "Только с OLX Доставкой",
                description = "Глобально оставлять только объявления с подключённой Безопасной сделкой",
                checked = appSettings.onlyDeliveryGlobal,
                onCheckedChange = {
                    AppPreferences.setOnlyDeliveryGlobal(context, it)
                    onChanged()
                }
            )
            HorizontalDivider(color = CyberneticCardBorderSoft, thickness = 1.dp)

            RuleToggleItem(
                title = "Конвертировать USD / EUR при фильтре цены",
                description = "Проверяет долларовые и евровые лоты по курсу, чтобы не пропускать выгодные варианты",
                checked = appSettings.convertForeignCurrency,
                onCheckedChange = {
                    AppPreferences.setConvertForeignCurrency(context, it)
                    onChanged()
                }
            )

            if (appSettings.convertForeignCurrency) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = usdRateInput,
                        onValueChange = { txt ->
                            usdRateInput = txt
                            txt.toFloatOrNull()?.let {
                                AppPreferences.setUsdRate(context, it)
                                onChanged()
                            }
                        },
                        label = { Text("Курс USD → ₴", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = eurRateInput,
                        onValueChange = { txt ->
                            eurRateInput = txt
                            txt.toFloatOrNull()?.let {
                                AppPreferences.setEurRate(context, it)
                                onChanged()
                            }
                        },
                        label = { Text("Курс EUR → ₴", fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationsAndQuietHoursCard(
    context: Context,
    appSettings: AppPreferences.Snapshot,
    quietEnabled: Boolean,
    quietFromHour: Int,
    quietFromMin: Int,
    quietToHour: Int,
    quietToMin: Int,
    onToggleQuiet: (Boolean) -> Unit,
    onSetQuietFrom: (Int, Int) -> Unit,
    onSetQuietTo: (Int, Int) -> Unit,
    onChanged: () -> Unit
) {
    RadarSurfaceCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "УВЕДОМЛЕНИЯ И ТИХИЕ ЧАСЫ",
                color = CyberneticPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )

            RuleToggleItem(
                title = "Тихие часы (ночной режим)",
                description = "Отключает пуш-оповещения в заданное время (находки продолжают сохраняться в ленту)",
                checked = quietEnabled,
                onCheckedChange = onToggleQuiet
            )

            if (quietEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(context, { _, h, m -> onSetQuietFrom(h, m) }, quietFromHour, quietFromMin, true).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = String.format(Locale.US, "С %02d:%02d", quietFromHour, quietFromMin),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(context, { _, h, m -> onSetQuietTo(h, m) }, quietToHour, quietToMin, true).show()
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = String.format(Locale.US, "До %02d:%02d", quietToHour, quietToMin),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            HorizontalDivider(color = CyberneticCardBorderSoft, thickness = 1.dp)

            RuleToggleItem(
                title = "Звук уведомлений",
                description = "Проигрывать системный звук при обнаружении нового лота",
                checked = appSettings.notifSoundEnabled,
                onCheckedChange = {
                    AppPreferences.setNotifSoundEnabled(context, it)
                    onChanged()
                }
            )
            HorizontalDivider(color = CyberneticCardBorderSoft, thickness = 1.dp)

            RuleToggleItem(
                title = "Вибрация",
                description = "Тактильный отклик при поступлении новой находки",
                checked = appSettings.notifVibrationEnabled,
                onCheckedChange = {
                    AppPreferences.setNotifVibrationEnabled(context, it)
                    onChanged()
                }
            )
            HorizontalDivider(color = CyberneticCardBorderSoft, thickness = 1.dp)

            RuleToggleItem(
                title = "Показывать фото товара в пуше",
                description = "Загружает превью фотографии прямо в шторку уведомлений",
                checked = appSettings.notifShowPhotos,
                onCheckedChange = {
                    AppPreferences.setNotifShowPhotos(context, it)
                    onChanged()
                }
            )
            HorizontalDivider(color = CyberneticCardBorderSoft, thickness = 1.dp)

            RuleToggleItem(
                title = "Группировать уведомления по радарам",
                description = "Объединяет находки одного радара в аккуратную группу",
                checked = appSettings.notifGroupByRadar,
                onCheckedChange = {
                    AppPreferences.setNotifGroupByRadar(context, it)
                    onChanged()
                }
            )
            HorizontalDivider(color = CyberneticCardBorderSoft, thickness = 1.dp)

            RuleToggleItem(
                title = "Уведомлять о снижении цены",
                description = "Присылать пуш при падении цены на уже найденный лот",
                checked = appSettings.notifPriceDrops,
                onCheckedChange = {
                    AppPreferences.setNotifPriceDrops(context, it)
                    onChanged()
                }
            )
        }
    }
}

@Composable
private fun StorageAndUninstallBackupCard(
    context: Context,
    appSettings: AppPreferences.Snapshot,
    totalDbFindings: Int,
    nowMs: Long,
    onBackupNow: () -> Unit,
    onPickSafFolder: () -> Unit,
    onExportFull: () -> Unit,
    onImportFull: () -> Unit,
    onClearAiCache: () -> Unit,
    onClearRejected: () -> Unit,
    onClearAllFindings: () -> Unit
) {
    RadarSurfaceCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ФАЙЛЫ ПОСЛЕ УДАЛЕНИЯ И БЭКАПЫ",
                    color = CyberneticPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = CyberneticSecondary.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "Documents/OLXRadar",
                        color = CyberneticSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = "Авто-бэкап сохраняет радары, настройки и избранное в общедоступную папку Documents/OLXRadar/ (и привязанную SAF-папку), а Android также предлагает «Сохранить данные» при удалении.",
                color = CyberneticOnSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )

            RuleToggleItem(
                title = "Авто-сохранение в Documents",
                description = "Автоматически обновляет файл бэкапа, который остаётся после удаления программы",
                checked = appSettings.autoBackupToPublicStorage,
                onCheckedChange = {
                    AppPreferences.setAutoBackupToPublicStorage(context, it)
                    if (it) PersistentStorageManager.scheduleAutoBackup(context)
                }
            )
            HorizontalDivider(color = CyberneticCardBorderSoft, thickness = 1.dp)

            RuleToggleItem(
                title = "Сохранять избранное и находки",
                description = "Включает до 300 последних и избранных лотов в резервную копию",
                checked = appSettings.backupIncludeFindings,
                onCheckedChange = {
                    AppPreferences.setBackupIncludeFindings(context, it)
                    PersistentStorageManager.scheduleAutoBackup(context)
                }
            )
            HorizontalDivider(color = CyberneticCardBorderSoft, thickness = 1.dp)

            RuleToggleItem(
                title = "Включать API-ключи ИИ в бэкап",
                description = "Удобно при переустановке, но хранит ключи в JSON-файле бэкапа",
                checked = appSettings.backupIncludeApiKeys,
                onCheckedChange = {
                    AppPreferences.setBackupIncludeApiKeys(context, it)
                    PersistentStorageManager.scheduleAutoBackup(context)
                }
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CyberneticSurfaceContainerLow,
                border = BorderStroke(1.dp, CyberneticCardBorderSoft),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val lastAtText = if (appSettings.lastAutoBackupAt > 0L) {
                        CachedFormatters.formatShortAgo(appSettings.lastAutoBackupAt, nowMs)
                    } else "ещё не создавался"
                    val safConnected = remember(appSettings.safBackupTreeUri) {
                        PersistentStorageManager.isSafTreeAccessible(context, appSettings.safBackupTreeUri)
                    }
                    Text(
                        text = "Последний авто-бэкап: $lastAtText",
                        color = CyberneticOnSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (appSettings.lastAutoBackupStatus.isNotBlank()) {
                        Text(
                            text = appSettings.lastAutoBackupStatus,
                            color = CyberneticOnSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = when {
                            safConnected -> "SAF-папка подключена ✓"
                            !appSettings.safBackupTreeUri.isNullOrBlank() -> "Доступ к SAF-папке утрачен — выберите папку снова"
                            else -> "SAF-папка не выбрана (используется Documents/OLXRadar)"
                        },
                        color = when {
                            safConnected -> CyberneticSecondary
                            !appSettings.safBackupTreeUri.isNullOrBlank() -> CyberneticError
                            else -> CyberneticOutline
                        },
                        fontSize = 10.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onBackupNow,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Сохранить", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                OutlinedButton(
                    onClick = onPickSafFolder,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Выбрать папку", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onExportFull,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.IosShare, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Экспорт JSON", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onImportFull,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Восстановить", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            HorizontalDivider(color = CyberneticCardBorderSoft, thickness = 1.dp)

            Text(
                text = "Управление базой данных ($totalDbFindings находок)",
                color = CyberneticOnSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onClearAiCache,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Сброс кэша ИИ", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = onClearRejected,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Очистить архив", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            OutlinedButton(
                onClick = onClearAllFindings,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberneticError),
                border = BorderStroke(1.dp, CyberneticError.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Очистить все находки в БД", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AiLiveActivityCard(aiEnabled: Boolean, nowMs: Long) {
    RadarSurfaceCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "НЕЙРОСЕТЬ",
                color = CyberneticPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            val aiAct by AiRuntime.activity.collectAsState()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (aiAct.isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = CyberneticSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    if (aiAct.isBusy) {
                        Text(
                            text = "Анализирует: ${aiAct.title ?: "..."}",
                            color = CyberneticOnSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = buildString {
                                append("Радар: ${aiAct.radar ?: "—"} · ${aiAct.busySeconds} сек")
                                if (aiAct.photosTotal > 0) append(" · фото: ${aiAct.photosTotal}")
                            },
                            color = CyberneticOnSurfaceVariant,
                            fontSize = 11.sp
                        )
                    } else {
                        val fin = aiAct.lastFinished
                        if (fin == null) {
                            Text(
                                text = if (aiEnabled) "Простаивает — ждёт новые объявления" else "Отключена (ИИ-фильтр выключен)",
                                color = CyberneticOnSurfaceVariant,
                                fontSize = 13.sp
                            )
                        } else {
                            Text(
                                text = "Последний анализ: ${fin.title.ifBlank { "—" }}",
                                color = CyberneticOnSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${verdictLabel(fin.status)}${fin.confidence?.let { " · $it%" } ?: ""} · ${CachedFormatters.formatShortAgo(fin.atMs, nowMs)}",
                                color = CyberneticOnSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiSettingsCard(
    aiEnabled: Boolean,
    onToggleAiEnabled: (Boolean) -> Unit,
    aiProvider: String,
    aiModelId: String,
    aiBaseUrl: String,
    aiApiKeyMasked: String,
    isModelVerified: Boolean,
    onOpenProviderConfig: () -> Unit,
    onTestPrimaryApi: suspend () -> Result<String>,
    isBackupAiEnabled: Boolean,
    onToggleBackupAi: (Boolean) -> Unit,
    backupProvider: String,
    backupModelId: String,
    backupBaseUrl: String,
    backupApiKeyMasked: String,
    isBackupModelVerified: Boolean,
    onOpenBackupConfig: () -> Unit,
    onTestBackupApi: suspend () -> Result<String>,
    aiConfidence: Int,
    onSelectConfidence: (Int) -> Unit,
    aiMaxPhotos: Int,
    onSelectMaxPhotos: (Int) -> Unit,
    aiMaxConcurrent: Int,
    onSelectMaxConcurrent: (Int) -> Unit,
    aiTimeoutSec: Int,
    onSelectTimeoutSec: (Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var testStatusText by remember { mutableStateOf<String?>(null) }
    var testStatusSuccess by remember { mutableStateOf<Boolean?>(null) }
    var isTestingKey by remember { mutableStateOf(false) }

    var testBackupStatusText by remember { mutableStateOf<String?>(null) }
    var testBackupStatusSuccess by remember { mutableStateOf<Boolean?>(null) }
    var isTestingBackupKey by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "НАСТРОЙКИ НЕЙРОСЕТИ VISION",
                color = CyberneticPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (aiEnabled) "ВКЛ" else "ВЫКЛ",
                    color = if (aiEnabled) CyberneticSecondary else CyberneticOutline,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Switch(
                    checked = aiEnabled,
                    onCheckedChange = onToggleAiEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyberneticOnSecondary,
                        checkedTrackColor = CyberneticSecondary,
                        uncheckedThumbColor = CyberneticOutline,
                        uncheckedTrackColor = CyberneticSurfaceContainerHighest
                    )
                )
            }
        }

        RadarSurfaceCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Провайдер и Модель
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.SmartToy, contentDescription = null, tint = CyberneticPrimary, modifier = Modifier.size(16.dp))
                            Text(
                                text = "ПРОВАЙДЕР И МОДЕЛЬ ИИ",
                                color = CyberneticOnSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        TextButton(
                            onClick = onOpenProviderConfig,
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = CyberneticPrimary, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Настроить", color = CyberneticPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Surface(
                        onClick = onOpenProviderConfig,
                        shape = RoundedCornerShape(12.dp),
                        color = CyberneticSurfaceContainerLow,
                        border = BorderStroke(
                            1.dp,
                            if (isModelVerified) CyberneticSecondary.copy(alpha = 0.45f) else CyberneticTertiary.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = CyberneticPrimary.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = AiProvider.fromId(aiProvider).displayName,
                                        color = CyberneticPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = (if (isModelVerified) CyberneticSecondary else CyberneticTertiary).copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = if (isModelVerified) "✓ Vision проверена" else "⚠️ Vision не подтверждена",
                                        color = if (isModelVerified) CyberneticSecondary else CyberneticTertiary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = aiModelId,
                                color = CyberneticOnSurface,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = aiBaseUrl,
                                color = CyberneticOnSurfaceVariant,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.Key, contentDescription = null, tint = CyberneticOutline, modifier = Modifier.size(14.dp))
                                    Text(
                                        text = if (aiApiKeyMasked.isNotBlank()) aiApiKeyMasked else "Локальный / без ключа",
                                        color = CyberneticOnSurfaceVariant,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CyberneticOnSurfaceVariant, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            isTestingKey = true
                            testStatusText = null
                            coroutineScope.launch {
                                val result = onTestPrimaryApi()
                                isTestingKey = false
                                if (result.isSuccess) {
                                    testStatusSuccess = true
                                    testStatusText = "200 OK: Соединение успешно!"
                                    Toast.makeText(context, result.getOrNull() ?: "Подключение успешно!", Toast.LENGTH_LONG).show()
                                } else {
                                    testStatusSuccess = false
                                    val err = result.exceptionOrNull()?.message ?: "Ошибка подключения"
                                    testStatusText = if (err.contains("401")) "Ошибка 401: Неверный ключ" else "Ошибка: ${err.take(28)}..."
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    ) {
                        if (isTestingKey) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Проверка Vision соединения...", fontSize = 12.sp)
                        } else {
                            Icon(
                                imageVector = when (testStatusSuccess) {
                                    true -> Icons.Default.CheckCircle
                                    false -> Icons.Default.ErrorOutline
                                    null -> Icons.Default.Speed
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(testStatusText ?: "Проверить текущее Vision подключение", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // BACKUP / FAILOVER PROVIDER CARD
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CyberneticSurfaceContainerLow,
                        border = BorderStroke(
                            1.dp,
                            if (isBackupAiEnabled) CyberneticSecondary.copy(alpha = 0.35f) else CyberneticCardBorderSoft
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.AltRoute,
                                        contentDescription = null,
                                        tint = if (isBackupAiEnabled) CyberneticSecondary else CyberneticOutline,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text("Резервный провайдер (Failover)", color = CyberneticOnSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("Переход при лимитах 429", color = CyberneticOnSurfaceVariant, fontSize = 11.sp)
                                    }
                                }

                                Switch(checked = isBackupAiEnabled, onCheckedChange = onToggleBackupAi)
                            }

                            if (isBackupAiEnabled) {
                                Surface(
                                    onClick = onOpenBackupConfig,
                                    shape = RoundedCornerShape(10.dp),
                                    color = CyberneticSurfaceContainer,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isBackupModelVerified) CyberneticSecondary.copy(alpha = 0.45f) else CyberneticTertiary.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(AiProvider.fromId(backupProvider).displayName, color = CyberneticSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = if (isBackupModelVerified) "✓ Проверена" else "⚠️ Не проверена",
                                                color = if (isBackupModelVerified) CyberneticSecondary else CyberneticTertiary,
                                                fontSize = 10.sp
                                            )
                                        }
                                        Text(backupModelId, color = CyberneticOnSurface, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = if (backupApiKeyMasked.isNotBlank()) backupApiKeyMasked else "Без ключа",
                                            color = CyberneticOnSurfaceVariant,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        isTestingBackupKey = true
                                        testBackupStatusText = null
                                        coroutineScope.launch {
                                            val result = onTestBackupApi()
                                            isTestingBackupKey = false
                                            testBackupStatusSuccess = result.isSuccess
                                            testBackupStatusText = if (result.isSuccess) "200 OK: Резерв готов!" else "Ошибка: ${(result.exceptionOrNull()?.message ?: "").take(25)}"
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().height(36.dp)
                                ) {
                                    Text(
                                        text = if (isTestingBackupKey) "Проверка резерва..." else (testBackupStatusText ?: "Проверить резервное подключение"),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Порог уверенности (FIXED динамический ярлык!)
                val confidenceLabel = when {
                    aiConfidence >= 95 -> "Макс. точность"
                    aiConfidence >= 85 -> "Строгий"
                    aiConfidence >= 75 -> "Оптимальный"
                    else -> "Мягкий"
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Порог уверенности нейросети", color = CyberneticOnSurfaceVariant, fontSize = 12.sp)
                        Text("$aiConfidence% ($confidenceLabel)", color = CyberneticPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val thresholds = listOf(60 to "Мягкий", 75 to "Оптимал.", 85 to "Строгий", 95 to "Макс.")
                        thresholds.forEach { (threshold, label) ->
                            val isSelected = aiConfidence == threshold
                            Surface(
                                onClick = { onSelectConfidence(threshold) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) CyberneticPrimary.copy(alpha = 0.15f) else CyberneticSurfaceContainerLow,
                                border = BorderStroke(1.dp, if (isSelected) CyberneticPrimary else CyberneticCardBorderSoft),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$threshold%", color = if (isSelected) CyberneticPrimary else CyberneticOnSurface, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                    Text(label, color = if (isSelected) CyberneticPrimary else CyberneticOutline, fontSize = 9.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                // Количество фото
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Количество фото для анализа", color = CyberneticOnSurfaceVariant, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CyberneticSurfaceContainerLow).padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(1 to "1 фото", 2 to "2 фото", 3 to "3 фото", 5 to "Все (5)").forEach { (count, label) ->
                            val isSelected = aiMaxPhotos == count
                            Surface(
                                onClick = { onSelectMaxPhotos(count) },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) CyberneticPrimary else Color.Transparent,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        color = if (isSelected) CyberneticOnPrimary else CyberneticOnSurfaceVariant,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Параллельность и таймаут
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Потоки ИИ", color = CyberneticOnSurfaceVariant, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(1, 2, 3).forEach { c ->
                                val isSel = aiMaxConcurrent == c
                                Surface(
                                    onClick = { onSelectMaxConcurrent(c) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) CyberneticPrimary.copy(alpha = 0.18f) else CyberneticSurfaceContainerLow,
                                    border = BorderStroke(1.dp, if (isSel) CyberneticPrimary else CyberneticCardBorderSoft),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                        Text("$c", color = if (isSel) CyberneticPrimary else CyberneticOnSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Таймаут запроса", color = CyberneticOnSurfaceVariant, fontSize = 11.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(15, 25, 40, 60).forEach { t ->
                                val isSel = aiTimeoutSec == t
                                Surface(
                                    onClick = { onSelectTimeoutSec(t) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) CyberneticPrimary.copy(alpha = 0.18f) else CyberneticSurfaceContainerLow,
                                    border = BorderStroke(1.dp, if (isSel) CyberneticPrimary else CyberneticCardBorderSoft),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(modifier = Modifier.padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                        Text("${t}с", color = if (isSel) CyberneticPrimary else CyberneticOnSurface, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FiltrationRulesCard(
    aiCheckSuspicious: Boolean,
    aiStrictMode: Boolean,
    aiManualReview: Boolean,
    aiNotifyRejected: Boolean,
    onCheckSuspiciousChange: (Boolean) -> Unit,
    onStrictModeChange: (Boolean) -> Unit,
    onManualReviewChange: (Boolean) -> Unit,
    onNotifyRejectedChange: (Boolean) -> Unit
) {
    val activeRulesCount = listOf(aiCheckSuspicious, aiStrictMode, aiManualReview, aiNotifyRejected).count { it }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ПРАВИЛА ФИЛЬТРАЦИИ ИИ", color = CyberneticSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Text("$activeRulesCount из 4 активно", color = CyberneticSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }

        RadarSurfaceCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                RuleToggleItem(
                    title = "Фильтровать запчасти и битые",
                    description = "Автоматически отсеивать объявления с битыми экранами, iCloud lock, запчастями и коробками",
                    checked = aiCheckSuspicious,
                    onCheckedChange = onCheckSuspiciousChange
                )
                HorizontalDivider(color = CyberneticCardBorderSoft, thickness = 1.dp)

                RuleToggleItem(
                    title = "Строгий режим фото и заголовка",
                    description = "Отклонять, если на фото другая модель или низкая уверенность ИИ",
                    checked = aiStrictMode,
                    onCheckedChange = onStrictModeChange
                )
                HorizontalDivider(color = CyberneticCardBorderSoft, thickness = 1.dp)

                RuleToggleItem(
                    title = "Режим ручной проверки (Архив)",
                    description = "Сохранять отклонённые и сомнительные лоты во вкладку «Архив», чтобы ничего не потерять",
                    checked = aiManualReview,
                    onCheckedChange = onManualReviewChange
                )
                HorizontalDivider(color = CyberneticCardBorderSoft, thickness = 1.dp)

                RuleToggleItem(
                    title = "Уведомлять об отклонённых",
                    description = "Если выключено — отсеянное тихо отправляется в «Архив» без пуша",
                    checked = aiNotifyRejected,
                    onCheckedChange = onNotifyRejectedChange
                )
            }
        }
    }
}

@Composable
private fun AboutAppCard(
    context: Context,
    prefsDev: SharedPreferences,
    devMode: Boolean,
    onDevModeChange: (Boolean) -> Unit
) {
    var devTapCount by remember { mutableIntStateOf(0) }
    var devLastTapMs by remember { mutableLongStateOf(0L) }

    RadarSurfaceCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("О ПРОГРАММЕ", color = CyberneticPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val now = System.currentTimeMillis()
                        if (now - devLastTapMs < 1200) devTapCount += 1 else devTapCount = 1
                        devLastTapMs = now
                        if (devTapCount >= 4) {
                            val newMode = !devMode
                            onDevModeChange(newMode)
                            prefsDev.edit().putBoolean("dev_mode", newMode).apply()
                            Toast.makeText(
                                context,
                                if (newMode) "Режим разработчика включён" else "Режим разработчика выключен",
                                Toast.LENGTH_SHORT
                            ).show()
                            devTapCount = 0
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = CyberneticSecondary, modifier = Modifier.size(18.dp))
                Text("OLX Radar · версия ${BuildConfig.VERSION_NAME}", color = CyberneticOnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("Разработчики: Deads_xB и neyseras", color = CyberneticOnSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DevLogsCard(
    context: Context,
    aiThoughtLogsCount: Int,
    onOpenAiThoughts: () -> Unit
) {
    val logEntries by AppLog.log.collectAsState()
    val scope = rememberCoroutineScope()

    RadarSurfaceCard(
        shape = RoundedCornerShape(18.dp),
        borderColor = CyberneticError.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onOpenAiThoughts,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberneticSecondary.copy(alpha = 0.2f),
                    contentColor = CyberneticSecondary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Логи мышления ИИ ($aiThoughtLogsCount)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            HorizontalDivider(color = CyberneticCardBorderSoft, thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("ЖУРНАЛ ОТЛАДКИ (${logEntries.size})", color = CyberneticError, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    TextButton(onClick = {
                        scope.launch {
                            val res = PersistentStorageManager.exportDiagnosticFile(
                                context,
                                "olx_radar_logs_${System.currentTimeMillis()}.txt",
                                AppLog.asText()
                            )
                            Toast.makeText(
                                context,
                                res.fold(onSuccess = { "Сохранено: $it" }, onFailure = { "Ошибка: ${it.message}" }),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }) { Text("Файл", color = CyberneticSecondary, fontSize = 11.sp) }
                    TextButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("OLX Radar logs", AppLog.asText()))
                        Toast.makeText(context, "Журнал скопирован", Toast.LENGTH_SHORT).show()
                    }) { Text("Копия", color = CyberneticPrimary, fontSize = 11.sp) }
                    TextButton(onClick = { AppLog.clear() }) { Text("Сброс", color = CyberneticOnSurfaceVariant, fontSize = 11.sp) }
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CyberneticSurfaceContainerLow,
                modifier = Modifier.fillMaxWidth()
            ) {
                val recent = remember(logEntries) { logEntries.takeLast(120).asReversed() }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(220.dp).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(recent, key = { e -> "${e.timeMs}_${System.identityHashCode(e)}" }) { e ->
                        Text(
                            text = e.formatted(),
                            color = when (e.level) {
                                "E" -> CyberneticError
                                "W" -> CyberneticTertiary
                                else -> CyberneticOnSurfaceVariant
                            },
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (recent.isEmpty()) {
                        item(key = "empty") { Text("Журнал пуст", color = CyberneticOnSurfaceVariant, fontSize = 11.sp) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProviderConfigDialog(
    dialogTitle: String = "Провайдер и Vision-модель",
    currentProvider: String,
    currentBaseUrl: String,
    currentModelId: String,
    currentApiKeyMasked: String,
    onDismiss: () -> Unit,
    onApply: (provider: String, baseUrl: String, apiKey: String, modelId: String) -> Unit,
    onTestVision: suspend (provider: String, baseUrl: String, apiKey: String, modelId: String) -> Result<GroqVisionClient.VisionCheckResult>,
    onFetchModels: suspend (baseUrl: String, apiKey: String) -> Result<List<String>>
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedProvider by remember { mutableStateOf(currentProvider) }
    var enteredBaseUrl by remember { mutableStateOf(currentBaseUrl) }
    var enteredApiKey by remember { mutableStateOf("") }
    var enteredModelId by remember { mutableStateOf(currentModelId) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var showCustomBaseUrl by remember(selectedProvider) {
        mutableStateOf(selectedProvider.equals(AiProvider.CUSTOM.id, ignoreCase = true))
    }

    var isTestingVision by remember { mutableStateOf(false) }
    var visionCheckResult by remember { mutableStateOf<Result<GroqVisionClient.VisionCheckResult>?>(null) }
    var isVerified by remember { mutableStateOf(false) }

    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingModels by remember { mutableStateOf(false) }

    fun invalidateVerification() {
        isVerified = false
        visionCheckResult = null
    }

    val activeProviderConfig = remember(selectedProvider) { AiProvider.fromId(selectedProvider) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberneticModalSurface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(Icons.Default.SmartToy, contentDescription = null, tint = CyberneticPrimary, modifier = Modifier.size(22.dp))
                    Text(dialogTitle, color = CyberneticOnSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = CyberneticOnSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Выберите провайдера:", color = CyberneticOnSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AiProvider.entries.forEach { p ->
                            val isSelected = p.id.equals(selectedProvider, ignoreCase = true)
                            Surface(
                                onClick = {
                                    if (!isSelected) {
                                        selectedProvider = p.id
                                        enteredBaseUrl = p.defaultBaseUrl
                                        enteredModelId = p.defaultModel
                                        showCustomBaseUrl = p.id.equals(AiProvider.CUSTOM.id, ignoreCase = true)
                                        invalidateVerification()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) CyberneticPrimary.copy(alpha = 0.2f) else CyberneticSurfaceContainerLow,
                                border = BorderStroke(1.dp, if (isSelected) CyberneticPrimary else CyberneticCardBorderSoft),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(modifier = Modifier.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = p.displayName.split(" ").first(),
                                        color = if (isSelected) CyberneticPrimary else CyberneticOnSurface,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                val isCustomProvider = selectedProvider.equals(AiProvider.CUSTOM.id, ignoreCase = true)
                if (!showCustomBaseUrl && !isCustomProvider) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(CyberneticSurfaceContainerLow).padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "URL: $enteredBaseUrl",
                            color = CyberneticOnSurfaceVariant,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Изменить",
                            color = CyberneticPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { showCustomBaseUrl = true }
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = enteredBaseUrl,
                        onValueChange = { enteredBaseUrl = it; invalidateVerification() },
                        label = { Text("Base URL (OpenAI API)") },
                        placeholder = { Text("https://.../v1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = enteredApiKey,
                    onValueChange = { enteredApiKey = it; invalidateVerification() },
                    label = { Text("API Ключ (${activeProviderConfig.keyHint})") },
                    placeholder = {
                        Text(
                            if (currentApiKeyMasked.isNotBlank()) "Текущий: $currentApiKeyMasked" else activeProviderConfig.keyPlaceholder
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                            Icon(
                                imageVector = if (isApiKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = CyberneticOnSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Модель (Model ID)", color = CyberneticOnSurfaceVariant, fontSize = 12.sp)
                        Text(
                            text = if (isLoadingModels) "Загрузка..." else "Загрузить с сервера",
                            color = CyberneticPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable(enabled = !isLoadingModels && enteredBaseUrl.isNotBlank()) {
                                isLoadingModels = true
                                coroutineScope.launch {
                                    val res = onFetchModels(enteredBaseUrl, enteredApiKey)
                                    isLoadingModels = false
                                    res.onSuccess { availableModels = it }
                                        .onFailure {
                                            Toast.makeText(context, "Ошибка загрузки моделей: ${it.message}", Toast.LENGTH_SHORT).show()
                                        }
                                }
                            }
                        )
                    }

                    OutlinedTextField(
                        value = enteredModelId,
                        onValueChange = { enteredModelId = it; invalidateVerification() },
                        placeholder = { Text(activeProviderConfig.defaultModel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (activeProviderConfig.suggestedVisionModels.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(activeProviderConfig.suggestedVisionModels.size) { index ->
                                val sugModel = activeProviderConfig.suggestedVisionModels[index]
                                val isSelected = sugModel.equals(enteredModelId.trim(), ignoreCase = true)
                                Surface(
                                    onClick = { enteredModelId = sugModel; invalidateVerification() },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) CyberneticPrimary.copy(alpha = 0.2f) else CyberneticSurfaceContainerLow,
                                    border = BorderStroke(1.dp, if (isSelected) CyberneticPrimary else CyberneticCardBorderSoft)
                                ) {
                                    Text(
                                        text = sugModel.substringAfterLast('/'),
                                        color = if (isSelected) CyberneticPrimary else CyberneticOnSurface,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (availableModels.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(availableModels.take(8).size) { index ->
                                val srvModel = availableModels[index]
                                val isSelected = srvModel.equals(enteredModelId.trim(), ignoreCase = true)
                                Surface(
                                    onClick = { enteredModelId = srvModel; invalidateVerification() },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) CyberneticPrimary.copy(alpha = 0.2f) else CyberneticSurfaceContainerLow,
                                    border = BorderStroke(1.dp, if (isSelected) CyberneticPrimary else CyberneticCardBorderSoft)
                                ) {
                                    Text(
                                        text = srvModel.substringAfterLast('/'),
                                        color = if (isSelected) CyberneticPrimary else CyberneticOnSurface,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        isTestingVision = true
                        visionCheckResult = null
                        coroutineScope.launch {
                            val res = onTestVision(selectedProvider, enteredBaseUrl, enteredApiKey, enteredModelId)
                            isTestingVision = false
                            visionCheckResult = res
                            isVerified = res.isSuccess
                        }
                    },
                    enabled = !isTestingVision && enteredModelId.isNotBlank() && enteredBaseUrl.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isVerified) CyberneticSecondary else CyberneticPrimary,
                        contentColor = if (isVerified) CyberneticOnSecondary else CyberneticOnPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    if (isTestingVision) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Тестирование Vision...", fontSize = 12.sp)
                    } else {
                        Icon(if (isVerified) Icons.Default.CheckCircle else Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isVerified) "Vision подтвержден (повторить)" else "Проверить модель на Vision", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                val resultMsg = when {
                    isVerified -> visionCheckResult?.getOrNull()?.message ?: "Модель подтвердила поддержку Vision (200 OK)."
                    visionCheckResult?.isFailure == true -> visionCheckResult?.exceptionOrNull()?.message ?: "Модель не поддерживает Vision."
                    else -> null
                }
                if (resultMsg != null) {
                    Text(
                        text = resultMsg,
                        color = if (isVerified) CyberneticSecondary else CyberneticError,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(selectedProvider, enteredBaseUrl, enteredApiKey, enteredModelId) },
                enabled = isVerified && !isTestingVision,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(if (isVerified) "Сохранить" else "Сначала проверьте Vision", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = CyberneticOnSurfaceVariant) }
        }
    )
}

@Composable
fun AiThoughtsDialog(
    logs: List<ua.magerko.olxradar.ai.AiThoughtLog>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberneticModalSurface,
        title = {
            Text("Логи мышления ИИ (${logs.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CyberneticOnSurface)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("AI Thoughts", ua.magerko.olxradar.ai.AiThoughtLogger.asFormattedText()))
                            Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) { Text("Копия", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val res = PersistentStorageManager.exportDiagnosticFile(
                                    context,
                                    "olx_radar_ai_thoughts_${System.currentTimeMillis()}.txt",
                                    ua.magerko.olxradar.ai.AiThoughtLogger.asFormattedText()
                                )
                                Toast.makeText(
                                    context,
                                    res.fold(onSuccess = { "Сохранено: $it" }, onFailure = { "Ошибка: ${it.message}" }),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) { Text("В файл", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }

                    OutlinedButton(
                        onClick = {
                            ua.magerko.olxradar.ai.AiThoughtLogger.clear()
                            Toast.makeText(context, "Очищено", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) { Text("Сброс", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(logs, key = { log -> "${log.timeMs}_${log.listingId}_${System.identityHashCode(log)}" }) { log ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CyberneticSurfaceContainerLow,
                            border = BorderStroke(1.dp, CyberneticCardBorderSoft),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "[${log.timeFormatted()}] ${log.status} ${log.confidence?.let { "$it%" } ?: ""}",
                                    color = CyberneticPrimary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(log.title, color = CyberneticOnSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(log.reasons, color = CyberneticOnSurfaceVariant, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть", color = CyberneticPrimary) }
        }
    )
}

@Composable
private fun RuleToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(title, color = CyberneticOnSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = CyberneticOnSurfaceVariant, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CyberneticOnPrimary,
                checkedTrackColor = CyberneticPrimary,
                uncheckedThumbColor = CyberneticOutline,
                uncheckedTrackColor = CyberneticSurfaceContainerHighest
            )
        )
    }
}

private fun verdictLabel(status: String): String = when (status.uppercase()) {
    AiStatus.MATCH, "APPROVED" -> "✓ Подходит"
    AiStatus.REJECTED -> "✗ Отклонено"
    AiStatus.NEEDS_REVIEW -> "? Сомнительно"
    AiStatus.QUEUED -> "⏳ В очереди"
    AiStatus.ERROR -> "⚠ Ошибка"
    else -> status
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TelegramForwardingCard(
    context: Context,
    appSettings: AppPreferences.Snapshot,
    onChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var botTokenInput by remember { mutableStateOf(appSettings.telegramBotToken) }
    var chatIdInput by remember { mutableStateOf(appSettings.telegramChatId) }
    var showToken by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }

    LaunchedEffect(appSettings.telegramBotToken) {
        if (botTokenInput != appSettings.telegramBotToken) {
            botTokenInput = appSettings.telegramBotToken
        }
    }
    LaunchedEffect(appSettings.telegramChatId) {
        if (chatIdInput != appSettings.telegramChatId) {
            chatIdInput = appSettings.telegramChatId
        }
    }

    RadarSurfaceCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "ПЕРЕСЫЛКА В TELEGRAM-БОТА",
                color = CyberneticPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )

            RuleToggleItem(
                title = "Дублировать находки в Telegram",
                description = "Удобно для уведомлений на основной телефон, если сканер работает на другом",
                checked = appSettings.telegramEnabled,
                onCheckedChange = {
                    AppPreferences.setTelegramEnabled(context, it)
                    onChanged()
                }
            )

            if (appSettings.telegramEnabled) {
                OutlinedTextField(
                    value = botTokenInput,
                    onValueChange = {
                        botTokenInput = it
                        AppPreferences.setTelegramBotToken(context, it)
                        onChanged()
                    },
                    label = { Text("Bot Token (@BotFather)") },
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = chatIdInput,
                    onValueChange = {
                        chatIdInput = it
                        AppPreferences.setTelegramChatId(context, it)
                        onChanged()
                    },
                    label = { Text("Chat ID (число или @channel)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Минимальный Deal Score для отправки:", color = CyberneticOnSurfaceVariant, fontSize = 11.sp)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(0 to "Все", 50 to "50+", 70 to "70+", 85 to "85+").forEach { (score, label) ->
                            FilterChip(
                                selected = appSettings.telegramMinDealScore == score,
                                onClick = {
                                    AppPreferences.setTelegramMinDealScore(context, score)
                                    onChanged()
                                },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        isTesting = true
                        scope.launch {
                            val res = TelegramNotifier.sendTestMessage(botTokenInput, chatIdInput)
                            isTesting = false
                            Toast.makeText(
                                context,
                                res.fold(onSuccess = { it }, onFailure = { "Ошибка: ${it.message}" }),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    enabled = !isTesting && botTokenInput.isNotBlank() && chatIdInput.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Отправка...", fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Отправить тест в Telegram", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BackgroundStabilityCard(
    context: Context,
    appSettings: AppPreferences.Snapshot
) {
    var ignoringBatteryOpt by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                pm?.isIgnoringBatteryOptimizations(context.packageName) == true
            } else true
        )
    }

    RadarSurfaceCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "СТАБИЛЬНОСТЬ В ФОНЕ И БАТАРЕЯ",
                color = CyberneticSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )

            RuleToggleItem(
                title = "Watchdog-перезапуск (WorkManager)",
                description = "Проверяет каждые 15 мин, не остановила ли прошивка фоновый сканер, и перезапускает его",
                checked = appSettings.watchdogEnabled,
                onCheckedChange = { enabled ->
                    AppPreferences.setWatchdogEnabled(context, enabled)
                    if (enabled) MonitorWatchdogWorker.schedule(context)
                    else MonitorWatchdogWorker.cancel(context)
                }
            )

            HorizontalDivider(color = CyberneticCardBorderSoft, thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (ignoringBatteryOpt) "Ограничения батареи сняты ✓" else "Батарея: есть системные ограничения",
                        color = if (ignoringBatteryOpt) CyberneticSecondary else CyberneticTertiary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Для Xiaomi/Samsung рекомендуется «Без ограничений»",
                        color = CyberneticOnSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !ignoringBatteryOpt) {
                    FilledTonalButton(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                }
                            }
                            ignoringBatteryOpt = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)
                                ?.isIgnoringBatteryOptimizations(context.packageName) == true
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Разрешить", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SellerBlacklistCard(
    blockedSellers: List<SellerBlacklist>,
    onUnblock: (String) -> Unit,
    onClearAll: () -> Unit
) {
    RadarSurfaceCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ЧЁРНЫЙ СПИСОК ПРОДАВЦОВ (${blockedSellers.size})",
                    color = CyberneticError,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                if (blockedSellers.isNotEmpty()) {
                    TextButton(
                        onClick = onClearAll,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("Очистить", fontSize = 11.sp, color = CyberneticError)
                    }
                }
            }

            if (blockedSellers.isEmpty()) {
                Text(
                    text = "Список пуст. Вы можете скрыть любого перекупа/продавца прямо из карточки объявления.",
                    color = CyberneticOnSurfaceVariant,
                    fontSize = 11.sp
                )
            } else {
                blockedSellers.take(15).forEach { seller ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CyberneticSurfaceContainerLow)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = seller.displayName,
                            color = CyberneticOnSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = { onUnblock(seller.sellerKey) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Вернуть", fontSize = 11.sp, color = CyberneticPrimary)
                        }
                    }
                }
            }
        }
    }
}
