package com.example

import android.util.Log
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Export ZIP (EPS + JPG). Terpisah dari konverter JPG.
 * Aturan penting: per item, EPS dan JPG dibuat DULU keduanya. Kalau salah satu gagal,
 * item itu tidak ditulis sama sekali dan dicatat sebagai gagal (tidak ada lagi JPG hilang diam-diam).
 */
object ZipEpsJpgExporter {
    private const val TAG = "ZipEpsJpgExporter"

    data class Item(
        val baseName: String,          // nama unik tanpa ekstensi
        val svgBytes: ByteArray,
        val title: String,
        val description: String,
        val keywords: List<String>,
        val creator: String
    )

    data class Result(val succeeded: List<String>, val failed: Map<String, String>)

    fun export(
        out: OutputStream,
        items: List<Item>,
        useFolders: Boolean = true,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Result {
        val ok = mutableListOf<String>()
        val bad = linkedMapOf<String, String>()

        ZipOutputStream(out.buffered()).use { zos ->
            items.forEachIndexed { index, item ->
                try {
                    val eps = SvgToEpsConverter.convertSvgToEps(
                        item.svgBytes, item.title, item.description, item.keywords, item.creator
                    )
                    val rawJpg = SvgToJpgConverter.convert(item.svgBytes)
                    val jpg = XmpInjector.injectIntoJpeg(
                        rawJpg, item.title, item.description, item.keywords, item.creator
                    )

                    val prefix = if (useFolders) "${item.baseName}/" else ""
                    zos.putNextEntry(ZipEntry("$prefix${item.baseName}.eps"))
                    zos.write(eps)
                    zos.closeEntry()
                    zos.putNextEntry(ZipEntry("$prefix${item.baseName}.jpg"))
                    zos.write(jpg)
                    zos.closeEntry()
                    ok += item.baseName
                } catch (t: Throwable) {
                    Log.e(TAG, "Gagal export ${item.baseName}", t)
                    bad[item.baseName] = t.message ?: t.javaClass.simpleName
                } finally {
                    onProgress(index + 1, items.size)
                }
            }
        }
        return Result(ok, bad)
    }
}
