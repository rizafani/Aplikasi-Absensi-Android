package com.example

import android.os.Bundle
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.components.ABSENSI_URL
import com.example.ui.components.AttendanceTopBar
import com.example.ui.components.AttendanceWebView
import com.example.ui.components.ErrorScreen
import com.example.ui.components.LoadingScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainAttendanceScreen(
                    onExitApp = { finish() }
                )
            }
        }
    }
}

@Composable
fun MainAttendanceScreen(
    onExitApp: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var initialLoadCompleted by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var pageTitle by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    val exitConfirmMsg = stringResource(R.string.exit_confirm)

    // Manage WebView Lifecycle (pause/resume timers and javascript execution)
    DisposableEffect(lifecycleOwner, webViewInstance) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> webViewInstance?.onPause()
                Lifecycle.Event.ON_RESUME -> webViewInstance?.onResume()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Hardware/Gesture Back Navigation
    BackHandler {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 2000L) {
                onExitApp()
            } else {
                lastBackPressTime = currentTime
                Toast.makeText(context, exitConfirmMsg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            AttendanceTopBar(
                canGoBack = canGoBack,
                canGoForward = canGoForward,
                isLoading = isLoading,
                progress = progress,
                currentTitle = pageTitle,
                onBackClick = {
                    if (webViewInstance?.canGoBack() == true) {
                        webViewInstance?.goBack()
                    }
                },
                onForwardClick = {
                    if (webViewInstance?.canGoForward() == true) {
                        webViewInstance?.goForward()
                    }
                },
                onRefreshClick = {
                    hasError = false
                    webViewInstance?.reload()
                },
                onHomeClick = {
                    hasError = false
                    webViewInstance?.loadUrl(ABSENSI_URL)
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            AttendanceWebView(
                url = ABSENSI_URL,
                onCanGoBackChanged = { canGoBack = it },
                onCanGoForwardChanged = { canGoForward = it },
                onLoadingChanged = { loading ->
                    isLoading = loading
                    if (!loading) {
                        initialLoadCompleted = true
                    }
                },
                onProgressChanged = { progress = it },
                onTitleChanged = { pageTitle = it },
                onErrorChanged = { error, desc ->
                    hasError = error
                    errorMessage = desc
                },
                webViewHolder = { webViewInstance = it }
            )

            // Initial splash loading screen
            AnimatedVisibility(
                visible = !initialLoadCompleted && isLoading && !hasError,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LoadingScreen()
            }

            // Error screen when offline or connection fails
            AnimatedVisibility(
                visible = hasError,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                ErrorScreen(
                    errorMessage = errorMessage,
                    onRetry = {
                        hasError = false
                        isLoading = true
                        webViewInstance?.reload()
                    }
                )
            }
        }
    }
}
