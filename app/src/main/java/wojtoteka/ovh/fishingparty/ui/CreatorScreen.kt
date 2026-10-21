package wojtoteka.ovh.fishingparty.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import wojtoteka.ovh.fishingparty.online.OnlineResult

/** Kreator poziomów: szkice na tym telefonie i poziomy opublikowane z konta (ze statystykami). */
@Composable
fun CreatorScreen(onBack: () -> Unit, onEdit: (String) -> Unit) {
    val services = LocalServices.current
    val drafts = services.drafts
    val untitled = stringResource(R.string.editor_untitled)
    var deleting by remember { mutableStateOf<LevelDraft?>(null) }
    var full by remember { mutableStateOf(false) }
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
            PublishedSection()
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
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(px(2))) {
                for ((d, tier) in listOf(UserLevelSeeds.Difficulty.EASY to 1, UserLevelSeeds.Difficulty.NORMAL to 2, UserLevelSeeds.Difficulty.HARD to 3)) {
                    PixelButton(tierName(tier), onClick = { difficulty = d }, style = if (difficulty == d) ButtonStyle.GREEN else ButtonStyle.SEA, size = 1, modifier = Modifier.weight(1f).fillMaxHeight())
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
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(px(3))) {
                PixelButton(stringResource(R.string.creator_copy), onClick = onCopy, style = ButtonStyle.SEA, size = 1, modifier = Modifier.weight(1f).fillMaxHeight())
                PixelButton(stringResource(R.string.creator_delete), onClick = onDelete, style = ButtonStyle.RED, size = 1, modifier = Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

/** Poziomy opublikowane z konta: ocena, punkty i liczby graczy. */
@Composable
private fun PublishedSection() {
    val online = LocalServices.current.online
    var levels by remember { mutableStateOf<List<OnlineLevel>?>(null) }
    var error by remember { mutableStateOf<OnlineResult.Failed?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var removing by remember { mutableStateOf<OnlineLevel?>(null) }
    val scope = rememberCoroutineScope()
    val loggedIn = online.loggedIn
    LaunchedEffect(loggedIn, refresh) {
        if (!loggedIn || !online.configured) return@LaunchedEffect
        when (val res = online.community.mine()) {
            is Net.Ok -> {
                levels = res.value
                error = null
            }
            is Net.Err -> error = res.failure
        }
    }
    VSpace(4)
    PixelText(stringResource(R.string.creator_published), size = 3, color = Pal.Gold)
    when {
        !loggedIn -> Hint(stringResource(R.string.creator_published_login), Pal.Foam)
        error != null -> Hint(onlineErrorText(error!!), Pal.RedLight)
        levels == null -> Hint(stringResource(R.string.online_loading), Pal.Gold)
        levels!!.isEmpty() -> Hint(stringResource(R.string.creator_published_empty), Pal.Foam)
        else -> for (level in levels!!) {
            OnlineLevelCard(level, done = false, modifier = Modifier.fillMaxWidth().widthIn(max = px(340))) {
                if (level.hidden) PixelText(stringResource(R.string.creator_hidden, level.hiddenReason ?: "-"), size = 1, color = Pal.RedLight)
                VSpace(2)
                PixelButton(stringResource(R.string.creator_unpublish), onClick = { removing = level }, style = ButtonStyle.RED, size = 1, modifier = Modifier.fillMaxWidth())
            }
        }
    }
    removing?.let { level ->
        ConfirmDialog(
            stringResource(R.string.creator_unpublish_confirm, level.title),
            onConfirm = {
                removing = null
                scope.launch {
                    when (val res = online.community.delete(level.id)) {
                        is Net.Ok -> refresh++
                        is Net.Err -> error = res.failure
                    }
                }
            },
            onCancel = { removing = null },
        )
    }
}
