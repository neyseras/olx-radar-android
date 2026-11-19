package ua.magerko.olxradar.ui

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import ua.magerko.olxradar.R
import ua.magerko.olxradar.data.model.Watch
import java.text.NumberFormat
import java.util.Locale

data class WatchItemUi(
    val watch: Watch,
    val findingsCount: Int = 0,
    val liveLastSuccessAt: Long? = null,
    val liveLastError: String? = null,
    val liveLastHttpCode: Int? = null
)

class WatchesAdapter(
    private val onCardClick: (Watch) -> Unit,
    private val onToggleActive: (Watch, Boolean) -> Unit,
    private val onDuplicateClick: (Watch) -> Unit,
    private val onDeleteClick: (Watch) -> Unit
) : ListAdapter<WatchItemUi, WatchesAdapter.ViewHolder>(WatchDiffCallback) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvQuery: TextView = view.findViewById(R.id.tv_watch_query)
        val switchActive: MaterialSwitch = view.findViewById(R.id.switch_watch_active)
        val btnDuplicate: ImageButton = view.findViewById(R.id.btn_duplicate_watch)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_delete_watch)
        val tvDetails: TextView = view.findViewById(R.id.tv_watch_details)
        val tvStopwords: TextView = view.findViewById(R.id.tv_watch_stopwords)
        val tvMustwords: TextView = view.findViewById(R.id.tv_watch_mustwords)
        val tvFindingsCount: TextView = view.findViewById(R.id.tv_watch_findings_count)
        val tvLastSuccess: TextView = view.findViewById(R.id.tv_watch_last_success)
        val tvLastError: TextView = view.findViewById(R.id.tv_watch_last_error)
        val tvSnooze: TextView = view.findViewById(R.id.tv_watch_snooze)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_watch, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val itemUi = getItem(position)
        val watch = itemUi.watch
        val context = holder.itemView.context
        val formatter = NumberFormat.getInstance(Locale("uk", "UA"))

        holder.tvQuery.text = watch.query

        // Temporarily clear checked listener to avoid recycled trigger
        holder.switchActive.setOnCheckedChangeListener(null)
        holder.switchActive.isChecked = watch.isActive
        holder.switchActive.setOnCheckedChangeListener { _, isChecked ->
            onToggleActive(watch, isChecked)
        }

        // Price formatting
        val curr = watch.priceCurrency
        val priceStr = when {
            watch.priceFrom != null && watch.priceTo != null ->
                context.getString(R.string.watch_price_range, formatter.format(watch.priceFrom), formatter.format(watch.priceTo), curr)
            watch.priceFrom != null ->
                context.getString(R.string.watch_price_from, formatter.format(watch.priceFrom), curr)
            watch.priceTo != null ->
                context.getString(R.string.watch_price_to, formatter.format(watch.priceTo), curr)
            else -> context.getString(R.string.watch_any_price)
        }
        val cityStr = watch.cityName ?: context.getString(R.string.watch_all_ukraine)
        holder.tvDetails.text = "$priceStr • $cityStr"

        // Stop words
        if (watch.stopWords.isNotBlank()) {
            holder.tvStopwords.visibility = View.VISIBLE
            holder.tvStopwords.text = context.getString(R.string.watch_stopwords_label, watch.stopWords)
        } else {
            holder.tvStopwords.visibility = View.GONE
        }

        // Must words
        if (watch.mustWords.isNotBlank()) {
            holder.tvMustwords.visibility = View.VISIBLE
            val modeLabel = if (watch.mustWordsMode == "all") "И" else "ИЛИ"
            holder.tvMustwords.text = "${context.getString(R.string.watch_mustwords_label, watch.mustWords)} ($modeLabel)"
        } else {
            holder.tvMustwords.visibility = View.GONE
        }

        // Findings count (C11)
        holder.tvFindingsCount.text = context.getString(R.string.watch_findings_count, itemUi.findingsCount)

        // Last success time (C11)
        val successAt = itemUi.liveLastSuccessAt ?: watch.lastSuccessAt
        if (successAt != null && successAt > 0) {
            val relativeTime = DateUtils.getRelativeTimeSpanString(
                successAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
            holder.tvLastSuccess.text = context.getString(R.string.watch_last_success, relativeTime)
        } else {
            holder.tvLastSuccess.text = context.getString(R.string.watch_never_checked)
        }

        // Last error in red (C11)
        val lastErr = itemUi.liveLastError ?: watch.lastError
        if (!lastErr.isNullOrBlank()) {
            holder.tvLastError.visibility = View.VISIBLE
            holder.tvLastError.text = context.getString(R.string.watch_last_error_label, lastErr)
        } else {
            holder.tvLastError.visibility = View.GONE
        }

        // Snooze indicator
        val now = System.currentTimeMillis()
        if (watch.snoozedUntil != null && watch.snoozedUntil > now) {
            holder.tvSnooze.visibility = View.VISIBLE
            val snoozeRemaining = DateUtils.getRelativeTimeSpanString(
                watch.snoozedUntil,
                now,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
            holder.tvSnooze.text = context.getString(R.string.watch_snoozed_until, snoozeRemaining)
        } else {
            holder.tvSnooze.visibility = View.GONE
        }

        // C8: Click card to edit
        holder.itemView.setOnClickListener {
            onCardClick(watch)
        }

        holder.btnDuplicate.setOnClickListener {
            onDuplicateClick(watch)
        }

        holder.btnDelete.setOnClickListener {
            onDeleteClick(watch)
        }
    }

    object WatchDiffCallback : DiffUtil.ItemCallback<WatchItemUi>() {
        override fun areItemsTheSame(oldItem: WatchItemUi, newItem: WatchItemUi): Boolean {
            return oldItem.watch.id == newItem.watch.id
        }

        override fun areContentsTheSame(oldItem: WatchItemUi, newItem: WatchItemUi): Boolean {
            return oldItem == newItem
        }
    }
}
