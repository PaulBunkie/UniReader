package com.example.unireader

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch

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
    }
}
