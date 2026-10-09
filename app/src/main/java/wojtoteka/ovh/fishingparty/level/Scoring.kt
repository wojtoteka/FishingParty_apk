package wojtoteka.ovh.fishingparty.level

import kotlin.math.max

/** Wynik pojedynczego podejścia do poziomu. */
class RunResult(
    val level: Int,
    val completed: Boolean,
    val score: Int,
    val stars: Int,
    val starFlags: BooleanArray,
    val timeMillis: Long,
    val commonCaught: Int,
    val blueCaught: Int,
    val goldenCaught: Int,
    val hits: Int,
    val maxCombo: Int,
    val stagesCleared: Int,
) {
    val fishCaught: Int get() = commonCaught + blueCaught + goldenCaught
}

object Scoring {

    /** Maksymalny odstęp między złowionymi rybami, który podtrzymuje combo. */
    const val COMBO_WINDOW_SECONDS = 1.6f

    fun fishPoints(kind: FishKind, combo: Int): Int = kind.points * combo.coerceIn(1, LevelConstants.MAX_COMBO)

    fun timeBonus(spec: LevelSpec, seconds: Float): Int =
        max(0, spec.parSeconds - seconds.toInt()) * LevelConstants.TIME_BONUS_PER_SECOND

    fun stars(
        layout: LevelLayout,
        completed: Boolean,
        score: Int,
        seconds: Float,
        hits: Int,
        regularCaught: Int,
        goldenCaught: Int,
        maxCombo: Int,
    ): BooleanArray {
        val spec = layout.spec
        if (!completed) return BooleanArray(3)
        val second = when (spec.secondGoal) {
            SecondGoal.NO_DAMAGE -> hits == 0
            SecondGoal.ALL_FISH -> regularCaught >= layout.fishCount
        }
        val third = when (spec.thirdGoal) {
            ThirdGoal.PAR_TIME -> seconds <= spec.parSeconds
            ThirdGoal.SCORE -> score >= spec.scoreTarget
            ThirdGoal.ALL_GOLDEN -> goldenCaught >= layout.goldenCount
            ThirdGoal.COMBO -> maxCombo >= spec.comboTarget
        }
        return booleanArrayOf(true, second, third)
    }

    fun coinsFor(
        common: Int,
        blue: Int,
        golden: Int,
        completed: Boolean,
        world: Int,
        newStars: Int,
    ): Int {
        var coins = common * FishKind.COMMON.coins + blue * FishKind.BLUE.coins + golden * FishKind.GOLDEN.coins
        if (completed) coins += 10 + world * 2 + newStars * 10
        return coins
    }
}

/**
 * Sprawdza, czy wynik jest w ogóle możliwy do uzyskania na danym poziomie.
 * Ten sam kod (ta sama deterministyczna generacja poziomów) może później
 * działać po stronie serwera rankingu.
 */
object ScoreValidator {

    /** Najszybsza możliwa łódź (turbo + prąd) w jednostkach świata na sekundę. */
    private const val FASTEST_SHIP = 72f * 1.2f + 30f

    fun minimumSeconds(plan: LevelPlan): Float {
        val distance = plan.stages.sumOf { it.rows + 1 } * LevelConstants.TILE
        return distance / FASTEST_SHIP * 0.5f
    }

    fun isPlausible(plan: LevelPlan, result: RunResult): Boolean {
        if (result.level != plan.spec.number) return false
        if (result.score < 0 || result.score > LevelGenerator.maxScore(plan)) return false
        if (result.maxCombo !in 0..LevelConstants.MAX_COMBO) return false
        if (result.stars !in 0..3) return false
        if (result.stagesCleared !in 0..plan.stages.size) return false
        val common = plan.stages.sumOf { it.common }
        val blue = plan.stages.sumOf { it.blue }
        if (result.commonCaught !in 0..common) return false
        if (result.blueCaught !in 0..blue) return false
        if (result.goldenCaught !in 0..plan.goldenCount) return false
        if (result.completed && result.timeMillis < minimumSeconds(plan) * 1000f) return false
        val fishPointsCap = result.commonCaught * FishKind.COMMON.points +
            result.blueCaught * FishKind.BLUE.points +
            result.goldenCaught * FishKind.GOLDEN.points
        val cap = fishPointsCap * LevelConstants.MAX_COMBO +
            result.stagesCleared * LevelConstants.STAGE_BONUS +
            (if (result.completed) plan.spec.parSeconds * LevelConstants.TIME_BONUS_PER_SECOND + LevelConstants.NO_DAMAGE_BONUS else 0)
        return result.score <= cap
    }
}
