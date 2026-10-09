package wojtoteka.ovh.fishingparty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wojtoteka.ovh.fishingparty.level.LevelConstants
import wojtoteka.ovh.fishingparty.level.LevelGenerator
import wojtoteka.ovh.fishingparty.level.LevelType
import wojtoteka.ovh.fishingparty.level.Tile
import wojtoteka.ovh.fishingparty.level.TileGrid

class LevelGeneratorTest {

    private val all = (1..LevelConstants.LEVEL_COUNT).map { LevelGenerator.generate(it) }

    @Test
    fun generationIsDeterministic() {
        for (n in listOf(1, 2, 37, 250, 500)) {
            val a = LevelGenerator.generate(n)
            val b = LevelGenerator.generate(n)
            assertEquals(a.grid.height, b.grid.height)
            for (r in 0 until a.grid.height) for (c in 0 until a.grid.width) {
                assertEquals("poziom $n pole $c,$r", a.grid[c, r], b.grid[c, r])
            }
            assertEquals(a.fishCount, b.fishCount)
        }
    }

    @Test
    fun everySandTileHasMatchingSprite() {
        for (layout in all) {
            val g = layout.grid
            for (r in 1 until g.height - 1) for (c in 0 until g.width) {
                if (g[c, r] != Tile.SAND) continue
                val n = water(g[c, r - 1])
                val s = water(g[c, r + 1])
                val w = water(g[c - 1, r])
                val e = water(g[c + 1, r])
                assertTrue("poziom ${layout.spec.number} pole $c,$r", !(n && s) && !(w && e))
            }
        }
    }

    private fun water(t: Tile) = t == Tile.SEA || t == Tile.ROCK

    @Test
    fun finishIsReachableFromStart() {
        for (layout in all) {
            val reached = flood(layout.grid, layout.startCol, layout.startRow)
            val finishReached = (1 until LevelConstants.FINISH_ROWS).any { r ->
                (0 until layout.grid.width).any { c -> reached[r * layout.grid.width + c] }
            }
            assertTrue("poziom ${layout.spec.number}: meta nieosiągalna", finishReached)
        }
    }

    private fun flood(g: TileGrid, col: Int, row: Int): BooleanArray {
        val seen = BooleanArray(g.width * g.height)
        val queue = ArrayDeque<Int>()
        queue += row * g.width + col
        seen[row * g.width + col] = true
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            val c = cell % g.width
            val r = cell / g.width
            for ((dc, dr) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                val nc = c + dc
                val nr = r + dr
                if (!g.inside(nc, nr)) continue
                val idx = nr * g.width + nc
                val t = g[nc, nr]
                if (!seen[idx] && (t == Tile.SEA || t == Tile.DOOR)) {
                    seen[idx] = true
                    queue += idx
                }
            }
        }
        return seen
    }

    @Test
    fun stagesHaveEnoughFishOnWater() {
        for (layout in all) {
            for (stage in layout.stages) {
                assertTrue("poziom ${layout.spec.number}", stage.fish.size >= stage.quota)
                for (f in stage.fish) {
                    assertEquals(Tile.SEA, layout.grid[f.col, f.row])
                    assertTrue(f.row in stage.topRow..stage.bottomRow)
                }
                for (e in stage.enemies) {
                    assertEquals(Tile.SEA, layout.grid[e.col, e.row])
                    assertTrue(e.row in stage.topRow..stage.bottomRow)
                }
                for (c in stage.doorCols) assertEquals(Tile.DOOR, layout.grid[c, stage.gateRow])
            }
        }
    }

    @Test
    fun difficultyGrowsAndVarietyExists() {
        val first = LevelGenerator.plan(1)
        assertEquals(1, first.stages.size)
        assertEquals(0, first.enemyCount)
        val types = (1..LevelConstants.LEVEL_COUNT).map { LevelGenerator.typeOf(it) }.toSet()
        assertEquals(LevelType.entries.toSet(), types)
        val early = (2..50).sumOf { LevelGenerator.plan(it).enemyCount } / 49f
        val late = (451..500).sumOf { LevelGenerator.plan(it).enemyCount } / 50f
        assertTrue("trudność nie rośnie: $early vs $late", late > early * 2)
        for (n in 1..LevelConstants.LEVEL_COUNT) {
            if (LevelGenerator.isBoss(n)) assertEquals(LevelType.BOSS, LevelGenerator.typeOf(n))
        }
    }
}
