package wojtoteka.ovh.fishingparty.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.data.Achievement

/** Jak długo baner zostaje na ekranie bez dotknięcia. */
private const val SHOW_MILLIS = 3200

/** Docelowy rozmiar ikony w pikselach interfejsu. */
private const val ICON_UNITS = 20

/**
 * Powiadomienie „Osiągnięcie!” wysuwane z góry jak push: ikona,
 * nazwa odznaki i nagroda. Znika samo, po dotknięciu albo po przesunięciu w górę;
 * kolejne odznaki z kolejki wjeżdżają jedna po drugiej.
 *
 * @param waiting ile odznak czeka jeszcze za tą (plakietka „+N”)
 */
@Composable
fun BoxScope.AchievementToast(achievement: Achievement, waiting: Int, onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    val slide = remember(achievement) { Animatable(0f) }
    val timer = remember(achievement) { Animatable(1f) }
    val shine = remember(achievement) { Animatable(-0.3f) }
    var closing by remember(achievement) { mutableStateOf(false) }
    var drag by remember(achievement) { mutableStateOf(0f) }

    LaunchedEffect(achievement, closing) {
        if (!closing) {
            launch { slide.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 500f)) }
            launch { shine.animateTo(1.3f, tween(900, delayMillis = 250)) }
            timer.animateTo(0f, tween(SHOW_MILLIS, easing = LinearEasing))
            closing = true
        } else {
            slide.animateTo(0f, tween(200))
            done()
        }
    }

    val dismissDistance = with(LocalDensity.current) { px(12).toPx() }

    Box(
        Modifier
            .align(Alignment.TopCenter)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = px(6), vertical = px(3))
            .widthIn(max = px(330))
            .fillMaxWidth()
            .graphicsLayer {
                translationY = -(size.height + 40f) * (1f - slide.value) + drag.coerceAtMost(0f)
                alpha = slide.value.coerceIn(0f, 1f)
            }
            .noRippleClick { closing = true }
            .pointerInput(achievement) {
                detectVerticalDragGestures(
                    onDragEnd = { if (drag < -dismissDistance) closing = true else drag = 0f },
                    onDragCancel = { drag = 0f },
                ) { _, dy -> drag += dy }
            },
    ) {
        DarkCard(
            Modifier
                .fillMaxWidth()
                .drawWithContent {
                    drawContent()
                    // Błysk przelatujący raz przez baner zaraz po wjeździe.
                    val x = size.width * shine.value
                    val band = size.height * 1.2f
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent),
                            start = Offset(x - band, 0f),
                            end = Offset(x, size.height),
                        ),
                    )
                }
                .clipToBounds(),
            highlight = true,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                val icon = rememberSprite("sprites/${achievement.icon}")
                // Duże sprity (rekin, łódź) dostają mniejszą skalę, żeby ikony miały podobny rozmiar.
                val iconScale = (ICON_UNITS / maxOf(icon.width, icon.height)).coerceIn(1, 2)
                Box(Modifier.size(px(30)), contentAlignment = Alignment.Center) {
                    SpriteImage(icon, iconScale)
                }
                HSpace(4)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PixelText(stringResource(R.string.achievement_unlocked), size = 1, color = Pal.Gold)
                        if (waiting > 0) {
                            HSpace(3)
                            PixelText(stringResource(R.string.achievement_more, waiting), size = 1, color = Pal.SeaLight)
                        }
                    }
                    PixelText(achievementTitle(achievement), size = 3, maxLines = 1)
                }
                HSpace(3)
                Plate(pad = 2) {
                    SpriteImage("fishscore_gold", 1)
                    HSpace(1)
                    PixelText("+${achievement.reward}", size = 2)
                }
            }
        }
        // Pasek czasu – ile jeszcze baner zostanie.
        Canvas(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = px(4))
                .padding(bottom = px(2))
                .fillMaxWidth()
                .height(px(1)),
        ) {
            drawRect(Pal.Gold.copy(alpha = 0.8f), size = Size(size.width * timer.value, size.height))
        }
    }
}
