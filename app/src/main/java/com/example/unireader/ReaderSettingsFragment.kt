package com.example.unireader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.slider.Slider

class ReaderSettingsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_reader_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity() as ReaderActivity

        val slider = view.findViewById<Slider>(R.id.sliderFontSize)
        val tvValue = view.findViewById<TextView>(R.id.tvFontSizeValue)

        // Устанавливаем текущее значение
        slider.value = activity.settings.fontSize.toFloat()
        tvValue.text = activity.settings.fontSize.toString()

        slider.addOnChangeListener { _, value, fromUser ->
            val newSize = value.toInt()
            tvValue.text = newSize.toString()

            if (fromUser) {
                // Применяем шрифт сразу при движении ползунка
                activity.settings.fontSize = newSize
                activity.applyCurrentSettings()
                // Сохраняем в Preferences, но не при каждом пикселе — throttle не нужен,
                // apply() асинхронный и лёгкий
                activity.settings.save(activity)
            }
        }
    }
}