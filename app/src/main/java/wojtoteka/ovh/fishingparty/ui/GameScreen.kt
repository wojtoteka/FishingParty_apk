package wojtoteka.ovh.fishingparty.ui

import androidx.compose.foundation.layout.widthIn
import wojtoteka.ovh.fishingparty.game.TutorialStep
import wojtoteka.ovh.fishingparty.game.TutorialCoach
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.data.Catalog
import wojtoteka.ovh.fishingparty.data.ControlMode
import wojtoteka.ovh.fishingparty.data.Reward
import wojtoteka.ovh.fishingparty.game.GameListener
import wojtoteka.ovh.fishingparty.game.GameRenderer
import wojtoteka.ovh.fishingparty.game.GameWorld
import wojtoteka.ovh.fishingparty.game.HudStrings
import wojtoteka.ovh.fishingparty.game.KeyboardSteer
import wojtoteka.ovh.fishingparty.game.Loadout
import wojtoteka.ovh.fishingparty.game.LoseReason
import wojtoteka.ovh.fishingparty.game.Sfx
import wojtoteka.ovh.fishingparty.game.TouchStick
import wojtoteka.ovh.fishingparty.gfx.ShipLook
import wojtoteka.ovh.fishingparty.level.Challenge
import wojtoteka.ovh.fishingparty.level.ChallengeKind
import wojtoteka.ovh.fishingparty.level.LevelConstants
import wojtoteka.ovh.fishingparty.level.LevelGenerator
import wojtoteka.ovh.fishingparty.level.LevelType
import wojtoteka.ovh.fishingparty.level.RunResult
import wojtoteka.ovh.fishingparty.level.UserLevel
import wojtoteka.ovh.fishingparty.level.UserLevels
import wojtoteka.ovh.fishingparty.level.Worlds
import wojtoteka.ovh.fishingparty.data.VerifyRun
import wojtoteka.ovh.fishingparty.online.Net
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.sqrt

sealed interface GameExit {
    data object ToMap : GameExit
    data object ToMenu : GameExit
    data class Retry(val level: Int) : GameExit
    data class Next(val level: Int) : GameExit
}

private class Outcome(val result: RunResult, val reason: LoseReason?)

/** Poziom z kreatora w grze: test autora, przejście przed publikacją albo poziom gracza z serwera. */
enum class CustomMode { TEST, VERIFY, PLAY }

/**
 * @param fromStage test od wybranego etapu (tylko [CustomMode.TEST])
 * @param draftId szkic, z którego pochodzi poziom (test i przejście przed publikacją)
 * @param onlineId numer poziomu na serwerze ([CustomMode.PLAY])
 */
class CustomRun(
    val level: UserLevel,
    val mode: CustomMode,
    val title: String,
    val fromStage: Int = 0,
    val draftId: String? = null,
    val onlineId: Int = 0,
)

