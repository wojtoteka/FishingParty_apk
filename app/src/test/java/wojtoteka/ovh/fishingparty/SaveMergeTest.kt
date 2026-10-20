package wojtoteka.ovh.fishingparty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import wojtoteka.ovh.fishingparty.data.Achievements
import wojtoteka.ovh.fishingparty.data.ChallengeResult
import wojtoteka.ovh.fishingparty.data.ControlMode
import wojtoteka.ovh.fishingparty.data.DailyRewards
import wojtoteka.ovh.fishingparty.data.SaveCodec
import wojtoteka.ovh.fishingparty.data.SaveData
import wojtoteka.ovh.fishingparty.data.SaveMerge
import wojtoteka.ovh.fishingparty.data.ScoreRecord
import wojtoteka.ovh.fishingparty.level.ChallengeKind

/** Ten sam konto na dwóch telefonach: postęp z obu łączy się bez strat i bez podwójnych nagród. */
class SaveMergeTest {

    private fun copy(s: SaveData) = SaveCodec.decode(SaveCodec.encode(s))

    /** Wspólny punkt wyjścia: konto z kilkoma poziomami, monetami i łodzią. */
    private fun base() = SaveData().apply {
        coins.set(1000)
        for (i in 0 until 5) {
            best[i] = 500
            stars[i] = 2
        }
        unlocked.set(6)
        totalFish.set(300)
        levelWins = 5
        owned += "boat:crimson"
        boat = "crimson"
    }

    private fun record(level: Int, at: Long) = ScoreRecord(level, 600, 40_000, 10, 2, 1, 0, 4, 3, at)

    private fun challenge(kind: ChallengeKind, period: Int, points: Int, at: Long) =
        ChallengeResult(kind, period, points / kind.pointsMultiplier, points, 50_000, 10, 2, 1, 0, 4, 3, at)

    @Test
    fun bothPhonesKeepWhatTheyEarned() {
        val base = base()
        // Telefon A (ten): gra poziom 6, kupuje żagiel, zarabia i wydaje monety.
        val local = copy(base).apply {
            coins.set(1000 + 300 - 200)
            best[5] = 700
            stars[5] = 3
            unlocked.set(7)
            totalFish.set(340)
            levelWins = 6
            owned += "sail:poland"
            sail = "poland"
            records += record(6, 2_000)
        }
        // Telefon B (zapisał na koncie wcześniej): poprawia poziom 3, kupuje łódź, zarabia monety.
        val remote = copy(base).apply {
            coins.set(1000 + 150 - 500)
            best[2] = 900
            stars[2] = 3
            totalFish.set(320)
            levelWins = 6
            owned += "boat:navy"
            records += record(3, 1_000)
        }
        val merged = SaveMerge.merge(base, local, remote)
        // Monety: 1000 + (A: +100) + (B: -350).
        assertEquals(750L, merged.coins.get())
        assertEquals(360L, merged.totalFish.get())
        assertEquals(7, merged.levelWins)
        assertEquals(900, merged.best[2])
        assertEquals(700, merged.best[5])
        assertEquals(3, merged.stars[2].toInt())
        assertEquals(3, merged.stars[5].toInt())
        assertEquals(7L, merged.unlocked.get())
        assertTrue("sail:poland" in merged.owned && "boat:navy" in merged.owned && "boat:crimson" in merged.owned)
        // Wygląd zmieniony na tym telefonie zostaje, niezmieniony – z konta.
        assertEquals("poland", merged.sail)
        assertEquals("crimson", merged.boat)
        assertEquals(listOf(1_000L, 2_000L), merged.records.map { it.at })
        // Wejściowe zapisy się nie zmieniają.
        assertEquals(1100L, local.coins.get())
        assertEquals(650L, remote.coins.get())
    }

    @Test
    fun sameLevelOnBothPhonesKeepsTheBetterResult() {
        val base = base()
        val local = copy(base).apply { best[0] = 800; records += record(1, 5_000) }
        val remote = copy(base).apply { best[0] = 650; records += record(1, 5_000); records += record(1, 4_000) }
        val merged = SaveMerge.merge(base, local, remote)
        assertEquals(800, merged.best[0])
        // Ten sam przejazd (ta sama chwila i poziom) nie dubluje się.
        assertEquals(listOf(4_000L, 5_000L), merged.records.map { it.at })
    }

