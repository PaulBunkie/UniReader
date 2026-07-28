package com.example.unireader

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class TOCSheet(
    private val toc: List<TocItem>,
    private val currentHref: String?,
    private val isTranslated: (String) -> Boolean,
    private val onItemClick: (String) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_toc, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val rv = view.findViewById<RecyclerView>(R.id.rvToc)
        rv.layoutManager = LinearLayoutManager(context)
        val adapter = TocAdapter(toc, currentHref, isTranslated) { href ->
            onItemClick(href)
            dismiss()
        }
        rv.adapter = adapter
        
        if (adapter.selectedIndex != -1) {
            rv.scrollToPosition(adapter.selectedIndex)
        }
    }

    class TocAdapter(
        private val items: List<TocItem>,
        private val currentHref: String?,
        private val isTranslated: (String) -> Boolean,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<TocAdapter.ViewHolder>() {

        var selectedIndex: Int = -1

        init {
            if (currentHref != null) {
                selectedIndex = items.indexOfFirst { item ->
                    item.href == currentHref || currentHref.endsWith(item.href) || item.href.endsWith(currentHref) ||
                            item.href.substringBefore("#") == currentHref ||
                            currentHref.endsWith(item.href.substringBefore("#"))
                }
            }
        }

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
            
            val translated = isTranslated(item.href)
            
            if (position == selectedIndex) {
                holder.title.setTypeface(null, Typeface.BOLD)
                val typedValue = TypedValue()
                holder.itemView.context.theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, typedValue, true)
                holder.itemView.setBackgroundColor(typedValue.data)
            } else {
                holder.title.setTypeface(null, Typeface.NORMAL)
                holder.itemView.setBackgroundColor(Color.TRANSPARENT)
            }

            if (translated) {
                val isDarkMode = (holder.itemView.context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                val color = if (isDarkMode) Color.parseColor("#FBC02D") else Color.parseColor("#E65100")
                holder.title.setTextColor(color)
            } else {
                val typedValue = TypedValue()
                holder.itemView.context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
                holder.title.setTextColor(typedValue.data)
            }

            holder.itemView.setOnClickListener { onClick(item.href) }
        }

        override fun getItemCount() = items.size
    }
}
