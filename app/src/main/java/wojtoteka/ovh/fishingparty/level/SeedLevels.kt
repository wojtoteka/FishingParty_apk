package wojtoteka.ovh.fishingparty.level

import wojtoteka.ovh.fishingparty.core.Rng
import wojtoteka.ovh.fishingparty.level.LevelConstants.LEVELS_PER_WORLD
import wojtoteka.ovh.fishingparty.level.LevelConstants.LEVEL_COUNT
import wojtoteka.ovh.fishingparty.level.LevelConstants.MAX_COMBO
import wojtoteka.ovh.fishingparty.level.LevelConstants.WIDTH
import kotlin.math.max
import kotlin.math.min

/**
 * Ustawienia generatora z seeda – „reguły świata”, które pilnują, żeby losowy
 * poziom był grywalny i miał zadaną trudność (jak ustawienia generowania świata).
 * Trudność podawana jest w promilach: 0 to poziom 1, 1000 to poziom 500,
 * a poziom 999 ma ok. 1300.
 */
class SeedProfile(
    val minDifficulty: Int,
    val maxDifficulty: Int,
    val minStages: Int,
    val maxStages: Int,
) {
    init {
        require(minDifficulty in 0..maxDifficulty && minStages in 2..maxStages && maxStages <= 6)
    }
}

/**
 * Poziom z seeda. Ten sam seed i te same ustawienia zawsze dają ten sam plan,
 * a z planu [LevelGenerator.generate] buduje planszę.
 *
 * Wszystkie liczby, od których zależy maksymalny wynik (etapy, wiersze, ryby,
 * potwory, czas wzorcowy), liczone są wyłącznie na liczbach całkowitych, żeby
 * serwer rankingu mógł powtórzyć je co do joty we własnym kodzie. Kolejność
 * losowań jest częścią formatu – jej zmiana zmienia wszystkie poziomy z seeda.
 */
object SeedLevels {

    private val PIRANHAS = listOf(EnemyKind.PIRANHA, EnemyKind.PIRANHA_VERTICAL, EnemyKind.RED_PIRANHA)

    /** Typ poziomu wylosowany z samego seeda (bez poprawki na poprzedni poziom). */
    fun rawType(seed: LevelSeed): LevelType {
        val rng = Rng(Rng.mix(seed.value, 7))
        val options = listOf(
            LevelType.CLASSIC to 26,
            LevelType.ROCKY to 12,
            LevelType.ARCHIPELAGO to 12,
            LevelType.CANAL to 10,
            LevelType.GOLD_RUSH to 8,
            LevelType.CURRENT to 10,
            LevelType.TIME_ATTACK to 8,
            LevelType.SWARM to 10,
        )
        var roll = rng.nextInt(options.sumOf { it.second })
        for ((type, weight) in options) {
            if (roll < weight) return type
            roll -= weight
        }
        return LevelType.CLASSIC
    }

    /**
     * @param number numer poziomu (do wyświetlania i zapisu wyników)
     * @param world świat – decyduje o palecie i nagrodzie w monetach
     * @param position miejsce poziomu w świecie, 0..1000 – trudność lekko rośnie pod koniec świata
     * @param boss poziom z bossem (ostatni w świecie)
     * @param previous seed poprzedniego poziomu – dwa takie same specjalne poziomy pod rząd zamieniamy na klasyczny
     */
    fun plan(seed: LevelSeed, profile: SeedProfile, number: Int, world: Int, position: Int, boss: Boolean, previous: LevelSeed?): LevelPlan {
        val type = when {
            boss -> LevelType.BOSS
            else -> rawType(seed).let { t ->
                if (t != LevelType.CLASSIC && previous != null && rawType(previous) == t) LevelType.CLASSIC else t
            }
        }
        val rng = Rng(seed.value)
        val span = profile.maxDifficulty - profile.minDifficulty
        var e = profile.minDifficulty + span * position.coerceIn(0, 1000) / 2000 + rng.nextInt(span / 2 + 1)
        if (boss) e += BOSS_EXTRA

        var stageCount = (3 + e * 32 / 10000 + rng.range(-1, 1)).coerceIn(profile.minStages, profile.maxStages)
        if (type == LevelType.BOSS) stageCount = min(stageCount + 1, 7)
        if (type == LevelType.TIME_ATTACK) stageCount = min(stageCount, 4)

        val stages = (0 until stageCount).map {
            val rows = min(24, 11 + rng.range(0, 4) + e * 8 / 1000)
            var quota = 4 + e * 6 / 1000 + rng.range(0, 2)
            if (type == LevelType.SWARM) quota = max(3, quota - 2)
            val total = quota + max(1, quota / 3)
            val blue = min(total / 2, rng.range(0, 1 + e * 3 / 1000))
            val golden = if (type == LevelType.GOLD_RUSH) {
                rng.range(2, 3)
            } else {
                (if (rng.nextInt(1000) < 350 + e * 300 / 1000) 1 else 0) + (if (e > 500 && rng.nextInt(1000) < 250) 1 else 0)
            }
            val enemies = planEnemies(rng, type, e)
            var rocks = rng.range(0, 2) + e * 3 / 1000
            var islands = rng.range(0, 1) + (if (e > 300) 1 else 0)
            when (type) {
                LevelType.ROCKY -> rocks += 5
                LevelType.ARCHIPELAGO -> islands += 3
                LevelType.CANAL -> {
                    rocks = min(rocks, 1); islands = 0
                }
                else -> Unit
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
                doorCenter = rng.range(2, WIDTH - 3),
                wideBanks = type == LevelType.CANAL,
            )
        }

        val par = parSeconds(stages)
        val timeLimit = if (type == LevelType.TIME_ATTACK) ceilDiv(par * 135, 100) else 0

        var currentX = 0f
        var currentY = 0f
        if (type == LevelType.CURRENT) {
            val strength = 10f + e * 14f / 1000f
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
            rng.nextInt(1000) < 600 -> SecondGoal.NO_DAMAGE
            else -> SecondGoal.ALL_FISH
        }
        val thirdGoal = when (type) {
            LevelType.TIME_ATTACK -> ThirdGoal.PAR_TIME
            LevelType.GOLD_RUSH -> ThirdGoal.ALL_GOLDEN
            else -> {
                val roll = rng.nextInt(100)
                when {
                    roll < 40 -> ThirdGoal.PAR_TIME
                    roll < 65 -> ThirdGoal.SCORE
                    roll < 85 -> ThirdGoal.COMBO
                    goldenTotal > 0 -> ThirdGoal.ALL_GOLDEN
                    else -> ThirdGoal.PAR_TIME
                }
            }
        }
        val regularPoints = stages.sumOf { it.common * FishKind.COMMON.points + it.blue * FishKind.BLUE.points }
        // Ten sam cel punktowy co w poziomach 1–999: (zwykłe × 1,6 + złote × 50 + etapy × 50), zaokrąglony do dziesiątek.
        val scoreTenfold = regularPoints * 16 + goldenTotal * FishKind.GOLDEN.points * 5 + stageCount * LevelConstants.STAGE_BONUS * 10
        val ef = e / 1000f

        val spec = LevelSpec(
            number = number,
            world = world,
            type = type,
            seed = seed.value,
            secondGoal = secondGoal,
            thirdGoal = thirdGoal,
            parSeconds = par,
            timeLimitSeconds = timeLimit,
            scoreTarget = (scoreTenfold + 50) / 100 * 10,
            comboTarget = min(MAX_COMBO, 3 + e * 25 / 10000),
            enemySpeed = 1f + 0.35f * min(ef, 1f) + 0.15f * max(0f, ef - 1f),
            currentX = currentX,
            currentY = currentY,
        )
        return LevelPlan(spec, stages)
    }

