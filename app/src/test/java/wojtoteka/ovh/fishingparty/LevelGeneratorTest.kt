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
        assertEquals(LevelType.entries.toSet() - LevelType.CUSTOM, types)
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
        // Od wersji 2.4 najtrudniejszy poziom kampanii: więcej potworów na etap niż u każdego bossa i nie wolniejsze.
        val bosses = (1 until LevelConstants.LEVEL_COUNT).filter { LevelGenerator.isBoss(it) }.map { LevelGenerator.plan(it) }
        for (boss in bosses) {
            assertTrue("boss ${boss.spec.number}", boss.enemyCount * plan.stages.size < plan.enemyCount * boss.stages.size)
            assertTrue("boss ${boss.spec.number}", boss.spec.enemySpeed <= plan.spec.enemySpeed + 0.01f)
        }
        // Plan finału jest stały – za każdym razem ta sama plansza.
        val again = LevelGenerator.generate(LevelConstants.LEVEL_COUNT)
        val first = all.last()
        assertEquals(first.grid.height, again.grid.height)
        assertEquals(first.stages.map { it.enemies.size }, again.stages.map { it.enemies.size })
    }

    /**
     * Od wersji 2.4 pierwszy świat przedstawia po kolei wszystkie potwory – każdego przez dwa poziomy
     * (w każdym etapie, widmokudę w drugim) – i dopiero potem zaczynają się łatwe poziomy.
     */
    @Test
    fun monstersAreIntroducedInFirstWorld() {
        for ((i, kind) in EnemyKind.entries.withIndex()) assertEquals(kind.name, 2 + i * EnemyKind.INTRO_LEVELS, kind.firstLevel)
        assertEquals(EnemyKind.STALKER.firstLevel + EnemyKind.INTRO_LEVELS - 1, LevelGenerator.INTRO_END)
        assertTrue(LevelGenerator.INTRO_END < LevelConstants.LEVELS_PER_WORLD)
        for (n in 2..LevelGenerator.INTRO_END) {
            val plan = LevelGenerator.plan(n)
            for (kind in plan.stages.flatMap { it.enemies }) assertTrue("poziom $n: $kind za wcześnie", kind.firstLevel <= n)
            val newcomer = EnemyKind.entries.last { it.firstLevel <= n }
            if (newcomer == EnemyKind.STALKER) {
                assertTrue("poziom $n: brak widmokudy", EnemyKind.STALKER in plan.stages[1].enemies)
            } else {
                for (stage in plan.stages) assertTrue("poziom $n: brak $newcomer", newcomer in stage.enemies)
            }
        }
    }

    /** Po zapoznaniu z potworami: łatwe poziomy, potem normalne, a pod koniec kampanii trudne. */
    @Test
    fun difficultyRisesFromEasyToHard() {
        val worldStarts = (0 until LevelConstants.WORLD_COUNT).map { LevelGenerator.difficulty(Worlds.firstLevel(it)) }
        assertEquals(worldStarts.sorted(), worldStarts)
        assertTrue(LevelGenerator.difficulty(LevelGenerator.INTRO_END + 1) in 0.15f..0.3f)
        assertTrue(LevelGenerator.difficulty(250) in 0.4f..0.7f)
        assertTrue(LevelGenerator.difficulty(LevelConstants.LEVEL_COUNT - 1) > 1.2f)
    }

    /**
     * Nowa krzywa trudności (wersja 2.4) zmieniła tylko pierwszą wyprawę – druga (501–999) jest taka sama
     * jak wcześniej (odcisk policzony na kodzie wersji 2.3), więc gracze nie tracą tam rekordów.
     * Wielki Finał od 2.4 jest trudniejszy – pilnuje go osobny odcisk.
     */
    @Test
    fun secondVoyageIsUnchanged() {
        assertEquals(SECOND_VOYAGE_FINGERPRINT, fingerprint(LevelConstants.FIRST_VOYAGE_LEVELS + 1 until LevelConstants.LEVEL_COUNT))
        assertEquals(FINALE_FINGERPRINT, fingerprint(LevelConstants.LEVEL_COUNT..LevelConstants.LEVEL_COUNT))
    }

    /**
     * Pilnuje, żeby poziomy 1–500 nie zmieniły się przez przypadek: każda zmiana to nowe plansze dla graczy
     * i nowe limity na serwerze rankingu (LevelLimitsExportTest, a stare limity zostają tam dla starszych wersji gry).
     */
    @Test
    fun firstVoyageIsStable() {
        assertEquals(FIRST_VOYAGE_FINGERPRINT, fingerprint(1..LevelConstants.FIRST_VOYAGE_LEVELS))
    }

    private fun fingerprint(range: IntRange): String {
        val md = MessageDigest.getInstance("SHA-256")
        fun put(v: Any) = md.update(v.toString().toByteArray())
        for (n in range) {
            val plan = LevelGenerator.plan(n)
            val sp = plan.spec
            put("L$n|${sp.type}|${sp.seed}|${sp.secondGoal}|${sp.thirdGoal}|${sp.parSeconds}|${sp.timeLimitSeconds}|")
            put("${sp.scoreTarget}|${sp.comboTarget}|${sp.enemySpeed.toBits()}|${sp.currentX.toBits()}|${sp.currentY.toBits()}|${sp.world}|")
            for (st in plan.stages) {
                put("S${st.rows},${st.quota},${st.common},${st.blue},${st.golden},${st.enemies},${st.rocks},${st.islands},${st.doorCenter},${st.wideBanks}|")
            }
            val layout = all[n - 1]
            val g = layout.grid
            for (r in 0 until g.height) for (c in 0 until g.width) put(g[c, r].ordinal)
            put(g.waves.joinToString("") { if (it) "1" else "0" })
            for (st in layout.stages) {
                put("G${st.gateRow},${st.topRow},${st.bottomRow},${st.doorCols},${st.quota},${st.reachable.contentHashCode()}|")
                for (f in st.fish) put("f${f.kind}${f.col},${f.row}")
                for (f in st.golden) put("g${f.delaySeconds}${f.col},${f.row}")
                for (e in st.enemies) put("e${e.kind}${e.col},${e.row}")
            }
            put("c${layout.crabs}s${layout.startCol},${layout.startRow}")
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        /** Wersja 2.4: zapoznanie z potworami w pierwszym świecie, potem łatwe i normalne poziomy. */
        const val FIRST_VOYAGE_FINGERPRINT = "7b72333e981dcbe0c62d83e9842a45d83e8610ff232fbe51f0cdbdc12414e676"
        /** Poziomy 501–999 od wersji 2.3 (z widmokudą). */
        const val SECOND_VOYAGE_FINGERPRINT = "2254eb7a19b80089b109ce0d0fdee9268a0d226967aa9d7657a776a159117a2f"
        /** Wielki Finał od wersji 2.4 (więcej potworów). */
        const val FINALE_FINGERPRINT = "8eddad000aafaddba34637fd51d1960206bde7e6c2bfc4dcce13d31490df05f3"
    }
}
