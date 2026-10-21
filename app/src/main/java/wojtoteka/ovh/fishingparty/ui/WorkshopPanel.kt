package wojtoteka.ovh.fishingparty.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.data.ItemKind
import wojtoteka.ovh.fishingparty.data.ShopItem
import wojtoteka.ovh.fishingparty.data.Workshop
import wojtoteka.ovh.fishingparty.game.Sfx
import wojtoteka.ovh.fishingparty.gfx.ColorMath
import wojtoteka.ovh.fishingparty.gfx.Hsv
import wojtoteka.ovh.fishingparty.gfx.SailDesign
import wojtoteka.ovh.fishingparty.gfx.SailPattern
import kotlin.math.floor

/*
 * Kreator na dole kart ŁODZIE i ŻAGLE: zwinięty to jeden przycisk („Chcesz własny kolor?”),
 * rozwinięty – pełna paleta (pole nasycenia i jasności + pasek barw), cena grupy wybranego
 * koloru, cennik wszystkich grup i przycisk zakupu. Podgląd projektu pokazuje duża łódź
 * na górze sklepu.
 */

/** Projekt żagla w kreatorze: wzór, oba kolory (jak na palecie) i który z nich teraz zmieniamy. */
data class SailDraft(
    val pattern: SailPattern = Workshop.DEFAULT_SAIL_PATTERN,
    val first: Hsv = Workshop.DEFAULT_SAIL_FIRST,
    val second: Hsv = Workshop.DEFAULT_SAIL_SECOND,
    val slot: Int = 1,
) {
    val design: SailDesign get() = SailDesign(pattern, first.color, second.color)
    val editing: Hsv get() = if (slot == 0) first else second

    fun withEditing(hsv: Hsv) = if (slot == 0) copy(first = hsv) else copy(second = hsv)
}

/** Własny kolor kadłuba. */
@Composable
fun BoatWorkshop(
    open: Boolean,
    onToggle: () -> Unit,
    pick: Hsv,
    onPick: (Hsv) -> Unit,
    onBuy: (ShopItem) -> Unit,
    onEquip: (ShopItem) -> Unit,
) {
    WorkshopFrame(stringResource(R.string.workshop_boat_open), stringResource(R.string.workshop_boat_title), open, onToggle) {
        PixelText(stringResource(R.string.workshop_pick_color), size = 1, color = Pal.Foam, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        VSpace(4)
        ColorPicker(pick, onPick)
        VSpace(4)
        val color = pick.color
        ColorReadout(color, Workshop.groupOf(color).boatPrice)
        VSpace(4)
        PriceTable(Workshop.groupOf(color), { it.boatPrice }) { onPick(sampleOf(it, pick)) }
        VSpace(4)
        WorkshopBuy(Workshop.boatItem(color), breakdown = null, quote = Workshop.boatQuote(color), onBuy = onBuy, onEquip = onEquip)
    }
}

/** Dwukolorowy żagiel: wzór i dwa kolory z palety. */
@Composable
fun SailWorkshop(
    open: Boolean,
    onToggle: () -> Unit,
    draft: SailDraft,
    onDraft: (SailDraft) -> Unit,
    onBuy: (ShopItem) -> Unit,
    onEquip: (ShopItem) -> Unit,
) {
    WorkshopFrame(stringResource(R.string.workshop_sail_open), stringResource(R.string.workshop_sail_title), open, onToggle) {
        PixelText(stringResource(R.string.workshop_pattern), size = 1, color = Pal.Foam)
        VSpace(2)
        for (line in SailPattern.entries.chunked(5)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(3))) {
                for (pattern in line) {
                    PatternChip(draft.design.copy(pattern = pattern), selected = pattern == draft.pattern, modifier = Modifier.weight(1f)) {
                        onDraft(draft.copy(pattern = pattern))
                    }
                }
            }
            VSpace(3)
        }
        VSpace(2)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(px(3))) {
            for (i in 0..1) {
                PixelButton(
                    text = "",
                    onClick = { onDraft(draft.copy(slot = i)) },
                    style = if (draft.slot == i) ButtonStyle.GREEN else ButtonStyle.SEA,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    ColorDot((if (i == 0) draft.first else draft.second).color, 8)
                    HSpace(3)
                    PixelText(stringResource(R.string.workshop_color_n, i + 1), size = 2)
                }
            }
            PixelButton(
                stringResource(R.string.workshop_swap),
                onClick = { onDraft(draft.copy(first = draft.second, second = draft.first)) },
                style = ButtonStyle.WOOD,
                size = 2,
            )
        }
        VSpace(4)
        ColorPicker(draft.editing) { onDraft(draft.withEditing(it)) }
        VSpace(4)
        val color = draft.editing.color
        ColorReadout(color, Workshop.groupOf(color).sailPrice)
        VSpace(4)
        PriceTable(Workshop.groupOf(color), { it.sailPrice }) { onDraft(draft.withEditing(sampleOf(it, draft.editing))) }
        VSpace(4)
        val design = draft.design
        WorkshopBuy(
            item = Workshop.sailItem(design),
            breakdown = stringResource(
                R.string.workshop_sail_price,
                Workshop.patternPrice(design.pattern),
                Workshop.groupOf(design.first).sailPrice,
                Workshop.groupOf(design.second).sailPrice,
            ),
            quote = Workshop.sailQuote(design),
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
    // Z klawiatury fokus idzie za przyciskiem: po rozwinięciu na „X”, po zwinięciu z powrotem na „Chcesz…”.
    val toggleFocus = remember { FocusRequester() }
    var toggled by remember { mutableStateOf(false) }
    val input = LocalInputModeManager.current
    LaunchedEffect(open) {
        if (toggled && input.inputMode == InputMode.Keyboard) runCatching { toggleFocus.requestFocus() }
    }
    val toggle = {
        toggled = true
        onToggle()
    }
    if (!open) {
        PixelButton(openLabel, onClick = toggle, style = ButtonStyle.ORANGE, size = 3, modifier = Modifier.focusRequester(toggleFocus).fillMaxWidth())
        return
    }
    DarkCard(Modifier.fillMaxWidth(), highlight = true) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth()) {
                PixelText(title, size = 3, color = Pal.Gold, modifier = Modifier.align(Alignment.CenterStart))
                PixelButton("X", onClick = toggle, style = ButtonStyle.RED, size = 2, modifier = Modifier.align(Alignment.CenterEnd).focusRequester(toggleFocus))
            }
            VSpace(4)
            content()
        }
    }
}

