package com.umain.draugr.vm

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.umain.draugr.BuildConfig

private const val TAG = "DraugrSurface"

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun VmSurface(bridge: VmBridge, modifier: Modifier) {
    val url = bridge.hostUrl
    DisposableEffect(bridge) { onDispose { bridge.detach() } }
    if (url == null) return

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
            WebView(context).apply {
                setBackgroundColor(AndroidColor.BLACK)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                if (BuildConfig.DEBUG) {
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            Log.d(TAG, "console ${message.messageLevel()} ${message.message()} @${message.sourceId()}:${message.lineNumber()}")
                            return true
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError,
                        ) {
                            Log.d(TAG, "neterror ${request.url} ${error.description}")
                        }

                        override fun onReceivedHttpError(
                            view: WebView,
                            request: WebResourceRequest,
                            errorResponse: WebResourceResponse,
                        ) {
                            Log.d(TAG, "httperror ${errorResponse.statusCode} ${request.url}")
                        }
                    }
                }
                addJavascriptInterface(bridge.nativeInterface, "DraugrNative")
                bridge.attach(this)
                if (BuildConfig.DEBUG) Log.d(TAG, "loading $url/host.html")
                loadUrl("$url/host.html")
            }
        },
        onRelease = { view ->
            bridge.detach()
            view.destroy()
        },
    )
}
