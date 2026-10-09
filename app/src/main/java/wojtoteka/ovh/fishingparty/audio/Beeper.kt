package wojtoteka.ovh.fishingparty.audio

import wojtoteka.ovh.fishingparty.core.Rng
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

enum class Wave { SQUARE, SAW, NOISE, SINE }

class BeepNote(
    val frequency: Double,
    val volume: Double,
    val span: Double,
    /** Czas trwania w sekundach. */
    val duration: Double,
    val wave: Wave,
) {
    var loopTo = -1
    /** Liczba powtórzeń pętli; 0 = w nieskończoność (jak w microStudio). */
    var repeats = 0
}

/**
 * Port parsera `audio.beep` z silnika microStudio, którym oryginalna gra
 * tworzyła całą muzykę i efekty. Stan (oktawa, tempo, głośność…) przechodzi
 * między kolejnymi wywołaniami, dokładnie jak w oryginale.
 */
class Beeper {
    private var octave = 5
    private var duration = 0.5
    private var volume = 0.5
    private var span = 1.0
    private var wave = Wave.SQUARE

    fun parse(input: String): List<BeepNote> {
        val sequence = ArrayList<BeepNote>()
        val loops = ArrayList<IntArray>() // [start, repeats]
        var status = "normal"
        var note: Int? = null
        for (t in input.split(" ")) {
            if (t.isEmpty()) continue
            when (status) {
                "normal" -> {
                    val absolute = absoluteNote(t)
                    val plain = PLAIN[t]
                    when {
                        absolute != null -> {
                            note = absolute
                            octave = absolute / 12
                            sequence += BeepNote(freq(absolute), volume, span, duration, wave)
                        }
                        plain != null -> {
                            val n = plain + octave * 12
                            note = n
                            sequence += BeepNote(freq(n), volume, span, duration, wave)
                        }
                        t == "square" -> wave = Wave.SQUARE
                        t == "saw" -> wave = Wave.SAW
                        t == "noise" -> wave = Wave.NOISE
                        t == "sine" -> wave = Wave.SINE
                        t in KEYWORDS -> status = t
                        t == "-" -> sequence += BeepNote(440.0, 0.0, span, duration, wave)
                        t == "end" -> if (loops.isNotEmpty() && sequence.isNotEmpty()) {
                            val loop = loops.removeAt(loops.lastIndex)
                            sequence += BeepNote(440.0, 0.0, span, 0.0, wave).also {
                                it.loopTo = loop[0]
                                it.repeats = loop[1]
                            }
                        }
                    }
                }
                "tempo" -> {
                    status = "normal"
                    t.toDoubleOrNull()?.takeIf { it > 0 }?.let { duration = 60.0 / it }
                }
                "duration" -> {
                    status = "normal"
                    t.toDoubleOrNull()?.takeIf { it > 0 }?.let { duration = it / 1000.0 }
                }
                "volume" -> {
                    status = "normal"
                    t.toDoubleOrNull()?.let { volume = it / 100.0 }
                }
                "span" -> {
                    status = "normal"
                    t.toDoubleOrNull()?.let { span = it / 100.0 }
                }
                "loop" -> {
                    status = "normal"
                    loops += intArrayOf(sequence.size, t.toDoubleOrNull()?.toInt() ?: 0)
                }
                "to" -> {
                    status = "normal"
                    val from = note
                    if (from != null) {
                        val target = absoluteNote(t) ?: PLAIN[t]?.let { it + octave * 12 }
                        if (target != null && target != from) {
                            val step = if (target > from) 1 else -1
                            var i = from + step
                            while (true) {
                                sequence += BeepNote(freq(i), volume, span, duration, wave)
                                if (i == target) break
                                i += step
                            }
                            note = target
                        }
                    }
                }
            }
        }
        if (loops.isNotEmpty() && sequence.isNotEmpty()) {
            val loop = loops.removeAt(loops.lastIndex)
            sequence += BeepNote(440.0, 0.0, span, 0.0, wave).also {
                it.loopTo = loop[0]
                it.repeats = loop[1]
            }
        }
        return sequence
    }

