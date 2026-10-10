package wojtoteka.ovh.fishingparty.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.isActive
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.game.GameRenderer
import wojtoteka.ovh.fishingparty.gfx.PixelText as CanvasText
import wojtoteka.ovh.fishingparty.gfx.ShipLook
import wojtoteka.ovh.fishingparty.gfx.ShipPainter
import wojtoteka.ovh.fishingparty.gfx.Sprites
import wojtoteka.ovh.fishingparty.gfx.TileRenderer
import wojtoteka.ovh.fishingparty.level.LevelConstants
import wojtoteka.ovh.fishingparty.level.LevelConstants.TILE
import wojtoteka.ovh.fishingparty.level.LevelConstants.WIDTH
import wojtoteka.ovh.fishingparty.level.WorldMap
import wojtoteka.ovh.fishingparty.level.Worlds
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun MapScreen(onBack: () -> Unit, onSelect: (Int) -> Unit) {
    val services = LocalServices.current
    val repo = services.repo
    repo.revision.intValue
    val progress = repo.progress
    val current = progress.highestUnlocked()
    val look = ShipLook(repo.save.boat, repo.save.sail, repo.save.sailor)
    val worldNames = stringArrayResource(R.array.world_names)
    val worldLabel = stringResource(R.string.world_label)
    val finaleLabel = stringResource(R.string.type_finale)

    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (isActive) withFrameNanos { now ->
            if (last != 0L) time.floatValue += ((now - last) / 1e9f).coerceAtMost(0.1f)
            last = now
        }
    }
    val tiles = remember { TileRenderer() }
    val painter = remember { ShipPainter() }
    val text = remember { CanvasText() }
    val star = remember { Sprites.sprite("fishscore_gold") }
    val starOff = remember { Sprites.sprite("fishscore_grey") }
    val arrow = remember { Sprites.sprite("arrowup") }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        val s = GameRenderer.scaleFor(widthPx).toFloat()
        val viewW = widthPx / s
        val viewH = heightPx / s
        val maxOffset = max(0f, WorldMap.grid.height * TILE - viewH)
        var offset by remember { mutableFloatStateOf((WorldMap.nodeY(current) - viewH * 0.55f).coerceIn(0f, maxOffset)) }
        val scrollState = rememberScrollableState { delta ->
            val old = offset
            offset = (offset - delta / s).coerceIn(0f, maxOffset)
            (old - offset) * s
        }
        val camX = (WIDTH * TILE - viewW) / 2f

        Canvas(
            Modifier
                .fillMaxSize()
                .scrollable(scrollState, Orientation.Vertical)
                .pointerInput(current) {
                    detectTapGestures { pos ->
                        val level = WorldMap.levelAt(camX + pos.x / s, offset + pos.y / s)
                        if (level != null && progress.isUnlocked(level)) onSelect(level)
                    }
                },
        ) {
            val t = time.floatValue
            val camY = (offset * s).roundToInt() / s
            drawRect(Color(Worlds.seaColor(WorldMap.worldOfRow((camY / TILE).toInt()))))
            drawIntoCanvas { c ->
                val canvas = c.nativeCanvas
                canvas.save()
                canvas.scale(s, s)
                canvas.translate(-camX, -camY)
                tiles.draw(
                    canvas, WorldMap.grid, camX, camY, viewW, viewH, t,
                    tilesForRow = { r -> Sprites.tiles(Worlds.theme(WorldMap.worldOfRow(r))) },
                    doorOpen = { r ->
                        val w = WorldMap.gateWorld(r)
                        w >= 0 && current >= WorldMap.gateOpensAt(w)
                    },
                )
                for ((i, pos) in WorldMap.crabs.withIndex()) {
                    val y = pos.second * TILE + 8f
                    if (y < camY - 16 || y > camY + viewH + 16) continue
                    val crab = Sprites.tiles(Worlds.theme(WorldMap.worldOfRow(pos.second))).crab
                    painter.sprite(canvas, crab, pos.first * TILE + 8f + sin(t * 1.2f + i) * 3f, y, if ((t * 4 + i).toInt() % 2 == 0) -8f else 8f)
                }
                // Złote ryby krążą po porcie Wielkiego Finału.
                val finale = LevelConstants.LEVEL_COUNT
                val fy = WorldMap.nodeY(finale)
                if (fy > camY - 80 && fy < camY + viewH + 80) {
                    val fx = WorldMap.nodeX(finale)
                    for (k in 0 until 4) {
                        val lane = if (k % 2 == 0) -1f else 1f
                        val y = fy + lane * (WorldMap.halfHeight(finale) + 16f + (k / 2) * 8f) + sin(t * 2f + k) * 1.5f
                        val phase = t * (0.7f + k * 0.15f) + k * 1.7f
                        val x = fx + sin(phase) * (WorldMap.halfWidth(finale) - 2f)
                        val second = (t + k * 0.1f) % 0.4f >= 0.2f
                        painter.sprite(canvas, Sprites.sprite(if (second) "fishgold2" else "fishgold1"), x, y, 0f, flipX = cos(phase) > 0f)
                    }
                }
                // Gwiazdki pod wysepkami (w świecie, więc przewijają się razem z mapą).
                for (n in 1..LevelConstants.LEVEL_COUNT) {
                    val y = WorldMap.nodeY(n)
                    if (y < camY - 40 || y > camY + viewH + 40) continue
                    if (n > current) continue
                    val st = progress.stars(n)
                    val x0 = WorldMap.nodeX(n) - 13f
                    for (k in 0 until 3) {
                        painter.sprite(canvas, if (k < st) star else starOff, x0 + k * 9f + 4f, y + WorldMap.halfHeight(n) + 5f, 0f)
                    }
                }
                // Łódź gracza przy aktualnym poziomie.
                val nx = WorldMap.nodeX(current)
                val ny = WorldMap.nodeY(current)
                val side = if (WorldMap.islandCol[current - 1] <= 4) 1 else -1
                val heading = (-PI / 2).toFloat() + sin(t * 1.1f) * 0.25f
                val boatX = nx + side * (WorldMap.halfWidth(current) + 10f)
                painter.draw(canvas, floor(boatX * s) / s, ny + sin(t * 2f), heading, true, t, look)
                val arrowY = ny - WorldMap.halfHeight(current) - 10f
                if (t % 0.8f < 0.5f) painter.sprite(canvas, arrow, nx, arrowY + sin(t * 4f) * 1.5f, 0f, alpha = 200)
                canvas.restore()

                // Numery poziomów i nazwy światów – w pikselach ekranu, żeby font był ostry.
                val p = max(3f, (s * 2f / 3f).roundToInt().toFloat())
                for (n in 1..LevelConstants.LEVEL_COUNT) {
                    val y = WorldMap.nodeY(n)
                    if (y < camY - 40 || y > camY + viewH + 40) continue
                    val color = when {
                        n == current -> CanvasText.GOLD
                        n < current -> CanvasText.WHITE
                        else -> 0xFF8A94A6.toInt()
                    }
                    // Numer wyspy finału jest większy, jak sama wyspa.
                    val size = if (n == finale) p * 1.5f else p
                    text.draw(canvas, n.toString(), (WorldMap.nodeX(n) - camX) * s, (y - camY) * s - 3.5f * size, size, color, align = 0)
                }
                val pw = max(2f, floor(s / 2f))
                // Napis świata stoi na jego dolnych wrotach; port finału ma złoty napis na ostatnich.
                for (w in 0..LevelConstants.WORLD_COUNT) {
                    val row = if (w == 0) WorldMap.grid.height - 2 else WorldMap.gateRow[w - 1]
                    val y = row * TILE + 8f
                    if (y < camY - 30 || y > camY + viewH + 30) continue
                    val isFinale = w == LevelConstants.WORLD_COUNT
                    val label = if (isFinale) finaleLabel else worldLabel.format(w + 1, worldNames[w])
                    var size = pw * (if (isFinale) 2f else 1.5f)
                    val maxW = viewW * s * 0.9f
                    val tw = text.width(label, size)
                    if (tw > maxW) size *= maxW / tw
                    val color = if (isFinale) CanvasText.GOLD else CanvasText.WHITE
                    text.draw(canvas, label, viewW * s / 2f, (y - camY) * s - 3.5f * size, size, color, align = 0)
                }
            }
        }

        TopBar(stringResource(R.string.map_title), onBack, Modifier.align(Alignment.TopCenter))

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = px(10)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PixelButton(
                stringResource(R.string.play_level, current),
                onClick = { onSelect(current) },
                style = ButtonStyle.GREEN,
                size = 4,
            )
        }
    }
}
