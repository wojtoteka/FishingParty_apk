package wojtoteka.ovh.fishingparty.online

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import wojtoteka.ovh.fishingparty.BuildConfig
import wojtoteka.ovh.fishingparty.core.GuardedLong
import wojtoteka.ovh.fishingparty.data.GameRepository
import wojtoteka.ovh.fishingparty.data.ScoreRecord
import wojtoteka.ovh.fishingparty.level.LevelConstants

/** Profil gracza z serwera. */
class AccountInfo(
    val nick: String,
    val email: String?,
    val points: Long,
    val rank: Int?,
    val hidden: Boolean,
    val maxLevel: Int,
)

class RankingEntry(val rank: Int, val id: Long, val nick: String, val points: Long, val maxLevel: Int)

/** [myId] = null, gdy ranking ogląda gość bez konta. */
class RankingData(val entries: List<RankingEntry>, val myId: Long?, val myRank: Int?, val myPoints: Long, val hidden: Boolean)

/** Wynik operacji na koncie. [code] to kod błędu serwera albo jeden z kodów klienta (offline, quick_wrong…). */
sealed interface OnlineResult {
    data object Ok : OnlineResult
    data class CodeSent(val email: String) : OnlineResult
    data class Failed(val code: String, val retryAfter: Int = 0, val attemptsLeft: Int = -1) : OnlineResult
}

enum class SyncStatus { IDLE, SYNCING, OK, WAITING, ERROR }

/**
 * Konto online: logowanie, rejestracja, ranking i synchronizacja postępu
 * na bieżąco. Gra działa w pełni offline – wszystko, czego nie dało się
 * wysłać, czeka i leci przy następnym połączeniu.
 */