    companion object {
        private val KEYWORDS = setOf("tempo", "duration", "volume", "span", "loop", "to")
        private val NAMES = listOf(
            listOf("C", "DO"), listOf("C#", "DO#", "Db", "REb"), listOf("D", "RE"), listOf("D#", "RE#", "Eb", "MIb"),
            listOf("E", "MI"), listOf("F", "FA"), listOf("F#", "FA#", "Gb", "SOLb"), listOf("G", "SOL"),
            listOf("G#", "SOL#", "Ab", "LAb"), listOf("A", "LA"), listOf("A#", "LA#", "Bb", "SIb"), listOf("B", "SI"),
        )
        private val ABSOLUTE = HashMap<String, Int>()
        private val PLAIN = HashMap<String, Int>()

        init {
            for (i in 0..127) {
                val oct = i / 12 - 1
                for (n in NAMES[i % 12]) {
                    ABSOLUTE["$n$oct"] = i
                    if (oct == -1) PLAIN[n] = i
                }
            }
        }

        private fun absoluteNote(t: String): Int? = t.toIntOrNull()?.takeIf { it in 0..127 } ?: ABSOLUTE[t]

        private fun freq(note: Int): Double = 440.0 * 2.0.pow((note - 69) / 12.0)
    }
}

/** Generator próbek – odpowiednik AudioWorkletu microStudio (z tym samym filtrem 0.9/0.1). */
object BeepSynth {
    private class Voice(val notes: List<BeepNote>) {
        var index = 0
        var phase = 0.0
        var time = 0
        val loopCount = IntArray(notes.size)
        var done = false
    }

    /**
     * @param seconds długość wyniku; null = do zakończenia wszystkich głosów (dla efektów)
     */
    fun render(voices: List<List<BeepNote>>, sampleRate: Int, seconds: Double? = null, seed: Long = 1): FloatArray {
        val rng = Rng(seed)
        val active = voices.filter { it.isNotEmpty() }.map { Voice(it) }
        val maxSamples = ((seconds ?: 10.0) * sampleRate).toInt()
        val out = FloatArray(maxSamples)
        var last = 0.0
        var produced = 0
        for (s in 0 until maxSamples) {
            var sig = 0.0
            var alive = false
            for (v in active) {
                if (v.done) continue
                alive = true
                val n = v.notes[v.index]
                val samples = n.duration * sampleRate
                var vol = n.volume
                if (v.time / samples > n.span) vol = 0.0
                sig += when (n.wave) {
                    Wave.SQUARE -> if (v.phase > 0.5) vol else -vol
                    Wave.SAW -> (v.phase * 2 - 1) * vol
                    Wave.NOISE -> (rng.nextFloat() * 2 - 1) * vol
                    Wave.SINE -> sin(v.phase * PI * 2) * vol
                }
                v.phase = (v.phase + n.frequency / sampleRate) % 1.0
                v.time++
                if (v.time >= samples) {
                    v.time = 0
                    advance(v, n)
                }
            }
            if (!alive && seconds == null) break
            last = last * 0.9 + sig * 0.1
            out[s] = last.toFloat()
            produced = s + 1
        }
        return if (seconds == null) out.copyOf(produced) else out
    }

    private fun advance(v: Voice, n: BeepNote) {
        if (n.loopTo >= 0) {
            if (n.repeats > 0) {
                v.loopCount[v.index]++
                if (v.loopCount[v.index] >= n.repeats) {
                    v.loopCount[v.index] = 0
                    next(v)
                } else {
                    v.index = n.loopTo
                }
            } else {
                v.index = n.loopTo
            }
        } else {
            next(v)
        }
    }

    private fun next(v: Voice) {
        if (v.index + 1 < v.notes.size) v.index++ else v.done = true
    }

    /** Długość jednego obiegu sekwencji w sekundach (ignoruje zewnętrzną pętlę nieskończoną). */
    fun loopLength(notes: List<BeepNote>): Double {
        var total = 0.0
        var i = 0
        val counts = IntArray(notes.size)
        var guard = 0
        while (i < notes.size && guard++ < 100_000) {
            val n = notes[i]
            total += n.duration
            if (n.loopTo >= 0 && n.repeats > 0) {
                counts[i]++
                if (counts[i] >= n.repeats) {
                    counts[i] = 0
                    i++
                } else {
                    i = n.loopTo
                }
            } else {
                i++
            }
        }
        return total
    }

