package wojtoteka.ovh.fishingparty.level

import wojtoteka.ovh.fishingparty.core.Rng
import wojtoteka.ovh.fishingparty.level.LevelConstants.LEVELS_PER_WORLD
import wojtoteka.ovh.fishingparty.level.LevelConstants.LEVEL_COUNT
import wojtoteka.ovh.fishingparty.level.LevelConstants.WIDTH
import wojtoteka.ovh.fishingparty.level.LevelConstants.WORLD_COUNT
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Mapa przygody: pionowy szlak z dołu do góry. Każdy poziom to wysepka 2×2,
 * światy oddziela mur z wrotami, które otwierają się po dotarciu do świata.
 */
object WorldMap {
    private const val TOP_ROWS = 4
    private const val BOTTOM_ROWS = 3
    private const val ROWS_PER_LEVEL = 3
    private const val BLOCK = 1 + LEVELS_PER_WORLD * ROWS_PER_LEVEL + 1

    val grid: TileGrid
    /** Lewa kolumna i górny wiersz wysepki każdego poziomu (indeks = numer - 1). */
    val islandCol = IntArray(LEVEL_COUNT)
    val islandRow = IntArray(LEVEL_COUNT)
    val gateRow = IntArray(WORLD_COUNT)
    val crabs = ArrayList<Pair<Int, Int>>()
    private val rowWorld: IntArray

    init {
        val height = TOP_ROWS + WORLD_COUNT * BLOCK + BOTTOM_ROWS
        grid = TileGrid(WIDTH, height)
        rowWorld = IntArray(height)
        val rng = Rng(0x4D4150L)

        for (c in 0 until WIDTH) grid[c, 0] = Tile.WALL
        for (w in 0 until WORLD_COUNT) {
            val blockTop = TOP_ROWS + (WORLD_COUNT - 1 - w) * BLOCK
            gateRow[w] = blockTop
            for (r in blockTop until blockTop + BLOCK) rowWorld[r] = w
            for (k in 0 until LEVELS_PER_WORLD) {
                val n = w * LEVELS_PER_WORLD + k
                val bandTop = blockTop + 1 + (LEVELS_PER_WORLD - 1 - k) * ROWS_PER_LEVEL
                islandRow[n] = bandTop + 1
                islandCol[n] = 2 + (2.5f + 2.5f * sin(n * 0.8f)).roundToInt()
            }
        }
        for (r in 0 until TOP_ROWS) rowWorld[r] = WORLD_COUNT - 1
        for (r in height - BOTTOM_ROWS until height) rowWorld[r] = 0

        // Brzegi po bokach – czasem szersze.
        var r = 1
        while (r < height) {
            val run = rng.range(3, 7)
            val leftWide = rng.chance(0.35f)
            val rightWide = rng.chance(0.35f)
            for (i in 0 until run) {
                if (r + i >= height) break
                grid[0, r + i] = Tile.SAND
                grid[WIDTH - 1, r + i] = Tile.SAND
                if (leftWide) grid[1, r + i] = Tile.SAND
                if (rightWide) grid[WIDTH - 2, r + i] = Tile.SAND
            }
            r += run
        }
        // Plaża startowa na samym dole.
        for (row in height - BOTTOM_ROWS until height) for (c in 0 until WIDTH) grid[c, row] = Tile.SAND

        // Wysepki poziomów.
        for (n in 0 until LEVEL_COUNT) {
            val c = islandCol[n]
            val top = islandRow[n]
            for (dr in 0..1) for (dc in -1..2) {
                // pas wody wokół wysepki, żeby nie zlała się z brzegiem
                if (dc == -1 || dc == 2) {
                    if (c + dc in 1 until WIDTH - 1) grid[c + dc, top + dr] = Tile.SEA
                } else {
                    grid[c + dc, top + dr] = Tile.SAND
                }
            }
        }

        // Mury z wrotami między światami.
        for (w in 0 until WORLD_COUNT) {
            val g = gateRow[w]
            for (c in 0 until WIDTH) grid[c, g] = if (c in 4..6) Tile.DOOR else Tile.WALL
        }

        // Skałki dla urozmaicenia (z dala od wysepek).
        for (n in 0 until LEVEL_COUNT) {
            if (!rng.chance(0.18f)) continue
            val row = islandRow[n] - 1
            val col = rng.range(1, WIDTH - 2)
            if (kotlin.math.abs(col - islandCol[n]) >= 3 && grid[col, row] == Tile.SEA) grid[col, row] = Tile.ROCK
        }

        LevelGenerator.fixSand(grid)
        // Wysepki mogły stracić piasek przy naprawie brzegów – przywracamy je (zawsze są poprawne 2×2).
        for (n in 0 until LEVEL_COUNT) for (dr in 0..1) for (dc in 0..1) grid[islandCol[n] + dc, islandRow[n] + dr] = Tile.SAND
        LevelGenerator.fixSand(grid)

        for (row in 0 until height) for (c in 0 until WIDTH) {
            if (grid[c, row] == Tile.SEA && rng.chance(0.14f)) grid.waves[row * WIDTH + c] = true
            if (grid[c, row] == Tile.SAND && (c == 0 || c == WIDTH - 1) && rng.chance(0.06f)) crabs += c to row
        }
    }

    fun worldOfRow(row: Int): Int = rowWorld[row.coerceIn(0, rowWorld.size - 1)]

    fun gateWorld(row: Int): Int = gateRow.indexOf(row)

    /** Środek wysepki poziomu w jednostkach świata. */
    fun nodeX(level: Int): Float = (islandCol[level - 1] + 1) * LevelConstants.TILE.toFloat()

    fun nodeY(level: Int): Float = (islandRow[level - 1] + 1) * LevelConstants.TILE.toFloat()

    fun levelAt(x: Float, y: Float): Int? {
        val t = LevelConstants.TILE
        for (n in 0 until LEVEL_COUNT) {
            val left = islandCol[n] * t - 4f
            val top = islandRow[n] * t - 4f
            if (x >= left && x <= left + 2 * t + 8f && y >= top && y <= top + 3 * t) return n + 1
        }
        return null
    }
}
