package wojtoteka.ovh.fishingparty.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.online.Announcement

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
