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
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
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

    fun generateXmpMeta(title: String, description: String, keywords: List<String>, creator: String): String {
        val rawTitle = title.trim()
        val rawDesc = description.trim()
        val effectiveTitle = if (rawTitle.isNotEmpty()) rawTitle else rawDesc
        val effectiveDesc = if (rawDesc.isNotEmpty()) rawDesc else rawTitle

        val titleEsc = escapeXml(effectiveTitle)
        val descEsc = escapeXml(effectiveDesc)
        val creatorEsc = escapeXml(creator.trim())
        val keywordsHtml = keywords.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("") { kw ->
            "<rdf:li>${escapeXml(kw)}</rdf:li>"
        }

        return """
            |<x:xmpmeta xmlns:x="adobe:ns:meta/">
            |  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
            |    <rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:photoshop="http://ns.adobe.com/photoshop/1.0/">
            |      <dc:title><rdf:Alt><rdf:li xml:lang="x-default">$titleEsc</rdf:li></rdf:Alt></dc:title>
            |      <dc:description><rdf:Alt><rdf:li xml:lang="x-default">$descEsc</rdf:li></rdf:Alt></dc:description>
            |      <dc:creator><rdf:Seq><rdf:li>$creatorEsc</rdf:li></rdf:Seq></dc:creator>
            |      <dc:subject><rdf:Bag>$keywordsHtml</rdf:Bag></dc:subject>
            |      <photoshop:Headline>$titleEsc</photoshop:Headline>
            |      <photoshop:Caption>$descEsc</photoshop:Caption>
            |    </rdf:Description>
            |  </rdf:RDF>
            |</x:xmpmeta>
        """.trimMargin()
    }

    /**
     * Builds PostScript-safe XMP Packet for EPS files.
     * Guarantees that dc:description, dc:title, photoshop:Caption, and photoshop:Headline
     * are populated for Shutterstock & microstock compliance.
     * Every line begins with '%' so that PostScript interpreters treat the packet as comments.
     */
    fun bangunXmpXml(title: String, description: String, keywords: List<String>, mimeType: String = "application/postscript"): String {
        val rawTitle = title.trim()
        val rawDesc = description.trim()
        val effectiveTitle = if (rawTitle.isNotEmpty()) rawTitle else rawDesc
        val effectiveDesc = if (rawDesc.isNotEmpty()) rawDesc else rawTitle

        val titleEsc = escapeXml(effectiveTitle)
        val descEsc = escapeXml(effectiveDesc)
        val cleanKeywords = keywords.map { it.trim() }.filter { it.isNotEmpty() }
        val bagKeywords = cleanKeywords.joinToString("\n") { kw -> "%        <rdf:li>${escapeXml(kw)}</rdf:li>" }

        val sb = java.lang.StringBuilder()
        sb.append("%<?xpacket begin=\"\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n")
        sb.append("%<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">\n")
        sb.append("%  <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n")
        sb.append("%    <rdf:Description rdf:about=\"\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:photoshop=\"http://ns.adobe.com/photoshop/1.0/\">\n")
        sb.append("%      <dc:format>image/eps</dc:format>\n")
        if (titleEsc.isNotEmpty()) {
            sb.append("%      <dc:title><rdf:Alt><rdf:li xml:lang=\"x-default\">$titleEsc</rdf:li></rdf:Alt></dc:title>\n")
        }
        if (descEsc.isNotEmpty()) {
            sb.append("%      <dc:description><rdf:Alt><rdf:li xml:lang=\"x-default\">$descEsc</rdf:li></rdf:Alt></dc:description>\n")
        }
        if (cleanKeywords.isNotEmpty()) {
            sb.append("%      <dc:subject><rdf:Bag>\n$bagKeywords\n%      </rdf:Bag></dc:subject>\n")
        }
        if (titleEsc.isNotEmpty()) {
            sb.append("%      <photoshop:Headline>$titleEsc</photoshop:Headline>\n")
        }
        if (descEsc.isNotEmpty()) {
            sb.append("%      <photoshop:Caption>$descEsc</photoshop:Caption>\n")
        }
        sb.append("%    </rdf:Description>\n")
        sb.append("%  </rdf:RDF>\n")
        sb.append("%</x:xmpmeta>\n")
        sb.append("%<?xpacket end=\"w\"?>")
        return sb.toString()
    }

    fun injectIntoJpeg(
        originalBytes: ByteArray,
        title: String,
        description: String,
        keywords: List<String>,
        creator: String
    ): ByteArray {
        if (originalBytes.size < 2 || originalBytes[0] != 0xFF.toByte() || originalBytes[1] != 0xD8.toByte()) {
            return originalBytes
        }

        val xmpXml = generateXmpMeta(title, description, keywords, creator)
        val xmpBytes = xmpXml.toByteArray(StandardCharsets.UTF_8)
        
        val signature = "http://ns.adobe.com/xap/1.0/\u0000"
        val signatureBytes = signature.toByteArray(StandardCharsets.UTF_8)
        
        val seg = ByteArray(4 + signatureBytes.size + xmpBytes.size)
        seg[0] = 0xFF.toByte()
        seg[1] = 0xE1.toByte()
        val len = signatureBytes.size + xmpBytes.size + 2
        seg[2] = ((len ushr 8) and 0xFF).toByte()
        seg[3] = (len and 0xFF).toByte()
        
        System.arraycopy(signatureBytes, 0, seg, 4, signatureBytes.size)
        System.arraycopy(xmpBytes, 0, seg, 4 + signatureBytes.size, xmpBytes.size)
        
        var pos = 2
        while (pos < originalBytes.size - 4) {
            if (originalBytes[pos] == 0xFF.toByte() && originalBytes[pos + 1] == 0xE1.toByte()) {
                val l = ((originalBytes[pos + 2].toInt() and 0xFF) shl 8) + (originalBytes[pos + 3].toInt() and 0xFF)
                var isXmp = true
                for (h in signatureBytes.indices) {
                    if (pos + 4 + h >= originalBytes.size || originalBytes[pos + 4 + h] != signatureBytes[h]) {
                        isXmp = false
                        break
                    }
                }
                if (isXmp) {
                    val newJpeg = ByteArray(originalBytes.size - (2 + l) + seg.size)
                    System.arraycopy(originalBytes, 0, newJpeg, 0, pos)
                    System.arraycopy(seg, 0, newJpeg, pos, seg.size)
                    System.arraycopy(originalBytes, pos + 2 + l, newJpeg, pos + seg.size, originalBytes.size - (pos + 2 + l))
                    return newJpeg
                }
                pos += 2 + l
            } else if (originalBytes[pos] == 0xFF.toByte() && (originalBytes[pos + 1] == 0xDA.toByte() || originalBytes[pos + 1] == 0xD9.toByte())) {
                break
            } else {
                pos++
            }
        }
        
        val out = ByteArray(originalBytes.size + seg.size)
        System.arraycopy(originalBytes, 0, out, 0, 2)
        System.arraycopy(seg, 0, out, 2, seg.size)
        System.arraycopy(originalBytes, 2, out, 2 + seg.size, originalBytes.size - 2)
        return out
    }

    fun injectIntoPng(
        originalBytes: ByteArray,
        title: String,
        description: String,
        keywords: List<String>,
        creator: String
    ): ByteArray {
        val xmpXml = generateXmpMeta(title, description, keywords, creator)
        val xmpBytes = xmpXml.toByteArray(StandardCharsets.UTF_8)

        val keywordBytes = "XML:com.adobe.xmp\u0000".toByteArray(StandardCharsets.UTF_8)
        val compBytes = byteArrayOf(0, 0)
        val langAndTransBytes = byteArrayOf(0, 0)

        val chunkDataOutput = ByteArrayOutputStream()
        chunkDataOutput.write(keywordBytes)
        chunkDataOutput.write(compBytes)
        chunkDataOutput.write(langAndTransBytes)
        chunkDataOutput.write(xmpBytes)

        val chunkData = chunkDataOutput.toByteArray()
        val chunkTypeBytes = "iTXt".toByteArray(StandardCharsets.UTF_8)

        val buffer = ByteBuffer.allocate(4 + chunkTypeBytes.size + chunkData.size + 4)
        buffer.order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(chunkData.size)
        buffer.put(chunkTypeBytes)
        buffer.put(chunkData)

        val crc = CRC32()
        crc.update(chunkTypeBytes)
        crc.update(chunkData)
        buffer.putInt(crc.value.toInt())

        val itxtChunkBytes = buffer.array()

        val pngSignature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        if (originalBytes.size < 8) {
            throw IllegalArgumentException("Not a valid PNG file (Too short)")
        }
        for (i in 0..7) {
            if (originalBytes[i] != pngSignature[i]) {
                throw IllegalArgumentException("Not a valid PNG file (Signature mismatch)")
            }
        }

        val outputStream = ByteArrayOutputStream()
        outputStream.write(pngSignature)

        var offset = 8
        while (offset < originalBytes.size) {
            if (offset + 8 > originalBytes.size) {
                outputStream.write(originalBytes, offset, originalBytes.size - offset)
                break
            }

            val lenBuf = ByteBuffer.wrap(originalBytes, offset, 4)
            lenBuf.order(ByteOrder.BIG_ENDIAN)
            val chunkLen = lenBuf.int

            val typeBytes = ByteArray(4)
            System.arraycopy(originalBytes, offset + 4, typeBytes, 0, 4)
            val chunkType = String(typeBytes, StandardCharsets.US_ASCII)

            var isExistingXmp = false
            if (chunkType == "iTXt" && offset + 8 + "XML:com.adobe.xmp\u0000".length <= originalBytes.size) {
                val kwCompare = String(originalBytes, offset + 8, "XML:com.adobe.xmp\u0000".length, StandardCharsets.UTF_8)
                if (kwCompare == "XML:com.adobe.xmp\u0000") {
                    isExistingXmp = true
                }
            }

            if (isExistingXmp) {
                offset += 4 + 4 + chunkLen + 4
            } else {
                val totalChunkSize = 4 + 4 + chunkLen + 4
                if (offset + totalChunkSize <= originalBytes.size) {
                    outputStream.write(originalBytes, offset, totalChunkSize)
                    offset += totalChunkSize
                } else {
                    outputStream.write(originalBytes, offset, originalBytes.size - offset)
                    break
                }

                if (chunkType == "IHDR") {
                    outputStream.write(itxtChunkBytes)
                }
            }
        }

        return outputStream.toByteArray()
    }

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
            val metaCreator = creator.trim()

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
                    val injectedPsBytes = injectIntoPostScriptBytes(rawPsBytes, metaTitle, metaDesc, cleanKeywords, metaCreator)
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
            return injectIntoPostScriptBytes(originalBytes, metaTitle, metaDesc, cleanKeywords, metaCreator)
        } catch (e: Exception) {
            e.printStackTrace()
            return originalBytes
        }
    }

    private fun injectIntoPostScriptBytes(
        psBytes: ByteArray,
        metaTitle: String,
        metaDesc: String,
        cleanKeywords: List<String>,
        metaCreator: String
    ): ByteArray {
        var psStr = String(psBytes, StandardCharsets.ISO_8859_1)

        // 1. Remove ANY complex client injection scripts (%ADOBeginClientInjection ... %ADOEndClientInjection)
        val clientInjectionRegex = Regex("""%ADOBeginClientInjection[\s\S]*?%ADOEndClientInjection[^\r\n]*\r?\n?""")
        psStr = psStr.replace(clientInjectionRegex, "")

        // 2. Remove previous XMP blocks if present
        val existingXmpRegex = Regex("""%BeginXMP: BeforeBegin[\s\S]*?%EndXMP:\r?\n?""")
        psStr = psStr.replace(existingXmpRegex, "")

        // 3. PostScript standard comments & single universal XMP packet block
        val xmpPacket = bangunXmpXml(metaTitle, metaDesc, cleanKeywords, "application/postscript")
        val headerKomentarList = mutableListOf<String>()
        headerKomentarList.add("%ADO_ContainsXMP: MainFirst")

        val rawTitle = metaTitle.trim()
        val rawDesc = metaDesc.trim()
        val effectiveTitle = if (rawTitle.isNotEmpty()) rawTitle else rawDesc
        val effectiveDesc = if (rawDesc.isNotEmpty()) rawDesc else rawTitle

        if (effectiveTitle.isNotEmpty()) {
            headerKomentarList.add("%%Title: $effectiveTitle")
        }
        if (effectiveDesc.isNotEmpty()) {
            headerKomentarList.add("%%Subject: $effectiveDesc")
            headerKomentarList.add("%%Comments: $effectiveDesc")
        }
        if (metaCreator.isNotEmpty()) {
            headerKomentarList.add("%%Creator: $metaCreator")
        }
        if (cleanKeywords.isNotEmpty()) {
            headerKomentarList.add("%%Keywords: " + cleanKeywords.joinToString(", "))
        }
        headerKomentarList.add("%BeginXMP: BeforeBegin\n$xmpPacket\n%EndXMP:")
        val headerKomentar = headerKomentarList.joinToString("\n")

        // 4. Inject comments right before %%EndComments
        val endCommentsRegex = Regex("""\r?\n%%EndComments""")
        if (psStr.contains(endCommentsRegex)) {
            psStr = psStr.replace(endCommentsRegex) {
                "\n" + headerKomentar + "\n%%EndComments"
            }
        } else {
            val psHeaderRegex = Regex("""%!PS-Adobe-3\.0[^\r\n]*""")
            if (psStr.contains(psHeaderRegex)) {
                psStr = psStr.replace(psHeaderRegex) { match ->
                    match.value + "\n" + headerKomentar + "\n%%EndComments"
                }
            } else {
                psStr = headerKomentar + "\n" + psStr
            }
        }

        return toBinaryPreservingBytes(psStr)
    }

    private fun toBinaryPreservingBytes(str: String): ByteArray {
        val bos = ByteArrayOutputStream(str.length + 512)
        var i = 0
        while (i < str.length) {
            val codePoint = str.codePointAt(i)
            if (codePoint <= 0xFF) {
                bos.write(codePoint)
            } else {
                val charBytes = String(Character.toChars(codePoint)).toByteArray(StandardCharsets.UTF_8)
                bos.write(charBytes)
            }
            i += Character.charCount(codePoint)
        }
        return bos.toByteArray()
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

    fun injectIntoSvg(
        originalBytes: ByteArray,
        title: String,
        description: String,
        keywords: List<String>
    ): ByteArray {
        try {
            val metaTitle = title.trim()
            val metaDesc = description.trim()
            val cleanKeywords = keywords.map { it.trim() }.filter { it.isNotEmpty() }

            if (metaTitle.isEmpty() && metaDesc.isEmpty() && cleanKeywords.isEmpty()) {
                return originalBytes
            }

            val effectiveTitle = if (metaTitle.isNotEmpty()) metaTitle else metaDesc
            val effectiveDesc = if (metaDesc.isNotEmpty()) metaDesc else metaTitle

            val fileStr = String(originalBytes, StandardCharsets.UTF_8)
            val titleEsc = escapeXml(effectiveTitle)
            val descEsc = escapeXml(effectiveDesc)
            val bagKeywords = cleanKeywords
                .map { kw -> "          <rdf:li>${escapeXml(kw)}</rdf:li>" }
                .joinToString("\n")

            val svgRdfXml = """<metadata id="metadata-xmp">
  <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
           xmlns:dc="http://purl.org/dc/elements/1.1/"
           xmlns:photoshop="http://ns.adobe.com/photoshop/1.0/">
    <rdf:Description rdf:about="">
      <dc:format>image/svg+xml</dc:format>
      <dc:title>
        <rdf:Alt>
          <rdf:li xml:lang="x-default">$titleEsc</rdf:li>
        </rdf:Alt>
      </dc:title>
      <dc:description>
        <rdf:Alt>
          <rdf:li xml:lang="x-default">$descEsc</rdf:li>
        </rdf:Alt>
      </dc:description>
      <dc:subject>
        <rdf:Bag>
$bagKeywords
        </rdf:Bag>
      </dc:subject>
      <photoshop:Headline>$titleEsc</photoshop:Headline>
      <photoshop:Caption>$descEsc</photoshop:Caption>
    </rdf:Description>
  </rdf:RDF>
</metadata>"""

            var hasilSvg = fileStr
            if (hasilSvg.contains("<metadata")) {
                val metadataRegex = Regex("<metadata[\\s\\S]*?</metadata>")
                hasilSvg = hasilSvg.replace(metadataRegex, svgRdfXml)
            } else {
                val svgOpenTagRegex = Regex("(<svg[^>]*>)")
                hasilSvg = hasilSvg.replace(svgOpenTagRegex, "$1\n$svgRdfXml")
            }

            return hasilSvg.toByteArray(StandardCharsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            return originalBytes
        }
    }
}
