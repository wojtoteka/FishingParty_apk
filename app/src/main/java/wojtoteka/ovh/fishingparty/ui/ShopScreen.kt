package wojtoteka.ovh.fishingparty.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.data.Catalog
import wojtoteka.ovh.fishingparty.data.ItemKind
import wojtoteka.ovh.fishingparty.data.PowerUp
import wojtoteka.ovh.fishingparty.data.ShopItem
import wojtoteka.ovh.fishingparty.data.Workshop
import wojtoteka.ovh.fishingparty.game.Sfx
import wojtoteka.ovh.fishingparty.gfx.ShipLook
import wojtoteka.ovh.fishingparty.gfx.ShipPainter
import wojtoteka.ovh.fishingparty.gfx.Sprites
import wojtoteka.ovh.fishingparty.level.LevelGenerator
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Jeden pędzel na wszystkie podglądy – wszystko rysuje się w wątku interfejsu, a karta w liście nie tworzy własnych farb. */
private val previewPainter by lazy { ShipPainter() }

/** Kołysząca się łódź narysowana w powiększeniu (podgląd w sklepie). */
@Composable
fun ShipPreview(look: ShipLook, scale: Int, modifier: Modifier = Modifier) {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (isActive) withFrameNanos { now ->
            if (last != 0L) time.floatValue += ((now - last) / 1e9f).coerceAtMost(0.1f)
            last = now
        }
    }
    val u = LocalUnit.current
    Canvas(modifier) {
        val t = time.floatValue
        drawIntoCanvas { drawShip(it.nativeCanvas, look, scale, u, size.width, size.height, sway = true, t) }
    }
}

/** Łódź wyśrodkowana w polu [w]×[h] (piksele ekranu, od bieżącego początku płótna). */
private fun drawShip(canvas: android.graphics.Canvas, look: ShipLook, scale: Int, u: Int, w: Float, h: Float, sway: Boolean, t: Float) {
    // Łódź z masztem i żaglem ma ok. 25×16 pikseli spritu (z kołysaniem więcej) – skala
    // dobrana tak, żeby zawsze mieściła się w polu, w całych pikselach ekranu (ostre krawędzie).
    // Bez kołysania wystarczy mniejszy zapas.
    val fit = if (sway) floor(minOf(h / SHIP_H, w / SHIP_W)) else floor(minOf(h / 26f, w / 18f))
    val k = minOf((scale * u).toFloat(), fit).coerceAtLeast(1f)
    val heading = (-PI / 2).toFloat() + if (sway) sin(t * 0.9f) * 0.5f else 0f
    canvas.save()
    // Punkt łodzi leży niżej niż środek rysunku (maszt i żagiel sięgają ok. 17 px w górę, kadłub 8 w dół).
    canvas.translate(w / 2f, h / 2f + 4.5f * k)
    canvas.scale(k, k)
    previewPainter.draw(canvas, 0f, 0f, heading, true, t, look)
    canvas.restore()
}

/** Wymiary rysunku łodzi (w pikselach spritu) z zapasem na kołysanie. */
private const val SHIP_H = 28f
private const val SHIP_W = 24f

private enum class ShopTab { BOATS, SAILS, SAILORS, POWERUPS, MARKET }

