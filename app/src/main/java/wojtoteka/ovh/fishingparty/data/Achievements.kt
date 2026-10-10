package wojtoteka.ovh.fishingparty.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class Metric { FISH, GOLDEN, LEVELS, STARS, PERFECT, NO_HIT, COMBO, BOSS, PURCHASES, COSMETICS, BOATS, DAILY_STREAK, STREAK, SPEED, COINS_HELD, VETERAN }

/**
 * @param icon ścieżka spritu względem assets/sprites (np. "fishgold1", "boats/golden/ship")
 */
class Achievement(val id: String, val metric: Metric, val target: Long, val reward: Int, val icon: String, val hidden: Boolean = false)

object Achievements {
    val all = listOf(
        Achievement("first_fish", Metric.FISH, 1, 10, "fish1"),
        Achievement("fish_100", Metric.FISH, 100, 50, "fish1"),
        Achievement("fish_1000", Metric.FISH, 1_000, 200, "fish2"),
        Achievement("fish_5000", Metric.FISH, 5_000, 500, "fishblue1"),
        Achievement("fish_20000", Metric.FISH, 20_000, 1500, "fishblue2"),
        Achievement("golden_1", Metric.GOLDEN, 1, 30, "fishgold1"),
        Achievement("golden_25", Metric.GOLDEN, 25, 150, "fishgold2"),
        Achievement("golden_100", Metric.GOLDEN, 100, 500, "fishgold1"),
        Achievement("level_5", Metric.LEVELS, 5, 30, "boats/classic/ship"),
        Achievement("level_25", Metric.LEVELS, 25, 100, "boats/classic/ship"),
        Achievement("level_50", Metric.LEVELS, 50, 200, "boats/crimson/ship"),
        Achievement("level_100", Metric.LEVELS, 100, 400, "boats/navy/ship"),
        Achievement("level_200", Metric.LEVELS, 200, 800, "boats/emerald/ship"),
        Achievement("level_350", Metric.LEVELS, 350, 1500, "boats/royal/ship"),
        Achievement("level_500", Metric.LEVELS, 500, 3000, "boats/golden/ship"),
        Achievement("level_750", Metric.LEVELS, 750, 4500, "boats/shadow/ship"),
        Achievement("level_1000", Metric.LEVELS, 1000, 10000, "fishgold1"),
        Achievement("stars_15", Metric.STARS, 15, 50, "fishscore_gold"),
        Achievement("stars_75", Metric.STARS, 75, 150, "fishscore_gold"),
        Achievement("stars_300", Metric.STARS, 300, 500, "fishscore_gold"),
        Achievement("stars_750", Metric.STARS, 750, 1000, "fishscore_gold"),
        Achievement("stars_1500", Metric.STARS, 1500, 3000, "fishscore_gold"),
        Achievement("stars_3000", Metric.STARS, 3000, 6000, "fishscore_gold"),
        Achievement("perfect_1", Metric.PERFECT, 1, 30, "fishscore_gold"),
        Achievement("perfect_25", Metric.PERFECT, 25, 250, "fishscore_gold"),
        Achievement("perfect_100", Metric.PERFECT, 100, 800, "fishscore_gold"),
        Achievement("nohit_10", Metric.NO_HIT, 10, 100, "octopus1"),
        Achievement("nohit_50", Metric.NO_HIT, 50, 400, "octopusred1"),
        Achievement("combo_5", Metric.COMBO, 5, 100, "fishblue1"),
        Achievement("boss_1", Metric.BOSS, 1, 150, "shark1"),
        Achievement("boss_10", Metric.BOSS, 10, 800, "sharkdark1"),
        Achievement("shopper", Metric.PURCHASES, 1, 20, "fishscore"),
        Achievement("fashion", Metric.COSMETICS, 5, 200, "sailors/punk"),
        Achievement("fleet", Metric.BOATS, 8, 1000, "boats/royal/ship"),
        Achievement("daily_3", Metric.DAILY_STREAK, 3, 50, "fishscore_blue"),
        Achievement("daily_7", Metric.DAILY_STREAK, 7, 200, "fishscore_blue"),
        Achievement("streak_3", Metric.STREAK, 3, 50, "fishscore_blue"),
        Achievement("streak_7", Metric.STREAK, 7, 200, "fishscore_blue"),
        Achievement("streak_30", Metric.STREAK, 30, 1000, "fishscore_gold"),
        Achievement("speed_1", Metric.SPEED, 1, 50, "piranhared1"),
        Achievement("speed_25", Metric.SPEED, 25, 300, "piranhared2"),
        Achievement("rich", Metric.COINS_HELD, 5_000, 250, "fishscore_gold"),
        Achievement("veteran", Metric.VETERAN, 1, 500, "sailors/captain", hidden = true),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}

/** Nagrody za kolejne dni logowania (7 dni, potem od nowa). */
object DailyRewards {
    val coins = intArrayOf(50, 75, 100, 125, 150, 200, 300)

