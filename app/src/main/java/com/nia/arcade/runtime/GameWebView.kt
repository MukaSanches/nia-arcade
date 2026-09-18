package com.nia.arcade.runtime

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.view.KeyEvent
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.nia.arcade.BuildConfig
import com.nia.arcade.model.Difficulty
import com.nia.arcade.model.DirectionScheme
import com.nia.arcade.model.Game
import com.nia.arcade.model.InputProfile

class GameWebView(context: Context) : WebView(context) {
    private val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
        .build()

    var onBackRequested: (() -> Unit)? = null
    var onPauseRequested: (() -> Unit)? = null
    var onGameReady: (() -> Unit)? = null
    var onRuntimeError: ((String) -> Unit)? = null
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
        settings.useWideViewPort = false
        settings.loadWithOverviewMode = false
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                if (consoleMessage?.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    val msg = consoleMessage.message().orEmpty()
                    if (msg.isNotBlank()) onRuntimeError?.invoke(msg.take(180))
                }
                return super.onConsoleMessage(consoleMessage)
            }
        }

        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                return request?.url?.let(assetLoader::shouldInterceptRequest)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return true
                return !isLocal(uri)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    onRuntimeError?.invoke(error?.description?.toString().orEmpty().ifBlank { "Falha ao carregar o jogo" })
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                postDelayed({
                    evaluateJavascript(
                        "(function(){return document.documentElement.dataset.niaReady==='1'||window.__niaGameReady===true;})();"
                    ) {
                        onGameReady?.invoke()
                        requestFocus()
                    }
                }, 520)
            }
        }
    }

    fun load(game: Game) {
        this.game = game
        val nextUrl = "https://appassets.androidplatform.net/assets/games/" + game.slug + "/index.html"
        loadUrl(nextUrl)
        requestFocus()
    }

    fun restart() {
        reload()
        requestFocus()
    }

    fun setDifficulty(difficulty: Difficulty) {
        evaluateJavascript(
            "window.__niaSetDifficulty&&window.__niaSetDifficulty('" + difficulty.name + "');",
            null
        )
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

        if (
            event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
            event.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
            event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
            event.keyCode == KeyEvent.KEYCODE_MENU ||
            event.keyCode == KeyEvent.KEYCODE_BUTTON_START
        ) {
            if (event.action == KeyEvent.ACTION_UP) onPauseRequested?.invoke()
            return true
        }

        val current = game ?: return super.dispatchKeyEvent(event)

        if (current.inputProfile == InputProfile.CURSOR) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> if (event.action == KeyEvent.ACTION_DOWN) cursorMove(0, -1)
                KeyEvent.KEYCODE_DPAD_DOWN -> if (event.action == KeyEvent.ACTION_DOWN) cursorMove(0, 1)
                KeyEvent.KEYCODE_DPAD_LEFT -> if (event.action == KeyEvent.ACTION_DOWN) cursorMove(-1, 0)
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (event.action == KeyEvent.ACTION_DOWN) cursorMove(1, 0)
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (event.action == KeyEvent.ACTION_DOWN) cursorDown() else if (event.action == KeyEvent.ACTION_UP) cursorUp()
                }
                else -> return super.dispatchKeyEvent(event)
            }
            return true
        }

        val down = event.action == KeyEvent.ACTION_DOWN
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

    private fun direction(game: Game, direction: String): String =
        if (game.directionScheme == DirectionScheme.WASD) {
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
        val qKey = normalized.jsQuote()
        val qCode = code.jsQuote()
        evaluateJavascript(
            "(function(){" +
                "const e1=new KeyboardEvent('" + type + "',{key:" + qKey + ",code:" + qCode + ",bubbles:true,cancelable:true});" +
                "document.dispatchEvent(e1);" +
                "const e2=new KeyboardEvent('" + type + "',{key:" + qKey + ",code:" + qCode + ",bubbles:true,cancelable:true});" +
                "window.dispatchEvent(e2);" +
            "})();",
            null
        )
    }

    private fun cursorMove(dx: Int, dy: Int) {
        evaluateJavascript("window.__niaCursorMove&&window.__niaCursorMove($dx,$dy);", null)
    }

    private fun cursorDown() {
        evaluateJavascript(
            "(function(){if(window.__niaActivatePrimary&&window.__niaActivatePrimary())return;" +
                "window.__niaCursorDown&&window.__niaCursorDown();})();",
            null
        )
    }

    private fun cursorUp() {
        evaluateJavascript("window.__niaCursorUp&&window.__niaCursorUp();", null)
    }

    private fun isLocal(uri: Uri): Boolean =
        uri.scheme == "https" && uri.host == "appassets.androidplatform.net"

    private fun String.jsQuote(): String =
        "'" + replace("\\", "\\\\").replace("'", "\\'") + "'"
}
