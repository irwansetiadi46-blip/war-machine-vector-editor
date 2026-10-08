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

    fun getSvgAspectRatio(svgBytes: ByteArray): Float {
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            factory.isValidating = false
            try {
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            } catch (_: Exception) {}
            val doc = factory.newDocumentBuilder().parse(ByteArrayInputStream(svgBytes))
            val root = doc.documentElement

            val viewBox = root.getAttribute("viewBox").trim()
            if (viewBox.isNotEmpty()) {
                val tokens = viewBox.split(Regex("""[\s,]+""")).mapNotNull { it.toFloatOrNull() }
                if (tokens.size >= 4 && tokens[2] > 0f && tokens[3] > 0f) {
                    return tokens[2] / tokens[3]
                }
            }
            val wStr = root.getAttribute("width").trim().replace(Regex("[^0-9.]"), "").toFloatOrNull()
            val hStr = root.getAttribute("height").trim().replace(Regex("[^0-9.]"), "").toFloatOrNull()
            if (wStr != null && hStr != null && wStr > 0f && hStr > 0f) {
                return wStr / hStr
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 1.0f
    }

    /**
     * Converts SVG bytes to high-resolution JPEG bytes using AndroidSVG native vector rendering.
     * Dimensions are proportional to the SVG file (high resolution Microstock preview standard),
     * rendered on a clean white background with exact vector accuracy.
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

                var docWidth = svg.documentWidth
                var docHeight = svg.documentHeight
                val viewBox = svg.documentViewBox

                if ((docWidth <= 0f || docHeight <= 0f) && viewBox != null && viewBox.width() > 0f && viewBox.height() > 0f) {
                    docWidth = viewBox.width()
                    docHeight = viewBox.height()
                }

                if (docWidth <= 0f || docHeight <= 0f) {
                    val ratio = getSvgAspectRatio(svgBytes)
                    docWidth = 3000f
                    docHeight = (3000f / ratio).coerceAtLeast(100f)
                }

                val aspectRatio = if (docWidth > 0f && docHeight > 0f) {
                    docWidth / docHeight
                } else {
                    getSvgAspectRatio(svgBytes)
                }

                // If native dimensions are high resolution (between 2000 and 5000), use native dimensions.
                // If native dimensions are small (< 2000), scale up to high-res preview standard.
                // If native dimensions are huge (> 5000), scale down to prevent OOM.
                val maxNativeDim = maxOf(docWidth, docHeight)
                val effectiveLongEdge = when {
                    maxNativeDim in 2000f..5000f -> maxNativeDim.toInt()
                    maxNativeDim > 5000f -> 5000
                    else -> targetLongEdge.coerceIn(2500, 4500)
                }

                val (targetWidth, targetHeight) = if (aspectRatio >= 1.0f) {
                    Pair(effectiveLongEdge, (effectiveLongEdge / aspectRatio).roundToInt().coerceAtLeast(100))
                } else {
                    Pair((effectiveLongEdge * aspectRatio).roundToInt().coerceAtLeast(100), effectiveLongEdge)
                }

                var bitmap: Bitmap? = try {
                    Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                } catch (oom: OutOfMemoryError) {
                    System.gc()
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
                // Fill clean white background (Microstock standard for JPEG preview)
                canvas.drawColor(Color.WHITE)

                // Ensure SVG scales to fit the canvas viewport
                if (svg.documentViewBox == null) {
                    svg.setDocumentViewBox(0f, 0f, docWidth, docHeight)
                }
                svg.setDocumentWidth("100%")
                svg.setDocumentHeight("100%")

                val renderOptions = RenderOptions.create()
                renderOptions.viewPort(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
                svg.renderToCanvas(canvas, renderOptions)

                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
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

                if (docWidth <= 0f || docHeight <= 0f) {
                    docWidth = 512f
                    docHeight = 512f
                }

                val size = 512
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)

                if (svg.documentViewBox == null) {
                    svg.setDocumentViewBox(0f, 0f, docWidth, docHeight)
                }
                svg.setDocumentWidth("100%")
                svg.setDocumentHeight("100%")

                val renderOptions = RenderOptions.create()
                renderOptions.viewPort(0f, 0f, size.toFloat(), size.toFloat())
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