@Composable
fun GameScreen(
    level: Int,
    loadout: Loadout,
    attempt: Int,
    tutorial: Boolean = false,
    challenge: Challenge? = null,
    custom: CustomRun? = null,
    onExit: (GameExit) -> Unit,
) {
    val services = LocalServices.current
    val repo = services.repo
    val audio = services.audio
    // Samouczek to osobny poziom (dwa etapy: ryby, potem potwory) – nie liczy się do wyników.
    // Poziom dnia/tygodnia powstaje z seeda z serwera.
    val plan = remember(level, tutorial, challenge, custom) {
        when {
            tutorial -> LevelGenerator.tutorialPlan()
            custom != null -> UserLevels.build(custom.level, custom.fromStage).plan
            challenge != null -> challenge.plan()
            else -> LevelGenerator.plan(level)
        }
    }
    val title = when {
        custom != null -> custom.title
        challenge != null -> challengeTitle(challenge.kind)
        else -> stringResource(R.string.level_n, level)
    }
    val layout = remember(plan, attempt) {
        if (custom != null) UserLevels.build(custom.level, custom.fromStage).layout else LevelGenerator.generate(plan)
    }

    var outcome by remember(attempt) { mutableStateOf<Outcome?>(null) }
    var reward by remember(attempt) { mutableStateOf<Reward?>(null) }
    var paused by remember(attempt) { mutableStateOf(false) }
    var applied by remember(attempt) { mutableStateOf(false) }
    // Przejazd poziomu gracza w kolejce wysyłki (czas przejazdu – klucz odpowiedzi serwera).
    var playAt by remember(attempt) { mutableStateOf<Long?>(null) }
    // Nowy gracz od razu trafia do wody – najpierw wyjaśniamy, że to krótki samouczek.
    var welcome by remember(attempt) { mutableStateOf(tutorial) }

    fun apply(result: RunResult) {
        if (applied) return
        applied = true
        when {
            tutorial -> if (result.completed) repo.save.tutorialDone = true
            custom != null -> when (custom.mode) {
                CustomMode.TEST -> Unit
                // Do publikacji liczy się tylko przejście całego poziomu (bez kontynuacji – w tym trybie jej nie ma).
                CustomMode.VERIFY -> if (result.completed && custom.fromStage == 0) {
                    val draft = custom.draftId?.let { services.drafts.get(it) }
                    if (draft != null && draft.hash == UserLevels.hashOf(custom.level)) {
                        services.drafts.save(draft.with(verifiedHash = draft.hash, verifyRun = VerifyRun.of(result, repo.clock.now())))
                    }
                }
                CustomMode.PLAY -> {
                    reward = repo.progress.applyUserLevel(result, plan, custom.onlineId)
                    playAt = repo.save.userLevelQueue.lastOrNull()?.at
                    services.online.flushPlays()
                }
            }
            challenge != null -> reward = repo.progress.applyChallenge(result, plan, challenge)
            else -> reward = repo.progress.applyRun(result, plan)
        }
        repo.changed()
    }

    val listener = remember(attempt) {
        object : GameListener {
            override fun onSound(sfx: Sfx) = audio.play(sfx)
            override fun onVibrate(millis: Long) = audio.vibrate(millis)
            override fun onFinished(result: RunResult, reason: LoseReason?) {
                outcome = Outcome(result, reason)
                // Przegrana w poziomie gracza też trafia do jego statystyk (kontynuacji tam nie ma).
                if (result.completed || custom?.mode == CustomMode.PLAY) apply(result)
            }
        }
    }
    val world = remember(layout) { GameWorld(layout, loadout, tutorial, listener) }

    // Polecenie samouczka sprawdzane kilka razy na sekundę – karta odświeża się tylko przy zmianie.
    var tutorialStep by remember(world) { mutableStateOf<TutorialStep?>(null) }
    var tutorialRemaining by remember(world) { mutableIntStateOf(0) }
    if (tutorial) {
        LaunchedEffect(world) {
            while (isActive) {
                tutorialStep = TutorialCoach.step(world)
                tutorialRemaining = TutorialCoach.remaining(world)
                delay(150)
            }
        }
    }

    val worldNames = stringArrayResource(R.array.world_names)
    val finale = plan.spec.type == LevelType.FINALE
    val hud = HudStrings(
        go = pluralStringResource(R.plurals.hud_go, layout.stages[0].quota, layout.stages[0].quota),
        stage = stringResource(R.string.hud_stage),
        gateOpen = stringResource(R.string.hud_gate_open),
        finishOpen = stringResource(R.string.hud_finish_open),
        finish = stringResource(R.string.hud_finish),
        lastLife = stringResource(R.string.hud_last_life),
        shield = stringResource(R.string.hud_shield),
        timeLow = stringResource(R.string.hud_time_low),
        combo = stringResource(R.string.hud_combo),
        tutorial = stringResource(R.string.hud_tutorial),
        tutorialJoystick = stringResource(R.string.hud_tutorial_joystick),
        finale = stringResource(R.string.type_finale),
        finaleStages = if (finale) Worlds.FINALE_STAGE_WORLDS.map { worldNames[it].uppercase() } else emptyList(),
        spotted = stringResource(R.string.hud_spotted),
        levelTag = custom?.title?.take(CUSTOM_TAG)
            ?: challenge?.let { stringResource(if (it.kind == ChallengeKind.DAILY) R.string.hud_daily else R.string.hud_weekly) },
    )
    val look = ShipLook(repo.save.boat, repo.save.sail, repo.save.sailor)
    val renderer = remember(attempt) { GameRenderer(hud, look) }
    val stick = remember { TouchStick() }
    val frame = remember { mutableLongStateOf(0L) }
    val density = LocalDensity.current
    val dead = with(density) { 10.dp.toPx() }
    val radius = with(density) { 64.dp.toPx() }
    val joyRadius = with(density) { 58.dp.toPx() }
    val joyMargin = with(density) { 28.dp.toPx() }
    val topInset = WindowInsets.safeDrawing.getTop(density).toFloat()
    val bottomInset = WindowInsets.safeDrawing.getBottom(density).toFloat()

    LaunchedEffect(world) {
        audio.play(Sfx.START)
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (stick.active && stick.joystick) {
                    // Joystick: baza stoi w miejscu, gałka w środku zatrzymuje łódź.
                    val dx = stick.x - stick.originX
                    val dy = stick.y - stick.originY
                    stick.engaged = sqrt(dx * dx + dy * dy) > dead
                    if (stick.engaged) stick.angle = atan2(dy, dx)
                } else if (stick.active) {
                    val dx = stick.x - stick.originX
                    val dy = stick.y - stick.originY
                    val d = sqrt(dx * dx + dy * dy)
                    if (d > dead) {
                        stick.engaged = true
                        stick.angle = atan2(dy, dx)
                        if (d > radius) {
                            stick.originX = stick.x - dx / d * radius
                            stick.originY = stick.y - dy / d * radius
                        }
                    }
                } else {
                    stick.engaged = false
                }
                // Dotyk ma pierwszeństwo, a bez niego łódź słucha klawiatury.
                val keys = KeyboardSteer.angle()
                if (keys != null && !(stick.active && stick.engaged)) {
                    world.setInput(true, keys)
                } else {
                    world.setInput(stick.active && stick.engaged, stick.angle)
                }
                world.paused = paused || welcome || outcome != null
                if (last != 0L) world.advance(min((now - last) / 1e9f, 0.1f))
                last = now
                frame.longValue = now
            }
        }
    }

    val view = LocalView.current
    // Strzałki sterują łodzią tylko w trakcie jazdy – w pauzie i na ekranie wyniku chodzą po przyciskach.
    val steering = outcome == null && !paused && !welcome
    SideEffect { KeyboardSteer.enabled = steering }
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            KeyboardSteer.enabled = false
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, attempt) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && outcome == null) paused = true
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = outcome == null && !welcome) { paused = !paused }
    // Po zakończeniu poziomu „wstecz” wraca na mapę (a nie zamyka gry).
    BackHandler(enabled = outcome != null) {
        outcome?.let { apply(it.result) }
        onExit(GameExit.ToMap)
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(world) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        stick.press(down.position.x, down.position.y)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            stick.x = change.position.x
                            stick.y = change.position.y
                            if (change.positionChange() != androidx.compose.ui.geometry.Offset.Zero) change.consume()
                        }
                        stick.active = false
                    }
                },
        ) {
            frame.longValue
            // Wybór sterowania może się zmienić w trakcie (pierwsze uruchomienie, samouczek).
            stick.joystick = repo.save.controls == ControlMode.JOYSTICK
            stick.left = repo.save.joystickLeft
            stick.layout(size.width, size.height, bottomInset, joyMargin, joyRadius)
            drawIntoCanvas { c ->
                renderer.draw(c.nativeCanvas, world, size.width.toInt(), size.height.toInt(), topInset, stick)
            }
        }

        if (tutorial && outcome == null && !welcome && !paused) {
            tutorialStep?.let { step ->
                TutorialCard(
                    step,
                    remaining = tutorialRemaining,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                        .padding(top = px(56), start = px(8), end = px(8)),
                )
            }
        }

        if (outcome == null && !paused && !welcome) {
            PixelButton(
                "II",
                onClick = { paused = true },
                style = ButtonStyle.WOOD,
                size = 3,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
                    .padding(px(4)),
            )
        }

        if (welcome) {
            TutorialWelcome(
                onStart = { welcome = false },
                onSkip = {
                    repo.save.tutorialDone = true
                    repo.changed()
                    onExit(GameExit.ToMenu)
                },
            )
        }

        val current = outcome
        when {
            current != null && current.result.completed && tutorial -> TutorialDonePanel(onExit)
            current != null && current.result.completed && custom != null -> CustomWinPanel(custom, title, current.result, reward, playAt, onExit)
            current != null && current.result.completed -> WinPanel(level, title, challenge != null, current.result, reward, onExit)
            current != null -> LosePanel(
                level = level,
                title = title,
                challenge = challenge != null,
                custom = custom,
                world = world,
                outcome = current,
                onRevive = {
                    outcome = null
                    world.revive()
                },
                onExit = { exit ->
                    apply(current.result)
                    onExit(exit)
                },
            )
            paused -> PausePanel(
                level = level,
                title = title,
                challenge = challenge != null,
                custom = custom,
                tutorial = tutorial,
                world = world,
                onResume = { paused = false },
                onExit = { exit ->
                    onExit(exit)
                },
            )
        }
    }
}

