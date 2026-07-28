package com.example.unireader

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class TranslationManager(private val context: Context, private val book: EpubBook, private val originalUri: String, private val baseUrl: String) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val service = TranslationService(context, baseUrl)
    private val modifier = EpubModifier(context)
    private val db = HighlightDatabase(context)
    private val libraryProvider = LibraryProvider(context)

    private val bookId = originalUri.hashCode().toString()
    private val activeTasks = ConcurrentHashMap<Int, Job>() // Kept for UI state tracking
    
    private val taskChannel = Channel<Int>(Channel.UNLIMITED)
    private val queuedIndices = mutableSetOf<Int>()

    var onChapterReady: ((Int) -> Unit)? = null
    var onTOCReady: ((List<TocItem>) -> Unit)? = null
    var onActiveTasksChanged: (() -> Unit)? = null
    var onTaskError: ((Int, String) -> Unit)? = null

    fun getQueuedCount(): Int = synchronized(queuedIndices) { queuedIndices.size }

    init {
        // Single worker for strict sequential processing
        scope.launch {
            for (index in taskChannel) {
                processTranslation(index)
            }
        }
    }

    fun onChapterVisible(index: Int) {
        // Standard prefetch: current and next one
        queueTranslation(index)
        queueTranslation(index + 1)
        onActiveTasksChanged?.invoke()
    }

    fun isChapterTranslated(index: Int): Boolean {
        return db.isChapterTranslated(originalUri, index)
    }

    fun getActiveTasks(): Set<Int> = activeTasks.keys

    fun forceTranslate(index: Int) {
        db.setChapterTranslated(originalUri, index, false)
        queueTranslation(index)
    }

    fun startInitialTranslation(startIndex: Int) {
        scope.launch {
            // RE-FETCH metadata to get the most up-to-date isTocTranslated flag
            val currentMeta = libraryProvider.getBooks().find { it.uri == originalUri }
            if (currentMeta?.isTocTranslated != true) {
                Log.d("TranslationManager", "TOC not translated or flag missing, requesting translation")
                translateTOC()
            } else {
                Log.d("TranslationManager", "TOC already marked as translated, skipping server request")
                // Notify UI that TOC is ready even if we didn't call the server
                withContext(Dispatchers.Main) {
                    onTOCReady?.invoke(book.toc)
                }
            }
            // Queue initial batch: current + 2 ahead to fill the container
            queueTranslation(startIndex)
            queueTranslation(startIndex + 1)
            queueTranslation(startIndex + 2)
            onActiveTasksChanged?.invoke()
        }
    }

    private fun queueTranslation(index: Int) {
        if (index < 0 || index >= book.spine.size) return
        if (isChapterTranslated(index)) return
        
        synchronized(queuedIndices) {
            if (queuedIndices.contains(index)) return
            queuedIndices.add(index)
        }
        
        Log.d("TranslationManager", "Queueing chapter $index for sequential processing")
        taskChannel.trySend(index)
        onActiveTasksChanged?.invoke()
    }

    private suspend fun processTranslation(index: Int) {
        Log.d("TranslationManager", "Processing chapter $index sequentially")
        
        // Track as active for UI
        val dummyJob = Job()
        activeTasks[index] = dummyJob
        onActiveTasksChanged?.invoke()

        try {
            val sourceBook = EpubParser(context).parse(Uri.parse(originalUri)) ?: return
            val loader = ChapterLoader(context, sourceBook)
            val chapterContent = loader.loadChapterHtml(index)
            val originalHtml = chapterContent?.html ?: run {
                Log.e("TranslationManager", "Failed to load original chapter $index body")
                return
            }

            val currentMeta = libraryProvider.getBooks().find { it.uri == originalUri }
            val localUri = currentMeta?.localCopyUri?.let { Uri.parse(it) }
            val entryName = getEntryName(index)

            // 1. Explicitly load original into the container and update UI
            if (localUri != null) {
                Log.d("TranslationManager", "Explicitly restoring original for chapter $index before translation")
                if (modifier.replaceEntry(localUri, entryName, originalHtml)) {
                    db.setChapterTranslated(originalUri, index, false)
                    withContext(Dispatchers.Main) {
                        onChapterReady?.invoke(index)
                    }
                }
            }
            
            val glossaryJson = currentMeta?.serverGlossary ?: "{}"
            val glossaryObj = try { JSONObject(glossaryJson) } catch(e: Exception) { JSONObject() }
            
            val dictEntries = db.getDictEntries(originalUri)
            val userCorrections = JSONArray()
            dictEntries.forEach { entry ->
                val translation = entry.replacementText?.substringAfter("]:") ?: ""
                userCorrections.put(JSONObject().apply {
                    put("original", entry.originalText)
                    put("translation", translation)
                })
            }
            
            Log.d("TranslationManager", "Requesting translation for chapter $index. Corrections: ${userCorrections.length()}")
            DebugLogger.log("MANAGER", "Ch $index: sending ${userCorrections.length()} corrections")
            val serverResponse = service.translateChapter(
                text = originalHtml,
                glossary = glossaryObj,
                userCorrections = userCorrections,
                bookId = bookId,
                sectionId = index
            )
            
            if (serverResponse != null) {
                val rawTranslation = serverResponse.xhtml
                val newGlossaryJson = serverResponse.glossaryJson
                
                val localUri = currentMeta?.localCopyUri?.let { Uri.parse(it) }
                val entryName = getEntryName(index)
                
                if (localUri != null) {
                    Log.d("TranslationManager", "Writing raw translated chapter $index to local EPUB")
                    if (modifier.replaceEntry(localUri, entryName, rawTranslation)) {
                        db.setChapterTranslated(originalUri, index, true)
                        db.markDictEntriesAsCommitted(originalUri)
                        currentMeta.serverGlossary = newGlossaryJson
                        libraryProvider.addBook(currentMeta)
                        
                        withContext(Dispatchers.Main) {
                            onChapterReady?.invoke(index)
                        }
                    } else {
                        Log.e("TranslationManager", "Failed to write chapter $index to local EPUB")
                        onTaskError?.invoke(index, context.getString(R.string.error_disk_write))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("TranslationManager", "Error processing chapter $index", e)
            onTaskError?.invoke(index, e.message ?: context.getString(R.string.error_server))
        } finally {
            activeTasks.remove(index)
            synchronized(queuedIndices) { queuedIndices.remove(index) }
            onActiveTasksChanged?.invoke()
        }
    }

    private suspend fun translateTOC() {
        val sourceBook = EpubParser(context).parse(Uri.parse(originalUri)) ?: return
        val tocText = sourceBook.toc.joinToString("\n") { it.title }
        Log.d("TranslationManager", "Requesting TOC translation from server")
        val translatedText = service.translateTOC(tocText)
        if (translatedText != null) {
            val lines = translatedText.split("|||").map { it.trim() }
            val newToc = sourceBook.toc.mapIndexed { i, item ->
                item.copy(title = lines.getOrElse(i) { item.title })
            }
            
            // Persist translated TOC directly to the FILE in the EPUB
            val currentMeta = libraryProvider.getBooks().find { it.uri == originalUri }
            val localUri = currentMeta?.localCopyUri?.let { Uri.parse(it) }
            val tocPath = book.tocPath
            
            if (localUri != null && tocPath != null) {
                val originalXml = modifier.readEntry(localUri, tocPath)
                if (originalXml != null) {
                    val updatedXml = updateTocXml(originalXml, newToc)
                    if (modifier.replaceEntry(localUri, tocPath, updatedXml)) {
                        Log.d("TranslationManager", "TOC file updated in EPUB successfully")
                        currentMeta.isTocTranslated = true
                        libraryProvider.addBook(currentMeta)
                    }
                }
            }

            withContext(Dispatchers.Main) {
                onTOCReady?.invoke(newToc)
            }
        }
    }

    private fun updateTocXml(xml: String, translatedToc: List<TocItem>): String {
        return try {
            val doc = org.jsoup.Jsoup.parse(xml, "", org.jsoup.parser.Parser.xmlParser())
            // Find all text tags in NCX or a tags in Nav and update them
            if (xml.contains("<ncx", true)) {
                val navLabels = doc.select("navLabel > text")
                navLabels.forEachIndexed { i, textNode ->
                    translatedToc.getOrNull(i)?.let { textNode.text(it.title) }
                }
            } else {
                val anchors = doc.select("nav a")
                anchors.forEachIndexed { i, a ->
                    translatedToc.getOrNull(i)?.let { a.text(it.title) }
                }
            }
            doc.outerHtml()
        } catch (e: Exception) {
            Log.e("TranslationManager", "Error updating TOC XML", e)
            xml
        }
    }

    private fun getEntryName(index: Int): String {
        val item = book.spine[index]
        val opfDir = File(book.opfPath).parent ?: ""
        return if (opfDir.isEmpty()) item.href else "$opfDir/${item.href}".replace("//", "/")
    }
}
