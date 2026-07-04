package com.example.unireader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BooksAdapter(
    private var books: List<BookMetadata>,
    private val onClick: (BookMetadata) -> Unit
) : RecyclerView.Adapter<BooksAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(android.R.id.text1)
        val author: TextView = view.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val book = books[position]
        holder.title.text = book.title
        holder.author.text = book.author
        holder.itemView.setOnClickListener { onClick(book) }
    }

    override fun getItemCount() = books.size

    fun updateBooks(newBooks: List<BookMetadata>) {
        books = newBooks
        notifyDataSetChanged()
    }
}
