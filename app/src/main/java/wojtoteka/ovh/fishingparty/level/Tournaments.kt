package wojtoteka.ovh.fishingparty.level

import wojtoteka.ovh.fishingparty.core.Rng

/**
 * Turniej weekendowy (od wersji 2.5). Ogłasza go sam serwer (on decyduje o starcie i końcu):
 * wszyscy grają ten sam poziom z seeda, prób jest dowolnie wiele, a do rankingu turnieju liczy się najlepszy wynik.
 * Po końcu serwer rozdaje miejsca, premie do rankingu i monety (odbierane w grze).
 *
 * Reguły świata ([profile]), miejsce w świecie ([position]) i nazwę motywu podaje serwer – plansza powstaje z nich
 * tym samym generatorem co Bezkresne Morze ([SeedLevels]), a serwer liczy z nich te same limity wyniku. Nowy motyw
 * na serwerze działa więc bez aktualizacji gry.
 *
 * @param id numer turnieju (numer tygodnia z serwera)
 * @param theme identyfikator motywu (np. `storm`) – nazwę do wyświetlenia daje [name]
 * @param seed seed planszy – tylko gdy turniej już trwa (przyszłych nie da się poznać)
 */
class Tournament(
    val id: Int,
    val theme: String,
    val namePl: String,
    val nameEn: String,
    val startsAt: Long,
    val endsAt: Long,
    val seed: LevelSeed? = null,
    val profile: SeedProfile? = null,
    val position: Int = 0,
) {
    /** Czy da się zagrać: serwer podał seed i poprawne reguły świata. */
    val playable: Boolean get() = seed != null && profile != null

    fun isRunning(now: Long): Boolean = now in startsAt until endsAt

    fun hasEnded(now: Long): Boolean = now >= endsAt

    /** Nazwa motywu w języku gry (gdy brak tłumaczenia – w drugim języku). */
    fun name(polish: Boolean): String = if (polish) namePl.ifEmpty { nameEn } else nameEn.ifEmpty { namePl }

    fun plan(): LevelPlan = Tournaments.plan(requireNotNull(seed), requireNotNull(profile), position)

    /** Ten sam turniej z tymi samymi danymi (plansza, czasy, nazwa) – nie trzeba zapisywać go od nowa. */
    fun sameAs(other: Tournament?): Boolean = other != null && key() == other.key()

    private fun key(): List<Any?> = listOf(
        id, theme, namePl, nameEn, startsAt, endsAt, seed?.value, position,
        profile?.let { listOf(it.minDifficulty, it.maxDifficulty, it.minStages, it.maxStages) },
    )
}

object Tournaments {
    /** Numer „poziomu” w wyniku przejazdu – spoza kampanii, nie trafia do rekordów poziomów. */
    const val LEVEL_NUMBER = 0

    private const val WORLD_SALT = 0x54_4F_55_52L

    /** Paleta planszy (świat kampanii) – z seeda, więc każdy widzi te same kolory. */
    fun world(seed: LevelSeed): Int = Math.floorMod(Rng.mix(seed.value, WORLD_SALT), LevelConstants.WORLD_COUNT.toLong()).toInt()

    fun plan(seed: LevelSeed, profile: SeedProfile, position: Int): LevelPlan = SeedLevels.plan(
        seed = seed,
        profile = profile,
        number = LEVEL_NUMBER,
        world = world(seed),
        position = position.coerceIn(0, 1000),
        boss = false,
        previous = null,
    )

    /** Reguły świata z serwera albo null, gdy są niepoprawne (gra nie zbuduje z nich planszy). */
    fun profileOf(minDifficulty: Int, maxDifficulty: Int, minStages: Int, maxStages: Int): SeedProfile? = try {
        SeedProfile(minDifficulty, maxDifficulty, minStages, maxStages)
    } catch (_: IllegalArgumentException) {
        null
    }
}
