package com.lb.apkparserdemo.apk_info

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RotateDrawable
import android.os.Build
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import net.dongliu.apk.parser.bean.DeviceConfig
import net.dongliu.apk.parser.parser.BinaryXmlParser
import net.dongliu.apk.parser.parser.XmlStreamer
import net.dongliu.apk.parser.struct.ResourceValue
import net.dongliu.apk.parser.struct.xml.Attributes
import net.dongliu.apk.parser.struct.xml.XmlNodeEndTag
import net.dongliu.apk.parser.struct.xml.XmlNodeStartTag
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import kotlin.math.*

object XmlDrawableParser {

    class VectorBitmapDrawable(context: Context, bitmap: Bitmap) : BitmapDrawable(context.resources, bitmap)

    private fun Attributes.getAttr(name: String): String? {
        val value = this.getString(name)
        if (value != null) return value
        for (attr in this.attributes) {
            if (attr != null && (attr.name == name || attr.name == "android:$name")) {
                return attr.value
            }
        }
        return null
    }

    fun tryParseDrawable(
        context: Context,
        binXml: ByteArray,
        apkInfo: ApkInfo,
        deviceConfig: DeviceConfig?,
        requestedAppIconSize: Int = 0,
        subResourceProvider: ((String) -> ByteArray?)? = null
    ): Drawable? {
        if (binXml.size < 4) return null
        val buffer = ByteBuffer.wrap(binXml).order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.short.toInt() != 0x0003) return null

