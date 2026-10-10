package wojtoteka.ovh.fishingparty

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import wojtoteka.ovh.fishingparty.audio.BeepSynth
import wojtoteka.ovh.fishingparty.audio.Beeper
import wojtoteka.ovh.fishingparty.audio.SoundScript
import wojtoteka.ovh.fishingparty.core.GuardedLong
import wojtoteka.ovh.fishingparty.data.Achievements
import wojtoteka.ovh.fishingparty.data.Catalog
import wojtoteka.ovh.fishingparty.data.PowerUp
import wojtoteka.ovh.fishingparty.data.Progress
import wojtoteka.ovh.fishingparty.data.SaveCodec
import wojtoteka.ovh.fishingparty.data.SaveCrypto
import wojtoteka.ovh.fishingparty.data.SaveData
import wojtoteka.ovh.fishingparty.level.LevelGenerator
import wojtoteka.ovh.fishingparty.level.RunResult
import wojtoteka.ovh.fishingparty.level.Tile
import wojtoteka.ovh.fishingparty.level.WorldMap
import java.security.GeneralSecurityException
import javax.crypto.KeyGenerator
import kotlin.math.abs

class SaveAndProgressTest {

    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val crypto = SaveCrypto { key }

    private fun sample(): SaveData = SaveData().apply {
        coins.set(1234)
        best[0] = 450
        best[41] = 9000
        stars[0] = 3
        stars[41] = 2
        unlocked.set(43)
        totalFish.set(777)
        owned += "boat:navy"
        boat = "navy"
        powerups[1] = 5
        claimed += "first_fish"
        dailyStreak = 4
        tutorialDone = true
        streakLastDay = 20_123
        streak = 9
        streakBest = 12
        trophies.set(321)
    }

    @Test
    fun codecRoundTrip() {
        val original = sample()
        val copy = SaveCodec.decode(SaveCodec.encode(original))
        assertEquals(1234L, copy.coins.get())
        assertEquals(450, copy.best[0])
        assertEquals(9000, copy.best[41])
        assertEquals(2, copy.stars[41].toInt())
        assertEquals(43L, copy.unlocked.get())
        assertEquals("navy", copy.boat)
        assertTrue("boat:navy" in copy.owned)
        assertEquals(5, copy.powerups[1])
        assertTrue(copy.tutorialDone)
        assertEquals(20_123L, copy.streakLastDay)
        assertEquals(9, copy.streak)
        assertEquals(12, copy.streakBest)
        assertEquals(321L, copy.trophies.get())
        assertArrayEquals(SaveCodec.encode(original), SaveCodec.encode(copy))
    }

    @Test
    fun encryptedSaveDetectsTampering() {
        val plain = SaveCodec.encode(sample())
        val sealed = crypto.seal(plain)
        assertArrayEquals(plain, crypto.open(sealed))
        // Zmiana dowolnego bajtu (np. próba podbicia monet) musi zostać wykryta.
        for (position in listOf(10, sealed.size / 2, sealed.size - 1)) {
            val forged = sealed.copyOf()
            forged[position] = (forged[position].toInt() xor 0x01).toByte()
            try {
                crypto.open(forged)
                fail("Zmodyfikowany zapis został zaakceptowany (bajt $position)")
            } catch (_: GeneralSecurityException) {
            }
        }
        // Zapis z innego urządzenia (inny klucz) też nie przejdzie.
        val otherKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        try {
            SaveCrypto { otherKey }.open(sealed)
            fail("Zapis odczytany obcym kluczem")
        } catch (_: GeneralSecurityException) {
        }
        // Plik nie zawiera jawnych liczb.
        assertFalse(String(sealed, Charsets.ISO_8859_1).contains("navy"))
    }

    @Test
    fun guardedValueSurvivesAndHidesValue() {
        val g = GuardedLong(500)
        assertEquals(500L, g.get())
        g.add(25)
        assertEquals(525L, g.get())
    }

