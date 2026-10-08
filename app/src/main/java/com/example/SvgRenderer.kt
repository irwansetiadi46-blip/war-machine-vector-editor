package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.caverock.androidsvg.RenderOptions
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt

object SvgRenderer {

    data class SvgDimension(val width: Float, val height: Float)

    fun getSvgDimensions(svgBytes: ByteArray): SvgDimension {
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            factory.isValidating = false
            try {
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            } catch (_: Exception) {}
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(svgBytes))
            val root = doc.documentElement

            // 1. Try explicit width and height attributes
            val wAttr = root.getAttribute("width").trim()
            val hAttr = root.getAttribute("height").trim()

            var w: Float? = if (!wAttr.endsWith("%")) {
                wAttr.replace(Regex("[^0-9.]"), "").toFloatOrNull()
            } else null

            var h: Float? = if (!hAttr.endsWith("%")) {
                hAttr.replace(Regex("[^0-9.]"), "").toFloatOrNull()
            } else null

            // 2. Try viewBox if width or height missing or 0
            val viewBox = root.getAttribute("viewBox").trim()
            if (viewBox.isNotEmpty()) {
                val tokens = viewBox.split(Regex("""[\s,]+""")).mapNotNull { it.toFloatOrNull() }
                if (tokens.size >= 4 && tokens[2] > 0f && tokens[3] > 0f) {
                    val vbWidth = tokens[2]
                    val vbHeight = tokens[3]
                    if (w == null || w <= 0f) w = vbWidth
                    if (h == null || h <= 0f) h = vbHeight
                }
            }

            if (w != null && h != null && w > 0f && h > 0f) {
                return SvgDimension(w, h)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback using AndroidSVG parser
        try {
            val svg = try {
                SVG.getFromInputStream(ByteArrayInputStream(svgBytes))
            } catch (_: Exception) {
                SVG.getFromString(String(svgBytes, Charsets.UTF_8))
            }
            var docWidth = svg.documentWidth
            var docHeight = svg.documentHeight
            val viewBox = svg.documentViewBox
            if ((docWidth <= 0f || docHeight <= 0f) && viewBox != null && viewBox.width() > 0f && viewBox.height() > 0f) {
                docWidth = viewBox.width()
                docHeight = viewBox.height()
            }
            if (docWidth > 0f && docHeight > 0f) {
                return SvgDimension(docWidth, docHeight)
            }
        } catch (_: Exception) {}

        return SvgDimension(4000f, 4000f)
    }

    fun getSvgAspectRatio(svgBytes: ByteArray): Float {
        val dim = getSvgDimensions(svgBytes)
        if (dim.height > 0f) {
            return dim.width / dim.height
        }
        return 1.0f
    }

