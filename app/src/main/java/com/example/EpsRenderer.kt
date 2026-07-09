package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.Base64
import java.io.ByteArrayOutputStream

object EpsRenderer {

    fun renderEpsToJpegBase64(context: Context, epsBytes: ByteArray, maxPreviewSize: Int = 512): String? {
        try {
            val epsText = String(epsBytes, Charsets.UTF_8)
            val lines = epsText.lineSequence()

            // 1. Ekstrak Ukuran Canvas Asli dari BoundingBox (Contoh: %%BoundingBox: 0 0 4000 4000)
            var originalWidth = 4000f
            var originalHeight = 4000f
            val bboxRegex = Regex("""%%BoundingBox:\s*(\d+)\s+(\d+)\s+(\d+)\s+(\d+)""")
            
            for (line in lines.take(50)) { // Cek 50 baris pertama saja untuk efisiensi
                val match = bboxRegex.find(line)
                if (match != null) {
                    originalWidth = match.groupValues[3].toFloat()
                    originalHeight = match.groupValues[4].toFloat()
                    break
                }
            }

            // Fallback jika nilainya tidak logis
            if (originalWidth <= 0f) originalWidth = 512f
            if (originalHeight <= 0f) originalHeight = 512f

            // 2. Hitung Skala Pengecilan agar file pratinjau tetap kecil & ringan
            val scale = maxPreviewSize.toFloat() / Math.max(originalWidth, originalHeight)
            val previewWidth = (originalWidth * scale).toInt().coerceAtLeast(1)
            val previewHeight = (originalHeight * scale).toInt().coerceAtLeast(1)

            // 3. Siapkan Bitmap, Canvas, Paint, dan Path Android
            val bitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            // Latar belakang default putih
            canvas.drawColor(Color.WHITE)

            val paint = Paint().apply {
                isAntiAlias = true
                style = Paint.Style.FILL // Default fill seperti struktur EPS Lineva
            }
            var currentPath = Path()

            // 4. Parser Baris demi Baris menggunakan Pola Regex
            // Pola untuk mencocokkan angka koordinat (Desimal atau Integer)
            val num = """(-?\d+(?:\.\d+)?)"""
            val movetoRegex = Regex("""$num\s+$num\s+moveto""")
            val linetoRegex = Regex("""$num\s+$num\s+lineto""")
            val curvetoRegex = Regex("""$num\s+$num\s+$num\s+$num\s+$num\s+$num\s+curveto""")
            val rgbRegex = Regex("""$num\s+$num\s+$num\s+setrgbcolor""")

            for (rawLine in epsText.lineSequence()) {
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("%")) continue

                when {
                    line.startsWith("newpath") -> {
                        currentPath = Path()
                    }
                    line.endsWith("moveto") -> {
                        movetoRegex.find(line)?.let {
                            val x = it.groupValues[1].toFloat() * scale
                            // EPS menggunakan koordinat Y-up (nol di bawah), Android menggunakan Y-down (nol di atas)
                            val y = (originalHeight - it.groupValues[2].toFloat()) * scale
                            currentPath.moveTo(x, y)
                        }
                    }
                    line.endsWith("lineto") -> {
                        linetoRegex.find(line)?.let {
                            val x = it.groupValues[1].toFloat() * scale
                            val y = (originalHeight - it.groupValues[2].toFloat()) * scale
                            currentPath.lineTo(x, y)
                        }
                    }
                    line.endsWith("curveto") -> {
                        curvetoRegex.find(line)?.let {
                            val cp1x = it.groupValues[1].toFloat() * scale
                            val cp1y = (originalHeight - it.groupValues[2].toFloat()) * scale
                            val cp2x = it.groupValues[3].toFloat() * scale
                            val cp2y = (originalHeight - it.groupValues[4].toFloat()) * scale
                            val x = it.groupValues[5].toFloat() * scale
                            val y = (originalHeight - it.groupValues[6].toFloat()) * scale
                            
                            // PostScript menggunakan Bezier Kubik (Cubic Bezier)
                            currentPath.cubicTo(cp1x, cp1y, cp2x, cp2y, x, y)
                        }
                    }
                    line.endsWith("closepath") -> {
                        currentPath.close()
                    }
                    line.endsWith("setrgbcolor") -> {
                        rgbRegex.find(line)?.let {
                            val r = (it.groupValues[1].toFloat() * 255).toInt().coerceIn(0, 255)
                            val g = (it.groupValues[2].toFloat() * 255).toInt().coerceIn(0, 255)
                            val b = (it.groupValues[3].toFloat() * 255).toInt().coerceIn(0, 255)
                            paint.color = Color.rgb(r, g, b)
                        }
                    }
                    line == "fill" -> {
                        canvas.drawPath(currentPath, paint)
                    }
                }
            }

            // 5. Kompres Gambar Menjadi JPG Base64 untuk dikirim ke Gemini
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val previewBytes = outputStream.toByteArray()
            
            // Bebaskan memori bitmap dari RAM
            bitmap.recycle()

            return Base64.encodeToString(previewBytes, Base64.NO_WRAP)

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
