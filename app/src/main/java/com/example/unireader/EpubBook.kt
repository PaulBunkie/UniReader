package com.example.unireader

import android.net.Uri

data class EpubBook(
    val uri: Uri,
    val title: String?,
    val author: String?,
    val spine: List<SpineItem>,
    val opfPath: String
)

data class SpineItem(
    val href: String,
    val idref: String,
    val mediaType: String? = null
)
