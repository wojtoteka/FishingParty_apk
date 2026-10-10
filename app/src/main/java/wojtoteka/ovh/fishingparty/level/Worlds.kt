package wojtoteka.ovh.fishingparty.level

/**
 * 40 światów po 25 poziomów. Co 10 światów palety się powtarzają, ale nazwy już nie.
 * Poziom 1000 (Wielki Finał) ma własną, złotą paletę.
 */
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

    const val FINALE_THEME = "legend"
    private const val FINALE_SEA = 0xFF371978.toInt()

    /** Umowny numer „świata” Wielkiego Finału – tylko do wyboru palety (port na mapie, tło menu). */
    const val FINALE = LevelConstants.WORLD_COUNT

    /**
     * Wielki Finał płynie przez kolejne światy: etap k ma paletę i nazwę świata
     * [FINALE_STAGE_WORLDS][k], a port na mecie – złotą paletę [FINALE_THEME].
     */
    val FINALE_STAGE_WORLDS = intArrayOf(0, 1, 3, 4, 8, 5, 9)

    fun theme(world: Int): String = if (world >= FINALE) FINALE_THEME else THEMES[world % THEMES.size]

    fun seaColor(world: Int): Int = if (world >= FINALE) FINALE_SEA else SEA_COLORS[world % SEA_COLORS.size]

    /** Paleta tła ekranów (menu, sklep…) dla gracza, który doszedł do danego poziomu. */
    fun paletteOf(level: Int): Int = if (level >= LevelConstants.LEVEL_COUNT) FINALE else LevelGenerator.worldOf(level)

    fun firstLevel(world: Int): Int = world * LevelConstants.LEVELS_PER_WORLD + 1

    fun lastLevel(world: Int): Int = (world + 1) * LevelConstants.LEVELS_PER_WORLD
}
