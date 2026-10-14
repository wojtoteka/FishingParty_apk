package wojtoteka.ovh.fishingparty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import wojtoteka.ovh.fishingparty.data.ClockState
import wojtoteka.ovh.fishingparty.data.ClockStore
import wojtoteka.ovh.fishingparty.data.GameClock
import wojtoteka.ovh.fishingparty.data.Progress
import wojtoteka.ovh.fishingparty.data.SaveData
import wojtoteka.ovh.fishingparty.level.LevelGenerator
import wojtoteka.ovh.fishingparty.level.RunResult
import java.time.Instant
import java.time.LocalDate

class GameClockTest {

    private class MemoryStore : ClockStore {
        var state: ClockState? = null
        override fun load(): ClockState? = state
        override fun save(state: ClockState) {
            this.state = state
        }
    }

    private var elapsed = 5_000_000L
    private var wall = Instant.parse("2026-10-14T10:00:00Z").toEpochMilli()
    private val store = MemoryStore()

    private fun clock(boot: Long = 7) = GameClock({ elapsed }, { wall }, boot, store)

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).toEpochDay()

    @Test
    fun dayComesFromServerNotFromPhoneClock() {
        val c = clock()
        assertNull("bez czasu serwera dzień jest nieznany", c.today())
        assertEquals(wall, c.now())
        c.onServerTime(ms("2026-10-14T21:59:00Z"), sentAt = elapsed - 200, receivedAt = elapsed)
        // 23:59 w Warszawie (czas letni) – jeszcze 14 października.
        assertEquals(day(2026, 10, 14), c.today())
        // Gracz przestawia datę w telefonie o tydzień – dzień gry się nie zmienia.
        wall += 7 * 86_400_000L
        assertEquals(day(2026, 10, 14), c.today())
        // Upływa prawdziwy czas (zegar monotoniczny) – po północy w Warszawie jest nowy dzień.
        elapsed += 2 * 60_000L
        assertEquals(day(2026, 10, 15), c.today())
    }

    @Test
    fun serverTimeSurvivesAppRestartButNotPhoneReboot() {
        clock().onServerTime(ms("2026-10-14T12:00:00Z"), elapsed, elapsed)
        elapsed += 3_600_000L
        val sameBoot = clock()
        assertTrue(sameBoot.synced)
        assertEquals(ms("2026-10-14T13:00:00Z"), sameBoot.serverNow())
        // Po ponownym uruchomieniu telefonu zegar monotoniczny liczy od zera – czas trzeba pobrać na nowo.
        elapsed = 10_000L
        assertFalse(clock(boot = 8).synced)
        assertFalse(clock(boot = -1).synced)
    }

    @Test
    fun slowResponsesAreIgnored() {
        val c = clock()
        c.onServerTime(ms("2026-10-14T12:00:00Z"), sentAt = elapsed - 60_000, receivedAt = elapsed)
        assertFalse(c.synced)
    }

    @Test
    fun levelsPlayedOfflineCountForStreakOnceServerTimeArrives() {
        val c = clock()
        var synced = 0
        c.onSynced = { synced++ }
        val save = SaveData()
        val p = Progress(save, c)
        // Poprzedni dzień passy: 13 października.
        save.streak = 4
        save.streakLastDay = day(2026, 10, 13)
        val plan = LevelGenerator.plan(1)
        val common = plan.stages.sumOf { it.common }
        val run = RunResult(1, true, 200, 1, BooleanArray(3) { it < 1 }, 60_000, common, 0, 0, 0, 1, plan.stages.size)
        val reward = p.applyRun(run, plan)
        assertTrue(reward.accepted)
        assertTrue(reward.streakDeferred)
        assertEquals(0, reward.streakDays)
        assertTrue(p.streakState().playedToday)
        assertFalse("drugi poziom nie powtarza komunikatu", p.applyRun(run, plan).streakDeferred)
        // Pół godziny później telefon łączy się z serwerem: poziomy były 14 października.
        elapsed += 30 * 60_000L
        c.onServerTime(ms("2026-10-14T18:30:00Z"), elapsed, elapsed)
        assertEquals(1, synced)
        assertEquals(listOf(day(2026, 10, 14)), c.takeDeferredDays())
        assertTrue(c.takeDeferredDays().isEmpty())
        assertTrue(p.creditStreakDay(day(2026, 10, 14)) > 0)
        assertEquals(5, save.streak)
        assertTrue(p.streakState().playedToday)
        // Kolejne odpowiedzi serwera nie wywołują już odświeżenia.
        c.onServerTime(ms("2026-10-14T18:31:00Z"), elapsed + 60_000, elapsed + 60_000)
        assertEquals(1, synced)
    }
}
