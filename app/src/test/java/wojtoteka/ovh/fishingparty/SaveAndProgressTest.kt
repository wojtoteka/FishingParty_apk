package wojtoteka.ovh.fishingparty

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import wojtoteka.ovh.fishingparty.audio.BeepSynth
import wojtoteka.ovh.fishingparty.audio.Beeper
import wojtoteka.ovh.fishingparty.audio.SoundScript
import wojtoteka.ovh.fishingparty.core.GuardedLong
import wojtoteka.ovh.fishingparty.data.Achievements
import wojtoteka.ovh.fishingparty.data.Catalog
import wojtoteka.ovh.fishingparty.data.ControlMode
import wojtoteka.ovh.fishingparty.data.PowerUp
import wojtoteka.ovh.fishingparty.data.Progress
import wojtoteka.ovh.fishingparty.data.SaveCodec
import wojtoteka.ovh.fishingparty.data.SaveCrypto
import wojtoteka.ovh.fishingparty.data.SaveData
import wojtoteka.ovh.fishingparty.data.StreakRepairs
import wojtoteka.ovh.fishingparty.data.StreakRewards
import wojtoteka.ovh.fishingparty.level.LevelConstants
import wojtoteka.ovh.fishingparty.level.LevelConstants.TILE
import wojtoteka.ovh.fishingparty.notify.StreakReminderSlots
import wojtoteka.ovh.fishingparty.ui.Backdrop
import wojtoteka.ovh.fishingparty.level.LevelGenerator
import wojtoteka.ovh.fishingparty.level.RunResult
import wojtoteka.ovh.fishingparty.level.Tile
import wojtoteka.ovh.fishingparty.level.WorldMap
import wojtoteka.ovh.fishingparty.level.Worlds
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.GeneralSecurityException
import java.time.LocalDate
import java.time.LocalTime
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
        streakStartDay = 20_115
        repairPeriod = 1
        repairsUsed = 3
        repairsTotal = 7
        streakReminders = false
        reminderHour = 20
        keepScreenOn = false
        controls = ControlMode.JOYSTICK
        controlsChosen = true
        joystickLeft = false
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
        assertEquals(20_115L, copy.streakStartDay)
        assertEquals(1, copy.repairPeriod)
        assertEquals(3, copy.repairsUsed)
        assertEquals(7, copy.repairsTotal)
        assertFalse(copy.streakReminders)
        assertEquals(20, copy.reminderHour)
        assertFalse(copy.keepScreenOn)
        assertEquals(ControlMode.JOYSTICK, copy.controls)
        assertTrue(copy.controlsChosen)
        assertFalse(copy.joystickLeft)
        assertArrayEquals(SaveCodec.encode(original), SaveCodec.encode(copy))
    }

    @Test
    fun saveFromBeforeControlsAsksForThem() {
        // Zapis v3 nie miał wyboru sterowania: 3 ostatnie bajty v4 odpadają, wersja w nagłówku to 3.
        val v4 = SaveCodec.encode(sample())
        val v3 = v4.copyOf(v4.size - 3)
        v3[5] = 3
        val loaded = SaveCodec.decode(v3)
        assertEquals(ControlMode.SWIPE, loaded.controls)
        assertFalse(loaded.controlsChosen)
        assertTrue(loaded.joystickLeft)
        assertEquals(450, loaded.best[0])
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
    fun dailyRewardFollowsServerDay() {
        val save = SaveData()
        var day: Long? = 20_000L
        val p = Progress(save, { 1_000_000_000L }, { day })
        assertTrue(p.dailyState().available)
        assertEquals(50, p.claimDaily())
        assertFalse(p.dailyState().available)
        day = 20_001L
        assertEquals(2, p.dailyState().day)
        assertEquals(75, p.claimDaily())
        // Telefon nie zna czasu serwera (np. po restarcie bez internetu) – nagroda czeka.
        day = null
        assertTrue(p.dailyState().needsOnline)
        assertFalse(p.dailyState().available)
        assertEquals(0, p.claimDaily())
        // Nagroda zapisana na dzień „z przyszłości” (zegar przestawiony w starszej wersji) blokuje do tego dnia.
        day = 19_998L
        assertFalse(p.dailyState().available)
        assertFalse(p.dailyState().needsOnline)
        // Przerwa w odbieraniu resetuje serię.
        day = 20_011L
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
        // Trzeci dzień tygodnia passy: 3 trofea.
        assertEquals(3, p.applyRun(win(1, 200, 1), LevelGenerator.plan(1)).trophies)
        assertEquals(1L + 2 + 3, p.trophies)
        assertEquals(3, save.streakBest)
        // Dzień przerwy przerywa passę, trofea zostają.
        nextDay(2)
        assertEquals(0, p.streakState().days)
        assertEquals(1, p.applyRun(win(1, 200, 1), LevelGenerator.plan(1)).streakDays)
        assertEquals(3, save.streakBest)
        // Dzień wcześniejszy niż ostatni zaliczony nie przedłuża passy.
        day -= 3
        assertEquals(0, p.applyRun(win(1, 200, 1), LevelGenerator.plan(1)).streakDays)
    }

    @Test
    fun brokenStreakCanBeRepairedFiveTimesAMonth() {
        val save = SaveData()
        var day = LocalDate.of(2026, 1, 10).toEpochDay()
        var now = 1_000_000_000L
        val p = Progress(save, { now }, { day })
        fun advance(n: Int) {
            day += n
            now += n * 86_400_000L
        }
        fun play() = p.applyRun(win(1, 200, 1), LevelGenerator.plan(1))
        p.addCoins(10_000)
        play()
        assertNull("trwająca passa nie potrzebuje naprawy", p.streakRepair())
        // Pięć napraw w miesiącu, każda droższa: x1.0, x1.2, x1.8, x2.2, x3.0.
        val prices = mutableListOf<Int>()
        repeat(5) {
            advance(2)
            assertEquals(0, p.streakState().days)
            val offer = p.streakRepair()!!
            assertEquals(1, offer.missedDays)
            prices += offer.cost
            val coins = p.coins
            assertTrue(p.repairStreak())
            assertEquals(coins - offer.cost, p.coins)
            assertFalse(p.streakState().playedToday)
            play()
        }
        assertEquals(listOf(200, 240, 360, 440, 600), prices)
        assertEquals(6, save.streak)
        assertEquals(5, save.repairsTotal)
        // Szósta przerwa w tym samym miesiącu – passa przepada.
        advance(2)
        assertNull(p.streakRepair())
        assertTrue(p.streakLost())
        assertFalse(p.repairStreak())
        play()
        assertEquals(1, save.streak)
        assertFalse(p.streakLost())
    }

    @Test
    fun streakRepairsResetEveryStreakMonth() {
        val save = SaveData()
        var day = LocalDate.of(2026, 1, 31).toEpochDay()
        val p = Progress(save, { 1_000_000_000L + day * 86_400_000L }, { day })
        fun play() = p.applyRun(win(1, 200, 1), LevelGenerator.plan(1))
        p.addCoins(10_000)
        play()
        assertEquals(day, save.streakStartDay)
        // Dwa opuszczone dni = dwie naprawy naraz (x1.0 + x1.2).
        day += 3
        val offer = p.streakRepair()!!
        assertEquals(2, offer.missedDays)
        assertEquals(200 + 240, offer.cost)
        assertEquals(LocalDate.of(2026, 2, 28).toEpochDay(), offer.resetDay)
        assertTrue(p.repairStreak())
        play()
        // Codzienna gra aż do nowego miesiąca passy (od 28 lutego).
        while (day < LocalDate.of(2026, 3, 1).toEpochDay()) {
            day++
            play()
        }
        day += 2
        val fresh = p.streakRepair()!!
        assertEquals(0, fresh.used)
        assertEquals(200, fresh.cost)
        // Za długa przerwa nie da się już załatać.
        day += 3
        assertNull(p.streakRepair())
    }

    @Test
    fun repairNeedsEnoughCoins() {
        val save = SaveData()
        var day = 20_000L
        val p = Progress(save, { 1_000_000_000L + day * 86_400_000L }, { day })
        p.applyRun(win(1, 200, 1), LevelGenerator.plan(1))
        day += 2
        val coins = p.coins
        assertTrue(coins < StreakRepairs.BASE_PRICE)
        assertFalse(p.repairStreak())
        assertEquals(coins, p.coins)
        assertEquals(0, save.repairsUsed)
        assertEquals(StreakRepairs.BASE_PRICE, p.streakRepair()!!.cost)
    }

    @Test
    fun reminderSlotsAreSpacedOut() {
        for (hour in StreakReminderSlots.HOURS) {
            val slots = StreakReminderSlots.slots(hour)
            assertTrue(slots.size in 2..3)
            for ((a, b) in slots.zipWithNext()) assertTrue("$hour: $a → $b", a.first.plusMinutes(89).isBefore(b.first))
            assertEquals(LocalTime.of(22, 0), slots.last().first)
        }
        assertEquals(listOf(18 to 0, 20 to 0, 22 to 0), StreakReminderSlots.slots(18).map { it.first.hour to it.first.minute })
    }

    @Test
    fun menuBoatNeverSailsThroughLand() {
        val g = Backdrop.grid
        val loop = Backdrop.ROWS * TILE
        for (y in 0 until loop) {
            val x = Backdrop.laneX(y.toFloat())
            // Kadłub z żaglem: ok. 14 px szerokości, od 14 px nad do 7 px pod punktem łodzi.
            for (py in (y - 14)..(y + 7) step 3) for (px in listOf(x - 7f, x, x + 7f)) {
                val tile = g[Math.floorDiv(px.toInt(), TILE), Math.floorDiv(py, TILE)]
                assertTrue("y=$y x=$x trafia w $tile", tile == Tile.SEA)
            }
        }
        // Kurs zmienia się płynnie (bez szarpnięć między wierszami).
        for (y in 0 until loop) {
            val d = abs(Backdrop.heading(y.toFloat()) - Backdrop.heading(y + 1f))
            assertTrue("skok kursu przy y=$y", d < 0.08f)
        }
    }

    @Test
    fun streakWeeksGetRicher() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 10), (1..7).map { StreakRewards.trophiesFor(it) })
        // Po 7 dniach tydzień zaczyna się od nowa, ale stawki rosną.
        assertEquals(listOf(2, 4, 6, 8, 10, 12, 20), (8..14).map { StreakRewards.trophiesFor(it) })
        assertEquals(2, StreakRewards.week(8))
        assertEquals(1, StreakRewards.week(7))
        // Mnożnik ma sufit.
        assertEquals(100, StreakRewards.trophiesFor(7 * 30))
        assertEquals(0, StreakRewards.trophiesFor(0))
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
    fun grandFinaleHasItsOwnHarbourOnTheMap() {
        val finale = LevelConstants.LEVEL_COUNT
        val last = LevelConstants.WORLD_COUNT - 1
        val map = WorldMap.forLevel(finale)
        assertEquals(0, map.endlessWorlds)
        val lastGate = map.labels.single { it.world == Worlds.FINALE }.row
        val firstGate = map.labels.single { it.world == 1 }.row
        // Duża wyspa na środku portu, za ostatnimi wrotami, w złotej palecie.
        assertEquals(5, map.islandW[finale - 1])
        assertEquals(3, map.islandH[finale - 1])
        assertEquals(LevelConstants.WIDTH * TILE / 2f, map.nodeX(finale))
        assertTrue(map.nodeY(finale) < lastGate * TILE)
        assertEquals(Worlds.FINALE, map.worldOfRow(map.islandRow[finale - 1]))
        assertEquals(Worlds.FINALE_THEME, Worlds.theme(map.worldOfRow(0)))
        assertEquals(last, map.worldOfRow(lastGate))
        // Ostatnie wrota otwierają się dopiero, gdy finał jest odblokowany.
        assertTrue(map.isGateOpen(lastGate, finale))
        assertFalse(map.isGateOpen(lastGate, finale - 1))
        assertTrue(map.isGateOpen(firstGate, Worlds.firstLevel(1)))
        assertFalse(map.isGateOpen(firstGate, Worlds.firstLevel(1) - 1))
        // Szczyt portu to mur – dopóki finał nie jest ukończony, dalej nie ma nic.
        assertEquals(Tile.WALL, map.grid[5, 0])
        assertEquals(Worlds.FINALE, Worlds.paletteOf(finale))
        assertEquals(last, Worlds.paletteOf(finale - 1))
    }

    @Test
    fun mapGrowsIntoTheEndlessSea() {
        val campaign = WorldMap.forLevel(1)
        val map = WorldMap.forLevel(1100)
        // Gracz na poziomie 1100 jest w 4. świecie Bezkresnego Morza – mapa pokazuje go i jeden świat dalej.
        assertEquals(5, map.endlessWorlds)
        assertEquals(1125, map.levelCount)
        // Część z poziomami 1–1000 wygląda tak samo, tylko leży niżej (oprócz wrót portu finału).
        val offset = map.grid.height - campaign.grid.height
        for (r in 1 until campaign.grid.height) for (c in 0 until LevelConstants.WIDTH) {
            assertEquals("pole $c,$r", campaign.grid[c, r], map.grid[c, r + offset])
        }
        assertEquals(Tile.DOOR, map.grid[5, offset])
        assertTrue(map.isGateOpen(offset, LevelConstants.LEVEL_COUNT + 1))
        assertFalse(map.isGateOpen(offset, LevelConstants.LEVEL_COUNT))
        // Każdy świat Bezkresnego Morza ma napis i własną paletę wierszy.
        for (e in 0 until map.endlessWorlds) {
            val world = LevelConstants.WORLD_COUNT + e
            val label = map.labels.single { it.world == world }
            assertEquals(world, map.worldOfRow(map.islandRow[Worlds.firstLevel(world) - 1]))
            assertTrue(map.isGateOpen(label.row, Worlds.firstLevel(world)))
            assertFalse(map.isGateOpen(label.row, Worlds.firstLevel(world) - 1))
        }
        assertEquals(0, WorldMap.endlessWorldsFor(LevelConstants.LEVEL_COUNT))
        assertEquals(2, WorldMap.endlessWorldsFor(LevelConstants.LEVEL_COUNT + 1))
        assertEquals(
            (LevelConstants.MAX_LEVEL - LevelConstants.LEVEL_COUNT) / LevelConstants.LEVELS_PER_WORLD,
            WorldMap.endlessWorldsFor(LevelConstants.MAX_LEVEL),
        )
    }

    @Test
    fun everyWorldHasANameInEveryLanguage() {
        for (dir in listOf("values", "values-pl")) {
            val xml = java.io.File("src/main/res/$dir/strings.xml").readText()
            val array = xml.substringAfter("<string-array name=\"world_names\">").substringBefore("</string-array>")
            val names = Regex("<item>(.+?)</item>").findAll(array).map { it.groupValues[1] }.toList()
            assertEquals(dir, LevelConstants.WORLD_COUNT, names.size)
            assertEquals(dir, names.size, names.toSet().size)
            for ((part, count) in listOf("endless_world_a" to Worlds.ENDLESS_NAME_A, "endless_world_b" to Worlds.ENDLESS_NAME_B)) {
                val items = Regex("<item>(.+?)</item>").findAll(
                    xml.substringAfter("<string-array name=\"$part\">").substringBefore("</string-array>"),
                ).map { it.groupValues[1] }.toList()
                assertEquals("$dir $part", count, items.size)
                assertEquals("$dir $part", count, items.toSet().size)
            }
        }
    }

    /** Zapis z czasów, gdy gra miała [levels] poziomów (tyle wpisów zamiast 1000). */
    private fun legacyBytes(save: SaveData, levels: Int): ByteArray {
        val bytes = SaveCodec.encode(save)
        val header = 4 + 2 + 8
        val entries = header + 2
        val out = ByteArrayOutputStream()
        out.write(bytes, 0, header)
        DataOutputStream(out).writeShort(levels)
        out.write(bytes, entries, levels * 5)
        val rest = entries + LevelConstants.LEVEL_COUNT * 5
        out.write(bytes, rest, bytes.size - rest)
        return out.toByteArray()
    }

    @Test
    fun oldSaveWithAllLevelsDoneUnlocksTheSecondVoyage() {
        val old = SaveData().apply {
            for (i in 0 until 500) {
                stars[i] = 2
                best[i] = 1000 + i
            }
            unlocked.set(500)
            coins.set(777)
        }
        val loaded = SaveCodec.decode(legacyBytes(old, 500))
        assertEquals(501L, loaded.unlocked.get())
        assertEquals(1499, loaded.best[499])
        assertEquals(0, loaded.stars[500].toInt())
        assertEquals(777L, loaded.coins.get())
        // Kto nie skończył jeszcze poziomu 500, nie dostaje nic za darmo.
        val midway = SaveData().apply {
            stars[0] = 1
            unlocked.set(2)
        }
        assertEquals(2L, SaveCodec.decode(legacyBytes(midway, 500)).unlocked.get())
    }

    @Test
    fun grandFinaleRewardsOnceAndOpensTheEndlessSea() {
        val save = SaveData().apply { unlocked.set(LevelConstants.LEVEL_COUNT.toLong()) }
        val p = progress(save)
        val n = LevelConstants.LEVEL_COUNT
        val plan = LevelGenerator.plan(n)
        val first = p.applyRun(win(n, 5000, 1, time = 300_000), plan)
        assertTrue(first.accepted)
        assertEquals(Progress.FINALE_BONUS, first.finaleBonus)
        assertTrue(first.coins > Progress.FINALE_BONUS)
        assertEquals(n + 1, p.highestUnlocked())
        assertEquals(1, save.bossWins)
        // Kolejne przejście finału nie płaci bonusu drugi raz.
        val again = p.applyRun(win(n, 6000, 2, time = 300_000), plan)
        assertEquals(0, again.finaleBonus)
        assertEquals(n + 1, p.highestUnlocked())
        // Ukończenie wszystkich poziomów daje odznakę finału.
        for (i in 0 until n) save.stars[i] = 1
        assertTrue(p.isUnlocked(Achievements.byId("level_1000")!!))
        assertFalse(p.isUnlocked(Achievements.byId("stars_3000")!!))
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
        for (current in listOf(1, LevelConstants.LEVEL_COUNT + 1, 1400)) {
            val map = WorldMap.forLevel(current)
            val g = map.grid
            for (n in 1..map.levelCount) {
                val c = map.islandCol[n - 1]
                val r = map.islandRow[n - 1]
                for (dr in 0 until map.islandH[n - 1]) for (dc in 0 until map.islandW[n - 1]) {
                    assertEquals("wyspa $n", Tile.SAND, g[c + dc, r + dr])
                }
                assertEquals(n, map.levelAt(map.nodeX(n), map.nodeY(n)))
            }
            // Wyspy nie nachodzą na siebie: każda leży wyżej od poprzedniej.
            for (n in 2..map.levelCount) assertTrue("wyspa $n", map.nodeY(n) < map.nodeY(n - 1))
            for (r in 1 until g.height - 1) for (c in 0 until g.width) {
                if (g[c, r] != Tile.SAND) continue
                val w = { t: Tile -> t == Tile.SEA || t == Tile.ROCK }
                assertFalse("pole $c,$r", (w(g[c, r - 1]) && w(g[c, r + 1])) || (w(g[c - 1, r]) && w(g[c + 1, r])))
            }
            // Wyszukiwanie widocznych wysepek zgadza się z przejrzeniem wszystkich.
            for (top in listOf(0, 5, 100, 999, g.height / 2, g.height - 40)) {
                val bottom = top + 45
                val expected = (1..map.levelCount).filter { map.islandRow[it - 1] in top..bottom }
                assertEquals("wiersze $top..$bottom", expected, map.levelsBetween(top, bottom).toList())
            }
        }
    }

    @Test
    fun endlessLevelsAreSavedAndUnlocked() {
        val save = SaveData().apply { unlocked.set(LevelConstants.LEVEL_COUNT + 1L) }
        val p = progress(save)
        assertEquals(LevelConstants.LEVEL_COUNT, p.levelsAvailable() - 1)
        val n = LevelConstants.LEVEL_COUNT + 1
        val reward = p.applyRun(win(n, 3000, 2), LevelGenerator.plan(n))
        assertTrue(reward.accepted)
        assertEquals(n + 1, p.highestUnlocked())
        assertEquals(3000, p.best(n))
        assertEquals(2, p.stars(n))
        assertEquals(0, p.best(n + 500))
        assertTrue(save.levelSlots >= n + 1)
        // Nagroda za ukończenie nie rośnie w nieskończoność razem z numerem świata.
        assertTrue(reward.coins < 400)

        val loaded = SaveCodec.decode(SaveCodec.encode(save))
        assertEquals(n + 1L, loaded.unlocked.get())
        assertEquals(3000, loaded.best[n - 1])
        assertEquals(2, loaded.stars[n - 1].toInt())
        assertEquals(n, loaded.records.last().level)
        assertEquals(p.rankingPoints(), progress(loaded).rankingPoints())

        // Poziomy ponad 32767 (numer jako krótka liczba bez znaku) też przechodzą przez zapis.
        val far = SaveData().apply {
            ensureLevels(LevelConstants.MAX_LEVEL)
            best[LevelConstants.MAX_LEVEL - 1] = 77
            unlocked.set(LevelConstants.MAX_LEVEL.toLong())
        }
        val farLoaded = SaveCodec.decode(SaveCodec.encode(far))
        assertEquals(77, farLoaded.best[LevelConstants.MAX_LEVEL - 1])
        assertEquals(LevelConstants.MAX_LEVEL, progress(farLoaded).highestUnlocked())

        // Reset postępu wraca do samej kampanii.
        p.resetProgress()
        assertEquals(LevelConstants.LEVEL_COUNT, save.levelSlots)
        assertEquals(1, p.highestUnlocked())
    }
}
