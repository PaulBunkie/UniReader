package com.example.unireader

import android.annotation.SuppressLint
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
import android.widget.ProgressBar
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
    
    // High-precision Element Index
    private var pendingElementIndex = -1
    private var pendingCharOffset = -1
    private var pendingAnchor: String? = null
    private var isJumpingToChapter = false
    private var chaptersToLoad = 0
    private var isSwipeBlocked = false
    
    private var lastKnownPosition: Triple<Int, Int, Int>? = null
    private val savePositionRunnable = Runnable { saveReadingPosition() }
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

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
    private val fixService = FixService()
    private var lastFixRequestJson: String? = null
    private var lastImprovedText: String? = null

    private var translationManager: TranslationManager? = null
    private var currentBookMetadata: BookMetadata? = null
    private lateinit var processingOverlay: View
    private lateinit var initialTranslationOverlay: View

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

        processingOverlay = findViewById(R.id.processingOverlay)
        initialTranslationOverlay = findViewById(R.id.initialTranslationOverlay)

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
        pendingElementIndex = savedInstanceState?.getInt("element_index", -1) ?: -1
        pendingCharOffset = savedInstanceState?.getInt("char_offset", -1) ?: -1
        pendingAnchor = savedInstanceState?.getString("anchor")
        
        // Load modes from settings, then override with savedInstanceState if present
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
                        pendingElementIndex = savedBook.lastElementIndex
                        pendingCharOffset = savedBook.lastCharOffset
                        pendingAnchor = savedBook.lastAnchor
                    }
                }
                
                if (metadata?.isTranslationMode == true) {
                    val isFirstOpen = metadata.lastSpineIndex == 0 && metadata.lastElementIndex == -1
                    val isCurrentReady = translationManager?.isChapterTranslated(currentSpineIndex) == true
                    
                    if (isFirstOpen && !isCurrentReady) {
                        initialTranslationOverlay.visibility = View.VISIBLE
                    } else {
                        initialTranslationOverlay.visibility = View.GONE
                    }
                    
                    initTranslation(book, uriString)
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
        
        val pos = lastKnownPosition
        outState.putInt("spine_index", pos?.first ?: currentSpineIndex)
        outState.putInt("element_index", pos?.second ?: pendingElementIndex)
        outState.putInt("char_offset", pos?.third ?: pendingCharOffset)
        
        outState.putString("anchor", pendingAnchor)
        outState.putBoolean("fullscreen", isFullscreenPref)
        outState.putBoolean("ui_visible", isUiOverlayVisible)
        outState.putBoolean("paged_mode", isPagedMode)
    }

    private fun initTranslation(book: EpubBook, originalUri: String) {
        translationManager = TranslationManager(this, book, originalUri)
        
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
                processingOverlay.visibility = if (tasks.contains(currentSpineIndex)) View.VISIBLE else View.GONE
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
        findViewById<TextView>(R.id.tvBookTitle)?.text = book.title ?: "Unknown Book"
        updateChapterTitle()
    }

    private fun updateChapterTitle() {
        val book = epubBook ?: return
        if (currentSpineIndex < book.spine.size) {
            val item = book.spine[currentSpineIndex]
            val href = item.href
            val tocTitle = book.toc.find { it.href == href || href.endsWith(it.href) || it.href.endsWith(href) }?.title
            findViewById<TextView>(R.id.tvChapterTitle)?.text = tocTitle ?: href
        }
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
                    val currentHref = if (currentSpineIndex < book.spine.size) book.spine[currentSpineIndex].href else null
                    TOCSheet(book.toc, currentHref) { href ->
                        handleInternalLink("epub://$href")
                    }.show(supportFragmentManager, "toc")
                }
                true
            }
            R.id.action_settings -> {
                val anchor = findViewById<View>(R.id.action_settings) ?: appBarLayout
                val popup = androidx.appcompat.widget.PopupMenu(this, anchor)
                popup.menu.add("Appearance").setOnMenuItemClickListener {
                    ReaderSettingsSheet().show(supportFragmentManager, "settings")
                    true
                }
                popup.menu.add("Save Updates").setOnMenuItemClickListener {
                    val book = epubBook ?: return@setOnMenuItemClickListener true
                    val fileName = book.uri.toString().substringAfterLast("/").substringBeforeLast(".") + "_improved.epub"
                    saveDocumentLauncher.launch(fileName)
                    true
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
            
            val finalPos = if (pos.first >= 0) pos else (lastKnownPosition ?: pos)
            
            if (finalPos.first >= 0) currentSpineIndex = finalPos.first
            pendingElementIndex = finalPos.second
            pendingCharOffset = finalPos.third
            
            if (!isPagedMode) {
                initSeamlessScroll()
            } else {
                initPagedView()
            }
            updateUiState()
        }
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(savePositionRunnable)
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
        captureCurrentPosition { pos ->
            val libraryProvider = LibraryProvider(this)
            val finalPos = if (pos.first >= 0) pos else (lastKnownPosition ?: pos)
            
            if (finalPos.first >= 0) {
                libraryProvider.updateBookProgress(uri, finalPos.first, finalPos.second, finalPos.third, null)
            }
        }
    }

    private fun captureCurrentPosition(onCaptured: (Triple<Int, Int, Int>) -> Unit) {
        val js = """
            (function() {
                function getTextOffset(node, target) {
                    var offset = 0;
                    var walker = document.createTreeWalker(target, NodeFilter.SHOW_TEXT, null, false);
                    while (walker.nextNode()) {
                        if (walker.currentNode === node) break;
                        offset += walker.currentNode.textContent.length;
                    }
                    return offset;
                }

                var pw = window.innerWidth;
                var mode = document.body.getAttribute('data-mode') || 'scroll';
                
                var startY = 60; 
                var endY = 500;
                
                for (var y = startY; y < endY; y += 40) {
                    var found = document.elementFromPoint(pw / 2, y);
                    if (!found) continue;
                    
                    var section = found.closest('section');
                    var chapterIdx = section ? parseInt(section.getAttribute('data-index')) : -1;
                    
                    var target = found.closest('p, h1, h2, h3, h4, h5, h6, li, img');
                    if (!target || !target.hasAttribute('data-idx')) continue;
                    
                    if (target.tagName.toLowerCase() === 'img') {
                        return JSON.stringify({c: chapterIdx, idx: parseInt(target.getAttribute('data-idx')), offset: -1});
                    }
                    
                    var range = document.caretRangeFromPoint(pw / 2, y);
                    if (range) {
                        var node = range.startContainer;
                        var localOffset = range.startOffset;
                        
                        var lineRange = document.createRange();
                        lineRange.setStart(node, localOffset);
                        lineRange.setEnd(node, localOffset);
                        var rects = lineRange.getClientRects();
                        if (rects.length > 0) {
                            var targetLeft = rects[0].left;
                            var searchOffset = localOffset;
                            while (searchOffset > 0) {
                                lineRange.setStart(node, searchOffset - 1);
                                lineRange.setEnd(node, searchOffset);
                                var r = lineRange.getClientRects();
                                if (r.length > 0 && Math.abs(r[0].left - targetLeft) > 10) {
                                    break;
                                }
                                searchOffset--;
                            }
                            localOffset = searchOffset;
                        }

                        var globalOffset = getTextOffset(node, target) + localOffset;
                        return JSON.stringify({c: chapterIdx, idx: parseInt(target.getAttribute('data-idx')), offset: globalOffset});
                    }
                }
                return JSON.stringify({c: -1, idx: -1, offset: -1});
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) {
            try {
                val json = org.json.JSONObject(it.trim('"').replace("\\\"", "\""))
                val c = json.optInt("c", -1)
                val idx = json.optInt("idx", -1)
                val off = json.optInt("offset", -1)
                val res = Triple(c, idx, off)
                if (c >= 0) {
                    lastKnownPosition = res
                    currentSpineIndex = c // Keep currentSpineIndex in sync
                }
                onCaptured(res)
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
        
        if (isPagedMode) {
            applyCurrentSettings()
        }
    }

    fun applyCurrentSettings() {
        val isDarkMode = settings.isDarkMode
        val bgColor = if (isDarkMode) "#000000" else "#FFFFFF"
        val textColor = if (isDarkMode) "#E0E0E0" else "#000000"
        
        webView.setBackgroundColor(if (isDarkMode) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        findViewById<CoordinatorLayout>(R.id.readerRoot)?.setBackgroundColor(if (isDarkMode) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())

        val commonCss = """
            body { 
                line-height: ${settings.lineHeight}; 
                font-family: sans-serif; 
                font-size: ${settings.fontSize}px;
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
            p {
                text-indent: ${settings.firstLineIndent}em;
            }
            * { max-width: 100% !important; box-sizing: border-box !important; }
            img { display: block; max-width: 100% !important; max-height: 80vh !important; margin: 10px auto !important; object-fit: contain; }
            
            mark.uni-highlight {
                background-color: #ffeb3b !important;
                color: #000 !important;
                border-radius: 2px;
            }
            mark.uni-fix {
                background-color: #C8E6C9 !important;
                color: #1B5E20 !important;
                border-radius: 2px;
                cursor: help;
            }
            [data-theme="dark"] mark.uni-highlight {
                background-color: #f57f17 !important;
                color: #fff !important;
            }
            [data-theme="dark"] mark.uni-fix {
                background-color: #2E7D32 !important;
                color: #E8F5E9 !important;
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
            }
            section {
                display: block;
                break-before: column;
                -webkit-column-break-before: column;
            }
            p, h1, h2, h3, h4, h5, h6, li { 
                margin: 0 !important;
                padding: 0 ${halfGapPx}px ${1.2 * settings.paragraphSpacing}em ${halfGapPx}px !important; 
            }
            div {
                margin: 0 !important;
                padding: 0 ${halfGapPx}px 0 ${halfGapPx}px !important;
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
            p, h1, h2, h3, h4, h5, h6, li { 
                margin-top: 0; 
                margin-bottom: ${settings.paragraphSpacing}em !important; 
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
                    Log.d("Reader", "onChapterEntered: index=$index, current=$currentSpineIndex, isJumping=$isJumpingToChapter, isSwipeBlocked=$isSwipeBlocked")
                    if (isJumpingToChapter || isSwipeBlocked) return@runOnUiThread
                    
                    if (currentSpineIndex != index) {
                        currentSpineIndex = index
                        updateChapterTitle()
                        saveReadingPosition()
                        
                        // Notify Manager
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
                    val text = "Глава ${section + 1}/$spineSize · Стр ${page + 1}/$totalPages · $percent%"
                    findViewById<TextView>(R.id.tvProgressPlaceholder)?.text = text
                    
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
                            bookUri = epubBook?.uri.toString(),
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
        val list = highlightDb.getHighlights(epubBook?.uri.toString(), spineIndex)
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
                console.log('UniReader: Injecting script');
                
                // Add CSS for the floating highlight button if not already there
                if (!document.getElementById('uni-highlight-style')) {
                    var style = document.createElement('style');
                    style.id = 'uni-highlight-style';
                    style.innerHTML = `
                        #uni-selection-menu {
                            position: fixed;
                            background: #2196F3 !important;
                            border: none !important;
                            border-radius: 20px !important;
                            display: none;
                            z-index: 2147483647 !important;
                            box-shadow: 0 4px 12px rgba(0,0,0,0.4) !important;
                            overflow: hidden;
                            flex-direction: row;
                            font-family: sans-serif !important;
                        }
                        .uni-menu-btn {
                            background: none !important;
                            border: none !important;
                            color: white !important;
                            padding: 10px 20px !important;
                            font-size: 14px !important;
                            font-weight: bold !important;
                            cursor: pointer !important;
                            transition: background 0.2s;
                            white-space: nowrap;
                        }
                        .uni-menu-btn:active {
                            background: rgba(0,0,0,0.1) !important;
                        }
                        .uni-menu-btn:not(:last-child) {
                            border-right: 1px solid rgba(255,255,255,0.3) !important;
                        }
                        #uni-fix-tooltip {
                            position: fixed;
                            background: #fdfdfd !important;
                            color: #333 !important;
                            border: 1px solid #ccc !important;
                            border-radius: 8px !important;
                            padding: 12px !important;
                            font-size: 14px !important;
                            z-index: 2147483647 !important;
                            box-shadow: 0 4px 12px rgba(0,0,0,0.2) !important;
                            max-width: 80% !important;
                            line-height: 1.4 !important;
                            display: none;
                            font-family: sans-serif !important;
                        }
                        #uni-fix-tooltip b {
                            color: #2E7D32;
                            display: block;
                            margin-bottom: 4px;
                        }
                    `;
                    document.head.appendChild(style);
                }
                
                var menu = document.getElementById('uni-selection-menu');
                if (!menu) {
                    menu = document.createElement('div');
                    menu.id = 'uni-selection-menu';
                    
                    var btnHighlight = document.createElement('button');
                    btnHighlight.id = 'uni-highlight-btn';
                    btnHighlight.className = 'uni-menu-btn';
                    btnHighlight.innerText = 'Пометить';
                    menu.appendChild(btnHighlight);
                    
                    var btnFix = document.createElement('button');
                    btnFix.id = 'uni-fix-btn';
                    btnFix.className = 'uni-menu-btn';
                    btnFix.innerText = 'Исправить';
                    menu.appendChild(btnFix);
                    
                    document.body.appendChild(menu);
                    
                    menu.onmousedown = function(e) {
                        e.preventDefault();
                        e.stopPropagation();
                    };
                    
                    btnHighlight.onclick = function(e) {
                        console.log('UniReader: Highlight clicked');
                        if (this.getAttribute('data-mode') === 'delete') {
                            var id = this.getAttribute('data-target-id');
                            if (id) AndroidReader.deleteHighlight(id);
                            window.getSelection().removeAllRanges();
                        } else {
                            window.getSelectionDetails();
                        }
                    };
                    
                    btnFix.onclick = function(e) {
                        console.log('UniReader: Fix clicked');
                        var sel = window.getSelection();
                        var text = sel.toString();
                        if (text) {
                            var range = sel.getRangeAt(0);
                            
                            // 1. Capture positioning data IMMEDIATELY
                            var container = range.commonAncestorContainer;
                            if (container.nodeType === 3) container = container.parentNode;
                            var el = container.closest('[data-idx]');
                            if (el) {
                                var idx = parseInt(el.getAttribute('data-idx'));
                                var preRange = document.createRange();
                                preRange.selectNodeContents(el);
                                preRange.setEnd(range.startContainer, range.startOffset);
                                var start = preRange.toString().length;
                                var end = start + text.length;
                                
                                var sectionEl = el.closest('section');
                                var spineIndex = sectionEl ? parseInt(sectionEl.getAttribute('data-index')) : -1;

                                // 2. Get context (1000 chars before/after)
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
                                
                                var context = contextLeft + text + contextRight;
                                
                                var hotpoints = [];
                                var fragment = range.cloneContents();
                                var tempDiv = document.createElement('div');
                                tempDiv.appendChild(fragment);
                                tempDiv.querySelectorAll('.uni-highlight').forEach(h => {
                                    hotpoints.push(h.innerText);
                                });

                                var data = {
                                    text: text,
                                    context: context,
                                    hotpoints: hotpoints,
                                    spineIndex: spineIndex,
                                    elementIdx: idx,
                                    startOffset: start,
                                    endOffset: end
                                };
                                console.log('UniReader: Sending fix data with positions', data);
                                AndroidReader.fixText(JSON.stringify(data));
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
                        if (tooltip.style.display === 'block' && !tooltip.contains(e.target)) {
                            tooltip.style.display = 'none';
                        }
                    });
                }
                
                var items = document.querySelectorAll('p, h1, h2, h3, h4, h5, h6, li, img');
                for (var i=0; i<items.length; i++) {
                    items[i].setAttribute('data-idx', i);
                }
                
                window.getSelectionDetails = function(isReplacement, replacementText) {
                    console.log('UniReader: getSelectionDetails called', {isReplacement, replacementText});
                    var sel = window.getSelection();
                    if (sel.rangeCount === 0) {
                        console.warn('UniReader: No selection range found');
                        return;
                    }
                    var range = sel.getRangeAt(0);
                    var container = range.commonAncestorContainer;
                    if (container.nodeType === 3) container = container.parentNode;
                    var el = container.closest('[data-idx]');
                    if (!el) {
                        console.warn('UniReader: No element with data-idx found near selection');
                        return;
                    }
                    
                    var idx = parseInt(el.getAttribute('data-idx'));
                    var preRange = document.createRange();
                    preRange.selectNodeContents(el);
                    preRange.setEnd(range.startContainer, range.startOffset);
                    var start = preRange.toString().length;
                    var end = start + range.toString().length;
                    
                    var sectionEl = el.closest('section');
                    if (!sectionEl) {
                        console.warn('UniReader: No section found for element');
                        return;
                    }

                    var data = {
                        spineIndex: parseInt(sectionEl.getAttribute('data-index')),
                        elementIdx: idx,
                        startOffset: start,
                        endOffset: end,
                        text: range.toString(),
                        replacementText: isReplacement ? replacementText : null
                    };
                    console.log('UniReader: Preparing to send data to Android', data);
                    AndroidReader.saveHighlight(JSON.stringify(data));
                    sel.removeAllRanges();
                    var menu = document.getElementById('uni-selection-menu');
                    if (menu) menu.style.display = 'none';
                };
                
                if (window.uniSelectionListener) {
                    document.removeEventListener('selectionchange', window.uniSelectionListener);
                }
                
                window.uniSelectionListener = function() {
                    try {
                        var sel = window.getSelection();
                        var menu = document.getElementById('uni-selection-menu');
                        if (!menu) return;

                        if (sel.isCollapsed || sel.rangeCount === 0) {
                            menu.style.display = 'none';
                            return;
                        }
                        
                        var range = sel.getRangeAt(0);
                        var container = range.commonAncestorContainer;
                        if (container.nodeType === 3) container = container.parentNode;
                        
                        var btnHighlight = document.getElementById('uni-highlight-btn');
                        var btnFix = document.getElementById('uni-fix-btn');
                        if (!btnHighlight || !btnFix) return;

                        var existingMark = container.closest('.uni-highlight, .uni-fix');
                        if (existingMark) {
                            btnHighlight.innerText = 'Удалить';
                            btnHighlight.setAttribute('data-mode', 'delete');
                            btnHighlight.setAttribute('data-target-id', existingMark.getAttribute('data-id'));
                            btnFix.style.display = 'none';
                        } else {
                            btnHighlight.innerText = 'Пометить';
                            btnHighlight.setAttribute('data-mode', 'save');
                            btnFix.style.display = 'block';
                        }

                        var rect = range.getBoundingClientRect();
                        
                        // Handle multi-line/complex rects
                        if (rect.width === 0 && range.getClientRects().length > 0) {
                            rect = range.getClientRects()[0];
                        }

                        if (rect.width > 0 && rect.height > 0) {
                            menu.style.display = 'flex';
                            var menuWidth = menu.offsetWidth || 200;
                            var menuHeight = menu.offsetHeight || 40;
                            
                            // Center horizontally
                            var left = rect.left + (rect.width / 2) - (menuWidth / 2);
                            left = Math.max(10, Math.min(window.innerWidth - menuWidth - 10, left));
                            
                            // Position above the selection (top) with some space
                            var top = rect.top - menuHeight - 20; 
                            
                            // If too close to the top edge, move it below the selection
                            if (top < 10) {
                                top = rect.bottom + 20;
                            }
                            
                            menu.style.left = left + 'px';
                            menu.style.top = top + 'px';
                        } else {
                            menu.style.display = 'none';
                        }
                    } catch (err) {
                        console.error('UniReader: selectionchange error', err);
                    }
                };
                
                document.addEventListener('selectionchange', window.uniSelectionListener);
                
                // Disable native context menu to be doubly sure
                document.oncontextmenu = function(e) {
                    if (window.getSelection().toString().length > 0) {
                        e.preventDefault();
                    }
                };

                window.applyHighlights = function(json) {
                    console.log('UniReader: Applying highlights', json);
                    var data;
                    try {
                        data = JSON.parse(json);
                    } catch(e) {
                        console.error('UniReader: JSON parse error', e);
                        return;
                    }
                    
                    var spineIndex = data.spineIndex;
                    var highlights = data.highlights;
                    
                    var section = document.querySelector('section[data-index="' + spineIndex + '"]');
                    if (!section) return;

                    // Clear existing highlights AND fixes ONLY within this section
                    section.querySelectorAll('.uni-highlight, .uni-fix').forEach(m => {
                        var p = m.parentNode;
                        while(m.firstChild) p.insertBefore(m.firstChild, m);
                        p.removeChild(m);
                    });
                    section.normalize();

                    highlights.forEach(h => {
                        var el = section.querySelector('[data-idx="' + h.elementIdx + '"]');
                        if (!el) return;
                        
                        var walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT, null, false);
                        var current = 0;
                        var startNode, startOffset, endNode, endOffset;
                        
                        while(walker.nextNode()) {
                            var node = walker.currentNode;
                            var len = node.textContent.length;
                            if (!startNode && current + len >= h.startOffset) {
                                startNode = node;
                                startOffset = h.startOffset - current;
                            }
                            if (startNode && !endNode && current + len >= h.endOffset) {
                                endNode = node;
                                endOffset = h.endOffset - current;
                                break;
                            }
                            current += len;
                        }
                        
                        if (startNode && endNode) {
                            var range = document.createRange();
                            range.setStart(startNode, startOffset);
                            range.setEnd(endNode, endOffset);
                            
                            var mark = document.createElement('mark');
                            var isFix = h.replacementText && h.replacementText.length > 0;
                            mark.setAttribute('class', isFix ? 'uni-fix' : 'uni-highlight');
                            if (!isFix) mark.style.backgroundColor = h.color;
                            mark.setAttribute('data-id', h.id);
                            if (isFix) mark.setAttribute('data-replacement', h.replacementText);
                            
                            try {
                                range.surroundContents(mark);
                            } catch (e) {
                                console.warn('UniReader: Complex range fallback', e);
                                var contents = range.extractContents();
                                mark.appendChild(contents);
                                range.insertNode(mark);
                            }
                        }
                    });
                };
                
                document.body.addEventListener('click', function(e) {
                    var fix = e.target.closest('.uni-fix');
                    if (fix) {
                        e.preventDefault();
                        e.stopPropagation();
                        var replacement = fix.getAttribute('data-replacement');
                        if (replacement) {
                            var rect = fix.getBoundingClientRect();
                            tooltip.innerHTML = '<b>Исправленный вариант:</b>' + replacement;
                            tooltip.style.display = 'block';
                            
                            var tWidth = tooltip.offsetWidth;
                            var left = rect.left + (rect.width / 2) - (tWidth / 2);
                            left = Math.max(10, Math.min(window.innerWidth - tWidth - 10, left));
                            var top = rect.top - tooltip.offsetHeight - 10;
                            if (top < 10) top = rect.bottom + 10;
                            
                            tooltip.style.left = left + 'px';
                            tooltip.style.top = top + 'px';
                        }
                        return;
                    }

                    var img = e.target.closest('img');
                    if (img && img.getAttribute('src')) {
                        e.preventDefault();
                        e.stopPropagation();
                        AndroidReader.openImage(img.getAttribute('src'));
                        return;
                    }
                    var a = e.target.closest('a');
                    if (a && a.getAttribute('href')) {
                        var href = a.getAttribute('href');
                        if (href.startsWith('#') || href.indexOf('://') === -1 || href.startsWith('epub://')) {
                            e.preventDefault();
                            var absolute = a.href;
                            AndroidReader.onLinkClicked(absolute);
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
            pendingElementIndex = -1
            pendingCharOffset = -1
            pendingAnchor = fragment
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
        Log.d("Reader", "initPagedView")
        val isDarkMode = settings.isDarkMode
        val bgColor = if (isDarkMode) "#000000" else "#FFFFFF"

        val html = """
            <!DOCTYPE html>
            <html style="background-color: $bgColor;">
            <head>
                <style id="reader-style">
                    mark.uni-highlight {
                        background-color: #ffeb3b !important;
                        color: #000 !important;
                        border-radius: 2px;
                    }
                    [data-theme="dark"] mark.uni-highlight {
                        background-color: #f57f17 !important;
                        color: #fff !important;
                    }
                </style>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            </head>
            <body data-mode="paged" style="background-color: $bgColor !important; margin: 0; padding: 0;">
                <div id="snap-ribbon"></div>
                <div id="chapters-container"></div>
                <script type="text/javascript">
                    // CSS Scroll Snap markers: rebuild ribbon of snap points after content changes
                    function updateSnapMarkers() {
                        var ribbon = document.getElementById('snap-ribbon');
                        if (!ribbon) return;
                        var totalWidth = document.documentElement.scrollWidth;
                        var pageWidth = document.documentElement.getBoundingClientRect().width;
                        if (pageWidth <= 0) return;
                        var pageCount = Math.max(1, Math.round(totalWidth / pageWidth));
                        ribbon.innerHTML = '';
                        for (var i = 0; i < pageCount; i++) {
                            var marker = document.createElement('div');
                            marker.className = 'snap-point';
                            ribbon.appendChild(marker);
                        }
                    }

                    window.addEventListener('resize', updateSnapMarkers);

                    var edgeCheckTimer = null;
                    var isLoadingTop = false;
                    var isLoadingBottom = false;
                    var wasInContent = false;

                    var lastReportedIdx = -1;
                    var lastReportedPage = -1;

                    window.addEventListener('scroll', function() {
                        var sw = document.documentElement.scrollWidth;
                        var pw = document.documentElement.getBoundingClientRect().width;
                        var sl = window.pageXOffset;

                        var sections = [...document.querySelectorAll('section')];
                        var active = null;
                        var activeIdx = -1;
                        
                        for (var i = 0; i < sections.length; i++) {
                            var r = sections[i].getBoundingClientRect();
                            if (r.left <= 20 && r.right > 20) {
                                active = sections[i];
                                activeIdx = i;
                                break;
                            }
                        }

                        if (active) {
                            var sectionStart = active.offsetLeft;
                            var sectionWidth = (activeIdx < sections.length - 1) ? 
                                               sections[activeIdx+1].offsetLeft - sectionStart : 
                                               sw - sectionStart;
                            
                            var localSl = sl - sectionStart;
                            var page = Math.max(0, Math.floor((localSl + 5) / pw));
                            var totalPages = Math.max(1, Math.round(sectionWidth / pw));
                            var idx = parseInt(active.getAttribute('data-index'));

                            if (idx !== lastReportedIdx || page !== lastReportedPage) {
                                lastReportedIdx = idx;
                                lastReportedPage = page;
                                AndroidReader.onChapterEntered(idx);
                                AndroidReader.onProgressUpdate(idx, page, totalPages);
                            }
                        }

                        clearTimeout(edgeCheckTimer);
                        edgeCheckTimer = setTimeout(function() {
                            var sw2 = document.documentElement.scrollWidth;
                            var pw2 = document.documentElement.getBoundingClientRect().width;
                            var sl2 = window.pageXOffset;
                            console.log('EDGE_TIMER: sl=' + sl2 + ' sw=' + sw2 + ' pw=' + pw2);

                            if (sl2 <= 20) {
                                if (!isLoadingTop && wasInContent) {
                                    isLoadingTop = true;
                                    AndroidReader.onReachedTop();
                                }
                            } else if (sl2 + pw2 >= sw2 - 20) {
                                if (!isLoadingBottom && wasInContent) {
                                    isLoadingBottom = true;
                                    AndroidReader.onReachedBottom();
                                }
                            } else if (sl2 > pw2) {
                                isLoadingTop = false;
                                isLoadingBottom = false;
                                wasInContent = true;
                            }
                        }, 700);
                    });

                    function appendChapter(index, html, targetIdx, targetOffset, lang, jumpToLast, anchor, scrollToNew, stickToIndex) {
                        var container = document.getElementById('chapters-container');
                        if (document.getElementById('chapter-' + index)) return;
                        console.log('APPEND: index=' + index + ' containerLen=' + container.children.length);
                        
                        var section = document.createElement('section');
                        section.id = 'chapter-' + index;
                        section.setAttribute('data-index', index);
                        if (lang) section.setAttribute('lang', lang);
                        section.innerHTML = html;
                        
                        var items = section.querySelectorAll('p, h1, h2, h3, h4, h5, h6, li, img');
                        for (var i=0; i<items.length; i++) items[i].setAttribute('data-idx', i);
                        
                        document.documentElement.style.scrollSnapType = 'none';
                        container.appendChild(section);
                        while (container.children.length > 3) {
                            container.removeChild(container.firstChild);
                        }
                        updateSnapMarkers();
                        document.documentElement.style.scrollSnapType = 'x mandatory';
                        
                        if (jumpToLast || anchor || targetIdx >= 0) {
                            var retry = 0;
                            function syncIdxScroll() {
                                var pw = document.documentElement.getBoundingClientRect().width;
                                var sw = document.documentElement.scrollWidth;
                                if (sw > pw || retry > 40) {
                                    if (jumpToLast) {
                                        var rect = section.getBoundingClientRect();
                                        var lastPageInDoc = Math.floor((window.pageXOffset + rect.right - 5) / pw);
                                        window.scrollTo(lastPageInDoc * pw, 0);
                                    } else if (anchor) {
                                        var target = document.getElementById(anchor) || document.getElementsByName(anchor)[0];
                                        if (target) {
                                            var rect = target.getBoundingClientRect();
                                            var page = Math.floor((window.pageXOffset + rect.left + 5) / pw);
                                            window.scrollTo(page * pw, 0);
                                        }
                                    } else if (targetIdx >= 0) {
                                        var target = section.querySelector('[data-idx="' + targetIdx + '"]');
                                        if (target) {
                                            if (targetOffset > 0) {
                                                var current = 0;
                                                var foundNode = null;
                                                var localOffset = 0;
                                                var walker = document.createTreeWalker(target, NodeFilter.SHOW_TEXT, null, false);
                                                while (walker.nextNode()) {
                                                    var len = walker.currentNode.textContent.length;
                                                    if (current + len >= targetOffset) {
                                                        foundNode = walker.currentNode;
                                                        localOffset = targetOffset - current;
                                                        break;
                                                    }
                                                    current += len;
                                                }
                                                if (foundNode) {
                                                    var range = document.createRange();
                                                    range.setStart(foundNode, localOffset);
                                                    range.setEnd(foundNode, Math.min(localOffset + 1, foundNode.textContent.length));
                                                    var rect = range.getBoundingClientRect();
                                                    var page = Math.floor((window.pageXOffset + rect.left + 5) / pw);
                                                    window.scrollTo(page * pw, 0);
                                                } else {
                                                    var rect = target.getBoundingClientRect();
                                                    var page = Math.floor((window.pageXOffset + rect.left + 5) / pw);
                                                    window.scrollTo(page * pw, 0);
                                                }
                                            } else {
                                                var rect = target.getBoundingClientRect();
                                                var page = Math.floor((window.pageXOffset + rect.left + 5) / pw);
                                                window.scrollTo(page * pw, 0);
                                            }
                                        }
                                    }
                                } else {
                                    retry++;
                                    setTimeout(syncIdxScroll, 50);
                                }
                            }
                            syncIdxScroll();
                        } else if (scrollToNew) {
                            var pw = document.documentElement.getBoundingClientRect().width;
                            window.scrollTo(window.pageXOffset + section.getBoundingClientRect().left, 0);
                        } else if (stickToIndex >= 0) {
                            var pw = document.documentElement.getBoundingClientRect().width;
                            var keptSection = document.querySelector('section[data-index="' + stickToIndex + '"]');
                            if (keptSection) {
                                var rect = keptSection.getBoundingClientRect();
                                var lastPage = Math.floor((window.pageXOffset + rect.right - 5) / pw);
                                window.scrollTo(lastPage * pw, 0);
                            }
                        }
                        isLoadingTop = false;
                        isLoadingBottom = false;
                        clearTimeout(edgeCheckTimer);
                    }

                    function prependChapter(index, html, lang, goToNew, keepIndex) {
                        var container = document.getElementById('chapters-container');
                        if (document.getElementById('chapter-' + index)) return;
                        console.log('PREPEND: index=' + index + ' containerLen=' + container.children.length);
                        
                        var section = document.createElement('section');
                        section.id = 'chapter-' + index;
                        section.setAttribute('data-index', index);
                        if (lang) section.setAttribute('lang', lang);
                        section.innerHTML = html;
                        
                        var items = section.querySelectorAll('p, h1, h2, h3, h4, h5, h6, li, img');
                        for (var i=0; i<items.length; i++) items[i].setAttribute('data-idx', i);
                        
                        document.documentElement.style.scrollSnapType = 'none';
                        var oldWidth = document.documentElement.scrollWidth;
                        container.insertBefore(section, container.firstChild);
                        while (container.children.length > 3) {
                            container.removeChild(container.lastChild);
                        }
                        var newWidth = document.documentElement.scrollWidth;
                        clearTimeout(edgeCheckTimer);

                        if (keepIndex >= 0) {
                            var pw = document.documentElement.getBoundingClientRect().width;
                            var keptSection = document.querySelector('section[data-index="' + keepIndex + '"]');
                            if (keptSection) {
                                window.scrollTo(window.pageXOffset + keptSection.getBoundingClientRect().left, 0);
                            } else {
                                window.scrollBy(newWidth - oldWidth, 0);
                            }
                        } else if (goToNew) {
                            var pw = document.documentElement.getBoundingClientRect().width;
                            var rect = section.getBoundingClientRect();
                            var lastPage = Math.floor((window.pageXOffset + rect.right - 5) / pw);
                            window.scrollTo(lastPage * pw, 0);
                        } else {
                            window.scrollBy(newWidth - oldWidth, 0);
                        }
                        isLoadingTop = false;
                        isLoadingBottom = false;

                        requestAnimationFrame(function() {
                            requestAnimationFrame(function() {
                                updateSnapMarkers();
                                document.documentElement.style.scrollSnapType = 'x mandatory';
                            });
                        });
                    }

                    function scrollToPosition(chapterIdx, targetIdx, targetOffset) {
                        var section = document.getElementById('chapter-' + chapterIdx);
                        if (!section) return;
                        
                        var pw = document.documentElement.getBoundingClientRect().width;
                        if (pw <= 0) return;

                        if (targetIdx >= 0) {
                            var target = section.querySelector('[data-idx="' + targetIdx + '"]');
                            if (target) {
                                if (targetOffset > 0) {
                                    var current = 0;
                                    var foundNode = null;
                                    var localOffset = 0;
                                    var walker = document.createTreeWalker(target, NodeFilter.SHOW_TEXT, null, false);
                                    while (walker.nextNode()) {
                                        var len = walker.currentNode.textContent.length;
                                        if (current + len >= targetOffset) {
                                            foundNode = walker.currentNode;
                                            localOffset = targetOffset - current;
                                            break;
                                        }
                                        current += len;
                                    }
                                    if (foundNode) {
                                        var range = document.createRange();
                                        range.setStart(foundNode, localOffset);
                                        range.setEnd(foundNode, Math.min(localOffset + 1, foundNode.textContent.length));
                                        var rect = range.getBoundingClientRect();
                                        // We need to disable snap before scrolling to be sure
                                        document.documentElement.style.scrollSnapType = 'none';
                                        var page = Math.floor((window.pageXOffset + rect.left + 5) / pw);
                                        window.scrollTo(page * pw, 0);
                                        document.documentElement.style.scrollSnapType = 'x mandatory';
                                    } else {
                                        var rect = target.getBoundingClientRect();
                                        document.documentElement.style.scrollSnapType = 'none';
                                        var page = Math.floor((window.pageXOffset + rect.left + 5) / pw);
                                        window.scrollTo(page * pw, 0);
                                        document.documentElement.style.scrollSnapType = 'x mandatory';
                                    }
                                } else {
                                    var rect = target.getBoundingClientRect();
                                    document.documentElement.style.scrollSnapType = 'none';
                                    var page = Math.floor((window.pageXOffset + rect.left + 5) / pw);
                                    window.scrollTo(page * pw, 0);
                                    document.documentElement.style.scrollSnapType = 'x mandatory';
                                }
                            }
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

        webView.loadDataWithBaseURL("epub://paged/", html, "text/html", "UTF-8", null)
    }

    private fun loadInitialPagedChapters() {
        val useCache = lastKnownPosition != null && lastKnownPosition?.first == currentSpineIndex
        val finalPos = if (useCache) lastKnownPosition!! else Triple(currentSpineIndex, pendingElementIndex, pendingCharOffset)
        
        val idxToUse = finalPos.second
        val offsetToUse = finalPos.third
        val anchorToUse = pendingAnchor
        val jumpToLast = shouldJumpToLastPage

        pendingElementIndex = -1
        pendingCharOffset = -1
        pendingAnchor = null
        shouldJumpToLastPage = false

        chaptersToLoad = 3
        isJumpingToChapter = true

        fun onChapterDone() {
            chaptersToLoad--
            if (chaptersToLoad <= 0) {
                isJumpingToChapter = false
            }
        }

        loadAndAppendChapter(finalPos.first, idxToUse, offsetToUse, jumpToLast, anchorToUse) { onChapterDone() }
        loadAndPrependChapter(finalPos.first - 1) { onChapterDone() }
        loadAndAppendChapter(finalPos.first + 1) { onChapterDone() }
    }

    private fun initSeamlessScroll() {
        val isDarkMode = settings.isDarkMode
        val bgColor = if (isDarkMode) "#000000" else "#FFFFFF"
        
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <style id="reader-style">
                    mark.uni-highlight {
                        background-color: #ffeb3b !important;
                        color: #000 !important;
                        border-radius: 2px;
                    }
                    [data-theme="dark"] mark.uni-highlight {
                        background-color: #f57f17 !important;
                        color: #fff !important;
                    }
                </style>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            </head>
            <body style="background-color: $bgColor !important;">
                <div id="chapters-container"></div>
                <script type="text/javascript">
                    var observer = new IntersectionObserver(function(entries) {
                        entries.forEach(function(entry) {
                            if (entry.isIntersecting) {
                                if (entry.target.id === 'bottom-sentinel') {
                                    AndroidReader.onReachedBottom();
                                } else if (entry.target.id === 'top-sentinel') {
                                    AndroidReader.onReachedTop();
                                }
                            }
                        });
                    }, { threshold: 0.1 });
                    
                    window.addEventListener('scroll', function() {
                        var sections = [...document.querySelectorAll('section')];
                        var active = sections.find(s => {
                            var r = s.getBoundingClientRect();
                            return r.top <= 150 && r.bottom > 150;
                        });
                        if (active) {
                            AndroidReader.onChapterEntered(parseInt(active.getAttribute('data-index')));
                        }
                    });

                    function appendChapter(index, html, targetIdx, targetOffset, lang) {
                        var container = document.getElementById('chapters-container');
                        if (document.getElementById('chapter-' + index)) return;
                        
                        var section = document.createElement('section');
                        section.id = 'chapter-' + index;
                        section.setAttribute('data-index', index);
                        if (lang) section.setAttribute('lang', lang);
                        section.innerHTML = html;
                        
                        var items = section.querySelectorAll('p, h1, h2, h3, h4, h5, h6, li, img');
                        for (var i=0; i<items.length; i++) items[i].setAttribute('data-idx', i);
                        
                        var oldBot = document.getElementById('bottom-sentinel');
                        if (oldBot) { observer.unobserve(oldBot); oldBot.remove(); }
                        
                        container.appendChild(section);
                        
                        var sentinel = document.createElement('div');
                        sentinel.id = 'bottom-sentinel';
                        sentinel.style.height = '100px';
                        sentinel.style.width = '100%';
                        container.appendChild(sentinel);
                        observer.observe(sentinel);
                        
                        if (targetIdx >= 0) {
                            var retry = 0;
                            function syncIdxScroll() {
                                var target = section.querySelector('[data-idx="' + targetIdx + '"]');
                                if (target || retry > 40) {
                                    if (target) {
                                        if (targetOffset <= 0) {
                                            window.scrollTo(0, target.offsetTop);
                                        } else {
                                            var current = 0;
                                            var foundNode = null;
                                            var localOffset = 0;
                                            var walker = document.createTreeWalker(target, NodeFilter.SHOW_TEXT, null, false);
                                            while (walker.nextNode()) {
                                                var len = walker.currentNode.textContent.length;
                                                if (current + len >= targetOffset) {
                                                    foundNode = walker.currentNode;
                                                    localOffset = targetOffset - current;
                                                    break;
                                                }
                                                current += len;
                                            }
                                            if (foundNode) {
                                                var range = document.createRange();
                                                range.setStart(foundNode, localOffset);
                                                range.setEnd(foundNode, Math.min(localOffset + 1, foundNode.textContent.length));
                                                var rect = range.getBoundingClientRect();
                                                window.scrollTo(0, window.pageYOffset + rect.top - 60);
                                            } else {
                                                window.scrollTo(0, target.offsetTop);
                                            }
                                        }
                                    }
                                } else {
                                    retry++;
                                    setTimeout(syncIdxScroll, 50);
                                }
                            }
                            syncIdxScroll();
                        }
                    }

                    function prependChapter(index, html, lang) {
                        var container = document.getElementById('chapters-container');
                        if (document.getElementById('chapter-' + index)) return;
                        
                        var section = document.createElement('section');
                        section.id = 'chapter-' + index;
                        section.setAttribute('data-index', index);
                        if (lang) section.setAttribute('lang', lang);
                        section.innerHTML = html;
                        
                        var items = section.querySelectorAll('p, h1, h2, h3, h4, h5, h6, li, img');
                        for (var i=0; i<items.length; i++) items[i].setAttribute('data-idx', i);
                        
                        var oldTop = document.getElementById('top-sentinel');
                        if (oldTop) { observer.unobserve(oldTop); oldTop.remove(); }

                        var oldHeight = container.scrollHeight;
                        container.insertBefore(section, container.firstChild);

                        var newHeight = container.scrollHeight;
                        window.scrollBy(0, newHeight - oldHeight);
                        
                        var sentinel = document.createElement('div');
                        sentinel.id = 'top-sentinel';
                        sentinel.style.height = '100px';
                        sentinel.style.width = '100%';
                        container.insertBefore(sentinel, container.firstChild);
                        observer.observe(sentinel);
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
        
        lastAppendedIndex = -1
        firstPrependedIndex = Int.MAX_VALUE
        isChapterLoading = false
        
        val useCache = lastKnownPosition != null && lastKnownPosition?.first == currentSpineIndex
        val finalPos = if (useCache) lastKnownPosition!! else Triple(currentSpineIndex, pendingElementIndex, pendingCharOffset)
        
        val idxToUse = finalPos.second
        val offsetToUse = finalPos.third
        
        pendingElementIndex = -1
        pendingCharOffset = -1
        
        webView.loadDataWithBaseURL("epub://seamless/", html, "text/html", "UTF-8", null)
        
        webView.postDelayed({
            isJumpingToChapter = true
            // Load target chapter FIRST
            loadAndAppendChapter(finalPos.first, idxToUse, offsetToUse) {
                // Once target is loaded, load neighbors
                loadAndPrependChapter(finalPos.first - 1, stayOnCurrent = true) {
                    loadAndAppendChapter(finalPos.first + 1, stickToCurrent = true) {
                        isJumpingToChapter = false
                    }
                }
            }
        }, 500)
    }

    private fun loadAndAppendChapter(
        index: Int,
        targetIdx: Int = -1,
        targetOffset: Int = -1,
        jumpToLast: Boolean = false,
        anchor: String? = null,
        scrollToNew: Boolean = false,
        stickToCurrent: Boolean = false,
        onFinished: (() -> Unit)? = null
    ) {
        Log.d("Reader", "loadAndAppendChapter: index=$index, lastAppended=$lastAppendedIndex, spineSize=${epubBook?.spine?.size}")
        val loader = chapterLoader ?: return
        if (index < 0 || index >= (epubBook?.spine?.size ?: 0) || index <= lastAppendedIndex) {
            Log.d("Reader", "loadAndAppendChapter SKIPPED: index=$index out of range or already loaded")
            onFinished?.invoke()
            return
        }
        
        isChapterLoading = true
        val content = loader.loadChapterHtml(index) ?: run {
            isChapterLoading = false
            onFinished?.invoke()
            return
        }
        
        if (lastAppendedIndex == -1) firstPrependedIndex = index
        lastAppendedIndex = index
        
        val escapedHtml = content.html.replace("`", "\\`").replace("$", "\\$")
        val langArg = if (content.lang != null) "'${content.lang}'" else "null"
        val anchorArg = if (anchor != null) "'$anchor'" else "null"
        val scrollToNewArg = scrollToNew.toString()
        val stickToIndexArg = if (stickToCurrent) currentSpineIndex.toString() else "-1"
        webView.evaluateJavascript("appendChapter($index, `$escapedHtml`, $targetIdx, $targetOffset, $langArg, $jumpToLast, $anchorArg, $scrollToNewArg, $stickToIndexArg);") {
            isChapterLoading = false
            webView.evaluateJavascript("applyHighlights('${getHighlightsJson(index)}')", null)
            onFinished?.invoke()
        }
    }

    private fun loadAndPrependChapter(index: Int, stayOnCurrent: Boolean = false, onFinished: (() -> Unit)? = null) {
        val loader = chapterLoader ?: return
        if (index < 0 || index >= (epubBook?.spine?.size ?: 0) || index >= firstPrependedIndex) {
            onFinished?.invoke()
            return
        }
        
        isChapterLoading = true
        val content = loader.loadChapterHtml(index) ?: run {
            isChapterLoading = false
            onFinished?.invoke()
            return
        }
        
        firstPrependedIndex = index
        val escapedHtml = content.html.replace("`", "\\`").replace("$", "\\$")
        val langArg = if (content.lang != null) "'${content.lang}'" else "null"
        val goToNewArg = stayOnCurrent.toString()
        val keepIndexArg = if (stayOnCurrent) currentSpineIndex.toString() else "-1"
        webView.evaluateJavascript("prependChapter($index, `$escapedHtml`, $langArg, $goToNewArg, $keepIndexArg);") {
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

    private fun loadSpineItem(index: Int, jumpToLast: Boolean = false) {
        lastKnownPosition = null // CLEAR CACHE on intentional jump
        currentSpineIndex = index
        shouldJumpToLastPage = jumpToLast
        updateChapterTitle()
        
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
        tvFixResult.text = "Создание задачи..."
        tvFixModel.visibility = View.GONE
        
        lifecycleScope.launch(Dispatchers.IO) {
            fixService.improveText(
                text = text,
                context = context,
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
                        tvFixResult.text = "Ошибка: $error"
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
                bookUri = epubBook?.uri.toString(),
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
            Toast.makeText(this, "Error saving", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performSave(destinationUri: Uri) {
        val book = epubBook ?: return
        val bookUriString = book.uri.toString()
        val pendingFixes = highlightDb.getPendingFixes(bookUriString)
        
        if (pendingFixes.isEmpty()) {
            Toast.makeText(this, "No pending fixes to save", Toast.LENGTH_SHORT).show()
            return
        }

        val progressDialog = android.app.ProgressDialog(this).apply {
            setMessage("Saving improved copy...")
            setCancelable(false)
            show()
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val success = EpubModifier(this@ReaderActivity).applyFixes(book, pendingFixes, destinationUri)
            
            withContext(Dispatchers.Main) {
                progressDialog.dismiss()
                if (success) {
                    highlightDb.deleteFixes(bookUriString)
                    Toast.makeText(this@ReaderActivity, "Saved successfully", Toast.LENGTH_LONG).show()
                    
                    // Note: We don't reload here because we saved to a NEW file.
                    // The current reader is still pointing to the original URI.
                } else {
                    Toast.makeText(this@ReaderActivity, "Error saving file", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
