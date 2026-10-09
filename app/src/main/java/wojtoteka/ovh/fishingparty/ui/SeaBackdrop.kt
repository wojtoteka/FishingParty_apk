package wojtoteka.ovh.fishingparty.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.isActive
import androidx.compose.runtime.withFrameNanos
import wojtoteka.ovh.fishingparty.core.Rng
import wojtoteka.ovh.fishingparty.game.GameRenderer
import wojtoteka.ovh.fishingparty.gfx.ShipLook
import wojtoteka.ovh.fishingparty.gfx.ShipPainter
import wojtoteka.ovh.fishingparty.gfx.Sprites
import wojtoteka.ovh.fishingparty.gfx.TileRenderer
import wojtoteka.ovh.fishingparty.level.LevelConstants.TILE
import wojtoteka.ovh.fishingparty.level.LevelConstants.WIDTH
import wojtoteka.ovh.fishingparty.level.LevelGenerator
import wojtoteka.ovh.fishingparty.level.Tile
import wojtoteka.ovh.fishingparty.level.TileGrid
import wojtoteka.ovh.fishingparty.level.Worlds
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** Zapętlone morze z brzegami – tło menu i ekranów (przewija się, jakby łódź płynęła). */
private object Backdrop {
    const val ROWS = 48
    val grid: TileGrid = TileGrid(WIDTH, ROWS).also { g ->
        g.wrapRows = true
        val rng = Rng(0xBAC4L)
        var r = 0
        while (r < ROWS) {
            val run = rng.range(3, 6)
            val left = rng.range(1, 2)
            val right = rng.range(1, 2)
            for (i in 0 until run) {
                if (r + i >= ROWS) break
                for (c in 0 until left) g[c, r + i] = Tile.SAND
                for (c in 0 until right) g[WIDTH - 1 - c, r + i] = Tile.SAND
            }
            r += run
        }
        // Zawinięcie: pierwszy i ostatni wiersz muszą do siebie pasować.
        for (c in 0 until WIDTH) g[c, ROWS - 1] = g[c, 0]
        repeat(5) {
            val c = rng.range(3, WIDTH - 4)
            val row = rng.range(2, ROWS - 3)
            g[c, row] = Tile.ROCK
        }
        LevelGenerator.fixSand(g)
        for (row in 0 until ROWS) for (c in 0 until WIDTH) {
            if (g[c, row] == Tile.SEA && rng.chance(0.16f)) g.waves[row * WIDTH + c] = true
        }
    }

    class FishSprite(val x: Float, val row: Int, val speed: Float, val phase: Float, val kind: Int)

    val fish = List(14) {
        val rng = Rng(it * 31L + 7)
        FishSprite(rng.range(40, 140).toFloat(), rng.range(0, ROWS - 1), 6f + rng.nextFloat() * 8f, rng.nextFloat() * 10f, rng.nextInt(10))
    }
}

/**
 * @param world numer świata – tło przyjmuje jego paletę
 * @param ship jeśli podany, na środku płynie łódź gracza
 */
@Composable
fun SeaBackdrop(world: Int, modifier: Modifier = Modifier, ship: ShipLook? = null, dim: Float = 0f, shipY: Float = 0.62f) {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (last != 0L) time.floatValue += ((now - last) / 1e9f).coerceAtMost(0.1f)
                last = now
            }
        }
    }
    val tiles = remember { TileRenderer() }
    val painter = remember { ShipPainter() }
    val tileSet = remember(world) { Sprites.tiles(Worlds.theme(world)) }
    val sea = Color(Worlds.seaColor(world))
    Canvas(modifier) {
        val t = time.floatValue
        val s = GameRenderer.scaleFor(size.width.toInt()).toFloat()
        val viewW = size.width / s
        val viewH = size.height / s
        val camX = (WIDTH * TILE - viewW) / 2f
        val camY = ((-t * 10f) * s).roundToInt() / s
        drawRect(sea)
        drawIntoCanvas { c ->
            val canvas = c.nativeCanvas
            canvas.save()
            canvas.scale(s, s)
            canvas.translate(-camX, -camY)
            tiles.draw(canvas, Backdrop.grid, camX, camY, viewW, viewH, t, { tileSet }, { false })
            val loop = Backdrop.ROWS * TILE
            for (f in Backdrop.fish) {
                val baseY = f.row * TILE + 8f
                val y = camY + ((baseY - camY) % loop + loop) % loop
                if (y > camY + viewH + 16f) continue
                val span = 90f
                val pos = (t * f.speed + f.phase * 10f) % (2 * span)
                val goingRight = pos < span
                val x = 43f + if (goingRight) pos else 2 * span - pos
                val second = (t + f.phase) % 0.4f >= 0.2f
                val name = when (f.kind) {
                    0 -> if (second) "fishgold2" else "fishgold1"
                    1, 2 -> if (second) "fishblue2" else "fishblue1"
                    else -> if (second) "fish2" else "fish1"
                }
                painter.sprite(canvas, Sprites.sprite(name), x, y, 0f, flipX = goingRight)
            }
            if (ship != null) {
                val heading = (-PI / 2).toFloat() + sin(t * 0.7f) * 0.35f
                val x = WIDTH * TILE / 2f + sin(t * 0.35f) * 18f
                val y = camY + viewH * shipY + sin(t * 1.3f) * 2f
                painter.draw(canvas, floor(x * s) / s, y, heading, true, t, ship)
            }
            canvas.restore()
        }
        if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
    }
}
