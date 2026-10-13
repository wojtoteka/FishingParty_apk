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
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** Zapętlone morze z brzegami – tło menu i ekranów (przewija się, jakby łódź płynęła). */
internal object Backdrop {
    const val ROWS = 48

    /** Prędkość łodzi w górę (piksele świata na sekundę). */
    const val SPEED = 10f
    private const val LOOP = ROWS * TILE

    /** Połowa szerokości łodzi z zapasem – tyle wody musi być z każdej strony. */
    private const val SHIP_HALF = 13f

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

    /** Ryby pływają tylko w wierszach bez skał – nie przenikają przez kamienie. */
    val fish = List(14) {
        val rng = Rng(it * 31L + 7)
        var row = rng.range(0, ROWS - 1)
        while ((0 until WIDTH).any { c -> grid[c, row] == Tile.ROCK }) row = (row + 1) % ROWS
        FishSprite(rng.range(40, 140).toFloat(), row, 6f + rng.nextFloat() * 8f, rng.nextFloat() * 10f, rng.nextInt(10))
    }

    /**
     * Trasa łodzi: x środka łodzi dla każdego piksela wysokości pętli. Łagodny
     * slalom omija skały i trzyma się z dala od brzegów, więc łódź nigdy nie
     * przepływa przez ląd.
     */
    val lane: FloatArray = run {
        val lo = FloatArray(ROWS)
        val hi = FloatArray(ROWS)
        val target = FloatArray(ROWS) { r -> WIDTH * TILE / 2f + 20f * sin(2f * PI.toFloat() * 3f * r / ROWS) }
        for (r in 0 until ROWS) {
            // Łódź (z żaglem) zajmuje ok. 1,5 kafelka w pionie – sprawdzamy też sąsiednie wiersze.
            var left = 0f
            var right = WIDTH * TILE.toFloat()
            val rocks = ArrayList<Float>()
            for (dr in -1..1) for (c in 0 until WIDTH) {
                val t = grid[c, r + dr]
                if (t == Tile.ROCK) rocks += c * TILE + TILE / 2f
                if (t == Tile.SAND || t == Tile.WALL) {
                    if (c < WIDTH / 2) left = maxOf(left, (c + 1f) * TILE) else right = minOf(right, c.toFloat() * TILE)
                }
            }
            var a = left + SHIP_HALF
            var b = right - SHIP_HALF
            for (rock in rocks) {
                val clear = TILE / 2f + SHIP_HALF
                val roomLeft = (rock - clear) - a
                val roomRight = b - (rock + clear)
                val goLeft = when {
                    roomLeft < 0f -> false
                    roomRight < 0f -> true
                    else -> target[r] < rock
                }
                if (goLeft) b = minOf(b, rock - clear) else a = maxOf(a, rock + clear)
            }
            lo[r] = a
            hi[r] = maxOf(a, b)
        }
        // Wygładzanie z ograniczeniami: średnia z sąsiadów, potem powrót do dozwolonego pasa.
        val x = FloatArray(ROWS) { target[it].coerceIn(lo[it], hi[it]) }
        repeat(60) {
            val prev = x.copyOf()
            for (r in 0 until ROWS) {
                val avg = (prev[(r + ROWS - 1) % ROWS] + 2f * prev[r] + prev[(r + 1) % ROWS]) / 4f
                x[r] = avg.coerceIn(lo[r], hi[r])
            }
        }
        // Na piksele: interpolacja między środkami wierszy i dodatkowe rozmycie, żeby skręty były płynne.
        var px = FloatArray(LOOP) { y ->
            val f = (y - TILE / 2f) / TILE
            val r0 = Math.floorMod(floor(f).toInt(), ROWS)
            val r1 = (r0 + 1) % ROWS
            val k = f - floor(f)
            x[r0] + (x[r1] - x[r0]) * k
        }
        repeat(3) {
            val src = px
            px = FloatArray(LOOP) { y ->
                var sum = 0f
                for (d in -6..6) sum += src[Math.floorMod(y + d, LOOP)]
                sum / 13f
            }
        }
        px
    }

    /** x łodzi na wysokości [y] świata (pętla się powtarza). */
    fun laneX(y: Float): Float {
        val f = ((y % LOOP) + LOOP) % LOOP
        val i = f.toInt() % LOOP
        val k = f - floor(f)
        return lane[i] + (lane[(i + 1) % LOOP] - lane[i]) * k
    }

    /** Kurs łodzi płynącej w górę po trasie (kąt jak w grze: -π/2 = prosto w górę). */
    fun heading(y: Float): Float {
        val slope = (laneX(y - 6f) - laneX(y + 6f)) / 12f
        return atan2(-1f, slope)
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
        val s = GameRenderer.scaleFor(size.width.toInt(), size.height.toInt()).toFloat()
        val viewW = size.width / s
        val viewH = size.height / s
        val camX = (WIDTH * TILE - viewW) / 2f
        val camY = ((-t * Backdrop.SPEED) * s).roundToInt() / s
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
                // Łódź płynie po trasie omijającej skały; lekkie kołysanie tylko dla życia.
                val y = camY + viewH * shipY
                val x = Backdrop.laneX(y)
                val heading = Backdrop.heading(y) + sin(t * 1.7f) * 0.04f
                painter.draw(canvas, floor(x * s) / s, y + sin(t * 1.3f) * 1.5f, heading, true, t, ship)
            }
            canvas.restore()
        }
        if (dim > 0f) drawRect(Color.Black.copy(alpha = dim))
    }
}
