package wojtoteka.ovh.fishingparty.ui

import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import wojtoteka.ovh.fishingparty.R
import wojtoteka.ovh.fishingparty.online.OnlineManager
import wojtoteka.ovh.fishingparty.online.OnlineResult
import wojtoteka.ovh.fishingparty.online.RankingData
import wojtoteka.ovh.fishingparty.online.SyncStatus
import java.util.Date

// ------------------------------------------------------------------ elementy formularzy

/** Pole tekstowe w stylu gry (font BitCell, ciemna wnęka). */
@Composable
fun PixelTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    maxLength: Int = 64,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    val u = LocalUnit.current.toFloat()
    val fp = fontPixels(3)
    val density = LocalDensity.current
    val style = remember(fp, density) {
        with(density) { TextStyle(fontFamily = BitCell, fontSize = (16 * fp).toSp(), lineHeight = (12 * fp).toSp(), color = Pal.White) }
    }
    Column(modifier) {
        PixelText(label, size = 1, color = Pal.Foam)
        VSpace(1)
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(it.take(maxLength)) },
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(Pal.Gold),
            visualTransformation = if (password) PasswordVisualTransformation('*') else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (password) KeyboardType.Password else keyboard,
                imeAction = imeAction,
                autoCorrectEnabled = false,
                capitalization = KeyboardCapitalization.None,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }, onGo = { onDone() }),
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind { pixelBox(u, Pal.Outline.copy(alpha = 0.9f), Pal.Navy, Pal.Outline, shadow = 0f) }
                .padding(horizontal = px(5), vertical = px(4)),
        )
    }
}

/** Pole wyboru (akceptacja regulaminu). */
@Composable
private fun PixelCheck(checked: Boolean, label: String, onToggle: () -> Unit) {
    val u = LocalUnit.current.toFloat()
    Row(Modifier.fillMaxWidth().noRippleClick(onToggle).padding(vertical = px(2)), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(px(12)).drawBehind {
                pixelBox(u, Pal.SeaDark, Pal.Navy, Pal.Outline, shadow = 0f)
                if (checked) drawRect(Pal.Gold, Offset(3 * u, 3 * u), Size(size.width - 6 * u, size.height - 6 * u))
            },
        )
        HSpace(4)
        PixelText(label, size = 1, color = if (checked) Pal.Gold else Pal.White, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Hint(text: String, color: androidx.compose.ui.graphics.Color = Pal.SeaLight) {
    PixelText(text, size = 1, color = color, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

private fun openUrl(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }
}

/** Informacja o polityce prywatności i regulaminie z polem akceptacji. */
@Composable
private fun PolicyBlock(accepted: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    DarkCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            PixelText(stringResource(R.string.policy_title), size = 2, color = Pal.Gold)
            VSpace(2)
            PixelText(stringResource(R.string.policy_summary), size = 1, color = Pal.Foam)
            VSpace(4)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(3))) {
                PixelButton(stringResource(R.string.policy_open_privacy), onClick = { openUrl(context, OnlineManager.PRIVACY_URL) }, style = ButtonStyle.SEA, size = 1, modifier = Modifier.weight(1f))
                PixelButton(stringResource(R.string.policy_open_terms), onClick = { openUrl(context, OnlineManager.TERMS_URL) }, style = ButtonStyle.SEA, size = 1, modifier = Modifier.weight(1f))
            }
            VSpace(4)
            PixelCheck(accepted, stringResource(R.string.policy_accept), onToggle)
        }
    }
}

@Composable
fun onlineErrorText(f: OnlineResult.Failed): String = when (f.code) {
    "offline" -> stringResource(R.string.err_offline)
    "server_unreachable", "bad_response", "bad_signature", "server_error" -> stringResource(R.string.err_server_unreachable)
    "nick_taken" -> stringResource(R.string.err_nick_taken)
    "nick_short", "nick_long" -> stringResource(R.string.err_nick_length)
    "nick_chars" -> stringResource(R.string.err_nick_chars)
    "nick_banned" -> stringResource(R.string.err_nick_banned)
    "nick_reserved" -> stringResource(R.string.err_nick_reserved)
    "registration_closed" -> stringResource(R.string.err_registration_closed)
    "device_unverified", "device_mismatch" -> stringResource(R.string.err_device_unverified)
    "device_unsupported" -> stringResource(R.string.err_device_unsupported)
    "password_short" -> stringResource(R.string.err_password_short)
    "password_long" -> stringResource(R.string.err_password_long)
    "password_mismatch" -> stringResource(R.string.err_password_mismatch)
    "password_required" -> stringResource(R.string.err_password_required)
    "password_already_set" -> stringResource(R.string.err_password_already_set)
    "email_invalid" -> stringResource(R.string.err_email_invalid)
    "email_taken" -> stringResource(R.string.err_email_taken)
    "policy_required", "policy_outdated" -> stringResource(R.string.err_policy)
    "invalid_credentials" -> stringResource(R.string.err_invalid_credentials)
    "quick_wrong" -> stringResource(R.string.err_quick_wrong)
    "account_locked" -> stringResource(R.string.err_locked, maxOf(1, (f.retryAfter + 59) / 60))
    "banned" -> stringResource(R.string.err_banned)
    "code_wrong" -> stringResource(R.string.err_code_wrong, maxOf(0, f.attemptsLeft))
    "code_expired" -> stringResource(R.string.err_code_expired)
    "code_attempts" -> stringResource(R.string.err_code_attempts)
    "code_invalid", "code_format" -> stringResource(R.string.err_code_format)
    "mail_failed" -> stringResource(R.string.err_mail_failed)
    "mail_throttled", "rate_limited" -> stringResource(R.string.err_rate_limited)
    "update_required" -> stringResource(R.string.err_update_required)
    "clock_skew" -> stringResource(R.string.err_clock)
    "not_configured" -> stringResource(R.string.online_not_configured)
    "not_logged_in" -> stringResource(R.string.session_expired)
    else -> stringResource(R.string.err_generic, f.code)
}

