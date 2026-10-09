package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import com.caverock.androidsvg.SVG
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Konverter SVG -> JPG (tanpa WebView, tanpa timeout).
 * Alur sama dengan versi web: gambar SVG ke canvas, isi latar putih, kompres JPEG.
 *
 * Dependency (build.gradle): implementation("com.caverock:androidsvg-aar:1.4")
 */
object SvgToJpgConverter {
    private const val TAG = "SvgToJpgConverter"

    /** Coba 4000px, kalau OOM turun ke 3000 lalu 2000. Melempar exception jika semua gagal. */
    fun convert(
        svgBytes: ByteArray,
        edges: List<Int> = listOf(4000, 3000, 2000),
        quality: Int = 92
    ): ByteArray {
        var lastError: Throwable? = null
        for (edge in edges) {
            try {
                return render(svgBytes, edge, quality)
            } catch (t: Throwable) {
                lastError = t
                Log.w(TAG, "Render ${edge}px gagal, coba lebih kecil", t)
                System.gc()
            }
        }
        throw IllegalStateException("Konversi SVG ke JPG gagal di semua ukuran", lastError)
    }

    private fun render(svgBytes: ByteArray, longEdge: Int, quality: Int): ByteArray {
        val svg = SVG.getFromInputStream(ByteArrayInputStream(svgBytes))

        // Tentukan ukuran asli; pastikan viewBox ada supaya bisa di-scale.
        val vb = svg.documentViewBox
        val rawW = if (vb != null) vb.width() else svg.documentWidth
        val rawH = if (vb != null) vb.height() else svg.documentHeight
        val srcW = if (rawW > 0f) rawW else 1000f
        val srcH = if (rawH > 0f) rawH else 1000f
        if (vb == null) svg.setDocumentViewBox(0f, 0f, srcW, srcH)

        val aspect = srcW / srcH
        val (w, h) = if (aspect >= 1f) {
            longEdge to (longEdge / aspect).toInt().coerceAtLeast(100)
        } else {
            (longEdge * aspect).toInt().coerceAtLeast(100) to longEdge
        }
        svg.setDocumentWidth(w.toFloat())
        svg.setDocumentHeight(h.toFloat())

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE) // JPEG tidak punya alpha
            svg.renderToCanvas(canvas)
            val out = ByteArrayOutputStream()
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) { "Bitmap.compress gagal" }
            return out.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }
}
