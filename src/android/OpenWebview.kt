package com.outsystems.plugins.openwebview

import com.google.gson.Gson
import androidx.lifecycle.lifecycleScope
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.OSIABEngine
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.OSIABClosable
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.OSIABRouter
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.helpers.OSIABFlowHelper
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.models.OSIABToolbarPosition
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.models.OSIABWebViewOptions
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.routeradapters.OSIABWebViewRouterAdapter
import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaInterface
import org.apache.cordova.CordovaPlugin
import org.apache.cordova.CordovaWebView
import org.apache.cordova.PluginResult
import org.json.JSONArray
import org.json.JSONObject

class OpenWebview : CordovaPlugin() {

    private var engine: OSIABEngine? = null
    private var activeRouter: OSIABRouter<Boolean>? = null
    private val gson by lazy { Gson() }

    override fun initialize(cordova: CordovaInterface, webView: CordovaWebView) {
        super.initialize(cordova, webView)
        this.engine = OSIABEngine()
    }

    override fun execute(
        action: String,
        args: JSONArray,
        callbackContext: CallbackContext
    ): Boolean {
        when (action) {
            "open" -> {
                open(args, callbackContext)
                return true
            }
            "close" -> {
                close(callbackContext)
                return true
            }
        }
        return false
    }

    private fun open(args: JSONArray, callbackContext: CallbackContext) {
        val url: String?
        val webViewOptions: OSIABWebViewOptions?
        var customHeaders: Map<String, String>? = null

        try {
            val arguments = args.getJSONObject(0)

            url = arguments.getString("url")
            if (url.isNullOrEmpty()) throw IllegalArgumentException()

            webViewOptions = buildWebViewOptions(arguments.optString("options", "{}"))

            if (arguments.has("customHeaders")) {
                customHeaders = arguments.getJSONObject("customHeaders").let { jsObject ->
                    val result = mutableMapOf<String, String>()
                    jsObject.keys().forEach { key ->
                        when (val value = jsObject.opt(key)) {
                            is String -> result[key] = value
                            is Number -> result[key] = value.toString()
                        }
                    }
                    result
                }
            }

        } catch (e: Exception) {
            sendError(callbackContext, OpenWebviewError.InputArgumentsIssue)
            return
        }

        try {
            close { _: Boolean ->
                val onPageLoadedCb: () -> Unit = {
                    sendSuccess(callbackContext, OpenWebviewEventType.PAGE_LOADED)
                }

                val onFinishedCb: () -> Unit = {
                    sendSuccess(callbackContext, OpenWebviewEventType.FINISHED)
                }

                val onNavCompletedCb: (Any?) -> Unit = { data: Any? ->
                    val navigatedUrl: String? = extractUrlFromNavigationData(data)

                    val converted: String? = convertDeepLink(navigatedUrl)

                    if (converted != null) {
                        (activeRouter as? OSIABClosable)?.close { _: Boolean ->
                            activeRouter = null

                            sendSuccess(
                                callbackContext,
                                OpenWebviewEventType.NAVIGATION_COMPLETED,
                                converted
                            )
                        }
                    } else {
                        sendSuccess(
                            callbackContext,
                            OpenWebviewEventType.NAVIGATION_COMPLETED,
                            data
                        )
                    }
                }

                val router = OSIABWebViewRouterAdapter(
                    context = cordova.context,
                    lifecycleScope = cordova.activity.lifecycleScope,
                    options = webViewOptions,
                    customHeaders = customHeaders,
                    flowHelper = OSIABFlowHelper(),
                    onBrowserPageLoaded = onPageLoadedCb,
                    onBrowserFinished = onFinishedCb,
                    onBrowserPageNavigationCompleted = onNavCompletedCb
                )

                engine?.openWebView(router, url) { success: Boolean ->
                    if (success) {
                        activeRouter = router
                        sendSuccess(callbackContext, OpenWebviewEventType.SUCCESS)
                    } else {
                        sendError(callbackContext, OpenWebviewError.OpenFailed(url))
                    }
                }
            }
        } catch (e: Exception) {
            sendError(callbackContext, OpenWebviewError.OpenFailed(url ?: ""))
        }
    }

    private fun close(callbackContext: CallbackContext) {
        close { success: Boolean ->
            if (success) {
                sendSuccess(callbackContext, OpenWebviewEventType.SUCCESS)
            } else {
                sendError(callbackContext, OpenWebviewError.CloseFailed)
            }
        }
    }

    private fun close(callback: (Boolean) -> Unit) {
        (activeRouter as? OSIABClosable)?.let { closableRouter ->
            closableRouter.close { success: Boolean ->
                if (success) {
                    activeRouter = null
                }
                callback(success)
            }
        } ?: callback(false)
    }

    private fun buildWebViewOptions(options: String): OSIABWebViewOptions {
        return gson.fromJson(options, OpenWebviewInputArguments::class.java).let {
            val androidOpts = it.android

            OSIABWebViewOptions(
                it.showURL ?: true,
                it.showToolbar ?: true,
                it.clearCache ?: true,
                it.clearSessionCache ?: true,
                it.mediaPlaybackRequiresUserAction ?: false,
                it.closeButtonText ?: "Close",
                it.toolbarPosition ?: OSIABToolbarPosition.TOP,
                it.leftToRight ?: false,
                it.showNavigationButtons ?: true,
                androidOpts?.allowZoom ?: true,
                androidOpts?.hardwareBack ?: true,
                androidOpts?.pauseMedia ?: true,
                it.customWebViewUserAgent
            )
        }
    }

    private fun sendSuccess(
        callbackContext: CallbackContext,
        eventType: OpenWebviewEventType,
        data: Any? = null
    ) {
        val payload: Map<String, Any?> = mapOf(
            "eventType" to eventType.value,
            "data" to data
        )
        val jsonString = gson.toJson(payload)

        val pluginResult = PluginResult(PluginResult.Status.OK, jsonString)
        pluginResult.keepCallback = true
        callbackContext.sendPluginResult(pluginResult)
    }

    private fun sendError(callbackContext: CallbackContext, error: OpenWebviewError) {
        val jsonError = JSONObject().apply {
            put("code", error.code)
            put("message", error.message)
        }

        val pluginResult = PluginResult(
            PluginResult.Status.ERROR,
            jsonError
        )
        callbackContext.sendPluginResult(pluginResult)
    }

    private fun extractUrlFromNavigationData(data: Any?): String? {
        return when (data) {
            is String -> data
            is Map<*, *> -> {
                val raw = data["url"]
                if (raw is String) raw else null
            }
            is JSONObject -> {
                data.optString("url", null)
            }
            else -> null
        }
    }

    private fun convertDeepLink(original: String?): String? {
        if (original.isNullOrEmpty()) {
            return null
        }

        val regex = Regex("^([a-zA-Z0-9+.-]+)://(.+?)/Android/(.*)$")
        val match = regex.find(original) ?: return null

        val appBase = match.groupValues[2]
        val rest = match.groupValues[3]

        return "https://$appBase/$rest"
    }
}
