package wojtoteka.ovh.fishingparty.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.data.PowerUp
import wojtoteka.ovh.fishingparty.game.Loadout
import wojtoteka.ovh.fishingparty.game.Sfx

private sealed interface Screen {
    data object Menu : Screen
    data object Map : Screen
    data object Shop : Screen
    data object Achievements : Screen
    data object Ranking : Screen
    data object Settings : Screen
    data class Game(val level: Int, val loadout: Loadout, val attempt: Int) : Screen
}

@Composable
fun AppRoot() {
    val services = LocalServices.current
    val repo = services.repo
    var attempts by remember { mutableIntStateOf(0) }
    // Pierwsze uruchomienie: od razu do wody – samouczek jest wbudowany w poziom 1.
    var screen by remember {
        mutableStateOf<Screen>(if (repo.save.tutorialDone) Screen.Menu else Screen.Game(1, Loadout(), 0))
    }
    var intro by remember { mutableStateOf<Int?>(null) }
    var showDaily by remember { mutableStateOf(false) }
    var dailyOffered by remember { mutableStateOf(false) }
    var lastSelection by remember { mutableStateOf(emptySet<PowerUp>()) }
    var tamperNotice by remember { mutableStateOf(repo.saveWasTampered) }

    fun start(level: Int, selection: Set<PowerUp>) {
        lastSelection = selection
        val loadout = repo.progress.consume(selection)
        repo.changed()
        attempts++
        intro = null
        screen = Screen.Game(level, loadout, attempts)
    }

    LaunchedEffect(screen) {
        if (screen == Screen.Menu && !dailyOffered && repo.save.tutorialDone && repo.progress.dailyState().available) {
            dailyOffered = true
            delay(600)
            showDaily = true
        }
    }

    BackHandler(enabled = screen !is Screen.Game && screen != Screen.Menu && intro == null && !showDaily) {
        screen = Screen.Menu
    }

    Box(Modifier.fillMaxSize().background(Pal.SeaDark)) {
        when (val s = screen) {
            Screen.Menu -> MenuScreen(
                onPlay = { intro = it },
                onOpen = { dest ->
                    when (dest) {
                        Destination.MAP -> screen = Screen.Map
                        Destination.SHOP -> screen = Screen.Shop
                        Destination.ACHIEVEMENTS -> screen = Screen.Achievements
                        Destination.RANKING -> screen = Screen.Ranking
                        Destination.SETTINGS -> screen = Screen.Settings
                        Destination.DAILY -> showDaily = true
                    }
                },
            )
            Screen.Map -> MapScreen(onBack = { screen = Screen.Menu }, onSelect = { intro = it })
            Screen.Shop -> ShopScreen(onBack = { screen = Screen.Menu })
            Screen.Achievements -> AchievementsScreen(onBack = { screen = Screen.Menu })
            Screen.Ranking -> RankingScreen(onBack = { screen = Screen.Menu })
            Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Menu })
            is Screen.Game -> GameScreen(s.level, s.loadout, s.attempt) { exit ->
                when (exit) {
                    GameExit.ToMap -> screen = Screen.Map
                    is GameExit.Retry -> start(exit.level, lastSelection)
                    is GameExit.Next -> {
                        screen = Screen.Map
                        intro = exit.level
                    }
                }
            }
        }

        intro?.let { level ->
            LevelIntro(level, onPlay = { start(level, it) }, onClose = { intro = null })
        }
        if (showDaily) DailyRewardDialog(onClose = { showDaily = false })
        if (tamperNotice) {
            Overlay(onDismiss = { tamperNotice = false }) {
                PixelText(stringResource(R.string.save_tampered), size = 2, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                VSpace(8)
                PixelButton(stringResource(R.string.close), onClick = { tamperNotice = false })
            }
        }

        val banner = repo.banners.firstOrNull()
        AchievementBanner(banner)
        LaunchedEffect(banner) {
            if (banner != null) {
                services.audio.play(Sfx.ACHIEVEMENT)
                delay(2600)
                repo.banners.remove(banner)
            }
        }
    }
}
