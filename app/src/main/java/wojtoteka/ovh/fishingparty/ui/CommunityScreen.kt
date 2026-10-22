package wojtoteka.ovh.fishingparty.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.game.Sfx
import wojtoteka.ovh.fishingparty.level.UserLevelRating
import wojtoteka.ovh.fishingparty.level.UserLevels
import wojtoteka.ovh.fishingparty.online.COMMUNITY_MIN_PLAYERS
import wojtoteka.ovh.fishingparty.online.LevelDetails
import wojtoteka.ovh.fishingparty.online.Net
import wojtoteka.ovh.fishingparty.online.OnlineLevel
import wojtoteka.ovh.fishingparty.online.OnlineResult
import wojtoteka.ovh.fishingparty.online.SharedLevel

/**
 * Sposoby sortowania listy poziomów graczy (jak zakładki poziomów online w Geometry Dash).
 * NAJLEPSZE – według gwiazdek od graczy: im więcej gwiazdek, tym wyżej.
 */
enum class LevelSort(val id: String) { FEATURED("featured"), STARS("stars"), RECENT("recent"), HARD("hard") }

/**
 * Stan przeglądarki poziomów graczy – trzymany poza ekranem, żeby po zagraniu w poziom
 * lista, zakładka i wyszukiwanie zostały takie same.
 */
class CommunityBrowser {
    var sort by mutableStateOf(LevelSort.STARS)
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
                                        LevelSort.STARS -> R.string.community_top
                                        LevelSort.RECENT -> R.string.community_recent
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

/** Karta poziomu gracza: stopień trudności, nazwa, autor, gwiazdki, liczby i punkty za ukończenie. */
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
                            (level.completionRate?.let { " · " + stringResource(R.string.community_rate) + " $it%" } ?: ""),
                        size = 1,
                        color = Pal.SeaLight,
                    )
                }
                HSpace(3)
                Column(horizontalAlignment = Alignment.End) {
                    StarCount(level.stars)
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
    var starring by remember(id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val share = rememberLinkShare()
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
                PixelText(level.title, size = dialogTitleSize(level.title), align = TextAlign.Center)
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
                    StatTile(stringResource(R.string.community_stars), level.stars.toString(), Modifier.weight(1f), icon = "fishscore_gold")
                    StatTile(stringResource(R.string.community_players), level.players.toString(), Modifier.weight(1f))
                    StatTile(stringResource(R.string.community_rate), level.completionRate?.let { "$it%" } ?: "-", Modifier.weight(1f))
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
        val starred = me?.starred == true
        // Gwiazdka: po zagraniu na tym telefonie i nie dla własnego poziomu; drugie dotknięcie ją cofa.
        PixelButton(
            stringResource(if (starred) R.string.community_unstar else R.string.community_star),
            onClick = {
                if (!starring && me != null) {
                    starring = true
                    error = null
                    scope.launch {
                        when (val res = online.community.star(level.id, !starred)) {
                            is Net.Ok -> {
                                details = LevelDetails(res.value.level, d.data, me.withStar(res.value.starred))
                                onChanged(res.value.level)
                                if (res.value.starred) services.audio.play(Sfx.STAR)
                            }
                            is Net.Err -> error = res.failure
                        }
                        starring = false
                    }
                }
            },
            enabled = me?.canStar == true && !starring,
            style = if (starred) ButtonStyle.GREEN else ButtonStyle.SAND,
            size = 2,
            icon = "fishscore_gold",
            modifier = Modifier.fillMaxWidth(),
        )
        if (me != null && me.attempts == 0 && !me.creator) {
            VSpace(2)
            PixelText(stringResource(R.string.community_star_hint), size = 1, color = Pal.WoodDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        VSpace(3)
        ButtonRow(gap = 3) {
            PixelButton(
                stringResource(R.string.community_share),
                onClick = { share.share(level.title, own = me?.creator == true) { lang -> online.community.shareLink(level.id, lang) } },
                enabled = !share.busy,
                style = ButtonStyle.SEA,
                size = 1,
            )
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
        (error ?: share.error)?.let {
            VSpace(3)
            PixelText(onlineErrorText(it), size = 1, color = Pal.RedDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Nazwa poziomu w okienku łamie się na spacjach – za długie słowo (np. kod seeda) dostaje mniejszą czcionkę, żeby nie wchodziło pod przycisk X. */
private fun dialogTitleSize(title: String): Int = if (title.split(' ').maxOf { it.length } > LONG_TITLE_WORD) 2 else 3

private const val LONG_TITLE_WORD = 12

/** Liczba gwiazdek od graczy ze złotą rybką (gwiazdki w grze to rybki). */
@Composable
fun StarCount(stars: Int, size: Int = 1) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SpriteImage("fishscore_gold", size)
        HSpace(2)
        PixelText(stars.toString(), size = size, color = Pal.Gold)
    }
}

@Composable
internal fun StatTile(label: String, value: String, modifier: Modifier, icon: String? = null) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PixelText(label, size = 1, color = Pal.Foam, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                SpriteImage(icon, 2)
                HSpace(2)
            }
            PixelText(value, size = 3, color = Pal.White)
        }
    }
}

/**
 * Udostępnianie linku do poziomu: prosi serwer o link (opublikowany poziom albo zapis z kreatora) i otwiera
 * systemowe okno „Udostępnij” (Messenger, Discord, SMS…). Link jest w języku gry – podgląd w komunikatorze też.
 */
class LinkShare internal constructor(private val scope: CoroutineScope, private val context: Context) {
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<OnlineResult.Failed?>(null)
        private set

    private val lang: String get() = if (context.resources.configuration.locales[0].language == "pl") "pl" else "en"

    fun share(title: String, own: Boolean, request: suspend (lang: String) -> Net<String>) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            when (val res = request(lang)) {
                is Net.Ok -> {
                    val text = context.getString(if (own) R.string.share_text_own else R.string.share_text, title, res.value)
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    try {
                        context.startActivity(Intent.createChooser(send, null))
                    } catch (_: ActivityNotFoundException) {
                        error = OnlineResult.Failed("unknown")
                    }
                }
                is Net.Err -> error = res.failure
            }
            busy = false
        }
    }
}

