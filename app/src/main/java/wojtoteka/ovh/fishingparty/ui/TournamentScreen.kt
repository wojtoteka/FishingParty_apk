package wojtoteka.ovh.fishingparty.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.data.PowerUp
import wojtoteka.ovh.fishingparty.game.Sfx
import wojtoteka.ovh.fishingparty.level.Tournament
import wojtoteka.ovh.fishingparty.online.Net
import wojtoteka.ovh.fishingparty.online.OnlineResult
import wojtoteka.ovh.fishingparty.online.TournamentBoard
import wojtoteka.ovh.fishingparty.online.TournamentEntry
import wojtoteka.ovh.fishingparty.online.TournamentPrize

/** Czy teksty z serwera (nazwa motywu turnieju) pokazać po polsku – według języka gry. */
@Composable
internal fun polishText(): Boolean = stringResource(R.string.content_language) == "pl"

/** „TURNIEJ WEEKENDOWY: SZTORM” – tytuł na ekranie gry i w oknie wyniku. */
@Composable
fun tournamentTitle(t: Tournament): String = stringResource(R.string.tournament_title) + ": " + t.name(polishText()).uppercase()

/**
 * Przycisk turnieju weekendowego w menu: w trakcie turnieju – ile zostało do końca, między turniejami – kiedy start.
 * „!” – nagroda z turnieju czeka na odebranie.
 */
@Composable
fun TournamentButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val services = LocalServices.current
    val repo = services.repo
    repo.revision.intValue
    val now = rememberGameNow()
    val running = repo.progress.currentTournament()
    // Turnieju nie ma w zapisie (pierwsze uruchomienie, nowy weekend) – pytamy serwer w tle.
    LaunchedEffect(running == null) {
        if (running == null) services.online.refreshTournaments()
    }
    val next = services.online.tournaments?.next ?: repo.save.tournamentCache
    val prize = services.online.account?.prizePending ?: 0L
    Box(modifier) {
        PixelButton(text = "", onClick = onClick, style = ButtonStyle.CRIMSON, size = 2, modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PixelText(stringResource(R.string.tournament_title), size = 2, align = TextAlign.Center)
                when {
                    running != null ->
                        PixelText(stringResource(R.string.tournament_ends_in, countdownText(running.endsAt - now)), size = 1, color = Pal.Gold)
                    next != null && next.startsAt > now ->
                        PixelText(stringResource(R.string.tournament_starts_in, countdownText(next.startsAt - now)), size = 1, color = Pal.Foam)
                    else -> PixelText(stringResource(R.string.tournament_soon), size = 1, color = Pal.Foam)
                }
            }
        }
        if (prize > 0) Badge("!", Modifier.align(Alignment.TopEnd))
    }
}

/**
 * Ekran turnieju weekendowego. W trakcie turnieju: poziom, rekord gracza, nagrody, wzmocnienia i ZAGRAJ (dowolnie
 * wiele prób) oraz ranking turnieju. Między turniejami: kiedy następny i wyniki poprzedniego. Nagroda z turnieju
 * czekająca na koncie – przycisk ODBIERZ.
 */
