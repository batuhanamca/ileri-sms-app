package com.ilerisms.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var progress: LinearProgressIndicator
    private lateinit var offlineView: View

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var lastBackPress = 0L
    private var loadFailed = false

    private val filePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            fileCallback?.onReceiveValue(uris)
            fileCallback = null
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        progress = findViewById(R.id.progress)
        offlineView = findViewById(R.id.offlineView)
        findViewById<MaterialButton>(R.id.retryButton).setOnClickListener { reload() }

        swipeRefresh.setColorSchemeResources(R.color.brand)
        swipeRefresh.setOnRefreshListener { reload() }
        // Sayfa en üstte değilse çekerek yenilemeyi devre dışı bırak.
        swipeRefresh.setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 }

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(false)
            builtInZoomControls = false
            userAgentString = "$userAgentString IleriSMSApp/${BuildConfig.VERSION_NAME}"
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = SiteClient()
        webView.webChromeClient = SiteChromeClient()
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            startDownload(url, userAgent, contentDisposition, mimeType)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    webView.canGoBack() -> webView.goBack()
                    System.currentTimeMillis() - lastBackPress < 2000 -> finish()
                    else -> {
                        lastBackPress = System.currentTimeMillis()
                        Toast.makeText(this@MainActivity, R.string.press_back_again, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(intent?.data?.takeIf { isSiteUrl(it) }?.toString() ?: BuildConfig.SITE_URL)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.data?.takeIf { isSiteUrl(it) }?.let { webView.loadUrl(it.toString()) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    private fun reload() {
        loadFailed = false
        if (!isOnline()) {
            showOffline(true)
            swipeRefresh.isRefreshing = false
            return
        }
        showOffline(false)
        if (webView.url.isNullOrEmpty()) webView.loadUrl(BuildConfig.SITE_URL) else webView.reload()
    }

    private fun showOffline(show: Boolean) {
        offlineView.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isSiteUrl(uri: Uri): Boolean {
        val host = uri.host ?: return false
        return uri.scheme == "https" &&
            (host == BuildConfig.SITE_HOST || host.endsWith(".${BuildConfig.SITE_HOST}"))
    }

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_app_found, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startDownload(url: String, userAgent: String?, disposition: String?, mime: String?) {
        try {
            val name = URLUtil.guessFileName(url, disposition, mime)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mime)
                addRequestHeader("User-Agent", userAgent)
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                setTitle(name)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            }
            (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private inner class SiteClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val uri = request.url
            if (isSiteUrl(uri)) return false
            // tel:, mailto:, sms:, whatsapp:, harici siteler → ilgili uygulamada aç
            openExternal(uri)
            return true
        }

        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
            loadFailed = false
            progress.visibility = View.VISIBLE
        }

        override fun onPageFinished(view: WebView, url: String?) {
            progress.visibility = View.GONE
            swipeRefresh.isRefreshing = false
            if (!loadFailed) showOffline(false)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) {
                loadFailed = true
                swipeRefresh.isRefreshing = false
                showOffline(true)
            }
        }
    }

    private inner class SiteChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progress.setProgressCompat(newProgress, true)
        }

        override fun onShowFileChooser(
            webView: WebView,
            callback: ValueCallback<Array<Uri>>,
            params: FileChooserParams
        ): Boolean {
            fileCallback?.onReceiveValue(null)
            fileCallback = callback
            return try {
                filePicker.launch(params.createIntent())
                true
            } catch (e: ActivityNotFoundException) {
                fileCallback = null
                callback.onReceiveValue(null)
                false
            }
        }
    }
}