/** Proste sprawdzenie po stronie telefonu – te same zasady co na serwerze. */
private object FormCheck {
    fun nick(n: String): String? {
        val t = n.trim().replace(Regex("\\s+"), " ")
        return when {
            t.length !in 3..16 -> "nick_short"
            !t.all { it.isLetterOrDigit() || it in " _.-" } -> "nick_chars"
            else -> null
        }
    }

    fun password(p: String, repeat: String? = null): String? = when {
        p.length < 8 -> "password_short"
        p.length > 72 -> "password_long"
        repeat != null && repeat != p -> "password_mismatch"
        else -> null
    }

    fun email(e: String): String? =
        if (Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(e.trim())) null else "email_invalid"

    fun code(c: String): String? = if (Regex("^\\d{6}$").matches(c.trim())) null else "code_format"
}

/** Stan formularza: trwa żądanie + ostatni błąd/komunikat. */
private class FormState {
    var busy by mutableStateOf(false)
    var error by mutableStateOf<OnlineResult.Failed?>(null)
    var info by mutableStateOf<String?>(null)
}

@Composable
private fun FormFeedback(form: FormState) {
    form.error?.let {
        VSpace(3)
        Hint(onlineErrorText(it), Pal.RedLight)
    }
    form.info?.let {
        VSpace(3)
        Hint(it, Pal.GreenLight)
    }
    if (form.busy) {
        VSpace(3)
        Hint(stringResource(R.string.online_loading), Pal.Gold)
    }
}

/** Uruchamia żądanie, a przed nim walidację; [onResult] dostaje odpowiedź, gdy nie było błędu. */
@Composable
private fun rememberSubmit(form: FormState): (List<String?>, suspend () -> OnlineResult, (OnlineResult) -> Unit) -> Unit {
    val scope = rememberCoroutineScope()
    return { checks, action, onResult ->
        val problem = checks.firstOrNull { it != null }
        form.info = null
        if (problem != null) {
            form.error = OnlineResult.Failed(problem)
        } else if (!form.busy) {
            form.busy = true
            form.error = null
            scope.launch {
                val result = action()
                form.busy = false
                if (result is OnlineResult.Failed) form.error = result else onResult(result)
            }
        }
    }
}

// ------------------------------------------------------------------ brak internetu

/** Sprawdza sieć przy wejściu i po powrocie do aplikacji. */
@Composable
private fun rememberOnline(): Pair<Boolean, () -> Unit> {
    val online = LocalServices.current.online
    var state by remember { mutableStateOf(online.isOnline()) }
    LifecycleResumeEffect(Unit) {
        state = online.isOnline()
        onPauseOrDispose { }
    }
    return state to { state = online.isOnline() }
}

@Composable
private fun OfflineCard(onRetry: () -> Unit) {
    PixelPanel(Modifier.fillMaxWidth().widthIn(max = px(320))) {
        PixelText(stringResource(R.string.online_offline_title), size = 4, color = Pal.RedDark, outline = null)
        VSpace(4)
        PixelText(
            stringResource(R.string.online_offline),
            size = 2,
            color = Pal.WoodDark,
            outline = null,
            align = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        VSpace(6)
        PixelButton(stringResource(R.string.online_retry), onClick = onRetry, style = ButtonStyle.GREEN, size = 2)
    }
}

@Composable
private fun NotConfiguredCard() {
    PixelPanel(Modifier.fillMaxWidth().widthIn(max = px(320))) {
        PixelText(stringResource(R.string.online_not_configured), size = 2, color = Pal.WoodDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/** Krótka zawartość stoi na środku ekranu (wygodniej sięgnąć kciukiem), dłuższa się przewija. */
@Composable
private fun OnlineColumn(content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = maxHeight
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = viewport)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .imePadding()
                .padding(start = px(10), top = px(6), end = px(10), bottom = px(BOTTOM_GAP)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(px(5), Alignment.CenterVertically),
            content = content,
        )
    }
}

// ------------------------------------------------------------------ tryb rankingowy (nick + hasło)

@Composable
private fun QuickJoinForm() {
    val online = LocalServices.current.online
    val form = remember { FormState() }
    val submit = rememberSubmit(form)
    var accepted by remember { mutableStateOf(false) }
    var nick by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val go = {
        submit(listOf(if (accepted) null else "policy_required", FormCheck.nick(nick), FormCheck.password(password)), { online.quickJoin(nick, password) }) {}
    }
    Column(Modifier.fillMaxWidth().widthIn(max = px(320)), verticalArrangement = Arrangement.spacedBy(px(4))) {
        DarkCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                PixelText(stringResource(R.string.ranking_mode_title), size = 3, color = Pal.Gold)
                VSpace(2)
                PixelText(stringResource(R.string.ranking_mode_info), size = 1, color = Pal.Foam)
                VSpace(4)
                PixelTextField(nick, { nick = it }, stringResource(R.string.field_nick), maxLength = 16)
                VSpace(3)
                PixelTextField(password, { password = it }, stringResource(R.string.field_password), password = true, maxLength = 72, imeAction = ImeAction.Done, onDone = go)
                VSpace(3)
                PixelText(stringResource(R.string.ranking_mode_warning), size = 1, color = Pal.Sand)
                VSpace(4)
                PixelButton(stringResource(R.string.ranking_join), onClick = go, style = ButtonStyle.GREEN, size = 3, enabled = !form.busy, modifier = Modifier.fillMaxWidth())
                FormFeedback(form)
            }
        }
        PolicyBlock(accepted) { accepted = !accepted }
    }
}

