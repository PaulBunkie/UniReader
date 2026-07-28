package com.example.unireader

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.unireader.databinding.FragmentFirstBinding
import android.content.Intent
import android.net.Uri
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var libraryProvider: LibraryProvider
    private lateinit var adapter: BooksAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        libraryProvider = LibraryProvider(requireContext())
        adapter = BooksAdapter(
            libraryProvider.getBooks(),
            onClick = { book ->
                val intent = Intent(requireContext(), ReaderActivity::class.java).apply {
                    putExtra("epub_uri", book.uri)
                }
                startActivity(intent)
            },
            onLongClick = { book ->
                showDeleteConfirmation(book)
            }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )
        binding.recyclerView.adapter = adapter
    }

    private fun showDeleteConfirmation(book: BookMetadata) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_book_title)
            .setMessage(getString(R.string.delete_book_message, book.title))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                libraryProvider.deleteBook(book)
                adapter.updateBooks(libraryProvider.getBooks())
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        adapter.updateBooks(libraryProvider.getBooks())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}