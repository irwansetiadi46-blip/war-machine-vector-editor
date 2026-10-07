package com.example

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
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

        // Verify LanguageLevel
        assertTrue("EPS should declare LanguageLevel", epsText.contains("%%LanguageLevel: 2") || epsText.contains("%%LanguageLevel: 3"))

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

    @Test
    fun testEpsRendererRendersLevel3ShadingGradientWithTrueColor() {
        val psContent = """%!PS-Adobe-3.0 EPSF-3.0
%%BoundingBox: 0 0 200 200
%%EndComments
gsave
newpath
10 10 moveto
190 10 lineto
190 190 lineto
10 190 lineto
closepath
<<
  /ShadingType 2
  /ColorSpace /DeviceRGB
  /Coords [10 10 190 190]
  /Function <<
    /FunctionType 2
    /Domain [0.0 1.0]
    /C0 [1.0 0.0 0.0]
    /C1 [0.0 0.0 1.0]
    /N 1.0
  >>
  /Extend [true true]
>> shfill
grestore
showpage
%%EOF
""".toByteArray(StandardCharsets.ISO_8859_1)

        val bmp = EpsRenderer.renderEpsToBitmap(psContent, 200)
        assertNotNull("Bitmap should be rendered", bmp)
        assertTrue("Bitmap width > 0", bmp!!.width > 0)
        assertTrue("Bitmap height > 0", bmp.height > 0)

        // Verify that rendered pixels contain non-grayscale colors (red and blue components)
        var foundRed = false
        var foundBlue = false
        for (y in 0 until bmp.height step 10) {
            for (x in 0 until bmp.width step 10) {
                val pixel = bmp.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                // Color should not be pure gray / black / white
                if (r > 150 && b < 100 && g < 100) foundRed = true
                if (b > 150 && r < 100 && g < 100) foundBlue = true
            }
        }
        assertTrue("Rendered Level 3 shading should contain red colors", foundRed)
        assertTrue("Rendered Level 3 shading should contain blue colors", foundBlue)
    }

    @Test
    fun testEpsRendererRendersAi5BeginGradientMultipleShapesWithVariedGradients() {
        val aiEpsContent = """%!PS-Adobe-3.0 EPSF-3.0
%%Creator: Adobe Illustrator
%%BoundingBox: 0 0 300 300
%AI5_BeginGradient: (OrangeToYellow)
1 2
[
0 50 1 [ 0 0.8 1 0 ]
100 50 1 [ 0 0.1 1 0 ]
]
%AI5_EndGradient
%AI5_BeginGradient: (CyanToMagenta)
1 2
[
0 50 1 [ 1 0 0 0 ]
100 50 1 [ 0 1 0 0 ]
]
%AI5_EndGradient
%%EndComments
gsave
% Shape 1 with OrangeToYellow
newpath
10 10 m 140 10 l 140 140 l 10 140 l h
[ (OrangeToYellow) ] _Xg
% Shape 2 with CyanToMagenta
newpath
160 160 m 290 160 l 290 290 l 160 290 l h
[ (CyanToMagenta) ] _Xg
grestore
showpage
%%EOF
""".toByteArray(StandardCharsets.ISO_8859_1)

        val bmp = EpsRenderer.renderEpsToBitmap(aiEpsContent, 300)
        assertNotNull("Bitmap should be rendered from AI gradients", bmp)

        // Verify shape 1 has orange/yellow pixels and shape 2 has cyan/magenta pixels
        var foundWarmColor = false
        var foundCoolColor = false
        for (y in 0 until bmp!!.height step 5) {
            for (x in 0 until bmp.width step 5) {
                val pixel = bmp.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // Warm color: high red and green, low blue
                if (r > 180 && g > 50 && b < 80) foundWarmColor = true
                // Cool color: cyan/magenta
                if ((b > 150 && g > 100) || (r > 150 && b > 150)) foundCoolColor = true
            }
        }
        assertTrue("Rendered AI EPS should contain warm gradient colors (not flat black/white)", foundWarmColor)
        assertTrue("Rendered AI EPS should contain cool gradient colors (not flat black/white)", foundCoolColor)
    }
}
