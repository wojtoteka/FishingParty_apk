package wojtoteka.ovh.fishingparty

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.*
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.core.*
import wojtoteka.ovh.fishingparty.ui.theme.FishingPartyTheme
import android.util.Log
import android.content.Context
import android.os.Build
import android.content.pm.ActivityInfo
import android.view.View
import android.view.WindowManager
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.*
import java.lang.Thread.UncaughtExceptionHandler
import androidx.activity.OnBackPressedCallback
import android.window.OnBackInvokedDispatcher
import android.view.MotionEvent
import android.app.Activity
import android.content.Intent

var globalWebView: WebView? = null
private var webViewRecoveryJob: Job? = null

class MainActivity : ComponentActivity() {
    private val TAG = "FishingParty"
    private var lastToastTime = 0L
    private var crashHandler: CrashHandler? = null
    private var memoryManager: WebViewMemoryManager? = null
    private var isAppInForeground = true
    private var pauseTime = 0L
    private var isGamePaused = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            initializeSafetySystems()
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            enableKeepScreenOn()
            enableFullscreen()
            startSystemBarsMonitoring()
            setupBackButtonHandler()

            enableEdgeToEdge()
            setContent {
                FishingPartyTheme {
                    Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
                        WebViewGameScreen(modifier = Modifier.fillMaxSize())
                    }
                }
            }
            Log.d(TAG, "Activity created successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Critical error in onCreate: ${e.message}", e)
            handleCriticalError("Błąd inicjalizacji aplikacji", e)
        }
    }

    private fun initializeSafetySystems() {
        crashHandler = CrashHandler(this)
        Thread.setDefaultUncaughtExceptionHandler(crashHandler)
        memoryManager = WebViewMemoryManager(this)
    }

    private fun enableKeepScreenOn() {
        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling keep screen on: ${e.message}", e)
        }
    }

    private fun enableFullscreen() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.insetsController?.let { controller ->
                    controller.hide(
                        WindowInsets.Type.statusBars() or
                        WindowInsets.Type.navigationBars() or
                        WindowInsets.Type.systemGestures() or
                        WindowInsets.Type.mandatorySystemGestures() or
                        WindowInsets.Type.displayCutout()
                    )
                    controller.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    if (Build.VERSION.SDK_INT >= 35) {
                        window.attributes.layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    } else {
                        window.attributes.layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
            } else {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val windowInsetsController =
                    WindowCompat.getInsetsController(window, window.decorView)
                windowInsetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                windowInsetsController.hide(
                    WindowInsetsCompat.Type.statusBars() or
                    WindowInsetsCompat.Type.navigationBars() or
                    WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.ime()
                )
            }

            window.addFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling fullscreen: ${e.message}", e)
        }
    }

    private fun setupBackButtonHandler() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                onBackInvokedDispatcher.registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT
                ) {
                    showExitConfirmation()
                }
            } else {
                val callback = object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        showExitConfirmation()
                    }
                }
                onBackPressedDispatcher.addCallback(this, callback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up back button: ${e.message}", e)
        }
    }

    private fun startSystemBarsMonitoring() {
        try {
            val handler = Handler(Looper.getMainLooper())
            val hideSystemBarsRunnable = object : Runnable {
                override fun run() {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            window.insetsController?.hide(
                                WindowInsets.Type.statusBars() or
                                WindowInsets.Type.navigationBars() or
                                WindowInsets.Type.systemGestures() or
                                WindowInsets.Type.mandatorySystemGestures() or
                                WindowInsets.Type.displayCutout()
                            )
                        } else {
                            val windowInsetsController =
                                WindowCompat.getInsetsController(window, window.decorView)
                            windowInsetsController.hide(
                                WindowInsetsCompat.Type.statusBars() or
                                WindowInsetsCompat.Type.navigationBars() or
                                WindowInsetsCompat.Type.systemBars() or
                                WindowInsetsCompat.Type.ime()
                            )
                        }
                        handler.postDelayed(this, 1000)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in system bars monitoring: ${e.message}", e)
                    }
                }
            }
            handler.postDelayed(hideSystemBarsRunnable, 1000)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting system bars monitoring: ${e.message}", e)
        }
    }

    private fun showExitConfirmation() {
        showToastSafe("Naciśnij ponownie, aby wyjść z gry")

        Handler(Looper.getMainLooper()).postDelayed({
            val secondCallback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    isEnabled = false
                    finish()
                }
            }
            onBackPressedDispatcher.addCallback(secondCallback)

            Handler(Looper.getMainLooper()).postDelayed({
                secondCallback.isEnabled = false
            }, 2000)
        }, 100)
    }

    fun showToastSafe(message: String) {
        try {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastToastTime > 3000) {
                runOnUiThread {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                }
                lastToastTime = currentTime
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing toast: ${e.message}", e)
        }
    }

    private fun handleCriticalError(message: String, error: Throwable) {
        try {
            Log.e(TAG, "Critical error: $message", error)
            runOnUiThread {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in error handler: ${e.message}", e)
        }
    }

    override fun onResume() {
        super.onResume()
        isAppInForeground = true

        try {
            val currentTime = System.currentTimeMillis()
            val backgroundDuration = currentTime - pauseTime
            Log.d(TAG, "App resumed (background time: ${backgroundDuration}ms)")

            enableFullscreen()

            globalWebView?.let { webView ->
                try {
                    webView.onResume()
                    webView.resumeTimers()

                    webView.evaluateJavascript("""
                        (function() {
                            try {
                                var audioCore = null;
                                var ctx = null;
                                if (window.player && window.player.runtime && window.player.runtime.audio) {
                                    audioCore = window.player.runtime.audio;
                                    ctx = audioCore.context;
                                }
                                
                                function restartMusic() {
                                    try {
                                        if (window.player && window.player.runtime && window.player.runtime.vm) {
                                            window.player.call("startMusic", []);
                                        }
                                    } catch(e) {}
                                }
                                
                                // Resume AudioContext
                                if (ctx && ctx.state === 'suspended') {
                                    ctx.resume().then(function() {
                                        // Reconnect ScriptProcessor after context is running
                                        if (window._scriptProcessorWasConnected && audioCore && audioCore.script_processor) {
                                            try {
                                                audioCore.script_processor.connect(ctx.destination);
                                                window._scriptProcessorWasConnected = false;
                                            } catch(e) {}
                                        }
                                        // Restart background music after audio is fully resumed
                                        restartMusic();
                                    }).catch(function(e) {});
                                } else if (window._scriptProcessorWasConnected && audioCore && audioCore.script_processor && ctx) {
                                    // Context already running, just reconnect processor
                                    try {
                                        audioCore.script_processor.connect(ctx.destination);
                                        window._scriptProcessorWasConnected = false;
                                    } catch(e) {}
                                    // Restart background music
                                    restartMusic();
                                } else {
                                    // AudioContext was already running and processor connected
                                    // Still restart music in case beeps were cancelled
                                    restartMusic();
                                }
                                
                                window._audioContextWasRunning = false;
                                window.isInDeepFreeze = false;
                                window.gameWasPaused = false;
                                
                                return 'resumed';
                            } catch(e) {
                                return 'resume_error: ' + e.message;
                            }
                        })();
                    """.trimIndent()) { result ->
                        Log.d(TAG, "Game resume result: $result")
                        isGamePaused = false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error resuming WebView: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onResume: ${e.message}", e)
        }

        memoryManager?.onResume()
    }

    override fun onPause() {
        super.onPause()
        isAppInForeground = false
        pauseTime = System.currentTimeMillis()

        try {
            Log.d(TAG, "App pausing - freezing game")

            globalWebView?.let { webView ->
                // First inject JS to kill audio BEFORE pausing WebView
                webView.evaluateJavascript("""
                    (function() {
                        try {
                            // Find the real AudioContext from microengine
                            var ctx = null;
                            var audioCore = null;
                            if (window.player && window.player.runtime && window.player.runtime.audio) {
                                audioCore = window.player.runtime.audio;
                                ctx = audioCore.context;
                            }
                            
                            // Disconnect ScriptProcessor to stop audio generation immediately
                            if (audioCore && audioCore.script_processor) {
                                try {
                                    audioCore.script_processor.disconnect();
                                    window._scriptProcessorWasConnected = true;
                                } catch(e) {}
                            }
                            
                            // Cancel any pending beeps
                            if (audioCore && typeof audioCore.cancelBeeps === 'function') {
                                try { audioCore.cancelBeeps(); } catch(e) {}
                            }
                            
                            // Suspend the AudioContext
                            if (ctx && ctx.state === 'running') {
                                ctx.suspend();
                                window._audioContextWasRunning = true;
                            }
                            
                            // Also stop any playing Sound objects
                            if (audioCore && audioCore.playing) {
                                audioCore.playing.forEach(function(s) {
                                    try { if (s.source) s.source.stop(); } catch(e) {}
                                });
                            }
                            
                            // Stop HTML audio/video elements
                            document.querySelectorAll('audio, video').forEach(function(el) {
                                if (!el.paused) {
                                    el.dataset.wasPlaying = 'true';
                                    el.pause();
                                }
                            });
                            
                            window.gameWasPaused = true;
                            return 'frozen';
                        } catch(e) {
                            return 'freeze_error: ' + e.message;
                        }
                    })();
                """.trimIndent()) { result ->
                    Log.d(TAG, "Game freeze result: $result")
                    isGamePaused = true
                }

                webView.onPause()
                webView.pauseTimers()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing app: ${e.message}", e)
        }

        memoryManager?.onPause()
    }

    override fun onStop() {
        super.onStop()
        try {
            Log.d(TAG, "App stopped - deep freeze")

            globalWebView?.let { webView ->
                // Ensure audio is fully dead - onStop may come without onPause on some devices
                webView.evaluateJavascript("""
                    (function() {
                        try {
                            var ctx = null;
                            var audioCore = null;
                            if (window.player && window.player.runtime && window.player.runtime.audio) {
                                audioCore = window.player.runtime.audio;
                                ctx = audioCore.context;
                            }
                            
                            // Disconnect ScriptProcessor (may already be disconnected from onPause)
                            if (audioCore && audioCore.script_processor) {
                                try {
                                    audioCore.script_processor.disconnect();
                                    window._scriptProcessorWasConnected = true;
                                } catch(e) {}
                            }
                            
                            // Force close the AudioContext entirely
                            if (ctx && ctx.state !== 'closed') {
                                if (ctx.state === 'running') {
                                    window._audioContextWasRunning = true;
                                }
                                ctx.suspend();
                            }
                            
                            // Cancel beeps
                            if (audioCore && typeof audioCore.cancelBeeps === 'function') {
                                try { audioCore.cancelBeeps(); } catch(e) {}
                            }
                            
                            // Stop all playing sounds
                            if (audioCore && audioCore.playing) {
                                audioCore.playing.forEach(function(s) {
                                    try { if (s.source) s.source.stop(); } catch(e) {}
                                });
                            }
                            
                            window.isInDeepFreeze = true;
                            return 'deep_frozen';
                        } catch(e) {
                            return 'deep_freeze_error: ' + e.message;
                        }
                    })();
                """.trimIndent()) { result ->
                    Log.d(TAG, "Deep freeze result: $result")
                }

                webView.pauseTimers()
                webView.onPause()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping app: ${e.message}", e)
        }
    }

    override fun onRestart() {
        super.onRestart()
        // Audio resuming is handled in onResume which always follows onRestart
        Log.d(TAG, "App restarting")
    }

    override fun onDestroy() {
        try {
            webViewRecoveryJob?.cancel()
            memoryManager?.cleanup()
            globalWebView?.destroy()
            globalWebView = null
            Log.d(TAG, "Resources cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}", e)
        }
        super.onDestroy()
    }
}

// Custom WebView - passes ALL touch events to JS, blocks only WebView scroll/overscroll
class FixedWebView(context: Context) : WebView(context) {

    init {
        overScrollMode = OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        isScrollContainer = false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Always request parent not to intercept - game needs every touch event
        requestDisallowInterceptTouchEvent(true)
        return super.onTouchEvent(event)
    }

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        // Block all scrolling - game is fullscreen, no scrolling needed
    }

    override fun overScrollBy(
        deltaX: Int, deltaY: Int,
        scrollX: Int, scrollY: Int,
        scrollRangeX: Int, scrollRangeY: Int,
        maxOverScrollX: Int, maxOverScrollY: Int,
        isTouchEvent: Boolean
    ): Boolean {
        // Block all overscroll
        return false
    }

    override fun scrollTo(x: Int, y: Int) {
        // Block all scrolling
    }

    override fun computeScroll() {
        // Block fling scroll
    }
}

// ── Loading Screen ──────────────────────────────────────────────────────────────
@Composable
fun FishingPartyLoadingScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    val secondaryRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "secondaryRotation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF001A33),
                        Color(0xFF000510)
                    ),
                    radius = 800f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { 0.3f },
                    modifier = Modifier
                        .size(100.dp)
                        .graphicsLayer(rotationZ = secondaryRotation),
                    color = Color(0xFF004080).copy(alpha = 0.6f),
                    strokeWidth = 2.dp
                )
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(70.dp)
                        .graphicsLayer(rotationZ = rotation),
                    color = Color(0xFF00AAFF),
                    strokeWidth = 4.dp
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Fishing Party",
                color = Color.White.copy(alpha = alpha),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Ładowanie gry...",
                color = Color(0xFF00AAFF).copy(alpha = alpha * 0.8f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// ── WebView Game Screen ─────────────────────────────────────────────────────────
@Composable
fun WebViewGameScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var retryAttempts by remember { mutableStateOf(0) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                createWebViewSafely(ctx) { webView ->
                    globalWebView = webView
                    configureWebViewSafely(webView)

                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageStarted(
                            view: WebView?, url: String?,
                            favicon: android.graphics.Bitmap?
                        ) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                            hasError = false

                            webViewRecoveryJob?.cancel()
                            webViewRecoveryJob = CoroutineScope(Dispatchers.Main).launch {
                                delay(15000)
                                if (isLoading) {
                                    Log.w("WebView", "Game load timeout")
                                    view?.stopLoading()
                                    handleOfflineGameError(
                                        ctx, view!!, Exception("Timeout loading game")
                                    )
                                }
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            webViewRecoveryJob?.cancel()
                            isLoading = false
                            hasError = false
                            retryAttempts = 0

                            Log.d("WebView", "Game loaded: $url")

                            if (url?.contains("index.html") == true ||
                                url?.startsWith("file:///android_asset/game/") == true
                            ) {
                                // Inject fullscreen CSS so the game fills the entire display
                                view?.evaluateJavascript("""
                                    (function() {
                                        var style = document.createElement('style');
                                        style.innerHTML = `
                                            body, html {
                                                margin: 0 !important;
                                                padding: 0 !important;
                                                width: 100vw !important;
                                                height: 100vh !important;
                                                overflow: hidden !important;
                                                background: black !important;
                                                position: fixed !important;
                                            }
                                            canvas {
                                                display: block !important;
                                                margin: 0 auto !important;
                                                padding: 0 !important;
                                                border: none !important;
                                                touch-action: none !important;
                                            }
                                            #canvaswrapper {
                                                width: 100vw !important;
                                                height: 100vh !important;
                                                margin: 0 !important;
                                                padding: 0 !important;
                                                overflow: hidden !important;
                                            }
                                        `;
                                        document.head.appendChild(style);
                                        
                                        document.addEventListener('visibilitychange', function() {
                                            if (document.hidden) {
                                                if (typeof pauseGame === 'function') pauseGame();
                                            } else {
                                                if (typeof resumeGame === 'function') resumeGame();
                                            }
                                        });
                                    })();
                                """.trimIndent()) { _ -> }

                                (context as? MainActivity)?.showToastSafe("Fishing Party gotowa!")
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            val errorCode = error?.errorCode ?: -1

                            if (errorCode == WebViewClient.ERROR_TIMEOUT ||
                                errorCode == WebViewClient.ERROR_HOST_LOOKUP ||
                                errorCode == WebViewClient.ERROR_CONNECT
                            ) {
                                val url = request?.url.toString()
                                if (url.startsWith("file:///android_asset/")) {
                                    val newAttempts = retryAttempts + 1
                                    retryAttempts = newAttempts
                                    hasError = true
                                    isLoading = false
                                    view?.loadDataWithBaseURL(
                                        null,
                                        createOfflineGameErrorPage(
                                            error?.description.toString()
                                        ),
                                        "text/html", "utf-8", null
                                    )
                                }
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            errorResponse: WebResourceResponse?
                        ) {
                            super.onReceivedHttpError(view, request, errorResponse)
                            Log.w("WebView", "HTTP Error: ${errorResponse?.statusCode}")
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url.toString()
                            return if (url.startsWith("file:///android_asset/")) {
                                false
                            } else {
                                Log.d("WebView", "Blocked external navigation: $url")
                                true
                            }
                        }
                    }

                    webView.webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            Log.d("Game-Console", "${consoleMessage?.message()}")
                            return true
                        }

                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            super.onProgressChanged(view, newProgress)
                            if (newProgress < 100) {
                                Log.d("WebView", "Loading: $newProgress%")
                            }
                        }
                    }

                    webView.addJavascriptInterface(AndroidInterface(context), "Android")
                    loadGameSafely(webView)
                }
            },
            modifier = modifier.fillMaxSize()
        )

        if (isLoading) {
            FishingPartyLoadingScreen()
        }
    }
}

