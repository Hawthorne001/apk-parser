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
import com.lb.apkparserdemo.utils.IconStorage

class ComparisonAdapter : ListAdapter<ComparisonItem, RecyclerView.ViewHolder>(DiffCallback) {

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

    override fun getItem(position: Int): ComparisonItem {
        return super.getItem(position - 1)
    }

    class HeaderViewHolder(binding: ItemComparisonHeaderBinding) : RecyclerView.ViewHolder(binding.root)

    class ItemViewHolder(private val binding: ItemAppComparisonBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ComparisonItem) {
            val info = item.info
            binding.appNameTextView.text = info.appName
            binding.packageNameTextView.text = info.packageName

            val context = binding.root.context
            val iconFile = IconStorage.getIconFile(context, info.iconFileName)
            binding.fetchedIconImageView.load(iconFile)

            val frameworkIconFile = IconStorage.getIconFile(context, info.frameworkIconFileName)
            binding.frameworkIconImageView.load(frameworkIconFile)

            val score = item.matchScore
            if (score != null) {
                val percentage = (score * 100).toInt()
                binding.comparisonResultTextView.text = if (score == 1.0f) {
                    "Perfect Match (100%)"
                } else {
                    "Match Score: $percentage%"
                }
                binding.comparisonResultTextView.setTextColor(if (score == 1.0f) {
                    android.graphics.Color.GREEN
                } else if (score > 0.9f) {
                    0xFFFFA500.toInt() // Orange
                } else {
                    android.graphics.Color.RED
                })
            } else {
                binding.comparisonResultTextView.text = "Comparing..."
                binding.comparisonResultTextView.setTextColor(android.graphics.Color.GRAY)
            }

            binding.root.setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("package name", info.packageName)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Copied: ${info.packageName}", Toast.LENGTH_SHORT).show()
            }

            binding.root.setOnLongClickListener {
                val packageManager = context.packageManager
                try {
                    val packageInfo = packageManager.getPackageInfo(info.packageName, 0)
                    val baseApkPath = packageInfo.applicationInfo?.publicSourceDir
                    val splitApkPaths = packageInfo.applicationInfo?.splitPublicSourceDirs?.toList() ?: emptyList()
                    val allApkFilePaths = mutableListOf<String>()
                    baseApkPath?.let { allApkFilePaths.add(it) }
                    allApkFilePaths.addAll(splitApkPaths)

                    val adbCommands = allApkFilePaths.joinToString("\n") { "./adb pull \"$it\"" }
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("adb pull commands", adbCommands)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Copied adb pull commands for ${info.packageName}", Toast.LENGTH_SHORT).show()
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

    object DiffCallback : DiffUtil.ItemCallback<ComparisonItem>() {
        override fun areItemsTheSame(oldItem: ComparisonItem, newItem: ComparisonItem): Boolean =
            oldItem.info.packageName == newItem.info.packageName

        override fun areContentsTheSame(oldItem: ComparisonItem, newItem: ComparisonItem): Boolean =
            oldItem == newItem
    }
}
