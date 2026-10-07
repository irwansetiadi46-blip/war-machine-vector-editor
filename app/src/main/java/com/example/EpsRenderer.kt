package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
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
            val bitmap = renderEpsToBitmap(epsBytes, maxPreviewSize) ?: return null
            return compressAndEncode(bitmap, maxPreviewSize)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun renderEpsToHighResJpgBytes(epsBytes: ByteArray, targetLongEdge: Int = 4000): ByteArray? {
        try {
            val bitmap = renderEpsToBitmap(epsBytes, targetLongEdge) ?: return null
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            bitmap.recycle()
            return outputStream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun renderEpsToBitmap(epsBytes: ByteArray, targetMaxSize: Int = 512): Bitmap? {
        try {
            val (psText, _) = extractPostScriptTextAndBytes(epsBytes)

            // 1. Try vector PostScript & Adobe Illustrator rendering FIRST
            val vectorBitmap = renderVectorEps(psText, targetMaxSize)
            if (vectorBitmap != null && !isBitmapBlankWhite(vectorBitmap)) {
                return vectorBitmap
            }

            // 2. Fallback to embedded high-resolution bitmap (TIFF / JPEG / PNG)
            val embeddedBitmap = extractEmbeddedBitmap(epsBytes)
            if (embeddedBitmap != null && !isBitmapBlankWhite(embeddedBitmap)) {
                vectorBitmap?.recycle()
                return embeddedBitmap
            }

            // 3. Fallback: ASCII preview
            val asciiPreviewBitmap = extractAsciiPreviewBitmap(epsBytes)
            if (asciiPreviewBitmap != null && !isBitmapBlankWhite(asciiPreviewBitmap)) {
                vectorBitmap?.recycle()
                return asciiPreviewBitmap
            }

            // If vectorBitmap had drawing elements even if light, return it
            if (vectorBitmap != null) {
                return vectorBitmap
            }

            return null
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun isBitmapBlankWhite(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return true

        val stepX = max(1, width / 25)
        val stepY = max(1, height / 25)

        for (y in 0 until height step stepY) {
            for (x in 0 until width step stepX) {
                val p = bitmap.getPixel(x, y)
                val alpha = (p ushr 24) and 0xFF
                val r = (p ushr 16) and 0xFF
                val g = (p ushr 8) and 0xFF
                val b = p and 0xFF
                if (alpha > 10 && (r < 245 || g < 245 || b < 245)) {
                    return false
                }
            }
        }
        return true
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
    // POSTSCRIPT EXTRACTION FROM EPS (DOS EPS & ASCII EPS)
    // =========================================================================

    private fun extractPostScriptTextAndBytes(epsBytes: ByteArray): Pair<String, ByteArray> {
        if (epsBytes.size >= 30) {
            val b0 = epsBytes[0].toInt() and 0xFF
            val b1 = epsBytes[1].toInt() and 0xFF
            val b2 = epsBytes[2].toInt() and 0xFF
            val b3 = epsBytes[3].toInt() and 0xFF

            // DOS EPS header magic: 0xC5D0D3C6
            if (b0 == 0xC5 && b1 == 0xD0 && b2 == 0xD3 && b3 == 0xC6) {
                val psOffset = getUInt32LE(epsBytes, 4)
                val psLength = getUInt32LE(epsBytes, 8)
                if (psOffset in 30..epsBytes.size && psLength > 0 && psOffset + psLength <= epsBytes.size) {
                    val rawPsBytes = epsBytes.copyOfRange(psOffset, psOffset + psLength)
                    val text = String(rawPsBytes, StandardCharsets.ISO_8859_1)
                    return Pair(text, rawPsBytes)
                }
            }
        }
        val text = String(epsBytes, StandardCharsets.ISO_8859_1)
        return Pair(text, epsBytes)
    }

    // =========================================================================
    // GRADIENT DATA MODELS & COLOR UTILITIES
    // =========================================================================

    data class GradientColorStop(
        val position: Float,
        val color: Int
    )

    data class ParsedGradient(
        val name: String,
        val isRadial: Boolean,
        val stops: List<GradientColorStop>,
        val coords: List<Float> = emptyList()
    )

    private fun parseColorComponents(cTokens: List<Float>): Int {
        if (cTokens.size >= 4) {
            // CMYK format (Cyan, Magenta, Yellow, Black)
            val maxVal = max(max(cTokens[0], cTokens[1]), max(cTokens[2], cTokens[3]))
            val div = if (maxVal > 1.0f) 100f else 1f
            val c = (cTokens[0] / div).coerceIn(0f, 1f)
            val m = (cTokens[1] / div).coerceIn(0f, 1f)
            val y = (cTokens[2] / div).coerceIn(0f, 1f)
            val k = (cTokens[3] / div).coerceIn(0f, 1f)
            val r = ((1f - c) * (1f - k) * 255f).toInt().coerceIn(0, 255)
            val g = ((1f - m) * (1f - k) * 255f).toInt().coerceIn(0, 255)
            val b = ((1f - y) * (1f - k) * 255f).toInt().coerceIn(0, 255)
            return Color.rgb(r, g, b)
        } else if (cTokens.size == 3) {
            // RGB format
            val maxVal = max(cTokens[0], max(cTokens[1], cTokens[2]))
            val div = if (maxVal > 1.0f) 255f else 1f
            val r = ((cTokens[0] / div).coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
            val g = ((cTokens[1] / div).coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
            val b = ((cTokens[2] / div).coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
            return Color.rgb(r, g, b)
        } else if (cTokens.isNotEmpty()) {
            // Grayscale format
            val div = if (cTokens[0] > 1.0f) (if (cTokens[0] > 100f) 255f else 100f) else 1f
            val gr = ((cTokens[0] / div).coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
            return Color.rgb(gr, gr, gr)
        }
        return Color.BLACK
    }

    // =========================================================================
    // ADOBE ILLUSTRATOR & POSTSCRIPT LEVEL 3 GRADIENT DEFINITION PARSERS
    // =========================================================================

    private fun parseGradientStops(body: String): List<GradientColorStop> {
        val stops = mutableListOf<GradientColorStop>()

        // Look for stop lines with bracketed color values:
        // Examples:
        // 0 50 1 [ 0 1 1 0 ]
        // 100 50 1 [ 1 0 0 0 ]
        // 0.0 [ 1 0 0 ]
        // [ 0 1 1 0 ] 0
        for (line in body.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("%") && !trimmed.contains("[")) continue
            if (!trimmed.contains("[")) continue

            val match = Regex("""(?:([0-9.]+)(?:\s+[0-9.]+)*\s+)?\[\s*([0-9.\s]+)\s*\](?:\s+([0-9.]+))?""").find(trimmed)
            if (match != null) {
                val beforeNumsStr = match.groupValues[1].trim()
                val colorStr = match.groupValues[2].trim()
                val afterNumStr = match.groupValues[3].trim()

                var rawOffset: Float? = null
                if (beforeNumsStr.isNotEmpty()) {
                    val tokens = beforeNumsStr.split(Regex("""\s+""")).mapNotNull { it.toFloatOrNull() }
                    if (tokens.isNotEmpty()) {
                        rawOffset = tokens[0] // In 'offset midpoint type [ ... ]', offset is the FIRST token!
                    }
                } else if (afterNumStr.isNotEmpty()) {
                    rawOffset = afterNumStr.toFloatOrNull()
                }

                val cTokens = colorStr.split(Regex("""\s+""")).mapNotNull { it.toFloatOrNull() }
                if (cTokens.isNotEmpty()) {
                    val color = parseColorComponents(cTokens)
                    val offset = if (rawOffset != null) {
                        var o = rawOffset
                        if (o > 1.0f) o /= 100f
                        o.coerceIn(0f, 1f)
                    } else {
                        stops.size.toFloat()
                    }
                    stops.add(GradientColorStop(offset, color))
                }
            }
        }

        // Generic bracket fallback if structured lines were not detected
        if (stops.isEmpty()) {
            val genericBracket = Regex("""\[\s*([0-9.\s]+)\s*\]""")
            val found = genericBracket.findAll(body).toList()
            for ((idx, m) in found.withIndex()) {
                val cTokens = m.groupValues[1].split(Regex("""\s+""")).mapNotNull { it.toFloatOrNull() }
                if (cTokens.size in 1..4) {
                    val color = parseColorComponents(cTokens)
                    val pos = if (found.size > 1) idx.toFloat() / (found.size - 1) else 0f
                    stops.add(GradientColorStop(pos, color))
                }
            }
        }

        if (stops.isEmpty()) return emptyList()

        stops.sortBy { it.position }

        val maxPos = stops.last().position
        if (maxPos > 1.0f) {
            for (i in stops.indices) {
                stops[i] = stops[i].copy(position = (stops[i].position / maxPos).coerceIn(0f, 1f))
            }
        }

        if (stops.first().position > 0f) {
            stops.add(0, GradientColorStop(0f, stops.first().color))
        }
        if (stops.last().position < 1f) {
            stops.add(GradientColorStop(1f, stops.last().color))
        }
        if (stops.size == 1) {
            stops.add(GradientColorStop(1f, stops[0].color))
        }

        // Ensure strictly non-decreasing positions for Android Shader
        for (i in 1 until stops.size) {
            if (stops[i].position <= stops[i - 1].position) {
                val adjusted = (stops[i - 1].position + 0.001f).coerceAtMost(1f)
                stops[i] = stops[i].copy(position = adjusted)
            }
        }

        return stops
    }

    private fun parseShadingDict(dictBody: String, name: String = ""): ParsedGradient? {
        val isRadial = dictBody.contains("/ShadingType 3") || dictBody.contains("/ShadingType\t3")

        val coordsMatch = Regex("""/Coords\s*\[\s*([0-9.\s-]+)\s*\]""").find(dictBody)
        val coords = coordsMatch?.groupValues?.get(1)?.split(Regex("""\s+"""))?.mapNotNull { it.toFloatOrNull() } ?: emptyList()

        val stops = mutableListOf<GradientColorStop>()

        val boundsMatch = Regex("""/Bounds\s*\[\s*([0-9.\s]+)\s*\]""").find(dictBody)
        val bounds = boundsMatch?.groupValues?.get(1)?.split(Regex("""\s+"""))?.mapNotNull { it.toFloatOrNull() } ?: emptyList()

        val c0Matches = Regex("""/C0\s*\[\s*([0-9.\s]+)\s*\]""").findAll(dictBody).toList()
        val c1Matches = Regex("""/C1\s*\[\s*([0-9.\s]+)\s*\]""").findAll(dictBody).toList()

        if (c0Matches.isNotEmpty() && c1Matches.isNotEmpty()) {
            if (c0Matches.size == 1 && bounds.isEmpty()) {
                val c0Tokens = c0Matches[0].groupValues[1].split(Regex("""\s+""")).mapNotNull { it.toFloatOrNull() }
                val c1Tokens = c1Matches[0].groupValues[1].split(Regex("""\s+""")).mapNotNull { it.toFloatOrNull() }
                stops.add(GradientColorStop(0f, parseColorComponents(c0Tokens)))
                stops.add(GradientColorStop(1f, parseColorComponents(c1Tokens)))
            } else {
                val segCount = min(c0Matches.size, c1Matches.size)
                val fullBounds = mutableListOf(0f)
                fullBounds.addAll(bounds)
                fullBounds.add(1f)

                for (s in 0 until segCount) {
                    val startPos = if (s < fullBounds.size) fullBounds[s] else (s.toFloat() / segCount)
                    val endPos = if (s + 1 < fullBounds.size) fullBounds[s + 1] else ((s + 1).toFloat() / segCount)

                    val c0Tokens = c0Matches[s].groupValues[1].split(Regex("""\s+""")).mapNotNull { it.toFloatOrNull() }
                    val c1Tokens = c1Matches[s].groupValues[1].split(Regex("""\s+""")).mapNotNull { it.toFloatOrNull() }

                    val col0 = parseColorComponents(c0Tokens)
                    val col1 = parseColorComponents(c1Tokens)

                    if (s == 0) {
                        stops.add(GradientColorStop(startPos, col0))
                    }
                    stops.add(GradientColorStop(endPos, col1))
                }
            }
        }

        if (stops.isEmpty()) return null
        return ParsedGradient(name = name, isRadial = isRadial, stops = stops, coords = coords)
    }

    private fun parseAllGradients(epsText: String): Map<String, ParsedGradient> {
        val result = mutableMapOf<String, ParsedGradient>()

        // 1. Adobe Illustrator Gradient Blocks across all versions (AI5, AI7, AI8, AI9, AI10, AI11, AI12, CC, CS)
        val aiGradRegex = Regex(
            """(?:%AI[0-9]*_BeginGradient:|%_BeginGradient:|%%BeginGradient:)\s*(?:\(([^)]+)\)|/([^\s]+))([\s\S]*?)(?:%AI[0-9]*_EndGradient|%_EndGradient|%%EndGradient)""",
            RegexOption.IGNORE_CASE
        )
        for (match in aiGradRegex.findAll(epsText)) {
            val name = (match.groupValues[1].ifEmpty { match.groupValues[2] }).trim()
            val body = match.groupValues[3]
            val isRadial = body.contains("/Radial", ignoreCase = true) ||
                    Regex("""%AI[0-9]*_GradientType:\s*2""").containsMatchIn(body) ||
                    Regex("""\b[1-9]\s+2\b""").containsMatchIn(body)
            val stops = parseGradientStops(body)
            if (stops.isNotEmpty()) {
                val grad = ParsedGradient(name, isRadial, stops)
                result[name] = grad
                result["/$name"] = grad
                result["($name)"] = grad
            }
        }

        // 2. Named PostScript Level 3 Shading dictionaries: /Name << /ShadingType ... >> def
        val psShadingRegex = Regex("""/([a-zA-Z0-9_.-]+)\s*<<([\s\S]*?/ShadingType[\s\S]*?)>>\s*(?:def|defineresource)""")
        for (match in psShadingRegex.findAll(epsText)) {
            val name = match.groupValues[1].trim()
            val dictBody = match.groupValues[2]
            val grad = parseShadingDict(dictBody, name)
            if (grad != null) {
                result[name] = grad
                result["/$name"] = grad
            }
        }

        return result
    }

    // =========================================================================
    // VECTOR EPS & POSTSCRIPT RENDERING ENGINE
    // =========================================================================

    private class GraphicsState(
        var ctm: Matrix = Matrix(),
        var fillColor: Int = Color.BLACK,
        var strokeColor: Int = Color.TRANSPARENT,
        var strokeWidth: Float = 1f,
        var lineCap: Paint.Cap = Paint.Cap.BUTT,
        var lineJoin: Paint.Join = Paint.Join.MITER,
        var dashPathEffect: DashPathEffect? = null,
        var activeShader: Shader? = null,
        var activeShaderDef: ParsedGradient? = null,
        var activeClip: Path? = null
    ) {
        fun copy(): GraphicsState {
            val c = GraphicsState(
                Matrix(ctm),
                fillColor,
                strokeColor,
                strokeWidth,
                lineCap,
                lineJoin,
                dashPathEffect,
                activeShader,
                activeShaderDef,
                null
            )
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
        val allBboxMatches = bboxRegex.findAll(epsText).toList()
        for (match in allBboxMatches) {
            val v1 = match.groupValues[1].toFloatOrNull() ?: continue
            val v2 = match.groupValues[2].toFloatOrNull() ?: continue
            val v3 = match.groupValues[3].toFloatOrNull() ?: continue
            val v4 = match.groupValues[4].toFloatOrNull() ?: continue
            val minX = min(v1, v3)
            val minY = min(v2, v4)
            val maxX = max(v1, v3)
            val maxY = max(v2, v4)
            if (maxX > minX && maxY > minY) {
                llx = minX
                lly = minY
                urx = maxX
                ury = maxY
                hasBbox = true
                break
            }
        }

        val allGradients = parseAllGradients(epsText)
        val tokens = tokenizePostScriptWithStrings(epsText).toList()

        if (!hasBbox) {
            // Coordinate scan fallback
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
                        "m", "moveto", "_m", "l", "lineto", "_l" -> {
                            if (tempStack.size >= 2) {
                                val y = tempStack.removeAt(tempStack.size - 1)
                                val x = tempStack.removeAt(tempStack.size - 1)
                                minX = min(minX, x); maxX = max(maxX, x)
                                minY = min(minY, y); maxY = max(maxY, y)
                            }
                        }
                        "c", "curveto", "_c" -> {
                            if (tempStack.size >= 6) {
                                for (k in 0 until 3) {
                                    val y = tempStack.removeAt(tempStack.size - 1)
                                    val x = tempStack.removeAt(tempStack.size - 1)
                                    minX = min(minX, x); maxX = max(maxX, x)
                                    minY = min(minY, y); maxY = max(maxY, y)
                                }
                            }
                        }
                        "re", "rectfill", "rectstroke" -> {
                            if (tempStack.size >= 4) {
                                val h = tempStack.removeAt(tempStack.size - 1)
                                val w = tempStack.removeAt(tempStack.size - 1)
                                val y = tempStack.removeAt(tempStack.size - 1)
                                val x = tempStack.removeAt(tempStack.size - 1)
                                minX = min(minX, min(x, x + w)); maxX = max(maxX, max(x, x + w))
                                minY = min(minY, min(y, y + h)); maxY = max(maxY, max(y, y + h))
                            }
                        }
                    }
                    tempStack.clear()
                }
            }

            if (minX < maxX && minY < maxY && minX.isFinite() && maxX.isFinite()) {
                llx = minX; lly = minY; urx = maxX; ury = maxY
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

        // Initial CTM converts PostScript user space (bottom-left origin) to Android screen coordinates (top-left origin)
        val initCtm = Matrix().apply {
            postTranslate(-llx, -ury)
            postScale(scale, -scale)
        }

        var state = GraphicsState(ctm = initCtm)
        val stateStack = mutableListOf<GraphicsState>()

        fun mapPoint(x: Float, y: Float): FloatArray {
            val pts = floatArrayOf(x, y)
            state.ctm.mapPoints(pts)
            return pts
        }

        var currentPath = Path()
        var currentX = 0f
        var currentY = 0f

        val numStack = mutableListOf<Float>()
        val stringStack = mutableListOf<String>()
        var drawCount = 0

        var idx = 0
        while (idx < tokens.size) {
            val tok = tokens[idx++]

            // 1. Numbers
            val num = tok.toFloatOrNull()
            if (num != null) {
                numStack.add(num)
                if (numStack.size > 100) numStack.removeAt(0)
                continue
            }

            // 2. Inline PostScript Level 3 Shading Dictionary: << ... /ShadingType ... >>
            if (tok == "<<") {
                val dictTokens = mutableListOf<String>()
                var depth = 1
                while (idx < tokens.size && depth > 0) {
                    val dt = tokens[idx++]
                    if (dt == "<<") depth++
                    else if (dt == ">>") depth--
                    if (depth > 0) dictTokens.add(dt)
                }
                val dictBody = dictTokens.joinToString(" ")
                if (dictBody.contains("ShadingType")) {
                    val inlineGrad = parseShadingDict(dictBody)
                    if (inlineGrad != null) {
                        state.activeShaderDef = inlineGrad
                    }
                }
                continue
            }

            // 3. String literals (Name) or Name literals /Name
            if (tok.startsWith("(") && tok.endsWith(")")) {
                stringStack.add(tok.substring(1, tok.length - 1))
                continue
            }
            if (tok.startsWith("/")) {
                stringStack.add(tok.substring(1))
            }

            when (tok) {
                // --- Coordinate Transformations (PostScript CTM) ---
                "translate" -> {
                    if (numStack.size >= 2) {
                        val ty = numStack.removeAt(numStack.size - 1)
                        val tx = numStack.removeAt(numStack.size - 1)
                        state.ctm.preTranslate(tx, ty)
                    }
                    numStack.clear()
                }
                "scale" -> {
                    if (numStack.size >= 2) {
                        val sy = numStack.removeAt(numStack.size - 1)
                        val sx = numStack.removeAt(numStack.size - 1)
                        state.ctm.preScale(sx, sy)
                    }
                    numStack.clear()
                }
                "rotate" -> {
                    if (numStack.isNotEmpty()) {
                        val angle = numStack.removeAt(numStack.size - 1)
                        state.ctm.preRotate(angle)
                    }
                    numStack.clear()
                }
                "concat" -> {
                    if (numStack.size >= 6) {
                        val ty = numStack.removeAt(numStack.size - 1)
                        val tx = numStack.removeAt(numStack.size - 1)
                        val d = numStack.removeAt(numStack.size - 1)
                        val c = numStack.removeAt(numStack.size - 1)
                        val b = numStack.removeAt(numStack.size - 1)
                        val a = numStack.removeAt(numStack.size - 1)
                        val m = Matrix()
                        m.setValues(floatArrayOf(a, c, tx, b, d, ty, 0f, 0f, 1f))
                        state.ctm.preConcat(m)
                    }
                    numStack.clear()
                }

                // --- Path Construction Operators (Standard + AI Underscored) ---
                "m", "moveto", "_m" -> {
                    if (numStack.size >= 2) {
                        val y = numStack.removeAt(numStack.size - 1)
                        val x = numStack.removeAt(numStack.size - 1)
                        val p = mapPoint(x, y)
                        currentPath.moveTo(p[0], p[1])
                        currentX = p[0]; currentY = p[1]
                    }
                    numStack.clear()
                }
                "l", "lineto", "_l" -> {
                    if (numStack.size >= 2) {
                        val y = numStack.removeAt(numStack.size - 1)
                        val x = numStack.removeAt(numStack.size - 1)
                        val p = mapPoint(x, y)
                        currentPath.lineTo(p[0], p[1])
                        currentX = p[0]; currentY = p[1]
                    }
                    numStack.clear()
                }
                "c", "curveto", "_c" -> {
                    if (numStack.size >= 6) {
                        val y3 = numStack.removeAt(numStack.size - 1)
                        val x3 = numStack.removeAt(numStack.size - 1)
                        val y2 = numStack.removeAt(numStack.size - 1)
                        val x2 = numStack.removeAt(numStack.size - 1)
                        val y1 = numStack.removeAt(numStack.size - 1)
                        val x1 = numStack.removeAt(numStack.size - 1)
                        val p1 = mapPoint(x1, y1)
                        val p2 = mapPoint(x2, y2)
                        val p3 = mapPoint(x3, y3)
                        currentPath.cubicTo(p1[0], p1[1], p2[0], p2[1], p3[0], p3[1])
                        currentX = p3[0]; currentY = p3[1]
                    }
                    numStack.clear()
                }
                "v", "_v" -> {
                    if (numStack.size >= 4) {
                        val y3 = numStack.removeAt(numStack.size - 1)
                        val x3 = numStack.removeAt(numStack.size - 1)
                        val y2 = numStack.removeAt(numStack.size - 1)
                        val x2 = numStack.removeAt(numStack.size - 1)
                        val p2 = mapPoint(x2, y2)
                        val p3 = mapPoint(x3, y3)
                        currentPath.cubicTo(currentX, currentY, p2[0], p2[1], p3[0], p3[1])
                        currentX = p3[0]; currentY = p3[1]
                    }
                    numStack.clear()
                }
                "y", "_y" -> {
                    if (numStack.size >= 4) {
                        val y3 = numStack.removeAt(numStack.size - 1)
                        val x3 = numStack.removeAt(numStack.size - 1)
                        val y1 = numStack.removeAt(numStack.size - 1)
                        val x1 = numStack.removeAt(numStack.size - 1)
                        val p1 = mapPoint(x1, y1)
                        val p3 = mapPoint(x3, y3)
                        currentPath.cubicTo(p1[0], p1[1], p3[0], p3[1], p3[0], p3[1])
                        currentX = p3[0]; currentY = p3[1]
                    }
                    numStack.clear()
                }
                "rlineto" -> {
                    if (numStack.size >= 2) {
                        val dy = numStack.removeAt(numStack.size - 1)
                        val dx = numStack.removeAt(numStack.size - 1)
                        val p0 = mapPoint(0f, 0f)
                        val p1 = mapPoint(dx, dy)
                        currentPath.rLineTo(p1[0] - p0[0], p1[1] - p0[1])
                        currentX += p1[0] - p0[0]; currentY += p1[1] - p0[1]
                    }
                    numStack.clear()
                }
                "rmoveto" -> {
                    if (numStack.size >= 2) {
                        val dy = numStack.removeAt(numStack.size - 1)
                        val dx = numStack.removeAt(numStack.size - 1)
                        val p0 = mapPoint(0f, 0f)
                        val p1 = mapPoint(dx, dy)
                        currentPath.rMoveTo(p1[0] - p0[0], p1[1] - p0[1])
                        currentX += p1[0] - p0[0]; currentY += p1[1] - p0[1]
                    }
                    numStack.clear()
                }
                "re", "rectfill", "rectstroke" -> {
                    if (numStack.size >= 4) {
                        val h = numStack.removeAt(numStack.size - 1)
                        val w = numStack.removeAt(numStack.size - 1)
                        val y = numStack.removeAt(numStack.size - 1)
                        val x = numStack.removeAt(numStack.size - 1)
                        val p0 = mapPoint(x, y)
                        val p1 = mapPoint(x + w, y)
                        val p2 = mapPoint(x + w, y + h)
                        val p3 = mapPoint(x, y + h)
                        val rPath = Path().apply {
                            moveTo(p0[0], p0[1])
                            lineTo(p1[0], p1[1])
                            lineTo(p2[0], p2[1])
                            lineTo(p3[0], p3[1])
                            close()
                        }
                        if (tok == "rectfill") {
                            fillPaint.shader = state.activeShader
                            fillPaint.color = state.fillColor
                            canvas.drawPath(rPath, fillPaint)
                            drawCount++
                        } else if (tok == "rectstroke") {
                            strokePaint.color = state.strokeColor
                            strokePaint.strokeWidth = state.strokeWidth
                            strokePaint.strokeCap = state.lineCap
                            strokePaint.strokeJoin = state.lineJoin
                            strokePaint.pathEffect = state.dashPathEffect
                            canvas.drawPath(rPath, strokePaint)
                            drawCount++
                        } else {
                            currentPath.addPath(rPath)
                        }
                    }
                    numStack.clear()
                }
                "h", "cp", "closepath", "_h" -> {
                    currentPath.close()
                    numStack.clear()
                }
                "n", "newpath", "_n" -> {
                    currentPath = Path()
                    numStack.clear()
                }

                // --- Color Operators ---
                "rg", "setrgbcolor", "_rg" -> {
                    if (numStack.size >= 3) {
                        val b = numStack.removeAt(numStack.size - 1)
                        val g = numStack.removeAt(numStack.size - 1)
                        val r = numStack.removeAt(numStack.size - 1)
                        state.fillColor = parseColorComponents(listOf(r, g, b))
                        state.activeShader = null
                        state.activeShaderDef = null
                    }
                    numStack.clear()
                }
                "RG", "_RG" -> {
                    if (numStack.size >= 3) {
                        val b = numStack.removeAt(numStack.size - 1)
                        val g = numStack.removeAt(numStack.size - 1)
                        val r = numStack.removeAt(numStack.size - 1)
                        state.strokeColor = parseColorComponents(listOf(r, g, b))
                    }
                    numStack.clear()
                }
                "k", "setcmykcolor", "_k" -> {
                    if (numStack.size >= 4) {
                        val k = numStack.removeAt(numStack.size - 1)
                        val y = numStack.removeAt(numStack.size - 1)
                        val m = numStack.removeAt(numStack.size - 1)
                        val c = numStack.removeAt(numStack.size - 1)
                        state.fillColor = parseColorComponents(listOf(c, m, y, k))
                        state.activeShader = null
                        state.activeShaderDef = null
                    }
                    numStack.clear()
                }
                "K", "_K" -> {
                    if (numStack.size >= 4) {
                        val k = numStack.removeAt(numStack.size - 1)
                        val y = numStack.removeAt(numStack.size - 1)
                        val m = numStack.removeAt(numStack.size - 1)
                        val c = numStack.removeAt(numStack.size - 1)
                        state.strokeColor = parseColorComponents(listOf(c, m, y, k))
                    }
                    numStack.clear()
                }
                "g", "setgray", "_g" -> {
                    if (numStack.isNotEmpty()) {
                        val gray = numStack.removeAt(numStack.size - 1)
                        state.fillColor = parseColorComponents(listOf(gray))
                        state.activeShader = null
                        state.activeShaderDef = null
                    }
                    numStack.clear()
                }
                "G", "_G" -> {
                    if (numStack.isNotEmpty()) {
                        val gray = numStack.removeAt(numStack.size - 1)
                        state.strokeColor = parseColorComponents(listOf(gray))
                    }
                    numStack.clear()
                }
                "x", "xx" -> {
                    if (numStack.isNotEmpty()) {
                        state.fillColor = parseColorComponents(numStack)
                        state.activeShader = null
                        state.activeShaderDef = null
                    }
                    numStack.clear()
                }
                "X", "Xx" -> {
                    if (numStack.isNotEmpty()) {
                        state.strokeColor = parseColorComponents(numStack)
                    }
                    numStack.clear()
                }
                "w", "setlinewidth", "_w" -> {
                    if (numStack.isNotEmpty()) {
                        val w = numStack.removeAt(numStack.size - 1)
                        val p0 = mapPoint(0f, 0f)
                        val p1 = mapPoint(w, 0f)
                        val screenW = sqrt((p1[0] - p0[0]) * (p1[0] - p0[0]) + (p1[1] - p0[1]) * (p1[1] - p0[1]))
                        state.strokeWidth = max(0.5f, screenW)
                    }
                    numStack.clear()
                }
                "J", "setlinecap", "_J" -> {
                    if (numStack.isNotEmpty()) {
                        val cap = numStack.removeAt(numStack.size - 1).toInt()
                        state.lineCap = when (cap) {
                            1 -> Paint.Cap.ROUND
                            2 -> Paint.Cap.SQUARE
                            else -> Paint.Cap.BUTT
                        }
                    }
                    numStack.clear()
                }
                "j", "setlinejoin", "_j" -> {
                    if (numStack.isNotEmpty()) {
                        val join = numStack.removeAt(numStack.size - 1).toInt()
                        state.lineJoin = when (join) {
                            1 -> Paint.Join.ROUND
                            2 -> Paint.Join.BEVEL
                            else -> Paint.Join.MITER
                        }
                    }
                    numStack.clear()
                }
                "d", "setdash", "_d" -> {
                    if (numStack.isNotEmpty()) {
                        val offset = numStack.removeAt(numStack.size - 1)
                        if (numStack.isNotEmpty()) {
                            val intervals = numStack.map { max(1f, it * scale) }.toFloatArray()
                            if (intervals.size % 2 == 0 && intervals.isNotEmpty()) {
                                state.dashPathEffect = DashPathEffect(intervals, offset * scale)
                            }
                        } else {
                            state.dashPathEffect = null
                        }
                    }
                    numStack.clear()
                }

                // --- Gradient Application Operators ---
                "_Xg", "_xg", "Xg", "xg",
                "_Yg", "_yg", "Yg", "yg",
                "_Bg", "_bg", "Bg", "bg",
                "_Ag", "_ag", "Ag", "ag",
                "shfill", "_sh" -> {
                    val isRadialOp = tok in listOf("_Yg", "_yg", "Yg", "yg", "_Ag", "_ag", "Ag", "ag")
                    val isStrokeAlso = tok in listOf("_Bg", "_bg", "Bg", "bg", "_Ag", "_ag", "Ag", "ag")

                    // 1. Resolve Gradient definition
                    var gradDef: ParsedGradient? = null
                    val candidateName = stringStack.lastOrNull { allGradients.containsKey(it) }
                    if (candidateName != null) {
                        gradDef = allGradients[candidateName]
                    }
                    if (gradDef == null && state.activeShaderDef != null) {
                        gradDef = state.activeShaderDef
                    }
                    if (gradDef == null && allGradients.isNotEmpty()) {
                        gradDef = allGradients.values.firstOrNull { it.isRadial == isRadialOp } ?: allGradients.values.last()
                    }

                    // 2. Compute Target Bounds from current shape path (or active clip)
                    val bounds = RectF()
                    val pathForBounds = if (!currentPath.isEmpty) currentPath else state.activeClip
                    pathForBounds?.computeBounds(bounds, true)
                    if (bounds.isEmpty || bounds.width() <= 0f || bounds.height() <= 0f) {
                        bounds.set(0f, 0f, previewWidth.toFloat(), previewHeight.toFloat())
                    }

                    // 3. Construct Gradient Shader with accurate shape coordinates
                    val shader: Shader? = if (gradDef != null && gradDef.stops.isNotEmpty()) {
                        val colors = gradDef.stops.map { it.color }.toIntArray()
                        val positions = gradDef.stops.map { it.position }.toFloatArray()
                        val isRad = isRadialOp || gradDef.isRadial

                        if (gradDef.coords.size >= 4) {
                            if (gradDef.coords.size >= 6 && isRad) {
                                val p1 = mapPoint(gradDef.coords[0], gradDef.coords[1])
                                val p2 = mapPoint(gradDef.coords[3], gradDef.coords[4])
                                val r = abs(mapPoint(gradDef.coords[5], 0f)[0] - mapPoint(0f, 0f)[0]).coerceAtLeast(10f)
                                RadialGradient(p2[0], p2[1], r, colors, positions, Shader.TileMode.CLAMP)
                            } else {
                                val p1 = mapPoint(gradDef.coords[0], gradDef.coords[1])
                                val p2 = mapPoint(gradDef.coords[2], gradDef.coords[3])
                                LinearGradient(p1[0], p1[1], p2[0], p2[1], colors, positions, Shader.TileMode.CLAMP)
                            }
                        } else if (isRad) {
                            val cx = bounds.centerX()
                            val cy = bounds.centerY()
                            val r = max(1f, max(bounds.width(), bounds.height()) / 2f)
                            RadialGradient(cx, cy, r, colors, positions, Shader.TileMode.CLAMP)
                        } else {
                            val sx = bounds.left
                            val sy = bounds.top
                            val ex = bounds.right
                            val ey = bounds.bottom
                            LinearGradient(sx, sy, ex, ey, colors, positions, Shader.TileMode.CLAMP)
                        }
                    } else if (state.fillColor != Color.BLACK && state.fillColor != Color.TRANSPARENT) {
                        val c0 = state.fillColor
                        val c1 = Color.rgb(
                            (Color.red(c0) * 0.7f + 70).toInt().coerceIn(0, 255),
                            (Color.green(c0) * 0.7f + 70).toInt().coerceIn(0, 255),
                            (Color.blue(c0) * 0.7f + 70).toInt().coerceIn(0, 255)
                        )
                        LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom, c0, c1, Shader.TileMode.CLAMP)
                    } else null

                    // 4. Render Gradient Fill to shape
                    if (shader != null) {
                        fillPaint.shader = shader
                        val drawPath = if (!currentPath.isEmpty) currentPath else state.activeClip
                        if (drawPath != null && !drawPath.isEmpty) {
                            canvas.drawPath(drawPath, fillPaint)
                        } else {
                            canvas.drawRect(0f, 0f, previewWidth.toFloat(), previewHeight.toFloat(), fillPaint)
                        }
                        fillPaint.shader = null
                    } else {
                        fillPaint.color = state.fillColor
                        val drawPath = if (!currentPath.isEmpty) currentPath else state.activeClip
                        if (drawPath != null && !drawPath.isEmpty) {
                            canvas.drawPath(drawPath, fillPaint)
                        }
                    }

                    if (isStrokeAlso && state.strokeColor != Color.TRANSPARENT && state.strokeWidth > 0f) {
                        strokePaint.color = state.strokeColor
                        strokePaint.strokeWidth = state.strokeWidth
                        strokePaint.strokeCap = state.lineCap
                        strokePaint.strokeJoin = state.lineJoin
                        strokePaint.pathEffect = state.dashPathEffect
                        val drawPath = if (!currentPath.isEmpty) currentPath else state.activeClip
                        if (drawPath != null && !drawPath.isEmpty) {
                            canvas.drawPath(drawPath, strokePaint)
                        }
                    }

                    currentPath = Path()
                    drawCount++
                    numStack.clear()
                    stringStack.clear()
                    state.activeShader = null
                    state.activeShaderDef = null
                }

                // --- Standard Drawing Operators ---
                "f", "F", "f*", "fill", "eofill", "_f", "_f*" -> {
                    val isEvenOdd = tok in listOf("f*", "eofill", "_f*")
                    currentPath.fillType = if (isEvenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
                    if (state.activeShader != null) {
                        fillPaint.shader = state.activeShader
                    } else {
                        fillPaint.shader = null
                        fillPaint.color = state.fillColor
                    }
                    canvas.drawPath(currentPath, fillPaint)
                    fillPaint.shader = null
                    currentPath = Path()
                    drawCount++
                    numStack.clear()
                }
                "s", "S", "stroke", "_s" -> {
                    strokePaint.color = state.strokeColor
                    strokePaint.strokeWidth = state.strokeWidth
                    strokePaint.strokeCap = state.lineCap
                    strokePaint.strokeJoin = state.lineJoin
                    strokePaint.pathEffect = state.dashPathEffect
                    canvas.drawPath(currentPath, strokePaint)
                    currentPath = Path()
                    drawCount++
                    numStack.clear()
                }
                "b", "B", "b*", "B*", "_b", "_b*" -> {
                    val isEvenOdd = tok in listOf("b*", "B*", "_b*")
                    currentPath.fillType = if (isEvenOdd) Path.FillType.EVEN_ODD else Path.FillType.WINDING
                    if (state.activeShader != null) {
                        fillPaint.shader = state.activeShader
                    } else {
                        fillPaint.shader = null
                        fillPaint.color = state.fillColor
                    }
                    canvas.drawPath(currentPath, fillPaint)
                    fillPaint.shader = null

                    if (state.strokeColor != Color.TRANSPARENT && state.strokeWidth > 0f) {
                        strokePaint.color = state.strokeColor
                        strokePaint.strokeWidth = state.strokeWidth
                        strokePaint.strokeCap = state.lineCap
                        strokePaint.strokeJoin = state.lineJoin
                        strokePaint.pathEffect = state.dashPathEffect
                        canvas.drawPath(currentPath, strokePaint)
                    }
                    currentPath = Path()
                    drawCount++
                    numStack.clear()
                }

                // --- Clipping & State Stack ---
                "clip", "eoclip", "W", "W*", "_W", "_W*" -> {
                    if (!currentPath.isEmpty) {
                        val clipCopy = Path(currentPath)
                        state.activeClip = clipCopy
                        try {
                            canvas.clipPath(clipCopy)
                        } catch (_: Exception) {}
                    }
                    numStack.clear()
                }
                "gsave", "q", "_q", "_gs" -> {
                    canvas.save()
                    stateStack.add(state.copy())
                    numStack.clear()
                }
                "grestore", "Q", "_Q", "_gr" -> {
                    try {
                        canvas.restore()
                    } catch (_: Exception) {}
                    if (stateStack.isNotEmpty()) {
                        state = stateStack.removeAt(stateStack.size - 1)
                    }
                    numStack.clear()
                }
                else -> {
                    if (numStack.size > 20) numStack.clear()
                }
            }
        }

        return if (drawCount > 0) bitmap else null
    }

    private fun tokenizePostScriptWithStrings(text: String): Sequence<String> {
        return sequence {
            val sb = StringBuilder()
            var inComment = false
            var inString = false
            var parensDepth = 0
            var i = 0

            while (i < text.length) {
                val c = text[i]

                if (inComment) {
                    if (c == '\n' || c == '\r') {
                        inComment = false
                    }
                    i++
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
                    i++
                    continue
                }

                if (c == '%') {
                    if (sb.isNotEmpty()) {
                        yield(sb.toString())
                        sb.clear()
                    }
                    inComment = true
                    i++
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
                    i++
                    continue
                }

                if (c == '<' && i + 1 < text.length && text[i + 1] == '<') {
                    if (sb.isNotEmpty()) {
                        yield(sb.toString())
                        sb.clear()
                    }
                    yield("<<")
                    i += 2
                    continue
                }

                if (c == '>' && i + 1 < text.length && text[i + 1] == '>') {
                    if (sb.isNotEmpty()) {
                        yield(sb.toString())
                        sb.clear()
                    }
                    yield(">>")
                    i += 2
                    continue
                }

                if (c == '[' || c == ']' || c == '{' || c == '}') {
                    if (sb.isNotEmpty()) {
                        yield(sb.toString())
                        sb.clear()
                    }
                    yield(c.toString())
                    i++
                    continue
                }

                if (c.isWhitespace()) {
                    if (sb.isNotEmpty()) {
                        yield(sb.toString())
                        sb.clear()
                    }
                    i++
                    continue
                }

                sb.append(c)
                i++
            }
            if (sb.isNotEmpty()) {
                yield(sb.toString())
            }
        }
    }

    // =========================================================================
    // EMBEDDED BITMAP & TIFF FALLBACK EXTRACTION
    // =========================================================================

    private fun extractEmbeddedBitmap(epsBytes: ByteArray): Bitmap? {
        if (epsBytes.size < 32) return null

        val b0 = epsBytes[0].toInt() and 0xFF
        val b1 = epsBytes[1].toInt() and 0xFF
        val b2 = epsBytes[2].toInt() and 0xFF
        val b3 = epsBytes[3].toInt() and 0xFF

        if (b0 == 0xC5 && b1 == 0xD0 && b2 == 0xD3 && b3 == 0xC6) {
            val tiffOffset = getUInt32LE(epsBytes, 20)
            val tiffLength = getUInt32LE(epsBytes, 24)
            if (tiffOffset > 0 && tiffLength > 0 && tiffOffset + tiffLength <= epsBytes.size) {
                val tiffBmp = TiffDecoder.decodeTiff(epsBytes, tiffOffset, tiffLength)
                if (tiffBmp != null) return tiffBmp

                try {
                    val bmp = BitmapFactory.decodeByteArray(epsBytes, tiffOffset, tiffLength)
                    if (bmp != null) return bmp
                } catch (_: Exception) {}
            }

            val wmfOffset = getUInt32LE(epsBytes, 12)
            val wmfLength = getUInt32LE(epsBytes, 16)
            if (wmfOffset > 0 && wmfLength > 0 && wmfOffset + wmfLength <= epsBytes.size) {
                try {
                    val bmp = BitmapFactory.decodeByteArray(epsBytes, wmfOffset, wmfLength)
                    if (bmp != null) return bmp
                } catch (_: Exception) {}
            }
        }

        // Search for embedded JPEG (FF D8 FF ... FF D9)
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

        // Search for embedded PNG
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
    // TIFF DECODER (PACKBITS, DEFLATE, RGB, CMYK, PALETTE)
    // =========================================================================

    private object TiffDecoder {

        fun decodeTiff(data: ByteArray, offset: Int, length: Int): Bitmap? {
            try {
                if (length < 8 || offset + length > data.size) return null

                val isLE = data[offset] == 'I'.code.toByte() && data[offset + 1] == 'I'.code.toByte()
                val isBE = data[offset] == 'M'.code.toByte() && data[offset + 1] == 'M'.code.toByte()
                if (!isLE && !isBE) return null

                val magic = getUInt16(data, offset + 2, isLE)
                if (magic != 42) return null

                val ifdOffset = getUInt32(data, offset + 4, isLE)
                if (ifdOffset <= 0 || ifdOffset >= length) return null

                var width = 0
                var height = 0
                var bitsPerSample = 8
                var compression = 1
                var photometric = 2
                var samplesPerPixel = 1
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
                            if (count == 1) {
                                stripOffsets.add(if (type == 3) getUInt16(data, entryPos + 8, isLE) else valOffset)
                            } else {
                                readIntArray(data, offset, valOffset, count, type, isLE, stripOffsets)
                            }
                        }
                        277 -> samplesPerPixel = if (type == 3) getUInt16(data, entryPos + 8, isLE) else valOffset
                        279 -> {
                            if (count == 1) {
                                stripByteCounts.add(if (type == 3) getUInt16(data, entryPos + 8, isLE) else valOffset)
                            } else {
                                readIntArray(data, offset, valOffset, count, type, isLE, stripByteCounts)
                            }
                        }
                        320 -> {
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

                val rawBuffer = ByteArrayOutputStream(width * height * max(1, samplesPerPixel))
                for (s in stripOffsets.indices) {
                    val sOffset = offset + stripOffsets[s]
                    val sLength = if (s < stripByteCounts.size) stripByteCounts[s] else (length - stripOffsets[s])
                    if (sOffset < offset || sOffset + sLength > offset + length || sLength <= 0) continue

                    when (compression) {
                        1 -> rawBuffer.write(data, sOffset, sLength)
                        32773 -> decompressPackBits(data, sOffset, sLength, rawBuffer)
                        8 -> decompressDeflate(data, sOffset, sLength, rawBuffer)
                        else -> rawBuffer.write(data, sOffset, sLength)
                    }
                }

                val decompressedBytes = rawBuffer.toByteArray()
                if (decompressedBytes.isEmpty()) return null

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val pixels = IntArray(width * height)

                if (photometric == 2 && samplesPerPixel >= 3) {
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
                    for (p in 0 until min(pixels.size, decompressedBytes.size)) {
                        val cIdx = decompressedBytes[p].toInt() and 0xFF
                        pixels[p] = if (cIdx < colorMap.size) colorMap[cIdx] else Color.BLACK
                    }
                } else if (photometric == 5 && samplesPerPixel >= 4) {
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
