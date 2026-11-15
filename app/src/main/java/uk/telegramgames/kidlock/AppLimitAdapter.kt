package uk.telegramgames.kidlock

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Список per-app дневных лимитов: имя приложения, лимит и использовано за сегодня.
 */
class AppLimitAdapter(
    private var items: List<AdminViewModel.AppLimitInfo>,
    private val formatMinutes: (Int) -> String,
    private val onRemove: (AdminViewModel.AppLimitInfo) -> Unit
) : RecyclerView.Adapter<AppLimitAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tvAppLimitName)
        val tvUsed: TextView = itemView.findViewById(R.id.tvAppLimitUsed)
        val btnRemove: Button = itemView.findViewById(R.id.btnRemoveAppLimit)

        fun bind(info: AdminViewModel.AppLimitInfo) {
            tvName.text = itemView.context.getString(
                R.string.app_limits_row_format,
                info.appName,
                formatMinutes(info.limitMinutes)
            )
            tvUsed.text = itemView.context.getString(
                R.string.app_limits_used_format,
                formatMinutes(info.usedMinutes)
            )
            btnRemove.setOnClickListener { onRemove(info) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_limit, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<AdminViewModel.AppLimitInfo>) {
        items = newItems
        notifyDataSetChanged()
    }
}
