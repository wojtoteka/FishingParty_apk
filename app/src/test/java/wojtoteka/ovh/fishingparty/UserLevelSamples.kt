package wojtoteka.ovh.fishingparty

import wojtoteka.ovh.fishingparty.core.Rng
import wojtoteka.ovh.fishingparty.level.EnemyKind
import wojtoteka.ovh.fishingparty.level.LevelConstants.WIDTH
import wojtoteka.ovh.fishingparty.level.SecondGoal
import wojtoteka.ovh.fishingparty.level.ThirdGoal
import wojtoteka.ovh.fishingparty.level.UserCrab
import wojtoteka.ovh.fishingparty.level.UserEnemy
import wojtoteka.ovh.fishingparty.level.UserFish
import wojtoteka.ovh.fishingparty.level.UserGolden
import wojtoteka.ovh.fishingparty.level.UserLevel
import wojtoteka.ovh.fishingparty.level.UserLevels
import wojtoteka.ovh.fishingparty.level.UserStage

/** Losowe poziomy „z kreatora” do testów (jak ułożyłby je gracz: brzegi, wyspy 2×2, skały, postacie). */
object UserLevelSamples {

    fun random(seed: Long): UserLevel {
        val rng = Rng(seed)
        val stages = List(rng.range(1, UserLevels.MAX_STAGES)) { stage(rng) }
        val level = UserLevel(
            title = "Próbka $seed",
            theme = rng.range(-1, 9),
            speed = rng.range(6, 16) * 10,
            time = if (rng.chance(0.25f)) rng.range(UserLevels.MIN_TIME, 300) else 0,
            secondGoal = rng.pick(SecondGoal.entries),
            thirdGoal = rng.pick(ThirdGoal.entries),
            combo = rng.range(2, 5),
            stages = stages,
        )
        val normal = UserLevels.normalize(level)
        // Wymagana liczba ryb po poprawkach (część ryb mogła wylądować na piasku).
        return normal.copy(stages = normal.stages.map { it.copy(quota = it.quota.coerceAtMost(maxOf(1, it.fish.size))) })
    }

    private fun stage(rng: Rng): UserStage {
        val rows = rng.range(UserLevels.MIN_ROWS, UserLevels.MAX_ROWS)
        val cells = ByteArray(rows * WIDTH) { UserLevels.SEA }
        fun sand(c: Int, r: Int) {
            if (c in 0 until WIDTH && r in 0 until rows) cells[r * WIDTH + c] = UserLevels.SAND
        }
        val bank = rng.range(0, 3)
        for (r in 0 until rows) for (c in 0 until WIDTH) if (c < bank || c >= WIDTH - bank) sand(c, r)
        repeat(rng.range(0, 6)) {
            val c = rng.range(1, WIDTH - 3)
            val r = rng.range(0, rows - 2)
            sand(c, r); sand(c + 1, r); sand(c, r + 1); sand(c + 1, r + 1)
        }
        repeat(rng.range(0, 8)) {
            val i = rng.nextInt(cells.size)
            if (cells[i] == UserLevels.SEA) cells[i] = UserLevels.ROCK
        }
        fun cell() = rng.range(0, WIDTH - 1) to rng.range(0, rows - 1)
        val fish = List(rng.range(1, 15)) { cell().let { (c, r) -> UserFish(rng.chance(0.3f), c, r) } }
        val golden = List(rng.range(0, 3)) { cell().let { (c, r) -> UserGolden(rng.range(0, 20), c, r) } }
        val enemies = List(rng.range(0, 8)) { cell().let { (c, r) -> UserEnemy(rng.pick(EnemyKind.entries), c, r, rng.range(-1, 3)) } }
        val crabs = List(rng.range(0, 3)) { cell().let { (c, r) -> UserCrab(c, r) } }
        val current = listOf(0, 0, 0, 0, 10, -10, 20, -20, 30)
        return UserStage(
            rows = rows,
            door = rng.range(1, WIDTH - 2),
            quota = rng.range(1, 8),
            theme = if (rng.chance(0.3f)) rng.range(-1, 9) else UserLevels.SAME_THEME,
            currentX = if (rng.chance(0.2f)) rng.pick(current) else 0,
            currentY = if (rng.chance(0.1f)) rng.pick(current) else 0,
            cells = cells,
            fish = fish,
            golden = golden,
            enemies = enemies,
            crabs = crabs,
        )
    }
}
