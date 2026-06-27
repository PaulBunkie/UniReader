package com.example.unireader

import android.content.Context
import java.io.File
import java.util.zip.ZipInputStream

class ChapterLoader(private val context: Context, private val book: EpubBook) {

    fun loadChapterHtml(index: Int): String? {
        if (index < 0 || index >= book.spine.size) return null
        val item = book.spine[index]
        val opfDir = File(book.opfPath).parent ?: ""
        val fullPath = if (opfDir.isEmpty()) item.href else "$opfDir/${item.href}".replace("//", "/")
        
        return try {
            context.contentResolver.openInputStream(book.uri)?.use { inputStream ->
                val zip = ZipInputStream(inputStream)
                var entry = zip.nextEntry
                while (entry != null) {
                    val entryName = entry.name.replace("\\", "/")
                    if (entryName == fullPath.replace("\\", "/")) {
                        val rawHtml = zip.readBytes().toString(Charsets.UTF_8)
                        return sanitizeHtml(rawHtml, opfDir)
                    }
                    entry = zip.nextEntry
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun sanitizeHtml(html: String, opfDir: String): String {
        val bodyRegex = Regex("<body[^>]*>(.*)</body>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val bodyMatch = bodyRegex.find(html)
        var content = bodyMatch?.groupValues?.get(1) ?: html

        content = content.replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        
        content = content.replace(Regex("src=\"(?!epub://|http://|https://)(.*?)\"")) { matchResult ->
            val relativePath = matchResult.groupValues[1]
            val absolutePath = resolveRelativePath(opfDir, relativePath)
            "src=\"epub://$absolutePath\""
        }
        
        content = content.replace(Regex("href=\"(?!epub://|http://|https://|#)(.*?)\"")) { matchResult ->
            val relativePath = matchResult.groupValues[1]
            val absolutePath = resolveRelativePath(opfDir, relativePath)
            "href=\"epub://$absolutePath\""
        }

        return content
    }
    
    private fun resolveRelativePath(base: String, relative: String): String {
        val parts = (if (base.isEmpty()) "" else "$base/").split("/").filter { it.isNotEmpty() }.toMutableList()
        val relParts = relative.split("/")
        for (part in relParts) {
            if (part == "..") {
                if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
            } else if (part != "." && part.isNotEmpty()) {
                parts.add(part)
            }
        }
        return parts.joinToString("/")
    }
}
