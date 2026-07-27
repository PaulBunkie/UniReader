package com.example.unireader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.json.JSONObject

class ServerGlossarySheet(
    private val glossaryJson: String
) : BottomSheetDialogFragment() {

    data class GlossaryItem(val original: String, val translation: String, val meta: String?)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_server_glossary, container, false)
        
        val items = parseGlossary(glossaryJson)
        val rv = view.findViewById<RecyclerView>(R.id.rvGlossary)
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = GlossaryAdapter(items)
        
        return view
    }

    private fun parseGlossary(json: String): List<GlossaryItem> {
        val list = mutableListOf<GlossaryItem>()
        try {
            val root = JSONObject(json)
            val array = root.optJSONArray("glossary") ?: return emptyList()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val gender = obj.optString("gender", "").let { if (it == "null" || it == "none") "" else it }
                list.add(GlossaryItem(
                    original = obj.optString("original", ""),
                    translation = obj.optString("translation", ""),
                    meta = gender
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    class GlossaryAdapter(private val items: List<GlossaryItem>) : RecyclerView.Adapter<GlossaryAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvOriginal: TextView = view.findViewById(R.id.tvServerOriginal)
            val tvTranslation: TextView = view.findViewById(R.id.tvServerTranslation)
            val tvMeta: TextView = view.findViewById(R.id.tvServerMeta)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_server_glossary, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvOriginal.text = item.original
            holder.tvTranslation.text = item.translation
            holder.tvMeta.text = item.meta
        }

        override fun getItemCount() = items.size
    }
}
