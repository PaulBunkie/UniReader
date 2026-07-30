package com.reaido.unireader

import android.content.Context
import android.net.Uri
import android.util.Log
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class EpubModifier(private val context: Context) {

    fun createLocalCopy(sourceUri: Uri): Uri? {
        val fileName = "book_${System.currentTimeMillis()}.epub"
        val localFile = File(context.filesDir, fileName)
        
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                localFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return Uri.fromFile(localFile)
        } catch (e: Exception) {
            Log.e("EpubModifier", "Error creating local copy", e)
            return null
        }
    }

    fun replaceEntry(epubUri: Uri, entryName: String, content: String): Boolean {
        Log.d("EpubModifier", "replaceEntry: target=$entryName uri=$epubUri")
        val path = epubUri.path ?: return false
        val sourceFile = File(path)
        if (!sourceFile.exists()) {
            Log.e("EpubModifier", "replaceEntry: Source file does not exist: $path")
            return false
        }

        val tempFile = File(context.cacheDir, "temp_mod_${System.currentTimeMillis()}.epub")
        val normalizedTarget = entryName.replace("\\", "/")
        
        var replaced = false
        try {
            sourceFile.inputStream().use { inputStream ->
                ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
                    val zis = ZipInputStream(inputStream)
                    
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        val normalizedName = name.replace("\\", "/")
                        
                        val newEntry = ZipEntry(name)
                        if (normalizedName == "mimetype") {
                            newEntry.method = ZipEntry.STORED
                            val bytes = zis.readBytes()
                            newEntry.size = bytes.size.toLong()
                            newEntry.compressedSize = bytes.size.toLong()
                            newEntry.crc = calculateCrc(bytes)
                            zos.putNextEntry(newEntry)
                            zos.write(bytes)
                        } else if (normalizedName == normalizedTarget) {
                            Log.d("EpubModifier", "replaceEntry: Found target entry $name, writing new content")
                            zos.putNextEntry(newEntry)
                            zos.write(content.toByteArray(Charsets.UTF_8))
                            replaced = true
                        } else {
                            zos.putNextEntry(newEntry)
                            zis.copyTo(zos)
                        }
                        zos.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
            
            if (!replaced) {
                Log.e("EpubModifier", "replaceEntry: Target entry $normalizedTarget not found in ZIP")
                tempFile.delete()
                return false
            }

            // Overwrite original file directly
            tempFile.inputStream().use { input ->
                sourceFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tempFile.delete()
            Log.d("EpubModifier", "replaceEntry: Successfully updated $normalizedTarget")
            return true
            
        } catch (e: Exception) {
            Log.e("EpubModifier", "replaceEntry: Error replacing entry", e)
            if (tempFile.exists()) tempFile.delete()
            return false
        }
    }

    fun readEntry(epubUri: Uri, entryName: String): String? {
        val path = epubUri.path ?: return null
        val sourceFile = File(path)
        if (!sourceFile.exists()) return null
        
        val normalizedTarget = entryName.replace("\\", "/")
        try {
            sourceFile.inputStream().use { inputStream ->
                val zis = ZipInputStream(inputStream)
                var entry = zis.nextEntry
                while (entry != null) {
                    if (entry.name.replace("\\", "/") == normalizedTarget) {
                        return zis.readBytes().toString(Charsets.UTF_8)
                    }
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            Log.e("EpubModifier", "Error reading entry $entryName", e)
        }
        return null
    }

    fun applyFixes(book: EpubBook, fixes: List<Highlight>, destinationUri: Uri): Boolean {
        try {
            context.contentResolver.openInputStream(book.uri)?.use { inputStream ->
                context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                    ZipOutputStream(outputStream).use { zos ->
                        val zis = ZipInputStream(inputStream)
                        
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val name = entry.name
                            val fixesForThisFile = getFixesForFile(book, name, fixes)
                            
                            val newEntry = ZipEntry(name)
                            if (name == "mimetype") {
                                newEntry.method = ZipEntry.STORED
                                val bytes = zis.readBytes()
                                newEntry.size = bytes.size.toLong()
                                newEntry.compressedSize = bytes.size.toLong()
                                newEntry.crc = calculateCrc(bytes)
                                zos.putNextEntry(newEntry)
                                zos.write(bytes)
                            } else if (fixesForThisFile.isNotEmpty()) {
                                zos.putNextEntry(newEntry)
                                val rawHtml = zis.readBytes().toString(Charsets.UTF_8)
                                val modifiedHtml = modifyHtml(rawHtml, fixesForThisFile)
                                zos.write(modifiedHtml.toByteArray(Charsets.UTF_8))
                            } else {
                                zos.putNextEntry(newEntry)
                                zis.copyTo(zos)
                            }
                            zos.closeEntry()
                            entry = zis.nextEntry
                        }
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e("EpubModifier", "Error applying fixes", e)
            return false
        }
    }

    private fun getFixesForFile(book: EpubBook, entryName: String, allFixes: List<Highlight>): List<Highlight> {
        val opfDir = File(book.opfPath).parent ?: ""
        return allFixes.filter { fix ->
            val item = book.spine.getOrNull(fix.spineIndex) ?: return@filter false
            val fullHref = if (opfDir.isEmpty()) item.href else "$opfDir/${item.href}".replace("//", "/")
            fullHref.replace("\\", "/") == entryName.replace("\\", "/")
        }
    }

    private fun modifyHtml(html: String, fixes: List<Highlight>): String {
        // Use XML parser to preserve XML declaration and handle XHTML correctly
        val doc = Jsoup.parse(html, "", Parser.xmlParser())
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml)
        doc.outputSettings().charset(Charsets.UTF_8)
        
        val items = doc.select("p, h1, h2, h3, h4, h5, h6, li, img")
        
        val fixesByElement = fixes.groupBy { it.elementIdx }
        
        fixesByElement.forEach { (elIdx, elementFixes) ->
            val element = items.getOrNull(elIdx) ?: return@forEach
            val sortedFixes = elementFixes.sortedByDescending { it.startOffset }
            
            sortedFixes.forEach { fix ->
                val replacement = fix.replacementText ?: return@forEach
                applyReplacementToElement(element, fix.startOffset, fix.endOffset, replacement)
            }
        }
        
        return doc.outerHtml()
    }

    private fun applyReplacementToElement(element: Element, start: Int, end: Int, replacement: String) {
        // Collect all text nodes including nested ones to match browser's TreeWalker behavior
        val allNodes = mutableListOf<TextNode>()
        fun findTextNodes(node: Node) {
            if (node is TextNode) {
                allNodes.add(node)
            } else {
                for (child in node.childNodes()) {
                    findTextNodes(child)
                }
            }
        }
        findTextNodes(element)

        var currentOffset = 0
        for (node in allNodes) {
            val nodeText = node.text()
            val nodeLen = nodeText.length
            
            // Does the target range start or end within this node?
            if (currentOffset + nodeLen > start) {
                val localStart = Math.max(0, start - currentOffset)
                val localEnd = Math.min(nodeLen, end - currentOffset)
                
                if (localStart < nodeLen) {
                    val sb = StringBuilder(nodeText)
                    // Ensure we don't go out of bounds if end is beyond this node
                    val safeEnd = Math.min(localEnd, nodeLen)
                    sb.replace(localStart, safeEnd, replacement)
                    node.text(sb.toString())
                    break // For now, we assume simple case or handle first node
                }
            }
            currentOffset += nodeLen
        }
    }

    private fun calculateCrc(bytes: ByteArray): Long {
        val crc = java.util.zip.CRC32()
        crc.update(bytes)
        return crc.value
    }
}