@Composable
fun TournamentScreen(onBack: () -> Unit, onPlay: (Tournament, Set<PowerUp>) -> Unit, onAccount: () -> Unit) {
    val services = LocalServices.current
    val repo = services.repo
    val online = services.online
    repo.revision.intValue
    val now = rememberGameNow()
    val (isOnline, recheck) = rememberOnline()
    val info = online.tournaments
    val running = repo.progress.currentTournament()
    var refreshKey by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf<OnlineResult.Failed?>(null) }
    var board by remember { mutableStateOf<TournamentBoard?>(null) }
    var boardFailed by remember { mutableStateOf<OnlineResult.Failed?>(null) }

    LaunchedEffect(refreshKey, isOnline) {
        if (!isOnline || !online.configured) return@LaunchedEffect
        loading = true
        failed = online.refreshTournaments(force = refreshKey > 0) as? OnlineResult.Failed
        loading = false
    }
    // Ranking trwającego turnieju, a między turniejami – poprzedniego (z miejscami i nagrodami).
    val shownId = running?.id ?: info?.current?.id ?: info?.previous?.id
    LaunchedEffect(shownId, refreshKey, isOnline, online.loggedIn) {
        if (shownId == null || !isOnline || !online.configured) return@LaunchedEffect
        when (val r = online.tournamentRanking(shownId)) {
            is Net.Ok -> {
                board = r.value
                boardFailed = null
            }
            is Net.Err -> boardFailed = r.failure
        }
    }
    val shownBoard = board?.takeIf { it.tournament.id == shownId }

    ScreenFrame(stringResource(R.string.tournament_screen), onBack) {
        val list = rememberListState()
        LazyColumn(
            Modifier.fillMaxSize().keyScroll(list).windowInsetsPadding(WindowInsets.navigationBars),
            state = list,
            contentPadding = PaddingValues(start = px(6), top = px(4), end = px(6), bottom = px(BOTTOM_GAP)),
            verticalArrangement = Arrangement.spacedBy(px(3)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(Modifier.fillMaxWidth().widthIn(max = px(340)), horizontalAlignment = Alignment.CenterHorizontally) {
                    PrizeClaimCard()
                    when {
                        running != null -> RunningTournament(running, shownBoard, info?.prizes.orEmpty(), now, onPlay, onAccount)
                        !online.configured -> NotConfiguredCard()
                        !isOnline -> OfflineCard(recheck)
                        info == null -> PixelPanel(Modifier.fillMaxWidth()) {
                            PixelText(
                                if (failed != null && !loading) onlineErrorText(failed!!) else stringResource(R.string.tournament_loading),
                                size = 2,
                                color = if (failed != null && !loading) Pal.RedDark else Pal.WoodDark,
                                outline = null,
                                align = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (failed != null && !loading) {
                                VSpace(6)
                                PixelButton(stringResource(R.string.online_retry), onClick = { refreshKey++ }, style = ButtonStyle.GREEN, size = 2)
                            }
                        }
                        else -> NextTournament(info.next, info.prizes, now)
                    }
                    VSpace(4)
                    if (shownBoard != null) {
                        val finished = shownBoard.tournament.hasEnded(now)
                        if (finished) {
                            PixelText(stringResource(R.string.tournament_previous, shownBoard.tournament.name(polishText()).uppercase()), size = 2, color = Pal.Gold)
                            if (!shownBoard.finalized) Hint(stringResource(R.string.tournament_counting), Pal.Foam)
                            val mine = shownBoard.me
                            if (shownBoard.finalized && mine != null && mine.rank != null) {
                                Hint(stringResource(R.string.tournament_your_prize, mine.prizeCoins, mine.prizePoints), Pal.GreenLight)
                            }
                            VSpace(2)
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                PixelText(stringResource(R.string.tournament_ranking), size = 3)
                                PixelText(stringResource(R.string.tournament_players, shownBoard.participants), size = 1, color = Pal.Foam)
                            }
                            PixelButton(stringResource(R.string.ranking_refresh), onClick = { refreshKey++ }, style = ButtonStyle.SEA, size = 2, enabled = !loading)
                        }
                        if (shownBoard.entries.isEmpty()) {
                            VSpace(6)
                            Hint(stringResource(R.string.tournament_ranking_empty), Pal.Foam)
                        }
                    }
                    boardFailed?.let {
                        VSpace(2)
                        Hint(onlineErrorText(it), Pal.RedLight)
                    }
                }
            }
            items(shownBoard?.entries.orEmpty(), key = { it.id }) { e ->
                TournamentRow(e, mine = e.id == shownBoard?.myId, finalized = shownBoard?.finalized == true)
            }
        }
    }
}

