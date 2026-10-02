package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.Inflater
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object EpsRenderer {

    fun renderEpsToJpegBase64(context: Context, epsBytes: ByteArray, maxPreviewSize: Int = 512): String? {
        try {
            // 1. Try to extract embedded preview (TIFF / JPEG / PNG in DOS EPS header or EPS stream)
            val embeddedBitmap = extractEmbeddedBitmap(epsBytes)
            if (embeddedBitmap != null) {
                return compressAndEncode(embeddedBitmap, maxPreviewSize)
            }

            // 2. Parse ASCII / Hex preview if present (%%BeginPreview:)
            val asciiPreviewBitmap = extractAsciiPreviewBitmap(epsBytes)
            if (asciiPreviewBitmap != null) {
                return compressAndEncode(asciiPreviewBitmap, maxPreviewSize)
            }

            // 3. Fallback to advanced vector PostScript & Adobe Illustrator parser with full gradient support
            val epsText = String(epsBytes, StandardCharsets.ISO_8859_1)
            val vectorBitmap = renderVectorEps(epsText, maxPreviewSize) ?: return null
            return compressAndEncode(vectorBitmap, maxPreviewSize)

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun compressAndEncode(bitmap: Bitmap, maxPreviewSize: Int): String {
        val scaledBitmap = if (bitmap.width > maxPreviewSize || bitmap.height > maxPreviewSize) {
            val scale = maxPreviewSize.toFloat() / max(bitmap.width, bitmap.height)
            val sw = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val sh = (bitmap.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)
        val previewBytes = outputStream.toByteArray()

        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }
        bitmap.recycle()

        return Base64.encodeToString(previewBytes, Base64.NO_WRAP)
    }

    // =========================================================================
    // 1. EMBEDDED BITMAP & TIFF EXTRACTION
    // =========================================================================

    private fun extractEmbeddedBitmap(epsBytes: ByteArray): Bitmap? {
        if (epsBytes.size < 32) return null

        // A. DOS EPS Binary Header (Magic: 0xC5D0D3C6)
        val b0 = epsBytes[0].toInt() and 0xFF
        val b1 = epsBytes[1].toInt() and 0xFF
        val b2 = epsBytes[2].toInt() and 0xFF
        val b3 = epsBytes[3].toInt() and 0xFF

        if (b0 == 0xC5 && b1 == 0xD0 && b2 == 0xD3 && b3 == 0xC6) {
            // TIFF offset at 20..23, length at 24..27
            val tiffOffset = getUInt32LE(epsBytes, 20)
            val tiffLength = getUInt32LE(epsBytes, 24)
            if (tiffOffset > 0 && tiffLength > 0 && tiffOffset + tiffLength <= epsBytes.size) {
                // Try pure-Kotlin TIFF decoder first
                val tiffBmp = TiffDecoder.decodeTiff(epsBytes, tiffOffset, tiffLength)
                if (tiffBmp != null) return tiffBmp

                // Fallback to standard BitmapFactory
                try {
                    val bmp = BitmapFactory.decodeByteArray(epsBytes, tiffOffset, tiffLength)
                    if (bmp != null) return bmp
                } catch (_: Exception) {}
            }

            // WMF offset at 12..15, length at 16..19
            val wmfOffset = getUInt32LE(epsBytes, 12)
            val wmfLength = getUInt32LE(epsBytes, 16)
            if (wmfOffset > 0 && wmfLength > 0 && wmfOffset + wmfLength <= epsBytes.size) {
                try {
                    val bmp = BitmapFactory.decodeByteArray(epsBytes, wmfOffset, wmfLength)
                    if (bmp != null) return bmp
                } catch (_: Exception) {}
            }
        }

        // B. Search for JPEG magic bytes FF D8 FF in raw bytes
        val jpegStart = findSequence(epsBytes, byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()))
        if (jpegStart >= 0) {
            val jpegEnd = findSequence(epsBytes, byteArrayOf(0xFF.toByte(), 0xD9.toByte()), jpegStart)
            if (jpegEnd > jpegStart) {
                val length = (jpegEnd + 2) - jpegStart
                try {
                    val bmp = BitmapFactory.decodeByteArray(epsBytes, jpegStart, length)
                    if (bmp != null) return bmp
                } catch (_: Exception) {}
            }
        }

        // C. Search for PNG magic bytes 89 50 4E 47 0D 0A 1A 0A
        val pngMagic = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte())
        val pngStart = findSequence(epsBytes, pngMagic)
        if (pngStart >= 0) {
            val pngIend = byteArrayOf('I'.code.toByte(), 'E'.code.toByte(), 'N'.code.toByte(), 'D'.code.toByte())
            val iendPos = findSequence(epsBytes, pngIend, pngStart)
            if (iendPos > pngStart) {
                val length = (iendPos + 8) - pngStart
                try {
                    val bmp = BitmapFactory.decodeByteArray(epsBytes, pngStart, length)
                    if (bmp != null) return bmp
                } catch (_: Exception) {}
            }
        }

        return null
    }

    private fun extractAsciiPreviewBitmap(epsBytes: ByteArray): Bitmap? {
        try {
            val text = String(epsBytes.copyOfRange(0, min(epsBytes.size, 100_000)), StandardCharsets.ISO_8859_1)
            val previewMatch = Regex("""%%BeginPreview:\s*(\d+)\s+(\d+)\s+(\d+)\s+(\d+)""").find(text) ?: return null
            val width = previewMatch.groupValues[1].toIntOrNull() ?: return null
            val height = previewMatch.groupValues[2].toIntOrNull() ?: return null
            val depth = previewMatch.groupValues[3].toIntOrNull() ?: return null
            val linesCount = previewMatch.groupValues[4].toIntOrNull() ?: return null

            if (width <= 0 || height <= 0 || (depth != 1 && depth != 8)) return null

            val startPos = previewMatch.range.last + 1
            val endPos = text.indexOf("%%EndPreview", startPos)
            if (endPos <= startPos) return null

            val hexSection = text.substring(startPos, endPos)
            val hexClean = hexSection.replace(Regex("""[%#\s\r\n]"""), "")
            val hexBytes = hexStringToByteArray(hexClean)

            if (hexBytes.isEmpty()) return null

            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val pixels = IntArray(width * height)

            if (depth == 1) {
                val bytesPerRow = (width + 7) / 8
                for (y in 0 until height) {
                    val rowStart = y * bytesPerRow
                    for (x in 0 until width) {
                        val byteIdx = rowStart + (x / 8)
                        if (byteIdx < hexBytes.size) {
                            val b = hexBytes[byteIdx].toInt() and 0xFF
                            val bit = (b ushr (7 - (x % 8))) and 1
                            pixels[y * width + x] = if (bit == 1) Color.BLACK else Color.WHITE
                        }
                    }
                }
            } else if (depth == 8) {
                for (i in 0 until min(pixels.size, hexBytes.size)) {
                    val gray = hexBytes[i].toInt() and 0xFF
                    pixels[i] = Color.rgb(gray, gray, gray)
                }
            }

            bmp.setPixels(pixels, 0, width, 0, 0, width, height)
            return bmp
        } catch (_: Exception) {
            return null
        }
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        var out = 0
        while (i < len - 1) {
            val d1 = Character.digit(s[i], 16)
            val d2 = Character.digit(s[i + 1], 16)
            if (d1 != -1 && d2 != -1) {
                data[out++] = ((d1 shl 4) + d2).toByte()
            }
            i += 2
        }
        return if (out == data.size) data else data.copyOf(out)
    }

    private fun getUInt32LE(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun findSequence(data: ByteArray, sequence: ByteArray, startFrom: Int = 0): Int {
        if (sequence.isEmpty() || data.size < sequence.size) return -1
        for (i in startFrom..(data.size - sequence.size)) {
            var match = true
            for (j in sequence.indices) {
                if (data[i + j] != sequence[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }

    // =========================================================================
    // 2. VECTOR EPS & ADOBE ILLUSTRATOR GRADIENT RENDERER
    // =========================================================================

    private data class GradientColorStop(
        val position: Float,
        val color: Int
    )

    private data class ParsedGradient(
        val name: String,
        val isRadial: Boolean,
        val stops: List<GradientColorStop>
    )

    private class GraphicsState(
        var fillColor: Int = Color.BLACK,
        var strokeColor: Int = Color.TRANSPARENT,
        var strokeWidth: Float = 1f,
        var activeShader: Shader? = null,
        var activeClip: Path? = null
    ) {
        fun copy(): GraphicsState {
            val c = GraphicsState(fillColor, strokeColor, strokeWidth, activeShader, null)
            if (activeClip != null) {
                c.activeClip = Path(activeClip!!)
            }
            return c
        }
    }

    private fun renderVectorEps(epsText: String, maxPreviewSize: Int): Bitmap? {
        // 1. Extract BoundingBox
        var llx = 0f
        var lly = 0f
        var urx = 0f
        var ury = 0f
        var hasBbox = false

        val bboxRegex = Regex("""(?:%%BoundingBox:|%%HiResBoundingBox:|%AIGPU_BoundingBox:)\s*(-?\d+(?:\.\d+)?)\s+(-?\d+(?:\.\d+)?)\s+(-?\d+(?:\.\d+)?)\s+(-?\d+(?:\.\d+)?)""")
        val lineSeq = epsText.lineSequence()
        for (line in lineSeq.take(300)) {
            val match = bboxRegex.find(line)
            if (match != null) {
                val v1 = match.groupValues[1].toFloat()
                val v2 = match.groupValues[2].toFloat()
                val v3 = match.groupValues[3].toFloat()
                val v4 = match.groupValues[4].toFloat()
                llx = min(v1, v3)
                lly = min(v2, v4)
                urx = max(v1, v3)
                ury = max(v2, v4)
                if (urx > llx && ury > lly) {
                    hasBbox = true
                    break
                }
            }
        }

        // 2. Extract Adobe Illustrator Pre-defined Gradients (%AI5_BeginGradient ... %AI5_EndGradient)
        val aiGradients = parseAiGradients(epsText)

        val tokens = tokenizePostScriptWithStrings(epsText)

        if (!hasBbox) {
            // Pre-scan coordinates to find bounds
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE

            val tempStack = mutableListOf<Float>()
            for (tok in tokens) {
                val num = tok.toFloatOrNull()
                if (num != null) {
                    tempStack.add(num)
                } else {
                    when (tok) {
                        "m", "moveto", "l", "lineto" -> {
                            if (tempStack.size >= 2) {
                                val y = tempStack.removeAt(tempStack.size - 1)
                                val x = tempStack.removeAt(tempStack.size - 1)
                                minX = min(minX, x); maxX = max(maxX, x)
                                minY = min(minY, y); maxY = max(maxY, y)
                            }
                        }
                        "c", "curveto" -> {
                            if (tempStack.size >= 6) {
                                for (i in 0 until 3) {
                                    val y = tempStack.removeAt(tempStack.size - 1)
                                    val x = tempStack.removeAt(tempStack.size - 1)
                                    minX = min(minX, x); maxX = max(maxX, x)
                                    minY = min(minY, y); maxY = max(maxY, y)
                                }
                            }
                        }
                        "re" -> {
                            if (tempStack.size >= 4) {
                                val h = tempStack.removeAt(tempStack.size - 1)
                                val w = tempStack.removeAt(tempStack.size - 1)
                                val y = tempStack.removeAt(tempStack.size - 1)
                                val x = tempStack.removeAt(tempStack.size - 1)
                                minX = min(minX, x); maxX = max(maxX, x + w)
                                minY = min(minY, y); maxY = max(maxY, y + h)
                            }
                        }
                    }
                    tempStack.clear()
                }
            }

            if (minX < maxX && minY < maxY) {
                llx = minX
                lly = minY
                urx = maxX
                ury = maxY
            } else {
                llx = 0f; lly = 0f; urx = 512f; ury = 512f
            }
        }

        val bbWidth = max(1f, urx - llx)
        val bbHeight = max(1f, ury - lly)

        val scale = maxPreviewSize.toFloat() / max(bbWidth, bbHeight)
        val previewWidth = (bbWidth * scale).toInt().coerceAtLeast(1)
        val previewHeight = (bbHeight * scale).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val fillPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; color = Color.BLACK }
        val strokePaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; color = Color.TRANSPARENT }

        fun mapX(x: Float): Float = (x - llx) * scale
        fun mapY(y: Float): Float = (ury - y) * scale

        var currentPath = Path()
        var currentX = 0f
        var currentY = 0f

        var state = GraphicsState()
        val stateStack = mutableListOf<GraphicsState>()

        val numStack = mutableListOf<Float>()
        val stringStack = mutableListOf<String>()

        // Level 3 Shading parser state
        var parsingShadingDict = false
        var shadingType = 2 // 2 = linear, 3 = radial
        val shadingCoords = mutableListOf<Float>()
        val shadingC0 = mutableListOf<Float>()
        val shadingC1 = mutableListOf<Float>()
        val shadingMultiColors = mutableListOf<Int>()
        val shadingMultiStops = mutableListOf<Float>()

        val tokenList = tokens.toList()
        var i = 0
        while (i < tokenList.size) {
            val tok = tokenList[i++]
            val num = tok.toFloatOrNull()

            if (num != null) {
                numStack.add(num)
                if (numStack.size > 50) numStack.removeAt(0)
                continue
            }

            // String or Name token (e.g. (GradientName) or /GradientName)
            if (tok.startsWith("(") && tok.endsWith(")")) {
                stringStack.add(tok.substring(1, tok.length - 1))
                continue
            }
            if (tok.startsWith("/")) {
                stringStack.add(tok.substring(1))
            }

            when (tok) {
                // --- Path Construction ---
                "m", "moveto" -> {
                    if (numStack.size >= 2) {
                        val y = numStack.removeAt(numStack.size - 1)
                        val x = numStack.removeAt(numStack.size - 1)
                        val mx = mapX(x); val my = mapY(y)
                        currentPath.moveTo(mx, my)
                        currentX = mx; currentY = my
                    }
                    numStack.clear()
                }
                "l", "lineto" -> {
                    if (numStack.size >= 2) {
                        val y = numStack.removeAt(numStack.size - 1)
                        val x = numStack.removeAt(numStack.size - 1)
                        val mx = mapX(x); val my = mapY(y)
                        currentPath.lineTo(mx, my)
                        currentX = mx; currentY = my
                    }
                    numStack.clear()
                }
                "c", "curveto" -> {
                    if (numStack.size >= 6) {
                        val y3 = numStack.removeAt(numStack.size - 1)
                        val x3 = numStack.removeAt(numStack.size - 1)
                        val y2 = numStack.removeAt(numStack.size - 1)
                        val x2 = numStack.removeAt(numStack.size - 1)
                        val y1 = numStack.removeAt(numStack.size - 1)
                        val x1 = numStack.removeAt(numStack.size - 1)
                        currentPath.cubicTo(mapX(x1), mapY(y1), mapX(x2), mapY(y2), mapX(x3), mapY(y3))
                        currentX = mapX(x3); currentY = mapY(y3)
                    }
                    numStack.clear()
                }
                "v" -> {
                    if (numStack.size >= 4) {
                        val y3 = numStack.removeAt(numStack.size - 1)
                        val x3 = numStack.removeAt(numStack.size - 1)
                        val y2 = numStack.removeAt(numStack.size - 1)
                        val x2 = numStack.removeAt(numStack.size - 1)
                        currentPath.cubicTo(currentX, currentY, mapX(x2), mapY(y2), mapX(x3), mapY(y3))
                        currentX = mapX(x3); currentY = mapY(y3)
                    }
                    numStack.clear()
                }
                "y" -> {
                    if (numStack.size >= 4) {
                        val y3 = numStack.removeAt(numStack.size - 1)
                        val x3 = numStack.removeAt(numStack.size - 1)
                        val y1 = numStack.removeAt(numStack.size - 1)
                        val x1 = numStack.removeAt(numStack.size - 1)
                        currentPath.cubicTo(mapX(x1), mapY(y1), mapX(x3), mapY(y3), mapX(x3), mapY(y3))
                        currentX = mapX(x3); currentY = mapY(y3)
                    }
                    numStack.clear()
                }
                "rlineto" -> {
                    if (numStack.size >= 2) {
                        val dy = numStack.removeAt(numStack.size - 1)
                        val dx = numStack.removeAt(numStack.size - 1)
                        currentPath.rLineTo(dx * scale, -dy * scale)
                    }
                    numStack.clear()
                }
                "rmoveto" -> {
                    if (numStack.size >= 2) {
                        val dy = numStack.removeAt(numStack.size - 1)
                        val dx = numStack.removeAt(numStack.size - 1)
                        currentPath.rMoveTo(dx * scale, -dy * scale)
                    }
                    numStack.clear()
                }
                "re" -> {
                    if (numStack.size >= 4) {
                        val h = numStack.removeAt(numStack.size - 1)
                        val w = numStack.removeAt(numStack.size - 1)
                        val y = numStack.removeAt(numStack.size - 1)
                        val x = numStack.removeAt(numStack.size - 1)
                        val left = mapX(x)
                        val right = mapX(x + w)
                        val top = mapY(y + h)
                        val bottom = mapY(y)
                        currentPath.addRect(min(left, right), min(top, bottom), max(left, right), max(top, bottom), Path.Direction.CW)
                    }
                    numStack.clear()
                }
                "h", "cp", "closepath" -> {
                    currentPath.close()
                    numStack.clear()
                }
                "n", "newpath" -> {
                    currentPath = Path()
                    numStack.clear()
                }

                // --- Color & Shading Attributes ---
                "rg", "setrgbcolor" -> {
                    if (numStack.size >= 3) {
                        val b = numStack.removeAt(numStack.size - 1)
                        val g = numStack.removeAt(numStack.size - 1)
                        val r = numStack.removeAt(numStack.size - 1)
                        state.fillColor = Color.rgb((r.coerceIn(0f, 1f) * 255).toInt(), (g.coerceIn(0f, 1f) * 255).toInt(), (b.coerceIn(0f, 1f) * 255).toInt())
                        state.activeShader = null
                    }
                    numStack.clear()
                }
                "RG" -> {
                    if (numStack.size >= 3) {
                        val b = numStack.removeAt(numStack.size - 1)
                        val g = numStack.removeAt(numStack.size - 1)
                        val r = numStack.removeAt(numStack.size - 1)
                        state.strokeColor = Color.rgb((r.coerceIn(0f, 1f) * 255).toInt(), (g.coerceIn(0f, 1f) * 255).toInt(), (b.coerceIn(0f, 1f) * 255).toInt())
                    }
                    numStack.clear()
                }
                "k", "setcmykcolor" -> {
                    if (numStack.size >= 4) {
                        val k = numStack.removeAt(numStack.size - 1)
                        val y = numStack.removeAt(numStack.size - 1)
                        val m = numStack.removeAt(numStack.size - 1)
                        val c = numStack.removeAt(numStack.size - 1)
                        val r = (1f - c.coerceIn(0f, 1f)) * (1f - k.coerceIn(0f, 1f))
                        val g = (1f - m.coerceIn(0f, 1f)) * (1f - k.coerceIn(0f, 1f))
                        val b = (1f - y.coerceIn(0f, 1f)) * (1f - k.coerceIn(0f, 1f))
                        state.fillColor = Color.rgb((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
                        state.activeShader = null
                    }
                    numStack.clear()
                }
                "K" -> {
                    if (numStack.size >= 4) {
                        val k = numStack.removeAt(numStack.size - 1)
                        val y = numStack.removeAt(numStack.size - 1)
                        val m = numStack.removeAt(numStack.size - 1)
                        val c = numStack.removeAt(numStack.size - 1)
                        val r = (1f - c.coerceIn(0f, 1f)) * (1f - k.coerceIn(0f, 1f))
                        val g = (1f - m.coerceIn(0f, 1f)) * (1f - k.coerceIn(0f, 1f))
                        val b = (1f - y.coerceIn(0f, 1f)) * (1f - k.coerceIn(0f, 1f))
                        state.strokeColor = Color.rgb((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
                    }
                    numStack.clear()
                }
                "g", "setgray" -> {
                    if (numStack.isNotEmpty()) {
                        val gray = numStack.removeAt(numStack.size - 1)
                        val gr = (gray.coerceIn(0f, 1f) * 255).toInt()
                        state.fillColor = Color.rgb(gr, gr, gr)
                        state.activeShader = null
                    }
                    numStack.clear()
                }
                "G" -> {
                    if (numStack.isNotEmpty()) {
                        val gray = numStack.removeAt(numStack.size - 1)
                        val gr = (gray.coerceIn(0f, 1f) * 255).toInt()
                        state.strokeColor = Color.rgb(gr, gr, gr)
                    }
                    numStack.clear()
                }
                "w", "setlinewidth" -> {
                    if (numStack.isNotEmpty()) {
                        val w = numStack.removeAt(numStack.size - 1)
                        state.strokeWidth = max(0.5f, w * scale)
                    }
                    numStack.clear()
                }

                // --- Level 3 Shading Dictionary parsing ---
                "ShadingType" -> {
                    parsingShadingDict = true
                    if (numStack.isNotEmpty()) {
                        shadingType = numStack.removeAt(numStack.size - 1).toInt()
                    }
                }
                "Coords" -> {
                    // Extract coordinates from preceding numbers in numStack
                    shadingCoords.clear()
                    shadingCoords.addAll(numStack)
                    numStack.clear()
                }
                "C0" -> {
                    shadingC0.clear()
                    shadingC0.addAll(numStack)
                    numStack.clear()
                }
                "C1" -> {
                    shadingC1.clear()
                    shadingC1.addAll(numStack)
                    numStack.clear()
                }
                "Bounds" -> {
                    shadingMultiStops.clear()
                    shadingMultiStops.add(0f)
                    shadingMultiStops.addAll(numStack)
                    shadingMultiStops.add(1f)
                    numStack.clear()
                }
                "shfill" -> {
                    // Construct Gradient Shader from Shading Dictionary
                    val shader = createShadingShader(
                        shadingType = shadingType,
                        coords = shadingCoords,
                        c0 = shadingC0,
                        c1 = shadingC1,
                        multiColors = shadingMultiColors,
                        multiStops = shadingMultiStops,
                        mapX = ::mapX,
                        mapY = ::mapY,
                        scale = scale,
                        previewWidth = previewWidth.toFloat(),
                        previewHeight = previewHeight.toFloat()
                    )

                    if (shader != null) {
                        state.activeShader = shader
                        fillPaint.shader = shader
                        if (!currentPath.isEmpty) {
                            canvas.drawPath(currentPath, fillPaint)
                        } else {
                            canvas.drawRect(0f, 0f, previewWidth.toFloat(), previewHeight.toFloat(), fillPaint)
                        }
                        fillPaint.shader = null
                    }
                    parsingShadingDict = false
                    numStack.clear()
                    stringStack.clear()
                }
                "setpattern", "makepattern" -> {
                    // Pattern Shading
                    val shader = createShadingShader(
                        shadingType = shadingType,
                        coords = shadingCoords,
                        c0 = shadingC0,
                        c1 = shadingC1,
                        multiColors = shadingMultiColors,
                        multiStops = shadingMultiStops,
                        mapX = ::mapX,
                        mapY = ::mapY,
                        scale = scale,
                        previewWidth = previewWidth.toFloat(),
                        previewHeight = previewHeight.toFloat()
                    )
                    if (shader != null) {
                        state.activeShader = shader
                    }
                }

                // --- Adobe Illustrator Gradient Fill Operators ---
                "_Xg", "_xg", "_Yg", "_yg", "_Bg", "_bg", "_Ag", "_ag" -> {
                    // Look up gradient by name in stringStack or fallback
                    var gradName = stringStack.lastOrNull { aiGradients.containsKey(it) }
                    if (gradName == null && stringStack.isNotEmpty()) {
                        gradName = stringStack.last()
                    }
                    val gradDef = if (gradName != null) aiGradients[gradName] else null

                    val isRadialOp = tok == "_Yg" || tok == "_yg" || (gradDef?.isRadial == true)
                    val shader = if (gradDef != null) {
                        createAiGradientShader(
                            gradDef = gradDef,
                            numStack = numStack,
                            mapX = ::mapX,
                            mapY = ::mapY,
                            scale = scale,
                            previewWidth = previewWidth.toFloat(),
                            previewHeight = previewHeight.toFloat()
                        )
                    } else if (numStack.size >= 4) {
                        // Fallback linear gradient from coords in numStack
                        val y1 = numStack[numStack.size - 1]
                        val x1 = numStack[numStack.size - 2]
                        val y0 = numStack[numStack.size - 3]
                        val x0 = numStack[numStack.size - 4]
                        LinearGradient(mapX(x0), mapY(y0), mapX(x1), mapY(y1), state.fillColor, Color.WHITE, Shader.TileMode.CLAMP)
                    } else null

                    if (shader != null) {
                        state.activeShader = shader
                        fillPaint.shader = shader
                        canvas.drawPath(currentPath, fillPaint)
                        fillPaint.shader = null
                    } else {
                        // Flat fallback
                        fillPaint.color = state.fillColor
                        canvas.drawPath(currentPath, fillPaint)
                    }
                    numStack.clear()
                    stringStack.clear()
                }

                // --- Clipping & Graphics State ---
                "clip", "eoclip", "W", "W*" -> {
                    if (!currentPath.isEmpty) {
                        val clipCopy = Path(currentPath)
                        state.activeClip = clipCopy
                        try {
                            canvas.clipPath(clipCopy)
                        } catch (_: Exception) {}
                    }
                    numStack.clear()
                }
                "gsave", "q" -> {
                    canvas.save()
                    stateStack.add(state.copy())
                    numStack.clear()
                }
                "grestore", "Q" -> {
                    try {
                        canvas.restore()
                    } catch (_: Exception) {}
                    if (stateStack.isNotEmpty()) {
                        state = stateStack.removeAt(stateStack.size - 1)
                    }
                    numStack.clear()
                }

                // --- Standard Drawing Operators ---
                "f", "F", "f*", "fill" -> {
                    if (state.activeShader != null) {
                        fillPaint.shader = state.activeShader
                    } else {
                        fillPaint.shader = null
                        fillPaint.color = state.fillColor
                    }
                    canvas.drawPath(currentPath, fillPaint)
                    fillPaint.shader = null
                    numStack.clear()
                }
                "s", "S", "stroke" -> {
                    strokePaint.color = state.strokeColor
                    strokePaint.strokeWidth = state.strokeWidth
                    canvas.drawPath(currentPath, strokePaint)
                    numStack.clear()
                }
                "b", "B", "b*", "B*" -> {
                    if (state.activeShader != null) {
                        fillPaint.shader = state.activeShader
                    } else {
                        fillPaint.shader = null
                        fillPaint.color = state.fillColor
                    }
                    canvas.drawPath(currentPath, fillPaint)
                    fillPaint.shader = null

                    if (state.strokeColor != Color.TRANSPARENT) {
                        strokePaint.color = state.strokeColor
                        strokePaint.strokeWidth = state.strokeWidth
                        canvas.drawPath(currentPath, strokePaint)
                    }
                    numStack.clear()
                }
                else -> {
                    // Keep numbers if we might be in the middle of a coordinate/color list
                    if (numStack.size > 20) numStack.clear()
                }
            }
        }

        return bitmap
    }

    private fun createShadingShader(
        shadingType: Int,
        coords: List<Float>,
        c0: List<Float>,
        c1: List<Float>,
        multiColors: List<Int>,
        multiStops: List<Float>,
        mapX: (Float) -> Float,
        mapY: (Float) -> Float,
        scale: Float,
        previewWidth: Float,
        previewHeight: Float
    ): Shader? {
        val color0 = if (c0.size >= 3) {
            Color.rgb((c0[0].coerceIn(0f, 1f) * 255).toInt(), (c0[1].coerceIn(0f, 1f) * 255).toInt(), (c0[2].coerceIn(0f, 1f) * 255).toInt())
        } else if (c0.size >= 4) {
            // CMYK
            val r = (1f - c0[0].coerceIn(0f, 1f)) * (1f - c0[3].coerceIn(0f, 1f))
            val g = (1f - c0[1].coerceIn(0f, 1f)) * (1f - c0[3].coerceIn(0f, 1f))
            val b = (1f - c0[2].coerceIn(0f, 1f)) * (1f - c0[3].coerceIn(0f, 1f))
            Color.rgb((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
        } else Color.BLACK

        val color1 = if (c1.size >= 3) {
            Color.rgb((c1[0].coerceIn(0f, 1f) * 255).toInt(), (c1[1].coerceIn(0f, 1f) * 255).toInt(), (c1[2].coerceIn(0f, 1f) * 255).toInt())
        } else if (c1.size >= 4) {
            // CMYK
            val r = (1f - c1[0].coerceIn(0f, 1f)) * (1f - c1[3].coerceIn(0f, 1f))
            val g = (1f - c1[1].coerceIn(0f, 1f)) * (1f - c1[3].coerceIn(0f, 1f))
            val b = (1f - c1[2].coerceIn(0f, 1f)) * (1f - c1[3].coerceIn(0f, 1f))
            Color.rgb((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
        } else Color.WHITE

        if (shadingType == 3 && coords.size >= 6) {
            // Radial Shading: [ x0 y0 r0 x1 y1 r1 ]
            val cx = mapX(coords[3])
            val cy = mapY(coords[4])
            val r = max(1f, coords[5] * scale)
            return RadialGradient(cx, cy, r, color0, color1, Shader.TileMode.CLAMP)
        } else if (coords.size >= 4) {
            // Linear Shading: [ x0 y0 x1 y1 ]
            val sx = mapX(coords[0])
            val sy = mapY(coords[1])
            val ex = mapX(coords[2])
            val ey = mapY(coords[3])
            return LinearGradient(sx, sy, ex, ey, color0, color1, Shader.TileMode.CLAMP)
        } else {
            // Default vertical gradient across preview
            return LinearGradient(0f, 0f, 0f, previewHeight, color0, color1, Shader.TileMode.CLAMP)
        }
    }

    private fun createAiGradientShader(
        gradDef: ParsedGradient,
        numStack: List<Float>,
        mapX: (Float) -> Float,
        mapY: (Float) -> Float,
        scale: Float,
        previewWidth: Float,
        previewHeight: Float
    ): Shader? {
        val stops = gradDef.stops
        if (stops.isEmpty()) return null

        val colors = stops.map { it.color }.toIntArray()
        val positions = stops.map { it.position }.toFloatArray()

        if (gradDef.isRadial) {
            val cx = if (numStack.size >= 2) mapX(numStack[0]) else previewWidth / 2f
            val cy = if (numStack.size >= 2) mapY(numStack[1]) else previewHeight / 2f
            val r = if (numStack.size >= 3) max(1f, numStack[2] * scale) else previewWidth / 2f
            return RadialGradient(cx, cy, r, colors, positions, Shader.TileMode.CLAMP)
        } else {
            val sx: Float
            val sy: Float
            val ex: Float
            val ey: Float
            if (numStack.size >= 4) {
                sx = mapX(numStack[0])
                sy = mapY(numStack[1])
                ex = mapX(numStack[2])
                ey = mapY(numStack[3])
            } else {
                sx = 0f
                sy = 0f
                ex = previewWidth
                ey = previewHeight
            }
            return LinearGradient(sx, sy, ex, ey, colors, positions, Shader.TileMode.CLAMP)
        }
    }

    private fun parseAiGradients(epsText: String): Map<String, ParsedGradient> {
        val result = mutableMapOf<String, ParsedGradient>()
        val regex = Regex("""%AI5_BeginGradient:\s*\(([^)]+)\)([\s\S]*?)%AI5_EndGradient""")
        for (match in regex.findAll(epsText)) {
            val name = match.groupValues[1].trim()
            val body = match.groupValues[2]

            // Check if radial or linear
            val isRadial = body.contains("/Radial") || body.contains(" 1 ")
            val stops = mutableListOf<GradientColorStop>()

            // Extract color stop sequences: e.g. offset [ r g b ] or offset [ c m y k ]
            val stopRegex = Regex("""([0-9.]+)\s*\[\s*([0-9.\s]+)\s*\]""")
            for (sm in stopRegex.findAll(body)) {
                val offset = sm.groupValues[1].toFloatOrNull() ?: continue
                val cTokens = sm.groupValues[2].split(Regex("""\s+""")).mapNotNull { it.toFloatOrNull() }
                val color = if (cTokens.size >= 4) {
                    val c = cTokens[0]; val m = cTokens[1]; val y = cTokens[2]; val k = cTokens[3]
                    val r = (1f - c.coerceIn(0f, 1f)) * (1f - k.coerceIn(0f, 1f))
                    val g = (1f - m.coerceIn(0f, 1f)) * (1f - k.coerceIn(0f, 1f))
                    val b = (1f - y.coerceIn(0f, 1f)) * (1f - k.coerceIn(0f, 1f))
                    Color.rgb((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
                } else if (cTokens.size >= 3) {
                    Color.rgb((cTokens[0].coerceIn(0f, 1f) * 255).toInt(), (cTokens[1].coerceIn(0f, 1f) * 255).toInt(), (cTokens[2].coerceIn(0f, 1f) * 255).toInt())
                } else if (cTokens.isNotEmpty()) {
                    val gr = (cTokens[0].coerceIn(0f, 1f) * 255).toInt()
                    Color.rgb(gr, gr, gr)
                } else {
                    Color.BLACK
                }
                stops.add(GradientColorStop(offset.coerceIn(0f, 1f), color))
            }

            if (stops.isNotEmpty()) {
                stops.sortBy { it.position }
                result[name] = ParsedGradient(name, isRadial, stops)
            }
        }
        return result
    }

    private fun tokenizePostScriptWithStrings(text: String): Sequence<String> {
        return sequence {
            val sb = StringBuilder()
            var inComment = false
            var inString = false
            var parensDepth = 0

            for (i in text.indices) {
                val c = text[i]

                if (inComment) {
                    if (c == '\n' || c == '\r') {
                        inComment = false
                    }
                    continue
                }

                if (inString) {
                    sb.append(c)
                    if (c == '(') parensDepth++
                    if (c == ')') {
                        parensDepth--
                        if (parensDepth <= 0) {
                            inString = false
                            yield(sb.toString())
                            sb.clear()
                        }
                    }
                    continue
                }

                if (c == '%') {
                    if (sb.isNotEmpty()) {
                        yield(sb.toString())
                        sb.clear()
                    }
                    inComment = true
                    continue
                }

                if (c == '(') {
                    if (sb.isNotEmpty()) {
                        yield(sb.toString())
                        sb.clear()
                    }
                    sb.append('(')
                    inString = true
                    parensDepth = 1
                    continue
                }

                if (c.isWhitespace() || c == '<' || c == '>' || c == '[' || c == ']' || c == '{' || c == '}') {
                    if (sb.isNotEmpty()) {
                        yield(sb.toString())
                        sb.clear()
                    }
                } else {
                    sb.append(c)
                }
            }
            if (sb.isNotEmpty()) {
                yield(sb.toString())
            }
        }
    }

    // =========================================================================
    // 3. PURE KOTLIN TIFF DECODER (PACKBITS, UNCOMPRESSED, LZW, RGB & PALETTE)
    // =========================================================================

    private object TiffDecoder {

        fun decodeTiff(data: ByteArray, offset: Int, length: Int): Bitmap? {
            try {
                if (length < 8 || offset + length > data.size) return null

                // 1. Byte Order Header: 'II' (Little Endian) or 'MM' (Big Endian)
                val isLE = data[offset] == 'I'.code.toByte() && data[offset + 1] == 'I'.code.toByte()
                val isBE = data[offset] == 'M'.code.toByte() && data[offset + 1] == 'M'.code.toByte()
                if (!isLE && !isBE) return null

                val magic = getUInt16(data, offset + 2, isLE)
                if (magic != 42) return null

                var ifdOffset = getUInt32(data, offset + 4, isLE)
                if (ifdOffset <= 0 || ifdOffset >= length) return null

                var width = 0
                var height = 0
                var bitsPerSample = 8
                var compression = 1 // 1 = uncompressed, 32773 = PackBits, 5 = LZW
                var photometric = 2 // 0/1 = Gray, 2 = RGB, 3 = Palette, 5 = CMYK
                var samplesPerPixel = 1
                var rowsPerStrip = 0
                val stripOffsets = mutableListOf<Int>()
                val stripByteCounts = mutableListOf<Int>()
                var colorMap: IntArray? = null

                val numEntries = getUInt16(data, offset + ifdOffset, isLE)
                var entryPos = offset + ifdOffset + 2

                for (e in 0 until numEntries) {
                    if (entryPos + 12 > offset + length) break
                    val tag = getUInt16(data, entryPos, isLE)
                    val type = getUInt16(data, entryPos + 2, isLE)
                    val count = getUInt32(data, entryPos + 4, isLE)
                    val valOffset = getUInt32(data, entryPos + 8, isLE)

                    when (tag) {
                        256 -> width = if (type == 3) getUInt16(data, entryPos + 8, isLE) else valOffset
                        257 -> height = if (type == 3) getUInt16(data, entryPos + 8, isLE) else valOffset
                        258 -> bitsPerSample = if (type == 3) getUInt16(data, entryPos + 8, isLE) else 8
                        259 -> compression = if (type == 3) getUInt16(data, entryPos + 8, isLE) else valOffset
                        262 -> photometric = if (type == 3) getUInt16(data, entryPos + 8, isLE) else valOffset
                        273 -> {
                            // StripOffsets
                            if (count == 1) {
                                stripOffsets.add(if (type == 3) getUInt16(data, entryPos + 8, isLE) else valOffset)
                            } else {
                                readIntArray(data, offset, valOffset, count, type, isLE, stripOffsets)
                            }
                        }
                        277 -> samplesPerPixel = if (type == 3) getUInt16(data, entryPos + 8, isLE) else valOffset
                        278 -> rowsPerStrip = if (type == 3) getUInt16(data, entryPos + 8, isLE) else valOffset
                        279 -> {
                            // StripByteCounts
                            if (count == 1) {
                                stripByteCounts.add(if (type == 3) getUInt16(data, entryPos + 8, isLE) else valOffset)
                            } else {
                                readIntArray(data, offset, valOffset, count, type, isLE, stripByteCounts)
                            }
                        }
                        320 -> {
                            // ColorMap for Palette images
                            if (count > 0 && offset + valOffset + (count * 2) <= offset + length) {
                                val mapSize = count / 3
                                val cmap = IntArray(mapSize)
                                val base = offset + valOffset
                                for (k in 0 until mapSize) {
                                    val r = (getUInt16(data, base + k * 2, isLE) ushr 8) and 0xFF
                                    val g = (getUInt16(data, base + (mapSize + k) * 2, isLE) ushr 8) and 0xFF
                                    val b = (getUInt16(data, base + (mapSize * 2 + k) * 2, isLE) ushr 8) and 0xFF
                                    cmap[k] = Color.rgb(r, g, b)
                                }
                                colorMap = cmap
                            }
                        }
                    }
                    entryPos += 12
                }

                if (width <= 0 || height <= 0 || stripOffsets.isEmpty()) return null

                // Decompress all strips into a single pixel byte buffer
                val rawBuffer = ByteArrayOutputStream(width * height * max(1, samplesPerPixel))
                for (s in stripOffsets.indices) {
                    val sOffset = offset + stripOffsets[s]
                    val sLength = if (s < stripByteCounts.size) stripByteCounts[s] else (length - stripOffsets[s])
                    if (sOffset < offset || sOffset + sLength > offset + length || sLength <= 0) continue

                    when (compression) {
                        1 -> {
                            // Uncompressed
                            rawBuffer.write(data, sOffset, sLength)
                        }
                        32773 -> {
                            // PackBits RLE
                            decompressPackBits(data, sOffset, sLength, rawBuffer)
                        }
                        8 -> {
                            // Deflate
                            decompressDeflate(data, sOffset, sLength, rawBuffer)
                        }
                        else -> {
                            // Unsupported compression in fallback, try uncompressed slice
                            rawBuffer.write(data, sOffset, sLength)
                        }
                    }
                }

                val decompressedBytes = rawBuffer.toByteArray()
                if (decompressedBytes.isEmpty()) return null

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val pixels = IntArray(width * height)

                // Render pixels according to photometric interpretation and samplesPerPixel
                if (photometric == 2 && samplesPerPixel >= 3) {
                    // RGB or RGBA
                    val step = samplesPerPixel
                    var ptr = 0
                    for (p in 0 until min(pixels.size, decompressedBytes.size / step)) {
                        val r = decompressedBytes[ptr].toInt() and 0xFF
                        val g = decompressedBytes[ptr + 1].toInt() and 0xFF
                        val b = decompressedBytes[ptr + 2].toInt() and 0xFF
                        val a = if (step >= 4) decompressedBytes[ptr + 3].toInt() and 0xFF else 255
                        pixels[p] = Color.argb(a, r, g, b)
                        ptr += step
                    }
                } else if (photometric == 3 && colorMap != null) {
                    // Palette / Indexed
                    for (p in 0 until min(pixels.size, decompressedBytes.size)) {
                        val idx = decompressedBytes[p].toInt() and 0xFF
                        pixels[p] = if (idx < colorMap.size) colorMap[idx] else Color.BLACK
                    }
                } else if (photometric == 5 && samplesPerPixel >= 4) {
                    // CMYK
                    var ptr = 0
                    for (p in 0 until min(pixels.size, decompressedBytes.size / 4)) {
                        val c = decompressedBytes[ptr].toInt() and 0xFF
                        val m = decompressedBytes[ptr + 1].toInt() and 0xFF
                        val y = decompressedBytes[ptr + 2].toInt() and 0xFF
                        val k = decompressedBytes[ptr + 3].toInt() and 0xFF
                        val r = ((255 - c) * (255 - k)) / 255
                        val g = ((255 - m) * (255 - k)) / 255
                        val b = ((255 - y) * (255 - k)) / 255
                        pixels[p] = Color.rgb(r, g, b)
                        ptr += 4
                    }
                } else {
                    // Grayscale
                    for (p in 0 until min(pixels.size, decompressedBytes.size)) {
                        val gr = decompressedBytes[p].toInt() and 0xFF
                        val finalGr = if (photometric == 0) 255 - gr else gr
                        pixels[p] = Color.rgb(finalGr, finalGr, finalGr)
                    }
                }

                bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
                return bitmap

            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }

        private fun decompressPackBits(src: ByteArray, offset: Int, length: Int, dst: ByteArrayOutputStream) {
            var i = offset
            val end = offset + length
            while (i < end) {
                val b = src[i++].toInt()
                if (b in 0..127) {
                    val count = b + 1
                    if (i + count <= end) {
                        dst.write(src, i, count)
                        i += count
                    } else {
                        break
                    }
                } else if (b in -127..-1) {
                    val count = 1 - b
                    if (i < end) {
                        val repeatByte = src[i++]
                        for (k in 0 until count) {
                            dst.write(repeatByte.toInt())
                        }
                    }
                }
                // -128 is a no-op
            }
        }

        private fun decompressDeflate(src: ByteArray, offset: Int, length: Int, dst: ByteArrayOutputStream) {
            val inflater = Inflater()
            inflater.setInput(src, offset, length)
            val buffer = ByteArray(4096)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count <= 0) break
                dst.write(buffer, 0, count)
            }
            inflater.end()
        }

        private fun readIntArray(data: ByteArray, baseOffset: Int, valOffset: Int, count: Int, type: Int, isLE: Boolean, out: MutableList<Int>) {
            var ptr = baseOffset + valOffset
            for (i in 0 until count) {
                if (ptr >= data.size) break
                val v = if (type == 3) {
                    val n = getUInt16(data, ptr, isLE)
                    ptr += 2
                    n
                } else {
                    val n = getUInt32(data, ptr, isLE)
                    ptr += 4
                    n
                }
                out.add(v)
            }
        }

        private fun getUInt16(bytes: ByteArray, offset: Int, isLE: Boolean): Int {
            val b0 = bytes[offset].toInt() and 0xFF
            val b1 = bytes[offset + 1].toInt() and 0xFF
            return if (isLE) b0 or (b1 shl 8) else (b0 shl 8) or b1
        }

        private fun getUInt32(bytes: ByteArray, offset: Int, isLE: Boolean): Int {
            val b0 = bytes[offset].toInt() and 0xFF
            val b1 = bytes[offset + 1].toInt() and 0xFF
            val b2 = bytes[offset + 2].toInt() and 0xFF
            val b3 = bytes[offset + 3].toInt() and 0xFF
            return if (isLE) {
                b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
            } else {
                (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
            }
        }
    }
}
