package wojtoteka.ovh.fishingparty.gfx

import android.graphics.Canvas
import android.graphics.Paint

/**
 * Tekst fontem BitCell (tym samym co w oryginale). Font ma siatkę 16 pikseli
 * na em, więc rozmiar 16·p daje ostre litery, gdzie jeden piksel litery = p
 * pikseli ekranu. Wielkie litery mają 7 pikseli wysokości.
 */
class PixelText {
    private val paint = Paint().apply {
        typeface = Sprites.font
        isAntiAlias = false
    }

    fun width(text: String, p: Float): Float {
        paint.textSize = 16f * p
        return paint.measureText(text)
    }

    /**
     * @param top górna krawędź wielkich liter
     * @param align -1 do lewej, 0 środek, 1 do prawej
     */
    fun draw(
        canvas: Canvas,
        text: String,
        x: Float,
        top: Float,
        p: Float,
        color: Int,
        align: Int = -1,
        outline: Int = OUTLINE,
        alpha: Int = 255,
    ) {
        paint.textSize = 16f * p
        val w = paint.measureText(text)
        val left = when (align) {
            0 -> x - w / 2f
            1 -> x - w
            else -> x
        }
        val baseline = top + 7f * p
        if (outline != 0) {
            paint.color = outline
            paint.alpha = alpha
            canvas.drawText(text, left - p, baseline, paint)
            canvas.drawText(text, left + p, baseline, paint)
            canvas.drawText(text, left, baseline - p, paint)
            canvas.drawText(text, left, baseline + p, paint)
            canvas.drawText(text, left + p, baseline + p, paint)
        }
        paint.color = color
        paint.alpha = alpha
        canvas.drawText(text, left, baseline, paint)
    }

    companion object {
        const val OUTLINE = 0xFF0A1E38.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val GOLD = 0xFFFFC820.toInt()
        const val RED = 0xFFFF5040.toInt()
        const val LEVEL_BLUE = 0xFF195471.toInt()
        const val PALE = 0xFFB4C8DC.toInt()
    }
}