/**
 * Pełna paleta: pole nasycenie (w prawo) × jasność (w górę) dla wybranej barwy i pasek wszystkich barw pod nim.
 * Dotyk i przeciąganie palcem od razu zmieniają kolor (i nie przewijają przy tym listy sklepu).
 * Pod paskiem można też wpisać kolor liczbami RGB.
 */
@Composable
private fun ColorPicker(pick: Hsv, onPick: (Hsv) -> Unit) {
    val u = LocalUnit.current.toFloat()
    val current by rememberUpdatedState(pick)
    val update by rememberUpdatedState(onPick)
    val pure = Color(ColorMath.fromHsv(pick.h, 1f, 1f))
    Box(
        Modifier
            .fillMaxWidth()
            .height(px(PICKER_H))
            .drawBehind {
                drawRect(Pal.Outline)
                val inner = Size(size.width - 2 * u, size.height - 2 * u)
                drawRect(Brush.horizontalGradient(listOf(Color.White, pure), startX = u, endX = size.width - u), Offset(u, u), inner)
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = u, endY = size.height - u), Offset(u, u), inner)
                marker(Offset(u + pick.s * inner.width, u + (1 - pick.v) * inner.height), u)
            }
            // Klawiatura: w lewo/prawo nasycenie, w górę/dół jasność.
            .keyAdjust { key ->
                val p = current
                val next = when (key) {
                    Key.DirectionLeft -> p.copy(s = (p.s - KEY_STEP).coerceAtLeast(0f))
                    Key.DirectionRight -> p.copy(s = (p.s + KEY_STEP).coerceAtMost(1f))
                    Key.DirectionUp -> p.copy(v = (p.v + KEY_STEP).coerceAtMost(1f))
                    Key.DirectionDown -> p.copy(v = (p.v - KEY_STEP).coerceAtLeast(0f))
                    else -> return@keyAdjust false
                }
                if (next == p) return@keyAdjust false
                update(next)
                true
            }
            .pointerInput(Unit) {
                track { pos ->
                    val s = ((pos.x - u) / (size.width - 2 * u)).coerceIn(0f, 1f)
                    val v = 1f - ((pos.y - u) / (size.height - 2 * u)).coerceIn(0f, 1f)
                    update(current.copy(s = s, v = v))
                }
            },
    )
    VSpace(3)
    Box(
        Modifier
            .fillMaxWidth()
            .height(px(HUE_H))
            .drawBehind {
                drawRect(Pal.Outline)
                val hues = List(7) { Color(ColorMath.fromHsv(it / 6f, 1f, 1f)) }
                drawRect(Brush.horizontalGradient(hues, startX = u, endX = size.width - u), Offset(u, u), Size(size.width - 2 * u, size.height - 2 * u))
                val x = u + pick.h * (size.width - 2 * u)
                drawRect(Pal.Outline, Offset(x - 2 * u, 0f), Size(4 * u, size.height))
                drawRect(Color.White, Offset(x - u, u), Size(2 * u, size.height - 2 * u))
            }
            .keyAdjust { key ->
                val h = current.h
                val next = when (key) {
                    Key.DirectionLeft -> (h - HUE_KEY_STEP).coerceAtLeast(0f)
                    Key.DirectionRight -> (h + HUE_KEY_STEP).coerceAtMost(0.999f)
                    else -> return@keyAdjust false
                }
                if (next == h) return@keyAdjust false
                update(current.copy(h = next))
                true
            }
            .pointerInput(Unit) {
                track { pos ->
                    // Barwa 1.0 to znów czerwień – zostajemy tuż przed nią, żeby znacznik nie skakał na początek paska.
                    val h = ((pos.x - u) / (size.width - 2 * u)).coerceIn(0f, 0.999f)
                    update(current.copy(h = h))
                }
            },
    )
    VSpace(3)
    RgbInput(pick, onPick)
}

