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
 * Nad ostatnim światem, za własnymi wrotami, leży złoty port Wielkiego Finału
 * z dużą wyspą poziomu 1000.
 */
object WorldMap {
    /** Port finału: mur na samej górze, woda i wyspa poziomu 1000. */
    private const val FINALE_ROWS = 9
    private const val BOTTOM_ROWS = 3
    private const val ROWS_PER_LEVEL = 3
    private const val BLOCK = 1 + LEVELS_PER_WORLD * ROWS_PER_LEVEL + 1

    val grid: TileGrid
    /** Lewa kolumna, górny wiersz i rozmiar wysepki każdego poziomu (indeks = numer - 1). */
    val islandCol = IntArray(LEVEL_COUNT)
    val islandRow = IntArray(LEVEL_COUNT)
    val islandW = IntArray(LEVEL_COUNT) { 2 }
    val islandH = IntArray(LEVEL_COUNT) { 2 }
    val gateRow = IntArray(WORLD_COUNT)
    val crabs = ArrayList<Pair<Int, Int>>()
    private val rowWorld: IntArray

    init {
        val height = FINALE_ROWS + WORLD_COUNT * BLOCK + BOTTOM_ROWS
        grid = TileGrid(WIDTH, height)
        rowWorld = IntArray(height)
        val rng = Rng(0x4D4150L)

        for (c in 0 until WIDTH) grid[c, 0] = Tile.WALL
        for (w in 0 until WORLD_COUNT) {
            val blockTop = FINALE_ROWS + (WORLD_COUNT - 1 - w) * BLOCK
            gateRow[w] = blockTop
            for (r in blockTop until blockTop + BLOCK) rowWorld[r] = w
            for (k in 0 until LEVELS_PER_WORLD) {
                val n = w * LEVELS_PER_WORLD + k
                val bandTop = blockTop + 1 + (LEVELS_PER_WORLD - 1 - k) * ROWS_PER_LEVEL
                islandRow[n] = bandTop + 1
                islandCol[n] = 2 + (2.5f + 2.5f * sin(n * 0.8f)).roundToInt()
            }
        }
        // Wyspa finału (5×3, na środku portu) zamiast zwykłej wysepki – jej pas w ostatnim świecie zostaje wolną wodą.
        val finale = LEVEL_COUNT - 1
        islandW[finale] = 5
        islandH[finale] = 3
        islandCol[finale] = (WIDTH - islandW[finale]) / 2
        islandRow[finale] = 3
        for (r in 0 until FINALE_ROWS) rowWorld[r] = Worlds.FINALE
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
            for (dr in 0 until islandH[n]) for (dc in -1..islandW[n]) {
                // pas wody wokół wysepki, żeby nie zlała się z brzegiem
                if (dc == -1 || dc == islandW[n]) {
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

        // Skałki dla urozmaicenia (z dala od wysepek; port finału zostaje czysty).
        for (n in 0 until LEVEL_COUNT - 1) {
            if (!rng.chance(0.18f)) continue
            val row = islandRow[n] - 1
            val col = rng.range(1, WIDTH - 2)
            if (kotlin.math.abs(col - islandCol[n]) >= 3 && grid[col, row] == Tile.SEA) grid[col, row] = Tile.ROCK
        }

        LevelGenerator.fixSand(grid)
        // Wysepki mogły stracić piasek przy naprawie brzegów – przywracamy je (zawsze są poprawne 2×2).
        for (n in 0 until LEVEL_COUNT) for (dr in 0 until islandH[n]) for (dc in 0 until islandW[n]) grid[islandCol[n] + dc, islandRow[n] + dr] = Tile.SAND
        LevelGenerator.fixSand(grid)

        for (row in 0 until height) for (c in 0 until WIDTH) {
            if (grid[c, row] == Tile.SEA && rng.chance(0.14f)) grid.waves[row * WIDTH + c] = true
            if (grid[c, row] == Tile.SAND && (c == 0 || c == WIDTH - 1) && rng.chance(0.06f)) crabs += c to row
        }
        // Dwa kraby pilnują wyspy finału.
        crabs += islandCol[finale] + 1 to islandRow[finale] + islandH[finale] - 1
        crabs += islandCol[finale] + islandW[finale] - 2 to islandRow[finale]
    }

    /** Numer świata (palety) wiersza; port finału ma umowny numer [Worlds.FINALE]. */
    fun worldOfRow(row: Int): Int = rowWorld[row.coerceIn(0, rowWorld.size - 1)]

    fun gateWorld(row: Int): Int = gateRow.indexOf(row)

    /** Poziom, od którego wrota nad światem [world] stoją otworem (ostatnie prowadzą do portu finału). */
    fun gateOpensAt(world: Int): Int = if (world == WORLD_COUNT - 1) LEVEL_COUNT else Worlds.firstLevel(world + 1)

    /** Środek wysepki poziomu w jednostkach świata. */
    fun nodeX(level: Int): Float = (islandCol[level - 1] + islandW[level - 1] / 2f) * LevelConstants.TILE

    fun nodeY(level: Int): Float = (islandRow[level - 1] + islandH[level - 1] / 2f) * LevelConstants.TILE

    /** Połowa szerokości i wysokości wysepki (do rozmieszczenia łodzi, strzałki i gwiazdek). */
    fun halfWidth(level: Int): Float = islandW[level - 1] * LevelConstants.TILE / 2f

    fun halfHeight(level: Int): Float = islandH[level - 1] * LevelConstants.TILE / 2f

    fun levelAt(x: Float, y: Float): Int? {
        val t = LevelConstants.TILE
        for (n in 0 until LEVEL_COUNT) {
            val left = islandCol[n] * t - 4f
            val top = islandRow[n] * t - 4f
            if (x >= left && x <= left + islandW[n] * t + 8f && y >= top && y <= top + (islandH[n] + 1) * t) return n + 1
        }
        return null
    }
}
