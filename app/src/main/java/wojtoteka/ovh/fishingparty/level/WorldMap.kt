package wojtoteka.ovh.fishingparty.level

import wojtoteka.ovh.fishingparty.core.Rng
import wojtoteka.ovh.fishingparty.level.LevelConstants.LEVELS_PER_WORLD
import wojtoteka.ovh.fishingparty.level.LevelConstants.LEVEL_COUNT
import wojtoteka.ovh.fishingparty.level.LevelConstants.MAX_LEVEL
import wojtoteka.ovh.fishingparty.level.LevelConstants.WIDTH
import wojtoteka.ovh.fishingparty.level.LevelConstants.WORLD_COUNT
import kotlin.math.roundToInt
import kotlin.math.sin

/** Napis świata na mapie: stoi na wrotach w wierszu [row]; [world] = [Worlds.FINALE] to port finału. */
class MapLabel(val row: Int, val world: Int)

/**
 * Mapa przygody: pionowy szlak z dołu do góry. Każdy poziom to wysepka 2×2,
 * światy oddziela mur z wrotami, które otwierają się po dotarciu do świata.
 * Nad ostatnim światem, za własnymi wrotami, leży złoty port Wielkiego Finału
 * z dużą wyspą poziomu 1000. Po jego ukończeniu wrota portu się otwierają, a nad
 * nim mapa sama dobudowuje kolejne światy Bezkresnego Morza (zawsze o jeden dalej,
 * niż dopłynął gracz). Część z poziomami 1–1000 wygląda zawsze tak samo.
 */
class WorldMap private constructor(val endlessWorlds: Int) {
    val levelCount = LEVEL_COUNT + endlessWorlds * LEVELS_PER_WORLD
    val grid: TileGrid

    /** Lewa kolumna, górny wiersz i rozmiar wysepki każdego poziomu (indeks = numer - 1). */
    val islandCol = IntArray(levelCount)
    val islandRow = IntArray(levelCount)
    val islandW = IntArray(levelCount) { 2 }
    val islandH = IntArray(levelCount) { 2 }
    val crabs = ArrayList<Pair<Int, Int>>()
    val labels = ArrayList<MapLabel>()
    private val rowWorld: IntArray

    /** Poziom, od którego stoją otworem wrota w danym wierszu (0 = w wierszu nie ma wrót). */
    private val rowGateOpens: IntArray

    init {
        val campaign = Campaign.instance
        val offset = endlessWorlds * BLOCK
        val height = offset + campaign.grid.height
        grid = TileGrid(WIDTH, height)
        rowWorld = IntArray(height)
        rowGateOpens = IntArray(height)

        // Kampania (poziomy 1–1000) – przesunięta w dół o światy Bezkresnego Morza.
        for (r in 0 until campaign.grid.height) {
            rowWorld[offset + r] = campaign.rowWorld[r]
            for (c in 0 until WIDTH) {
                grid[c, offset + r] = campaign.grid[c, r]
                grid.waves[(offset + r) * WIDTH + c] = campaign.grid.waves[r * WIDTH + c]
            }
        }
        for (n in 0 until LEVEL_COUNT) {
            islandCol[n] = campaign.islandCol[n]
            islandRow[n] = offset + campaign.islandRow[n]
            islandW[n] = campaign.islandW[n]
            islandH[n] = campaign.islandH[n]
        }
        for ((c, r) in campaign.crabs) crabs += c to offset + r
        for (w in 0 until WORLD_COUNT) rowGateOpens[offset + campaign.gateRow[w]] = gateOpensAt(w)
        labels += MapLabel(height - 2, 0)
        for (w in 1 until WORLD_COUNT) labels += MapLabel(offset + campaign.gateRow[w - 1], w)
        labels += MapLabel(offset + campaign.gateRow[WORLD_COUNT - 1], Worlds.FINALE)

        if (endlessWorlds > 0) {
            // Mur na szczycie portu finału zamienia się w wrota na Bezkresne Morze.
            for (c in DOOR_COLS) grid[c, offset] = Tile.DOOR
            rowGateOpens[offset] = LEVEL_COUNT + 1
            for (e in 0 until endlessWorlds) buildEndlessWorld(e)
            LevelGenerator.fixSand(grid)
            for (n in LEVEL_COUNT until levelCount) restoreIsland(n)
            LevelGenerator.fixSand(grid)
            for (e in 0 until endlessWorlds) decorateEndlessWorld(e)
        }
    }

