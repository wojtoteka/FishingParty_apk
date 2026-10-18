package wojtoteka.ovh.fishingparty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import wojtoteka.ovh.fishingparty.game.GameListener
import wojtoteka.ovh.fishingparty.game.GameWorld
import wojtoteka.ovh.fishingparty.game.Loadout
import wojtoteka.ovh.fishingparty.game.LoseReason
import wojtoteka.ovh.fishingparty.game.Sfx
import wojtoteka.ovh.fishingparty.level.EnemyKind
import wojtoteka.ovh.fishingparty.level.EnemySpawn
import wojtoteka.ovh.fishingparty.level.LevelConstants.TILE
import wojtoteka.ovh.fishingparty.level.LevelConstants.WIDTH
import wojtoteka.ovh.fishingparty.level.LevelLayout
import wojtoteka.ovh.fishingparty.level.LevelSpec
import wojtoteka.ovh.fishingparty.level.LevelType
import wojtoteka.ovh.fishingparty.level.RunResult
import wojtoteka.ovh.fishingparty.level.SecondGoal
import wojtoteka.ovh.fishingparty.level.StageLayout
import wojtoteka.ovh.fishingparty.level.ThirdGoal
import wojtoteka.ovh.fishingparty.level.Tile
import wojtoteka.ovh.fishingparty.level.TileGrid
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot

/** Łódź może wpłynąć rozpędem kawałek na piasek przy brzegu – tam nie ma napędu, ale potwory jej nie dosięgną. */
class BeachTest {

    private class Quiet : GameListener {
        override fun onSound(sfx: Sfx) = Unit
        override fun onVibrate(millis: Long) = Unit
        override fun onFinished(result: RunResult, reason: LoseReason?) = Unit
    }

    /** Woda z plażą (2 kolumny piasku) po lewej, łódź tuż przy niej, opcjonalnie potwór obok. */
    private fun beach(enemy: EnemyKind? = null): GameWorld {
        val height = 24
        val grid = TileGrid(WIDTH, height)
        for (c in 0 until WIDTH) {
            grid[c, 0] = Tile.WALL
            grid[c, 1] = Tile.WALL
            grid[c, height - 1] = Tile.WALL_BOTTOM
        }
        for (r in 2 until height - 1) {
            grid[0, r] = Tile.SAND
            grid[1, r] = Tile.SAND
        }
        val spec = LevelSpec(
            number = 500, world = 0, type = LevelType.CLASSIC, seed = 7L, secondGoal = SecondGoal.NO_DAMAGE,
            thirdGoal = ThirdGoal.PAR_TIME, parSeconds = 60, timeLimitSeconds = 0, scoreTarget = 100, comboTarget = 3,
            enemySpeed = 1.2f, currentX = 0f, currentY = 0f,
        )
        val stage = StageLayout(
            index = 0, gateRow = 1, topRow = 2, bottomRow = height - 2, doorCols = 4..6, quota = 99,
            fish = emptyList(), golden = emptyList(), enemies = listOfNotNull(enemy?.let { EnemySpawn(it, 3, 6) }),
            reachable = IntArray(0),
        )
        val layout = LevelLayout(spec, grid, listOf(stage), startCol = 2, startRow = 12, crabs = emptyList())
        return GameWorld(layout, Loadout(), tutorial = false, listener = Quiet()).also { it.setViewport(180f, 390f) }
    }

    private fun GameWorld.sail(angle: Float, seconds: Float) {
        repeat((seconds * 60).toInt()) {
            setInput(true, angle)
            advance(1f / 60f)
        }
    }

    private val shore = 2f * TILE

    @Test
    fun boatSlidesOnlyALittleOntoTheSand() {
        val world = beach()
        world.sail(PI.toFloat(), 3f)
        assertTrue("łódź nie wpłynęła na piasek (${world.shipX})", world.beached)
        assertTrue("za głęboko: ${shore - world.shipX}", shore - world.shipX <= GameWorld.BEACH_REACH + 0.01f)
        assertTrue(world.speed < 1f)

        // Wzdłuż plaży się nie rusza – bez napędu na piasku zostaje w miejscu.
        val y = world.shipY
        world.sail((-PI / 2).toFloat(), 5f)
        assertTrue("płynie po piasku: ${y - world.shipY}", abs(y - world.shipY) < 1f)
        assertTrue(world.beached)

        // Ale obróci się i wypłynie.
        world.sail(0f, 2f)
        assertFalse(world.beached)
        assertTrue(world.shipX > shore + 20f)
    }

    @Test
    fun boatCannotSailAcrossTheSand() {
        // Z piasku nie da się płynąć w żadną stronę, która nie prowadzi do wody – ani wzdłuż brzegu, ani w głąb.
        for (degrees in 0 until 360 step 5) {
            val world = beach()
            world.sail(PI.toFloat(), 1.5f)
            assertTrue(world.beached)
            var onSand = 0f
            val angle = (degrees * PI / 180).toFloat()
            repeat(60 * 20) {
                val x = world.shipX
                val y = world.shipY
                val wasBeached = world.beached
                world.setInput(true, angle)
                world.advance(1f / 60f)
                if (wasBeached) onSand += hypot(world.shipX - x, world.shipY - y)
            }
            assertTrue("kurs $degrees°: po piasku ${onSand} jedn.", onSand < 25f)
        }
    }

    @Test
    fun shallowApproachDoesNotLetTheBoatCrawlAlongTheShore() {
        // Łódź płynąca z wody pod małym kątem do brzegu wpływa rozpędem, ale potem stoi.
        for (degrees in listOf(95, 100, 110, 120, 135, 150, 170)) {
            val world = beach()
            val angle = (degrees * PI / 180).toFloat()
            var onSand = 0f
            repeat(60 * 20) {
                val x = world.shipX
                val y = world.shipY
                val wasBeached = world.beached
                world.setInput(true, angle)
                world.advance(1f / 60f)
                if (wasBeached) onSand += hypot(world.shipX - x, world.shipY - y)
            }
            assertTrue("kurs $degrees°: po piasku ${onSand} jedn.", onSand < 25f)
        }
    }

    @Test
    fun monstersCannotReachABeachedBoat() {
        for (kind in listOf(EnemyKind.STALKER, EnemyKind.DARK_SHARK, EnemyKind.RED_OCTOPUS)) {
            val world = beach(kind)
            world.sail(PI.toFloat(), 1.5f)
            assertTrue(world.beached)
            repeat(60 * 30) {
                world.setInput(false, 0f)
                world.advance(1f / 60f)
                assertFalse("$kind widzi łódź na piasku", world.enemies.single().locked)
            }
            assertEquals("$kind trafił łódź na piasku", 0, world.hits)
        }
    }
}
