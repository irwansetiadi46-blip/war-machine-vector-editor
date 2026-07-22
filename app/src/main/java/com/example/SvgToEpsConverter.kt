package com.example

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

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

            // 1. Parse XML Document
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            factory.isValidating = false
            try {
                factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            } catch (_: Exception) {}

            val builder = factory.newDocumentBuilder()
            val doc: Document = builder.parse(ByteArrayInputStream(svgBytes))
            val root = doc.documentElement

            // 2. Extract Document Dimensions and ViewBox
            var minX = 0f
            var minY = 0f
            var vbWidth = 512f
            var vbHeight = 512f
            var hasViewBox = false

            val viewBoxAttr = root.getAttribute("viewBox").trim()
            if (viewBoxAttr.isNotEmpty()) {
                val vbTokens = viewBoxAttr.split(Regex("""[\s,]+""")).mapNotNull { it.toFloatOrNull() }
                if (vbTokens.size >= 4) {
                    minX = vbTokens[0]
                    minY = vbTokens[1]
                    if (vbTokens[2] > 0f) vbWidth = vbTokens[2]
                    if (vbTokens[3] > 0f) vbHeight = vbTokens[3]
                    hasViewBox = true
                }
            }

            val widthAttr = root.getAttribute("width").trim()
            val heightAttr = root.getAttribute("height").trim()

            var artboardWidth = parseLengthToPt(widthAttr, if (hasViewBox) vbWidth else 512f)
            var artboardHeight = parseLengthToPt(heightAttr, if (hasViewBox) vbHeight else 512f)

            if (artboardWidth <= 0f) artboardWidth = vbWidth
            if (artboardHeight <= 0f) artboardHeight = vbHeight

            if (!hasViewBox) {
                vbWidth = artboardWidth
                vbHeight = artboardHeight
            }

            val scaleX = artboardWidth / vbWidth
            val scaleY = artboardHeight / vbHeight

            // 3. Collect Defs, IDs, Styles, and Gradients
            val idMap = mutableMapOf<String, Element>()
            val gradientMap = mutableMapOf<String, String>() // id -> fallback hex color
            val cssClassMap = parseCssStyles(root)

            indexElementsAndGradients(root, idMap, gradientMap)

            // 4. Build EPS PostScript Content
            val psBuilder = StringBuilder()
            psBuilder.append("%!PS-Adobe-3.0 EPSF-3.0\n")
            psBuilder.append("%%Creator: WarMachineHybrid SVG Converter\n")
            if (title.isNotEmpty()) psBuilder.append("%%Title: $title\n")
            psBuilder.append(String.format(Locale.US, "%%%%BoundingBox: 0 0 %d %d\n", artboardWidth.toInt(), artboardHeight.toInt()))
            psBuilder.append(String.format(Locale.US, "%%%%HiResBoundingBox: 0 0 %.3f %.3f\n", artboardWidth, artboardHeight))
            psBuilder.append(String.format(Locale.US, "%%%%DocumentMedia: Canvas %.3f %.3f 0 () ()\n", artboardWidth, artboardHeight))
            psBuilder.append("%%LanguageLevel: 2\n")
            psBuilder.append("%%Pages: 1\n")
            psBuilder.append("%%EndComments\n\n")

            // Global SVG to PostScript Coordinate Transformation
            psBuilder.append("gsave\n")
            psBuilder.append(String.format(Locale.US, "0 %.3f translate\n", artboardHeight))
            psBuilder.append(String.format(Locale.US, "%.5f %.5f scale\n", scaleX, -scaleY))
            psBuilder.append(String.format(Locale.US, "%.3f %.3f translate\n", -minX, -minY))
            psBuilder.append("\n")

            // Recursive traversal of SVG DOM
            val defaultStyle = StyleContext()
            processChildrenNodes(root, defaultStyle, psBuilder, idMap, gradientMap, cssClassMap)

            psBuilder.append("grestore\n")
            psBuilder.append("showpage\n%%EOF\n")

            val rawEpsBytes = psBuilder.toString().toByteArray(StandardCharsets.UTF_8)

            // 5. Inject XMP Metadata
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

    // --- DOM Traverser & Group Manager ---

    private fun processChildrenNodes(
        parent: Element,
        parentStyle: StyleContext,
        sb: StringBuilder,
        idMap: Map<String, Element>,
        gradientMap: Map<String, String>,
        cssClassMap: Map<String, Map<String, String>>
    ) {
        val childNodes = parent.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                processNode(node as Element, parentStyle, sb, idMap, gradientMap, cssClassMap)
            }
        }
    }

    private fun processNode(
        element: Element,
        parentStyle: StyleContext,
        sb: StringBuilder,
        idMap: Map<String, Element>,
        gradientMap: Map<String, String>,
        cssClassMap: Map<String, Map<String, String>>
    ) {
        val tagName = element.tagName.lowercase(Locale.US)

        // Ignore defs, style, metadata, script, title, desc
        if (tagName in listOf("defs", "style", "metadata", "script", "title", "desc")) {
            return
        }

        val nodeStyle = resolveElementStyle(element, parentStyle, cssClassMap, gradientMap)
        val transformStr = element.getAttribute("transform").trim()
        val idAttr = element.getAttribute("id").trim()

        when (tagName) {
            "g", "a", "svg" -> {
                sb.append("gsave\n")
                if (idAttr.isNotEmpty()) {
                    sb.append("% Group: $idAttr\n")
                }
                if (transformStr.isNotEmpty()) {
                    sb.append(convertSvgTransformToPostScript(transformStr))
                }

                // If nested SVG, handle x, y translation
                if (tagName == "svg" && element.parentNode != null) {
                    val x = parseLengthToPt(element.getAttribute("x"), 0f)
                    val y = parseLengthToPt(element.getAttribute("y"), 0f)
                    if (x != 0f || y != 0f) {
                        sb.append(String.format(Locale.US, "%.3f %.3f translate\n", x, y))
                    }
                }

                processChildrenNodes(element, nodeStyle, sb, idMap, gradientMap, cssClassMap)
                sb.append("grestore\n")
            }

            "use" -> {
                val href = (element.getAttribute("href").ifEmpty { element.getAttribute("xlink:href") }).trim()
                if (href.isNotEmpty()) {
                    val targetId = href.removePrefix("#")
                    val targetElem = idMap[targetId]
                    if (targetElem != null) {
                        sb.append("gsave\n")
                        val x = parseLengthToPt(element.getAttribute("x"), 0f)
                        val y = parseLengthToPt(element.getAttribute("y"), 0f)
                        if (x != 0f || y != 0f) {
                            sb.append(String.format(Locale.US, "%.3f %.3f translate\n", x, y))
                        }
                        if (transformStr.isNotEmpty()) {
                            sb.append(convertSvgTransformToPostScript(transformStr))
                        }
                        processNode(targetElem, nodeStyle, sb, idMap, gradientMap, cssClassMap)
                        sb.append("grestore\n")
                    }
                }
            }

            "path", "rect", "circle", "ellipse", "line", "polygon", "polyline" -> {
                val pathCommands = when (tagName) {
                    "path" -> convertPathToPostScript(element.getAttribute("d"))
                    "rect" -> convertRectToPostScript(element)
                    "circle" -> convertCircleToPostScript(element)
                    "ellipse" -> convertEllipseToPostScript(element)
                    "line" -> convertLineToPostScript(element)
                    "polygon" -> convertPolygonToPostScript(element, isClosed = true)
                    "polyline" -> convertPolygonToPostScript(element, isClosed = false)
                    else -> ""
                }

                if (pathCommands.isBlank()) return

                sb.append("gsave\n")
                if (transformStr.isNotEmpty()) {
                    sb.append(convertSvgTransformToPostScript(transformStr))
                }

                sb.append("newpath\n")
                sb.append(pathCommands)

                // Render Fill
                val fillRgb = parseColorToRgb(nodeStyle.fill ?: "black", gradientMap)
                if (fillRgb != null) {
                    sb.append("gsave\n")
                    sb.append(String.format(Locale.US, "%.3f %.3f %.3f setrgbcolor\n", fillRgb[0], fillRgb[1], fillRgb[2]))
                    if (nodeStyle.fillRule == "evenodd") {
                        sb.append("eofill\n")
                    } else {
                        sb.append("fill\n")
                    }
                    sb.append("grestore\n")
                }

                // Render Stroke
                val strokeRgb = parseColorToRgb(nodeStyle.stroke ?: "none", gradientMap)
                val strokeWidth = nodeStyle.strokeWidth ?: 1f
                if (strokeRgb != null && strokeWidth > 0f) {
                    sb.append("gsave\n")
                    sb.append(String.format(Locale.US, "%.3f setlinewidth\n", strokeWidth))

                    val lineCapInt = when (nodeStyle.strokeLineCap) {
                        "round" -> 1
                        "square" -> 2
                        else -> 0 // butt
                    }
                    val lineJoinInt = when (nodeStyle.strokeLineJoin) {
                        "round" -> 1
                        "bevel" -> 2
                        else -> 0 // miter
                    }
                    sb.append(String.format(Locale.US, "%d setlinecap\n%d setlinejoin\n", lineCapInt, lineJoinInt))
                    sb.append(String.format(Locale.US, "%.3f %.3f %.3f setrgbcolor\n", strokeRgb[0], strokeRgb[1], strokeRgb[2]))
                    sb.append("stroke\n")
                    sb.append("grestore\n")
                }

                sb.append("grestore\n")
            }
        }
    }

    // --- Shape Converters ---

    private fun convertRectToPostScript(element: Element): String {
        val x = parseLengthToPt(element.getAttribute("x"), 0f)
        val y = parseLengthToPt(element.getAttribute("y"), 0f)
        val w = parseLengthToPt(element.getAttribute("width"), 0f)
        val h = parseLengthToPt(element.getAttribute("height"), 0f)
        if (w <= 0f || h <= 0f) return ""

        val rxAttr = parseLengthToPt(element.getAttribute("rx"), 0f)
        val ryAttr = parseLengthToPt(element.getAttribute("ry"), rxAttr)
        val rx = rxAttr.coerceAtMost(w / 2f)
        val ry = ryAttr.coerceAtMost(h / 2f)

        if (rx <= 0f || ry <= 0f) {
            return String.format(
                Locale.US,
                "%.3f %.3f moveto %.3f %.3f lineto %.3f %.3f lineto %.3f %.3f lineto closepath\n",
                x, y, x + w, y, x + w, y + h, x, y + h
            )
        }

        // Rounded Rectangle
        val kx = rx * 0.55228475f
        val ky = ry * 0.55228475f
        val sb = StringBuilder()
        sb.append(String.format(Locale.US, "%.3f %.3f moveto\n", x + rx, y))
        sb.append(String.format(Locale.US, "%.3f %.3f lineto\n", x + w - rx, y))
        sb.append(String.format(Locale.US, "%.3f %.3f %.3f %.3f %.3f %.3f curveto\n", x + w - rx + kx, y, x + w, y + ry - ky, x + w, y + ry))
        sb.append(String.format(Locale.US, "%.3f %.3f lineto\n", x + w, y + h - ry))
        sb.append(String.format(Locale.US, "%.3f %.3f %.3f %.3f %.3f %.3f curveto\n", x + w, y + h - ry + ky, x + w - rx + kx, y + h, x + w - rx, y + h))
        sb.append(String.format(Locale.US, "%.3f %.3f lineto\n", x + rx, y + h))
        sb.append(String.format(Locale.US, "%.3f %.3f %.3f %.3f %.3f %.3f curveto\n", x + rx - kx, y + h, x, y + h - ry + ky, x, y + h - ry))
        sb.append(String.format(Locale.US, "%.3f %.3f lineto\n", x, y + ry))
        sb.append(String.format(Locale.US, "%.3f %.3f %.3f %.3f %.3f %.3f curveto\n", x, y + ry - ky, x + rx - kx, y, x + rx, y))
        sb.append("closepath\n")
        return sb.toString()
    }

    private fun convertCircleToPostScript(element: Element): String {
        val cx = parseLengthToPt(element.getAttribute("cx"), 0f)
        val cy = parseLengthToPt(element.getAttribute("cy"), 0f)
        val r = parseLengthToPt(element.getAttribute("r"), 0f)
        if (r <= 0f) return ""
        return convertEllipseToPostScriptValues(cx, cy, r, r)
    }

    private fun convertEllipseToPostScript(element: Element): String {
        val cx = parseLengthToPt(element.getAttribute("cx"), 0f)
        val cy = parseLengthToPt(element.getAttribute("cy"), 0f)
        val rx = parseLengthToPt(element.getAttribute("rx"), 0f)
        val ry = parseLengthToPt(element.getAttribute("ry"), 0f)
        if (rx <= 0f || ry <= 0f) return ""
        return convertEllipseToPostScriptValues(cx, cy, rx, ry)
    }

    private fun convertEllipseToPostScriptValues(cx: Float, cy: Float, rx: Float, ry: Float): String {
        val kx = rx * 0.55228475f
        val ky = ry * 0.55228475f
        return String.format(
            Locale.US,
            "%.3f %.3f moveto %.3f %.3f %.3f %.3f %.3f %.3f curveto %.3f %.3f %.3f %.3f %.3f %.3f curveto %.3f %.3f %.3f %.3f %.3f %.3f curveto %.3f %.3f %.3f %.3f %.3f %.3f curveto closepath\n",
            cx + rx, cy,
            cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry,
            cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy,
            cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry,
            cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy
        )
    }

    private fun convertLineToPostScript(element: Element): String {
        val x1 = parseLengthToPt(element.getAttribute("x1"), 0f)
        val y1 = parseLengthToPt(element.getAttribute("y1"), 0f)
        val x2 = parseLengthToPt(element.getAttribute("x2"), 0f)
        val y2 = parseLengthToPt(element.getAttribute("y2"), 0f)
        return String.format(Locale.US, "%.3f %.3f moveto %.3f %.3f lineto\n", x1, y1, x2, y2)
    }

    private fun convertPolygonToPostScript(element: Element, isClosed: Boolean): String {
        val pointsStr = element.getAttribute("points").trim()
        if (pointsStr.isEmpty()) return ""
        val tokens = pointsStr.split(Regex("""[\s,]+""")).mapNotNull { it.toFloatOrNull() }
        if (tokens.size < 4) return ""

        val sb = StringBuilder()
        var i = 0
        while (i + 1 < tokens.size) {
            val x = tokens[i]
            val y = tokens[i + 1]
            if (i == 0) {
                sb.append(String.format(Locale.US, "%.3f %.3f moveto\n", x, y))
            } else {
                sb.append(String.format(Locale.US, "%.3f %.3f lineto\n", x, y))
            }
            i += 2
        }
        if (isClosed) sb.append("closepath\n")
        return sb.toString()
    }

    // --- Path 'd' Tokenizer & Parser ---

    private fun convertPathToPostScript(pathD: String): String {
        if (pathD.isBlank()) return ""

        val sb = StringBuilder()
        val tokenizer = PathTokenizer(pathD)

        var currentX = 0f
        var currentY = 0f
        var startX = 0f
        var startY = 0f
        var lastControlX = 0f
        var lastControlY = 0f
        var lastQuadControlX = 0f
        var lastQuadControlY = 0f
        var lastCmd = ' '

        while (tokenizer.hasNext()) {
            val cmd = if (tokenizer.isNextCommand()) tokenizer.nextCommand() else lastCmd
            if (cmd == ' ') break

            val isRelative = cmd.isLowerCase()
            val upperCmd = cmd.uppercaseChar()

            when (upperCmd) {
                'M' -> {
                    var isFirstPair = true
                    while (tokenizer.hasNextNumber()) {
                        val x = tokenizer.nextNumber() ?: break
                        val y = tokenizer.nextNumber() ?: break
                        val finalX = if (isRelative && !isFirstPair) currentX + x else if (isRelative) currentX + x else x
                        val finalY = if (isRelative && !isFirstPair) currentY + y else if (isRelative) currentY + y else y

                        if (isFirstPair) {
                            sb.append(String.format(Locale.US, "%.3f %.3f moveto\n", finalX, finalY))
                            startX = finalX
                            startY = finalY
                            isFirstPair = false
                        } else {
                            sb.append(String.format(Locale.US, "%.3f %.3f lineto\n", finalX, finalY))
                        }
                        currentX = finalX
                        currentY = finalY
                        lastControlX = currentX
                        lastControlY = currentY
                        lastQuadControlX = currentX
                        lastQuadControlY = currentY
                    }
                    lastCmd = if (isRelative) 'm' else 'M'
                }

                'L' -> {
                    while (tokenizer.hasNextNumber()) {
                        val x = tokenizer.nextNumber() ?: break
                        val y = tokenizer.nextNumber() ?: break
                        val finalX = if (isRelative) currentX + x else x
                        val finalY = if (isRelative) currentY + y else y

                        sb.append(String.format(Locale.US, "%.3f %.3f lineto\n", finalX, finalY))
                        currentX = finalX
                        currentY = finalY
                        lastControlX = currentX
                        lastControlY = currentY
                        lastQuadControlX = currentX
                        lastQuadControlY = currentY
                    }
                    lastCmd = if (isRelative) 'l' else 'L'
                }

                'H' -> {
                    while (tokenizer.hasNextNumber()) {
                        val x = tokenizer.nextNumber() ?: break
                        val finalX = if (isRelative) currentX + x else x

                        sb.append(String.format(Locale.US, "%.3f %.3f lineto\n", finalX, currentY))
                        currentX = finalX
                        lastControlX = currentX
                        lastControlY = currentY
                        lastQuadControlX = currentX
                        lastQuadControlY = currentY
                    }
                    lastCmd = if (isRelative) 'h' else 'H'
                }

                'V' -> {
                    while (tokenizer.hasNextNumber()) {
                        val y = tokenizer.nextNumber() ?: break
                        val finalY = if (isRelative) currentY + y else y

                        sb.append(String.format(Locale.US, "%.3f %.3f lineto\n", currentX, finalY))
                        currentY = finalY
                        lastControlX = currentX
                        lastControlY = currentY
                        lastQuadControlX = currentX
                        lastQuadControlY = currentY
                    }
                    lastCmd = if (isRelative) 'v' else 'V'
                }

                'C' -> {
                    while (tokenizer.hasNextNumber()) {
                        val x1 = tokenizer.nextNumber() ?: break
                        val y1 = tokenizer.nextNumber() ?: break
                        val x2 = tokenizer.nextNumber() ?: break
                        val y2 = tokenizer.nextNumber() ?: break
                        val x = tokenizer.nextNumber() ?: break
                        val y = tokenizer.nextNumber() ?: break

                        val fx1 = if (isRelative) currentX + x1 else x1
                        val fy1 = if (isRelative) currentY + y1 else y1
                        val fx2 = if (isRelative) currentX + x2 else x2
                        val fy2 = if (isRelative) currentY + y2 else y2
                        val fx = if (isRelative) currentX + x else x
                        val fy = if (isRelative) currentY + y else y

                        sb.append(String.format(Locale.US, "%.3f %.3f %.3f %.3f %.3f %.3f curveto\n", fx1, fy1, fx2, fy2, fx, fy))
                        lastControlX = fx2
                        lastControlY = fy2
                        currentX = fx
                        currentY = fy
                        lastQuadControlX = currentX
                        lastQuadControlY = currentY
                    }
                    lastCmd = if (isRelative) 'c' else 'C'
                }

                'S' -> {
                    while (tokenizer.hasNextNumber()) {
                        val x2 = tokenizer.nextNumber() ?: break
                        val y2 = tokenizer.nextNumber() ?: break
                        val x = tokenizer.nextNumber() ?: break
                        val y = tokenizer.nextNumber() ?: break

                        val fx1 = if (lastCmd.uppercaseChar() in listOf('C', 'S')) 2f * currentX - lastControlX else currentX
                        val fy1 = if (lastCmd.uppercaseChar() in listOf('C', 'S')) 2f * currentY - lastControlY else currentY
                        val fx2 = if (isRelative) currentX + x2 else x2
                        val fy2 = if (isRelative) currentY + y2 else y2
                        val fx = if (isRelative) currentX + x else x
                        val fy = if (isRelative) currentY + y else y

                        sb.append(String.format(Locale.US, "%.3f %.3f %.3f %.3f %.3f %.3f curveto\n", fx1, fy1, fx2, fy2, fx, fy))
                        lastControlX = fx2
                        lastControlY = fy2
                        currentX = fx
                        currentY = fy
                        lastQuadControlX = currentX
                        lastQuadControlY = currentY
                    }
                    lastCmd = if (isRelative) 's' else 'S'
                }

                'Q' -> {
                    while (tokenizer.hasNextNumber()) {
                        val x1 = tokenizer.nextNumber() ?: break
                        val y1 = tokenizer.nextNumber() ?: break
                        val x = tokenizer.nextNumber() ?: break
                        val y = tokenizer.nextNumber() ?: break

                        val qx1 = if (isRelative) currentX + x1 else x1
                        val qy1 = if (isRelative) currentY + y1 else y1
                        val fx = if (isRelative) currentX + x else x
                        val fy = if (isRelative) currentY + y else y

                        val cx1 = currentX + (2f / 3f) * (qx1 - currentX)
                        val cy1 = currentY + (2f / 3f) * (qy1 - currentY)
                        val cx2 = fx + (2f / 3f) * (qx1 - fx)
                        val cy2 = fy + (2f / 3f) * (qy1 - fy)

                        sb.append(String.format(Locale.US, "%.3f %.3f %.3f %.3f %.3f %.3f curveto\n", cx1, cy1, cx2, cy2, fx, fy))
                        lastQuadControlX = qx1
                        lastQuadControlY = qy1
                        currentX = fx
                        currentY = fy
                        lastControlX = currentX
                        lastControlY = currentY
                    }
                    lastCmd = if (isRelative) 'q' else 'Q'
                }

                'T' -> {
                    while (tokenizer.hasNextNumber()) {
                        val x = tokenizer.nextNumber() ?: break
                        val y = tokenizer.nextNumber() ?: break

                        val qx1 = if (lastCmd.uppercaseChar() in listOf('Q', 'T')) 2f * currentX - lastQuadControlX else currentX
                        val qy1 = if (lastCmd.uppercaseChar() in listOf('Q', 'T')) 2f * currentY - lastQuadControlY else currentY
                        val fx = if (isRelative) currentX + x else x
                        val fy = if (isRelative) currentY + y else y

                        val cx1 = currentX + (2f / 3f) * (qx1 - currentX)
                        val cy1 = currentY + (2f / 3f) * (qy1 - currentY)
                        val cx2 = fx + (2f / 3f) * (qx1 - fx)
                        val cy2 = fy + (2f / 3f) * (qy1 - fy)

                        sb.append(String.format(Locale.US, "%.3f %.3f %.3f %.3f %.3f %.3f curveto\n", cx1, cy1, cx2, cy2, fx, fy))
                        lastQuadControlX = qx1
                        lastQuadControlY = qy1
                        currentX = fx
                        currentY = fy
                        lastControlX = currentX
                        lastControlY = currentY
                    }
                    lastCmd = if (isRelative) 't' else 'T'
                }

                'A' -> {
                    while (tokenizer.hasNextNumber()) {
                        val rx = tokenizer.nextNumber() ?: break
                        val ry = tokenizer.nextNumber() ?: break
                        val xAxisRotation = tokenizer.nextNumber() ?: break
                        val largeArcFlag = tokenizer.nextNumber() ?: break
                        val sweepFlag = tokenizer.nextNumber() ?: break
                        val x = tokenizer.nextNumber() ?: break
                        val y = tokenizer.nextNumber() ?: break

                        val fx = if (isRelative) currentX + x else x
                        val fy = if (isRelative) currentY + y else y

                        val beziers = endpointToCubicBeziers(
                            currentX, currentY, rx, ry, xAxisRotation,
                            largeArcFlag != 0f, sweepFlag != 0f, fx, fy
                        )

                        for (b in beziers) {
                            sb.append(String.format(Locale.US, "%.3f %.3f %.3f %.3f %.3f %.3f curveto\n", b[0], b[1], b[2], b[3], b[4], b[5]))
                        }

                        currentX = fx
                        currentY = fy
                        lastControlX = currentX
                        lastControlY = currentY
                        lastQuadControlX = currentX
                        lastQuadControlY = currentY
                    }
                    lastCmd = if (isRelative) 'a' else 'A'
                }

                'Z' -> {
                    sb.append("closepath\n")
                    currentX = startX
                    currentY = startY
                    lastControlX = currentX
                    lastControlY = currentY
                    lastQuadControlX = currentX
                    lastQuadControlY = currentY
                    lastCmd = if (isRelative) 'z' else 'Z'
                }

                else -> {
                    // Unknown command, skip token
                }
            }
        }

        return sb.toString()
    }

    // Mathematical SVG Arc to Cubic Beziers Algorithm
    private fun endpointToCubicBeziers(
        x1: Float, y1: Float,
        rxIn: Float, ryIn: Float,
        xAxisRotation: Float,
        largeArcFlag: Boolean, sweepFlag: Boolean,
        x2: Float, y2: Float
    ): List<FloatArray> {
        val result = mutableListOf<FloatArray>()
        if (x1 == x2 && y1 == y2) return result

        var rx = abs(rxIn)
        var ry = abs(ryIn)
        if (rx == 0f || ry == 0f) return result

        val phi = Math.toRadians((xAxisRotation % 360).toDouble())
        val cosPhi = cos(phi).toFloat()
        val sinPhi = sin(phi).toFloat()

        val dx2 = (x1 - x2) / 2f
        val dy2 = (y1 - y2) / 2f

        val x1p = cosPhi * dx2 + sinPhi * dy2
        val y1p = -sinPhi * dx2 + cosPhi * dy2

        var lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
        if (lambda > 1f) {
            val sqrtLambda = sqrt(lambda)
            rx *= sqrtLambda
            ry *= sqrtLambda
        }

        val rxSq = rx * rx
        val rySq = ry * ry
        val x1pSq = x1p * x1p
        val y1pSq = y1p * y1p

        var sq = (rxSq * rySq - rxSq * y1pSq - rySq * x1pSq) / (rxSq * y1pSq + rySq * x1pSq)
        if (sq < 0f) sq = 0f
        var coef = sqrt(sq)
        if (largeArcFlag == sweepFlag) coef = -coef

        val cxp = coef * ((rx * y1p) / ry)
        val cyp = coef * (-(ry * x1p) / rx)

        val mx = (x1 + x2) / 2f
        val my = (y1 + y2) / 2f
        val cx = cosPhi * cxp - sinPhi * cyp + mx
        val cy = sinPhi * cxp + cosPhi * cyp + my

        val ux = (x1p - cxp) / rx
        val uy = (y1p - cyp) / ry
        val vx = (-x1p - cxp) / rx
        val vy = (-y1p - cyp) / ry

        var theta1 = computeAngle(1f, 0f, ux, uy)
        var dTheta = computeAngle(ux, uy, vx, vy)

        if (!sweepFlag && dTheta > 0) dTheta -= 2f * Math.PI.toFloat()
        if (sweepFlag && dTheta < 0) dTheta += 2f * Math.PI.toFloat()

        val segments = ceil(abs(dTheta) / (Math.PI / 2.0)).toInt().coerceAtLeast(1)
        val delta = dTheta / segments

        for (i in 0 until segments) {
            val t1 = theta1 + i * delta
            val t2 = t1 + delta

            val alpha = 4f / 3f * tan(delta / 4f)

            val cosT1 = cos(t1.toDouble()).toFloat()
            val sinT1 = sin(t1.toDouble()).toFloat()
            val cosT2 = cos(t2.toDouble()).toFloat()
            val sinT2 = sin(t2.toDouble()).toFloat()

            val p1x = cosT1
            val p1y = sinT1
            val p1px = -sinT1
            val p1py = cosT1

            val p2x = cosT2
            val p2y = sinT2
            val p2px = -sinT2
            val p2py = cosT2

            val q1x = p1x + alpha * p1px
            val q1y = p1y + alpha * p1py
            val q2x = p2x - alpha * p2px
            val q2y = p2y - alpha * p2py

            val cp1x = cx + cosPhi * (q1x * rx) - sinPhi * (q1y * ry)
            val cp1y = cy + sinPhi * (q1x * rx) + cosPhi * (q1y * ry)
            val cp2x = cx + cosPhi * (q2x * rx) - sinPhi * (q2y * ry)
            val cp2y = cy + sinPhi * (q2x * rx) + cosPhi * (q2y * ry)
            val endX = cx + cosPhi * (p2x * rx) - sinPhi * (p2y * ry)
            val endY = cy + sinPhi * (p2x * rx) + cosPhi * (p2y * ry)

            result.add(floatArrayOf(cp1x, cp1y, cp2x, cp2y, endX, endY))
        }

        return result
    }

    private fun computeAngle(ux: Float, uy: Float, vx: Float, vy: Float): Float {
        val dot = ux * vx + uy * vy
        val len = sqrt(ux * ux + uy * uy) * sqrt(vx * vx + vy * vy)
        var cos = if (len != 0f) dot / len else 0f
        if (cos < -1f) cos = -1f
        if (cos > 1f) cos = 1f
        var angle = atan2(uy.toDouble(), ux.toDouble()).toFloat() - atan2(vy.toDouble(), vx.toDouble()).toFloat()
        angle = -angle
        return angle
    }

    // --- Helper Classes & Parsers ---

    private class PathTokenizer(private val d: String) {
        private var pos = 0
        private val len = d.length

        fun hasNext(): Boolean {
            skipWhitespaceAndCommas()
            return pos < len
        }

        fun isNextCommand(): Boolean {
            skipWhitespaceAndCommas()
            if (pos >= len) return false
            return d[pos].isLetter()
        }

        fun nextCommand(): Char {
            skipWhitespaceAndCommas()
            if (pos < len && d[pos].isLetter()) {
                return d[pos++]
            }
            return ' '
        }

        fun hasNextNumber(): Boolean {
            skipWhitespaceAndCommas()
            if (pos >= len) return false
            val c = d[pos]
            return c.isDigit() || c == '+' || c == '-' || c == '.'
        }

        fun nextNumber(): Float? {
            skipWhitespaceAndCommas()
            if (pos >= len) return null
            val start = pos
            if (d[pos] == '+' || d[pos] == '-') pos++
            var hasDigits = false
            while (pos < len && d[pos].isDigit()) {
                pos++
                hasDigits = true
            }
            if (pos < len && d[pos] == '.') {
                pos++
                while (pos < len && d[pos].isDigit()) {
                    pos++
                    hasDigits = true
                }
            }
            if (pos < len && (d[pos] == 'e' || d[pos] == 'E')) {
                val ePos = pos
                pos++
                if (pos < len && (d[pos] == '+' || d[pos] == '-')) pos++
                if (pos < len && d[pos].isDigit()) {
                    while (pos < len && d[pos].isDigit()) pos++
                } else {
                    pos = ePos
                }
            }
            if (!hasDigits) {
                pos = start
                return null
            }
            return d.substring(start, pos).toFloatOrNull()
        }

        private fun skipWhitespaceAndCommas() {
            while (pos < len && (d[pos].isWhitespace() || d[pos] == ',')) {
                pos++
            }
        }
    }

    private data class StyleContext(
        val fill: String? = null,
        val stroke: String? = null,
        val strokeWidth: Float? = null,
        val strokeLineCap: String? = null,
        val strokeLineJoin: String? = null,
        val fillRule: String? = null,
        val opacity: Float? = null
    )

    private fun resolveElementStyle(
        element: Element,
        parentStyle: StyleContext,
        cssClassMap: Map<String, Map<String, String>>,
        gradientMap: Map<String, String>
    ): StyleContext {
        val mergedMap = mutableMapOf<String, String>()

        // 1. Inherited parent style
        parentStyle.fill?.let { mergedMap["fill"] = it }
        parentStyle.stroke?.let { mergedMap["stroke"] = it }
        parentStyle.strokeWidth?.let { mergedMap["stroke-width"] = it.toString() }
        parentStyle.strokeLineCap?.let { mergedMap["stroke-linecap"] = it }
        parentStyle.strokeLineJoin?.let { mergedMap["stroke-linejoin"] = it }
        parentStyle.fillRule?.let { mergedMap["fill-rule"] = it }
        parentStyle.opacity?.let { mergedMap["opacity"] = it.toString() }

        // 2. CSS Class Styles
        val classAttr = element.getAttribute("class").trim()
        if (classAttr.isNotEmpty()) {
            val classes = classAttr.split(Regex("""\s+"""))
            for (c in classes) {
                val classMap = cssClassMap[c]
                if (classMap != null) {
                    mergedMap.putAll(classMap)
                }
            }
        }

        // 3. Direct SVG attributes
        val directAttrs = listOf("fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin", "fill-rule", "opacity")
        for (attr in directAttrs) {
            val v = element.getAttribute(attr).trim()
            if (v.isNotEmpty()) {
                mergedMap[attr] = v
            }
        }

        // 4. Inline style="..." attribute
        val styleAttr = element.getAttribute("style").trim()
        if (styleAttr.isNotEmpty()) {
            mergedMap.putAll(parseStyleDeclarations(styleAttr))
        }

        return StyleContext(
            fill = mergedMap["fill"] ?: parentStyle.fill ?: "black",
            stroke = mergedMap["stroke"] ?: parentStyle.stroke ?: "none",
            strokeWidth = parseLengthToPt(mergedMap["stroke-width"] ?: "", parentStyle.strokeWidth ?: 1f),
            strokeLineCap = mergedMap["stroke-linecap"] ?: parentStyle.strokeLineCap ?: "butt",
            strokeLineJoin = mergedMap["stroke-linejoin"] ?: parentStyle.strokeLineJoin ?: "miter",
            fillRule = mergedMap["fill-rule"] ?: parentStyle.fillRule ?: "nonzero",
            opacity = (mergedMap["opacity"] ?: parentStyle.opacity?.toString())?.toFloatOrNull() ?: 1f
        )
    }

    private fun parseStyleDeclarations(styleStr: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val declarations = styleStr.split(";")
        for (decl in declarations) {
            val kv = decl.split(":")
            if (kv.size == 2) {
                map[kv[0].trim().lowercase(Locale.US)] = kv[1].trim()
            }
        }
        return map
    }

    private fun parseCssStyles(root: Element): Map<String, Map<String, String>> {
        val result = mutableMapOf<String, MutableMap<String, String>>()
        val styleNodes = root.getElementsByTagName("style")
        for (i in 0 until styleNodes.length) {
            val cssText = styleNodes.item(i).textContent ?: continue
            val ruleRegex = Regex("""([^{]+)\{([^}]+)\}""")
            for (match in ruleRegex.findAll(cssText)) {
                val selectorGroup = match.groupValues[1].trim()
                val declGroup = match.groupValues[2].trim()

                val decls = parseStyleDeclarations(declGroup)
                val selectors = selectorGroup.split(",").map { it.trim() }
                for (sel in selectors) {
                    if (sel.startsWith(".")) {
                        val className = sel.substring(1)
                        val map = result.getOrPut(className) { mutableMapOf() }
                        map.putAll(decls)
                    }
                }
            }
        }
        return result
    }

    private fun indexElementsAndGradients(
        element: Element,
        idMap: MutableMap<String, Element>,
        gradientMap: MutableMap<String, String>
    ) {
        val idAttr = element.getAttribute("id").trim()
        if (idAttr.isNotEmpty()) {
            idMap[idAttr] = element
        }

        val tagName = element.tagName.lowercase(Locale.US)
        if (tagName == "lineargradient" || tagName == "radialgradient") {
            if (idAttr.isNotEmpty()) {
                val stops = element.getElementsByTagName("stop")
                var firstColor = "#000000"
                if (stops.length > 0) {
                    val firstStop = stops.item(0) as Element
                    val colorAttr = firstStop.getAttribute("stop-color").trim()
                    val styleAttr = firstStop.getAttribute("style").trim()
                    val styleMap = parseStyleDeclarations(styleAttr)
                    firstColor = colorAttr.ifEmpty { styleMap["stop-color"] ?: "#000000" }
                }
                gradientMap[idAttr] = firstColor
            }
        }

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                indexElementsAndGradients(child as Element, idMap, gradientMap)
            }
        }
    }

    private fun convertSvgTransformToPostScript(transformStr: String): String {
        val sb = StringBuilder()
        val funcRegex = Regex("""(matrix|translate|scale|rotate|skewX|skewY)\s*\(([^)]+)\)""", RegexOption.IGNORE_CASE)
        for (match in funcRegex.findAll(transformStr)) {
            val type = match.groupValues[1].lowercase(Locale.US)
            val args = match.groupValues[2].split(Regex("""[\s,]+""")).mapNotNull { it.toFloatOrNull() }
            when (type) {
                "matrix" -> {
                    if (args.size >= 6) {
                        sb.append(String.format(Locale.US, "[%.5f %.5f %.5f %.5f %.5f %.5f] concat\n", args[0], args[1], args[2], args[3], args[4], args[5]))
                    }
                }
                "translate" -> {
                    val tx = args.getOrNull(0) ?: 0f
                    val ty = args.getOrNull(1) ?: 0f
                    sb.append(String.format(Locale.US, "%.5f %.5f translate\n", tx, ty))
                }
                "scale" -> {
                    val sx = args.getOrNull(0) ?: 1f
                    val sy = args.getOrNull(1) ?: sx
                    sb.append(String.format(Locale.US, "%.5f %.5f scale\n", sx, sy))
                }
                "rotate" -> {
                    val angle = args.getOrNull(0) ?: 0f
                    val cx = args.getOrNull(1)
                    val cy = args.getOrNull(2)
                    if (cx != null && cy != null) {
                        sb.append(String.format(Locale.US, "%.5f %.5f translate %.5f rotate %.5f %.5f translate\n", cx, cy, angle, -cx, -cy))
                    } else {
                        sb.append(String.format(Locale.US, "%.5f rotate\n", angle))
                    }
                }
                "skewx" -> {
                    val angle = args.getOrNull(0) ?: 0f
                    val rad = Math.toRadians(angle.toDouble())
                    val tanVal = tan(rad).toFloat()
                    sb.append(String.format(Locale.US, "[1 0 %.5f 1 0 0] concat\n", tanVal))
                }
                "skewy" -> {
                    val angle = args.getOrNull(0) ?: 0f
                    val rad = Math.toRadians(angle.toDouble())
                    val tanVal = tan(rad).toFloat()
                    sb.append(String.format(Locale.US, "[1 %.5f 0 1 0 0] concat\n", tanVal))
                }
            }
        }
        return sb.toString()
    }

    private fun parseLengthToPt(lengthStr: String, defaultValue: Float): Float {
        val s = lengthStr.trim().lowercase(Locale.US)
        if (s.isEmpty() || s.endsWith("%")) return defaultValue
        val numStr = s.replace(Regex("[^0-9.-]"), "")
        val v = numStr.toFloatOrNull() ?: return defaultValue

        return when {
            s.endsWith("in") -> v * 72f
            s.endsWith("cm") -> v * 28.346457f
            s.endsWith("mm") -> v * 2.8346457f
            s.endsWith("pt") -> v
            s.endsWith("px") -> v
            else -> v
        }
    }

    private fun parseColorToRgb(colorStr: String, gradientMap: Map<String, String>): FloatArray? {
        var c = colorStr.trim().lowercase(Locale.US)
        if (c == "none" || c == "transparent" || c.isEmpty()) return null

        if (c.startsWith("url(")) {
            val gradId = c.substringAfter("url(").substringBefore(")").removePrefix("#").removeSurrounding("'", "\"")
            val fallback = gradientMap[gradId] ?: "#000000"
            c = fallback.lowercase(Locale.US)
        }

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
                8 -> { // RGBA hex
                    val r = hex.substring(0, 2).toInt(16) / 255f
                    val g = hex.substring(2, 4).toInt(16) / 255f
                    val b = hex.substring(4, 6).toInt(16) / 255f
                    floatArrayOf(r, g, b)
                }
                else -> floatArrayOf(0f, 0f, 0f)
            }
        }

        if (c.startsWith("rgb")) {
            val nums = Regex("""\d+(?:\.\d+)?%?""").findAll(c).map {
                val v = it.value
                if (v.endsWith("%")) {
                    v.dropLast(1).toFloatOrNull()?.div(100f) ?: 0f
                } else {
                    (v.toFloatOrNull() ?: 0f) / 255f
                }
            }.toList()

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
            "lime" -> floatArrayOf(0f, 1f, 0f)
            "blue" -> floatArrayOf(0f, 0f, 1f)
            "yellow" -> floatArrayOf(1f, 1f, 0f)
            "cyan", "aqua" -> floatArrayOf(0f, 1f, 1f)
            "magenta", "fuchsia" -> floatArrayOf(1f, 0f, 1f)
            "gray", "grey" -> floatArrayOf(0.5f, 0.5f, 0.5f)
            "silver" -> floatArrayOf(0.75f, 0.75f, 0.75f)
            "maroon" -> floatArrayOf(0.5f, 0f, 0f)
            "navy" -> floatArrayOf(0f, 0f, 0.5f)
            "olive" -> floatArrayOf(0.5f, 0.5f, 0f)
            "purple" -> floatArrayOf(0.5f, 0f, 0.5f)
            "teal" -> floatArrayOf(0f, 0.5f, 0.5f)
            "orange" -> floatArrayOf(1f, 0.647f, 0f)
            else -> floatArrayOf(0f, 0f, 0f)
        }
    }
}
