package wojtoteka.ovh.fishingparty.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.data.StreakRepairs
import wojtoteka.ovh.fishingparty.game.Sfx
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Odzyskanie przerwanej passy za monety. Pokazuje drabinkę cen pięciu napraw
 * w bieżącym miesiącu passy: zużyte, te, które zostaną teraz użyte, i pozostałe.
 */
@Composable
fun StreakRepairDialog(onClose: () -> Unit) {
    val services = LocalServices.current
    val repo = services.repo
    repo.revision.intValue
    val progress = repo.progress
    val offer = progress.streakRepair()
    if (offer == null) {
        // Oferta wygasła (np. minęła północ) – nie ma już czego pokazywać.
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val affordable = progress.coins >= offer.cost
    Overlay(onDismiss = onClose) {
        PixelText(stringResource(R.string.repair_title), size = 4)
        VSpace(2)
        PixelText(pluralStringResource(R.plurals.streak_days, offer.days, offer.days), size = 5, color = Pal.Gold)
        PixelText(
            pluralStringResource(R.plurals.repair_missed, offer.missedDays, offer.missedDays),
            size = 2,
            color = Pal.RedDark,
            outline = null,
        )
        VSpace(3)
        PixelText(
            stringResource(R.string.repair_info),
            size = 1,
            color = Pal.WoodDark,
            outline = null,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        VSpace(6)
        PixelText(stringResource(R.string.repair_month), size = 2, color = Pal.Outline, outline = null)
        VSpace(3)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(2))) {
            for (i in 0 until StreakRepairs.PER_MONTH) {
                RepairSlot(
                    index = i,
                    used = i < offer.used,
                    now = i >= offer.used && i < offer.used + offer.missedDays,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        VSpace(3)
        PixelText(
            stringResource(R.string.repair_reset, LocalDate.ofEpochDay(offer.resetDay).format(DATE)),
            size = 1,
            color = Pal.Outline,
            outline = null,
        )
        VSpace(2)
        PixelText(
            stringResource(R.string.repair_rules),
            size = 1,
            color = Pal.WoodDark,
            outline = null,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        VSpace(8)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(px(4))) {
            PixelButton(
                stringResource(R.string.repair_later),
                onClick = onClose,
                style = ButtonStyle.WOOD,
                size = 2,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            PixelButton(
                text = "",
                onClick = {
                    if (progress.repairStreak()) {
                        services.audio.play(Sfx.COIN)
                        repo.changed()
                        onClose()
                    }
                },
                style = ButtonStyle.GREEN,
                enabled = affordable,
                modifier = Modifier.weight(1.4f).fillMaxHeight(),
            ) {
                // Napis i cena w jednej linii, mniejszą czcionką – przycisk jest cienki, a nic się nie łamie nawet na wąskim telefonie.
                PixelText(stringResource(R.string.streak_repair), size = 2, maxLines = 1)
                HSpace(3)
                SpriteImage("fishscore_gold", 1)
                HSpace(1)
                PixelText(offer.cost.toString(), size = 2, maxLines = 1)
            }
        }
        if (!affordable) {
            VSpace(3)
            Plate(pad = 1) {
                PixelText(stringResource(R.string.shop_owned, progress.coins), size = 1)
                HSpace(2)
                SpriteImage("fishscore_gold", 1)
            }
        }
    }
}

private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** Jedna z pięciu napraw w miesiącu: mnożnik i cena. */
@Composable
private fun RepairSlot(index: Int, used: Boolean, now: Boolean, modifier: Modifier) {
    val u = LocalUnit.current.toFloat()
    val style = when {
        used -> ButtonStyle.GREY
        now -> ButtonStyle.GREEN
        else -> ButtonStyle.SEA
    }
    Column(
        modifier
            .drawBehind { pixelBox(u, style.base, style.light, style.dark, shadow = 1f) }
            .padding(top = px(2), bottom = px(3)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PixelText(StreakRepairs.multiplierText(index), size = 1, color = if (now) Pal.White else Pal.Foam)
        SpriteImage(if (used) "fishscore_grey" else "fishscore_gold", 1)
        PixelText(StreakRepairs.price(index).toString(), size = 1, color = if (used) Pal.GreyLight else Pal.White)
    }
}
