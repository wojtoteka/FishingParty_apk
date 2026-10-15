package wojtoteka.ovh.fishingparty.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.data.ChallengeResult
import wojtoteka.ovh.fishingparty.data.PowerUp
import wojtoteka.ovh.fishingparty.level.Challenge
import wojtoteka.ovh.fishingparty.level.ChallengeKind
import wojtoteka.ovh.fishingparty.online.OnlineResult

/** Czas gry (czas serwera, gdy telefon go zna) odświeżany co sekundę – do odliczania. */
@Composable
fun rememberGameNow(): Long {
    val clock = LocalServices.current.repo.clock
    var now by remember { mutableLongStateOf(clock.now()) }
    LaunchedEffect(clock) {
        while (isActive) {
            now = clock.now()
            delay(1000L - now % 1000L)
        }
    }
    return now
}

/** Odliczanie: „05:12:33”, a przy dłuższych okresach „2 DNI 05:12:33”. */
@Composable
fun countdownText(ms: Long): String {
    val total = ((ms + 999) / 1000).coerceAtLeast(0)
    val days = (total / 86_400).toInt()
    val hms = "%02d:%02d:%02d".format(total % 86_400 / 3600, total % 3600 / 60, total % 60)
    return if (days > 0) pluralStringResource(R.plurals.streak_days, days, days) + " " + hms else hms
}

@Composable
fun challengeTitle(kind: ChallengeKind): String =
    stringResource(if (kind == ChallengeKind.DAILY) R.string.daily_level else R.string.weekly_level)

fun challengeStyle(kind: ChallengeKind): ButtonStyle = if (kind == ChallengeKind.DAILY) ButtonStyle.ORANGE else ButtonStyle.PURPLE

private fun challengeAccent(kind: ChallengeKind): Color = if (kind == ChallengeKind.DAILY) Pal.Orange else Pal.Purple

/**
 * Kolorowy przycisk poziomu dnia/tygodnia w menu. Pod nazwą: ile zostało do końca okresu,
 * a po ukończeniu – za ile pojawi się nowy poziom. „NOWY!” – poziom czeka na rozegranie.
 */
@Composable
fun ChallengeButton(kind: ChallengeKind, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val services = LocalServices.current
    val repo = services.repo
    repo.revision.intValue
    val now = rememberGameNow()
    val challenge = repo.progress.currentChallenge(kind)
    val done = challenge?.let { repo.progress.challengeResult(kind, it.period) }
    // Brak seeda (pierwsze uruchomienie, nowy dzień) – pobieramy go w tle.
    LaunchedEffect(challenge == null) {
        if (challenge == null) services.online.refreshChallenges()
    }
    Box(modifier) {
        PixelButton(text = "", onClick = onClick, style = challengeStyle(kind), size = 2, modifier = Modifier.fillMaxSize()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PixelText(challengeTitle(kind), size = 2, align = TextAlign.Center)
                when {
                    challenge != null && done != null ->
                        PixelText(stringResource(R.string.challenge_new_in, countdownText(challenge.endsAt - now)), size = 1, color = Pal.GreenLight)
                    challenge != null ->
                        PixelText(stringResource(R.string.challenge_left, countdownText(challenge.endsAt - now)), size = 1, color = Pal.Foam)
                    else -> PixelText(stringResource(R.string.challenge_points_short, kind.pointsMultiplier), size = 1, color = Pal.Foam)
                }
            }
        }
        if (done == null) Badge(stringResource(R.string.challenge_new), Modifier.align(Alignment.TopEnd))
    }
}

/**
 * Okno poziomu dnia/tygodnia: przed rozegraniem karta poziomu z nagrodami i wzmocnieniami,
 * po ukończeniu – wynik i odliczanie do nowego poziomu.
 */
@Composable
fun ChallengeDialog(kind: ChallengeKind, onPlay: (Challenge, Set<PowerUp>) -> Unit, onClose: () -> Unit) {
    val services = LocalServices.current
    val repo = services.repo
    val online = services.online
    repo.revision.intValue
    val now = rememberGameNow()
    val challenge = repo.progress.currentChallenge(kind)
    val done = challenge?.let { repo.progress.challengeResult(kind, it.period) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<OnlineResult.Failed?>(null) }
    val scope = rememberCoroutineScope()
    val refresh = {
        if (!loading) {
            loading = true
            error = null
            scope.launch {
                val result = online.refreshChallenges()
                loading = false
                error = result as? OnlineResult.Failed
            }
        }
    }
    // Nie ma jeszcze seeda albo okres właśnie się skończył – pobieramy nowy.
    LaunchedEffect(challenge == null) { if (challenge == null) refresh() }

    Overlay(onDismiss = onClose) {
        Box(Modifier.fillMaxWidth()) {
            PixelButton("X", onClick = onClose, style = ButtonStyle.RED, size = 3, modifier = Modifier.align(Alignment.TopEnd))
            Column(Modifier.align(Alignment.TopCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                // Odstęp po bokach, żeby tytuł („POZIOM TYGODNIA”) i opis nie wchodziły na przycisk zamknięcia.
                PixelText(
                    challengeTitle(kind),
                    size = 3,
                    color = challengeAccent(kind),
                    align = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = px(18)),
                )
                PixelText(
                    stringResource(if (kind == ChallengeKind.DAILY) R.string.challenge_daily_info else R.string.challenge_weekly_info),
                    size = 1,
                    color = Pal.Outline,
                    outline = null,
                    align = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = px(18)),
                )
            }
        }
        VSpace(6)
        when {
            challenge == null -> ChallengeLoading(loading, error, onRetry = refresh)
            done != null -> ChallengeCompleted(challenge, done, now, onClose)
            else -> ChallengeIntro(challenge, now, onPlay)
        }
    }
}

