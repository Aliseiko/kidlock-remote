package uk.telegramgames.kidlock

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppSelectorAdapter(
    private var apps: List<AppInfo>,
    private val onToggle: (AppInfo, Boolean) -> Unit
) : RecyclerView.Adapter<AppSelectorAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAppIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        val tvAppName: TextView = view.findViewById(R.id.tvAppName)
        val tvPackageName: TextView = view.findViewById(R.id.tvPackageName)
        val cbBlocked: CheckBox = view.findViewById(R.id.cbBlocked)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_selector, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.tvAppName.text = app.appName
        holder.tvPackageName.text = app.packageName
        holder.ivAppIcon.setImageDrawable(app.icon)
        holder.cbBlocked.isChecked = app.isSelected
        holder.cbBlocked.setOnCheckedChangeListener { _, isChecked ->
            app.isSelected = isChecked
            onToggle(app, isChecked)
        }
    }

    override fun getItemCount(): Int = apps.size

    fun updateApps(newApps: List<AppInfo>) {
        apps = newApps
        notifyDataSetChanged()
    }

    fun getSelectedPackages(): List<String> {
        return apps.filter { it.isSelected }.map { it.packageName }
    }
}
