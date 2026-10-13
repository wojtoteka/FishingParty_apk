package wojtoteka.ovh.fishingparty.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.data.Achievement
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Jak długo karta zostaje na ekranie bez dotknięcia. */
private const val SHOW_MILLIS = 4000L

/** Chwila po pojawieniu się, w której dotknięcie nie zamyka karty (palec mógł celować w przycisk pod spodem). */
private const val TAP_GUARD_MILLIS = 450L

/**
 * Karta „Osiągnięcie!” na środku ekranu: złote promienie, duża ikona, opis i nagroda.
 * Zamyka się sama albo po dotknięciu; kolejne odznaki z kolejki wchodzą jedna po drugiej.
 *
 * @param waiting ile odznak czeka jeszcze za tą (licznik „+N”)
 */
@Composable
fun AchievementPopup(achievement: Achievement, waiting: Int, onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    val pop = remember(achievement) { Animatable(0f) }
    var closing by remember(achievement) { mutableStateOf(false) }
    var tappable by remember(achievement) { mutableStateOf(false) }

    LaunchedEffect(achievement, closing) {
        if (!closing) {
            launch { delay(TAP_GUARD_MILLIS); tappable = true }
            pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 400f))
            delay(SHOW_MILLIS)
            closing = true
        } else {
            pop.animateTo(0f, tween(180))
            done()
        }
    }
    fun close() {
        if (tappable && !closing) closing = true
    }
    BackHandler { close() }

    val spin = rememberInfiniteTransition(label = "rays")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(12000, easing = LinearEasing)), label = "angle")
    val glow by spin.animateFloat(0.55f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "glow")
    val p = pop.value

    Box(
        Modifier
            .fillMaxSize()
            .background(Pal.Scrim.copy(alpha = Pal.Scrim.alpha * p.coerceIn(0f, 1f)))
            .noRippleClick { close() }
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(px(10))
                .widthIn(max = px(300))
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = p
                    scaleY = p
                    alpha = p.coerceIn(0f, 1f)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PixelText(
                stringResource(R.string.achievement_unlocked),
                size = 6,
                color = Pal.Gold,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Box(contentAlignment = Alignment.Center) {
                Rays(angle, glow, Modifier.size(px(120)))
                SpriteImage(achievement.icon, 4)
            }
            DarkCard(Modifier.fillMaxWidth(), highlight = true) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    PixelText(achievementTitle(achievement), size = 4, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    val description = achievementDescription(achievement)
                    if (description.isNotEmpty()) {
                        VSpace(2)
                        PixelText(description, size = 2, color = Pal.Foam, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    VSpace(5)
                    Plate {
                        PixelText(stringResource(R.string.achievement_reward), size = 2, color = Pal.Gold)
                        HSpace(3)
                        SpriteImage("fishscore_gold", 2)
                        HSpace(1)
                        PixelText("+${achievement.reward}", size = 3)
                    }
                    VSpace(2)
                    PixelText(stringResource(R.string.achievement_claim_hint), size = 1, color = Pal.SeaLight, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            VSpace(6)
            Row(horizontalArrangement = Arrangement.spacedBy(px(6)), verticalAlignment = Alignment.CenterVertically) {
                PixelText(stringResource(R.string.tap_to_continue), size = 2, color = Pal.White.copy(alpha = glow))
                if (waiting > 0) PixelText(stringResource(R.string.achievement_more, waiting), size = 2, color = Pal.Gold)
            }
        }
    }
}

/** Obracające się złote promienie za ikoną. */
@Composable
private fun Rays(angle: Float, glow: Float, modifier: Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2
        val half = (PI / 18).toFloat()
        rotate(angle, c) {
            for (i in 0 until 12) {
                val a = (i * PI / 6).toFloat()
                val ray = Path().apply {
                    moveTo(c.x, c.y)
                    lineTo(c.x + r * cos(a - half), c.y + r * sin(a - half))
                    lineTo(c.x + r * cos(a + half), c.y + r * sin(a + half))
                    close()
                }
                drawPath(ray, Pal.Gold.copy(alpha = 0.35f * glow))
            }
        }
        drawCircle(Pal.SandLight.copy(alpha = 0.3f * glow), r * 0.45f, c)
    }
}
