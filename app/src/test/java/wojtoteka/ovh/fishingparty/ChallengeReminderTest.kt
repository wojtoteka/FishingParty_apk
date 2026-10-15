package wojtoteka.ovh.fishingparty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import wojtoteka.ovh.fishingparty.level.ChallengeKind
import wojtoteka.ovh.fishingparty.notify.ChallengeReminderPlan
import wojtoteka.ovh.fishingparty.notify.StreakReminderSlots
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ChallengeReminderTest {
    private val zone = ZoneId.of("Europe/Warsaw")

    // Czwartek 15.10.2026, poniedziałek 19.10.2026.
    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone)

    @Test
    fun dailyReminderComesOncePerDayAtChosenHour() {
        val (time, kind) = ChallengeReminderPlan.next(at(15, 8), 11, daily = true, weekly = true, lastOpened = LocalDate.of(2026, 10, 15))!!
        assertEquals(at(15, 11), time)
        assertEquals(ChallengeKind.DAILY, kind)
        // Po dzisiejszym przypomnieniu następne dopiero jutro.
        assertEquals(at(16, 11), ChallengeReminderPlan.next(at(15, 11), 11, true, true, LocalDate.of(2026, 10, 15))!!.first)
    }

    @Test
    fun mondayBringsOneWeeklyReminderInsteadOfTwo() {
        val (time, kind) = ChallengeReminderPlan.next(at(18, 20), 10, daily = true, weekly = true, lastOpened = LocalDate.of(2026, 10, 18))!!
        assertEquals(DayOfWeek.MONDAY, time.dayOfWeek)
        assertEquals(at(19, 10), time)
        assertEquals(ChallengeKind.WEEKLY, kind)
        // Bez przypomnień tygodnia poniedziałek jest zwykłym dniem.
        assertEquals(ChallengeKind.DAILY, ChallengeReminderPlan.next(at(18, 20), 10, daily = true, weekly = false, lastOpened = null)!!.second)
        // Tylko tydzień: najbliższy poniedziałek, a nie jutro.
        assertEquals(at(19, 10), ChallengeReminderPlan.next(at(15, 12), 10, daily = false, weekly = true, lastOpened = null)!!.first)
        assertNull(ChallengeReminderPlan.next(at(15, 12), 10, daily = false, weekly = false, lastOpened = null))
    }

    @Test
    fun idlePlayersGetFewerReminders() {
        // Tydzień bez gry: zostaje tylko poniedziałek.
        val lastOpened = LocalDate.of(2026, 10, 8)
        val (time, kind) = ChallengeReminderPlan.next(at(15, 12), 11, daily = true, weekly = true, lastOpened = lastOpened)!!
        assertEquals(at(19, 11), time)
        assertEquals(ChallengeKind.WEEKLY, kind)
        // Cztery tygodnie bez gry – cisza.
        assertNull(ChallengeReminderPlan.next(at(15, 12), 11, daily = true, weekly = true, lastOpened = LocalDate.of(2026, 9, 10)))
    }

    @Test
    fun noReminderWhenGameOpenedTodayLevelDoneOrRightAfterAnother() {
        val now = at(15, 11).toInstant().toEpochMilli()
        val dayStart = at(15, 0).toInstant().toEpochMilli()
        val yesterday = dayStart - 3_600_000L
        assertTrue(ChallengeReminderPlan.shouldShow(now, dayStart, openedAt = yesterday, lastNotifiedAt = yesterday, completed = false))
        assertFalse(ChallengeReminderPlan.shouldShow(now, dayStart, openedAt = dayStart + 60_000, lastNotifiedAt = yesterday, completed = false))
        assertFalse(ChallengeReminderPlan.shouldShow(now, dayStart, openedAt = yesterday, lastNotifiedAt = yesterday, completed = true))
        assertFalse(ChallengeReminderPlan.shouldShow(now, dayStart, openedAt = yesterday, lastNotifiedAt = now - 3_600_000L, completed = false))
    }

    @Test
    fun levelRemindersStayWellBeforeStreakReminders() {
        val latestLevel = ChallengeReminderPlan.HOURS.max()
        val earliestStreak = StreakReminderSlots.HOURS.min()
        assertTrue(earliestStreak - latestLevel >= ChallengeReminderPlan.MIN_GAP_HOURS)
        assertTrue(ChallengeReminderPlan.DEFAULT_HOUR in ChallengeReminderPlan.HOURS)
    }
}
