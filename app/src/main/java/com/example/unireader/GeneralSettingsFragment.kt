package com.example.unireader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

class GeneralSettingsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_general_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity() as ReaderActivity

        val switchFullscreen = view.findViewById<MaterialSwitch>(R.id.switchFullscreen)
        val switchPagedMode = view.findViewById<MaterialSwitch>(R.id.switchPagedMode)

        switchFullscreen.isChecked = activity.isFullscreenPref
        switchFullscreen.setOnCheckedChangeListener { _, isChecked ->
            activity.toggleFullscreenExternally(isChecked)
        }

        switchPagedMode.isChecked = activity.isPagedMode
        switchPagedMode.setOnCheckedChangeListener { _, isChecked ->
            activity.setReadingMode(isChecked)
        }

        // Margins
        setupMarginSlider(view, R.id.sliderPaddingLeft, R.id.tvPaddingLeftValue, activity.settings.paddingLeft) {
            activity.settings.paddingLeft = it
            activity.updateWebViewPadding()
            activity.settings.save(activity)
        }
        setupMarginSlider(view, R.id.sliderPaddingRight, R.id.tvPaddingRightValue, activity.settings.paddingRight) {
            activity.settings.paddingRight = it
            activity.updateWebViewPadding()
            activity.settings.save(activity)
        }
        setupMarginSlider(view, R.id.sliderPaddingTop, R.id.tvPaddingTopValue, activity.settings.paddingTop) {
            activity.settings.paddingTop = it
            activity.updateWebViewPadding()
            activity.settings.save(activity)
        }
        setupMarginSlider(view, R.id.sliderPaddingBottom, R.id.tvPaddingBottomValue, activity.settings.paddingBottom) {
            activity.settings.paddingBottom = it
            activity.updateWebViewPadding()
            activity.settings.save(activity)
        }
    }

    private fun setupMarginSlider(view: View, sliderId: Int, tvId: Int, initialValue: Int, onUpdate: (Int) -> Unit) {
        val slider = view.findViewById<Slider>(sliderId)
        val tv = view.findViewById<TextView>(tvId)
        slider.value = initialValue.toFloat()
        tv.text = initialValue.toString()
        slider.addOnChangeListener { _, value, fromUser ->
            val newVal = value.toInt()
            tv.text = newVal.toString()
            if (fromUser) onUpdate(newVal)
        }
    }
}