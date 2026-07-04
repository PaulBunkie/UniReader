package com.example.unireader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class TOCSheet(private val toc: List<TocItem>, private val onItemClick: (String) -> Unit) : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_toc, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val rv = view.findViewById<RecyclerView>(R.id.rvToc)
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = TocAdapter(toc) { href ->
            onItemClick(href)
            dismiss()
        }
    }

    class TocAdapter(private val items: List<TocItem>, private val onClick: (String) -> Unit) :
        RecyclerView.Adapter<TocAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.tvTocTitle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_toc, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.title.text = item.title
            holder.itemView.setOnClickListener { onClick(item.href) }
        }

        override fun getItemCount() = items.size
    }
}
