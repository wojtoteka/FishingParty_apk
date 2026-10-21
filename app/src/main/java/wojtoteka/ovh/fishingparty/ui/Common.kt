package wojtoteka.ovh.fishingparty.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import wojtoteka.ovh.fishingparty.R

@Composable
fun TopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, showCoins: Boolean = true) {
    val repo = LocalServices.current.repo
    repo.revision.intValue
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(horizontal = px(6), vertical = px(4)),
    ) {
        PixelButton("<", onClick = onBack, style = ButtonStyle.WOOD, size = 3, modifier = Modifier.align(Alignment.CenterStart))
        // Długie tytuły (np. „POZIOMY GRACZY”) mniejszą czcionką, żeby nie wchodziły na przycisk i monety.
        PixelText(title, size = if (title.length > LONG_TITLE) 3 else 4, modifier = Modifier.align(Alignment.Center))
        if (showCoins) CoinCounter(repo.progress.coins, Modifier.align(Alignment.CenterEnd), size = 3)
    }
}

private const val LONG_TITLE = 11

/** Przyciemnienie + wyśrodkowany panel. Kliknięcie w tło nic nie robi (chroni przed przypadkowym zamknięciem). */
@Composable
fun Overlay(onDismiss: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(enabled = onDismiss != null) { onDismiss?.invoke() }
    val primary = remember { FocusRequester() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Pal.Scrim)
            // Tło tylko zatrzymuje dotyk – klawiatura po nim nie chodzi.
            .focusProperties { canFocus = false }
            .noRippleClick { }
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        // Za wysoki panel (niski tablet, duża czcionka) przewija się zamiast uciąć przyciski na dole.
        PixelPanel(
            Modifier
                .keyLayer(trap = true, autoFocus = true, initial = primary)
                .padding(px(10))
                .widthIn(max = px(320))
                .fillMaxWidth(),
            scrollable = true,
        ) {
            CompositionLocalProvider(LocalPrimaryFocus provides primary) { content() }
        }
    }
}

@Composable
fun ConfirmDialog(message: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Overlay(onDismiss = onCancel) {
        PixelText(message, size = 3, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        VSpace(10)
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(px(6))) {
            PixelButton(stringResource(R.string.cancel), onClick = onCancel, style = ButtonStyle.SEA, modifier = Modifier.weight(1f).fillMaxHeight())
            PixelButton(stringResource(R.string.confirm), onClick = onConfirm, style = ButtonStyle.RED, modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
}