    /** Czas wzorcowy jak w poziomach 1–999 (ilość ryb × 2,6 s + wiersze × 0,32 s + potwory × 0,6 s + 3 s), w setnych sekundy. */
    fun parSeconds(stages: List<StagePlan>): Int =
        ceilDiv(stages.sumOf { it.quota * 260 + it.rows * 32 + it.enemies.size * 60 } + 300, 100)

    private fun planEnemies(rng: Rng, type: LevelType, e: Int): List<EnemyKind> {
        val all = EnemyKind.entries
        val result = mutableListOf<EnemyKind>()
        if (type == LevelType.SWARM) {
            repeat(3 + e * 5 / 1000) { result += rng.pick(PIRANHAS) }
            return result
        }
        // Budżet trudności w dziesiątych częściach kosztu potwora.
        var budget = 10 + e * 65 / 1000 + rng.nextInt(10)
        if (type == LevelType.GOLD_RUSH) budget = budget * 6 / 10
        if (type == LevelType.TIME_ATTACK) budget = budget * 8 / 10
        if (type == LevelType.BOSS) {
            result += EnemyKind.DARK_SHARK
            result += EnemyKind.DARK_SHARK
            budget += 10
        }
        val maxCount = min(10, 3 + e * 7 / 1000 + if (type == LevelType.BOSS) 2 else 0)
        while (budget >= CHEAPEST && result.size < maxCount) {
            var kind = rng.pick(all)
            if (kind.costTenths > budget + 5) {
                val affordable = all.filter { it.costTenths <= budget + 5 }
                if (affordable.isEmpty()) break
                kind = rng.pick(affordable)
            }
            result += kind
            budget -= kind.costTenths
        }
        return result
    }

    private const val CHEAPEST = 10
    private const val BOSS_EXTRA = 80

    private fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b
}

/**
 * Bezkresne Morze – poziomy powyżej 1000. Każdy ma własny seed wynikający z numeru,
 * więc żaden się nie powtarza, a wszyscy gracze dostają te same plansze (ranking
 * pozostaje uczciwy). Trudność skacze losowo między „druga połowa pierwszej wyprawy”
 * a „początek drugiej” – wyraźnie trudniej niż na starcie gry, ale lżej niż Wielki Finał.
 */
object EndlessLevels {
    val PROFILE = SeedProfile(minDifficulty = 620, maxDifficulty = 1080, minStages = 3, maxStages = 6)

    fun isEndless(number: Int): Boolean = number > LEVEL_COUNT

    fun seedOf(number: Int): LevelSeed = LevelSeed.endless(number)

    /** Numer świata Bezkresnego Morza liczony od zera (poziomy 1001–1025 to świat 0). */
    fun endlessWorld(number: Int): Int = (number - LEVEL_COUNT - 1) / LEVELS_PER_WORLD

    fun plan(number: Int): LevelPlan {
        require(isEndless(number) && number <= LevelConstants.MAX_LEVEL) { "To nie jest poziom Bezkresnego Morza: $number" }
        val k = (number - LEVEL_COUNT - 1) % LEVELS_PER_WORLD
        return SeedLevels.plan(
            seed = seedOf(number),
            profile = PROFILE,
            number = number,
            world = LevelGenerator.worldOf(number),
            position = k * 1000 / (LEVELS_PER_WORLD - 1),
            boss = k == LEVELS_PER_WORLD - 1,
            previous = if (number > LEVEL_COUNT + 1) seedOf(number - 1) else null,
        )
    }
}
