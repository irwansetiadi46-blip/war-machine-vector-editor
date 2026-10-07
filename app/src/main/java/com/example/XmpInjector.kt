package com.example

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

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

    /**
     * Membentuk XML XMP Packet sesuai standar Adobe
     */
    private fun generateXmpPacket(title: String, description: String, keywords: List<String>): String {
        val t = escapeXml(title.trim())
        val d = escapeXml(description.trim())
        val cleanKeywords = keywords.map { escapeXml(it.trim()) }.filter { it.isNotEmpty() }

        val kwItems = if (cleanKeywords.isNotEmpty()) {
            cleanKeywords.joinToString("\n") { "    <rdf:li>$it</rdf:li>" }
        } else ""

        val lines = mutableListOf<String>()
        lines.add("""<?xpacket begin="" id="W5M0MpCehiHzreSzNTczkc9d"?>""")
        lines.add("""<x:xmpmeta xmlns:x="adobe:ns:meta/">""")
        lines.add("""<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">""")
        lines.add("""<rdf:Description rdf:about="" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:photoshop="http://ns.adobe.com/photoshop/1.0/">""")

        if (t.isNotEmpty()) {
            lines.add("""<dc:title><rdf:Alt><rdf:li xml:lang="x-default">$t</rdf:li></rdf:Alt></dc:title>""")
        }
        if (d.isNotEmpty()) {
            lines.add("""<dc:description><rdf:Alt><rdf:li xml:lang="x-default">$d</rdf:li></rdf:Alt></dc:description>""")
        }
        if (kwItems.isNotEmpty()) {
            lines.add("""<dc:subject><rdf:Bag>""")
            lines.add(kwItems)
            lines.add("""</rdf:Bag></dc:subject>""")
        }
        if (t.isNotEmpty()) {
            lines.add("""<photoshop:Headline>$t</photoshop:Headline>""")
        }

        lines.add("""</rdf:Description>""")
        lines.add("""</rdf:RDF>""")
        lines.add("""</x:xmpmeta>""")
        lines.add("""<?xpacket end="w"?>""")

        return lines.joinToString("\n")
    }

    /**
     * Membuat PostScript Injection Block (Sesuai fungsi `me` dari Lineva)
     */
    private fun buildPostScriptXmpBlock(title: String, description: String, keywords: List<String>): String {
        val xmpXml = generateXmpPacket(title, description, keywords)
        val endMarker = "%  &&end XMP packet marker&&"

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
            "currentfile 0 ($endMarker)",
            "/SubFileDecode filter AI11_ReadMetadata_PDFMark5",
            xmpXml,
            endMarker,
            "[{vector_design_metadata_stream}",
            "<</Type /Metadata /Subtype /XML>>",
            "/PUT AI11_PDFMark5",
            "[/Document",
            "1 dict begin /Metadata {vector_design_metadata_stream} def",
            "currentdict end /BDC AI11_PDFMark5",
            "%ADOEndClientInjection: PageSetup End \"AI11EPS\"\n"
        ).joinToString("\n")
    }

    /**
     * Menyuntikkan Metadata ke File EPS (Sesuai fungsi `we` dari Lineva)
     */
    fun injectIntoEps(
        originalBytes: ByteArray,
        title: String,
        description: String,
        keywords: List<String>
    ): ByteArray {
        try {
            val t = cleanSingleLine(title)
            val d = cleanSingleLine(description)
            val kwList = keywords.map { cleanSingleLine(it) }.filter { it.isNotEmpty() }

            if (t.isEmpty() && d.isEmpty() && kwList.isEmpty()) {
                return originalBytes
            }

            var psStr = String(originalBytes, StandardCharsets.ISO_8859_1)

            // 1. Tambahkan Comments Line di bawah Header EPS
            val commentsHeader = mutableListOf("%ADO_ContainsXMP: MainFirst")
            if (t.isNotEmpty()) commentsHeader.add("%%Title: $t")
            if (kwList.isNotEmpty()) commentsHeader.add("%%Keywords: ${kwList.joinToString(", ")}")
            val commentsBlock = commentsHeader.joinToString("\n")

            if (psStr.contains("%%EndComments")) {
                psStr = psStr.replace("\n%%EndComments", "\n$commentsBlock\n%%EndComments")
            }

            // 2. Sisipkan PDFMark Injection Stream persis setelah %%EndComments
            val pdfMarkInjection = buildPostScriptXmpBlock(t, d, kwList)
            psStr = psStr.replace(Regex("""(%%EndComments\s*)""")) { matchResult ->
                "${matchResult.value}\n$pdfMarkInjection"
            }

            // 3. Sisipkan PageTrailer di akhir sebelum showpage/EOF (Sesuai `xe` pada Lineva)
            val pageTrailerBlock = listOf(
                "%ADOBeginClientInjection: PageTrailer Start \"AI11EPS\"",
                "[/EMC AI11_PDFMark5",
                "[/NamespacePop AI11_PDFMark5",
                "%ADOEndClientInjection: PageTrailer Start \"AI11EPS\"\n"
            ).joinToString("\n")

            if (psStr.contains("showpage")) {
                psStr = psStr.replace("\nshowpage", "\n$pageTrailerBlock\nshowpage")
            } else if (psStr.contains("%%EOF")) {
                psStr = psStr.replace("%%EOF", "$pageTrailerBlock\n%%EOF")
            }

            return psStr.toByteArray(StandardCharsets.ISO_8859_1)

        } catch (e: Exception) {
            e.printStackTrace()
            return originalBytes
        }
    }
}