// ------------------------------------------------------------------ tylko nick (bez hasła)

@Composable
private fun NickOnlyForm() {
    val online = LocalServices.current.online
    val form = remember { FormState() }
    val submit = rememberSubmit(form)
    var accepted by remember { mutableStateOf(false) }
    var nick by remember { mutableStateOf("") }
    val go = { submit(listOf(if (accepted) null else "policy_required", FormCheck.nick(nick)), { online.joinNick(nick) }) {} }
    Column(Modifier.fillMaxWidth().widthIn(max = px(320)), verticalArrangement = Arrangement.spacedBy(px(4))) {
        DarkCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                PixelText(stringResource(R.string.nick_only_title), size = 3, color = Pal.Gold)
                VSpace(2)
                PixelText(stringResource(R.string.nick_only_info), size = 1, color = Pal.Foam)
                VSpace(4)
                PixelTextField(nick, { nick = it }, stringResource(R.string.field_nick), maxLength = 16, imeAction = ImeAction.Done, onDone = go)
                VSpace(3)
                PixelText(stringResource(R.string.nick_only_warning), size = 1, color = Pal.Sand)
                VSpace(4)
                PixelButton(stringResource(R.string.nick_only_join), onClick = go, style = ButtonStyle.ORANGE, size = 3, enabled = !form.busy, modifier = Modifier.fillMaxWidth())
                FormFeedback(form)
            }
        }
        PolicyBlock(accepted) { accepted = !accepted }
    }
}

// ------------------------------------------------------------------ ranking

@Composable
fun RankingScreen(onBack: () -> Unit, onAccount: () -> Unit) {
    val services = LocalServices.current
    val online = services.online
    val (isOnline, recheck) = rememberOnline()
    var refreshKey by remember { mutableIntStateOf(0) }
    var data by remember { mutableStateOf<RankingData?>(null) }
    var failed by remember { mutableStateOf<OnlineResult.Failed?>(null) }
    var loading by remember { mutableStateOf(false) }
    val loggedIn = online.loggedIn

    LaunchedEffect(loggedIn, refreshKey, isOnline) {
        if (!isOnline || !online.configured) return@LaunchedEffect
        loading = true
        val result = online.ranking()
        loading = false
        result.onSuccess {
            data = it
            failed = null
        }.onFailure { e ->
            failed = OnlineResult.Failed(
                when (e) {
                    is wojtoteka.ovh.fishingparty.online.ApiException -> e.code
                    is wojtoteka.ovh.fishingparty.online.OfflineException -> if (online.isOnline()) "server_unreachable" else "offline"
                    else -> "unknown"
                },
            )
        }
    }

    ScreenFrame(stringResource(R.string.ranking_title), onBack) {
        when {
            !online.configured -> OnlineColumn {
                NotConfiguredCard()
                LocalPointsPanel()
            }
            !isOnline -> OnlineColumn {
                OfflineCard(recheck)
                LocalPointsPanel()
            }
            else -> RankingList(data, failed, loading, onRefresh = { refreshKey++ }, onAccount = onAccount)
        }
    }
}