// ── WebView helpers ─────────────────────────────────────────────────────────────
fun createWebViewSafely(context: Context, configure: (WebView) -> Unit): WebView {
    return try {
        FixedWebView(context).apply(configure)
    } catch (e: Exception) {
        Log.e("WebView", "Error creating WebView: ${e.message}", e)
        FixedWebView(context).apply {
            loadDataWithBaseURL(
                null, createErrorPage("Błąd inicjalizacji WebView"),
                "text/html", "utf-8", null
            )
        }
    }
}

fun configureWebViewSafely(webView: WebView) {
    try {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            allowContentAccess = true
            allowFileAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.isScrollContainer = false
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus()

        webView.layoutParams = android.view.ViewGroup.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(false)
        }
    } catch (e: Exception) {
        Log.e("WebView", "Error configuring WebView: ${e.message}", e)
    }
}

fun loadGameSafely(webView: WebView) {
    try {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                webView.loadUrl("file:///android_asset/game/index.html")
                Log.d("WebView", "Game loaded from assets")
            } catch (e: Exception) {
                Log.e("WebView", "Error loading game: ${e.message}", e)
                handleOfflineGameError(webView.context, webView, e)
            }
        }
    } catch (e: Exception) {
        Log.e("WebView", "Critical error in loadGameSafely: ${e.message}", e)
        handleOfflineGameError(webView.context, webView, e)
    }
}

