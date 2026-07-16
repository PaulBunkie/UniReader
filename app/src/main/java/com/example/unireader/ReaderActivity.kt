package com.example.unireader

import android.annotation.SuppressLint
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
import android.widget.TextView
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
import com.google.android.material.appbar.AppBarLayout
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

    lateinit var settings: ReaderSettings
    private lateinit var gestureDetector: GestureDetector
    private var isAdjustingBrightness = false
    
    private val hideBrightnessRunnable = Runnable { 
        findViewById<View>(R.id.tvBrightnessHint)?.visibility = View.GONE 
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        settings = ReaderSettings.load(this)
        
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

        // STATE RESTORATION
        val uriString = savedInstanceState?.getString("epub_uri") ?: intent.getStringExtra("epub_uri")
        currentSpineIndex = savedInstanceState?.getInt("spine_index", 0) ?: 0
        pendingElementIndex = savedInstanceState?.getInt("element_index", -1) ?: -1
        pendingCharOffset = savedInstanceState?.getInt("char_offset", -1) ?: -1
        pendingAnchor = savedInstanceState?.getString("anchor")
        isFullscreenPref = savedInstanceState?.getBoolean("fullscreen", false) ?: false
        isUiOverlayVisible = savedInstanceState?.getBoolean("ui_visible", true) ?: !isFullscreenPref

        if (uriString != null) {
            val uri = uriString.toUri()
            epubBook = EpubParser(this).parse(uri)
            epubBook?.let { book ->
                chapterLoader = ChapterLoader(this, book)
                updateBookTitles()
                
                // If it's a fresh open (no pending index from saveState), check LibraryProvider
                if (savedInstanceState == null) {
                    val libraryProvider = LibraryProvider(this)
                    val savedBook = libraryProvider.getBooks().find { it.uri == uriString }
                    if (savedBook != null) {
                        currentSpineIndex = savedBook.lastSpineIndex
                        pendingElementIndex = savedBook.lastElementIndex
                        pendingCharOffset = savedBook.lastCharOffset
                        pendingAnchor = savedBook.lastAnchor
                    }
                }
                
                loadSpineItem(currentSpineIndex)
            }
        }

        updateUiState()
        updateWebViewPadding()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        intent.getStringExtra("epub_uri")?.let { outState.putString("epub_uri", it) }
        outState.putInt("spine_index", currentSpineIndex)
        outState.putInt("element_index", pendingElementIndex)
        outState.putInt("char_offset", pendingCharOffset)
        outState.putString("anchor", pendingAnchor)
        outState.putBoolean("fullscreen", isFullscreenPref)
        outState.putBoolean("ui_visible", isUiOverlayVisible)
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
            findViewById<TextView>(R.id.tvChapterTitle)?.text = item.href
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
                    TOCSheet(book.toc) { href ->
                        handleInternalLink("epub://$href")
                    }.show(supportFragmentManager, "toc")
                }
                true
            }
            R.id.action_settings -> {
                ReaderSettingsSheet().show(supportFragmentManager, "settings")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun toggleFullscreenExternally(enabled: Boolean) {
        isFullscreenPref = enabled
        isUiOverlayVisible = !isFullscreenPref
        updateUiState()
    }

    fun setReadingMode(paged: Boolean) {
        if (isPagedMode == paged) return
        
        captureCurrentPosition { pos ->
            isPagedMode = paged
            pendingElementIndex = pos.first
            pendingCharOffset = pos.second
            
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
        saveReadingPosition()
    }

    private fun saveReadingPosition() {
        val uri = intent.getStringExtra("epub_uri") ?: return
        captureCurrentPosition { pos ->
            val libraryProvider = LibraryProvider(this)
            libraryProvider.updateBookProgress(uri, currentSpineIndex, pos.first, pos.second, null)
        }
    }

    private fun captureCurrentPosition(onCaptured: (Pair<Int, Int>) -> Unit) {
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
                var sl = window.pageXOffset || 0;
                var mode = document.body.getAttribute('data-mode') || 'scroll';
                
                // For paged mode, we scan the viewport
                var startY = 60; 
                var endY = 500;
                
                for (var y = startY; y < endY; y += 40) {
                    var found = document.elementFromPoint(pw / 2, y);
                    if (!found) continue;
                    
                    var target = found.closest('p, h1, h2, h3, h4, h5, h6, li, img');
                    if (!target || !target.hasAttribute('data-idx')) continue;
                    
                    if (target.tagName.toLowerCase() === 'img') {
                        return JSON.stringify({idx: parseInt(target.getAttribute('data-idx')), offset: -1});
                    }
                    
                    // Precise line detection using Range API
                    var range = document.caretRangeFromPoint(pw / 2, y);
                    if (range) {
                        var node = range.startContainer;
                        var localOffset = range.startOffset;
                        
                        // We want the start of the LINE containing this point
                        var lineRange = document.createRange();
                        lineRange.setStart(node, localOffset);
                        lineRange.setEnd(node, localOffset);
                        var rects = lineRange.getClientRects();
                        if (rects.length > 0) {
                            var targetLeft = rects[0].left;
                            // Search backwards in the same node to find where the line starts
                            var searchOffset = localOffset;
                            while (searchOffset > 0) {
                                lineRange.setStart(node, searchOffset - 1);
                                lineRange.setEnd(node, searchOffset);
                                var r = lineRange.getClientRects();
                                if (r.length > 0 && Math.abs(r[0].left - targetLeft) > 10) {
                                    // Found a break or significant shift
                                    break;
                                }
                                searchOffset--;
                            }
                            localOffset = searchOffset;
                        }

                        var globalOffset = getTextOffset(node, target) + localOffset;
                        return JSON.stringify({idx: parseInt(target.getAttribute('data-idx')), offset: globalOffset});
                    }
                }
                return JSON.stringify({idx: -1, offset: -1});
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) {
            try {
                val json = org.json.JSONObject(it.trim('"').replace("\\\"", "\""))
                onCaptured(Pair(json.optInt("idx", -1), json.optInt("offset", -1)))
            } catch (_: Exception) {
                onCaptured(Pair(-1, -1))
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
        """.trimIndent()

        val modeCss = if (isPagedMode) {
            val snapType = if (currentSpineIndex == 0) "none" else "x mandatory"
            val halfGapPx = (settings.columnGap * resources.displayMetrics.density).toInt() / 2
            """
            html { 
                margin: 0; padding: 0; height: 100vh; width: 100vw; 
                overflow-x: auto; overflow-y: hidden; 
                scroll-snap-type: $snapType; 
                -webkit-overflow-scrolling: touch;
            }
            body { 
                height: 100vh; width: 100vw;
                display: block; position: relative;
                -webkit-column-width: 100vw !important; -webkit-column-gap: 0 !important;
                column-width: 100vw !important; column-gap: 0 !important;
                -webkit-column-fill: auto; column-fill: auto;
            }
            section, div, p, h1, h2, h3, h4, h5, h6 { 
                scroll-snap-align: start; 
                scroll-snap-stop: always;
            }
            p, h1, h2, h3, h4, h5, h6, li { 
                margin: 0 !important;
                padding: 0 ${halfGapPx}px ${1.2 * settings.paragraphSpacing}em ${halfGapPx}px !important; 
            }
            div {
                margin: 0 !important;
                padding: 0 ${halfGapPx}px 0 ${halfGapPx}px !important;
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
        webView.evaluateJavascript("var style = document.getElementById('reader-style') || document.createElement('style'); style.id = 'reader-style'; style.innerHTML = '$finalCss'; if (!style.parentNode) document.head.appendChild(style);", null)
    }

    fun updateUiState() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        val params = webViewContainer.layoutParams as CoordinatorLayout.LayoutParams

        if (isFullscreenPref) {
            params.behavior = null 
            params.topMargin = 0
            params.bottomMargin = 0
            if (!isUiOverlayVisible) {
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                appBarLayout.visibility = View.GONE
                bottomPanel.visibility = View.GONE
            } else {
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                appBarLayout.visibility = View.VISIBLE
                bottomPanel.visibility = View.VISIBLE
                appBarLayout.bringToFront()
                bottomPanel.bringToFront()
            }
        } else {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
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
                if ((hr.type == WebView.HitTestResult.SRC_ANCHOR_TYPE) || (hr.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE)) {
                    return false
                }

                when {
                    (x < width * 0.3) -> if (isPagedMode) prevPage()
                    (x > width * 0.7) -> if (isPagedMode) nextPage()
                    else -> if (isFullscreenPref) { isUiOverlayVisible = !isUiOverlayVisible; updateUiState() }
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
                
                // Проверяем, начался ли жест в левых 8% экрана
                if (e1.x < width * 0.08f) {
                    if (!isAdjustingBrightness) {
                        isAdjustingBrightness = true
                        // Отменяем обработку жеста в WebView, чтобы не включалось выделение текста
                        val cancelEvent = MotionEvent.obtain(e2.downTime, e2.eventTime, MotionEvent.ACTION_CANCEL, e2.x, e2.y, 0)
                        webView.dispatchTouchEvent(cancelEvent)
                        cancelEvent.recycle()
                    }
                    val lp = window.attributes
                    var brightness = lp.screenBrightness
                    
                    if (brightness < 0) {
                        // Если яркость окна не задана, берем системную, чтобы избежать скачка
                        brightness = try {
                            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
                        } catch (_: Exception) {
                            0.5f
                        }
                    }
                    
                    // Уменьшаем чувствительность: делим на высоту * 1.5, чтобы свайп был более "вязким" и точным
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
                    if (!isPagedMode && !isChapterLoading) {
                        loadAndAppendChapter(lastAppendedIndex + 1)
                    }
                }
            }
            @Keep
            @JavascriptInterface
            @Suppress("unused")
            fun onReachedTop() {
                runOnUiThread {
                    if (!isPagedMode && !isChapterLoading) {
                        loadAndPrependChapter(firstPrependedIndex - 1)
                    }
                }
            }
            @Keep
            @JavascriptInterface
            @Suppress("unused")
            fun onChapterEntered(index: Int) {
                runOnUiThread {
                    if (currentSpineIndex != index) {
                        currentSpineIndex = index
                        updateChapterTitle()
                        saveReadingPosition()
                    }
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
                // Only inject indexing in paged mode; seamless mode indexes per-section in appendChapter/prependChapter
                if (isPagedMode) {
                    injectIndexingScript()
                }
                
                if (shouldJumpToLastPage) {
                    executeJumpToLastPage()
                }
            }
        }
        webView.setOnTouchListener { _, event ->
            val handled = gestureDetector.onTouchEvent(event)
            
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                isAdjustingBrightness = false
            }

            // Если мы регулируем яркость, перехватываем все события
            if (isAdjustingBrightness) return@setOnTouchListener true
            
            // Для ACTION_DOWN возвращаем false, чтобы WebView могла обработать возможный клик
            if (event.action == MotionEvent.ACTION_DOWN) return@setOnTouchListener false
            
            // В постраничном режиме блокируем прокрутку WebView, если жест обработан нами (тап по краям)
            if (isPagedMode) handled else false
        }
    }

    private fun injectIndexingScript() {
        val js = """
            (function() {
                var items = document.querySelectorAll('p, h1, h2, h3, h4, h5, h6, li, img');
                for (var i=0; i<items.length; i++) {
                    items[i].setAttribute('data-idx', i);
                }
                
                document.body.addEventListener('click', function(e) {
                    var a = e.target.closest('a');
                    if (a && a.getAttribute('href')) {
                        var href = a.getAttribute('href');
                        if (href.startsWith('#') || href.indexOf('://') === -1 || href.startsWith('epub://')) {
                            e.preventDefault();
                            var absolute = a.href; // Browser resolves relative to epub://...
                            AndroidReader.onLinkClicked(absolute);
                        }
                    }
                }, true);
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun handleInternalLink(url: String) {
        shouldJumpToLastPage = false
        if (!url.startsWith("epub://") && !url.contains("#") && !url.endsWith(".xhtml") && !url.endsWith(".html")) return

        val cleanPath = url.replace("epub://", "").substringBefore("?")
        val pathWithoutFragment = cleanPath.substringBefore("#").replace("\\", "/")
        val fragment = if (cleanPath.contains("#")) cleanPath.substringAfter("#") else null

        val book = epubBook ?: return
        val opfDir = File(book.opfPath).parent ?: ""
        
        var targetIndex = -1
        
        // 1. Прямое совпадение
        for (i in book.spine.indices) {
            val itemHref = book.spine[i].href
            val fullHref = if (opfDir.isEmpty()) itemHref else "$opfDir/$itemHref".replace("//", "/").replace("\\", "/")
            if (fullHref.equals(pathWithoutFragment, ignoreCase = true)) {
                targetIndex = i
                break
            }
        }

        // 2. Поиск по имени файла (если пути в TOC и OPF расходятся)
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
            pendingAnchor = fragment
            loadSpineItem(targetIndex)
        } else if (fragment != null) {
            if (isPagedMode) {
                webView.evaluateJavascript("""
                    (function() {
                        var retry = 0;
                        var lastWidth = 0;
                        function sync() {
                            var target = document.getElementById('$fragment') || document.getElementsByName('$fragment')[0];
                            var pw = document.documentElement.clientWidth || window.innerWidth;
                            var sw = document.documentElement.scrollWidth;
                            if ((target && sw > pw && sw === lastWidth) || retry > 60) {
                                if (target) {
                                    var rect = target.getBoundingClientRect();
                                    var pageIndex = Math.floor((window.pageXOffset + rect.left + 2) / pw);
                                    window.scrollTo(pageIndex * pw, 0);
                                }
                            } else {
                                lastWidth = sw;
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
                        var target = document.getElementById('$fragment') || document.getElementsByName('$fragment')[0];
                        if (target) {
                            window.scrollTo(0, window.pageYOffset + target.getBoundingClientRect().top);
                        }
                    })();
                """.trimIndent(), null)
            }
        }
    }

    private fun initPagedView() {
        val loader = chapterLoader ?: return
        val content = loader.loadChapterHtml(currentSpineIndex) ?: return
        
        val isDarkMode = settings.isDarkMode
        val bgColor = if (isDarkMode) "#000000" else "#FFFFFF"

        val targetIdx = pendingElementIndex
        val targetOffset = pendingCharOffset
        pendingElementIndex = -1
        pendingCharOffset = -1

        val wrappedHtml = """
            <!DOCTYPE html>
            <html ${if (content.lang != null) "lang=\"${content.lang}\"" else ""} style="background-color: $bgColor;">
            <head>
                <style id="reader-style"></style>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <script>
                    function restorePosition(idx, offset) {
                        var target = document.querySelector('[data-idx="' + idx + '"]');
                        if (!target) return;
                        
                        var pw = window.innerWidth;
                        
                        if (offset <= 0) {
                            var rect = target.getBoundingClientRect();
                            var page = Math.floor((window.pageXOffset + rect.left) / pw);
                            window.scrollTo(page * pw, 0);
                            return;
                        }
                        
                        // Find node and local offset
                        var current = 0;
                        var foundNode = null;
                        var localOffset = 0;
                        var walker = document.createTreeWalker(target, NodeFilter.SHOW_TEXT, null, false);
                        while (walker.nextNode()) {
                            var len = walker.currentNode.textContent.length;
                            if (current + len >= offset) {
                                foundNode = walker.currentNode;
                                localOffset = offset - current;
                                break;
                            }
                            current += len;
                        }
                        
                        if (foundNode) {
                            var range = document.createRange();
                            range.setStart(foundNode, localOffset);
                            range.setEnd(foundNode, Math.min(localOffset + 1, foundNode.textContent.length));
                            var rects = range.getClientRects();
                            if (rects.length > 0) {
                                var rect = rects[0];
                                var page = Math.floor((window.pageXOffset + rect.left) / pw);
                                window.scrollTo(page * pw, 0);
                            }
                        }
                    }
                    
                    window.addEventListener('load', function() {
                        // Index elements BEFORE attempting restore
                        var items = document.querySelectorAll('p, h1, h2, h3, h4, h5, h6, li, img');
                        for (var i = 0; i < items.length; i++) items[i].setAttribute('data-idx', i);
                        
                        if ($targetIdx >= 0) {
                            var retry = 0;
                            function sync() {
                                var sw = document.documentElement.scrollWidth;
                                var pw = window.innerWidth;
                                if (sw > pw || retry > 50) {
                                    restorePosition($targetIdx, $targetOffset);
                                } else {
                                    retry++;
                                    setTimeout(sync, 50);
                                }
                            }
                            sync();
                        }
                    });
                </script>
            </head>
            <body data-mode="paged" style="margin: 0 !important; padding: 0 !important; background-color: $bgColor !important;">
                ${content.html}
            </body>
            </html>
        """.trimIndent()
        webView.loadDataWithBaseURL("epub://paged/", wrappedHtml, "text/html", "UTF-8", null)
    }

    private fun initSeamlessScroll() {
        val isDarkMode = settings.isDarkMode
        val bgColor = if (isDarkMode) "#000000" else "#FFFFFF"
        
        val html = """
            <!DOCTYPE html>
            <html style="background-color: $bgColor;">
            <head>
                <style id="reader-style"></style>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            </head>
            <body style="background-color: $bgColor !important;">
                <div id="chapters-container"></div>
                <script>
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
        val idxToUse = pendingElementIndex
        val offsetToUse = pendingCharOffset
        pendingElementIndex = -1
        pendingCharOffset = -1
        
        webView.loadDataWithBaseURL("epub://seamless/", html, "text/html", "UTF-8", null)
        
        webView.postDelayed({
            loadAndPrependChapter(currentSpineIndex - 1)
            loadAndAppendChapter(currentSpineIndex, idxToUse, offsetToUse)
            loadAndAppendChapter(currentSpineIndex + 1)
        }, 500)
    }

    private fun loadAndAppendChapter(index: Int, targetIdx: Int = -1, targetOffset: Int = -1) {
        val loader = chapterLoader ?: return
        if (index < 0 || index >= (epubBook?.spine?.size ?: 0) || index <= lastAppendedIndex) return
        
        isChapterLoading = true
        val content = loader.loadChapterHtml(index) ?: run {
            isChapterLoading = false
            return
        }
        
        if (lastAppendedIndex == -1) firstPrependedIndex = index
        lastAppendedIndex = index
        
        val escapedHtml = content.html.replace("`", "\\`").replace("$", "\\$")
        val langArg = if (content.lang != null) "'${content.lang}'" else "null"
        webView.evaluateJavascript("appendChapter($index, `$escapedHtml`, $targetIdx, $targetOffset, $langArg);") {
            isChapterLoading = false
        }
    }

    private fun loadAndPrependChapter(index: Int) {
        val loader = chapterLoader ?: return
        if (index < 0 || index >= (epubBook?.spine?.size ?: 0) || index >= firstPrependedIndex) return
        
        isChapterLoading = true
        val content = loader.loadChapterHtml(index) ?: run {
            isChapterLoading = false
            return
        }
        
        firstPrependedIndex = index
        val escapedHtml = content.html.replace("`", "\\`").replace("$", "\\$")
        val langArg = if (content.lang != null) "'${content.lang}'" else "null"
        webView.evaluateJavascript("prependChapter($index, `$escapedHtml`, $langArg);") {
            isChapterLoading = false
        }
    }

    private fun loadNextSpineItem() {
        if (currentSpineIndex < (epubBook?.spine?.size ?: 0) - 1) {
            loadSpineItem(currentSpineIndex + 1, jumpToLast = false)
        }
    }

    private fun loadPrevSpineItem() {
        if (currentSpineIndex > 0) {
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
                var pw = window.innerWidth;
                var sl = window.pageXOffset || document.documentElement.scrollLeft;
                var sw = document.documentElement.scrollWidth;
                var currentPage = Math.round(sl / pw);
                var nextScroll = (currentPage + 1) * pw;
                
                // Если следующая страница начинается слишком близко к концу (менее чем пол-экрана запаса),
                // значит пора переходить к следующей главе. Это решает проблему "математической стены".
                if (nextScroll + (pw / 2) < sw) { 
                    window.scrollTo({ left: nextScroll, behavior: 'auto' }); 
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
                var pw = window.innerWidth;
                var sl = window.pageXOffset || document.documentElement.scrollLeft;
                var currentPage = Math.round(sl / pw);
                
                if (currentPage > 0) { 
                    window.scrollTo({ left: (currentPage - 1) * pw, behavior: 'auto' });
                    return 'ok'; 
                } 
                return 'prev'; 
            })();
        """.trimIndent()) {
            if (it == "\"prev\"") loadPrevSpineItem()
        }
    }
}