/** Punkty liczone na telefonie i zasady passy – widoczne także bez konta i bez internetu. */
@Composable
private fun LocalPointsPanel() {
    val repo = LocalServices.current.repo
    repo.revision.intValue
    val progress = repo.progress
    val streak = progress.streakState()
    PixelPanel(Modifier.fillMaxWidth().widthIn(max = px(320))) {
        PixelText(stringResource(R.string.ranking_points), size = 2, color = Pal.Outline, outline = null)
        PixelText(progress.rankingPoints().toString(), size = 6)
        VSpace(3)
        PixelText(stringResource(R.string.ranking_info), size = 1, color = Pal.WoodDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        VSpace(6)
        PixelText(stringResource(R.string.streak_title), size = 2, color = Pal.Outline, outline = null)
        PixelText(pluralStringResource(R.plurals.streak_days, streak.days, streak.days), size = 4)
        VSpace(3)
        PixelText(stringResource(R.string.streak_info), size = 1, color = Pal.WoodDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun RankingList(data: RankingData?, failed: OnlineResult.Failed?, loading: Boolean, onRefresh: () -> Unit, onAccount: () -> Unit) {
    val account = LocalServices.current.online.account
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = px(6)), horizontalAlignment = Alignment.CenterHorizontally) {
            if (account == null) {
                // Gość widzi ranking, ale sam w nim nie występuje.
                DarkCard(Modifier.fillMaxWidth().widthIn(max = px(340)), highlight = true) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        PixelText(
                            stringResource(R.string.ranking_guest_info),
                            size = 2,
                            color = Pal.Foam,
                            align = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        VSpace(4)
                        PixelButton(stringResource(R.string.register_button), onClick = onAccount, style = ButtonStyle.GREEN, size = 2)
                    }
                }
            } else DarkCard(Modifier.fillMaxWidth().widthIn(max = px(340)), highlight = true) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        PixelText(stringResource(R.string.ranking_my_position), size = 1, color = Pal.Gold)
                        PixelText(account?.nick ?: "", size = 3)
                        val rank = data?.myRank ?: account?.rank
                        PixelText(
                            if (rank != null) "#$rank" else stringResource(R.string.ranking_not_ranked),
                            size = 2,
                            color = Pal.Foam,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        PixelText(stringResource(R.string.account_points), size = 1, color = Pal.Gold)
                        PixelText((data?.myPoints ?: account?.points ?: 0L).toString(), size = 4)
                    }
                }
            }
            if (data?.hidden == true || account?.hidden == true) {
                VSpace(3)
                Hint(stringResource(R.string.account_hidden), Pal.RedLight)
            }
            VSpace(3)
            Row(Modifier.fillMaxWidth().widthIn(max = px(340)), verticalAlignment = Alignment.CenterVertically) {
                PixelText(stringResource(R.string.ranking_top, 100), size = 3, modifier = Modifier.weight(1f))
                PixelButton(stringResource(R.string.ranking_refresh), onClick = onRefresh, style = ButtonStyle.SEA, size = 2, enabled = !loading)
            }
            failed?.let {
                VSpace(2)
                Hint(onlineErrorText(it), Pal.RedLight)
            }
            if (loading && data == null) {
                VSpace(6)
                Hint(stringResource(R.string.online_loading), Pal.Gold)
            }
        }
        val entries = data?.entries.orEmpty()
        if (data != null && entries.isEmpty()) {
            VSpace(10)
            Hint(stringResource(R.string.ranking_empty), Pal.Foam)
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = px(6), top = px(6), end = px(6), bottom = px(BOTTOM_GAP)),
            verticalArrangement = Arrangement.spacedBy(px(3)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(entries, key = { it.id }) { e ->
                val mine = e.id == data?.myId
                DarkCard(Modifier.fillMaxWidth().widthIn(max = px(340)), highlight = mine) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.widthIn(min = px(30))) {
                            PixelText(
                                "#${e.rank}",
                                size = 2,
                                color = when (e.rank) {
                                    1 -> Pal.Gold
                                    2 -> Pal.GreyLight
                                    3 -> Pal.WoodLight
                                    else -> Pal.SeaLight
                                },
                            )
                        }
                        HSpace(3)
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PixelText(e.nick, size = 2, color = if (mine) Pal.Gold else Pal.White)
                                if (mine) {
                                    HSpace(3)
                                    PixelText(stringResource(R.string.ranking_you), size = 1, color = Pal.GreenLight)
                                }
                            }
                            PixelText(stringResource(R.string.ranking_level, e.maxLevel), size = 1, color = Pal.Foam)
                        }
                        PixelText(e.points.toString(), size = 2, color = Pal.Gold)
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ konto

private enum class AccountMode {
    MENU, QUICK, NICK_ONLY, EMAIL_REGISTER, EMAIL_CODE, LOGIN, RESET_EMAIL, RESET_CODE,
    ADD_EMAIL, ADD_EMAIL_CODE, CHANGE_PASSWORD, SET_PASSWORD, DELETE,
}