    /**
     * Converts SVG bytes to high-resolution JPEG bytes using AndroidSVG native vector rendering.
     * Dimensions match the EXACT artboard size of the SVG file (e.g. 4000x4000 -> 4000x4000 JPG).
     * If the SVG artboard is very small (e.g. 100x100 icons), it scales up proportionally to high-resolution
     * standard (at least 2000px up to 4000px) so the preview JPG is never blurry or low quality.
     * Rendered on a clean pure white background with exact vector sharpness and highest JPEG quality.
     */
    suspend fun renderSvgToHighResJpgBytes(
        context: Context,
        svgBytes: ByteArray,
        targetLongEdge: Int = 4000
    ): ByteArray? {
        return withContext(Dispatchers.Default) {
            try {
                val svg = try {
                    SVG.getFromInputStream(ByteArrayInputStream(svgBytes))
                } catch (e: Exception) {
                    SVG.getFromString(String(svgBytes, Charsets.UTF_8))
                }

                val exactDim = getSvgDimensions(svgBytes)
                var docWidth = exactDim.width
                var docHeight = exactDim.height

                val aspectRatio = if (docHeight > 0f) docWidth / docHeight else 1.0f

                // Determine target dimensions:
                // If SVG has explicit artboard size >= 1000px (e.g. 4000x4000, 3000x2000, 5000x5000),
                // use EXACT dimensions so JPG matches SVG artboard 100% accurately.
                // If SVG artboard is tiny (e.g. 24x24, 100x100, 500x500), upscale proportionally
                // to targetLongEdge (4000px) so preview JPEG is crisp and suitable for Microstock.
                val maxNativeDim = maxOf(docWidth, docHeight)
                val (targetWidth, targetHeight) = if (maxNativeDim >= 1000f) {
                    // Exact native artboard size
                    val w = docWidth.roundToInt().coerceAtLeast(100)
                    val h = docHeight.roundToInt().coerceAtLeast(100)
                    Pair(w, h)
                } else {
                    // Small artboard: scale up proportionally to targetLongEdge
                    val longEdge = targetLongEdge.coerceIn(2000, 4000)
                    if (aspectRatio >= 1.0f) {
                        Pair(longEdge, (longEdge / aspectRatio).roundToInt().coerceAtLeast(100))
                    } else {
                        Pair((longEdge * aspectRatio).roundToInt().coerceAtLeast(100), longEdge)
                    }
                }

                var bitmap: Bitmap? = try {
                    Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                } catch (oom: OutOfMemoryError) {
                    System.gc()
                    // If device is extremely low on memory, fallback to a safe scale
                    val fallbackEdge = 2500
                    val (fw, fh) = if (aspectRatio >= 1.0f) {
                        Pair(fallbackEdge, (fallbackEdge / aspectRatio).roundToInt().coerceAtLeast(100))
                    } else {
                        Pair((fallbackEdge * aspectRatio).roundToInt().coerceAtLeast(100), fallbackEdge)
                    }
                    Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888)
                }

                if (bitmap == null) return@withContext null

                val canvas = Canvas(bitmap)
                // Fill clean pure white background (Microstock standard for JPEG preview)
                canvas.drawColor(Color.WHITE)

                // Ensure SVG scales to fit the canvas viewport accurately
                if (svg.documentViewBox == null) {
                    svg.setDocumentViewBox(0f, 0f, docWidth, docHeight)
                }
                svg.setDocumentWidth("100%")
                svg.setDocumentHeight("100%")

                val renderOptions = RenderOptions.create()
                renderOptions.viewPort(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
                svg.renderToCanvas(canvas, renderOptions)

                val outputStream = ByteArrayOutputStream()
                // Maximum 100% quality for perfect fidelity and sharpness
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                val rawJpgBytes = outputStream.toByteArray()

                bitmap.recycle()
                rawJpgBytes
            } catch (t: Throwable) {
                t.printStackTrace()
                null
            }
        }
    }

    /**
     * Converts SVG bytes to PNG Base64 for fast thumbnail / preview rendering in UI.
     */
    suspend fun renderSvgToPngBase64(context: Context, svgBytes: ByteArray): String? {
        return withContext(Dispatchers.Default) {
            try {
                val svg = try {
                    SVG.getFromInputStream(ByteArrayInputStream(svgBytes))
                } catch (e: Exception) {
                    SVG.getFromString(String(svgBytes, Charsets.UTF_8))
                }

                var docWidth = svg.documentWidth
                var docHeight = svg.documentHeight
                val viewBox = svg.documentViewBox

                if ((docWidth <= 0f || docHeight <= 0f) && viewBox != null && viewBox.width() > 0f && viewBox.height() > 0f) {
                    docWidth = viewBox.width()
                    docHeight = viewBox.height()
                }

                val ratio = if (docWidth > 0f && docHeight > 0f) {
                    docWidth / docHeight
                } else {
                    getSvgAspectRatio(svgBytes)
                }

                val targetLongEdge = 800
                val (targetW, targetH) = if (ratio >= 1.0f) {
                    Pair(targetLongEdge, (targetLongEdge / ratio).roundToInt().coerceIn(64, targetLongEdge))
                } else {
                    Pair((targetLongEdge * ratio).roundToInt().coerceIn(64, targetLongEdge), targetLongEdge)
                }

                val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)

                if (svg.documentViewBox == null) {
                    svg.setDocumentViewBox(0f, 0f, docWidth, docHeight)
                }
                svg.setDocumentWidth("100%")
                svg.setDocumentHeight("100%")

                val renderOptions = RenderOptions.create()
                renderOptions.viewPort(0f, 0f, targetW.toFloat(), targetH.toFloat())
                svg.renderToCanvas(canvas, renderOptions)

                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                val pngBytes = outputStream.toByteArray()
                bitmap.recycle()
                android.util.Base64.encodeToString(pngBytes, android.util.Base64.NO_WRAP)
            } catch (t: Throwable) {
                t.printStackTrace()
                null
            }
        }
    }
}