class OnlineManager(
    context: Context,
    private val repo: GameRepository,
    /** Po przywróceniu zapisu z konta (ustawienia dźwięku itp.). */
    private val onRestored: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val store = AccountStore(appContext.noBackupFilesDir, repo.crypto)
    private val api = ApiClient(
        BuildConfig.FP_API_URL,
        BuildConfig.FP_HMAC_SECRET,
        BuildConfig.VERSION_CODE,
        object : DeviceSigner {
            override val deviceId: String get() = store.deviceId
            override fun sign(message: String): String? = DeviceKey.sign(message)
        },
    )
    private val deviceLock = Mutex()

    /** Rośnie przy każdej udanej atestacji – równoległe żądania nie tworzą klucza kilka razy. */
    @Volatile
    private var deviceGeneration = 0

    /** Serwer ma wyłączoną atestację (tryb testowy) – nie pytamy o nią do restartu gry. */
    @Volatile
    private var attestationOff = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val syncLock = Mutex()
    private var snapshotJob: Job? = null

    /** Ustawiane przez UI: zapis z konta wolno podmienić tylko poza rozgrywką. */
    var canAutoPull: () -> Boolean = { true }

    var account by mutableStateOf<AccountInfo?>(null)
        private set
    var status by mutableStateOf(SyncStatus.IDLE)
        private set
    var lastSyncAt by mutableLongStateOf(0L)
        private set

    /** Zapis na koncie zmienił inny telefon, a ten też ma niewysłane zmiany – gracz wybiera. */
    var conflict by mutableStateOf(false)
        private set

    /** Po zalogowaniu na telefonie, który ma już własny postęp – gracz wybiera, który zachować. */
    var restoreChoice by mutableStateOf(false)
        private set

    /** Komunikat do pokazania (np. sesja wygasła). */
    var notice by mutableStateOf<String?>(null)

    /** Czytane przez Compose – [account] jest stanem, więc ekrany odświeżają się po zalogowaniu i wylogowaniu. */
    val loggedIn: Boolean get() = account != null

    val configured: Boolean get() = api.configured

    init {
        if (store.loggedIn) {
            account = AccountInfo(store.nick, store.email, 0, null, false, 0)
        }
        repo.onChanged = ::onLocalChange
        val cm = appContext.getSystemService(ConnectivityManager::class.java)
        try {
            cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch { if (store.loggedIn) sync() }
                }
            })
        } catch (e: Exception) {
            Log.w("FishingParty", "Brak nasłuchu sieci", e)
        }
    }

    fun isOnline(): Boolean {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** [device] = false: bez atestacji telefonu (tylko podgląd rankingu przez gościa). */
    private suspend fun call(method: String, path: String, body: JSONObject? = null, auth: Boolean = true, device: Boolean = true): JSONObject =
        withContext(Dispatchers.IO) {
            if (!device) return@withContext api.call(method, path, body, null)
            val generation = ensureDevice()
            try {
                api.call(method, path, body, if (auth) store.token else null)
            } catch (e: ApiException) {
                if (e.code != "device_unverified") throw e
                // Serwer nie zna już klucza tego telefonu (np. admin kazał go zapomnieć) – nowa atestacja i jedna powtórka.
                ensureDevice(staleGeneration = generation)
                api.call(method, path, body, if (auth) store.token else null)
            }
        }

    /**
     * Rejestruje klucz telefonu na serwerze (atestacja), jeśli jeszcze tego nie zrobiono.
     * [staleGeneration] wymusza nowy klucz, chyba że w międzyczasie zrobiło to inne żądanie.
     */
    private suspend fun ensureDevice(staleGeneration: Int? = null): Int = deviceLock.withLock {
        val needed = if (staleGeneration != null) staleGeneration == deviceGeneration else !(store.deviceAttested && DeviceKey.exists())
        if (!needed || attestationOff) return@withLock deviceGeneration
        val res = api.call("POST", "/device/challenge", JSONObject().put("deviceId", store.deviceId))
        if (res.optString("mode") == "off") {
            attestationOff = true
            return@withLock deviceGeneration
        }
        val chain = try {
            DeviceKey.create(Base64.decode(res.getString("challenge"), Base64.NO_WRAP))
        } catch (e: Exception) {
            Log.w("FishingParty", "Telefon nie obsługuje atestacji klucza", e)
            throw ApiException(0, "device_unsupported", JSONObject())
        }
        try {
            api.call("POST", "/device/attest", JSONObject().put("deviceId", store.deviceId).put("chain", JSONArray(chain)))
        } catch (e: ApiException) {
            if (e.code == "attestation_failed") {
                Log.w("FishingParty", "Serwer odrzucił atestację: ${e.body.optString("reason")}")
                throw ApiException(e.status, "device_unverified", e.body)
            }
            throw e
        }
        store.deviceAttested = true
        store.save()
        deviceGeneration++
        deviceGeneration
    }

    private fun failure(e: Exception): OnlineResult.Failed = when (e) {
        is ApiException -> {
            if (e.code == "not_logged_in" && store.loggedIn) dropSession()
            OnlineResult.Failed(e.code, e.body.optInt("retryAfter", 0), e.body.optInt("attemptsLeft", -1))
        }
        is OfflineException -> OnlineResult.Failed(if (isOnline()) "server_unreachable" else "offline")
        else -> {
            Log.e("FishingParty", "Błąd konta online", e)
            OnlineResult.Failed("unknown")
        }
    }

    private suspend fun guarded(block: suspend () -> OnlineResult): OnlineResult = try {
        if (!isOnline()) OnlineResult.Failed("offline") else block()
    } catch (e: Exception) {
        failure(e)
    }

    // ------------------------------------------------------------------ konto

    private fun deviceBody(vararg pairs: Pair<String, Any?>) = JSONObject().apply {
        put("deviceId", store.deviceId)
        for ((k, v) in pairs) if (v != null) put(k, v)
    }

    /**
     * Tryb rankingowy: nick + hasło. Istniejący nick z poprawnym hasłem = logowanie
     * (przywraca postęp po reinstalacji), nowy nick = nowe konto.
     */
    suspend fun quickJoin(nick: String, password: String): OnlineResult = guarded {
        try {
            afterAuth(call("POST", "/auth/login", deviceBody("login" to nick.trim(), "password" to password), auth = false), fresh = false)
        } catch (e: ApiException) {
            if (e.code != "invalid_credentials") throw e
            try {
                afterAuth(
                    call("POST", "/auth/register", deviceBody("nick" to nick, "password" to password, "acceptPolicy" to true, "policyVersion" to POLICY_VERSION), auth = false),
                    fresh = true,
                )
            } catch (r: ApiException) {
                // Nick istnieje, a logowanie się nie udało – hasło nie pasuje.
                if (r.code == "nick_taken") OnlineResult.Failed("quick_wrong") else throw r
            }
        }
    }

    suspend fun registerEmail(nick: String, email: String, password: String): OnlineResult = guarded {
        val res = call(
            "POST", "/auth/register",
            deviceBody("nick" to nick, "email" to email, "password" to password, "acceptPolicy" to true, "policyVersion" to POLICY_VERSION),
            auth = false,
        )
        if (res.optBoolean("pending")) OnlineResult.CodeSent(res.optString("email", email)) else afterAuth(res, fresh = true)
    }

    suspend fun verifyEmail(email: String, code: String): OnlineResult = guarded {
        afterAuth(call("POST", "/auth/verify", deviceBody("email" to email, "code" to code), auth = false), fresh = true)
    }

    suspend fun login(login: String, password: String): OnlineResult = guarded {
        afterAuth(call("POST", "/auth/login", deviceBody("login" to login.trim(), "password" to password), auth = false), fresh = false)
    }

    suspend fun resetStart(email: String): OnlineResult = guarded {
        call("POST", "/auth/password-reset/start", JSONObject().put("email", email), auth = false)
        OnlineResult.CodeSent(email)
    }

    suspend fun resetFinish(email: String, code: String, password: String): OnlineResult = guarded {
        afterAuth(
            call("POST", "/auth/password-reset/finish", deviceBody("email" to email, "code" to code, "password" to password), auth = false),
            fresh = false,
        )
    }

    suspend fun addEmail(email: String, password: String): OnlineResult = guarded {
        val res = call("POST", "/account/email", JSONObject().put("email", email).put("password", password))
        OnlineResult.CodeSent(res.optString("email", email))
    }

    suspend fun confirmEmail(email: String, code: String): OnlineResult = guarded {
        applyProfile(call("POST", "/account/email/confirm", JSONObject().put("email", email).put("code", code)).getJSONObject("account"))
        OnlineResult.Ok
    }

    suspend fun changePassword(old: String, new: String): OnlineResult = guarded {
        call("POST", "/account/password", JSONObject().put("oldPassword", old).put("password", new))
        OnlineResult.Ok
    }

    suspend fun deleteAccount(password: String): OnlineResult = guarded {
        call("POST", "/account/delete", JSONObject().put("password", password))
        dropSession()
        OnlineResult.Ok
    }

    /** Wylogowanie – postęp zostaje na telefonie, przestaje się tylko synchronizować. */
    fun logout() {
        val token = store.token
        scope.launch {
            // Przed wylogowaniem dosyłamy to, co czeka.
            if (isOnline()) {
                try {
                    syncLock.withLock { pushAll() }
                    if (token != null) withContext(Dispatchers.IO) { api.call("POST", "/auth/logout", null, token) }
                } catch (_: Exception) {
                }
            }
            dropSession()
        }
    }

    private fun dropSession() {
        snapshotJob?.cancel()
        store.clearSession()
        account = null
        conflict = false
        restoreChoice = false
        status = SyncStatus.IDLE
    }

    private fun applyProfile(p: JSONObject) {
        val info = AccountInfo(
            nick = p.optString("nick"),
            email = p.optString("email").takeIf { !p.isNull("email") && it.isNotEmpty() },
            points = p.optLong("points"),
            rank = if (p.isNull("rank")) null else p.optInt("rank"),
            hidden = p.optBoolean("hidden"),
            maxLevel = p.optInt("maxLevel"),
        )
        account = info
        if (store.nick != info.nick || store.email != info.email) {
            store.nick = info.nick
            store.email = info.email
            store.save()
        }
    }

    private fun latestRecordAt(): Long = repo.save.records.maxOfOrNull { it.at } ?: 0L

    private suspend fun afterAuth(res: JSONObject, fresh: Boolean): OnlineResult {
        val profile = res.getJSONObject("account")
        store.token = res.getString("token")
        store.accountId = profile.optLong("id")
        store.knownRevision = 0
        store.importPending = false
        store.dirty = true
        applyProfile(profile)
        val snapshot = res.optJSONObject("snapshot")
        val remote = snapshot?.optBoolean("exists") == true
        when {
            fresh -> {
                // Nowe konto: dotychczasowe rekordy jadą jednorazowym importem, kolejne przejazdy na bieżąco.
                store.importPending = repo.progress.completedLevels() > 0
                store.lastSentRunAt = latestRecordAt()
            }
            remote && repo.progress.completedLevels() == 0 -> {
                pullSnapshot()
            }
            remote -> {
                store.lastSentRunAt = latestRecordAt()
                restoreChoice = true
            }
            else -> store.lastSentRunAt = 0L
        }
        store.save()
        if (!restoreChoice) scope.launch { sync() }
        return OnlineResult.Ok
    }

    /** Wybór po zalogowaniu albo przy konflikcie: true = postęp z konta, false = postęp z tego telefonu. */
    fun resolve(useAccount: Boolean, onDone: (OnlineResult) -> Unit = {}) {
        scope.launch {
            val result = guarded {
                syncLock.withLock {
                    if (useAccount) pullSnapshot() else pushSnapshot(force = true)
                }
                OnlineResult.Ok
            }
            if (result == OnlineResult.Ok) {
                restoreChoice = false
                conflict = false
                sync()
            }
            onDone(result)
        }
    }

    private suspend fun pullSnapshot() {
        val res = call("GET", "/sync/snapshot")
        val bytes = Base64.decode(res.getString("data"), Base64.NO_WRAP)
        repo.restoreSnapshot(bytes)
        store.knownRevision = res.getInt("revision")
        store.lastSentRunAt = latestRecordAt()
        store.dirty = false
        store.save()
        onRestored()
    }

    // ------------------------------------------------------------------ synchronizacja

    private fun onLocalChange() {
        if (!store.loggedIn) return
        if (!store.dirty) {
            store.dirty = true
            store.save()
        }
        // Ukończony poziom leci od razu, pełny zapis chwilę po ostatniej zmianie.
        if (repo.save.records.any { it.at > store.lastSentRunAt }) scope.launch { sync(snapshot = false) }
        snapshotJob?.cancel()
        snapshotJob = scope.launch {
            delay(SNAPSHOT_DEBOUNCE_MS)
            sync()
        }
    }

    /** Aplikacja wróciła na ekran: sprawdzamy, czy inny telefon czegoś nie zapisał. */
    fun onForeground() {
        if (store.loggedIn) scope.launch { sync(checkRemote = true) }
    }

    /** Aplikacja schodzi z ekranu – wysyłamy wszystko od razu. */
    fun onBackground() {
        if (store.loggedIn && store.dirty) {
            snapshotJob?.cancel()
            scope.launch { sync() }
        }
    }

    fun syncNow() {
        scope.launch { sync(checkRemote = true) }
    }

    private suspend fun sync(snapshot: Boolean = true, checkRemote: Boolean = false) {
        if (!store.loggedIn || restoreChoice || conflict || !api.configured) return
        if (!isOnline()) {
            if (store.dirty) status = SyncStatus.WAITING
            return
        }
        syncLock.withLock {
            status = SyncStatus.SYNCING
            try {
                if (checkRemote) {
                    val state = call("GET", "/sync/state")
                    val remoteRevision = state.optInt("revision")
                    val remoteDevice = state.optString("deviceId")
                    if (state.optBoolean("exists") && remoteRevision != store.knownRevision && remoteDevice != store.deviceId) {
                        if (!store.dirty && canAutoPull()) {
                            pullSnapshot()
                        } else if (store.dirty) {
                            conflict = true
                            status = SyncStatus.IDLE
                            return
                        }
                    }
                }
                pushRuns()
                if (snapshot) pushSnapshot(force = false)
                status = SyncStatus.OK
                lastSyncAt = System.currentTimeMillis()
            } catch (e: ApiException) {
                when (e.code) {
                    "snapshot_conflict" -> conflict = true
                    "not_logged_in" -> {
                        dropSession()
                        notice = "session_expired"
                    }
                    else -> Log.w("FishingParty", "Synchronizacja: ${e.code}")
                }
                if (store.loggedIn) status = SyncStatus.ERROR
            } catch (e: OfflineException) {
                status = SyncStatus.WAITING
            } catch (e: Exception) {
                Log.e("FishingParty", "Synchronizacja nieudana", e)
                status = SyncStatus.ERROR
            }
        }
    }

    private suspend fun pushAll() {
        pushRuns()
        pushSnapshot(force = false)
    }

    private fun integrity() = JSONObject()
        .put("tamperEvents", repo.save.tamperEvents)
        .put("memoryTampered", GuardedLong.tampered)
        .put("saveTampered", repo.saveWasTampered)
        .put("debuggable", BuildConfig.DEBUG)

    private suspend fun pushRuns() {
        if (store.importPending) {
            val bests = JSONArray()
            for (i in 0 until LevelConstants.LEVEL_COUNT) bests.put(repo.save.best[i])
            try {
                val res = call(
                    "POST", "/sync/import",
                    JSONObject()
                        .put("bests", bests)
                        .put("trophies", repo.save.trophies.get())
                        .put("streakBest", repo.save.streakBest)
                        .put("integrity", integrity()),
                )
                applyProfile(res.getJSONObject("account"))
            } catch (e: ApiException) {
                if (e.code != "already_imported" && e.code != "import_expired") throw e
            }
            store.importPending = false
            store.save()
        }
        while (true) {
            val pending: List<ScoreRecord> = repo.save.records.filter { it.at > store.lastSentRunAt }.take(MAX_RUNS_PER_REQUEST)
            val runs = JSONArray()
            for (r in pending) {
                runs.put(
                    JSONObject()
                        .put("level", r.level)
                        .put("score", r.score)
                        .put("timeMs", r.timeMillis)
                        .put("common", r.common)
                        .put("blue", r.blue)
                        .put("golden", r.golden)
                        .put("hits", r.hits)
                        .put("maxCombo", r.maxCombo)
                        .put("stages", r.stages)
                        .put("at", r.at),
                )
            }
            val res = call(
                "POST", "/runs",
                JSONObject().put("runs", runs).put("trophies", repo.save.trophies.get()).put("integrity", integrity()),
            )
            applyProfile(res.getJSONObject("account"))
            if (pending.isEmpty()) return
            store.lastSentRunAt = pending.maxOf { it.at }
            store.save()
            if (pending.size < MAX_RUNS_PER_REQUEST) return
        }
    }

    private suspend fun pushSnapshot(force: Boolean) {
        if (!store.dirty && !force) return
        val data = Base64.encodeToString(repo.exportSnapshot(), Base64.NO_WRAP)
        val body = JSONObject().put("baseRevision", store.knownRevision).put("deviceId", store.deviceId).put("data", data)
        val res = call("PUT", if (force) "/sync/snapshot?force=1" else "/sync/snapshot", body)
        store.knownRevision = res.getInt("revision")
        store.dirty = false
        store.save()
    }

    // ------------------------------------------------------------------ ranking

    suspend fun ranking(): Result<RankingData> = try {
        if (!isOnline()) {
            Result.failure(OfflineException())
        } else {
            // Gość też widzi ranking – bez tokenu i bez rejestrowania klucza telefonu.
            val guest = !store.loggedIn
            val res = call("GET", "/ranking", auth = !guest, device = !guest)
            val list = res.getJSONArray("entries")
            val entries = (0 until list.length()).map { i ->
                val e = list.getJSONObject(i)
                RankingEntry(e.getInt("rank"), e.getLong("id"), e.getString("nick"), e.getLong("points"), e.optInt("maxLevel"))
            }
            val me = res.optJSONObject("me")
            if (me == null) {
                Result.success(RankingData(entries, null, null, 0, false))
            } else {
                val rank = if (me.isNull("rank")) null else me.optInt("rank")
                account?.let {
                    account = AccountInfo(it.nick, it.email, me.optLong("points"), rank, me.optBoolean("hidden"), it.maxLevel)
                }
                Result.success(RankingData(entries, me.optLong("id"), rank, me.optLong("points"), me.optBoolean("hidden")))
            }
        }
    } catch (e: Exception) {
        if (e is ApiException && e.code == "not_logged_in" && store.loggedIn) dropSession()
        Result.failure(e)
    }

    suspend fun refreshProfile(): OnlineResult = guarded {
        applyProfile(call("GET", "/account/me").getJSONObject("account"))
        OnlineResult.Ok
    }

    companion object {
        /** Wersja polityki prywatności i regulaminu – musi zgadzać się z POLICY_VERSION serwera. */
        const val POLICY_VERSION = 1
        const val PRIVACY_URL = "https://wojtoteka.ovh/polityka-fishingparty"
        const val TERMS_URL = "https://wojtoteka.ovh/regulamin-fishingparty"
        private const val SNAPSHOT_DEBOUNCE_MS = 4_000L
        private const val MAX_RUNS_PER_REQUEST = 50
    }
}
