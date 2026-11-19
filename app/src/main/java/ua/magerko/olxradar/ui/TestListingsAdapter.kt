package ua.magerko.olxradar.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import ua.magerko.olxradar.R
import ua.magerko.olxradar.data.model.Listing
import java.text.NumberFormat
import java.util.Locale

class TestListingsAdapter : ListAdapter<Listing, TestListingsAdapter.ViewHolder>(ListingDiffCallback) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tv_preview_title)
        val tvPrice: TextView = view.findViewById(R.id.tv_preview_price)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_preview_listing, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context
        holder.tvTitle.text = item.title

        holder.tvPrice.text = if (item.price != null) {
            val formatter = NumberFormat.getInstance(Locale("uk", "UA"))
            "${formatter.format(item.price)} ${item.currency ?: "UAH"} • ${item.cityName}"
        } else {
            "${context.getString(R.string.price_negotiable)} • ${item.cityName}"
        }
    }

    object ListingDiffCallback : DiffUtil.ItemCallback<Listing>() {
        override fun areItemsTheSame(oldItem: Listing, newItem: Listing): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Listing, newItem: Listing): Boolean = oldItem == newItem
    }
}
