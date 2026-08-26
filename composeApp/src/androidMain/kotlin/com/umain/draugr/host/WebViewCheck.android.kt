package com.umain.draugr.host

import android.content.Context
import androidx.webkit.WebViewCompat

/**
 * Old system WebViews on OEM builds have broken WASM behaviour. Chromium 100 is the floor.
 */
fun webViewWarning(context: Context): String? {
    val pkg = WebViewCompat.getCurrentWebViewPackage(context)
        ?: return "NO SYSTEM WEBVIEW FOUND"
    val major = pkg.versionName?.substringBefore('.')?.toIntOrNull() ?: return null
    return if (major < MIN_CHROMIUM_MAJOR) {
        "SYSTEM WEBVIEW IS CHROMIUM $major, BELOW $MIN_CHROMIUM_MAJOR. WASM MAY MISBEHAVE."
    } else {
        null
    }
}

private const val MIN_CHROMIUM_MAJOR = 100
