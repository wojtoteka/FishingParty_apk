package wojtoteka.ovh.fishingparty.gfx

import kotlin.math.max

/**
 * Kolory kadłuba łodzi: oryginalny sprite ma 4 odcienie brązu (od najciemniejszego),
 * a każda skórka podmienia je na swoje. Drugi odcień pokrywa większość kadłuba.
 */
object HullPaints {
    val ORIGINAL = intArrayOf(rgb(85, 27, 0), rgb(97, 39, 9), rgb(113, 55, 25), rgb(129, 71, 41))

    /** Ile pikseli kadłuba ma każdy odcień – im więcej, tym bardziej liczy się przy porównaniu kolorów. */
    val WEIGHTS = intArrayOf(11, 85, 32, 20)

    /** Skórki ze sklepu – te same liczby co w tools/recolor_sprites.py (pilnuje tego test). */
    val presets: Map<String, IntArray> = mapOf(
        "classic" to ORIGINAL,
        "crimson" to table(110, 10, 10, 140, 25, 25, 165, 40, 40, 190, 60, 55),
        "navy" to table(10, 25, 70, 20, 40, 95, 35, 60, 120, 55, 85, 150),
        "emerald" to table(10, 60, 30, 20, 85, 45, 35, 110, 60, 55, 135, 80),
        "ivory" to table(150, 140, 120, 190, 180, 160, 215, 207, 190, 238, 233, 218),
        "golden" to table(150, 95, 0, 190, 130, 10, 220, 165, 30, 245, 200, 60),
        "shadow" to table(20, 18, 25, 35, 32, 42, 52, 48, 62, 72, 68, 85),
        "royal" to table(60, 15, 80, 85, 30, 110, 110, 50, 140, 135, 75, 165),
        "sky" to table(30, 90, 150, 45, 115, 180, 70, 145, 205, 100, 175, 230),
        "teal" to table(0, 80, 80, 10, 105, 100, 25, 130, 120, 45, 155, 145),
        "mint" to table(40, 120, 90, 60, 150, 110, 90, 180, 135, 130, 210, 165),
        "lime" to table(70, 110, 10, 95, 140, 20, 125, 170, 35, 160, 200, 60),
        "amber" to table(150, 60, 0, 185, 85, 5, 215, 115, 20, 240, 150, 45),
        "rose" to table(140, 40, 80, 175, 60, 105, 205, 90, 135, 230, 125, 165),
        "lavender" to table(90, 70, 140, 115, 95, 170, 145, 125, 195, 175, 160, 220),
        "silver" to table(90, 95, 105, 125, 130, 140, 160, 165, 175, 195, 200, 210),
        "tangerine" to table(176, 69, 8, 225, 95, 20, 255, 124, 48, 255, 160, 104),
        "coral" to table(179, 77, 69, 230, 105, 95, 255, 138, 129, 255, 184, 178),
        "lemon" to table(176, 155, 24, 225, 200, 40, 255, 230, 69, 255, 237, 123),
        "turquoise" to table(18, 140, 132, 30, 180, 170, 49, 212, 201, 77, 244, 233),
        "cobalt" to table(17, 50, 156, 30, 70, 200, 51, 94, 234, 89, 128, 255),
        "fuchsia" to table(156, 25, 115, 200, 40, 150, 234, 62, 180, 255, 100, 206),
        "plum" to table(86, 20, 57, 110, 30, 75, 133, 44, 94, 157, 63, 116),
        "ice" to table(142, 171, 183, 185, 220, 235, 222, 245, 255, 255, 255, 255),
    )

    /** Łódź z kreatora: „own-RRGGBB”, gdzie RRGGBB to kolor główny kadłuba. */
    private const val OWN = "own-"

    fun ownId(color: Int): String = OWN + hex(color)

    fun ownColor(id: String): Int? {
        if (!id.startsWith(OWN) || id.length != OWN.length + 6) return null
        return parseHex(id.substring(OWN.length))
    }

    /**
     * 4 odcienie z koloru głównego: ciemniejszy, główny i dwa jaśniejsze (jak w tools/recolor_sprites.py).
     * Jasnym kolorom brakuje miejsca na rozjaśnienie, więc jaśniejsze odcienie tracą wtedy nasycenie.
     */
    fun shades(main: Int): IntArray {
        val hsv = ColorMath.toHsv(main)
        fun shade(sat: Float, valueMul: Float, valueAdd: Float): Int {
            val v = hsv.v * valueMul + valueAdd
            val s = hsv.s * sat - max(0f, v - 1f) * 0.9f
            return ColorMath.fromHsv(hsv.h, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
        }
        return intArrayOf(shade(1.05f, 0.78f, 0f), main or OPAQUE, shade(0.92f, 1.12f, 0.04f), shade(0.82f, 1.24f, 0.08f))
    }

    private const val OPAQUE = 0xFF000000.toInt()

    private fun table(vararg c: Int) = IntArray(4) { rgb(c[it * 3], c[it * 3 + 1], c[it * 3 + 2]) }

    private fun rgb(r: Int, g: Int, b: Int): Int = ColorMath.rgb(r, g, b)

    fun hex(color: Int): String = "%06X".format(color and 0xFFFFFF)

    fun parseHex(text: String): Int? =
        if (text.length == 6 && text.all { it in '0'..'9' || it in 'A'..'F' }) OPAQUE or text.toInt(16) else null
}
