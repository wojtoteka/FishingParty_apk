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
import androidx.compose.runtime.Composable
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
                CoinCounter(progress.coins, size = 3)
                Box(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpriteImage("fishscore_gold", 2)
                    HSpace(2)
                    PixelText("${progress.totalStars()}/1500", size = 2)
                }
            }
            VSpace(14)
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
            VSpace(6)
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