    private fun progress(save: SaveData = SaveData(), now: Long = 1_000_000_000L, day: Long = 20_000L) =
        Progress(save, { now }, { day })

    private fun win(level: Int, score: Int, stars: Int, time: Long = 60_000): RunResult {
        val plan = LevelGenerator.plan(level)
        val common = plan.stages.sumOf { it.common }
        return RunResult(
            level, true, score, stars, BooleanArray(3) { it < stars }, time,
            common, 0, 0, 0, 1, plan.stages.size,
        )
    }

    @Test
    fun runsUnlockLevelsAndRewardCoins() {
        val p = progress()
        assertEquals(1, p.highestUnlocked())
        val reward = p.applyRun(win(1, 200, 2), LevelGenerator.plan(1))
        assertTrue(reward.accepted)
        assertEquals(2, p.highestUnlocked())
        assertEquals(2, p.stars(1))
        assertEquals(200, p.best(1))
        assertTrue(reward.coins > 0)
        assertEquals(reward.coins.toLong(), p.coins)
        // Gorszy wynik nie nadpisuje rekordu, gwiazdki się nie cofają.
        val second = p.applyRun(win(1, 100, 1), LevelGenerator.plan(1))
        assertFalse(second.newBest)
        assertEquals(200, p.best(1))
        assertEquals(2, p.stars(1))
        // Pierwszy wygrany poziom dnia zaczyna passę: 1 trofeum doliczone do rankingu.
        assertEquals(1L, p.trophies)
        assertEquals(201L, p.rankingPoints())
    }

    @Test
    fun impossibleRunsAreRejected() {
        val p = progress()
        val cheat = win(1, 50_000_000, 3)
        val reward = p.applyRun(cheat, LevelGenerator.plan(1))
        assertFalse(reward.accepted)
        assertEquals(0, p.best(1))
        assertEquals(0L, p.coins)
        // Poziom jeszcze niedostępny też jest odrzucany.
        assertFalse(p.applyRun(win(30, 100, 1), LevelGenerator.plan(30)).accepted)
    }

    @Test
    fun shopBuysAndEquips() {
        val p = progress()
        p.addCoins(1000)
        val navy = Catalog.boats.first { it.id == "navy" }
        assertTrue(p.buy(navy))
        assertTrue(p.owns(navy))
        assertTrue(p.isEquipped(navy))
        assertFalse(p.buy(navy))
        assertEquals(600L, p.coins)
        val golden = Catalog.boats.first { it.id == "golden" }
        assertFalse(p.buy(golden))
        val loadout = p.consume(setOf(PowerUp.SHIELD, PowerUp.TURBO))
        assertTrue(loadout.shield && loadout.turbo && !loadout.magnet)
        assertEquals(1, p.powerups(PowerUp.SHIELD))
    }

    @Test
    fun dailyRewardStreakAndClockRollback() {
        val save = SaveData()
        var now = 1_000_000_000L
        var day = 20_000L
        val p = Progress(save, { now }, { day })
        p.touchClock()
        assertTrue(p.dailyState().available)
        assertEquals(50, p.claimDaily())
        assertFalse(p.dailyState().available)
        day++
        now += 86_400_000L
        assertEquals(2, p.dailyState().day)
        assertEquals(75, p.claimDaily())
        // Gracz cofa zegar o 3 dni – nagroda zablokowana.
        now -= 3 * 86_400_000L
        day -= 3
        val state = p.dailyState()
        assertFalse(state.available)
        assertTrue(state.clockTampered)
        // Przerwa w logowaniu resetuje serię.
        now += 10 * 86_400_000L
        day += 10
        assertEquals(1, p.dailyState().day)
    }

