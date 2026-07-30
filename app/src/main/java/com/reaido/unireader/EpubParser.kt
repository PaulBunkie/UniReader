package com.reaido.unireader

import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

class EpubParser(private val context: Context) {

    fun parse(uri: Uri): EpubBook? {
        val opfPath = findOpfPath(uri) ?: return null
        return parseOpf(uri, opfPath)
    }

    private fun findOpfPath(uri: Uri): String? {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val zip = ZipInputStream(inputStream)
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.equals("META-INF/container.xml", ignoreCase = true)) {
                    return parseContainerXml(zip)
                }
                entry = zip.nextEntry
            }
        }
        return null
    }

    private fun parseContainerXml(inputStream: InputStream): String? {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                return parser.getAttributeValue(null, "full-path")
            }
            eventType = parser.next()
        }
        return null
    }

    private fun parseOpf(uri: Uri, opfPath: String): EpubBook? {
        var title: String? = null
        var author: String? = null
        val manifest = mutableMapOf<String, Pair<String, String>>() // id -> (href, media-type)
        val spine = mutableListOf<SpineItem>()
        var tocId: String? = null
        var navHref: String? = null

        val opfDir = File(opfPath).parent ?: ""

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val zip = ZipInputStream(inputStream)
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name.replace("\\", "/").equals(opfPath.replace("\\", "/"), ignoreCase = true)) {
                    val parser = Xml.newPullParser()
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    parser.setInput(zip, null)

                    var eventType = parser.eventType
                    var currentTag: String? = null

                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        when (eventType) {
                            XmlPullParser.START_TAG -> {
                                currentTag = parser.name.lowercase()
                                when (currentTag) {
                                    "item" -> {
                                        val id = parser.getAttributeValue(null, "id")
                                        val href = parser.getAttributeValue(null, "href")
                                        val type = parser.getAttributeValue(null, "media-type")
                                        val properties = parser.getAttributeValue(null, "properties")
                                        if (id != null && href != null) {
                                            manifest[id] = href to (type ?: "")
                                            if (properties == "nav") navHref = href
                                        }
                                    }
                                    "spine" -> {
                                        tocId = parser.getAttributeValue(null, "toc")
                                    }
                                    "itemref" -> {
                                        val idref = parser.getAttributeValue(null, "idref")
                                        if (idref != null) {
                                            manifest[idref]?.let {
                                                spine.add(SpineItem(it.first, idref, it.second))
                                            }
                                        }
                                    }
                                }
                            }
                            XmlPullParser.TEXT -> {
                                val text = parser.text?.trim()
                                if (!text.isNullOrEmpty()) {
                                    when (currentTag) {
                                        "dc:title", "title" -> title = text
                                        "dc:creator", "creator", "dc:author", "author" -> author = text
                                    }
                                }
                            }
                            XmlPullParser.END_TAG -> currentTag = null
                        }
                        eventType = parser.next()
                    }
                    break
                }
                entry = zip.nextEntry
            }
        }

        val toc = mutableListOf<TocItem>()
        var tocPath: String? = null
        
        // Try Nav (EPUB 3) first
        if (navHref != null) {
            val fullNavPath = if (opfDir.isEmpty()) navHref else "$opfDir/$navHref".replace("//", "/")
            val navDir = File(fullNavPath).parent ?: ""
            parseNav(uri, fullNavPath, navDir)?.let { 
                toc.addAll(it)
                tocPath = fullNavPath
            }
        }
        
        // If Nav failed or empty, try NCX (EPUB 2)
        if (toc.isEmpty() && tocId != null) {
            val ncxHref = manifest[tocId]?.first
            if (ncxHref != null) {
                val ncxPath = if (opfDir.isEmpty()) ncxHref else "$opfDir/$ncxHref".replace("//", "/")
                val ncxDir = File(ncxPath).parent ?: ""
                parseNcx(uri, ncxPath, ncxDir)?.let { 
                    toc.addAll(it)
                    tocPath = ncxPath
                }
            }
        }

        Log.d("EpubParser", "Parsed TOC: ${toc.size} items found, path: $tocPath")

        return if (spine.isNotEmpty()) {
            EpubBook(uri, title, author, spine, opfPath, toc, tocPath)
        } else {
            null
        }
    }

    private fun parseNav(uri: Uri, navPath: String, navDir: String): List<TocItem>? {
        val toc = mutableListOf<TocItem>()
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val zip = ZipInputStream(inputStream)
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name.replace("\\", "/").equals(navPath.replace("\\", "/"), ignoreCase = true)) {
                        val parser = Xml.newPullParser()
                        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                        parser.setInput(zip, null)

                        var eventType = parser.eventType
                        var inNav = false
                        var currentText = StringBuilder()
                        var currentHref: String? = null

                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            when (eventType) {
                                XmlPullParser.START_TAG -> {
                                    if (parser.name == "nav") inNav = true
                                    if (inNav && parser.name == "a") {
                                        currentHref = parser.getAttributeValue(null, "href")
                                        currentText = StringBuilder()
                                    }
                                }
                                XmlPullParser.TEXT -> {
                                    if (inNav && currentHref != null) {
                                        currentText.append(parser.text)
                                    }
                                }
                                XmlPullParser.END_TAG -> {
                                    if (parser.name == "nav") inNav = false
                                    if (inNav && parser.name == "a") {
                                        if (currentHref != null && currentText.isNotEmpty()) {
                                            val normalizedHref = resolveRelativePath(navDir, currentHref!!)
                                            toc.add(TocItem(currentText.toString().trim(), normalizedHref))
                                        }
                                        currentHref = null
                                    }
                                }
                            }
                            eventType = parser.next()
                        }
                        return toc
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return null
    }

    private fun parseNcx(uri: Uri, ncxPath: String, ncxDir: String): List<TocItem>? {
        val toc = mutableListOf<TocItem>()
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val zip = ZipInputStream(inputStream)
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name.replace("\\", "/").equals(ncxPath.replace("\\", "/"), ignoreCase = true)) {
                        val parser = Xml.newPullParser()
                        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                        parser.setInput(zip, null)

                        var eventType = parser.eventType
                        var currentText: String? = null
                        var currentHref: String? = null
                        var currentTag: String? = null

                        while (eventType != XmlPullParser.END_DOCUMENT) {
                            when (eventType) {
                                XmlPullParser.START_TAG -> {
                                    currentTag = parser.name
                                    if (currentTag == "content") {
                                        currentHref = parser.getAttributeValue(null, "src")
                                    }
                                }
                                XmlPullParser.TEXT -> {
                                    if (currentTag == "text") {
                                        currentText = parser.text
                                    }
                                }
                                XmlPullParser.END_TAG -> {
                                    if (parser.name == "navPoint") {
                                        if (currentText != null && currentHref != null) {
                                            val normalizedHref = resolveRelativePath(ncxDir, currentHref!!)
                                            toc.add(TocItem(currentText!!.trim(), normalizedHref))
                                        }
                                        currentText = null
                                        currentHref = null
                                    }
                                    currentTag = null
                                }
                            }
                            eventType = parser.next()
                        }
                        return toc
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return null
    }

    private fun resolveRelativePath(base: String, relative: String): String {
        if (relative.contains("://")) return relative
        val pathWithoutFragment = relative.substringBefore("#")
        val fragment = if (relative.contains("#")) "#" + relative.substringAfter("#") else ""
        
        val parts = (if (base.isEmpty()) "" else "$base/").split("/").filter { it.isNotEmpty() }.toMutableList()
        val relParts = pathWithoutFragment.split("/")
        for (part in relParts) {
            if (part == "..") {
                if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
            } else if (part != "." && part.isNotEmpty()) {
                parts.add(part)
            }
        }
        return parts.joinToString("/") + fragment
    }
}
