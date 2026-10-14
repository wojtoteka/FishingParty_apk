package wojtoteka.ovh.fishingparty.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.pluralStringResource
import wojtoteka.ovh.fishingparty.data.StreakRewards
import wojtoteka.ovh.fishingparty.data.StreakState
import androidx.compose.ui.text.style.TextAlign
import wojtoteka.ovh.fishingparty.level.LevelConstants
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.stringResource
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.gfx.ShipLook
import wojtoteka.ovh.fishingparty.level.Worlds

enum class Destination { MAP, SHOP, ACHIEVEMENTS, RANKING, ACCOUNT, SETTINGS, DAILY, REPAIR, SOON }

@Composable
fun MenuScreen(onPlay: (Int) -> Unit, onOpen: (Destination) -> Unit) {
    val repo = LocalServices.current.repo
    repo.revision.intValue
    val progress = repo.progress
    val current = progress.highestUnlocked()
    val world = Worlds.paletteOf(current)
    val look = ShipLook(repo.save.boat, repo.save.sail, repo.save.sailor)
    val claimable = progress.claimable().size
    val daily = progress.dailyState().available
    val streak = progress.streakState()
    val repair = progress.streakRepair()
    val lost = progress.streakLost()
    StreakNotificationPermission(streak.days > 0)

    val unit = LocalUnit.current
    val bob = rememberInfiniteTransition(label = "logo")
    val wave by bob.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "wave",
    )

    // Łódź płynie w wolnym pasie między tytułem a przyciskami – nie zasłania ani napisu, ani przycisków.
    var screenH by remember { mutableFloatStateOf(1f) }
    var titleBottom by remember { mutableFloatStateOf(0f) }
    var buttonsTop by remember { mutableFloatStateOf(0f) }
    val shipY = if (buttonsTop > titleBottom) ((titleBottom + buttonsTop) / 2f / screenH + 0.02f) else 0.5f

    Box(Modifier.fillMaxSize().onGloballyPositioned { screenH = it.size.height.toFloat().coerceAtLeast(1f) }) {
        SeaBackdrop(world, Modifier.fillMaxSize(), ship = look, shipY = shipY)
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = px(10), vertical = px(6)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Plate(pad = 2) { CoinCounter(progress.coins, size = 3) }
                Box(Modifier.weight(1f))
                Plate(pad = 2) {
                    SpriteImage("fishscore_gold", 2)
                    HSpace(2)
                    PixelText("${progress.totalStars()}/${progress.levelsAvailable() * 3}", size = 2)
                }
            }
            // Karta passy nie przykleja się do górnej krawędzi – stoi bliżej środka, w zasięgu kciuka.
            Box(Modifier.weight(0.5f))
            // Karta passy otwiera nagrodę dnia (albo naprawę przerwanej passy); „!” – nagroda czeka na odebranie.
            Box(Modifier.widthIn(max = px(300)).fillMaxWidth()) {
                StreakCard(
                    streak,
                    repairDays = repair?.days ?: 0,
                    lost = lost,
                    onRepair = { onOpen(Destination.REPAIR) },
                    modifier = Modifier.noRippleClick { onOpen(if (repair != null) Destination.REPAIR else Destination.DAILY) },
                )
                if (daily && repair == null) {
                    Badge(
                        "!",
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset { IntOffset(0, (wave * unit).toInt()) },
                    )
                }
            }
            VSpace(8)
            // Tytuł bierze wolne miejsce nad przyciskami. Na niskim ekranie (tablet w poziomie)
            // maleje, zamiast wypychać dolny rząd przycisków poza ekran.
            BoxWithConstraints(Modifier.weight(5f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                val titleSize = titleSizeFor(constraints.maxHeight / unit)
                Column(
                    Modifier
                        .onGloballyPositioned { titleBottom = it.boundsInRoot().bottom },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PixelText("FISHING", size = titleSize)
                    PixelText("PARTY", size = titleSize, color = Pal.Sand)
                }
            }
            Column(
                Modifier
                    .widthIn(max = px(300))
                    .fillMaxWidth()
                    .onGloballyPositioned { buttonsTop = it.boundsInRoot().top },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(px(5)),
            ) {
                PixelButton(
                    text = "",
                    onClick = { onPlay(current) },
                    style = ButtonStyle.GREEN,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PixelText(stringResource(R.string.play), size = 6)
                        PixelText(stringResource(R.string.level_n, current), size = 2, color = Pal.GreenLight)
                    }
                }
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(px(5))) {
                    PixelButton(stringResource(R.string.map_title), onClick = { onOpen(Destination.MAP) }, style = ButtonStyle.SEA, modifier = Modifier.weight(1f).fillMaxHeight())
                    PixelButton(stringResource(R.string.shop_title), onClick = { onOpen(Destination.SHOP) }, style = ButtonStyle.SAND, modifier = Modifier.weight(1f).fillMaxHeight())
                }
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(px(5))) {
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        PixelButton(
                            stringResource(R.string.achievements_short),
                            onClick = { onOpen(Destination.ACHIEVEMENTS) },
                            style = ButtonStyle.WOOD,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (claimable > 0) Badge(claimable.toString(), Modifier.align(Alignment.TopEnd))
                    }
                    PixelButton(
                        stringResource(R.string.ranking_title),
                        onClick = { onOpen(Destination.RANKING) },
                        style = ButtonStyle.WOOD,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(px(5))) {
                    PixelButton(
                        stringResource(R.string.settings_title),
                        onClick = { onOpen(Destination.SETTINGS) },
                        style = ButtonStyle.SEA,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    PixelButton(
                        stringResource(R.string.account_title),
                        onClick = { onOpen(Destination.ACCOUNT) },
                        style = ButtonStyle.SEA,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                // Zapowiedź: poziomy dnia i tygodnia (ten sam seed dla wszystkich) przyjdą w kolejnej aktualizacji.
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(px(5))) {
                    SoonButton(stringResource(R.string.daily_level), onClick = { onOpen(Destination.SOON) }, Modifier.weight(1f).fillMaxHeight())
                    SoonButton(stringResource(R.string.weekly_level), onClick = { onOpen(Destination.SOON) }, Modifier.weight(1f).fillMaxHeight())
                }
            }
            Box(Modifier.weight(0.5f))
            VSpace(BOTTOM_GAP)
        }
    }
}

