package wojtoteka.ovh.fishingparty.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import wojtoteka.ovh.fishingparty.AppLanguage
import wojtoteka.ovh.fishingparty.BuildConfig
import wojtoteka.ovh.fishingparty.Language
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.data.Achievements
import wojtoteka.ovh.fishingparty.data.DailyRewards
import wojtoteka.ovh.fishingparty.game.Sfx
import wojtoteka.ovh.fishingparty.level.LevelConstants
import wojtoteka.ovh.fishingparty.level.LevelGenerator

@Composable
private fun ScreenFrame(title: String, onBack: () -> Unit, dim: Float = 0.35f, content: @Composable ColumnScope.() -> Unit) {
    val repo = LocalServices.current.repo
    Box(Modifier.fillMaxSize()) {
        SeaBackdrop(LevelGenerator.worldOf(repo.progress.highestUnlocked()), Modifier.fillMaxSize(), dim = dim)
        Column(Modifier.fillMaxSize()) {
            TopBar(title, onBack)
            content()
        }
    }
}

@Composable
fun AchievementsScreen(onBack: () -> Unit) {
    val services = LocalServices.current
    val repo = services.repo
    repo.revision.intValue
    val progress = repo.progress
    val visible = Achievements.all.filter { !it.hidden || progress.isUnlocked(it) }
    // Kolejność ustalana raz przy wejściu – odebrana odznaka zostaje na miejscu,
    // inaczej lista przeskakiwała za nią na sam dół.
    val order = remember {
        visible.sortedBy {
            when {
                progress.isUnlocked(it) && !progress.isClaimed(it) -> 0
                !progress.isUnlocked(it) -> 1
                else -> 2
            }
        }.map { it.id }
    }
    val sorted = order.mapNotNull { id -> visible.firstOrNull { it.id == id } }
    ScreenFrame(stringResource(R.string.achievements_title), onBack) {
        PixelText(
            stringResource(R.string.achievements_count, visible.count { progress.isUnlocked(it) }, visible.size),
            size = 2,
            color = Pal.Foam,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        LazyColumn(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars),
            contentPadding = PaddingValues(px(6)),
            verticalArrangement = Arrangement.spacedBy(px(4)),
        ) {
            items(sorted, key = { it.id }) { a ->
                val unlocked = progress.isUnlocked(a)
                val claimed = progress.isClaimed(a)
                DarkCard(Modifier.fillMaxWidth(), highlight = unlocked && !claimed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.widthIn(min = px(30)), contentAlignment = Alignment.Center) {
                            SpriteImage(a.icon, 2, alpha = if (unlocked) 1f else 0.45f)
                        }
                        HSpace(4)
                        Column(Modifier.weight(1f)) {
                            PixelText(achievementTitle(a), size = 2, color = if (unlocked) Pal.Gold else Pal.White)
                            PixelText(achievementDescription(a), size = 1, color = Pal.Foam)
                            VSpace(2)
                            val value = progress.metric(a.metric).coerceAtMost(a.target)
                            PixelBar(value.toFloat() / a.target, Modifier.fillMaxWidth().height(px(5)))
                            PixelText("$value/${a.target}", size = 1, color = Pal.SeaLight)
                        }
                        HSpace(4)
                        when {
                            claimed -> PixelText(stringResource(R.string.claimed), size = 1, color = Pal.GreenLight)
                            unlocked -> PixelButton(
                                text = "",
                                onClick = {
                                    if (progress.claim(a) > 0) {
                                        services.audio.play(Sfx.COIN)
                                        repo.changed()
                                    }
                                },
                                style = ButtonStyle.GREEN,
                            ) {
                                SpriteImage("fishscore_gold", 1)
                                HSpace(1)
                                PixelText("+${a.reward}", size = 2)
                            }
                            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                                SpriteImage("fishscore_gold", 1, alpha = 0.6f)
                                HSpace(1)
                                PixelText(a.reward.toString(), size = 1, color = Pal.GreyLight)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RankingScreen(onBack: () -> Unit) {
    val repo = LocalServices.current.repo
    repo.revision.intValue
    val progress = repo.progress
    ScreenFrame(stringResource(R.string.ranking_title), onBack) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(px(10)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            VSpace(10)
            PixelText(
                stringResource(R.string.soon),
                size = 9,
                color = Pal.Gold,
                modifier = Modifier.rotate(-8f),
            )
            VSpace(10)
            PixelPanel(Modifier.fillMaxWidth().widthIn(max = px(320))) {
                PixelText(stringResource(R.string.ranking_points), size = 2, color = Pal.Outline, outline = null)
                PixelText(progress.rankingPoints().toString(), size = 6)
                VSpace(4)
                PixelText(
                    stringResource(R.string.ranking_info),
                    size = 1,
                    color = Pal.WoodDark,
                    outline = null,
                    align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            VSpace(8)
            DarkCard(Modifier.fillMaxWidth().widthIn(max = px(320))) {
                Column(Modifier.fillMaxWidth()) {
                    StatRow(stringResource(R.string.stat_levels), "${progress.completedLevels()}/${LevelConstants.LEVEL_COUNT}")
                    StatRow(stringResource(R.string.stat_stars), "${progress.totalStars()}/${LevelConstants.LEVEL_COUNT * 3}")
                    StatRow(stringResource(R.string.stat_fish), repo.save.totalFish.get().toString())
                    StatRow(stringResource(R.string.stat_golden), repo.save.totalGolden.get().toString())
                    StatRow(stringResource(R.string.stat_combo), "x${repo.save.maxCombo}")
                    StatRow(stringResource(R.string.stat_trophies), progress.trophies.toString())
                    StatRow(stringResource(R.string.stat_streak), pluralStringResource(R.plurals.streak_days, repo.save.streakBest, repo.save.streakBest))
                }
            }
            VSpace(8)
            PixelPanel(Modifier.fillMaxWidth().widthIn(max = px(320))) {
                val streak = progress.streakState()
                PixelText(stringResource(R.string.streak_title), size = 2, color = Pal.Outline, outline = null)
                PixelText(pluralStringResource(R.plurals.streak_days, streak.days, streak.days), size = 4)
                VSpace(3)
                PixelText(
                    stringResource(R.string.streak_info),
                    size = 1,
                    color = Pal.WoodDark,
                    outline = null,
                    align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = px(2)), verticalAlignment = Alignment.CenterVertically) {
        PixelText(label, size = 2, color = Pal.Foam, modifier = Modifier.weight(1f))
        PixelText(value, size = 2)
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val services = LocalServices.current
    val repo = services.repo
    repo.revision.intValue
    val save = repo.save
    var confirmReset by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        ScreenFrame(stringResource(R.string.settings_title), onBack) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(px(10)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(px(5)),
            ) {
                Toggle(stringResource(R.string.set_music), save.music) {
                    save.music = it
                    services.audio.musicEnabled = it
                    repo.changed()
                }
                Toggle(stringResource(R.string.set_sounds), save.sfx) {
                    save.sfx = it
                    services.audio.sfxEnabled = it
                    repo.changed()
                }
                Toggle(stringResource(R.string.set_vibration), save.vibration) {
                    save.vibration = it
                    services.audio.vibrationEnabled = it
                    if (it) services.audio.vibrate(40)
                    repo.changed()
                }
                VSpace(4)
                LanguagePicker()
                VSpace(10)
                PixelButton(
                    stringResource(R.string.set_reset),
                    onClick = { confirmReset = true },
                    style = ButtonStyle.RED,
                    size = 2,
                )
                VSpace(10)
                PixelText(stringResource(R.string.set_author), size = 3, color = Pal.Gold)
                PixelText(
                    stringResource(R.string.set_credits),
                    size = 1,
                    color = Pal.Foam,
                    align = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                PixelText(stringResource(R.string.version, BuildConfig.VERSION_NAME), size = 1, color = Pal.SeaLight)
            }
        }
        if (confirmReset) {
            ConfirmDialog(
                message = stringResource(R.string.set_reset_confirm),
                onConfirm = {
                    repo.progress.resetProgress()
                    repo.changed()
                    confirmReset = false
                },
                onCancel = { confirmReset = false },
            )
        }
    }
}

/** Wybór języka: AUTO (wg telefonu), polski, angielski – każdy z pikselową flagą. */
@Composable
private fun LanguagePicker() {
    val context = LocalContext.current
    val current = remember { AppLanguage.current(context) }
    val phonePolish = remember { Resources.getSystem().configuration.locales[0].language == "pl" }
    DarkCard(Modifier.fillMaxWidth().widthIn(max = px(320))) {
        Column(Modifier.fillMaxWidth()) {
            PixelText(stringResource(R.string.set_language), size = 3)
            VSpace(4)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(3))) {
                for (language in Language.entries) {
                    val selected = language == current
                    PixelButton(
                        text = "",
                        onClick = { context.findActivity()?.let { AppLanguage.set(it, language) } },
                        style = if (selected) ButtonStyle.GREEN else ButtonStyle.SEA,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val flag = if (language == Language.AUTO) {
                                if (phonePolish) Language.PL else Language.EN
                            } else {
                                language
                            }
                            Flag(flag)
                            VSpace(2)
                            PixelText(
                                stringResource(
                                    when (language) {
                                        Language.AUTO -> R.string.lang_auto
                                        Language.PL -> R.string.lang_pl
                                        Language.EN -> R.string.lang_en
                                    },
                                ),
                                size = 2,
                                color = if (selected) Pal.White else Pal.Foam,
                            )
                        }
                    }
                }
            }
            VSpace(2)
            PixelText(
                stringResource(R.string.lang_auto) + " = " + stringResource(R.string.lang_auto_hint),
                size = 1,
                color = Pal.SeaLight,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Flaga 17×12 pikseli interfejsu, rysowana na tej samej siatce co ramki i przyciski. */
@Composable
private fun Flag(language: Language) {
    val u = LocalUnit.current.toFloat()
    Box(
        Modifier.size(px(17), px(12)).drawBehind {
            fun cell(x: Int, y: Int, w: Int, h: Int, c: Color) = drawRect(c, Offset(x * u, y * u), Size(w * u, h * u))
            cell(0, 0, 17, 12, Pal.Outline)
            if (language == Language.PL) {
                cell(1, 1, 15, 5, Pal.White)
                cell(1, 6, 15, 5, Pal.Red)
                cell(1, 10, 15, 1, Pal.RedDark)
            } else {
                // Pole 15×10: granat, białe przekątne, biały krzyż z czerwonym środkiem.
                cell(1, 1, 15, 10, Pal.Navy)
                for (y in 0 until 10) {
                    val x = y * 13 / 9
                    cell(1 + x, 1 + y, 2, 1, Pal.White)
                    cell(1 + 13 - x, 1 + y, 2, 1, Pal.White)
                    cell(1 + x + (if (y < 5) 1 else 0), 1 + y, 1, 1, Pal.Red)
                    cell(1 + 13 - x + (if (y < 5) 0 else 1), 1 + y, 1, 1, Pal.Red)
                }
                cell(1, 4, 15, 4, Pal.White)
                cell(7, 1, 3, 10, Pal.White)
                cell(1, 5, 15, 2, Pal.Red)
                cell(8, 1, 1, 10, Pal.Red)
            }
        },
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    DarkCard(Modifier.fillMaxWidth().widthIn(max = px(320))) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PixelText(label, size = 3, modifier = Modifier.weight(1f))
            PixelButton(
                stringResource(if (value) R.string.on else R.string.off),
                onClick = { onChange(!value) },
                style = if (value) ButtonStyle.GREEN else ButtonStyle.GREY,
                size = 2,
            )
        }
    }
}

@Composable
fun DailyRewardDialog(onClose: () -> Unit) {
    val services = LocalServices.current
    val repo = services.repo
    repo.revision.intValue
    val state = repo.progress.dailyState()
    Overlay(onDismiss = onClose) {
        PixelText(stringResource(R.string.daily_title), size = 4)
        VSpace(6)
        for (rowStart in listOf(0, 4)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(3))) {
                for (i in rowStart until minOf(rowStart + 4, DailyRewards.coins.size)) {
                    val day = i + 1
                    val today = state.available && day == state.day
                    val done = if (state.available) day < state.day else day <= state.day
                    DarkCard(Modifier.weight(1f), highlight = today) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            PixelText(stringResource(R.string.daily_day, day), size = 1, color = if (today) Pal.Gold else Pal.Foam)
                            SpriteImage(if (done) "fishscore_grey" else "fishscore_gold", 2)
                            PixelText(DailyRewards.coins[i].toString(), size = 1)
                            if (day == DailyRewards.BONUS_DAY) PixelText("+4", size = 1, color = Pal.GreenLight)
                        }
                    }
                }
            }
            VSpace(3)
        }
        PixelText(stringResource(R.string.daily_bonus), size = 1, color = Pal.WoodDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (state.clockTampered) {
            VSpace(4)
            PixelText(stringResource(R.string.daily_clock), size = 1, color = Pal.Red, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        VSpace(8)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(4))) {
            PixelButton(stringResource(R.string.close), onClick = onClose, style = ButtonStyle.WOOD, modifier = Modifier.weight(1f))
            PixelButton(
                stringResource(R.string.daily_claim),
                onClick = {
                    if (repo.progress.claimDaily() > 0) {
                        services.audio.play(Sfx.COIN)
                        repo.changed()
                        onClose()
                    }
                },
                style = ButtonStyle.GREEN,
                enabled = state.available,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
