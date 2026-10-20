package wojtoteka.ovh.fishingparty.level

import wojtoteka.ovh.fishingparty.core.Rng

/**
 * Poziom dnia i poziom tygodnia. Seed losuje serwer i podaje go wszystkim graczom
 * (`GET /challenges`), więc każdy gra tę samą planszę. Dzienny zmienia się o 00:00,
 * tygodniowy w poniedziałek o 00:00 (czas polski, jak passa).
 *
 * Plansza powstaje z seeda tym samym generatorem co Bezkresne Morze ([SeedLevels]), tylko
 * z własnymi „regułami świata” – tydzień jest wyraźnie trudniejszy od dnia. Serwer liczy
 * z nich te same limity wyniku, więc [profile] i [position] muszą zgadzać się z serwerem.
 *
 * @param pointsMultiplier punkty rankingowe = wynik × mnożnik
 * @param coinBonus monety za pierwsze ukończenie w danym okresie
 */
enum class ChallengeKind(
    val id: String,
    val profile: SeedProfile,
    val position: Int,
    val pointsMultiplier: Int,
    val coinBonus: Int,
) {
    DAILY("daily", SeedProfile(minDifficulty = 380, maxDifficulty = 820, minStages = 3, maxStages = 5), 500, 1, 100),
    WEEKLY("weekly", SeedProfile(minDifficulty = 900, maxDifficulty = 1250, minStages = 5, maxStages = 6), 1000, 2, 300),
    ;

    companion object {
        fun of(id: String): ChallengeKind? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Poziom dnia/tygodnia z serwera.
 * @param period numer dnia (epoch day) albo tygodnia liczonego od poniedziałku
 * @param startsAt początek okresu (ms, czas serwera)
 * @param endsAt koniec okresu – wtedy pojawia się nowy poziom
 */
class Challenge(val kind: ChallengeKind, val period: Int, val seed: LevelSeed, val startsAt: Long, val endsAt: Long) {
    fun plan(): LevelPlan = Challenges.plan(kind, seed)

    fun isCurrent(now: Long): Boolean = now in startsAt until endsAt
}

object Challenges {
    /** Numer „poziomu” w wyniku przejazdu – spoza kampanii, nie trafia do rekordów poziomów. */
    const val LEVEL_NUMBER = 0

    private const val WORLD_SALT = 0x43_48_4C_57L

    /** Paleta planszy (świat kampanii) – też z seeda, więc każdy widzi te same kolory. */
    fun world(seed: LevelSeed): Int = Math.floorMod(Rng.mix(seed.value, WORLD_SALT), LevelConstants.WORLD_COUNT.toLong()).toInt()

    fun plan(kind: ChallengeKind, seed: LevelSeed): LevelPlan = SeedLevels.plan(
        seed = seed,
        profile = kind.profile,
        number = LEVEL_NUMBER,
        world = world(seed),
        position = kind.position,
        boss = false,
        previous = null,
    )
}
