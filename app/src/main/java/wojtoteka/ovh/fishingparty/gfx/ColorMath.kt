package wojtoteka.ovh.fishingparty.gfx

import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Barwa (0..1, czerwień = 0), nasycenie i jasność (0..1) – tak wybiera się kolor w kreatorze. */
data class Hsv(val h: Float, val s: Float, val v: Float) {
    val color: Int get() = ColorMath.fromHsv(h, s, v)
}

/** Przeliczenia kolorów bez klas Androida (działają też w testach JVM). */
object ColorMath {
    private const val OPAQUE = 0xFF000000.toInt()

    fun rgb(r: Int, g: Int, b: Int): Int = OPAQUE or (r shl 16) or (g shl 8) or b

    fun red(c: Int) = c shr 16 and 0xFF
    fun green(c: Int) = c shr 8 and 0xFF
    fun blue(c: Int) = c and 0xFF

    fun toHsv(c: Int): Hsv {
        val r = red(c) / 255f
        val g = green(c) / 255f
        val b = blue(c) / 255f
        val hi = max(r, max(g, b))
        val lo = min(r, min(g, b))
        val d = hi - lo
        val h = when {
            d == 0f -> 0f
            hi == r -> ((g - b) / d).mod(6f)
            hi == g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } / 6f
        return Hsv(h, if (hi == 0f) 0f else d / hi, hi)
    }

    fun fromHsv(h: Float, s: Float, v: Float): Int {
        val hh = h.mod(1f) * 6f
        val i = hh.toInt().coerceIn(0, 5)
        val f = hh - i
        val p = v * (1 - s)
        val q = v * (1 - s * f)
        val t = v * (1 - s * (1 - f))
        val (r, g, b) = when (i) {
            0 -> Triple(v, t, p)
            1 -> Triple(q, v, p)
            2 -> Triple(p, v, t)
            3 -> Triple(p, q, v)
            4 -> Triple(t, p, v)
            else -> Triple(v, p, q)
        }
        return rgb((r * 255).roundToInt(), (g * 255).roundToInt(), (b * 255).roundToInt())
    }

    /** sRGB → CIE Lab (biel D65) – przestrzeń, w której odległość odpowiada temu, co widzi oko. */
    fun lab(color: Int): FloatArray {
        fun linear(channel: Int): Double {
            val c = channel / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        val r = linear(red(color))
        val g = linear(green(color))
        val b = linear(blue(color))
        fun f(t: Double) = if (t > 0.008856) cbrt(t) else 7.787 * t + 16.0 / 116.0
        val fx = f((0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047)
        val fy = f(0.2126 * r + 0.7152 * g + 0.0722 * b)
        val fz = f((0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883)
        return floatArrayOf((116 * fy - 16).toFloat(), (500 * (fx - fy)).toFloat(), (200 * (fy - fz)).toFloat())
    }

    /** Różnica dwóch kolorów Lab (ΔE): ok. 2 – ledwo widać, 30 – ten sam kolor w innym odcieniu. */
    fun deltaE(a: FloatArray, b: FloatArray): Float {
        val l = a[0] - b[0]
        val x = a[1] - b[1]
        val y = a[2] - b[2]
        return sqrt(l * l + x * x + y * y)
    }
}
