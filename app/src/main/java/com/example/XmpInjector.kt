package com.example

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32

data class XmpData(
    val title: String = "",
    val description: String = "",
    val keywords: String = "",
    val creator: String = ""
)

object XmpInjector {

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private fun cleanSingleLine(input: String): String {
        return input.replace(Regex("""[\r\n]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun cleanXmlAndHtml(input: String): String {
        var clean = input.replace(Regex("<[^>]*>"), "")
        clean = clean.replace(Regex("\\s+"), " ").trim()
        return clean.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }

    fun extractXMP(text: String): XmpData {
        val lis = Regex("<rdf:li[^>]*>(.*?)</rdf:li>", RegexOption.DOT_MATCHES_ALL)
            .findAll(text)
            .map { it.groupValues[1] }
            .map { cleanXmlAndHtml(it) }
            .toList()

        if (lis.size >= 3) {
            val title = lis[0]
            val desc = lis[1]
            val creator = lis[2]
            val keywords = lis.drop(3).joinToString(", ")
            return XmpData(title, desc, keywords, creator)
        }

        if (lis.size == 1 && lis[0].contains(",")) {
            return XmpData(keywords = lis[0])
        }

        val titleFallback = findFallbackTag(text, "dc:title")
        val descFallback = findFallbackTag(text, "dc:description")
        val creatorFallback = findFallbackTag(text, "dc:creator")
        
        val subjectMatch = Regex("<dc:subject[^>]*>(.*?)</dc:subject>", RegexOption.DOT_MATCHES_ALL).find(text)
        var keywordsFallback = ""
        if (subjectMatch != null) {
            val subjectContent = subjectMatch.groupValues[1]
            val subjectLis = Regex("<rdf:li[^>]*>(.*?)</rdf:li>", RegexOption.DOT_MATCHES_ALL)
                .findAll(subjectContent)
                .map { cleanXmlAndHtml(it.groupValues[1]) }
                .toList()
            keywordsFallback = if (subjectLis.isNotEmpty()) {
                subjectLis.joinToString(", ")
            } else {
                cleanXmlAndHtml(subjectContent)
            }
        }

        if (keywordsFallback.isEmpty()) {
            val subjectFallback = findFallbackTag(text, "dc:subject")
            keywordsFallback = subjectFallback
        }

        return XmpData(
            title = titleFallback,
            description = descFallback,
            keywords = keywordsFallback,
            creator = creatorFallback
        )
    }

    private fun findFallbackTag(xml: String, tagName: String): String {
        val regex = Regex("<$tagName[^>]*>(.*?)</$tagName>", RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(xml)
        return if (match != null) {
            cleanXmlAndHtml(match.groupValues[1])
        } else {
            ""
        }
    }

    fun extractXMPFromJpeg(bytes: ByteArray): String? {
        if (bytes.size < 2 || bytes[0] != 0xFF.toByte() || bytes[1] != 0xD8.toByte()) {
            return null
        }
        val signature = "http://ns.adobe.com/xap/1.0/\u0000"
        val sigBytes = signature.toByteArray(StandardCharsets.UTF_8)

        var offset = 2
        while (offset < bytes.size) {
            if (offset + 1 >= bytes.size) break

            val b1 = bytes[offset]
            val b2 = bytes[offset + 1]

            if (b1 != 0xFF.toByte()) {
                offset++
                continue
            }

            val marker = b2.toInt() and 0xFF
            if (marker == 0x00 || marker == 0xFF) {
                offset += 2
                continue
            }

            if (marker == 0xD9 || marker == 0xDA) {
                break
            }

            if (offset + 3 >= bytes.size) break
            val lenHigh = bytes[offset + 2].toInt() and 0xFF
            val lenLow = bytes[offset + 3].toInt() and 0xFF
            val segmentLen = (lenHigh shl 8) or lenLow

            if (marker == 0xE1) {
                if (offset + 4 + sigBytes.size <= bytes.size) {
                    var sigMatch = true
                    for (j in sigBytes.indices) {
                        if (bytes[offset + 4 + j] != sigBytes[j]) {
                            sigMatch = false
                            break
                        }
                    }
                    if (sigMatch) {
                        val xmlStart = offset + 4 + sigBytes.size
                        val xmlLen = segmentLen - 2 - sigBytes.size
                        if (xmlStart + xmlLen <= bytes.size) {
                            return String(bytes, xmlStart, xmlLen, StandardCharsets.UTF_8)
                        }
                    }
                }
            }
            offset += 2 + segmentLen
        }
        return null
    }

    fun extractXMPFromPng(bytes: ByteArray): String? {
        val pngSignature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        if (bytes.size < 8) return null
        for (i in 0..7) {
            if (bytes[i] != pngSignature[i]) return null
        }

        val keyword = "XML:com.adobe.xmp\u0000"
        val kwBytes = keyword.toByteArray(StandardCharsets.UTF_8)

        var offset = 8
        while (offset < bytes.size) {
            if (offset + 8 > bytes.size) break

            val lenBuf = ByteBuffer.wrap(bytes, offset, 4)
            lenBuf.order(ByteOrder.BIG_ENDIAN)
            val chunkLen = lenBuf.int

            val typeBytes = ByteArray(4)
            System.arraycopy(bytes, offset + 4, typeBytes, 0, 4)
            val chunkType = String(typeBytes, StandardCharsets.US_ASCII)

            if (chunkType == "iTXt" && offset + 8 + chunkLen <= bytes.size) {
                val bodyOffset = offset + 8
                var kwMatch = true
                if (chunkLen >= kwBytes.size) {
                    for (i in kwBytes.indices) {
                        if (bytes[bodyOffset + i] != kwBytes[i]) {
                            kwMatch = false
                            break
                        }
                    }
                    if (kwMatch) {
                        var curr = bodyOffset + kwBytes.size
                        if (curr + 2 <= bodyOffset + chunkLen) {
                            curr += 2
                            while (curr < bodyOffset + chunkLen && bytes[curr] != 0.toByte()) {
                                curr++
                            }
                            curr++
                            while (curr < bodyOffset + chunkLen && bytes[curr] != 0.toByte()) {
                                curr++
                            }
                            curr++

                            val xmpLen = (bodyOffset + chunkLen) - curr
                            if (xmpLen > 0 && curr + xmpLen <= bytes.size) {
                                return String(bytes, curr, xmpLen, StandardCharsets.UTF_8)
                            }
                        }
                    }
                }
            }
            offset += 4 + 4 + chunkLen + 4
        }
        return null
    }

    fun extractXMPFromEps(bytes: ByteArray): String? {
        try {
            val str = String(bytes, StandardCharsets.ISO_8859_1)
            val startIdx = str.indexOf("<x:xmpmeta")
            if (startIdx != -1) {
                val endIdx = str.indexOf("</x:xmpmeta>", startIdx)
                if (endIdx != -1) {
                    val xmlIso = str.substring(startIdx, endIdx + "</x:xmpmeta>".length)
                    return String(xmlIso.toByteArray(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun extractXMPFromSvg(bytes: ByteArray): String? {
        try {
            val str = String(bytes, StandardCharsets.UTF_8)
            val startIdx = str.indexOf("<x:xmpmeta")
            if (startIdx != -1) {
                val endIdx = str.indexOf("</x:xmpmeta>", startIdx)
                if (endIdx != -1) {
                    return str.substring(startIdx, endIdx + "</x:xmpmeta>".length)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun parseXMP(originalBytes: ByteArray, isPng: Boolean, isEps: Boolean = false, isSvg: Boolean = false): XmpData? {
        try {
            val xmlStr = (if (isEps) {
                extractXMPFromEps(originalBytes)
            } else if (isSvg) {
                extractXMPFromSvg(originalBytes)
            } else if (isPng) {
                extractXMPFromPng(originalBytes)
            } else {
                extractXMPFromJpeg(originalBytes)
            }) ?: return null

            return extractXMP(xmlStr)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Generates raw XMP Packet string matching version 2.0.0 reference implementation (`Xn` in JS).
     */
    fun generateXmpPacket(title: String, description: String, keywords: List<String>): String {
        val t = title.trim()
        val d = description.trim()
        val kwList = keywords.map { it.trim() }.filter { it.isNotEmpty() }

        val kwLines = if (kwList.isNotEmpty()) {
            kwList.joinToString("\n") { kw -> "    <rdf:li>${escapeXml(kw)}</rdf:li>" }
        } else ""

        val lines = mutableListOf<String>()
        lines.add("<?xpacket begin=\"\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>")
        lines.add("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">")
        lines.add("<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">")
        lines.add("<rdf:Description rdf:about=\"\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:photoshop=\"http://ns.adobe.com/photoshop/1.0/\" xmlns:xmp=\"http://ns.adobe.com/xap/1.0/\">")
        lines.add("<xmp:CreatorTool>Adobe Illustrator 10.0</xmp:CreatorTool>")

        if (t.isNotEmpty()) {
            lines.add("<dc:title><rdf:Alt><rdf:li xml:lang=\"x-default\">${escapeXml(t)}</rdf:li></rdf:Alt></dc:title>")
        }
        if (d.isNotEmpty()) {
            lines.add("<dc:description><rdf:Alt><rdf:li xml:lang=\"x-default\">${escapeXml(d)}</rdf:li></rdf:Alt></dc:description>")
        }
        if (kwLines.isNotEmpty()) {
            lines.add("<dc:subject><rdf:Bag>\n$kwLines\n</rdf:Bag></dc:subject>")
        }
        if (t.isNotEmpty()) {
            lines.add("<photoshop:Headline>${escapeXml(t)}</photoshop:Headline>")
        }

        lines.add("</rdf:Description>")
        lines.add("</rdf:RDF>")
        lines.add("</x:xmpmeta>")
        lines.add("<?xpacket end=\"w\"?>")

        return lines.joinToString("\n")
    }

    /**
     * Injects EPS metadata into pure Adobe Illustrator 10 (EPS 10) compatible PostScript structure.
     */
    fun injectIntoEps(
        originalBytes: ByteArray,
        title: String,
        description: String,
        keywords: List<String>,
        creator: String = ""
    ): ByteArray {
        try {
            val metaTitle = title.trim()
            val metaDesc = description.trim()
            val cleanKeywords = keywords.map { it.trim() }.filter { it.isNotEmpty() }

            if (metaTitle.isEmpty() && metaDesc.isEmpty() && cleanKeywords.isEmpty()) {
                return originalBytes
            }

            // Check if DOS EPS binary header is present (Magic: 0xC5D0D3C6)
            val isDosEps = originalBytes.size >= 30 &&
                    (originalBytes[0].toInt() and 0xFF) == 0xC5 &&
                    (originalBytes[1].toInt() and 0xFF) == 0xD0 &&
                    (originalBytes[2].toInt() and 0xFF) == 0xD3 &&
                    (originalBytes[3].toInt() and 0xFF) == 0xC6

            if (isDosEps) {
                val psOffset = getUInt32LE(originalBytes, 4)
                val psLength = getUInt32LE(originalBytes, 8)
                var wmfOffset = getUInt32LE(originalBytes, 12)
                val wmfLength = getUInt32LE(originalBytes, 16)
                var tiffOffset = getUInt32LE(originalBytes, 20)
                val tiffLength = getUInt32LE(originalBytes, 24)

                if (psOffset in 30..originalBytes.size && psLength > 0 && psOffset + psLength <= originalBytes.size) {
                    val rawPsBytes = originalBytes.copyOfRange(psOffset, psOffset + psLength)
                    val injectedPsBytes = injectIntoPostScriptBytes(rawPsBytes, metaTitle, metaDesc, cleanKeywords)
                    val diff = injectedPsBytes.size - rawPsBytes.size

                    val newHeader = originalBytes.copyOfRange(0, 30)
                    setUInt32LE(newHeader, 4, psOffset)
                    setUInt32LE(newHeader, 8, injectedPsBytes.size)

                    if (wmfOffset >= psOffset + psLength) wmfOffset += diff
                    setUInt32LE(newHeader, 12, wmfOffset)
                    setUInt32LE(newHeader, 16, wmfLength)

                    if (tiffOffset >= psOffset + psLength) tiffOffset += diff
                    setUInt32LE(newHeader, 20, tiffOffset)
                    setUInt32LE(newHeader, 24, tiffLength)
                    setUInt16LE(newHeader, 28, 0xFFFF)

                    val outputStream = ByteArrayOutputStream(originalBytes.size + diff + 1024)
                    outputStream.write(newHeader)
                    if (psOffset > 30) {
                        outputStream.write(originalBytes, 30, psOffset - 30)
                    }
                    outputStream.write(injectedPsBytes)
                    val trailingStart = psOffset + psLength
                    if (trailingStart < originalBytes.size) {
                        outputStream.write(originalBytes, trailingStart, originalBytes.size - trailingStart)
                    }
                    return outputStream.toByteArray()
                }
            }

            // Pure PostScript EPS
            return injectIntoPostScriptBytes(originalBytes, metaTitle, metaDesc, cleanKeywords)
        } catch (e: Exception) {
            e.printStackTrace()
            return originalBytes
        }
    }

    /**
     * Injects EPS metadata matching official Adobe Illustrator 10.0 (EPS 10) specification.
     * Removes non-standard/newer AI11 markers that cause rejections on microstock sites like Vecteezy.
     */
    private fun injectIntoPostScriptBytes(
        psBytes: ByteArray,
        metaTitle: String,
        metaDesc: String,
        cleanKeywords: List<String>
    ): ByteArray {
        val t = metaTitle.trim()
        val d = metaDesc.trim()
        val kwList = cleanKeywords.map { it.trim() }.filter { it.isNotEmpty() }

        var psStr = String(psBytes, StandardCharsets.ISO_8859_1)

        // 1. Hapus blok injeksi lama (kalau re-inject)
        psStr = psStr.replace(
            Regex("""%ADO_ContainsXMP:[\s\S]*?%EndXMPPacket\r?\n?"""), "")

        // 2. Pastikan header AI 10 konsisten
        psStr = psStr.replace(
            Regex("""%%Creator:\s*Adobe Illustrator\(R\)\s*[\d\.]+"""),
            "%%Creator: Adobe Illustrator(R) 10.0")
        psStr = psStr.replace(
            Regex("""%%AI8_CreatorVersion:\s*[\d\.]+"""),
            "%%AI8_CreatorVersion: 10.0")

        // 3. Update/insert %%Title & %%Keywords DSC (di header, sebelum %%EndComments)
        if (t.isNotEmpty()) {
            val titleLine = "%%Title: ${cleanSingleLine(t)}"
            if (psStr.contains(Regex("""%%Title:.*"""))) {
                psStr = psStr.replace(Regex("""%%Title:[^\r\n]*"""), titleLine)
            } else {
                psStr = psStr.replace("%%EndComments", "$titleLine\n%%EndComments")
            }
        }
        if (kwList.isNotEmpty()) {
            val kwLine = "%%Keywords: ${cleanSingleLine(kwList.joinToString(", "))}"
            if (psStr.contains(Regex("""%%Keywords:.*"""))) {
                psStr = psStr.replace(Regex("""%%Keywords:[^\r\n]*"""), kwLine)
            } else {
                psStr = psStr.replace("%%EndComments", "$kwLine\n%%EndComments")
            }
        }

        // 4. Generate XMP
        val xmpXml = generateXmpPacket(t, d, kwList)
        val xmpBytes = xmpXml.toByteArray(StandardCharsets.UTF_8)

        // 5. Build output: PS body (ISO-8859-1) + XMP block (UTF-8) di dalam %%Trailer
        val eofRegex = Regex("""%%EOF[^\n]*\n?""")
        val eofMatch = eofRegex.find(psStr)

        val out = ByteArrayOutputStream(psBytes.size + xmpBytes.size + 256)

        if (eofMatch != null) {
            val beforeEof = psStr.substring(0, eofMatch.range.first)
            val afterEof = psStr.substring(eofMatch.range.last + 1)

            out.write(beforeEof.toByteArray(StandardCharsets.ISO_8859_1))

            // Pastikan ada %%Trailer sebelum XMP
            if (!beforeEof.trimEnd().endsWith("%%Trailer")) {
                out.write("%%Trailer\n".toByteArray(StandardCharsets.ISO_8859_1))
            }

            // XMP header (ASCII)
            out.write("%ADO_ContainsXMP: MainFirst\n".toByteArray(StandardCharsets.ISO_8859_1))
            out.write("%BeginXMPPacket: ${xmpBytes.size}\n".toByteArray(StandardCharsets.ISO_8859_1))

            // XMP content (UTF-8, byte-count cocok)
            out.write(xmpBytes)

            // XMP trailer (ASCII)
            out.write("\n%EndXMPPacket\n".toByteArray(StandardCharsets.ISO_8859_1))

            if (afterEof.isNotEmpty()) {
                out.write(afterEof.toByteArray(StandardCharsets.ISO_8859_1))
            }
        } else {
            // Fallback: tidak ada %%EOF
            out.write(psStr.toByteArray(StandardCharsets.ISO_8859_1))
            out.write("\n%%Trailer\n".toByteArray(StandardCharsets.ISO_8859_1))
            out.write("%ADO_ContainsXMP: MainFirst\n".toByteArray(StandardCharsets.ISO_8859_1))
            out.write("%BeginXMPPacket: ${xmpBytes.size}\n".toByteArray(StandardCharsets.ISO_8859_1))
            out.write(xmpBytes)
            out.write("\n%EndXMPPacket\n%%EOF\n".toByteArray(StandardCharsets.ISO_8859_1))
        }

        return out.toByteArray()
    }

    private fun getUInt32LE(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun setUInt32LE(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((value ushr 16) and 0xFF).toByte()
        bytes[offset + 3] = ((value ushr 24) and 0xFF).toByte()
    }

    private fun setUInt16LE(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    }

    /**
     * Injects SVG metadata matching version 2.0.0 reference implementation (`he` / `$e` in JS).
     */
    fun injectIntoSvg(
        originalBytes: ByteArray,
        title: String,
        description: String,
        keywords: List<String>
    ): ByteArray {
        try {
            val t = title.trim()
            val d = description.trim()
            val kwList = keywords.map { it.trim() }.filter { it.isNotEmpty() }

            if (t.isEmpty() && d.isEmpty() && kwList.isEmpty()) {
                return originalBytes
            }

            val kwStr = kwList.joinToString(", ")
            val titleTag = if (t.isNotEmpty()) "<title>${escapeXml(t)}</title>" else ""
            val descTag = if (d.isNotEmpty()) "<desc>${escapeXml(d)}</desc>" else ""

            val dcTitle = if (t.isNotEmpty()) "<dc:title>${escapeXml(t)}</dc:title>" else ""
            val dcDesc = if (d.isNotEmpty()) "<dc:description>${escapeXml(d)}</dc:description>" else ""
            val dcSubj = if (kwStr.isNotEmpty()) "<dc:subject>${escapeXml(kwStr)}</dc:subject>" else ""

            val metadataContent = listOf(dcTitle, dcDesc, dcSubj).filter { it.isNotEmpty() }.joinToString("")
            val metadataTag = if (metadataContent.isNotEmpty()) "<metadata>$metadataContent</metadata>" else ""

            val svgMetaBlock = listOf(titleTag, descTag, metadataTag).filter { it.isNotEmpty() }.joinToString("\n")
            if (svgMetaBlock.isEmpty()) return originalBytes

            val fileStr = String(originalBytes, StandardCharsets.UTF_8)
            val svgOpenTagRegex = Regex("""(<svg\b[^>]*>)""")
            val hasilSvg = if (fileStr.contains(svgOpenTagRegex)) {
                fileStr.replace(svgOpenTagRegex, "$1 xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n$svgMetaBlock")
            } else {
                svgMetaBlock + "\n" + fileStr
            }

            return hasilSvg.toByteArray(StandardCharsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            return originalBytes
        }
    }

    /**
     * Injects JPEG APP1 XMP metadata matching version 2.0.0 reference implementation (`ve` in JS).
     */
    fun injectIntoJpeg(
        originalBytes: ByteArray,
        title: String,
        description: String,
        keywords: List<String>,
        creator: String = ""
    ): ByteArray {
        val t = title.trim()
        val d = description.trim()
        val kwList = keywords.map { it.trim() }.filter { it.isNotEmpty() }

        if (t.isEmpty() && d.isEmpty() && kwList.isEmpty()) {
            return originalBytes
        }

        if (originalBytes.size < 2 || originalBytes[0] != 0xFF.toByte() || originalBytes[1] != 0xD8.toByte()) {
            return originalBytes
        }

        val xmpXml = generateXmpPacket(t, d, kwList)
        val xmpHeader = "http://ns.adobe.com/xap/1.0/\u0000"
        val payload = (xmpHeader + xmpXml).toByteArray(StandardCharsets.UTF_8)

        val seg = ByteArray(4 + payload.size)
        seg[0] = 0xFF.toByte()
        seg[1] = 0xE1.toByte()
        val len = payload.size + 2
        seg[2] = ((len ushr 8) and 0xFF).toByte()
        seg[3] = (len and 0xFF).toByte()

        System.arraycopy(payload, 0, seg, 4, payload.size)

        val out = ByteArray(originalBytes.size + seg.size)
        System.arraycopy(originalBytes, 0, out, 0, 2)
        System.arraycopy(seg, 0, out, 2, seg.size)
        System.arraycopy(originalBytes, 2, out, 2 + seg.size, originalBytes.size - 2)
        return out
    }

    /**
     * Injects PNG tEXt chunks matching version 2.0.0 reference implementation (`Se` / `an` in JS).
     */
    fun injectIntoPng(
        originalBytes: ByteArray,
        title: String,
        description: String,
        keywords: List<String>,
        creator: String = ""
    ): ByteArray {
        val t = title.trim()
        val d = description.trim()
        val kwList = keywords.map { it.trim() }.filter { it.isNotEmpty() }

        if (t.isEmpty() && d.isEmpty() && kwList.isEmpty()) {
            return originalBytes
        }

        val pngSignature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        if (originalBytes.size < 8) return originalBytes
        for (i in 0..7) {
            if (originalBytes[i] != pngSignature[i]) return originalBytes
        }

        var iendOffset = -1
        var offset = 8
        while (offset + 8 <= originalBytes.size) {
            val lenBuf = ByteBuffer.wrap(originalBytes, offset, 4)
            lenBuf.order(ByteOrder.BIG_ENDIAN)
            val chunkLen = lenBuf.int

            val typeBytes = ByteArray(4)
            System.arraycopy(originalBytes, offset + 4, typeBytes, 0, 4)
            val chunkType = String(typeBytes, StandardCharsets.US_ASCII)

            if (chunkType == "IEND") {
                iendOffset = offset
                break
            }
            offset += 12 + chunkLen
        }

        if (iendOffset < 0) return originalBytes

        val textChunks = mutableListOf<ByteArray>()
        if (t.isNotEmpty()) textChunks.add(createPngTextChunk("Title", t))
        if (d.isNotEmpty()) textChunks.add(createPngTextChunk("Description", d))
        if (kwList.isNotEmpty()) textChunks.add(createPngTextChunk("Keywords", kwList.joinToString(", ")))

        val totalExtraSize = textChunks.sumOf { it.size }
        val out = ByteArray(originalBytes.size + totalExtraSize)

        System.arraycopy(originalBytes, 0, out, 0, iendOffset)
        var writePos = iendOffset
        for (chunk in textChunks) {
            System.arraycopy(chunk, 0, out, writePos, chunk.size)
            writePos += chunk.size
        }
        System.arraycopy(originalBytes, iendOffset, out, writePos, originalBytes.size - iendOffset)

        return out
    }

    private fun createPngTextChunk(keyword: String, text: String): ByteArray {
        val keywordBytes = keyword.toByteArray(StandardCharsets.UTF_8)
        val textBytes = text.toByteArray(StandardCharsets.UTF_8)
        val chunkData = ByteArray(keywordBytes.size + 1 + textBytes.size)
        System.arraycopy(keywordBytes, 0, chunkData, 0, keywordBytes.size)
        chunkData[keywordBytes.size] = 0.toByte()
        System.arraycopy(textBytes, 0, chunkData, keywordBytes.size + 1, textBytes.size)

        val typeBytes = "tEXt".toByteArray(StandardCharsets.US_ASCII)

        val crc = CRC32()
        crc.update(typeBytes)
        crc.update(chunkData)

        val buffer = ByteBuffer.allocate(4 + typeBytes.size + chunkData.size + 4)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(chunkData.size)
        buffer.put(typeBytes)
        buffer.put(chunkData)
        buffer.putInt(crc.value.toInt())

        return buffer.array()
    }
}
