package wojtoteka.ovh.fishingparty.core

import java.security.SecureRandom

/**
 * Wartość liczbowa przechowywana w pamięci w zamaskowanej postaci.
 *
 * Narzędzia typu „memory editor” szukają w pamięci procesu konkretnej liczby
 * (np. stanu monet) i ją podmieniają. Tutaj liczba nigdy nie leży w pamięci
 * jawnie: trzymamy ją zaszyfrowaną losową maską, która zmienia się przy każdym
 * zapisie, oraz z sumą kontrolną. Podmiana dowolnego pola zostanie wykryta,
 * a wartość wróci do ostatniego poprawnego stanu.
 */
class GuardedLong(initial: Long = 0L) {
    private var masked = 0L
    private var mask = 0L
    private var check = 0L
    private var shadow = 0L
    private var shadowMask = 0L

    init {
        set(initial)
    }

    fun get(): Long {
        val value = masked xor mask
        if (checksum(value, mask) != check) {
            tampered = true
            val restored = shadow xor shadowMask
            set(restored)
            return restored
        }
        return value
    }

    fun set(value: Long) {
        mask = random.nextLong()
        masked = value xor mask
        check = checksum(value, mask)
        shadowMask = random.nextLong()
        shadow = value xor shadowMask
    }

    fun add(delta: Long): Long {
        val v = get() + delta
        set(v)
        return v
    }

    private fun checksum(value: Long, m: Long): Long = Rng.mix(value xor SALT, m)

    companion object {
        private val random = SecureRandom()
        private val SALT = random.nextLong()

        /** Ustawiane, gdy wykryto ingerencję w pamięć w tej sesji. */
        @Volatile
        var tampered: Boolean = false
            private set
    }
}

/** Tablica liczb całkowitych z tą samą ochroną co [GuardedLong]. */
class GuardedIntArray(size: Int) {
    private val items = Array(size) { GuardedLong(0) }

    val size: Int get() = items.size

    operator fun get(index: Int): Int = items[index].get().toInt()

    operator fun set(index: Int, value: Int) {
        items[index].set(value.toLong())
    }

    fun sum(): Long {
        var total = 0L
        for (item in items) total += item.get()
        return total
    }
}