/**
 * Kolor wpisany liczbami: trzy pola R, G, B (0–255). Każda poprawna liczba od razu zmienia kolor,
 * a grupę i cenę gra dobiera sama, tak jak przy wyborze palcem. Puste pole czeka na wpisanie liczby.
 */
@Composable
private fun RgbInput(pick: Hsv, onPick: (Hsv) -> Unit) {
    val color = pick.color
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(3))) {
        for (channel in 0..2) {
            val value = ColorMath.channel(color, channel)
            // Tekst pola nadąża za kolorem z palety, ale nie przeszkadza w pisaniu (np. pustemu polu).
            var text by remember(value) { mutableStateOf(value.toString()) }
            PixelTextField(
                value = text,
                onValueChange = { typed ->
                    val digits = typed.filter { it.isDigit() }
                    text = digits
                    val number = digits.toIntOrNull() ?: return@PixelTextField
                    val clamped = number.coerceAtMost(255)
                    if (clamped != number) text = clamped.toString()
                    if (clamped != value) onPick(ColorMath.withChannel(pick, channel, clamped))
                },
                label = RGB_LABELS[channel],
                keyboard = KeyboardType.Number,
                maxLength = 3,
                imeAction = if (channel < 2) ImeAction.Next else ImeAction.Done,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private val RGB_LABELS = listOf("R", "G", "B")

/** Typowy kolor grupy z cennika; biel, szarość i czerń nie mają barwy, więc pasek barw zostaje tam, gdzie był. */
private fun sampleOf(group: Workshop.ColorGroup, current: Hsv): Hsv =
    if (group.neutral != null) group.sample.copy(h = current.h) else group.sample

/** Kwadratowy celownik: biała ramka w ciemnej obwódce, widoczny na każdym kolorze. */
private fun DrawScope.marker(center: Offset, u: Float) {
    val r = 4 * u
    drawRect(Pal.Outline, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r))
    drawRect(Color.White, Offset(center.x - r + u, center.y - r + u), Size(2 * r - 2 * u, 2 * r - 2 * u))
    drawRect(Pal.Outline, Offset(center.x - r + 2 * u, center.y - r + 2 * u), Size(2 * r - 4 * u, 2 * r - 4 * u))
}

/** Śledzi palec od dotknięcia do puszczenia; zjada ruch, żeby lista sklepu się nie przewijała. */
private suspend fun PointerInputScope.track(onPosition: (Offset) -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        down.consume()
        onPosition(down.position)
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            change.consume()
            onPosition(change.position)
        }
    }
}

