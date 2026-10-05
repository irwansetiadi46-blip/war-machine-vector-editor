package com.example

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
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

    data class GradientStopDef(val offset: Float, val r: Float, val g: Float, val b: Float)

    data class SvgGradientDef(
        val id: String,
        val isRadial: Boolean,
        val x1Str: String = "0%",
        val y1Str: String = "0%",
        val x2Str: String = "100%",
        val y2Str: String = "0%",
        val cxStr: String = "50%",
        val cyStr: String = "50%",
        val rStr: String = "50%",
        val fxStr: String = "",
        val fyStr: String = "",
        val isUserSpace: Boolean = false,
        val stops: List<GradientStopDef> = emptyList(),
        val transform: String = ""
    )

    data class SvgClipPathDef(
        val id: String,
        val element: Element
    )

    fun convertSvgToEps(
        svgBytes: ByteArray,
        title: String = "",
        description: String = "",
        keywords: List<String> = emptyList(),
        creator: String = ""
    ): ByteArray {
        try {
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

            // 2. Extract Document Dimensions and ViewBox (Exact 1:1 Precision)
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

            if (vbWidth <= 0f || vbWidth.isNaN() || vbWidth.isInfinite()) vbWidth = 512f
            if (vbHeight <= 0f || vbHeight.isNaN() || vbHeight.isInfinite()) vbHeight = 512f

            var artboardWidth = parseLengthToPt(widthAttr, if (hasViewBox) vbWidth else 512f)
            var artboardHeight = parseLengthToPt(heightAttr, if (hasViewBox) vbHeight else 512f)

            if (artboardWidth <= 0f || artboardWidth.isNaN() || artboardWidth.isInfinite()) artboardWidth = vbWidth
            if (artboardHeight <= 0f || artboardHeight.isNaN() || artboardHeight.isInfinite()) artboardHeight = vbHeight

            if (!hasViewBox) {
                vbWidth = artboardWidth
                vbHeight = artboardHeight
            }

            // Exact 1:1 scale mapping without distortion or auto-resizing
            val scaleX = artboardWidth / vbWidth
            val scaleY = artboardHeight / vbHeight

            // 3. Index Defs, IDs, Styles, Gradients, and ClipPaths
            val idMap = mutableMapOf<String, Element>()
            val gradientMap = mutableMapOf<String, String>() // id -> fallback hex color
            val fullGradientMap = mutableMapOf<String, SvgGradientDef>()
            val clipPathMap = mutableMapOf<String, SvgClipPathDef>()
            val cssClassMap = parseCssStyles(root)

            indexElementsAndGradients(root, idMap, gradientMap, fullGradientMap, clipPathMap)

            // 4. Build Standard Adobe Illustrator AI8-Compatible PostScript Header & Prolog
            val psBuilder = StringBuilder()

            val cleanTitle = if (title.isNotEmpty()) {
                title.replace("(", "[").replace(")", "]")
            } else {
                "converted_artwork"
            }

            psBuilder.append("%!PS-Adobe-3.0 EPSF-3.0\n")
            psBuilder.append("%%Creator: Adobe Illustrator(R) 8.0\n")
            psBuilder.append("%%AI8_CreatorVersion: 8.0\n")
            psBuilder.append("%%Title: ($cleanTitle.eps)\n")
            psBuilder.append(String.format(Locale.US, "%%%%BoundingBox: 0 0 %d %d\n", ceil(artboardWidth.toDouble()).toInt(), ceil(artboardHeight.toDouble()).toInt()))
            psBuilder.append(String.format(Locale.US, "%%%%HiResBoundingBox: 0 0 %.3f %.3f\n", artboardWidth, artboardHeight))
            psBuilder.append("%%DocumentNeededResources: procset Adobe_Illustrator_AI5 1.0 0\n")
            psBuilder.append("%%LanguageLevel: 2\n")
            psBuilder.append("%%Pages: 1\n")
            psBuilder.append("%%EndComments\n\n")

            // --- PostScript Prolog & Operator Definitions ---
            psBuilder.append("%%BeginProlog\n")
            psBuilder.append("/_AI_save /save load def\n")
            psBuilder.append("/_AI_restore /restore load def\n")
            psBuilder.append("/q { gsave } bind def\n")
            psBuilder.append("/Q { grestore } bind def\n")
            psBuilder.append("/u { count 0 gt { dup type /stringtype eq { pop } if } if } bind def\n")
            psBuilder.append("/U {} bind def\n")
            psBuilder.append("/*u { count 0 gt { dup type /stringtype eq { pop } if } if } bind def\n")
            psBuilder.append("/*U {} bind def\n")
            psBuilder.append("/m { moveto } bind def\n")
            psBuilder.append("/l { lineto } bind def\n")
            psBuilder.append("/c { curveto } bind def\n")
            psBuilder.append("/v { currentpoint 6 2 roll curveto } bind def\n")
            psBuilder.append("/y { 2 copy curveto } bind def\n")
            psBuilder.append("/h { closepath } bind def\n")
            psBuilder.append("/n { newpath } bind def\n")
            psBuilder.append("/f { fill } bind def\n")
            psBuilder.append("/F { fill } bind def\n")
            psBuilder.append("/f* { eofill } bind def\n")
            psBuilder.append("/s { stroke } bind def\n")
            psBuilder.append("/S { stroke } bind def\n")
            psBuilder.append("/b { gsave fill grestore stroke } bind def\n")
            psBuilder.append("/B { gsave fill grestore stroke } bind def\n")
            psBuilder.append("/b* { gsave eofill grestore stroke } bind def\n")
            psBuilder.append("/B* { gsave eofill grestore stroke } bind def\n")
            psBuilder.append("/W { clip } bind def\n")
            psBuilder.append("/W* { eoclip } bind def\n")
            psBuilder.append("/w { setlinewidth } bind def\n")
            psBuilder.append("/J { setlinecap } bind def\n")
            psBuilder.append("/j { setlinejoin } bind def\n")
            psBuilder.append("/M { setmiterlimit } bind def\n")
            psBuilder.append("/d { setdash } bind def\n")
            psBuilder.append("/rg { setrgbcolor } bind def\n")
            psBuilder.append("/RG { setrgbcolor } bind def\n")
            psBuilder.append("/k { setcmykcolor } bind def\n")
            psBuilder.append("/K { setcmykcolor } bind def\n")
            psBuilder.append("/g { setgray } bind def\n")
            psBuilder.append("/G { setgray } bind def\n")
            psBuilder.append("/Lb { cleartomark } bind def\n")
            psBuilder.append("/Ln { pop } bind def\n")
            psBuilder.append("/LB {} bind def\n")
            psBuilder.append("%%EndProlog\n\n")

            psBuilder.append("%%BeginSetup\n")
            psBuilder.append("%%EndSetup\n\n")

            // Global SVG-to-PostScript Coordinate Transformation (1:1 top-left mapping)
            psBuilder.append("q\n")
            psBuilder.append(String.format(Locale.US, "0 %.3f translate\n", artboardHeight))
            psBuilder.append("1 -1 scale\n")
            if (scaleX != 1f || scaleY != 1f) {
                psBuilder.append(String.format(Locale.US, "%.5f %.5f scale\n", scaleX, scaleY))
            }
            if (minX != 0f || minY != 0f) {
                psBuilder.append(String.format(Locale.US, "%.3f %.3f translate\n", -minX, -minY))
            }
            psBuilder.append("\n")

            // Start Adobe Illustrator Layer 1 with mark keyword
            val rawLayerId = root.getAttribute("id").trim()
            val layerName = if (rawLayerId.isNotEmpty()) rawLayerId.replace("(", "[").replace(")", "]") else "Layer 1"
            psBuilder.append("%AI5_BeginLayer\n")
            psBuilder.append("mark 1 1 1 1 0 0 0 79 128 255 Lb\n")
            psBuilder.append("($layerName) Ln\n")

            // Recursive traversal of SVG DOM with AI Group Tracking
            val defaultStyle = StyleContext()
            processChildrenNodes(
                parent = root,
                parentStyle = defaultStyle,
                sb = psBuilder,
                idMap = idMap,
                gradientMap = gradientMap,
                fullGradientMap = fullGradientMap,
                clipPathMap = clipPathMap,
                cssClassMap = cssClassMap,
                depth = 0
            )

            // Close Adobe Illustrator Layer
            psBuilder.append("LB\n")
            psBuilder.append("%AI5_EndLayer\n")

            psBuilder.append("Q\n")
            psBuilder.append("showpage\n%%EOF\n")

            val rawEpsBytes = psBuilder.toString().toByteArray(StandardCharsets.UTF_8)

            // 5. Inject Clean Standard XMP Metadata
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

    // --- DOM Traverser & Group Hierarchy Manager ---

    private fun processChildrenNodes(
        parent: Element,
        parentStyle: StyleContext,
        sb: StringBuilder,
        idMap: Map<String, Element>,
        gradientMap: Map<String, String>,
        fullGradientMap: Map<String, SvgGradientDef>,
        clipPathMap: Map<String, SvgClipPathDef>,
        cssClassMap: Map<String, Map<String, String>>,
        depth: Int
    ) {
        val childNodes = parent.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                processNode(
                    element = node as Element,
                    parentStyle = parentStyle,
                    sb = sb,
                    idMap = idMap,
                    gradientMap = gradientMap,
                    fullGradientMap = fullGradientMap,
                    clipPathMap = clipPathMap,
                    cssClassMap = cssClassMap,
                    depth = depth
                )
            }
        }
    }

    private fun processNode(
        element: Element,
        parentStyle: StyleContext,
        sb: StringBuilder,
        idMap: Map<String, Element>,
        gradientMap: Map<String, String>,
        fullGradientMap: Map<String, SvgGradientDef>,
        clipPathMap: Map<String, SvgClipPathDef>,
        cssClassMap: Map<String, Map<String, String>>,
        depth: Int
    ) {
        val tagName = element.tagName.lowercase(Locale.US)

        // Ignore defs, style, metadata, script, title, desc, clipPath
        if (tagName in listOf("defs", "style", "metadata", "script", "title", "desc", "clippath")) {
            return
        }

        val nodeStyle = resolveElementStyle(element, parentStyle, cssClassMap, gradientMap)
        val transformStr = element.getAttribute("transform").trim()
        val idAttr = element.getAttribute("id").trim()
        val clipPathAttr = element.getAttribute("clip-path").trim().ifEmpty {
            val styleMap = parseStyleDeclarations(element.getAttribute("style"))
            styleMap["clip-path"] ?: ""
        }

        val hasTransform = transformStr.isNotEmpty()
        val hasClipPath = clipPathAttr.isNotEmpty()

        when (tagName) {
            "g", "a", "svg" -> {
                val needsStateIsolation = hasTransform || hasClipPath || (tagName == "svg" && element.parentNode != null)

                if (needsStateIsolation) {
                    sb.append("q\n")
                }

                // Handle Clip-Path on Group
                if (hasClipPath) {
                    val clipId = clipPathAttr.substringAfter("url(").substringBefore(")").removePrefix("#").removeSurrounding("'", "\"").trim()
                    val clipDef = clipPathMap[clipId]
                    if (clipDef != null) {
                        emitClipPath(sb, clipDef.element)
                    }
                }

                // Handle Transform on Group
                if (hasTransform) {
                    sb.append(convertSvgTransformToPostScript(transformStr))
                }

                if (tagName == "svg" && element.parentNode != null) {
                    val x = parseLengthToPt(element.getAttribute("x"), 0f)
                    val y = parseLengthToPt(element.getAttribute("y"), 0f)
                    if (x != 0f || y != 0f) {
                        sb.append(String.format(Locale.US, "%.3f %.3f translate\n", x, y))
                    }
                }

                // AI Group Begin Operator: 'u' or '(id) u'
                if (idAttr.isNotEmpty()) {
                    val cleanId = idAttr.replace("(", "[").replace(")", "]")
                    sb.append("($cleanId) u\n")
                } else {
                    sb.append("u\n")
                }

                // Process children inside Group
                processChildrenNodes(
                    parent = element,
                    parentStyle = nodeStyle,
                    sb = sb,
                    idMap = idMap,
                    gradientMap = gradientMap,
                    fullGradientMap = fullGradientMap,
                    clipPathMap = clipPathMap,
                    cssClassMap = cssClassMap,
                    depth = depth + 1
                )

                // AI Group End Operator: 'U'
                sb.append("U\n")

                if (needsStateIsolation) {
                    sb.append("Q\n")
                }
            }

            "use" -> {
                val href = (element.getAttribute("href").ifEmpty { element.getAttribute("xlink:href") }).trim()
                if (href.isNotEmpty()) {
                    val targetId = href.removePrefix("#")
                    val targetElem = idMap[targetId]
                    if (targetElem != null) {
                        sb.append("q\n")
                        val x = parseLengthToPt(element.getAttribute("x"), 0f)
                        val y = parseLengthToPt(element.getAttribute("y"), 0f)
                        if (x != 0f || y != 0f) {
                            sb.append(String.format(Locale.US, "%.3f %.3f translate\n", x, y))
                        }
                        if (hasTransform) {
                            sb.append(convertSvgTransformToPostScript(transformStr))
                        }
                        sb.append("u\n")
                        processNode(
                            element = targetElem,
                            parentStyle = nodeStyle,
                            sb = sb,
                            idMap = idMap,
                            gradientMap = gradientMap,
                            fullGradientMap = fullGradientMap,
                            clipPathMap = clipPathMap,
                            cssClassMap = cssClassMap,
                            depth = depth + 1
                        )
                        sb.append("U\n")
                        sb.append("Q\n")
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

                val needsLocalState = hasTransform || hasClipPath
                if (needsLocalState) {
                    sb.append("q\n")
                }

                if (hasClipPath) {
                    val clipId = clipPathAttr.substringAfter("url(").substringBefore(")").removePrefix("#").removeSurrounding("'", "\"").trim()
                    val clipDef = clipPathMap[clipId]
                    if (clipDef != null) {
                        emitClipPath(sb, clipDef.element)
                    }
                }

                if (hasTransform) {
                    sb.append(convertSvgTransformToPostScript(transformStr))
                }

                val fillStr = nodeStyle.fill ?: "black"
                val gradId = if (fillStr.startsWith("url(")) {
                    fillStr.substringAfter("url(").substringBefore(")").removePrefix("#").removeSurrounding("'", "\"").trim()
                } else null

                val gradDef = if (gradId != null) fullGradientMap[gradId] else null
                val fillRgb = if (gradDef == null) parseColorToRgb(fillStr, gradientMap) else null

                val strokeRgb = parseColorToRgb(nodeStyle.stroke ?: "none", gradientMap)
                val strokeWidth = nodeStyle.strokeWidth ?: 1f
                val hasStroke = strokeRgb != null && strokeWidth > 0f

                if (gradDef != null && gradDef.stops.isNotEmpty()) {
                    sb.append("q\n")
                    sb.append("n\n")
                    sb.append(pathCommands)
                    if (nodeStyle.fillRule == "evenodd") {
                        sb.append("W* n\n")
                    } else {
                        sb.append("W n\n")
                    }
                    writeGradientShading(sb, gradDef)
                    sb.append("Q\n")

                    if (hasStroke) {
                        emitStrokeState(sb, nodeStyle, strokeRgb!!, strokeWidth)
                        sb.append("n\n")
                        sb.append(pathCommands)
                        sb.append("s\n")
                    }
                } else {
                    val isEvenOdd = nodeStyle.fillRule == "evenodd"

                    if (fillRgb != null && hasStroke) {
                        sb.append(String.format(Locale.US, "%.3f %.3f %.3f rg\n", fillRgb[0], fillRgb[1], fillRgb[2]))
                        emitStrokeState(sb, nodeStyle, strokeRgb!!, strokeWidth)
                        sb.append("n\n")
                        sb.append(pathCommands)
                        sb.append(if (isEvenOdd) "b*\n" else "b\n")
                    } else if (fillRgb != null) {
                        sb.append(String.format(Locale.US, "%.3f %.3f %.3f rg\n", fillRgb[0], fillRgb[1], fillRgb[2]))
                        sb.append("n\n")
                        sb.append(pathCommands)
                        sb.append(if (isEvenOdd) "f*\n" else "f\n")
                    } else if (hasStroke) {
                        emitStrokeState(sb, nodeStyle, strokeRgb!!, strokeWidth)
                        sb.append("n\n")
                        sb.append(pathCommands)
                        sb.append("s\n")
                    }
                }

                if (needsLocalState) {
                    sb.append("Q\n")
                }
            }
        }
    }

    private fun emitStrokeState(sb: StringBuilder, nodeStyle: StyleContext, strokeRgb: FloatArray, strokeWidth: Float) {
        sb.append(String.format(Locale.US, "%.3f %.3f %.3f RG\n", strokeRgb[0], strokeRgb[1], strokeRgb[2]))
        sb.append(String.format(Locale.US, "%.3f w\n", strokeWidth))
        val lineCapInt = when (nodeStyle.strokeLineCap) {
            "round" -> 1
            "square" -> 2
            else -> 0
        }
        val lineJoinInt = when (nodeStyle.strokeLineJoin) {
            "round" -> 1
            "bevel" -> 2
            else -> 0
        }
        sb.append(String.format(Locale.US, "%d J %d j\n", lineCapInt, lineJoinInt))
    }

    private fun emitClipPath(sb: StringBuilder, clipElement: Element) {
        val childNodes = clipElement.childNodes
        for (i in 0 until childNodes.length) {
            val node = childNodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val elem = node as Element
                val tag = elem.tagName.lowercase(Locale.US)
                val pathCmds = when (tag) {
                    "path" -> convertPathToPostScript(elem.getAttribute("d"))
                    "rect" -> convertRectToPostScript(elem)
                    "circle" -> convertCircleToPostScript(elem)
                    "ellipse" -> convertEllipseToPostScript(elem)
                    "polygon" -> convertPolygonToPostScript(elem, isClosed = true)
                    else -> ""
                }
                if (pathCmds.isNotBlank()) {
                    val clipRule = elem.getAttribute("clip-rule").ifEmpty { elem.getAttribute("fill-rule") }
                    val isEvenOdd = clipRule.equals("evenodd", ignoreCase = true)
                    sb.append("n\n")
                    sb.append(pathCmds)
                    if (isEvenOdd) {
                        sb.append("W* n\n")
                    } else {
                        sb.append("W n\n")
                    }
                }
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
                "%.3f %.3f m %.3f %.3f l %.3f %.3f l %.3f %.3f l h\n",
                x, y, x + w, y, x + w, y + h, x, y + h
            )
        }

        val kx = rx * 0.55228475f
        val ky = ry * 0.55228475f
        val sb = StringBuilder()
        sb.append(String.format(Locale.US, "%.3f %.3f m\n", x + rx, y))
        sb.append(String.format(Locale.US, "%.3f %.3f l\n", x + w - rx, y))
        sb.append(String.format(Locale.US, "%.3f %.3f %.3f %.3f %.3f %.3f c\n", x + w - rx + kx, y, x + w, y + ry - ky, x + w, y + ry))
        sb.append(String.format(Locale.US, "%.3f %.3f l\n", x + w, y + h - ry))
        sb.append(String.format(Locale.US, "%.3f %.3f %.3f %.3f %.3f %.3f c\n", x + w, y + h - ry + ky, x + w - rx + kx, y + h, x + w - rx, y + h))
        sb.append(String.format(Locale.US, "%.3f %.3f l\n", x + rx, y + h))
        sb.append(String.format(Locale.US, "%.3f %.3f %.3f %.3f %.3f %.3f c\n", x + rx - kx, y + h, x, y + h - ry + ky, x, y + h - ry))
        sb.append(String.format(Locale.US, "%.3f %.3f l\n", x, y + ry))
        sb.append(String.format(Locale.US, "%.3f %.3f %.3f %.3f %.3f %.3f c\n", x, y + ry - ky, x + rx - kx, y, x + rx, y))
        sb.append("h\n")
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
            "%.3f %.3f m %.3f %.3f %.3f %.3f %.3f %.3f c %.3f %.3f %.3f %.3f %.3f %.3f c %.3f %.3f %.3f %.3f %.3f %.3f c %.3f %.3f %.3f %.3f %.3f %.3f c h\n",
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
        return String.format(Locale.US, "%.3f %.3f m %.3f %.3f l\n", x1, y1, x2, y2)
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
                sb.append(String.format(Locale.US, "%.3f %.3f m\n", x, y))
            } else {
                sb.append(String.format(Locale.US, "%.3f %.3f l\n", x, y))
            }
            i += 2
        }
        if (isClosed) sb.append("h\n")
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
                            sb.append(String.format(Locale.US, "%.3f %.3f m\n", finalX, finalY))
                            startX = finalX
                            startY = finalY
                            isFirstPair = false
                        } else {
                            sb.append(String.format(Locale.US, "%.3f %.3f l\n", finalX, finalY))
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

                        sb.append(String.format(Locale.US, "%.3f %.3f l\n", finalX, finalY))
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

                        sb.append(String.format(Locale.US, "%.3f %.3f l\n", finalX, currentY))
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

                        sb.append(String.format(Locale.US, "%.3f %.3f l\n", currentX, finalY))
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

                        sb.append(String.format(Locale.US, "%.3f %.3f %.3f %.3f %.3f %.3f c\n", fx1, fy1, fx2, fy2, fx, fy))
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

                        sb.append(String.format(Locale.US, "%.3f %.3f %.3f %.3f %.3f %.3f c\n", fx1, fy1, fx2, fy2, fx, fy))
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

                        sb.append(String.format(Locale.US, "%.3f %.3f %.3f %.3f %.3f %.3f c\n", cx1, cy1, cx2, cy2, fx, fy))
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

                        sb.append(String.format(Locale.US, "%.3f %.3f %.3f %.3f %.3f %.3f c\n", cx1, cy1, cx2, cy2, fx, fy))
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
                        val largeArcFlag = tokenizer.nextFlag() ?: break
                        val sweepFlag = tokenizer.nextFlag() ?: break
                        val x = tokenizer.nextNumber() ?: break
                        val y = tokenizer.nextNumber() ?: break

                        val fx = if (isRelative) currentX + x else x
                        val fy = if (isRelative) currentY + y else y

                        val beziers = endpointToCubicBeziers(
                            currentX, currentY, rx, ry, xAxisRotation,
                            largeArcFlag != 0f, sweepFlag != 0f, fx, fy
                        )

                        for (b in beziers) {
                            sb.append(String.format(Locale.US, "%.3f %.3f %.3f %.3f %.3f %.3f c\n", b[0], b[1], b[2], b[3], b[4], b[5]))
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
                    sb.append("h\n")
                    currentX = startX
                    currentY = startY
                    lastControlX = currentX
                    lastControlY = currentY
                    lastQuadControlX = currentX
                    lastQuadControlY = currentY
                    lastCmd = if (isRelative) 'z' else 'Z'
                }

                else -> {}
            }
        }

        return sb.toString()
    }

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

        fun nextFlag(): Float? {
            skipWhitespaceAndCommas()
            if (pos >= len) return null
            val c = d[pos]
            if (c == '0' || c == '1') {
                pos++
                return if (c == '1') 1f else 0f
            }
            return nextNumber()
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
        gradientMap: MutableMap<String, String>,
        fullGradientMap: MutableMap<String, SvgGradientDef>,
        clipPathMap: MutableMap<String, SvgClipPathDef>
    ) {
        val idAttr = element.getAttribute("id").trim()
        if (idAttr.isNotEmpty()) {
            idMap[idAttr] = element
        }

        val tagName = element.tagName.lowercase(Locale.US)
        if (tagName == "clippath" && idAttr.isNotEmpty()) {
            clipPathMap[idAttr] = SvgClipPathDef(idAttr, element)
        }

        if (tagName == "lineargradient" || tagName == "radialgradient") {
            if (idAttr.isNotEmpty()) {
                val isRadial = tagName == "radialgradient"
                val stopsList = mutableListOf<GradientStopDef>()
                val stops = element.getElementsByTagName("stop")
                var firstColor = "#000000"

                for (s in 0 until stops.length) {
                    val stopElem = stops.item(s) as Element
                    val offsetStr = stopElem.getAttribute("offset").trim()
                    val offset = if (offsetStr.endsWith("%")) {
                        (offsetStr.dropLast(1).toFloatOrNull() ?: 0f) / 100f
                    } else {
                        offsetStr.toFloatOrNull() ?: if (stops.length > 1) (s.toFloat() / (stops.length - 1)) else 0f
                    }

                    val colorAttr = stopElem.getAttribute("stop-color").trim()
                    val styleAttr = stopElem.getAttribute("style").trim()
                    val styleMap = parseStyleDeclarations(styleAttr)
                    val stopColorStr = colorAttr.ifEmpty { styleMap["stop-color"] ?: "#000000" }
                    if (s == 0) firstColor = stopColorStr

                    val rgb = parseColorToRgb(stopColorStr, gradientMap) ?: floatArrayOf(0f, 0f, 0f)
                    stopsList.add(GradientStopDef(offset.coerceIn(0f, 1f), rgb[0], rgb[1], rgb[2]))
                }

                if (stopsList.isEmpty()) {
                    stopsList.add(GradientStopDef(0f, 0f, 0f, 0f))
                    stopsList.add(GradientStopDef(1f, 1f, 1f, 1f))
                } else if (stopsList.size == 1) {
                    stopsList.add(GradientStopDef(1f, stopsList[0].r, stopsList[0].g, stopsList[0].b))
                }
                stopsList.sortBy { it.offset }
                gradientMap[idAttr] = firstColor

                val gradUnits = element.getAttribute("gradientUnits").trim()
                val isUserSpace = gradUnits.equals("userSpaceOnUse", ignoreCase = true)
                val gradTransform = element.getAttribute("gradientTransform").trim()

                fullGradientMap[idAttr] = SvgGradientDef(
                    id = idAttr,
                    isRadial = isRadial,
                    x1Str = element.getAttribute("x1").ifEmpty { "0%" },
                    y1Str = element.getAttribute("y1").ifEmpty { "0%" },
                    x2Str = element.getAttribute("x2").ifEmpty { "100%" },
                    y2Str = element.getAttribute("y2").ifEmpty { "0%" },
                    cxStr = element.getAttribute("cx").ifEmpty { "50%" },
                    cyStr = element.getAttribute("cy").ifEmpty { "50%" },
                    rStr = element.getAttribute("r").ifEmpty { "50%" },
                    fxStr = element.getAttribute("fx"),
                    fyStr = element.getAttribute("fy"),
                    isUserSpace = isUserSpace,
                    stops = stopsList,
                    transform = gradTransform
                )
            }
        }

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.ELEMENT_NODE) {
                indexElementsAndGradients(child as Element, idMap, gradientMap, fullGradientMap, clipPathMap)
            }
        }
    }

    private fun writeGradientShading(
        sb: StringBuilder,
        grad: SvgGradientDef
    ) {
        if (grad.transform.isNotEmpty()) {
            sb.append(convertSvgTransformToPostScript(grad.transform))
        }

        val stops = grad.stops
        if (grad.isRadial) {
            val cx = parseCoordinateOrPercent(grad.cxStr, 256f)
            val cy = parseCoordinateOrPercent(grad.cyStr, 256f)
            val r = parseCoordinateOrPercent(grad.rStr, 256f)
            val fx = if (grad.fxStr.isNotEmpty()) parseCoordinateOrPercent(grad.fxStr, 256f) else cx
            val fy = if (grad.fyStr.isNotEmpty()) parseCoordinateOrPercent(grad.fyStr, 256f) else cy

            sb.append("<<\n")
            sb.append("  /ShadingType 3\n")
            sb.append("  /ColorSpace /DeviceRGB\n")
            sb.append(String.format(Locale.US, "  /Coords [%.3f %.3f 0.0 %.3f %.3f %.3f]\n", fx, fy, cx, cy, r))
            writeFunction(sb, stops)
            sb.append("  /Extend [true true]\n")
            sb.append(">> shfill\n")
        } else {
            val x1 = parseCoordinateOrPercent(grad.x1Str, 512f)
            val y1 = parseCoordinateOrPercent(grad.y1Str, 512f)
            val x2 = parseCoordinateOrPercent(grad.x2Str, 512f)
            val y2 = parseCoordinateOrPercent(grad.y2Str, 512f)

            sb.append("<<\n")
            sb.append("  /ShadingType 2\n")
            sb.append("  /ColorSpace /DeviceRGB\n")
            sb.append(String.format(Locale.US, "  /Coords [%.3f %.3f %.3f %.3f]\n", x1, y1, x2, y2))
            writeFunction(sb, stops)
            sb.append("  /Extend [true true]\n")
            sb.append(">> shfill\n")
        }
    }

    private fun writeFunction(sb: StringBuilder, stops: List<GradientStopDef>) {
        if (stops.size == 2) {
            val s0 = stops[0]
            val s1 = stops[1]
            sb.append("  /Function <<\n")
            sb.append("    /FunctionType 2\n")
            sb.append("    /Domain [0.0 1.0]\n")
            sb.append(String.format(Locale.US, "    /C0 [%.3f %.3f %.3f]\n", s0.r, s0.g, s0.b))
            sb.append(String.format(Locale.US, "    /C1 [%.3f %.3f %.3f]\n", s1.r, s1.g, s1.b))
            sb.append("    /N 1.0\n")
            sb.append("  >>\n")
        } else if (stops.size > 2) {
            val segCount = stops.size - 1
            sb.append("  /Function <<\n")
            sb.append("    /FunctionType 3\n")
            sb.append("    /Domain [0.0 1.0]\n")
            sb.append("    /Functions [\n")
            for (i in 0 until segCount) {
                val s0 = stops[i]
                val s1 = stops[i + 1]
                sb.append(String.format(Locale.US, "      << /FunctionType 2 /Domain [0.0 1.0] /C0 [%.3f %.3f %.3f] /C1 [%.3f %.3f %.3f] /N 1.0 >>\n", s0.r, s0.g, s0.b, s1.r, s1.g, s1.b))
            }
            sb.append("    ]\n")
            val boundsList = mutableListOf<Float>()
            for (i in 1 until segCount) {
                val prev = if (boundsList.isNotEmpty()) boundsList.last() else stops[0].offset
                val curr = stops[i].offset.coerceAtLeast(prev + 0.0001f)
                boundsList.add(curr)
            }
            val boundsStr = boundsList.joinToString(" ") { String.format(Locale.US, "%.4f", it) }
            sb.append("    /Bounds [$boundsStr]\n")
            val encodeStr = (0 until segCount).joinToString(" ") { "0.0 1.0" }
            sb.append("    /Encode [$encodeStr]\n")
            sb.append("  >>\n")
        } else {
            val s0 = stops.firstOrNull() ?: GradientStopDef(0f, 0f, 0f, 0f)
            sb.append("  /Function << /FunctionType 2 /Domain [0.0 1.0] ")
            sb.append(String.format(Locale.US, "/C0 [%.3f %.3f %.3f] /C1 [%.3f %.3f %.3f] /N 1.0 >>\n", s0.r, s0.g, s0.b, s0.r, s0.g, s0.b))
        }
    }

    private fun parseCoordinateOrPercent(valueStr: String, baseSize: Float): Float {
        val s = valueStr.trim()
        if (s.isEmpty()) return 0f
        return if (s.endsWith("%")) {
            val pct = s.dropLast(1).toFloatOrNull() ?: 0f
            (pct / 100f) * baseSize
        } else {
            val num = s.toFloatOrNull() ?: 0f
            if (num in 0f..1f && baseSize > 1f) num * baseSize else num
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
                8 -> {
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
