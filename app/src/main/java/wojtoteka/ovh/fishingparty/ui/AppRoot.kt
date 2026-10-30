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
import wojtoteka.ovh.fishingparty.level.Challenge
import wojtoteka.ovh.fishingparty.level.ChallengeKind
import wojtoteka.ovh.fishingparty.level.Challenges
import wojtoteka.ovh.fishingparty.level.UserLevels
import wojtoteka.ovh.fishingparty.online.LevelLink

private sealed interface Screen {
    data object Menu : Screen
    data object Map : Screen
    data object Shop : Screen
    data object Achievements : Screen
    data object Ranking : Screen
    data object Account : Screen
    data object Settings : Screen
    data object Community : Screen
    data object Creator : Screen
    data class Editor(val draftId: String) : Screen
    data class Game(
        val level: Int,
        val loadout: Loadout,
        val attempt: Int,
        val tutorial: Boolean = false,
        /** Poziom dnia/tygodnia zamiast zwykłego poziomu [level]. */
        val challenge: Challenge? = null,
        /** Poziom z kreatora (test, przejście przed publikacją albo poziom gracza). */
        val custom: CustomRun? = null,
    ) : Screen
}

@Composable
fun AppRoot() {
    // Wersja zablokowana w panelu serwera (sprawdzane przy każdym wejściu z internetem) – zamiast gry tylko komunikat.
    val block = LocalServices.current.online.versionBlock
    if (block != null) VersionBlockedScreen(block) else AppContent()
}

