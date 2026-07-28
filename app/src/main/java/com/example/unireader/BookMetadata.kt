package com.example.unireader

data class BookMetadata(
    val uri: String,
    val title: String,
    val author: String,
    var lastSpineIndex: Int = 0,
    var lastPageIndex: Int = -1,
    var isTranslationMode: Boolean = false,
    var isTocTranslated: Boolean = false,
    var serverGlossary: String? = null,
    var localCopyUri: String? = null,
    var totalSpineItems: Int = 0
)