@Composable
fun AccountScreen(onBack: () -> Unit) {
    val online = LocalServices.current.online
    val (isOnline, recheck) = rememberOnline()
    var mode by remember { mutableStateOf(AccountMode.MENU) }
    var pendingEmail by remember { mutableStateOf("") }
    val loggedIn = online.loggedIn
    LaunchedEffect(loggedIn) { mode = AccountMode.MENU }
    LaunchedEffect(loggedIn, isOnline) { if (loggedIn && isOnline) online.refreshProfile() }

    ScreenFrame(stringResource(R.string.account_title), onBack = { if (mode == AccountMode.MENU) onBack() else mode = AccountMode.MENU }) {
        OnlineColumn {
            Column(Modifier.fillMaxWidth().widthIn(max = px(320)), verticalArrangement = Arrangement.spacedBy(px(5))) {
                when {
                    !online.configured -> NotConfiguredCard()
                    !isOnline -> OfflineCard(recheck)
                    !loggedIn -> when (mode) {
                        AccountMode.QUICK -> QuickJoinForm()
                        AccountMode.NICK_ONLY -> NickOnlyForm()
                        AccountMode.EMAIL_REGISTER -> EmailRegisterForm { pendingEmail = it; mode = AccountMode.EMAIL_CODE }
                        AccountMode.EMAIL_CODE -> CodeForm(pendingEmail, resend = null) { code -> online.verifyEmail(pendingEmail, code) }
                        AccountMode.LOGIN -> LoginForm(onForgot = { mode = AccountMode.RESET_EMAIL })
                        AccountMode.RESET_EMAIL -> ResetEmailForm { pendingEmail = it; mode = AccountMode.RESET_CODE }
                        AccountMode.RESET_CODE -> ResetCodeForm(pendingEmail)
                        else -> AccountChooser { mode = it }
                    }
                    else -> when (mode) {
                        AccountMode.ADD_EMAIL -> AddEmailForm { pendingEmail = it; mode = AccountMode.ADD_EMAIL_CODE }
                        AccountMode.ADD_EMAIL_CODE -> AddEmailCodeForm(pendingEmail) { mode = AccountMode.MENU }
                        AccountMode.CHANGE_PASSWORD -> ChangePasswordForm { mode = AccountMode.MENU }
                        AccountMode.SET_PASSWORD -> SetPasswordForm { mode = AccountMode.MENU }
                        AccountMode.DELETE -> DeleteAccountForm()
                        else -> AccountProfile { mode = it }
                    }
                }
                if (mode != AccountMode.MENU && isOnline) {
                    PixelButton(stringResource(R.string.back), onClick = { mode = AccountMode.MENU }, style = ButtonStyle.WOOD, size = 2, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }
}

@Composable
private fun AccountChooser(onPick: (AccountMode) -> Unit) {
    PixelPanel(Modifier.fillMaxWidth()) {
        PixelText(stringResource(R.string.account_intro), size = 2, color = Pal.WoodDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    OptionCard(stringResource(R.string.account_email_option), stringResource(R.string.account_email_option_info), ButtonStyle.GREEN) { onPick(AccountMode.EMAIL_REGISTER) }
    OptionCard(stringResource(R.string.account_quick_option), stringResource(R.string.account_quick_option_info), ButtonStyle.SAND) { onPick(AccountMode.QUICK) }
    OptionCard(stringResource(R.string.account_nick_option), stringResource(R.string.account_nick_option_info), ButtonStyle.ORANGE) { onPick(AccountMode.NICK_ONLY) }
    PixelButton(stringResource(R.string.account_login_option), onClick = { onPick(AccountMode.LOGIN) }, style = ButtonStyle.SEA, size = 2, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun OptionCard(title: String, info: String, style: ButtonStyle, onClick: () -> Unit) {
    DarkCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            PixelButton(title, onClick = onClick, style = style, size = 3, modifier = Modifier.fillMaxWidth())
            VSpace(2)
            Hint(info, Pal.Foam)
        }
    }
}

@Composable
private fun FormCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    DarkCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            PixelText(title, size = 3, color = Pal.Gold)
            VSpace(4)
            content()
        }
    }
}

@Composable
private fun EmailRegisterForm(onCodeSent: (String) -> Unit) {
    val online = LocalServices.current.online
    val form = remember { FormState() }
    val submit = rememberSubmit(form)
    var accepted by remember { mutableStateOf(false) }
    var nick by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val go = {
        submit(
            listOf(if (accepted) null else "policy_required", FormCheck.nick(nick), FormCheck.email(email), FormCheck.password(password, repeat)),
            { online.registerEmail(nick, email.trim(), password) },
        ) { if (it is OnlineResult.CodeSent) onCodeSent(it.email) }
    }
    FormCard(stringResource(R.string.account_email_option)) {
        PixelTextField(email, { email = it }, stringResource(R.string.field_email), keyboard = KeyboardType.Email, maxLength = 254)
        VSpace(3)
        PixelTextField(nick, { nick = it }, stringResource(R.string.field_nick), maxLength = 16)
        VSpace(3)
        PixelTextField(password, { password = it }, stringResource(R.string.field_password), password = true, maxLength = 72)
        VSpace(3)
        PixelTextField(repeat, { repeat = it }, stringResource(R.string.field_password2), password = true, maxLength = 72, imeAction = ImeAction.Done, onDone = go)
        VSpace(5)
        PixelButton(stringResource(R.string.register_button), onClick = go, style = ButtonStyle.GREEN, size = 3, enabled = !form.busy, modifier = Modifier.fillMaxWidth())
        FormFeedback(form)
    }
    PolicyBlock(accepted) { accepted = !accepted }
}

@Composable
private fun CodeForm(email: String, resend: (suspend () -> OnlineResult)?, confirm: suspend (String) -> OnlineResult) {
    val form = remember { FormState() }
    val submit = rememberSubmit(form)
    var code by remember { mutableStateOf("") }
    val go = { submit(listOf(FormCheck.code(code)), { confirm(code.trim()) }) {} }
    FormCard(stringResource(R.string.field_code)) {
        PixelText(stringResource(R.string.code_sent, email), size = 1, color = Pal.Foam)
        VSpace(4)
        PixelTextField(code, { v -> code = v.filter { it.isDigit() } }, stringResource(R.string.field_code), keyboard = KeyboardType.NumberPassword, maxLength = 6, imeAction = ImeAction.Done, onDone = go)
        VSpace(5)
        PixelButton(stringResource(R.string.code_confirm), onClick = go, style = ButtonStyle.GREEN, size = 3, enabled = !form.busy, modifier = Modifier.fillMaxWidth())
        if (resend != null) {
            VSpace(3)
            PixelButton(stringResource(R.string.code_resend), onClick = { submit(emptyList(), resend) {} }, style = ButtonStyle.SEA, size = 2, enabled = !form.busy, modifier = Modifier.fillMaxWidth())
        }
        FormFeedback(form)
    }
}

@Composable
private fun LoginForm(onForgot: () -> Unit) {
    val online = LocalServices.current.online
    val form = remember { FormState() }
    val submit = rememberSubmit(form)
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val go = { submit(listOf(if (login.isBlank()) "invalid_credentials" else null, if (password.isEmpty()) "invalid_credentials" else null), { online.login(login, password) }) {} }
    FormCard(stringResource(R.string.login_button)) {
        PixelTextField(login, { login = it }, stringResource(R.string.field_login), keyboard = KeyboardType.Email, maxLength = 254)
        VSpace(3)
        PixelTextField(password, { password = it }, stringResource(R.string.field_password), password = true, maxLength = 72, imeAction = ImeAction.Done, onDone = go)
        VSpace(5)
        PixelButton(stringResource(R.string.login_button), onClick = go, style = ButtonStyle.GREEN, size = 3, enabled = !form.busy, modifier = Modifier.fillMaxWidth())
        VSpace(3)
        PixelButton(stringResource(R.string.forgot_password), onClick = onForgot, style = ButtonStyle.SEA, size = 2, modifier = Modifier.fillMaxWidth())
        FormFeedback(form)
    }
}

@Composable
private fun ResetEmailForm(onCodeSent: (String) -> Unit) {
    val online = LocalServices.current.online
    val form = remember { FormState() }
    val submit = rememberSubmit(form)
    var email by remember { mutableStateOf("") }
    val go = { submit(listOf(FormCheck.email(email)), { online.resetStart(email.trim()) }) { if (it is OnlineResult.CodeSent) onCodeSent(it.email) } }
    FormCard(stringResource(R.string.reset_title)) {
        PixelText(stringResource(R.string.reset_info), size = 1, color = Pal.Foam)
        VSpace(4)
        PixelTextField(email, { email = it }, stringResource(R.string.field_email), keyboard = KeyboardType.Email, maxLength = 254, imeAction = ImeAction.Done, onDone = go)
        VSpace(5)
        PixelButton(stringResource(R.string.reset_send), onClick = go, style = ButtonStyle.GREEN, size = 3, enabled = !form.busy, modifier = Modifier.fillMaxWidth())
        FormFeedback(form)
    }
}

@Composable
private fun ResetCodeForm(email: String) {
    val online = LocalServices.current.online
    val form = remember { FormState() }
    val submit = rememberSubmit(form)
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val go = { submit(listOf(FormCheck.code(code), FormCheck.password(password, repeat)), { online.resetFinish(email, code.trim(), password) }) {} }
    FormCard(stringResource(R.string.reset_title)) {
        PixelText(stringResource(R.string.code_sent, email), size = 1, color = Pal.Foam)
        VSpace(4)
        PixelTextField(code, { v -> code = v.filter { it.isDigit() } }, stringResource(R.string.field_code), keyboard = KeyboardType.NumberPassword, maxLength = 6)
        VSpace(3)
        PixelTextField(password, { password = it }, stringResource(R.string.field_new_password), password = true, maxLength = 72)
        VSpace(3)
        PixelTextField(repeat, { repeat = it }, stringResource(R.string.field_password2), password = true, maxLength = 72, imeAction = ImeAction.Done, onDone = go)
        VSpace(5)
        PixelButton(stringResource(R.string.reset_save), onClick = go, style = ButtonStyle.GREEN, size = 3, enabled = !form.busy, modifier = Modifier.fillMaxWidth())
        FormFeedback(form)
    }
}

@Composable
private fun AccountProfile(onPick: (AccountMode) -> Unit) {
    val online = LocalServices.current.online
    val context = LocalContext.current
    val account = online.account ?: return
    var confirmLogout by remember { mutableStateOf(false) }
    DarkCard(Modifier.fillMaxWidth(), highlight = true) {
        Column(Modifier.fillMaxWidth()) {
            ProfileRow(stringResource(R.string.account_nick), account.nick)
            ProfileRow(
                stringResource(R.string.account_type),
                stringResource(
                    when {
                        !account.hasPassword -> R.string.account_type_nick
                        account.email == null -> R.string.account_type_password
                        else -> R.string.account_type_email
                    },
                ),
                if (account.hasPassword) Pal.White else Pal.Sand,
            )
            if (account.hasPassword) {
                ProfileRow(stringResource(R.string.account_email), account.email ?: stringResource(R.string.account_no_email), if (account.email == null) Pal.Sand else Pal.White)
            }
            ProfileRow(stringResource(R.string.account_points), account.points.toString())
            ProfileRow(stringResource(R.string.account_rank), account.rank?.let { "#$it" } ?: stringResource(R.string.ranking_not_ranked))
            val sync = when (online.status) {
                SyncStatus.OK -> stringResource(R.string.sync_ok, DateFormat.getTimeFormat(context).format(Date(online.lastSyncAt)))
                SyncStatus.SYNCING -> stringResource(R.string.sync_syncing)
                SyncStatus.WAITING -> stringResource(R.string.sync_waiting)
                SyncStatus.ERROR -> stringResource(R.string.sync_error)
                SyncStatus.IDLE -> stringResource(R.string.sync_idle)
            }
            ProfileRow(stringResource(R.string.account_sync), sync, if (online.status == SyncStatus.ERROR) Pal.RedLight else Pal.GreenLight)
        }
    }
    if (account.hidden) Hint(stringResource(R.string.account_hidden), Pal.RedLight)
    if (!account.hasPassword) Hint(stringResource(R.string.account_no_password_warning), Pal.Sand)
    PixelButton(stringResource(R.string.sync_now), onClick = { online.syncNow() }, style = ButtonStyle.GREEN, size = 2, enabled = online.status != SyncStatus.SYNCING, modifier = Modifier.fillMaxWidth())
    if (!account.hasPassword) {
        // Konto „tylko nick”: najpierw hasło, dopiero potem e-mail.
        PixelButton(stringResource(R.string.account_set_password), onClick = { onPick(AccountMode.SET_PASSWORD) }, style = ButtonStyle.ORANGE, size = 2, modifier = Modifier.fillMaxWidth())
        PixelButton(stringResource(R.string.account_add_email), onClick = {}, enabled = false, size = 2, modifier = Modifier.fillMaxWidth())
        Hint(stringResource(R.string.account_email_needs_password), Pal.Foam)
    } else {
        if (account.email == null) {
            PixelButton(stringResource(R.string.account_add_email), onClick = { onPick(AccountMode.ADD_EMAIL) }, style = ButtonStyle.SAND, size = 2, modifier = Modifier.fillMaxWidth())
        }
        PixelButton(stringResource(R.string.account_change_password), onClick = { onPick(AccountMode.CHANGE_PASSWORD) }, style = ButtonStyle.SEA, size = 2, modifier = Modifier.fillMaxWidth())
    }
    PixelButton(stringResource(R.string.account_logout), onClick = { confirmLogout = true }, style = ButtonStyle.WOOD, size = 2, modifier = Modifier.fillMaxWidth())
    VSpace(4)
    PixelButton(stringResource(R.string.account_delete), onClick = { onPick(AccountMode.DELETE) }, style = ButtonStyle.RED, size = 2, modifier = Modifier.fillMaxWidth())
    if (confirmLogout) {
        ConfirmDialog(
            message = stringResource(if (account.hasPassword) R.string.account_logout_confirm else R.string.account_logout_nick_confirm),
            onConfirm = {
                confirmLogout = false
                online.logout()
            },
            onCancel = { confirmLogout = false },
        )
    }
}

@Composable
private fun ProfileRow(label: String, value: String, color: androidx.compose.ui.graphics.Color = Pal.White) {
    Row(Modifier.fillMaxWidth().padding(vertical = px(2)), verticalAlignment = Alignment.CenterVertically) {
        PixelText(label, size = 1, color = Pal.Foam, modifier = Modifier.weight(0.4f))
        PixelText(value, size = 2, color = color, align = TextAlign.End, modifier = Modifier.weight(0.6f))
    }
}

@Composable
private fun AddEmailForm(onCodeSent: (String) -> Unit) {
    val online = LocalServices.current.online
    val form = remember { FormState() }
    val submit = rememberSubmit(form)
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val go = { submit(listOf(FormCheck.email(email), if (password.isEmpty()) "invalid_credentials" else null), { online.addEmail(email.trim(), password) }) { if (it is OnlineResult.CodeSent) onCodeSent(it.email) } }
    FormCard(stringResource(R.string.account_add_email)) {
        PixelText(stringResource(R.string.account_add_email_info), size = 1, color = Pal.Foam)
        VSpace(4)
        PixelTextField(email, { email = it }, stringResource(R.string.field_email), keyboard = KeyboardType.Email, maxLength = 254)
        VSpace(3)
        PixelTextField(password, { password = it }, stringResource(R.string.field_old_password), password = true, maxLength = 72, imeAction = ImeAction.Done, onDone = go)
        VSpace(5)
        PixelButton(stringResource(R.string.reset_send), onClick = go, style = ButtonStyle.GREEN, size = 3, enabled = !form.busy, modifier = Modifier.fillMaxWidth())
        FormFeedback(form)
    }
}

@Composable
private fun AddEmailCodeForm(email: String, onDone: () -> Unit) {
    val online = LocalServices.current.online
    CodeForm(email, resend = null) { code ->
        online.confirmEmail(email, code).also { if (it == OnlineResult.Ok) onDone() }
    }
}

@Composable
private fun ChangePasswordForm(onDone: () -> Unit) {
    val online = LocalServices.current.online
    val form = remember { FormState() }
    val submit = rememberSubmit(form)
    var old by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val done = stringResource(R.string.password_changed)
    val go = {
        submit(listOf(if (old.isEmpty()) "invalid_credentials" else null, FormCheck.password(password, repeat)), { online.changePassword(old, password) }) {
            old = ""
            password = ""
            repeat = ""
            form.info = done
        }
    }
    FormCard(stringResource(R.string.account_change_password)) {
        PixelTextField(old, { old = it }, stringResource(R.string.field_old_password), password = true, maxLength = 72)
        VSpace(3)
        PixelTextField(password, { password = it }, stringResource(R.string.field_new_password), password = true, maxLength = 72)
        VSpace(3)
        PixelTextField(repeat, { repeat = it }, stringResource(R.string.field_password2), password = true, maxLength = 72, imeAction = ImeAction.Done, onDone = go)
        VSpace(5)
        PixelButton(stringResource(R.string.save_button), onClick = go, style = ButtonStyle.GREEN, size = 3, enabled = !form.busy, modifier = Modifier.fillMaxWidth())
        FormFeedback(form)
        if (form.info != null) {
            VSpace(3)
            PixelButton(stringResource(R.string.close), onClick = onDone, style = ButtonStyle.SEA, size = 2, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SetPasswordForm(onDone: () -> Unit) {
    val online = LocalServices.current.online
    val form = remember { FormState() }
    val submit = rememberSubmit(form)
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val done = stringResource(R.string.password_set)
    val go = {
        submit(listOf(FormCheck.password(password, repeat)), { online.setPassword(password) }) {
            password = ""
            repeat = ""
            form.info = done
        }
    }
    FormCard(stringResource(R.string.account_set_password)) {
        PixelText(stringResource(R.string.account_set_password_info), size = 1, color = Pal.Foam)
        VSpace(4)
        PixelTextField(password, { password = it }, stringResource(R.string.field_new_password), password = true, maxLength = 72)
        VSpace(3)
        PixelTextField(repeat, { repeat = it }, stringResource(R.string.field_password2), password = true, maxLength = 72, imeAction = ImeAction.Done, onDone = go)
        VSpace(5)
        PixelButton(stringResource(R.string.save_button), onClick = go, style = ButtonStyle.GREEN, size = 3, enabled = !form.busy && form.info == null, modifier = Modifier.fillMaxWidth())
        FormFeedback(form)
        if (form.info != null) {
            VSpace(3)
            PixelButton(stringResource(R.string.close), onClick = onDone, style = ButtonStyle.SEA, size = 2, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DeleteAccountForm() {
    val online = LocalServices.current.online
    val form = remember { FormState() }
    val submit = rememberSubmit(form)
    var password by remember { mutableStateOf("") }
    // Konto „tylko nick” nie ma hasła – usuwa się samą sesją z tego telefonu.
    val needsPassword = online.account?.hasPassword != false
    val go = {
        submit(listOf(if (needsPassword && password.isEmpty()) "invalid_credentials" else null), { online.deleteAccount(if (needsPassword) password else null) }) {}
    }
    FormCard(stringResource(R.string.account_delete)) {
        PixelText(stringResource(if (needsPassword) R.string.account_delete_info else R.string.account_delete_nick_info), size = 1, color = Pal.Foam)
        VSpace(4)
        if (needsPassword) {
            PixelTextField(password, { password = it }, stringResource(R.string.field_password), password = true, maxLength = 72, imeAction = ImeAction.Done, onDone = go)
        }
        VSpace(5)
        PixelButton(stringResource(R.string.account_delete), onClick = go, style = ButtonStyle.RED, size = 3, enabled = !form.busy, modifier = Modifier.fillMaxWidth())
        FormFeedback(form)
    }
}

// ------------------------------------------------------------------ okna synchronizacji (na każdym ekranie poza grą)

@Composable
fun OnlineDialogs() {
    val online = LocalServices.current.online
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<OnlineResult.Failed?>(null) }
    if (online.restoreChoice || online.conflict) {
        Overlay {
            PixelText(stringResource(if (online.conflict) R.string.conflict_title else R.string.restore_title), size = 4, color = Pal.WoodDark, outline = null)
            VSpace(4)
            PixelText(
                stringResource(if (online.conflict) R.string.conflict_info else R.string.restore_info),
                size = 2,
                color = Pal.WoodDark,
                outline = null,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                VSpace(3)
                PixelText(onlineErrorText(it), size = 1, color = Pal.RedDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            VSpace(8)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(px(5))) {
                listOf(true, false).forEach { useAccount ->
                    PixelButton(
                        stringResource(if (useAccount) R.string.restore_account else R.string.restore_phone),
                        onClick = {
                            busy = true
                            error = null
                            online.resolve(useAccount) { result ->
                                busy = false
                                error = result as? OnlineResult.Failed
                            }
                        },
                        style = if (useAccount) ButtonStyle.GREEN else ButtonStyle.SEA,
                        size = 2,
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
    if (online.notice == "session_expired") {
        Overlay(onDismiss = { online.notice = null }) {
            PixelText(stringResource(R.string.session_expired), size = 2, color = Pal.WoodDark, outline = null, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            VSpace(8)
            PixelButton(stringResource(R.string.close), onClick = { online.notice = null })
        }
    }
}
