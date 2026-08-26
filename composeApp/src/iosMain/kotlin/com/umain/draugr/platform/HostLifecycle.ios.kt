package com.umain.draugr.platform

import com.umain.draugr.catalog.MachineSpec
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.darwin.NSObjectProtocol

actual class HostLifecycle actual constructor() {

    private var backgroundToken: NSObjectProtocol? = null
    private var foregroundToken: NSObjectProtocol? = null

    actual fun observe(onBackground: () -> Unit, onForeground: () -> Unit) {
        val center = NSNotificationCenter.defaultCenter
        backgroundToken = center.addObserverForName(
            name = UIApplicationWillResignActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _: NSNotification? -> onBackground() }
        foregroundToken = center.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _: NSNotification? -> onForeground() }
    }

    actual fun dispose() {
        val center = NSNotificationCenter.defaultCenter
        backgroundToken?.let { center.removeObserver(it) }
        foregroundToken?.let { center.removeObserver(it) }
        backgroundToken = null
        foregroundToken = null
    }
}

/** WebContent is jetsam bait above this, so a first pass refuses to ask for more. */
actual fun platformMemoryCeilingMb(): Int? = MachineSpec.IOS_MEM_BUDGET_MB