    private fun blockTop(endless: Int) = (endlessWorlds - 1 - endless) * BLOCK

    private fun buildEndlessWorld(endless: Int) {
        val world = WORLD_COUNT + endless
        val top = blockTop(endless)
        val rng = Rng(Rng.mix(MAP_SALT, endless.toLong()))
        for (r in top until top + BLOCK) rowWorld[r] = world
        // Brzegi po bokach – czasem szersze.
        var r = top + 1
        while (r < top + BLOCK) {
            val run = rng.range(3, 6)
            val leftWide = rng.chance(0.35f)
            val rightWide = rng.chance(0.35f)
            for (i in 0 until run) {
                if (r + i >= top + BLOCK) break
                grid[0, r + i] = Tile.SAND
                grid[WIDTH - 1, r + i] = Tile.SAND
                if (leftWide) grid[1, r + i] = Tile.SAND
                if (rightWide) grid[WIDTH - 2, r + i] = Tile.SAND
            }
            r += run
        }
        for (k in 0 until LEVELS_PER_WORLD) {
            val n = LEVEL_COUNT + endless * LEVELS_PER_WORLD + k
            islandRow[n] = top + 1 + (LEVELS_PER_WORLD - 1 - k) * ROWS_PER_LEVEL + 1
            islandCol[n] = islandColumn(n)
            restoreIsland(n, waterAround = true)
        }
        for (c in 0 until WIDTH) grid[c, top] = if (c in DOOR_COLS) Tile.DOOR else Tile.WALL
        rowGateOpens[top] = Worlds.firstLevel(world + 1)
        labels += MapLabel(if (endless == 0) blockTop(0) + BLOCK else blockTop(endless - 1), world)
        for (k in 0 until LEVELS_PER_WORLD) {
            if (!rng.chance(0.18f)) continue
            val n = LEVEL_COUNT + endless * LEVELS_PER_WORLD + k
            val row = islandRow[n] - 1
            val col = rng.range(1, WIDTH - 2)
            if (kotlin.math.abs(col - islandCol[n]) >= 3 && grid[col, row] == Tile.SEA) grid[col, row] = Tile.ROCK
        }
    }

    private fun decorateEndlessWorld(endless: Int) {
        val top = blockTop(endless)
        val rng = Rng(Rng.mix(MAP_SALT xor 0x5741564553L, endless.toLong()))
        for (row in top until top + BLOCK) for (c in 0 until WIDTH) {
            if (grid[c, row] == Tile.SEA && rng.chance(0.14f)) grid.waves[row * WIDTH + c] = true
            if (grid[c, row] == Tile.SAND && (c == 0 || c == WIDTH - 1) && rng.chance(0.06f)) crabs += c to row
        }
    }

    /** Piasek wysepki (i pas wody wokół niej, żeby nie zlała się z brzegiem). */
    private fun restoreIsland(n: Int, waterAround: Boolean = false) {
        val c = islandCol[n]
        val top = islandRow[n]
        for (dr in 0 until islandH[n]) for (dc in -1..islandW[n]) {
            if (dc == -1 || dc == islandW[n]) {
                if (waterAround && c + dc in 1 until WIDTH - 1) grid[c + dc, top + dr] = Tile.SEA
            } else {
                grid[c + dc, top + dr] = Tile.SAND
            }
        }
    }

    /** Numer świata (palety) wiersza; port finału ma umowny numer [Worlds.FINALE]. */
    fun worldOfRow(row: Int): Int = rowWorld[row.coerceIn(0, rowWorld.size - 1)]

    /** Czy wrota w wierszu [row] są otwarte dla gracza, który doszedł do poziomu [current]. */
    fun isGateOpen(row: Int, current: Int): Boolean {
        val opens = rowGateOpens.getOrElse(row) { 0 }
        return opens > 0 && current >= opens
    }

    /** Środek wysepki poziomu w jednostkach świata. */
    fun nodeX(level: Int): Float = (islandCol[level - 1] + islandW[level - 1] / 2f) * LevelConstants.TILE

    fun nodeY(level: Int): Float = (islandRow[level - 1] + islandH[level - 1] / 2f) * LevelConstants.TILE

