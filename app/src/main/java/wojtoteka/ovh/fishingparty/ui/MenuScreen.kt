package wojtoteka.ovh.fishingparty.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.stringResource
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.gfx.ShipLook
import wojtoteka.ovh.fishingparty.level.LevelGenerator

enum class Destination { MAP, SHOP, ACHIEVEMENTS, RANKING, SETTINGS, DAILY }

@Composable
fun MenuScreen(onPlay: (Int) -> Unit, onOpen: (Destination) -> Unit) {
    val repo = LocalServices.current.repo
    repo.revision.intValue
    val progress = repo.progress
    val current = progress.highestUnlocked()
    val world = LevelGenerator.worldOf(current)
    val look = ShipLook(repo.save.boat, repo.save.sail, repo.save.sailor)
    val claimable = progress.claimable().size
    val daily = progress.dailyState().available
    val streak = progress.streakState()
    StreakNotificationPermission(streak.days > 0)

    val unit = LocalUnit.current
    val bob = rememberInfiniteTransition(label = "logo")
    val wave by bob.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "wave",
    )

    Box(Modifier.fillMaxSize()) {
        SeaBackdrop(world, Modifier.fillMaxSize(), ship = look, shipY = 0.5f)
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
                    PixelText("${progress.totalStars()}/${LevelConstants.LEVEL_COUNT * 3}", size = 2)
                }
            }
            VSpace(5)
            StreakCard(streak, Modifier.noRippleClick { onOpen(Destination.RANKING) })
            VSpace(8)
            Column(Modifier.offset { IntOffset(0, (wave * 2f * unit).toInt()) }, horizontalAlignment = Alignment.CenterHorizontally) {
                PixelText("FISHING", size = 7)
                PixelText("PARTY", size = 7, color = Pal.Sand)
            }
            Box(Modifier.weight(1f))
            Column(
                Modifier.widthIn(max = px(260)).fillMaxWidth(),
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(5))) {
                    PixelButton(stringResource(R.string.map_title), onClick = { onOpen(Destination.MAP) }, style = ButtonStyle.SEA, modifier = Modifier.weight(1f))
                    PixelButton(stringResource(R.string.shop_title), onClick = { onOpen(Destination.SHOP) }, style = ButtonStyle.SAND, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(5))) {
                    Box(Modifier.weight(1f)) {
                        PixelButton(
                            stringResource(R.string.achievements_short),
                            onClick = { onOpen(Destination.ACHIEVEMENTS) },
                            style = ButtonStyle.WOOD,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (claimable > 0) Badge(claimable.toString(), Modifier.align(Alignment.TopEnd))
                    }
                    Box(Modifier.weight(1f)) {
                        PixelButton(
                            stringResource(R.string.ranking_title),
                            onClick = { onOpen(Destination.RANKING) },
                            style = ButtonStyle.WOOD,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Badge(stringResource(R.string.soon), Modifier.align(Alignment.TopEnd), color = ButtonStyle.SEA)
                    }
                }
                PixelButton(
                    stringResource(R.string.settings_title),
                    onClick = { onOpen(Destination.SETTINGS) },
                    style = ButtonStyle.SEA,
                    size = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (daily) {
                    PixelButton(
                        stringResource(R.string.daily_title),
                        onClick = { onOpen(Destination.DAILY) },
                        style = ButtonStyle.RED,
                        icon = "fishscore_gold",
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(0, (wave * unit).toInt()) },
                    )
                }
            }
            Box(Modifier.weight(0.6f))
            VSpace(6)
        }
    }
}

/**
 * Karta passy: bieżący tydzień jako 7 pól z trofeami do zdobycia. Po 7. dniu
 * pola zaczynają się od nowa, a stawki rosną z każdym tygodniem.
 */
@Composable
private fun StreakCard(streak: StreakState, modifier: Modifier = Modifier) {
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
        PixelText(
            stringResource(
                when {
                    streak.playedToday -> R.string.streak_safe
                    streak.days > 0 -> R.string.streak_keep
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
