package com.outsystems.plugins.openwebview

enum class OpenWebviewEventType(val value: Int) {
    SUCCESS(1),
    FINISHED(2),
    PAGE_LOADED(3),
    NAVIGATION_COMPLETED(4)
}
