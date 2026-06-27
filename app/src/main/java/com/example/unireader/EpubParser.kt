package com.example.unireader

import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
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
                if (entry.name == "META-INF/container.xml") {
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

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val zip = ZipInputStream(inputStream)
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == opfPath) {
                    val parser = Xml.newPullParser()
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    parser.setInput(zip, null)

                    var eventType = parser.eventType
                    var currentTag: String? = null

                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        when (eventType) {
                            XmlPullParser.START_TAG -> {
                                currentTag = parser.name
                                when (currentTag) {
                                    "item" -> {
                                        val id = parser.getAttributeValue(null, "id")
                                        val href = parser.getAttributeValue(null, "href")
                                        val type = parser.getAttributeValue(null, "media-type")
                                        if (id != null && href != null) {
                                            manifest[id] = href to (type ?: "")
                                        }
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
                                if (currentTag == "dc:title") title = parser.text
                                if (currentTag == "dc:creator") author = parser.text
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

        return if (spine.isNotEmpty()) {
            EpubBook(uri, title, author, spine, opfPath)
        } else {
            null
        }
    }
}