    /** Połowa szerokości i wysokości wysepki (do rozmieszczenia łodzi, strzałki i gwiazdek). */
    fun halfWidth(level: Int): Float = islandW[level - 1] * LevelConstants.TILE / 2f

    fun halfHeight(level: Int): Float = islandH[level - 1] * LevelConstants.TILE / 2f

    /**
     * Poziomy, których wysepki leżą między wierszami [topRow] i [bottomRow] (włącznie).
     * Wyższe poziomy leżą wyżej na mapie, więc wystarczy wyszukiwanie binarne.
     */
    fun levelsBetween(topRow: Int, bottomRow: Int): IntRange {
        // Pierwszy poziom (od dołu) z wysepką nie niżej niż bottomRow i pierwszy wyżej niż topRow.
        fun firstAtOrAbove(row: Int): Int {
            var lo = 0
            var hi = levelCount
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (islandRow[mid] <= row) hi = mid else lo = mid + 1
            }
            return lo
        }
        return (firstAtOrAbove(bottomRow) + 1)..firstAtOrAbove(topRow - 1)
    }

    fun levelAt(x: Float, y: Float): Int? {
        val t = LevelConstants.TILE
        val row = (y / t).toInt()
        for (level in levelsBetween(row - 4, row + 4)) {
            val n = level - 1
            val left = islandCol[n] * t - 4f
            val top = islandRow[n] * t - 4f
            if (x >= left && x <= left + islandW[n] * t + 8f && y >= top && y <= top + (islandH[n] + 1) * t) return level
        }
        return null
    }

    /**
     * Poziomy 1–1000: mapa kampanii liczona raz. Kod generujący jest taki sam jak
     * przed dodaniem Bezkresnego Morza, więc ta część mapy nie zmieniła wyglądu.
     */
    private class Campaign {
        val grid: TileGrid
        val islandCol = IntArray(LEVEL_COUNT)
        val islandRow = IntArray(LEVEL_COUNT)
        val islandW = IntArray(LEVEL_COUNT) { 2 }
        val islandH = IntArray(LEVEL_COUNT) { 2 }
        val gateRow = IntArray(WORLD_COUNT)
        val crabs = ArrayList<Pair<Int, Int>>()
        val rowWorld: IntArray

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
                    islandCol[n] = islandColumn(n)
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
                for (c in 0 until WIDTH) grid[c, g] = if (c in DOOR_COLS) Tile.DOOR else Tile.WALL
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

        companion object {
            val instance by lazy { Campaign() }
        }
    }

    companion object {
        /** Port finału: mur na samej górze, woda i wyspa poziomu 1000. */
        private const val FINALE_ROWS = 9
        private const val BOTTOM_ROWS = 3
        private const val ROWS_PER_LEVEL = 3
        private const val BLOCK = 1 + LEVELS_PER_WORLD * ROWS_PER_LEVEL + 1
        private val DOOR_COLS = 4..6
        private const val MAP_SALT = 0x4D41505F454E444CL

        /** Najwięcej światów Bezkresnego Morza (do poziomu [MAX_LEVEL]). */
        private const val MAX_ENDLESS_WORLDS = (MAX_LEVEL - LEVEL_COUNT) / LEVELS_PER_WORLD

        private fun islandColumn(n: Int): Int = 2 + (2.5f + 2.5f * sin(n * 0.8f)).roundToInt()

        /** Poziom, od którego wrota nad światem kampanii [world] stoją otworem (ostatnie prowadzą do portu finału). */
        private fun gateOpensAt(world: Int): Int = if (world == WORLD_COUNT - 1) LEVEL_COUNT else Worlds.firstLevel(world + 1)

        /** Ile światów Bezkresnego Morza pokazać graczowi na poziomie [current]: jego świat i jeden za nim. */
        fun endlessWorldsFor(current: Int): Int =
            if (current <= LEVEL_COUNT) 0 else (EndlessLevels.endlessWorld(current) + 2).coerceAtMost(MAX_ENDLESS_WORLDS)

        @Volatile
        private var cached: WorldMap? = null

        /** Mapa dla gracza, który doszedł do poziomu [current] (ostatnio zbudowana jest zapamiętana). */
        fun forLevel(current: Int): WorldMap {
            val worlds = endlessWorldsFor(current)
            cached?.let { if (it.endlessWorlds == worlds) return it }
            return WorldMap(worlds).also { cached = it }
        }
    }
}
