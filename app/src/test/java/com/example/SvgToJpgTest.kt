package com.example

import android.graphics.BitmapFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SvgToJpgTest {

    private val sampleSvg = """
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 500 500" width="500" height="500">
            <rect x="10" y="10" width="480" height="480" fill="#FF5722"/>
            <circle cx="250" cy="250" r="100" fill="#4CAF50"/>
        </svg>
    """.trimIndent().toByteArray(StandardCharsets.UTF_8)

    @Test
    fun testSvgToJpgConverterRendersValidJpeg() {
        val jpgBytes = SvgToJpgConverter.convert(sampleSvg, edges = listOf(1000, 500), quality = 90)
        assertNotNull(jpgBytes)
        assertTrue(jpgBytes.size > 100)

        // Verify valid JPEG header (SOI marker 0xFF 0xD8)
        assertEquals(0xFF.toByte(), jpgBytes[0])
        assertEquals(0xD8.toByte(), jpgBytes[1])

        val bitmap = BitmapFactory.decodeByteArray(jpgBytes, 0, jpgBytes.size)
        assertNotNull("Bitmap should be decodable", bitmap)
        assertEquals(1000, bitmap.width)
        assertEquals(1000, bitmap.height)
    }

    @Test
    fun testZipEpsJpgExporterExportsBothEpsAndJpg() {
        val out = ByteArrayOutputStream()
        val item = ZipEpsJpgExporter.Item(
            baseName = "test_vector",
            svgBytes = sampleSvg,
            title = "Test Title",
            description = "Test Description",
            keywords = listOf("vector", "test"),
            creator = "WarMachine"
        )

        val result = ZipEpsJpgExporter.export(out, listOf(item), useFolders = false)
        assertTrue(result.failed.isEmpty())
        assertEquals(1, result.succeeded.size)

        // Read entries from the output zip
        val zipIn = ZipInputStream(ByteArrayInputStream(out.toByteArray()))
        val entryNames = mutableListOf<String>()
        var entry = zipIn.nextEntry
        while (entry != null) {
            entryNames.add(entry.name)
            zipIn.closeEntry()
            entry = zipIn.nextEntry
        }

        assertTrue("Should contain EPS entry", entryNames.contains("test_vector.eps"))
        assertTrue("Should contain JPG entry", entryNames.contains("test_vector.jpg"))
    }

    @Test
    fun testSvgMetadataInjectionProducesParsableSvgAndExtractableXmp() {
        val injected = XmpInjector.injectIntoSvg(
            originalBytes = sampleSvg,
            title = "Awesome Vector Art",
            description = "Detailed vector description",
            keywords = listOf("art", "illustration", "vector")
        )

        val injectedStr = String(injected, StandardCharsets.UTF_8)
        // Verify no broken root tag with double '>'
        assertTrue("SVG open tag must be well-formed", !injectedStr.contains("> xmlns:"))
        assertTrue("SVG must contain metadata block", injectedStr.contains("<metadata>"))
        assertTrue("SVG must declare xmlns:dc", injectedStr.contains("xmlns:dc="))

        // Verify AndroidSVG can parse the injected SVG without throwing
        val svg = com.caverock.androidsvg.SVG.getFromInputStream(ByteArrayInputStream(injected))
        assertNotNull("AndroidSVG must parse injected SVG", svg)

        // Verify XMP extraction round-trip
        val xmpData = XmpInjector.parseXMP(injected, isPng = false, isSvg = true)
        assertNotNull("XmpData must be parsed", xmpData)
        assertEquals("Awesome Vector Art", xmpData?.title)
        assertEquals("Detailed vector description", xmpData?.description)
        assertTrue(xmpData?.keywords?.contains("illustration") == true)
    }

    @Test
    fun testRepeatedSvgMetadataInjectionReplacesCleanly() {
        val injectedOnce = XmpInjector.injectIntoSvg(
            originalBytes = sampleSvg,
            title = "First Title",
            description = "First Desc",
            keywords = listOf("first")
        )

        val injectedTwice = XmpInjector.injectIntoSvg(
            originalBytes = injectedOnce,
            title = "Second Title",
            description = "Second Desc",
            keywords = listOf("second")
        )

        val str = String(injectedTwice, StandardCharsets.UTF_8)
        assertEquals(1, Regex("<metadata").findAll(str).count())
        assertEquals(1, Regex("<title").findAll(str).count())
        assertEquals(1, Regex("<desc").findAll(str).count())

        val xmpData = XmpInjector.parseXMP(injectedTwice, isPng = false, isSvg = true)
        assertEquals("Second Title", xmpData?.title)
    }
}