/** Trwający turniej: poziom, rekord i miejsce gracza, nagrody, wzmocnienia i ZAGRAJ. */
@Composable
private fun RunningTournament(
    t: Tournament,
    board: TournamentBoard?,
    prizes: List<TournamentPrize>,
    now: Long,
    onPlay: (Tournament, Set<PowerUp>) -> Unit,
    onAccount: () -> Unit,
) {
    val services = LocalServices.current
    val online = services.online
    val plan = remember(t.id, t.seed) { t.plan() }
    val spec = plan.spec
    val worldName = rememberWorldNames()
    val selected = remember(t.id) { mutableStateListOf<PowerUp>() }
    // Rekord z tego telefonu albo z serwera (np. z drugiego telefonu tego konta).
    val best = maxOf(services.repo.progress.tournamentBest(t.id), board?.me?.score ?: 0)
    val rank = board?.me?.rank
    PixelPanel(Modifier.fillMaxWidth()) {
        PixelText(stringResource(R.string.tournament_title), size = 2, color = Pal.Outline, outline = null)
        PixelText(t.name(polishText()).uppercase(), size = 4, color = Pal.Crimson, align = TextAlign.Center)
        PixelText(stringResource(R.string.tournament_ends_in, countdownText(t.endsAt - now)), size = 2, color = Pal.RedDark, outline = null)
        VSpace(2)
        PixelText(stringResource(R.string.tournament_info), size = 1, color = Pal.WoodDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        VSpace(4)
        DarkCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                PixelText(worldName(spec.world), size = 1, color = Pal.SeaLight)
                PixelText(typeName(spec.type), size = 3, color = Pal.Gold)
                VSpace(2)
                PixelText(typeDescription(spec.type), size = 1, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                VSpace(2)
                PixelText(stringResource(R.string.intro_stages, plan.stages.size), size = 1, color = Pal.SeaLight)
            }
        }
        VSpace(4)
        DarkCard(Modifier.fillMaxWidth(), highlight = best > 0) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    PixelText(stringResource(R.string.tournament_best_label), size = 1, color = Pal.Gold)
                    PixelText(if (best > 0) best.toString() else stringResource(R.string.tournament_no_score), size = if (best > 0) 4 else 1)
                }
                if (rank != null) PixelText(stringResource(R.string.tournament_my_place, rank), size = 2, color = Pal.GreenLight)
            }
        }
        if (prizes.isNotEmpty()) {
            VSpace(4)
            PrizeTable(prizes)
        }
        if (!online.loggedIn) {
            VSpace(4)
            PixelText(stringResource(R.string.tournament_guest), size = 1, color = Pal.RedDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            VSpace(2)
            PixelButton(stringResource(R.string.register_button), onClick = onAccount, style = ButtonStyle.GREEN, size = 2)
        }
        VSpace(5)
        PowerUpPicker(selected)
        VSpace(6)
        PixelButton(
            stringResource(R.string.play),
            onClick = { onPlay(t, selected.toSet()) },
            style = ButtonStyle.CRIMSON,
            size = 5,
            modifier = Modifier.keyPrimary().fillMaxWidth(),
        )
    }
}

