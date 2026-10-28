package wojtoteka.ovh.fishingparty.game

/**
 * Wibracja z rytmem (jak VibrationEffect.createWaveform): odcinki po [timings] ms z siłą [amplitudes]
 * (0 – przerwa, 1..255 – drganie).
 */
class Rumble(val timings: LongArray, val amplitudes: IntArray) {
    val millis: Long get() = timings.sum()
}

object Rumbles {
    /**
     * Przegrana: łagodne, smutne „łuu-łuu-łuu-łuuuu” (ok. 1 s) – trzy coraz słabsze nuty i ostatnia, dłuższa, gasnąca.
     * Przerwy między nutami są dość długie, żeby silniczek zdążył stanąć i każdą nutę było czuć osobno.
     */
    val LOST: Rumble = rumble {
        note(120, 110); rest(90)
        note(120, 90); rest(90)
        note(120, 70); rest(100)
        note(130, 90); note(110, 55); note(90, 30)
    }

    /**
     * Trafienie, które zabiera życie: podwójne, lekkie „bzz-bzz” – inne niż pojedyncze, lekkie stuknięcie
     * po złowieniu ryby, żeby gracz rozpoznawał je samym dotykiem.
     */
    val HIT: Rumble = rumble {
        note(60, 120); rest(70)
        note(90, 120)
    }

    /** Trafienie zatrzymane przez tarczę: jedno miękkie brzęczenie (bez utraty życia). */
    val SHIELD: Rumble = rumble {
        note(110, 80)
    }

    /** Wygrana: radosne „ta-ta-ta-TAAAM” (ok. 0,7 s) – trzy krótkie nuty i delikatnie dłuższe zakończenie. */
    val WON: Rumble = rumble {
        note(50, 90); rest(60)
        note(50, 90); rest(60)
        note(50, 90); rest(80)
        note(220, 130)
    }

    private class Builder {
        val timings = ArrayList<Long>()
        val amplitudes = ArrayList<Int>()

        fun note(millis: Long, strength: Int) {
            timings += millis
            amplitudes += strength
        }

        fun rest(millis: Long) = note(millis, 0)
    }

    private fun rumble(build: Builder.() -> Unit): Rumble =
        Builder().apply(build).let { Rumble(it.timings.toLongArray(), it.amplitudes.toIntArray()) }
}
