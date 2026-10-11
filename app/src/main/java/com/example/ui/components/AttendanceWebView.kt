package com.example.ui.components

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView

const val ABSENSI_URL = "https://absensi-guru-sma-negeri-1-julok-1.ai.studio/"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AttendanceWebView(
    url: String = ABSENSI_URL,
    onCanGoBackChanged: (Boolean) -> Unit,
    onCanGoForwardChanged: (Boolean) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onProgressChanged: (Float) -> Unit,
    onTitleChanged: (String) -> Unit,
    onErrorChanged: (Boolean, String?) -> Unit,
    webViewHolder: (WebView?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var filePathCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var pendingGeoCallback by remember { mutableStateOf<Pair<String, GeolocationPermissions.Callback>?>(null) }
    var pendingPermissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }

    // File picker launcher for file uploads inside WebView
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris: Array<Uri>? = when {
            result.resultCode == android.app.Activity.RESULT_OK -> {
                val data = result.data
                when {
                    data?.clipData != null -> {
                        val clip = data.clipData!!
                        Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
                    }
                    data?.data != null -> {
                        arrayOf(data.data!!)
                    }
                    else -> null
                }
            }
            else -> null
        }
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }

    // Geolocation runtime permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        pendingGeoCallback?.let { (origin, callback) ->
            callback.invoke(origin, granted, false)
        }
        pendingGeoCallback = null
    }

    // Camera runtime permission launcher for selfie attendance
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        pendingPermissionRequest?.let { request ->
            if (isGranted) {
                request.grant(request.resources)
            } else {
                request.deny()
            }
        }
        pendingPermissionRequest = null
    }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                builtInZoomControls = true
                displayZoomControls = false
                setSupportZoom(true)
                allowFileAccess = true
                allowContentAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = "$userAgentString AbsensiGuruApp/1.0"
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val targetUri = request?.url ?: return false
                    val scheme = targetUri.scheme?.lowercase() ?: ""
                    return if (scheme == "http" || scheme == "https") {
                        false
                    } else {
                        // Open external apps for tel, mailto, whatsapp, maps, etc.
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, targetUri)
                            context.startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(
                                context,
                                "Tidak ada aplikasi untuk membuka tautan ini.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        true
                    }
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    onLoadingChanged(true)
                    onErrorChanged(false, null)
                    onCanGoBackChanged(view?.canGoBack() == true)
                    onCanGoForwardChanged(view?.canGoForward() == true)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    onLoadingChanged(false)
                    onCanGoBackChanged(view?.canGoBack() == true)
                    onCanGoForwardChanged(view?.canGoForward() == true)
                    view?.title?.let { onTitleChanged(it) }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        val desc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            error?.description?.toString()
                        } else null
                        onErrorChanged(true, desc)
                        onLoadingChanged(false)
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    val progressFloat = newProgress / 100f
                    onProgressChanged(progressFloat)
                    if (newProgress >= 100) {
                        onLoadingChanged(false)
                    }
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    if (!title.isNullOrBlank()) {
                        onTitleChanged(title)
                    }
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    if (origin != null && callback != null) {
                        pendingGeoCallback = Pair(origin, callback)
                        locationPermissionLauncher.launch(
                            arrayOf(
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    if (request == null) return
                    val requestedResources = request.resources
                    val needsCamera = requestedResources.any {
                        it == PermissionRequest.RESOURCE_VIDEO_CAPTURE
                    }

                    if (needsCamera) {
                        pendingPermissionRequest = request
                        cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                    } else {
                        request.grant(requestedResources)
                    }
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallbackArg: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = filePathCallbackArg

                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }

                    return try {
                        filePickerLauncher.launch(intent)
                        true
                    } catch (e: ActivityNotFoundException) {
                        filePathCallback?.onReceiveValue(null)
                        filePathCallback = null
                        false
                    }
                }
            }

            loadUrl(url)
        }
    }

    LaunchedEffect(webView) {
        webViewHolder(webView)
    }

    DisposableEffect(webView) {
        onDispose {
            webViewHolder(null)
            webView.destroy()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { webView },
            modifier = Modifier
                .fillMaxSize()
                .testTag("attendance_webview")
        )
    }
}
