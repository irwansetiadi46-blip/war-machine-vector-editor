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

    /**
     * Parses standard SVG length values (e.g. "4000", "4000px", "4000pt", "100mm", "10in") into pixels.
     * 1 inch = 96 px, 1 pt = 96/72 px = 1.3333 px, 1 mm = 96/25.4 px = 3.7795 px, 1 cm = 37.795 px.
     */
    private fun parseSvgLengthToPixels(raw: String): Float? {
        val s = raw.trim().lowercase(java.util.Locale.ROOT)
        if (s.isEmpty() || s.endsWith("%")) return null
        return try {
            when {
                s.endsWith("px") -> s.removeSuffix("px").trim().toFloatOrNull()
                s.endsWith("pt") -> s.removeSuffix("pt").trim().toFloatOrNull()?.let { it * (96f / 72f) }
                s.endsWith("in") -> s.removeSuffix("in").trim().toFloatOrNull()?.let { it * 96f }
                s.endsWith("mm") -> s.removeSuffix("mm").trim().toFloatOrNull()?.let { it * (96f / 25.4f) }
                s.endsWith("cm") -> s.removeSuffix("cm").trim().toFloatOrNull()?.let { it * (96f / 2.54f) }
                s.endsWith("pc") -> s.removeSuffix("pc").trim().toFloatOrNull()?.let { it * 16f }
                else -> s.replace(Regex("[^0-9.]"), "").toFloatOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }

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

            // 1. Try explicit width and height attributes (supports px, pt, mm, in, etc.)
            val wAttr = root.getAttribute("width").trim()
            val hAttr = root.getAttribute("height").trim()

            var w: Float? = parseSvgLengthToPixels(wAttr)
            var h: Float? = parseSvgLengthToPixels(hAttr)

            // 2. Try viewBox if width or height missing or <= 0
            val viewBox = root.getAttribute("viewBox").trim()
            if (viewBox.isNotEmpty()) {
                val tokens = viewBox.split(Regex("""[\s,]+""")).mapNotNull { it.toFloatOrNull() }
                if (tokens.size >= 4 && tokens[2] > 0f && tokens[3] > 0f) {
                    val vbWidth = tokens[2]
                    val vbHeight = tokens[3]
                    // If width/height were not specified or 100%, viewBox represents the true SVG artboard coordinate system
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

        // Fallback using AndroidSVG native document bounds
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

                // Target Dimensions matching the SVG artboard dimensions & aspect ratio:
                // 1. If SVG has explicit artboard size >= 1000px (e.g. 4000x4000, 3000x2000, 5000x5000),
                //    use the EXACT native artboard dimensions so the JPG matches the SVG 1:1 in width & height.
                // 2. If SVG artboard is smaller than 1000px (e.g. small icon 100x100 or viewBox 500x500),
                //    scale up proportionally to targetLongEdge while strictly maintaining the EXACT aspect ratio.
                val maxNativeDim = maxOf(docWidth, docHeight)
                val (targetWidth, targetHeight) = if (maxNativeDim >= 1000f) {
                    val w = docWidth.roundToInt().coerceAtLeast(100)
                    val h = docHeight.roundToInt().coerceAtLeast(100)
                    Pair(w, h)
                } else {
                    val longEdge = targetLongEdge.coerceIn(2000, 4000)
                    if (aspectRatio >= 1.0f) {
                        Pair(longEdge, (longEdge / aspectRatio).roundToInt().coerceAtLeast(100))
                    } else {
                        Pair((longEdge * aspectRatio).roundToInt().coerceAtLeast(100), longEdge)
                    }
                }

                // Allocate bitmap safely with progressive fallback if device memory is constrained
                var bitmap: Bitmap? = try {
                    Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                } catch (oom: OutOfMemoryError) {
                    System.gc()
                    try {
                        // Try RGB_565 (uses 50% less RAM, perfect for opaque JPEG preview)
                        Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.RGB_565)
                    } catch (oom2: OutOfMemoryError) {
                        System.gc()
                        val fallbackEdge = 3000
                        val (fw, fh) = if (aspectRatio >= 1.0f) {
                            Pair(fallbackEdge, (fallbackEdge / aspectRatio).roundToInt().coerceAtLeast(100))
                        } else {
                            Pair((fallbackEdge * aspectRatio).roundToInt().coerceAtLeast(100), fallbackEdge)
                        }
                        try {
                            Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888)
                        } catch (oom3: OutOfMemoryError) {
                            System.gc()
                            try {
                                Bitmap.createBitmap(fw, fh, Bitmap.Config.RGB_565)
                            } catch (_: Throwable) {
                                null
                            }
                        }
                    }
                }

                if (bitmap == null) return@withContext null

                val canvas = Canvas(bitmap)
                // Fill clean pure white background (Microstock standard for JPEG preview)
                canvas.drawColor(Color.WHITE)

                // Ensure SVG document viewbox covers the artboard
                if (svg.documentViewBox == null) {
                    svg.setDocumentViewBox(0f, 0f, docWidth, docHeight)
                }
                svg.setDocumentWidth("100%")
                svg.setDocumentHeight("100%")

                val renderOptions = RenderOptions.create()
                renderOptions.viewPort(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
                svg.renderToCanvas(canvas, renderOptions)

                val outputStream = ByteArrayOutputStream()
                // 100% maximum quality for perfect clarity and sharpness
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