fun handleOfflineGameError(context: Context, webView: WebView, error: Throwable) {
    try {
        Log.e("WebView", "Game loading failed: ${error.message}", error)
        (context as? MainActivity)?.showToastSafe("Błąd ładowania gry")
        webView.loadDataWithBaseURL(
            null,
            createOfflineGameErrorPage(error.message ?: "Nieznany błąd"),
            "text/html", "utf-8", null
        )
    } catch (e: Exception) {
        Log.e("WebView", "Error in handleOfflineGameError: ${e.message}", e)
    }
}

// ── Crash Handler ───────────────────────────────────────────────────────────────
class CrashHandler(private val activity: Activity) : UncaughtExceptionHandler {
    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, exception: Throwable) {
        try {
            Log.e("CrashHandler", "Uncaught exception in ${thread.name}", exception)

            activity.runOnUiThread {
                try {
                    Toast.makeText(
                        activity,
                        "Wystąpił nieoczekiwany błąd. Aplikacja zostanie zrestartowana.",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (_: Exception) {}
            }

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    activity.startActivity(intent)
                    activity.finish()
                } catch (e: Exception) {
                    defaultHandler?.uncaughtException(thread, exception)
                }
            }, 2000)
        } catch (e: Exception) {
            defaultHandler?.uncaughtException(thread, exception)
        }
    }
}

