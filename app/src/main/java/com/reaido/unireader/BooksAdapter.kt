package com.reaido.unireader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class BooksAdapter(
    private var books: List<BookMetadata>,
    private val onClick: (BookMetadata) -> Unit,
    private val onLongClick: (BookMetadata) -> Unit
) : RecyclerView.Adapter<BooksAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.textTitle)
        val author: TextView = view.findViewById(R.id.textAuthor)
        val iconMode: ImageView = view.findViewById(R.id.iconMode)
        val progress: TextView = view.findViewById(R.id.textProgress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_book, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val book = books[position]
        holder.title.text = book.title
        holder.author.text = book.author
        
        val iconRes = if (book.isTranslationMode) R.drawable.ic_translate else R.drawable.ic_book
        holder.iconMode.setImageResource(iconRes)

        if (book.totalSpineItems > 0) {
            holder.progress.text = holder.itemView.context.getString(R.string.progress_format, book.lastSpineIndex + 1, book.totalSpineItems)
            holder.progress.visibility = View.VISIBLE
        } else {
            holder.progress.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onClick(book) }
        holder.itemView.setOnLongClickListener {
            onLongClick(book)
            true
        }
    }

    override fun getItemCount() = books.size

    fun updateBooks(newBooks: List<BookMetadata>) {
        books = newBooks
        notifyDataSetChanged()
    }
}
