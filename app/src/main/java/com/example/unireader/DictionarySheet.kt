package com.example.unireader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class DictionarySheet(
    private val items: List<Highlight>,
    private val onDelete: (Highlight) -> Unit,
    private val onEdit: (Highlight) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_dictionary, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        view.findViewById<TextView>(R.id.tvDictTitle).text = getString(R.string.my_notes_count, items.size)
        
        val rv = view.findViewById<RecyclerView>(R.id.rvDict)
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = DictAdapter(items.toMutableList(), onDelete, onEdit)
    }

    fun refresh(newItems: List<Highlight>) {
        (view?.findViewById<RecyclerView>(R.id.rvDict)?.adapter as? DictAdapter)?.update(newItems)
        view?.findViewById<TextView>(R.id.tvDictTitle)?.text = getString(R.string.my_notes_count, newItems.size)
    }

    class DictAdapter(
        private var items: MutableList<Highlight>,
        private val onDelete: (Highlight) -> Unit,
        private val onEdit: (Highlight) -> Unit
    ) : RecyclerView.Adapter<DictAdapter.ViewHolder>() {

        fun update(newItems: List<Highlight>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val original: TextView = view.findViewById(R.id.tvDictOriginal)
            val translation: TextView = view.findViewById(R.id.tvDictTranslation)
            val btnDelete: ImageButton = view.findViewById(R.id.btnDictDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_dictionary, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.original.text = item.originalText
            holder.translation.text = item.replacementText?.substringAfter("]:") ?: ""
            
            holder.itemView.setOnClickListener {
                onEdit(item)
            }
            holder.btnDelete.setOnClickListener {
                onDelete(item)
            }
        }

        override fun getItemCount() = items.size
    }
}
