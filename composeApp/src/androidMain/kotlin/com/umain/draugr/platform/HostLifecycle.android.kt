package com.umain.draugr.platform

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.umain.draugr.DraugrApplication

actual class HostLifecycle actual constructor() {

    private var callbacks: Application.ActivityLifecycleCallbacks? = null

    actual fun observe(onBackground: () -> Unit, onForeground: () -> Unit) {
        val application = DraugrApplication.appContext
        val registered = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = onForeground()
            override fun onActivityPaused(activity: Activity) = onBackground()
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        application.registerActivityLifecycleCallbacks(registered)
        callbacks = registered
    }

    actual fun dispose() {
        callbacks?.let { DraugrApplication.appContext.unregisterActivityLifecycleCallbacks(it) }
        callbacks = null
    }
}

/** Android WebView survives backgrounding, and `largeHeap` covers the bundled guests. */
actual fun platformMemoryCeilingMb(): Int? = null