/** Największy rozmiar tytułu (2 linie po 13 × (rozmiar − 1) jednostek), który zostawia pod sobą pas na łódź. */
private fun titleSizeFor(heightUnits: Int): Int =
    (7 downTo 4).firstOrNull { 26 * (it - 1) + TITLE_GAP <= heightUnits } ?: 3

private const val TITLE_GAP = 24

/** Przycisk trybu, który dopiero nadchodzi – szary, z czerwoną plakietką SOON. */
@Composable
private fun SoonButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        PixelButton(text, onClick = onClick, style = ButtonStyle.GREY, size = 2, modifier = Modifier.fillMaxSize())
        Badge(stringResource(R.string.soon), Modifier.align(Alignment.TopEnd))
    }
}

/** Zapowiedź poziomów dnia i tygodnia. */
@Composable
fun SoonDialog(onClose: () -> Unit) {
    Overlay(onDismiss = onClose) {
        PixelText(stringResource(R.string.soon_title), size = 4, color = Pal.Gold, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        VSpace(6)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(4))) {
            for (label in listOf(R.string.daily_level, R.string.weekly_level)) {
                DarkCard(Modifier.weight(1f)) {
                    Column(Modifier.fillMaxWidth().padding(vertical = px(3)), horizontalAlignment = Alignment.CenterHorizontally) {
                        SpriteImage("fishscore_gold", 2)
                        VSpace(2)
                        PixelText(stringResource(label), size = 1, align = TextAlign.Center)
                    }
                }
            }
        }
        VSpace(6)
        PixelText(stringResource(R.string.soon_text), size = 2, color = Pal.WoodDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        VSpace(8)
        PixelButton(stringResource(R.string.close), onClick = onClose, style = ButtonStyle.WOOD, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Karta passy: bieżący tydzień jako 7 pól z trofeami do zdobycia. Po 7. dniu
 * pola zaczynają się od nowa, a stawki rosną z każdym tygodniem.
 */
@Composable
private fun StreakCard(streak: StreakState, repairDays: Int, lost: Boolean, onRepair: () -> Unit, modifier: Modifier = Modifier) {
    val u = LocalUnit.current.toFloat()
    // Dzień, o który teraz toczy się gra: dzisiejszy zaliczony albo następny do zaliczenia.
    val focus = if (streak.playedToday) streak.days else streak.days + 1
    val week = StreakRewards.week(focus)
    val first = (week - 1) * StreakRewards.WEEK + 1
    Column(
        modifier
            .widthIn(max = px(300))
            .fillMaxWidth()
            .drawBehind { pixelBox(u, Pal.SeaDark.copy(alpha = 0.94f), Pal.Navy, Pal.Outline, shadow = 0f) }
            .padding(horizontal = px(5), vertical = px(4)),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PixelText(
                stringResource(R.string.streak_title) + ": " +
                    pluralStringResource(R.plurals.streak_days, streak.days, streak.days),
                size = 2,
                color = if (streak.playedToday) Pal.Gold else Pal.White,
                modifier = Modifier.weight(1f),
            )
            PixelText(stringResource(R.string.streak_week, week), size = 1, color = Pal.SeaLight)
            if (week > 1) {
                HSpace(2)
                PixelText("x${StreakRewards.multiplier(week)}", size = 1, color = Pal.GreenLight)
            }
        }
        VSpace(3)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(2))) {
            for (i in 0 until StreakRewards.WEEK) {
                val day = first + i
                StreakDay(i + 1, StreakRewards.trophiesFor(day), done = day <= streak.days, today = day == focus, Modifier.weight(1f))
            }
        }
        VSpace(3)
        if (repairDays > 0) {
            // Przerwana passa, którą da się jeszcze odzyskać – wyraźny przycisk zamiast zwykłego podpisu.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    PixelText(stringResource(R.string.streak_broken), size = 2, color = Pal.RedLight)
                    PixelText(pluralStringResource(R.plurals.streak_days, repairDays, repairDays), size = 1, color = Pal.Foam)
                }
                PixelButton(stringResource(R.string.streak_repair), onClick = onRepair, style = ButtonStyle.RED, size = 2)
            }
        } else {
            PixelText(
                stringResource(
                    when {
                        streak.playedToday -> R.string.streak_safe
                        streak.days > 0 -> R.string.streak_keep
                        lost -> R.string.streak_lost
                        else -> R.string.streak_start
                    },
                ),
                size = 1,
                color = if (streak.playedToday) Pal.GreenLight else Pal.SeaLight,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StreakDay(dayOfWeek: Int, trophies: Int, done: Boolean, today: Boolean, modifier: Modifier) {
    val u = LocalUnit.current.toFloat()
    val style = when {
        done -> ButtonStyle.SAND
        today -> ButtonStyle.SEA
        else -> null
    }
    Column(
        modifier.drawBehind {
            if (style != null) pixelBox(u, style.base, style.light, style.dark, shadow = 1f)
            else pixelBox(u, Pal.Navy, Pal.LevelBlue, Pal.Outline, shadow = 1f)
        }.padding(top = px(2), bottom = px(3)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PixelText(dayOfWeek.toString(), size = 1, color = if (style == null) Pal.SeaLight else Pal.White)
        SpriteImage(if (done || today) "fishscore_blue" else "fishscore_grey", 2)
        PixelText("+$trophies", size = 1, color = if (dayOfWeek == StreakRewards.WEEK) Pal.Gold else Pal.White)
    }
}

/** Na Androidzie 13+ prosimy o zgodę na powiadomienia, gdy gracz zacznie pierwszą passę (raz). */
@Composable
private fun StreakNotificationPermission(hasStreak: Boolean) {
    if (Build.VERSION.SDK_INT < 33) return
    val repo = LocalServices.current.repo
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(hasStreak) {
        if (hasStreak && !repo.save.notificationsAsked) {
            repo.save.notificationsAsked = true
            repo.changed()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier, color: ButtonStyle = ButtonStyle.RED) {
    val u = LocalUnit.current.toFloat()
    Box(
        modifier
            .offset(x = px(4), y = px(-5))
            .drawBehind { pixelBox(u, color.base, color.light, color.dark, shadow = 1f) }
            .padding(horizontal = px(3), vertical = px(1)),
    ) {
        PixelText(text, size = 1)
    }
}
