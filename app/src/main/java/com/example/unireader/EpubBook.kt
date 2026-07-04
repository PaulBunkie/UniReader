package com.example.unireader

import android.net.Uri

data class TocItem(
    val title: String,
    val href: String
)

data class EpubBook(
    val uri: Uri,
    val title: String?,
    val author: String?,
    val spine: List<SpineItem>,
    val opfPath: String,
    val toc: List<TocItem> = emptyList()
)

data class SpineItem(
    val href: String,
    val idref: String,
    val mediaType: String?
)
