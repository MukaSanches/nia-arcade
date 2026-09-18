package com.nia.arcade.runtime

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.KeyEvent
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.nia.arcade.BuildConfig
import com.nia.arcade.model.DirectionScheme
import com.nia.arcade.model.Game
import com.nia.arcade.model.InputProfile

class GameWebView(context: Context) : WebView(context) {
    private val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()

    var onBackRequested: (() -> Unit)? = null
    var onPauseRequested: (() -> Unit)? = null
    private var game: Game? = null

    init {
        setBackgroundColor(Color.BLACK)
        isFocusable = true
        isFocusableInTouchMode = true
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER

        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.blockNetworkLoads = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                return request?.url?.let(assetLoader::shouldInterceptRequest)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return true
                return !isLocal(uri)
            }
        }
    }

    fun load(game: Game) {
        this.game = game
        val nextUrl = "https://appassets.androidplatform.net/assets/games/" + game.slug + "/index.html"
        if (url != nextUrl) loadUrl(nextUrl)
        requestFocus()
    }

    fun restart() {
        reload()
        requestFocus()
    }

    fun pauseGame() {
        evaluateJavascript("window.__niaResetViewport&&window.__niaResetViewport();", null)
        onPause()
        pauseTimers()
        clearFocus()
    }

    fun resumeGame() {
        resumeTimers()
        onResume()
        evaluateJavascript("window.__niaResetViewport&&window.__niaResetViewport();", null)
        requestFocus()
    }

    fun destroySafely() {
        stopLoading()
        loadUrl("about:blank")
        clearHistory()
        removeAllViews()
        destroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onBackRequested?.invoke()
            return true
        }

        if (event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
            event.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
            event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
            event.keyCode == KeyEvent.KEYCODE_MENU ||
            event.keyCode == KeyEvent.KEYCODE_BUTTON_START
        ) {
            if (event.action == KeyEvent.ACTION_UP) onPauseRequested?.invoke()
            return true
        }

        val current = game ?: return super.dispatchKeyEvent(event)
        val down = event.action == KeyEvent.ACTION_DOWN

        if (current.inputProfile == InputProfile.CURSOR) {
            if (!down) return true
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> cursorMove(0, -54)
                KeyEvent.KEYCODE_DPAD_DOWN -> cursorMove(0, 54)
                KeyEvent.KEYCODE_DPAD_LEFT -> cursorMove(-54, 0)
                KeyEvent.KEYCODE_DPAD_RIGHT -> cursorMove(54, 0)
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> smartCursorClick()
                else -> return super.dispatchKeyEvent(event)
            }
            return true
        }

        val mapped = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> direction(current, "up")
            KeyEvent.KEYCODE_DPAD_DOWN -> direction(current, "down")
            KeyEvent.KEYCODE_DPAD_LEFT -> direction(current, "left")
            KeyEvent.KEYCODE_DPAD_RIGHT -> direction(current, "right")
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> current.actionKey
            else -> null
        } ?: return super.dispatchKeyEvent(event)

        dispatchKeyboard(mapped, down)
        return true
    }

    private fun direction(game: Game, direction: String): String {
        return if (game.directionScheme == DirectionScheme.WASD) {
            when (direction) {
                "up" -> "w"
                "down" -> "s"
                "left" -> "a"
                else -> "d"
            }
        } else {
            when (direction) {
                "up" -> "ArrowUp"
                "down" -> "ArrowDown"
                "left" -> "ArrowLeft"
                else -> "ArrowRight"
            }
        }
    }

    private fun dispatchKeyboard(key: String, down: Boolean) {
        val type = if (down) "keydown" else "keyup"
        val code = when (key) {
            " ", "Space" -> "Space"
            "Enter" -> "Enter"
            "ArrowUp", "ArrowDown", "ArrowLeft", "ArrowRight" -> key
            "w" -> "KeyW"
            "a" -> "KeyA"
            "s" -> "KeyS"
            "d" -> "KeyD"
            else -> key
        }
        val normalized = if (key == "Space") " " else key
        val quotedKey = normalized.jsQuote()
        val quotedCode = code.jsQuote()
        val script =
            "(function(){" +
            "if('" + type + "'==='keydown'&&(" + quotedKey + "==='Enter'||" + quotedKey + "===' ')&&window.__niaActivatePrimary&&window.__niaActivatePrimary()){return;}" +
            "const e1=new KeyboardEvent('" + type + "',{key:" + quotedKey + ",code:" + quotedCode + ",bubbles:true,cancelable:true});" +
            "document.dispatchEvent(e1);" +
            "const e2=new KeyboardEvent('" + type + "',{key:" + quotedKey + ",code:" + quotedCode + ",bubbles:true,cancelable:true});" +
            "window.dispatchEvent(e2);" +
            "if('" + type + "'==='keydown'&&(" + quotedKey + "==='Enter'||" + quotedKey + "===' ')){" +
            "const a=document.activeElement;" +
            "if(a&&(a.tagName==='BUTTON'||a.tagName==='A'||a.getAttribute('role')==='button')){try{a.click();}catch(_){}}" +
            "setTimeout(function(){window.__niaResetViewport&&window.__niaResetViewport();},0);" +
            "setTimeout(function(){window.__niaResetViewport&&window.__niaResetViewport();},120);" +
            "}" +
            "})();"
        evaluateJavascript(script, null)
    }

    private fun cursorMove(dx: Int, dy: Int) {
        evaluateJavascript("window.__niaCursorMove&&window.__niaCursorMove(" + dx + "," + dy + ");", null)
    }

    private fun smartCursorClick() {
        evaluateJavascript(
            "(function(){if(window.__niaActivatePrimary&&window.__niaActivatePrimary())return;" +
                "if(window.__niaCursorClick)window.__niaCursorClick();" +
                "setTimeout(function(){window.__niaResetViewport&&window.__niaResetViewport();},120);" +
            "})();",
            null
        )
    }

    private fun isLocal(uri: Uri): Boolean =
        uri.scheme == "https" && uri.host == "appassets.androidplatform.net"

    private fun String.jsQuote(): String =
        "'" + replace("\\", "\\\\").replace("'", "\\'") + "'"
}
