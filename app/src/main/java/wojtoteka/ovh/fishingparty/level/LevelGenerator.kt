package wojtoteka.ovh.fishingparty.level

import wojtoteka.ovh.fishingparty.core.Rng
import wojtoteka.ovh.fishingparty.level.LevelConstants.FINISH_ROWS
import wojtoteka.ovh.fishingparty.level.LevelConstants.LEVELS_PER_WORLD
import wojtoteka.ovh.fishingparty.level.LevelConstants.LEVEL_COUNT
import wojtoteka.ovh.fishingparty.level.LevelConstants.WIDTH
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Plan etapu – same liczby, bez siatki. Na jego podstawie liczymy też maksymalny wynik. */
class StagePlan(
    val rows: Int,
    val quota: Int,
    val common: Int,
    val blue: Int,
    val golden: Int,
    val enemies: List<EnemyKind>,
    val rocks: Int,
    val islands: Int,
    val doorCenter: Int,
    val wideBanks: Boolean,
)

class LevelPlan(val spec: LevelSpec, val stages: List<StagePlan>) {
    val totalFishPoints: Int
        get() = stages.sumOf {
            it.common * FishKind.COMMON.points + it.blue * FishKind.BLUE.points + it.golden * FishKind.GOLDEN.points
        }
    val goldenCount: Int get() = stages.sumOf { it.golden }
    val enemyCount: Int get() = stages.sumOf { it.enemies.size }
}

/**
 * Generator 500 poziomów. Wszystko wynika z numeru poziomu, więc poziomy nie
 * muszą być nigdzie zapisane, a każdy gracz dostaje identyczną planszę.
 */
object LevelGenerator {

    private const val SEED_SALT = 0x46495348_50415254L

    fun seedOf(number: Int): Long = Rng.mix(SEED_SALT, number.toLong())

    fun worldOf(number: Int): Int = (number - 1) / LEVELS_PER_WORLD

    fun isBoss(number: Int): Boolean = number % LEVELS_PER_WORLD == 0

    /** Trudność 0..1 – rośnie przez całą grę i lekko w obrębie każdego świata. */
    fun difficulty(number: Int): Float {
        val global = (number - 1) / (LEVEL_COUNT - 1f)
        val local = ((number - 1) % LEVELS_PER_WORLD) / (LEVELS_PER_WORLD - 1f)
        return (global * 0.82f + local * 0.18f * (0.4f + 0.6f * global)).coerceIn(0f, 1f)
    }

    private fun rawType(number: Int): LevelType {
        if (number <= 5) return LevelType.CLASSIC
        if (isBoss(number)) return LevelType.BOSS
        val rng = Rng(Rng.mix(seedOf(number), 7))
        val options = buildList {
            add(LevelType.CLASSIC to 30)
            add(LevelType.ROCKY to 12)
            add(LevelType.ARCHIPELAGO to 12)
            if (number >= 10) add(LevelType.CANAL to 10)
            if (number >= 20) add(LevelType.GOLD_RUSH to 8)
            if (number >= 30) add(LevelType.CURRENT to 10)
            if (number >= 40) add(LevelType.TIME_ATTACK to 8)
            if (number >= 50) add(LevelType.SWARM to 10)
        }
        var roll = rng.nextInt(options.sumOf { it.second })
        for ((type, weight) in options) {
            if (roll < weight) return type
            roll -= weight
        }
        return LevelType.CLASSIC
    }

    fun typeOf(number: Int): LevelType {
        val type = rawType(number)
        if (type == LevelType.CLASSIC || type == LevelType.BOSS || number <= 5) return type
        // Unikamy dwóch takich samych „specjalnych” poziomów pod rząd.
        return if (rawType(number - 1) == type) LevelType.CLASSIC else type
    }

