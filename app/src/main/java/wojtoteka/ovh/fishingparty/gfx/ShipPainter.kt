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

/** Kolory 11 pasów żagla (pierwszy i ostatni to lina, jak w oryginale). */
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
    )

    fun colors(id: String): IntArray = styles[id] ?: styles.getValue("white")
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
        sail(canvas, x, y, heading, rotation, SailStyles.colors(look.sail), alpha)
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

    private fun sail(canvas: Canvas, x: Float, y: Float, heading: Float, rotation: Float, colors: IntArray, alpha: Int) {
        val dx = cos(-heading)
        val dy = sin(-heading)
        for (i in 0..10) {
            val a = 1f - (abs(5f - i) / 5f).pow(2)
            val push = (a * 2f + 1f)
            val cx = x + dx * push * 0.8f
            val cy = y - (6f + i + dy * push) * 0.8f
            val w = (10f + a * 5f) * 0.8f
            val h = 1.6f
            fill.color = colors[i]
            fill.alpha = alpha
            canvas.save()
            canvas.rotate(rotation, cx, cy)
            rect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
            canvas.drawRect(rect, fill)
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
