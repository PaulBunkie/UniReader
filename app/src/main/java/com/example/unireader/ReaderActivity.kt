package com.example.unireader

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
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
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

class ReaderActivity : AppCompatActivity() {

    lateinit var webView: WebView
    private lateinit var appBarLayout: AppBarLayout
    private lateinit var bottomPanel: View
    private var epubBook: EpubBook? = null
    private var currentSpineIndex = 0
    var isPagedMode = true
    var isFullscreenPref = false 
    var isUiOverlayVisible = true
    
    private var shouldJumpToLastPage = false

    lateinit var settings: ReaderSettings
    private lateinit var gestureDetector: GestureDetectorCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = ReaderSettings.load(this)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        
        setContentView(R.layout.activity_reader)

        appBarLayout = findViewById(R.id.appBarLayout)
        bottomPanel = findViewById(R.id.bottomPanel)
        webView = findViewById(R.id.webView)
        
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val toolbarContent = layoutInflater.inflate(R.layout.reader_toolbar_content, toolbar, false)
        toolbar.addView(toolbarContent)
        
        toolbarContent.findViewById<TextView>(R.id.tvBookTitle).setTextColor(0xFF000000.toInt())
        toolbarContent.findViewById<TextView>(R.id.tvChapterTitle).setTextColor(0xFF000000.toInt())

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
            updateBookTitles()
            loadSpineItem(0)
        }

        updateUiState(animate = false)
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
        isPagedMode = paged
        applyCurrentSettings()
        updateUiState()
    }

    fun applyCurrentSettings() {
        if (isPagedMode) injectPaginationCss()
        else injectScrollCss()
    }

    fun updateUiState(animate: Boolean = true) {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        val params = webView.layoutParams as CoordinatorLayout.LayoutParams

        if (isFullscreenPref) {
            params.behavior = null 
            params.topMargin = 0
            params.bottomMargin = 0
            if (!isUiOverlayVisible) {
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }
            appBarLayout.setBackgroundColor(0xE6F0F0F0.toInt())
            bottomPanel.setBackgroundColor(0xE6F0F0F0.toInt())
        } else {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            isUiOverlayVisible = true
            params.behavior = null 
            appBarLayout.post {
                webView.updateLayoutParams<CoordinatorLayout.LayoutParams> { 
                    topMargin = appBarLayout.height 
                    bottomMargin = bottomPanel.height
                }
            }
            appBarLayout.setBackgroundColor(0xFFF0F0F0.toInt())
            bottomPanel.setBackgroundColor(0xFFF0F0F0.toInt())
        }
        
        webView.layoutParams = params
        appBarLayout.visibility = if (isUiOverlayVisible) View.VISIBLE else View.GONE
        bottomPanel.visibility = if (isUiOverlayVisible) View.VISIBLE else View.GONE
        
        if (isUiOverlayVisible) {
            appBarLayout.bringToFront()
            bottomPanel.bringToFront()
        }
        
        webView.postDelayed({ applyCurrentSettings() }, 50)
    }

    private fun setupGestures() {
        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val width = webView.width
                val x = e.x
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
        
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onReachedBottom() {
                runOnUiThread { loadNextSpineItem() }
            }
            @JavascriptInterface
            fun onReachedTop() {
                runOnUiThread { loadPrevSpineItem() }
            }
        }, "AndroidReader")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                if (url.startsWith("epub://")) return serveEpubResource(url.replace("epub://", ""))
                return super.shouldInterceptRequest(view, request)
            }
            override fun onPageFinished(view: WebView?, url: String?) { 
                applyCurrentSettings()
                if (shouldJumpToLastPage) {
                    webView.postDelayed({
                        executeJumpToLastPage()
                    }, 100)
                }
                if (!isPagedMode) {
                    injectScrollListener()
                }
            }
        }
        webView.setOnTouchListener { _, event ->
            val handled = gestureDetector.onTouchEvent(event)
            if (isPagedMode) handled else false
        }
    }

    private fun injectScrollListener() {
        val js = """
            window.removeEventListener('scroll', window._readerScrollHandler);
            window._readerScrollHandler = function() {
                var docHeight = Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);
                var winHeight = window.innerHeight;
                var scrollPos = window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop;
                
                if ((winHeight + scrollPos) >= docHeight - 20) {
                    AndroidReader.onReachedBottom();
                }
                if (scrollPos <= -20) {
                    AndroidReader.onReachedTop();
                }
            };
            window.addEventListener('scroll', window._readerScrollHandler);
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun loadNextSpineItem() {
        val book = epubBook ?: return
        if (currentSpineIndex < book.spine.size - 1) {
            loadSpineItem(currentSpineIndex + 1)
        }
    }

    private fun loadPrevSpineItem() {
        if (currentSpineIndex > 0) {
            shouldJumpToLastPage = true
            loadSpineItem(currentSpineIndex - 1)
        }
    }

    private fun executeJumpToLastPage() {
        if (isPagedMode) {
            webView.evaluateJavascript("(function() { window.scrollTo(document.documentElement.scrollWidth, 0); document.body.style.visibility = 'visible'; })();") {
                shouldJumpToLastPage = false
            }
        } else {
            webView.evaluateJavascript("(function() { window.scrollTo(0, document.documentElement.scrollHeight); document.body.style.visibility = 'visible'; })();") {
                shouldJumpToLastPage = false
            }
        }
    }

    private fun loadSpineItem(index: Int) {
        val book = epubBook ?: return
        if (index < 0 || index >= book.spine.size) return
        currentSpineIndex = index
        updateChapterTitle()
        webView.loadUrl("epub://${if (File(book.opfPath).parent.isNullOrEmpty()) book.spine[index].href else "${File(book.opfPath).parent}/${book.spine[index].href}"}")
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

    private fun injectPaginationCss() {
        val widthPx = webView.width
        val heightPx = webView.height
        if (widthPx <= 0 || heightPx <= 0) return
        
        val initialVisibility = if (shouldJumpToLastPage) "hidden" else "visible"
        
        val css = """
            html { margin: 0; padding: 0; height: 100vh; width: 100vw; overflow: hidden; -webkit-column-width: 100vw; -webkit-column-gap: 0; }
            body { 
                margin: 0; padding: 0; height: 100vh; width: 100vw; 
                column-count: 1; column-gap: 0; -webkit-column-count: 1; -webkit-column-gap: 0;
                display: block; position: relative; box-sizing: border-box; line-height: 1.6;
                font-family: sans-serif; font-size: ${settings.fontSize}px;
                visibility: $initialVisibility;
            }
            p, div, h1, h2, h3, h4, h5, h6 { margin: 0 6vw 1em 6vw; text-align: justify; hyphens: auto; }
            * { max-width: 100vw; box-sizing: border-box; word-wrap: break-word; }
            img { display: block; max-width: 90%; max-height: 80%; margin: 10px auto; object-fit: contain; }
        """.trimIndent().replace("\n", "")
        webView.evaluateJavascript("var style = document.getElementById('reader-style') || document.createElement('style'); style.id = 'reader-style'; style.innerHTML = '$css'; if (!style.parentNode) document.head.appendChild(style); var meta = document.querySelector('meta[name=\"viewport\"]') || document.createElement('meta'); meta.name = 'viewport'; meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no'; if (!meta.parentNode) document.head.appendChild(meta);", null)
    }

    private fun injectScrollCss() {
        val initialVisibility = if (shouldJumpToLastPage) "hidden" else "visible"
        val css = """
            html, body { overflow: auto !important; height: auto !important; width: 100% !important; }
            body { 
                margin: 0; padding: 24px; line-height: 1.6; font-family: sans-serif; 
                font-size: ${settings.fontSize}px; visibility: $initialVisibility; 
                display: block !important;
            } 
            p, div, h1, h2, h3, h4, h5, h6 { text-align: justify; hyphens: auto; margin-top: 0; margin-bottom: 1em; }
        """.trimIndent().replace("\n", "")
        webView.evaluateJavascript("var style = document.getElementById('reader-style') || document.createElement('style'); style.id = 'reader-style'; style.innerHTML = '$css'; if (!style.parentNode) document.head.appendChild(style);", null)
    }

    private fun nextPage() {
        webView.evaluateJavascript("(function() { var sw = document.documentElement.scrollWidth || document.body.scrollWidth; var sl = window.pageXOffset || document.documentElement.scrollLeft || document.body.scrollLeft; var pw = window.innerWidth; if (sw > (sl + pw + 10)) { window.scrollTo(sl + pw, 0); return 'ok'; } return 'next'; })();") { 
            if (it == "\"next\"") loadNextSpineItem()
        }
    }

    private fun prevPage() {
        webView.evaluateJavascript("(function() { var sl = window.pageXOffset || document.documentElement.scrollLeft || document.body.scrollLeft; var pw = window.innerWidth; if (sl > 10) { window.scrollTo(sl - pw, 0); return 'ok'; } return 'prev'; })();") {
            if (it == "\"prev\"") loadPrevSpineItem()
        }
    }
}
