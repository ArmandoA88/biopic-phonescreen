package com.focusfade.app.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.focusfade.app.databinding.ItemAppBinding
import com.focusfade.app.manager.WhitelistManager

class AppListAdapter(
    private val onAppClick: (WhitelistManager.AppInfo) -> Unit,
    private val showRemoveButton: Boolean = false
) : ListAdapter<WhitelistManager.AppInfo, AppListAdapter.AppViewHolder>(AppDiffCallback()) {
    
    private var whitelistedApps = setOf<String>()
    
    fun updateWhitelistedApps(whitelistedPackages: Set<String>) {
        whitelistedApps = whitelistedPackages
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val binding = ItemAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AppViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class AppViewHolder(private val binding: ItemAppBinding) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(appInfo: WhitelistManager.AppInfo) {
            binding.apply {
                textAppName.text = appInfo.appName
                textPackageName.text = appInfo.packageName
                
                // Set app icon
                if (appInfo.icon != null) {
                    imageAppIcon.setImageDrawable(appInfo.icon)
                } else {
                    imageAppIcon.setImageResource(android.R.drawable.sym_def_app_icon)
                }
                
                // Show/hide remove button or status
                if (showRemoveButton) {
                    buttonAction.text = "Remove"
                    buttonAction.visibility = android.view.View.VISIBLE
                    textStatus.visibility = android.view.View.GONE
                } else {
                    buttonAction.visibility = android.view.View.GONE
                    textStatus.visibility = android.view.View.VISIBLE
                    
                    if (whitelistedApps.contains(appInfo.packageName)) {
                        textStatus.text = "Whitelisted"
                        textStatus.setTextColor(binding.root.context.getColor(android.R.color.holo_green_dark))
                    } else {
                        textStatus.text = "Tap to add"
                        textStatus.setTextColor(binding.root.context.getColor(android.R.color.darker_gray))
                    }
                }
                
                // Set click listeners
                root.setOnClickListener {
                    onAppClick(appInfo)
                }
                
                buttonAction.setOnClickListener {
                    onAppClick(appInfo)
                }
            }
        }
    }
    
    private class AppDiffCallback : DiffUtil.ItemCallback<WhitelistManager.AppInfo>() {
        override fun areItemsTheSame(oldItem: WhitelistManager.AppInfo, newItem: WhitelistManager.AppInfo): Boolean {
            return oldItem.packageName == newItem.packageName
        }
        
        override fun areContentsTheSame(oldItem: WhitelistManager.AppInfo, newItem: WhitelistManager.AppInfo): Boolean {
            return oldItem == newItem
        }
    }
}
