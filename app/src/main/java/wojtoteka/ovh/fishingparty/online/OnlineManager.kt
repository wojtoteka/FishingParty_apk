package wojtoteka.ovh.fishingparty.online

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
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
import wojtoteka.ovh.fishingparty.level.Challenge
import wojtoteka.ovh.fishingparty.level.ChallengeKind
import wojtoteka.ovh.fishingparty.level.LevelConstants
import wojtoteka.ovh.fishingparty.level.LevelSeed

/** Profil gracza z serwera. [hasPassword] = false – konto „tylko nick”. */
class AccountInfo(
    val nick: String,
    val email: String?,
    val points: Long,
    val rank: Int?,
    val hidden: Boolean,
    val maxLevel: Int,
    val hasPassword: Boolean = true,
    /** Punkty rankingu z ukończonych poziomów graczy (wliczone w [points]). */
    val userLevelPoints: Long = 0,
    /** Zarobek z targu czekający na odebranie. */
    val marketPending: Long = 0,
)

class RankingEntry(val rank: Int, val id: Long, val nick: String, val points: Long, val maxLevel: Int)

/** [myId] = null, gdy ranking ogląda gość bez konta. [size] – ile miejsc ma ranking (top 100). */
class RankingData(
    val entries: List<RankingEntry>,
    val myId: Long?,
    val myRank: Int?,
    val myPoints: Long,
    val hidden: Boolean,
    val size: Int = 100,
)

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
        repo.clock,
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

    /**
     * Zapis na koncie zmienił inny telefon, ten też ma niewysłane zmiany, a nie da się ich połączyć
     * (telefon nie zna ostatniego wspólnego zapisu, np. po aktualizacji ze starszej wersji) – gracz wybiera.
     */
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

    /**
     * Ta wersja gry jest zablokowana w panelu serwera – sprawdzane przy każdym wejściu do gry z internetem
     * (`GET /launch`), a podpisane żądania dostają `426 update_required`. Nie jest zapisywana w telefonie,
     * więc bez internetu gra działa normalnie.
     */
    var versionBlock by mutableStateOf<VersionBlock?>(null)
        private set

    /** Ogłoszenia z panelu, których gracz jeszcze nie zamknął – UI pokazuje pierwsze z nich. */
    val announcements = mutableStateListOf<Announcement>()
    private val launchPrefs = appContext.getSharedPreferences(LAUNCH_PREFS, Context.MODE_PRIVATE)
    private var launchJob: Job? = null
    private var launchCheckedAt = 0L

    /** Ostatnia lista ogłoszeń z serwera, a bez połączenia – zapisane ogłoszenia „przy każdym uruchomieniu”. */
    private var knownAnnouncements: List<Announcement> = emptyList()

    /** Ogłoszenia „przy każdym uruchomieniu” zamknięte od startu gry (po kolejnym uruchomieniu wracają). */
    private val shownThisLaunch = mutableSetOf<String>()

    /** Gra właśnie się uruchomiła – najbliższe udane sprawdzenie liczy się w statystykach jako uruchomienie. */
    private var newSession = true
    private var backgroundAt = 0L

    /** Poziomy graczy i targ. */
    val community = Community(this, repo)

    init {
        if (store.loggedIn) {
            account = AccountInfo(store.nick, store.email, 0, null, false, 0, store.hasPassword)
        }
        repo.onChanged = ::onLocalChange
        val cm = appContext.getSystemService(ConnectivityManager::class.java)
        try {
            cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch {
                        syncClockNow()
                        // Gra uruchomiona bez internetu sprawdza wersję i ogłoszenia, gdy tylko złapie sieć.
                        checkLaunch()
                        if (store.loggedIn) sync()
                        community.flush()
                        community.resumeClaim()
                    }
                }
            })
        } catch (e: Exception) {
            Log.w("FishingParty", "Brak nasłuchu sieci", e)
        }
        syncClock()
        // Zapisane ogłoszenia „przy każdym uruchomieniu” pokazują się od razu, także bez internetu.
        knownAnnouncements = loadSticky()
        rebuildAnnouncements()
        checkLaunch()
    }

    /**
     * Pobiera czas gry z serwera, jeśli telefon jeszcze go nie zna (po uruchomieniu telefonu).
     * Nagroda dzienna i passa liczą dni tylko według tego czasu – także dla gości bez konta.
     */
    fun syncClock() {
        if (!repo.clock.synced) scope.launch { syncClockNow() }
    }

    private suspend fun syncClockNow() {
        if (repo.clock.synced || !isOnline()) return
        withContext(Dispatchers.IO) {
            try {
                api.syncTime()
            } catch (e: Exception) {
                Log.w("FishingParty", "Nie udało się pobrać czasu z serwera: ${e.message}")
            }
        }
    }

    fun isOnline(): Boolean {
        val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** [device] = false: bez atestacji telefonu (tylko podgląd rankingu przez gościa). */
    internal suspend fun call(method: String, path: String, body: JSONObject? = null, auth: Boolean = true, device: Boolean = true): JSONObject = try {
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
    } catch (e: ApiException) {
        // Wersja zablokowana w trakcie gry (albo starsza niż minimalna) – od razu komunikat zamiast gry.
        if (e.status == 426 && e.code == "update_required") versionBlock = LaunchInfo.blockOf(e.body)
        throw e
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

    /** Identyfikator tej instalacji gry (gracz na poziomach graczy i kupujący na targu, także bez konta). */
    internal val deviceId: String get() = store.deviceId

    internal fun failureOf(e: Exception): OnlineResult.Failed = failure(e)

    /** Odpowiedź z profilem konta (np. po ukończeniu poziomu gracza) – odświeża punkty na ekranach. */
    internal fun applyProfileOf(res: JSONObject) {
        if (store.loggedIn) res.optJSONObject("account")?.let { applyProfile(it) }
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

    /**
     * Konto „tylko nick” – bez hasła i e-maila. Nick należy do gracza, dopóki jest zalogowany na tym
     * telefonie; po wylogowaniu przepada (wynik zostaje w rankingu). Hasło można dodać później ([setPassword]).
     */
    suspend fun joinNick(nick: String): OnlineResult = guarded {
        afterAuth(
            call("POST", "/auth/nick", deviceBody("nick" to nick, "acceptPolicy" to true, "policyVersion" to POLICY_VERSION), auth = false),
            fresh = true,
        )
    }

    /** Konto „tylko nick” dostaje hasło – od teraz da się na nie zalogować nickiem i hasłem, a potem dodać e-mail. */
    suspend fun setPassword(password: String): OnlineResult = guarded {
        applyProfile(call("POST", "/account/set-password", JSONObject().put("password", password)).getJSONObject("account"))
        OnlineResult.Ok
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

    /** [password] = null – konto „tylko nick” (bez hasła wystarczy sesja). */
    suspend fun deleteAccount(password: String?): OnlineResult = guarded {
        call("POST", "/account/delete", JSONObject().apply { if (password != null) put("password", password) })
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
        repo.rememberSynced(null)
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
            hasPassword = p.optBoolean("hasPassword", true),
            userLevelPoints = p.optLong("userLevelPoints"),
            marketPending = p.optLong("marketPending"),
        )
        account = info
        if (store.nick != info.nick || store.email != info.email || store.hasPassword != info.hasPassword) {
            store.nick = info.nick
            store.email = info.email
            store.hasPassword = info.hasPassword
            store.save()
        }
    }

    private fun latestRecordAt(): Long = repo.save.records.maxOfOrNull { it.at } ?: 0L

    private fun latestChallengeAt(): Long = repo.save.challengeResults.maxOfOrNull { it.at } ?: 0L

    private suspend fun afterAuth(res: JSONObject, fresh: Boolean): OnlineResult {
        val profile = res.getJSONObject("account")
        store.token = res.getString("token")
        store.accountId = profile.optLong("id")
        store.knownRevision = 0
        store.importPending = false
        store.dirty = true
        repo.rememberSynced(null)
        applyProfile(profile)
        val snapshot = res.optJSONObject("snapshot")
        val remote = snapshot?.optBoolean("exists") == true
        when {
            fresh -> {
                // Nowe konto: dotychczasowe rekordy jadą jednorazowym importem, kolejne przejazdy na bieżąco.
                store.importPending = repo.progress.completedLevels() > 0
                store.lastSentRunAt = latestRecordAt()
                // Poziomy dnia/tygodnia rozegrane jako gość lecą zwykłą drogą (serwer przyjmie te z bieżących okresów).
                store.lastSentChallengeAt = 0L
            }
            remote && repo.progress.completedLevels() == 0 -> {
                pullSnapshot()
            }
            remote -> {
                store.lastSentRunAt = latestRecordAt()
                store.lastSentChallengeAt = latestChallengeAt()
                restoreChoice = true
            }
            else -> {
                store.lastSentRunAt = 0L
                store.lastSentChallengeAt = 0L
            }
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
        repo.rememberSynced(bytes)
        store.knownRevision = res.getInt("revision")
        store.lastSentRunAt = latestRecordAt()
        store.lastSentChallengeAt = latestChallengeAt()
        store.dirty = false
        store.save()
        onRestored()
    }

    /**
     * Drugi telefon zapisał na koncie nowszy postęp, a ten ma swój niewysłany: łączymy oba (nic nie przepada)
     * i wysyłamy wynik. Gdy telefon nie zna ostatniego wspólnego zapisu (stara wersja gry), wybiera gracz.
     * Przejazdy i poziomy dnia/tygodnia tego telefonu, które czekają na wysłanie, nadal czekają.
     */
    private suspend fun mergeRemote() {
        val res = call("GET", "/sync/snapshot")
        val bytes = Base64.decode(res.getString("data"), Base64.NO_WRAP)
        if (!repo.mergeSnapshot(bytes)) {
            conflict = true
            return
        }
        repo.rememberSynced(bytes)
        store.knownRevision = res.getInt("revision")
        store.dirty = true
        store.save()
        onRestored()
        pushSnapshot(force = false)
    }

    // ------------------------------------------------------------------ synchronizacja

    private fun onLocalChange() {
        if (!store.loggedIn) return
        if (!store.dirty) {
            store.dirty = true
            store.save()
        }
        // Ukończony poziom leci od razu, pełny zapis chwilę po ostatniej zmianie.
        if (repo.save.records.any { it.at > store.lastSentRunAt } || repo.save.challengeResults.any { it.at > store.lastSentChallengeAt }) {
            scope.launch { sync(snapshot = false) }
        }
        snapshotJob?.cancel()
        snapshotJob = scope.launch {
            delay(SNAPSHOT_DEBOUNCE_MS)
            sync()
        }
    }

    /** Aplikacja wróciła na ekran: wersja i ogłoszenia, potem czy inny telefon czegoś nie zapisał. */
    fun onForeground() {
        // Powrót po dłuższej przerwie to dla gracza nowe uruchomienie gry.
        if (backgroundAt != 0L && SystemClock.elapsedRealtime() - backgroundAt >= NEW_SESSION_MS) onLaunch()
        backgroundAt = 0L
        syncClock()
        checkLaunch()
        if (store.loggedIn) scope.launch { sync(checkRemote = true) }
        scope.launch {
            community.flush()
            community.resumeClaim()
        }
    }

    /** Nowy przejazd poziomu gracza w kolejce – wysyłamy od razu (bez internetu poczeka). */
    fun flushPlays() {
        scope.launch { community.flush() }
    }

    /** Aplikacja schodzi z ekranu – wysyłamy wszystko od razu. */
    fun onBackground() {
        backgroundAt = SystemClock.elapsedRealtime()
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
                // W trakcie gry zapisu nie podmieniamy – połączymy go po powrocie do menu.
                var pushOwn = snapshot
                if (checkRemote) {
                    val state = call("GET", "/sync/state")
                    val remoteRevision = state.optInt("revision")
                    val remoteDevice = state.optString("deviceId")
                    if (state.optBoolean("exists") && remoteRevision != store.knownRevision && remoteDevice != store.deviceId) {
                        when {
                            !canAutoPull() -> pushOwn = false
                            store.dirty -> mergeRemote()
                            else -> pullSnapshot()
                        }
                        if (conflict) {
                            status = SyncStatus.IDLE
                            return
                        }
                    }
                }
                pushRuns()
                pushChallenges()
                if (pushOwn) {
                    try {
                        pushSnapshot(force = false)
                    } catch (e: ApiException) {
                        // Drugi telefon zdążył zapisać swój postęp – łączymy (albo czekamy z tym do końca gry).
                        if (e.code != "snapshot_conflict") throw e
                        if (canAutoPull()) mergeRemote()
                    }
                    if (conflict) {
                        status = SyncStatus.IDLE
                        return
                    }
                }
                status = SyncStatus.OK
                lastSyncAt = System.currentTimeMillis()
            } catch (e: ApiException) {
                when (e.code) {
                    // Drugi telefon zapisał coś w trakcie łączenia – następna synchronizacja połączy jeszcze raz.
                    "snapshot_conflict" -> Unit
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
        pushChallenges()
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
            // Kampania zawsze w całości, poziomy Bezkresnego Morza do ostatniego z wynikiem.
            var count = LevelConstants.LEVEL_COUNT
            for (i in LevelConstants.LEVEL_COUNT until repo.save.levelSlots) if (repo.save.best[i] > 0) count = i + 1
            for (i in 0 until count) bests.put(repo.save.best[i])
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

    /**
     * Zaliczone poziomy dnia/tygodnia (po kolei). Serwer przyjmuje jeden wynik na okres; odrzucony
     * (np. wysłany po zamknięciu okresu) nie wraca do kolejki. Serwer bez tej funkcji albo brak
     * sieci – zostają w kolejce na następną synchronizację, reszta synchronizacji idzie dalej.
     */
    private suspend fun pushChallenges() {
        val pending = repo.save.challengeResults.filter { it.at > store.lastSentChallengeAt }.sortedBy { it.at }
        for (r in pending) {
            val body = JSONObject()
                .put("kind", r.kind.id)
                .put("period", r.period)
                .put(
                    "run",
                    JSONObject()
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
                .put("integrity", integrity())
            try {
                val res = call("POST", "/challenges/run", body)
                applyProfile(res.getJSONObject("account"))
            } catch (e: OfflineException) {
                return
            } catch (e: ApiException) {
                when {
                    e.status == 401 || e.status == 403 || e.status == 426 -> throw e
                    e.status == 404 || e.status == 429 || e.status >= 500 -> return
                    // Nieprawidłowe dane – ponowna wysyłka nic nie zmieni.
                    else -> Log.w("FishingParty", "Poziom ${r.kind.id} #${r.period} odrzucony: ${e.code}")
                }
            }
            store.lastSentChallengeAt = r.at
            store.save()
        }
    }

    private suspend fun pushSnapshot(force: Boolean) {
        if (!store.dirty && !force) return
        val bytes = repo.exportSnapshot()
        val data = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val body = JSONObject().put("baseRevision", store.knownRevision).put("deviceId", store.deviceId).put("data", data)
        val res = call("PUT", if (force) "/sync/snapshot?force=1" else "/sync/snapshot", body)
        repo.rememberSynced(bytes)
        store.knownRevision = res.getInt("revision")
        // Zmiana w trakcie wysyłania (np. koniec poziomu) poleci następnym razem.
        store.dirty = !repo.exportSnapshot().contentEquals(bytes)
        store.save()
    }

    // ------------------------------------------------------------------ poziom dnia i tygodnia

    private val challengeLock = Mutex()
    private var challengesFetchedAt = 0L

    /**
     * Pobiera seedy poziomu dnia i tygodnia (zapamiętywane w zapisie – działają potem bez internetu
     * do końca okresu). Równoczesne wywołania czekają na jedno pobranie.
     */
    suspend fun refreshChallenges(): OnlineResult = challengeLock.withLock {
        val fresh = SystemClock.elapsedRealtime() - challengesFetchedAt < CHALLENGE_REFRESH_MS
        if (fresh && ChallengeKind.entries.all { repo.progress.currentChallenge(it) != null }) return@withLock OnlineResult.Ok
        guarded {
            val json = withContext(Dispatchers.IO) { api.challenges() } ?: return@guarded OnlineResult.Failed("server_unreachable")
            val list = ChallengeKind.entries.mapNotNull { kind -> json.optJSONObject(kind.id)?.let { parseChallenge(kind, it) } }
            if (list.isEmpty()) return@guarded OnlineResult.Failed("bad_response")
            challengesFetchedAt = SystemClock.elapsedRealtime()
            if (repo.progress.cacheChallenges(list)) repo.changed()
            OnlineResult.Ok
        }
    }

    private fun parseChallenge(kind: ChallengeKind, o: JSONObject): Challenge? {
        val seed = o.optString("seed").toLongOrNull() ?: return null
        val startsAt = o.optLong("startsAt")
        val endsAt = o.optLong("endsAt")
        if (!o.has("period") || endsAt <= startsAt) return null
        return Challenge(kind, o.getInt("period"), LevelSeed(seed), startsAt, endsAt)
    }

    // ------------------------------------------------------------------ start gry: blokada wersji i ogłoszenia

    /**
     * Przy wejściu do gry (także bez konta) pyta serwer, czy ta wersja nie jest zablokowana, i pobiera ogłoszenia
     * dla gościa albo zalogowanego. Najwyżej raz na minutę (start aplikacji to też powrót na ekran).
     */
    fun checkLaunch() {
        if (!isOnline()) {
            // Bez internetu wersji nie da się sprawdzić – gra działa normalnie, także po wcześniejszej blokadzie.
            // Po powrocie sieci sprawdzamy od razu. Z ogłoszeń zostają tylko zapisane „przy każdym uruchomieniu”.
            versionBlock = null
            launchCheckedAt = 0L
            knownAnnouncements = loadSticky()
            rebuildAnnouncements()
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (launchJob?.isActive == true || (launchCheckedAt != 0L && now - launchCheckedAt < LAUNCH_RECHECK_MS)) return
        val start = newSession
        launchJob = scope.launch {
            val json = try {
                withContext(Dispatchers.IO) { api.launch(store.loggedIn, store.deviceId, start) }
            } catch (e: Exception) {
                Log.w("FishingParty", "Start gry: serwer nie odpowiada (${e.message})")
                null
            }
            if (json == null) return@launch
            launchCheckedAt = SystemClock.elapsedRealtime()
            if (start) newSession = false
            val info = LaunchInfo.parse(json)
            versionBlock = info.block
            // Lista z serwera zastępuje kolejkę i zapisane ogłoszenia – nowa data albo treść obowiązuje od razu,
            // a ogłoszenie wyłączone albo usunięte w panelu znika też z telefonu.
            knownAnnouncements = info.announcements
            saveSticky(StickyAnnouncements.keep(info.announcements))
            rebuildAnnouncements()
        }
    }

    /**
     * Nowe uruchomienie gry (MainActivity albo powrót po dłuższej przerwie): ogłoszenia „przy każdym uruchomieniu”
     * pokazują się znowu, a najbliższe sprawdzenie na serwerze liczy się jako uruchomienie w statystykach.
     */
    fun onLaunch() {
        newSession = true
        shownThisLaunch.clear()
        rebuildAnnouncements()
    }

    /**
     * Gracz zamknął ogłoszenie – zwykłe nie pokaże się ponownie (chyba że admin każe pokazać je jeszcze raz),
     * a „przy każdym uruchomieniu” wróci po kolejnym starcie gry.
     */
    fun dismissAnnouncement(announcement: Announcement) {
        if (announcement.persistent) shownThisLaunch += announcement.key
        // Także „przy każdym uruchomieniu” – gdy admin zmieni je na zwykłe, gracz nie zobaczy go jeszcze raz.
        val seen = SeenAnnouncements.remember(seenAnnouncements(), announcement.key)
        launchPrefs.edit { putString(KEY_SEEN, SeenAnnouncements.encode(seen)) }
        announcements.removeAll { it.key == announcement.key }
    }

    private fun rebuildAnnouncements() {
        val fresh = SeenAnnouncements.queue(knownAnnouncements, seenAnnouncements(), shownThisLaunch, repo.clock.now())
        if (fresh != announcements.toList()) {
            announcements.clear()
            announcements.addAll(fresh)
        }
    }

    private fun seenAnnouncements(): List<String> = SeenAnnouncements.decode(launchPrefs.getString(KEY_SEEN, null))

    /** Zapisane ogłoszenia „przy każdym uruchomieniu” – te po dacie końca są od razu usuwane z telefonu. */
    private fun loadSticky(): List<Announcement> {
        val cached = StickyAnnouncements.decode(launchPrefs.getString(KEY_STICKY, null))
        val valid = StickyAnnouncements.valid(cached, repo.clock.now())
        if (valid.size != cached.size) saveSticky(valid)
        return valid
    }

    private fun saveSticky(list: List<Announcement>) {
        launchPrefs.edit {
            if (list.isEmpty()) remove(KEY_STICKY) else putString(KEY_STICKY, StickyAnnouncements.encode(list))
        }
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
            val size = res.optInt("size", 100)
            if (me == null) {
                Result.success(RankingData(entries, null, null, 0, false, size))
            } else {
                val rank = if (me.isNull("rank")) null else me.optInt("rank")
                account?.let {
                    account = AccountInfo(it.nick, it.email, me.optLong("points"), rank, me.optBoolean("hidden"), it.maxLevel, it.hasPassword, it.userLevelPoints, it.marketPending)
                }
                Result.success(RankingData(entries, me.optLong("id"), rank, me.optLong("points"), me.optBoolean("hidden"), size))
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
        private const val CHALLENGE_REFRESH_MS = 5_000L
        private const val LAUNCH_RECHECK_MS = 60_000L
        private const val LAUNCH_PREFS = "launch"
        private const val KEY_SEEN = "seen_announcements"
        private const val KEY_STICKY = "sticky_announcements"

        /** Po takiej przerwie w tle powrót do gry liczy się jak nowe uruchomienie. */
        private const val NEW_SESSION_MS = 30 * 60_000L
    }
}