    fun plan(number: Int): LevelPlan {
        require(number in 1..LEVEL_COUNT) { "Nieprawidłowy numer poziomu: $number" }
        val seed = seedOf(number)
        val rng = Rng(seed)
        val type = typeOf(number)
        val e = difficulty(number)
        val d = (number - 1) / (LEVEL_COUNT - 1f)

        var stageCount = when {
            number == 1 -> 1
            number <= 5 -> 2
            number <= 15 -> rng.range(2, 3)
            else -> (3 + (d * 3.2f).toInt() + rng.range(-1, 1)).coerceIn(2, 6)
        }
        if (type == LevelType.BOSS) stageCount = min(stageCount + 1, 7)
        if (type == LevelType.TIME_ATTACK) stageCount = min(stageCount, 4)

        val unlocked = EnemyKind.entries.filter { number >= it.firstLevel }
        val newest = unlocked.lastOrNull()
        val featured = if (newest != null && number - newest.firstLevel < 4) newest else null

        val stages = (0 until stageCount).map { index ->
            val rows = if (number == 1) 14 else min(24, 11 + rng.range(0, 4) + (e * 8).toInt())
            var quota = when {
                number == 1 -> 4
                number <= 3 -> 5
                else -> 4 + (e * 6).toInt() + rng.range(0, 2)
            }
            if (type == LevelType.SWARM) quota = max(3, quota - 2)
            val extra = max(1, quota / 3)
            val total = quota + extra
            val blue = if (number >= 4) min(total / 2, rng.range(0, 1 + (e * 3).toInt())) else 0
            val golden = when {
                type == LevelType.GOLD_RUSH -> rng.range(2, 3)
                number < 6 -> 0
                else -> (if (rng.chance(0.35f + e * 0.3f)) 1 else 0) + (if (e > 0.5f && rng.chance(0.25f)) 1 else 0)
            }
            val enemies = planEnemies(rng, number, index, type, e, unlocked, featured)
            var rocks = rng.range(0, 2) + (e * 3).toInt()
            var islands = rng.range(0, 1) + (if (e > 0.3f) 1 else 0)
            when (type) {
                LevelType.ROCKY -> rocks += 5
                LevelType.ARCHIPELAGO -> islands += 3
                LevelType.CANAL -> {
                    rocks = min(rocks, 1); islands = 0
                }
                else -> Unit
            }
            if (number == 1) {
                rocks = 1; islands = 0
            }
            StagePlan(
                rows = rows,
                quota = quota,
                common = total - blue,
                blue = blue,
                golden = golden,
                enemies = enemies,
                rocks = rocks,
                islands = islands,
                doorCenter = if (number <= 2) WIDTH / 2 else rng.range(2, WIDTH - 3),
                wideBanks = type == LevelType.CANAL,
            )
        }

        val par = ceil(
            stages.sumOf { it.quota * 2.6 + it.rows * 0.32 + it.enemies.size * 0.6 } + 3.0
        ).toInt()
        val timeLimit = if (type == LevelType.TIME_ATTACK) ceil(par * 1.35).toInt() else 0

        var currentX = 0f
        var currentY = 0f
        if (type == LevelType.CURRENT) {
            val strength = 10f + e * 14f
            when (rng.nextInt(4)) {
                0 -> currentX = strength
                1 -> currentX = -strength
                else -> currentY = strength // w dół – pod prąd
            }
        }

        val totalEnemies = stages.sumOf { it.enemies.size }
        val goldenTotal = stages.sumOf { it.golden }
        val secondGoal = when {
            totalEnemies == 0 -> SecondGoal.ALL_FISH
            rng.chance(0.6f) -> SecondGoal.NO_DAMAGE
            else -> SecondGoal.ALL_FISH
        }
        val thirdGoal = when (type) {
            LevelType.TIME_ATTACK -> ThirdGoal.PAR_TIME
            LevelType.GOLD_RUSH -> ThirdGoal.ALL_GOLDEN
            else -> {
                val roll = rng.nextInt(100)
                when {
                    number <= 3 -> ThirdGoal.PAR_TIME
                    roll < 40 -> ThirdGoal.PAR_TIME
                    roll < 65 -> ThirdGoal.SCORE
                    roll < 85 -> ThirdGoal.COMBO
                    goldenTotal > 0 -> ThirdGoal.ALL_GOLDEN
                    else -> ThirdGoal.PAR_TIME
                }
            }
        }
        val regularPoints = stages.sumOf { it.common * FishKind.COMMON.points + it.blue * FishKind.BLUE.points }
        val scoreTarget = ((regularPoints * 1.6f + goldenTotal * FishKind.GOLDEN.points * 0.5f +
            stageCount * LevelConstants.STAGE_BONUS) / 10f).roundToInt() * 10

        val spec = LevelSpec(
            number = number,
            world = worldOf(number),
            type = type,
            seed = seed,
            secondGoal = secondGoal,
            thirdGoal = thirdGoal,
            parSeconds = par,
            timeLimitSeconds = timeLimit,
            scoreTarget = scoreTarget,
            comboTarget = 3 + (e * 2.5f).toInt(),
            enemySpeed = 1f + 0.35f * e,
            currentX = currentX,
            currentY = currentY,
        )
        return LevelPlan(spec, stages)
    }

