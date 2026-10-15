package wojtoteka.ovh.fishingparty.gfx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Wygląd łodzi wybrany w sklepie. */
data class ShipLook(val boat: String = "classic", val sail: String = "white", val sailor: String = "ginger")

/**
 * Kolory 11 pasów żagla (pierwszy i ostatni to lina, jak w oryginale). Pas ma jeden kolor
 * albo – we flagach – kilka, rysowanych obok siebie w poprzek żagla (od lewej).
 */
object SailStyles {
    private const val ROPE = 0xFF713719.toInt()

    private fun solid(color: Int) = IntArray(11) { if (it == 0 || it == 10) ROPE else color }

    private val styles: Map<String, IntArray> = mapOf(
        "white" to solid(0xFFEEEEEE.toInt()),
        "red" to solid(0xFFD63434.toInt()),
        "blue" to solid(0xFF2C60CE.toInt()),
        "yellow" to solid(0xFFFAC828.toInt()),
        "green" to solid(0xFF2EAA54.toInt()),
        "black" to solid(0xFF2C2C34.toInt()),
        "striped" to IntArray(11) {
            when {
                it == 0 || it == 10 -> ROPE
                it % 2 == 1 -> 0xFFEEEEEE.toInt()
                else -> 0xFFD63434.toInt()
            }
        },
        "rainbow" to intArrayOf(
            ROPE, 0xFFE8383D.toInt(), 0xFFF58A2A.toInt(), 0xFFFAD02C.toInt(), 0xFF5CC94E.toInt(),
            0xFF2BB5C9.toInt(), 0xFF3466D6.toInt(), 0xFF8048C8.toInt(), 0xFFE058B8.toInt(), 0xFFE8383D.toInt(), ROPE,
        ),
        // Wersja 2.3. Pasy liczone od dołu żagla (1) do góry (9).
        "orange" to solid(0xFFF58A2A.toInt()),
        "pink" to solid(0xFFF06EAA.toInt()),
        "purple" to solid(0xFF8048C8.toInt()),
        "cyan" to solid(0xFF2BB5C9.toInt()),
        "marine" to stripes(0xFFEEEEEE.toInt(), 0xFF1E3C8C.toInt()),
        "candy" to stripes(0xFFEEEEEE.toInt(), 0xFFF06EAA.toInt()),
        "poland" to IntArray(11) {
            when {
                it == 0 || it == 10 -> ROPE
                it >= 5 -> 0xFFEEEEEE.toInt()
                else -> 0xFFDC143C.toInt()
            }
        },
        "pirate" to IntArray(11) {
            when {
                it == 0 || it == 10 -> ROPE
                it == 5 -> 0xFFEEEEEE.toInt()
                else -> 0xFF2C2C34.toInt()
            }
        },
        "ocean" to intArrayOf(
            ROPE, 0xFF0F2D6E.toInt(), 0xFF143C8C.toInt(), 0xFF1E50AA.toInt(), 0xFF2864C3.toInt(),
            0xFF2D7DD2.toInt(), 0xFF3796DC.toInt(), 0xFF50B4E6.toInt(), 0xFF78D2F0.toInt(), 0xFFAAE6F8.toInt(), ROPE,
        ),
        "sunset" to intArrayOf(
            ROPE, 0xFFFFD23C.toInt(), 0xFFFFB43C.toInt(), 0xFFFF8C3C.toInt(), 0xFFF5643C.toInt(),
            0xFFE6464B.toInt(), 0xFFC83C64.toInt(), 0xFFA0367D.toInt(), 0xFF7D3291.toInt(), 0xFF5A2D9B.toInt(), ROPE,
        ),
        "gold" to stripes(0xFFFFC420.toInt(), 0xFFFFE07A.toInt()),
    )

