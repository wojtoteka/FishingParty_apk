package wojtoteka.ovh.fishingparty.game

import android.view.KeyEvent
import kotlin.math.atan2

/**
 * Sterowanie łodzią z fizycznej klawiatury (np. tablet z klawiaturą): WASD i strzałki,
 * także ukośnie po wciśnięciu dwóch klawiszy. Działa tylko w trakcie poziomu ([enabled]);
 * w menu klawisze zachowują się zwyczajnie.
 */
object KeyboardSteer {
    private const val UP = 1
    private const val DOWN = 2
    private const val LEFT = 4
    private const val RIGHT = 8

    private var held = 0

    var enabled = false
        set(value) {
            field = value
            if (!value) held = 0
        }

    /** Kierunek z wciśniętych klawiszy (jak kąt joysticka, oś y w dół) albo null, gdy łódź stoi. */
    fun angle(): Float? {
        val dx = (if (held and RIGHT != 0) 1f else 0f) - (if (held and LEFT != 0) 1f else 0f)
        val dy = (if (held and DOWN != 0) 1f else 0f) - (if (held and UP != 0) 1f else 0f)
        return if (dx == 0f && dy == 0f) null else atan2(dy, dx)
    }

    /** Puszcza wszystkie klawisze – np. gdy okno traci fokus i nie dostanie już zdarzeń puszczenia. */
    fun release() {
        held = 0
    }

    /** @return true, gdy klawisz steruje łodzią i nie powinien trafić dalej */
    fun onKey(event: KeyEvent): Boolean {
        if (!enabled) return false
        val bit = when (event.keyCode) {
            KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_DPAD_UP -> UP
            KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_DPAD_DOWN -> DOWN
            KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_DPAD_LEFT -> LEFT
            KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_DPAD_RIGHT -> RIGHT
            else -> return false
        }
        when (event.action) {
            KeyEvent.ACTION_DOWN -> held = held or bit
            KeyEvent.ACTION_UP -> held = held and bit.inv()
        }
        return true
    }
}