    @Test
    fun streakNeedsOneLevelEveryDay() {
        val save = SaveData()
        var now = 1_000_000_000L
        var day = 20_000L
        val p = Progress(save, { now }, { day })
        p.touchClock()
        assertEquals(0, p.streakState().days)
        // Porażka nie liczy się do passy.
        p.applyRun(RunResult(1, false, 0, 0, BooleanArray(3), 10_000, 0, 0, 0, 0, 0, 0), LevelGenerator.plan(1))
        assertEquals(0, p.streakState().days)
        val first = p.applyRun(win(1, 200, 1), LevelGenerator.plan(1))
        assertEquals(1, first.streakDays)
        assertEquals(1, first.trophies)
        assertTrue(p.streakState().playedToday)
        // Drugi poziom tego samego dnia nie daje kolejnych trofeów.
        assertEquals(0, p.applyRun(win(1, 200, 1), LevelGenerator.plan(1)).streakDays)
        fun nextDay(n: Int = 1) {
            day += n
            now += n * 86_400_000L
        }
        nextDay()
        assertEquals(1, p.streakState().days)
        assertFalse(p.streakState().playedToday)
        val second = p.applyRun(win(1, 200, 1), LevelGenerator.plan(1))
        assertEquals(2, second.streakDays)
        nextDay()
        // Trzeci dzień: 3 trofea + premia za okrągłą passę.
        assertEquals(3 + 5, p.applyRun(win(1, 200, 1), LevelGenerator.plan(1)).trophies)
        assertEquals(1L + 2 + 8, p.trophies)
        assertEquals(3, save.streakBest)
        // Dzień przerwy przerywa passę, trofea zostają.
        nextDay(2)
        assertEquals(0, p.streakState().days)
        assertEquals(1, p.applyRun(win(1, 200, 1), LevelGenerator.plan(1)).streakDays)
        assertEquals(3, save.streakBest)
        // Cofnięty zegar nie przedłuża passy.
        now -= 5 * 86_400_000L
        day += 1
        assertEquals(0, p.applyRun(win(1, 200, 1), LevelGenerator.plan(1)).streakDays)
    }

    @Test
    fun achievementsUnlockAndPayOnce() {
        val p = progress()
        p.applyRun(win(1, 200, 3), LevelGenerator.plan(1))
        val first = Achievements.byId("first_fish")!!
        assertTrue(p.isUnlocked(first))
        val fresh = p.takeNewAchievements()
        assertTrue(fresh.any { it.id == "first_fish" })
        assertTrue(p.takeNewAchievements().isEmpty())
        val before = p.coins
        assertEquals(first.reward, p.claim(first))
        assertEquals(0, p.claim(first))
        assertEquals(before + first.reward, p.coins)
    }

    @Test
    fun musicLoopsAreTwelveSeconds() {
        val beeper = Beeper()
        for (voice in SoundScript.MUSIC) {
            // Każdy głos musi mieścić się w 12-sekundowej pętli całkowitą liczbę razy.
            val length = BeepSynth.loopLength(beeper.parse(voice))
            val repeats = SoundScript.MUSIC_SECONDS / length
            assertTrue("głos trwa $length s", abs(repeats - Math.round(repeats)) < 0.001)
        }
        for ((name, lines) in SoundScript.SFX) {
            val samples = SoundScript.render(lines, 22050)
            assertTrue("efekt $name jest pusty", samples.isNotEmpty())
            assertTrue("efekt $name za długi", samples.size < 22050 * 4)
        }
    }

    @Test
    fun worldMapIslandsAreValid() {
        val g = WorldMap.grid
        for (n in 1..500) {
            val c = WorldMap.islandCol[n - 1]
            val r = WorldMap.islandRow[n - 1]
            for (dr in 0..1) for (dc in 0..1) assertEquals(Tile.SAND, g[c + dc, r + dr])
            assertEquals(n, WorldMap.levelAt(WorldMap.nodeX(n), WorldMap.nodeY(n)))
        }
        for (r in 1 until g.height - 1) for (c in 0 until g.width) {
            if (g[c, r] != Tile.SAND) continue
            val w = { t: Tile -> t == Tile.SEA || t == Tile.ROCK }
            assertFalse("pole $c,$r", (w(g[c, r - 1]) && w(g[c, r + 1])) || (w(g[c - 1, r]) && w(g[c + 1, r])))
        }
    }
}
