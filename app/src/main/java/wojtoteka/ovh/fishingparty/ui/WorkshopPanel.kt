package wojtoteka.ovh.fishingparty.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.data.ItemKind
import wojtoteka.ovh.fishingparty.data.ShopItem
import wojtoteka.ovh.fishingparty.data.Workshop
import wojtoteka.ovh.fishingparty.game.Sfx
import wojtoteka.ovh.fishingparty.gfx.SailDesign
import wojtoteka.ovh.fishingparty.gfx.SailPattern
import kotlin.math.floor

/*
 * Kreator na dole kart ŁODZIE i ŻAGLE: zwinięty to jeden przycisk („Chcesz własny kolor?”),
 * rozwinięty – paleta odcieni (każdy ze swoją ceną) i przycisk zakupu. Podgląd projektu
 * pokazuje duża łódź na górze sklepu.
 */

/** Własny kolor kadłuba. */
@Composable
fun BoatWorkshop(
    open: Boolean,
    onToggle: () -> Unit,
    color: Int,
    onColor: (Int) -> Unit,
    onBuy: (ShopItem) -> Unit,
    onEquip: (ShopItem) -> Unit,
) {
    WorkshopFrame(stringResource(R.string.workshop_boat_open), stringResource(R.string.workshop_boat_title), open, onToggle) {
        PixelText(stringResource(R.string.workshop_pick_shade), size = 1, color = Pal.Foam, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        VSpace(3)
        ShadePalette(selected = color, other = null, onPick = onColor)
        VSpace(4)
        val quote = Workshop.boatQuote(color)
        WorkshopBuy(
            item = Workshop.boatItem(color),
            breakdown = quote?.let { stringResource(R.string.workshop_boat_price, it.base) },
            quote = quote,
            problem = null,
            onBuy = onBuy,
            onEquip = onEquip,
        )
    }
}

/** Dwukolorowy żagiel: wzór, dwa kolory z palety. */
@Composable
fun SailWorkshop(
    open: Boolean,
    onToggle: () -> Unit,
    design: SailDesign,
    onDesign: (SailDesign) -> Unit,
    onBuy: (ShopItem) -> Unit,
    onEquip: (ShopItem) -> Unit,
) {
    // Który kolor zmienia paleta: 0 – pierwszy (tło), 1 – drugi (wzór).
    var slot by remember { mutableIntStateOf(1) }
    WorkshopFrame(stringResource(R.string.workshop_sail_open), stringResource(R.string.workshop_sail_title), open, onToggle) {
        PixelText(stringResource(R.string.workshop_pattern), size = 1, color = Pal.Foam)
        VSpace(2)
        for (line in SailPattern.entries.chunked(5)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(3))) {
                for (pattern in line) {
                    PatternChip(design.copy(pattern = pattern), selected = pattern == design.pattern, modifier = Modifier.weight(1f)) {
                        onDesign(design.copy(pattern = pattern))
                    }
                }
            }
            VSpace(3)
        }
        VSpace(2)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(3))) {
            for (i in 0..1) {
                PixelButton(
                    text = "",
                    onClick = { slot = i },
                    style = if (slot == i) ButtonStyle.GREEN else ButtonStyle.SEA,
                    modifier = Modifier.weight(1f),
                ) {
                    ColorDot(if (i == 0) design.first else design.second)
                    HSpace(3)
                    PixelText(stringResource(R.string.workshop_color_n, i + 1), size = 2)
                }
            }
            PixelButton(
                stringResource(R.string.workshop_swap),
                onClick = { onDesign(design.copy(first = design.second, second = design.first)) },
                style = ButtonStyle.WOOD,
                size = 2,
            )
        }
        VSpace(4)
        ShadePalette(
            selected = if (slot == 0) design.first else design.second,
            other = if (slot == 0) design.second else design.first,
            onPick = { c -> onDesign(if (slot == 0) design.copy(first = c) else design.copy(second = c)) },
        )
        VSpace(4)
        val quote = Workshop.sailQuote(design)
        val a = Workshop.shade(design.first)
        val b = Workshop.shade(design.second)
        WorkshopBuy(
            item = Workshop.sailItem(design),
            breakdown = if (a != null && b != null) {
                stringResource(R.string.workshop_sail_price, Workshop.patternPrice(design.pattern), a.sailPrice, b.sailPrice)
            } else {
                null
            },
            quote = quote,
            problem = stringResource(R.string.workshop_same_colors),
            onBuy = onBuy,
            onEquip = onEquip,
        )
    }
}

@Composable
private fun WorkshopFrame(
    openLabel: String,
    title: String,
    open: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!open) {
        PixelButton(openLabel, onClick = onToggle, style = ButtonStyle.ORANGE, size = 3, modifier = Modifier.fillMaxWidth())
        return
    }
    DarkCard(Modifier.fillMaxWidth(), highlight = true) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth()) {
                PixelText(title, size = 3, color = Pal.Gold, modifier = Modifier.align(Alignment.CenterStart))
                PixelButton("X", onClick = onToggle, style = ButtonStyle.RED, size = 2, modifier = Modifier.align(Alignment.CenterEnd))
            }
            VSpace(4)
            content()
        }
    }
}