    fun toPcm16(samples: FloatArray, gain: Float = 1f): ShortArray = ShortArray(samples.size) {
        val v = (samples[it] * gain).coerceIn(-1f, 1f)
        floor(v * 32767f).toInt().toShort()
    }
}

/** Wszystkie dźwięki gry zapisane w notacji beep – te z oryginału oraz kilka nowych w tym samym stylu. */
object SoundScript {
    val MUSIC = listOf(
        "loop 0 span 50 volume 20 tempo 80 saw loop 2 F2 C2 end loop 4 G C end F2 C2 tempo 160 F C D E tempo 80 end",
        "loop 0 span 20 volume 20 tempo 160 saw loop 4 - A5 end loop 8 - Bb5 end loop 4 - A5  end",
        "loop 0 span 10 volume 20 tempo 160 noise - C4 end",
        "span 50 square tempo 320 volume 10 loop 0 " +
            "F5 E F G A - F E F E D C D C Bb4 A " +
            "G A Bb A G - G F# G A Bb B C5 D Eb E " +
            "G F E D C - A4 Bb C5 D C B4 C5 D D# E " +
            "F A C6 Bb5 A - - E D C Bb4 C5 C# D D# E end",
    )

    /** Długość pętli muzyki: 12 s (wszystkie cztery głosy kończą się razem). */
    const val MUSIC_SECONDS = 12.0

    private val LEVEL_UP = listOf(
        "square duration 100 volume 50 span 50 F4 F G G duration 200 A volume 20 A volume 6 A",
        "square duration 100 volume 50 span 50 Bb4 Bb C5 C duration 200 C volume 20 C volume 6 C",
        "square duration 100 volume 50 span 50 D5 D E5 E duration 200 F volume 20 F volume 6 F",
    )
    private val LEVEL_CLEARED = listOf(
        "duration 200 - saw duration 50 volume 100 span 80 F5 C6 F",
        "duration 200 - saw volume 50 span 80 duration 75 - duration 50 F5 C6 F",
        "duration 200 - saw volume 25 span 80 duration 175 - duration 50 F5 C6 F",
    )

    val SFX: Map<String, List<String>> = mapOf(
        "fish" to listOf("square tempo 20000 volume 50 span 100 F4 to F6"),
        "fish_gold" to listOf("square tempo 20000 volume 50 span 100 F5 to F7", "duration 90 - square tempo 20000 volume 30 span 100 C6 to C7"),
        "combo" to listOf("square tempo 20000 volume 50 span 100 A4 to A6"),
        "hit" to listOf("saw tempo 10000 volume 100 span 100 C2 to C4 to C2 to C4"),
        "lost" to listOf(
            "saw tempo 10000 volume 100 span 100 C2 to C4 to C2 to C4",
            "duration 500 - square duration 200 span 80 duration 800 C3",
            "duration 500 - square duration 200 span 80 duration 800 A3",
            "duration 500 - square duration 200 span 80 duration 800 F3",
        ),
        "start" to listOf("square tempo 960 volume 100 span 50 F5 C6"),
        "bump" to listOf("noise duration 50 volume 50 span 100 C6", "saw duration 100 volume 100 span 100 C2"),
        "gate" to LEVEL_CLEARED,
        "stage" to LEVEL_UP,
        "win" to LEVEL_UP + LEVEL_CLEARED.map { "duration 700 - $it" },
        "click" to listOf("square tempo 2400 volume 25 span 60 C6"),
        "coin" to listOf("square tempo 1600 volume 40 span 70 B5 E6"),
        "achievement" to LEVEL_UP,
        "shield" to listOf("noise duration 50 volume 50 span 100 C6", "square tempo 20000 volume 40 span 100 C6 to C4"),
        "star" to listOf("square tempo 1200 volume 45 span 80 C6 G6"),
    )

    fun render(lines: List<String>, sampleRate: Int, seconds: Double? = null): FloatArray {
        val beeper = Beeper()
        return BeepSynth.render(lines.map { beeper.parse(it) }, sampleRate, seconds)
    }
}
