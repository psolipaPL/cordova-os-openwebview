package com.outsystems.plugins.openwebview

sealed class OpenWebviewError(val code: String, val message: String) {

    data object InputArgumentsIssue : OpenWebviewError(
        code = 7.formatErrorCode(),
        message = "The 'open' input parameters aren't valid."
    )

    data class OpenFailed(val url: String) : OpenWebviewError(
        code = 11.formatErrorCode(),
        message = "The WebView couldn't open the following URL: $url"
    )

    data object CloseFailed : OpenWebviewError(
        code = 12.formatErrorCode(),
        message = "There's no browser view to close."
    )
}

private fun Int.formatErrorCode(): String {
    return "OS-PLUG-IABP-" + this.toString().padStart(4, '0')
}