@Composable
private fun WinPanel(level: Int, title: String, challenge: Boolean, result: RunResult, reward: Reward?, onExit: (GameExit) -> Unit) {
    val audio = LocalServices.current.audio
    val finale = !challenge && LevelGenerator.isFinale(level)
    var shownStars by remember { mutableIntStateOf(0) }
    var shownScore by remember { mutableIntStateOf(0) }
    LaunchedEffect(result) {
        delay(250)
        for (i in 1..result.stars) {
            shownStars = i
            audio.play(Sfx.STAR)
            delay(380)
        }
        val steps = 24
        for (i in 1..steps) {
            shownScore = result.score * i / steps
            delay(22)
        }
        shownScore = result.score
        if ((reward?.coins ?: 0) > 0) audio.play(Sfx.COIN)
    }
    Overlay {
        PixelText(
            stringResource(if (finale) R.string.finale_win else R.string.result_win),
            size = 4,
            color = Pal.Gold,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        PixelText(title, size = 2, color = Pal.Outline, outline = null)
        if (finale) {
            // Koniec gry: złote ryby wokół podziękowania.
            VSpace(4)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Plate(pad = 1) { SpriteImage("fishgold1", 3) }
                HSpace(3)
                PixelText(
                    stringResource(R.string.finale_win_text),
                    size = 2,
                    color = Pal.WoodDark,
                    outline = null,
                    align = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                HSpace(3)
                Plate(pad = 1) { SpriteImage("fishgold2", 3) }
            }
        }
        VSpace(6)
        Plate { StarRow(shownStars, scale = 4, gap = 4) }
        VSpace(6)
        PixelText(stringResource(R.string.result_score), size = 2, color = Pal.Outline, outline = null)
        PixelText(shownScore.toString(), size = 5)
        if (reward?.newBest == true && shownScore == result.score) {
            PixelText(stringResource(R.string.result_new_best), size = 2, color = Pal.Red)
        }
        VSpace(5)
        if (reward != null && reward.finaleBonus > 0) {
            RewardTile(stringResource(R.string.finale_bonus), reward.finaleBonus, "fishscore_gold", Pal.Gold, Modifier.fillMaxWidth())
            VSpace(4)
        }
        if (reward != null && reward.challengePoints > 0) {
            RewardTile(stringResource(R.string.challenge_points_won), reward.challengePoints, "fishscore_blue", Pal.SeaLight, Modifier.fillMaxWidth())
            VSpace(4)
        }
        if (reward != null && (reward.coins - reward.finaleBonus > 0 || reward.streakDays > 0)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(4))) {
                if (reward.coins - reward.finaleBonus > 0) {
                    RewardTile(stringResource(R.string.result_coins), reward.coins - reward.finaleBonus, "fishscore_gold", Pal.Gold, Modifier.weight(1f))
                }
                if (reward.streakDays > 0) {
                    RewardTile(
                        stringResource(R.string.streak_title) + ": " +
                            pluralStringResource(R.plurals.streak_days, reward.streakDays, reward.streakDays),
                        reward.trophies,
                        "fishscore_blue",
                        Pal.SeaLight,
                        Modifier.weight(1f),
                    )
                }
            }
        }
        if (reward?.streakDeferred == true) {
            PixelText(stringResource(R.string.streak_deferred), size = 1, color = Pal.SeaLight, align = TextAlign.Center)
        }
        if (reward?.accepted == false) {
            PixelText(stringResource(R.string.result_rejected), size = 1, color = Pal.Red, align = TextAlign.Center)
        }
        VSpace(8)
        if (challenge) {
            // Poziom dnia/tygodnia zalicza się raz – po wygranej tylko powrót do menu.
            PixelButton(stringResource(R.string.main_menu), onClick = { onExit(GameExit.ToMenu) }, style = ButtonStyle.GREEN, size = 4, modifier = Modifier.fillMaxWidth())
        } else {
            if (level < LevelConstants.MAX_LEVEL) {
                PixelButton(
                    stringResource(R.string.next),
                    onClick = { onExit(GameExit.Next(level + 1)) },
                    style = ButtonStyle.GREEN,
                    size = 4,
                    modifier = Modifier.keyPrimary().fillMaxWidth(),
                )
                VSpace(4)
            }
            ExitButtons(stringResource(R.string.retry), level, onExit)
        }
    }
}

