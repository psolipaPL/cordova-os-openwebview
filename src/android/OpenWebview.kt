package com.outsystems.plugins.openwebview

import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.OSIABClosable
import com.outsystems.plugins.inappbrowser.osinappbrowserlib.OSIABEngine
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
import org.apache.cordova.engine.SystemWebViewEngine
import org.json.JSONArray
import org.json.JSONObject

class OpenWebview : CordovaPlugin() {

    private var engine: OSIABEngine? = null
    private var activeRouter: OSIABRouter<Boolean>? = null
    private val gson by lazy { Gson() }

    private var defaultUserAgent: String? = null

    override fun initialize(cordova: CordovaInterface, webView: CordovaWebView) {
        super.initialize(cordova, webView)
        this.engine = OSIABEngine()

        defaultUserAgent = try {
            val eng = webView.engine
            val uaFromCordova = if (eng is SystemWebViewEngine) {
                val wv = eng.view as? WebView
                try {
                    wv?.getSettings()?.getUserAgentString()
                } catch (t: Throwable) {
                    null
                }
            } else null

            uaFromCordova
                ?: try {
                    WebSettings.getDefaultUserAgent(cordova.context)
                } catch (t: Throwable) {
                    System.getProperty("http.agent")
                }
        } catch (t: Throwable) {
            try {
                WebSettings.getDefaultUserAgent(cordova.context)
            } catch (t2: Throwable) {
                System.getProperty("http.agent")
            }
        }
    }

    override fun execute(
        action: String,
        args: JSONArray,
        callbackContext: CallbackContext
    ): Boolean {
        return when (action) {
            "open" -> {
                open(args, callbackContext)
                true
            }
            "close" -> {
                close(callbackContext)
                true
            }
            else -> false
        }
    }

    private fun open(args: JSONArray, callbackContext: CallbackContext) {
        val url: String?
        val webViewOptions: OSIABWebViewOptions?
        var jsProvidedHeaders: Map<String, String>? = null

        try {
            val arguments = args.getJSONObject(0)

            url = arguments.getString("url")
            if (url.isNullOrEmpty()) throw IllegalArgumentException()

            webViewOptions = buildWebViewOptions(arguments.optString("options", "{}"))

            if (arguments.has("customHeaders")) {
                jsProvidedHeaders = arguments.getJSONObject("customHeaders").let { jsObject ->
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
                val finalHeaders = mutableMapOf<String, String>()
                jsProvidedHeaders?.forEach { (k, v) ->
                    finalHeaders[k] = v
                }

                try {
                    if (!url.isNullOrEmpty()) {
                        val cookieString = CookieManager.getInstance().getCookie(url)
                        if (!cookieString.isNullOrEmpty()) {
                            finalHeaders["Cookie"] = cookieString
                        }
                    }
                } catch (_: Throwable) {
                }

                val router = OSIABWebViewRouterAdapter(
                    context = cordova.context,
                    lifecycleScope = cordova.activity.lifecycleScope,
                    options = webViewOptions,
                    customHeaders = finalHeaders,
                    flowHelper = OSIABFlowHelper(),
                    onBrowserPageLoaded = {
                        sendSuccess(callbackContext, OpenWebviewEventType.PAGE_LOADED)
                    },
                    onBrowserFinished = {
                        sendSuccess(callbackContext, OpenWebviewEventType.FINISHED)
                    },
                    onBrowserPageNavigationCompleted = { data ->
                        val navigatedUrl = extractUrlFromNavigationData(data)
                        val converted = convertDeepLink(navigatedUrl)

                        if (converted != null) {
                            sendSuccess(
                                callbackContext,
                                OpenWebviewEventType.NAVIGATION_COMPLETED,
                                converted
                            )
                        } else {
                            sendSuccess(
                                callbackContext,
                                OpenWebviewEventType.NAVIGATION_COMPLETED,
                                data
                            )
                        }

                        // Nota: NÃO fechamos a WebView aqui.
                        // O JS é que decide quando chamar close()
                    }
                )

                engine?.openWebView(router, url) { success: Boolean ->
                    if (success) {
                        activeRouter = router
                        sendSuccess(callbackContext, OpenWebviewEventType.SUCCESS)
                    } else {
                        sendError(callbackContext, OpenWebviewError.OpenFailed(url ?: ""))
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
        return gson.fromJson(options, OpenWebviewInputArguments::class.java).let { parsed ->
            val androidOpts = parsed.android

            val uaToUse = if (!parsed.customWebViewUserAgent.isNullOrEmpty()) {
                parsed.customWebViewUserAgent
            } else {
                defaultUserAgent ?: ""
            }

            OSIABWebViewOptions(
                parsed.showURL ?: true,
                parsed.showToolbar ?: true,
                parsed.clearCache ?: false,
                parsed.clearSessionCache ?: false,
                parsed.mediaPlaybackRequiresUserAction ?: false,
                parsed.closeButtonText ?: "Close",
                parsed.toolbarPosition ?: OSIABToolbarPosition.TOP,
                parsed.leftToRight ?: false,
                parsed.showNavigationButtons ?: true,
                androidOpts?.allowZoom ?: true,
                androidOpts?.hardwareBack ?: true,
                androidOpts?.pauseMedia ?: true,
                uaToUse
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
