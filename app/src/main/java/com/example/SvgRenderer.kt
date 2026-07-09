package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

object SvgRenderer {
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
