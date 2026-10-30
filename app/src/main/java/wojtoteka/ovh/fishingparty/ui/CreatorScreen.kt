package wojtoteka.ovh.fishingparty.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import wojtoteka.ovh.fishingparty.data.LevelDraft
import wojtoteka.ovh.fishingparty.data.LevelDrafts
import androidx.compose.ui.text.input.ImeAction
import wojtoteka.ovh.fishingparty.level.LevelSeed
import wojtoteka.ovh.fishingparty.level.UserLevel
import wojtoteka.ovh.fishingparty.level.UserLevelRating
import wojtoteka.ovh.fishingparty.level.UserLevelSeeds
import wojtoteka.ovh.fishingparty.level.UserLevels
import wojtoteka.ovh.fishingparty.online.Net
import wojtoteka.ovh.fishingparty.online.OnlineLevel
import wojtoteka.ovh.fishingparty.online.OnlineManager
import wojtoteka.ovh.fishingparty.online.OnlineResult

/**
 * Kreator poziomów: szkice na tym telefonie i poziomy opublikowane z konta (ze statystykami).
 * [onView] – opublikowany poziom w POZIOMACH GRACZY (szczegóły i ZAGRAJ).
 */
@Composable
fun CreatorScreen(onBack: () -> Unit, onEdit: (String) -> Unit, onView: (Int) -> Unit) {
    val services = LocalServices.current
    val drafts = services.drafts
    val online = services.online
    val untitled = stringResource(R.string.editor_untitled)
    var deleting by remember { mutableStateOf<LevelDraft?>(null) }
    var full by remember { mutableStateOf(false) }
    // Okienka opublikowanych poziomów są poza przewijaną listą – Overlay w przewijanej kolumnie wywraca grę.
    val published = remember { PublishedLevels() }
    var removing by remember { mutableStateOf<OnlineLevel?>(null) }
    var managing by remember { mutableStateOf<OnlineLevel?>(null) }
    val scope = rememberCoroutineScope()
    ScreenFrame(stringResource(R.string.creator_title), onBack) {
        val scroll = rememberScrollState()
        Column(
            Modifier
                .fillMaxSize()
                .keyScroll(scroll)
                .verticalScroll(scroll)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = px(8), top = px(4), end = px(8), bottom = px(BOTTOM_GAP)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(px(5)),
        ) {
            PixelPanel(Modifier.fillMaxWidth().widthIn(max = px(340))) {
                PixelText(stringResource(R.string.creator_intro), size = 1, color = Pal.WoodDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                VSpace(5)
                PixelButton(
                    stringResource(R.string.creator_new),
                    onClick = {
                        if (drafts.drafts.size >= LevelDrafts.MAX_DRAFTS) {
                            full = true
                        } else {
                            onEdit(drafts.create(UserLevels.starter(untitled)).id)
                        }
                    },
                    style = ButtonStyle.GREEN,
                    size = 4,
                    modifier = Modifier.keyPrimary().fillMaxWidth(),
                )
                if (full) {
                    VSpace(3)
                    PixelText(stringResource(R.string.creator_limit, LevelDrafts.MAX_DRAFTS), size = 1, color = Pal.RedDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            SeedCard(onCreate = { level ->
                if (drafts.drafts.size >= LevelDrafts.MAX_DRAFTS) full = true else onEdit(drafts.create(level).id)
            })
            PixelText(stringResource(R.string.creator_drafts), size = 3, color = Pal.Gold)
            if (drafts.drafts.isEmpty()) Hint(stringResource(R.string.creator_empty), Pal.Foam)
            for (draft in drafts.drafts) {
                DraftCard(
                    draft,
                    onEdit = { onEdit(draft.id) },
                    onCopy = {
                        if (drafts.drafts.size >= LevelDrafts.MAX_DRAFTS) full = true else drafts.create(draft.level)
                    },
                    onDelete = { deleting = draft },
                )
            }
            PublishedSection(published, onManage = { managing = it }, onRemove = { removing = it })
        }
    }
    deleting?.let { d ->
        ConfirmDialog(
            stringResource(R.string.creator_delete_confirm, d.level.title),
            onConfirm = {
                drafts.delete(d.id)
                deleting = null
            },
            onCancel = { deleting = null },
        )
    }
    removing?.let { level ->
        ConfirmDialog(
            stringResource(R.string.creator_unpublish_confirm, level.title),
            onConfirm = {
                removing = null
                scope.launch {
                    when (val res = online.community.delete(level.id)) {
                        is Net.Ok -> {
                            unlinkDrafts(drafts, level.id)
                            published.refresh++
                        }
                        is Net.Err -> published.error = res.failure
                    }
                }
            },
            onCancel = { removing = null },
        )
    }
    managing?.let { level ->
        ManageLevelDialog(
            level,
            onChanged = { changed ->
                published.replace(changed)
                managing = changed
            },
            onView = {
                managing = null
                onView(level.id)
            },
            onEdit = {
                managing = null
                onEdit(it)
            },
            onClose = { managing = null },
        )
    }
}

/** Poziom z seeda: kod wpisany albo wylosowany i trudność – gotowy poziom do dalszej edycji. */
@Composable
private fun SeedCard(onCreate: (UserLevel) -> Unit) {
    var code by remember { mutableStateOf(UserLevelSeeds.random().code) }
    var difficulty by remember { mutableStateOf(UserLevelSeeds.Difficulty.NORMAL) }
    var invalid by remember { mutableStateOf(false) }
    val go = {
        val seed = LevelSeed.parse(code)
        invalid = seed == null
        if (seed != null) onCreate(UserLevelSeeds.build(seed, difficulty))
    }
    DarkCard(Modifier.fillMaxWidth().widthIn(max = px(340))) {
        Column(Modifier.fillMaxWidth()) {
            PixelText(stringResource(R.string.creator_seed_title), size = 3, color = Pal.Gold)
            VSpace(2)
            PixelText(stringResource(R.string.creator_seed_info), size = 1, color = Pal.Foam)
            VSpace(4)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                PixelTextField(
                    code,
                    {
                        code = it.uppercase()
                        invalid = false
                    },
                    stringResource(R.string.creator_seed_field),
                    maxLength = 20,
                    imeAction = ImeAction.Done,
                    onDone = go,
                    modifier = Modifier.weight(1f),
                )
                HSpace(3)
                PixelButton(stringResource(R.string.creator_seed_random), onClick = {
                    code = UserLevelSeeds.random().code
                    invalid = false
                }, style = ButtonStyle.SAND, size = 1)
            }
            if (invalid) {
                VSpace(2)
                PixelText(stringResource(R.string.creator_seed_invalid), size = 1, color = Pal.RedLight)
            }
            VSpace(4)
            PixelText(stringResource(R.string.creator_seed_difficulty), size = 1, color = Pal.Foam)
            VSpace(2)
            ButtonRow(gap = 2) {
                for ((d, tier) in listOf(UserLevelSeeds.Difficulty.EASY to 1, UserLevelSeeds.Difficulty.NORMAL to 2, UserLevelSeeds.Difficulty.HARD to 3)) {
                    PixelButton(tierName(tier), onClick = { difficulty = d }, style = if (difficulty == d) ButtonStyle.GREEN else ButtonStyle.SEA, size = 1)
                }
            }
            VSpace(4)
            PixelButton(stringResource(R.string.creator_seed_go), onClick = go, style = ButtonStyle.ORANGE, size = 3, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DraftCard(draft: LevelDraft, onEdit: () -> Unit, onCopy: () -> Unit, onDelete: () -> Unit) {
    val estimate = remember(draft.hash) { UserLevelRating.estimate(draft.level) }
    val share = rememberLinkShare()
    val online = LocalServices.current.online
    DarkCard(Modifier.fillMaxWidth().widthIn(max = px(340)), highlight = draft.published) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TierIcon(estimate.tier, 2)
                HSpace(4)
                Column(Modifier.weight(1f)) {
                    PixelText(draft.level.title.ifBlank { "?" }, size = 2, maxLines = 1)
                    PixelText(
                        when {
                            draft.published -> stringResource(R.string.creator_state_published, draft.publishedId ?: 0)
                            draft.publishedId != null -> stringResource(R.string.creator_state_changed, draft.publishedId)
                            draft.verified -> stringResource(R.string.creator_state_verified)
                            else -> stringResource(R.string.creator_state_draft)
                        },
                        size = 1,
                        color = if (draft.published || draft.verified) Pal.GreenLight else Pal.Foam,
                    )
                    PixelText(
                        stringResource(R.string.intro_stages, draft.level.stages.size) + " · " + tierName(estimate.tier),
                        size = 1,
                        color = tierColor(estimate.tier),
                    )
                }
                PixelButton(stringResource(R.string.creator_edit), onClick = onEdit, style = ButtonStyle.GREEN, size = 2)
            }
            VSpace(3)
            ButtonRow(gap = 3) {
                PixelButton(
                    stringResource(R.string.community_share),
                    onClick = { shareDraft(share, online, draft) },
                    enabled = !share.busy,
                    style = ButtonStyle.ORANGE,
                    size = 1,
                )
                PixelButton(stringResource(R.string.creator_copy), onClick = onCopy, style = ButtonStyle.SEA, size = 1)
                PixelButton(stringResource(R.string.creator_delete), onClick = onDelete, style = ButtonStyle.RED, size = 1)
            }
            share.error?.let {
                VSpace(2)
                PixelText(onlineErrorText(it), size = 1, color = Pal.RedLight)
            }
        }
    }
}

/**
 * Link do szkicu: opublikowany poziom (ta sama wersja) – link do niego, a każdy inny – link bez publikacji
 * (bez przejścia poziomu i bez konta; zagrają tylko osoby z linkiem).
 */
fun shareDraft(share: LinkShare, online: OnlineManager, draft: LevelDraft) {
    val title = draft.level.title.ifBlank { "?" }
    val published = draft.publishedId.takeIf { draft.published }
    share.share(title, own = true) { lang ->
        if (published != null) online.community.shareLink(published, lang) else online.community.shareDraft(draft.level, lang)
    }
}

/** Poziomy opublikowane z konta – trzymane w ekranie kreatora, żeby okienka zmieniały je poza przewijaną listą. */
private class PublishedLevels {
    var levels by mutableStateOf<List<OnlineLevel>?>(null)
    var error by mutableStateOf<OnlineResult.Failed?>(null)
    var refresh by mutableIntStateOf(0)

    fun replace(level: OnlineLevel) {
        levels = levels?.map { if (it.id == level.id) level else it }
    }
}

/** Poziomy opublikowane z konta: ocena, punkty i liczby graczy. */
@Composable
private fun PublishedSection(state: PublishedLevels, onManage: (OnlineLevel) -> Unit, onRemove: (OnlineLevel) -> Unit) {
    val online = LocalServices.current.online
    val loggedIn = online.loggedIn
    LaunchedEffect(loggedIn, state.refresh) {
        if (!loggedIn || !online.configured) return@LaunchedEffect
        when (val res = online.community.mine()) {
            is Net.Ok -> {
                state.levels = res.value
                state.error = null
            }
            is Net.Err -> state.error = res.failure
        }
    }
    val levels = state.levels
    val error = state.error
    VSpace(4)
    PixelText(stringResource(R.string.creator_published), size = 3, color = Pal.Gold)
    when {
        !loggedIn -> Hint(stringResource(R.string.creator_published_login), Pal.Foam)
        error != null -> Hint(onlineErrorText(error), Pal.RedLight)
        levels == null -> Hint(stringResource(R.string.online_loading), Pal.Gold)
        levels.isEmpty() -> Hint(stringResource(R.string.creator_published_empty), Pal.Foam)
        else -> for (level in levels) {
            OnlineLevelCard(level, done = false, modifier = Modifier.fillMaxWidth().widthIn(max = px(340))) {
                if (level.hidden) PixelText(stringResource(R.string.creator_hidden, level.hiddenReason ?: "-"), size = 1, color = Pal.RedLight)
                VSpace(3)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(3))) {
                    PixelButton(stringResource(R.string.creator_manage), onClick = { onManage(level) }, style = ButtonStyle.SEA, size = 1, modifier = Modifier.weight(1f))
                    PixelButton(stringResource(R.string.creator_unpublish), onClick = { onRemove(level) }, style = ButtonStyle.RED, size = 1, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Szkic opublikowanego poziomu na tym telefonie (ta sama plansza i nazwa co na serwerze). */
private fun publishedDraft(drafts: LevelDrafts, id: Int): LevelDraft? = drafts.drafts.firstOrNull { it.publishedId == id && it.published }

/** Poziom usunięty z serwera – jego szkice znów można opublikować. */
private fun unlinkDrafts(drafts: LevelDrafts, id: Int) {
    for (d in drafts.drafts.filter { it.publishedId == id }) drafts.save(d.with(publishedId = null, publishedHash = null))
}

/** Nowa nazwa opublikowanego poziomu także w jego szkicu – plansza ta sama, więc szkic dalej jest opublikowany i przejechany. */
private fun renameDraft(drafts: LevelDrafts, id: Int, title: String) {
    val d = publishedDraft(drafts, id) ?: return
    if (d.level.title == title) return
    val level = UserLevels.normalize(d.level.copy(title = title))
    val hash = UserLevels.hashOf(level)
    drafts.save(d.with(level = level, publishedHash = hash, verifiedHash = if (d.verified) hash else d.verifiedHash))
}

/**
 * Zarządzanie opublikowanym poziomem: statystyki, nazwa i zgoda na kopiowanie (zmienia je serwer – plansza, gracze
 * i gwiazdki zostają), link, podgląd w POZIOMACH GRACZY i szkic w kreatorze (pobrany z serwera, gdy go nie ma na telefonie).
 */
@Composable
private fun ManageLevelDialog(
    level: OnlineLevel,
    onChanged: (OnlineLevel) -> Unit,
    onView: () -> Unit,
    onEdit: (String) -> Unit,
    onClose: () -> Unit,
) {
    val services = LocalServices.current
    val online = services.online
    val drafts = services.drafts
    var title by remember(level.id) { mutableStateOf(level.title) }
    var copyable by remember(level.id) { mutableStateOf(level.copyable) }
    var busy by remember(level.id) { mutableStateOf(false) }
    var saved by remember(level.id) { mutableStateOf(false) }
    var full by remember(level.id) { mutableStateOf(false) }
    var error by remember(level.id) { mutableStateOf<OnlineResult.Failed?>(null) }
    val scope = rememberCoroutineScope()
    val share = rememberLinkShare()
    val clean = UserLevels.normalizeTitle(title).filter { UserLevels.titleAllowed(it.toString()) }.take(UserLevels.TITLE_MAX)
    val newTitle = clean.takeIf { it != level.title }
    val newCopyable = copyable.takeIf { it != level.copyable }
    val draft = publishedDraft(drafts, level.id)
    fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        saved = false
        error = null
        scope.launch {
            block()
            busy = false
        }
    }
    Overlay(onDismiss = onClose) {
        Box(Modifier.fillMaxWidth()) {
            PixelButton("X", onClick = onClose, style = ButtonStyle.RED, size = 3, modifier = Modifier.align(Alignment.TopEnd))
            Column(Modifier.align(Alignment.TopCenter).padding(horizontal = px(18)), horizontalAlignment = Alignment.CenterHorizontally) {
                PixelText(level.title, size = 3, align = TextAlign.Center)
                PixelText("#${level.id}", size = 1, color = Pal.WoodDark, outline = null)
            }
        }
        VSpace(5)
        DarkCard(Modifier.fillMaxWidth(), highlight = level.featured) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TierIcon(level.tier, 2)
                    HSpace(4)
                    Column(Modifier.weight(1f)) {
                        PixelText(tierName(level.tier), size = 2, color = tierColor(level.tier))
                        PixelText(
                            if (level.rated) stringResource(R.string.community_points, level.points) else stringResource(R.string.community_unrated),
                            size = 1,
                            color = if (level.rated) Pal.Gold else Pal.GreyLight,
                        )
                    }
                }
                VSpace(3)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(3))) {
                    StatTile(stringResource(R.string.community_stars), level.stars.toString(), Modifier.weight(1f), icon = "fishscore_gold")
                    StatTile(stringResource(R.string.community_players), level.players.toString(), Modifier.weight(1f))
                    StatTile(stringResource(R.string.community_rate), level.completionRate?.let { "$it%" } ?: "-", Modifier.weight(1f))
                }
                VSpace(2)
                PixelText(stringResource(R.string.manage_counts, level.attempts, level.completions), size = 1, color = Pal.SeaLight)
                if (level.hidden) {
                    VSpace(2)
                    PixelText(stringResource(R.string.creator_hidden, level.hiddenReason ?: "-"), size = 1, color = Pal.RedLight)
                }
            }
        }
        VSpace(4)
        DarkCard(Modifier.fillMaxWidth()) {
            PixelTextField(
                title,
                {
                    title = it
                    saved = false
                },
                stringResource(R.string.editor_name),
                maxLength = UserLevels.TITLE_MAX,
                imeAction = ImeAction.Done,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        VSpace(3)
        CheckRow(copyable, stringResource(R.string.editor_copyable)) {
            copyable = !copyable
            saved = false
        }
        VSpace(3)
        PixelButton(
            stringResource(R.string.save_button),
            onClick = {
                run {
                    when (val res = online.community.updateLevel(level.id, newTitle, newCopyable)) {
                        is Net.Ok -> {
                            if (newTitle != null) renameDraft(drafts, level.id, res.value.title)
                            title = res.value.title
                            copyable = res.value.copyable
                            saved = true
                            onChanged(res.value)
                        }
                        is Net.Err -> error = res.failure
                    }
                }
            },
            enabled = !busy && (newTitle != null || newCopyable != null) && clean.length >= UserLevels.TITLE_MIN,
            style = ButtonStyle.GREEN,
            size = 2,
            modifier = Modifier.keyPrimary().fillMaxWidth(),
        )
        VSpace(4)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(3))) {
            // Ukryty przez admina poziom nie ma linku ani strony w POZIOMACH GRACZY.
            PixelButton(
                stringResource(R.string.community_share),
                onClick = { share.share(level.title, own = true) { lang -> online.community.shareLink(level.id, lang) } },
                enabled = !share.busy && !level.hidden,
                style = ButtonStyle.ORANGE,
                size = 1,
                modifier = Modifier.weight(1f),
            )
            PixelButton(stringResource(R.string.manage_view), onClick = onView, enabled = !level.hidden, style = ButtonStyle.SEA, size = 1, modifier = Modifier.weight(1f))
        }
        VSpace(3)
        if (draft != null) {
            PixelButton(stringResource(R.string.manage_open_draft), onClick = { onEdit(draft.id) }, style = ButtonStyle.SAND, size = 2, modifier = Modifier.fillMaxWidth())
        } else {
            PixelButton(
                stringResource(R.string.manage_download),
                onClick = {
                    if (drafts.drafts.size >= LevelDrafts.MAX_DRAFTS) {
                        full = true
                    } else {
                        run {
                            when (val res = online.community.level(level.id)) {
                                is Net.Ok -> {
                                    val created = drafts.create(res.value.data)
                                    drafts.save(created.with(publishedId = level.id, publishedHash = created.hash))
                                    onEdit(created.id)
                                }
                                is Net.Err -> error = res.failure
                            }
                        }
                    }
                },
                enabled = !busy && !level.hidden,
                style = ButtonStyle.SAND,
                size = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        VSpace(2)
        PixelText(stringResource(R.string.manage_edit_info), size = 1, color = Pal.WoodDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        val failure = error ?: share.error
        val message = when {
            full -> stringResource(R.string.creator_limit, LevelDrafts.MAX_DRAFTS)
            failure != null -> onlineErrorText(failure)
            busy -> stringResource(R.string.online_loading)
            saved -> stringResource(R.string.manage_saved)
            else -> null
        }
        message?.let {
            VSpace(3)
            PixelText(it, size = 1, color = if (saved && !full) Pal.GreenDark else Pal.RedDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        VSpace(4)
        PixelButton(stringResource(R.string.close), onClick = onClose, style = ButtonStyle.WOOD, size = 2, modifier = Modifier.fillMaxWidth())
    }
}
