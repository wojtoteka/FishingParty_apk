package wojtoteka.ovh.fishingparty.level

import wojtoteka.ovh.fishingparty.core.Rng
import wojtoteka.ovh.fishingparty.level.LevelConstants.WIDTH
import java.security.SecureRandom
import kotlin.math.roundToInt

/**
 * Start w kreatorze od gotowego poziomu: gracz wpisuje kod seeda (albo losuje nowy) i wybiera trudność,
 * a gra buduje poziom tym samym generatorem co Bezkresne Morze ([SeedLevels], [LevelGenerator.generate])
 * i zamienia go na poziom z kreatora – z brzegami, wyspami, rybami i potworami do dalszej edycji.
 * Ten sam seed z tą samą trudnością daje zawsze ten sam poziom.
 */
object UserLevelSeeds {

    /** Trudność poziomu z seeda: „reguły świata” generatora (jak poziom dnia i tygodnia). */
    enum class Difficulty(val profile: SeedProfile, val position: Int) {
        EASY(SeedProfile(minDifficulty = 60, maxDifficulty = 380, minStages = 2, maxStages = 3), 300),
        NORMAL(ChallengeKind.DAILY.profile, ChallengeKind.DAILY.position),
        HARD(ChallengeKind.WEEKLY.profile, ChallengeKind.WEEKLY.position),
    }

    private const val THEME_SALT = 0x55_53_45_45_44L

    fun random(): LevelSeed = LevelSeed(SecureRandom().nextLong())

    fun build(seed: LevelSeed, difficulty: Difficulty): UserLevel {
        val plan = SeedLevels.plan(
            seed = seed,
            profile = difficulty.profile,
            number = UserLevels.LEVEL_NUMBER,
            world = 0,
            position = difficulty.position,
            boss = false,
            previous = null,
        )
        val layout = LevelGenerator.generate(plan)
        val spec = plan.spec
        val stages = layout.stages.map { s ->
            val top = s.topRow
            val rows = s.bottomRow - s.topRow + 1
            val cells = ByteArray(rows * WIDTH) { i ->
                when (layout.grid[i % WIDTH, top + i / WIDTH]) {
                    Tile.SAND -> UserLevels.SAND
                    Tile.ROCK -> UserLevels.ROCK
                    else -> UserLevels.SEA
                }
            }
            UserStage(
                rows = rows.coerceIn(UserLevels.MIN_ROWS, UserLevels.MAX_ROWS),
                door = s.doorCols.first + 1,
                quota = s.quota,
                currentX = spec.currentX.roundToInt().coerceIn(-UserLevels.MAX_CURRENT, UserLevels.MAX_CURRENT),
                currentY = spec.currentY.roundToInt().coerceIn(-UserLevels.MAX_CURRENT, UserLevels.MAX_CURRENT),
                cells = cells,
                fish = s.fish.map { UserFish(it.kind == FishKind.BLUE, it.col, it.row - top) },
                golden = s.golden.map { UserGolden(it.delaySeconds.toInt().coerceIn(0, UserLevels.MAX_DELAY), it.col, it.row - top) },
                enemies = s.enemies.map { UserEnemy(it.kind, it.col, it.row - top) },
                crabs = layout.crabs.filter { (_, r) -> r in top..s.bottomRow }.map { (c, r) -> UserCrab(c, r - top) },
            )
        }
        val time = spec.timeLimitSeconds
        val level = UserLevel(
            title = "Seed ${seed.code}",
            theme = Math.floorMod(Rng.mix(seed.value, THEME_SALT), Worlds.THEMES.size.toLong()).toInt(),
            speed = ((spec.enemySpeed * 10f).roundToInt() * 10).coerceIn(UserLevels.MIN_SPEED, UserLevels.MAX_SPEED),
            time = if (time > 0) time.coerceIn(UserLevels.MIN_TIME, UserLevels.MAX_TIME) else 0,
            secondGoal = spec.secondGoal,
            thirdGoal = spec.thirdGoal,
            combo = spec.comboTarget.coerceIn(2, LevelConstants.MAX_COMBO),
            stages = stages,
        )
        return UserLevels.normalize(level)
    }
}