    private fun planEnemies(
        rng: Rng,
        number: Int,
        stage: Int,
        type: LevelType,
        e: Float,
        unlocked: List<EnemyKind>,
        featured: EnemyKind?,
    ): List<EnemyKind> {
        if (unlocked.isEmpty() || number == 1) return emptyList()
        val result = mutableListOf<EnemyKind>()
        if (type == LevelType.SWARM) {
            val piranhas = unlocked.filter {
                it == EnemyKind.PIRANHA || it == EnemyKind.PIRANHA_VERTICAL || it == EnemyKind.RED_PIRANHA
            }
            repeat(3 + (e * 5).toInt()) { result += rng.pick(piranhas) }
            return result
        }
        var budget = 1f + e * 6.5f + rng.nextFloat()
        if (type == LevelType.GOLD_RUSH) budget *= 0.6f
        if (type == LevelType.TIME_ATTACK) budget *= 0.8f
        if (number < 30 && stage == 0) budget *= 0.6f
        if (type == LevelType.BOSS) {
            val strongest = unlocked.maxBy { it.cost }
            result += strongest
            result += strongest
            budget += 1f
        }
        if (featured != null) {
            result += featured
            budget -= featured.cost
        }
        val maxCount = min(10, 3 + (e * 7).toInt() + if (type == LevelType.BOSS) 2 else 0)
        val cheapest = unlocked.minOf { it.cost }
        while (budget >= cheapest && result.size < maxCount) {
            var kind = if (featured != null && rng.chance(0.4f)) featured else rng.pick(unlocked)
            if (kind.cost > budget + 0.5f) kind = unlocked.filter { it.cost <= budget + 0.5f }.ifEmpty { break }.let { rng.pick(it) }
            result += kind
            budget -= kind.cost
        }
        return result
    }

    fun spec(number: Int): LevelSpec = plan(number).spec

    /** Maksymalny teoretycznie możliwy wynik poziomu – wszystko powyżej to oszustwo. */
    fun maxScore(plan: LevelPlan): Int =
        plan.totalFishPoints * LevelConstants.MAX_COMBO +
            plan.stages.size * LevelConstants.STAGE_BONUS +
            plan.spec.parSeconds * LevelConstants.TIME_BONUS_PER_SECOND +
            LevelConstants.NO_DAMAGE_BONUS

    // ------------------------------------------------------------------ siatka

