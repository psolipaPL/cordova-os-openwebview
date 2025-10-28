package com.outsystems.plugins.openwebview

import com.google.gson.annotations.SerializedName
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.models.OSIABToolbarPosition

data class OpenWebviewInputArguments(
    @SerializedName("showURL") val showURL: Boolean?,
    @SerializedName("showToolbar") val showToolbar: Boolean?,
    @SerializedName("clearCache") val clearCache: Boolean?,
    @SerializedName("clearSessionCache") val clearSessionCache: Boolean?,
    @SerializedName("mediaPlaybackRequiresUserAction") val mediaPlaybackRequiresUserAction: Boolean?,
    @SerializedName("closeButtonText") val closeButtonText: String?,
    @SerializedName("toolbarPosition") val toolbarPosition: OSIABToolbarPosition?,
    @SerializedName("leftToRight") val leftToRight: Boolean?,
    @SerializedName("showNavigationButtons") val showNavigationButtons: Boolean?,
    @SerializedName("customWebViewUserAgent") val customWebViewUserAgent: String?,
    @SerializedName("android") val android: OpenWebviewAndroidOptions?
)

data class OpenWebviewAndroidOptions(
    @SerializedName("allowZoom") val allowZoom: Boolean?,
    @SerializedName("hardwareBack") val hardwareBack: Boolean?,
    @SerializedName("pauseMedia") val pauseMedia: Boolean?
)
