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
     * Przegrana: smutne „łuu-łuu-łuu-łuuuu” (ok. 1 s) – trzy coraz słabsze nuty i ostatnia, dłuższa, gasnąca.
     * Przerwy między nutami są dość długie, żeby silniczek zdążył stanąć i każdą nutę było czuć osobno.
     */
    val LOST: Rumble = rumble {
        note(140, 255); rest(80)
        note(140, 210); rest(80)
        note(140, 170); rest(90)
        note(150, 230); note(130, 150); note(110, 80)
    }

    /**
     * Trafienie, które zabiera życie: podwójne, mocne „bzz-BZZZ” – inne niż pojedyncze, lekkie stuknięcie
     * po złowieniu ryby, żeby gracz rozpoznawał je samym dotykiem.
     */
    val HIT: Rumble = rumble {
        note(80, 255); rest(70)
        note(130, 255)
    }

    /** Trafienie zatrzymane przez tarczę: jedno dłuższe, średnie brzęczenie (bez utraty życia). */
    val SHIELD: Rumble = rumble {
        note(160, 170)
    }

    /** Wygrana: radosne „ta-ta-ta-TAAAM” (ok. 0,7 s) – trzy krótkie nuty i mocne, długie zakończenie. */
    val WON: Rumble = rumble {
        note(60, 200); rest(60)
        note(60, 200); rest(60)
        note(60, 200); rest(80)
        note(300, 255)
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
