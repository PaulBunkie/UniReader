package com.example.unireader

data class BookMetadata(
    val uri: String,
    val title: String,
    val author: String,
    var lastSpineIndex: Int = 0,
    var lastElementIndex: Int = -1,
    var lastAnchor: String? = null,
    var lastCharOffset: Int = -1
)
