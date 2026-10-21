package wojtoteka.ovh.fishingparty.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.game.Sfx
import wojtoteka.ovh.fishingparty.level.UserLevels
import wojtoteka.ovh.fishingparty.online.COMMUNITY_MIN_PLAYERS
import wojtoteka.ovh.fishingparty.online.LevelDetails
import wojtoteka.ovh.fishingparty.online.Net
import wojtoteka.ovh.fishingparty.online.OnlineLevel
import wojtoteka.ovh.fishingparty.online.OnlineResult
import wojtoteka.ovh.fishingparty.online.PlayerState

/** Sposoby sortowania listy poziomów graczy (jak zakładki poziomów online w Geometry Dash). */
enum class LevelSort(val id: String) { FEATURED("featured"), RECENT("recent"), POPULAR("popular"), HARD("hard") }

/**
 * Stan przeglądarki poziomów graczy – trzymany poza ekranem, żeby po zagraniu w poziom
 * lista, zakładka i wyszukiwanie zostały takie same.
 */
class CommunityBrowser {
    var sort by mutableStateOf(LevelSort.RECENT)
    var query by mutableStateOf("")
    val levels = mutableStateListOf<OnlineLevel>()
    var page by mutableStateOf(0)
    var more by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var loaded by mutableStateOf(false)
    var error by mutableStateOf<OnlineResult.Failed?>(null)
    var open by mutableStateOf<Int?>(null)

    fun reset() {
        levels.clear()
        page = 0
        more = false
        loaded = false
    }
}

@Composable
fun CommunityScreen(browser: CommunityBrowser, onBack: () -> Unit, onPlay: (LevelDetails) -> Unit, onCopied: (String) -> Unit) {
    val services = LocalServices.current
    val online = services.online
    val (isOnline, recheck) = rememberOnline()
    val scope = rememberCoroutineScope()
    fun load(next: Boolean) {
        if (browser.loading) return
        browser.loading = true
        browser.error = null
        val page = if (next) browser.page + 1 else 0
        scope.launch {
            when (val res = online.community.levels(browser.sort.id, page, browser.query)) {
                is Net.Ok -> {
                    if (!next) browser.levels.clear()
                    browser.levels.addAll(res.value.levels.filter { l -> browser.levels.none { it.id == l.id } })
                    browser.page = page
                    browser.more = res.value.more
                    browser.loaded = true
                }
                is Net.Err -> browser.error = res.failure
            }
            browser.loading = false
        }
    }
    LaunchedEffect(browser.sort, isOnline) {
        if (isOnline && online.configured && !browser.loaded) load(next = false)
    }

    ScreenFrame(stringResource(R.string.community_title), onBack) {
        when {
            !online.configured -> Column(Modifier.fillMaxSize().padding(px(10)), horizontalAlignment = Alignment.CenterHorizontally) { NotConfiguredCard() }
            !isOnline -> Column(Modifier.fillMaxSize().padding(px(10)), horizontalAlignment = Alignment.CenterHorizontally) { OfflineCard(recheck) }
            else -> Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = px(6)), horizontalAlignment = Alignment.CenterHorizontally) {
                    ButtonRow(Modifier.widthIn(max = px(360)), gap = 2) {
                        for (sort in LevelSort.entries) {
                            PixelButton(
                                stringResource(
                                    when (sort) {
                                        LevelSort.FEATURED -> R.string.community_featured
                                        LevelSort.RECENT -> R.string.community_recent
                                        LevelSort.POPULAR -> R.string.community_popular
                                        LevelSort.HARD -> R.string.community_hard
                                    },
                                ),
                                onClick = {
                                    if (browser.sort != sort) {
                                        browser.sort = sort
                                        browser.reset()
                                    }
                                },
                                style = if (browser.sort == sort) ButtonStyle.GREEN else ButtonStyle.SEA,
                                size = 1,
                            )
                        }
                    }
                    VSpace(3)
                    var text by remember { mutableStateOf(browser.query) }
                    val search = {
                        browser.query = text.trim()
                        browser.reset()
                        load(next = false)
                    }
                    Row(Modifier.fillMaxWidth().widthIn(max = px(360)), verticalAlignment = Alignment.Bottom) {
                        PixelTextField(text, { text = it }, stringResource(R.string.community_search), maxLength = 40, imeAction = ImeAction.Done, onDone = search, modifier = Modifier.weight(1f))
                        HSpace(3)
                        PixelButton(stringResource(R.string.community_search_go), onClick = search, style = ButtonStyle.SAND, size = 1)
                    }
                    browser.error?.let {
                        VSpace(3)
                        Hint(onlineErrorText(it), Pal.RedLight)
                        VSpace(2)
                        PixelButton(stringResource(R.string.online_retry), onClick = { load(next = false) }, style = ButtonStyle.GREEN, size = 2)
                    }
                    if (browser.loading && browser.levels.isEmpty()) {
                        VSpace(6)
                        Hint(stringResource(R.string.online_loading), Pal.Gold)
                    }
                    if (browser.loaded && browser.levels.isEmpty()) {
                        VSpace(8)
                        Hint(stringResource(R.string.community_empty), Pal.Foam)
                    }
                }
                val list = rememberListState()
                LazyColumn(
                    Modifier.fillMaxSize().keyScroll(list),
                    state = list,
                    contentPadding = PaddingValues(start = px(6), top = px(5), end = px(6), bottom = px(BOTTOM_GAP)),
                    verticalArrangement = Arrangement.spacedBy(px(3)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(browser.levels, key = { it.id }) { level ->
                        OnlineLevelCard(
                            level,
                            done = services.repo.progress.userLevelDone(level.id),
                            modifier = Modifier.fillMaxWidth().widthIn(max = px(360)).noRippleClick {
                                services.audio.play(Sfx.CLICK)
                                browser.open = level.id
                            },
                        )
                    }
                    if (browser.more) {
                        item {
                            PixelButton(stringResource(R.string.community_more), onClick = { load(next = true) }, enabled = !browser.loading, style = ButtonStyle.SEA, size = 2)
                        }
                    }
                }
            }
        }
    }
    browser.open?.let { id ->
        LevelDetailDialog(
            id,
            onPlay = {
                browser.open = null
                onPlay(it)
            },
            onCopied = onCopied,
            onClose = { browser.open = null },
            onChanged = { changed -> browser.levels.replaceAll { if (it.id == changed.id) changed else it } },
        )
    }
}