    /** Flagi państw: 9 wierszy od góry żagla, każdy po 12 pól od lewej. */
    private val flags: Map<String, Array<IntArray>> = mapOf(
        "germany" to flag("KKKKKKKKKKKK*3", "RRRRRRRRRRRR*3", "YYYYYYYYYYYY*3", 'K' to 0xFF1E1E1E, 'R' to 0xFFDD0000, 'Y' to 0xFFFFCE00),
        "ukraine" to flag("BBBBBBBBBBBB*5", "YYYYYYYYYYYY*4", 'B' to 0xFF0057B7, 'Y' to 0xFFFFD700),
        "lithuania" to flag("YYYYYYYYYYYY*3", "GGGGGGGGGGGG*3", "RRRRRRRRRRRR*3", 'Y' to 0xFFFDB913, 'G' to 0xFF006A44, 'R' to 0xFFC1272D),
        "netherlands" to flag("RRRRRRRRRRRR*3", "WWWWWWWWWWWW*3", "BBBBBBBBBBBB*3", 'R' to 0xFFAE1C28, 'W' to WHITE, 'B' to 0xFF21468B),
        "austria" to flag("RRRRRRRRRRRR*3", "WWWWWWWWWWWW*3", "RRRRRRRRRRRR*3", 'R' to 0xFFC8102E, 'W' to WHITE),
        "hungary" to flag("RRRRRRRRRRRR*3", "WWWWWWWWWWWW*3", "GGGGGGGGGGGG*3", 'R' to 0xFFCD2A3E, 'W' to WHITE, 'G' to 0xFF436F4D),
        "spain" to flag("RRRRRRRRRRRR*2", "YYYYYYYYYYYY*5", "RRRRRRRRRRRR*2", 'R' to 0xFFAA151B, 'Y' to 0xFFF1BF00),
        "france" to flag("BBBBWWWWRRRR*9", 'B' to 0xFF0055A4, 'W' to WHITE, 'R' to 0xFFEF4135),
        "italy" to flag("GGGGWWWWRRRR*9", 'G' to 0xFF009246, 'W' to WHITE, 'R' to 0xFFCE2B37),
        "ireland" to flag("GGGGWWWWOOOO*9", 'G' to 0xFF169B62, 'W' to WHITE, 'O' to 0xFFFF883E),
        "belgium" to flag("KKKKYYYYRRRR*9", 'K' to 0xFF1E1E1E, 'Y' to 0xFFFDDA24, 'R' to 0xFFEF3340),
        "czechia" to flag(
            "BWWWWWWWWWWW", "BBWWWWWWWWWW", "BBBWWWWWWWWW", "BBBBWWWWWWWW", "BBBBBBWWWWWW",
            "BBBBRRRRRRRR", "BBBRRRRRRRRR", "BBRRRRRRRRRR", "BRRRRRRRRRRR",
            'B' to 0xFF11457E, 'W' to WHITE, 'R' to 0xFFD7141A,
        ),
        "sweden" to nordic('B' to 0xFF006AA7, 'Y' to 0xFFFECC02),
        "finland" to nordic('B' to WHITE, 'Y' to 0xFF002F6C),
        "denmark" to nordic('B' to 0xFFC8102E, 'Y' to WHITE),
        "norway" to flag(
            "RRRWBWRRRRRR*3", "WWWWBWWWWWWW", "BBBBBBBBBBBB", "WWWWBWWWWWWW", "RRRWBWRRRRRR*3",
            'R' to 0xFFBA0C2F, 'W' to WHITE, 'B' to 0xFF00205B,
        ),
        "switzerland" to flag(
            "RRRRRRRRRRRR", "RRRRRWWRRRRR*2", "RRRWWWWWWRRR*3", "RRRRRWWRRRRR*2", "RRRRRRRRRRRR",
            'R' to 0xFFDA291C, 'W' to WHITE,
        ),
        "uk" to flag(
            "WBBBWRRWBBBW", "BWBBWRRWBBWB", "BBWBWRRWBWBB", "WWWWWRRWWWWW", "RRRRRRRRRRRR",
            "WWWWWRRWWWWW", "BBWBWRRWBWBB", "BWBBWRRWBBWB", "WBBBWRRWBBBW",
            'W' to WHITE, 'B' to 0xFF012169, 'R' to 0xFFC8102E,
        ),
        "greece" to flag(
            "BBWBBBBBBBBB", "BBWBBWWWWWWW", "WWWWWBBBBBBB", "BBWBBWWWWWWW", "BBWBBBBBBBBB",
            "WWWWWWWWWWWW", "BBBBBBBBBBBB", "WWWWWWWWWWWW", "BBBBBBBBBBBB",
            'B' to 0xFF0D5EAF, 'W' to WHITE,
        ),
        "portugal" to flag(
            "GGGGGRRRRRRR*3", "GGGGYYRRRRRR*3", "GGGGGRRRRRRR*3",
            'G' to 0xFF046A38, 'R' to 0xFFDA291C, 'Y' to 0xFFFFE900,
        ),
        "usa" to flag(
            "NWNWNRRRRRRR", "NNNNNWWWWWWW", "NWNWNRRRRRRR", "NNNNNWWWWWWW", "NWNWNRRRRRRR",
            "WWWWWWWWWWWW", "RRRRRRRRRRRR", "WWWWWWWWWWWW", "RRRRRRRRRRRR",
            'N' to 0xFF3C3B6E, 'W' to WHITE, 'R' to 0xFFB22234,
        ),
        "canada" to flag(
            "RRRWWWWWWRRR", "RRRWWRRWWRRR", "RRRWRRRRWRRR", "RRRWRRRRWRRR", "RRRWWRRWWRRR",
            "RRRWWRRWWRRR", "RRRWWWWWWRRR*3",
            'R' to 0xFFD52B1E, 'W' to WHITE,
        ),
        "brazil" to flag(
            "GGGGGGGGGGGG", "GGGGGYYGGGGG", "GGGYYYYYYGGG", "GGYYYBBYYYGG", "GYYYBBBBYYYG",
            "GGYYYBBYYYGG", "GGGYYYYYYGGG", "GGGGGYYGGGGG", "GGGGGGGGGGGG",
            'G' to 0xFF009C3B, 'Y' to 0xFFFFDF00, 'B' to 0xFF002776,
        ),
        "argentina" to flag("LLLLLLLLLLLL*3", "WWWWWWWWWWWW", "WWWWWYYWWWWW", "WWWWWWWWWWWW", "LLLLLLLLLLLL*3", 'L' to 0xFF74ACDF, 'W' to WHITE, 'Y' to 0xFFF6B40E),
        "mexico" to flag("GGGGWWWWRRRR*3", "GGGGWYYWRRRR*3", "GGGGWWWWRRRR*3", 'G' to 0xFF006847, 'W' to WHITE, 'R' to 0xFFCE1126, 'Y' to 0xFF8C5A2B),
        "japan" to flag(
            "WWWWWWWWWWWW*2", "WWWWWRRWWWWW", "WWWWRRRRWWWW*3", "WWWWWRRWWWWW", "WWWWWWWWWWWW*2",
            'W' to WHITE, 'R' to 0xFFBC002D,
        ),
    )