// ── Memory Manager ──────────────────────────────────────────────────────────────
class WebViewMemoryManager(private val context: Context) {
    private val memoryThreshold = 0.9

    fun onResume() {
        checkMemoryUsage()
    }

    fun onPause() {
        try { System.gc() } catch (_: Exception) {}
    }

    private fun checkMemoryUsage() {
        try {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val memoryPercent = usedMemory.toDouble() / maxMemory.toDouble()

            Log.d("MemoryManager", "Memory usage: ${(memoryPercent * 100).toInt()}%")

            if (memoryPercent > memoryThreshold) {
                Log.w("MemoryManager", "High memory usage, performing cleanup...")
                System.gc()
            }
        } catch (e: Exception) {
            Log.e("MemoryManager", "Error checking memory: ${e.message}", e)
        }
    }

    fun cleanup() {
        try {
            globalWebView?.clearCache(true)
            globalWebView?.clearHistory()
            globalWebView?.removeAllViews()
        } catch (e: Exception) {
            Log.e("MemoryManager", "Error in cleanup: ${e.message}", e)
        }
    }
}

// ── JavaScript Interface ────────────────────────────────────────────────────────
class AndroidInterface(private val context: Context) {
    @JavascriptInterface
    fun showToast(message: String) {
        (context as? MainActivity)?.showToastSafe(message)
    }