/** Karta poziomu gracza: stopień trudności, nazwa, autor, liczby i punkty za ukończenie. */
@Composable
fun OnlineLevelCard(level: OnlineLevel, done: Boolean, modifier: Modifier = Modifier, extra: (@Composable ColumnScope.() -> Unit)? = null) {
    DarkCard(modifier, highlight = level.featured) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TierIcon(level.tier, 2)
                HSpace(4)
                Column(Modifier.weight(1f)) {
                    PixelText(level.title, size = 2, maxLines = 1)
                    PixelText("#${level.id} · " + stringResource(R.string.community_by, level.creator), size = 1, color = Pal.Foam, maxLines = 1)
                    PixelText(
                        stringResource(R.string.intro_stages, level.stages) + " · " + stringResource(R.string.community_players) + " " + level.players +
                            (level.completionRate?.let { " · " + stringResource(R.string.community_rate) + " $it%" } ?: "") +
                            " · " + stringResource(R.string.community_likes) + " " + level.likes,
                        size = 1,
                        color = Pal.SeaLight,
                    )
                }
                HSpace(3)
                Column(horizontalAlignment = Alignment.End) {
                    PixelText(tierName(level.tier), size = 1, color = tierColor(level.tier))
                    PixelText(
                        if (level.rated) stringResource(R.string.community_points, level.points) else stringResource(R.string.community_unrated),
                        size = 1,
                        color = if (level.rated) Pal.Gold else Pal.GreyLight,
                    )
                    if (done) PixelText(stringResource(R.string.community_done), size = 1, color = Pal.GreenLight)
                }
            }
            extra?.invoke(this)
        }
    }
}

