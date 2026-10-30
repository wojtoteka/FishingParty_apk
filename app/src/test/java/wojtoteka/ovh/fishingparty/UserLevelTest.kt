package wojtoteka.ovh.fishingparty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import wojtoteka.ovh.fishingparty.game.GameListener
import wojtoteka.ovh.fishingparty.game.GameWorld
import wojtoteka.ovh.fishingparty.game.Loadout
import wojtoteka.ovh.fishingparty.game.LoseReason
import wojtoteka.ovh.fishingparty.game.Sfx
import wojtoteka.ovh.fishingparty.level.EnemyKind
import wojtoteka.ovh.fishingparty.level.LevelConstants
import wojtoteka.ovh.fishingparty.level.LevelConstants.WIDTH
import wojtoteka.ovh.fishingparty.level.LevelGenerator
import wojtoteka.ovh.fishingparty.level.LevelType
import wojtoteka.ovh.fishingparty.level.RunResult
import wojtoteka.ovh.fishingparty.level.Tile
import wojtoteka.ovh.fishingparty.level.TileGrid
import wojtoteka.ovh.fishingparty.level.UserCrab
import wojtoteka.ovh.fishingparty.level.UserEnemy
import wojtoteka.ovh.fishingparty.level.UserFish
import wojtoteka.ovh.fishingparty.level.UserGolden
import wojtoteka.ovh.fishingparty.level.UserLevel
import wojtoteka.ovh.fishingparty.level.UserLevelProblem
import wojtoteka.ovh.fishingparty.level.UserLevelRating
import wojtoteka.ovh.fishingparty.level.UserLevels
import wojtoteka.ovh.fishingparty.level.Worlds

class UserLevelTest {

    private val samples = (1L..60L).map { UserLevelSamples.random(it) }

    @Test
    fun encodeDecodeRoundTrip() {
        for (level in samples) {
            val text = UserLevels.encode(level)
            val back = UserLevels.decode(text)
            assertNotNull(text, back)
            assertEquals(text, UserLevels.encode(back!!))
        }
    }

    @Test
    fun titleIsEscaped() {
        val level = UserLevels.starter("Rafa \"Kraken\" \\ ąę")
        val back = UserLevels.decode(UserLevels.encode(level))!!
        assertEquals(level.title, back.title)
    }

    @Test
    fun brokenDataIsRejected() {
        val text = UserLevels.encode(UserLevels.starter("Test"))
        assertNull(UserLevels.decode(text.replace("\"rows\":16", "\"rows\":99")))
        assertNull(UserLevels.decode(text.replace("\"door\":5", "\"door\":0")))
        assertNull(UserLevels.decode(text.replace("\"speed\":100", "\"speed\":500")))
        assertNull(UserLevels.decode(text.replace("[0,3,4]", "[0,3,40]")))
        assertNull(UserLevels.decode(text.replaceFirst("ss", "sx")))
        assertNull(UserLevels.decode(text.replaceFirst("ss", "sg")))
        assertNull(UserLevels.decode("{nie json"))
    }

    @Test
    fun normalizeIsStable() {
        for (level in samples) {
            val once = UserLevels.normalize(level)
            assertEquals(UserLevels.encode(once), UserLevels.encode(UserLevels.normalize(once)))
        }
    }

    @Test
    fun thinSandAndEntitiesOnSandAreRemoved() {
        val base = UserLevels.blankStage()
        val cells = base.cells.copyOf()
        // Pojedynczy pas piasku (1 wiersz) nie ma kafelka – znika; wyspa 2×2 zostaje.
        for (c in 3..7) cells[6 * WIDTH + c] = UserLevels.SAND
        for ((c, r) in listOf(4 to 10, 5 to 10, 4 to 11, 5 to 11)) cells[r * WIDTH + c] = UserLevels.SAND
        val stage = base.copy(
            cells = cells,
            fish = listOf(UserFish(false, 4, 10), UserFish(false, 7, 3), UserFish(true, 7, 3)),
            enemies = listOf(UserEnemy(EnemyKind.SHARK, 5, 11)),
            crabs = listOf(UserCrab(4, 10), UserCrab(8, 8)),
        )
        val level = UserLevels.normalize(UserLevels.starter("Plaża").copy(stages = listOf(stage)))
        val s = level.stages[0]
        assertEquals(UserLevels.SEA, s.cell(5, 6))
        assertEquals(UserLevels.SAND, s.cell(4, 10))
        // Ryba na piasku przepada, a na jednym polu zostaje jedna postać.
        assertEquals(listOf(UserFish(false, 7, 3)), s.fish)
        assertTrue(s.enemies.isEmpty())
        assertEquals(listOf(UserCrab(4, 10)), s.crabs)
    }

