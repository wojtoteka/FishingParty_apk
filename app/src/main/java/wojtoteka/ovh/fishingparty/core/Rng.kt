package wojtoteka.ovh.fishingparty.core

/**
 * Deterministyczny generator SplitMix64. Ten sam seed daje na każdym urządzeniu
 * identyczną sekwencję, więc każdy gracz dostaje dokładnie ten sam poziom.
 */
class Rng(seed: Long) {
    private var state = seed

    fun nextLong(): Long {
        state += -0x61c8864680b583ebL
        var z = state
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    /** Liczba z przedziału [0, bound). */
    fun nextInt(bound: Int): Int {
        require(bound > 0)
        return ((nextLong() ushr 33) % bound).toInt()
    }

    /** Liczba z przedziału [from, to] (obustronnie domknięty). */
    fun range(from: Int, to: Int): Int = if (to <= from) from else from + nextInt(to - from + 1)

    fun nextFloat(): Float = (nextLong() ushr 40).toFloat() / (1 shl 24).toFloat()

    fun chance(p: Float): Boolean = nextFloat() < p

    fun sign(): Int = if (nextInt(2) == 0) -1 else 1

    fun <T> pick(list: List<T>): T = list[nextInt(list.size)]

    companion object {
        fun mix(a: Long, b: Long): Long {
            var z = a * -0x61c8864680b583ebL + b
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }
    }
}
