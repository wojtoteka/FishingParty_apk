package wojtoteka.ovh.fishingparty.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Czas, według którego [Progress] liczy dni nagrody dziennej i passy. */
interface GameTime {
    /** Bieżąca chwila (ms) – znaczniki przejazdów. */
    fun now(): Long

    /** Dzień gry (epoch day) z czasu serwera albo null, gdy telefon jeszcze go nie zna. */
    fun today(): Long?

    /** Dzień według zegara telefonu – tylko do wyświetlania, gdy [today] jest nieznany. */
    fun localDay(): Long

    /** Czy jakieś ukończenie poziomu czeka na czas serwera (passa zaliczy się po połączeniu). */
    fun hasDeferredPlay(): Boolean = false

    /** Zapamiętuje ukończenie poziomu bez znanego czasu serwera. */
    fun deferPlay() {}
}

/** Zapamiętany stan [GameClock] – trzymany poza zapisem gry, bo dotyczy tylko tego uruchomienia telefonu. */
class ClockState(
    val boot: Long = -1,
    val serverMs: Long = 0,
    val elapsedMs: Long = 0,
    val zone: String = GameClock.DEFAULT_ZONE,
    val pending: List<Long> = emptyList(),
)

interface ClockStore {
    fun load(): ClockState?
    fun save(state: ClockState)
}

/**
 * Zegar gry: godzinę podaje serwer (`GET /time` albo nagłówek `X-FP-Server-Time`),
 * a między połączeniami odlicza ją zegar monotoniczny telefonu (czas od włączenia,
 * którego nie da się przestawić w ustawieniach). Przestawienie daty w telefonie nie
 * zmienia więc dnia gry. Dni liczy się w strefie podanej przez serwer (Europe/Warsaw).
 *
 * Po ponownym uruchomieniu telefonu zegar monotoniczny liczy od zera, więc czas serwera
 * trzeba pobrać na nowo – do tego czasu nagrody czekają, a ukończone poziomy są
 * zapamiętywane ([deferPlay]) i zaliczane do passy, gdy tylko serwer poda godzinę.
 *
 * @param elapsed czas od uruchomienia telefonu (SystemClock.elapsedRealtime)
 * @param wall zegar telefonu – tylko awaryjnie, do znaczników i wyświetlania
 * @param boot numer uruchomienia telefonu (Settings.Global.BOOT_COUNT), -1 = nieznany
 */
class GameClock(
    private val elapsed: () -> Long,
    private val wall: () -> Long,
    private val boot: Long,
    private val store: ClockStore,
) : GameTime {
    private var serverMs = 0L
    private var elapsedMs = 0L
    private var anchored = false
    private val pending = ArrayList<Long>()

    @Volatile
    var zone: ZoneId = ZoneId.of(DEFAULT_ZONE)
        private set

    /** Wywoływane, gdy zegar właśnie poznał czas serwera albo ma zaległe poziomy do zaliczenia. */
    var onSynced: (() -> Unit)? = null

    init {
        store.load()?.let { s ->
            zone = zoneOf(s.zone)
            // Ten sam rozruch telefonu: zapamiętany czas serwera nadal jest ważny, także bez internetu.
            if (boot >= 0 && s.boot == boot && elapsed() >= s.elapsedMs) {
                serverMs = s.serverMs
                elapsedMs = s.elapsedMs
                anchored = s.serverMs > 0
                pending += s.pending
            }
        }
    }

    val synced: Boolean @Synchronized get() = anchored

    /** Czas od uruchomienia telefonu – do mierzenia czasu odpowiedzi serwera. */
    fun elapsedNow(): Long = elapsed()

    /** Czas serwera albo null, gdy nie był jeszcze pobrany od uruchomienia telefonu. */
    @Synchronized
    fun serverNow(): Long? = if (anchored) serverMs + (elapsed() - elapsedMs) else null

    override fun now(): Long = serverNow() ?: wall()

    override fun today(): Long? = serverNow()?.let(::dayOf)

    override fun localDay(): Long = LocalDate.now(zone).toEpochDay()

    fun dayOf(ms: Long): Long = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toEpochDay()

    /**
     * Czas z serwera zmierzony między [sentAt] a [receivedAt] (czas od uruchomienia telefonu);
     * przyjmujemy, że serwer odpowiedział w połowie drogi.
     */
    fun onServerTime(server: Long, sentAt: Long, receivedAt: Long, zoneId: String? = null) {
        if (server <= 0 || receivedAt < sentAt || receivedAt - sentAt > MAX_ROUND_TRIP_MS) return
        val notify: Boolean
        synchronized(this) {
            notify = !anchored || pending.isNotEmpty()
            serverMs = server
            elapsedMs = sentAt + (receivedAt - sentAt) / 2
            anchored = true
            if (zoneId != null) zone = zoneOf(zoneId)
            persist()
        }
        if (notify) onSynced?.invoke()
    }

    @Synchronized
    override fun hasDeferredPlay(): Boolean = pending.isNotEmpty()

    @Synchronized
    override fun deferPlay() {
        if (pending.size >= MAX_PENDING) pending.removeAt(0)
        pending += elapsed()
        persist()
    }

    /** Dni (w kolejności), w których ukończono poziomy bez znanego czasu serwera; czyści listę. */
    @Synchronized
    fun takeDeferredDays(): List<Long> {
        if (!anchored || pending.isEmpty()) return emptyList()
        val days = pending.map { dayOf(serverMs + (it - elapsedMs)) }.distinct().sorted()
        pending.clear()
        persist()
        return days
    }

    private fun persist() {
        store.save(ClockState(boot, if (anchored) serverMs else 0, elapsedMs, zone.id, pending.toList()))
    }

    companion object {
        const val DEFAULT_ZONE = "Europe/Warsaw"
        private const val MAX_ROUND_TRIP_MS = 20_000L
        private const val MAX_PENDING = 16

        private fun zoneOf(id: String): ZoneId = try {
            ZoneId.of(id)
        } catch (_: Exception) {
            ZoneId.of(DEFAULT_ZONE)
        }
    }
}
