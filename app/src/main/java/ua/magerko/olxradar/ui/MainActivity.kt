package ua.magerko.olxradar.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import ua.magerko.olxradar.data.backup.PersistentStorageManager
import ua.magerko.olxradar.data.model.Finding
import ua.magerko.olxradar.service.MonitorService
import ua.magerko.olxradar.ui.compose.CyberneticRadarTheme
import ua.magerko.olxradar.ui.compose.MainComposeScreen
import ua.magerko.olxradar.ui.compose.NavigationTab

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OPEN_FINDING_ID = "extra_open_finding_id"
        const val EXTRA_OPEN_REJECTED_TAB = "extra_open_rejected_tab"
    }

    private lateinit var findingsViewModel: FindingsViewModel
    private lateinit var watchesViewModel: WatchesViewModel
    private lateinit var statusViewModel: StatusViewModel

    private val selectedFindingToOpen = mutableStateOf<Finding?>(null)
    private val crashReportState = mutableStateOf<String?>(null)
    private val initialTabState = mutableStateOf(NavigationTab.FINDINGS)
    private val sharedOlxUrlState = mutableStateOf<String?>(null)

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Восстановление сохранённой пользователем темы оформления
        val savedTheme = ua.magerko.olxradar.ui.compose.ThemePreferences.get(this)
        ua.magerko.olxradar.ui.compose.applyThemeMode(savedTheme)

        val factory = ViewModelFactory(this)
        findingsViewModel = ViewModelProvider(this, factory)[FindingsViewModel::class.java]
        watchesViewModel = ViewModelProvider(this, factory)[WatchesViewModel::class.java]
        statusViewModel = ViewModelProvider(this, factory)[StatusViewModel::class.java]

        setupPermissions()

        val openRejected = intent?.getBooleanExtra(EXTRA_OPEN_REJECTED_TAB, false) ?: false
        if (openRejected) {
            initialTabState.value = NavigationTab.REJECTED
        }

        handleNotificationIntent(intent)
        handleShareIntent(intent)
        startRadarService()

        crashReportState.value = try {
            File(filesDir, "last_crash.txt").takeIf { it.exists() }?.readText()
        } catch (_: Exception) {
            null
        }

        setContent {
            CyberneticRadarTheme {
                if (crashReportState.value != null) {
                    CrashReportScreen(
                        report = crashReportState.value.orEmpty(),
                        onDismiss = {
                            try { File(filesDir, "last_crash.txt").delete() } catch (_: Exception) {}
                            crashReportState.value = null
                        }
                    )
                } else {
                    MainComposeScreen(
                        findingsViewModel = findingsViewModel,
                        watchesViewModel = watchesViewModel,
                        statusViewModel = statusViewModel,
                        initialTab = initialTabState.value,
                        initialFindingToOpen = selectedFindingToOpen.value,
                        onClearInitialFinding = { selectedFindingToOpen.value = null },
                        initialSharedOlxUrl = sharedOlxUrlState.value,
                        onClearSharedOlxUrl = { sharedOlxUrlState.value = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val raw = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            val urlMatch = Regex("https?://[^\\s]*olx\\.ua[^\\s]*", RegexOption.IGNORE_CASE).find(raw)?.value ?: raw.trim()
            if (urlMatch.contains("olx.ua", ignoreCase = true)) {
                sharedOlxUrlState.value = urlMatch
                initialTabState.value = NavigationTab.RADARS
            } else {
                Toast.makeText(this, "В переданном тексте не найдена ссылка OLX.ua", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val findingId = intent?.getLongExtra(EXTRA_OPEN_FINDING_ID, -1L) ?: -1L
        if (findingId > 0) {
            val isRejected = intent?.getBooleanExtra(EXTRA_OPEN_REJECTED_TAB, false) ?: false
            if (isRejected) {
                initialTabState.value = NavigationTab.REJECTED
            }
            lifecycleScope.launch {
                val finding = findingsViewModel.getFindingById(findingId)
                if (finding != null && !isFinishing) {
                    findingsViewModel.markAsRead(finding)
                    selectedFindingToOpen.value = finding
                }
            }
        }
    }

    fun onTabSwitchedFromFragment(isRejected: Boolean) {
        if (isRejected) {
            initialTabState.value = NavigationTab.REJECTED
        } else {
            initialTabState.value = NavigationTab.FINDINGS
        }
    }

    private fun setupPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun startRadarService() {
        val intent = Intent(this, MonitorService::class.java).apply {
            action = MonitorService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

/** Экран отчёта о сбое: показывает стектрейс последнего падения, позволяет скопировать и сохранить в Documents. */
@Composable
private fun CrashReportScreen(report: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1326))
            .systemBarsPadding()
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Приложение упало в прошлый раз",
                color = Color(0xFFE7ECF6),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Скопируйте стектрейс или сохраните его в Documents/OLXRadar.",
                color = Color(0xFF9AA7B8),
                fontSize = 13.sp
            )
            SelectionContainer(modifier = Modifier.weight(1f)) {
                Text(
                    text = report,
                    color = Color(0xFFBFE3D8),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(report))
                        Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00E5FF),
                        contentColor = Color(0xFF00363D)
                    ),
                    modifier = Modifier.weight(1f)
                ) { Text("Копировать") }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            val res = PersistentStorageManager.exportDiagnosticFile(
                                context,
                                "olx_radar_crash_${System.currentTimeMillis()}.txt",
                                report
                            )
                            Toast.makeText(
                                context,
                                res.fold(
                                    onSuccess = { "Сохранено в $it" },
                                    onFailure = { "Ошибка: ${it.message}" }
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE7ECF6)),
                    modifier = Modifier.weight(1f)
                ) { Text("В файл") }

                OutlinedButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE7ECF6)),
                    modifier = Modifier.weight(1f)
                ) { Text("Дальше") }
            }
        }
    }
}