@Composable
fun rememberLinkShare(): LinkShare {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    return remember(scope, context) { LinkShare(scope, context) }
}

/**
 * Poziom z linku bez publikacji (/s/<kod>): nazwa, autor, trudność policzona w grze i cele gwiazdek.
 * Gra się w niego jak w poziom gracza (bez wzmocnień i kontynuacji), ale bez punktów, monet i statystyk.
 */
@Composable
fun SharedLevelDialog(code: String, onPlay: (SharedLevel) -> Unit, onClose: () -> Unit) {
    val online = LocalServices.current.online
    var shared by remember(code) { mutableStateOf(sharedCache[code]) }
    var error by remember(code) { mutableStateOf<OnlineResult.Failed?>(null) }
    var retry by remember(code) { mutableIntStateOf(0) }
    val (isOnline, recheck) = rememberOnline()
    LaunchedEffect(code, retry, isOnline) {
        if (shared != null || !isOnline) return@LaunchedEffect
        error = null
        when (val res = online.community.shared(code)) {
            is Net.Ok -> {
                sharedCache[code] = res.value
                while (sharedCache.size > 8) sharedCache.remove(sharedCache.keys.first())
                shared = res.value
            }
            is Net.Err -> error = res.failure
        }
    }
    Overlay(onDismiss = onClose) {
        val s = shared
        if (s == null) {
            val failure = error ?: if (!isOnline) OnlineResult.Failed("offline") else null
            PixelText(failure?.let { onlineErrorText(it) } ?: stringResource(R.string.online_loading), size = 2, color = Pal.WoodDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            VSpace(6)
            if (failure != null && failure.code != "not_found") {
                PixelButton(stringResource(R.string.online_retry), onClick = {
                    recheck()
                    retry++
                }, style = ButtonStyle.GREEN, size = 2, modifier = Modifier.fillMaxWidth())
                VSpace(3)
            }
            PixelButton(stringResource(R.string.close), onClick = onClose, style = ButtonStyle.WOOD, size = 2, modifier = Modifier.fillMaxWidth())
            return@Overlay
        }
        val plan = remember(s) { UserLevels.build(s.data).plan }
        val tier = remember(s) { UserLevelRating.estimate(s.data).tier }
        Box(Modifier.fillMaxWidth()) {
            PixelButton("X", onClick = onClose, style = ButtonStyle.RED, size = 3, modifier = Modifier.align(Alignment.TopEnd))
            Column(Modifier.align(Alignment.TopCenter).padding(horizontal = px(18)), horizontalAlignment = Alignment.CenterHorizontally) {
                PixelText(s.title.ifBlank { "?" }, size = dialogTitleSize(s.title), align = TextAlign.Center)
                PixelText(s.creator?.let { stringResource(R.string.community_by, it) } ?: stringResource(R.string.shared_no_author), size = 1, color = Pal.WoodDark, outline = null)
            }
        }
        VSpace(6)
        Badge(stringResource(R.string.shared_badge), color = ButtonStyle.PURPLE)
        VSpace(3)
        DarkCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TierIcon(tier, 2)
                    HSpace(4)
                    PixelText(tierName(tier), size = 3, color = tierColor(tier))
                }
                VSpace(2)
                PixelText(stringResource(R.string.intro_stages, s.data.stages.size) + " · " + typeDescription(plan.spec.type), size = 1, color = Pal.SeaLight)
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
        VSpace(4)
        PixelText(stringResource(R.string.shared_rules), size = 1, color = Pal.WoodDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        VSpace(6)
        PixelButton(stringResource(R.string.play), onClick = { onPlay(s) }, style = ButtonStyle.GREEN, size = 5, modifier = Modifier.keyPrimary().fillMaxWidth())
        VSpace(3)
        PixelButton(stringResource(R.string.close), onClick = onClose, style = ButtonStyle.WOOD, size = 2, modifier = Modifier.fillMaxWidth())
    }
}

/** Pobrane poziomy z linków – po zagraniu okno pokazuje się od razu, bez ponownego pobierania. */
private val sharedCache = LinkedHashMap<String, SharedLevel>()
