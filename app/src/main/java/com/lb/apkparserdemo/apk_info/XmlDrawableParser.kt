package com.lb.apkparserdemo.apk_info

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Xml
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
import net.dongliu.apk.parser.parser.XmlTranslator
import net.dongliu.apk.parser.struct.xml.XmlNodeEndTag
import net.dongliu.apk.parser.struct.xml.XmlNodeStartTag
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.nio.ByteBuffer
import java.util.*
import kotlin.math.*

object XmlDrawableParser {

    class VectorBitmapDrawable(context: Context, bitmap: Bitmap) : BitmapDrawable(context.resources, bitmap)

    fun tryParseDrawable(context: Context, binXml: ByteArray, apkInfo: ApkInfo, deviceConfig: DeviceConfig?, subResourceProvider: ((String) -> ByteArray?)? = null): Drawable? {
        android.util.Log.d("AppLog", "icon fetching: tryParseDrawable (Binary)")
        try {
            val xmlTranslator = XmlTranslator()
            val fallbackBuffer = ByteBuffer.wrap(binXml)
            val fallbackBinaryXmlParser = BinaryXmlParser(fallbackBuffer, apkInfo.resourceTable, xmlTranslator, deviceConfig)
            fallbackBinaryXmlParser.parse()
            val xml = xmlTranslator.xml
            android.util.Log.d("AppLog", "icon fetching: XML content:\n$xml")
        } catch (e: Exception) {
            android.util.Log.d("AppLog", "icon fetching: failed to log XML content: ${e.message}")
        }

        val streamer = VectorDrawableStreamer(context, apkInfo, deviceConfig, subResourceProvider)
        val parser = BinaryXmlParser(ByteBuffer.wrap(binXml), apkInfo.resourceTable, streamer, deviceConfig)
        return try {
            parser.parse()
            if (streamer.isVector) {
                android.util.Log.d("AppLog", "icon fetching: parsed as VectorDrawable")
                streamer.imageVector?.let { imageVectorToDrawable(context, it, requestedAppIconSize = 0) } // We can pass a size here if needed
            } else {
                // Fallback to framework for non-vector drawables (layer-list, etc.)
                android.util.Log.d("AppLog", "icon fetching: not a vector, fallback to framework")
                val drawable = tryParseFrameworkDrawable(context, binXml)
                if (drawable == null) android.util.Log.d("AppLog", "icon fetching: framework fallback failed")
                else android.util.Log.d("AppLog", "icon fetching: framework fallback succeeded")
                drawable
            }
        } catch (e: Exception) {
            android.util.Log.d("AppLog", "icon fetching: exception in BinaryXmlParser: ${e.message}")
            // Last resort fallback
            tryParseFrameworkDrawable(context, binXml)
        }
    }

