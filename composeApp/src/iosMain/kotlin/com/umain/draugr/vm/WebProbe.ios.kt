package com.umain.draugr.vm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import platform.CoreGraphics.CGRect
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

private class ProbeHandler(
    private val onMessage: (String) -> Unit,
) : NSObject(), WKScriptMessageHandlerProtocol {
    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage,
    ) {
        onMessage(didReceiveScriptMessage.body.toString())
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun WebProbe(
    url: String,
    onMessage: (String) -> Unit,
    modifier: Modifier,
) {
    val handler = remember(onMessage) { ProbeHandler(onMessage) }
    UIKitView(
        modifier = modifier,
        factory = {
            val configuration = WKWebViewConfiguration().apply {
                allowsInlineMediaPlayback = true
                userContentController.addScriptMessageHandler(handler, name = "draugr")
            }
            WKWebView(frame = cValue<CGRect>(), configuration = configuration).apply {
                loadRequest(NSURLRequest.requestWithURL(NSURL(string = url)))
            }
        },
    )
}
