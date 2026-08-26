package com.umain.draugr.vm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.launch
import platform.CoreGraphics.CGRect
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.UIKit.UIView
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

private class BridgeMessageHandler(
    private val bridge: VmBridge,
) : NSObject(), WKScriptMessageHandlerProtocol {
    override fun userContentController(
        userContentController: WKUserContentController,
        didReceiveScriptMessage: WKScriptMessage,
    ) {
        bridge.onMessage(didReceiveScriptMessage.body.toString())
    }
}

/**
 * Jetsam is the number one failure mode on iOS: WebContent is killed under memory pressure and
 * the page simply vanishes. Catching it here is what turns a crash into a restore offer.
 */
private class HostProcessWatcher(
    private val bridge: VmBridge,
) : NSObject(), WKNavigationDelegateProtocol {
    override fun webViewWebContentProcessDidTerminate(webView: WKWebView) {
        bridge.onHostTerminated()
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun VmSurface(bridge: VmBridge, modifier: Modifier) {
    val url = bridge.hostUrl
    val handler = remember(bridge) { BridgeMessageHandler(bridge) }
    val watcher = remember(bridge) { HostProcessWatcher(bridge) }
    val scope = rememberCoroutineScope()
    DisposableEffect(bridge) { onDispose { bridge.detach() } }
    if (url == null) return

    UIKitView(
        modifier = modifier,
        factory = {
            val configuration = WKWebViewConfiguration().apply {
                allowsInlineMediaPlayback = true
                userContentController.addScriptMessageHandler(handler, name = "draugr")
            }
            WKWebView(frame = cValue<CGRect>(), configuration = configuration).apply {
                opaque = false
                navigationDelegate = watcher
                // Scrolling stays on so a zoomed guest can be panned; bouncing does not,
                // because rubber-banding a terminal feels broken.
                scrollView.scrollEnabled = true
                scrollView.bounces = false
                bridge.attach(this)
                loadRequest(NSURLRequest.requestWithURL(NSURL(string = "$url/host.html")))
            }
        },
        // X11 guests need the new geometry, and the text screen has to be refitted.
        onResize = { view: UIView, rect: CValue<CGRect> ->
            view.setFrame(rect)
            val size = rect.useContents { size }
            scope.launch {
                bridge.notifyResize(size.width.toInt(), size.height.toInt())
            }
        },
        onRelease = { bridge.detach() },
    )
}