    fun tryParseDrawable(context: Context, xml: String): Drawable? {
        android.util.Log.d("AppLog", "icon fetching: tryParseDrawable (String XML):\n$xml")
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(StringReader(xml))
            var type = parser.next()
            while (type != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {
                type = parser.next()
            }
            if (type != XmlPullParser.START_TAG) return null

            return if (parser.name == "vector") {
                android.util.Log.d("AppLog", "icon fetching: parsed string XML as vector")
                val imageVector = parseVectorFromPullParser(context, parser)
                imageVector?.let { imageVectorToDrawable(context, it) }
            } else {
                android.util.Log.d("AppLog", "icon fetching: parsed string XML root: ${parser.name}, fallback to framework")
                val attrs = Xml.asAttributeSet(parser)
                Drawable.createFromXmlInner(context.resources, parser, attrs, context.theme)
            }
        } catch (e: Exception) {
            android.util.Log.d("AppLog", "icon fetching: exception parsing string XML: ${e.message}")
        }
        return null
    }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun tryParseFrameworkDrawable(context: Context, binXml: ByteArray): Drawable? {
        try {
            val xmlBlock = Class.forName("android.content.res.XmlBlock")
            val xmlBlockCtr = xmlBlock.getConstructor(ByteArray::class.java)
            val xmlParserNew = xmlBlock.getDeclaredMethod("newParser")
            xmlBlockCtr.isAccessible = true
            xmlParserNew.isAccessible = true
            val parser = xmlParserNew.invoke(xmlBlockCtr.newInstance(binXml)) as XmlPullParser
            var type = parser.next()
            while (type != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {
                type = parser.next()
            }
            if (type == XmlPullParser.START_TAG) {
                val attrs = Xml.asAttributeSet(parser)
                val drawable = Drawable.createFromXmlInner(context.resources, parser, attrs, context.theme)
                // if (drawable == null) android.util.Log.d("AppLog", "icon fetching: framework failed to create drawable from XML")
                return drawable
            }
        } catch (e: Exception) {
            // android.util.Log.d("AppLog", "icon fetching: framework exception: ${e.message}")
        }
        return null
    }

    private class VectorDrawableStreamer(
            private val context: Context,
            private val apkInfo: ApkInfo,
            private val deviceConfig: DeviceConfig?,
            private val subResourceProvider: ((String) -> ByteArray?)?
    ) : XmlStreamer {
        var imageVector: ImageVector? = null
        var isVector = false
        private var builder: ImageVector.Builder? = null
        private val extraGroupsStack = mutableListOf<Int>()

        override fun onStartTag(tag: XmlNodeStartTag) {
            val attr = tag.attributes
            when (tag.name) {
                "vector" -> {
                    isVector = true
                    val width = attr.getString("width")?.parseDimension() ?: 24f
                    val height = attr.getString("height")?.parseDimension() ?: 24f
                    val viewportWidth = attr.getString("viewportWidth")?.toFloat() ?: width
                    val viewportHeight = attr.getString("viewportHeight")?.toFloat() ?: height

                    builder = ImageVector.Builder(
                            name = attr.getString("name") ?: "vector",
                            defaultWidth = width.dp,
                            defaultHeight = height.dp,
                            viewportWidth = viewportWidth,
                            viewportHeight = viewportHeight,
                            tintColor = attr.getString("tint")?.let { parseColor(context, it) }
                                    ?: Color.Unspecified,
                            tintBlendMode = parseBlendMode(attr.getString("tintMode")),
                            autoMirror = attr.getBoolean("autoMirrored", false)
                    )
                    extraGroupsStack.add(0)
                }

                "group" -> {
                    builder?.addGroup(
                            name = attr.getString("name") ?: "",
                            rotate = attr.getString("rotation")?.toFloat() ?: 0f,
                            pivotX = attr.getString("pivotX")?.toFloat() ?: 0f,
                            pivotY = attr.getString("pivotY")?.toFloat() ?: 0f,
                            scaleX = attr.getString("scaleX")?.toFloat() ?: 1f,
                            scaleY = attr.getString("scaleY")?.toFloat() ?: 1f,
                            translationX = attr.getString("translateX")?.toFloat() ?: 0f,
                            translationY = attr.getString("translateY")?.toFloat() ?: 0f
                    )
                    extraGroupsStack.add(0)
                }

                "inset" -> {
                    // Just a container for modern icons, let the parser continue to child tags
                    attr.getString("drawable")?.let { innerDrawable ->
                        android.util.Log.d("AppLog", "icon fetching: inset has drawable attribute: $innerDrawable")
                        // If it's a path or resourceId, we should try to parse it
                        if (innerDrawable.endsWith(".xml")) {
                            subResourceProvider?.invoke(innerDrawable)?.let { innerBytes ->
                                val subStreamer = VectorDrawableStreamer(context, apkInfo, deviceConfig, subResourceProvider)
                                val subParser = BinaryXmlParser(ByteBuffer.wrap(innerBytes), apkInfo.resourceTable, subStreamer, deviceConfig)
                                try {
                                    subParser.parse()
                                    subStreamer.imageVector?.let { subVector ->
                                        builder?.addPath(
                                                pathData = subVector.root.map { if (it is VectorPath) it.pathData else emptyList() }.flatten(),
                                                name = "inset_sub",
                                                fill = SolidColor(Color.Transparent) // This is a hack, proper nested vector support is complex
                                        )
                                    }
                                } catch (ignored: Exception) {
                                }
                            }
                        }
                    }
                }

                "path" -> {
                    val pathData = attr.getString("pathData") ?: return
                    builder?.addPath(
                            pathData = addPathNodes(pathData),
                            name = attr.getString("name") ?: "",
                            fill = attr.getString("fillColor")?.let { obtainBrush(context, it, apkInfo, deviceConfig, subResourceProvider) },
                            fillAlpha = attr.getString("fillAlpha")?.toFloat() ?: 1f,
                            stroke = attr.getString("strokeColor")?.let { obtainBrush(context, it, apkInfo, deviceConfig, subResourceProvider) },
                            strokeAlpha = attr.getString("strokeAlpha")?.toFloat() ?: 1f,
                            strokeLineWidth = attr.getString("strokeWidth")?.toFloat() ?: 0f,
                            strokeLineCap = parseStrokeCap(attr.getString("strokeLineCap")),
                            strokeLineJoin = parseStrokeJoin(attr.getString("strokeLineJoin")),
                            strokeLineMiter = attr.getString("strokeMiterLimit")?.toFloat() ?: 4f,
                            pathFillType = if (attr.getString("fillType") == "evenOdd" || attr.getString("fillType") == "1") PathFillType.EvenOdd else PathFillType.NonZero
                    )
                }

                "clip-path" -> {
                    val pathData = attr.getString("pathData") ?: return
                    builder?.addGroup(
                            name = attr.getString("name") ?: "",
                            clipPathData = addPathNodes(pathData)
                    )
                    if (extraGroupsStack.isNotEmpty()) {
                        extraGroupsStack[extraGroupsStack.size - 1]++
                    }
                }
            }
        }

        override fun onEndTag(tag: XmlNodeEndTag) {
            when (tag.name) {
                "vector" -> {
                    if (extraGroupsStack.isNotEmpty()) {
                        val extras = extraGroupsStack.removeAt(extraGroupsStack.size - 1)
                        repeat(extras) { builder?.clearGroup() }
                    }
                    imageVector = builder?.build()
                }

                "group" -> {
                    if (extraGroupsStack.isNotEmpty()) {
                        val extras = extraGroupsStack.removeAt(extraGroupsStack.size - 1)
                        repeat(extras + 1) { builder?.clearGroup() }
                    }
                }
            }
        }

        override fun onCData(xmlCData: net.dongliu.apk.parser.struct.xml.XmlCData) {}
        override fun onNamespaceStart(tag: net.dongliu.apk.parser.struct.xml.XmlNamespaceStartTag) {}
        override fun onNamespaceEnd(tag: net.dongliu.apk.parser.struct.xml.XmlNamespaceEndTag) {}
    }

    private fun parseVectorFromPullParser(context: Context, parser: XmlPullParser): ImageVector? {
        val ns = "http://schemas.android.com/apk/res/android"
        val width = parser.getAttributeValue(ns, "width")?.parseDimension() ?: 24f
        val height = parser.getAttributeValue(ns, "height")?.parseDimension() ?: 24f
        val viewportWidth = parser.getAttributeValue(ns, "viewportWidth")?.toFloat() ?: width
        val viewportHeight = parser.getAttributeValue(ns, "viewportHeight")?.toFloat() ?: height

        val builder = ImageVector.Builder(
                name = parser.getAttributeValue(ns, "name") ?: "vector",
                defaultWidth = width.dp,
                defaultHeight = height.dp,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                tintColor = parser.getAttributeValue(ns, "tint")?.let { parseColor(context, it) }
                        ?: Color.Unspecified,
                tintBlendMode = parseBlendMode(parser.getAttributeValue(ns, "tintMode")),
                autoMirror = parser.getAttributeValue(ns, "autoMirrored")?.toBoolean() ?: false
        )

        val extraGroupsStack = mutableListOf<Int>()
        extraGroupsStack.add(0)

        var eventType = parser.next()
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "group" -> {
                        builder.addGroup(
                                name = parser.getAttributeValue(ns, "name") ?: "",
                                rotate = parser.getAttributeValue(ns, "rotation")?.toFloat() ?: 0f,
                                pivotX = parser.getAttributeValue(ns, "pivotX")?.toFloat() ?: 0f,
                                pivotY = parser.getAttributeValue(ns, "pivotY")?.toFloat() ?: 0f,
                                scaleX = parser.getAttributeValue(ns, "scaleX")?.toFloat() ?: 1f,
                                scaleY = parser.getAttributeValue(ns, "scaleY")?.toFloat() ?: 1f,
                                translationX = parser.getAttributeValue(ns, "translateX")?.toFloat()
                                        ?: 0f,
                                translationY = parser.getAttributeValue(ns, "translateY")?.toFloat()
                                        ?: 0f
                        )
                        extraGroupsStack.add(0)
                    }

                    "path" -> {
                        val pathData = parser.getAttributeValue(ns, "pathData")
                        if (pathData != null) {
                            builder.addPath(
                                    pathData = addPathNodes(pathData),
                                    name = parser.getAttributeValue(ns, "name") ?: "",
                                    fill = parser.getAttributeValue(ns, "fillColor")?.let { obtainBrush(context, it) },
                                    fillAlpha = parser.getAttributeValue(ns, "fillAlpha")?.toFloat()
                                            ?: 1f,
                                    stroke = parser.getAttributeValue(ns, "strokeColor")?.let { obtainBrush(context, it) },
                                    strokeAlpha = parser.getAttributeValue(ns, "strokeAlpha")?.toFloat()
                                            ?: 1f,
                                    strokeLineWidth = parser.getAttributeValue(ns, "strokeWidth")?.toFloat()
                                            ?: 0f,
                                    strokeLineCap = parseStrokeCap(parser.getAttributeValue(ns, "strokeLineCap")),
                                    strokeLineJoin = parseStrokeJoin(parser.getAttributeValue(ns, "strokeLineJoin")),
                                    strokeLineMiter = parser.getAttributeValue(ns, "strokeMiterLimit")?.toFloat()
                                            ?: 4f,
                                    pathFillType = if (parser.getAttributeValue(ns, "fillType") == "evenOdd") PathFillType.EvenOdd else PathFillType.NonZero
                            )
                        }
                    }

                    "clip-path" -> {
                        val pathData = parser.getAttributeValue(ns, "pathData")
                        if (pathData != null) {
                            builder.addGroup(clipPathData = addPathNodes(pathData))
                            if (extraGroupsStack.isNotEmpty()) {
                                extraGroupsStack[extraGroupsStack.size - 1]++
                            }
                        }
                    }
                }
            } else if (eventType == XmlPullParser.END_TAG) {
                if (parser.name == "vector") break
                if (parser.name == "group") {
                    if (extraGroupsStack.isNotEmpty()) {
                        val extras = extraGroupsStack.removeAt(extraGroupsStack.size - 1)
                        repeat(extras + 1) { builder.clearGroup() }
                    }
                }
            }
            eventType = parser.next()
        }
        return builder.build()
    }

    private fun imageVectorToDrawable(context: Context, imageVector: ImageVector, requestedAppIconSize: Int = 0): Drawable {
        val density = Density(context.resources.displayMetrics.density)
        val widthPx = if (requestedAppIconSize > 0) requestedAppIconSize else with(density) { imageVector.defaultWidth.toPx() }.toInt().coerceAtLeast(1)
        val heightPx = if (requestedAppIconSize > 0) requestedAppIconSize else with(density) { imageVector.defaultHeight.toPx() }.toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(android.graphics.Canvas(bitmap))
        val drawScope = CanvasDrawScope()
        drawScope.draw(density, LayoutDirection.Ltr, canvas, androidx.compose.ui.geometry.Size(widthPx.toFloat(), heightPx.toFloat())) {
            withTransform({
                scale(
                        scaleX = widthPx.toFloat() / imageVector.viewportWidth,
                        scaleY = heightPx.toFloat() / imageVector.viewportHeight,
                        pivot = androidx.compose.ui.geometry.Offset.Zero
                )
            }) {
                renderVectorGroup(imageVector.root)
            }
        }
        return VectorBitmapDrawable(context, bitmap)
    }

    private fun androidx.compose.ui.graphics.drawscope.DrawScope.renderVectorGroup(group: VectorGroup) {
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
                                style = Fill
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
                                    )
                            )
                        }
                    }

                    is VectorGroup -> renderVectorGroup(node)
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

        var radicand = (rxSq * rySq - rxSq * y1pSq - rySq * x1pSq) / (rxSq * y1pSq + rySq * x1pSq)
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

            // Rotate and translate
            path.cubicTo(
                    (cosTheta * x2 - sinTheta * y2 + cx).toFloat(), (sinTheta * x2 + cosTheta * y2 + cy).toFloat(),
                    (cosTheta * x3 - sinTheta * y3 + cx).toFloat(), (sinTheta * x3 + cosTheta * y3 + cy).toFloat(),
                    (cosTheta * bx - sinTheta * by + cx).toFloat(), (sinTheta * bx + cosTheta * by + cy).toFloat()
            )
            angle += segmentDelta
        }
    }

    private fun addPathNodes(pathData: String): List<PathNode> = androidx.compose.ui.graphics.vector.addPathNodes(pathData)

    private fun String.parseDimension(): Float = filter { it.isDigit() || it == '.' || it == '-' }.toFloatOrNull()
            ?: 0f

    private fun parseColor(context: Context, colorStr: String): Color {
        return try {
            if (colorStr.startsWith("#")) {
                Color(android.graphics.Color.parseColor(colorStr))
            } else if (colorStr.startsWith("resourceId:")) {
                val resId = colorStr.substringAfter("0x").toLong(16).toInt()
                if ((resId shr 24) == 0x01) {
                    val color = androidx.core.content.res.ResourcesCompat.getColor(context.resources, resId, null)
                    Color(color)
                } else Color.Transparent
            } else Color.Transparent
        } catch (e: Exception) {
            Color.Transparent
        }
    }

    private fun obtainBrush(
            context: Context,
            colorStr: String,
            apkInfo: ApkInfo? = null,
            deviceConfig: DeviceConfig? = null,
            subResourceProvider: ((String) -> ByteArray?)? = null
    ): Brush? {
        val color = parseColor(context, colorStr)
        if (color != Color.Transparent) return SolidColor(color)

        if (colorStr.endsWith(".xml") && subResourceProvider != null && apkInfo != null) {
            android.util.Log.d("AppLog", "icon fetching: attempting to parse complex color: $colorStr")
            val bytes = subResourceProvider(colorStr)
            if (bytes != null) {
                return tryParseComplexColor(context, bytes, apkInfo, deviceConfig, subResourceProvider)
            } else {
                android.util.Log.d("AppLog", "icon fetching: subResourceProvider returned null for $colorStr")
            }
        }
        return null
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
            if (streamer.brush != null) {
                android.util.Log.d("AppLog", "icon fetching: successfully parsed complex color")
            } else {
                android.util.Log.d("AppLog", "icon fetching: failed to parse complex color (brush is null)")
            }
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
                    type = attr.getString("type") ?: "linear"
                    startColor = attr.getString("startColor")?.let { parseColor(context, it) }
                            ?: Color.Transparent
                    endColor = attr.getString("endColor")?.let { parseColor(context, it) }
                            ?: Color.Transparent
                    centerColor = attr.getString("centerColor")?.let { parseColor(context, it) }
                    startX = attr.getString("startX")?.toFloat() ?: 0f
                    startY = attr.getString("startY")?.toFloat() ?: 0f
                    endX = attr.getString("endX")?.toFloat() ?: 0f
                    endY = attr.getString("endY")?.toFloat() ?: 0f
                    centerX = attr.getString("centerX")?.toFloat() ?: 0f
                    centerY = attr.getString("centerY")?.toFloat() ?: 0f
                    gradientRadius = attr.getString("gradientRadius")?.toFloat() ?: 0f
                }

                "item" -> {
                    val offset = attr.getString("offset")?.toFloat() ?: 0f
                    val colorStr = attr.getString("color")
                    val color = if (colorStr != null) {
                        obtainBrush(context, colorStr, apkInfo, deviceConfig, subResourceProvider)?.let {
                            if (it is SolidColor) it.value else Color.Transparent
                        } ?: Color.Transparent
                    } else Color.Transparent
                    stops.add(offset)
                    colors.add(color)
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
        "src_over" -> BlendMode.SrcOver
        "src_in" -> BlendMode.SrcIn
        "src_atop" -> BlendMode.SrcAtop
        "multiply" -> BlendMode.Modulate
        "screen" -> BlendMode.Screen
        "add" -> BlendMode.Plus
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
