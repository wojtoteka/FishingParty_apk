package wojtoteka.ovh.fishingparty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import wojtoteka.ovh.fishingparty.core.Rng
import wojtoteka.ovh.fishingparty.data.Progress
import wojtoteka.ovh.fishingparty.data.SaveCodec
import wojtoteka.ovh.fishingparty.data.SaveData
import wojtoteka.ovh.fishingparty.level.Challenge
import wojtoteka.ovh.fishingparty.level.ChallengeKind
import wojtoteka.ovh.fishingparty.level.Challenges
import wojtoteka.ovh.fishingparty.level.LevelGenerator
import wojtoteka.ovh.fishingparty.level.LevelSeed
import wojtoteka.ovh.fishingparty.level.RunResult
import wojtoteka.ovh.fishingparty.level.ScoreValidator

class ChallengesTest {

    private fun seed(i: Int) = LevelSeed(Rng.mix(0x7E57L, i.toLong()))

    @Test
    fun sameSeedGivesSameLevelForEveryone() {
        for (kind in ChallengeKind.entries) {
            val a = Challenges.plan(kind, seed(1))
            val b = Challenges.plan(kind, seed(1))
            assertEquals(a.spec.type, b.spec.type)
            assertEquals(a.stages.map { it.rows to it.quota }, b.stages.map { it.rows to it.quota })
            assertEquals(LevelGenerator.maxScore(a), LevelGenerator.maxScore(b))
            assertEquals(Challenges.LEVEL_NUMBER, a.spec.number)
            // Różne seedy – różne plansze.
            val different = (2..20).count { LevelGenerator.maxScore(Challenges.plan(kind, seed(it))) != LevelGenerator.maxScore(a) }
            assertTrue(different > 15)
        }
    }

    @Test
    fun weeklyIsHarderThanDaily() {
        val daily = (0 until 200).map { Challenges.plan(ChallengeKind.DAILY, seed(it)) }
        val weekly = (0 until 200).map { Challenges.plan(ChallengeKind.WEEKLY, seed(it)) }
        assertTrue(weekly.sumOf { it.enemyCount } > daily.sumOf { it.enemyCount } * 1.3)
        assertTrue(weekly.sumOf { it.stages.size } > daily.sumOf { it.stages.size })
        assertTrue(weekly.sumOf { it.spec.enemySpeed.toDouble() } > daily.sumOf { it.spec.enemySpeed.toDouble() })
        assertTrue(ChallengeKind.WEEKLY.pointsMultiplier > ChallengeKind.DAILY.pointsMultiplier)
        assertTrue(ChallengeKind.WEEKLY.coinBonus > ChallengeKind.DAILY.coinBonus)
        for (p in daily + weekly) LevelGenerator.generate(p)
    }

    private fun win(plan: wojtoteka.ovh.fishingparty.level.LevelPlan, score: Int) = RunResult(
        level = Challenges.LEVEL_NUMBER,
        completed = true,
        score = score,
        stars = 1,
        starFlags = booleanArrayOf(true, false, false),
        timeMillis = (ScoreValidator.minimumSeconds(plan) * 4000).toLong(),
        commonCaught = plan.stages.sumOf { it.common },
        blueCaught = 0,
        goldenCaught = 0,
        hits = 1,
        maxCombo = 2,
        stagesCleared = plan.stages.size,
    )

    @Test
    fun completingGivesRankingPointsOncePerPeriod() {
        val save = SaveData()
        var now = 1_800_000_000_000L
        val progress = Progress(save, { now }, { 20_000L })
        val daily = Challenge(ChallengeKind.DAILY, 20_000, seed(5), now - 1000, now + 3_600_000)
        val weekly = Challenge(ChallengeKind.WEEKLY, 2_857, seed(6), now - 1000, now + 86_400_000)
        assertTrue(progress.cacheChallenges(listOf(daily, weekly)))
        assertFalse(progress.cacheChallenges(listOf(daily, weekly)))
        assertEquals(daily, progress.currentChallenge(ChallengeKind.DAILY))
        assertNull(progress.challengeResult(ChallengeKind.DAILY, 20_000))

        val plan = daily.plan()
        val reward = progress.applyChallenge(win(plan, 500), plan, daily)
        assertTrue(reward.accepted)
        assertEquals(500, reward.challengePoints)
        assertTrue(reward.coins >= ChallengeKind.DAILY.coinBonus)
        assertEquals(1, reward.streakDays)
        assertNotNull(progress.challengeResult(ChallengeKind.DAILY, 20_000))
        assertEquals(500L + save.trophies.get(), progress.rankingPoints())

        // Drugie ukończenie tego samego dnia – bez punktów i premii.
        val again = progress.applyChallenge(win(plan, 900), plan, daily)
        assertEquals(0, again.challengePoints)
        assertEquals(500L, save.challengePoints.get())

        // Tydzień liczy się podwójnie.
        val wplan = weekly.plan()
        assertEquals(800, progress.applyChallenge(win(wplan, 400), wplan, weekly).challengePoints)
        assertEquals(1300L, save.challengePoints.get())

        // Wynik ponad limit planszy jest odrzucany.
        val forged = progress.applyChallenge(win(plan, 10_000_000), plan, daily)
        assertFalse(forged.accepted)

        // Po końcu okresu poziom trzeba pobrać na nowo.
        now += 3_600_000
        assertNull(progress.currentChallenge(ChallengeKind.DAILY))
        assertNotNull(progress.currentChallenge(ChallengeKind.WEEKLY))
    }

    @Test
    fun challengeDataSurvivesSaveAndOlderSavesLoad() {
        val save = SaveData()
        val now = 1_800_000_000_000L
        val progress = Progress(save, { now }, { 20_000L })
        val daily = Challenge(ChallengeKind.DAILY, 20_000, LevelSeed(-42L), now - 1000, now + 60_000)
        progress.cacheChallenges(listOf(daily))
        val plan = daily.plan()
        progress.applyChallenge(win(plan, 321), plan, daily)

        val copy = SaveCodec.decode(SaveCodec.encode(save))
        assertEquals(321L, copy.challengePoints.get())
        assertEquals(1, copy.challengeResults.size)
        assertEquals(ChallengeKind.DAILY, copy.challengeResults[0].kind)
        assertEquals(321, copy.challengeResults[0].points)
        assertEquals(-42L, copy.challengeCache.single().seed.value)
        assertEquals(daily.endsAt, copy.challengeCache.single().endsAt)

        // Zapis v4 (sprzed poziomów dnia/tygodnia): bez części v5, wersja 4 w nagłówku.
        val empty = SaveData()
        val v5 = SaveCodec.encode(empty)
        val v4 = v5.copyOf(v5.size - (8 + 2 + 2))
        v4[5] = 4
        val old = SaveCodec.decode(v4)
        assertEquals(0L, old.challengePoints.get())
        assertTrue(old.challengeResults.isEmpty())
    }
}
