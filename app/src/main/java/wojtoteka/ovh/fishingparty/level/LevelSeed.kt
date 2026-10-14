package wojtoteka.ovh.fishingparty.level

import wojtoteka.ovh.fishingparty.core.Rng

/**
 * Seed poziomu – jak seed świata w Minecraft: ta sama liczba daje u każdego
 * gracza dokładnie ten sam poziom, a różne seedy dają zupełnie różne plansze.
 * Na razie z seedów korzystają poziomy powyżej 1000 (Bezkresne Morze);
 * ten sam mechanizm posłuży poziomom dnia i tygodnia, których seed przyjdzie z serwera.
 *
 * Kod seeda to 64 bity zapisane w 13 znakach Crockford base32 (bez liter I, L, O, U),
 * pogrupowane po cztery, np. `7K3F-9QX2-MB4T-A`.
 */
@JvmInline
value class LevelSeed(val value: Long) {

    val code: String
        get() {
            val chars = CharArray(CODE_LENGTH)
            var v = value
            for (i in CODE_LENGTH - 1 downTo 0) {
                chars[i] = ALPHABET[(v and 31L).toInt()]
                v = v ushr 5
            }
            return chars.toList().chunked(4).joinToString("-") { it.joinToString("") }
        }

    override fun toString(): String = code

    companion object {
        private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
        private const val CODE_LENGTH = 13

        /** Sól seedów Bezkresnego Morza – niezależna od soli poziomów 1–1000. */
        private const val ENDLESS_SALT = 0x46505F45_4E444C53L

        /** Seed poziomu Bezkresnego Morza. Różne numery zawsze dają różne seedy (mieszanie jest odwracalne). */
        fun endless(number: Int): LevelSeed = LevelSeed(Rng.mix(ENDLESS_SALT, number.toLong()))

        /** Odczytuje kod seeda (wielkość liter, myślniki i spacje nie mają znaczenia; O→0, I/L→1). */
        fun parse(code: String): LevelSeed? {
            val clean = code.uppercase().filter { it != '-' && !it.isWhitespace() }
                .replace('O', '0').replace('I', '1').replace('L', '1')
            if (clean.length != CODE_LENGTH) return null
            var v = 0L
            for ((i, ch) in clean.withIndex()) {
                val d = ALPHABET.indexOf(ch)
                if (d < 0) return null
                // Pierwszy znak niesie tylko 4 z 5 bitów (13 × 5 = 65).
                if (i == 0 && d > 15) return null
                v = (v shl 5) or d.toLong()
            }
            return LevelSeed(v)
        }
    }
}
