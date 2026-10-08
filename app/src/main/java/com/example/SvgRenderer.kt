package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.coroutines.resume

object SvgRenderer {

    fun getSvgAspectRatio(svgBytes: ByteArray): Float {
        try {
            val svg = SVG.getFromInputStream(ByteArrayInputStream(svgBytes))
            val viewBox: RectF? = svg.documentViewBox
            if (viewBox != null && viewBox.width() > 0f && viewBox.height() > 0f) {
                return viewBox.width() / viewBox.height()
            }
            if (svg.documentWidth > 0f && svg.documentHeight > 0f) {
                return svg.documentWidth / svg.documentHeight
            }
        } catch (_: Exception) {}

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

    suspend fun renderSvgToHighResJpgBytes(
        context: Context,
        svgBytes: ByteArray,
        targetLongEdge: Int = 4000
    ): ByteArray? = withContext(Dispatchers.Default) {
        try {
            // 1. Direct Vector rendering using AndroidSVG
            val svg = SVG.getFromInputStream(ByteArrayInputStream(svgBytes))
            val viewBox: RectF? = svg.documentViewBox

            val docWidth = if (viewBox != null && viewBox.width() > 0f) {
                viewBox.width()
            } else if (svg.documentWidth > 0f) {
                svg.documentWidth
            } else {
                1000f
            }

            val docHeight = if (viewBox != null && viewBox.height() > 0f) {
                viewBox.height()
            } else if (svg.documentHeight > 0f) {
                svg.documentHeight
            } else {
                1000f
            }

            val aspectRatio = docWidth / docHeight

            val (targetWidth, targetHeight) = if (aspectRatio >= 1.0f) {
                Pair(targetLongEdge, (targetLongEdge / aspectRatio).toInt().coerceAtLeast(100))
            } else {
                Pair((targetLongEdge * aspectRatio).toInt().coerceAtLeast(100), targetLongEdge)
            }

            var bitmap: Bitmap? = try {
                Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            } catch (oom: Throwable) {
                System.gc()
                try {
                    val fallbackEdge = 2500
                    val (fw, fh) = if (aspectRatio >= 1.0f) {
                        Pair(fallbackEdge, (fallbackEdge / aspectRatio).toInt().coerceAtLeast(100))
                    } else {
                        Pair((fallbackEdge * aspectRatio).toInt().coerceAtLeast(100), fallbackEdge)
                    }
                    Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888)
                } catch (_: Throwable) {
                    null
                }
            }

            if (bitmap != null) {
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                val scaleX = targetWidth.toFloat() / docWidth
                val scaleY = targetHeight.toFloat() / docHeight

                canvas.save()
                canvas.scale(scaleX, scaleY)
                if (viewBox != null) {
                    canvas.translate(-viewBox.left, -viewBox.top)
                }
                svg.renderToCanvas(canvas)
                canvas.restore()

                val outputStream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                val rawBytes = outputStream.toByteArray()
                bitmap.recycle()

                if (rawBytes.isNotEmpty()) {
                    return@withContext rawBytes
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Fallback to WebView with enableSlowWholeDocumentDraw
        return@withContext renderSvgToJpgWithWebView(context, svgBytes, targetLongEdge)
    }

    private suspend fun renderSvgToJpgWithWebView(
        context: Context,
        svgBytes: ByteArray,
        targetLongEdge: Int
    ): ByteArray? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            var webView: WebView? = null
            var isResumed = false

            fun cleanupAndResume(result: ByteArray?) {
                if (!isResumed) {
                    isResumed = true
                    try {
                        webView?.stopLoading()
                        webView?.destroy()
                        webView = null
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    if (continuation.isActive) {
                        continuation.resume(result)
                    }
                }
            }

            val timeoutHandler = Handler(Looper.getMainLooper())
            val timeoutRunnable = Runnable { cleanupAndResume(null) }
            timeoutHandler.postDelayed(timeoutRunnable, 6000)

            try {
                WebView.enableSlowWholeDocumentDraw()

                val aspectRatio = getSvgAspectRatio(svgBytes)
                val (targetWidth, targetHeight) = if (aspectRatio >= 1.0f) {
                    Pair(targetLongEdge, (targetLongEdge / aspectRatio).toInt().coerceAtLeast(100))
                } else {
                    Pair((targetLongEdge * aspectRatio).toInt().coerceAtLeast(100), targetLongEdge)
                }

                val view = WebView(context)
                webView = view
                view.isVerticalScrollBarEnabled = false
                view.isHorizontalScrollBarEnabled = false

                val settings = view.settings
                settings.javaScriptEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true

                view.layout(0, 0, targetWidth, targetHeight)

                val svgBase64 = android.util.Base64.encodeToString(svgBytes, android.util.Base64.NO_WRAP)
                val html = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                    <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    html, body {
                        width: ${targetWidth}px;
                        height: ${targetHeight}px;
                        overflow: hidden;
                        background-color: #ffffff;
                    }
                    img {
                        width: 100%;
                        height: 100%;
                        object-fit: fill;
                        display: block;
                    }
                    </style>
                    </head>
                    <body>
                    <img src="data:image/svg+xml;base64,$svgBase64" />
                    </body>
                    </html>
                """.trimIndent()

                view.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(v: WebView?, url: String?) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            timeoutHandler.removeCallbacks(timeoutRunnable)
                            try {
                                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(bitmap)
                                canvas.drawColor(Color.WHITE)
                                v?.draw(canvas)

                                val outputStream = ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, outputStream)
                                val rawJpgBytes = outputStream.toByteArray()

                                bitmap.recycle()
                                cleanupAndResume(rawJpgBytes)
                            } catch (t: Throwable) {
                                t.printStackTrace()
                                cleanupAndResume(null)
                            }
                        }, 200)
                    }
                }

                view.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)

                continuation.invokeOnCancellation {
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    cleanupAndResume(null)
                }
            } catch (t: Throwable) {
                t.printStackTrace()
                timeoutHandler.removeCallbacks(timeoutRunnable)
                cleanupAndResume(null)
            }
        }
    }

    suspend fun renderSvgToPngBase64(
        context: Context,
        svgBytes: ByteArray
    ): String? = withContext(Dispatchers.Default) {
        try {
            val svg = SVG.getFromInputStream(ByteArrayInputStream(svgBytes))
            val viewBox: RectF? = svg.documentViewBox

            val docWidth = if (viewBox != null && viewBox.width() > 0f) viewBox.width() else if (svg.documentWidth > 0f) svg.documentWidth else 512f
            val docHeight = if (viewBox != null && viewBox.height() > 0f) viewBox.height() else if (svg.documentHeight > 0f) svg.documentHeight else 512f

            val size = 512
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val scale = Math.min(size.toFloat() / docWidth, size.toFloat() / docHeight)
            val dx = (size - docWidth * scale) / 2f
            val dy = (size - docHeight * scale) / 2f

            canvas.save()
            canvas.translate(dx, dy)
            canvas.scale(scale, scale)
            if (viewBox != null) {
                canvas.translate(-viewBox.left, -viewBox.top)
            }
            svg.renderToCanvas(canvas)
            canvas.restore()

            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            val pngBytes = outputStream.toByteArray()
            bitmap.recycle()
            return@withContext android.util.Base64.encodeToString(pngBytes, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
}
