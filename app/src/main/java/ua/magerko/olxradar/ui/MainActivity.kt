package ua.magerko.olxradar.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ua.magerko.olxradar.data.model.Finding
import ua.magerko.olxradar.service.MonitorService
import ua.magerko.olxradar.ui.compose.CyberneticRadarTheme
import ua.magerko.olxradar.ui.compose.MainComposeScreen
import ua.magerko.olxradar.ui.compose.NavigationTab
import ua.magerko.olxradar.util.AppLog

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_OPEN_FINDING_ID = "extra_open_finding_id"
        const val EXTRA_OPEN_REJECTED_TAB = "extra_open_rejected_tab"
    }

    private lateinit var findingsViewModel: FindingsViewModel
    private lateinit var watchesViewModel: WatchesViewModel
    private lateinit var statusViewModel: StatusViewModel

    private val selectedFindingToOpen = mutableStateOf<Finding?>(null)
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

        setContent {
            CyberneticRadarTheme {
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
        initialTabState.value = if (isRejected) NavigationTab.REJECTED else NavigationTab.FINDINGS
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
        try {
            val intent = Intent(this, MonitorService::class.java).apply {
                action = MonitorService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            AppLog.w("MainActivity", "Failed to start MonitorService: ${e.message}", e)
        }
    }
}
