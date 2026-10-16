package wojtoteka.ovh.fishingparty.ui

import android.view.KeyEvent
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch

/**
 * Sterowanie całą aplikacją z fizycznej klawiatury (tablet z klawiaturą): strzałki i Tab
 * przechodzą po przyciskach, Enter/spacja klika, Esc wraca, a gdy dalej nie ma już przycisków,
 * strzałki przewijają ekran. W trakcie poziomu strzałki i WASD sterują łodzią (`KeyboardSteer`).
 *
 * Ekran i każde okienko to osobna warstwa. Pierwsze wciśnięcie strzałki, gdy nic nie jest wybrane,
 * wybiera pierwszy przycisk najwyższej warstwy – a nie ten, który leży pod okienkiem.
 */
object KeyNav {
    internal class Layer(val requester: FocusRequester, var initial: FocusRequester?) {
        var hasFocus = false

        /** Najpierw wskazany element ([initial]), a gdy go nie ma na ekranie – pierwszy przycisk warstwy. */
        fun focus(): Boolean {
            val first = initial?.let { runCatching { it.requestFocus() }.getOrDefault(false) } ?: false
            return first || runCatching { requester.requestFocus() }.getOrDefault(false)
        }
    }

    internal val layers = ArrayList<Layer>()

    internal fun isTop(layer: Layer) = layers.lastOrNull() === layer

    /** Wywoływane przez aktywność przed Compose. @return true, gdy klawisz został obsłużony */
    fun onKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val nav = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_TAB -> true
            else -> false
        }
        val top = layers.lastOrNull() ?: return false
        if (!nav || top.hasFocus) return false
        return top.focus()
    }
}

/**
 * Warstwa nawigacji klawiaturą: ekran albo okienko. [trap] – fokus nie wychodzi z warstwy, dopóki leży
 * ona na wierzchu (okienko). [autoFocus] – po otwarciu z klawiatury od razu wybiera pierwszy przycisk.
 * [initial] – co wybrać na start zamiast pierwszego przycisku (np. mapę).
 */
@Composable
fun Modifier.keyLayer(trap: Boolean = false, autoFocus: Boolean = false, initial: FocusRequester? = null): Modifier {
    val requester = remember { FocusRequester() }
    val layer = remember { KeyNav.Layer(requester, initial) }
    layer.initial = initial
    DisposableEffect(layer) {
        KeyNav.layers += layer
        onDispose { KeyNav.layers -= layer }
    }
    val input = LocalInputModeManager.current
    if (autoFocus) {
        LaunchedEffect(layer) {
            // Na dotyk nic nie wybieramy – pole tekstowe w okienku otworzyłoby klawiaturę ekranową.
            if (input.inputMode == InputMode.Keyboard) layer.focus()
        }
    }
    return this
        .focusRequester(requester)
        .onFocusChanged { layer.hasFocus = it.hasFocus }
        .focusProperties { if (trap) onExit = { if (KeyNav.isTop(layer)) cancelFocusChange() } }
        .focusGroup()
}

/** Główny przycisk okienka (np. „Graj”) – od niego zaczyna się wybór z klawiatury. Ustawia go [Overlay]. */
val LocalPrimaryFocus = staticCompositionLocalOf<FocusRequester?> { null }

/** Oznacza główny przycisk okienka – po otwarciu z klawiatury to on jest wybrany jako pierwszy. */
@Composable
fun Modifier.keyPrimary(): Modifier = LocalPrimaryFocus.current?.let { focusRequester(it) } ?: this

/**
 * Przewijanie strzałkami: gdy w danym kierunku nie ma już przycisku, na który można przejść
 * (np. długi tekst pod ostatnim przyciskiem), lista przewija się o kawałek. Page Up/Down – o ekran.
 */
@Composable
fun Modifier.keyScroll(state: ScrollableState): Modifier {
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val height = remember { floatArrayOf(0f) }
    return this
        .onSizeChanged { height[0] = it.height.toFloat() }
        .onKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            val page = height[0] * 0.8f
            val (direction, by) = when (event.key) {
                Key.DirectionDown -> FocusDirection.Down to page / 3f
                Key.DirectionUp -> FocusDirection.Up to -page / 3f
                Key.PageDown -> null to page
                Key.PageUp -> null to -page
                else -> return@onKeyEvent false
            }
            if (direction != null && focus.moveFocus(direction)) return@onKeyEvent true
            val can = if (by > 0f) state.canScrollForward else state.canScrollBackward
            if (can) scope.launch { state.animateScrollBy(by) }
            can
        }
}

/**
 * Element bez akcji, na który da się przejść strzałkami (karta odznaki, wiersz rankingu) –
 * dzięki temu lista bez przycisków też przewija się z klawiatury. Na dotyk nic się nie zmienia.
 */
@Composable
fun Modifier.keyStop(): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val focused = keyFocused(interaction)
    val u = LocalUnit.current.toFloat()
    return this
        .zIndex(if (focused) 1f else 0f)
        .drawBehind { if (focused) focusRing(u) }
        .focusable(interactionSource = interaction)
}

/**
 * Suwak sterowany strzałkami (pole koloru i pasek barw w kreatorze). [onKey] zwraca false, gdy
 * wartość jest już na końcu – wtedy strzałka przechodzi dalej, do sąsiedniego elementu.
 */
@Composable
fun Modifier.keyAdjust(onKey: (Key) -> Boolean): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val focused = keyFocused(interaction)
    val u = LocalUnit.current.toFloat()
    return this
        .zIndex(if (focused) 1f else 0f)
        .drawBehind { if (focused) focusRing(u) }
        .onKeyEvent { it.type == KeyEventType.KeyDown && onKey(it.key) }
        .focusable(interactionSource = interaction)
}
