package com.example.unireader

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.appbar.AppBarLayout
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

import android.view.WindowManager
import android.os.Build

class ReaderActivity : AppCompatActivity() {

    private val TAG = "ReaderActivity"
    private lateinit var webView: WebView
    private lateinit var appBarLayout: AppBarLayout
    private var epubBook: EpubBook? = null
    private var currentSpineIndex = 0
    private var isPagedMode = true
    private var isFullscreen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Allow content to flow under system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        
        setContentView(R.layout.activity_reader)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "UniReader"

        appBarLayout = findViewById(R.id.appBarLayout)
        webView = findViewById(R.id.webView)
        setupWebView()

        val uriString = intent.getStringExtra("epub_uri")
        if (uriString != null) {
            val uri = Uri.parse(uriString)
            epubBook = EpubParser(this).parse(uri)
            loadSpineItem(0)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_reader, menu)
        menu.findItem(R.id.action_fullscreen)?.isChecked = isFullscreen
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.mode_scroll -> {
                setReadingMode(false)
                true
            }
            R.id.mode_paged -> {
                setReadingMode(true)
                true
            }
            R.id.action_fullscreen -> {
                toggleFullscreen()
                item.isChecked = isFullscreen
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setReadingMode(paged: Boolean) {
        if (isPagedMode == paged) return
        isPagedMode = paged
        webView.reload()
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        applyFullscreen()
    }

    private fun applyFullscreen() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (isFullscreen) {
            // Hide UI
            supportActionBar?.hide()
            appBarLayout.visibility = View.GONE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            // Show UI
            supportActionBar?.show()
            appBarLayout.visibility = View.VISIBLE
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        }
        
        // Use a global layout listener or a more robust way to wait for the view to resize
        webView.post {
            if (isPagedMode) injectPaginationCss()
            else injectScrollCss()
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.allowFileAccess = true
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d("WebViewConsole", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                if (url.startsWith("epub://")) {
                    val path = url.replace("epub://", "")
                    return serveEpubResource(path)
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (isPagedMode) {
                    injectPaginationCss()
                } else {
                    injectScrollCss()
                }
            }
        }

        webView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                if (isFullscreen) {
                    // In fullscreen, center tap toggles UI back
                    val width = v.width
                    val x = event.x
                    if (x > width / 3 && x < width * 2 / 3) {
                        toggleFullscreen()
                        return@setOnTouchListener true
                    }
                }
                
                if (isPagedMode) {
                    val width = v.width
                    val x = event.x
                    if (x < width / 3) {
                        prevPage()
                        return@setOnTouchListener true
                    } else if (x > width * 2 / 3) {
                        nextPage()
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }
    }

    private fun loadSpineItem(index: Int) {
        val book = epubBook ?: return
        if (index < 0 || index >= book.spine.size) return
        
        currentSpineIndex = index
        val item = book.spine[index]
        
        val opfDir = File(book.opfPath).parent ?: ""
        val fullPath = if (opfDir.isEmpty()) item.href else "$opfDir/${item.href}"
        
        webView.loadUrl("epub://$fullPath")
    }

    private fun serveEpubResource(path: String): WebResourceResponse? {
        val book = epubBook ?: return null
        try {
            contentResolver.openInputStream(book.uri)?.use { inputStream ->
                val zip = ZipInputStream(inputStream)
                var entry = zip.nextEntry
                while (entry != null) {
                    val entryName = entry.name.replace("\\", "/")
                    val targetPath = path.replace("\\", "/")
                    
                    if (entryName == targetPath) {
                        val bytes = zip.readBytes()
                        val mimeType = getMimeType(path)
                        return WebResourceResponse(mimeType, "UTF-8", ByteArrayInputStream(bytes))
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun getMimeType(path: String): String {
        return when {
            path.endsWith(".html") || path.endsWith(".xhtml") -> "text/html"
            path.endsWith(".css") -> "text/css"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".gif") -> "image/gif"
            else -> "application/octet-stream"
        }
    }

    private fun injectPaginationCss() {
        val css = """
            html {
                margin: 0 !important;
                padding: 0 !important;
                height: 100vh !important;
                width: 100vw !important;
                overflow: hidden !important;
            }
            body {
                margin: 0 !important;
                padding: 0 !important;
                height: 100vh !important;
                width: 100vw !important;
                column-count: 1 !important;
                column-gap: 0 !important;
                column-fill: auto !important;
                -webkit-column-count: 1 !important;
                -webkit-column-gap: 0 !important;
                display: block !important;
                position: relative !important;
                box-sizing: border-box !important;
                line-height: 1.6 !important;
            }
            * {
                max-width: 100vw !important;
                box-sizing: border-box !important;
                word-wrap: break-word !important;
            }
            img {
                display: block !important;
                max-width: 90% !important;
                max-height: 80% !important;
                margin: 10px auto !important;
                object-fit: contain !important;
            }
            p, div, h1, h2, h3, h4, h5, h6 {
                margin-left: 5vw !important;
                margin-right: 5vw !important;
                padding: 0 !important;
                line-height: 1.6 !important;
                break-inside: auto !important;
                text-align: justify !important;
                hyphens: auto !important;
            }
        """.trimIndent().replace("\n", "")

        val js = """
            var meta = document.createElement('meta');
            meta.name = 'viewport';
            meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no';
            document.head.appendChild(meta);
            var style = document.createElement('style');
            style.innerHTML = '$css';
            document.head.appendChild(style);
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun injectScrollCss() {
        val css = """
            body {
                margin: 0 !important;
                padding: 20px !important;
                overflow-x: hidden !important;
                line-height: 1.6 !important;
            }
            p, div, h1, h2, h3, h4, h5, h6 {
                line-height: 1.6 !important;
                text-align: justify !important;
                hyphens: auto !important;
            }
            img {
                max-width: 100% !important;
            }
        """.trimIndent().replace("\n", "")
        val js = "var style = document.createElement('style'); style.innerHTML = '$css'; document.head.appendChild(style);"
        webView.evaluateJavascript(js, null)
    }

    private fun nextPage() {
        webView.evaluateJavascript(
            "(function() { " +
            "  var totalWidth = document.documentElement.scrollWidth || document.body.scrollWidth; " +
            "  var currentScroll = window.pageXOffset || document.documentElement.scrollLeft || document.body.scrollLeft; " +
            "  var pageWidth = window.innerWidth; " +
            "  if (totalWidth > (currentScroll + pageWidth + 10)) { " +
            "    window.scrollTo(currentScroll + pageWidth, 0); " +
            "    return 'scrolled'; " +
            "  } " +
            "  return 'next_chapter'; " +
            "})();"
        ) { result ->
            if (result == "\"next_chapter\"") {
                loadSpineItem(currentSpineIndex + 1)
            }
        }
    }

    private fun prevPage() {
        webView.evaluateJavascript(
            "(function() { " +
            "  var currentScroll = window.pageXOffset || document.documentElement.scrollLeft || document.body.scrollLeft; " +
            "  var pageWidth = window.innerWidth; " +
            "  if (currentScroll > 10) { " +
            "    window.scrollTo(currentScroll - pageWidth, 0); " +
            "    return 'scrolled'; " +
            "  } " +
            "  return 'prev_chapter'; " +
            "})();"
        ) { result ->
            if (result == "\"prev_chapter\"") {
                loadSpineItem(currentSpineIndex - 1)
            }
        }
    }
}