/** Między turniejami: kiedy startuje następny (z motywem) i jakie są nagrody. */
@Composable
private fun NextTournament(next: Tournament?, prizes: List<TournamentPrize>, now: Long) {
    PixelPanel(Modifier.fillMaxWidth()) {
        PixelText(stringResource(R.string.tournament_next), size = 2, color = Pal.Outline, outline = null)
        if (next != null) {
            PixelText(next.name(polishText()).uppercase(), size = 4, color = Pal.Crimson, align = TextAlign.Center)
            if (next.startsAt > now) {
                PixelText(stringResource(R.string.tournament_starts_in, countdownText(next.startsAt - now)), size = 2, color = Pal.RedDark, outline = null)
            }
        } else {
            PixelText(stringResource(R.string.tournament_not_running), size = 2, color = Pal.WoodDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        VSpace(3)
        PixelText(stringResource(R.string.tournament_info), size = 1, color = Pal.WoodDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (prizes.isNotEmpty()) {
            VSpace(4)
            PrizeTable(prizes)
        }
    }
}

/** Nagrody za miejsca: podium osobno, dalej przedziały i „każdy gracz”. */
@Composable
private fun PrizeTable(prizes: List<TournamentPrize>) {
    DarkCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            PixelText(stringResource(R.string.tournament_prizes), size = 2, color = Pal.Gold)
            VSpace(1)
            val everyone = stringResource(R.string.tournament_prize_everyone)
            for (p in prizes) {
                val places = when {
                    p.to == null -> everyone
                    p.to == p.from -> "#${p.from}"
                    else -> "#${p.from}–${p.to}"
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PixelText(places, size = 1, color = if (p.from <= 3) placeColor(p.from) else Pal.Foam, modifier = Modifier.weight(1f))
                    PixelText("+${p.coins}", size = 1, color = Pal.Gold)
                    HSpace(1)
                    SpriteImage("fishscore_gold", 1)
                    HSpace(3)
                    PixelText("+${p.points}", size = 1, color = Pal.SeaLight)
                    HSpace(1)
                    SpriteImage("fishscore_blue", 1)
                }
            }
        }
    }
}

/** Nagroda z turnieju czeka na koncie – przycisk ODBIERZ dodaje monety w grze. */
@Composable
private fun PrizeClaimCard() {
    val services = LocalServices.current
    val online = services.online
    val pending = online.account?.prizePending ?: 0L
    var claiming by remember { mutableStateOf(false) }
    var claimed by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<OnlineResult.Failed?>(null) }
    val scope = rememberCoroutineScope()
    if (pending <= 0 && claimed <= 0) return
    DarkCard(Modifier.fillMaxWidth(), highlight = true) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            PixelText(stringResource(R.string.tournament_prize_waiting), size = 2, color = Pal.Gold)
            VSpace(2)
            if (pending > 0) {
                CoinCounter(pending, size = 3)
                VSpace(3)
                PixelButton(
                    stringResource(R.string.tournament_claim),
                    onClick = {
                        if (!claiming) {
                            claiming = true
                            error = null
                            scope.launch {
                                when (val r = online.claimPrizes()) {
                                    is Net.Ok -> {
                                        claimed += r.value
                                        if (r.value > 0) services.audio.play(Sfx.COIN)
                                    }
                                    is Net.Err -> error = r.failure
                                }
                                claiming = false
                            }
                        }
                    },
                    style = ButtonStyle.GREEN,
                    size = 3,
                    enabled = !claiming,
                )
            }
            if (claimed > 0) PixelText(stringResource(R.string.tournament_claimed, claimed), size = 2, color = Pal.GreenLight)
            error?.let { Hint(onlineErrorText(it), Pal.RedLight) }
        }
    }
    VSpace(4)
}

/** Wiersz rankingu turnieju; po rozliczeniu z nagrodą w monetach. */
@Composable
private fun TournamentRow(e: TournamentEntry, mine: Boolean, finalized: Boolean) {
    DarkCard(Modifier.fillMaxWidth().widthIn(max = px(340)).keyStop(), highlight = mine) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.widthIn(min = px(30))) { PixelText("#${e.rank}", size = 2, color = placeColor(e.rank)) }
            HSpace(3)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PixelText(e.nick, size = 2, color = if (mine) Pal.Gold else Pal.White)
                    if (mine) {
                        HSpace(3)
                        PixelText(stringResource(R.string.ranking_you), size = 1, color = Pal.GreenLight)
                    }
                }
                if (finalized && e.prizeCoins > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PixelText("+${e.prizeCoins}", size = 1, color = Pal.Gold)
                        HSpace(1)
                        SpriteImage("fishscore_gold", 1)
                    }
                }
            }
            PixelText(e.score.toString(), size = 2, color = Pal.Gold)
        }
    }
}

/** Kolor miejsca: złoto, srebro, brąz, dalej morski. */
internal fun placeColor(rank: Int) = when (rank) {
    1 -> Pal.Gold
    2 -> Pal.GreyLight
    3 -> Pal.WoodLight
    else -> Pal.SeaLight
}
