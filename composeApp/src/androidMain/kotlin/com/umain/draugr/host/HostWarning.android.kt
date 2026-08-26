package com.umain.draugr.host

import com.umain.draugr.DraugrApplication

actual fun hostWarning(): String? = webViewWarning(DraugrApplication.appContext)