/** Siatka odcieni; [selected] ma złotą ramkę, [other] (drugi kolor żagla) – białą. */
@Composable
private fun ShadePalette(selected: Int?, other: Int?, onPick: (Int) -> Unit) {
    val u = LocalUnit.current.toFloat()
    val audio = LocalServices.current.audio
    val pick by rememberUpdatedState(onPick)
    val palette = Workshop.palette
    val rows = palette.size / Workshop.COLUMNS
    Box(
        Modifier
            .fillMaxWidth()
            .height(px(rows * SHADE_H))
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val col = (pos.x / (size.width / Workshop.COLUMNS.toFloat())).toInt().coerceIn(0, Workshop.COLUMNS - 1)
                    val row = (pos.y / (size.height / rows.toFloat())).toInt().coerceIn(0, rows - 1)
                    audio.play(Sfx.CLICK)
                    pick(palette[row * Workshop.COLUMNS + col].color)
                }
            }
            .drawBehind {
                // Całe piksele ekranu na pole – ostre krawędzie jak w reszcie gry.
                val cw = floor(size.width / Workshop.COLUMNS)
                val ch = floor(size.height / rows)
                val left = floor((size.width - cw * Workshop.COLUMNS) / 2f)
                for ((i, shade) in palette.withIndex()) {
                    val x = left + (i % Workshop.COLUMNS) * cw
                    val y = (i / Workshop.COLUMNS) * ch
                    val frame = when (shade.color) {
                        selected -> Pal.Gold
                        other -> Pal.White
                        else -> Pal.Outline
                    }
                    val border = if (frame == Pal.Outline) u else 2 * u
                    drawRect(frame, Offset(x, y), Size(cw, ch))
                    drawRect(Color(shade.color), Offset(x + border, y + border), Size(cw - 2 * border, ch - 2 * border))
                }
            },
    )
}

/** Miniatura wzoru w wybranych kolorach. */
@Composable
private fun PatternChip(design: SailDesign, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val u = LocalUnit.current.toFloat()
    val audio = LocalServices.current.audio
    Box(
        modifier
            // Proporcje jak żagiel na łodzi (szerszy niż wyższy).
            .aspectRatio(1.5f)
            .noRippleClick {
                audio.play(Sfx.CLICK)
                onClick()
            }
            .drawBehind {
                drawRect(if (selected) Pal.Gold else Pal.Outline)
                val border = if (selected) 2 * u else u
                drawGrid(design.grid(), Offset(border, border), Size(size.width - 2 * border, size.height - 2 * border))
            },
    )
}

private fun DrawScope.drawGrid(grid: Array<IntArray>, topLeft: Offset, area: Size) {
    val rows = grid.size
    val cols = grid[0].size
    for (r in 0 until rows) {
        // Brzegi pól w całych pikselach, żeby sąsiednie pola się stykały bez szczelin.
        val y0 = topLeft.y + floor(area.height * r / rows)
        val y1 = topLeft.y + floor(area.height * (r + 1) / rows)
        for (c in 0 until cols) {
            val x0 = topLeft.x + floor(area.width * c / cols)
            val x1 = topLeft.x + floor(area.width * (c + 1) / cols)
            drawRect(Color(grid[r][c]), Offset(x0, y0), Size(x1 - x0, y1 - y0))
        }
    }
}

@Composable
private fun ColorDot(color: Int) {
    val u = LocalUnit.current.toFloat()
    Box(
        Modifier.size(px(8)).drawBehind {
            drawRect(Pal.Outline)
            drawRect(Color(color), Offset(u, u), Size(size.width - 2 * u, size.height - 2 * u))
        },
    )
}

/** Cena projektu i przycisk: kup / wybierz / w użyciu. */
@Composable
private fun WorkshopBuy(
    item: ShopItem?,
    breakdown: String?,
    quote: Workshop.Quote?,
    problem: String?,
    onBuy: (ShopItem) -> Unit,
    onEquip: (ShopItem) -> Unit,
) {
    val repo = LocalServices.current.repo
    repo.revision.intValue
    val progress = repo.progress
    if (breakdown != null) PixelText(breakdown, size = 1, color = Pal.SeaLight, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    val similar = quote?.similar
    if (quote != null && similar != null && quote.raised) {
        PixelText(
            stringResource(
                if (similar.kind == ItemKind.BOAT) R.string.workshop_similar_boat else R.string.workshop_similar_sail,
                itemName(similar),
                similar.price,
            ),
            size = 1,
            color = Pal.SandLight,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    VSpace(3)
    when {
        item == null -> PixelButton(problem ?: "", onClick = {}, enabled = false, size = 2, modifier = Modifier.fillMaxWidth())
        progress.isEquipped(item) -> PixelButton(stringResource(R.string.shop_equipped), onClick = {}, enabled = false, size = 2, modifier = Modifier.fillMaxWidth())
        progress.owns(item) -> PixelButton(stringResource(R.string.shop_equip), onClick = { onEquip(item) }, style = ButtonStyle.SEA, size = 2, modifier = Modifier.fillMaxWidth())
        else -> PixelButton(
            text = "",
            onClick = { onBuy(item) },
            style = ButtonStyle.GREEN,
            enabled = progress.coins >= item.price,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SpriteImage("fishscore_gold", 1)
            HSpace(2)
            PixelText(item.price.toString(), size = 2)
        }
    }
}

/** Wysokość wiersza palety w pikselach interfejsu. */
private const val SHADE_H = 18
