package com.reaido.unireader

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updateLayoutParams
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.constraintlayout.widget.ConstraintLayout
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

class ImageViewerActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var bookUri: Uri? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContentView(R.layout.activity_image_viewer)

        val uriString = intent.getStringExtra("book_uri")
        val imageUrl = intent.getStringExtra("image_url")
        if (uriString != null) bookUri = uriString.toUri()

        webView = findViewById(R.id.webViewImageViewer)
        val closeButton = findViewById<FloatingActionButton>(R.id.fabClose)
        closeButton.setOnClickListener { finish() }

        ViewCompat.setOnApplyWindowInsetsListener(closeButton) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ConstraintLayout.LayoutParams> {
                topMargin = bars.top + (16 * resources.displayMetrics.density).toInt()
                rightMargin = bars.right + (16 * resources.displayMetrics.density).toInt()
            }
            insets
        }

        setupWebView()

        if (imageUrl != null) {
            val html = """
                <html>
                <head>
                    <style>
                        body { margin: 0; padding: 0; background: black; display: flex; align-items: center; justify-content: center; height: 100vh; width: 100vw; }
                        img { max-width: 100%; max-height: 100%; object-fit: contain; }
                    </style>
                </head>
                <body>
                    <img src="$imageUrl" />
                </body>
                </html>
            """.trimIndent()
            webView.loadDataWithBaseURL("epub://image-viewer/", html, "text/html", "UTF-8", null)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        webView.setBackgroundColor(android.graphics.Color.BLACK)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                if (url.startsWith("epub://")) {
                    val path = url.replace("epub://", "").replace("image-viewer/", "")
                    return serveEpubResource(path)
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
    }

    private fun serveEpubResource(path: String): WebResourceResponse? {
        val uri = bookUri ?: return null
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
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
        path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
        path.endsWith(".png") -> "image/png"
        path.endsWith(".gif") -> "image/gif"
        else -> "application/octet-stream"
    }
}
