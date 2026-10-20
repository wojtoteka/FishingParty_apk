package wojtoteka.ovh.fishingparty.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.level.Worlds
import wojtoteka.ovh.fishingparty.online.Announcement
import wojtoteka.ovh.fishingparty.online.VersionBlock

/** Język treści z serwera – ten sam, w którym gra wyświetla napisy (polski albo angielski). */
@Composable
private fun polishContent(): Boolean = stringResource(R.string.content_language) == "pl"

/** Ogłoszenie z panelu serwera – pokazywane raz, przy wejściu do gry. */
@Composable
fun AnnouncementDialog(announcement: Announcement, onClose: () -> Unit) {
    val polish = polishContent()
    Overlay(onDismiss = onClose) {
        PixelText(stringResource(R.string.announcement_label), size = 1, color = Pal.Wood, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        VSpace(3)
        PixelText(announcement.title(polish), size = 3, color = Pal.WoodDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        VSpace(5)
        PixelText(announcement.body(polish), size = 2, color = Pal.WoodDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        VSpace(8)
        PixelButton(stringResource(R.string.close), onClick = onClose, style = ButtonStyle.SEA, modifier = Modifier.keyPrimary().fillMaxWidth())
    }
}

/**
 * Ta wersja gry jest zablokowana w panelu serwera – zamiast gry tylko komunikat z powodem
 * i przycisk do Google Play. „Wstecz” zamyka grę.
 */
@Composable
fun VersionBlockedScreen(block: VersionBlock) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val polish = polishContent()
    BackHandler { activity?.finish() }
    Box(Modifier.fillMaxSize().background(Pal.SeaDark)) {
        SeaBackdrop(Worlds.paletteOf(1), Modifier.fillMaxSize(), dim = 0.3f)
        Overlay {
            PixelText(stringResource(R.string.version_blocked_title), size = 4, color = Pal.RedDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            VSpace(5)
            PixelText(
                block.reason(polish) ?: stringResource(R.string.version_blocked_outdated),
                size = 2,
                color = Pal.WoodDark,
                outline = null,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            VSpace(4)
            PixelText(stringResource(R.string.version_blocked_info), size = 1, color = Pal.WoodDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            VSpace(8)
            PixelButton(
                stringResource(R.string.version_blocked_store),
                onClick = { openStore(context) },
                style = ButtonStyle.GREEN,
                size = 2,
                modifier = Modifier.keyPrimary().fillMaxWidth(),
            )
            VSpace(4)
            PixelButton(stringResource(R.string.version_blocked_quit), onClick = { activity?.finish() }, style = ButtonStyle.SEA, size = 2, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Strona gry w Google Play – aplikacja sklepu, a bez niej przeglądarka. */
private fun openStore(context: Context) {
    val id = context.packageName
    for (url in listOf("market://details?id=$id", "https://play.google.com/store/apps/details?id=$id")) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {
        }
    }
}
