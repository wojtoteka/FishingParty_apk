package wojtoteka.ovh.fishingparty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wojtoteka.ovh.fishingparty.level.EnemyKind
import wojtoteka.ovh.fishingparty.level.LevelConstants
import wojtoteka.ovh.fishingparty.level.LevelGenerator
import wojtoteka.ovh.fishingparty.level.LevelType
import wojtoteka.ovh.fishingparty.level.SecondGoal
import wojtoteka.ovh.fishingparty.level.ThirdGoal
import wojtoteka.ovh.fishingparty.level.Tile
import wojtoteka.ovh.fishingparty.level.TileGrid
import wojtoteka.ovh.fishingparty.level.Worlds
import java.security.MessageDigest

class LevelGeneratorTest {

    private val all = (1..LevelConstants.LEVEL_COUNT).map { LevelGenerator.generate(it) }

    @Test
    fun generationIsDeterministic() {
        for (n in listOf(1, 2, 37, 250, 500, 501, 777, 999, 1000)) {
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
    fun noUnreachableWaterPockets() {
        for (layout in all) {
            val g = layout.grid
            val reach = LevelGenerator.reachableFromStart(g, layout.startCol, layout.startRow)
            for (r in 1 until g.height - 1) for (c in 0 until g.width) {
                if (g[c, r] == Tile.SEA) assertTrue("poziom ${layout.spec.number} pole $c,$r", reach[r * g.width + c])
            }
        }
    }

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
        // Druga wyprawa (501–999) jest jeszcze trudniejsza od końca pierwszej.
        val second = (950..999).sumOf { LevelGenerator.plan(it).enemyCount } / 50f
        assertTrue("druga wyprawa nie jest trudniejsza: $late vs $second", second > late)
        for (n in 1..LevelConstants.LEVEL_COUNT) {
            if (LevelGenerator.isBoss(n)) assertEquals(LevelType.BOSS, LevelGenerator.typeOf(n))
            if (n != LevelConstants.LEVEL_COUNT) assertTrue(LevelGenerator.typeOf(n) != LevelType.FINALE)
        }
        assertEquals(LevelType.FINALE, LevelGenerator.typeOf(LevelConstants.LEVEL_COUNT))
    }

    @Test
    fun laterLevelsStayFair() {
        for (n in 501..LevelConstants.LEVEL_COUNT) {
            val spec = LevelGenerator.plan(n).spec
            assertTrue("poziom $n: combo ${spec.comboTarget}", spec.comboTarget <= LevelConstants.MAX_COMBO)
            // Łódź (72 j/s) musi być szybsza od rekina i od prądu.
            assertTrue("poziom $n: potwory ${spec.enemySpeed}", spec.enemySpeed < 1.45f)
            assertTrue("poziom $n: prąd", kotlin.math.abs(spec.currentX) + kotlin.math.abs(spec.currentY) < 30f)
        }
    }

    @Test
    fun grandFinaleIsSpecial() {
        val plan = LevelGenerator.plan(LevelConstants.LEVEL_COUNT)
        assertEquals(LevelType.FINALE, plan.spec.type)
        assertTrue(LevelGenerator.isFinale(LevelConstants.LEVEL_COUNT))
        assertEquals(SecondGoal.NO_DAMAGE, plan.spec.secondGoal)
        assertEquals(ThirdGoal.ALL_GOLDEN, plan.spec.thirdGoal)
        // Najdłuższy poziom gry: każdy etap w innym świecie.
        assertEquals(Worlds.FINALE_STAGE_WORLDS.size, plan.stages.size)
        assertEquals(Worlds.FINALE_STAGE_WORLDS.size, Worlds.FINALE_STAGE_WORLDS.distinct().size)
        val longest = (1 until LevelConstants.LEVEL_COUNT).maxOf { LevelGenerator.plan(it).stages.size }
        assertTrue(plan.stages.size >= longest)
        // Wszystkie potwory oceanu i złote ryby w każdym etapie.
        assertEquals(EnemyKind.entries.toSet(), plan.stages.flatMap { it.enemies }.toSet())
        assertTrue(plan.stages.all { it.golden > 0 })
        assertTrue(plan.stages.all { it.common + it.blue >= it.quota })
        assertTrue(plan.stages.any { it.wideBanks })
        // Plan finału jest stały – za każdym razem ta sama plansza.
        val again = LevelGenerator.generate(LevelConstants.LEVEL_COUNT)
        val first = all.last()
        assertEquals(first.grid.height, again.grid.height)
        assertEquals(first.stages.map { it.enemies.size }, again.stages.map { it.enemies.size })
    }

    /**
     * Rozszerzenie gry do 1000 poziomów nie mogło zmienić pierwszych 500: gracze mają
     * na nich rekordy i gwiazdki. Odcisk policzony na wersji sprzed rozszerzenia.
     */
    @Test
    fun firstVoyageLevelsAreUnchanged() {
        assertEquals(FIRST_VOYAGE_FINGERPRINT, fingerprint(1..LevelConstants.FIRST_VOYAGE_LEVELS))
    }

    /**
     * Widmokuda (wersja 2.3) zmieniła tylko skład potworów: plansze, ryby, czasy i cele są te same co
     * w wersji 2.2 (odcisk policzony na kodzie sprzed widmokudy), a poziomy 1–19 są identyczne w całości.
     */
    @Test
    fun stalkerChangesOnlyMonsters() {
        assertEquals(CAMPAIGN_WITHOUT_MONSTERS, fingerprint(1..LevelConstants.LEVEL_COUNT, monsters = false))
        assertEquals(BEFORE_STALKER, fingerprint(1 until 20))
    }

    private fun fingerprint(range: IntRange, monsters: Boolean = true): String {
        val md = MessageDigest.getInstance("SHA-256")
        fun put(v: Any) = md.update(v.toString().toByteArray())
        for (n in range) {
            val plan = LevelGenerator.plan(n)
            val sp = plan.spec
            put("L$n|${sp.type}|${sp.seed}|${sp.secondGoal}|${sp.thirdGoal}|${sp.parSeconds}|${sp.timeLimitSeconds}|")
            put("${sp.scoreTarget}|${sp.comboTarget}|${sp.enemySpeed.toBits()}|${sp.currentX.toBits()}|${sp.currentY.toBits()}|${sp.world}|")
            for (st in plan.stages) {
                val enemies = if (monsters) st.enemies.toString() else ""
                put("S${st.rows},${st.quota},${st.common},${st.blue},${st.golden},$enemies,${st.rocks},${st.islands},${st.doorCenter},${st.wideBanks}|")
            }
            val layout = all[n - 1]
            val g = layout.grid
            for (r in 0 until g.height) for (c in 0 until g.width) put(g[c, r].ordinal)
            put(g.waves.joinToString("") { if (it) "1" else "0" })
            for (st in layout.stages) {
                put("G${st.gateRow},${st.topRow},${st.bottomRow},${st.doorCols},${st.quota},${st.reachable.contentHashCode()}|")
                for (f in st.fish) put("f${f.kind}${f.col},${f.row}")
                for (f in st.golden) put("g${f.delaySeconds}${f.col},${f.row}")
                if (monsters) for (e in st.enemies) put("e${e.kind}${e.col},${e.row}")
            }
            put("c${layout.crabs}s${layout.startCol},${layout.startRow}")
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        /** Od wersji 2.3 z widmokudą (wcześniej 8a85a72a…16271). */
        const val FIRST_VOYAGE_FINGERPRINT = "33717631853fa5788f46847684b5b0c3950e973250bf43b9eea5fb5852786cc4"
        const val CAMPAIGN_WITHOUT_MONSTERS = "3aafc4ac7255b99d10ded34cce10aa25366e34688bfe6528976dad4a535d40ed"
        const val BEFORE_STALKER = "7e6f4cd641ed149535ccd91a3e5e0ff808294409f12e8bf09c3c25ea764bb433"
    }
}