    @JavascriptInterface
    fun logMessage(message: String) {
        Log.d("Game-JS", message)
    }

    @JavascriptInterface
    fun retryConnection() {
        (context as? MainActivity)?.runOnUiThread {
            globalWebView?.let { loadGameSafely(it) }
        }
    }

    @JavascriptInterface
    fun saveScore(score: Int) {
        try {
            val prefs = context.getSharedPreferences("fishingparty_prefs", Context.MODE_PRIVATE)
            val highScore = prefs.getInt("high_score", 0)
            if (score > highScore) {
                prefs.edit().putInt("high_score", score).commit()
                Log.d("AndroidInterface", "High score saved: $score")
            }
        } catch (e: Exception) {
            Log.e("AndroidInterface", "Error saving score: ${e.message}", e)
        }
    }

    @JavascriptInterface
    fun getHighScore(): Int {
        return try {
            val prefs = context.getSharedPreferences("fishingparty_prefs", Context.MODE_PRIVATE)
            prefs.getInt("high_score", 0)
        } catch (e: Exception) { 0 }
    }

    @JavascriptInterface
    fun saveTotalFish(total: Int) {
        try {
            val prefs = context.getSharedPreferences("fishingparty_prefs", Context.MODE_PRIVATE)
            prefs.edit().putInt("total_fish", total).commit()
            Log.d("AndroidInterface", "Total fish saved: $total")
        } catch (e: Exception) {
            Log.e("AndroidInterface", "Error saving total fish: ${e.message}", e)
        }
    }

