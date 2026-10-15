package wojtoteka.ovh.fishingparty.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.data.PowerUp
import wojtoteka.ovh.fishingparty.level.LevelGenerator
import wojtoteka.ovh.fishingparty.level.LevelType

/** Karta poziomu przed startem: typ, cele na gwiazdki i wybór wzmocnień. */
@Composable
fun LevelIntro(level: Int, onPlay: (Set<PowerUp>) -> Unit, onClose: () -> Unit) {
    val repo = LocalServices.current.repo
    repo.revision.intValue
    val progress = repo.progress
    val plan = remember(level) { LevelGenerator.plan(level) }
    val spec = plan.spec
    val worldName = rememberWorldNames()
    val selected = remember(level) { mutableStateListOf<PowerUp>() }
    val earned = progress.stars(level)
    val finale = spec.type == LevelType.FINALE

    Overlay(onDismiss = onClose) {
        Box(Modifier.fillMaxWidth()) {
            PixelButton("X", onClick = onClose, style = ButtonStyle.RED, size = 3, modifier = Modifier.align(Alignment.TopEnd))
            Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                // Cztero- i pięciocyfrowe numery w pełnym rozmiarze wchodziłyby na przycisk zamknięcia.
                PixelText(stringResource(R.string.level_n, level), size = if (level >= 1000) 4 else 5, color = if (finale) Pal.Gold else Pal.White)
                if (finale) {
                    PixelText(stringResource(R.string.finale_subtitle), size = 1, color = Pal.Gold)
                } else {
                    PixelText(worldName(spec.world), size = 1, color = Pal.Foam)
                }
            }
        }
        VSpace(6)
        DarkCard(Modifier.fillMaxWidth(), highlight = finale) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                PixelText(typeName(spec.type), size = 3, color = Pal.Gold)
                VSpace(2)
                PixelText(typeDescription(spec.type), size = 1, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                VSpace(2)
                PixelText(
                    stringResource(R.string.intro_stages, plan.stages.size),
                    size = 1,
                    color = Pal.SeaLight,
                )
            }
        }
        VSpace(6)
        for ((i, goal) in goalTexts(spec).withIndex()) {
            Row(Modifier.fillMaxWidth().padding(vertical = px(1)), verticalAlignment = Alignment.CenterVertically) {
                Plate(pad = 1) { SpriteImage(if (i < earned) "fishscore_gold" else "fishscore_grey", 2) }
                HSpace(4)
                PixelText(goal, size = 2, color = Pal.Outline, outline = null)
            }
        }
        if (progress.best(level) > 0) {
            VSpace(3)
            PixelText(stringResource(R.string.intro_best, progress.best(level)), size = 2, color = Pal.WoodDark, outline = null)
        }
        VSpace(6)
        PowerUpPicker(selected)
        VSpace(8)
        PixelButton(
            stringResource(R.string.play),
            onClick = { onPlay(selected.toSet()) },
            style = ButtonStyle.GREEN,
            size = 5,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Wybór wzmocnień na kartę poziomu. Wzmocnień jest więcej, niż mieści się w oknie,
 * więc rząd przewija się w bok (okno zostaje tej samej wielkości).
 */
@Composable
fun PowerUpPicker(selected: SnapshotStateList<PowerUp>) {
    val progress = LocalServices.current.repo.progress
    PixelText(stringResource(R.string.intro_powerups), size = 2, color = Pal.Outline, outline = null)
    VSpace(3)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(px(3)),
    ) {
        for (p in PowerUp.entries) {
            val count = progress.powerups(p)
            val on = p in selected
            PixelButton(
                text = "",
                onClick = { if (on) selected.remove(p) else if (count > 0) selected.add(p) },
                style = if (on) ButtonStyle.GREEN else ButtonStyle.SEA,
                enabled = count > 0 || on,
                modifier = Modifier.width(px(POWERUP_TILE)),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.height(px(18)), contentAlignment = Alignment.Center) { SpriteImage(powerUpIcon(p), powerUpIconScale(p, 2)) }
                    PixelText("x$count", size = 1)
                }
            }
        }
    }
    VSpace(2)
    val hint = selected.lastOrNull()
    PixelText(
        if (hint != null) powerUpName(hint) + ": " + powerUpDescription(hint) else stringResource(R.string.intro_powerups_hint),
        size = 1,
        color = Pal.WoodDark,
        outline = null,
        align = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Szerokość kafelka wzmocnienia – pięć mieści się w oknie, kolejne są po przewinięciu w prawo. */
private const val POWERUP_TILE = 52