@Composable
private fun AppContent() {
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
    var challengeDialog by remember { mutableStateOf<ChallengeKind?>(null) }
    var repairOffered by remember { mutableStateOf(false) }
    var lastSelection by remember { mutableStateOf(emptySet<PowerUp>()) }
    var tamperNotice by remember { mutableStateOf(repo.saveWasTampered) }
    // Sterowanie wybiera się raz – nowy gracz przed samouczkiem, dotychczasowy po aktualizacji.
    var chooseControls by remember { mutableStateOf(!repo.save.controlsChosen) }

    // Zapis z konta (zmieniony na innym telefonie) podmieniamy tylko poza rozgrywką.
    val currentScreen by rememberUpdatedState(screen)
    LaunchedEffect(Unit) { services.online.canAutoPull = { currentScreen !is Screen.Game } }
    // Po wyjściu z rozgrywki łączymy postęp, który drugi telefon zapisał na koncie w jej trakcie.
    var wasPlaying by remember { mutableStateOf(false) }
    val playing = screen is Screen.Game
    LaunchedEffect(playing) {
        if (wasPlaying && !playing) services.online.syncNow()
        wasPlaying = playing
    }

    // Lista poziomów graczy zostaje taka sama po zagraniu w poziom.
    val communityBrowser = remember { CommunityBrowser() }
    // Okno poziomu z linku bez publikacji (kod z /s/<kod>) – nad bieżącym ekranem.
    var sharedOpen by remember { mutableStateOf<String?>(null) }

    /** Poziom z kreatora – bez wzmocnień (liczy się sama umiejętność). */
    fun startCustom(run: CustomRun) {
        attempts++
        intro = null
        screen = Screen.Game(UserLevels.LEVEL_NUMBER, Loadout(), attempts, custom = run)
    }

    fun start(level: Int, selection: Set<PowerUp>, challenge: Challenge? = null) {
        lastSelection = selection
        val loadout = repo.progress.consume(selection)
        repo.changed()
        attempts++
        intro = null
        challengeDialog = null
        screen = Screen.Game(level, loadout, attempts, challenge = challenge)
    }

    // Ogłoszenie z panelu serwera (pierwsze z niezamkniętych) – poza rozgrywką i po wyborze sterowania.
    val announcement = services.online.announcements.firstOrNull()

    // Gra otwarta linkiem do poziomu (albo link przyszedł w trakcie): poza rozgrywką i po wyborze sterowania
    // opublikowany poziom otwiera się w POZIOMACH GRACZY, a poziom bez publikacji – w okienku nad bieżącym ekranem.
    val link = services.online.community.pendingLink
    LaunchedEffect(link, screen, chooseControls) {
        if (link == null || screen is Screen.Game || chooseControls) return@LaunchedEffect
        services.online.community.pendingLink = null
        intro = null
        challengeDialog = null
        when (link) {
            is LevelLink.Published -> {
                sharedOpen = null
                communityBrowser.open = link.id
                screen = Screen.Community
            }
            is LevelLink.Shared -> sharedOpen = link.code
        }
    }

    // Po wejściu do menu: najpierw oferta odzyskania przerwanej passy, potem nagroda dzienna (obie po ogłoszeniach).
    LaunchedEffect(screen, showRepair, chooseControls, announcement, sharedOpen, link) {
        if (screen != Screen.Menu || showRepair || chooseControls || announcement != null || sharedOpen != null || link != null ||
            (!repo.save.tutorialDone && repo.progress.highestUnlocked() == 1)
        ) {
            return@LaunchedEffect
        }
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

    BackHandler(enabled = screen !is Screen.Game && screen != Screen.Menu && intro == null && !showDaily && !showRepair && challengeDialog == null && sharedOpen == null) {
        screen = Screen.Menu
    }

    // Cała aplikacja to warstwa nawigacji klawiaturą; okienka (Overlay) dokładają swoje na wierzchu.
    Box(Modifier.fillMaxSize().background(Pal.SeaDark).keyLayer()) {
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
                        Destination.DAILY_LEVEL -> challengeDialog = ChallengeKind.DAILY
                        Destination.WEEKLY_LEVEL -> challengeDialog = ChallengeKind.WEEKLY
                        Destination.COMMUNITY -> screen = Screen.Community
                        Destination.CREATOR -> screen = Screen.Creator
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
            Screen.Community -> CommunityScreen(
                communityBrowser,
                onBack = { screen = Screen.Menu },
                onPlay = { d -> startCustom(CustomRun(d.data, CustomMode.PLAY, d.level.title, onlineId = d.level.id, starred = d.me?.starred == true)) },
                onCopied = { },
            )
            Screen.Creator -> CreatorScreen(
                onBack = { screen = Screen.Menu },
                onEdit = { screen = Screen.Editor(it) },
                onView = { id ->
                    communityBrowser.open = id
                    screen = Screen.Community
                },
            )
            is Screen.Editor -> EditorScreen(
                s.draftId,
                onBack = { screen = Screen.Creator },
                onPlay = { draft, run ->
                    startCustom(
                        when (run) {
                            is EditorRun.Test -> CustomRun(draft.level, CustomMode.TEST, draft.level.title, fromStage = run.fromStage, draftId = draft.id)
                            EditorRun.Verify -> CustomRun(draft.level, CustomMode.VERIFY, draft.level.title, draftId = draft.id)
                        },
                    )
                },
                onAccount = { screen = Screen.Account },
            )
            is Screen.Game -> GameScreen(s.level, s.loadout, s.attempt, s.tutorial, s.challenge, s.custom) { exit ->
                val challenge = s.challenge
                val custom = s.custom
                if (custom != null) {
                    when (exit) {
                        GameExit.ToMap -> screen = if (custom.mode == CustomMode.PLAY || custom.draftId == null) Screen.Community else Screen.Editor(custom.draftId)
                        // Poziom z linku: menu z okienkiem tego poziomu (można zagrać jeszcze raz albo je zamknąć).
                        GameExit.ToMenu -> {
                            screen = Screen.Menu
                            if (custom.mode == CustomMode.SHARED) sharedOpen = custom.shareCode
                        }
                        is GameExit.Retry, is GameExit.Next -> startCustom(custom)
                    }
                    return@GameScreen
                }
                when (exit) {
                    // Poziom dnia/tygodnia nie leży na mapie – wracamy do menu.
                    GameExit.ToMap -> screen = if (challenge != null) Screen.Menu else Screen.Map
                    GameExit.ToMenu -> screen = Screen.Menu
                    is GameExit.Retry -> when {
                        s.tutorial -> screen = Screen.Game(1, Loadout(), ++attempts, tutorial = true)
                        challenge != null -> start(exit.level, lastSelection, challenge)
                        else -> start(exit.level, lastSelection)
                    }
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
        challengeDialog?.let { kind ->
            ChallengeDialog(kind, onPlay = { challenge, selection -> start(Challenges.LEVEL_NUMBER, selection, challenge) }, onClose = { challengeDialog = null })
        }
        // Poziom z linku – nad nagrodą dnia i innymi okienkami menu (gracz otworzył go sam).
        if (screen !is Screen.Game) {
            sharedOpen?.let { code ->
                SharedLevelDialog(
                    code,
                    onPlay = { s ->
                        sharedOpen = null
                        startCustom(CustomRun(s.data, CustomMode.SHARED, s.title, shareCode = s.code))
                    },
                    onClose = { sharedOpen = null },
                )
            }
        }
        if (chooseControls) ControlsChooser(onDone = { chooseControls = false })
        if (announcement != null && screen !is Screen.Game && !chooseControls) {
            AnnouncementDialog(announcement, onClose = { services.online.dismissAnnouncement(announcement) })
        }
        if (tamperNotice) {
            Overlay(onDismiss = { tamperNotice = false }) {
                PixelText(stringResource(R.string.save_tampered), size = 2, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                VSpace(8)
                PixelButton(stringResource(R.string.close), onClick = { tamperNotice = false })
            }
        }

        val banner = repo.banners.firstOrNull()
        if (banner != null) {
            LaunchedEffect(banner) { services.audio.play(Sfx.ACHIEVEMENT) }
            AchievementToast(banner, waiting = repo.banners.size - 1, onDone = { repo.banners.remove(banner) })
        }
    }
}