        val streamer = DrawableStreamer(context, apkInfo, deviceConfig, requestedAppIconSize, subResourceProvider)
        val parser = BinaryXmlParser(ByteBuffer.wrap(binXml), apkInfo.resourceTable, streamer, deviceConfig)
        return try {
            parser.parse()
            streamer.result
        } catch (e: Exception) {
            android.util.Log.d("AppLog", "icon fetching: exception in BinaryXmlParser: ${e.message}")
            null
        }
    }

    private class DrawableStreamer(
            private val context: Context,
            private val apkInfo: ApkInfo,
            private val deviceConfig: DeviceConfig?,
            private val requestedAppIconSize: Int,
            private val subResourceProvider: ((String) -> ByteArray?)?
    ) : XmlStreamer {
        var result: Drawable? = null

        private val drawableStack = Stack<Any>()
        private var vectorBuilder: ImageVector.Builder? = null
        private val extraGroupsStack = mutableListOf<Int>()
        private var depth = 0

        override fun onStartTag(tag: XmlNodeStartTag) {
            val attr = tag.attributes
            val name = tag.name

            when (name) {
                "vector" -> {
                    val width = attr.getAttr("width")?.parseDimension() ?: 24f
                    val height = attr.getAttr("height")?.parseDimension() ?: 24f
                    val viewportWidth = attr.getAttr("viewportWidth")?.toFloat() ?: width
                    val viewportHeight = attr.getAttr("viewportHeight")?.toFloat() ?: height

                    val newBuilder = ImageVector.Builder(
                            name = attr.getAttr("name") ?: "vector",
                            defaultWidth = width.dp,
                            defaultHeight = height.dp,
                            viewportWidth = viewportWidth,
                            viewportHeight = viewportHeight,
                            tintColor = attr.getAttr("tint")?.let { resolveColor(context, it, apkInfo, deviceConfig, subResourceProvider) }
                                    ?: Color.Unspecified,
                            tintBlendMode = parseBlendMode(attr.getAttr("tintMode")),
                            autoMirror = attr.getAttr("autoMirrored")?.toBoolean() ?: false
                    )

                    if (vectorBuilder != null) {
                        drawableStack.push(vectorBuilder!!)
                    }
                    vectorBuilder = newBuilder
                    extraGroupsStack.add(0)
                }
                "group" -> {
                    vectorBuilder?.addGroup(
                            name = attr.getAttr("name") ?: "",
                            rotate = attr.getAttr("rotation")?.toFloat() ?: 0f,
                            pivotX = attr.getAttr("pivotX")?.toFloat() ?: 0f,
                            pivotY = attr.getAttr("pivotY")?.toFloat() ?: 0f,
                            scaleX = attr.getAttr("scaleX")?.toFloat() ?: 1f,
                            scaleY = attr.getAttr("scaleY")?.toFloat() ?: 1f,
                            translationX = attr.getAttr("translateX")?.toFloat() ?: 0f,
                            translationY = attr.getAttr("translateY")?.toFloat() ?: 0f
                    )
                    extraGroupsStack.add(0)
                }
                "path" -> {
                    val pathData = attr.getAttr("pathData") ?: return
                    val fillBrush = attr.getAttr("fillColor")?.let { obtainBrush(it) }
                    val strokeBrush = attr.getAttr("strokeColor")?.let { obtainBrush(it) }

                    val finalFill = fillBrush ?: if (attr.getAttr("fillColor") != null) null else SolidColor(Color.Black)

                    vectorBuilder?.addPath(
                            pathData = addPathNodes(pathData),
                            name = attr.getAttr("name") ?: "",
                            fill = finalFill,
                            fillAlpha = attr.getAttr("fillAlpha")?.toFloat() ?: 1f,
                            stroke = strokeBrush,
                            strokeAlpha = attr.getAttr("strokeAlpha")?.toFloat() ?: 1f,
                            strokeLineWidth = attr.getAttr("strokeWidth")?.toFloat() ?: 0f,
                            strokeLineCap = parseStrokeCap(attr.getAttr("strokeLineCap")),
                            strokeLineJoin = parseStrokeJoin(attr.getAttr("strokeLineJoin")),
                            strokeLineMiter = attr.getAttr("strokeMiterLimit")?.toFloat() ?: 4f,
                            pathFillType = if (attr.getAttr("fillType") == "evenOdd" || attr.getAttr("fillType") == "1") PathFillType.EvenOdd else PathFillType.NonZero
                    )
                }
                "clip-path" -> {
                    val pathData = attr.getAttr("pathData") ?: return
                    vectorBuilder?.addGroup(
                            name = attr.getAttr("name") ?: "",
                            clipPathData = addPathNodes(pathData)
                    )
                    if (extraGroupsStack.isNotEmpty()) {
                        extraGroupsStack[extraGroupsStack.size - 1]++
                    }
                }
                "adaptive-icon" -> {
                    drawableStack.push(AdaptiveIconBuilder())
                }
                "background", "foreground", "monochrome" -> {
                    val builder = drawableStack.peek() as? AdaptiveIconBuilder
                    builder?.currentSection = name
                    attr.getAttr("drawable")?.let { builder?.setDrawable(name, it) }
                }
                "layer-list" -> {
                    drawableStack.push(mutableListOf<LayerItem>())
                }
                "item" -> {
                    val layerList = drawableStack.peek() as? MutableList<LayerItem>
                    val drawablePath = attr.getAttr("drawable")
                    val drawable = if (drawablePath != null) resolve(drawablePath) else null
                    layerList?.add(LayerItem(drawable))
                }
                "inset" -> {
                    val drawablePath = attr.getAttr("drawable")
                    val drawable = if (drawablePath != null) resolve(drawablePath) else null
                    val insetBuilder = InsetBuilder(drawable)
                    drawableStack.push(insetBuilder)
                }
                "rotate" -> {
                    val drawablePath = attr.getAttr("drawable")
                    val drawable = if (drawablePath != null) resolve(drawablePath) else null
                    val rotateBuilder = RotateBuilder(drawable)
                    rotateBuilder.fromDegrees = attr.getAttr("fromDegrees")?.toFloat() ?: 0f
                    rotateBuilder.toDegrees = attr.getAttr("toDegrees")?.toFloat() ?: 360f
                    rotateBuilder.pivotX = attr.getAttr("pivotX") ?: "50%"
                    rotateBuilder.pivotY = attr.getAttr("pivotY") ?: "50%"
                    drawableStack.push(rotateBuilder)
                }
                "color" -> {
                    val color = attr.getAttr("color")?.let { resolveColor(context, it, apkInfo, deviceConfig, subResourceProvider) } ?: Color.Transparent
                    if (depth == 0) result = ColorDrawable(color.toArgb())
                    else handleFinishedDrawable(ColorDrawable(color.toArgb()))
                }
            }
            depth++
        }

        override fun onEndTag(tag: XmlNodeEndTag) {
            depth--
            val name = tag.name
            when (name) {
                "vector" -> {
                    if (extraGroupsStack.isNotEmpty()) {
                        val extras = extraGroupsStack.removeAt(extraGroupsStack.size - 1)
                        repeat(extras) { vectorBuilder?.clearGroup() }
                    }
                    val finishedVector = vectorBuilder?.build()
                    vectorBuilder = if (drawableStack.isNotEmpty() && drawableStack.peek() is ImageVector.Builder) {
                        drawableStack.pop() as ImageVector.Builder
                    } else null

                    if (finishedVector != null) {
                        val isLayer = drawableStack.isNotEmpty() && (drawableStack.peek() is AdaptiveIconBuilder || drawableStack.peek() is MutableList<*> || drawableStack.peek() is InsetBuilder || drawableStack.peek() is RotateBuilder)
                        val drawable = imageVectorToDrawable(context, finishedVector, requestedAppIconSize, isLayer)
                        handleFinishedDrawable(drawable)
                    }
                }
                "group" -> {
                    if (extraGroupsStack.isNotEmpty()) {
                        val extras = extraGroupsStack.removeAt(extraGroupsStack.size - 1)
                        repeat(extras + 1) { vectorBuilder?.clearGroup() }
                    }
                }
                "adaptive-icon" -> {
                    val builder = drawableStack.pop() as AdaptiveIconBuilder
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val bg = builder.background ?: ColorDrawable(android.graphics.Color.TRANSPARENT)
                        val fg = builder.foreground ?: ColorDrawable(android.graphics.Color.TRANSPARENT)
                        val drawable = AdaptiveIconDrawable(bg, fg)
                        handleFinishedDrawable(drawable)
                    }
                }
                "background", "foreground", "monochrome" -> {
                    (drawableStack.peek() as? AdaptiveIconBuilder)?.currentSection = null
                }
                "layer-list" -> {
                    val items = drawableStack.pop() as MutableList<LayerItem>
                    val drawables = items.mapNotNull { it.drawable }
                    if (drawables.isNotEmpty()) {
                        handleFinishedDrawable(LayerDrawable(drawables.toTypedArray()))
                    }
                }
                "inset" -> {
                    val builder = drawableStack.pop() as InsetBuilder
                    builder.drawable?.let { handleFinishedDrawable(InsetDrawable(it, 0)) }
                }
                "rotate" -> {
                    val builder = drawableStack.pop() as RotateBuilder
                    builder.drawable?.let {
                        val rd = RotateDrawable()
                        rd.drawable = it
                        rd.fromDegrees = builder.fromDegrees
                        rd.toDegrees = builder.toDegrees
                        rd.level = 0
                        handleFinishedDrawable(rd)
                    }
                }
            }
        }

        private fun handleFinishedDrawable(drawable: Drawable) {
            if (drawableStack.isEmpty()) {
                result = drawable
            } else {
                when (val parent = drawableStack.peek()) {
                    is AdaptiveIconBuilder -> {
                        when (parent.currentSection) {
                            "background" -> parent.background = drawable
                            "foreground" -> parent.foreground = drawable
                            "monochrome" -> parent.monochrome = drawable
                        }
                    }
                    is MutableList<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        val list = parent as MutableList<LayerItem>
                        list.lastOrNull()?.drawable = drawable
                    }
                    is InsetBuilder -> parent.drawable = drawable
                    is RotateBuilder -> parent.drawable = drawable
                }
            }
        }

        private fun resolveColor(colorStr: String): Color {
            if (colorStr.startsWith("#")) {
                val normalizedColor = if (colorStr.length == 4) {
                    "#" + colorStr[1] + colorStr[1] + colorStr[2] + colorStr[2] + colorStr[3] + colorStr[3]
                } else colorStr
                return try { Color(android.graphics.Color.parseColor(normalizedColor)) } catch (e: Exception) { Color.Transparent }
            }
            if (colorStr.startsWith("resourceId:")) {
                val resId = try { colorStr.substringAfter("0x").toLong(16).toInt() } catch (e: Exception) { 0 }
                if ((resId shr 24) == 0x01) {
                    return try { Color(androidx.core.content.res.ResourcesCompat.getColor(context.resources, resId, null)) } catch (e: Exception) { Color.Transparent }
                }
                val ref = ResourceValue.reference(resId)
                val value = ref.toStringValue(apkInfo.resourceTable, deviceConfig)
                if (value != null && value != colorStr) return resolveColor(value)
            }
            return Color.Transparent
        }

        private fun obtainBrush(colorStr: String): Brush? {
            if (colorStr.endsWith(".xml") && subResourceProvider != null) {
                val bytes = subResourceProvider.invoke(colorStr)
                if (bytes != null) {
                    return tryParseComplexColor(context, bytes, apkInfo, deviceConfig, subResourceProvider)
                }
            }
            val color = resolveColor(colorStr)
            if (color != Color.Transparent) return SolidColor(color)
            return null
        }

        private fun resolve(path: String): Drawable? {
            if (path.startsWith("#")) return ColorDrawable(android.graphics.Color.parseColor(path))
            if (path.startsWith("resourceId:")) {
                val resId = try { path.substringAfter("0x").toLong(16).toInt() } catch (e: Exception) { 0 }
                if ((resId shr 24) == 0x01) {
                    return try { androidx.core.content.res.ResourcesCompat.getDrawable(context.resources, resId, null) } catch (e: Exception) { null }
                }
                val ref = ResourceValue.reference(resId)
                val value = ref.toStringValue(apkInfo.resourceTable, deviceConfig)
                if (value != null && value != path) return resolve(value)
            }
            val bytes = subResourceProvider?.invoke(path)
            if (bytes != null) {
                val xmlDrawable = tryParseDrawable(context, bytes, apkInfo, deviceConfig, requestedAppIconSize, subResourceProvider)
                if (xmlDrawable != null) return xmlDrawable
                // Fallback for raster assets (PNG/WebP)
                return try {
                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap != null) BitmapDrawable(context.resources, bitmap) else null
                } catch (e: Exception) {
                    null
                }
            }
            return null
        }

        private inner class AdaptiveIconBuilder {
            var background: Drawable? = null
            var foreground: Drawable? = null
            var monochrome: Drawable? = null
            var currentSection: String? = null

            fun setDrawable(section: String, path: String) {
                val d = resolve(path)
                when (section) {
                    "background" -> background = d
                    "foreground" -> foreground = d
                    "monochrome" -> monochrome = d
                }
            }
        }

        private class LayerItem(var drawable: Drawable? = null)
        private class InsetBuilder(var drawable: Drawable? = null)
        private class RotateBuilder(var drawable: Drawable? = null) {
            var fromDegrees: Float = 0f
            var toDegrees: Float = 360f
            var pivotX: String = "50%"
            var pivotY: String = "50%"
        }

        override fun onCData(xmlCData: net.dongliu.apk.parser.struct.xml.XmlCData) {}
        override fun onNamespaceStart(tag: net.dongliu.apk.parser.struct.xml.XmlNamespaceStartTag) {}
        override fun onNamespaceEnd(tag: net.dongliu.apk.parser.struct.xml.XmlNamespaceEndTag) {}
    }

    private fun imageVectorToDrawable(context: Context, imageVector: ImageVector, requestedAppIconSize: Int = 0, isLayer: Boolean = false): Drawable {
        val density = Density(context.resources.displayMetrics.density)

        val widthPx: Int
        val heightPx: Int

        if (requestedAppIconSize > 0) {
            if (isLayer) {
                widthPx = (requestedAppIconSize * (108.0 / 72.0)).toInt()
                heightPx = (requestedAppIconSize * (108.0 / 72.0)).toInt()
            } else {
                widthPx = requestedAppIconSize
                heightPx = requestedAppIconSize
            }
        } else {
            widthPx = with(density) { imageVector.defaultWidth.toPx() }.toInt().coerceAtLeast(1)
            heightPx = with(density) { imageVector.defaultHeight.toPx() }.toInt().coerceAtLeast(1)
        }

        val finalWidth = widthPx
        val finalHeight = heightPx

        val bitmap = Bitmap.createBitmap(finalWidth, finalHeight, Bitmap.Config.ARGB_8888)
        bitmap.density = context.resources.displayMetrics.densityDpi
        val canvas = Canvas(android.graphics.Canvas(bitmap))
        val drawScope = CanvasDrawScope()
        drawScope.draw(density, LayoutDirection.Ltr, canvas, androidx.compose.ui.geometry.Size(finalWidth.toFloat(), finalHeight.toFloat())) {
            val colorFilter = if (imageVector.tintColor != Color.Unspecified && imageVector.tintColor != Color.Transparent) {
                ColorFilter.tint(imageVector.tintColor, imageVector.tintBlendMode)
            } else null
            withTransform({
                scale(
                        scaleX = finalWidth.toFloat() / imageVector.viewportWidth,
                        scaleY = finalHeight.toFloat() / imageVector.viewportHeight,
                        pivot = androidx.compose.ui.geometry.Offset.Zero
                )
            }) {
                renderVectorGroup(imageVector.root, colorFilter)
            }
        }
        return VectorBitmapDrawable(context, bitmap)
    }

    private fun androidx.compose.ui.graphics.drawscope.DrawScope.renderVectorGroup(group: VectorGroup, colorFilter: ColorFilter?) {
        withTransform({
            translate(group.translationX, group.translationY)
            rotate(group.rotation, androidx.compose.ui.geometry.Offset(group.pivotX, group.pivotY))
            scale(group.scaleX, group.scaleY, androidx.compose.ui.geometry.Offset(group.pivotX, group.pivotY))
        }) {
            for (node in group) {
                when (node) {
                    is VectorPath -> {
                        val path = Path()
                        addPathNodesToPath(node.pathData, path)
                        path.fillType = node.pathFillType
                        drawPath(
                                path = path,
                                brush = node.fill ?: SolidColor(Color.Transparent),
                                alpha = node.fillAlpha,
                                style = Fill,
                                colorFilter = colorFilter
                        )
                        if (node.stroke != null && node.strokeLineWidth > 0) {
                            drawPath(
                                    path = path,
                                    brush = node.stroke!!,
                                    alpha = node.strokeAlpha,
                                    style = Stroke(
                                            width = node.strokeLineWidth,
                                            cap = node.strokeLineCap,
                                            join = node.strokeLineJoin,
                                            miter = node.strokeLineMiter
                                    ),
                                    colorFilter = colorFilter
                            )
                        }
                    }

                    is VectorGroup -> renderVectorGroup(node, colorFilter)
                }
            }
        }
    }

    private fun addPathNodesToPath(nodes: List<PathNode>, path: Path) {
        var currentX = 0f
        var currentY = 0f
        var segmentX = 0f
        var segmentY = 0f
        var lastControlX = 0f
        var lastControlY = 0f

        for (node in nodes) {
            var nextControlX = 0f
            var nextControlY = 0f
            when (node) {
                is PathNode.Close -> {
                    currentX = segmentX
                    currentY = segmentY
                    path.close()
                }

                is PathNode.MoveTo -> {
                    currentX = node.x
                    currentY = node.y
                    segmentX = node.x
                    segmentY = node.y
                    path.moveTo(node.x, node.y)
                }

                is PathNode.RelativeMoveTo -> {
                    currentX += node.dx
                    currentY += node.dy
                    segmentX = currentX
                    segmentY = currentY
                    path.relativeMoveTo(node.dx, node.dy)
                }

                is PathNode.LineTo -> {
                    currentX = node.x
                    currentY = node.y
                    path.lineTo(node.x, node.y)
                }

                is PathNode.RelativeLineTo -> {
                    currentX += node.dx
                    currentY += node.dy
                    path.relativeLineTo(node.dx, node.dy)
                }

                is PathNode.HorizontalTo -> {
                    currentX = node.x
                    path.lineTo(node.x, currentY)
                }

                is PathNode.RelativeHorizontalTo -> {
                    currentX += node.dx
                    path.relativeLineTo(node.dx, 0f)
                }

                is PathNode.VerticalTo -> {
                    currentY = node.y
                    path.lineTo(currentX, node.y)
                }

                is PathNode.RelativeVerticalTo -> {
                    currentY += node.dy
                    path.relativeLineTo(0f, node.dy)
                }

                is PathNode.CurveTo -> {
                    path.cubicTo(node.x1, node.y1, node.x2, node.y2, node.x3, node.y3)
                    nextControlX = node.x2
                    nextControlY = node.y2
                    currentX = node.x3
                    currentY = node.y3
                }

                is PathNode.RelativeCurveTo -> {
                    path.relativeCubicTo(node.dx1, node.dy1, node.dx2, node.dy2, node.dx3, node.dy3)
                    nextControlX = currentX + node.dx2
                    nextControlY = currentY + node.dy2
                    currentX += node.dx3
                    currentY += node.dy3
                }

                is PathNode.QuadTo -> {
                    path.quadraticTo(node.x1, node.y1, node.x2, node.y2)
                    nextControlX = node.x1
                    nextControlY = node.y1
                    currentX = node.x2
                    currentY = node.y2
                }

                is PathNode.RelativeQuadTo -> {
                    path.relativeQuadraticTo(node.dx1, node.dy1, node.dx2, node.dy2)
                    nextControlX = currentX + node.dx1
                    nextControlY = currentY + node.dy1
                    currentX += node.dx2
                    currentY += node.dy2
                }

                is PathNode.ArcTo -> {
                    drawArc(
                            path,
                            currentX.toDouble(),
                            currentY.toDouble(),
                            node.arcStartX.toDouble(),
                            node.arcStartY.toDouble(),
                            node.horizontalEllipseRadius.toDouble(),
                            node.verticalEllipseRadius.toDouble(),
                            node.theta.toDouble(),
                            node.isMoreThanHalf,
                            node.isPositiveArc
                    )
                    currentX = node.arcStartX
                    currentY = node.arcStartY
                }

                is PathNode.RelativeArcTo -> {
                    val nextX = currentX + node.arcStartDx
                    val nextY = currentY + node.arcStartDy
                    drawArc(
                            path,
                            currentX.toDouble(),
                            currentY.toDouble(),
                            nextX.toDouble(),
                            nextY.toDouble(),
                            node.horizontalEllipseRadius.toDouble(),
                            node.verticalEllipseRadius.toDouble(),
                            node.theta.toDouble(),
                            node.isMoreThanHalf,
                            node.isPositiveArc
                    )
                    currentX = nextX
                    currentY = nextY
                }
                is PathNode.ReflectiveCurveTo -> {
                    val cx = if (lastControlX.isNaN()) currentX else 2 * currentX - lastControlX
                    val cy = if (lastControlY.isNaN()) currentY else 2 * currentY - lastControlY
                    path.cubicTo(cx, cy, node.x1, node.y1, node.x2, node.y2)
                    nextControlX = node.x1
                    nextControlY = node.y1
                    currentX = node.x2
                    currentY = node.y2
                }
                is PathNode.RelativeReflectiveCurveTo -> {
                    val cx = if (lastControlX.isNaN()) currentX else 2 * currentX - lastControlX
                    val cy = if (lastControlY.isNaN()) currentY else 2 * currentY - lastControlY
                    path.cubicTo(cx, cy, currentX + node.dx1, currentY + node.dy1, currentX + node.dx2, currentY + node.dy2)
                    nextControlX = currentX + node.dx1
                    nextControlY = currentY + node.dy1
                    currentX += node.dx2
                    currentY += node.dy2
                }
                is PathNode.ReflectiveQuadTo -> {
                    val cx = if (lastControlX.isNaN()) currentX else 2 * currentX - lastControlX
                    val cy = if (lastControlY.isNaN()) currentY else 2 * currentY - lastControlY
                    path.quadraticTo(cx, cy, node.x, node.y)
                    nextControlX = cx
                    nextControlY = cy
                    currentX = node.x
                    currentY = node.y
                }
                is PathNode.RelativeReflectiveQuadTo -> {
                    val cx = if (lastControlX.isNaN()) currentX else 2 * currentX - lastControlX
                    val cy = if (lastControlY.isNaN()) currentY else 2 * currentY - lastControlY
                    path.quadraticTo(cx, cy, currentX + node.dx, currentY + node.dy)
                    nextControlX = cx
                    nextControlY = cy
                    currentX += node.dx
                    currentY += node.dy
                }
            }
            lastControlX = if (node is PathNode.CurveTo || node is PathNode.RelativeCurveTo ||
                node is PathNode.QuadTo || node is PathNode.RelativeQuadTo ||
                node is PathNode.ReflectiveCurveTo || node is PathNode.RelativeReflectiveCurveTo ||
                node is PathNode.ReflectiveQuadTo || node is PathNode.RelativeReflectiveQuadTo) {
                nextControlX
            } else {
                Float.NaN
            }
            lastControlY = if (node is PathNode.CurveTo || node is PathNode.RelativeCurveTo ||
                node is PathNode.QuadTo || node is PathNode.RelativeQuadTo ||
                node is PathNode.ReflectiveCurveTo || node is PathNode.RelativeReflectiveCurveTo ||
                node is PathNode.ReflectiveQuadTo || node is PathNode.RelativeReflectiveQuadTo) {
                nextControlY
            } else {
                Float.NaN
            }
        }
    }

    private fun drawArc(
            path: Path,
            x0: Double, y0: Double,
            x1: Double, y1: Double,
            a: Double, b: Double,
            theta: Double,
            isLargeArc: Boolean,
            isSweep: Boolean
    ) {
        val thetaRad = Math.toRadians(theta)
        val cosTheta = cos(thetaRad)
        val sinTheta = sin(thetaRad)

        val dx2 = (x0 - x1) / 2.0
        val dy2 = (y0 - y1) / 2.0
        val x1p = cosTheta * dx2 + sinTheta * dy2
        val y1p = -sinTheta * dx2 + cosTheta * dy2

        var rx = abs(a)
        var ry = abs(b)
        val lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
        if (lambda > 1.0) {
            rx *= sqrt(lambda)
            ry *= sqrt(lambda)
        }

        val rxSq = rx * rx
        val rySq = ry * ry
        val x1pSq = x1p * x1p
        val y1pSq = y1p * y1p

        var radicand = (rxSq * rySq - rxSq * y1pSq - rySq * x1pSq) / (rxSq * rySq + rySq * x1pSq)
        radicand = max(0.0, radicand)
        val coef = (if (isLargeArc == isSweep) -1.0 else 1.0) * sqrt(radicand)
        val cxp = coef * ((rx * y1p) / ry)
        val cyp = coef * (-(ry * x1p) / rx)

        val cx = cosTheta * cxp - sinTheta * cyp + (x0 + x1) / 2.0
        val cy = sinTheta * cxp + cosTheta * cyp + (y0 + y1) / 2.0

        val ux = (x1p - cxp) / rx
        val uy = (y1p - cyp) / ry
        val vx = (-x1p - cxp) / rx
        val vy = (-y1p - cyp) / ry

        fun angle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val dot = ux * vx + uy * vy
            val len = sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy)
            var ang = acos(max(-1.0, min(1.0, dot / len)))
            if (ux * vy - uy * vx < 0.0) ang = -ang
            return ang
        }

        val startAngle = angle(1.0, 0.0, ux, uy)
        var deltaAngle = angle(ux, uy, vx, vy)

        if (!isSweep && deltaAngle > 0) {
            deltaAngle -= 2.0 * PI
        } else if (isSweep && deltaAngle < 0) {
            deltaAngle += 2.0 * PI
        }

        val numSegments = ceil(abs(deltaAngle) / (PI / 2.0)).toInt()
        var angle = startAngle
        for (i in 0 until numSegments) {
            val segmentDelta = deltaAngle / numSegments
            val ax = rx * cos(angle)
            val ay = ry * sin(angle)
            val bx = rx * cos(angle + segmentDelta)
            val by = ry * sin(angle + segmentDelta)

            val t = 4.0 / 3.0 * tan(segmentDelta / 4.0)
            val x2 = ax - t * ry * sin(angle)
            val y2 = ay + t * rx * cos(angle)
            val x3 = bx + t * ry * sin(angle + segmentDelta)
            val y3 = by - t * rx * cos(angle + segmentDelta)

            path.cubicTo(
                    (cosTheta * x2 - sinTheta * y2 + cx).toFloat(), (sinTheta * x2 + cosTheta * y2 + cy).toFloat(),
                    (cosTheta * x3 - sinTheta * y3 + cx).toFloat(), (sinTheta * x3 + cosTheta * y3 + cy).toFloat(),
                    (cosTheta * bx - sinTheta * by + cx).toFloat(), (sinTheta * bx + cosTheta * by + cy).toFloat()
            )
            angle += segmentDelta
        }
    }

    private fun addPathNodes(pathData: String): List<PathNode> = androidx.compose.ui.graphics.vector.addPathNodes(pathData)

    private fun String.parseDimension(): Float = filter { it.isDigit() || it == '.' || it == '-' || it == 'e' || it == 'E' }.toFloatOrNull()
            ?: 0f

    private fun obtainBrush(
            context: Context,
            colorStr: String,
            apkInfo: ApkInfo,
            deviceConfig: DeviceConfig?,
            subResourceProvider: ((String) -> ByteArray?)?
    ): Brush? {
        if (colorStr.endsWith(".xml") && subResourceProvider != null) {
            val bytes = subResourceProvider(colorStr)
            if (bytes != null) {
                return tryParseComplexColor(context, bytes, apkInfo, deviceConfig, subResourceProvider)
            }
        }
        val color = resolveColor(context, colorStr, apkInfo, deviceConfig)
        if (color != Color.Transparent) return SolidColor(color)
        return null
    }

    private fun resolveColor(context: Context, colorStr: String, apkInfo: ApkInfo, deviceConfig: DeviceConfig?, subResourceProvider: ((String) -> ByteArray?)? = null): Color {
        if (colorStr.startsWith("#")) {
            val normalizedColor = if (colorStr.length == 4) {
                "#" + colorStr[1] + colorStr[1] + colorStr[2] + colorStr[2] + colorStr[3] + colorStr[3]
            } else colorStr
            return try { Color(android.graphics.Color.parseColor(normalizedColor)) } catch (e: Exception) { Color.Transparent }
        }
        if (colorStr.startsWith("resourceId:")) {
            val resId = try { colorStr.substringAfter("0x").toLong(16).toInt() } catch (e: Exception) { 0 }
            if ((resId shr 24) == 0x01) {
                return try { Color(androidx.core.content.res.ResourcesCompat.getColor(context.resources, resId, null)) } catch (e: Exception) { Color.Transparent }
            }
            val ref = ResourceValue.reference(resId)
            val value = ref.toStringValue(apkInfo.resourceTable, deviceConfig)
            if (value != null && value != colorStr) return resolveColor(context, value, apkInfo, deviceConfig, subResourceProvider)
        }
        if (colorStr.endsWith(".xml") && subResourceProvider != null) {
            val bytes = subResourceProvider(colorStr)
            if (bytes != null) {
                // Parse ColorStateList XML and pick the best/default color
                return parseColorStateList(context, bytes, apkInfo, deviceConfig, subResourceProvider)
            }
        }
        return Color.Transparent
    }

    private fun parseColorStateList(context: Context, bytes: ByteArray, apkInfo: ApkInfo, deviceConfig: DeviceConfig?, subResourceProvider: ((String) -> ByteArray?)?): Color {
        var resultColor = Color.Transparent
        val streamer = object : XmlStreamer {
            override fun onStartTag(tag: XmlNodeStartTag) {
                if (tag.name == "item") {
                    val colorAttr = tag.attributes.getAttr("color")
                    if (colorAttr != null && resultColor == Color.Transparent) {
                        resultColor = resolveColor(context, colorAttr, apkInfo, deviceConfig, subResourceProvider)
                    }
                }
            }
            override fun onEndTag(tag: XmlNodeEndTag) {}
            override fun onCData(xmlCData: net.dongliu.apk.parser.struct.xml.XmlCData) {}
            override fun onNamespaceStart(tag: net.dongliu.apk.parser.struct.xml.XmlNamespaceStartTag) {}
            override fun onNamespaceEnd(tag: net.dongliu.apk.parser.struct.xml.XmlNamespaceEndTag) {}
        }
        val parser = BinaryXmlParser(ByteBuffer.wrap(bytes), apkInfo.resourceTable, streamer, deviceConfig)
        try { parser.parse() } catch (e: Exception) {}
        return resultColor
    }

    private fun tryParseComplexColor(
            context: Context,
            bytes: ByteArray,
            apkInfo: ApkInfo,
            deviceConfig: DeviceConfig?,
            subResourceProvider: ((String) -> ByteArray?)?
    ): Brush? {
        val streamer = GradientStreamer(context, apkInfo, deviceConfig, subResourceProvider)
        val parser = BinaryXmlParser(ByteBuffer.wrap(bytes), apkInfo.resourceTable, streamer, deviceConfig)
        try {
            parser.parse()
            return streamer.brush
        } catch (e: Exception) {
            android.util.Log.d("AppLog", "icon fetching: failed to parse complex color exception: ${e.message}")
        }
        return null
    }

    private class GradientStreamer(
            private val context: Context,
            private val apkInfo: ApkInfo,
            private val deviceConfig: DeviceConfig?,
            private val subResourceProvider: ((String) -> ByteArray?)?
    ) : XmlStreamer {
        var brush: Brush? = null
        private var type: String? = null
        private var startColor: Color = Color.Transparent
        private var endColor: Color = Color.Transparent
        private var centerColor: Color? = null
        private var startX = 0f
        private var startY = 0f
        private var endX = 0f
        private var endY = 0f
        private var centerX = 0f
        private var centerY = 0f
        private var gradientRadius = 0f
        private val stops = mutableListOf<Float>()
        private val colors = mutableListOf<Color>()

        override fun onStartTag(tag: XmlNodeStartTag) {
            val attr = tag.attributes
            when (tag.name) {
                "gradient" -> {
                    type = attr.getAttr("type") ?: "linear"
                    startColor = attr.getAttr("startColor")?.let { resolveColor(context, it, apkInfo, deviceConfig, subResourceProvider) }
                            ?: Color.Transparent
                    endColor = attr.getAttr("endColor")?.let { resolveColor(context, it, apkInfo, deviceConfig, subResourceProvider) }
                            ?: Color.Transparent
                    centerColor = attr.getAttr("centerColor")?.let { resolveColor(context, it, apkInfo, deviceConfig, subResourceProvider) }
                    startX = attr.getAttr("startX")?.toFloat() ?: 0f
                    startY = attr.getAttr("startY")?.toFloat() ?: 0f
                    endX = attr.getAttr("endX")?.toFloat() ?: 0f
                    endY = attr.getAttr("endY")?.toFloat() ?: 0f
                    centerX = attr.getAttr("centerX")?.toFloat() ?: 0f
                    centerY = attr.getAttr("centerY")?.toFloat() ?: 0f
                    gradientRadius = attr.getAttr("gradientRadius")?.toFloat() ?: 0f
                }

                "item" -> {
                    val offset = attr.getAttr("offset")?.toFloat() ?: 0f
                    val colorStr = attr.getAttr("color")
                    val color = if (colorStr != null) {
                        obtainBrush(context, colorStr, apkInfo, deviceConfig, subResourceProvider)?.let {
                            if (it is SolidColor) it.value else Color.Transparent
                        } ?: Color.Transparent
                    } else Color.Transparent
                    stops.add(offset)
                    colors.add(color)
                }
                "color" -> {
                    val color = attr.getAttr("color")?.let { resolveColor(context, it, apkInfo, deviceConfig, subResourceProvider) } ?: Color.Transparent
                    brush = SolidColor(color)
                }
            }
        }

        override fun onEndTag(tag: XmlNodeEndTag) {
            if (tag.name == "gradient") {
                val finalColors = if (colors.isNotEmpty()) colors else {
                    centerColor?.let { listOf(startColor, it, endColor) }
                            ?: listOf(startColor, endColor)
                }
                val finalStops = if (stops.isNotEmpty()) stops else {
                    if (centerColor != null) listOf(0f, 0.5f, 1f) else listOf(0f, 1f)
                }

                brush = when (type) {
                    "radial" -> Brush.radialGradient(
                            colorStops = finalStops.zip(finalColors).toTypedArray(),
                            center = androidx.compose.ui.geometry.Offset(centerX, centerY),
                            radius = gradientRadius
                    )

                    "sweep" -> Brush.sweepGradient(
                            colorStops = finalStops.zip(finalColors).toTypedArray(),
                            center = androidx.compose.ui.geometry.Offset(centerX, centerY)
                    )

                    else -> Brush.linearGradient(
                            colorStops = finalStops.zip(finalColors).toTypedArray(),
                            start = androidx.compose.ui.geometry.Offset(startX, startY),
                            end = androidx.compose.ui.geometry.Offset(endX, endY)
                    )
                }
            }
        }

        override fun onCData(xmlCData: net.dongliu.apk.parser.struct.xml.XmlCData) {}
        override fun onNamespaceStart(tag: net.dongliu.apk.parser.struct.xml.XmlNamespaceStartTag) {}
        override fun onNamespaceEnd(tag: net.dongliu.apk.parser.struct.xml.XmlNamespaceEndTag) {}
    }

    private fun parseBlendMode(modeStr: String?): BlendMode = when (modeStr) {
        "src_over", "3" -> BlendMode.SrcOver
        "src_in", "5" -> BlendMode.SrcIn
        "src_atop", "9" -> BlendMode.SrcAtop
        "multiply", "14" -> BlendMode.Modulate
        "screen", "15" -> BlendMode.Screen
        "add", "16" -> BlendMode.Plus
        else -> BlendMode.SrcIn
    }

    private fun parseStrokeCap(capStr: String?): StrokeCap = when (capStr) {
        "butt", "0" -> StrokeCap.Butt
        "round", "1" -> StrokeCap.Round
        "square", "2" -> StrokeCap.Square
        else -> StrokeCap.Butt
    }

    private fun parseStrokeJoin(joinStr: String?): StrokeJoin = when (joinStr) {
        "miter", "0" -> StrokeJoin.Miter
        "round", "1" -> StrokeJoin.Round
        "bevel", "2" -> StrokeJoin.Bevel
        else -> StrokeJoin.Miter
    }
}
