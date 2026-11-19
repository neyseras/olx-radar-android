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
    val aiApiKeyMasked by statusViewModel.aiApiKeyMasked.collectAsState()
    val aiModelId by statusViewModel.aiModelId.collectAsState()
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

    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    var testStatusText by remember { mutableStateOf<String?>(null) }
    var testStatusSuccess by remember { mutableStateOf<Boolean?>(null) }
    var isTestingKey by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberneticSurface)
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

                    // Тумблер эффектов стекла (производительность)
                    var glassFxOn by remember { mutableStateOf(ThemePreferences.isGlassEffectsEnabled(context)) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Эффекты Liquid Glass",
                                color = CyberneticOnSurface,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (glassFxOn) "Включены — красиво, но тяжелее" else "Отключены — быстрее и экономнее",
                                color = CyberneticOnSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = glassFxOn,
                            onCheckedChange = { on ->
                                glassFxOn = on
                                ThemePreferences.setGlassEffectsEnabled(context, on)
                                Toast.makeText(
                                    context,
                                    if (on) "Эффекты включены" else "Эффекты отключены",
                                    Toast.LENGTH_SHORT
                                ).show()
                                (context as? Activity)?.recreate()
                            }
                        )
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
                        // Model Selector
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Модель распознавания",
                                color = CyberneticOnSurfaceVariant,
                                fontSize = 12.sp
                            )
                            Surface(
                                onClick = { showModelDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                color = CyberneticSurfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SmartToy,
                                            contentDescription = null,
                                            tint = CyberneticPrimary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Column {
                                            Text(
                                                text = aiModelId,
                                                color = CyberneticOnSurface,
                                                fontSize = 13.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "быстрая (~480 токенов/сек)",
                                                color = CyberneticSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = CyberneticOnSurfaceVariant
                                    )
                                }
                            }
                        }

                        // API Key Row
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "API ключ Groq",
                                    color = CyberneticOnSurfaceVariant,
                                    fontSize = 12.sp
                                )
                                TextButton(
                                    onClick = { showApiKeyDialog = true },
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
                                        text = "Изменить",
                                        color = CyberneticPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = CyberneticSurfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = null,
                                        tint = CyberneticOutline,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (aiApiKeyMasked.isNotBlank()) aiApiKeyMasked else "Не задан (нажмите, чтобы ввести)",
                                        color = if (aiApiKeyMasked.isNotBlank()) CyberneticOnSurface else CyberneticOnSurfaceVariant,
                                        fontSize = 13.sp,
                                        fontFamily = if (aiApiKeyMasked.isNotBlank()) FontFamily.Monospace else FontFamily.Default,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = CyberneticOutline,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            // Test Connection Button
                            OutlinedButton(
                                onClick = {
                                    if (aiApiKeyMasked.isBlank()) {
                                        testStatusText = "API-ключ не задан (введите ключ)"
                                        testStatusSuccess = false
                                        Toast.makeText(context, "Сначала введите API-ключ Groq", Toast.LENGTH_SHORT).show()
                                        return@OutlinedButton
                                    }
                                    isTestingKey = true
                                    testStatusText = null
                                    coroutineScope.launch {
                                        val result = statusViewModel.testGroqApi()
                                        isTestingKey = false
                                        if (result.isSuccess) {
                                            testStatusSuccess = true
                                            testStatusText = "200 OK: Соединение успешно!"
                                            Toast.makeText(context, result.getOrNull() ?: "Groq API подключен успешно!", Toast.LENGTH_LONG).show()
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
                                        text = "Проверка на api.groq.com...",
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
                                        text = testStatusText ?: "Проверить соединение (Groq Vision)",
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
                            title = "Уведомлять только о ценах ниже рынка",
                            description = "Флаг, если цена минимум на 20% ниже средней рыночной",
                            checked = true,
                            onCheckedChange = { }
                        )
                        Divider(color = CyberneticSurfaceContainerHighest, thickness = 1.dp)

                        RuleToggleItem(
                            title = "Звуковой сигнал при супер-выгодных сделках",
                            description = "Принудительное уведомление в обход режима «Не беспокоить»",
                            checked = true,
                            onCheckedChange = { }
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
                            text = "OLX Radar · версия ${BuildConfig.VERSION_NAME} (сборка ${BuildConfig.VERSION_CODE})",
                            color = CyberneticOnSurface,
                            fontSize = 13.sp
                        )
                    }
                    Text(
                        text = "Разработчики: Deads_xB и Neyseras",
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

    // API Key Dialog
    if (showApiKeyDialog) {
        var newKey by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            containerColor = CyberneticSurfaceContainer,
            title = {
                Text(
                    text = "Введите Groq API Key",
                    color = CyberneticOnSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Ключ формата gsk_... Получите на console.groq.com",
                        color = CyberneticOnSurfaceVariant,
                        fontSize = 12.sp
                    )
                    OutlinedTextField(
                        value = newKey,
                        onValueChange = { newKey = it },
                        label = { Text("Groq API Key") },
                        placeholder = { Text("gsk_...") },
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
                        if (newKey.isNotBlank()) {
                            statusViewModel.setApiKey(newKey.trim())
                        }
                        testStatusText = null
                        testStatusSuccess = null
                        showApiKeyDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyberneticPrimary,
                        contentColor = CyberneticOnPrimary
                    )
                ) {
                    Text("Сохранить", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Отмена", color = CyberneticOnSurfaceVariant)
                }
            }
        )
    }

    // Model Selector Dialog
    if (showModelDialog) {
        val models = listOf(
            "qwen/qwen3.8-27b-vision",
            "llama-3.2-11b-vision-preview",
            "llama-3.2-90b-vision-preview"
        )
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            containerColor = CyberneticSurfaceContainer,
            title = {
                Text(
                    text = "Выберите модель Groq Vision",
                    color = CyberneticOnSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    models.forEach { modelName ->
                        Surface(
                            onClick = {
                                statusViewModel.setModelId(modelName)
                                showModelDialog = false
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (modelName == aiModelId) CyberneticPrimary.copy(alpha = 0.2f) else CyberneticSurfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = modelName,
                                color = if (modelName == aiModelId) CyberneticPrimary else CyberneticOnSurface,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showModelDialog = false }) {
                    Text("Закрыть", color = CyberneticOnSurfaceVariant)
                }
            }
        )
    }
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