    private const val WHITE = 0xFFEEEEEE

    /**
     * Flaga z wierszy pól (od góry żagla); „*n” na końcu wiersza powtarza go n razy.
     * Razem musi wyjść 9 wierszy – tyle pasów ma żagiel między linami.
     */
    private fun flag(vararg parts: Any): Array<IntArray> {
        val palette = parts.filterIsInstance<Pair<Char, Long>>().associate { (c, v) -> c to v.toInt() }
        val rows = parts.filterIsInstance<String>().flatMap { row ->
            val star = row.indexOf('*')
            if (star < 0) listOf(row) else List(row.substring(star + 1).toInt()) { row.substring(0, star) }
        }
        require(rows.size == 9) { "Flaga musi mieć 9 wierszy" }
        // Pas 9 to góra żagla, pas 1 – dół.
        return Array(11) { i ->
            if (i == 0 || i == 10) intArrayOf(ROPE) else rows[9 - i].map { palette.getValue(it) }.toIntArray()
        }
    }

    /** Krzyż skandynawski: tło [field], krzyż [cross] przesunięty do masztu. */
    private fun nordic(field: Pair<Char, Long>, cross: Pair<Char, Long>) = flag(
        "BBBBYBBBBBBB*4", "YYYYYYYYYYYY", "BBBBYBBBBBBB*4", 'B' to field.second, 'Y' to cross.second,
    )

    /** Pasy na zmianę: nieparzyste [odd], parzyste [even] (jak „w paski”). */
    private fun stripes(odd: Int, even: Int) = IntArray(11) {
        when {
            it == 0 || it == 10 -> ROPE
            it % 2 == 1 -> odd
            else -> even
        }
    }

