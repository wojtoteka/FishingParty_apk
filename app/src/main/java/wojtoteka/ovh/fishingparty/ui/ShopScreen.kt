package wojtoteka.ovh.fishingparty.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.data.Catalog
import wojtoteka.ovh.fishingparty.data.ItemKind
import wojtoteka.ovh.fishingparty.data.PowerUp
import wojtoteka.ovh.fishingparty.data.ShopItem
import wojtoteka.ovh.fishingparty.game.Sfx
import wojtoteka.ovh.fishingparty.gfx.ShipLook
import wojtoteka.ovh.fishingparty.gfx.ShipPainter
import wojtoteka.ovh.fishingparty.level.LevelGenerator
import kotlin.math.PI
import kotlin.math.sin

/** Łódź narysowana w powiększeniu (podgląd w sklepie). */
@Composable
fun ShipPreview(look: ShipLook, scale: Int, modifier: Modifier = Modifier, sway: Boolean = true) {
    val painter = remember { ShipPainter() }
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
        val k = (scale * u).toFloat()
        val heading = (-PI / 2).toFloat() + if (sway) sin(t * 0.9f) * 0.5f else 0f
        drawIntoCanvas { c ->
            val canvas = c.nativeCanvas
            canvas.save()
            canvas.translate(size.width / 2f, size.height / 2f + 4f * k)
            canvas.scale(k, k)
            painter.draw(canvas, 0f, 0f, heading, true, t, look)
            canvas.restore()
        }
    }
}

private enum class ShopTab { BOATS, SAILS, SAILORS, POWERUPS }

@Composable
fun ShopScreen(onBack: () -> Unit) {
    val services = LocalServices.current
    val repo = services.repo
    repo.revision.intValue
    val progress = repo.progress
    var tab by remember { mutableStateOf(ShopTab.BOATS) }
    var preview by remember { mutableStateOf<ShopItem?>(null) }
    val equipped = ShipLook(repo.save.boat, repo.save.sail, repo.save.sailor)
    val look = when (preview?.kind) {
        ItemKind.BOAT -> equipped.copy(boat = preview!!.id)
        ItemKind.SAIL -> equipped.copy(sail = preview!!.id)
        ItemKind.SAILOR -> equipped.copy(sailor = preview!!.id)
        else -> equipped
    }
    val items = when (tab) {
        ShopTab.BOATS -> Catalog.boats
        ShopTab.SAILS -> Catalog.sails
        ShopTab.SAILORS -> Catalog.sailors
        ShopTab.POWERUPS -> Catalog.powerups
    }

    Box(Modifier.fillMaxSize()) {
        SeaBackdrop(LevelGenerator.worldOf(progress.highestUnlocked()), Modifier.fillMaxSize(), dim = 0.2f)
        Column(Modifier.fillMaxSize()) {
            TopBar(stringResource(R.string.shop_title), onBack)
            ShipPreview(look, 5, Modifier.fillMaxWidth().height(px(70)))
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
                            },
                        ),
                        onClick = {
                            tab = t
                            preview = null
                        },
                        style = if (t == tab) ButtonStyle.GREEN else ButtonStyle.SEA,
                        size = 2,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            VSpace(4)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars),
                contentPadding = PaddingValues(horizontal = px(6), vertical = px(4)),
                horizontalArrangement = Arrangement.spacedBy(px(4)),
                verticalArrangement = Arrangement.spacedBy(px(4)),
            ) {
                items(items, key = { it.key }) { item ->
                    ShopCard(
                        item = item,
                        selected = preview?.key == item.key,
                        onPreview = { if (item.kind != ItemKind.POWERUP) preview = item },
                        onBuy = {
                            if (progress.buy(item)) {
                                services.audio.play(Sfx.COIN)
                                repo.changed()
                            }
                        },
                        onEquip = {
                            progress.equip(item)
                            preview = null
                            repo.changed()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ShopCard(item: ShopItem, selected: Boolean, onPreview: () -> Unit, onBuy: () -> Unit, onEquip: () -> Unit) {
    val repo = LocalServices.current.repo
    val progress = repo.progress
    val owned = progress.owns(item)
    val equipped = progress.isEquipped(item)
    DarkCard(Modifier.fillMaxWidth().noRippleClick(onPreview), highlight = selected || equipped) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.height(px(40)).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (item.kind) {
                    ItemKind.BOAT -> SpriteImage("boats/${item.id}/ship", 3)
                    ItemKind.SAIL -> ShipPreview(ShipLook(sail = item.id), 3, Modifier.size(px(40)), sway = false)
                    ItemKind.SAILOR -> SpriteImage("sailors/${item.id}", 4)
                    ItemKind.POWERUP -> SpriteImage(powerUpIcon(PowerUp.entries.first { it.id == item.id }), 3)
                }
            }
            PixelText(itemName(item), size = 2, align = TextAlign.Center, modifier = Modifier.fillMaxWidth(), maxLines = 1)
            if (item.kind == ItemKind.POWERUP) {
                val p = PowerUp.entries.first { it.id == item.id }
                // Zawsze dwie linijki opisu, żeby karty w rzędzie miały równą wysokość.
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    PixelText("A\nA", size = 1, color = Color.Transparent, outline = null)
                    PixelText(powerUpDescription(p), size = 1, color = Pal.Foam, align = TextAlign.Center, maxLines = 2, modifier = Modifier.fillMaxWidth())
                }
                PixelText(stringResource(R.string.shop_owned, progress.powerups(p)), size = 1, color = Pal.Gold)
            }
            VSpace(3)
            // Wszystkie warianty przycisku mają tę samą wysokość co przycisk z ceną (ikona + tekst).
            when {
                item.kind != ItemKind.POWERUP && equipped -> PixelButton(
                    text = "", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth(),
                ) { ButtonLabel(stringResource(R.string.shop_equipped)) }
                item.kind != ItemKind.POWERUP && owned -> PixelButton(
                    text = "", onClick = onEquip, style = ButtonStyle.SEA, modifier = Modifier.fillMaxWidth(),
                ) { ButtonLabel(stringResource(R.string.shop_equip)) }
                else -> PixelButton(
                    text = "",
                    onClick = onBuy,
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
    }
}

@Composable
private fun ButtonLabel(text: String) {
    Box(Modifier.size(width = 0.dp, height = px(8)))
    PixelText(text, size = 2)
}