    @Test
    fun doorAndStartStayWater() {
        val stage = UserLevels.blankStage().let { s -> s.copy(cells = ByteArray(s.cells.size) { UserLevels.SAND }) }
        val level = UserLevels.normalize(UserLevels.starter("Zasypany").copy(stages = listOf(stage)))
        val s = level.stages[0]
        for (c in UserLevels.doorCols(s.door)) {
            assertEquals(UserLevels.SEA, s.cell(c, 0))
            assertEquals(UserLevels.SEA, s.cell(c, 1))
        }
        for (r in s.rows - 3 until s.rows) for (c in 4..6) assertEquals(UserLevels.SEA, s.cell(c, r))
        assertTrue(UserLevels.locked(level, 0, 5, 0))
        assertTrue(UserLevels.locked(level, 0, 5, s.rows - 1))
        assertFalse(UserLevels.locked(level, 0, 5, 8))
        // Zasypany etap nie ma drogi od startu do bramy.
        assertTrue(UserLevels.check(level).any { it.kind == UserLevelProblem.Kind.DOOR_UNREACHABLE })
    }

    @Test
    fun gatesHaveNoHoles() {
        for (level in samples) {
            val built = UserLevels.build(level)
            val grid = built.layout.grid
            for (stage in built.layout.stages) {
                for (c in 0 until WIDTH) {
                    val t = grid[c, stage.gateRow]
                    if (c in stage.doorCols) assertEquals(Tile.DOOR, t) else assertTrue("dziura w bramie: $t", t == Tile.WALL || t == Tile.SAND)
                }
            }
        }
    }

    @Test
    fun builtLevelMatchesDefinition() {
        for (level in samples) {
            val built = UserLevels.build(level)
            assertEquals(LevelType.CUSTOM, built.plan.spec.type)
            assertEquals(level.stages.size, built.layout.stages.size)
            for ((k, s) in level.stages.withIndex()) {
                val layout = built.layout.stages[k]
                assertEquals(s.rows, layout.bottomRow - layout.topRow + 1)
                assertEquals(s.fish.size, layout.fish.size)
                assertEquals(s.golden.size, layout.golden.size)
                assertEquals(s.enemies.size, layout.enemies.size)
                assertEquals(s.currentX.toFloat(), layout.currentX)
                for (f in layout.fish) assertEquals(Tile.SEA, built.layout.grid[f.col, f.row])
                assertEquals(s.fish.count { !it.blue }, built.plan.stages[k].common)
            }
            assertEquals(LevelConstants.FINISH_ROWS, built.layout.stages.last().gateRow)
            assertTrue(LevelGenerator.maxScore(built.plan) > 0)
        }
    }

    @Test
    fun sandColorsAreKept() {
        val base = UserLevels.blankStage()
        val cells = base.cells.copyOf()
        // Wyspa 2×2 w złotym piasku finału, obok – w piasku Mroźnego Fiordu; brzegi w barwach etapu.
        for ((c, r) in listOf(3 to 6, 4 to 6, 3 to 7, 4 to 7)) cells[r * WIDTH + c] = UserLevels.sandCode(Worlds.FINALE)
        for ((c, r) in listOf(6 to 9, 7 to 9, 6 to 10, 7 to 10)) cells[r * WIDTH + c] = UserLevels.sandCode(3)
        // Kawałek lewego brzegu w piasku Morza o Północy.
        for ((c, r) in listOf(0 to 2, 1 to 2, 0 to 3, 1 to 3)) cells[r * WIDTH + c] = UserLevels.sandCode(7)
        // Pojedynczy kolorowy pas znika jak zwykły piasek.
        for (c in 2..8) cells[13 * WIDTH + c] = UserLevels.sandCode(5)
        val stage = base.copy(cells = cells, crabs = listOf(UserCrab(3, 6), UserCrab(7, 10)))
        val level = UserLevels.normalize(UserLevels.starter("Tęcza").copy(stages = listOf(stage)))
        val s = level.stages[0]
        assertEquals(UserLevels.sandCode(Worlds.FINALE), s.cell(3, 6))
        assertEquals(UserLevels.sandCode(3), s.cell(7, 10))
        assertEquals(UserLevels.SAND, s.cell(0, 6))
        assertEquals(UserLevels.SEA, s.cell(3, 13))
        assertEquals(listOf(UserCrab(3, 6), UserCrab(7, 10)), s.crabs)
        assertTrue(UserLevels.isSand(s.cell(3, 6)) && !UserLevels.isSand(UserLevels.ROCK))
        assertEquals(3, UserLevels.sandPalette(s.cell(6, 9)))
        assertEquals(UserLevels.SAME_THEME, UserLevels.sandPalette(UserLevels.SAND))
        // Zapis: cyfra to paleta świata, „f” – złota paleta finału.
        val text = UserLevels.encode(level)
        assertTrue(text.contains("ff") && text.contains("33"))
        assertEquals(text, UserLevels.encode(UserLevels.decode(text)!!))
        // Na planszy gry piasek ma swój kolor, a zwykły – barwy wiersza.
        val layout = UserLevels.build(level).layout
        val top = layout.stages[0].topRow
        assertEquals(Tile.SAND, layout.grid[3, top + 6])
        assertEquals(Worlds.FINALE, layout.grid.sandColor(3, top + 6))
        assertEquals(3, layout.grid.sandColor(6, top + 9))
        assertEquals(TileGrid.DEFAULT_SAND, layout.grid.sandColor(0, top + 6))
        // Poza planszą piasek ciągnie się w kolorze brzegu.
        assertEquals(7, layout.grid.sandColor(-1, top + 2))
        assertEquals(TileGrid.DEFAULT_SAND, layout.grid.sandColor(-1, top + 6))
    }

