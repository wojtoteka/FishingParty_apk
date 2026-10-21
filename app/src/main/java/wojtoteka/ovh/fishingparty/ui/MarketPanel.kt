package wojtoteka.ovh.fishingparty.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.data.ItemKind
import wojtoteka.ovh.fishingparty.data.ShopItem
import wojtoteka.ovh.fishingparty.game.Sfx
import wojtoteka.ovh.fishingparty.gfx.SailDesign
import wojtoteka.ovh.fishingparty.online.MarketItem
import wojtoteka.ovh.fishingparty.online.MyMarket
import wojtoteka.ovh.fishingparty.online.Net
import wojtoteka.ovh.fishingparty.online.OnlineResult

/** Procent ceny dla sprzedawcy (MARKET_SELLER_SHARE serwera). */
private const val SELLER_SHARE = 80

/** Najwyższa cena projektu na targu (MAX_PRICE serwera). */
private const val MARKET_MAX_PRICE = 20_000

/**
 * Karta TARG w sklepie: kolory łodzi i żagle zaprojektowane przez graczy (kupić można bez konta),
 * a na dole stoisko gracza z kontem – zarobek do odebrania, wystawione projekty i wystawianie nowych.
 */
@Composable
fun MarketPanel(previewKey: String?, onPreview: (ShopItem) -> Unit, onEquip: (ShopItem) -> Unit) {
    val services = LocalServices.current
    val online = services.online
    val repo = services.repo
    repo.revision.intValue
    val (isOnline, recheck) = rememberOnline()
    var kind by remember { mutableStateOf<ItemKind?>(null) }
    var sort by remember { mutableStateOf("popular") }
    val items = remember { mutableStateListOf<MarketItem>() }
    var page by remember { mutableIntStateOf(0) }
    var more by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<OnlineResult.Failed?>(null) }
    var stall by remember { mutableStateOf<MyMarket?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var selling by remember { mutableStateOf<ShopItem?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val claimedText = stringResource(R.string.market_claimed)

    fun load(next: Boolean) {
        if (loading) return
        loading = true
        error = null
        val target = if (next) page + 1 else 0
        scope.launch {
            when (val res = online.community.market(kind, sort, target)) {
                is Net.Ok -> {
                    if (!next) items.clear()
                    items.addAll(res.value.items)
                    page = target
                    more = res.value.more
                }
                is Net.Err -> error = res.failure
            }
            loading = false
        }
    }
    LaunchedEffect(kind, sort, isOnline, refresh) { if (isOnline && online.configured) load(next = false) }
    LaunchedEffect(online.loggedIn, isOnline, refresh) {
        stall = null
        if (online.loggedIn && isOnline) (online.community.myMarket() as? Net.Ok)?.let { stall = it.value }
    }

    if (!online.configured) {
        NotConfiguredCard()
        return
    }
    if (!isOnline) {
        OfflineCard(recheck)
        return
    }
    Hint(stringResource(R.string.market_info, SELLER_SHARE), Pal.Foam)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(px(2))) {
        for ((value, label) in listOf(null to R.string.market_all, ItemKind.BOAT to R.string.shop_boats, ItemKind.SAIL to R.string.shop_sails)) {
            PixelButton(stringResource(label), onClick = { kind = value }, style = if (kind == value) ButtonStyle.GREEN else ButtonStyle.SEA, size = 1, modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(px(2))) {
        for ((value, label) in listOf("popular" to R.string.community_popular, "recent" to R.string.community_recent)) {
            PixelButton(stringResource(label), onClick = { sort = value }, style = if (sort == value) ButtonStyle.ORANGE else ButtonStyle.WOOD, size = 1, modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
    error?.let { Hint(onlineErrorText(it), Pal.RedLight) }
    info?.let { Hint(it, Pal.GreenLight) }
    if (loading && items.isEmpty()) Hint(stringResource(R.string.online_loading), Pal.Gold)
    if (!loading && error == null && items.isEmpty()) Hint(stringResource(R.string.market_empty), Pal.Foam)
    val mine = stall?.items?.map { it.id }?.toSet().orEmpty()
    for (row in items.chunked(2)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(4))) {
            for (entry in row) {
                MarketCard(
                    entry,
                    own = entry.id in mine,
                    selected = entry.item?.key == previewKey,
                    onPreview = { entry.item?.let(onPreview) },
                    onEquip = { entry.item?.let(onEquip) },
                    onBuy = {
                        scope.launch {
                            when (val res = online.community.buy(entry)) {
                                is Net.Ok -> if (res.value) {
                                    services.audio.play(Sfx.COIN)
                                    entry.item?.let(onPreview)
                                    // Nowa liczba sprzedanych na karcie.
                                    refresh++
                                } else {
                                    error = OnlineResult.Failed("not_enough_coins")
                                }
                                is Net.Err -> error = res.failure
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
    if (more) PixelButton(stringResource(R.string.community_more), onClick = { load(next = true) }, enabled = !loading, style = ButtonStyle.SEA, size = 2, modifier = Modifier.fillMaxWidth())

    // ------------------------------------------------------------------ stoisko gracza
    VSpace(4)
    PixelText(stringResource(R.string.market_stall), size = 3, color = Pal.Gold, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    if (!online.loggedIn) {
        Hint(stringResource(R.string.market_stall_login), Pal.Foam)
        return
    }
    val data = stall
    if (data == null) {
        Hint(stringResource(R.string.online_loading), Pal.Gold)
        return
    }
    DarkCard(Modifier.fillMaxWidth(), highlight = data.pending > 0) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SpriteImage("fishscore_gold", 2)
            HSpace(3)
            PixelText(stringResource(R.string.market_pending, data.pending), size = 2, modifier = Modifier.weight(1f))
            PixelButton(
                stringResource(R.string.daily_claim),
                onClick = {
                    scope.launch {
                        when (val res = online.community.claim()) {
                            is Net.Ok -> {
                                if (res.value > 0) services.audio.play(Sfx.COIN)
                                info = claimedText.format(res.value)
                                refresh++
                            }
                            is Net.Err -> error = res.failure
                        }
                    }
                },
                enabled = data.pending > 0,
                style = ButtonStyle.GREEN,
                size = 2,
            )
        }
    }
    for (entry in data.items.filter { it.active }) {
        DarkCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DesignPreview(entry.kind, entry.design, Modifier.width(px(40)))
                HSpace(4)
                Column(Modifier.weight(1f)) {
                    PixelText(entry.name, size = 2, maxLines = 1)
                    PixelText(stringResource(R.string.market_on_sale, entry.price) + " · " + stringResource(R.string.market_sales, entry.sales), size = 1, color = Pal.Foam)
                }
                PixelButton(
                    stringResource(R.string.market_unlist),
                    onClick = { scope.launch { if (online.community.unlist(entry.id) is Net.Ok) refresh++ } },
                    style = ButtonStyle.RED,
                    size = 1,
                )
            }
        }
    }
    VSpace(2)
    PixelText(stringResource(R.string.market_your_designs), size = 2, color = Pal.Gold)
    val listed = data.items.filter { it.active }.map { it.kind to it.design }.toSet()
    val bought = items.map { it.kind to it.design }.toSet() - listed
    val designs = (repo.progress.creations(ItemKind.BOAT) + repo.progress.creations(ItemKind.SAIL))
        .filter { (it.kind to it.id) !in listed && (it.kind to it.id) !in bought }
    if (designs.isEmpty()) Hint(stringResource(R.string.market_no_designs), Pal.Foam)
    for (item in designs) {
        DarkCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                DesignPreview(item.kind, item.id, Modifier.width(px(40)))
                HSpace(4)
                PixelText(itemName(item), size = 2, modifier = Modifier.weight(1f))
                PixelButton(stringResource(R.string.market_sell), onClick = { selling = item }, style = ButtonStyle.ORANGE, size = 1)
            }
        }
    }
    selling?.let { item ->
        SellDialog(item, onClose = { selling = null }) { name, price ->
            when (val res = online.community.sell(item, name, price)) {
                is Net.Ok -> {
                    selling = null
                    refresh++
                    null
                }
                is Net.Err -> res.failure
            }
        }
    }
}

@Composable
private fun MarketCard(
    entry: MarketItem,
    own: Boolean,
    selected: Boolean,
    onPreview: () -> Unit,
    onEquip: () -> Unit,
    onBuy: () -> Unit,
    modifier: Modifier,
) {
    val repo = LocalServices.current.repo
    repo.revision.intValue
    val progress = repo.progress
    val item = entry.item ?: return
    val owned = progress.owns(item)
    val equipped = progress.isEquipped(item)
    DarkCard(modifier.noRippleClick(onPreview), highlight = selected || equipped) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            DesignPreview(entry.kind, entry.design, Modifier.width(px(54)))
            VSpace(2)
            PixelText(entry.name, size = 2, maxLines = 1, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            PixelText(stringResource(R.string.community_by, entry.seller), size = 1, color = Pal.Foam, maxLines = 1)
            PixelText(stringResource(R.string.market_sales, entry.sales), size = 1, color = Pal.SeaLight)
            VSpace(3)
            when {
                own -> PixelButton(stringResource(R.string.market_yours), onClick = {}, enabled = false, size = 2, modifier = Modifier.fillMaxWidth())
                equipped -> PixelButton(stringResource(R.string.shop_equipped), onClick = {}, enabled = false, size = 2, modifier = Modifier.fillMaxWidth())
                owned -> PixelButton(stringResource(R.string.shop_equip), onClick = onEquip, style = ButtonStyle.SEA, size = 2, modifier = Modifier.fillMaxWidth())
                else -> PixelButton(
                    text = "",
                    onClick = onBuy,
                    enabled = progress.coins >= entry.price,
                    style = ButtonStyle.GREEN,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SpriteImage("fishscore_gold", 1)
                    HSpace(2)
                    PixelText(entry.price.toString(), size = 2)
                }
            }
        }
    }
}

/** Podgląd projektu: kadłub w kolorze z kreatora albo wzór żagla. */
@Composable
fun DesignPreview(kind: ItemKind, design: String, modifier: Modifier = Modifier) {
    Box(modifier.height(px(28)), contentAlignment = Alignment.Center) {
        if (kind == ItemKind.BOAT) {
            SpriteImage(rememberSprite("sprites/boats/$design/ship"), 2)
        } else {
            val grid = remember(design) { SailDesign.parse(design)?.grid() } ?: return@Box
            val u = LocalUnit.current.toFloat()
            Box(
                Modifier.fillMaxWidth().aspectRatio(1.5f).drawBehind {
                    drawRect(Pal.Outline)
                    drawGrid(grid, Offset(u, u), Size(size.width - 2 * u, size.height - 2 * u))
                },
            )
        }
    }
}

/** Wystawienie projektu: nazwa i cena (od ceny w kreatorze do trzykrotności). [onSell] zwraca błąd albo null. */
@Composable
private fun SellDialog(item: ShopItem, onClose: () -> Unit, onSell: suspend (String, Int) -> OnlineResult.Failed?) {
    val min = item.price.coerceAtLeast(50)
    val max = (min * 3).coerceAtMost(MARKET_MAX_PRICE)
    var name by remember { mutableStateOf("") }
    var price by remember { mutableIntStateOf(min) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<OnlineResult.Failed?>(null) }
    val scope = rememberCoroutineScope()
    val go = {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        when {
            clean.length !in 3..20 -> error = OnlineResult.Failed("name_length")
            !busy -> {
                busy = true
                error = null
                scope.launch {
                    error = onSell(clean, price)
                    busy = false
                }
            }
        }
    }
    Overlay(onDismiss = onClose) {
        PixelText(stringResource(R.string.market_sell_title), size = 3, color = Pal.WoodDark, outline = null)
        VSpace(4)
        DesignPreview(item.kind, item.id, Modifier.width(px(80)))
        VSpace(4)
        DarkCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                PixelTextField(name, { name = it }, stringResource(R.string.market_name), maxLength = 20, imeAction = ImeAction.Done, onDone = go)
                VSpace(4)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PixelText(stringResource(R.string.market_price), size = 1, color = Pal.Foam, modifier = Modifier.weight(1f))
                    PixelButton("-", onClick = { price = (price - 50).coerceAtLeast(min) }, style = ButtonStyle.SEA, size = 2)
                    Row(Modifier.width(px(70)), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        SpriteImage("fishscore_gold", 1)
                        HSpace(2)
                        PixelText(price.toString(), size = 2)
                    }
                    PixelButton("+", onClick = { price = (price + 50).coerceAtMost(max) }, style = ButtonStyle.SEA, size = 2)
                }
                VSpace(3)
                PixelText(stringResource(R.string.market_price_info, min, max, price * SELLER_SHARE / 100), size = 1, color = Pal.SeaLight)
            }
        }
        error?.let {
            VSpace(3)
            PixelText(onlineErrorText(it), size = 1, color = Pal.RedDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        VSpace(6)
        PixelButton(stringResource(R.string.market_sell), onClick = go, enabled = !busy, style = ButtonStyle.ORANGE, size = 3, modifier = Modifier.keyPrimary().fillMaxWidth())
        VSpace(3)
        PixelButton(stringResource(R.string.cancel), onClick = onClose, style = ButtonStyle.WOOD, size = 2, modifier = Modifier.fillMaxWidth())
    }
}
