package com.lb.apkparserdemo.activities.comparison

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.lb.apkparserdemo.databinding.ItemAppComparisonBinding
import com.lb.apkparserdemo.db.AppIconInfo
import com.lb.apkparserdemo.utils.IconStorage

class ComparisonAdapter : ListAdapter<AppIconInfo, ComparisonAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(private val binding: ItemAppComparisonBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AppIconInfo) {
            binding.appNameTextView.text = item.appName
            binding.packageNameTextView.text = item.packageName
            
            val context = binding.root.context
            val iconFile = IconStorage.getIconFile(context, item.iconFileName)
            binding.fetchedIconImageView.load(iconFile)

            val frameworkIconFile = IconStorage.getIconFile(context, item.frameworkIconFileName)
            binding.frameworkIconImageView.load(frameworkIconFile)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemAppComparisonBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    object DiffCallback : DiffUtil.ItemCallback<AppIconInfo>() {
        override fun areItemsTheSame(oldItem: AppIconInfo, newItem: AppIconInfo): Boolean =
            oldItem.packageName == newItem.packageName

        override fun areContentsTheSame(oldItem: AppIconInfo, newItem: AppIconInfo): Boolean =
            oldItem == newItem
    }
}
