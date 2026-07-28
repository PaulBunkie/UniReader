package com.example.unireader

import android.annotation.SuppressLint
import android.graphics.Color
import android.util.Log
import android.content.Intent
import android.net.Uri
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import org.json.JSONArray
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

class ReaderActivity : AppCompatActivity() {

    lateinit var webView: WebView
    private lateinit var webViewContainer: View
    private lateinit var appBarLayout: AppBarLayout
    private lateinit var bottomPanel: View
    private var epubBook: EpubBook? = null
    private var currentSpineIndex = 0
    var isPagedMode = true
    var isFullscreenPref = false 
    var isUiOverlayVisible = true
    
    private var shouldJumpToLastPage = false
    private var chapterLoader: ChapterLoader? = null
    
    private var isChapterLoading = false
    private var lastAppendedIndex = -1
    private var firstPrependedIndex = Int.MAX_VALUE
    
    private var pendingPageIndex = -1
    private var pendingAnchor: String? = null
    private var isJumpingToChapter = false
    private var chaptersToLoad = 0
    private var isSwipeBlocked = false
    
    private var lastKnownPosition: Triple<Int, Int, Int>? = null
    private val savePositionRunnable = Runnable { saveReadingPosition() }
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    
    private var pendingReloadIndex = -1
    private var pendingReloadJumpToLast = false
    private val reloadChapterRunnable = Runnable {
        if (pendingReloadIndex != -1) {
            loadSpineItem(pendingReloadIndex, pendingReloadJumpToLast)
            pendingReloadIndex = -1
        }
    }

    lateinit var settings: ReaderSettings
    private lateinit var gestureDetector: GestureDetector
    private lateinit var highlightDb: HighlightDatabase
    private var isAdjustingBrightness = false
    
    private lateinit var fixOverlay: View
    private lateinit var fixLoading: ProgressBar
    private lateinit var tvFixResult: TextView
    private lateinit var tvFixModel: TextView
    private lateinit var fixActions: View
    private lateinit var btnFixRefresh: View
    private lateinit var btnFixAccept: View
    private lateinit var fixService: FixService
    private var lastFixRequestJson: String? = null
    private var lastImprovedText: String? = null
    
    private var lastFailedSpineIndex: Int = -1

    private var translationManager: TranslationManager? = null
    private var currentBookMetadata: BookMetadata? = null
    private lateinit var translationStatusContainer: View
    private lateinit var initialTranslationOverlay: View
    private lateinit var apiLogOverlay: View
    private lateinit var tvApiLog: TextView