    fun generate(number: Int): LevelLayout {
        val plan = plan(number)
        val rng = Rng(Rng.mix(plan.spec.seed, 99))
        val stagesTopDown = plan.stages.indices.reversed()

        // Układ wierszy: [mur][port mety][brama K][etap K]...[brama 1][etap 1][mur dolny]
        val gateRows = IntArray(plan.stages.size)
        var cursor = FINISH_ROWS
        for (k in stagesTopDown) {
            gateRows[k] = cursor
            cursor += plan.stages[k].rows + 1
        }
        val bottomRow = cursor
        val grid = TileGrid(WIDTH, bottomRow + 1)
        val reserved = BooleanArray(grid.width * grid.height)

        for (c in 0 until WIDTH) {
            grid[c, 0] = Tile.WALL
            grid[c, bottomRow] = Tile.WALL_BOTTOM
        }
        // Port na mecie – spokojna woda z plażami.
        for (r in 1 until FINISH_ROWS) {
            grid[0, r] = Tile.SAND
            grid[1, r] = Tile.SAND
            grid[WIDTH - 1, r] = Tile.SAND
            grid[WIDTH - 2, r] = Tile.SAND
        }

        // Brzegi etapów.
        for (k in plan.stages.indices) {
            val stage = plan.stages[k]
            val top = gateRows[k] + 1
            val bottom = gateRows[k] + stage.rows
            val minW = if (stage.wideBanks) 2 else 1
            val maxW = if (stage.wideBanks) 4 else 3
            fillBank(grid, rng, top, bottom, minW, maxW, left = true)
            fillBank(grid, rng, top, bottom, minW, maxW, left = false)
        }

        // Bramy.
        val doorRanges = plan.stages.map { (it.doorCenter - 1)..(it.doorCenter + 1) }
        for (k in plan.stages.indices) {
            val g = gateRows[k]
            for (c in 0 until WIDTH) {
                grid[c, g] = when {
                    c in doorRanges[k] -> Tile.DOOR
                    grid[c, g - 1] == Tile.SAND && grid[c, g + 1] == Tile.SAND -> Tile.SAND
                    else -> Tile.WALL
                }
            }
            for (c in doorRanges[k]) {
                for (dr in listOf(-2, -1, 1, 2)) {
                    val r = g + dr
                    if (r in 1 until bottomRow && grid[c, r] != Tile.WALL && grid[c, r] != Tile.DOOR) {
                        grid[c, r] = Tile.SEA
                        reserved[r * WIDTH + c] = true
                    }
                }
            }
        }

        // Gwarantowana trasa przez każdy etap (od wejścia do wrót).
        val startCol = WIDTH / 2
        val entries = IntArray(plan.stages.size)
        for (k in plan.stages.indices) {
            val top = gateRows[k] + 1
            val bottom = gateRows[k] + plan.stages[k].rows
            val entryCol = if (k == 0) startCol else plan.stages[k - 1].doorCenter
            entries[k] = entryCol
            carvePath(grid, reserved, rng, entryCol, bottom, plan.stages[k].doorCenter, top)
        }
        // Start: kilka wolnych pól wokół łodzi.
        for (r in bottomRow - 3 until bottomRow) for (c in startCol - 1..startCol + 1) {
            grid[c, r] = Tile.SEA
            reserved[r * WIDTH + c] = true
        }

        // Wyspy i skały.
        for (k in plan.stages.indices) {
            val stage = plan.stages[k]
            val top = gateRows[k] + 3
            val bottom = gateRows[k] + stage.rows - 2
            repeat(stage.islands) { placeIsland(grid, reserved, rng, top, bottom) }
            repeat(stage.rocks) { placeRock(grid, reserved, rng, top, bottom) }
        }

        fixSand(grid)

        // Dekoracyjne fale.
        val waveRng = Rng(Rng.mix(plan.spec.seed, 5))
        for (r in 0 until grid.height) for (c in 0 until WIDTH) {
            if (grid[c, r] == Tile.SEA && waveRng.chance(0.14f)) grid.waves[r * WIDTH + c] = true
        }

        // Zawartość etapów.
        val spawnRng = Rng(Rng.mix(plan.spec.seed, 11))
        val stages = plan.stages.mapIndexed { k, stage ->
            val top = gateRows[k] + 1
            val bottom = gateRows[k] + stage.rows
            val entryRow = bottom
            val reachable = reachableCells(grid, entries[k], entryRow, top, bottom)
            val cells = reachable.toMutableList()
            val fish = mutableListOf<FishSpawn>()
            val kinds = List(stage.blue) { FishKind.BLUE } + List(stage.common) { FishKind.COMMON }
            for (kind in kinds) {
                val cell = takeCell(cells, spawnRng, entries[k], entryRow, minDistance = 2)
                fish += FishSpawn(kind, cell % WIDTH, cell / WIDTH)
            }
            val golden = (0 until stage.golden).map { i ->
                val cell = reachable[spawnRng.nextInt(reachable.size)]
                GoldenSpawn(3f + i * 9f + spawnRng.range(0, 3), cell % WIDTH, cell / WIDTH)
            }
            val enemyCells = reachable.toMutableList()
            val enemies = stage.enemies.map { kind ->
                val cell = takeCell(enemyCells, spawnRng, entries[k], entryRow, minDistance = 7)
                EnemySpawn(kind, cell % WIDTH, cell / WIDTH)
            }
            StageLayout(
                index = k,
                gateRow = gateRows[k],
                topRow = top,
                bottomRow = bottom,
                doorCols = doorRanges[k],
                quota = stage.quota,
                fish = fish,
                golden = golden,
                enemies = enemies,
                reachable = reachable,
            )
        }

        val crabs = mutableListOf<Pair<Int, Int>>()
        val crabRng = Rng(Rng.mix(plan.spec.seed, 13))
        for (r in 1 until bottomRow) for (c in 0 until WIDTH) {
            if (grid[c, r] == Tile.SAND && isSolidSand(grid, c, r) && crabRng.chance(0.08f)) crabs += c to r
        }

        return LevelLayout(plan.spec, grid, stages, startCol, bottomRow - 1, crabs)
    }

