package wojtoteka.ovh.fishingparty

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import wojtoteka.ovh.fishingparty.notify.StreakReminder
import wojtoteka.ovh.fishingparty.ui.AppRoot
import wojtoteka.ovh.fishingparty.ui.ProvideUi

class MainActivity : ComponentActivity() {
    private val app get() = application as FishingPartyApp

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        drawBehindSystemBars()
        // Gra steruje się dotykiem bez przerw – ekran nie może gasnąć w trakcie.
        applyKeepScreenOn()
        hideSystemBars()
        setContent {
            ProvideUi(app) { AppRoot() }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        app.inForeground = true
        app.audio.setForeground(true)
        StreakReminder.dismiss(this)
        app.online.onForeground()
    }

    override fun onPause() {
        app.inForeground = false
        app.audio.setForeground(false)
        StreakReminder.schedule(this)
        app.online.onBackground()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Ekran nie gaśnie w trakcie gry (można wyłączyć w ustawieniach). */
    fun applyKeepScreenOn() {
        if (app.repository.save.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /**
     * Obraz pod paskami systemowymi i wycięciem aparatu (marginesy liczy Compose z WindowInsets).
     * Od Androida 15 przy targetSdk 35+ dzieje się to samo, a starsze wersje włączamy ręcznie –
     * bez enableEdgeToEdge(), które ustawia wycofane w Androidzie 15 kolory pasków
     * (setStatusBarColor/setNavigationBarColor) i tryb wycięcia SHORT_EDGES.
     */
    private fun drawBehindSystemBars() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
    }

    /** Pełny ekran – paski systemowe pojawiają się tylko po przesunięciu od krawędzi. */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
