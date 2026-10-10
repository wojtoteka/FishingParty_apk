package wojtoteka.ovh.fishingparty.ui

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
import wojtoteka.ovh.fishingparty.data.Reward
import wojtoteka.ovh.fishingparty.game.GameListener
import wojtoteka.ovh.fishingparty.game.GameRenderer
import wojtoteka.ovh.fishingparty.game.GameWorld
import wojtoteka.ovh.fishingparty.game.HudStrings
import wojtoteka.ovh.fishingparty.game.Loadout
import wojtoteka.ovh.fishingparty.game.LoseReason
import wojtoteka.ovh.fishingparty.game.Sfx
import wojtoteka.ovh.fishingparty.game.TouchStick
import wojtoteka.ovh.fishingparty.gfx.ShipLook
import wojtoteka.ovh.fishingparty.level.LevelConstants
import wojtoteka.ovh.fishingparty.level.LevelGenerator
import wojtoteka.ovh.fishingparty.level.RunResult
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

@Composable
fun GameScreen(level: Int, loadout: Loadout, attempt: Int, replayTutorial: Boolean = false, onExit: (GameExit) -> Unit) {
    val services = LocalServices.current
    val repo = services.repo
    val audio = services.audio
    val tutorial = remember(level, attempt) { level == 1 && (replayTutorial || !repo.save.tutorialDone) }
    val plan = remember(level) { LevelGenerator.plan(level) }
    val layout = remember(level, attempt) { LevelGenerator.generate(level) }

    var outcome by remember(attempt) { mutableStateOf<Outcome?>(null) }
    var reward by remember(attempt) { mutableStateOf<Reward?>(null) }
    var paused by remember(attempt) { mutableStateOf(false) }
    var applied by remember(attempt) { mutableStateOf(false) }

    fun apply(result: RunResult) {
        if (applied) return
        applied = true
        reward = repo.progress.applyRun(result, plan)
        if (result.completed && level == 1) repo.save.tutorialDone = true
        repo.changed()
    }

    val listener = remember(attempt) {
        object : GameListener {
            override fun onSound(sfx: Sfx) = audio.play(sfx)
            override fun onVibrate(millis: Long) = audio.vibrate(millis)
            override fun onFinished(result: RunResult, reason: LoseReason?) {
                outcome = Outcome(result, reason)
                if (result.completed) apply(result)
            }
        }
    }
    val world = remember(layout) { GameWorld(layout, loadout, tutorial, listener) }

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
    )
    val look = ShipLook(repo.save.boat, repo.save.sail, repo.save.sailor)
    val renderer = remember(attempt) { GameRenderer(hud, look) }
    val stick = remember { TouchStick() }
    val frame = remember { mutableLongStateOf(0L) }
    val density = LocalDensity.current
    val dead = with(density) { 10.dp.toPx() }
    val radius = with(density) { 64.dp.toPx() }
    val topInset = WindowInsets.safeDrawing.getTop(density).toFloat()

    LaunchedEffect(world) {
        audio.play(Sfx.START)
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (stick.active) {
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
                world.setInput(stick.active && stick.engaged, stick.angle)
                world.paused = paused || outcome != null
                if (last != 0L) world.advance(min((now - last) / 1e9f, 0.1f))
                last = now
                frame.longValue = now
            }
        }
    }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, attempt) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && outcome == null) paused = true
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = outcome == null) { paused = !paused }
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
                        stick.active = true
                        stick.originX = down.position.x
                        stick.originY = down.position.y
                        stick.x = down.position.x
                        stick.y = down.position.y
                        stick.engaged = false
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
            drawIntoCanvas { c ->
                renderer.draw(c.nativeCanvas, world, size.width.toInt(), size.height.toInt(), topInset, stick)
            }
        }

        if (outcome == null && !paused) {
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

        val current = outcome
        when {
            current != null && current.result.completed -> WinPanel(level, current.result, reward, onExit)
            current != null -> LosePanel(
                level = level,
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
private fun WinPanel(level: Int, result: RunResult, reward: Reward?, onExit: (GameExit) -> Unit) {
    val audio = LocalServices.current.audio
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
        PixelText(stringResource(R.string.result_win), size = 4, color = Pal.Gold, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        PixelText(stringResource(R.string.level_n, level), size = 2, color = Pal.Outline, outline = null)
        VSpace(6)
        Plate { StarRow(shownStars, scale = 4, gap = 4) }
        VSpace(6)
        PixelText(stringResource(R.string.result_score), size = 2, color = Pal.Outline, outline = null)
        PixelText(shownScore.toString(), size = 5)
        if (reward?.newBest == true && shownScore == result.score) {
            PixelText(stringResource(R.string.result_new_best), size = 2, color = Pal.Red)
        }
        VSpace(5)
        if (reward != null && (reward.coins > 0 || reward.streakDays > 0)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(4))) {
                if (reward.coins > 0) {
                    RewardTile(stringResource(R.string.result_coins), reward.coins, "fishscore_gold", Pal.Gold, Modifier.weight(1f))
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
        if (reward?.accepted == false) {
            PixelText(stringResource(R.string.result_rejected), size = 1, color = Pal.Red, align = TextAlign.Center)
        }
        VSpace(8)
        if (level < LevelConstants.LEVEL_COUNT) {
            PixelButton(
                stringResource(R.string.next),
                onClick = { onExit(GameExit.Next(level + 1)) },
                style = ButtonStyle.GREEN,
                size = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            VSpace(4)
        }
        ExitButtons(stringResource(R.string.retry), level, onExit)
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

/** Wspólny rząd wyjść z poziomu: od nowa, mapa, menu główne. */
@Composable
private fun ExitButtons(retryText: String, level: Int, onExit: (GameExit) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(3))) {
        PixelButton(retryText, onClick = { onExit(GameExit.Retry(level)) }, style = ButtonStyle.SEA, size = 2, modifier = Modifier.weight(1f))
        PixelButton(stringResource(R.string.to_map), onClick = { onExit(GameExit.ToMap) }, style = ButtonStyle.WOOD, size = 2, modifier = Modifier.weight(1f))
        PixelButton(stringResource(R.string.main_menu), onClick = { onExit(GameExit.ToMenu) }, style = ButtonStyle.WOOD, size = 2, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun LosePanel(level: Int, world: GameWorld, outcome: Outcome, onRevive: () -> Unit, onExit: (GameExit) -> Unit) {
    val services = LocalServices.current
    val repo = services.repo
    repo.revision.intValue
    val cost = Catalog.reviveCost(world.continuesUsed)
    val canRevive = world.continuesUsed < 3
    Overlay {
        PixelText(stringResource(R.string.result_lose), size = 5, color = Pal.Red)
        PixelText(
            stringResource(if (outcome.reason == LoseReason.TIME) R.string.lose_time else R.string.lose_caught),
            size = 2,
            color = Pal.Outline,
            outline = null,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
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
        ExitButtons(stringResource(R.string.retry), level, onExit)
    }
}

@Composable
private fun PausePanel(level: Int, world: GameWorld, onResume: () -> Unit, onExit: (GameExit) -> Unit) {
    val services = LocalServices.current
    val repo = services.repo
    repo.revision.intValue
    val flags = world.liveStarFlags()
    Overlay(onDismiss = onResume) {
        PixelText(stringResource(R.string.paused), size = 5)
        PixelText(stringResource(R.string.level_n, level) + " · " + typeName(world.spec.type), size = 2, color = Pal.Outline, outline = null)
        VSpace(6)
        for ((i, goal) in goalTexts(world.spec).withIndex()) {
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
        PixelButton(stringResource(R.string.resume), onClick = onResume, style = ButtonStyle.GREEN, size = 4, modifier = Modifier.fillMaxWidth())
        VSpace(4)
        ExitButtons(stringResource(R.string.restart), level, onExit)
    }
}
