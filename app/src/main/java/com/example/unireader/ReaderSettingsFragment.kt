package com.example.unireader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.slider.Slider
import java.util.Locale

class ReaderSettingsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_reader_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity() as ReaderActivity

        // Font Size
        val sliderFontSize = view.findViewById<Slider>(R.id.sliderFontSize)
        val tvFontSizeValue = view.findViewById<TextView>(R.id.tvFontSizeValue)

        sliderFontSize.value = activity.settings.fontSize.toFloat()
        tvFontSizeValue.text = activity.settings.fontSize.toString()

        sliderFontSize.addOnChangeListener { _, value, fromUser ->
            val newSize = value.toInt()
            tvFontSizeValue.text = newSize.toString()
            if (fromUser) {
                activity.settings.fontSize = newSize
                activity.applyCurrentSettings()
                activity.settings.save(activity)
            }
        }

        // Paragraph Spacing
        val sliderSpacing = view.findViewById<Slider>(R.id.sliderParagraphSpacing)
        val tvSpacingValue = view.findViewById<TextView>(R.id.tvParagraphSpacingValue)

        sliderSpacing.value = activity.settings.paragraphSpacing
        tvSpacingValue.text = String.format(Locale.US, "%.1f", activity.settings.paragraphSpacing)

        sliderSpacing.addOnChangeListener { _, value, fromUser ->
            tvSpacingValue.text = String.format(Locale.US, "%.1f", value)
            if (fromUser) {
                activity.settings.paragraphSpacing = value
                activity.applyCurrentSettings()
                activity.settings.save(activity)
            }
        }

        // Line Height
        val sliderLineHeight = view.findViewById<Slider>(R.id.sliderLineHeight)
        val tvLineHeightValue = view.findViewById<TextView>(R.id.tvLineHeightValue)

        sliderLineHeight.value = activity.settings.lineHeight
        tvLineHeightValue.text = String.format(Locale.US, "%.1f", activity.settings.lineHeight)

        sliderLineHeight.addOnChangeListener { _, value, fromUser ->
            tvLineHeightValue.text = String.format(Locale.US, "%.1f", value)
            if (fromUser) {
                activity.settings.lineHeight = value
                activity.applyCurrentSettings()
                activity.settings.save(activity)
            }
        }

        // First Line Indent
        val sliderIndent = view.findViewById<Slider>(R.id.sliderFirstLineIndent)
        val tvIndentValue = view.findViewById<TextView>(R.id.tvFirstLineIndentValue)

        sliderIndent.value = activity.settings.firstLineIndent
        tvIndentValue.text = String.format(Locale.US, "%.1f", activity.settings.firstLineIndent)

        sliderIndent.addOnChangeListener { _, value, fromUser ->
            tvIndentValue.text = String.format(Locale.US, "%.1f", value)
            if (fromUser) {
                activity.settings.firstLineIndent = value
                activity.applyCurrentSettings()
                activity.settings.save(activity)
            }
        }

        // Column Gap
        val sliderColumnGap = view.findViewById<Slider>(R.id.sliderColumnGap)
        val tvColumnGapValue = view.findViewById<TextView>(R.id.tvColumnGapValue)

        sliderColumnGap.value = activity.settings.columnGap.toFloat()
        tvColumnGapValue.text = activity.settings.columnGap.toString()

        sliderColumnGap.addOnChangeListener { _, value, fromUser ->
            val newGap = value.toInt()
            tvColumnGapValue.text = newGap.toString()
            if (fromUser) {
                activity.settings.columnGap = newGap
                activity.applyCurrentSettings()
                activity.settings.save(activity)
            }
        }
    }
}
