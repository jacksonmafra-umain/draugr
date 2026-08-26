package com.umain.draugr.vm

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.umain.draugr.BuildConfig

private class ProbeBridge(private val onMessage: (String) -> Unit) {
    @JavascriptInterface
    fun report(json: String) = onMessage(json)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun WebProbe(
    url: String,
    onMessage: (String) -> Unit,
    modifier: Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                addJavascriptInterface(ProbeBridge(onMessage), "DraugrNative")
                loadUrl(url)
            }
        },
        update = { view -> if (view.url != url) view.loadUrl(url) },
    )
}
