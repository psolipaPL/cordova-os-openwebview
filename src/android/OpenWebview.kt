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
import org.apache.cordova.*
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

        var deeplinkScheme: String? = null
        var deeplinkReplace: String? = null

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

            if (arguments.has("deeplinkScheme")) {
                deeplinkScheme = arguments.getString("deeplinkScheme")
            }
            if (arguments.has("deeplinkReplace")) {
                deeplinkReplace = arguments.getString("deeplinkReplace")
            }

        } catch (e: Exception) {
            sendError(callbackContext, OpenWebviewError.InputArgumentsIssue)
            return
        }

        try {
            close {
                val router = OSIABWebViewRouterAdapter(
                    context = cordova.context,
                    lifecycleScope = cordova.activity.lifecycleScope,
                    options = webViewOptions,
                    customHeaders = customHeaders,
                    flowHelper = OSIABFlowHelper(),

                    onBrowserPageLoaded = {
                        sendSuccess(callbackContext, OpenWebviewEventType.PAGE_LOADED)
                    },

                    onBrowserFinished = {
                        sendSuccess(callbackContext, OpenWebviewEventType.FINISHED)
                    },

                    onBrowserPageNavigationCompleted = { data ->
                        val navigatedUrl = extractUrlFromNavigationData(data)
