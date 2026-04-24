package com.lb.apkparserdemo.apk_info.app_icon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import com.lb.apkparserdemo.apk_info.AbstractZipFilter
import com.lb.apkparserdemo.apk_info.ApkInfo
import com.lb.apkparserdemo.apk_info.XmlDrawableParser
import net.dongliu.apk.parser.bean.DeviceConfig
import net.dongliu.apk.parser.bean.IconPath
import net.dongliu.apk.parser.parser.AdaptiveIconParser
import net.dongliu.apk.parser.parser.BinaryXmlParser
import net.dongliu.apk.parser.parser.XmlTranslator
import net.dongliu.apk.parser.struct.resource.Densities
import java.nio.ByteBuffer
import kotlin.math.abs

object ApkIconFetcher {
    interface ZipFilterCreator {
        fun generateZipFilter(): AbstractZipFilter
    }

    fun getApkIcon(
        context: Context,
        deviceConfig: DeviceConfig?,
        filterGenerator: ZipFilterCreator,
        apkInfo: ApkInfo,
        requestedAppIconSize: Int = 0,
        targetResources: android.content.res.Resources? = null
    ): Bitmap? {
        val apkMeta = apkInfo.apkMetaTranslator.apkMeta
        if (targetResources != null) {
            val resIds = mutableListOf<Long>()
            if (apkMeta.roundIconResourceId != 0L) resIds.add(apkMeta.roundIconResourceId)
            if (apkMeta.iconResourceId != 0L) resIds.add(apkMeta.iconResourceId)
            
            for (resId in resIds) {
                try {
                    val drawable = androidx.core.content.res.ResourcesCompat.getDrawable(targetResources, resId.toInt(), null)
                    if (drawable != null) {
                         android.util.Log.d("AppLog", "icon fetching for ${apkMeta.packageName}: SUCCESS via targetResources.getDrawable(0x${java.lang.Long.toHexString(resId)})")
                         val size = if (requestedAppIconSize > 0) requestedAppIconSize else AppInfoUtil.getAppIconSize(context)
                         return drawable.toBitmap(size, size)
                    }
                } catch (e: Exception) {
                     // android.util.Log.d("AppLog", "icon fetching: failed loading via targetResources 0x${java.lang.Long.toHexString(resId)}: ${e.message}")
                }
            }
        }

        val iconPaths = apkInfo.apkMetaTranslator.iconPaths
        if (iconPaths.isEmpty()) {
            android.util.Log.d("AppLog", "icon fetching: no icon paths found in manifest")
            return null
        }

        val densityDpi = context.resources.displayMetrics.densityDpi
        android.util.Log.d("AppLog", "icon fetching for ${apkInfo.apkMetaTranslator.apkMeta.packageName}: target densityDpi: $densityDpi, found ${iconPaths.size} icon paths")

        // Custom sorting for density: ANY is best, then closest to target densityDpi. 
        // Deprioritize NONE (nodpi) and DEFAULT (0) as they are often not the primary launcher icons.
        val sortedIconPaths = iconPaths.sortedWith(Comparator { o1: IconPath, o2: IconPath ->
            if (o1.density == o2.density) return@Comparator 0
            if (o1.density == Densities.ANY) return@Comparator -1
            if (o2.density == Densities.ANY) return@Comparator 1
            
            val d1 = o1.density
            val d2 = o2.density
            val isNone1 = d1 == Densities.NONE || d1 == Densities.DEFAULT
            val isNone2 = d2 == Densities.NONE || d2 == Densities.DEFAULT
            
            if (isNone1 != isNone2) return@Comparator if (isNone1) 1 else -1

            val diff1 = abs(d1 - densityDpi)
            val diff2 = abs(d2 - densityDpi)
            if (diff1 != diff2) return@Comparator diff1.compareTo(diff2)
            // if same distance, prefer higher density
            d2.compareTo(d1)
        })
        // android.util.Log.d("AppLog", "icon fetching: sorted icon paths: ${sortedIconPaths.map { "${it.path} (density: ${it.density})" }}")

        // Filter out colors for now, try image/xml icons first
        val colorIconsPaths = sortedIconPaths.mapNotNull { it.path }.filter { it.startsWith("#") }.distinct()
        val otherIconPaths = sortedIconPaths.mapNotNull { it.path }.filter { !it.startsWith("#") }.distinct()
        // android.util.Log.d("AppLog", "icon fetching: icon paths to try: $otherIconPaths")

        for (path in otherIconPaths) {
            filterGenerator.generateZipFilter().use { filter ->
                val bytes = filter.getByteArrayForEntries(hashSetOf(path))?.get(path)
                if (bytes != null) {
                    try {
                        val drawable = fetchDrawable(context, path, bytes, apkInfo, deviceConfig, filterGenerator, requestedAppIconSize, targetResources)
                        if (drawable != null) {
                            val typeStr = getDetailedDrawableType(drawable, path)
                            android.util.Log.d("AppLog", "icon fetching for ${apkInfo.apkMetaTranslator.apkMeta.packageName}: SUCCESS: $path, type: $typeStr")
                            return drawable.toBitmap(requestedAppIconSize, requestedAppIconSize)
                        } else {
                            android.util.Log.d("AppLog", "icon fetching for ${apkInfo.apkMetaTranslator.apkMeta.packageName}: FAILED TO DECODE: $path")
                        }
                    } catch (e: Exception) {
                        android.util.Log.d("AppLog", "icon fetching: exception decoding $path: ${e.message}")
                    }
                }
            }
        }

        // Try colors if everything else failed
        for (colorPath in colorIconsPaths) {
            try {
                val color = Color.parseColor(colorPath)
                val bitmap = Bitmap.createBitmap(requestedAppIconSize, requestedAppIconSize, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(color)
                android.util.Log.d("AppLog", "icon fetching for ${apkInfo.apkMetaTranslator.apkMeta.packageName}: SUCCESS with Color: $colorPath")
                return bitmap
            } catch (e: Exception) {}
        }
        return null
    }

    private fun getDetailedDrawableType(drawable: Drawable, path: String?): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && drawable is AdaptiveIconDrawable) {
            val bg = drawable.background
            val fg = drawable.foreground
            val bgType = getDrawableType(bg)
            val fgType = getDrawableType(fg)
            "Adaptive icon (BG: $bgType, FG: $fgType)"
        } else if (drawable is XmlDrawableParser.VectorBitmapDrawable) {
            "VectorDrawable (Rendered)"
        } else if (path?.endsWith(".xml") == true) {
            "XML Drawable (${drawable.javaClass.simpleName})"
        } else if (drawable is BitmapDrawable) {
            "Simple raster image"
        } else {
            "Single Drawable (${drawable.javaClass.simpleName})"
        }
    }

    private fun getDrawableType(drawable: Drawable?): String {
        if (drawable == null) return "null"
        if (drawable is XmlDrawableParser.VectorBitmapDrawable) return "VectorDrawable"
        if (drawable is LayerDrawable) return "LayerList"
        if (drawable is ColorDrawable) return "Color"
        if (drawable is BitmapDrawable) return "Raster"
        return drawable.javaClass.simpleName
    }

    private fun fetchDrawable(
        context: Context,
        path: String,
        bytes: ByteArray?,
        apkInfo: ApkInfo,
        deviceConfig: DeviceConfig?,
        filterGenerator: ZipFilterCreator,
        requestedAppIconSize: Int,
        targetResources: android.content.res.Resources? = null
    ): Drawable? {
        if (path.startsWith("#")) {
            return try {
                ColorDrawable(Color.parseColor(path))
            } catch (e: Exception) {
                null
            }
        }
        if (path.startsWith("?")) {
            // Attempt to resolve attribute reference
            try {
                val attrId = if (path.contains("0x")) path.substringAfter("0x").toLong(16) else 0L
                if (attrId != 0L) {
                    val resources = apkInfo.resourceTable.getResourcesById(attrId)
                    if (resources.isNotEmpty()) {
                        for (res in resources) {
                            val value = res.resourceEntry.toStringValue(apkInfo.resourceTable, deviceConfig)
                            if (value != null && (value.startsWith("#") || value.startsWith("res/"))) {
                                if (value.startsWith("#")) return ColorDrawable(Color.parseColor(value))

                                filterGenerator.generateZipFilter().use { filter ->
                                    val subBytes = filter.getByteArrayForEntries(emptySet(), hashSetOf(value))?.get(value)
                                    return fetchDrawable(context, value, subBytes, apkInfo, deviceConfig, filterGenerator, requestedAppIconSize, targetResources)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {}
        }
        if (path.startsWith("resourceId:")) {
            val resId = try {
                path.substringAfter("0x").toLong(16).toInt()
            } catch (e: Exception) {
                0
            }
            if (resId != 0) {
                val packageId = resId shr 24
                if (packageId == 0x01) {
                    try {
                        val drawable = androidx.core.content.res.ResourcesCompat.getDrawable(context.resources, resId, null)
                        if (drawable != null) return drawable
                    } catch (e: Exception) {}
                } else {
                    // Try to resolve from app resources
                    try {
                        val resources = apkInfo.resourceTable.getResourcesById(resId.toLong())
                        if (resources.isNotEmpty()) {
                            for (res in resources) {
                                val value = res.resourceEntry.toStringValue(apkInfo.resourceTable, deviceConfig)
                                if (value != null && value != path) {
                                    if (value.startsWith("#")) return ColorDrawable(Color.parseColor(value))
                                    filterGenerator.generateZipFilter().use { filter ->
                                        val subBytes = if (isZipPath(value)) filter.getByteArrayForEntries(emptySet(), hashSetOf(value))?.get(value) else null
                                        return fetchDrawable(context, value, subBytes, apkInfo, deviceConfig, filterGenerator, requestedAppIconSize, targetResources)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {}
                }
            }
            return null
        }
        if (bytes == null) return null
        
        if (!path.endsWith(".xml", true)) {
            return getAppIconFromByteArray(bytes, requestedAppIconSize, path)?.let {
                BitmapDrawable(context.resources, it)
            }
        }

        // Handle XML
        try {
            val adaptiveIconParser = AdaptiveIconParser()
            val buffer = ByteBuffer.wrap(bytes)
            val binaryXmlParser = BinaryXmlParser(buffer, apkInfo.resourceTable, adaptiveIconParser, deviceConfig)
            binaryXmlParser.parse()
            val rootTag = adaptiveIconParser.rootTag

            if (rootTag == "adaptive-icon" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val backgroundPaths = adaptiveIconParser.backgroundDrawables
                var foregroundPaths = adaptiveIconParser.foregroundDrawables
                val monochromePaths = adaptiveIconParser.monochromeDrawables
                if (foregroundPaths.isEmpty() && !monochromePaths.isEmpty()) {
                    foregroundPaths = monochromePaths
                }

                if (adaptiveIconParser.hasInlineContent()) {
                    return XmlDrawableParser.tryParseDrawable(context, bytes, apkInfo, deviceConfig, targetResources) { subPath ->
                        filterGenerator.generateZipFilter().use { it.getByteArrayForEntries(emptySet(), hashSetOf(subPath))?.get(subPath) }
                    }
                }

                if (foregroundPaths.isNotEmpty()) {
                    filterGenerator.generateZipFilter().use { filter ->
                        val pathsToFetch = hashSetOf<String>()
                        pathsToFetch.addAll(backgroundPaths.filter { isZipPath(it) })
                        pathsToFetch.addAll(foregroundPaths.filter { isZipPath(it) })
                        val byteArrayForEntries = if (pathsToFetch.isNotEmpty()) filter.getByteArrayForEntries(emptySet(), pathsToFetch) ?: emptyMap() else emptyMap()

                        val backgroundDrawables = backgroundPaths.mapNotNull { p ->
                            fetchDrawable(context, p, byteArrayForEntries[p], apkInfo, deviceConfig, filterGenerator, 0, targetResources)
                        }

                        val foregroundDrawables = foregroundPaths.mapNotNull { p ->
                            fetchDrawable(context, p, byteArrayForEntries[p], apkInfo, deviceConfig, filterGenerator, 0, targetResources)
                        }

                        if (foregroundDrawables.isNotEmpty()) {
                            val bg = when {
                                backgroundDrawables.size > 1 -> LayerDrawable(backgroundDrawables.toTypedArray())
                                backgroundDrawables.size == 1 -> backgroundDrawables[0]
                                else -> ColorDrawable(Color.TRANSPARENT)
                            }
                            val fg = if (foregroundDrawables.size > 1) LayerDrawable(foregroundDrawables.toTypedArray()) else foregroundDrawables[0]
                            return AdaptiveIconDrawable(bg, fg)
                        }
                    }
                }
                return XmlDrawableParser.tryParseDrawable(context, bytes, apkInfo, deviceConfig, targetResources) { subPath ->
                    filterGenerator.generateZipFilter().use { it.getByteArrayForEntries(emptySet(), hashSetOf(subPath))?.get(subPath) }
                }
            } else if (rootTag == "layer-list") {
                val drawablesPaths = adaptiveIconParser.drawables
                if (drawablesPaths.isNotEmpty()) {
                    filterGenerator.generateZipFilter().use { filter ->
                        val pathsToFetch = drawablesPaths.filter { isZipPath(it) }.toHashSet()
                        val byteArrayForEntries = if (pathsToFetch.isNotEmpty()) filter.getByteArrayForEntries(emptySet(), pathsToFetch) ?: emptyMap() else emptyMap()
                        val drawables = drawablesPaths.mapNotNull { layerPath ->
                            fetchDrawable(context, layerPath, byteArrayForEntries[layerPath], apkInfo, deviceConfig, filterGenerator, 0, targetResources)
                        }
                        if (drawables.isNotEmpty()) {
                            return LayerDrawable(drawables.toTypedArray())
                        }
                    }
                }
                return XmlDrawableParser.tryParseDrawable(context, bytes, apkInfo, deviceConfig, targetResources) { subPath ->
                    filterGenerator.generateZipFilter().use { it.getByteArrayForEntries(emptySet(), hashSetOf(subPath))?.get(subPath) }
                }
            } else if (rootTag == "bitmap" || rootTag == "nine-patch" || rootTag == "inset" || rootTag == "clip" || rootTag == "scale" || rootTag == "rotate") {
                val innerPath = adaptiveIconParser.drawables.firstOrNull()
                if (!innerPath.isNullOrBlank()) {
                    filterGenerator.generateZipFilter().use { filter ->
                        val srcBytes = if (isZipPath(innerPath)) filter.getByteArrayForEntries(hashSetOf(innerPath))?.get(innerPath) else null
                        val drawable = fetchDrawable(context, innerPath, srcBytes, apkInfo, deviceConfig, filterGenerator, 0, targetResources)
                        if (drawable != null) return drawable
                    }
                }
                return XmlDrawableParser.tryParseDrawable(context, bytes, apkInfo, deviceConfig, targetResources) { subPath ->
                    filterGenerator.generateZipFilter().use { it.getByteArrayForEntries(emptySet(), hashSetOf(subPath))?.get(subPath) }
                }
            } else {
                val drawable = XmlDrawableParser.tryParseDrawable(context, bytes, apkInfo, deviceConfig, targetResources) { subPath ->
                    filterGenerator.generateZipFilter().use { it.getByteArrayForEntries(emptySet(), hashSetOf(subPath))?.get(subPath) }
                }
                if (drawable == null) {
                    try {
                        val xmlTranslator = XmlTranslator()
                        val fallbackBuffer = ByteBuffer.wrap(bytes)
                        val fallbackBinaryXmlParser = BinaryXmlParser(fallbackBuffer, apkInfo.resourceTable, xmlTranslator, deviceConfig)
                        fallbackBinaryXmlParser.parse()
                        val xml = xmlTranslator.xml
                        return XmlDrawableParser.tryParseDrawable(context, xml)
                    } catch (e: Exception) {}
                }
                return drawable
            }
        } catch (e: Exception) {}
        return null
    }

    private fun isZipPath(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        if (path.startsWith("#")) return false
        if (path.startsWith("resourceId:")) return false
        return true
    }

    private fun getAppIconFromByteArray(bytes: ByteArray, requestedAppIconSize: Int, path: String): Bitmap? {
        if (requestedAppIconSize > 0) {
            val bitmapOptions = BitmapFactory.Options()
            bitmapOptions.inJustDecodeBounds = true
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bitmapOptions)
            BitmapHelper.prepareBitmapOptionsForSampling(
                bitmapOptions,
                requestedAppIconSize,
                requestedAppIconSize
            )
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bitmapOptions)
            if (bitmap != null) return bitmap
        } else {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) return bitmap
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val source = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                return ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    if (requestedAppIconSize > 0) {
                        decoder.setTargetSize(requestedAppIconSize, requestedAppIconSize)
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } catch (e: Exception) {}
        }
        return null
    }
}
