package com.example.unireader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment

class ReaderSettingsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_reader_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity() as ReaderActivity

        val seekBar = view.findViewById<SeekBar>(R.id.seekBarFontSize)
        val tvValue = view.findViewById<TextView>(R.id.tvFontSizeValue)

        // SeekBar max=22, progress = fontSize - 10 (диапазон 10..32)
        seekBar.progress = activity.settings.fontSize - 10
        tvValue.text = activity.settings.fontSize.toString()

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val newSize = progress + 10
                tvValue.text = newSize.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val newSize = (seekBar?.progress ?: 8) + 10
                activity.settings.fontSize = newSize
                activity.settings.save(activity)
                activity.applyCurrentSettings()
            }
        })
    }
}