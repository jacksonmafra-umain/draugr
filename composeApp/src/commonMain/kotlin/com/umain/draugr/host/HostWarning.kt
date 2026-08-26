package com.umain.draugr.host

/** Platform-specific health check shown on the catalog before anything boots. */
expect fun hostWarning(): String?
