package com.example.unireader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class LibraryProvider(private val context: Context) {
    private val prefs = context.getSharedPreferences("library_prefs", Context.MODE_PRIVATE)

    fun getBooks(): MutableList<BookMetadata> {
        val json = prefs.getString("books", "[]") ?: "[]"
        val array = JSONArray(json)
        val list = mutableListOf<BookMetadata>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(BookMetadata(
                uri = obj.getString("uri"),
                title = obj.getString("title"),
                author = obj.getString("author"),
                lastSpineIndex = obj.optInt("lastSpineIndex", 0),
                lastElementIndex = obj.optInt("lastElementIndex", -1),
                lastAnchor = obj.optString("lastAnchor", null),
                lastCharOffset = obj.optInt("lastCharOffset", -1),
                isTranslationMode = obj.optBoolean("isTranslationMode", false),
                isTocTranslated = obj.optBoolean("isTocTranslated", false),
                translationGuidelines = obj.optString("translationGuidelines", null),
                localCopyUri = obj.optString("localCopyUri", null)
            ))
        }
        return list
    }

    fun addBook(book: BookMetadata) {
        val books = getBooks()
        val index = books.indexOfFirst { it.uri == book.uri }
        if (index != -1) {
            books[index] = book // Update existing
        } else {
            books.add(book) // Add new
        }
        saveBooks(books)
    }

    fun updateBookProgress(uri: String, spineIndex: Int, elementIndex: Int, charOffset: Int, anchor: String?) {
        if (spineIndex < 0) return // Invalid spine index
        
        val books = getBooks()
        val book = books.find { it.uri == uri }
        if (book != null) {
            // Only update if we have a valid element index, or if we are at least updating the spine index
            if (elementIndex >= 0 || spineIndex != book.lastSpineIndex) {
                book.lastSpineIndex = spineIndex
                book.lastElementIndex = elementIndex
                book.lastCharOffset = charOffset
                book.lastAnchor = anchor
                saveBooks(books)
            }
        }
    }

    private fun saveBooks(books: List<BookMetadata>) {
        val array = JSONArray()
        books.forEach { book ->
            val obj = JSONObject()
            obj.put("uri", book.uri)
            obj.put("title", book.title)
            obj.put("author", book.author)
            obj.put("lastSpineIndex", book.lastSpineIndex)
            obj.put("lastElementIndex", book.lastElementIndex)
            obj.put("lastCharOffset", book.lastCharOffset)
            obj.put("lastAnchor", book.lastAnchor)
            obj.put("isTranslationMode", book.isTranslationMode)
            obj.put("isTocTranslated", book.isTocTranslated)
            obj.put("translationGuidelines", book.translationGuidelines)
            obj.put("localCopyUri", book.localCopyUri)
            array.put(obj)
        }
        prefs.edit().putString("books", array.toString()).apply()
    }
}
