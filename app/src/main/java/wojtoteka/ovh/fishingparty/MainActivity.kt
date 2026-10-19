package wojtoteka.ovh.fishingparty

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import wojtoteka.ovh.fishingparty.game.KeyboardSteer
import wojtoteka.ovh.fishingparty.notify.ChallengeReminder
import wojtoteka.ovh.fishingparty.notify.ReminderLog
import wojtoteka.ovh.fishingparty.notify.StreakReminder
import wojtoteka.ovh.fishingparty.ui.AppRoot
import wojtoteka.ovh.fishingparty.ui.KeyNav
import wojtoteka.ovh.fishingparty.ui.ProvideUi

class MainActivity : ComponentActivity() {
    private val app get() = application as FishingPartyApp

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Nowe uruchomienie gry (nie odtworzenie po obrocie czy zmianie języka) – liczy się w statystykach.
        if (savedInstanceState == null) app.online.onLaunch()
        drawBehindSystemBars()
        applyOrientation(resources.configuration)
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
        ChallengeReminder.dismiss(this)
        ReminderLog.opened(this)
        app.online.onForeground()
    }

    override fun onPause() {
        app.inForeground = false
        app.audio.setForeground(false)
        StreakReminder.schedule(this)
        ChallengeReminder.schedule(this)
        app.online.onBackground()
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Składany telefon: po rozłożeniu duży ekran może się obracać, po złożeniu wraca pion.
        applyOrientation(newConfig)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars() else KeyboardSteer.release()
    }

    // Klawiatura steruje łodzią zanim Compose użyje strzałek do przenoszenia fokusu między przyciskami,
    // a poza grą pierwsza strzałka wybiera przycisk na wierzchu (okienko, a nie ekran pod nim).
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Esc działa jak „wstecz” (zamyka okienko, wraca do menu, pauzuje grę) – nie każda klawiatura robi to sama.
        // Przy wpisywaniu (pole RGB, logowanie) Esc najpierw chowa klawiaturę ekranową.
        if (event.keyCode == KeyEvent.KEYCODE_ESCAPE) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                val ime = ViewCompat.getRootWindowInsets(window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
                if (ime) WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.ime())
                else onBackPressedDispatcher.onBackPressed()
            }
            return true
        }
        return KeyboardSteer.onKey(event) || KeyNav.onKey(event) || super.dispatchKeyEvent(event)
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

    /**
     * Na telefonie gra stoi w pionie (rzeka płynie z dołu do góry), a na tabletach i rozłożonych
     * składakach (najkrótszy bok od 600 dp) działa w każdej orientacji i rozmiarze okna –
     * dlatego blokada nie siedzi w manifeście, który obowiązywałby wszystkie ekrany.
     */
    private fun applyOrientation(config: Configuration) {
        val phone = config.smallestScreenWidthDp < LARGE_SCREEN_DP && !isInMultiWindowMode
        val wanted = if (phone) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (requestedOrientation != wanted) requestedOrientation = wanted
    }

    /** Pełny ekran – paski systemowe pojawiają się tylko po przesunięciu od krawędzi. */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private companion object {
        const val LARGE_SCREEN_DP = 600
    }
}
