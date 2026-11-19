package ua.magerko.olxradar.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import ua.magerko.olxradar.R
import ua.magerko.olxradar.ai.AiAnalysisResult
import ua.magerko.olxradar.ai.AiStatus
import ua.magerko.olxradar.data.model.Finding
import ua.magerko.olxradar.databinding.BottomSheetAiDetailsBinding

class AiDetailsBottomSheetFragment : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_AI_SCORE = "arg_ai_score"
        private const val ARG_AI_STATUS = "arg_ai_status"
        private const val ARG_AI_PRODUCT = "arg_ai_product"
        private const val ARG_AI_MODEL = "arg_ai_model"
        private const val ARG_AI_PHOTO_MATCH = "arg_ai_photo_match"
        private const val ARG_AI_REASONS = "arg_ai_reasons"
        private const val ARG_AI_REJECT = "arg_ai_reject"
        private const val ARG_AI_DETAILS_JSON = "arg_ai_details_json"

        fun newInstance(finding: Finding): AiDetailsBottomSheetFragment {
            return AiDetailsBottomSheetFragment().apply {
                arguments = Bundle().apply {
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

    private var _binding: BottomSheetAiDetailsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAiDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = arguments ?: return
        val score = if (args.containsKey(ARG_AI_SCORE)) args.getInt(ARG_AI_SCORE) else null
        val status = args.getString(ARG_AI_STATUS) ?: AiStatus.ANALYZING
        val product = args.getString(ARG_AI_PRODUCT)
        val model = args.getString(ARG_AI_MODEL)
        val photoMatch = if (args.containsKey(ARG_AI_PHOTO_MATCH)) args.getBoolean(ARG_AI_PHOTO_MATCH) else null
        val reasons = args.getString(ARG_AI_REASONS)
        val rejectReason = args.getString(ARG_AI_REJECT)
        val detailsJson = args.getString(ARG_AI_DETAILS_JSON)

        val parsedResult = detailsJson?.let { AiAnalysisResult.fromJson(it) }

        // Status badge
        val (statusText, statusBgColor) = when (status) {
            AiStatus.MATCH -> getString(R.string.ai_status_match) to R.color.ai_match
            AiStatus.NEEDS_REVIEW -> getString(R.string.ai_status_needs_review) to R.color.ai_needs_review
            AiStatus.REJECTED -> getString(R.string.ai_status_rejected) to R.color.ai_rejected
            AiStatus.ERROR -> getString(R.string.ai_status_error) to R.color.ai_error
            else -> getString(R.string.ai_status_analyzing) to R.color.ai_analyzing
        }
        binding.tvSheetAiStatus.text = statusText
        binding.tvSheetAiStatus.backgroundTintList = ContextCompat.getColorStateList(requireContext(), statusBgColor)

        // Score
        if (score != null) {
            binding.tvSheetAiScore.text = getString(R.string.ai_score_label, score)
        } else {
            binding.tvSheetAiScore.text = "AI Score: —"
        }

        // Photo match
        if (photoMatch != null) {
            if (photoMatch) {
                binding.tvSheetPhotoMatch.text = "✓ " + getString(R.string.ai_photo_match_yes)
                binding.tvSheetPhotoMatch.setTextColor(ContextCompat.getColor(requireContext(), R.color.ai_match))
            } else {
                binding.tvSheetPhotoMatch.text = "✗ " + getString(R.string.ai_photo_match_no)
                binding.tvSheetPhotoMatch.setTextColor(ContextCompat.getColor(requireContext(), R.color.ai_rejected))
            }
        } else {
            binding.tvSheetPhotoMatch.visibility = View.GONE
        }

        // Product & Model
        val prodText = product ?: parsedResult?.detectedProduct ?: "Не определен"
        binding.tvSheetDetectedProduct.text = "${getString(R.string.ai_detected_product_label)} $prodText"

        val modelText = model ?: parsedResult?.detectedModel ?: "Не определена"
        binding.tvSheetDetectedModel.text = "${getString(R.string.ai_detected_model_label)} $modelText"

        // Condition & Completeness
        val condition = parsedResult?.condition ?: "—"
        val completeness = parsedResult?.completeness ?: "—"
        binding.tvSheetConditionCompleteness.text =
            "${getString(R.string.ai_condition_label)} $condition • ${getString(R.string.ai_completeness_label)} $completeness"

        // Visible defects
        val defects = parsedResult?.visibleDefects ?: emptyList()
        if (defects.isNotEmpty()) {
            binding.tvSheetDefects.visibility = View.VISIBLE
            binding.tvSheetDefects.text = "${getString(R.string.ai_defects_label)} ${defects.joinToString(", ")}"
        } else {
            binding.tvSheetDefects.visibility = View.GONE
        }

        // Reasons
        val reasonsText = reasons ?: parsedResult?.reasons?.joinToString("\n• ", prefix = "• ")
        if (!reasonsText.isNullOrBlank()) {
            binding.tvSheetReasons.text = reasonsText
        } else {
            binding.tvSheetReasons.text = "—"
        }

        // Reject reason
        val reject = rejectReason ?: parsedResult?.rejectReason
        if (!reject.isNullOrBlank()) {
            binding.tvSheetRejectReason.visibility = View.VISIBLE
            binding.tvSheetRejectReason.text = "${getString(R.string.ai_reject_reason_label)} $reject"
        } else {
            binding.tvSheetRejectReason.visibility = View.GONE
        }

        // Raw JSON toggle
        if (!detailsJson.isNullOrBlank()) {
            binding.tvSheetRawJson.text = detailsJson
            binding.btnToggleRawJson.setOnClickListener {
                val isVisible = binding.tvSheetRawJson.visibility == View.VISIBLE
                binding.tvSheetRawJson.visibility = if (isVisible) View.GONE else View.VISIBLE
            }
        } else {
            binding.btnToggleRawJson.visibility = View.GONE
            binding.tvSheetRawJson.visibility = View.GONE
        }

        binding.btnCloseSheet.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
