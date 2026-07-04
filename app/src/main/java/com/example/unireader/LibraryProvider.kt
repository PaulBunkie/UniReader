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
                lastAnchor = obj.optString("lastAnchor", null)
            ))
        }
        return list
    }

    fun addBook(book: BookMetadata) {
        val books = getBooks()
        if (books.any { it.uri == book.uri }) return
        books.add(book)
        saveBooks(books)
    }

    fun updateBookProgress(uri: String, spineIndex: Int, elementIndex: Int, anchor: String?) {
        val books = getBooks()
        val book = books.find { it.uri == uri }
        if (book != null) {
            book.lastSpineIndex = spineIndex
            book.lastElementIndex = elementIndex
            book.lastAnchor = anchor
            saveBooks(books)
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
            obj.put("lastAnchor", book.lastAnchor)
            array.put(obj)
        }
        prefs.edit().putString("books", array.toString()).apply()
    }
}
