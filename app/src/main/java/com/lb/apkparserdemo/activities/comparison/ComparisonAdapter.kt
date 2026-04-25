package com.lb.apkparserdemo.activities.comparison

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.lb.apkparserdemo.databinding.ItemAppComparisonBinding
import com.lb.apkparserdemo.databinding.ItemComparisonHeaderBinding
import com.lb.apkparserdemo.db.AppIconInfo
import com.lb.apkparserdemo.utils.IconStorage

class ComparisonAdapter : ListAdapter<AppIconInfo, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) TYPE_HEADER else TYPE_ITEM
    }

    override fun getItemCount(): Int {
        val count = super.getItemCount()
        return if (count > 0) count + 1 else 0
    }

    override fun getItem(position: Int): AppIconInfo {
        return super.getItem(position - 1)
    }

    class HeaderViewHolder(binding: ItemComparisonHeaderBinding) : RecyclerView.ViewHolder(binding.root)

    class ItemViewHolder(private val binding: ItemAppComparisonBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: AppIconInfo) {
            binding.appNameTextView.text = item.appName
            binding.packageNameTextView.text = item.packageName
            
            val context = binding.root.context
            val iconFile = IconStorage.getIconFile(context, item.iconFileName)
            binding.fetchedIconImageView.load(iconFile)

            val frameworkIconFile = IconStorage.getIconFile(context, item.frameworkIconFileName)
            binding.frameworkIconImageView.load(frameworkIconFile)

            binding.root.setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("package name", item.packageName)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Copied: ${item.packageName}", Toast.LENGTH_SHORT).show()
            }

            binding.root.setOnLongClickListener {
                val packageManager = context.packageManager
                try {
                    val packageInfo = packageManager.getPackageInfo(item.packageName, 0)
                    val baseApkPath = packageInfo.applicationInfo?.publicSourceDir
                    val splitApkPaths = packageInfo.applicationInfo?.splitPublicSourceDirs?.toList() ?: emptyList()
                    val allApkFilePaths = mutableListOf<String>()
                    baseApkPath?.let { allApkFilePaths.add(it) }
                    allApkFilePaths.addAll(splitApkPaths)

                    val adbCommands = allApkFilePaths.joinToString("\n") { "adb pull \"$it\"" }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("adb pull commands", adbCommands)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Copied adb pull commands for ${item.packageName}", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(ItemComparisonHeaderBinding.inflate(inflater, parent, false))
        } else {
            ItemViewHolder(ItemAppComparisonBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ItemViewHolder) {
            holder.bind(getItem(position))
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<AppIconInfo>() {
        override fun areItemsTheSame(oldItem: AppIconInfo, newItem: AppIconInfo): Boolean =
            oldItem.packageName == newItem.packageName

        override fun areContentsTheSame(oldItem: AppIconInfo, newItem: AppIconInfo): Boolean =
            oldItem == newItem
    }
}
