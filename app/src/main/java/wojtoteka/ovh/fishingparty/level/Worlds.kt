package wojtoteka.ovh.fishingparty.level

import wojtoteka.ovh.fishingparty.core.Rng

/**
 * 40 światów po 25 poziomów. Co 10 światów palety się powtarzają, ale nazwy już nie.
 * Poziom 1000 (Wielki Finał) ma własną, złotą paletę. Światy Bezkresnego Morza
 * (od 41.) losują paletę i nazwę z seeda – w każdej dziesiątce światów każda paleta
 * pojawia się raz i nigdy dwa razy pod rząd.
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
    const val FINALE = -1

    /** Liczba nazw do składania nazw światów Bezkresnego Morza (string-array endless_world_a/b). */
    const val ENDLESS_NAME_A = 12
    const val ENDLESS_NAME_B = 20

    private const val ENDLESS_SALT = 0x46505F57_4F524C44L

    /**
     * Wielki Finał płynie przez kolejne światy: etap k ma paletę i nazwę świata
     * [FINALE_STAGE_WORLDS][k], a port na mecie – złotą paletę [FINALE_THEME].
     */
    val FINALE_STAGE_WORLDS = intArrayOf(0, 1, 3, 4, 8, 5, 9)

    fun isEndless(world: Int): Boolean = world >= LevelConstants.WORLD_COUNT

    private fun paletteIndex(world: Int): Int =
        if (isEndless(world)) endlessPalette(world - LevelConstants.WORLD_COUNT) else world % THEMES.size

    fun theme(world: Int): String = if (world == FINALE) FINALE_THEME else THEMES[paletteIndex(world)]

    fun seaColor(world: Int): Int = if (world == FINALE) FINALE_SEA else SEA_COLORS[paletteIndex(world)]

    /** Paleta tła ekranów (menu, sklep…) dla gracza, który doszedł do danego poziomu. */
    fun paletteOf(level: Int): Int = if (level == LevelConstants.LEVEL_COUNT) FINALE else LevelGenerator.worldOf(level)

    fun firstLevel(world: Int): Int = world * LevelConstants.LEVELS_PER_WORLD + 1

    fun lastLevel(world: Int): Int = (world + 1) * LevelConstants.LEVELS_PER_WORLD

    /** Przetasowanie liczb 0 until [size] dla bloku [block] (z seeda, niezależne dla każdej [salt]). */
    private fun shuffled(size: Int, salt: Long, block: Int): IntArray {
        val order = IntArray(size) { it }
        val rng = Rng(Rng.mix(salt, block.toLong()))
        for (i in order.size - 1 downTo 1) {
            val j = rng.nextInt(i + 1)
            val t = order[i]; order[i] = order[j]; order[j] = t
        }
        return order
    }

    /**
     * Element [n] ciągu złożonego z kolejnych przetasowań liczb 0 until [size]: w każdym
     * bloku każda liczba pada raz, a na styku bloków ta sama liczba nie powtarza się pod rząd.
     */
    private fun cycle(size: Int, salt: Long, n: Int): Int {
        val block = n / size
        val order = shuffled(size, salt, block)
        if (block > 0 && order[0] == shuffled(size, salt, block - 1).last()) {
            val t = order[0]; order[0] = order[1]; order[1] = t
        }
        return order[n % size]
    }

    /** Paleta świata Bezkresnego Morza [endless] (liczonego od zera). */
    fun endlessPalette(endless: Int): Int = cycle(THEMES.size, ENDLESS_SALT, endless)

    /**
     * Nazwa świata Bezkresnego Morza jako para indeksów do list endless_world_a i endless_world_b
     * (np. „Zatoka” + „Wichrów”). Sąsiednie światy nigdy nie mają wspólnej części nazwy.
     */
    fun endlessName(endless: Int): Pair<Int, Int> =
        cycle(ENDLESS_NAME_A, ENDLESS_SALT + 1, endless) to cycle(ENDLESS_NAME_B, ENDLESS_SALT + 2, endless)
}
