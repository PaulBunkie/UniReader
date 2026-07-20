package com.example.unireader

data class Highlight(
    val id: Long = 0,
    val bookUri: String,
    val spineIndex: Int,
    val elementIdx: Int,
    val startOffset: Int,
    val endOffset: Int,
    val originalText: String,
    val replacementText: String? = null,
    val color: String = "#ffeb3b"
)