    /** Pasy żagla od dołu (0 = lina); każdy pas to kolory pól od lewej. */
    fun bands(id: String): Array<IntArray> =
        flags[id] ?: (styles[id] ?: styles.getValue("white")).let { colors -> Array(11) { intArrayOf(colors[it]) } }

    /** Czy taki żagiel istnieje (sprawdzane w testach katalogu). */
    fun has(id: String): Boolean = id in flags || id in styles
}

/**
 * Port rysowania łodzi z oryginału: obrócony kadłub, maszt z marynarzem
 * i żagiel z 11 pasków wypychanych w kierunku płynięcia.
 * Oryginał używał 20 jednostek na kafelek, tu kafelek ma 16 – stąd 0.8.
 */
class ShipPainter {
    private val bitmapPaint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
    }
    private val fill = Paint().apply { isAntiAlias = false }
    private val rect = RectF()

    fun draw(canvas: Canvas, x: Float, y: Float, heading: Float, moving: Boolean, time: Float, look: ShipLook, alpha: Int = 255) {
        bitmapPaint.alpha = alpha
        val rotation = Math.toDegrees(heading.toDouble()).toFloat() + 90f
        val hull = if (moving && time % 0.4f < 0.2f) Sprites.boat(look.boat, "ship2") else Sprites.boat(look.boat, "ship")
        sprite(canvas, hull, x, y, rotation)
        sprite(canvas, Sprites.boat(look.boat, "shiptop"), x, y - 1.6f, rotation)

        // W oryginale „rotation > 180” = łódź skierowana w dół ekranu: maszt chowa się za żaglem.
        val facingDown = sin(heading) > 0f
        if (facingDown) mastAndSailor(canvas, x, y, heading, look)
        sail(canvas, x, y, heading, rotation, SailStyles.bands(look.sail), alpha)
        if (!facingDown) mastAndSailor(canvas, x, y, heading, look)
        bitmapPaint.alpha = 255
    }

    private fun mastAndSailor(canvas: Canvas, x: Float, y: Float, heading: Float, look: ShipLook) {
        sprite(canvas, Sprites.sprite("mast"), x, y - 8f, 0f)
        val up = -heading + PI.toFloat()
        val px = 3.2f * cos(up)
        val py = 3.2f * sin(up) + 4f
        sprite(canvas, Sprites.sailor(look.sailor), x + px, y - py, 0f)
    }

    private fun sail(canvas: Canvas, x: Float, y: Float, heading: Float, rotation: Float, bands: Array<IntArray>, alpha: Int) {
        val dx = cos(-heading)
        val dy = sin(-heading)
        for (i in 0..10) {
            val a = 1f - (abs(5f - i) / 5f).pow(2)
            val push = (a * 2f + 1f)
            val cx = x + dx * push * 0.8f
            val cy = y - (6f + i + dy * push) * 0.8f
            val w = (10f + a * 5f) * 0.8f
            val h = 1.6f
            val colors = bands[i]
            val part = w / colors.size
            canvas.save()
            canvas.rotate(rotation, cx, cy)
            for ((j, color) in colors.withIndex()) {
                fill.color = color
                fill.alpha = alpha
                val left = cx - w / 2f + j * part
                rect.set(left, cy - h / 2f, left + part, cy + h / 2f)
                canvas.drawRect(rect, fill)
            }
            canvas.restore()
        }
    }

    fun sprite(canvas: Canvas, bitmap: Bitmap, x: Float, y: Float, rotation: Float, flipX: Boolean = false, alpha: Int = -1) {
        val previous = bitmapPaint.alpha
        if (alpha >= 0) bitmapPaint.alpha = alpha
        canvas.save()
        canvas.translate(x, y)
        if (rotation != 0f) canvas.rotate(rotation)
        if (flipX) canvas.scale(-1f, 1f)
        rect.set(-bitmap.width / 2f, -bitmap.height / 2f, bitmap.width / 2f, bitmap.height / 2f)
        canvas.drawBitmap(bitmap, null, rect, bitmapPaint)
        canvas.restore()
        bitmapPaint.alpha = previous
    }
}
