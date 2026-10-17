package wojtoteka.ovh.fishingparty.level

import wojtoteka.ovh.fishingparty.core.Rng
import kotlin.math.min

/**
 * Widmokuda w planach poziomów. Dokładana dopiero po policzeniu czasu wzorcowego, celów
 * i limitów wyniku, więc nie zmienia niczego, co sprawdza serwer rankingu (ryby, etapy,
 * czas wzorcowy, maksymalny wynik) – serwer nie musi o niej wiedzieć.
 *
 * Zamiast dorzucać kolejne potwory zastępuje kilka słabszych: etap ma mniej potworów,
 * ale jest trudniejszy. Etap z trzema lub więcej rekinami zostaje z dwoma najgroźniejszymi
 * i widmokudą, a na poziomach 20–23 (jak każdy nowy potwór) pojawia się na pewno – jedna,
 * w drugim etapie, żeby gracz mógł ją poznać.
 */
object Stalkers {
    const val FIRST_LEVEL = 20

    /** Na tylu poziomach od [FIRST_LEVEL] widmokuda jest przedstawiana graczowi. */
    const val INTRO_LEVELS = 4

    private const val SALT = 0x57_49_44_4D_4FL

    fun isIntro(number: Int): Boolean = number in FIRST_LEVEL until FIRST_LEVEL + INTRO_LEVELS

    fun place(spec: LevelSpec, stages: List<StagePlan>, difficulty: Float, intro: Boolean): List<StagePlan> {
        // Ławica to same piranie – tam widmokudy nie ma.
        if (spec.type == LevelType.SWARM) return stages
        val rng = Rng(Rng.mix(spec.seed, SALT))
        val introStage = min(1, stages.lastIndex)
        val chance = chance(spec.type, difficulty)
        return stages.mapIndexed { index, stage ->
            val slots = stage.enemySlots.toMutableList()
            val sharks = slots.withIndex().filter { it.value?.isShark == true }
            when {
                sharks.size >= 3 -> {
                    val extra = sharks.sortedByDescending { it.value!!.cost }.drop(2).map { it.index }
                    extra.forEach { slots[it] = null }
                    slots[extra.min()] = EnemyKind.STALKER
                }
                intro -> if (index == introStage && slots.any { it != null }) {
                    slots[slots.indices.filter { slots[it] != null }.maxBy { slots[it]!!.cost }] = EnemyKind.STALKER
                }
                slots.count { it != null } >= 2 && rng.chance(chance) -> replaceCheapest(slots)
            }
            // Na najtrudniejszych etapach (druga wyprawa, Bezkresne Morze, poziom tygodnia) mogą być dwie.
            if (!intro && difficulty >= 1f && slots.count { it != null } >= 6 && slots.count { it == EnemyKind.STALKER } < 2 && rng.chance(0.3f)) {
                replaceCheapest(slots)
            }
            // Boss zawsze czeka z widmokudą w ostatnim etapie.
            if (spec.type == LevelType.BOSS && index == stages.lastIndex && EnemyKind.STALKER !in slots && slots.count { it != null } >= 2) {
                replaceCheapest(slots)
            }
            if (slots == stage.enemySlots) stage else stage.withSlots(slots)
        }
    }

    /** Szansa na widmokudę w etapie: od ok. 15% na początku gry do ok. 50% pod koniec drugiej wyprawy. */
    private fun chance(type: LevelType, difficulty: Float): Float {
        var p = 0.12f + 0.3f * min(difficulty, 1.3f)
        if (type == LevelType.BOSS) p += 0.2f
        if (type == LevelType.GOLD_RUSH || type == LevelType.TIME_ATTACK) p *= 0.6f
        return p
    }

    /** Dwa najsłabsze potwory etapu ustępują miejsca jednej widmokudzie (zajmuje miejsce pierwszego z nich). */
    private fun replaceCheapest(slots: MutableList<EnemyKind?>) {
        val cheapest = slots.indices.filter { slots[it] != null && slots[it] != EnemyKind.STALKER }
            .sortedBy { slots[it]!!.cost }.take(2)
        cheapest.forEach { slots[it] = null }
        slots[cheapest.min()] = EnemyKind.STALKER
    }

    private val EnemyKind.isShark: Boolean get() = this == EnemyKind.SHARK || this == EnemyKind.DARK_SHARK

    private fun StagePlan.withSlots(slots: List<EnemyKind?>) = StagePlan(
        rows = rows, quota = quota, common = common, blue = blue, golden = golden, enemies = slots.filterNotNull(),
        rocks = rocks, islands = islands, doorCenter = doorCenter, wideBanks = wideBanks, enemySlots = slots,
    )
}
