package com.example.unireader

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class HighlightDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "highlights.db"
        private const val DATABASE_VERSION = 1

        private const val TABLE_HIGHLIGHTS = "highlights"
        private const val COLUMN_ID = "id"
        private const val COLUMN_BOOK_URI = "book_uri"
        private const val COLUMN_SPINE_INDEX = "spine_index"
        private const val COLUMN_ELEMENT_IDX = "element_idx"
        private const val COLUMN_START_OFFSET = "start_offset"
        private const val COLUMN_END_OFFSET = "end_offset"
        private const val COLUMN_ORIGINAL_TEXT = "original_text"
        private const val COLUMN_REPLACEMENT_TEXT = "replacement_text"
        private const val COLUMN_COLOR = "color"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_HIGHLIGHTS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_BOOK_URI TEXT,
                $COLUMN_SPINE_INDEX INTEGER,
                $COLUMN_ELEMENT_IDX INTEGER,
                $COLUMN_START_OFFSET INTEGER,
                $COLUMN_END_OFFSET INTEGER,
                $COLUMN_ORIGINAL_TEXT TEXT,
                $COLUMN_REPLACEMENT_TEXT TEXT,
                $COLUMN_COLOR TEXT
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HIGHLIGHTS")
        onCreate(db)
    }

    fun saveHighlight(highlight: Highlight): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_BOOK_URI, highlight.bookUri)
            put(COLUMN_SPINE_INDEX, highlight.spineIndex)
            put(COLUMN_ELEMENT_IDX, highlight.elementIdx)
            put(COLUMN_START_OFFSET, highlight.startOffset)
            put(COLUMN_END_OFFSET, highlight.endOffset)
            put(COLUMN_ORIGINAL_TEXT, highlight.originalText)
            put(COLUMN_REPLACEMENT_TEXT, highlight.replacementText)
            put(COLUMN_COLOR, highlight.color)
        }
        return db.insert(TABLE_HIGHLIGHTS, null, values)
    }

    fun getHighlights(bookUri: String, spineIndex: Int): List<Highlight> {
        val highlights = mutableListOf<Highlight>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_HIGHLIGHTS,
            null,
            "$COLUMN_BOOK_URI = ? AND $COLUMN_SPINE_INDEX = ?",
            arrayOf(bookUri, spineIndex.toString()),
            null, null, null
        )

        cursor?.use {
            while (it.moveToNext()) {
                highlights.add(Highlight(
                    id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                    bookUri = it.getString(it.getColumnIndexOrThrow(COLUMN_BOOK_URI)),
                    spineIndex = it.getInt(it.getColumnIndexOrThrow(COLUMN_SPINE_INDEX)),
                    elementIdx = it.getInt(it.getColumnIndexOrThrow(COLUMN_ELEMENT_IDX)),
                    startOffset = it.getInt(it.getColumnIndexOrThrow(COLUMN_START_OFFSET)),
                    endOffset = it.getInt(it.getColumnIndexOrThrow(COLUMN_END_OFFSET)),
                    originalText = it.getString(it.getColumnIndexOrThrow(COLUMN_ORIGINAL_TEXT)),
                    replacementText = it.getString(it.getColumnIndexOrThrow(COLUMN_REPLACEMENT_TEXT)),
                    color = it.getString(it.getColumnIndexOrThrow(COLUMN_COLOR))
                ))
            }
        }
        return highlights
    }
}
