package com.example.unireader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch

class ColorsSettingsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_placeholder_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = requireActivity() as ReaderActivity

        val switchDarkMode = view.findViewById<MaterialSwitch>(R.id.switchDarkMode)
        switchDarkMode.isChecked = activity.settings.isDarkMode
        
        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            activity.settings.isDarkMode = isChecked
            activity.applyCurrentSettings()
            activity.settings.save(activity)
        }
    }
}
