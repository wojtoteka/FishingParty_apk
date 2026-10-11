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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import wojtoteka.ovh.fishingparty.AppLanguage
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
    data object Account : Screen
    data object Settings : Screen
    data class Game(val level: Int, val loadout: Loadout, val attempt: Int, val tutorial: Boolean = false) : Screen
}

@Composable
fun AppRoot() {
    val services = LocalServices.current
    val repo = services.repo
    var attempts by remember { mutableIntStateOf(0) }
    // Pierwsze uruchomienie: od razu do wody – samouczek jest wbudowany w poziom 1.
    var screen by remember {
        mutableStateOf<Screen>(
            when {
                // Gracz, który ma już odblokowane dalsze poziomy, nie jest wrzucany do samouczka.
                !repo.save.tutorialDone && repo.progress.highestUnlocked() == 1 -> Screen.Game(1, Loadout(), 0, tutorial = true)
                // Zmiana języka odtwarza ekran – gracz zostaje w ustawieniach.
                AppLanguage.reopenSettings -> Screen.Settings.also { AppLanguage.reopenSettings = false }
                else -> Screen.Menu
            },
        )
    }
    var intro by remember { mutableStateOf<Int?>(null) }
    var showDaily by remember { mutableStateOf(false) }
    var dailyOffered by remember { mutableStateOf(false) }
    var showRepair by remember { mutableStateOf(false) }
    var repairOffered by remember { mutableStateOf(false) }
    var lastSelection by remember { mutableStateOf(emptySet<PowerUp>()) }
    var tamperNotice by remember { mutableStateOf(repo.saveWasTampered) }

    // Zapis z konta (zmieniony na innym telefonie) podmieniamy tylko poza rozgrywką.
    val currentScreen by rememberUpdatedState(screen)
    LaunchedEffect(Unit) { services.online.canAutoPull = { currentScreen !is Screen.Game } }

    fun start(level: Int, selection: Set<PowerUp>) {
        lastSelection = selection
        val loadout = repo.progress.consume(selection)
        repo.changed()
        attempts++
        intro = null
        screen = Screen.Game(level, loadout, attempts)
    }

    // Po wejściu do menu: najpierw oferta odzyskania przerwanej passy, potem nagroda dzienna.
    LaunchedEffect(screen, showRepair) {
        if (screen != Screen.Menu || showRepair || (!repo.save.tutorialDone && repo.progress.highestUnlocked() == 1)) return@LaunchedEffect
        if (!repairOffered && repo.progress.streakRepair() != null) {
            repairOffered = true
            delay(600)
            showRepair = true
        } else if (!dailyOffered && repo.progress.dailyState().available) {
            dailyOffered = true
            delay(600)
            showDaily = true
        }
    }

    BackHandler(enabled = screen !is Screen.Game && screen != Screen.Menu && intro == null && !showDaily && !showRepair) {
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
                        Destination.ACCOUNT -> screen = Screen.Account
                        Destination.SETTINGS -> screen = Screen.Settings
                        Destination.DAILY -> showDaily = true
                        Destination.REPAIR -> showRepair = true
                    }
                },
            )
            Screen.Map -> MapScreen(onBack = { screen = Screen.Menu }, onSelect = { intro = it })
            Screen.Shop -> ShopScreen(onBack = { screen = Screen.Menu })
            Screen.Achievements -> AchievementsScreen(onBack = { screen = Screen.Menu })
            Screen.Ranking -> RankingScreen(onBack = { screen = Screen.Menu }, onAccount = { screen = Screen.Account })
            Screen.Account -> AccountScreen(onBack = { screen = Screen.Menu })
            Screen.Settings -> SettingsScreen(
                onBack = { screen = Screen.Menu },
                // Powtórka samouczka nie zmienia zapisu – wyjście w połowie nie wymusza go przy kolejnym starcie.
                onTutorial = { screen = Screen.Game(1, Loadout(), ++attempts, tutorial = true) },
            )
            is Screen.Game -> GameScreen(s.level, s.loadout, s.attempt, s.tutorial) { exit ->
                when (exit) {
                    GameExit.ToMap -> screen = Screen.Map
                    GameExit.ToMenu -> screen = Screen.Menu
                    is GameExit.Retry ->
                        if (s.tutorial) screen = Screen.Game(1, Loadout(), ++attempts, tutorial = true) else start(exit.level, lastSelection)
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
        if (screen !is Screen.Game) OnlineDialogs()
        if (showDaily) DailyRewardDialog(onClose = { showDaily = false })
        if (showRepair) StreakRepairDialog(onClose = { showRepair = false })
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
