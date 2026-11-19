package ua.magerko.olxradar.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import ua.magerko.olxradar.R
import ua.magerko.olxradar.data.model.Watch
import ua.magerko.olxradar.databinding.FragmentStatusBinding
import ua.magerko.olxradar.service.MonitorService
import ua.magerko.olxradar.service.ServiceStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StatusFragment : Fragment() {

    private var _binding: FragmentStatusBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: StatusViewModel

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity(), ViewModelFactory(requireContext()))[StatusViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStatusBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupServiceButtons()
        setupBatteryOptimizationButton()
        setupAiSettings()
        setupQuietHours()
        setupIntervalChips()
        setupClearFindings()
        setupObservers()
    }

    private fun setupServiceButtons() {
        binding.btnStartService.setOnClickListener {
            val intent = Intent(requireContext(), MonitorService::class.java).apply {
                action = MonitorService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requireContext().startForegroundService(intent)
            } else {
                requireContext().startService(intent)
            }
        }

        binding.btnPauseService.setOnClickListener {
            val isPaused = viewModel.radarStatus.value.isPaused
            val action = if (isPaused) MonitorService.ACTION_RESUME else MonitorService.ACTION_PAUSE
            val intent = Intent(requireContext(), MonitorService::class.java).apply {
                this.action = action
            }
            requireContext().startService(intent)
        }

        binding.btnStopService.setOnClickListener {
            val intent = Intent(requireContext(), MonitorService::class.java).apply {
                action = MonitorService.ACTION_STOP
            }
            requireContext().startService(intent)
        }
    }

    private fun setupBatteryOptimizationButton() {
        binding.btnBatteryOptimization.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                startActivity(intent)
            } catch (_: Exception) {
                try {
                    val fallbackIntent = Intent(Settings.ACTION_SETTINGS)
                    startActivity(fallbackIntent)
                } catch (_: Exception) {
                    Toast.makeText(requireContext(), R.string.status_battery_not_supported, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupQuietHours() {
        binding.switchQuietHours.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setQuietHoursEnabled(isChecked)
            binding.layoutQuietHoursPickers.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        binding.btnQuietFrom.setOnClickListener {
            val h = viewModel.quietHoursFromHour.value
            val m = viewModel.quietHoursFromMin.value
            TimePickerDialog(requireContext(), { _, hourOfDay, minute ->
                viewModel.setQuietHoursFrom(hourOfDay, minute)
            }, h, m, true).show()
        }

        binding.btnQuietTo.setOnClickListener {
            val h = viewModel.quietHoursToHour.value
            val m = viewModel.quietHoursToMin.value
            TimePickerDialog(requireContext(), { _, hourOfDay, minute ->
                viewModel.setQuietHoursTo(hourOfDay, minute)
            }, h, m, true).show()
        }
    }

    private fun setupIntervalChips() {
        binding.chipGroupInterval.setOnCheckedStateChangeListener { _, checkedIds ->
            val sec = when {
                checkedIds.contains(R.id.chip_15s) -> 15
                checkedIds.contains(R.id.chip_25s) -> 25
                checkedIds.contains(R.id.chip_60s) -> 60
                checkedIds.contains(R.id.chip_300s) -> 300
                else -> 25
            }
            viewModel.setInterval(sec)
        }
    }

    private fun setupClearFindings() {
        binding.btnClearFindings.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.clear_findings_confirm_title)
                .setMessage(R.string.clear_findings_confirm_msg)
                .setPositiveButton(R.string.action_delete) { _, _ ->
                    viewModel.clearAllFindings()
                    Toast.makeText(requireContext(), R.string.clear_findings_cleared, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.action_cancel, null)
                .show()
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.radarStatus.collect { status ->
                        updateStatusUi(status)
                    }
                }

                launch {
                    viewModel.quietHoursEnabled.collect { enabled ->
                        binding.switchQuietHours.isChecked = enabled
                        binding.layoutQuietHoursPickers.visibility = if (enabled) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.quietHoursFromHour.collect { h ->
                        val m = viewModel.quietHoursFromMin.value
                        binding.btnQuietFrom.text = getString(R.string.status_quiet_hours_from, h, m)
                    }
                }

                launch {
                    viewModel.quietHoursToHour.collect { h ->
                        val m = viewModel.quietHoursToMin.value
                        binding.btnQuietTo.text = getString(R.string.status_quiet_hours_to, h, m)
                    }
                }

                launch {
                    viewModel.currentInterval.collect { interval ->
                        when (interval) {
                            15 -> binding.chip15s.isChecked = true
                            25 -> binding.chip25s.isChecked = true
                            60 -> binding.chip60s.isChecked = true
                            300 -> binding.chip300s.isChecked = true
                        }
                    }
                }

                launch {
                    viewModel.watches.collect { watches ->
                        updateDiagnostics(watches, viewModel.radarStatus.value)
                    }
                }

                launch {
                    viewModel.aiEnabled.collect { enabled ->
                        binding.switchAiEnabled.isChecked = enabled
                        binding.layoutAiOptions.visibility = if (enabled) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.aiApiKeyMasked.collect { masked ->
                        if (masked.isNotBlank() && masked != "••••••••") {
                            binding.tvAiApiKeyStatus.text = getString(R.string.ai_api_key_configured, masked)
                        } else {
                            binding.tvAiApiKeyStatus.text = getString(R.string.ai_api_key_not_configured)
                        }
                    }
                }

                launch {
                    viewModel.aiModelId.collect { model ->
                        if (binding.etAiModelId.text?.toString() != model) {
                            binding.etAiModelId.setText(model)
                        }
                    }
                }

                launch {
                    viewModel.aiConfidence.collect { conf ->
                        when (conf) {
                            60 -> binding.chipConf60.isChecked = true
                            75 -> binding.chipConf75.isChecked = true
                            85 -> binding.chipConf85.isChecked = true
                            90 -> binding.chipConf90.isChecked = true
                        }
                    }
                }

                launch {
                    viewModel.aiMaxPhotos.collect { count ->
                        when (count) {
                            1 -> binding.chipPhotos1.isChecked = true
                            2 -> binding.chipPhotos2.isChecked = true
                            3 -> binding.chipPhotos3.isChecked = true
                        }
                    }
                }

                launch {
                    viewModel.aiCheckSuspicious.collect { checked ->
                        binding.switchCheckSuspicious.isChecked = checked
                    }
                }

                launch {
                    viewModel.aiStrictMode.collect { checked ->
                        binding.switchStrictMode.isChecked = checked
                    }
                }

                launch {
                    viewModel.aiManualReview.collect { checked ->
                        binding.switchManualReview.isChecked = checked
                    }
                }

                launch {
                    viewModel.aiNotifyRejected.collect { checked ->
                        binding.switchNotifyRejected.isChecked = checked
                    }
                }

                launch {
                    viewModel.aiMaxConcurrent.collect { limit ->
                        when (limit) {
                            1 -> binding.chipConc1.isChecked = true
                            2 -> binding.chipConc2.isChecked = true
                            4 -> binding.chipConc4.isChecked = true
                        }
                    }
                }

                launch {
                    viewModel.aiTimeoutSec.collect { sec ->
                        when (sec) {
                            15 -> binding.chipTimeout15s.isChecked = true
                            25 -> binding.chipTimeout25s.isChecked = true
                            45 -> binding.chipTimeout45s.isChecked = true
                        }
                    }
                }
            }
        }
    }

    private fun setupAiSettings() {
        binding.switchAiEnabled.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAiEnabled(isChecked)
            binding.layoutAiOptions.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        binding.btnChangeApiKey.setOnClickListener {
            showApiKeyDialog()
        }

        binding.btnTestGroqApi.setOnClickListener {
            binding.btnTestGroqApi.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val result = viewModel.testGroqApi()
                binding.btnTestGroqApi.isEnabled = true
                result.onSuccess { msg ->
                    Toast.makeText(requireContext(), getString(R.string.ai_test_success) + "\n" + msg, Toast.LENGTH_LONG).show()
                }.onFailure { err ->
                    Toast.makeText(requireContext(), getString(R.string.ai_test_failed, err.localizedMessage ?: err.message), Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.etAiModelId.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                val model = s?.toString()?.trim() ?: ""
                if (model.isNotEmpty() && model != viewModel.aiModelId.value) {
                    viewModel.setModelId(model)
                }
            }
        })

        binding.chipGroupConfidence.setOnCheckedStateChangeListener { _, checkedIds ->
            val conf = when {
                checkedIds.contains(R.id.chip_conf_60) -> 60
                checkedIds.contains(R.id.chip_conf_75) -> 75
                checkedIds.contains(R.id.chip_conf_85) -> 85
                checkedIds.contains(R.id.chip_conf_90) -> 90
                else -> 75
            }
            viewModel.setConfidence(conf)
        }

        binding.chipGroupMaxPhotos.setOnCheckedStateChangeListener { _, checkedIds ->
            val count = when {
                checkedIds.contains(R.id.chip_photos_1) -> 1
                checkedIds.contains(R.id.chip_photos_2) -> 2
                checkedIds.contains(R.id.chip_photos_3) -> 3
                else -> 2
            }
            viewModel.setMaxPhotos(count)
        }

        binding.switchCheckSuspicious.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setCheckSuspicious(isChecked)
        }

        binding.switchStrictMode.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setStrictMode(isChecked)
        }

        binding.switchManualReview.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setManualReview(isChecked)
        }

        binding.switchNotifyRejected.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setAiNotifyRejected(isChecked)
        }

        binding.chipGroupConcurrency.setOnCheckedStateChangeListener { _, checkedIds ->
            val conc = when {
                checkedIds.contains(R.id.chip_conc_1) -> 1
                checkedIds.contains(R.id.chip_conc_2) -> 2
                checkedIds.contains(R.id.chip_conc_4) -> 4
                else -> 2
            }
            viewModel.setMaxConcurrent(conc)
        }

        binding.chipGroupAiTimeout.setOnCheckedStateChangeListener { _, checkedIds ->
            val sec = when {
                checkedIds.contains(R.id.chip_timeout_15s) -> 15
                checkedIds.contains(R.id.chip_timeout_25s) -> 25
                checkedIds.contains(R.id.chip_timeout_45s) -> 45
                else -> 25
            }
            viewModel.setTimeoutSec(sec)
        }
    }

    private fun showApiKeyDialog() {
        val container = android.widget.FrameLayout(requireContext())
        val density = resources.displayMetrics.density
        val padH = (24 * density).toInt()
        val padV = (8 * density).toInt()

        val input = android.widget.EditText(requireContext()).apply {
            hint = getString(R.string.dialog_set_api_key_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val params = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(padH, padV, padH, padV)
        }
        container.addView(input, params)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_set_api_key_title)
            .setView(container)
            .setPositiveButton(R.string.action_save_api_key) { _, _ ->
                val newKey = input.text.toString().trim()
                if (newKey.isNotEmpty()) {
                    viewModel.setApiKey(newKey)
                    Toast.makeText(requireContext(), R.string.ai_api_key_configured, Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.resetApiKey()
                    Toast.makeText(requireContext(), R.string.ai_api_key_reset, Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton(R.string.action_reset_default_key) { _, _ ->
                viewModel.resetApiKey()
                Toast.makeText(requireContext(), R.string.ai_api_key_reset, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun updateStatusUi(status: ServiceStatus) {
        val statusText = when {
            !status.isRunning -> getString(R.string.service_stopped)
            status.isPaused -> getString(R.string.service_paused)
            else -> getString(R.string.status_service_running_247)
        }
        binding.tvServiceStatus.text = statusText
        binding.tvStatsChecks.text = getString(R.string.status_checks_count, status.checksCount)

        val lastCheckedStr = if (status.lastCheckedAt != null && status.lastCheckedAt > 0) {
            timeFormat.format(Date(status.lastCheckedAt))
        } else {
            "—"
        }
        binding.tvStatsLastCheck.text = getString(R.string.status_last_poll, lastCheckedStr)

        binding.btnPauseService.text = if (status.isPaused) {
            getString(R.string.action_resume)
        } else {
            getString(R.string.action_pause)
        }

        binding.btnStartService.isEnabled = !status.isRunning
        binding.btnPauseService.isEnabled = status.isRunning
        binding.btnStopService.isEnabled = status.isRunning

        updateDiagnostics(viewModel.watches.value, status)
    }

    private fun updateDiagnostics(watches: List<Watch>, status: ServiceStatus) {
        val container = binding.containerWatchDiagnostics
        container.removeAllViews()

        if (watches.isEmpty()) {
            val emptyTv = TextView(requireContext()).apply {
                text = getString(R.string.status_no_active_watches)
                setTextColor(requireContext().getColor(R.color.text_secondary))
                setPadding(0, 16, 0, 16)
            }
            container.addView(emptyTv)
            return
        }

        for (watch in watches) {
            val itemBinding = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_status_watch, container, false)

            val tvQuery = itemBinding.findViewById<TextView>(R.id.tv_diag_query)
            val tvCode = itemBinding.findViewById<TextView>(R.id.tv_diag_code)
            val tvLastSuccess = itemBinding.findViewById<TextView>(R.id.tv_diag_last_success)
            val tvLastError = itemBinding.findViewById<TextView>(R.id.tv_diag_last_error)

            val watchStatus = status.perWatch[watch.id]
            val lastSuccess = watchStatus?.lastSuccessAt ?: watch.lastSuccessAt
            val lastError = watchStatus?.lastError ?: watch.lastError
            val lastHttpCode = watchStatus?.lastHttpCode ?: watch.lastHttpCode

            tvQuery.text = watch.query

            if (lastHttpCode != null) {
                tvCode.text = if (lastHttpCode == 200) {
                    getString(R.string.status_watch_code_ok)
                } else {
                    "HTTP $lastHttpCode"
                }
            } else {
                tvCode.text = ""
            }

            if (lastSuccess != null && lastSuccess > 0) {
                val rel = DateUtils.getRelativeTimeSpanString(
                    lastSuccess,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                )
                tvLastSuccess.text = getString(R.string.watch_last_success, rel)
            } else {
                tvLastSuccess.text = getString(R.string.watch_never_checked)
            }

            if (!lastError.isNullOrBlank()) {
                tvLastError.visibility = View.VISIBLE
                tvLastError.text = getString(R.string.watch_last_error_label, lastError)
            } else {
                tvLastError.visibility = View.GONE
            }

            container.addView(itemBinding)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
