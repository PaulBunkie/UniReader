package com.example.unireader

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.GestureDetectorCompat
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
    private var pendingAnchor: String? = null

    lateinit var settings: ReaderSettings
    private lateinit var gestureDetector: GestureDetectorCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme mode before super.onCreate/setContentView
        settings = ReaderSettings.load(this)
        val mode = if (settings.isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
        
        super.onCreate(savedInstanceState)
        
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

        val uriString = intent.getStringExtra("epub_uri")
        if (uriString != null) {
            val uri = Uri.parse(uriString)
            epubBook = EpubParser(this).parse(uri)
            epubBook?.let { book ->
                chapterLoader = ChapterLoader(this, book)
                updateBookTitles()
                
                val libraryProvider = LibraryProvider(this)
                val savedBook = libraryProvider.getBooks().find { it.uri == uriString }
                if (savedBook != null) {
                    currentSpineIndex = savedBook.lastSpineIndex
                    pendingElementIndex = savedBook.lastElementIndex
                    pendingAnchor = savedBook.lastAnchor
                }
                
                loadSpineItem(currentSpineIndex)
            }
        }

        updateUiState(animate = false)
        updateWebViewPadding()
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
        
        captureCurrentElementIndex { idx ->
            isPagedMode = paged
            pendingElementIndex = idx
            
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
        captureCurrentElementIndex { idx ->
            val libraryProvider = LibraryProvider(this)
            libraryProvider.updateBookProgress(uri, currentSpineIndex, idx, null)
        }
    }

    private fun captureCurrentElementIndex(onCaptured: (Int) -> Unit) {
        val js = """
            (function() {
                var pw = document.documentElement.clientWidth || window.innerWidth;
                var el = null;
                for (var y = 100; y < 800; y += 20) {
                    var found = document.elementFromPoint(pw / 2, y);
                    if (found) {
                        var target = found.closest('p, h1, h2, h3, h4, h5, h6, li, img');
                        if (target && target.hasAttribute('data-idx')) {
                            return parseInt(target.getAttribute('data-idx'));
                        }
                    }
                }
                return -1;
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) {
            onCaptured(it.toIntOrNull() ?: -1)
        }
    }

    fun updateWebViewPadding() {
        val density = resources.displayMetrics.density
        val pl = (settings.paddingLeft * density).toInt()
        val pr = (settings.paddingRight * density).toInt()
        val pt = (settings.paddingTop * density).toInt()
        val pb = (settings.paddingBottom * density).toInt()
        
        webViewContainer.setPadding(pl, pt, pr, pb)
        
        if (isPagedMode) {
            applyCurrentSettings()
        }
    }

    fun applyCurrentSettings() {
        val isDarkMode = settings.isDarkMode
        webView.setBackgroundColor(if (isDarkMode) 0xFF121212.toInt() else 0xFFFFFFFF.toInt())

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
            }
            p, div, h1, h2, h3, h4, h5, h6 { 
                text-align: justify; 
                hyphens: auto; 
                box-sizing: border-box;
            }
            p {
                text-indent: ${settings.firstLineIndent}em;
            }
            * { max-width: 100% !important; box-sizing: border-box !important; }
            img { display: block; max-width: 100% !important; max-height: 80vh !important; margin: 10px auto !important; object-fit: contain; }
            
            /* SYSTEM DARK MODE MEDIA QUERY */
            @media (prefers-color-scheme: dark) {
                body, html { background-color: #121212 !important; color: #E0E0E0 !important; }
                p, div, h1, h2, h3, h4, h5, h6, li { color: #E0E0E0 !important; }
            }
            @media (prefers-color-scheme: light) {
                body, html { background-color: #FFFFFF !important; color: #000000 !important; }
                p, div, h1, h2, h3, h4, h5, h6, li { color: #000000 !important; }
            }
        """.trimIndent()

        val modeCss = if (isPagedMode) {
            val snapType = if (currentSpineIndex == 0) "none" else "x mandatory"
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
            p, div, h1, h2, h3, h4, h5, h6 { 
                margin: 0 !important;
                padding: 0 0 ${1.2 * settings.paragraphSpacing}em 0 !important; 
            }
            """.trimIndent()
        } else {
            """
            html, body { overflow-x: hidden !important; overflow-y: auto !important; height: auto !important; }
            body { 
                visibility: visible;
                display: block !important;
            } 
            p, div, h1, h2, h3, h4, h5, h6 { 
                margin-top: 0; 
                margin-bottom: ${settings.paragraphSpacing}em !important; 
            }
            """.trimIndent()
        }

        val finalCss = (commonCss + modeCss).replace("\n", " ")
        webView.evaluateJavascript("var style = document.getElementById('reader-style') || document.createElement('style'); style.id = 'reader-style'; style.innerHTML = '$finalCss'; if (!style.parentNode) document.head.appendChild(style);", null)
    }

    fun updateUiState(animate: Boolean = true) {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        val params = webViewContainer.layoutParams as CoordinatorLayout.LayoutParams

        if (isFullscreenPref) {
            params.behavior = null 
            params.topMargin = 0
            params.bottomMargin = 0
            if (!isUiOverlayVisible) {
                windowInsetsController?.hide(WindowInsetsCompat.Type.systemBars())
                windowInsetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                appBarLayout.visibility = View.GONE
                bottomPanel.visibility = View.GONE
            } else {
                windowInsetsController?.show(WindowInsetsCompat.Type.systemBars())
                appBarLayout.visibility = View.VISIBLE
                bottomPanel.visibility = View.VISIBLE
                appBarLayout.bringToFront()
                bottomPanel.bringToFront()
            }
        } else {
            windowInsetsController?.show(WindowInsetsCompat.Type.systemBars())
            isUiOverlayVisible = true
            appBarLayout.visibility = View.VISIBLE
            bottomPanel.visibility = View.VISIBLE
            
            val topH = if (appBarLayout.height > 0) appBarLayout.height else (56 * resources.displayMetrics.density).toInt()
            val botH = if (bottomPanel.height > 0) bottomPanel.height else (56 * resources.displayMetrics.density).toInt()
            
            webViewContainer.updateLayoutParams<CoordinatorLayout.LayoutParams> { 
                topMargin = topH
                bottomMargin = botH
            }
        }
        
        webViewContainer.layoutParams = params
        applyCurrentSettings()
    }

    private fun setupGestures() {
        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val width = webView.width
                val x = e.x

                val hr = webView.hitTestResult
                if (hr.type == WebView.HitTestResult.SRC_ANCHOR_TYPE || hr.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                    return false
                }

                when {
                    x < width * 0.3 -> if (isPagedMode) prevPage()
                    x > width * 0.7 -> if (isPagedMode) nextPage()
                    else -> if (isFullscreenPref) { isUiOverlayVisible = !isUiOverlayVisible; updateUiState() }
                }
                return true
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        webView.settings.domStorageEnabled = true
        
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onLinkClicked(url: String) {
                runOnUiThread {
                    handleInternalLink(url)
                }
            }
            @JavascriptInterface
            fun onReachedBottom() {
                runOnUiThread { 
                    if (!isPagedMode && !isChapterLoading) {
                        loadAndAppendChapter(lastAppendedIndex + 1)
                    }
                }
            }
            @JavascriptInterface
            fun onReachedTop() {
                runOnUiThread {
                    if (!isPagedMode && !isChapterLoading) {
                        loadAndPrependChapter(firstPrependedIndex - 1)
                    }
                }
            }
            @JavascriptInterface
            fun onChapterEntered(index: Int) {
                runOnUiThread {
                    if (currentSpineIndex != index) {
                        currentSpineIndex = index
                        updateChapterTitle()
                        saveReadingPosition()
                    }
                }
            }
        }, "AndroidReader")

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
                    webView.evaluateJavascript("""
                        (function() {
                            setTimeout(function() {
                                document.body.style.setProperty('visibility', 'visible', 'important');
                            }, 100);
                        })();
                    """.trimIndent(), null)
                }
                
                if (isPagedMode && (pendingAnchor != null || pendingElementIndex >= 0)) {
                    val anchor = pendingAnchor
                    val idx = pendingElementIndex
                    pendingAnchor = null
                    pendingElementIndex = -1
                    
                    webView.evaluateJavascript("""
                        (function() {
                            var retry = 0;
                            var lastWidth = 0;
                            function sync() {
                                var target = null;
                                if ('$anchor' !== 'null') {
                                    target = document.getElementById('$anchor') || document.getElementsByName('$anchor')[0];
                                } else if ($idx >= 0) {
                                    target = document.querySelector('[data-idx="$idx"]');
                                }
                                
                                var pw = window.innerWidth;
                                var sw = document.documentElement.scrollWidth;
                                
                                if ((target && sw > pw && sw === lastWidth) || retry > 60) {
                                    if (target) {
                                        var rect = target.getBoundingClientRect();
                                        // Точный расчет страницы элемента
                                        var pageIndex = Math.round((window.pageXOffset + rect.left) / pw);
                                        window.scrollTo(pageIndex * pw, 0);
                                    }
                                    document.body.style.visibility = 'visible';
                                } else {
                                    lastWidth = sw;
                                    retry++;
                                    setTimeout(sync, 50);
                                }
                            }
                            sync();
                        })();
                    """.trimIndent(), null)
                } else if (isPagedMode) {
                    webView.evaluateJavascript("document.body.style.visibility = 'visible';", null)
                }
                
                if (shouldJumpToLastPage) {
                    executeJumpToLastPage()
                }
            }
        }
        webView.setOnTouchListener { _, event ->
            val handled = gestureDetector.onTouchEvent(event)
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
                        document.body.style.visibility = 'hidden';
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
                                document.body.style.visibility = 'visible';
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
        val bgColor = if (isDarkMode) "#121212" else "#FFFFFF"

        val wrappedHtml = """
            <!DOCTYPE html>
            <html ${if (content.lang != null) "lang=\"${content.lang}\"" else ""} style="background-color: $bgColor;">
            <head>
                <style id="reader-style"></style>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            </head>
            <body data-mode="paged" style="visibility: hidden !important; margin: 0 !important; padding: 0 !important; background-color: $bgColor !important;">
                ${content.html}
            </body>
            </html>
        """.trimIndent()
        webView.loadDataWithBaseURL("epub://paged/", wrappedHtml, "text/html", "UTF-8", null)
    }

    private fun initSeamlessScroll() {
        val isDarkMode = settings.isDarkMode
        val bgColor = if (isDarkMode) "#121212" else "#FFFFFF"
        
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

                    function appendChapter(index, html, targetIdx, lang) {
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
                                    if (target) window.scrollTo(0, target.offsetTop);
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
        pendingElementIndex = -1
        
        webView.loadDataWithBaseURL("epub://seamless/", html, "text/html", "UTF-8", null)
        
        webView.postDelayed({
            loadAndPrependChapter(currentSpineIndex - 1)
            loadAndAppendChapter(currentSpineIndex, idxToUse)
            loadAndAppendChapter(currentSpineIndex + 1)
        }, 500)
    }

    private fun loadAndAppendChapter(index: Int, targetIdx: Int = -1) {
        val loader = chapterLoader ?: return
        if (index < 0 || index >= (epubBook?.spine?.size ?: 0) || index <= lastAppendedIndex) return
        
        isChapterLoading = true
        val content = loader.loadChapterHtml(index) ?: run {
            isChapterLoading = false
            return
        }
        
        if (lastAppendedIndex == -1) firstPrependedIndex = index
        lastAppendedIndex = index
        
        val escapedHtml = content.html.replace("`", "\\`").replace("${"$"}", "\\$")
        val langArg = if (content.lang != null) "'${content.lang}'" else "null"
        webView.evaluateJavascript("appendChapter($index, `$escapedHtml`, $targetIdx, $langArg);") {
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
        val escapedHtml = content.html.replace("`", "\\`").replace("${"$"}", "\\$")
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
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
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