    @Test
    fun plainSandKeepsOldFormat() {
        // Poziomy bez kolorowego piasku zapisują się jak dotąd (ten sam skrót na telefonie i na serwerze).
        val text = UserLevels.encode(UserLevels.starter("Stary"))
        assertTrue(text.contains("\"cells\":\"ss.......ss"))
        assertFalse(Regex("\"cells\":\"[^\"]*[0-9f]").containsMatchIn(text))
    }

    @Test
    fun stagePalettesGoToRows() {
        val level = UserLevels.starter("Dwa światy").let { l ->
            l.copy(stages = listOf(l.stages[0].copy(theme = 3), UserLevels.blankStage().copy(quota = 1, fish = listOf(UserFish(false, 5, 5)))))
        }
        val layout = UserLevels.build(level).layout
        val rows = layout.rowWorlds!!
        assertEquals(3, rows[layout.stages[0].topRow])
        assertEquals(level.theme, rows[layout.stages[1].topRow])
        assertNull(UserLevels.build(UserLevels.starter("Jeden")).layout.rowWorlds)
    }

    @Test
    fun problemsAreFound() {
        val start = UserLevels.starter("Rafa")
        assertTrue(UserLevels.check(start).none { it.error })
        val greedy = start.copy(stages = listOf(start.stages[0].copy(quota = 9)))
        assertTrue(UserLevels.check(greedy).any { it.kind == UserLevelProblem.Kind.QUOTA_TOO_HIGH })
        val golden = start.copy(stages = listOf(start.stages[0].copy(quota = 5, golden = listOf(UserGolden(3, 6, 6)))))
        assertTrue(UserLevels.check(golden).any { it.kind == UserLevelProblem.Kind.QUOTA_NEEDS_GOLDEN })
        assertTrue(UserLevels.check(golden).none { it.error })
        // Ryba za ścianą skał nie do złowienia, potwór w takiej kieszeni tylko ostrzega.
        val s = start.stages[0]
        val cells = s.cells.copyOf()
        for (c in 2..8) cells[3 * WIDTH + c] = UserLevels.ROCK
        for (c in 2..8) cells[5 * WIDTH + c] = UserLevels.ROCK
        cells[4 * WIDTH + 2] = UserLevels.ROCK
        cells[4 * WIDTH + 8] = UserLevels.ROCK
        val pocket = UserLevels.normalize(
            start.copy(stages = listOf(s.copy(cells = cells, fish = s.fish + UserFish(false, 4, 4), enemies = listOf(UserEnemy(EnemyKind.SHARK, 6, 4))))),
        )
        val kinds = UserLevels.check(pocket).map { it.kind }
        assertTrue(UserLevelProblem.Kind.FISH_UNREACHABLE in kinds)
        assertTrue(UserLevelProblem.Kind.ENEMY_TRAPPED in kinds)
        assertTrue(UserLevels.check(start.copy(title = " a ")).any { it.kind == UserLevelProblem.Kind.TITLE_SHORT })
    }