@Composable
private fun ChallengeLoading(loading: Boolean, error: OnlineResult.Failed?, onRetry: () -> Unit) {
    PixelText(
        when {
            loading -> stringResource(R.string.challenge_loading)
            error?.code == "offline" -> stringResource(R.string.challenge_offline)
            error != null -> onlineErrorText(error)
            else -> stringResource(R.string.challenge_loading)
        },
        size = 2,
        color = if (error != null && !loading) Pal.RedDark else Pal.WoodDark,
        outline = null,
        align = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    if (error != null && !loading) {
        VSpace(6)
        PixelButton(stringResource(R.string.online_retry), onClick = onRetry, style = ButtonStyle.GREEN, size = 2, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ChallengeCompleted(challenge: Challenge, done: ChallengeResult, now: Long, onClose: () -> Unit) {
    DarkCard(Modifier.fillMaxWidth(), highlight = true) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            PixelText(stringResource(R.string.challenge_done), size = 4, color = Pal.Gold)
            VSpace(2)
            PixelText(stringResource(R.string.challenge_your_score, done.score), size = 2)
            PixelText(stringResource(R.string.challenge_your_points, done.points), size = 2, color = Pal.GreenLight)
        }
    }
    VSpace(6)
    PixelText(stringResource(R.string.challenge_next), size = 2, color = Pal.Outline, outline = null)
    PixelText(countdownText(challenge.endsAt - now), size = 5)
    VSpace(8)
    PixelButton(stringResource(R.string.close), onClick = onClose, style = ButtonStyle.WOOD, size = 3, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ChallengeIntro(challenge: Challenge, now: Long, onPlay: (Challenge, Set<PowerUp>) -> Unit) {
    val online = LocalServices.current.online
    val plan = remember(challenge.kind, challenge.seed) { challenge.plan() }
    val spec = plan.spec
    val worldName = rememberWorldNames()
    val selected = remember(challenge.kind, challenge.period) { mutableStateListOf<PowerUp>() }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        PixelText(worldName(spec.world), size = 1, color = Pal.Outline, outline = null)
        PixelText(stringResource(R.string.challenge_left, countdownText(challenge.endsAt - now)), size = 2, color = Pal.RedDark, outline = null)
    }
    VSpace(4)
    DarkCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            PixelText(typeName(spec.type), size = 3, color = Pal.Gold)
            VSpace(2)
            PixelText(typeDescription(spec.type), size = 1, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            VSpace(2)
            PixelText(stringResource(R.string.intro_stages, plan.stages.size), size = 1, color = Pal.SeaLight)
        }
    }
    VSpace(4)
    // Nagrody: punkty do rankingu (tydzień liczy się podwójnie) i premia w monetach.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(4))) {
        RewardInfo(stringResource(R.string.challenge_points_label), "x${challenge.kind.pointsMultiplier}", "fishscore_blue", Modifier.weight(1f))
        RewardInfo(stringResource(R.string.challenge_bonus_label), "+${challenge.kind.coinBonus}", "fishscore_gold", Modifier.weight(1f))
    }
    VSpace(4)
    for (goal in goalTexts(spec)) {
        Row(Modifier.fillMaxWidth().padding(vertical = px(1)), verticalAlignment = Alignment.CenterVertically) {
            Plate(pad = 1) { SpriteImage("fishscore_grey", 2) }
            HSpace(4)
            PixelText(goal, size = 2, color = Pal.Outline, outline = null)
        }
    }
    VSpace(4)
    PixelText(stringResource(R.string.challenge_once), size = 1, color = Pal.WoodDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    if (!online.loggedIn) {
        VSpace(2)
        PixelText(stringResource(R.string.challenge_guest), size = 1, color = Pal.RedDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    VSpace(5)
    PowerUpPicker(selected)
    VSpace(8)
    PixelButton(
        stringResource(R.string.play),
        onClick = { onPlay(challenge, selected.toSet()) },
        style = challengeStyle(challenge.kind),
        size = 5,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RewardInfo(label: String, value: String, icon: String, modifier: Modifier) {
    DarkCard(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            PixelText(label, size = 1, color = Pal.Foam, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                PixelText(value, size = 3, color = Pal.Gold)
                HSpace(2)
                SpriteImage(icon, 2)
            }
        }
    }
}
