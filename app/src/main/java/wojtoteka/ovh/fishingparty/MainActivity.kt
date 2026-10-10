package wojtoteka.ovh.fishingparty

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Gra steruje się dotykiem bez przerw – ekran nie może gasnąć w trakcie.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        setContent {
            ProvideUi(app) { AppRoot() }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        app.audio.setForeground(true)
        StreakReminder.dismiss(this)
    }

    override fun onPause() {
        app.audio.setForeground(false)
        StreakReminder.schedule(this, app.repository.progress.streakState())
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Pełny ekran – paski systemowe pojawiają się tylko po przesunięciu od krawędzi. */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