    @Test
    fun ratingGrowsWithDanger() {
        val calm = UserLevels.starter("Spokój")
        assertEquals(0, UserLevelRating.estimate(calm).tier)
        val s = calm.stages[0]
        val one = calm.copy(stages = listOf(s.copy(enemies = listOf(UserEnemy(EnemyKind.PIRANHA, 5, 2)))))
        val many = calm.copy(
            speed = 150,
            stages = listOf(
                s.copy(
                    enemies = listOf(
                        UserEnemy(EnemyKind.DARK_SHARK, 3, 2), UserEnemy(EnemyKind.DARK_SHARK, 6, 3), UserEnemy(EnemyKind.STALKER, 4, 6),
                        UserEnemy(EnemyKind.RED_OCTOPUS, 7, 8), UserEnemy(EnemyKind.STALKER, 3, 9), UserEnemy(EnemyKind.SHARK, 6, 11),
                    ),
                ),
            ),
        )
        val easy = UserLevelRating.estimate(one)
        val hard = UserLevelRating.estimate(many)
        assertTrue(easy.tier in 1..2)
        assertTrue("${hard.difficulty}", hard.tier >= 5)
        assertTrue(hard.difficulty > easy.difficulty)
        // Potwór zamknięty w kieszeni nie podnosi trudności.
        val cells = s.cells.copyOf()
        for (c in 2..8) cells[3 * WIDTH + c] = UserLevels.ROCK
        for (c in 2..8) cells[5 * WIDTH + c] = UserLevels.ROCK
        cells[4 * WIDTH + 2] = UserLevels.ROCK
        cells[4 * WIDTH + 8] = UserLevels.ROCK
        val trapped = UserLevels.normalize(calm.copy(stages = listOf(s.copy(cells = cells, enemies = listOf(UserEnemy(EnemyKind.STALKER, 5, 4))))))
        assertEquals(0, UserLevelRating.estimate(trapped).tier)
    }

    private class Recorder : GameListener {
        var result: RunResult? = null
        override fun onSound(sfx: Sfx) = Unit
        override fun onVibrate(millis: Long) = Unit
        override fun onFinished(result: RunResult, reason: LoseReason?) {
            this.result = result
        }
    }

    @Test
    fun enemyStartsInChosenDirection() {
        val s = UserLevels.starter("Kierunki").stages[0]
        val level = UserLevels.starter("Kierunki").copy(
            stages = listOf(
                s.copy(
                    enemies = listOf(
                        UserEnemy(EnemyKind.SHARK, 3, 2, 3), UserEnemy(EnemyKind.PIRANHA, 6, 3, 2),
                        UserEnemy(EnemyKind.PIRANHA_VERTICAL, 8, 5, 1), UserEnemy(EnemyKind.OCTOPUS, 4, 8, 0),
                    ),
                ),
            ),
        )
        val world = GameWorld(UserLevels.build(level).layout, Loadout(), tutorial = false, listener = Recorder())
        val dirs = world.enemies.map { it.dirX to it.dirY }
        assertEquals(listOf(0 to -1, -1 to 0, 0 to 1, 1 to 0), dirs)
    }

    @Test
    fun stageWithoutFishOpensGateAtOnce() {
        val start = UserLevels.starter("Bez ryb")
        val empty = UserLevels.blankStage(quota = 0)
        val level = UserLevels.normalize(start.copy(stages = listOf(empty, start.stages[0])))
        assertEquals(0, level.stages[0].quota)
        assertTrue(UserLevels.check(level).none { it.error && it.stage == 0 })
        assertEquals(UserLevels.encode(level), UserLevels.decode(UserLevels.encode(level))?.let { UserLevels.encode(it) })
        val world = GameWorld(UserLevels.build(level).layout, Loadout(), tutorial = false, listener = Recorder())
        assertTrue(world.isDoorOpen(0))
        assertFalse(world.isDoorOpen(1))
    }

    @Test
    fun stageCurrentPushesBoat() {
        val start = UserLevels.starter("Prąd")
        val pushed = start.copy(stages = listOf(start.stages[0].copy(currentX = 30)))
        fun drift(level: UserLevel): Float {
            val world = GameWorld(UserLevels.build(level).layout, Loadout(), tutorial = false, listener = Recorder())
            world.setViewport(180f, 390f)
            val x = world.shipX
            repeat(60) { world.advance(1f / 60f) }
            return world.shipX - x
        }
        assertEquals(0f, drift(start), 0.01f)
        assertTrue(drift(pushed) > 10f)
    }

    @Test
    fun testFromLaterStageStartsUnderPreviousDoor() {
        val start = UserLevels.starter("Etapy")
        val level = start.copy(stages = listOf(start.stages[0].copy(door = 2), UserLevels.blankStage(quota = 1).copy(fish = listOf(UserFish(false, 5, 3)))))
        val built = UserLevels.build(level, fromStage = 1)
        assertEquals(1, built.layout.stages.size)
        assertEquals(2, built.layout.startCol)
        assertEquals(Tile.SEA, built.layout.grid[2, built.layout.startRow])
    }
}
