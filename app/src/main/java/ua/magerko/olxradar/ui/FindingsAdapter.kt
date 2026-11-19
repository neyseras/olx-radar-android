package ua.magerko.olxradar.ui

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import ua.magerko.olxradar.R
import ua.magerko.olxradar.data.model.Finding
import java.text.NumberFormat
import java.util.Locale

class FindingsAdapter(
    private val onItemClick: (Finding) -> Unit,
    private val onDeleteClick: (Finding) -> Unit,
    private val onAiDetailsClick: ((Finding) -> Unit)? = null,
    private val onOpenOlxClick: ((Finding) -> Unit)? = null
) : ListAdapter<Finding, FindingsAdapter.ViewHolder>(FindingDiffCallback) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivPhoto: ImageView = view.findViewById(R.id.iv_finding_photo)
        val tvBadge: TextView = view.findViewById(R.id.tv_badge)
        val tvAiBadge: TextView = view.findViewById(R.id.tv_ai_badge)
        val tvCity: TextView = view.findViewById(R.id.tv_city)
        val tvTitle: TextView = view.findViewById(R.id.tv_title)
        val tvPrice: TextView = view.findViewById(R.id.tv_price)
        val layoutAiInfo: View = view.findViewById(R.id.layout_ai_info)
        val tvAiProduct: TextView = view.findViewById(R.id.tv_ai_product)
        val btnAiDetails: TextView = view.findViewById(R.id.btn_ai_details)
        val tvTime: TextView = view.findViewById(R.id.tv_time)
        val btnOpen: MaterialButton = view.findViewById(R.id.btn_open_olx)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_delete_finding)
        val tvMarketSpread: TextView? = view.findViewById(R.id.tv_finding_market_spread)
        val tvSellerType: TextView? = view.findViewById(R.id.tv_finding_seller_type)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_finding, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context

        holder.tvTitle.text = item.title
        holder.tvCity.text = item.cityName

        // C6: Format price with thousands separator and currency
        holder.tvPrice.text = if (item.price != null) {
            val formatter = NumberFormat.getInstance(Locale("uk", "UA"))
            "${formatter.format(item.price)} ${item.currency ?: "UAH"}"
        } else {
            context.getString(R.string.price_negotiable)
        }

        // Market spread estimate
        if (item.price != null && item.price > 0) {
            val formatter = NumberFormat.getInstance(Locale("uk", "UA"))
            val estMarket = (item.price * 1.35).toInt()
            holder.tvMarketSpread?.visibility = View.VISIBLE
            holder.tvMarketSpread?.text = "Рынок: ~${formatter.format(estMarket)} ${item.currency ?: "₴"}"
        } else {
            holder.tvMarketSpread?.visibility = View.GONE
        }

        // C6: Relative time span ("5 мин назад")
        val relativeTime = DateUtils.getRelativeTimeSpanString(
            item.foundAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        )
        holder.tvTime.text = relativeTime

        // C5: Pill badge
        if (item.reason == "pushup") {
            holder.tvBadge.text = context.getString(R.string.notif_badge_pushup)
            holder.tvBadge.backgroundTintList = ContextCompat.getColorStateList(context, R.color.badge_pushup)
        } else {
            holder.tvBadge.text = context.getString(R.string.notif_badge_new)
            holder.tvBadge.backgroundTintList = ContextCompat.getColorStateList(context, R.color.badge_new)
        }

        // AI Status Badge
        if (!item.aiStatus.isNullOrEmpty() && item.aiStatus != "skipped") {
            holder.tvAiBadge.visibility = View.VISIBLE
            val (badgeText, badgeBg) = when (item.aiStatus) {
                ua.magerko.olxradar.ai.AiStatus.MATCH ->
                    "${item.aiScore ?: 0}% ${context.getString(R.string.ai_status_match)}" to R.color.ai_match
                ua.magerko.olxradar.ai.AiStatus.NEEDS_REVIEW ->
                    "${item.aiScore ?: 0}% ${context.getString(R.string.ai_status_needs_review)}" to R.color.ai_needs_review
                ua.magerko.olxradar.ai.AiStatus.REJECTED ->
                    context.getString(R.string.ai_status_rejected) to R.color.ai_rejected
                ua.magerko.olxradar.ai.AiStatus.ANALYZING ->
                    context.getString(R.string.ai_status_analyzing) to R.color.ai_analyzing
                else ->
                    context.getString(R.string.ai_status_error) to R.color.ai_error
            }
            holder.tvAiBadge.text = badgeText
            holder.tvAiBadge.backgroundTintList = ContextCompat.getColorStateList(context, badgeBg)
        } else {
            holder.tvAiBadge.visibility = View.GONE
        }

        // AI Product, Model, Reject Reason & Photo Mismatch Info
        val hasAiInfo = !item.aiProduct.isNullOrBlank() ||
                !item.aiModel.isNullOrBlank() ||
                !item.aiRejectReason.isNullOrBlank() ||
                item.aiPhotoMatch == false ||
                !item.aiDetailsJson.isNullOrBlank()

        if (hasAiInfo) {
            holder.layoutAiInfo.visibility = View.VISIBLE

            val aiInfoText = when {
                item.aiPhotoMatch == false -> {
                    val detail = item.aiRejectReason ?: item.aiProduct ?: ""
                    if (detail.isNotBlank()) "⚠ Фото не соответствует! $detail" else "⚠ Фото не соответствует заголовку!"
                }
                item.aiStatus == ua.magerko.olxradar.ai.AiStatus.REJECTED && !item.aiRejectReason.isNullOrBlank() -> {
                    "✗ ${item.aiRejectReason}"
                }
                !item.aiReasons.isNullOrBlank() -> {
                    item.aiReasons
                }
                !item.aiProduct.isNullOrBlank() -> {
                    val prodStr = item.aiProduct ?: ""
                    val modelStr = item.aiModel?.let { " ($it)" } ?: ""
                    "• $prodStr$modelStr"
                }
                !item.aiRejectReason.isNullOrBlank() -> {
                    "✗ ${item.aiRejectReason}"
                }
                else -> {
                    "• AI Анализ подтвержден"
                }
            }

            holder.tvAiProduct.text = aiInfoText
            holder.btnAiDetails.setOnClickListener {
                onAiDetailsClick?.invoke(item)
            }
        } else {
            holder.layoutAiInfo.visibility = View.GONE
        }

        // C7: Unread indicator styling (dim read items slightly)
        holder.itemView.alpha = if (item.isRead) 0.72f else 1.0f

        if (!item.photoUrl.isNullOrEmpty()) {
            Glide.with(context)
                .load(item.photoUrl)
                .placeholder(R.drawable.ic_radar)
                .error(R.drawable.ic_radar)
                .centerCrop()
                .into(holder.ivPhoto)
        } else {
            holder.ivPhoto.setImageResource(R.drawable.ic_radar)
        }

        // Tap on whole card opens info details
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }

        // Details button on card also opens info details
        holder.btnOpen.setOnClickListener {
            onItemClick(item)
        }

        holder.btnDelete.setOnClickListener {
            onDeleteClick(item)
        }
    }

    object FindingDiffCallback : DiffUtil.ItemCallback<Finding>() {
        override fun areItemsTheSame(oldItem: Finding, newItem: Finding): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Finding, newItem: Finding): Boolean {
            return oldItem == newItem
        }
    }
}