    @JavascriptInterface
    fun getTotalFish(): Int {
        return try {
            val prefs = context.getSharedPreferences("fishingparty_prefs", Context.MODE_PRIVATE)
            prefs.getInt("total_fish", 0)
        } catch (e: Exception) { 0 }
    }

    @JavascriptInterface
    fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) { "1.0" }
    }
}

// ── Error Pages ─────────────────────────────────────────────────────────────────
fun createOfflineGameErrorPage(errorMessage: String): String {
    return """
        <!DOCTYPE html>
        <html lang="pl">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
            <title>Fishing Party - Błąd</title>
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; overscroll-behavior: none; }
                html, body {
                    width: 100vw; height: 100vh; overflow: hidden;
                    font-family: Arial, sans-serif;
                    background: linear-gradient(135deg, #001A33 0%, #003366 100%);
                    color: white; display: flex; justify-content: center; align-items: center;
                    position: fixed; top: 0; left: 0;
                }
                .container {
                    text-align: center; padding: 30px;
                    background: rgba(0,0,0,0.85); border-radius: 20px;
                    max-width: 85%; width: 400px;
                    border: 2px solid #ff6b6b;
                }
                .icon { font-size: 50px; margin-bottom: 15px; }
                h1 { color: #ff6b6b; font-size: 20px; margin-bottom: 15px; }
                .error-details {
                    background: rgba(0,0,0,0.3); padding: 12px; border-radius: 10px;
                    margin: 15px 0; font-family: monospace; font-size: 12px; word-break: break-word;
                }
                .btn {
                    background: linear-gradient(45deg, #00AAFF, #0077CC);
                    color: white; border: none; padding: 12px 25px;
                    border-radius: 25px; font-size: 15px; font-weight: bold;
                    cursor: pointer; margin: 8px; min-width: 160px;
                    touch-action: manipulation;
                }
                .btn:active { transform: scale(0.95); }
                p { font-size: 14px; line-height: 1.5; color: #e0e0e0; margin-bottom: 10px; }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="icon">🎣💥</div>
                <h1>Błąd ładowania gry</h1>
                <p>Nie udało się załadować Fishing Party.</p>
                <div class="error-details">$errorMessage</div>
                <button class="btn" onclick="location.reload()">🔄 Spróbuj ponownie</button>
                <button class="btn" onclick="if(typeof Android!=='undefined')Android.retryConnection()">🔧 Ponowne ładowanie</button>
            </div>
            <script>
                document.addEventListener('touchmove', function(e) { e.preventDefault(); }, { passive: false });
            </script>
        </body>
        </html>
    """.trimIndent()
}

