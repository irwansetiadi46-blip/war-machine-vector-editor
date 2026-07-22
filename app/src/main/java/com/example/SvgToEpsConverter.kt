package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

object SvgToEpsConverter {

    fun convertSvgToEps(
        svgBytes: ByteArray,
        title: String = "",
        description: String = "",
        keywords: List<String> = emptyList(),
        creator: String = ""
    ): ByteArray {
        try {
            val svgStr = String(svgBytes, StandardCharsets.UTF_8)

            // 1. Determine Canvas Dimensions (Width and Height)
            var width = 512f
            var height = 512f

            val viewBoxRegex = Regex("""viewBox=["']\s*([-\d.]+)\s+([-\d.]+)\s+([-\d.]+)\s+([-\d.]+)\s*["']""", RegexOption.IGNORE_CASE)
            val viewBoxMatch = viewBoxRegex.find(svgStr)

            val widthRegex = Regex("""width=["']([0-9.]+)(px|pt|mm|cm|in)?["']""", RegexOption.IGNORE_CASE)
            val heightRegex = Regex("""height=["']([0-9.]+)(px|pt|mm|cm|in)?["']""", RegexOption.IGNORE_CASE)

            val wMatch = widthRegex.find(svgStr)
            val hMatch = heightRegex.find(svgStr)

            if (viewBoxMatch != null) {
                val vbW = viewBoxMatch.groupValues[3].toFloatOrNull() ?: 512f
                val vbH = viewBoxMatch.groupValues[4].toFloatOrNull() ?: 512f
                if (vbW > 0) width = vbW
                if (vbH > 0) height = vbH
            } else if (wMatch != null && hMatch != null) {
                val wVal = wMatch.groupValues[1].toFloatOrNull() ?: 512f
                val hVal = hMatch.groupValues[1].toFloatOrNull() ?: 512f
                if (wVal > 0) width = wVal
                if (hVal > 0) height = hVal
            }

            // 2. Build PostScript EPS Header
            val psBuilder = StringBuilder()
            psBuilder.append("%!PS-Adobe-3.0 EPSF-3.0\n")
            psBuilder.append("%%Creator: WarMachineHybrid SVG Converter\n")
            psBuilder.append(String.format(Locale.US, "%%%%BoundingBox: 0 0 %d %d\n", width.toInt(), height.toInt()))
            psBuilder.append(String.format(Locale.US, "%%%%HiResBoundingBox: 0 0 %.3f %.3f\n", width, height))
            psBuilder.append("%%LanguageLevel: 2\n")
            psBuilder.append("%%Pages: 1\n")
            psBuilder.append("%%EndComments\n\n")

            // 3. Extract and convert vector shapes
            val psCommands = parseSvgElementsToPostScript(svgStr, height)
            psBuilder.append(psCommands)

            psBuilder.append("\nshowpage\n%%EOF\n")

            val rawEpsBytes = psBuilder.toString().toByteArray(StandardCharsets.UTF_8)

            // 4. Inject Metadata into EPS
            return XmpInjector.injectIntoEps(
                originalBytes = rawEpsBytes,
                title = title,
                description = description,
                keywords = keywords,
                creator = creator
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return svgBytes
        }
    }

    private fun parseSvgElementsToPostScript(svgStr: String, canvasHeight: Float): String {
        val sb = StringBuilder()

        // Match shape elements: path, rect, circle, ellipse, line, polygon, polyline
        val elementRegex = Regex("""<(path|rect|circle|ellipse|line|polygon|polyline)\b([^>]*)/?>""", RegexOption.IGNORE_CASE)
        val matches = elementRegex.findAll(svgStr)

        for (match in matches) {
            val tagName = match.groupValues[1].lowercase(Locale.US)
            val attrStr = match.groupValues[2]

            val attrs = parseAttributes(attrStr)

            // Convert shapes to path 'd' string
            val pathData = when (tagName) {
                "path" -> attrs["d"] ?: ""
                "rect" -> convertRectToPath(attrs)
                "circle" -> convertCircleToPath(attrs)
                "ellipse" -> convertEllipseToPath(attrs)
                "line" -> convertLineToPath(attrs)
                "polygon", "polyline" -> convertPolygonToPath(attrs, tagName == "polygon")
                else -> ""
            }

            if (pathData.isBlank()) continue

            val fillAttr = attrs["fill"] ?: "black"
            val strokeAttr = attrs["stroke"] ?: "none"
            val strokeWidthAttr = attrs["stroke-width"]?.toFloatOrNull() ?: 1f
            val fillRule = attrs["fill-rule"] ?: "nonzero"

            val fillRgb = parseColorToRgb(fillAttr)
            val strokeRgb = parseColorToRgb(strokeAttr)

            val psPath = svgPathDToPostScript(pathData, canvasHeight)
            if (psPath.isBlank()) continue

            // Fill
            if (fillRgb != null) {
                sb.append("gsave\n")
                sb.append("newpath\n")
                sb.append(psPath)
                sb.append(String.format(Locale.US, "%.3f %.3f %.3f setrgbcolor\n", fillRgb[0], fillRgb[1], fillRgb[2]))
                if (fillRule == "evenodd") {
                    sb.append("eofill\n")
                } else {
                    sb.append("fill\n")
                }
                sb.append("grestore\n")
            }

            // Stroke
            if (strokeRgb != null && strokeWidthAttr > 0) {
                sb.append("gsave\n")
                sb.append("newpath\n")
                sb.append(psPath)
                sb.append(String.format(Locale.US, "%.3f setlinewidth\n", strokeWidthAttr))
                sb.append("1 setlinejoin\n1 setlinecap\n")
                sb.append(String.format(Locale.US, "%.3f %.3f %.3f setrgbcolor\n", strokeRgb[0], strokeRgb[1], strokeRgb[2]))
                sb.append("stroke\n")
                sb.append("grestore\n")
            }
        }

        return sb.toString()
    }

    private fun parseAttributes(attrStr: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val regex = Regex("""([a-zA-Z0-9_-]+)=["']([^"']*)["']""")
        for (match in regex.findAll(attrStr)) {
            map[match.groupValues[1].lowercase(Locale.US)] = match.groupValues[2]
        }
        return map
    }

    private fun parseColorToRgb(colorStr: String): FloatArray? {
        val c = colorStr.trim().lowercase(Locale.US)
        if (c == "none" || c == "transparent" || c.isEmpty()) return null

        if (c.startsWith("#")) {
            val hex = c.substring(1)
            return when (hex.length) {
                3 -> {
                    val r = hex[0].toString().repeat(2).toInt(16) / 255f
                    val g = hex[1].toString().repeat(2).toInt(16) / 255f
                    val b = hex[2].toString().repeat(2).toInt(16) / 255f
                    floatArrayOf(r, g, b)
                }
                6 -> {
                    val r = hex.substring(0, 2).toInt(16) / 255f
                    val g = hex.substring(2, 4).toInt(16) / 255f
                    val b = hex.substring(4, 6).toInt(16) / 255f
                    floatArrayOf(r, g, b)
                }
                else -> floatArrayOf(0f, 0f, 0f)
            }
        }

        if (c.startsWith("rgb")) {
            val nums = Regex("""\d+""").findAll(c).map { it.value.toFloat() / 255f }.toList()
            if (nums.size >= 3) {
                return floatArrayOf(
                    nums[0].coerceIn(0f, 1f),
                    nums[1].coerceIn(0f, 1f),
                    nums[2].coerceIn(0f, 1f)
                )
            }
        }

        return when (c) {
            "white" -> floatArrayOf(1f, 1f, 1f)
            "black" -> floatArrayOf(0f, 0f, 0f)
            "red" -> floatArrayOf(1f, 0f, 0f)
            "green" -> floatArrayOf(0f, 0.5f, 0f)
            "blue" -> floatArrayOf(0f, 0f, 1f)
            "yellow" -> floatArrayOf(1f, 1f, 0f)
            "cyan" -> floatArrayOf(0f, 1f, 1f)
            "magenta" -> floatArrayOf(1f, 0f, 1f)
            "gray", "grey" -> floatArrayOf(0.5f, 0.5f, 0.5f)
            else -> floatArrayOf(0f, 0f, 0f)
        }
    }

    private fun convertRectToPath(attrs: Map<String, String>): String {
        val x = attrs["x"]?.toFloatOrNull() ?: 0f
        val y = attrs["y"]?.toFloatOrNull() ?: 0f
        val w = attrs["width"]?.toFloatOrNull() ?: 0f
        val h = attrs["height"]?.toFloatOrNull() ?: 0f
        if (w <= 0f || h <= 0f) return ""
        return "M $x $y L ${x + w} $y L ${x + w} ${y + h} L $x ${y + h} Z"
    }

    private fun convertCircleToPath(attrs: Map<String, String>): String {
        val cx = attrs["cx"]?.toFloatOrNull() ?: 0f
        val cy = attrs["cy"]?.toFloatOrNull() ?: 0f
        val r = attrs["r"]?.toFloatOrNull() ?: 0f
        if (r <= 0f) return ""
        return convertEllipseToPath(mapOf("cx" to "$cx", "cy" to "$cy", "rx" to "$r", "ry" to "$r"))
    }

    private fun convertEllipseToPath(attrs: Map<String, String>): String {
        val cx = attrs["cx"]?.toFloatOrNull() ?: 0f
        val cy = attrs["cy"]?.toFloatOrNull() ?: 0f
        val rx = attrs["rx"]?.toFloatOrNull() ?: 0f
        val ry = attrs["ry"]?.toFloatOrNull() ?: 0f
        if (rx <= 0f || ry <= 0f) return ""

        val k = 0.55228475f
        val kx = rx * k
        val ky = ry * k

        return String.format(
            Locale.US,
            "M %.3f %.3f C %.3f %.3f %.3f %.3f %.3f %.3f C %.3f %.3f %.3f %.3f %.3f %.3f C %.3f %.3f %.3f %.3f %.3f %.3f C %.3f %.3f %.3f %.3f %.3f %.3f Z",
            cx + rx, cy,
            cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry,
            cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy,
            cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry,
            cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy
        )
    }

    private fun convertLineToPath(attrs: Map<String, String>): String {
        val x1 = attrs["x1"]?.toFloatOrNull() ?: 0f
        val y1 = attrs["y1"]?.toFloatOrNull() ?: 0f
        val x2 = attrs["x2"]?.toFloatOrNull() ?: 0f
        val y2 = attrs["y2"]?.toFloatOrNull() ?: 0f
        return "M $x1 $y1 L $x2 $y2"
    }

    private fun convertPolygonToPath(attrs: Map<String, String>, isClosed: Boolean): String {
        val pointsStr = attrs["points"] ?: return ""
        val tokens = pointsStr.trim().split(Regex("""[\s,]+""")).filter { it.isNotEmpty() }
        if (tokens.size < 4) return ""

        val sb = StringBuilder()
        var i = 0
        while (i + 1 < tokens.size) {
            val x = tokens[i].toFloatOrNull() ?: 0f
            val y = tokens[i + 1].toFloatOrNull() ?: 0f
            if (i == 0) {
                sb.append("M $x $y ")
            } else {
                sb.append("L $x $y ")
            }
            i += 2
        }
        if (isClosed) sb.append("Z")
        return sb.toString().trim()
    }

    private fun svgPathDToPostScript(pathData: String, canvasHeight: Float): String {
        val tokens = tokenizePathData(pathData)
        if (tokens.isEmpty()) return ""

        val sb = StringBuilder()
        var i = 0
        var currentX = 0f
        var currentY = 0f
        var startX = 0f
        var startY = 0f
        var lastCommand = ""

        fun toPsY(y: Float): Float = canvasHeight - y

        while (i < tokens.size) {
            val token = tokens[i]
            val cmd = token[0]
            val isRelative = cmd.isLowerCase()
            val upperCmd = cmd.uppercase()
            val isUpperCmd = cmd.isUpperCase()

            if (token.length == 1 && token[0].isLetter()) {
                i++
                lastCommand = token
            } else if (lastCommand.isNotEmpty()) {
                // Repeating previous command parameters
            } else {
                break
            }

            val activeCmd = if (token.length == 1 && token[0].isLetter()) upperCmd[0] else lastCommand.uppercase()[0]
            val relative = if (token.length == 1 && token[0].isLetter()) isRelative else lastCommand[0].isLowerCase()

            when (activeCmd) {
                'M' -> {
                    val x = tokens.getOrNull(i++)?.toFloatOrNull() ?: 0f
                    val y = tokens.getOrNull(i++)?.toFloatOrNull() ?: 0f
                    val finalX = if (relative) currentX + x else x
                    val finalY = if (relative) currentY + y else y

                    sb.append(String.format(Locale.US, "%.3f %.3f moveto\n", finalX, toPsY(finalY)))
                    currentX = finalX
                    currentY = finalY
                    startX = finalX
                    startY = finalY

                    // Subsequent pairs treated as lineto
                    lastCommand = if (relative) "m" else "M"
                }
                'L' -> {
                    val x = tokens.getOrNull(i++)?.toFloatOrNull() ?: 0f
                    val y = tokens.getOrNull(i++)?.toFloatOrNull() ?: 0f
                    val finalX = if (relative) currentX + x else x
                    val finalY = if (relative) currentY + y else y

                    sb.append(String.format(Locale.US, "%.3f %.3f lineto\n", finalX, toPsY(finalY)))
                    currentX = finalX
                    currentY = finalY
                }
                'H' -> {
                    val x = tokens.getOrNull(i++)?.toFloatOrNull() ?: 0f
                    val finalX = if (relative) currentX + x else x

                    sb.append(String.format(Locale.US, "%.3f %.3f lineto\n", finalX, toPsY(currentY)))
                    currentX = finalX
                }
                'V' -> {
                    val y = tokens.getOrNull(i++)?.toFloatOrNull() ?: 0f
                    val finalY = if (relative) currentY + y else y

                    sb.append(String.format(Locale.US, "%.3f %.3f lineto\n", currentX, toPsY(finalY)))
                    currentY = finalY
                }
                'C' -> {
                    val x1 = tokens.getOrNull(i++)?.toFloatOrNull() ?: 0f
                    val y1 = tokens.getOrNull(i++)?.toFloatOrNull() ?: 0f
                    val x2 = tokens.getOrNull(i++)?.toFloatOrNull() ?: 0f
                    val y2 = tokens.getOrNull(i++)?.toFloatOrNull() ?: 0f
                    val x = tokens.getOrNull(i++)?.toFloatOrNull() ?: 0f
                    val y = tokens.getOrNull(i++)?.toFloatOrNull() ?: 0f

                    val fx1 = if (relative) currentX + x1 else x1
                    val fy1 = if (relative) currentY + y1 else y1
                    val fx2 = if (relative) currentX + x2 else x2
                    val fy2 = if (relative) currentY + y2 else y2
                    val fx = if (relative) currentX + x else x
                    val fy = if (relative) currentY + y else y

                    sb.append(String.format(
                        Locale.US, "%.3f %.3f %.3f %.3f %.3f %.3f curveto\n",
                        fx1, toPsY(fy1), fx2, toPsY(fy2), fx, toPsY(fy)
                    ))
                    currentX = fx
                    currentY = fy
                }
                'Q' -> {
                    val x1 = tokens.getOrNull(i++)?.toFloatOrNull() ?: 0f
                    val y1 = tokens.getOrNull(i++)?.toFloatOrNull() ?: 0f
                    val x = tokens.getOrNull(i++)?.toFloatOrNull() ?: 0f
                    val y = tokens.getOrNull(i++)?.toFloatOrNull() ?: 0f

                    val fx1 = if (relative) currentX + x1 else x1
                    val fy1 = if (relative) currentY + y1 else y1
                    val fx = if (relative) currentX + x else x
                    val fy = if (relative) currentY + y else y

                    val cp1x = currentX + (2f / 3f) * (fx1 - currentX)
                    val cp1y = currentY + (2f / 3f) * (fy1 - currentY)
                    val cp2x = fx + (2f / 3f) * (fx1 - fx)
                    val cp2y = fy + (2f / 3f) * (fy1 - fy)

                    sb.append(String.format(
                        Locale.US, "%.3f %.3f %.3f %.3f %.3f %.3f curveto\n",
                        cp1x, toPsY(cp1y), cp2x, toPsY(cp2y), fx, toPsY(fy)
                    ))
                    currentX = fx
                    currentY = fy
                }
                'Z' -> {
                    sb.append("closepath\n")
                    currentX = startX
                    currentY = startY
                }
                else -> {
                    // Unknown command, skip
                    i++
                }
            }
        }

        return sb.toString()
    }

    private fun tokenizePathData(pathData: String): List<String> {
        val result = mutableListOf<String>()
        val regex = Regex("""([MmLlHhVvCcSsQqTtAaZz])|(-?\d+(?:\.\d+)?(?:e[-+]?\d+)?)""")
        for (match in regex.findAll(pathData)) {
            result.add(match.value)
        }
        return result
    }
}