@Composable
private fun LevelDetailDialog(
    id: Int,
    onPlay: (LevelDetails) -> Unit,
    onCopied: (String) -> Unit,
    onClose: () -> Unit,
    onChanged: (OnlineLevel) -> Unit,
) {
    val services = LocalServices.current
    val online = services.online
    var details by remember(id) { mutableStateOf<LevelDetails?>(null) }
    var error by remember(id) { mutableStateOf<OnlineResult.Failed?>(null) }
    var info by remember(id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id) {
        when (val res = online.community.level(id)) {
            is Net.Ok -> details = res.value
            is Net.Err -> error = res.failure
        }
    }
    val reported = stringResource(R.string.community_reported)
    val copied = stringResource(R.string.community_copied)
    Overlay(onDismiss = onClose) {
        val d = details
        if (d == null) {
            PixelText(error?.let { onlineErrorText(it) } ?: stringResource(R.string.online_loading), size = 2, color = Pal.WoodDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            VSpace(6)
            PixelButton(stringResource(R.string.close), onClick = onClose, style = ButtonStyle.WOOD, size = 2, modifier = Modifier.fillMaxWidth())
            return@Overlay
        }
        val level = d.level
        val me = d.me
        val plan = remember(d) { UserLevels.build(d.data).plan }
        Box(Modifier.fillMaxWidth()) {
            PixelButton("X", onClick = onClose, style = ButtonStyle.RED, size = 3, modifier = Modifier.align(Alignment.TopEnd))
            Column(Modifier.align(Alignment.TopCenter).padding(horizontal = px(18)), horizontalAlignment = Alignment.CenterHorizontally) {
                PixelText(level.title, size = 3, align = TextAlign.Center)
                PixelText("#${level.id} · " + stringResource(R.string.community_by, level.creator), size = 1, color = Pal.WoodDark, outline = null)
            }
        }
        VSpace(5)
        DarkCard(Modifier.fillMaxWidth(), highlight = level.featured) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TierIcon(level.tier, 2)
                    HSpace(4)
                    Column(Modifier.weight(1f)) {
                        PixelText(tierName(level.tier), size = 3, color = tierColor(level.tier))
                        PixelText(
                            if (level.rated) stringResource(R.string.community_points, level.points) else stringResource(R.string.community_unrated),
                            size = 2,
                            color = if (level.rated) Pal.Gold else Pal.GreyLight,
                        )
                    }
                }
                if (!level.rated) {
                    VSpace(2)
                    PixelText(stringResource(R.string.community_rating_pending, COMMUNITY_MIN_PLAYERS), size = 1, color = Pal.Foam)
                }
                VSpace(3)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(3))) {
                    StatTile(stringResource(R.string.community_players), level.players.toString(), Modifier.weight(1f))
                    StatTile(stringResource(R.string.community_rate), level.completionRate?.let { "$it%" } ?: "-", Modifier.weight(1f))
                    StatTile(stringResource(R.string.community_likes), level.likes.toString(), Modifier.weight(1f))
                }
                VSpace(2)
                PixelText(stringResource(R.string.intro_stages, level.stages) + " · " + typeDescription(plan.spec.type), size = 1, color = Pal.SeaLight)
            }
        }
        VSpace(4)
        for (goal in goalTexts(plan.spec)) {
            Row(Modifier.fillMaxWidth().padding(vertical = px(1)), verticalAlignment = Alignment.CenterVertically) {
                Plate(pad = 1) { SpriteImage("fishscore_grey", 2) }
                HSpace(4)
                PixelText(goal, size = 2, color = Pal.Outline, outline = null)
            }
        }
        if (me != null && me.attempts > 0) {
            VSpace(3)
            PixelText(
                stringResource(R.string.community_attempts, me.attempts) + (if (me.best > 0) " · " + stringResource(R.string.intro_best, me.best) else ""),
                size = 2,
                color = Pal.WoodDark,
                outline = null,
            )
        }
        VSpace(4)
        PixelText(stringResource(R.string.community_no_powerups), size = 1, color = Pal.WoodDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (me?.creator == true) {
            PixelText(stringResource(R.string.community_own), size = 1, color = Pal.RedDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        } else if (!online.loggedIn) {
            PixelText(stringResource(R.string.community_guest), size = 1, color = Pal.RedDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        VSpace(6)
        PixelButton(stringResource(R.string.play), onClick = { onPlay(d) }, style = ButtonStyle.GREEN, size = 5, modifier = Modifier.keyPrimary().fillMaxWidth())
        VSpace(4)
        val canVote = me != null && me.attempts > 0 && !me.creator
        ButtonRow(gap = 3) {
            for ((value, label) in listOf(1 to R.string.community_like, -1 to R.string.community_dislike)) {
                PixelButton(
                    stringResource(label),
                    onClick = {
                        scope.launch {
                            val target = if (me?.vote == value) 0 else value
                            when (val res = online.community.vote(level.id, target)) {
                                is Net.Ok -> {
                                    details = LevelDetails(res.value, d.data, me?.withVote(target))
                                    onChanged(res.value)
                                }
                                is Net.Err -> error = res.failure
                            }
                        }
                    },
                    enabled = canVote,
                    style = if (me?.vote == value) ButtonStyle.GREEN else ButtonStyle.SEA,
                    size = 1,
                )
            }
            PixelButton(
                stringResource(R.string.community_report),
                onClick = {
                    scope.launch {
                        when (val res = online.community.report(level.id)) {
                            is Net.Ok -> info = reported
                            is Net.Err -> error = res.failure
                        }
                    }
                },
                enabled = me?.reported != true && me?.creator != true && info == null,
                style = ButtonStyle.WOOD,
                size = 1,
            )
        }
        if (level.copyable) {
            VSpace(3)
            PixelButton(
                stringResource(R.string.community_copy),
                onClick = {
                    val draft = services.drafts.create(d.data)
                    info = copied
                    onCopied(draft.id)
                },
                style = ButtonStyle.SAND,
                size = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        info?.let {
            VSpace(3)
            PixelText(it, size = 1, color = Pal.GreenDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        error?.let {
            VSpace(3)
            PixelText(onlineErrorText(it), size = 1, color = Pal.RedDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Ten sam stan gracza z innym głosem. */
private fun PlayerState.withVote(vote: Int) = PlayerState(attempts, completed, best, vote, reported, creator)

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PixelText(label, size = 1, color = Pal.Foam, maxLines = 1)
        PixelText(value, size = 3, color = Pal.White)
    }
}
