package ua.magerko.olxradar.ui.compose

import android.os.Debug
import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import ua.magerko.olxradar.ai.AiProvider
import ua.magerko.olxradar.ai.GroqVisionClient
import ua.magerko.olxradar.ai.AiRuntime
import ua.magerko.olxradar.ai.AiStatus
import ua.magerko.olxradar.service.ServiceStatus
import ua.magerko.olxradar.ui.StatusViewModel
import ua.magerko.olxradar.BuildConfig
import ua.magerko.olxradar.util.AppLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusAiSettingsScreen(
    statusViewModel: StatusViewModel,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val radarStatus by statusViewModel.radarStatus.collectAsState()
    val aiEnabled by statusViewModel.aiEnabled.collectAsState()
    val aiProvider by statusViewModel.aiProvider.collectAsState()
    val aiBaseUrl by statusViewModel.aiBaseUrl.collectAsState()
    val aiApiKeyMasked by statusViewModel.aiApiKeyMasked.collectAsState()
    val aiModelId by statusViewModel.aiModelId.collectAsState()
    val isModelVerified by statusViewModel.isModelVerified.collectAsState()
    val aiConfidence by statusViewModel.aiConfidence.collectAsState()
    val aiMaxPhotos by statusViewModel.aiMaxPhotos.collectAsState()
    val aiCheckSuspicious by statusViewModel.aiCheckSuspicious.collectAsState()
    val aiStrictMode by statusViewModel.aiStrictMode.collectAsState()
    val aiManualReview by statusViewModel.aiManualReview.collectAsState()
    val aiNotifyRejected by statusViewModel.aiNotifyRejected.collectAsState()
    val watches by statusViewModel.watches.collectAsState()
    val currentInterval by statusViewModel.currentInterval.collectAsState()

    // Тик для «N сек назад» в диагностике (обновление раз в 5 сек)
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            nowMs = System.currentTimeMillis()
        }
    }
    // Реальное использование памяти процесса (PSS), пересчитываем по тику
    val processPssMb = remember(nowMs) {
        val mi = Debug.MemoryInfo()
        Debug.getMemoryInfo(mi)
        (mi.totalPss / 1024).coerceAtLeast(1)
    }
    val lastPollAgoSec = radarStatus.lastCheckedAt?.let { ((nowMs - it) / 1000).coerceAtLeast(0) }
    val activeWatchCount = watches.count { it.isActive }

    // Реальная телеметрия (вместо прежних заглушек)
    val watchesList by statusViewModel.watches.collectAsState()
    val totalFindingsCount by statusViewModel.totalFindings.collectAsState()
    val currentIntervalSec by statusViewModel.currentInterval.collectAsState()

    // «Что нейросеть обрабатывает прямо сейчас» — живой статус ИИ
    val aiActivity by AiRuntime.activity.collectAsState()
    val aiElapsedSec = aiActivity.busySeconds

    // Режим разработчика: 4 быстрых тапа по строке «О приложении»
    val prefsDev = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    var devMode by remember { mutableStateOf(prefsDev.getBoolean("dev_mode", false)) }
    var devTapCount by remember { mutableIntStateOf(0) }
    var devLastTapMs by remember { mutableLongStateOf(0L) }
    val logEntries by AppLog.log.collectAsState()
    val clipboard = LocalClipboardManager.current
    val checksCount = radarStatus.checksCount
    val activeRadarCount = watchesList.count { it.isActive }
    val activeRatio = if (watchesList.isEmpty()) 0f else activeRadarCount.toFloat() / watchesList.size
    val lastPollText = radarStatus.lastCheckedAt?.let {
        val sec = ((nowMs - it) / 1000).coerceAtLeast(0)
        when {
            sec < 60 -> "$sec сек назад"
            sec < 3600 -> "${sec / 60} мин назад"
            else -> "${sec / 3600} ч назад"
        }
    } ?: "—"
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
    val pssMb = processPssMb

    var showProviderConfigDialog by remember { mutableStateOf(false) }
    var showAiThoughtsDialog by remember { mutableStateOf(false) }
    val aiThoughtLogs by ua.magerko.olxradar.ai.AiThoughtLogger.logsFlow.collectAsState()
    var testStatusText by remember { mutableStateOf<String?>(null) }
    var testStatusSuccess by remember { mutableStateOf<Boolean?>(null) }
    var isTestingKey by remember { mutableStateOf(false) }

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
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Назад",
                        tint = CyberneticOnSurface
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Настройки и диагностика",
                    color = CyberneticOnSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = "OLX Radar AI Engine · Groq Vision",
                    color = CyberneticOnSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 0. ВНЕШНИЙ ВИД (выбор темы)
            val savedThemeMode = remember { ThemePreferences.get(context) }
            var selectedThemeMode by remember { mutableStateOf(savedThemeMode) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberneticOutlineVariant.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CyberneticSurfaceContainer)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "ОФОРМЛЕНИЕ",
                        color = CyberneticPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    ThemeMode.entries.forEach { mode ->
                        Surface(
                            onClick = {
                                selectedThemeMode = mode
                                ThemePreferences.set(context, mode)
                                // Полное пересоздание активити: смена темы со стеклом
                                // «на живую» роняет слой backdrop (issue #60)
                                (context as? Activity)?.recreate()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedThemeMode == mode)
                                CyberneticPrimary.copy(alpha = 0.12f)
                            else
                                CyberneticSurfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (selectedThemeMode == mode)
                                        Icons.Default.RadioButtonChecked else Icons.Default.PanoramaFishEye,
                                    contentDescription = null,
                                    tint = if (selectedThemeMode == mode) CyberneticPrimary else CyberneticOutline,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mode.title,
                                        color = CyberneticOnSurface,
                                        fontSize = 14.sp,
                                        fontWeight = if (selectedThemeMode == mode) FontWeight.Bold else FontWeight.Normal
                                    )
                                    Text(
                                        text = mode.subtitle,
                                        color = CyberneticOnSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                                if (mode == ThemeMode.GLASS && !isGlassFallbackSupported) {
                                    Text(
                                        text = "Android 12+",
                                        color = CyberneticTertiary,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                }
            }


            // 1. DIAGNOSTICS CARD
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberneticOutlineVariant.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CyberneticSurfaceContainer)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header with Status and Uptime
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
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (radarStatus.isPaused) CyberneticSecondary.copy(alpha = 0.15f) else CyberneticTertiary.copy(alpha = 0.15f),
                                modifier = Modifier.clickable { statusViewModel.toggleMonitoringPause() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = CyberneticSurfaceContainerHigh
                            ) {
                                Text(
                                    text = if (radarStatus.isRunning) "${activeRadarCount} из ${watchesList.size} радаров" else "—",
                                    color = CyberneticOnSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    // 2x2 Telemetry Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Cell 1: Last Poll
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CyberneticSurfaceContainerLow,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
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
                                    Text(
                                        text = "Последний опрос",
                                        color = CyberneticOnSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                                Text(
                                    text = lastPollText,
                                    color = CyberneticOnSurface,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Cell 2: API Latency
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CyberneticSurfaceContainerLow,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = CyberneticSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Активные радары",
                                        color = CyberneticOnSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                                Text(
                                    text = "${activeRadarCount} / ${watchesList.size}",
                                    color = CyberneticSecondary,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Cell 3: Total checks
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CyberneticSurfaceContainerLow,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = null,
                                        tint = CyberneticPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Всего проверок",
                                        color = CyberneticOnSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                                Text(
                                    text = "$checksCount",
                                    color = CyberneticOnSurface,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Cell 4: Memory Usage
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CyberneticSurfaceContainerLow,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Memory,
                                        contentDescription = null,
                                        tint = CyberneticTertiary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = "Память процесса",
                                        color = CyberneticOnSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                                Text(
                                    text = "$pssMb МБ",
                                    color = CyberneticOnSurface,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Activity indicator bar
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CyberneticSurfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Доля активных радаров",
                                    color = CyberneticOnSurfaceVariant,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "~$currentIntervalSec сек / цикл",
                                    color = CyberneticPrimary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
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
                        }
                    }
                }
            }

            // 1.5. РАБОТА НЕЙРОСЕТИ (что ИИ обрабатывает прямо сейчас)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberneticOutlineVariant.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CyberneticSurfaceContainer)
            ) {
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
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
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
                                        text = "${verdictLabel(fin.status)}${fin.confidence?.let { " · $it%" } ?: ""} · ${relativeSec((nowMs - fin.atMs) / 1000)}",
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

            // 2. GROQ VISION AI SETTINGS CARD
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "НАСТРОЙКИ НЕЙРОСЕТИ GROQ VISION",
                        color = CyberneticPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
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
                            onCheckedChange = { statusViewModel.setAiEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyberneticOnSecondary,
                                checkedTrackColor = CyberneticSecondary,
                                uncheckedThumbColor = CyberneticOutline,
                                uncheckedTrackColor = CyberneticSurfaceContainerHighest
                            )
                        )
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyberneticOutlineVariant.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberneticSurfaceContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Провайдер и Модель нейросети (OpenAI, Groq, OpenRouter, Custom)
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
                                    Icon(
                                        imageVector = Icons.Default.SmartToy,
                                        contentDescription = null,
                                        tint = CyberneticPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "ПРОВАЙДЕР И МОДЕЛЬ ИИ",
                                        color = CyberneticOnSurfaceVariant,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }

                                TextButton(
                                    onClick = { showProviderConfigDialog = true },
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    modifier = Modifier.height(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = CyberneticPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Настроить",
                                        color = CyberneticPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Surface(
                                onClick = { showProviderConfigDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                color = CyberneticSurfaceContainerLow,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isModelVerified) CyberneticSecondary.copy(alpha = 0.45f) else CyberneticTertiary.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
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

                                        if (isModelVerified) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = CyberneticSecondary.copy(alpha = 0.16f)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = null,
                                                        tint = CyberneticSecondary,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Text(
                                                        text = "Vision проверена",
                                                        color = CyberneticSecondary,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        } else {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = CyberneticTertiary.copy(alpha = 0.16f)
                                            ) {
                                                Text(
                                                    text = "⚠️ Vision не подтверждена",
                                                    color = CyberneticTertiary,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                                    }

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
                                                imageVector = Icons.Default.Key,
                                                contentDescription = null,
                                                tint = CyberneticOutline,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = if (aiApiKeyMasked.isNotBlank()) aiApiKeyMasked else "Локальный / без ключа",
                                                color = CyberneticOnSurfaceVariant,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }

                                        Icon(
                                            imageVector = Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = CyberneticOnSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            // Test Connection Button
                            OutlinedButton(
                                onClick = {
                                    isTestingKey = true
                                    testStatusText = null
                                    coroutineScope.launch {
                                        val result = statusViewModel.testGroqApi()
                                        isTestingKey = false
                                        if (result.isSuccess) {
                                            testStatusSuccess = true
                                            testStatusText = "200 OK: Соединение успешно!"
                                            Toast.makeText(context, result.getOrNull() ?: "Подключение успешно!", Toast.LENGTH_LONG).show()
                                        } else {
                                            testStatusSuccess = false
                                            val err = result.exceptionOrNull()?.message ?: "Ошибка подключения"
                                            testStatusText = if (err.contains("401")) {
                                                "Ошибка 401: Неверный ключ"
                                            } else {
                                                "Ошибка: ${err.take(28)}..."
                                            }
                                            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = when (testStatusSuccess) {
                                        true -> CyberneticSecondary
                                        false -> CyberneticError
                                        null -> CyberneticPrimary
                                    }
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    when (testStatusSuccess) {
                                        true -> CyberneticSecondary.copy(alpha = 0.5f)
                                        false -> CyberneticError.copy(alpha = 0.5f)
                                        null -> CyberneticPrimary.copy(alpha = 0.4f)
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                            ) {
                                if (isTestingKey) {
                                    CircularProgressIndicator(
                                        color = CyberneticPrimary,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Проверка Vision соединения...",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    Icon(
                                        imageVector = when (testStatusSuccess) {
                                            true -> Icons.Default.CheckCircle
                                            false -> Icons.Default.ErrorOutline
                                            null -> Icons.Default.Speed
                                        },
                                        contentDescription = null,
                                        tint = when (testStatusSuccess) {
                                            true -> CyberneticSecondary
                                            false -> CyberneticError
                                            null -> CyberneticPrimary
                                        },
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = testStatusText ?: "Проверить текущее Vision подключение",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // Confidence Threshold (4 Segments)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Порог уверенности нейросети",
                                    color = CyberneticOnSurfaceVariant,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "$aiConfidence% (Строгий)",
                                    color = CyberneticPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val thresholds = listOf(
                                    60 to "Мягкий",
                                    75 to "Оптимальный",
                                    85 to "Строгий (Рек.)",
                                    95 to "Макс. точность"
                                )
                                thresholds.forEach { (threshold, label) ->
                                    val isSelected = aiConfidence == threshold
                                    Surface(
                                        onClick = { statusViewModel.setConfidence(threshold) },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) CyberneticPrimary.copy(alpha = 0.15f) else CyberneticSurfaceContainerLow,
                                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, CyberneticPrimary) else null,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(8.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "$threshold%",
                                                color = if (isSelected) CyberneticPrimary else CyberneticOnSurface,
                                                fontSize = 13.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = label,
                                                color = if (isSelected) CyberneticPrimary else CyberneticOutline,
                                                fontSize = 9.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Photo Count selector
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Количество фото для анализа",
                                color = CyberneticOnSurfaceVariant,
                                fontSize = 12.sp
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CyberneticSurfaceContainerLow)
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val photoOptions = listOf(1 to "1 фото", 2 to "2 фото", 3 to "3 фото", 5 to "Все")
                                photoOptions.forEach { (count, label) ->
                                    val isSelected = aiMaxPhotos == count
                                    Surface(
                                        onClick = { statusViewModel.setMaxPhotos(count) },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) CyberneticPrimary else Color.Transparent,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
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
                    }
                }
            }

            // 3. FILTRATION RULES CARD
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ПРАВИЛА ФИЛЬТРАЦИИ ОБЪЯВЛЕНИЙ",
                        color = CyberneticSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "5 активно",
                        color = CyberneticSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyberneticOutlineVariant.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberneticSurfaceContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        RuleToggleItem(
                            title = "Фильтровать запчасти и битые",
                            description = "Автоматически отсеивать объявления с битыми экранами, iCloud lock, запчастями и коробками",
                            checked = aiCheckSuspicious,
                            onCheckedChange = { statusViewModel.setCheckSuspicious(it) }
                        )
                        Divider(color = CyberneticSurfaceContainerHighest, thickness = 1.dp)

                        RuleToggleItem(
                            title = "Проверка соответствия фото и заголовка",
                            description = "Отклонять, если на фото другая модель или параметры",
                            checked = aiStrictMode,
                            onCheckedChange = { statusViewModel.setStrictMode(it) }
                        )
                        Divider(color = CyberneticSurfaceContainerHighest, thickness = 1.dp)

                        RuleToggleItem(
                            title = "Без звука для отклоненных",
                            description = "Тихо отправлять отсеянное во вкладку «Отклонено»",
                            checked = aiNotifyRejected,
                            onCheckedChange = { statusViewModel.setAiNotifyRejected(it) }
                        )
                    }
                }
            }


            // 5. О ПРОГРАММЕ + режим разработчика
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CyberneticOutlineVariant.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CyberneticSurfaceContainer)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "О ПРОГРАММЕ",
                        color = CyberneticPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    // Тапни 4 раза быстро — включится режим разработчика
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val now = System.currentTimeMillis()
                                if (now - devLastTapMs < 1200) devTapCount += 1 else devTapCount = 1
                                devLastTapMs = now
                                if (devTapCount >= 4) {
                                    devMode = !devMode
                                    prefsDev.edit().putBoolean("dev_mode", devMode).apply()
                                    Toast.makeText(
                                        context,
                                        if (devMode) "Режим разработчика включён" else "Режим разработчика выключен",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    devTapCount = 0
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = CyberneticSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "OLX Radar · версия ${BuildConfig.VERSION_NAME}", // схема нумерации версий (ГГММДД + счетчик).
                            color = CyberneticOnSurface,
                            fontSize = 13.sp
                        )
                    }
                    Text(
                        text = "Разработчики: Deads_xB и neyseras",
                        color = CyberneticOnSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }

            // Режим разработчика: журнал отладки
            if (devMode) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyberneticError.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberneticSurfaceContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Кнопка: Логи мышления ИИ
                        Button(
                            onClick = { showAiThoughtsDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyberneticSecondary.copy(alpha = 0.25f),
                                contentColor = CyberneticSecondary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Логи мышления ИИ (${aiThoughtLogs.size})",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Divider(color = CyberneticSurfaceContainerHighest, thickness = 1.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "ЖУРНАЛ ОТЛАДКИ (${logEntries.size})",
                                color = CyberneticError,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    cm.setPrimaryClip(
                                        android.content.ClipData.newPlainText("OLX Radar logs", AppLog.asText())
                                    )
                                    Toast.makeText(context, "Журнал скопирован", Toast.LENGTH_SHORT).show()
                                }) {
                                    Text("Копировать", color = CyberneticPrimary, fontSize = 12.sp)
                                }
                                TextButton(onClick = { AppLog.clear() }) {
                                    Text("Очистить", color = CyberneticOnSurfaceVariant, fontSize = 12.sp)
                                }
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = CyberneticSurfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                val recent = logEntries.takeLast(120).asReversed()
                                items(recent.size) { idx ->
                                    val e = recent[idx]
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
                                    item {
                                        Text(
                                            text = "Журнал пуст",
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

            // 4. SAVE BUTTON
            Button(
                onClick = {
                    Toast.makeText(context, "Настройки успешно сохранены", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberneticPrimary,
                    contentColor = CyberneticOnPrimary
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Сохранить настройки",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // Provider & Vision Model Config Dialog
    if (showProviderConfigDialog) {
        AiProviderConfigDialog(
            currentProvider = aiProvider,
            currentBaseUrl = aiBaseUrl,
            currentModelId = aiModelId,
            currentApiKeyMasked = aiApiKeyMasked,
            onDismiss = { showProviderConfigDialog = false },
            onApply = { provider, baseUrl, apiKey, modelId ->
                statusViewModel.applyAiConfig(provider, baseUrl, apiKey, modelId)
                showProviderConfigDialog = false
                testStatusText = null
                testStatusSuccess = true
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

    if (showAiThoughtsDialog) {
        AiThoughtsDialog(
            logs = aiThoughtLogs,
            onDismiss = { showAiThoughtsDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiProviderConfigDialog(
    currentProvider: String,
    currentBaseUrl: String,
    currentModelId: String,
    currentApiKeyMasked: String,
    onDismiss: () -> Unit,
    onApply: (provider: String, baseUrl: String, apiKey: String, modelId: String) -> Unit,
    onTestVision: suspend (provider: String, baseUrl: String, apiKey: String, modelId: String) -> Result<GroqVisionClient.VisionCheckResult>,
    onFetchModels: suspend (baseUrl: String, apiKey: String) -> Result<List<String>>
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedProvider by remember { mutableStateOf(currentProvider) }
    var enteredBaseUrl by remember { mutableStateOf(currentBaseUrl) }
    var enteredApiKey by remember { mutableStateOf("") }
    var enteredModelId by remember { mutableStateOf(currentModelId) }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    var isTestingVision by remember { mutableStateOf(false) }
    var visionCheckResult by remember { mutableStateOf<Result<GroqVisionClient.VisionCheckResult>?>(null) }
    var isVerified by remember { mutableStateOf(false) }

    var availableModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingModels by remember { mutableStateOf(false) }

    fun invalidateVerification() {
        isVerified = false
        visionCheckResult = null
    }

    val activeProviderConfig = remember(selectedProvider) {
        AiProvider.fromId(selectedProvider)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CyberneticSurfaceContainer,
        title = {
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
                        imageVector = Icons.Default.SmartToy,
                        contentDescription = null,
                        tint = CyberneticPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Провайдер и Vision-модель",
                        color = CyberneticOnSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
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
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Провайдер (Селектор в виде чипов)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Выберите провайдера:",
                        color = CyberneticOnSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AiProvider.entries.forEach { p ->
                            val isSelected = p.id.equals(selectedProvider, ignoreCase = true)
                            Surface(
                                onClick = {
                                    if (!isSelected) {
                                        selectedProvider = p.id
                                        enteredBaseUrl = p.defaultBaseUrl
                                        enteredModelId = p.defaultModel
                                        invalidateVerification()
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) CyberneticPrimary.copy(alpha = 0.2f) else CyberneticSurfaceContainerLow,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) CyberneticPrimary else CyberneticOutlineVariant.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
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

                // Base URL
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Base URL (OpenAI API)",
                            color = CyberneticOnSurfaceVariant,
                            fontSize = 12.sp
                        )
                        if (!enteredBaseUrl.equals(activeProviderConfig.defaultBaseUrl, ignoreCase = true)) {
                            Text(
                                text = "Сброс к дефолту",
                                color = CyberneticPrimary,
                                fontSize = 11.sp,
                                modifier = Modifier.clickable {
                                    enteredBaseUrl = activeProviderConfig.defaultBaseUrl
                                    invalidateVerification()
                                }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = enteredBaseUrl,
                        onValueChange = {
                            enteredBaseUrl = it
                            invalidateVerification()
                        },
                        placeholder = { Text("https://.../v1") },
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

                // API Key
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "API Ключ (${activeProviderConfig.keyHint})",
                        color = CyberneticOnSurfaceVariant,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = enteredApiKey,
                        onValueChange = {
                            enteredApiKey = it
                            invalidateVerification()
                        },
                        placeholder = {
                            Text(
                                if (currentApiKeyMasked.isNotBlank()) "Текущий: $currentApiKeyMasked (оставьте пустым, если не меняете)"
                                else activeProviderConfig.keyPlaceholder
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = CyberneticOnSurface,
                            unfocusedTextColor = CyberneticOnSurface,
                            focusedBorderColor = CyberneticPrimary,
                            unfocusedBorderColor = CyberneticOutlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Model ID
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Идентификатор модели (Model ID)",
                            color = CyberneticOnSurfaceVariant,
                            fontSize = 12.sp
                        )

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
                                    res.onSuccess {
                                        availableModels = it
                                    }
                                }
                            }
                        )
                    }

                    OutlinedTextField(
                        value = enteredModelId,
                        onValueChange = {
                            enteredModelId = it
                            invalidateVerification()
                        },
                        placeholder = { Text(activeProviderConfig.defaultModel) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = CyberneticOnSurface,
                            unfocusedTextColor = CyberneticOnSurface,
                            focusedBorderColor = CyberneticPrimary,
                            unfocusedBorderColor = CyberneticOutlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Suggested models chips
                    if (activeProviderConfig.suggestedVisionModels.isNotEmpty()) {
                        Text(
                            text = "Рекомендуемые Vision-модели:",
                            color = CyberneticOnSurfaceVariant,
                            fontSize = 11.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            activeProviderConfig.suggestedVisionModels.forEach { sugModel ->
                                val isSelected = sugModel.equals(enteredModelId.trim(), ignoreCase = true)
                                Surface(
                                    onClick = {
                                        enteredModelId = sugModel
                                        invalidateVerification()
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) CyberneticPrimary.copy(alpha = 0.2f) else CyberneticSurfaceContainerLow,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) CyberneticPrimary else CyberneticOutlineVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Text(
                                        text = sugModel.substringAfterLast('/'),
                                        color = if (isSelected) CyberneticPrimary else CyberneticOnSurface,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Loaded models chips if any
                    if (availableModels.isNotEmpty()) {
                        Text(
                            text = "Модели с вашего сервера (${availableModels.size}):",
                            color = CyberneticOnSurfaceVariant,
                            fontSize = 11.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            availableModels.take(4).forEach { srvModel ->
                                val isSelected = srvModel.equals(enteredModelId.trim(), ignoreCase = true)
                                Surface(
                                    onClick = {
                                        enteredModelId = srvModel
                                        invalidateVerification()
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) CyberneticPrimary.copy(alpha = 0.2f) else CyberneticSurfaceContainerLow,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (isSelected) CyberneticPrimary else CyberneticOutlineVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Text(
                                        text = srvModel.substringAfterLast('/'),
                                        color = if (isSelected) CyberneticPrimary else CyberneticOnSurface,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Кнопка и статус проверки зрение (Vision)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when {
                        isVerified -> CyberneticSecondary.copy(alpha = 0.12f)
                        visionCheckResult?.isFailure == true -> CyberneticError.copy(alpha = 0.12f)
                        else -> CyberneticSurfaceContainerLow
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        when {
                            isVerified -> CyberneticSecondary.copy(alpha = 0.6f)
                            visionCheckResult?.isFailure == true -> CyberneticError.copy(alpha = 0.6f)
                            else -> CyberneticTertiary.copy(alpha = 0.4f)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
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
                                    imageVector = when {
                                        isVerified -> Icons.Default.CheckCircle
                                        visionCheckResult?.isFailure == true -> Icons.Default.ErrorOutline
                                        else -> Icons.Default.Visibility
                                    },
                                    contentDescription = null,
                                    tint = when {
                                        isVerified -> CyberneticSecondary
                                        visionCheckResult?.isFailure == true -> CyberneticError
                                        else -> CyberneticTertiary
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "ПРОВЕРКА VISION",
                                    color = when {
                                        isVerified -> CyberneticSecondary
                                        visionCheckResult?.isFailure == true -> CyberneticError
                                        else -> CyberneticTertiary
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (isVerified) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = CyberneticSecondary.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "ПОДТВЕРЖДЕНО",
                                        color = CyberneticSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = when {
                                isVerified -> visionCheckResult?.getOrNull()?.message ?: "Модель успешно подтвердила поддержку зрения (200 OK)."
                                visionCheckResult?.isFailure == true -> visionCheckResult?.exceptionOrNull()?.message ?: "Модель не поддерживает зрение."
                                else -> "Перед сохранением необходимо обязательно протестировать модель на поддержку изображений (1x1 probe)."
                            },
                            color = when {
                                isVerified -> CyberneticSecondary
                                visionCheckResult?.isFailure == true -> CyberneticError
                                else -> CyberneticOnSurfaceVariant
                            },
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )

                        Button(
                            onClick = {
                                isTestingVision = true
                                visionCheckResult = null
                                coroutineScope.launch {
                                    val res = onTestVision(
                                        selectedProvider,
                                        enteredBaseUrl,
                                        enteredApiKey,
                                        enteredModelId
                                    )
                                    isTestingVision = false
                                    visionCheckResult = res
                                    isVerified = res.isSuccess
                                }
                            },
                            enabled = !isTestingVision && enteredModelId.isNotBlank() && enteredBaseUrl.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isVerified) CyberneticSecondary else CyberneticPrimary,
                                contentColor = CyberneticOnPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                        ) {
                            if (isTestingVision) {
                                CircularProgressIndicator(
                                    color = CyberneticOnPrimary,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Тестирование зрение модели (1x1 probe)...", fontSize = 12.sp)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isVerified) "Повторить проверку Vision" else "Проверить модель на Vision",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(selectedProvider, enteredBaseUrl, enteredApiKey, enteredModelId)
                },
                enabled = isVerified && !isTestingVision,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberneticPrimary,
                    contentColor = CyberneticOnPrimary,
                    disabledContainerColor = CyberneticSurfaceContainerHighest,
                    disabledContentColor = CyberneticOnSurfaceVariant
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isVerified) "Сохранить и применить" else "Проверьте Vision для сохранения",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = CyberneticOnSurfaceVariant)
            }
        }
    )
}

@Composable
fun AiThoughtsDialog(
    logs: List<ua.magerko.olxradar.ai.AiThoughtLog>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Логи мышления ИИ (${logs.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyberneticOnSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(
                                android.content.ClipData.newPlainText("AI Thoughts", ua.magerko.olxradar.ai.AiThoughtLogger.asFormattedText())
                            )
                            Toast.makeText(context, "Логи мышления ИИ скопированы", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberneticPrimary,
                            contentColor = CyberneticOnPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Копировать", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            ua.magerko.olxradar.ai.AiThoughtLogger.clear()
                            Toast.makeText(context, "Логи очищены", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Очистить", fontSize = 12.sp)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CyberneticSurfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                ) {
                    if (logs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Логи мышления ИИ пусты.\nОни появятся при следующем анализе лота.",
                                color = CyberneticOnSurfaceVariant,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(logs.size) { idx ->
                                val log = logs[idx]
                                val borderTint = when (log.status) {
                                    AiStatus.REJECTED -> CyberneticError.copy(alpha = 0.4f)
                                    AiStatus.MATCH -> CyberneticSecondary.copy(alpha = 0.4f)
                                    AiStatus.NEEDS_REVIEW, AiStatus.QUEUED -> CyberneticTertiary.copy(alpha = 0.4f)
                                    AiStatus.ERROR -> CyberneticError.copy(alpha = 0.4f)
                                    else -> CyberneticPrimary.copy(alpha = 0.3f)
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = CyberneticSurfaceContainerHigh,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, borderTint),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val statusLabel = when (log.status) {
                                                AiStatus.REJECTED -> "ОТКЛОНЕНО"
                                                AiStatus.MATCH -> "ПОДХОДИТ"
                                                AiStatus.NEEDS_REVIEW -> "НА ПРОВЕРКЕ"
                                                AiStatus.QUEUED -> "В ОЧЕРЕДИ"
                                                AiStatus.ERROR -> "ОШИБКА"
                                                "cached" -> "ИЗ КЭША"
                                                else -> log.status.uppercase()
                                            }
                                            val statusColor = when (log.status) {
                                                AiStatus.REJECTED, AiStatus.ERROR -> CyberneticError
                                                AiStatus.MATCH -> CyberneticSecondary
                                                AiStatus.NEEDS_REVIEW, AiStatus.QUEUED -> CyberneticTertiary
                                                else -> CyberneticPrimary
                                            }
                                            Text(
                                                text = "${log.timeFormatted()} · $statusLabel",
                                                color = statusColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (log.confidence != null) {
                                                Text(
                                                    text = "Уверенность: ${log.confidence}%",
                                                    color = CyberneticOnSurfaceVariant,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }

                                        Text(
                                            text = log.title,
                                            color = CyberneticOnSurface,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Радар: ${log.radarTitle}",
                                                color = CyberneticPrimary,
                                                fontSize = 10.sp
                                            )
                                            Text(
                                                text = log.price,
                                                color = CyberneticSecondary,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        if (!log.error.isNullOrBlank()) {
                                            Text(
                                                text = "Ошибка: ${log.error}",
                                                color = CyberneticError,
                                                fontSize = 10.sp
                                            )
                                        }

                                        if (log.reasons.isNotBlank()) {
                                            Text(
                                                text = log.reasons,
                                                color = CyberneticOnSurfaceVariant,
                                                fontSize = 10.sp,
                                                lineHeight = 14.sp
                                            )
                                        }

                                        if (!log.thinking.isNullOrBlank()) {
                                            Surface(
                                                color = CyberneticSurfaceContainerLowest,
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                    Text(
                                                        text = "Рассуждения модели (<think>):",
                                                        color = CyberneticTertiary,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = log.thinking,
                                                        color = CyberneticOnSurfaceVariant,
                                                        fontSize = 10.sp,
                                                        lineHeight = 13.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        maxLines = 8,
                                                        overflow = TextOverflow.Ellipsis
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
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть", color = CyberneticPrimary)
            }
        }
    )
}

@Composable
fun RuleToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                color = CyberneticOnSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp
            )
            Text(
                text = description,
                color = CyberneticOnSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = CyberneticOnSecondary,
                checkedTrackColor = CyberneticSecondary,
                uncheckedThumbColor = CyberneticOutline,
                uncheckedTrackColor = CyberneticSurfaceContainerHighest
            )
        )
    }
}

private fun verdictLabel(status: String): String = when (status) {
    AiStatus.MATCH -> "Подходит"
    AiStatus.NEEDS_REVIEW -> "На проверке"
    AiStatus.REJECTED -> "Отклонено"
    AiStatus.ERROR -> "Ошибка анализа"
    AiStatus.SKIPPED -> "Пропущено"
    else -> "Готово"
}

private fun relativeSec(sec: Long): String = when {
    sec < 60 -> "${sec} с назад"
    sec < 3600 -> "${sec / 60} мин назад"
    sec < 86400 -> "${sec / 3600} ч назад"
    else -> "${sec / 86400} дн назад"
}