    private fun fillBank(grid: TileGrid, rng: Rng, top: Int, bottom: Int, minW: Int, maxW: Int, left: Boolean) {
        var width = rng.range(minW, maxW)
        var r = top
        while (r <= bottom) {
            val run = rng.range(2, 5)
            for (i in 0 until run) {
                if (r > bottom) break
                for (w in 0 until width) {
                    val c = if (left) w else WIDTH - 1 - w
                    grid[c, r] = Tile.SAND
                }
                r++
            }
            width = (width + rng.range(-1, 1)).coerceIn(minW, maxW)
        }
    }

    private fun carvePath(grid: TileGrid, reserved: BooleanArray, rng: Rng, fromCol: Int, fromRow: Int, toCol: Int, toRow: Int) {
        var c = fromCol
        var r = fromRow
        fun mark() {
            grid[c, r] = Tile.SEA
            reserved[r * WIDTH + c] = true
        }
        mark()
        while (r > toRow) {
            val roll = rng.nextInt(10)
            when {
                roll < 6 -> r--
                roll < 8 -> c += if (toCol > c) 1 else if (toCol < c) -1 else rng.sign()
                else -> c += rng.sign()
            }
            c = c.coerceIn(2, WIDTH - 3)
            mark()
        }
        while (c != toCol) {
            c += if (toCol > c) 1 else -1
            mark()
        }
    }

