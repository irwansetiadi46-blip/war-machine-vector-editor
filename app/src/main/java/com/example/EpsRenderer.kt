package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

object EpsRenderer {

    fun renderEpsToJpegBase64(context: Context, epsBytes: ByteArray, maxPreviewSize: Int = 512): String? {
        try {
            // 1. Try to extract embedded binary thumbnail (TIFF/JPEG in DOS EPS header or EPS stream)
            val embeddedBitmap = extractEmbeddedBitmap(epsBytes)
            if (embeddedBitmap != null) {
                return compressAndEncode(embeddedBitmap, maxPreviewSize)
            }

            // 2. Fallback to PostScript & Adobe Illustrator vector parser
            val epsText = String(epsBytes, Charsets.UTF_8)
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
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        val previewBytes = outputStream.toByteArray()

        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }
        bitmap.recycle()

        return Base64.encodeToString(previewBytes, Base64.NO_WRAP)
    }

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

        // B. Search for JPEG magic bytes FF D8 FF E0 / E1 / DB / EE in raw bytes
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

        return null
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

    private fun renderVectorEps(epsText: String, maxPreviewSize: Int): Bitmap? {
        // 1. Extract BoundingBox
        var llx = 0f
        var lly = 0f
        var urx = 0f
        var ury = 0f
        var hasBbox = false

        val bboxRegex = Regex("""(?:%%BoundingBox:|%%HiResBoundingBox:|%AIGPU_BoundingBox:)\s*(-?\d+(?:\.\d+)?)\s+(-?\d+(?:\.\d+)?)\s+(-?\d+(?:\.\d+)?)\s+(-?\d+(?:\.\d+)?)""")
        val lineSeq = epsText.lineSequence()
        for (line in lineSeq.take(200)) {
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

        val tokens = tokenizePostScript(epsText)

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
        var fillColor = Color.BLACK
        var strokeColor = Color.TRANSPARENT
        var strokeWidth = 1f
        var currentX = 0f
        var currentY = 0f

        val stack = mutableListOf<Float>()

        for (tok in tokens) {
            val num = tok.toFloatOrNull()
            if (num != null) {
                stack.add(num)
                if (stack.size > 20) stack.removeAt(0)
            } else {
                when (tok) {
                    "m", "moveto" -> {
                        if (stack.size >= 2) {
                            val y = stack.removeAt(stack.size - 1)
                            val x = stack.removeAt(stack.size - 1)
                            val mx = mapX(x); val my = mapY(y)
                            currentPath.moveTo(mx, my)
                            currentX = mx; currentY = my
                        }
                    }
                    "l", "lineto" -> {
                        if (stack.size >= 2) {
                            val y = stack.removeAt(stack.size - 1)
                            val x = stack.removeAt(stack.size - 1)
                            val mx = mapX(x); val my = mapY(y)
                            currentPath.lineTo(mx, my)
                            currentX = mx; currentY = my
                        }
                    }
                    "c", "curveto" -> {
                        if (stack.size >= 6) {
                            val y3 = stack.removeAt(stack.size - 1)
                            val x3 = stack.removeAt(stack.size - 1)
                            val y2 = stack.removeAt(stack.size - 1)
                            val x2 = stack.removeAt(stack.size - 1)
                            val y1 = stack.removeAt(stack.size - 1)
                            val x1 = stack.removeAt(stack.size - 1)
                            currentPath.cubicTo(mapX(x1), mapY(y1), mapX(x2), mapY(y2), mapX(x3), mapY(y3))
                            currentX = mapX(x3); currentY = mapY(y3)
                        }
                    }
                    "v" -> {
                        if (stack.size >= 4) {
                            val y3 = stack.removeAt(stack.size - 1)
                            val x3 = stack.removeAt(stack.size - 1)
                            val y2 = stack.removeAt(stack.size - 1)
                            val x2 = stack.removeAt(stack.size - 1)
                            currentPath.cubicTo(currentX, currentY, mapX(x2), mapY(y2), mapX(x3), mapY(y3))
                            currentX = mapX(x3); currentY = mapY(y3)
                        }
                    }
                    "y" -> {
                        if (stack.size >= 4) {
                            val y3 = stack.removeAt(stack.size - 1)
                            val x3 = stack.removeAt(stack.size - 1)
                            val y1 = stack.removeAt(stack.size - 1)
                            val x1 = stack.removeAt(stack.size - 1)
                            currentPath.cubicTo(mapX(x1), mapY(y1), mapX(x3), mapY(y3), mapX(x3), mapY(y3))
                            currentX = mapX(x3); currentY = mapY(y3)
                        }
                    }
                    "rlineto" -> {
                        if (stack.size >= 2) {
                            val dy = stack.removeAt(stack.size - 1)
                            val dx = stack.removeAt(stack.size - 1)
                            currentPath.rLineTo(dx * scale, -dy * scale)
                        }
                    }
                    "rmoveto" -> {
                        if (stack.size >= 2) {
                            val dy = stack.removeAt(stack.size - 1)
                            val dx = stack.removeAt(stack.size - 1)
                            currentPath.rMoveTo(dx * scale, -dy * scale)
                        }
                    }
                    "h", "cp", "closepath" -> {
                        currentPath.close()
                    }
                    "n", "newpath" -> {
                        currentPath = Path()
                    }
                    "rg", "setrgbcolor" -> {
                        if (stack.size >= 3) {
                            val b = stack.removeAt(stack.size - 1)
                            val g = stack.removeAt(stack.size - 1)
                            val r = stack.removeAt(stack.size - 1)
                            fillColor = Color.rgb((r.coerceIn(0f, 1f) * 255).toInt(), (g.coerceIn(0f, 1f) * 255).toInt(), (b.coerceIn(0f, 1f) * 255).toInt())
                        }
                    }
                    "RG" -> {
                        if (stack.size >= 3) {
                            val b = stack.removeAt(stack.size - 1)
                            val g = stack.removeAt(stack.size - 1)
                            val r = stack.removeAt(stack.size - 1)
                            strokeColor = Color.rgb((r.coerceIn(0f, 1f) * 255).toInt(), (g.coerceIn(0f, 1f) * 255).toInt(), (b.coerceIn(0f, 1f) * 255).toInt())
                        }
                    }
                    "k", "setcmykcolor" -> {
                        if (stack.size >= 4) {
                            val k = stack.removeAt(stack.size - 1)
                            val y = stack.removeAt(stack.size - 1)
                            val m = stack.removeAt(stack.size - 1)
                            val c = stack.removeAt(stack.size - 1)
                            val r = (1f - c.coerceIn(0f, 1f)) * (1f - k.coerceIn(0f, 1f))
                            val g = (1f - m.coerceIn(0f, 1f)) * (1f - k.coerceIn(0f, 1f))
                            val b = (1f - y.coerceIn(0f, 1f)) * (1f - k.coerceIn(0f, 1f))
                            fillColor = Color.rgb((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
                        }
                    }
                    "K" -> {
                        if (stack.size >= 4) {
                            val k = stack.removeAt(stack.size - 1)
                            val y = stack.removeAt(stack.size - 1)
                            val m = stack.removeAt(stack.size - 1)
                            val c = stack.removeAt(stack.size - 1)
                            val r = (1f - c.coerceIn(0f, 1f)) * (1f - k.coerceIn(0f, 1f))
                            val g = (1f - m.coerceIn(0f, 1f)) * (1f - k.coerceIn(0f, 1f))
                            val b = (1f - y.coerceIn(0f, 1f)) * (1f - k.coerceIn(0f, 1f))
                            strokeColor = Color.rgb((r * 255).toInt(), (g * 255).toInt(), (b * 255).toInt())
                        }
                    }
                    "g", "setgray" -> {
                        if (stack.isNotEmpty()) {
                            val gray = stack.removeAt(stack.size - 1)
                            val gr = (gray.coerceIn(0f, 1f) * 255).toInt()
                            fillColor = Color.rgb(gr, gr, gr)
                        }
                    }
                    "G" -> {
                        if (stack.isNotEmpty()) {
                            val gray = stack.removeAt(stack.size - 1)
                            val gr = (gray.coerceIn(0f, 1f) * 255).toInt()
                            strokeColor = Color.rgb(gr, gr, gr)
                        }
                    }
                    "w", "setlinewidth" -> {
                        if (stack.isNotEmpty()) {
                            val w = stack.removeAt(stack.size - 1)
                            strokeWidth = max(0.5f, w * scale)
                        }
                    }
                    "f", "F", "f*", "fill" -> {
                        fillPaint.color = fillColor
                        canvas.drawPath(currentPath, fillPaint)
                    }
                    "s", "S", "stroke" -> {
                        strokePaint.color = strokeColor
                        strokePaint.strokeWidth = strokeWidth
                        canvas.drawPath(currentPath, strokePaint)
                    }
                    "b", "B", "b*", "B*" -> {
                        fillPaint.color = fillColor
                        canvas.drawPath(currentPath, fillPaint)
                        if (strokeColor != Color.TRANSPARENT) {
                            strokePaint.color = strokeColor
                            strokePaint.strokeWidth = strokeWidth
                            canvas.drawPath(currentPath, strokePaint)
                        }
                    }
                    else -> {
                        stack.clear()
                    }
                }
            }
        }

        return bitmap
    }

    private fun tokenizePostScript(text: String): Sequence<String> {
        return sequence {
            val sb = StringBuilder()
            var inComment = false
            for (i in text.indices) {
                val c = text[i]
                if (inComment) {
                    if (c == '\n' || c == '\r') {
                        inComment = false
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
                if (c.isWhitespace() || c == '(' || c == ')' || c == '<' || c == '>' || c == '[' || c == ']' || c == '{' || c == '}' || c == '/') {
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
}

