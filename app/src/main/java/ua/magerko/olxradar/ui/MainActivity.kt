package ua.magerko.olxradar.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
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

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        startRadarService()

        // Отчёт о последнем сбое (пишется CrashGuard в Application).
        // Если прошлый запуск упал — показываем стектрейс: причину можно
        // скопировать и отправить без adb.
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
                        initialFindingToOpen = selectedFindingToOpen.value
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
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
                != PackageManager.PERMISSION_GRANTED) {
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

/** Экран отчёта о сбое: показывает стектрейс последнего падения, позволяет его скопировать. */
@Composable
private fun CrashReportScreen(report: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1326))
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
                text = "Нажми «Скопировать» и пришли мне текст — так я найду точную причину.",
                color = Color(0xFF9AA7B8),
                fontSize = 13.sp
            )
            SelectionContainer {
                Text(
                    text = report,
                    color = Color(0xFFBFE3D8),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { clipboard.setText(AnnotatedString(report)) },
                    modifier = Modifier.weight(1f)
                ) { Text("Скопировать") }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) { Text("Продолжить") }
            }
        }
    }
}

}
