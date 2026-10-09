package wojtoteka.ovh.fishingparty.level

/** 20 światów po 25 poziomów. Co 10 światów palety się powtarzają, ale nazwy i przeciwnicy już nie. */
object Worlds {
    val THEMES = listOf(
        "sunny_bay", "coral_reef", "sunset_lagoon", "frozen_fjord", "misty_swamp",
        "volcanic_isles", "emerald_atoll", "midnight_sea", "desert_oasis", "abyss",
    )

    /** Kolor wody każdej palety (tło poza mapą i akcenty UI). */
    private val SEA_COLORS = intArrayOf(
        0xFF0097E3.toInt(), 0xFF00AAAA.toInt(), 0xFF5A50C8.toInt(), 0xFF145AAA.toInt(), 0xFF32785A.toInt(),
        0xFF283C6E.toInt(), 0xFF00B9A0.toInt(), 0xFF0F2D6E.toInt(), 0xFF009BBE.toInt(), 0xFF191E4B.toInt(),
    )

    fun theme(world: Int): String = THEMES[world % THEMES.size]

    fun seaColor(world: Int): Int = SEA_COLORS[world % SEA_COLORS.size]

    fun firstLevel(world: Int): Int = world * LevelConstants.LEVELS_PER_WORLD + 1

    fun lastLevel(world: Int): Int = (world + 1) * LevelConstants.LEVELS_PER_WORLD
}
