package ua.magerko.olxradar.ui

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import ua.magerko.olxradar.R
import ua.magerko.olxradar.data.model.Watch
import ua.magerko.olxradar.databinding.BottomSheetEditWatchBinding

class EditWatchBottomSheetFragment : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_WATCH_ID = "arg_watch_id"

        fun newInstance(watchId: Long? = null): EditWatchBottomSheetFragment {
            return EditWatchBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    if (watchId != null) {
                        putLong(ARG_WATCH_ID, watchId)
                    }
                }
            }
        }
    }

    private var _binding: BottomSheetEditWatchBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: WatchesViewModel

    private var editingWatch: Watch? = null
    private val previewAdapter = TestListingsAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity(), ViewModelFactory(requireContext()))[WatchesViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetEditWatchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDropdowns()
        setupPreviewRecycler()

        val watchId = arguments?.getLong(ARG_WATCH_ID)
        if (watchId != null && watchId > 0) {
            val itemUi = viewModel.watchesUi.value.find { it.watch.id == watchId }
            if (itemUi != null) {
                editingWatch = itemUi.watch
                populateFields(itemUi.watch)
                updateUiForEditing()
            } else {
                viewLifecycleOwner.lifecycleScope.launch {
                    val watch = viewModel.getWatchById(watchId)
                    if (watch != null) {
                        editingWatch = watch
                        populateFields(watch)
                        updateUiForEditing()
                    }
                }
            }
        }

        binding.tvSheetTitle.text = if (editingWatch == null) {
            getString(R.string.dialog_create_watch_title)
        } else {
            getString(R.string.dialog_edit_watch_title)
        }

        setupButtons()
    }

    private fun updateUiForEditing() {
        binding.tvSheetTitle.text = getString(R.string.dialog_edit_watch_title)
        binding.btnDuplicate.visibility = View.VISIBLE
    }

    private fun setupDropdowns() {
        val currencies = arrayOf("UAH", "USD", "EUR")
        binding.actvCurrency.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, currencies)
        )

        val ownerTypes = arrayOf(
            getString(R.string.owner_type_all),
            getString(R.string.owner_type_private),
            getString(R.string.owner_type_business)
        )
        binding.actvOwnerType.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, ownerTypes)
        )

        val states = arrayOf(
            getString(R.string.state_all),
            getString(R.string.state_new),
            getString(R.string.state_used)
        )
        binding.actvState.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, states)
        )

        val snoozes = arrayOf(
            getString(R.string.snooze_none),
            getString(R.string.snooze_1h),
            getString(R.string.snooze_4h),
            getString(R.string.snooze_1d)
        )
        binding.actvSnooze.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, snoozes)
        )
    }

    private fun setupPreviewRecycler() {
        binding.recyclerPreview.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerPreview.adapter = previewAdapter
    }

    private fun populateFields(watch: Watch) {
        binding.etQuery.setText(watch.query)
        watch.priceFrom?.let { binding.etPriceFrom.setText(it.toString()) }
        watch.priceTo?.let { binding.etPriceTo.setText(it.toString()) }
        binding.actvCurrency.setText(watch.priceCurrency, false)
        binding.cbIncludeNoPrice.isChecked = watch.includeNoPrice
        binding.etStopWords.setText(watch.stopWords)
        binding.etMustWords.setText(watch.mustWords)

        when (watch.ownerType) {
            "private" -> binding.actvOwnerType.setText(getString(R.string.owner_type_private), false)
            "business" -> binding.actvOwnerType.setText(getString(R.string.owner_type_business), false)
            else -> binding.actvOwnerType.setText(getString(R.string.owner_type_all), false)
        }

        when (watch.state) {
            "new" -> binding.actvState.setText(getString(R.string.state_new), false)
            "used" -> binding.actvState.setText(getString(R.string.state_used), false)
            else -> binding.actvState.setText(getString(R.string.state_all), false)
        }

        watch.intervalSecOverride?.let { binding.etInterval.setText(it.toString()) }
    }

    private fun setupButtons() {
        // C9: URL parsing button
        binding.btnImportUrl.setOnClickListener {
            val urlStr = binding.etUrlImport.text?.toString()?.trim() ?: ""
            if (urlStr.isNotEmpty()) {
                parseOlxUrl(urlStr)
            }
        }

        // C10: Check now button
        binding.btnCheckNow.setOnClickListener {
            val watch = buildWatchFromFields() ?: return@setOnClickListener
            binding.layoutPreviewContainer.visibility = View.VISIBLE
            binding.progressPreview.visibility = View.VISIBLE
            binding.tvPreviewEmpty.visibility = View.GONE
            previewAdapter.submitList(emptyList())

            lifecycleScope.launch {
                val result = viewModel.checkListings(watch)
                binding.progressPreview.visibility = View.GONE
                result.onSuccess { listings ->
                    if (listings.isEmpty()) {
                        binding.tvPreviewEmpty.visibility = View.VISIBLE
                    } else {
                        previewAdapter.submitList(listings)
                    }
                }.onFailure { err ->
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.checking_error, err.localizedMessage ?: err.message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        // C8: Duplicate button inside bottom sheet
        binding.btnDuplicate.setOnClickListener {
            val watch = editingWatch ?: buildWatchFromFields()
            if (watch != null) {
                viewModel.duplicateWatch(watch)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.watch_duplicated_toast, watch.query),
                    Toast.LENGTH_SHORT
                ).show()
                dismiss()
            }
        }

        binding.btnSave.setOnClickListener {
            val watch = buildWatchFromFields() ?: return@setOnClickListener
            viewModel.saveWatch(watch)
            val msg = if (editingWatch == null) {
                getString(R.string.watch_created_toast, watch.query)
            } else {
                getString(R.string.watch_updated_toast, watch.query)
            }
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            dismiss()
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }
    }

    /**
     * C9: Reliable query-parameters parsing from olx.ua URL.
     */
    private fun parseOlxUrl(urlString: String) {
        try {
            val uri = Uri.parse(urlString)
            var recognized = false

            // Query parameter 'q' or 'search[query]'
            val queryParam = uri.getQueryParameter("q")
                ?: uri.getQueryParameter("search[query]")
                ?: uri.getQueryParameter("query")
            if (!queryParam.isNullOrBlank()) {
                binding.etQuery.setText(queryParam)
                recognized = true
            }

            // Price from
            val priceFromParam = uri.getQueryParameter("search[filter_float_price:from]")
                ?: uri.getQueryParameter("filter_float_price:from")
            if (!priceFromParam.isNullOrBlank()) {
                priceFromParam.toIntOrNull()?.let {
                    binding.etPriceFrom.setText(it.toString())
                    recognized = true
                }
            }

            // Price to
            val priceToParam = uri.getQueryParameter("search[filter_float_price:to]")
                ?: uri.getQueryParameter("filter_float_price:to")
            if (!priceToParam.isNullOrBlank()) {
                priceToParam.toIntOrNull()?.let {
                    binding.etPriceTo.setText(it.toString())
                    recognized = true
                }
            }

            // Currency
            val currencyParam = uri.getQueryParameter("currency")
            if (!currencyParam.isNullOrBlank() && currencyParam in listOf("UAH", "USD", "EUR")) {
                binding.actvCurrency.setText(currencyParam, false)
                recognized = true
            }

            if (recognized) {
                Toast.makeText(requireContext(), R.string.url_imported_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), R.string.url_import_failed, Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.url_import_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildWatchFromFields(): Watch? {
        val query = binding.etQuery.text?.toString()?.trim() ?: ""
        if (query.isEmpty()) {
            binding.etQuery.error = getString(R.string.field_query_error_empty)
            return null
        }
        val priceFrom = binding.etPriceFrom.text?.toString()?.trim()?.toIntOrNull()
        val priceTo = binding.etPriceTo.text?.toString()?.trim()?.toIntOrNull()
        val currency = binding.actvCurrency.text?.toString()?.trim() ?: "UAH"
        val includeNoPrice = binding.cbIncludeNoPrice.isChecked
        val stopWords = binding.etStopWords.text?.toString()?.trim() ?: ""
        val mustWords = binding.etMustWords.text?.toString()?.trim() ?: ""

        val ownerTypeText = binding.actvOwnerType.text?.toString() ?: ""
        val ownerType = when (ownerTypeText) {
            getString(R.string.owner_type_private) -> "private"
            getString(R.string.owner_type_business) -> "business"
            else -> null
        }

        val stateText = binding.actvState.text?.toString() ?: ""
        val state = when (stateText) {
            getString(R.string.state_new) -> "new"
            getString(R.string.state_used) -> "used"
            else -> null
        }

        val interval = binding.etInterval.text?.toString()?.trim()?.toIntOrNull()

        val snoozeText = binding.actvSnooze.text?.toString() ?: ""
        val now = System.currentTimeMillis()
        val snoozedUntil = when (snoozeText) {
            getString(R.string.snooze_1h) -> now + 3600_000L
            getString(R.string.snooze_4h) -> now + 4 * 3600_000L
            getString(R.string.snooze_1d) -> now + 24 * 3600_000L
            else -> null
        }

        val queryChanged = editingWatch != null && editingWatch?.query != query
        val isInitialized = if (queryChanged) false else (editingWatch?.initialized ?: false)

        return Watch(
            id = editingWatch?.id ?: 0L,
            title = query,
            query = query,
            priceFrom = priceFrom,
            priceTo = priceTo,
            cityId = editingWatch?.cityId,
            cityName = editingWatch?.cityName,
            categoryId = editingWatch?.categoryId,
            ownerType = ownerType,
            state = state,
            stopWords = stopWords,
            mustWords = mustWords,
            mustWordsMode = editingWatch?.mustWordsMode ?: "any",
            publicationAge = editingWatch?.publicationAge ?: "any",
            notifyMode = editingWatch?.notifyMode ?: "new_pushup",
            isActive = editingWatch?.isActive ?: true,
            createdAt = editingWatch?.createdAt ?: now,
            initialized = isInitialized,
            lastSuccessAt = editingWatch?.lastSuccessAt,
            lastError = editingWatch?.lastError,
            lastHttpCode = editingWatch?.lastHttpCode,
            includeNoPrice = includeNoPrice,
            priceCurrency = currency,
            snoozedUntil = snoozedUntil,
            intervalSecOverride = interval
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