@Composable
fun ShopScreen(onBack: () -> Unit) {
    val services = LocalServices.current
    val repo = services.repo
    repo.revision.intValue
    val progress = repo.progress
    var tab by remember { mutableStateOf(ShopTab.BOATS) }
    var preview by remember { mutableStateOf<ShopItem?>(null) }
    // Kreator na dole kart ŁODZIE i ŻAGLE – stan trzymany tutaj, żeby przetrwał zmianę zakładki.
    var workshopOpen by remember { mutableStateOf(false) }
    var boatPick by remember { mutableStateOf(Workshop.DEFAULT_BOAT) }
    var sailDraft by remember { mutableStateOf(SailDraft()) }
    // Wystawianie na targ – okienko poza przewijaną listą.
    var selling by remember { mutableStateOf<ShopItem?>(null) }
    var sold by remember { mutableIntStateOf(0) }
    val equipped = ShipLook(repo.save.boat, repo.save.sail, repo.save.sailor)
    val look = when (preview?.kind) {
        ItemKind.BOAT -> equipped.copy(boat = preview!!.id)
        ItemKind.SAIL -> equipped.copy(sail = preview!!.id)
        ItemKind.SAILOR -> equipped.copy(sailor = preview!!.id)
        else -> equipped
    }
    val items = when (tab) {
        ShopTab.BOATS -> Catalog.boats
        ShopTab.SAILS -> Catalog.sails - Catalog.flags.toSet()
        ShopTab.SAILORS -> Catalog.sailors
        ShopTab.POWERUPS -> Catalog.powerups
        ShopTab.MARKET -> emptyList()
    }
    // Karta ŻAGLE: zwykłe kolory, pod nimi flagi (z nagłówkiem), potem własne żagle i kreator na samym dole.
    val flags = if (tab == ShopTab.SAILS) Catalog.flags else emptyList()
    val creations = when (tab) {
        ShopTab.BOATS -> progress.creations(ItemKind.BOAT)
        ShopTab.SAILS -> progress.creations(ItemKind.SAIL)
        else -> emptyList()
    }

    Box(Modifier.fillMaxSize()) {
        SeaBackdrop(LevelGenerator.worldOf(progress.highestUnlocked()), Modifier.fillMaxSize(), dim = 0.2f)
        Column(Modifier.fillMaxSize()) {
            TopBar(stringResource(R.string.shop_title), onBack)
            ShipPreview(look, 5, Modifier.fillMaxWidth().height(px(96)).padding(vertical = px(3)))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = px(6)),
                horizontalArrangement = Arrangement.spacedBy(px(3)),
            ) {
                for (t in ShopTab.entries) {
                    PixelButton(
                        stringResource(
                            when (t) {
                                ShopTab.BOATS -> R.string.shop_boats
                                ShopTab.SAILS -> R.string.shop_sails
                                ShopTab.SAILORS -> R.string.shop_sailors
                                ShopTab.POWERUPS -> R.string.shop_powerups
                                ShopTab.MARKET -> R.string.shop_market
                            },
                        ),
                        onClick = {
                            tab = t
                            preview = null
                            workshopOpen = false
                        },
                        style = if (t == tab) ButtonStyle.GREEN else if (t == ShopTab.MARKET) ButtonStyle.ORANGE else ButtonStyle.SEA,
                        size = 2,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            VSpace(4)
            // Zwykła przewijana kolumna zamiast leniwej siatki: karty danej zakładki powstają raz, przy jej
            // otwarciu, a przewijanie tylko przesuwa gotowy obraz (jak mapa). Leniwa siatka przy szybkim
            // przewijaniu budowała i mierzyła w jednej klatce cały nowy rząd kart – stąd zacięcia.
            // Każda zakładka zaczyna od góry.
            val scroll = remember(tab) { ScrollState(0) }
            var workshopTop by remember { mutableIntStateOf(0) }
            val gap = px(4)
            val gapPx = with(LocalDensity.current) { gap.roundToPx() }
            // Po rozwinięciu kreatora lista przewija się do niego (jest na samym dole) – klatkę później,
            // gdy rozwinięty kreator jest już zmierzony i lista ma nową długość.
            LaunchedEffect(workshopOpen) {
                if (workshopOpen) {
                    withFrameNanos { }
                    scroll.animateScrollTo(workshopTop - gapPx)
                }
            }
            val buy = { item: ShopItem ->
                if (progress.buy(item)) {
                    services.audio.play(Sfx.COIN)
                    repo.changed()
                }
            }
            val equip = { item: ShopItem ->
                progress.equip(item)
                preview = null
                repo.changed()
            }
            val card: @Composable (ShopItem, Modifier) -> Unit = { item, modifier ->
                ShopCard(
                    item = item,
                    selected = preview?.key == item.key,
                    onPreview = { if (item.kind != ItemKind.POWERUP) preview = item },
                    onBuy = { buy(item) },
                    onEquip = { equip(item) },
                    modifier = modifier,
                )
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .keyScroll(scroll)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .verticalScroll(scroll)
                    .padding(start = px(6), top = gap, end = px(6), bottom = px(BOTTOM_GAP)),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                // Targ: projekty innych graczy i stoisko gracza (zamiast kart sklepu).
                if (tab == ShopTab.MARKET) MarketPanel(preview?.key, onPreview = { preview = it }, onEquip = equip, onSell = { selling = it }, sold = sold)
                // Własne łodzie i żagle dochodzą na koniec ostatniej sekcji, w tych samych rzędach.
                CardRows(if (flags.isEmpty()) items + creations else items, gap, card)
                if (flags.isNotEmpty()) {
                    PixelText(
                        stringResource(R.string.shop_flags),
                        size = 2,
                        color = Pal.Gold,
                        align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = gap),
                    )
                    CardRows(flags + creations, gap, card)
                }
                if (tab == ShopTab.BOATS || tab == ShopTab.SAILS) {
                    val toggle = {
                        workshopOpen = !workshopOpen
                        preview = when {
                            !workshopOpen -> null
                            tab == ShopTab.BOATS -> Workshop.boatItem(boatPick.color)
                            else -> Workshop.sailItem(sailDraft.design)
                        }
                    }
                    Box(Modifier.onPlaced { workshopTop = it.positionInParent().y.roundToInt() }) {
                        if (tab == ShopTab.BOATS) {
                            BoatWorkshop(
                                open = workshopOpen,
                                onToggle = toggle,
                                pick = boatPick,
                                onPick = {
                                    boatPick = it
                                    preview = Workshop.boatItem(it.color)
                                },
                                onBuy = buy,
                                onEquip = equip,
                            )
                        } else {
                            SailWorkshop(
                                open = workshopOpen,
                                onToggle = toggle,
                                draft = sailDraft,
                                onDraft = {
                                    sailDraft = it
                                    // Dwa prawie takie same kolory to nie projekt – podgląd pokazuje wtedy ostatni poprawny.
                                    Workshop.sailItem(it.design)?.let { item -> preview = item }
                                },
                                onBuy = buy,
                                onEquip = equip,
                            )
                        }
                    }
                }
            }
        }
        selling?.let { item ->
            MarketSellDialog(item, onClose = { selling = null }) {
                selling = null
                sold++
            }
        }
    }
}

