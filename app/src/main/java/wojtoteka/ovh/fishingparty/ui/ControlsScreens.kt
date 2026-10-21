package wojtoteka.ovh.fishingparty.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.data.ControlMode
import wojtoteka.ovh.fishingparty.game.Sfx
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Wybór sterowania przy pierwszym uruchomieniu – później można go zmienić w ustawieniach. */
@Composable
fun ControlsChooser(onDone: () -> Unit) {
    val services = LocalServices.current
    val repo = services.repo
    var choice by remember { mutableStateOf(repo.save.controls) }
    fun confirm() {
        repo.save.controls = choice
        repo.save.controlsChosen = true
        repo.changed()
        onDone()
    }
    // „Wstecz” zatwierdza zaznaczony wybór – nie może uruchomić gry ukrytej pod spodem.
    Overlay(onDismiss = ::confirm) {
        PixelText(stringResource(R.string.controls_title), size = 4, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        VSpace(2)
        PixelText(
            stringResource(R.string.controls_text),
            size = 2,
            color = Pal.Outline,
            outline = null,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        VSpace(6)
        for (mode in ControlMode.entries) {
            ControlOption(mode, selected = mode == choice) {
                services.audio.play(Sfx.CLICK)
                choice = mode
            }
            VSpace(4)
        }
        PixelText(
            stringResource(R.string.controls_later),
            size = 1,
            color = Pal.WoodDark,
            outline = null,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        VSpace(6)
        PixelButton(
            stringResource(R.string.controls_ok),
            onClick = ::confirm,
            style = ButtonStyle.GREEN,
            size = 4,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ControlOption(mode: ControlMode, selected: Boolean, onClick: () -> Unit) {
    DarkCard(Modifier.fillMaxWidth().noRippleClick(onClick), highlight = selected) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ControlPreview(mode)
            HSpace(5)
            Column(Modifier.weight(1f)) {
                PixelText(
                    stringResource(if (mode == ControlMode.JOYSTICK) R.string.controls_joystick else R.string.controls_swipe),
                    size = 3,
                    color = if (selected) Pal.Gold else Pal.White,
                )
                VSpace(1)
                PixelText(
                    stringResource(if (mode == ControlMode.JOYSTICK) R.string.controls_joystick_desc else R.string.controls_swipe_desc),
                    size = 1,
                    color = Pal.Foam,
                )
            }
        }
    }
}

/** Mała animacja sterowania: palec rysujący ósemkę albo krążąca gałka joysticka. */
@Composable
private fun ControlPreview(mode: ControlMode) {
    val u = LocalUnit.current.toFloat()
    val huit = rememberSprite("sprites/huit")
    val doigt = rememberSprite("sprites/doigt")
    val phase by rememberInfiniteTransition(label = "controls").animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Canvas(Modifier.size(px(38), px(30))) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        if (mode == ControlMode.SWIPE) {
            // Ta sama ósemka i palec co w samouczku w grze.
            val hw = huit.width * u
            drawSprite(huit, Offset(cx - hw / 2f, cy - huit.height * u / 2f), u, alpha = 0.6f)
            val fx = cx + sin(phase) * hw * 35f / 80f + hw * 5f / 80f
            val fy = cy - (sin(phase * 2f) * hw * 15f / 80f - hw * 10f / 80f)
            drawSprite(doigt, Offset(fx - doigt.width * u / 2f, fy - doigt.height * u / 2f), u)
        } else {
            val r = 13f * u
            drawCircle(Pal.Outline.copy(alpha = 0.55f), r, Offset(cx, cy))
            drawCircle(Pal.Foam.copy(alpha = 0.8f), r - u / 2f, Offset(cx, cy), style = Stroke(u))
            val knob = Offset(cx + cos(phase) * r * 0.55f, cy + sin(phase) * r * 0.55f)
            drawCircle(Pal.Foam, r * 0.45f, knob)
            drawCircle(Pal.Outline, r * 0.45f - u / 2f, knob, style = Stroke(u))
        }
    }
}

/** Ustawienie sterowania: palec albo joystick (i strona ekranu, po której stoi joystick). */
@Composable
fun ControlsSettings() {
    val repo = LocalServices.current.repo
    repo.revision.intValue
    val save = repo.save
    Column(Modifier.fillMaxWidth()) {
        PixelText(stringResource(R.string.set_controls), size = 2, color = Pal.Foam)
        VSpace(3)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(px(3))) {
            for (mode in ControlMode.entries) {
                PixelButton(
                    stringResource(if (mode == ControlMode.JOYSTICK) R.string.controls_joystick else R.string.controls_swipe),
                    onClick = {
                        save.controls = mode
                        save.controlsChosen = true
                        repo.changed()
                    },
                    style = if (mode == save.controls) ButtonStyle.GREEN else ButtonStyle.SEA,
                    size = 2,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        if (save.controls == ControlMode.JOYSTICK) {
            VSpace(4)
            PixelText(stringResource(R.string.set_joystick_side), size = 2, color = Pal.Foam)
            VSpace(3)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(px(3))) {
                for (left in listOf(true, false)) {
                    PixelButton(
                        stringResource(if (left) R.string.set_left else R.string.set_right),
                        onClick = {
                            save.joystickLeft = left
                            repo.changed()
                        },
                        style = if (left == save.joystickLeft) ButtonStyle.GREEN else ButtonStyle.SEA,
                        size = 2,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}
