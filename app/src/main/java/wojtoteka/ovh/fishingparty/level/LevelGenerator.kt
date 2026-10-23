package wojtoteka.ovh.fishingparty.level

import wojtoteka.ovh.fishingparty.core.Rng
import wojtoteka.ovh.fishingparty.level.LevelConstants.FINISH_ROWS
import wojtoteka.ovh.fishingparty.level.LevelConstants.FIRST_VOYAGE_LEVELS
import wojtoteka.ovh.fishingparty.level.LevelConstants.LEVELS_PER_WORLD
import wojtoteka.ovh.fishingparty.level.LevelConstants.LEVEL_COUNT
import wojtoteka.ovh.fishingparty.level.LevelConstants.MAX_COMBO
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
    /**
     * Miejsca potworów w kolejności losowania pozycji. Zwykle to samo co [enemies]; gdy widmokuda
     * zastąpi kilka potworów ([Stalkers]), zajmuje miejsce jednego z nich, a reszta zostaje pusta (null) –
     * dzięki temu ryby, skały i pozostałe potwory leżą dokładnie tam, gdzie przedtem.
     */
    val enemySlots: List<EnemyKind?> = enemies,
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
 * Generator poziomów. Wszystko wynika z numeru poziomu, więc poziomy nie
 * muszą być nigdzie zapisane, a każdy gracz dostaje identyczną planszę.
 * Poziomy 1–[INTRO_END] przedstawiają po kolei wszystkie potwory (każdego przez dwa poziomy),
 * dalej do 500 są łatwe, a potem normalne poziomy, 501–999 to druga, trudna wyprawa,
 * poziom 1000 to ręcznie ułożony Wielki Finał, a od 1001
 * zaczyna się Bezkresne Morze – poziomy z seeda ([EndlessLevels]).
 */
object LevelGenerator {

    private const val SEED_SALT = 0x46495348_50415254L

    fun seedOf(number: Int): Long = Rng.mix(SEED_SALT, number.toLong())

    fun worldOf(number: Int): Int = (number - 1) / LEVELS_PER_WORLD

    fun isFinale(number: Int): Boolean = number == LEVEL_COUNT

    fun isBoss(number: Int): Boolean = number % LEVELS_PER_WORLD == 0 && !isFinale(number)

    /** Ostatni poziom zapoznawania z potworami – widmokuda jest przedstawiana na końcu. */
    const val INTRO_END = Stalkers.FIRST_LEVEL + Stalkers.INTRO_LEVELS - 1

    /** Postęp na pierwszym łatwym poziomie, zaraz po zapoznaniu z potworami. */
    private const val EASY_START = 0.15f

    /** O ile druga wyprawa (poziomy 501–999) podnosi trudność ponad poziom 500. */
    private const val SECOND_VOYAGE_BOOST = 0.3f

    /**
     * Postęp przez grę: w zapoznaniu z potworami rośnie od 0 do [EASY_START], dalej do 1 na poziomie 500
     * (łatwe, potem normalne poziomy), a w drugiej wyprawie aż do 1 + [SECOND_VOYAGE_BOOST] na poziomie 999.
     */
    private fun voyage(number: Int): Float = when {
        number <= INTRO_END -> EASY_START * (number - 1) / INTRO_END
        number <= FIRST_VOYAGE_LEVELS ->
            EASY_START + (1f - EASY_START) * (number - INTRO_END) / (FIRST_VOYAGE_LEVELS - INTRO_END)
        else -> 1f + SECOND_VOYAGE_BOOST * (number - FIRST_VOYAGE_LEVELS) / (LEVEL_COUNT - 1 - FIRST_VOYAGE_LEVELS).toFloat()
    }

    /** Trudność: 0..1 w pierwszych 500 poziomach, potem do ok. 1,25 – rośnie przez całą grę i lekko w obrębie świata. */
    fun difficulty(number: Int): Float {
        val global = voyage(number)
        val local = ((number - 1) % LEVELS_PER_WORLD) / (LEVELS_PER_WORLD - 1f)
        val e = global * 0.82f + local * 0.18f * (0.4f + 0.6f * min(global, 1f))
        return if (number <= FIRST_VOYAGE_LEVELS) e.coerceIn(0f, 1f) else e
    }

    private fun rawType(number: Int): LevelType {
        if (number <= 5) return LevelType.CLASSIC
        if (isFinale(number)) return LevelType.FINALE
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
        if (EndlessLevels.isEndless(number)) return EndlessLevels.plan(number).spec.type
        val type = rawType(number)
        if (type == LevelType.CLASSIC || type == LevelType.BOSS || type == LevelType.FINALE || number <= 5) return type
        // Unikamy dwóch takich samych „specjalnych” poziomów pod rząd.
        return if (rawType(number - 1) == type) LevelType.CLASSIC else type
    }

