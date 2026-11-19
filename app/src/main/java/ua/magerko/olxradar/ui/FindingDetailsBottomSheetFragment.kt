package ua.magerko.olxradar.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import ua.magerko.olxradar.R
import ua.magerko.olxradar.ai.AiAnalysisResult
import ua.magerko.olxradar.ai.AiStatus
import ua.magerko.olxradar.data.model.Finding
import ua.magerko.olxradar.databinding.BottomSheetFindingDetailsBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FindingDetailsBottomSheetFragment : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_ID = "arg_id"
        private const val ARG_WATCH_ID = "arg_watch_id"
        private const val ARG_LISTING_ID = "arg_listing_id"
        private const val ARG_TITLE = "arg_title"
        private const val ARG_DESCRIPTION = "arg_description"
        private const val ARG_PRICE = "arg_price"
        private const val ARG_CURRENCY = "arg_currency"
        private const val ARG_CITY = "arg_city"
        private const val ARG_URL = "arg_url"
        private const val ARG_PHOTO_URL = "arg_photo_url"
        private const val ARG_REASON = "arg_reason"
        private const val ARG_CREATED_AT = "arg_created_at"
        private const val ARG_FOUND_AT = "arg_found_at"
        private const val ARG_IS_READ = "arg_is_read"

        private const val ARG_AI_SCORE = "arg_ai_score"
        private const val ARG_AI_STATUS = "arg_ai_status"
        private const val ARG_AI_PRODUCT = "arg_ai_product"
        private const val ARG_AI_MODEL = "arg_ai_model"
        private const val ARG_AI_PHOTO_MATCH = "arg_ai_photo_match"
        private const val ARG_AI_REASONS = "arg_ai_reasons"
        private const val ARG_AI_REJECT = "arg_ai_reject"
        private const val ARG_AI_DETAILS_JSON = "arg_ai_details_json"

        fun newInstance(finding: Finding): FindingDetailsBottomSheetFragment {
            return FindingDetailsBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_ID, finding.id)
                    putLong(ARG_WATCH_ID, finding.watchId)
                    putLong(ARG_LISTING_ID, finding.listingId)
                    putString(ARG_TITLE, finding.title)
                    putString(ARG_DESCRIPTION, finding.description)
                    finding.price?.let { putInt(ARG_PRICE, it) }
                    putString(ARG_CURRENCY, finding.currency)
                    putString(ARG_CITY, finding.cityName)
                    putString(ARG_URL, finding.url)
                    putString(ARG_PHOTO_URL, finding.photoUrl)
                    putString(ARG_REASON, finding.reason)
                    putLong(ARG_CREATED_AT, finding.createdAt)
                    putLong(ARG_FOUND_AT, finding.foundAt)
                    putBoolean(ARG_IS_READ, finding.isRead)

                    finding.aiScore?.let { putInt(ARG_AI_SCORE, it) }
                    finding.aiStatus?.let { putString(ARG_AI_STATUS, it) }
                    finding.aiProduct?.let { putString(ARG_AI_PRODUCT, it) }
                    finding.aiModel?.let { putString(ARG_AI_MODEL, it) }
                    finding.aiPhotoMatch?.let { putBoolean(ARG_AI_PHOTO_MATCH, it) }
                    finding.aiReasons?.let { putString(ARG_AI_REASONS, it) }
                    finding.aiRejectReason?.let { putString(ARG_AI_REJECT, it) }
                    finding.aiDetailsJson?.let { putString(ARG_AI_DETAILS_JSON, it) }
                }
            }
        }
    }

    private var _binding: BottomSheetFindingDetailsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetFindingDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = arguments ?: return

        val id = args.getLong(ARG_ID)
        val watchId = args.getLong(ARG_WATCH_ID)
        val listingId = args.getLong(ARG_LISTING_ID)
        val title = args.getString(ARG_TITLE) ?: ""
        val description = args.getString(ARG_DESCRIPTION) ?: ""
        val price = if (args.containsKey(ARG_PRICE)) args.getInt(ARG_PRICE) else null
        val currency = args.getString(ARG_CURRENCY) ?: "UAH"
        val city = args.getString(ARG_CITY) ?: ""
        val url = args.getString(ARG_URL) ?: ""
        val photoUrl = args.getString(ARG_PHOTO_URL)
        val reason = args.getString(ARG_REASON) ?: "new"
        val createdAt = args.getLong(ARG_CREATED_AT)
        val foundAt = args.getLong(ARG_FOUND_AT)

        val score = if (args.containsKey(ARG_AI_SCORE)) args.getInt(ARG_AI_SCORE) else null
        val aiStatus = args.getString(ARG_AI_STATUS)
        val aiProduct = args.getString(ARG_AI_PRODUCT)
        val aiModel = args.getString(ARG_AI_MODEL)
        val photoMatch = if (args.containsKey(ARG_AI_PHOTO_MATCH)) args.getBoolean(ARG_AI_PHOTO_MATCH) else null
        val reasons = args.getString(ARG_AI_REASONS)
        val rejectReason = args.getString(ARG_AI_REJECT)
        val detailsJson = args.getString(ARG_AI_DETAILS_JSON)

        // Close button
        binding.btnSheetClose.setOnClickListener {
            dismiss()
        }

        // Photo
        if (!photoUrl.isNullOrEmpty()) {
            binding.cardSheetPhoto.visibility = View.VISIBLE
            Glide.with(this)
                .load(photoUrl)
                .placeholder(R.drawable.ic_radar)
                .error(R.drawable.ic_radar)
                .centerCrop()
                .into(binding.ivSheetPhoto)
        } else {
            binding.cardSheetPhoto.visibility = View.GONE
        }

        // Badges: Reason
        if (reason == "pushup") {
            binding.tvSheetReasonBadge.text = getString(R.string.notif_badge_pushup)
            binding.tvSheetReasonBadge.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.badge_pushup)
        } else {
            binding.tvSheetReasonBadge.text = getString(R.string.notif_badge_new)
            binding.tvSheetReasonBadge.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.badge_new)
        }

        // Badges: AI Status
        if (!aiStatus.isNullOrEmpty() && aiStatus != AiStatus.SKIPPED) {
            binding.tvSheetAiStatusBadge.visibility = View.VISIBLE
            val (statusText, statusBg) = when (aiStatus) {
                AiStatus.MATCH ->
                    "${score ?: 0}% ${getString(R.string.ai_status_match)}" to R.color.ai_match
                AiStatus.NEEDS_REVIEW ->
                    "${score ?: 0}% ${getString(R.string.ai_status_needs_review)}" to R.color.ai_needs_review
                AiStatus.REJECTED ->
                    getString(R.string.ai_status_rejected) to R.color.ai_rejected
                AiStatus.ANALYZING ->
                    getString(R.string.ai_status_analyzing) to R.color.ai_analyzing
                else ->
                    getString(R.string.ai_status_error) to R.color.ai_error
            }
            binding.tvSheetAiStatusBadge.text = statusText
            binding.tvSheetAiStatusBadge.backgroundTintList = ContextCompat.getColorStateList(requireContext(), statusBg)
        } else {
            binding.tvSheetAiStatusBadge.visibility = View.GONE
        }

        // Title
        binding.tvSheetTitle.text = title

        // Price
        val priceStr = if (price != null) {
            val formatter = NumberFormat.getInstance(Locale("uk", "UA"))
            "${formatter.format(price)} $currency"
        } else {
            getString(R.string.price_negotiable)
        }
        binding.tvSheetPrice.text = priceStr

        // Location & Time
        if (city.isNotBlank()) {
            binding.tvSheetLocation.text = "📍 $city"
            binding.tvSheetLocation.visibility = View.VISIBLE
        } else {
            binding.tvSheetLocation.visibility = View.GONE
        }

        if (foundAt > 0) {
            val relativeTime = DateUtils.getRelativeTimeSpanString(
                foundAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val exactTime = timeFormat.format(Date(foundAt))
            binding.tvSheetTime.text = "🕒 $relativeTime ($exactTime)"
            binding.tvSheetTime.visibility = View.VISIBLE
        } else {
            binding.tvSheetTime.visibility = View.GONE
        }

        // Description
        if (description.isNotBlank()) {
            val formattedDesc = if (description.contains("<") && description.contains(">")) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Html.fromHtml(description, Html.FROM_HTML_MODE_COMPACT)
                } else {
                    @Suppress("DEPRECATION")
                    Html.fromHtml(description)
                }
            } else {
                description
            }
            binding.tvSheetDescription.text = formattedDesc
            binding.tvSheetDescription.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
        } else {
            binding.tvSheetDescription.text = getString(R.string.sheet_no_description)
            binding.tvSheetDescription.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
        }

        // AI Analysis Section
        val hasAiDetails = !aiStatus.isNullOrEmpty() && aiStatus != AiStatus.SKIPPED
        if (hasAiDetails) {
            binding.layoutAiSection.visibility = View.VISIBLE
            val parsedResult = detailsJson?.let { AiAnalysisResult.fromJson(it) }

            // Score
            if (score != null) {
                binding.tvSheetAiScore.text = getString(R.string.ai_score_label, score)
            } else {
                binding.tvSheetAiScore.text = "AI Score: —"
            }

            // Photo match
            if (photoMatch != null) {
                binding.tvSheetAiPhotoMatch.visibility = View.VISIBLE
                if (photoMatch) {
                    binding.tvSheetAiPhotoMatch.text = "✓ " + getString(R.string.ai_photo_match_yes)
                    binding.tvSheetAiPhotoMatch.setTextColor(ContextCompat.getColor(requireContext(), R.color.ai_match))
                } else {
                    binding.tvSheetAiPhotoMatch.text = "✗ " + getString(R.string.ai_photo_match_no)
                    binding.tvSheetAiPhotoMatch.setTextColor(ContextCompat.getColor(requireContext(), R.color.ai_rejected))
                }
            } else {
                binding.tvSheetAiPhotoMatch.visibility = View.GONE
            }

            // Product & Model
            val prodText = aiProduct ?: parsedResult?.detectedProduct
            if (!prodText.isNullOrBlank()) {
                binding.tvSheetAiProduct.visibility = View.VISIBLE
                binding.tvSheetAiProduct.text = "${getString(R.string.ai_detected_product_label)} $prodText"
            } else {
                binding.tvSheetAiProduct.visibility = View.GONE
            }

            val modelText = aiModel ?: parsedResult?.detectedModel
            if (!modelText.isNullOrBlank()) {
                binding.tvSheetAiModel.visibility = View.VISIBLE
                binding.tvSheetAiModel.text = "${getString(R.string.ai_detected_model_label)} $modelText"
            } else {
                binding.tvSheetAiModel.visibility = View.GONE
            }

            // Condition & Completeness
            val condition = parsedResult?.condition
            val completeness = parsedResult?.completeness
            if (condition != null || completeness != null) {
                binding.tvSheetAiConditionCompleteness.visibility = View.VISIBLE
                binding.tvSheetAiConditionCompleteness.text =
                    "${getString(R.string.ai_condition_label)} ${condition ?: "—"} • ${getString(R.string.ai_completeness_label)} ${completeness ?: "—"}"
            } else {
                binding.tvSheetAiConditionCompleteness.visibility = View.GONE
            }

            // Visible defects
            val defects = parsedResult?.visibleDefects ?: emptyList()
            if (defects.isNotEmpty()) {
                binding.tvSheetAiDefects.visibility = View.VISIBLE
                binding.tvSheetAiDefects.text = "${getString(R.string.ai_defects_label)} ${defects.joinToString(", ")}"
            } else {
                binding.tvSheetAiDefects.visibility = View.GONE
            }

            // Reject reason
            val reject = rejectReason ?: parsedResult?.rejectReason
            if (!reject.isNullOrBlank()) {
                binding.tvSheetAiRejectReason.visibility = View.VISIBLE
                binding.tvSheetAiRejectReason.text = "${getString(R.string.ai_reject_reason_label)} $reject"
            } else {
                binding.tvSheetAiRejectReason.visibility = View.GONE
            }

            // Reasons
            val reasonsText = reasons ?: parsedResult?.reasons?.joinToString("\n• ", prefix = "• ")
            if (!reasonsText.isNullOrBlank()) {
                binding.tvSheetAiReasons.visibility = View.VISIBLE
                binding.tvSheetAiReasons.text = reasonsText
            } else {
                binding.tvSheetAiReasons.visibility = View.GONE
            }

            // Raw JSON toggle
            if (!detailsJson.isNullOrBlank()) {
                binding.btnToggleRawJson.visibility = View.VISIBLE
                binding.tvSheetRawJson.text = detailsJson
                binding.btnToggleRawJson.setOnClickListener {
                    val isVisible = binding.tvSheetRawJson.visibility == View.VISIBLE
                    binding.tvSheetRawJson.visibility = if (isVisible) View.GONE else View.VISIBLE
                }
            } else {
                binding.btnToggleRawJson.visibility = View.GONE
                binding.tvSheetRawJson.visibility = View.GONE
            }
        } else {
            binding.layoutAiSection.visibility = View.GONE
        }

        // Action: Open in OLX
        binding.btnSheetOpenOlx.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (_: Exception) {
            }
        }

        // Action: Share
        binding.btnSheetShare.setOnClickListener {
            try {
                val shareText = "$title\n$priceStr • $city\n$url"
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    type = "text/plain"
                }
                startActivity(Intent.createChooser(sendIntent, title))
            } catch (_: Exception) {
            }
        }

        // Action: Delete
        binding.btnSheetDelete.setOnClickListener {
            val finding = Finding(
                id = id,
                watchId = watchId,
                listingId = listingId,
                title = title,
                description = description,
                price = price,
                currency = currency,
                cityName = city,
                url = url,
                photoUrl = photoUrl,
                reason = reason,
                createdAt = createdAt,
                foundAt = foundAt,
                isRead = true,
                aiScore = score,
                aiStatus = aiStatus,
                aiProduct = aiProduct,
                aiModel = aiModel,
                aiPhotoMatch = photoMatch,
                aiReasons = reasons,
                aiRejectReason = rejectReason,
                aiDetailsJson = detailsJson
            )
            val findingsFragment = parentFragment as? FindingsFragment
                ?: (activity as? MainActivity)?.supportFragmentManager?.findFragmentById(R.id.fragment_container) as? FindingsFragment
            if (findingsFragment != null) {
                findingsFragment.deleteWithUndo(finding)
            } else {
                try {
                    val vm = androidx.lifecycle.ViewModelProvider(requireActivity(), ViewModelFactory(requireContext()))[FindingsViewModel::class.java]
                    vm.deleteFinding(finding)
                } catch (_: Exception) {}
            }
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? com.google.android.material.bottomsheet.BottomSheetDialog ?: return
        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet).apply {
            state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
