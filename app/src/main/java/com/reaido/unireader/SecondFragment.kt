package com.reaido.unireader

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import android.widget.ArrayAdapter
import com.reaido.unireader.databinding.FragmentSecondBinding

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val settings = ReaderSettings.load(requireContext())
        val languages = listOf(
            getString(R.string.language_auto),
            getString(R.string.language_russian),
            getString(R.string.language_english),
            getString(R.string.language_german),
            getString(R.string.language_french),
            getString(R.string.language_spanish),
            getString(R.string.language_italian),
            getString(R.string.language_japanese),
            getString(R.string.language_chinese)
        )

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, languages)
        binding.languageSelector.setAdapter(adapter)

        // Set current selection
        val currentLang = settings.targetLanguage
        val displayLang = if (currentLang == "Auto") getString(R.string.language_auto) else currentLang
        binding.languageSelector.setText(displayLang, false)

        binding.languageSelector.setOnItemClickListener { _, _, position, _ ->
            val selected = languages[position]
            settings.targetLanguage = if (selected == getString(R.string.language_auto)) "Auto" else selected
            settings.save(requireContext())
        }

        binding.buttonSecond.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}