    fun plan(number: Int): LevelPlan {
        require(number in 1..LevelConstants.MAX_LEVEL) { "Nieprawidłowy numer poziomu: $number" }
        if (isFinale(number)) return finalePlan()
        if (EndlessLevels.isEndless(number)) return EndlessLevels.plan(number)
        val seed = seedOf(number)
        val rng = Rng(seed)
        val type = typeOf(number)
        val e = difficulty(number)
        val d = voyage(number)

        var stageCount = when {
            number == 1 -> 1
            number <= 5 -> 2
            number <= 15 -> rng.range(2, 3)
            else -> (3 + (d * 3.2f).toInt() + rng.range(-1, 1)).coerceIn(2, 6)
        }
        if (type == LevelType.BOSS) stageCount = min(stageCount + 1, 7)
        if (type == LevelType.TIME_ATTACK) stageCount = min(stageCount, 4)

        val unlocked = EnemyKind.rolled.filter { number >= it.firstLevel }
        val newest = unlocked.lastOrNull()
        val featured = if (newest != null && number - newest.firstLevel < EnemyKind.INTRO_LEVELS) newest else null

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
            comboTarget = min(MAX_COMBO, 3 + (e * 2.5f).toInt()),
            // W drugiej wyprawie potwory przyspieszają już wolniej, żeby łódź wciąż mogła im uciec.
            enemySpeed = 1f + 0.35f * min(e, 1f) + 0.15f * max(0f, e - 1f),
            currentX = currentX,
            currentY = currentY,
        )
        if (number < Stalkers.FIRST_LEVEL) return LevelPlan(spec, stages)
        return LevelPlan(spec, Stalkers.place(spec, stages, e, intro = number - Stalkers.FIRST_LEVEL < Stalkers.INTRO_LEVELS))
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
            // Para najgroźniejszych potworów, na jakie pozwala trudność – czarne rekiny od ok. 1/3 trudności.
            val strongest = unlocked.filter { it.cost <= 2f + e * 3f }.maxBy { it.cost }
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

    fun generate(number: Int): LevelLayout = generate(plan(number))

    /**
     * Osobny poziom samouczka (spoza 1000 poziomów, bez wpływu na wyniki):
     * etap 1 uczy sterowania i łowienia, etap 2 – omijania potworów i złotej ryby.
     */
    fun tutorialPlan(): LevelPlan {
        val base = plan(1).spec
        val spec = LevelSpec(
            number = 1,
            world = 0,
            type = LevelType.CLASSIC,
            seed = Rng.mix(SEED_SALT, -1L),
            secondGoal = SecondGoal.ALL_FISH,
            thirdGoal = ThirdGoal.PAR_TIME,
            parSeconds = base.parSeconds * 2,
            timeLimitSeconds = 0,
            scoreTarget = base.scoreTarget,
            comboTarget = 3,
            // Pirania w samouczku jest wolniejsza niż na zwykłych poziomach.
            enemySpeed = 0.75f,
            currentX = 0f,
            currentY = 0f,
        )
        val center = WIDTH / 2
        return LevelPlan(
            spec,
            listOf(
                StagePlan(rows = 12, quota = TUTORIAL_QUOTA, common = 3, blue = 1, golden = 0, enemies = emptyList(), rocks = 1, islands = 0, doorCenter = center, wideBanks = false),
                StagePlan(rows = 15, quota = TUTORIAL_QUOTA, common = 4, blue = 0, golden = 1, enemies = listOf(EnemyKind.PIRANHA), rocks = 1, islands = 0, doorCenter = center, wideBanks = false),
            ),
        )
    }

    /** Ile ryb trzeba złowić w każdym etapie samouczka. */
    const val TUTORIAL_QUOTA = 3

