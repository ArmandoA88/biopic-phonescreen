package com.focusfade.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.focusfade.app.R
import com.focusfade.app.manager.WhitelistManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.switchmaterial.SwitchMaterial

class DelayedLaunchAppAdapter(
    private var apps: List<DelayedLaunchAppItem>,
    private val onAppDelayChanged: (String, Int?) -> Unit
) : RecyclerView.Adapter<DelayedLaunchAppAdapter.AppViewHolder>() {

    data class DelayedLaunchAppItem(
        val appInfo: WhitelistManager.AppInfo,
        var customDelay: Int? = null,
        var isExpanded: Boolean = false
    )

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageAppIcon: ImageView = itemView.findViewById(R.id.imageAppIcon)
        val textAppName: TextView = itemView.findViewById(R.id.textAppName)
        val textPackageName: TextView = itemView.findViewById(R.id.textPackageName)
        val textAppType: TextView = itemView.findViewById(R.id.textAppType)
        val textDelayStatus: TextView = itemView.findViewById(R.id.textDelayStatus)
        val switchAppDelay: SwitchMaterial = itemView.findViewById(R.id.switchAppDelay)
        val layoutDelayConfig: View = itemView.findViewById(R.id.layoutDelayConfig)
        val textCustomDelay: TextView = itemView.findViewById(R.id.textCustomDelay)
        val sliderCustomDelay: Slider = itemView.findViewById(R.id.sliderCustomDelay)
        val buttonRemoveDelay: MaterialButton = itemView.findViewById(R.id.buttonRemoveDelay)
        val buttonSaveDelay: MaterialButton = itemView.findViewById(R.id.buttonSaveDelay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_delayed_launch_app, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val item = apps[position]
        val appInfo = item.appInfo

        // Set app info
        holder.textAppName.text = appInfo.appName
        holder.textPackageName.text = appInfo.packageName
        holder.imageAppIcon.setImageDrawable(appInfo.icon)

        // Set app type
        holder.textAppType.text = if (appInfo.isSystemApp) "System App" else "User App"

        // Set delay status
        if (item.customDelay != null) {
            holder.textDelayStatus.text = "Custom: ${item.customDelay}s"
            holder.textDelayStatus.visibility = View.VISIBLE
            holder.switchAppDelay.isChecked = true
        } else {
            holder.textDelayStatus.visibility = View.GONE
            holder.switchAppDelay.isChecked = false
        }

        // Set expansion state
        holder.layoutDelayConfig.visibility = if (item.isExpanded) View.VISIBLE else View.GONE

        // Set up switch listener
        holder.switchAppDelay.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Expand configuration
                item.isExpanded = true
                holder.layoutDelayConfig.visibility = View.VISIBLE
                
                // Set initial delay if not set
                if (item.customDelay == null) {
                    item.customDelay = 5
                    holder.sliderCustomDelay.value = 5f
                    updateDelayText(holder, 5)
                }
            } else {
                // Remove custom delay
                item.customDelay = null
                item.isExpanded = false
                holder.layoutDelayConfig.visibility = View.GONE
                holder.textDelayStatus.visibility = View.GONE
                onAppDelayChanged(appInfo.packageName, null)
            }
        }

        // Set up slider
        if (item.customDelay != null) {
            holder.sliderCustomDelay.value = item.customDelay!!.toFloat()
            updateDelayText(holder, item.customDelay!!)
        }

        holder.sliderCustomDelay.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                updateDelayText(holder, value.toInt())
            }
        }

        // Set up buttons
        holder.buttonSaveDelay.setOnClickListener {
            val newDelay = holder.sliderCustomDelay.value.toInt()
            item.customDelay = newDelay
            holder.textDelayStatus.text = "Custom: ${newDelay}s"
            holder.textDelayStatus.visibility = View.VISIBLE
            onAppDelayChanged(appInfo.packageName, newDelay)
            
            // Collapse configuration
            item.isExpanded = false
            holder.layoutDelayConfig.visibility = View.GONE
        }

        holder.buttonRemoveDelay.setOnClickListener {
            item.customDelay = null
            item.isExpanded = false
            holder.layoutDelayConfig.visibility = View.GONE
            holder.textDelayStatus.visibility = View.GONE
            holder.switchAppDelay.isChecked = false
            onAppDelayChanged(appInfo.packageName, null)
        }

        // Click on card to expand/collapse
        holder.itemView.setOnClickListener {
            if (item.customDelay != null) {
                item.isExpanded = !item.isExpanded
                holder.layoutDelayConfig.visibility = if (item.isExpanded) View.VISIBLE else View.GONE
            }
        }
    }

    private fun updateDelayText(holder: AppViewHolder, delay: Int) {
        holder.textCustomDelay.text = "$delay second${if (delay != 1) "s" else ""}"
    }

    override fun getItemCount(): Int = apps.size

    fun updateApps(newApps: List<DelayedLaunchAppItem>) {
        apps = newApps
        notifyDataSetChanged()
    }

    fun filterApps(query: String, showAll: Boolean = true, showUserOnly: Boolean = false, showConfiguredOnly: Boolean = false): List<DelayedLaunchAppItem> {
        return apps.filter { item ->
            val matchesQuery = if (query.isBlank()) true else {
                item.appInfo.appName.contains(query, ignoreCase = true) ||
                item.appInfo.packageName.contains(query, ignoreCase = true)
            }

            val matchesFilter = when {
                showConfiguredOnly -> item.customDelay != null
                showUserOnly -> !item.appInfo.isSystemApp
                showAll -> true
                else -> true
            }

            matchesQuery && matchesFilter
        }
    }
}
