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

    // --- Helper Formatting & Cleaning ---

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

    // --- Unified Main Dispatcher ---

    /**
     * Helper universal untuk menyuntikkan metadata ke berbagai format berdasarkan format extension/type.
     */
    fun injectMetadata(
        originalBytes: ByteArray,
        format: String,
        title: String,
        description: String,
        keywords: List<String>,
        creator: String = ""
    ): ByteArray {
        val ext = format.lowercase().trim().removePrefix(".")
        return when (ext) {
            "jpg", "jpeg" -> injectIntoJpeg(originalBytes, title, description, keywords, creator)
            "png" -> injectIntoPng(originalBytes, title, description, keywords, creator)
            "svg" -> injectIntoSvg(originalBytes, title, description, keywords)
            "eps" -> injectIntoEps(originalBytes, title, description, keywords, creator)
            else -> originalBytes
        }
    }

    // --- XMP Extraction & Parsing ---

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

    // --- Packet Generators & Injectors ---

    /**
     * Generates raw XMP Packet string matching Lineva implementation (Xn).
     */
    fun generateXnXml(title: String, description: String, keywords: List<String>): String {
        val t = title.trim()
        val d = description.trim()
        val cleanKw = keywords.map { it.trim() }.filter { it.isNotEmpty() }

        val kwLines = if (cleanKw.isNotEmpty()) {
            cleanKw.joinToString("\n") { kw -> "    <rdf:li>${escapeXml(kw)}</rdf:li>" }
        } else ""

        val parts = mutableListOf<String>()
        parts.add("<?xpacket begin=\"\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>")
        parts.add("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">")
        parts.add("<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">")
        parts.add("<rdf:Description rdf:about=\"\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:photoshop=\"http://ns.adobe.com/photoshop/1.0/\">")

        if (t.isNotEmpty()) {
            parts.add("<dc:title><rdf:Alt><rdf:li xml:lang=\"x-default\">${escapeXml(t)}</rdf:li></rdf:Alt></dc:title>")
        }
        if (d.isNotEmpty()) {
            parts.add("<dc:description><rdf:Alt><rdf:li xml:lang=\"x-default\">${escapeXml(d)}</rdf:li></rdf:Alt></dc:description>")
        }
        if (kwLines.isNotEmpty()) {
            parts.add("<dc:subject><rdf:Bag>\n$kwLines\n</rdf:Bag></dc:subject>")
        }
        if (t.isNotEmpty()) {
            parts.add("<photoshop:Headline>${escapeXml(t)}</photoshop:Headline>")
        }

        parts.add("</rdf:Description>")
        parts.add("</rdf:RDF>")
        parts.add("</x:xmpmeta>")
        parts.add("<?xpacket end=\"w\"?>")

        return parts.joinToString("\n")
    }

    fun generateXmpPacket(title: String, description: String, keywords: List<String>): String =
        generateXnXml(title, description, keywords)

    /**
     * Generates PageSetup ClientInjection Block matching Lineva implementation (me).
     */
    fun generateMeBlock(title: String, description: String, keywords: List<String>): String {
        val xnXml = generateXnXml(title, description, keywords)
        val marker = "%  &&end XMP packet marker&&"
        return listOf(
            "%ADOBeginClientInjection: PageSetup End \"AI11EPS\"",
            "/currentdistillerparams where",
            "{pop currentdistillerparams /CoreDistVersion get 5000 lt} {true} ifelse",
            "{ userdict /AI11_PDFMark5 /cleartomark load put",
            "userdict /AI11_ReadMetadata_PDFMark5 {flushfile cleartomark } bind put}",
            "{ userdict /AI11_PDFMark5 /pdfmark load put",
            "userdict /AI11_ReadMetadata_PDFMark5 {/PUT pdfmark} bind put } ifelse",
            "[/NamespacePush AI11_PDFMark5",
            "[/_objdef {vector_design_metadata_stream} /type /stream /OBJ AI11_PDFMark5",
            "[{vector_design_metadata_stream}",
            "currentfile 0 ($marker)",
            "/SubFileDecode filter AI11_ReadMetadata_PDFMark5",
            xnXml,
            marker,
            "[{vector_design_metadata_stream}",
            "<</Type /Metadata /Subtype /XML>>",
            "/PUT AI11_PDFMark5",
            "[/Document",
            "1 dict begin /Metadata {vector_design_metadata_stream} def",
            "currentdict end /BDC AI11_PDFMark5",
            "%ADOEndClientInjection: PageSetup End \"AI11EPS\"",
            ""
        ).joinToString("\n")
    }

    /**
     * PageTrailer ClientInjection Block matching Lineva implementation (xe).
     */
    val XE_BLOCK = listOf(
        "%ADOBeginClientInjection: PageTrailer Start \"AI11EPS\"",
        "[/EMC AI11_PDFMark5",
        "[/NamespacePop AI11_PDFMark5",
        "%ADOEndClientInjection: PageTrailer Start \"AI11EPS\"",
        ""
    ).joinToString("\n")

    /**
     * Embeds metadata into EPS PostScript text matching Lineva implementation (we).
     */
    fun embedEpsMetadata(
        epsContent: String,
        title: String,
        description: String,
        keywords: List<String>
    ): String {
        val t = title.trim()
        val d = description.trim()
        val kws = keywords.map { it.trim() }.filter { it.isNotEmpty() }

        if (t.isEmpty() && d.isEmpty() && kws.isEmpty()) {
            return epsContent
        }

        val headerLines = mutableListOf<String>()
        headerLines.add("%ADO_ContainsXMP: MainFirst")
        if (t.isNotEmpty()) {
            headerLines.add("%%Title: ${cleanSingleLine(t)}")
        }
        if (kws.isNotEmpty()) {
            headerLines.add("%%Keywords: ${cleanSingleLine(kws.joinToString(", "))}")
        }
        val xmpHeader = headerLines.joinToString("\n")

        val meBlock = generateMeBlock(t, d, kws)

        var s = epsContent
        if (xmpHeader.isNotEmpty()) {
            if (s.contains("\n%%EndComments")) {
                s = s.replace("\n%%EndComments", "\n$xmpHeader\n%%EndComments")
            } else if (s.contains("%%EndComments")) {
                s = s.replace("%%EndComments", "$xmpHeader\n%%EndComments")
            }
        }

        val endCommentsRegex = Regex("""(%%EndComments\s*)""")
        if (s.contains(endCommentsRegex)) {
            s = s.replace(endCommentsRegex, "$1\n$meBlock")
        } else {
            s = "$meBlock\n$s"
        }

        val showpageEofRegex = Regex("""\nshowpage\n%%EOF""")
        if (s.contains(showpageEofRegex)) {
            s = s.replace(showpageEofRegex, "\n${XE_BLOCK}showpage\n%%EOF")
        } else if (s.contains("showpage")) {
            s = s.replace("showpage", "${XE_BLOCK}showpage")
        }

        return s
    }

    /**
     * Injects EPS metadata using exact Lineva PostScript embedding method.
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

            val epsString = String(originalBytes, StandardCharsets.UTF_8)
            val resultEps = embedEpsMetadata(epsString, metaTitle, metaDesc, cleanKeywords)
            return resultEps.toByteArray(StandardCharsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            return originalBytes
        }
    }

    /**
     * Injects SVG metadata matching Lineva implementation (`$e` / `he`).
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
     * Injects JPEG APP1 XMP metadata matching Lineva implementation (`ve`).
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
     * Injects PNG tEXt chunks matching Lineva implementation (`Se` / `an`).
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