    private fun placeIsland(grid: TileGrid, reserved: BooleanArray, rng: Rng, top: Int, bottom: Int) {
        if (bottom - top < 4) return
        val shapes = listOf(
            intArrayOf(2, 2), intArrayOf(2, 2, 2), intArrayOf(3, 3), intArrayOf(3, 3, 3),
            intArrayOf(2, 4, 2), intArrayOf(2, 4, 4, 2), intArrayOf(2, 2, 2, 2),
        )
        repeat(12) {
            val rowsW = rng.pick(shapes)
            val maxW = rowsW.max()
            val r0 = rng.range(top, bottom - rowsW.size + 1)
            val c0 = rng.range(1, WIDTH - 1 - maxW)
            var ok = true
            for (i in rowsW.indices) {
                val off = (maxW - rowsW[i]) / 2
                for (j in 0 until rowsW[i]) {
                    val c = c0 + off + j
                    val r = r0 + i
                    if (reserved[r * WIDTH + c] || grid[c, r] != Tile.SEA) ok = false
                }
            }
            if (!ok) return@repeat
            for (i in rowsW.indices) {
                val off = (maxW - rowsW[i]) / 2
                for (j in 0 until rowsW[i]) grid[c0 + off + j, r0 + i] = Tile.SAND
            }
            return
        }
    }

    private fun placeRock(grid: TileGrid, reserved: BooleanArray, rng: Rng, top: Int, bottom: Int) {
        if (bottom < top) return
        repeat(20) {
            val c = rng.range(0, WIDTH - 1)
            val r = rng.range(top, bottom)
            if (grid[c, r] == Tile.SEA && !reserved[r * WIDTH + c]) {
                grid[c, r] = Tile.ROCK
                return
            }
        }
    }

    private fun isWaterLike(t: Tile) = t == Tile.SEA || t == Tile.ROCK

    /**
     * Mamy tylko kafelki brzegu dla jednej strony albo zewnętrznego narożnika.
     * Piasek z wodą po przeciwnych stronach (lub z trzech stron) nie ma
     * pasującego sprite'a, więc zamieniamy go w wodę aż do skutku.
     */
    fun fixSand(grid: TileGrid) {
        var changed = true
        while (changed) {
            changed = false
            for (r in 1 until grid.height - 1) for (c in 0 until grid.width) {
                if (grid[c, r] != Tile.SAND) continue
                val n = isWaterLike(grid[c, r - 1])
                val s = isWaterLike(grid[c, r + 1])
                val w = isWaterLike(grid[c - 1, r])
                val e = isWaterLike(grid[c + 1, r])
                if ((n && s) || (w && e)) {
                    grid[c, r] = Tile.SEA
                    changed = true
                }
            }
        }
    }

    private fun isSolidSand(grid: TileGrid, c: Int, r: Int): Boolean =
        grid[c, r - 1] == Tile.SAND && grid[c, r + 1] == Tile.SAND &&
            grid[c - 1, r] == Tile.SAND && grid[c + 1, r] == Tile.SAND

    fun reachableCells(grid: TileGrid, col: Int, row: Int, top: Int, bottom: Int): IntArray {
        val seen = BooleanArray(grid.width * grid.height)
        val queue = ArrayDeque<Int>()
        val out = ArrayList<Int>()
        if (grid[col, row] == Tile.SEA) {
            queue += row * grid.width + col
            seen[row * grid.width + col] = true
        }
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            out += cell
            val c = cell % grid.width
            val r = cell / grid.width
            for ((dc, dr) in NEIGHBOURS) {
                val nc = c + dc
                val nr = r + dr
                if (nr < top || nr > bottom || !grid.inside(nc, nr)) continue
                val idx = nr * grid.width + nc
                if (!seen[idx] && grid[nc, nr] == Tile.SEA) {
                    seen[idx] = true
                    queue += idx
                }
            }
        }
        return out.toIntArray()
    }

    private val NEIGHBOURS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

    private fun takeCell(cells: MutableList<Int>, rng: Rng, entryCol: Int, entryRow: Int, minDistance: Int): Int {
        val far = cells.filter { abs(it % WIDTH - entryCol) + abs(it / WIDTH - entryRow) >= minDistance }
        val pool = far.ifEmpty { cells }
        val cell = pool[rng.nextInt(pool.size)]
        if (cells.size > 1) cells.remove(cell)
        return cell
    }
}