    /**
     * Poziom 1000 – Wielki Finał, ułożony ręcznie. Siedem etapów, każdy w barwach innego
     * świata ([Worlds.FINALE_STAGE_WORLDS]) i z własnym zestawem potworów: od cieśniny piranii,
     * przez rafę, archipelag, kanał i gorączkę złota, po czerwony legion i leże Krakena.
     * Druga gwiazdka – bez trafienia, trzecia – wszystkie złote ryby.
     */
    fun finalePlan(): LevelPlan {
        val p = EnemyKind.PIRANHA
        val pv = EnemyKind.PIRANHA_VERTICAL
        val o = EnemyKind.OCTOPUS
        val s = EnemyKind.SHARK
        val rp = EnemyKind.RED_PIRANHA
        val ro = EnemyKind.RED_OCTOPUS
        val ds = EnemyKind.DARK_SHARK
        fun stage(
            rows: Int, quota: Int, blue: Int, golden: Int, enemies: List<EnemyKind>,
            rocks: Int, islands: Int, door: Int, canal: Boolean = false,
        ) = StagePlan(
            rows = rows, quota = quota, common = quota + max(1, quota / 3) - blue, blue = blue, golden = golden,
            enemies = enemies, rocks = rocks, islands = islands, doorCenter = door, wideBanks = canal,
        )
        val stages = listOf(
            stage(20, 9, 2, 1, listOf(p, p, p, pv, pv, pv, rp, rp), rocks = 2, islands = 1, door = 7),
            stage(22, 10, 3, 1, listOf(o, o, o, ro, ro, p, p), rocks = 7, islands = 0, door = 3),
            stage(24, 10, 3, 1, listOf(s, s, s, pv, pv, o, o), rocks = 2, islands = 4, door = 6),
            stage(24, 10, 2, 1, listOf(rp, rp, rp, rp, s, s), rocks = 1, islands = 0, door = 5, canal = true),
            stage(22, 9, 3, 3, listOf(p, p, o, s), rocks = 2, islands = 1, door = 2),
            stage(24, 11, 3, 1, listOf(rp, rp, rp, ro, ro, ro, ds, ds), rocks = 4, islands = 2, door = 8),
            stage(24, 12, 4, 2, listOf(ds, ds, ds, ro, ro, rp, rp, s, s, pv), rocks = 3, islands = 2, door = 5),
        )
        check(stages.size == Worlds.FINALE_STAGE_WORLDS.size)
        val par = ceil(stages.sumOf { it.quota * 2.6 + it.rows * 0.32 + it.enemies.size * 0.6 } + 3.0).toInt()
        val regularPoints = stages.sumOf { it.common * FishKind.COMMON.points + it.blue * FishKind.BLUE.points }
        val goldenTotal = stages.sumOf { it.golden }
        val spec = LevelSpec(
            number = LEVEL_COUNT,
            world = worldOf(LEVEL_COUNT),
            type = LevelType.FINALE,
            seed = seedOf(LEVEL_COUNT),
            secondGoal = SecondGoal.NO_DAMAGE,
            thirdGoal = ThirdGoal.ALL_GOLDEN,
            parSeconds = par,
            timeLimitSeconds = 0,
            scoreTarget = ((regularPoints * 1.6f + goldenTotal * FishKind.GOLDEN.points * 0.5f +
                stages.size * LevelConstants.STAGE_BONUS) / 10f).roundToInt() * 10,
            comboTarget = MAX_COMBO,
            enemySpeed = 1.35f,
            currentX = 0f,
            currentY = 0f,
        )
        return LevelPlan(spec, Stalkers.place(spec, stages, difficulty = 1.3f, intro = false))
    }

    fun generate(plan: LevelPlan): LevelLayout {
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
        fillUnreachableWater(grid, startCol, bottomRow - 1)

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
            val enemies = stage.enemySlots.mapNotNull { kind ->
                val cell = takeCell(enemyCells, spawnRng, entries[k], entryRow, minDistance = 7)
                kind?.let { EnemySpawn(it, cell % WIDTH, cell / WIDTH) }
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

    /**
     * Woda, do której łódź nie ma jak wpłynąć (kieszeń między wyspą a brzegiem),
     * wygląda jak błąd – zasypujemy ją piaskiem. Zasypanie może zostawić piasek
     * bez pasującego sprite'a, który [fixSand] zmieni z powrotem w wodę, więc
     * powtarzamy do skutku; gdyby to nie zbiegło, resztę kieszeni zajmują skały.
     */
    fun fillUnreachableWater(grid: TileGrid, startCol: Int, startRow: Int) {
        repeat(8) {
            val reach = reachableFromStart(grid, startCol, startRow)
            var filled = false
            for (r in 1 until grid.height - 1) for (c in 0 until grid.width) {
                if (grid[c, r] == Tile.SEA && !reach[r * grid.width + c]) {
                    grid[c, r] = Tile.SAND
                    filled = true
                }
            }
            if (!filled) return
            fixSand(grid)
        }
        val reach = reachableFromStart(grid, startCol, startRow)
        for (r in 1 until grid.height - 1) for (c in 0 until grid.width) {
            if (grid[c, r] == Tile.SEA && !reach[r * grid.width + c]) grid[c, r] = Tile.ROCK
        }
    }

    /** Pola morza osiągalne łodzią ze startu przez wszystkie (otwierane) wrota. */
    fun reachableFromStart(grid: TileGrid, startCol: Int, startRow: Int): BooleanArray {
        val seen = BooleanArray(grid.width * grid.height)
        val queue = ArrayDeque<Int>()
        fun passable(t: Tile) = t == Tile.SEA || t == Tile.DOOR
        if (passable(grid[startCol, startRow])) {
            seen[startRow * grid.width + startCol] = true
            queue += startRow * grid.width + startCol
        }
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            val c = cell % grid.width
            val r = cell / grid.width
            for ((dc, dr) in NEIGHBOURS) {
                val nc = c + dc
                val nr = r + dr
                if (!grid.inside(nc, nr)) continue
                val idx = nr * grid.width + nc
                if (!seen[idx] && passable(grid[nc, nr])) {
                    seen[idx] = true
                    queue += idx
                }
            }
        }
        return seen
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
