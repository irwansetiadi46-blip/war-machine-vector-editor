package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
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

    suspend fun renderSvgToHighResJpgBytes(context: Context, svgBytes: ByteArray, targetLongEdge: Int = 4000): ByteArray? {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                try {
                    val aspectRatio = getSvgAspectRatio(svgBytes)
                    val (targetWidth, targetHeight) = if (aspectRatio >= 1.0f) {
                        Pair(targetLongEdge, (targetLongEdge / aspectRatio).toInt().coerceAtLeast(100))
                    } else {
                        Pair((targetLongEdge * aspectRatio).toInt().coerceAtLeast(100), targetLongEdge)
                    }

                    val webView = WebView(context)
                    webView.isVerticalScrollBarEnabled = false
                    webView.isHorizontalScrollBarEnabled = false

                    val settings = webView.settings
                    settings.javaScriptEnabled = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true

                    webView.layout(0, 0, targetWidth, targetHeight)

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

                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                try {
                                    val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                                    val canvas = Canvas(bitmap)
                                    canvas.drawColor(Color.WHITE)
                                    webView.draw(canvas)

                                    val outputStream = ByteArrayOutputStream()
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                                    val rawJpgBytes = outputStream.toByteArray()

                                    bitmap.recycle()
                                    continuation.resume(rawJpgBytes)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    continuation.resume(null)
                                }
                            }, 200)
                        }
                    }

                    webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
                } catch (e: Exception) {
                    e.printStackTrace()
                    continuation.resume(null)
                }
            }
        }
    }

    suspend fun renderSvgToPngBase64(context: Context, svgBytes: ByteArray): String? {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                try {
                    val webView = WebView(context)
                    webView.isVerticalScrollBarEnabled = false
                    webView.isHorizontalScrollBarEnabled = false
                    
                    val settings = webView.settings
                    settings.javaScriptEnabled = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    
                    val size = 512
                    webView.layout(0, 0, size, size)

                    val svgBase64 = android.util.Base64.encodeToString(svgBytes, android.util.Base64.NO_WRAP)
                    val html = """
                        <html>
                        <head>
                        <style>
                        html, body {
                            margin: 0;
                            padding: 0;
                            width: 100%;
                            height: 100%;
                            overflow: hidden;
                            background-color: transparent;
                        }
                        img {
                            width: 100%;
                            height: 100%;
                            object-fit: contain;
                        }
                        </style>
                        </head>
                        <body>
                        <img src="data:image/svg+xml;base64,$svgBase64" />
                        </body>
                        </html>
                    """.trimIndent()

                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                try {
                                    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                                    val canvas = Canvas(bitmap)
                                    webView.draw(canvas)
                                    
                                    val outputStream = ByteArrayOutputStream()
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                                    val pngBytes = outputStream.toByteArray()
                                    val base64String = android.util.Base64.encodeToString(pngBytes, android.util.Base64.NO_WRAP)
                                    
                                    bitmap.recycle()
                                    continuation.resume(base64String)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    continuation.resume(null)
                                }
                            }, 150)
                        }
                    }

                    webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "utf-8", null)
                } catch (e: Exception) {
                    e.printStackTrace()
                    continuation.resume(null)
                }
            }
        }
    }
}