    private val saveDocumentLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/epub+zip")) { uri ->
        uri?.let { performSave(it) }
    }

    private val hideBrightnessRunnable = Runnable { 
        findViewById<View>(R.id.tvBrightnessHint)?.visibility = View.GONE 
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        settings = ReaderSettings.load(this)
        highlightDb = HighlightDatabase(this)
        
        super.onCreate(savedInstanceState)
        
        // APPLY SAVED BRIGHTNESS
        if (settings.brightness >= 0f) {
            val lp = window.attributes
            lp.screenBrightness = settings.brightness
            window.attributes = lp
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        
        fixService = FixService(this, getApiBaseUrl())
        
        setContentView(R.layout.activity_reader)

        fixOverlay = findViewById(R.id.fixOverlay)
        fixLoading = findViewById(R.id.fixLoading)
        tvFixResult = findViewById(R.id.tvFixResult)
        tvFixModel = findViewById(R.id.tvFixModel)
        fixActions = findViewById(R.id.fixActions)
        btnFixRefresh = findViewById(R.id.btnFixRefresh)
        btnFixAccept = findViewById(R.id.btnFixAccept)

        findViewById<View>(R.id.btnOverlayClose).setOnClickListener {
            fixOverlay.visibility = View.GONE
        }
        
        btnFixRefresh.setOnClickListener {
            lastFixRequestJson?.let { showFixOverlay(it) }
        }
        
        btnFixAccept.setOnClickListener {
            acceptImprovement()
        }

        translationStatusContainer = findViewById(R.id.translationStatusContainer)
        initialTranslationOverlay = findViewById(R.id.initialTranslationOverlay)
        apiLogOverlay = findViewById(R.id.apiLogOverlay)
        tvApiLog = findViewById(R.id.tvApiLog)
        
        findViewById<View>(R.id.btnProcessingRetry).setOnClickListener {
            if (lastFailedSpineIndex != -1) {
                translationManager?.onChapterVisible(lastFailedSpineIndex)
                lastFailedSpineIndex = -1
                updateUiState()
            }
        }
        
        findViewById<View>(R.id.btnInitialRetry).setOnClickListener {
            if (lastFailedSpineIndex != -1) {
                translationManager?.onChapterVisible(lastFailedSpineIndex)
                lastFailedSpineIndex = -1
                
                // Hide error state on big overlay
                findViewById<ProgressBar>(R.id.pbInitial)?.visibility = View.VISIBLE
                findViewById<View>(R.id.btnInitialRetry).visibility = View.GONE
                findViewById<TextView>(R.id.tvInitialStatus)?.text = getString(R.string.retrying_translation)
            }
        }

        DebugLogger.onLogUpdate = { fullLog ->
            runOnUiThread {
                tvApiLog.text = fullLog
                // Auto-scroll to bottom
                apiLogOverlay.post {
                    (apiLogOverlay as ScrollView).fullScroll(View.FOCUS_DOWN)
                }
            }
        }

        appBarLayout = findViewById(R.id.appBarLayout)
        bottomPanel = findViewById(R.id.bottomPanel)
        webView = findViewById(R.id.webView)
        webViewContainer = findViewById(R.id.webViewContainer)
        
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val toolbarContent = layoutInflater.inflate(R.layout.reader_toolbar_content, toolbar, false)
        toolbar.addView(toolbarContent)

        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, 0)
            insets
        }

        setupWebView()
        setupGestures()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })

        // STATE RESTORATION
        val uriString = savedInstanceState?.getString("epub_uri") ?: intent.getStringExtra("epub_uri")
        currentSpineIndex = savedInstanceState?.getInt("spine_index", 0) ?: 0
        pendingPageIndex = savedInstanceState?.getInt("page_index", -1) ?: -1
        pendingAnchor = savedInstanceState?.getString("anchor")
        
        // Load modes from settings
        isPagedMode = savedInstanceState?.getBoolean("paged_mode", settings.isPagedMode) ?: settings.isPagedMode
        isFullscreenPref = savedInstanceState?.getBoolean("fullscreen", settings.isFullscreen) ?: settings.isFullscreen
        isUiOverlayVisible = savedInstanceState?.getBoolean("ui_visible", !isFullscreenPref) ?: !isFullscreenPref

        if (uriString != null) {
            val uri = uriString.toUri()
            
            // LOAD METADATA
            val libraryProvider = LibraryProvider(this)
            val metadata = libraryProvider.getBooks().find { it.uri == uriString }
            currentBookMetadata = metadata

            // In translation mode, we should open the LOCAL COPY for reading/writing
            val finalUri = if (metadata?.isTranslationMode == true && metadata.localCopyUri != null) {
                metadata.localCopyUri!!.toUri()
            } else {
                uri
            }

            epubBook = EpubParser(this).parse(finalUri)
            
            epubBook?.let { book ->
                chapterLoader = ChapterLoader(this, book)
                updateBookTitles()
                
                // If it's a fresh open (no pending index from saveState), check metadata
                if (savedInstanceState == null) {
                    metadata?.let { savedBook ->
                        currentSpineIndex = savedBook.lastSpineIndex
                        pendingPageIndex = savedBook.lastPageIndex
                    }
                }
                
                if (metadata?.isTranslationMode == true) {
                    initTranslation(book, uriString)
                    
                    val isFirstOpen = metadata.lastSpineIndex == 0 && metadata.lastPageIndex == -1
                    val isCurrentReady = translationManager?.isChapterTranslated(currentSpineIndex) == true
                    
                    if (isFirstOpen && !isCurrentReady) {
                        initialTranslationOverlay.visibility = View.VISIBLE
                    } else {
                        initialTranslationOverlay.visibility = View.GONE
                    }
                } else {
                    loadSpineItem(currentSpineIndex)
                }
            }
        }

        updateUiState()
        updateWebViewPadding()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        intent.getStringExtra("epub_uri")?.let { outState.putString("epub_uri", it) }
        
        outState.putInt("spine_index", currentSpineIndex)
        outState.putInt("page_index", currentBookMetadata?.lastPageIndex ?: pendingPageIndex)
        outState.putString("anchor", pendingAnchor)
        
        outState.putBoolean("fullscreen", isFullscreenPref)
        outState.putBoolean("ui_visible", isUiOverlayVisible)
        outState.putBoolean("paged_mode", isPagedMode)
    }

    private fun getApiBaseUrl(): String {
        return if (settings.isProdApi) "http://136.109.52.87:8080/api" else "http://10.0.2.2:8080/api"
    }

    private fun initTranslation(book: EpubBook, originalUri: String) {
        translationManager = TranslationManager(this, book, originalUri, getApiBaseUrl())
        
        // Warm Start: Hide overlay if current chapter is already ready
        if (translationManager?.isChapterTranslated(currentSpineIndex) == true) {
            initialTranslationOverlay.visibility = View.GONE
            loadSpineItem(currentSpineIndex)
        }

        translationManager?.onTOCReady = { translatedToc ->
            epubBook = epubBook?.copy(toc = translatedToc)
            runOnUiThread { updateBookTitles() }
        }

        translationManager?.onActiveTasksChanged = {
            runOnUiThread {
                val tasks = translationManager?.getActiveTasks() ?: emptySet()
                val queuedCount = translationManager?.getQueuedCount() ?: 0
                
                val tvProgress = findViewById<TextView>(R.id.tvProgressPlaceholder)
                
                if (tasks.contains(currentSpineIndex)) {
                    translationStatusContainer.visibility = View.VISIBLE
                    tvProgress?.visibility = View.GONE
                    findViewById<ProgressBar>(R.id.pbProcessing)?.visibility = View.VISIBLE
                    findViewById<View>(R.id.btnProcessingRetry).visibility = View.GONE
                    findViewById<TextView>(R.id.tvProcessingStatus)?.apply {
                        text = getString(R.string.translating_current_chapter)
                        setTextColor(if (settings.isDarkMode) Color.WHITE else Color.BLACK)
                    }
                } else if (lastFailedSpineIndex == currentSpineIndex) {
                    translationStatusContainer.visibility = View.VISIBLE
                    tvProgress?.visibility = View.GONE
                    findViewById<ProgressBar>(R.id.pbProcessing)?.visibility = View.GONE
                    findViewById<View>(R.id.btnProcessingRetry).visibility = View.VISIBLE
                    findViewById<TextView>(R.id.tvProcessingStatus)?.apply {
                        text = getString(R.string.translation_failed)
                        setTextColor(Color.RED)
                    }
                } else if (queuedCount > 0) {
                    translationStatusContainer.visibility = View.VISIBLE
                    tvProgress?.visibility = View.GONE
                    findViewById<ProgressBar>(R.id.pbProcessing)?.visibility = View.VISIBLE
                    findViewById<View>(R.id.btnProcessingRetry).visibility = View.GONE
                    findViewById<TextView>(R.id.tvProcessingStatus)?.apply {
                        text = getString(R.string.prefetching_queue, queuedCount)
                        setTextColor(if (settings.isDarkMode) Color.WHITE else Color.BLACK)
                    }
                } else {
                    translationStatusContainer.visibility = View.GONE
                    tvProgress?.visibility = View.VISIBLE
                }
            }
        }

        translationManager?.onTaskError = { index, error ->
            runOnUiThread {
                DebugLogger.log("MANAGER", "Error on ch $index: $error")
                if (index == currentSpineIndex) {
                    lastFailedSpineIndex = index
                    
                    // Update big overlay if visible
                    if (initialTranslationOverlay.visibility == View.VISIBLE) {
                        findViewById<ProgressBar>(R.id.pbInitial)?.visibility = View.GONE
                        findViewById<View>(R.id.btnInitialRetry).visibility = View.VISIBLE
                        findViewById<TextView>(R.id.tvInitialStatus)?.text = getString(R.string.error_prefix, error)
                    }
                    
                    translationManager?.onActiveTasksChanged?.invoke()
                }
            }
        }
        translationManager?.onChapterReady = { index ->
            runOnUiThread {
                if (index == currentSpineIndex) {
                    initialTranslationOverlay.visibility = View.GONE
                    loadSpineItem(currentSpineIndex)
                }
            }
        }

        translationManager?.startInitialTranslation(currentSpineIndex)
    }

    private fun updateBookTitles() {
        val book = epubBook ?: return
        findViewById<TextView>(R.id.tvBookTitle)?.text = book.title ?: getString(R.string.unknown_book)
        updateChapterTitle()
    }

    private fun updateChapterTitle() {
        val book = epubBook ?: return
        if (currentSpineIndex < book.spine.size) {
            val fullHref = getSpineItemFullPath(currentSpineIndex)
            val tocTitle = book.toc.find { 
                it.href == fullHref || it.href.substringBefore("#") == fullHref
            }?.title
            findViewById<TextView>(R.id.tvChapterTitle)?.text = tocTitle ?: book.spine[currentSpineIndex].href
        }
    }

    private fun getSpineItemFullPath(index: Int): String {
        val book = epubBook ?: return ""
        if (index < 0 || index >= book.spine.size) return ""
        val item = book.spine[index]
        val opfDir = File(book.opfPath).parent ?: ""
        return if (opfDir.isEmpty()) item.href else "$opfDir/${item.href}".replace("//", "/")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_reader, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            R.id.action_toc -> {
                epubBook?.let { book ->
                    val currentHref = if (currentSpineIndex < book.spine.size) getSpineItemFullPath(currentSpineIndex) else null
                    TOCSheet(book.toc, currentHref, 
                        isTranslated = { href ->
                            val cleanHref = href.substringBefore("#")
                            val opfDir = File(book.opfPath).parent ?: ""
                            val idx = book.spine.indexOfFirst { 
                                val itemFull = if (opfDir.isEmpty()) it.href else "$opfDir/${it.href}".replace("//", "/")
                                itemFull == cleanHref
                            }
                            if (idx != -1) translationManager?.isChapterTranslated(idx) == true else false
                        }
                    ) { href ->
                        handleInternalLink("epub://$href")
                    }.show(supportFragmentManager, "toc")
                }
                true
            }
            R.id.action_settings -> {
                val anchor = findViewById<View>(R.id.action_settings) ?: appBarLayout
                val popup = androidx.appcompat.widget.PopupMenu(this, anchor)
                popup.menu.add(getString(R.string.menu_appearance)).setOnMenuItemClickListener {
                    ReaderSettingsSheet().show(supportFragmentManager, "settings")
                    true
                }
                if (currentBookMetadata?.isTranslationMode == true) {
                    popup.menu.add(getString(R.string.menu_retranslate)).setOnMenuItemClickListener {
                        translationManager?.forceTranslate(currentSpineIndex)
                        true
                    }
                }
                popup.menu.add(getString(R.string.menu_save_updates)).setOnMenuItemClickListener {
                    val book = epubBook ?: return@setOnMenuItemClickListener true
                    val fileName = book.uri.toString().substringAfterLast("/").substringBeforeLast(".") + "_improved.epub"
                    saveDocumentLauncher.launch(fileName)
                    true
                }
                if (currentBookMetadata?.isTranslationMode == true) {
                    popup.menu.add(getString(R.string.my_notes)).setOnMenuItemClickListener {
                        val bookUri = epubBook?.uri?.toString() ?: return@setOnMenuItemClickListener true
                        val dictEntries = highlightDb.getDictEntries(bookUri)
                        DictionarySheet(dictEntries, 
                            onDelete = { highlight ->
                                highlightDb.deleteHighlight(highlight.id)
                                webView.evaluateJavascript("applyHighlights('${getHighlightsJson(highlight.spineIndex)}')", null)
                            },
                            onEdit = { highlight ->
                                showEditCorrectionDialog(highlight)
                            }
                        ).show(supportFragmentManager, "dictionary")
                        true
                    }
                    popup.menu.add(getString(R.string.glossary)).setOnMenuItemClickListener {
                        val bookUri = currentBookMetadata?.uri ?: return@setOnMenuItemClickListener true
                        val latestMetadata = LibraryProvider(this).getBooks().find { it.uri == bookUri }
                        val glossary = latestMetadata?.serverGlossary ?: "{}"
                        ServerGlossarySheet(
                            glossaryJson = glossary,
                            onEdit = { item -> showEditGlossaryDialog(item) },
                            onAdd = { showAddGlossaryEntryDialog() }
                        ).show(supportFragmentManager, "server_glossary")
                        true
                    }
                    popup.menu.add(getString(R.string.menu_api_log)).setOnMenuItemClickListener {
                        apiLogOverlay.visibility = if (apiLogOverlay.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                        true
                    }
                    val apiLabel = if (settings.isProdApi) getString(R.string.menu_switch_to_local) else getString(R.string.menu_switch_to_prod)
                    popup.menu.add(apiLabel).setOnMenuItemClickListener {
                        settings.isProdApi = !settings.isProdApi
                        settings.save(this)
                        
                        // Re-init services
                        fixService = FixService(this, getApiBaseUrl())
                        epubBook?.let { book ->
                            currentBookMetadata?.uri?.let { uri ->
                                if (currentBookMetadata?.isTranslationMode == true) {
                                    initTranslation(book, uri)
                                }
                            }
                        }
                        
                        val mode = if (settings.isProdApi) "PROD" else "LOCAL"
                    Toast.makeText(this, getString(R.string.api_switched_to, mode), Toast.LENGTH_SHORT).show()
                        true
                    }
                }
                popup.show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun toggleFullscreenExternally(enabled: Boolean) {
        if (isPagedMode) {
            captureCurrentPosition { pos ->
                isFullscreenPref = enabled
                isUiOverlayVisible = !isFullscreenPref
                settings.isFullscreen = enabled
                settings.save(this)
                updateUiState()
                
                webView.postDelayed({
                    webView.evaluateJavascript("if (typeof scrollToPosition === 'function') scrollToPosition(${pos.first}, ${pos.second}, ${pos.third});", null)
                }, 300)
            }
        } else {
            isFullscreenPref = enabled
            isUiOverlayVisible = !isFullscreenPref
            settings.isFullscreen = enabled
            settings.save(this)
            updateUiState()
        }
    }

    fun setReadingMode(paged: Boolean) {
        if (isPagedMode == paged) return
        
        captureCurrentPosition { pos ->
            isPagedMode = paged
            settings.isPagedMode = paged
            settings.save(this)
            
            if (pos.first >= 0) {
                currentSpineIndex = pos.first
                // Note: element indexing is temporarily disabled for mode switching
            }
            
            if (!isPagedMode) initSeamlessScroll() else initPagedView()
            updateUiState()
        }
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(savePositionRunnable)
        mainHandler.removeCallbacks(reloadChapterRunnable)
        saveReadingPosition()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (isPagedMode) {
            captureCurrentPosition { pos ->
                // Wait for layout to settle after orientation change
                webView.postDelayed({
                    updateUiState() // Ensure margins are correct
                    webView.evaluateJavascript("if (typeof scrollToPosition === 'function') scrollToPosition(${pos.first}, ${pos.second}, ${pos.third});", null)
                }, 500)
            }
        }
    }

    private fun saveReadingPosition() {
        val uri = intent.getStringExtra("epub_uri") ?: return
        val metadata = currentBookMetadata ?: return
        
        val libraryProvider = LibraryProvider(this)
        libraryProvider.updateBookProgress(
            uri,
            currentSpineIndex,
            metadata.lastPageIndex
        )
    }

    private fun captureCurrentPosition(onCaptured: (Triple<Int, Int, Int>) -> Unit) {
        val js = """
            (function() {
                var pw = window.innerWidth;
                var isPaged = document.body.getAttribute('data-mode') === 'paged';
                var found = document.elementFromPoint(isPaged ? 40 : pw / 2, 150);
                var c = -1, idx = -1, p = -1;

                if (found) {
                    var target = found.closest('[data-idx]');
                    if (target) {
                        var section = target.closest('section');
                        c = section ? parseInt(section.getAttribute('data-index')) : -1;
                        idx = parseInt(target.getAttribute('data-idx'));
                        
                        if (isPaged) {
                            p = Math.floor((window.pageXOffset - section.offsetLeft + (pw / 2)) / pw);
                        }
                    }
                }
                return JSON.stringify({c: c, idx: idx, p: p});
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) {
            try {
                val json = org.json.JSONObject(it.trim('"').replace("\\\"", "\""))
                val c = json.optInt("c", -1)
                val idx = json.optInt("idx", -1)
                val pg = json.optInt("p", -1)
                
                if (c >= 0) {
                    lastKnownPosition = Triple(c, idx, -1) // Use Triple(chapter, elementIdx, -1)
                    currentSpineIndex = c 
                    currentBookMetadata?.lastPageIndex = pg
                }
                onCaptured(lastKnownPosition ?: Triple(-1, -1, -1))
            } catch (_: Exception) {
                onCaptured(Triple(-1, -1, -1))
            }
        }
    }

    fun updateWebViewPadding() {
        val density = resources.displayMetrics.density
        val pl = (settings.paddingLeft * density).toInt()
        val pr = (settings.paddingRight * density).toInt()
        // Top margin ONLY applies in fullscreen mode; always 0 in normal mode
        val pt = if (isFullscreenPref) (settings.paddingTop * density).toInt() else 0
        val pb = (settings.paddingBottom * density).toInt()
        
        webViewContainer.setPadding(pl, pt, pr, pb)
        applyCurrentSettings()
    }

    fun applyCurrentSettings() {
        val isDarkMode = settings.isDarkMode
        val bgColor = if (isDarkMode) "#000000" else "#FFFFFF"
        val textColor = if (isDarkMode) "#E0E0E0" else "#000000"
        
        val menuBg = if (isDarkMode) "#1976D2" else "#2196F3"
        val tooltipBg = if (isDarkMode) "#2C2C2C" else "#FFFFFF"
        val tooltipText = if (isDarkMode) "#E0E0E0" else "#333333"
        val tooltipBorder = if (isDarkMode) "#444444" else "#CCCCCC"
        
        webView.setBackgroundColor(if (isDarkMode) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        findViewById<CoordinatorLayout>(R.id.readerRoot)?.setBackgroundColor(if (isDarkMode) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())

        val lh = settings.lineHeight
        val fs = settings.fontSize
        val lhPx = fs * lh

        val commonCss = """
            body { 
                line-height: $lh; 
                font-family: sans-serif; 
                font-size: ${fs}px;
                text-align: justify;
                hyphens: auto;
                word-wrap: break-word;
                box-sizing: border-box;
                margin: 0 !important;
                padding: 0 !important;
                background-color: $bgColor !important;
                color: $textColor !important;
            }
            p, div, h1, h2, h3, h4, h5, h6, li { 
                text-align: justify; 
                hyphens: auto; 
                box-sizing: border-box;
                color: $textColor !important;
            }
            p { text-indent: ${settings.firstLineIndent}em; }
            * { max-width: 100% !important; box-sizing: border-box !important; }
            img { display: block; max-width: 100% !important; max-height: 80vh !important; margin: 10px auto !important; object-fit: contain; }
            
            #uni-selection-menu {
                position: fixed; background: $menuBg !important; border: none !important;
                border-radius: 20px !important; display: none; z-index: 2147483647 !important;
                box-shadow: 0 4px 12px rgba(0,0,0,0.4) !important; 
                flex-direction: row; align-items: stretch; justify-content: center;
                overflow: hidden; white-space: nowrap; padding: 0 !important; margin: 0 !important;
                line-height: normal !important; min-width: max-content !important;
                box-sizing: border-box !important;
            }
            .uni-menu-btn {
                background: transparent !important; border: none !important; color: white !important;
                padding: 10px 16px !important; font-size: 14px !important; font-weight: bold !important; 
                cursor: pointer !important; flex: 0 0 auto !important; margin: 0 !important;
                height: auto !important; line-height: 1.2 !important; outline: none !important;
                box-shadow: none !important; -webkit-appearance: none !important;
                text-transform: none !important; box-sizing: border-box !important;
                display: flex !important; align-items: center !important; justify-content: center !important;
            }
            .uni-menu-btn:not(:last-child) { border-right: 1px solid rgba(255,255,255,0.3) !important; }
            
            #uni-fix-tooltip {
                position: fixed; background: $tooltipBg !important; color: $tooltipText !important;
                border: 1px solid $tooltipBorder !important; border-radius: 8px !important;
                padding: 12px !important; font-size: 14px !important; z-index: 2147483647 !important;
                box-shadow: 0 4px 12px rgba(0,0,0,0.3) !important; max-width: 80% !important; line-height: 1.4 !important; display: none;
            }
            #uni-fix-tooltip b { color: ${if (isDarkMode) "#81C784" else "#2E7D32"}; display: block; margin-bottom: 4px; }
            
            mark.uni-highlight {
                background-color: ${if (isDarkMode) "#f57f17" else "#ffeb3b"} !important;
                color: ${if (isDarkMode) "#ffffff" else "#000000"} !important;
                border-radius: 2px;
            }
            mark.uni-fix {
                background-color: ${if (isDarkMode) "#2E7D32" else "#C8E6C9"} !important;
                color: ${if (isDarkMode) "#E8F5E9" else "#1B5E20"} !important;
                border-radius: 2px;
            }
            mark.uni-dict {
                border-bottom: 2px dashed #FF9800 !important;
                background-color: ${if (isDarkMode) "rgba(255, 152, 0, 0.2)" else "rgba(255, 152, 0, 0.15)"} !important;
                color: inherit !important;
            }
        """.trimIndent()

        val modeCss = if (isPagedMode) {
            val halfGapPx = (settings.columnGap * resources.displayMetrics.density).toInt() / 2
            """
            html { 
                margin: 0; padding: 0; height: 100vh; width: 100vw; 
                overflow-x: auto; overflow-y: hidden;
                scroll-behavior: auto;
                scroll-snap-type: x mandatory;
                -webkit-overflow-scrolling: touch;
            }
            body { 
                height: 100vh; width: 100vw;
                display: block; position: relative;
                -webkit-column-width: 100vw !important; -webkit-column-gap: 0 !important;
                column-width: 100vw !important; column-gap: 0 !important;
                -webkit-column-fill: auto; column-fill: auto;
                padding-bottom: calc(100vh % ${lhPx}px) !important;
            }
            section {
                display: block;
                break-before: column;
                -webkit-column-break-before: column;
            }
            p, div, h1, h2, h3, h4, h5, h6, li { 
                margin: 0 !important;
                padding: 0 ${halfGapPx}px ${settings.paragraphSpacing * lh}em ${halfGapPx}px !important; 
            }
            #snap-ribbon {
                position: absolute; top: 0; left: 0;
                display: flex; height: 1px; pointer-events: none;
                padding: 0 !important;
            }
            .snap-point {
                width: 100vw; height: 1px; flex-shrink: 0;
                scroll-snap-align: start; scroll-snap-stop: always;
                padding: 0 !important;
            }
            """.trimIndent()
        } else {
            val halfGapPx = (settings.columnGap * resources.displayMetrics.density).toInt() / 2
            """
            html, body { overflow-x: hidden !important; overflow-y: auto !important; height: auto !important; }
            body { 
                visibility: visible;
                display: block !important;
            } 
            p, div, h1, h2, h3, h4, h5, h6, li { 
                margin: 0 !important; 
                margin-bottom: ${settings.paragraphSpacing * lh}em !important; 
                padding-left: ${halfGapPx}px !important;
                padding-right: ${halfGapPx}px !important;
            }
            """.trimIndent()
        }

        val finalCss = (commonCss + modeCss).replace("\n", " ")
        val themeAttr = if (isDarkMode) "dark" else "light"
        webView.evaluateJavascript("""
            (function() {
                var style = document.getElementById('reader-style') || document.createElement('style');
                style.id = 'reader-style';
                style.textContent = '$finalCss';
                if (!style.parentNode) document.getElementsByTagName('head')[0].appendChild(style);
                document.documentElement.setAttribute('data-theme', '$themeAttr');
            })();
        """.trimIndent(), null)
    }

    fun updateUiState() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        val params = webViewContainer.layoutParams as CoordinatorLayout.LayoutParams

        if (isFullscreenPref) {
            params.behavior = null 
            params.topMargin = 0
            params.bottomMargin = 0
            if (!isUiOverlayVisible) {
                window.decorView.post {
                    windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                    windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
                appBarLayout.visibility = View.GONE
                bottomPanel.visibility = View.GONE
            } else {
                window.decorView.post {
                    windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                }
                appBarLayout.visibility = View.VISIBLE
                bottomPanel.visibility = View.VISIBLE
                appBarLayout.bringToFront()
                bottomPanel.bringToFront()
            }
        } else {
            window.decorView.post {
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }
            isUiOverlayVisible = true
            appBarLayout.visibility = View.VISIBLE
            bottomPanel.visibility = View.VISIBLE
            
            // ENSURE WE HAVE ACTUAL MEASUREMENTS
            appBarLayout.post {
                val topH = appBarLayout.height
                val botH = bottomPanel.height
                webViewContainer.updateLayoutParams<CoordinatorLayout.LayoutParams> { 
                    topMargin = topH
                    bottomMargin = botH
                }
            }
        }
        
        webViewContainer.layoutParams = params
        updateWebViewPadding()
        applyCurrentSettings()
    }

    private fun setupGestures() {
        gestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(event: MotionEvent): Boolean {
                isAdjustingBrightness = false
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val width = webView.width
                val x = e.x

                val hr = webView.hitTestResult
                if ((hr.type == WebView.HitTestResult.SRC_ANCHOR_TYPE) || 
                    (hr.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) ||
                    (hr.type == WebView.HitTestResult.IMAGE_TYPE)) {
                    return false
                }

                when {
                    (x < width * 0.1) -> if (isPagedMode) prevPage()
                    (x > width * 0.9) -> if (isPagedMode) nextPage()
                    else -> {
                        if (isFullscreenPref) {
                            if (isPagedMode) {
                                captureCurrentPosition { pos ->
                                    isUiOverlayVisible = !isUiOverlayVisible
                                    updateUiState()
                                    webView.postDelayed({
                                        webView.evaluateJavascript("if (typeof scrollToPosition === 'function') scrollToPosition(${pos.first}, ${pos.second}, ${pos.third});", null)
                                    }, 300)
                                }
                            } else {
                                isUiOverlayVisible = !isUiOverlayVisible
                                updateUiState()
                            }
                        }
                    }
                }
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                if (e1 == null) return false
                val width = webView.width
                
                if (e1.x < width * 0.08f) {
                    if (!isAdjustingBrightness) {
                        isAdjustingBrightness = true
                        val cancelEvent = MotionEvent.obtain(e2.downTime, e2.eventTime, MotionEvent.ACTION_CANCEL, e2.x, e2.y, 0)
                        webView.dispatchTouchEvent(cancelEvent)
                        cancelEvent.recycle()
                    }
                    val lp = window.attributes
                    var brightness = lp.screenBrightness
                    
                    if (brightness < 0) {
                        brightness = try {
                            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
                        } catch (_: Exception) {
                            0.5f
                        }
                    }
                    
                    val delta = distanceY / (webView.height * 1.5f)
                    brightness = (brightness + delta).coerceIn(0.01f, 1.0f)
                    
                    lp.screenBrightness = brightness
                    window.attributes = lp
                    
                    settings.brightness = brightness
                    settings.save(this@ReaderActivity)
                    
                    showBrightnessFeedback(brightness)
                    return true
                }
                return false
            }
        },)
    }

    private fun showBrightnessFeedback(value: Float) {
        val hint = findViewById<TextView>(R.id.tvBrightnessHint) ?: return
        hint.removeCallbacks(hideBrightnessRunnable)
        hint.text = getString(R.string.brightness_format, (value * 100).toInt())
        hint.visibility = View.VISIBLE
        hint.postDelayed(hideBrightnessRunnable, 1000)
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        webView.settings.domStorageEnabled = true

        // Suppress the system selection menu
        val noMenuCallback = object : android.view.ActionMode.Callback {
            override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean = false
            override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean = false
            override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?): Boolean = false
            override fun onDestroyActionMode(mode: android.view.ActionMode?) {}
        }
        
        try {
            val setSelection = View::class.java.getMethod("setCustomSelectionActionModeCallback", android.view.ActionMode.Callback::class.java)
            setSelection.invoke(webView, noMenuCallback)
            val setInsertion = View::class.java.getMethod("setCustomInsertionActionModeCallback", android.view.ActionMode.Callback::class.java)
            setInsertion.invoke(webView, noMenuCallback)
        } catch (e: Exception) {
            Log.e("Reader", "Could not suppress system menu", e)
        }
        
        webView.addJavascriptInterface(object {
            @Keep
            @JavascriptInterface
            @Suppress("unused")
            fun onLinkClicked(url: String) {
                runOnUiThread {
                    handleInternalLink(url)
                }
            }
            @Keep
            @JavascriptInterface
            @Suppress("unused")
            fun onReachedBottom() {
                runOnUiThread { 
                    Log.d("Reader", "onReachedBottom: lastAppended=$lastAppendedIndex, loading index=${lastAppendedIndex + 1}")
                    if (!isChapterLoading && !isSwipeBlocked) {
                        isSwipeBlocked = true
                        loadAndAppendChapter(lastAppendedIndex + 1, stickToCurrent = true)
                        webView.postDelayed({ isSwipeBlocked = false }, 500)
                    }
                }
            }
            @Keep
            @JavascriptInterface
            @Suppress("unused")
            fun onReachedTop() {
                runOnUiThread {
                    Log.d("Reader", "onReachedTop: firstPrepended=$firstPrependedIndex, loading index=${firstPrependedIndex - 1}")
                    if (!isChapterLoading && !isSwipeBlocked) {
                        isSwipeBlocked = true
                        loadAndPrependChapter(firstPrependedIndex - 1, stayOnCurrent = true)
                        webView.postDelayed({ isSwipeBlocked = false }, 500)
                    }
                }
            }
            @Keep
            @JavascriptInterface
            @Suppress("unused")
            fun onChapterEntered(index: Int) {
                runOnUiThread {
                    if (isJumpingToChapter || isSwipeBlocked) return@runOnUiThread
                    
                    if (currentSpineIndex != index) {
                        if (isPagedMode && currentBookMetadata?.isTranslationMode == true) {
                            // Paged mode: reload to pick up translations.
                            mainHandler.removeCallbacks(reloadChapterRunnable)
                            pendingReloadJumpToLast = index < currentSpineIndex
                            pendingReloadIndex = index
                            currentSpineIndex = index
                            updateChapterTitle()
                            mainHandler.postDelayed(reloadChapterRunnable, 500)
                            return@runOnUiThread
                        }

                        // Seamless or non-translation: pure update.
                        currentSpineIndex = index
                        updateChapterTitle()
                        saveReadingPosition()
                        translationManager?.onChapterVisible(index)
                        webView.evaluateJavascript("applyHighlights('${getHighlightsJson(index)}')", null)
                    }
                }
            }
            @Keep
            @JavascriptInterface
            @Suppress("unused")
            fun onProgressUpdate(section: Int, page: Int, totalPages: Int) {
                runOnUiThread {
                    val spineSize = epubBook?.spine?.size ?: 1
                    val sectionProgress = section.toFloat() / spineSize
                    val pageProgress = if (totalPages > 0) (page.toFloat() / totalPages) / spineSize else 0f
                    val percent = ((sectionProgress + pageProgress) * 100).toInt().coerceIn(0, 100)
                    val text = getString(R.string.reading_progress_format, section + 1, spineSize, page + 1, totalPages, percent)
                    findViewById<TextView>(R.id.tvProgressPlaceholder)?.text = text
                    
                    // Sync current page to metadata
                    currentBookMetadata?.let {
                        it.lastSpineIndex = section
                        it.lastPageIndex = page
                    }

                    // Debounced save
                    mainHandler.removeCallbacks(savePositionRunnable)
                    mainHandler.postDelayed(savePositionRunnable, 2000)
                }
            }

            @Keep
            @JavascriptInterface
            @Suppress("unused")
            fun openImage(src: String) {
                runOnUiThread {
                    val intent = Intent(this@ReaderActivity, ImageViewerActivity::class.java).apply {
                        putExtra("book_uri", epubBook?.uri.toString())
                        putExtra("image_url", src)
                    }
                    startActivity(intent)
                }
            }

            @Keep
            @JavascriptInterface
            @Suppress("unused")
            fun saveHighlight(json: String) {
                Log.d("Reader", "JS -> saveHighlight: $json")
                runOnUiThread {
                    try {
                        val obj = JSONObject(json)
                        val highlight = Highlight(
                            bookUri = currentBookMetadata?.uri ?: epubBook?.uri.toString(),
                            spineIndex = obj.getInt("spineIndex"),
                            elementIdx = obj.getInt("elementIdx"),
                            startOffset = obj.getInt("startOffset"),
                            endOffset = obj.getInt("endOffset"),
                            originalText = obj.getString("text"),
                            replacementText = if (obj.has("replacementText") && !obj.isNull("replacementText")) obj.getString("replacementText") else null
                        )
                        Log.d("Reader", "Saving Highlight to DB: $highlight")
                        val id = highlightDb.saveHighlight(highlight)
                        Log.d("Reader", "Saved with ID: $id")
                        // Refresh current chapter to show new highlight
                        webView.evaluateJavascript("applyHighlights('${getHighlightsJson(highlight.spineIndex)}')", null)
                    } catch (e: Exception) {
                        Log.e("Reader", "Error saving highlight", e)
                    }
                }
            }

            @Keep
            @JavascriptInterface
            @Suppress("unused")
            fun deleteHighlight(id: String) {
                runOnUiThread {
                    try {
                        highlightDb.deleteHighlight(id.toLong())
                        // We need to know which spine index to refresh. 
                        // For simplicity, refresh current if we can, or just tell JS to remove it.
                        // But applyHighlights refreshes based on DB, so we just need to call it.
                        // We can get the current spine index from the activity state.
                        webView.evaluateJavascript("applyHighlights('${getHighlightsJson(currentSpineIndex)}')", null)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            @Keep
            @JavascriptInterface
            @Suppress("unused")
            fun fixText(json: String) {
                runOnUiThread {
                    showFixOverlay(json)
                }
            }

            @Keep
            @JavascriptInterface
            @Suppress("unused")
            fun addToDictionary(json: String) {
                runOnUiThread {
                    showDictDialog(json)
                }
            }
        }, "AndroidReader",)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return true
                handleInternalLink(url)
                return true
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                if (url.startsWith("epub://")) return serveEpubResource(url.replace("epub://", ""))
                return super.shouldInterceptRequest(view, request)
            }
            override fun onPageFinished(view: WebView?, url: String?) { 
                applyCurrentSettings()
                injectIndexingScript()
                if (isPagedMode) {
                    loadInitialPagedChapters()
                } else {
                    loadInitialSeamlessChapters()
                }
                
                webView.evaluateJavascript("applyHighlights('${getHighlightsJson(currentSpineIndex)}')", null)

                if (shouldJumpToLastPage && !isPagedMode) {
                    executeJumpToLastPage()
                }
            }
        }
        webView.setOnTouchListener { _, event ->
            if (isSwipeBlocked) {
                if (event.action == MotionEvent.ACTION_DOWN) {
                    gestureDetector.onTouchEvent(event)
                }
                return@setOnTouchListener true
            }
            
            val handled = gestureDetector.onTouchEvent(event)
            
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                isAdjustingBrightness = false
            }

            if (isAdjustingBrightness) return@setOnTouchListener true
            
            if (event.action == MotionEvent.ACTION_DOWN) return@setOnTouchListener false
            
            if (isPagedMode) handled else false
        }
    }

    private fun getHighlightsJson(spineIndex: Int): String {
        val bookUri = currentBookMetadata?.uri ?: epubBook?.uri.toString()
        val list = highlightDb.getHighlights(bookUri, spineIndex)
        val result = JSONObject()
        result.put("spineIndex", spineIndex)
        val array = JSONArray()
        list.forEach { h ->
            val obj = JSONObject()
            obj.put("id", h.id)
            obj.put("spineIndex", h.spineIndex)
            obj.put("elementIdx", h.elementIdx)
            obj.put("startOffset", h.startOffset)
            obj.put("endOffset", h.endOffset)
            obj.put("color", h.color)
            obj.put("replacementText", h.replacementText)
            array.put(obj)
        }
        result.put("highlights", array)
        val json = result.toString().replace("'", "\\'")
        Log.d("Reader", "Sending highlights to JS: $json")
        return json
    }

    private fun injectIndexingScript() {
        val js = """
            (function() {
                var menu = document.getElementById('uni-selection-menu');
                if (!menu) {
                    menu = document.createElement('div');
                    menu.id = 'uni-selection-menu';
                    
                    var btnHighlight = document.createElement('button');
                    btnHighlight.className = 'uni-menu-btn';
                    btnHighlight.id = 'uni-highlight-btn';
                    btnHighlight.innerText = '${getString(R.string.selection_highlight)}';
                    menu.appendChild(btnHighlight);
                    
                    var btnFix = document.createElement('button');
                    btnFix.className = 'uni-menu-btn';
                    btnFix.id = 'uni-fix-btn';
                    btnFix.innerText = '${getString(R.string.selection_fix)}';
                    menu.appendChild(btnFix);
                    
                    var btnDict = document.createElement('button');
                    btnDict.className = 'uni-menu-btn';
                    btnDict.id = 'uni-dict-btn';
                    btnDict.innerText = '${getString(R.string.selection_dict)}';
                    menu.appendChild(btnDict);
                    
                    document.body.appendChild(menu);
                    
                    menu.onmousedown = function(e) { e.preventDefault(); e.stopPropagation(); };
                    
                    btnHighlight.onclick = function() {
                        if (this.getAttribute('data-mode') === 'delete') {
                            var id = this.getAttribute('data-target-id');
                            if (id) AndroidReader.deleteHighlight(id);
                            window.getSelection().removeAllRanges();
                        } else {
                            window.getSelectionDetails();
                        }
                    };
                    
                    btnFix.onclick = function() {
                        var sel = window.getSelection();
                        var text = sel.toString();
                        if (text && sel.rangeCount > 0) {
                            var range = sel.getRangeAt(0);
                            var node = range.startContainer;
                            if (node.nodeType === 3) node = node.parentNode;
                            var el = node.closest('[data-idx]');
                            
                            if (el) {
                                var idx = parseInt(el.getAttribute('data-idx'));
                                var preRange = document.createRange();
                                preRange.selectNodeContents(el);
                                preRange.setEnd(range.startContainer, range.startOffset);
                                var start = preRange.toString().length;
                                
                                var sectionEl = el.closest('section');
                                var spineIndex = sectionEl ? parseInt(sectionEl.getAttribute('data-index')) : -1;

                                var context = "";
                                try {
                                    var fullPreRange = document.createRange();
                                    fullPreRange.setStartBefore(document.body.firstChild);
                                    fullPreRange.setEnd(range.startContainer, range.startOffset);
                                    var preText = fullPreRange.toString();
                                    var contextLeft = preText.substring(Math.max(0, preText.length - 1000));
                                    
                                    var fullPostRange = document.createRange();
                                    fullPostRange.setStart(range.endContainer, range.endOffset);
                                    fullPostRange.setEndAfter(document.body.lastChild);
                                    var postText = fullPostRange.toString();
                                    var contextRight = postText.substring(0, 1000);
                                    
                                    context = contextLeft + text + contextRight;
                                } catch(err) { context = text; }
                                
                                var hotpoints = [];
                                var fragment = range.cloneContents();
                                var tempDiv = document.createElement('div');
                                tempDiv.appendChild(fragment);
                                tempDiv.querySelectorAll('.uni-highlight').forEach(h => hotpoints.push(h.innerText));

                                AndroidReader.fixText(JSON.stringify({
                                    text: text, context: context, hotpoints: hotpoints,
                                    spineIndex: spineIndex, elementIdx: idx,
                                    startOffset: start, endOffset: start + text.length
                                }));
                            }
                            window.getSelection().removeAllRanges();
                        }
                        menu.style.display = 'none';
                    };
                    
                    btnDict.onclick = function() {
                        var sel = window.getSelection();
                        var text = sel.toString();
                        if (text && sel.rangeCount > 0) {
                            var range = sel.getRangeAt(0);
                            var node = range.startContainer;
                            if (node.nodeType === 3) node = node.parentNode;
                            var el = node.closest('[data-idx]');
                            if (el) {
                                var idx = parseInt(el.getAttribute('data-idx'));
                                var preRange = document.createRange();
                                preRange.selectNodeContents(el);
                                preRange.setEnd(range.startContainer, range.startOffset);
                                var start = preRange.toString().length;
                                var sectionEl = el.closest('section');
                                var spineIndex = sectionEl ? parseInt(sectionEl.getAttribute('data-index')) : -1;

                                AndroidReader.addToDictionary(JSON.stringify({
                                    text: text, spineIndex: spineIndex, elementIdx: idx,
                                    startOffset: start, endOffset: start + text.length
                                }));
                            }
                            window.getSelection().removeAllRanges();
                        }
                        menu.style.display = 'none';
                    };
                }
                
                var tooltip = document.getElementById('uni-fix-tooltip');
                if (!tooltip) {
                    tooltip = document.createElement('div');
                    tooltip.id = 'uni-fix-tooltip';
                    document.body.appendChild(tooltip);
                    document.addEventListener('mousedown', function(e) {
                        if (tooltip.style.display === 'block' && !tooltip.contains(e.target)) tooltip.style.display = 'none';
                    });
                }
                
                document.querySelectorAll('p, h1, h2, h3, h4, h5, h6, li, img').forEach((item, i) => item.setAttribute('data-idx', i));
                
                window.getSelectionDetails = function(isReplacement, replacementText) {
                    var sel = window.getSelection();
                    if (sel.rangeCount === 0) return;
                    var range = sel.getRangeAt(0);
                    var node = range.startContainer;
                    if (node.nodeType === 3) node = node.parentNode;
                    var el = node.closest('[data-idx]');
                    if (!el) return;
                    
                    var idx = parseInt(el.getAttribute('data-idx'));
                    var preRange = document.createRange();
                    preRange.selectNodeContents(el);
                    preRange.setEnd(range.startContainer, range.startOffset);
                    var start = preRange.toString().length;
                    var sectionEl = el.closest('section');
                    if (!sectionEl) return;

                    AndroidReader.saveHighlight(JSON.stringify({
                        spineIndex: parseInt(sectionEl.getAttribute('data-index')),
                        elementIdx: idx, startOffset: start, endOffset: start + range.toString().length,
                        text: range.toString(), replacementText: isReplacement ? replacementText : null
                    }));
                    sel.removeAllRanges();
                    if (menu) menu.style.display = 'none';
                };
                
                window.uniSelectionListener = function() {
                    try {
                        var sel = window.getSelection();
                        var menu = document.getElementById('uni-selection-menu');
                        if (!menu) return;
                        if (sel.isCollapsed || sel.rangeCount === 0) { menu.style.display = 'none'; return; }
                        
                        var range = sel.getRangeAt(0);
                        var container = range.commonAncestorContainer;
                        if (container.nodeType === 3) container = container.parentNode;
                        
                        var btnHighlight = document.getElementById('uni-highlight-btn');
                        var btnFix = document.getElementById('uni-fix-btn');
                        var btnDict = document.getElementById('uni-dict-btn');
                        if (!btnHighlight || !btnFix || !btnDict) return;

                        var existingMark = container.closest('.uni-highlight, .uni-fix, .uni-dict');
                        if (existingMark) {
                            btnHighlight.innerText = '${getString(R.string.selection_delete)}';
                            btnHighlight.setAttribute('data-mode', 'delete');
                            btnHighlight.setAttribute('data-target-id', existingMark.getAttribute('data-id'));
                            btnFix.style.display = 'none'; btnDict.style.display = 'none';
                        } else {
                            btnHighlight.innerText = '${getString(R.string.selection_highlight)}';
                            btnHighlight.setAttribute('data-mode', 'save');
                            var isTrans = ${currentBookMetadata?.isTranslationMode == true};
                            btnFix.style.display = isTrans ? 'block' : 'none';
                            btnDict.style.display = isTrans ? 'block' : 'none';
                        }

                        var rect = range.getBoundingClientRect();
                        if (rect.width === 0 && range.getClientRects().length > 0) rect = range.getClientRects()[0];

                        if (rect.width > 0 && rect.height > 0) {
                            menu.style.display = 'flex';
                            var mw = menu.scrollWidth || 200, mh = menu.scrollHeight || 40;
                            var left = Math.max(10, Math.min(window.innerWidth - mw - 10, rect.left + rect.width/2 - mw/2));
                            var top = rect.top - mh - 20;
                            if (top < 10) top = rect.bottom + 20;
                            menu.style.left = left + 'px'; menu.style.top = top + 'px';
                        } else menu.style.display = 'none';
                    } catch (err) {}
                };
                
                document.addEventListener('selectionchange', window.uniSelectionListener);
                document.oncontextmenu = function(e) { if (window.getSelection().toString().length > 0) e.preventDefault(); };

                window.applyHighlights = function(json) {
                    var data = JSON.parse(json);
                    var section = document.querySelector('section[data-index="' + data.spineIndex + '"]');
                    if (!section) return;

                    section.querySelectorAll('.uni-highlight, .uni-fix, .uni-dict').forEach(m => {
                        var p = m.parentNode;
                        while(m.firstChild) p.insertBefore(m.firstChild, m);
                        p.removeChild(m);
                    });
                    section.normalize();

                    data.highlights.forEach(h => {
                        var el = section.querySelector('[data-idx="' + h.elementIdx + '"]');
                        if (!el) return;
                        var walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT, null, false);
                        var current = 0, startNode, startOffset, endNode, endOffset;
                        while(walker.nextNode()) {
                            var node = walker.currentNode, len = node.textContent.length;
                            if (!startNode && current + len >= h.startOffset) { startNode = node; startOffset = h.startOffset - current; }
                            if (startNode && !endNode && current + len >= h.endOffset) { endNode = node; endOffset = h.endOffset - current; break; }
                            current += len;
                        }
                        if (startNode && endNode) {
                            var range = document.createRange();
                            range.setStart(startNode, startOffset); range.setEnd(endNode, endOffset);
                            var mark = document.createElement('mark');
                            var isDict = h.replacementText && h.replacementText.indexOf('[DICT_P]:') === 0;
                            var isFix = h.replacementText && h.replacementText.length > 0 && !isDict;
                            mark.className = isDict ? 'uni-dict' : (isFix ? 'uni-fix' : 'uni-highlight');
                            if (mark.className === 'uni-highlight') mark.style.backgroundColor = h.color;
                            mark.setAttribute('data-id', h.id);
                            if (isFix || isDict) mark.setAttribute('data-replacement', h.replacementText);
                            try { range.surroundContents(mark); } catch (e) {
                                var contents = range.extractContents(); mark.appendChild(contents); range.insertNode(mark);
                            }
                        }
                    });
                };
                
                document.body.addEventListener('click', function(e) {
                    var fix = e.target.closest('.uni-fix, .uni-dict');
                    if (fix) {
                        e.preventDefault(); e.stopPropagation();
                        var replacement = fix.getAttribute('data-replacement');
                        if (replacement) {
                            var isDict = replacement.indexOf('[DICT_P]:') === 0;
                            var rect = fix.getBoundingClientRect();
                            tooltip.innerHTML = (isDict ? '<b>Словарь:</b>' : '<b>Исправленный вариант:</b>') + (isDict ? replacement.substring(9) : replacement);
                            tooltip.style.display = 'block';
                            var tw = tooltip.offsetWidth;
                            var left = Math.max(10, Math.min(window.innerWidth - tw - 10, rect.left + rect.width/2 - tw/2));
                            var top = rect.top - tooltip.offsetHeight - 10;
                            if (top < 10) top = rect.bottom + 10;
                            tooltip.style.left = left + 'px'; tooltip.style.top = top + 'px';
                        }
                        return;
                    }
                    var img = e.target.closest('img');
                    if (img && img.getAttribute('src')) {
                        e.preventDefault(); e.stopPropagation();
                        AndroidReader.openImage(img.getAttribute('src')); return;
                    }
                    var a = e.target.closest('a');
                    if (a && a.getAttribute('href')) {
                        var href = a.getAttribute('href');
                        if (href.startsWith('#') || href.indexOf('://') === -1 || href.startsWith('epub://')) {
                            e.preventDefault(); AndroidReader.onLinkClicked(a.href);
                        }
                    }
                }, true);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun handleInternalLink(url: String) {
        Log.d("Reader", "handleInternalLink: $url")
        shouldJumpToLastPage = false
        if (!url.startsWith("epub://") && !url.contains("#") && !url.endsWith(".xhtml") && !url.endsWith(".html")) return

        var cleanPath = url.replace("epub://", "").substringBefore("?")
        // Strip mode prefixes if WebView includes them in absolute URLs
        cleanPath = cleanPath.replace("paged/", "").replace("seamless/", "")
        
        val pathWithoutFragment = cleanPath.substringBefore("#").replace("\\", "/")
        val fragment = if (cleanPath.contains("#")) cleanPath.substringAfter("#") else null

        val book = epubBook ?: return
        val opfDir = File(book.opfPath).parent ?: ""
        
        var targetIndex = -1
        
        // 1. Direct match (normalized paths from root)
        for (i in book.spine.indices) {
            val itemHref = book.spine[i].href
            val fullHref = if (opfDir.isEmpty()) itemHref else "$opfDir/$itemHref".replace("//", "/").replace("\\", "/")
            if (fullHref.equals(pathWithoutFragment, ignoreCase = true) || itemHref.equals(pathWithoutFragment, ignoreCase = true)) {
                targetIndex = i
                break
            }
        }

        // 2. Search by filename
        if (targetIndex == -1) {
            val fileName = pathWithoutFragment.substringAfterLast("/")
            for (i in book.spine.indices) {
                if (book.spine[i].href.substringAfterLast("/").equals(fileName, ignoreCase = true)) {
                    targetIndex = i
                    break
                }
            }
        }

        if (targetIndex != -1 && targetIndex != currentSpineIndex) {
            loadSpineItem(targetIndex)
        } else if (fragment != null) {
            if (isPagedMode) {
                webView.evaluateJavascript("""
                    (function() {
                        var retry = 0;
                        function sync() {
                            var currentSection = document.getElementById('chapter-$currentSpineIndex');
                            var target = currentSection ? (currentSection.querySelector('#$fragment') || document.getElementsByName('$fragment')[0]) : null;
                            
                            if (!target || (currentSection && !currentSection.contains(target))) {
                                target = document.getElementById('$fragment') || document.getElementsByName('$fragment')[0];
                            }
                            
                            var pw = document.documentElement.getBoundingClientRect().width;
                            var sw = document.documentElement.scrollWidth;
                            if ((target && sw > pw) || retry > 60) {
                                if (target) {
                                    var rect = target.getBoundingClientRect();
                                    var pageIndex = Math.floor((window.pageXOffset + rect.left + 5) / pw);
                                    
                                    var targetSection = target.closest('section');
                                    if (targetSection) {
                                        var newIdx = parseInt(targetSection.getAttribute('data-index'));
                                        AndroidReader.onChapterEntered(newIdx);
                                    }
                                    
                                    window.scrollTo(pageIndex * pw, 0);
                                }
                            } else {
                                retry++;
                                setTimeout(sync, 50);
                            }
                        }
                        sync();
                    })();
                """.trimIndent(), null)
            } else {
                webView.evaluateJavascript("""
                    (function() {
                        var currentSection = document.getElementById('chapter-$currentSpineIndex');
                        var target = currentSection ? (currentSection.querySelector('#$fragment') || document.getElementsByName('$fragment')[0]) : null;
                        
                        if (!target || (currentSection && !currentSection.contains(target))) {
                            target = document.getElementById('$fragment') || document.getElementsByName('$fragment')[0];
                        }

                        if (target) {
                            var targetSection = target.closest('section');
                            if (targetSection) {
                                var newIdx = parseInt(targetSection.getAttribute('data-index'));
                                AndroidReader.onChapterEntered(newIdx);
                            }
                            window.scrollTo(0, window.pageYOffset + target.getBoundingClientRect().top - 60);
                        }
                    })();
                """.trimIndent(), null)
            }
        }
    }

    private fun initPagedView() {
        val isDarkMode = settings.isDarkMode
        val bgColor = if (isDarkMode) "#000000" else "#FFFFFF"
        val textColor = if (isDarkMode) "#E0E0E0" else "#000000"

        val html = """
            <!DOCTYPE html>
            <html style="background-color: $bgColor;">
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    body { margin: 0; padding: 0; background-color: $bgColor; color: $textColor; }
                    #chapters-container { width: 100%; height: 100%; }
                </style>
            </head>
            <body data-mode="paged" style="background-color: $bgColor !important; margin: 0; padding: 0;">
                <div id="snap-ribbon"></div>
                <div id="chapters-container"></div>
                <script type="text/javascript">
                    function updateSnapMarkers() {
                        var ribbon = document.getElementById('snap-ribbon');
                        if (!ribbon) return;
                        var pw = document.documentElement.getBoundingClientRect().width;
                        if (pw <= 0) return;
                        var pageCount = Math.max(1, Math.round(document.documentElement.scrollWidth / pw));
                        ribbon.innerHTML = '';
                        for (var i = 0; i < pageCount; i++) {
                            var marker = document.createElement('div');
                            marker.className = 'snap-point';
                            ribbon.appendChild(marker);
                        }
                    }

                    window.addEventListener('resize', updateSnapMarkers);

                    var isLoadingTop = false, isLoadingBottom = false, wasInContent = false;
                    var lastReportedIdx = -1, lastReportedPage = -1;
                    var isAutoScrolling = false;

                    window.addEventListener('scroll', function() {
                        if (isAutoScrolling) return;
                        var pw = document.documentElement.getBoundingClientRect().width, sl = window.pageXOffset;
                        var mid = pw / 2;
                        var sections = [...document.querySelectorAll('section')];
                        var active = sections.find(s => { 
                            var r = s.getBoundingClientRect(); 
                            return r.left < mid && r.right > mid; 
                        });

                        if (active) {
                            var sectionStart = active.offsetLeft;
                            var idx = parseInt(active.getAttribute('data-index'));
                            var sectionWidth = (sections.indexOf(active) < sections.length - 1) ? 
                                               sections[sections.indexOf(active)+1].offsetLeft - sectionStart : 
                                               document.documentElement.scrollWidth - sectionStart;
                            
                            var page = Math.max(0, Math.floor((sl - sectionStart + (pw / 2)) / pw));

                            if (idx !== lastReportedIdx || page !== lastReportedPage) {
                                lastReportedIdx = idx; lastReportedPage = page;
                                AndroidReader.onChapterEntered(idx);
                                AndroidReader.onProgressUpdate(idx, page, Math.max(1, Math.round(sectionWidth / pw)));
                            }
                        }

                        if (sl <= 20) {
                            if (!isLoadingTop && wasInContent) { isLoadingTop = true; AndroidReader.onReachedTop(); }
                        } else if (sl + pw >= document.documentElement.scrollWidth - 20) {
                            if (!isLoadingBottom && wasInContent) { isLoadingBottom = true; AndroidReader.onReachedBottom(); }
                        } else if (sl > pw) {
                            isLoadingTop = false; isLoadingBottom = false; wasInContent = true;
                        }
                    });

                    function appendChapter(index, html, lang, jumpToLast, anchor, scrollToNew, stickToIndex, targetPage) {
                        var container = document.getElementById('chapters-container');
                        if (document.getElementById('chapter-' + index)) return;
                        
                        var section = document.createElement('section');
                        section.id = 'chapter-' + index;
                        section.setAttribute('data-index', index);
                        if (lang) section.setAttribute('lang', lang);
                        section.innerHTML = html;
                        section.querySelectorAll('p, h1, h2, h3, h4, h5, h6, li, img').forEach((it, i) => it.setAttribute('data-idx', i));
                        
                        document.documentElement.style.scrollSnapType = 'none';
                        container.appendChild(section);
                        while (container.children.length > 3) container.removeChild(container.firstChild);
                        updateSnapMarkers();
                        
                        if (jumpToLast || anchor || targetPage >= 0) {
                            var isAutoScrolling = true;
                            var retry = 0;
                            function sync() {
                                var pw = document.documentElement.getBoundingClientRect().width;
                                if (document.documentElement.scrollWidth > pw || retry > 40) {
                                    if (jumpToLast) {
                                        var r = section.getBoundingClientRect();
                                        window.scrollTo(Math.floor((window.pageXOffset + r.right - 5) / pw) * pw, 0);
                                    } else if (targetPage >= 0) {
                                        window.scrollTo(section.offsetLeft + targetPage * pw, 0);
                                    } else if (anchor) {
                                        var t = document.getElementById(anchor) || document.getElementsByName(anchor)[0];
                                        if (t) window.scrollTo(Math.floor((window.pageXOffset + t.getBoundingClientRect().left + 5) / pw) * pw, 0);
                                    }
                                    document.documentElement.style.scrollSnapType = 'x mandatory';
                                    isAutoScrolling = false;
                                } else { retry++; setTimeout(sync, 50); }
                            }
                            sync();
                        } else if (scrollToNew) {
                            window.scrollTo(window.pageXOffset + section.getBoundingClientRect().left, 0);
                            document.documentElement.style.scrollSnapType = 'x mandatory';
                        } else if (stickToIndex >= 0) {
                            var s = document.querySelector('section[data-index="' + stickToIndex + '"]');
                            if (s) {
                                var pw = document.documentElement.getBoundingClientRect().width;
                                window.scrollTo(Math.floor((window.pageXOffset + s.getBoundingClientRect().right - 5) / pw) * pw, 0);
                            }
                            document.documentElement.style.scrollSnapType = 'x mandatory';
                        } else {
                            document.documentElement.style.scrollSnapType = 'x mandatory';
                        }
                        isLoadingTop = isLoadingBottom = false;
                    }

                    function prependChapter(index, html, lang, goToNew, keepIndex) {
                        var container = document.getElementById('chapters-container');
                        if (document.getElementById('chapter-' + index)) return;
                        
                        var section = document.createElement('section');
                        section.id = 'chapter-' + index;
                        section.setAttribute('data-index', index);
                        if (lang) section.setAttribute('lang', lang);
                        section.innerHTML = html;
                        section.querySelectorAll('p, h1, h2, h3, h4, h5, h6, li, img').forEach((it, i) => it.setAttribute('data-idx', i));
                        
                        document.documentElement.style.scrollSnapType = 'none';
                        var oldW = document.documentElement.scrollWidth;
                        container.insertBefore(section, container.firstChild);
                        while (container.children.length > 3) container.removeChild(container.lastChild);
                        var newW = document.documentElement.scrollWidth;

                        isAutoScrolling = true;
                        if (keepIndex >= 0) {
                            var s = document.querySelector('section[data-index="' + keepIndex + '"]');
                            if (s) window.scrollTo(window.pageXOffset + s.getBoundingClientRect().left, 0);
                            else window.scrollBy(newW - oldW, 0);
                        } else if (goToNew) {
                            var pw = document.documentElement.getBoundingClientRect().width;
                            window.scrollTo(Math.floor((window.pageXOffset + section.getBoundingClientRect().right - 5) / pw) * pw, 0);
                        } else window.scrollBy(newW - oldW, 0);
                        isAutoScrolling = false;
                        
                        isLoadingTop = isLoadingBottom = false;
                        requestAnimationFrame(() => { requestAnimationFrame(() => { updateSnapMarkers(); document.documentElement.style.scrollSnapType = 'x mandatory'; }); });
                    }

                    function scrollToPosition(chapterIdx, targetIdx, targetOffset) {
                        var s = document.getElementById('chapter-' + chapterIdx);
                        if (!s) return;
                        var pw = document.documentElement.getBoundingClientRect().width;
                        if (pw <= 0) return;
                        
                        var target = null;
                        if (targetIdx >= 0) {
                            target = s.querySelector('[data-idx="' + targetIdx + '"]');
                        }

                        if (target) {
                            isAutoScrolling = true;
                            document.documentElement.style.scrollSnapType = 'none';
                            var left = target.getBoundingClientRect().left;
                            window.scrollTo(Math.floor((window.pageXOffset + left + 5) / pw) * pw, 0);
                            document.documentElement.style.scrollSnapType = 'x mandatory';
                            isAutoScrolling = false;
                        }
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        lastAppendedIndex = -1
        firstPrependedIndex = Int.MAX_VALUE
        isChapterLoading = false
        isJumpingToChapter = true

        webView.loadDataWithBaseURL("epub://reader/", html, "text/html", "UTF-8", null)
    }

    private fun loadInitialPagedChapters() {
        val useCache = lastKnownPosition != null && lastKnownPosition?.first == currentSpineIndex
        val finalPos = if (useCache) lastKnownPosition!! else Triple(currentSpineIndex, -1, -1)
        
        val pageToUse = pendingPageIndex
        val anchorToUse = pendingAnchor
        val jumpToLast = shouldJumpToLastPage

        pendingPageIndex = -1
        pendingAnchor = null
        shouldJumpToLastPage = false

        isJumpingToChapter = true

        loadAndAppendChapter(finalPos.first, jumpToLast = jumpToLast, anchor = anchorToUse, targetPage = pageToUse) {
            loadAndPrependChapter(finalPos.first - 1) {
                loadAndAppendChapter(finalPos.first + 1) {
                    isJumpingToChapter = false
                }
            }
        }
    }

    private fun initSeamlessScroll() {
        val isDarkMode = settings.isDarkMode
        val bgColor = if (isDarkMode) "#000000" else "#FFFFFF"
        val textColor = if (isDarkMode) "#E0E0E0" else "#000000"
        
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    body { margin: 0; padding: 0; background-color: $bgColor; color: $textColor; }
                </style>
            </head>
            <body style="background-color: $bgColor !important;">
                <div id="chapters-container"></div>
                <script type="text/javascript">
                    var observer = new IntersectionObserver(function(entries) {
                        entries.forEach(function(entry) {
                            if (entry.isIntersecting) {
                                if (entry.target.id === 'bottom-sentinel') AndroidReader.onReachedBottom();
                                else if (entry.target.id === 'top-sentinel') AndroidReader.onReachedTop();
                            }
                        });
                    }, { threshold: 0.1 });
                    
                    window.addEventListener('scroll', function() {
                        var sections = [...document.querySelectorAll('section')];
                        var mid = window.innerHeight / 2;
                        var active = sections.find(s => {
                            var r = s.getBoundingClientRect();
                            return r.top < mid && r.bottom > mid;
                        });
                        if (active) AndroidReader.onChapterEntered(parseInt(active.getAttribute('data-index')));
                    });

                    function appendChapter(index, html, lang) {
                        var container = document.getElementById('chapters-container');
                        if (document.getElementById('chapter-' + index)) return;
                        
                        var section = document.createElement('section');
                        section.id = 'chapter-' + index;
                        section.setAttribute('data-index', index);
                        if (lang) section.setAttribute('lang', lang);
                        section.innerHTML = html;
                        section.querySelectorAll('p, h1, h2, h3, h4, h5, h6, li, img').forEach((it, i) => it.setAttribute('data-idx', i));
                        
                        var oldBot = document.getElementById('bottom-sentinel');
                        if (oldBot) { observer.unobserve(oldBot); oldBot.remove(); }
                        
                        container.appendChild(section);
                        
                        var sentinel = document.createElement('div');
                        sentinel.id = 'bottom-sentinel'; sentinel.style.height = '100px'; sentinel.style.width = '100%';
                        container.appendChild(sentinel); observer.observe(sentinel);
                    }

                    function prependChapter(index, html, lang) {
                        var container = document.getElementById('chapters-container');
                        if (document.getElementById('chapter-' + index)) return;
                        
                        var section = document.createElement('section');
                        section.id = 'chapter-' + index;
                        section.setAttribute('data-index', index);
                        if (lang) section.setAttribute('lang', lang);
                        section.innerHTML = html;
                        section.querySelectorAll('p, h1, h2, h3, h4, h5, h6, li, img').forEach((it, i) => it.setAttribute('data-idx', i));
                        
                        var oldTop = document.getElementById('top-sentinel');
                        if (oldTop) { observer.unobserve(oldTop); oldTop.remove(); }

                        var oldH = container.scrollHeight;
                        container.insertBefore(section, container.firstChild);
                        window.scrollBy(0, container.scrollHeight - oldH);
                        
                        var sentinel = document.createElement('div');
                        sentinel.id = 'top-sentinel'; sentinel.style.height = '100px'; sentinel.style.width = '100%';
                        container.insertBefore(sentinel, container.firstChild); observer.observe(sentinel);
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
        
        lastAppendedIndex = -1
        firstPrependedIndex = Int.MAX_VALUE
        isChapterLoading = false
        isJumpingToChapter = true
        
        webView.loadDataWithBaseURL("epub://reader/", html, "text/html", "UTF-8", null)
    }

    private fun loadInitialSeamlessChapters() {
        val useCache = lastKnownPosition != null && lastKnownPosition?.first == currentSpineIndex
        val finalPos = if (useCache) lastKnownPosition!! else Triple(currentSpineIndex, -1, -1)
        
        isJumpingToChapter = true
        loadAndAppendChapter(finalPos.first) {
            loadAndPrependChapter(finalPos.first - 1, stayOnCurrent = true) {
                loadAndAppendChapter(finalPos.first + 1, stickToCurrent = true) {
                    isJumpingToChapter = false
                }
            }
        }
    }

    private fun loadAndAppendChapter(
        index: Int,
        jumpToLast: Boolean = false,
        anchor: String? = null,
        scrollToNew: Boolean = false,
        stickToCurrent: Boolean = false,
        targetPage: Int = -1,
        onFinished: (() -> Unit)? = null
    ) {
        val loader = chapterLoader ?: return
        if (index < 0 || index >= (epubBook?.spine?.size ?: 0) || index <= lastAppendedIndex) {
            onFinished?.invoke()
            return
        }
        
        isChapterLoading = true
        val content = loader.loadChapterHtml(index) ?: run {
            isChapterLoading = false; onFinished?.invoke(); return
        }
        
        if (lastAppendedIndex == -1) firstPrependedIndex = index
        lastAppendedIndex = index
        
        val escapedHtml = content.html.replace("`", "\\`").replace("$", "\\$")
        val langArg = if (content.lang != null) "'${content.lang}'" else "null"
        val anchorArg = if (anchor != null) "'$anchor'" else "null"
        val stickToIndexArg = if (stickToCurrent) currentSpineIndex.toString() else "-1"
        webView.evaluateJavascript("appendChapter($index, `$escapedHtml`, $langArg, $jumpToLast, $anchorArg, $scrollToNew, $stickToIndexArg, $targetPage);") {
            isChapterLoading = false
            webView.evaluateJavascript("applyHighlights('${getHighlightsJson(index)}')", null)
            onFinished?.invoke()
        }
    }

    private fun loadAndPrependChapter(index: Int, stayOnCurrent: Boolean = false, onFinished: (() -> Unit)? = null) {
        val loader = chapterLoader ?: return
        if (index < 0 || index >= (epubBook?.spine?.size ?: 0) || index >= firstPrependedIndex) {
            onFinished?.invoke(); return
        }
        
        isChapterLoading = true
        val content = loader.loadChapterHtml(index) ?: run {
            isChapterLoading = false; onFinished?.invoke(); return
        }
        
        firstPrependedIndex = index
        val escapedHtml = content.html.replace("`", "\\`").replace("$", "\\$")
        val langArg = if (content.lang != null) "'${content.lang}'" else "null"
        val keepIndexArg = if (stayOnCurrent) currentSpineIndex.toString() else "-1"
        webView.evaluateJavascript("prependChapter($index, `$escapedHtml`, $langArg, $stayOnCurrent, $keepIndexArg);") {
            isChapterLoading = false
            webView.evaluateJavascript("applyHighlights('${getHighlightsJson(index)}')", null)
            onFinished?.invoke()
        }
    }

    private fun loadNextSpineItem() {
        if (isPagedMode) {
            loadAndAppendChapter(lastAppendedIndex + 1, stickToCurrent = true)
        } else if (currentSpineIndex < (epubBook?.spine?.size ?: 0) - 1) {
            loadSpineItem(currentSpineIndex + 1, jumpToLast = false)
        }
    }

    private fun loadPrevSpineItem() {
        if (isPagedMode) {
                loadAndPrependChapter(firstPrependedIndex - 1, stayOnCurrent = true)
        } else if (currentSpineIndex > 0) {
            loadSpineItem(currentSpineIndex - 1, jumpToLast = true)
        }
    }

    private fun executeJumpToLastPage() {
        shouldJumpToLastPage = false
        if (isPagedMode) {
            webView.evaluateJavascript("""
                (function() { 
                    var sw = document.documentElement.scrollWidth; 
                    var pw = window.innerWidth; 
                    var lastPage = Math.floor((sw - 1) / pw);
                    window.scrollTo(lastPage * pw, 0); 
                    document.body.style.visibility = 'visible'; 
                })();
            """.trimIndent(), null)
        } else {
            webView.evaluateJavascript("(function() { window.scrollTo(0, document.documentElement.scrollHeight); document.body.style.visibility = 'visible'; })();", null)
        }
    }

    private fun loadSpineItem(index: Int, jumpToLast: Boolean = false, targetPage: Int = -1) {
        mainHandler.removeCallbacks(reloadChapterRunnable)
        pendingReloadIndex = -1
        
        lastKnownPosition = null // CLEAR CACHE on intentional jump
        currentSpineIndex = index
        shouldJumpToLastPage = jumpToLast
        if (targetPage >= 0) pendingPageIndex = targetPage
        updateChapterTitle()

        // Ensure manager starts pre-fetching for the new current chapter and its neighbors
        translationManager?.onChapterVisible(index)
        
        if (isPagedMode) {
            initPagedView()
        } else {
            initSeamlessScroll()
        }
    }



    private fun serveEpubResource(path: String): WebResourceResponse? {
        val book = epubBook ?: return null
        try {
            contentResolver.openInputStream(book.uri)?.use { inputStream ->
                val zip = ZipInputStream(inputStream)
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name.replace("\\", "/") == path.replace("\\", "/")) {
                        return WebResourceResponse(getMimeType(path), "UTF-8", ByteArrayInputStream(zip.readBytes()))
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        return null
    }

    private fun getMimeType(path: String) = when {
        path.endsWith(".html") || path.endsWith(".xhtml") -> "text/html"
        path.endsWith(".css") -> "text/css"
        path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
        path.endsWith(".png") -> "image/png"
        path.endsWith(".gif") -> "image/gif"
        else -> "application/octet-stream"
    }

    private fun nextPage() {
        if (!isPagedMode) return
        webView.evaluateJavascript("""
            (function() { 
                var sw = document.documentElement.scrollWidth;
                var pw = document.documentElement.getBoundingClientRect().width;
                var sl = window.pageXOffset || document.documentElement.scrollLeft;
                
                if (sl + pw + 5 < sw) { 
                    window.scrollTo({ left: (Math.round(sl / pw) + 1) * pw, behavior: 'auto' }); 
                    return 'ok'; 
                } 
                return 'next'; 
            })();
        """.trimIndent()) { 
            if (it == "\"next\"") {
                loadNextSpineItem()
            }
        }
    }

    private fun prevPage() {
        if (!isPagedMode) return
        webView.evaluateJavascript("""
            (function() { 
                var pw = document.documentElement.getBoundingClientRect().width;
                var sl = window.pageXOffset || document.documentElement.scrollLeft;
                
                if (sl > 5) { 
                    window.scrollTo({ left: (Math.round(sl / pw) - 1) * pw, behavior: 'auto' });
                    return 'ok'; 
                } 
                return 'prev'; 
            })();
        """.trimIndent()) {
            if (it == "\"prev\"") loadPrevSpineItem()
        }
    }

    private fun showEditCorrectionDialog(highlight: Highlight) {
        val input = EditText(this).apply {
            setText(highlight.replacementText?.substringAfter("]:") ?: "")
            setPadding(48, 32, 48, 32)
        }
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.edit_note)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val newTranslation = input.text.toString().trim()
                if (newTranslation.isNotEmpty()) {
                    val updated = highlight.copy(replacementText = "[DICT_P]:$newTranslation")
                    highlightDb.saveHighlight(updated)
                    webView.evaluateJavascript("applyHighlights('${getHighlightsJson(highlight.spineIndex)}')", null)
                    
                    // Refresh sheet if open
                    val bookUri = currentBookMetadata?.uri ?: epubBook?.uri?.toString() ?: return@setPositiveButton
                    val dictEntries = highlightDb.getDictEntries(bookUri)
                    (supportFragmentManager.findFragmentByTag("dictionary") as? DictionarySheet)?.refresh(dictEntries)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditGlossaryDialog(item: ServerGlossarySheet.GlossaryItem) {
        val bookUri = currentBookMetadata?.uri ?: return
        val latestMetadata = LibraryProvider(this).getBooks().find { it.uri == bookUri } ?: return
        val glossaryJson = latestMetadata.serverGlossary ?: return
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }
        
        val etTranslation = EditText(this).apply {
            hint = getString(R.string.translation_text)
            setText(item.translation)
        }
        
        val etGender = EditText(this).apply {
            hint = getString(R.string.gender_hint)
            setText(item.meta ?: "")
        }
        
        layout.addView(etTranslation)
        layout.addView(etGender)
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.edit_glossary_item, item.original))
            .setView(layout)
            .setPositiveButton(R.string.save) { _, _ ->
                val newTrans = etTranslation.text.toString().trim()
                val newGender = etGender.text.toString().trim()
                
                try {
                    val root = JSONObject(glossaryJson)
                    val array = root.optJSONArray("glossary")
                    if (array != null) {
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            if (obj.optString("original") == item.original) {
                                obj.put("translation", newTrans)
                                if (newGender.isEmpty()) obj.remove("gender")
                                else obj.put("gender", newGender)
                                break
                            }
                        }
                        
                        latestMetadata.serverGlossary = root.toString()
                        LibraryProvider(this).addBook(latestMetadata)
                        Toast.makeText(this, R.string.updated, Toast.LENGTH_SHORT).show()
                        
                        // Refresh sheet if open
                        (supportFragmentManager.findFragmentByTag("server_glossary") as? ServerGlossarySheet)?.refresh(root.toString())
                    }
                } catch (e: Exception) {
                    Log.e("Reader", "Error updating glossary JSON", e)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddGlossaryEntryDialog() {
        val bookUri = currentBookMetadata?.uri ?: return
        val latestMetadata = LibraryProvider(this).getBooks().find { it.uri == bookUri } ?: return
        val glossaryJson = latestMetadata.serverGlossary ?: "{}"
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }
        
        val etOriginal = EditText(this).apply { hint = getString(R.string.original_term_hint) }
        val etTranslation = EditText(this).apply { hint = getString(R.string.translation_text) }
        val etGender = EditText(this).apply { hint = getString(R.string.gender_hint) }
        
        layout.addView(etOriginal)
        layout.addView(etTranslation)
        layout.addView(etGender)
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_to_glossary)
            .setView(layout)
            .setPositiveButton(R.string.add_to_glossary) { _, _ ->
                val original = etOriginal.text.toString().trim()
                val translation = etTranslation.text.toString().trim()
                val gender = etGender.text.toString().trim()
                
                if (original.isNotEmpty() && translation.isNotEmpty()) {
                    try {
                        val root = JSONObject(glossaryJson)
                        val array = root.optJSONArray("glossary") ?: JSONArray().also { root.put("glossary", it) }
                        
                        val newEntry = JSONObject().apply {
                            put("original", original)
                            put("translation", translation)
                            if (gender.isNotEmpty()) put("gender", gender)
                        }
                        array.put(newEntry)
                        
                        latestMetadata.serverGlossary = root.toString()
                        LibraryProvider(this).addBook(latestMetadata)
                        
                        (supportFragmentManager.findFragmentByTag("server_glossary") as? ServerGlossarySheet)?.refresh(root.toString())
                        Toast.makeText(this, R.string.term_added, Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.e("Reader", "Error adding to glossary", e)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDictDialog(json: String) {
        try {
            val obj = JSONObject(json)
            val text = obj.getString("text")
            
            val input = EditText(this).apply {
                hint = getString(R.string.translation_for, text)
                setPadding(48, 32, 48, 32)
            }
            
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.add_to_dictionary)
                .setView(input)
                .setPositiveButton(R.string.save) { _, _ ->
                    val translation = input.text.toString().trim()
                    if (translation.isNotEmpty()) {
                        val highlight = Highlight(
                            bookUri = currentBookMetadata?.uri ?: epubBook?.uri.toString(),
                            spineIndex = obj.getInt("spineIndex"),
                            elementIdx = obj.getInt("elementIdx"),
                            startOffset = obj.getInt("startOffset"),
                            endOffset = obj.getInt("endOffset"),
                            originalText = text,
                            replacementText = "[DICT_P]:$translation"
                        )
                        highlightDb.saveHighlight(highlight)
                        webView.evaluateJavascript("applyHighlights('${getHighlightsJson(highlight.spineIndex)}')", null)
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
                
        } catch (e: Exception) {
            Log.e("Reader", "Error showing dict dialog", e)
        }
    }

    private fun showFixOverlay(json: String) {
        lastFixRequestJson = json
        val data = JSONObject(json)
        val text = data.getString("text")
        val context = data.optString("context")
        val hotpointsJson = data.optJSONArray("hotpoints")
        val hotpoints = mutableListOf<String>()
        if (hotpointsJson != null) {
            for (i in 0 until hotpointsJson.length()) {
                hotpoints.add(hotpointsJson.getString(i))
            }
        }

        fixOverlay.visibility = View.VISIBLE
        fixLoading.visibility = View.VISIBLE
        fixActions.visibility = View.GONE
        tvFixResult.text = getString(R.string.creating_task)
        tvFixModel.visibility = View.GONE
        
        lifecycleScope.launch(Dispatchers.IO) {
            fixService.improveText(
                text = text,
                contextText = context,
                hotpoints = hotpoints,
                onStatusUpdate = { status ->
                    runOnUiThread { tvFixResult.text = status }
                },
                onSuccess = { result, model ->
                    runOnUiThread {
                        lastImprovedText = result
                        fixLoading.visibility = View.GONE
                        fixActions.visibility = View.VISIBLE
                        tvFixResult.text = result
                        if (!model.isNullOrEmpty()) {
                            tvFixModel.text = model
                            tvFixModel.visibility = View.VISIBLE
                        }
                    }
                },
                onError = { error ->
                    runOnUiThread {
                        fixLoading.visibility = View.GONE
                        fixActions.visibility = View.VISIBLE
                        tvFixResult.text = getString(R.string.error_prefix, error)
                    }
                }
            )
        }
    }

    private fun acceptImprovement() {
        val improved = lastImprovedText ?: return
        val lastRequest = lastFixRequestJson?.let { JSONObject(it) } ?: return
        
        Log.d("Reader", "Accepting improvement. Directly saving to DB using cached positions.")
        
        try {
            val highlight = Highlight(
                bookUri = currentBookMetadata?.uri ?: epubBook?.uri.toString(),
                spineIndex = lastRequest.getInt("spineIndex"),
                elementIdx = lastRequest.getInt("elementIdx"),
                startOffset = lastRequest.getInt("startOffset"),
                endOffset = lastRequest.getInt("endOffset"),
                originalText = lastRequest.getString("text"),
                replacementText = improved
            )
            
            val id = highlightDb.saveHighlight(highlight)
            Log.d("Reader", "Directly saved fix with ID: $id")
            
            // Refresh visuals
            webView.evaluateJavascript("applyHighlights('${getHighlightsJson(highlight.spineIndex)}')", null)
            fixOverlay.visibility = View.GONE
            
        } catch (e: Exception) {
            Log.e("Reader", "Error during direct save", e)
            Toast.makeText(this, R.string.error_saving, Toast.LENGTH_SHORT).show()
        }
    }

    private fun performSave(destinationUri: Uri) {
        val book = epubBook ?: return
        val bookUriString = currentBookMetadata?.uri ?: book.uri.toString()
        val pendingFixes = highlightDb.getPendingFixes(bookUriString)
        
        if (pendingFixes.isEmpty()) {
            Toast.makeText(this, R.string.no_pending_fixes, Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = android.app.ProgressDialog(this).apply {
            setMessage(getString(R.string.saving_improved_copy))
            setCancelable(false)
            show()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val success = EpubModifier(this@ReaderActivity).applyFixes(book, pendingFixes, destinationUri)
            
            withContext(Dispatchers.Main) {
                progressDialog.dismiss()
                if (success) {
                    highlightDb.deleteFixes(bookUriString)
                    Toast.makeText(this@ReaderActivity, R.string.saved_successfully, Toast.LENGTH_LONG).show()
                    
                    // Note: We don't reload here because we saved to a NEW file.
                    // The current reader is still pointing to the original URI.
                } else {
                    Toast.makeText(this@ReaderActivity, R.string.error_saving_file, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