/** Wybrany kolor: próbka, wartości RGB, grupa i jej cena. */
@Composable
private fun ColorReadout(color: Int, price: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        ColorDot(color, 18)
        HSpace(5)
        Column {
            PixelText(stringResource(R.string.workshop_rgb, ColorMath.red(color), ColorMath.green(color), ColorMath.blue(color)), size = 2)
            PixelText(stringResource(R.string.workshop_group, groupName(Workshop.groupOf(color)), price), size = 1, color = Pal.Gold)
        }
    }
}

/** Cennik: każda grupa kolorów z ceną; dotknięcie ustawia jej typowy kolor, bieżąca ma złotą ramkę. */
@Composable
private fun PriceTable(current: Workshop.ColorGroup, priceOf: (Workshop.ColorGroup) -> Int, onGroup: (Workshop.ColorGroup) -> Unit) {
    val u = LocalUnit.current.toFloat()
    val audio = LocalServices.current.audio
    PixelText(stringResource(R.string.workshop_prices), size = 1, color = Pal.Foam)
    VSpace(2)
    val rows = Workshop.groups.filter { it.tone != null }.chunked(Workshop.Hue.entries.size) + listOf(Workshop.groups.filter { it.neutral != null })
    for (row in rows) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(1))) {
            for (group in row) {
                val sample = Color(group.sample.color)
                Box(
                    Modifier
                        .weight(1f)
                        .height(px(15))
                        .drawBehind {
                            drawRect(if (group === current) Pal.Gold else Pal.Outline)
                            val b = if (group === current) 2 * u else u
                            drawRect(sample, Offset(b, b), Size(size.width - 2 * b, size.height - 2 * b))
                        }
                        .noRippleClick {
                            audio.play(Sfx.CLICK)
                            onGroup(group)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    PixelText(priceOf(group).toString(), size = 1)
                }
            }
        }
        VSpace(1)
    }
}

@Composable
private fun groupName(group: Workshop.ColorGroup): String {
    group.neutral?.let {
        return stringResource(
            when (it) {
                Workshop.Neutral.WHITE -> R.string.color_white
                Workshop.Neutral.SILVER -> R.string.color_silver
                Workshop.Neutral.GREY -> R.string.color_grey
                Workshop.Neutral.GRAPHITE -> R.string.color_graphite
                Workshop.Neutral.BLACK -> R.string.color_black
            },
        )
    }
    val hue = stringResource(
        when (group.hue!!) {
            Workshop.Hue.RED -> R.string.color_reds
            Workshop.Hue.ORANGE -> R.string.color_oranges
            Workshop.Hue.YELLOW -> R.string.color_yellows
            Workshop.Hue.GREEN -> R.string.color_greens
            Workshop.Hue.TEAL -> R.string.color_teals
            Workshop.Hue.BLUE -> R.string.color_blues
            Workshop.Hue.PURPLE -> R.string.color_purples
            Workshop.Hue.PINK -> R.string.color_pinks
        },
    )
    val tone = stringResource(
        when (group.tone!!) {
            Workshop.Tone.PASTEL -> R.string.color_pastel
            Workshop.Tone.VIVID -> R.string.color_vivid
            Workshop.Tone.DARK -> R.string.color_dark
        },
    )
    return stringResource(R.string.color_group_name, hue, tone)
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

internal fun DrawScope.drawGrid(grid: Array<IntArray>, topLeft: Offset, area: Size) {
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
private fun ColorDot(color: Int, units: Int) {
    val u = LocalUnit.current.toFloat()
    Box(
        Modifier.size(px(units)).drawBehind {
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
        item == null -> PixelButton(stringResource(R.string.workshop_same_colors), onClick = {}, enabled = false, size = 2, modifier = Modifier.fillMaxWidth())
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

/** Wysokość pola palety i paska barw w pikselach interfejsu. */
private const val PICKER_H = 90
private const val HUE_H = 14

/** Krok strzałki w kreatorze: nasycenie/jasność o 1/20, barwa o 1/36 koła. */
private const val KEY_STEP = 0.05f
private const val HUE_KEY_STEP = 1f / 36f
