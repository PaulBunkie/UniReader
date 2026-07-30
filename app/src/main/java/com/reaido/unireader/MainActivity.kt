package com.reaido.unireader

import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import com.reaido.unireader.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            
            val epubBook = EpubParser(this).parse(it)
            if (epubBook != null) {
                showReadModeDialog(it, epubBook)
            }
        }
    }

    private fun showReadModeDialog(uri: Uri, book: EpubBook) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.select_reading_mode)
            .setMessage(R.string.enable_ai_translation)
            .setNeutralButton(R.string.cancel, null)
            .setNegativeButton(R.string.original_mode) { _, _ ->
                openBook(uri, book, isTranslation = false)
            }
            .setPositiveButton(R.string.translate_and_read) { _, _ ->
                openBook(uri, book, isTranslation = true)
            }
            .show()
    }

    private fun openBook(uri: Uri, epubBook: EpubBook, isTranslation: Boolean) {
        val libraryProvider = LibraryProvider(this)
        val sourceUriString = uri.toString()
        var localCopyUriString: String? = null
        
        if (isTranslation) {
            val localCopy = EpubModifier(this).createLocalCopy(uri)
            if (localCopy != null) {
                localCopyUriString = localCopy.toString()
            }
        }

        val finalTitle = if (!epubBook.title.isNullOrBlank()) {
            epubBook.title
        } else {
            getFileNameFromUri(uri) ?: getString(R.string.unknown_title)
        }

        libraryProvider.addBook(BookMetadata(
            uri = sourceUriString,
            title = finalTitle,
            author = epubBook.author ?: getString(R.string.unknown_author),
            isTranslationMode = isTranslation,
            localCopyUri = localCopyUriString,
            totalSpineItems = epubBook.spine.size
        ))

        val intent = Intent(this, ReaderActivity::class.java).apply {
            putExtra("epub_uri", sourceUriString)
        }
        startActivity(intent)
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path
            val cut = name?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                name = name?.substring(cut + 1)
            }
        }
        // Remove extension if possible
        if (name != null && name!!.contains(".")) {
            name = name!!.substringBeforeLast(".")
        }
        return name
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment_content_main) as NavHostFragment
        val navController = navHostFragment.navController
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.SecondFragment) {
                binding.fab.hide()
            } else {
                binding.fab.show()
            }
        }

        binding.fab.setOnClickListener {
            openDocumentLauncher.launch(arrayOf("application/epub+zip"))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> {
                findNavController(R.id.nav_host_fragment_content_main).navigate(R.id.SecondFragment)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}