/** Kafelek nagrody na panelu wygranej – monety i passa stoją obok siebie w tym samym kształcie. */
@Composable
private fun RewardTile(label: String, amount: Int, icon: String, labelColor: Color, modifier: Modifier = Modifier) {
    val u = LocalUnit.current.toFloat()
    Column(
        modifier
            .drawBehind { pixelBox(u, Pal.SeaDark, Pal.Navy, Pal.Outline, shadow = 0f) }
            .padding(horizontal = px(3), vertical = px(3)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PixelText(label, size = 1, color = labelColor, maxLines = 1)
        VSpace(1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelText("+$amount", size = 3, color = Pal.Gold)
            HSpace(2)
            SpriteImage(icon, 2)
        }
    }
}

/**
 * Wspólny rząd wyjść z poziomu: od nowa, mapa (poza poziomem dnia/tygodnia), menu główne.
 * [backText] – zamiast mapy powrót do kreatora albo do poziomów graczy.
 */
@Composable
private fun ExitButtons(retryText: String, level: Int, onExit: (GameExit) -> Unit, showMap: Boolean = true, backText: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(3))) {
        PixelButton(retryText, onClick = { onExit(GameExit.Retry(level)) }, style = ButtonStyle.SEA, size = 2, modifier = Modifier.weight(1f))
        if (backText != null) {
            PixelButton(backText, onClick = { onExit(GameExit.ToMap) }, style = ButtonStyle.WOOD, size = 2, modifier = Modifier.weight(1f))
        } else if (showMap) {
            PixelButton(stringResource(R.string.to_map), onClick = { onExit(GameExit.ToMap) }, style = ButtonStyle.WOOD, size = 2, modifier = Modifier.weight(1f))
        }
        PixelButton(stringResource(R.string.main_menu), onClick = { onExit(GameExit.ToMenu) }, style = ButtonStyle.WOOD, size = 2, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun LosePanel(
    level: Int,
    title: String,
    challenge: Boolean,
    custom: CustomRun?,
    world: GameWorld,
    outcome: Outcome,
    onRevive: () -> Unit,
    onExit: (GameExit) -> Unit,
) {
    val services = LocalServices.current
    val repo = services.repo
    repo.revision.intValue
    // W samouczku i w teście z kreatora kontynuacja jest darmowa i bez limitu – nowy gracz nie ma jeszcze monet.
    // Przejście przed publikacją i poziomy graczy są bez kontynuacji: liczy się samo przejście.
    val free = world.tutorial || custom?.mode == CustomMode.TEST
    val cost = if (free) 0 else Catalog.reviveCost(world.continuesUsed)
    val canRevive = free || (custom == null && world.continuesUsed < 3)
    Overlay {
        PixelText(stringResource(R.string.result_lose), size = 5, color = Pal.Red)
        PixelText(title, size = 2, color = Pal.Outline, outline = null)
        PixelText(
            stringResource(if (outcome.reason == LoseReason.TIME) R.string.lose_time else R.string.lose_caught),
            size = 2,
            color = Pal.Outline,
            outline = null,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (custom?.mode == CustomMode.VERIFY) {
            VSpace(3)
            PixelText(stringResource(R.string.editor_verify_lost), size = 1, color = Pal.RedDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        VSpace(6)
        PixelText(stringResource(R.string.result_score), size = 2, color = Pal.Outline, outline = null)
        PixelText(outcome.result.score.toString(), size = 4)
        VSpace(8)
        if (canRevive) {
            val affordable = repo.progress.coins >= cost
            PixelButton(
                text = "",
                onClick = {
                    if (repo.progress.spend(cost)) {
                        repo.changed()
                        services.audio.play(Sfx.COIN)
                        onRevive()
                    }
                },
                style = ButtonStyle.GREEN,
                enabled = affordable,
                modifier = Modifier.fillMaxWidth(),
            ) {
                PixelText(stringResource(R.string.continue_play), size = 3)
                HSpace(4)
                SpriteImage("fishscore_gold", 2)
                HSpace(1)
                PixelText(cost.toString(), size = 3)
            }
            VSpace(4)
        }
        ExitButtons(stringResource(R.string.retry), level, onExit, showMap = !challenge, backText = customBack(custom))
    }
}

/** Napis przycisku powrotu z poziomu z kreatora: do kreatora albo do poziomów graczy. */
@Composable
private fun customBack(custom: CustomRun?): String? = when (custom?.mode) {
    null -> null
    CustomMode.PLAY -> stringResource(R.string.menu_community)
    else -> stringResource(R.string.editor_back)
}

@Composable
private fun PausePanel(
    level: Int,
    title: String,
    challenge: Boolean,
    custom: CustomRun?,
    tutorial: Boolean,
    world: GameWorld,
    onResume: () -> Unit,
    onExit: (GameExit) -> Unit,
) {
    val services = LocalServices.current
    val repo = services.repo
    repo.revision.intValue
    val flags = world.liveStarFlags()
    Overlay(onDismiss = onResume) {
        PixelText(stringResource(R.string.paused), size = 5)
        PixelText(
            if (tutorial) stringResource(R.string.tutorial_badge) else "$title · " + typeName(world.spec.type),
            size = 2,
            color = Pal.Outline,
            outline = null,
        )
        VSpace(6)
        for ((i, goal) in (if (tutorial) emptyList() else goalTexts(world.spec)).withIndex()) {
            Row(Modifier.fillMaxWidth().padding(vertical = px(1)), verticalAlignment = Alignment.CenterVertically) {
                val on = i > 0 && flags[i]
                Plate(pad = 1) { SpriteImage(if (on) "fishscore_gold" else "fishscore_grey", 2) }
                HSpace(4)
                PixelText(goal, size = 2, color = Pal.Outline, outline = null)
            }
        }
        VSpace(6)
        Row(horizontalArrangement = Arrangement.spacedBy(px(4))) {
            PixelButton(
                stringResource(R.string.set_music) + ": " + stringResource(if (repo.save.music) R.string.on else R.string.off),
                onClick = {
                    repo.save.music = !repo.save.music
                    services.audio.musicEnabled = repo.save.music
                    repo.changed()
                },
                style = ButtonStyle.SEA,
                size = 2,
            )
            PixelButton(
                stringResource(R.string.set_sounds) + ": " + stringResource(if (repo.save.sfx) R.string.on else R.string.off),
                onClick = {
                    repo.save.sfx = !repo.save.sfx
                    services.audio.sfxEnabled = repo.save.sfx
                    repo.changed()
                },
                style = ButtonStyle.SEA,
                size = 2,
            )
        }
        VSpace(8)
        PixelButton(stringResource(R.string.resume), onClick = onResume, style = ButtonStyle.GREEN, size = 4, modifier = Modifier.keyPrimary().fillMaxWidth())
        VSpace(4)
        ExitButtons(stringResource(R.string.restart), level, onExit, showMap = !challenge, backText = customBack(custom))
    }
}

/** Najwięcej znaków nazwy poziomu z kreatora w rogu ekranu (zamiast „Lv numer”). */
private const val CUSTOM_TAG = 14

/**
 * Wygrana w poziomie z kreatora. Test – wynik i powrót do kreatora, przejście przed publikacją – potwierdzenie,
 * poziom gracza – odpowiedź serwera (monety za pierwsze ukończenie, punkty rankingu) i głos na poziom.
 */
@Composable
private fun CustomWinPanel(custom: CustomRun, title: String, result: RunResult, reward: Reward?, playAt: Long?, onExit: (GameExit) -> Unit) {
    val services = LocalServices.current
    val online = services.online
    val audio = services.audio
    var shownStars by remember { mutableIntStateOf(0) }
    LaunchedEffect(result) {
        delay(250)
        for (i in 1..result.stars) {
            shownStars = i
            audio.play(Sfx.STAR)
            delay(380)
        }
    }
    val scope = rememberCoroutineScope()
    var vote by remember { mutableIntStateOf(0) }
    Overlay {
        PixelText(stringResource(R.string.result_win), size = 4, color = Pal.Gold, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        PixelText(title, size = 2, color = Pal.Outline, outline = null)
        Badge(
            stringResource(
                when (custom.mode) {
                    CustomMode.TEST -> R.string.editor_test_badge
                    CustomMode.VERIFY -> R.string.editor_verify_badge
                    CustomMode.PLAY -> R.string.type_custom
                },
            ),
            color = if (custom.mode == CustomMode.PLAY) ButtonStyle.GREEN else ButtonStyle.ORANGE,
        )
        VSpace(6)
        Plate { StarRow(shownStars, scale = 4, gap = 4) }
        VSpace(6)
        PixelText(stringResource(R.string.result_score), size = 2, color = Pal.Outline, outline = null)
        PixelText(result.score.toString(), size = 5)
        VSpace(5)
        when (custom.mode) {
            CustomMode.TEST -> Unit
            CustomMode.VERIFY -> PixelText(
                stringResource(if (custom.fromStage == 0) R.string.editor_verify_done else R.string.editor_verify_lost),
                size = 2,
                color = Pal.GreenDark,
                outline = null,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            CustomMode.PLAY -> {
                val answer = playAt?.let { online.community.outcomes[it] }
                when {
                    answer == null -> PixelText(
                        stringResource(if (online.isOnline()) R.string.community_sending else R.string.community_offline_result),
                        size = 1,
                        color = Pal.WoodDark,
                        outline = null,
                        align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    else -> {
                        if (answer.firstCompletion) PixelText(stringResource(R.string.community_first), size = 3, color = Pal.Gold)
                        if (answer.coins > 0 || answer.points > 0) {
                            VSpace(3)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(4))) {
                                if (answer.coins > 0) RewardTile(stringResource(R.string.result_coins), answer.coins, "fishscore_gold", Pal.Gold, Modifier.weight(1f))
                                if (answer.points > 0) RewardTile(stringResource(R.string.challenge_points_won), answer.points, "fishscore_blue", Pal.SeaLight, Modifier.weight(1f))
                            }
                        }
                        val note = when {
                            answer.overDailyLimit -> R.string.community_daily_limit
                            !answer.level.rated && online.loggedIn -> R.string.community_points_pending
                            !online.loggedIn -> R.string.community_guest
                            else -> null
                        }
                        note?.let {
                            VSpace(2)
                            PixelText(stringResource(it), size = 1, color = Pal.WoodDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                if (reward != null && reward.streakDays > 0) {
                    VSpace(3)
                    RewardTile(
                        stringResource(R.string.streak_title) + ": " + pluralStringResource(R.plurals.streak_days, reward.streakDays, reward.streakDays),
                        reward.trophies,
                        "fishscore_blue",
                        Pal.SeaLight,
                        Modifier.fillMaxWidth(),
                    )
                }
                VSpace(4)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(3))) {
                    for ((value, label) in listOf(1 to R.string.community_like, -1 to R.string.community_dislike)) {
                        PixelButton(
                            stringResource(label),
                            onClick = {
                                val target = if (vote == value) 0 else value
                                scope.launch { if (online.community.vote(custom.onlineId, target) is Net.Ok) vote = target }
                            },
                            style = if (vote == value) ButtonStyle.GREEN else ButtonStyle.SEA,
                            size = 1,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        VSpace(8)
        if (custom.mode == CustomMode.VERIFY) {
            PixelButton(stringResource(R.string.editor_back), onClick = { onExit(GameExit.ToMap) }, style = ButtonStyle.GREEN, size = 3, modifier = Modifier.keyPrimary().fillMaxWidth())
        } else {
            ExitButtons(stringResource(R.string.retry), UserLevels.LEVEL_NUMBER, onExit, backText = customBack(custom))
        }
    }
}

/** Powitanie nowego gracza: gra zaczyna się od razu w wodzie, więc mówimy, że to krótki samouczek. */
@Composable
private fun TutorialWelcome(onStart: () -> Unit, onSkip: () -> Unit) {
    Overlay(onDismiss = onStart) {
        PixelText(stringResource(R.string.tutorial_welcome_title), size = 4, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        VSpace(2)
        Badge(stringResource(R.string.tutorial_badge), color = ButtonStyle.GREEN)
        VSpace(6)
        PixelText(
            stringResource(R.string.tutorial_welcome_text),
            size = 2,
            color = Pal.Outline,
            outline = null,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        VSpace(4)
        PixelText(
            stringResource(R.string.tutorial_welcome_after),
            size = 1,
            color = Pal.WoodDark,
            outline = null,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        VSpace(8)
        PixelButton(stringResource(R.string.tutorial_start), onClick = onStart, style = ButtonStyle.GREEN, size = 4, modifier = Modifier.keyPrimary().fillMaxWidth())
        VSpace(4)
        PixelButton(stringResource(R.string.tutorial_skip), onClick = onSkip, style = ButtonStyle.WOOD, size = 2, modifier = Modifier.fillMaxWidth())
    }
}

/** Karta z bieżącym poleceniem samouczka – zawsze widoczna u góry ekranu. */
@Composable
private fun TutorialCard(step: TutorialStep, remaining: Int, modifier: Modifier = Modifier) {
    // Po zmianie kroku karta przez chwilę świeci na złoto, żeby gracz zauważył nowe polecenie.
    var fresh by remember(step) { mutableStateOf(true) }
    LaunchedEffect(step) {
        delay(1500)
        fresh = false
    }
    val repo = LocalServices.current.repo
    repo.revision.intValue
    val joystick = repo.save.controls == ControlMode.JOYSTICK
    DarkCard(modifier.widthIn(max = px(320)).fillMaxWidth(), highlight = fresh || step == TutorialStep.GOLDEN) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            PixelText(
                stringResource(R.string.tut_step, step.number, TutorialStep.COUNT),
                size = 1,
                color = Pal.Gold,
            )
            VSpace(2)
            PixelText(
                when (step) {
                    TutorialStep.MOVE -> stringResource(if (joystick) R.string.tut_move_joystick else R.string.tut_move)
                    TutorialStep.FIRST_FISH -> stringResource(R.string.tut_first_fish)
                    TutorialStep.FILL_QUOTA -> pluralStringResource(R.plurals.tut_fill_quota, remaining, remaining)
                    TutorialStep.GATE -> stringResource(R.string.tut_gate)
                    TutorialStep.MONSTER -> stringResource(R.string.tut_monster)
                    TutorialStep.GOLDEN -> stringResource(R.string.tut_golden)
                    TutorialStep.DODGE_AND_FISH -> pluralStringResource(R.plurals.tut_dodge, remaining, remaining)
                    TutorialStep.FINISH -> stringResource(R.string.tut_finish)
                },
                size = 2,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Koniec samouczka: najważniejsze zasady gry i przejście do pierwszego prawdziwego poziomu. */
@Composable
private fun TutorialDonePanel(onExit: (GameExit) -> Unit) {
    Overlay {
        PixelText(stringResource(R.string.tut_done_title), size = 4, color = Pal.Gold, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        VSpace(6)
        for ((icon, text) in listOf(
            "fishscore_gold" to R.string.tut_done_stars,
            "fishgold1" to R.string.tut_done_shop,
            "fishscore_blue" to R.string.tut_done_streak,
        )) {
            Row(Modifier.fillMaxWidth().padding(vertical = px(2)), verticalAlignment = Alignment.CenterVertically) {
                Plate(pad = 1) { SpriteImage(icon, 2) }
                HSpace(4)
                PixelText(stringResource(text), size = 2, color = Pal.Outline, outline = null, modifier = Modifier.weight(1f))
            }
        }
        VSpace(8)
        PixelButton(
            stringResource(R.string.tut_play_first),
            onClick = { onExit(GameExit.Next(1)) },
            style = ButtonStyle.GREEN,
            size = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        VSpace(4)
        PixelButton(stringResource(R.string.main_menu), onClick = { onExit(GameExit.ToMenu) }, style = ButtonStyle.WOOD, size = 2, modifier = Modifier.fillMaxWidth())
    }
}
