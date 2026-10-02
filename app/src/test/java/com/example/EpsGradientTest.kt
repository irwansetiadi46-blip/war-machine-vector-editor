package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class EpsGradientTest {

    @Test
    fun testSvgWithLinearGradientConvertsToEpsWithLevel3Shading() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 500 500" width="500" height="500">
                <defs>
                    <linearGradient id="rainbow" x1="0%" y1="0%" x2="100%" y2="100%">
                        <stop offset="0%" stop-color="#ff0000" />
                        <stop offset="50%" stop-color="#00ff00" />
                        <stop offset="100%" stop-color="#0000ff" />
                    </linearGradient>
                </defs>
                <rect x="50" y="50" width="400" height="400" fill="url(#rainbow)" />
            </svg>
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)

        val epsBytes = SvgToEpsConverter.convertSvgToEps(
            svgBytes = svg,
            title = "Linear Gradient Vector",
            description = "A vector with linear gradient",
            keywords = listOf("gradient", "vector", "art"),
            creator = "WarMachine"
        )

        val epsText = String(epsBytes, StandardCharsets.UTF_8)

        // Verify LanguageLevel 3
        assertTrue("EPS should declare LanguageLevel 3", epsText.contains("%%LanguageLevel: 3"))

        // Verify Level 3 Shading dictionary
        assertTrue("EPS should contain ShadingType 2", epsText.contains("/ShadingType 2"))
        assertTrue("EPS should contain shfill operator", epsText.contains("shfill"))

        // Verify XMP injection
        assertTrue("EPS should contain injected title", epsText.contains("<dc:title>"))
        assertTrue("EPS should contain injected keywords", epsText.contains("<rdf:li>gradient</rdf:li>"))
    }

    @Test
    fun testSvgWithRadialGradientConvertsToEpsWithRadialShading() {
        val svg = """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 500 500" width="500" height="500">
                <defs>
                    <radialGradient id="sun" cx="50%" cy="50%" r="50%">
                        <stop offset="0%" stop-color="#ffff00" />
                        <stop offset="100%" stop-color="#ff4500" />
                    </radialGradient>
                </defs>
                <circle cx="250" cy="250" r="200" fill="url(#sun)" />
            </svg>
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)

        val epsBytes = SvgToEpsConverter.convertSvgToEps(
            svgBytes = svg,
            title = "Radial Gradient Vector",
            description = "A vector with radial sun gradient",
            keywords = listOf("sun", "yellow", "radial")
        )

        val epsText = String(epsBytes, StandardCharsets.UTF_8)

        // Verify ShadingType 3 for radial
        assertTrue("EPS should contain ShadingType 3 for radial", epsText.contains("/ShadingType 3"))
        assertTrue("EPS should contain shfill operator", epsText.contains("shfill"))
    }

    @Test
    fun testXmpInjectorPreservesEpsGradientsAndBinaryData() {
        // Create simulated EPS with gradient shading and binary comments
        val rawPostScript = """%!PS-Adobe-3.0 EPSF-3.0
%%Creator: Adobe Illustrator
%%BoundingBox: 0 0 500 500
%%EndComments

gsave
<<
  /ShadingType 2
  /ColorSpace /DeviceRGB
  /Coords [0 0 500 500]
  /Function << /FunctionType 2 /Domain [0 1] /C0 [1 0 0] /C1 [0 0 1] /N 1 >>
  /Extend [true true]
>> shfill
grestore

showpage
%%EOF
"""
        val rawBytes = rawPostScript.toByteArray(StandardCharsets.ISO_8859_1)

        val injected = XmpInjector.injectIntoEps(
            originalBytes = rawBytes,
            title = "Test Gradient Preservation",
            description = "Description of gradient",
            keywords = listOf("preservation", "gradient", "stock")
        )

        val injectedText = String(injected, StandardCharsets.ISO_8859_1)

        // Verify gradient is 100% intact
        assertTrue("Gradient dictionary must be preserved", injectedText.contains("/ShadingType 2"))
        assertTrue("Coords must be preserved", injectedText.contains("/Coords [0 0 500 500]"))
        assertTrue("C0 must be preserved", injectedText.contains("/C0 [1 0 0]"))
        assertTrue("C1 must be preserved", injectedText.contains("/C1 [0 0 1]"))
        assertTrue("shfill must be preserved", injectedText.contains("shfill"))

        // Verify XMP is cleanly extractable
        val xmpXml = XmpInjector.extractXMPFromEps(injected)
        assertNotNull("XMP must be extractable", xmpXml)
        assertTrue("Title must be in XMP", xmpXml!!.contains("Test Gradient Preservation"))
    }

    @Test
    fun testDosEpsBinaryHeaderPreserved() {
        // Construct simulated DOS EPS header: 30 bytes
        val header = ByteArray(30)
        // Magic C5 D0 D3 C6
        header[0] = 0xC5.toByte()
        header[1] = 0xD0.toByte()
        header[2] = 0xD3.toByte()
        header[3] = 0xC6.toByte()

        val psContent = """%!PS-Adobe-3.0 EPSF-3.0
%%BoundingBox: 0 0 100 100
%%EndComments
newpath 0 0 moveto 100 100 lineto stroke
showpage
%%EOF
""".toByteArray(StandardCharsets.ISO_8859_1)

        val psOffset = 30
        val psLength = psContent.size
        // PS offset (4..7)
        header[4] = (psOffset and 0xFF).toByte()
        header[5] = ((psOffset ushr 8) and 0xFF).toByte()
        header[6] = 0
        header[7] = 0
        // PS length (8..11)
        header[8] = (psLength and 0xFF).toByte()
        header[9] = ((psLength ushr 8) and 0xFF).toByte()
        header[10] = 0
        header[11] = 0

        // Simulated TIFF preview appended after PostScript
        val dummyTiff = byteArrayOf(0x49, 0x49, 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00)
        val tiffOffset = psOffset + psLength
        val tiffLength = dummyTiff.size
        header[20] = (tiffOffset and 0xFF).toByte()
        header[21] = ((tiffOffset ushr 8) and 0xFF).toByte()
        header[22] = 0
        header[23] = 0
        header[24] = (tiffLength and 0xFF).toByte()
        header[25] = 0
        header[26] = 0
        header[27] = 0

        val fullDosEps = header + psContent + dummyTiff

        val injected = XmpInjector.injectIntoEps(
            originalBytes = fullDosEps,
            title = "DOS EPS Title",
            description = "DOS EPS Desc",
            keywords = listOf("dos", "eps")
        )

        // Verify magic number is preserved
        assertEquals(0xC5.toByte(), injected[0])
        assertEquals(0xD0.toByte(), injected[1])
        assertEquals(0xD3.toByte(), injected[2])
        assertEquals(0xC6.toByte(), injected[3])

        // Verify updated PostScript length
        val newPsLen = (injected[8].toInt() and 0xFF) or ((injected[9].toInt() and 0xFF) shl 8)
        assertTrue("New PS length must be greater than original", newPsLen > psLength)

        // Verify updated TIFF offset
        val newTiffOffset = (injected[20].toInt() and 0xFF) or ((injected[21].toInt() and 0xFF) shl 8)
        assertEquals(psOffset + newPsLen, newTiffOffset)

        // Verify TIFF bytes are untouched
        for (i in dummyTiff.indices) {
            assertEquals("TIFF byte $i must match", dummyTiff[i], injected[newTiffOffset + i])
        }
    }
}