/** Karty po dwie w rzędzie (jak dawna siatka – ostatnia, nieparzysta karta zajmuje lewą połowę). */
@Composable
private fun CardRows(list: List<ShopItem>, gap: Dp, card: @Composable (ShopItem, Modifier) -> Unit) {
    for (row in list.chunked(2)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            for (item in row) key(item.key) { card(item, Modifier.weight(1f)) }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

/** Rozmiary, położenie i napisy karty policzone przy mierzeniu – rysowanie i dotyk tylko z nich korzystają. */
private class CardLayout {
    var name: TextLayoutResult? = null
    var desc: TextLayoutResult? = null
    var count: TextLayoutResult? = null
    var label: TextLayoutResult? = null
    var nameAt = Offset.Zero
    var descAt = Offset.Zero
    var countAt = Offset.Zero
    var labelAt = Offset.Zero
    var coinAt = Offset.Zero
    var button = Rect.Zero
    private var measures = 0

    /** Numer pomiaru – rysowanie odświeża się także wtedy, gdy rozmiar karty po zmianie (np. zakupie) jest ten sam. */
    var version by mutableIntStateOf(0)
        private set
    var pressed by mutableStateOf(false)
    var focused by mutableStateOf(false)

    fun measured() {
        // Tylko zapis stanu, bez odczytu – inaczej pomiar obserwowałby własny licznik i powtarzał się w kółko.
        version = ++measures
    }
}

private val CLICK_KEYS = setOf(Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.Spacebar)

/** Po tylu milisekundach od dotknięcia przycisk w karcie wygląda na wciśnięty (jak przyciski w przewijanych listach). */
private const val PRESS_DELAY_MS = 64L

/**
 * Karta rzeczy w sklepie jako jeden element: tło, obrazek, nazwa i przycisk są rysowane ręcznie,
 * a wyglądają i mają rozmiary jak [DarkCard], [PixelText] i [PixelButton]. Wcześniej karta składała
 * się z kilkunastu elementów (przycisk z własnym stanem wciśnięcia i fokusu, osobne napisy), a przy
 * szybkim przewijaniu w jednej klatce powstawał cały nowy rząd kart – trwało to dłużej niż klatka
 * i lista się zacinała.
 *
 * Dotknięcie przycisku kupuje albo zakłada rzecz, dotknięcie reszty karty (albo nieaktywnego przycisku)
 * pokazuje podgląd. Z klawiatury wybiera się całą kartę (od razu podgląd), Enter działa jak przycisk;
 * złota ramka otacza przycisk, a gdy jest nieaktywny – całą kartę.
 */
@Composable
private fun ShopCard(
    item: ShopItem,
    selected: Boolean,
    onPreview: () -> Unit,
    onBuy: () -> Unit,
    onEquip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val services = LocalServices.current
    val repo = services.repo
    // Karta w siatce to osobny zakres kompozycji – bez odczytu rewizji po zakupie nie odświeżała
    // stanu (przycisk ceny świecił się mimo braku monet, licznik „masz” stał w miejscu).
    repo.revision.intValue
    val progress = repo.progress
    val owned = progress.owns(item)
    val equipped = progress.isEquipped(item)
    val power = if (item.kind == ItemKind.POWERUP) PowerUp.entries.first { it.id == item.id } else null
    // Akcja przycisku; null – przycisk nieaktywny (w użyciu, za mało monet).
    val click: (() -> Unit)? = when {
        power == null && equipped -> null
        power == null && owned -> onEquip
        progress.coins >= item.price -> onBuy
        else -> null
    }
    val style = when {
        click == null -> ButtonStyle.GREY
        power == null && owned -> ButtonStyle.SEA
        else -> ButtonStyle.GREEN
    }
    // Napis na przycisku; null – cena z ikoną monety.
    val label = when {
        power == null && equipped -> stringResource(R.string.shop_equipped)
        power == null && owned -> stringResource(R.string.shop_equip)
        else -> null
    }
    val name = itemName(item)
    val desc = power?.let { powerUpDescription(it) }
    val count = power?.let { stringResource(R.string.shop_owned, progress.powerups(it)) }
    val icon = remember(item.key) {
        when (item.kind) {
            ItemKind.BOAT -> Sprites.sprite("boats/${item.id}/ship")
            ItemKind.SAILOR -> Sprites.sprite("sailors/${item.id}")
            ItemKind.POWERUP -> Sprites.sprite(powerUpIcon(power!!))
            ItemKind.SAIL -> null
        }?.asImageBitmap()
    }
    val iconScale = when (item.kind) {
        ItemKind.BOAT -> 3
        ItemKind.SAILOR -> 4
        ItemKind.POWERUP -> powerUpIconScale(power!!, 3)
        ItemKind.SAIL -> 0
    }
    val coin = rememberSprite("sprites/fishscore_gold")
    val u = LocalUnit.current
    val density = LocalDensity.current
    val measurer = LocalTextMeasurer.current ?: rememberTextMeasurer()
    val input = LocalInputModeManager.current
    val highlight = selected || equipped
    val card = remember { CardLayout() }
    val currentClick by rememberUpdatedState(click)
    val currentPreview by rememberUpdatedState(onPreview)
    val press = { action: () -> Unit ->
        services.audio.play(Sfx.CLICK)
        action()
    }

    Layout(
        modifier = modifier
            // Wybrana karta rysuje się nad sąsiadami, żeby nie zasłaniały jej ramki.
            .zIndex(if (card.focused) 1f else 0f)
            .drawBehind {
                card.version
                val uf = u.toFloat()
                val pad = 6 * u
                val inner = size.width - 2 * pad
                val ring = card.focused && input.inputMode == InputMode.Keyboard
                if (ring && click == null) focusRing(uf)
                pixelBox(
                    uf,
                    if (highlight) Pal.Navy else Pal.SeaDark.copy(alpha = 0.92f),
                    if (highlight) Pal.Gold else Pal.Sea,
                    Pal.Outline,
                    shadow = 1f,
                )
                // Obrazek wyśrodkowany w polu 54×54.
                val box = 54 * u
                if (icon != null) {
                    val k = iconScale * u
                    val x = pad + ((inner - icon.width * k) / 2f).roundToInt()
                    val y = pad + (box - icon.height * k) / 2
                    drawSprite(icon, Offset(x.toFloat(), y.toFloat()), k.toFloat())
                } else {
                    drawIntoCanvas {
                        val c = it.nativeCanvas
                        c.save()
                        c.translate(pad + ((inner - box) / 2f).roundToInt().toFloat(), pad.toFloat())
                        drawShip(c, ShipLook(sail = item.id), 3, u, box.toFloat(), box.toFloat(), sway = false, t = 0f)
                        c.restore()
                    }
                }
                val fp1 = fontPixels(1, u)
                val fp2 = fontPixels(2, u)
                card.name?.let { drawPixelText(it, card.nameAt.x, card.nameAt.y, fp2) }
                card.desc?.let { drawPixelText(it, card.descAt.x, card.descAt.y, fp1) }
                card.count?.let { drawPixelText(it, card.countAt.x, card.countAt.y, fp1) }
                // Przycisk jak PixelButton – wciśnięty ma niższy cień, a napis zjeżdża o piksel.
                val b = card.button
                val pressed = card.pressed && click != null
                translate(b.left, b.top) {
                    if (ring && click != null) focusRing(uf, b.width, b.height)
                    pixelBox(uf, style.base, style.light, style.dark, shadow = if (pressed) 1f else 3f, w = b.width, h = b.height)
                }
                val dy = if (pressed) uf else -uf
                if (label == null) drawSprite(coin, card.coinAt + Offset(0f, dy), uf)
                card.label?.let { drawPixelText(it, card.labelAt.x, card.labelAt.y + dy, fp2) }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { pos ->
                        if (currentClick != null && card.button.contains(pos)) {
                            // Wciśnięcie widać dopiero po chwili – przy przewijaniu przyciski nie mrugają.
                            if (withTimeoutOrNull(PRESS_DELAY_MS) { tryAwaitRelease() } == null) {
                                card.pressed = true
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    card.pressed = false
                                }
                            }
                        }
                    },
                    onTap = { pos ->
                        val action = currentClick
                        if (action != null && card.button.contains(pos)) press(action) else currentPreview()
                    },
                )
            }
            .onFocusChanged {
                card.focused = it.isFocused
                if (it.isFocused) currentPreview()
            }
            .onKeyEvent { event ->
                val action = currentClick
                if (action == null || event.key !in CLICK_KEYS) return@onKeyEvent false
                if (event.type == KeyEventType.KeyUp) press(action)
                true
            }
            .semantics {
                role = Role.Button
                contentDescription = "$name, ${label ?: item.price}"
                onClick {
                    val action = currentClick
                    if (action != null) press(action) else currentPreview()
                    true
                }
            }
            .focusable(),
    ) { _, constraints ->
        val w = constraints.maxWidth
        val pad = 6 * u
        val inner = w - 2 * pad
        val fp1 = fontPixels(1, u)
        val fp2 = fontPixels(2, u)
        fun centered(width: Int) = (pad + (inner - width) / 2).toFloat()
        var y = pad + 54 * u

        val nameText = measurer.measurePixelText(name, fp2, Pal.White, density, inner, TextAlign.Center, maxLines = 1)
        card.name = nameText
        card.nameAt = Offset(centered(nameText.size.width + 2 * fp2), y.toFloat())
        y += nameText.size.height + 2 * fp2

        if (desc != null && count != null) {
            // Zawsze dwie linijki opisu, żeby karty w rzędzie miały równą wysokość.
            val twoLines = measurer.measurePixelText("A\nA", fp1, Color.Transparent, density, inner, outline = false)
            val descText = measurer.measurePixelText(desc, fp1, Pal.Foam, density, inner, TextAlign.Center, maxLines = 2)
            val descH = descText.size.height + 2 * fp1
            val boxH = max(twoLines.size.height, descH)
            card.desc = descText
            card.descAt = Offset(centered(descText.size.width + 2 * fp1), (y + (boxH - descH) / 2).toFloat())
            y += boxH
            val countText = measurer.measurePixelText(count, fp1, Pal.Gold, density, inner)
            card.count = countText
            card.countAt = Offset(centered(countText.size.width + 2 * fp1), y.toFloat())
            y += countText.size.height + 2 * fp1
        } else {
            card.desc = null
            card.count = null
        }
        y += 3 * u

        // Przycisk na całą szerokość: co najmniej MIN_BUTTON wysokości, w środku napis (albo moneta i cena).
        val labelText = measurer.measurePixelText(label ?: item.price.toString(), fp2, Pal.White, density, inner - 14 * u)
        val textW = labelText.size.width + 2 * fp2
        val textH = labelText.size.height + 2 * fp2
        val iconW = if (label == null) coin.width * u + 2 * u else 0
        val contentH = max(textH, if (label == null) coin.height * u else 8 * u)
        val buttonH = max(MIN_BUTTON * u, contentH + 10 * u)
        card.button = Rect(pad.toFloat(), y.toFloat(), (pad + inner).toFloat(), (y + buttonH).toFloat())
        val contentX = centered(iconW + textW)
        val contentY = y + (buttonH - contentH) / 2
        card.coinAt = Offset(contentX, (contentY + (contentH - coin.height * u) / 2).toFloat())
        card.label = labelText
        card.labelAt = Offset(contentX + iconW, (contentY + (contentH - textH) / 2).toFloat())
        y += buttonH + pad

        card.measured()
        layout(w, max(constraints.minHeight, y)) {}
    }
}