fun createErrorPage(errorMessage: String): String {
    return """
        <!DOCTYPE html>
        <html lang="pl">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
            <title>Fishing Party - Błąd</title>
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; overscroll-behavior: none; }
                html, body {
                    width: 100vw; height: 100vh; overflow: hidden;
                    font-family: Arial, sans-serif;
                    background: linear-gradient(135deg, #001A33 0%, #003366 100%);
                    color: white; display: flex; justify-content: center; align-items: center;
                    position: fixed; top: 0; left: 0;
                }
                .container {
                    text-align: center; padding: 30px;
                    background: rgba(0,0,0,0.85); border-radius: 20px; max-width: 90%;
                }
                .icon { font-size: 50px; margin-bottom: 15px; }
                .title { color: #ff6b6b; font-size: 22px; margin-bottom: 15px; font-weight: bold; }
                .message { font-size: 15px; margin-bottom: 20px; line-height: 1.5; }
                .btn {
                    background: linear-gradient(45deg, #00AAFF, #0077CC);
                    color: white; border: none; padding: 12px 25px;
                    border-radius: 25px; font-size: 15px; font-weight: bold; cursor: pointer;
                }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="icon">🚨</div>
                <div class="title">Fishing Party</div>
                <div class="title">Błąd aplikacji</div>
                <div class="message">$errorMessage</div>
                <button class="btn" onclick="location.reload()">🔄 Spróbuj ponownie</button>
            </div>
            <script>
                document.addEventListener('touchmove', function(e) { e.preventDefault(); }, { passive: false });
            </script>
        </body>
        </html>
    """.trimIndent()
}