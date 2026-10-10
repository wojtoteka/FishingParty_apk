package wojtoteka.ovh.fishingparty.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.data.Achievement

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
        PixelText(title, size = 4, modifier = Modifier.align(Alignment.Center))
        if (showCoins) CoinCounter(repo.progress.coins, Modifier.align(Alignment.CenterEnd), size = 3)
    }
}

/** Przyciemnienie + wyśrodkowany panel. Kliknięcie w tło nic nie robi (chroni przed przypadkowym zamknięciem). */
@Composable
fun Overlay(onDismiss: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(enabled = onDismiss != null) { onDismiss?.invoke() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Pal.Scrim)
            .noRippleClick { }
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        PixelPanel(
            Modifier
                .padding(px(10))
                .widthIn(max = px(320))
                .fillMaxWidth(),
            content = content,
        )
    }
}

@Composable
fun ConfirmDialog(message: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Overlay(onDismiss = onCancel) {
        PixelText(message, size = 3, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        VSpace(10)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(6))) {
            PixelButton(stringResource(R.string.cancel), onClick = onCancel, style = ButtonStyle.SEA, modifier = Modifier.weight(1f))
            PixelButton(stringResource(R.string.confirm), onClick = onConfirm, style = ButtonStyle.RED, modifier = Modifier.weight(1f))
        }
    }
}

/** Baner „Osiągnięcie!” wysuwany z góry ekranu. */
@Composable
fun BoxScope.AchievementBanner(achievement: Achievement?) {
    AnimatedVisibility(
        visible = achievement != null,
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it },
        modifier = Modifier
            .align(Alignment.TopCenter)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .padding(top = px(30)),
    ) {
        if (achievement != null) {
            DarkCard(highlight = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SpriteImage(achievement.icon, 2)
                    HSpace(4)
                    Column {
                        PixelText(stringResource(R.string.achievement_unlocked), size = 1, color = Pal.Gold)
                        PixelText(achievementTitle(achievement), size = 2)
                    }
                }
            }
        }
    }
}