    /** Siódmego dnia dodatkowo po jednym wzmocnieniu każdego rodzaju. */
    const val BONUS_DAY = 7
}

/** Trofea za passę liczone tygodniami: po 7 dniach zaczyna się nowy tydzień z wyższymi stawkami. */
object StreakRewards {
    const val WEEK = 7
    const val MAX_MULTIPLIER = 10

    /** Stawki dni tygodnia passy – 7. dzień jest najcenniejszy. */
    val base = intArrayOf(1, 2, 3, 4, 5, 6, 10)

    /** Numer tygodnia passy (od 1), w którym wypada dany dzień. */
    fun week(day: Int): Int = (day - 1).coerceAtLeast(0) / WEEK + 1

    /** Każdy kolejny tydzień mnoży stawki (x2, x3 … do x10). */
    fun multiplier(week: Int): Int = week.coerceIn(1, MAX_MULTIPLIER)

    fun trophiesFor(day: Int): Int = if (day <= 0) 0 else base[(day - 1) % WEEK] * multiplier(week(day))
}

/**
 * Odzyskiwanie passy za monety. W każdym miesiącu passy (liczonym od dnia jej
 * rozpoczęcia) jest 5 napraw, każda kolejna droższa; szósta przerwa = passa przepada.
 */
object StreakRepairs {
    const val BASE_PRICE = 200
    const val PER_MONTH = 5

    /** Dłuższej przerwy nie da się już załatać. */
    const val MAX_MISSED_DAYS = 3

    /** Mnożniki ceny kolejnych napraw w miesiącu, w dziesiątych częściach (x1.0, x1.2, x1.8, x2.2, x3.0). */
    val multipliers = intArrayOf(10, 12, 18, 22, 30)

    /** Cena naprawy o indeksie [index] (0 = pierwsza w miesiącu). */
    fun price(index: Int): Int = BASE_PRICE * multipliers[index.coerceIn(0, PER_MONTH - 1)] / 10

    fun multiplierText(index: Int): String {
        val m = multipliers[index.coerceIn(0, PER_MONTH - 1)]
        return "x${m / 10}.${m % 10}"
    }

    /**
     * Numer miesiąca passy (0 = pierwszy), do którego należy [day]. Liczony tak,
     * żeby zgadzał się z [periodStart] także dla końcówek miesięcy (31 stycznia → 28 lutego).
     */
    fun period(startDay: Long, day: Long): Int {
        if (startDay < 0 || day < startDay) return 0
        var k = ChronoUnit.MONTHS.between(LocalDate.ofEpochDay(startDay), LocalDate.ofEpochDay(day)).toInt()
        while (periodStart(startDay, k + 1) <= day) k++
        return k
    }

    /** Pierwszy dzień [period]-tego miesiąca passy. */
    fun periodStart(startDay: Long, period: Int): Long =
        LocalDate.ofEpochDay(startDay.coerceAtLeast(0)).plusMonths(period.toLong()).toEpochDay()
}