    @Test
    fun rewardsClaimedOnBothPhonesCountOnce() {
        val base = base().apply { dailyLastDay = 99; dailyStreak = 6 }
        val achievement = Achievements.all.first()
        val daily = DailyRewards.coins[DailyRewards.BONUS_DAY - 1]
        val kind = ChallengeKind.entries.first()
        fun phone() = copy(base).apply {
            // Ta sama nagroda dzienna (dzień 7 z wzmocnieniami), to samo osiągnięcie, ten sam poziom dnia.
            dailyLastDay = 100
            dailyStreak = DailyRewards.BONUS_DAY
            coins.add(daily.toLong() + achievement.reward + kind.coinBonus)
            for (i in powerups.indices) powerups[i]++
            claimed += achievement.id
            challengePoints.add(400)
            challengeResults += challenge(kind, 7, 400, 3_000)
        }
        val local = phone()
        val remote = phone().apply { challengeResults.clear(); challengeResults += challenge(kind, 7, 400, 2_000) }
        val merged = SaveMerge.merge(base, local, remote)
        assertEquals(base.coins.get() + daily + achievement.reward + kind.coinBonus, merged.coins.get())
        assertEquals(base.powerups.map { it + 1 }, merged.powerups.toList())
        assertEquals(400L, merged.challengePoints.get())
        assertEquals(1, merged.challengeResults.size)
        assertEquals(100L, merged.dailyLastDay)
    }

    @Test
    fun differentChallengePeriodsAddUp() {
        val base = base()
        val kind = ChallengeKind.entries.first()
        val local = copy(base).apply { challengePoints.add(300); challengeResults += challenge(kind, 8, 300, 9_000) }
        val remote = copy(base).apply { challengePoints.add(200); challengeResults += challenge(kind, 7, 200, 1_000) }
        val merged = SaveMerge.merge(base, local, remote)
        assertEquals(500L, merged.challengePoints.get())
        assertEquals(listOf(7, 8), merged.challengeResults.map { it.period })
    }

    @Test
    fun streakTakesTheFurthestDay() {
        val base = base().apply { streakLastDay = 10; streak = 3; trophies.set(30) }
        // Oba telefony zaliczyły dzień 11 (ta sama passa) – trofea liczą się raz.
        val a = copy(base).apply { streakLastDay = 11; streak = 4; trophies.set(40) }
        val b = copy(base).apply { streakLastDay = 11; streak = 4; trophies.set(40) }
        val same = SaveMerge.merge(base, a, b)
        assertEquals(40L, same.trophies.get())
        assertEquals(4, same.streak)
        // Passę przedłużył tylko ten telefon – zostaje jego dzień i trofea.
        val c = copy(base).apply { streakLastDay = 12; streak = 5; trophies.set(55) }
        val merged = SaveMerge.merge(base, c, copy(base))
        assertEquals(55L, merged.trophies.get())
        assertEquals(12L, merged.streakLastDay)
        assertEquals(5, merged.streak)
    }

    @Test
    fun settingsChangedHereWinAndControlsStayOnThePhone() {
        val base = base().apply { music = true; vibration = true }
        val local = copy(base).apply { music = false; controls = ControlMode.JOYSTICK; controlsChosen = true }
        val remote = copy(base).apply { vibration = false; controls = ControlMode.SWIPE }
        val merged = SaveMerge.merge(base, local, remote)
        assertFalse(merged.music)
        assertFalse(merged.vibration)
        assertEquals(ControlMode.JOYSTICK, merged.controls)
    }

    @Test
    fun endlessSeaLevelsFromEitherPhoneSurvive() {
        val base = base()
        val local = copy(base).apply { ensureLevels(1500); best[1499] = 1234; stars[1499] = 1; unlocked.set(1501) }
        val merged = SaveMerge.merge(base, local, copy(base))
        assertEquals(1234, merged.best[1499])
        assertEquals(1501L, merged.unlocked.get())
        val back = SaveMerge.merge(base, copy(base), local)
        assertEquals(1234, back.best[1499])
    }
}
