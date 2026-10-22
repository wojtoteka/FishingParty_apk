package wojtoteka.ovh.fishingparty.level

import org.json.JSONArray
import org.json.JSONObject
import wojtoteka.ovh.fishingparty.core.Rng
import wojtoteka.ovh.fishingparty.level.LevelConstants.FINISH_ROWS
import wojtoteka.ovh.fishingparty.level.LevelConstants.WIDTH
import java.security.MessageDigest
import kotlin.math.abs

/*
 * Poziom z kreatora (od wersji 2.4). W przeciwieństwie do poziomów z generatora wszystko jest
 * w nim zapisane wprost: kafelki każdego etapu, ryby, złote ryby, potwory (z kierunkiem startowym)
 * i kraby. Z tego opisu gra buduje planszę ([UserLevels.build]), a serwer rankingu liczy z niego
 * limity wyniku i trudność – oba według tych samych zasad i wyłącznie na liczbach całkowitych
 * (kopia na serwerze: src/userlevels.ts, zgodność pilnuje UserLevelExportTest).
 *
 * Wiersze etapu liczone są od góry (0 – tuż pod bramą etapu), kolumny od lewej (0..10).
 * Etap 0 to pierwszy etap – na samym dole planszy, tam gdzie startuje łódź.
 */

/** Ryba zwykła albo niebieska ([blue]). */
data class UserFish(val blue: Boolean, val col: Int, val row: Int)

/** Złota ryba – pojawia się [delay] sekund po wpłynięciu do etapu. */
data class UserGolden(val delay: Int, val col: Int, val row: Int)

/** Potwór; [dir] – kierunek na start (0 prawo, 1 dół, 2 lewo, 3 góra), -1 – losowy jak w zwykłych poziomach. */
data class UserEnemy(val kind: EnemyKind, val col: Int, val row: Int, val dir: Int = -1)

/** Krab na plaży – tylko dekoracja. */
data class UserCrab(val col: Int, val row: Int)

/**
 * Etap poziomu z kreatora.
 * @param door środkowa kolumna trzypolowych wrót bramy nad etapem
 * @param quota ile ryb trzeba złowić, żeby brama się otworzyła (0 – brama otwarta od razu)
 * @param theme paleta etapu: [UserLevels.SAME_THEME] – jak cały poziom, [Worlds.FINALE] – złota, 0..9 – palety światów
 * @param currentX prąd morski w etapie (jednostki świata na sekundę), w prawo dodatni
 * @param currentY prąd w dół dodatni (pod prąd)
 * @param cells kafelki wierszami od góry: [UserLevels.SEA], [UserLevels.SAND] (piasek w barwach etapu), piasek
 *   w kolorze innej palety ([UserLevels.sandCode]) albo [UserLevels.ROCK]
 */
data class UserStage(
    val rows: Int,
    val door: Int,
    val quota: Int,
    val theme: Int = UserLevels.SAME_THEME,
    val currentX: Int = 0,
    val currentY: Int = 0,
    val cells: ByteArray,
    val fish: List<UserFish> = emptyList(),
    val golden: List<UserGolden> = emptyList(),
    val enemies: List<UserEnemy> = emptyList(),
    val crabs: List<UserCrab> = emptyList(),
) {
    fun cell(col: Int, row: Int): Byte = if (col in 0 until WIDTH && row in 0 until rows) cells[row * WIDTH + col] else UserLevels.SAND

    val fishTotal: Int get() = fish.size + golden.size

    /** Ryby, które nie uciekają (złote znikają po chwili). */
    val steadyFish: Int get() = fish.size
}

/**
 * Cały poziom z kreatora.
 * @param theme paleta poziomu: [Worlds.FINALE] albo 0..9
 * @param speed tempo potworów w procentach (100 = jak na początku gry)
 * @param time limit czasu w sekundach (wyścig z czasem), 0 – bez limitu
 * @param combo cel combo, gdy trzecia gwiazdka to [ThirdGoal.COMBO]
 */
data class UserLevel(
    val title: String,
    val theme: Int,
    val speed: Int,
    val time: Int,
    val secondGoal: SecondGoal,
    val thirdGoal: ThirdGoal,
    val combo: Int,
    val stages: List<UserStage>,
)

/** Problem znaleziony w poziomie z kreatora; [error] – nie da się opublikować, inaczej tylko ostrzeżenie. */
class UserLevelProblem(val kind: Kind, val stage: Int, val col: Int = -1, val row: Int = -1) {
    enum class Kind(val error: Boolean) {
        /** Brama etapu jest odcięta od wejścia. */
        DOOR_UNREACHABLE(true),

        /** Za mało ryb, żeby otworzyć bramę. */
        QUOTA_TOO_HIGH(true),

        /** Brama otworzy się tylko ze złotymi rybami, które uciekają. */
        QUOTA_NEEDS_GOLDEN(false),

        /** Ryba w wodzie odciętej od reszty etapu – nie da się jej złowić. */
        FISH_UNREACHABLE(true),

        /** Potwór zamknięty w odciętej wodzie – nikomu nie zagraża (i nie liczy się do trudności). */
        ENEMY_TRAPPED(false),

        /** Cel „wszystkie złote ryby” bez złotych ryb. */
        NO_GOLDEN(true),

        /** Nazwa za krótka do publikacji. */
        TITLE_SHORT(true),
    }

    val error: Boolean get() = kind.error
}

object UserLevels {
    const val SEA: Byte = 0
    const val SAND: Byte = 1
    const val ROCK: Byte = 2

    /** Piasek w kolorze palety p ([Worlds.FINALE]..9) ma kod p + [SAND_SHIFT] (3..13) – etap może mieć kilka kolorów piasku. */
    private const val SAND_SHIFT = 4
    private const val FIRST_SAND_COLOR = Worlds.FINALE + SAND_SHIFT
    private const val LAST_SAND_COLOR = 9 + SAND_SHIFT

    /** Kod piasku w kolorze palety [palette]; [SAME_THEME] – piasek w barwach etapu ([SAND]). */
    fun sandCode(palette: Int): Byte = if (palette == SAME_THEME) SAND else (palette + SAND_SHIFT).toByte()

    fun isSand(code: Byte): Boolean = code == SAND || code in FIRST_SAND_COLOR..LAST_SAND_COLOR

    /** Paleta piasku z kodu pola: [SAME_THEME] – barwy etapu. */
    fun sandPalette(code: Byte): Int = if (code in FIRST_SAND_COLOR..LAST_SAND_COLOR) code - SAND_SHIFT else SAME_THEME

    /** Paleta etapu taka jak całego poziomu. */
    const val SAME_THEME = -2

    /** Numer „poziomu” w wyniku przejazdu – spoza kampanii (jak poziom dnia), nie trafia do rekordów. */
    const val LEVEL_NUMBER = -1

    const val FORMAT = 1
    const val MAX_STAGES = 7
    const val MIN_ROWS = 8
    const val MAX_ROWS = 32
    const val DEFAULT_ROWS = 16
    const val MAX_FISH = 40
    const val MAX_GOLDEN = 6
    const val MAX_ENEMIES = 16
    const val MAX_CRABS = 12
    const val MAX_QUOTA = 60
    const val MIN_SPEED = 60
    const val MAX_SPEED = 160
    const val MIN_TIME = 20
    const val MAX_TIME = 600
    const val MAX_CURRENT = 30
    const val MAX_DELAY = 60
    const val TITLE_MIN = 3
    const val TITLE_MAX = 24

    /** Łódź startuje na środku dolnej krawędzi (jak w poziomach z generatora). */
    const val START_COL = WIDTH / 2

    // ------------------------------------------------------------------ nowy poziom

    /** Pusty etap: piaszczyste brzegi po bokach i woda pośrodku. */
    fun blankStage(rows: Int = DEFAULT_ROWS, door: Int = START_COL, quota: Int = 1): UserStage {
        val cells = ByteArray(rows * WIDTH) { i ->
            val c = i % WIDTH
            if (c <= 1 || c >= WIDTH - 2) SAND else SEA
        }
        return UserStage(rows = rows, door = door, quota = quota, cells = cells)
    }

    /** Poziom na start kreatora: jeden etap z kilkoma rybami. */
    fun starter(title: String): UserLevel {
        val stage = blankStage(quota = 3).copy(
            fish = listOf(UserFish(false, 3, 4), UserFish(false, 7, 7), UserFish(true, 5, 10), UserFish(false, 4, 12)),
        )
        return UserLevel(title, theme = 0, speed = 100, time = 0, SecondGoal.ALL_FISH, ThirdGoal.PAR_TIME, combo = 3, stages = listOf(stage))
    }

    // ------------------------------------------------------------------ plansza

    /** Złożona plansza: siatka i wiersze bram (indeks jak etapy). */
    class Assembled(val grid: TileGrid, val gateRows: IntArray, val bottomRow: Int, val startCol: Int) {
        fun top(k: Int) = gateRows[k] + 1
    }

    private fun tileOf(code: Byte): Tile = when {
        isSand(code) -> Tile.SAND
        code == ROCK -> Tile.ROCK
        else -> Tile.SEA
    }

    private fun codeOf(tile: Tile, sand: Int): Byte = when (tile) {
        Tile.SAND -> sandCode(sand)
        Tile.ROCK -> ROCK
        else -> SEA
    }

    /**
     * Składa planszę jak [LevelGenerator.generate]: mur, port na mecie, etapy od góry, bramy z wrotami.
     * Wokół wrót i startu zawsze jest woda, a piasek bez pasującego kafelka znika ([LevelGenerator.fixSand]).
     * Pole bramy, któremu po tej poprawce zabrakło piasku nad i pod sobą, wraca do muru – brama nie ma dziur.
     * Piasek w bramie ma kolor piasku pod nią (pierwszego rzędu etapu).
     */
    fun assemble(stages: List<UserStage>, startCol: Int = START_COL): Assembled {
        val n = stages.size
        val gateRows = IntArray(n)
        var cursor = FINISH_ROWS
        for (k in n - 1 downTo 0) {
            gateRows[k] = cursor
            cursor += stages[k].rows + 1
        }
        val bottomRow = cursor
        val grid = TileGrid(WIDTH, bottomRow + 1)
        for (c in 0 until WIDTH) {
            grid[c, 0] = Tile.WALL
            grid[c, bottomRow] = Tile.WALL_BOTTOM
        }
        for (r in 1 until FINISH_ROWS) {
            grid[0, r] = Tile.SAND
            grid[1, r] = Tile.SAND
            grid[WIDTH - 1, r] = Tile.SAND
            grid[WIDTH - 2, r] = Tile.SAND
        }
        for ((k, stage) in stages.withIndex()) {
            val top = gateRows[k] + 1
            for (r in 0 until stage.rows) for (c in 0 until WIDTH) {
                val code = stage.cell(c, r)
                grid[c, top + r] = tileOf(code)
                grid.setSandColor(c, top + r, sandPalette(code))
            }
        }
        for ((k, stage) in stages.withIndex()) {
            val g = gateRows[k]
            val doors = doorCols(stage.door)
            for (c in 0 until WIDTH) {
                grid[c, g] = when {
                    c in doors -> Tile.DOOR
                    grid[c, g - 1] == Tile.SAND && grid[c, g + 1] == Tile.SAND -> Tile.SAND
                    else -> Tile.WALL
                }
                grid.setSandColor(c, g, grid.sandColor(c, g + 1))
            }
        }
        for ((k, stage) in stages.withIndex()) {
            val g = gateRows[k]
            for (c in doorCols(stage.door)) for (dr in DOOR_CLEAR) {
                val r = g + dr
                if (r in 1 until bottomRow && grid[c, r] != Tile.WALL && grid[c, r] != Tile.DOOR) grid[c, r] = Tile.SEA
            }
        }
        for (r in bottomRow - START_ROWS until bottomRow) for (c in startCol - 1..startCol + 1) {
            if (c in 0 until WIDTH) grid[c, r] = Tile.SEA
        }
        LevelGenerator.fixSand(grid)
        for ((k, stage) in stages.withIndex()) {
            val g = gateRows[k]
            val doors = doorCols(stage.door)
            for (c in 0 until WIDTH) {
                if (c in doors) continue
                if (grid[c, g] != Tile.WALL && !(grid[c, g - 1] == Tile.SAND && grid[c, g + 1] == Tile.SAND)) grid[c, g] = Tile.WALL
            }
        }
        return Assembled(grid, gateRows, bottomRow, startCol)
    }

    fun doorCols(door: Int): IntRange = (door - 1)..(door + 1)

    /** Wiersze nad i pod bramą, w których przy wrotach jest zawsze woda. */
    private val DOOR_CLEAR = intArrayOf(-2, -1, 1, 2)
    private const val START_ROWS = 3

    /**
     * Czy pole etapu [k] jest zawsze wodą (przy wrotach albo na starcie) – kreator nie pozwala tam
     * malować i zaznacza je na planszy.
     */
    fun locked(level: UserLevel, k: Int, col: Int, row: Int): Boolean {
        val stage = level.stages[k]
        if (row <= 1 && col in doorCols(stage.door)) return true
        if (k == 0) return row >= stage.rows - START_ROWS && col in START_COL - 1..START_COL + 1
        return row >= stage.rows - 2 && col in doorCols(level.stages[k - 1].door)
    }

    /**
     * Poziom po tych samych poprawkach, które zrobi gra przy budowaniu planszy: kafelki takie, jak będą
     * w grze, a ryby i potwory tylko na wodzie (kraby – na piasku), po jednej postaci na polu.
     * Kreator pokazuje zawsze poziom po poprawkach, a serwer przyjmuje tylko taki.
     */
    fun normalize(level: UserLevel): UserLevel {
        val a = assemble(level.stages)
        val stages = level.stages.mapIndexed { k, stage ->
            val top = a.top(k)
            val cells = ByteArray(stage.rows * WIDTH) { i -> (top + i / WIDTH).let { r -> codeOf(a.grid[i % WIDTH, r], a.grid.sandColor(i % WIDTH, r)) } }
            val taken = HashSet<Int>()
            fun free(col: Int, row: Int): Boolean =
                col in 0 until WIDTH && row in 0 until stage.rows && cells[row * WIDTH + col] == SEA && taken.add(row * WIDTH + col)
            val fish = stage.fish.filter { free(it.col, it.row) }.take(MAX_FISH)
            val golden = stage.golden.filter { free(it.col, it.row) }.take(MAX_GOLDEN)
                .map { it.copy(delay = it.delay.coerceIn(0, MAX_DELAY)) }
            val enemies = stage.enemies.filter { free(it.col, it.row) }.take(MAX_ENEMIES)
                .map { it.copy(dir = validDir(it.kind, it.dir)) }
            val crabCells = HashSet<Int>()
            val crabs = stage.crabs.filter {
                it.col in 0 until WIDTH && it.row in 0 until stage.rows && isSand(cells[it.row * WIDTH + it.col]) && crabCells.add(it.row * WIDTH + it.col)
            }.take(MAX_CRABS)
            stage.copy(
                cells = cells,
                fish = fish,
                golden = golden,
                enemies = enemies,
                crabs = crabs,
                quota = stage.quota.coerceIn(0, MAX_QUOTA),
            )
        }
        return level.copy(stages = stages)
    }

    /** Kierunek startowy pasujący do potwora: piranie pływają tylko w poziomie albo tylko w pionie. */
    fun validDir(kind: EnemyKind, dir: Int): Int = when {
        dir !in 0..3 -> -1
        (kind == EnemyKind.PIRANHA || kind == EnemyKind.RED_PIRANHA) && dir % 2 == 1 -> -1
        kind == EnemyKind.PIRANHA_VERTICAL && dir % 2 == 0 -> -1
        else -> dir
    }

    /** Plan (do limitów wyniku i gwiazdek) i plansza gotowa do gry. */
    class Built(val plan: LevelPlan, val layout: LevelLayout)

    /**
     * Buduje poziom do gry. [fromStage] > 0 – test w kreatorze od wybranego etapu: łódź startuje
     * pod wrotami poprzedniego etapu, a wcześniejsze etapy są pominięte.
     */
    fun build(level: UserLevel, fromStage: Int = 0): Built {
        val stages = level.stages.drop(fromStage)
        val startCol = if (fromStage == 0) START_COL else level.stages[fromStage - 1].door
        val a = assemble(stages, startCol)
        val grid = a.grid
        val waveRng = Rng(Rng.mix(seedOf(level), 5))
        for (r in 0 until grid.height) for (c in 0 until WIDTH) {
            if (grid[c, r] == Tile.SEA && waveRng.chance(0.14f)) grid.waves[r * WIDTH + c] = true
        }
        val plans = stages.map { s ->
            StagePlan(
                rows = s.rows,
                quota = s.quota,
                common = s.fish.count { !it.blue },
                blue = s.fish.count { it.blue },
                golden = s.golden.size,
                enemies = s.enemies.map { it.kind },
                rocks = 0,
                islands = 0,
                doorCenter = s.door,
                wideBanks = false,
            )
        }
        val layouts = stages.mapIndexed { k, s ->
            val top = a.top(k)
            val bottom = top + s.rows - 1
            val entry = if (k == 0) startCol else stages[k - 1].door
            StageLayout(
                index = k,
                gateRow = a.gateRows[k],
                topRow = top,
                bottomRow = bottom,
                doorCols = doorCols(s.door),
                quota = s.quota,
                fish = s.fish.map { FishSpawn(if (it.blue) FishKind.BLUE else FishKind.COMMON, it.col, top + it.row) },
                golden = s.golden.map { GoldenSpawn(it.delay.toFloat(), it.col, top + it.row) },
                enemies = s.enemies.map { EnemySpawn(it.kind, it.col, top + it.row, it.dir) },
                reachable = LevelGenerator.reachableCells(grid, entry, bottom, top, bottom),
                currentX = s.currentX.toFloat(),
                currentY = s.currentY.toFloat(),
            )
        }
        val crabs = stages.flatMapIndexed { k, s -> s.crabs.map { it.col to a.top(k) + it.row } }
        val rowWorlds = if (stages.any { it.theme != SAME_THEME }) {
            IntArray(grid.height) { level.theme }.also { rows ->
                for ((k, s) in stages.withIndex()) {
                    val theme = themeOf(level, s)
                    for (r in a.gateRows[k]..a.top(k) + s.rows - 1) rows[r] = theme
                }
                rows[rows.lastIndex] = themeOf(level, stages[0])
            }
        } else {
            null
        }
        val spec = spec(level, plans)
        return Built(
            LevelPlan(spec, plans),
            LevelLayout(spec, grid, layouts, startCol, a.bottomRow - 1, crabs, rowWorlds),
        )
    }

    fun themeOf(level: UserLevel, stage: UserStage): Int = if (stage.theme == SAME_THEME) level.theme else stage.theme

    private fun spec(level: UserLevel, stages: List<StagePlan>): LevelSpec {
        val par = SeedLevels.parSeconds(stages)
        val regularPoints = stages.sumOf { it.common * FishKind.COMMON.points + it.blue * FishKind.BLUE.points }
        val goldenTotal = stages.sumOf { it.golden }
        val scoreTenfold = regularPoints * 16 + goldenTotal * FishKind.GOLDEN.points * 5 + stages.size * LevelConstants.STAGE_BONUS * 10
        return LevelSpec(
            number = LEVEL_NUMBER,
            world = level.theme,
            type = LevelType.CUSTOM,
            seed = seedOf(level),
            secondGoal = level.secondGoal,
            thirdGoal = level.thirdGoal,
            parSeconds = par,
            timeLimitSeconds = level.time,
            scoreTarget = (scoreTenfold + 50) / 100 * 10,
            comboTarget = level.combo.coerceIn(2, LevelConstants.MAX_COMBO),
            enemySpeed = level.speed / 100f,
            currentX = 0f,
            currentY = 0f,
        )
    }

    /** Seed poziomu (fale na wodzie, losowe ruchy potworów) – z treści, więc każdy gracz dostaje ten sam. */
    fun seedOf(level: UserLevel): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest(encode(level).toByteArray())
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (digest[i].toLong() and 0xFF)
        return v
    }

    /** Skrót treści poziomu (hex) – po zmianie poziomu weryfikacja przestaje obowiązywać. */
    fun hashOf(level: UserLevel): String =
        MessageDigest.getInstance("SHA-256").digest(encode(level).toByteArray()).joinToString("") { "%02x".format(it) }

    // ------------------------------------------------------------------ sprawdzenie

    /** Problemy poziomu (po [normalize]): błędy blokują publikację, ostrzeżenia tylko podpowiadają. */
    fun check(level: UserLevel): List<UserLevelProblem> {
        val out = ArrayList<UserLevelProblem>()
        if (normalizeTitle(level.title).length < TITLE_MIN) out += UserLevelProblem(UserLevelProblem.Kind.TITLE_SHORT, -1)
        val built = build(level)
        for ((k, stage) in level.stages.withIndex()) {
            val layout = built.layout.stages[k]
            val reach = layout.reachable.toHashSet()
            val top = layout.topRow
            if ((top * WIDTH + stage.door) !in reach) out += UserLevelProblem(UserLevelProblem.Kind.DOOR_UNREACHABLE, k, stage.door, 0)
            if (stage.quota > stage.fishTotal) {
                out += UserLevelProblem(UserLevelProblem.Kind.QUOTA_TOO_HIGH, k)
            } else if (stage.quota > stage.steadyFish) {
                out += UserLevelProblem(UserLevelProblem.Kind.QUOTA_NEEDS_GOLDEN, k)
            }
            for (f in stage.fish) if (((top + f.row) * WIDTH + f.col) !in reach) out += UserLevelProblem(UserLevelProblem.Kind.FISH_UNREACHABLE, k, f.col, f.row)
            for (g in stage.golden) if (((top + g.row) * WIDTH + g.col) !in reach) out += UserLevelProblem(UserLevelProblem.Kind.FISH_UNREACHABLE, k, g.col, g.row)
            for (e in stage.enemies) if (((top + e.row) * WIDTH + e.col) !in reach) out += UserLevelProblem(UserLevelProblem.Kind.ENEMY_TRAPPED, k, e.col, e.row)
        }
        if (level.thirdGoal == ThirdGoal.ALL_GOLDEN && level.stages.none { it.golden.isNotEmpty() }) {
            out += UserLevelProblem(UserLevelProblem.Kind.NO_GOLDEN, -1)
        }
        return out
    }

    /** Nazwa poziomu bez zbędnych spacji (jak nick gracza). */
    fun normalizeTitle(title: String): String = title.trim().replace(Regex("\\s+"), " ")

    /** Dozwolone znaki nazwy: litery, cyfry, spacja i kilka znaków przestankowych. */
    fun titleAllowed(title: String): Boolean = title.all { it.isLetterOrDigit() || it in TITLE_CHARS }

    private const val TITLE_CHARS = " _.,-!?'():&#"

    // ------------------------------------------------------------------ zapis (JSON)

    private val ENEMY_IDS: Map<EnemyKind, String> = EnemyKind.entries.associateWith { it.name.lowercase() }
    private val ENEMY_BY_ID: Map<String, EnemyKind> = ENEMY_IDS.entries.associate { (k, v) -> v to k }

    private val SECOND_IDS = mapOf(SecondGoal.NO_DAMAGE to "no_damage", SecondGoal.ALL_FISH to "all_fish")
    private val THIRD_IDS = mapOf(
        ThirdGoal.PAR_TIME to "par_time",
        ThirdGoal.SCORE to "score",
        ThirdGoal.ALL_GOLDEN to "all_golden",
        ThirdGoal.COMBO to "combo",
    )

    fun enemyId(kind: EnemyKind): String = ENEMY_IDS.getValue(kind)

    /**
     * Zapis w stałej kolejności pól (ten sam tekst daje ten sam skrót na telefonie i na serwerze).
     * Kafelki etapu to jeden napis: „.” woda, „s” piasek, „r” skała, „0”–„9” piasek w kolorze palety świata,
     * „f” – w złotej palecie finału.
     */
    fun encode(level: UserLevel): String = buildString {
        append("{\"v\":").append(FORMAT)
        append(",\"title\":").appendJson(level.title)
        append(",\"theme\":").append(level.theme)
        append(",\"speed\":").append(level.speed)
        append(",\"time\":").append(level.time)
        append(",\"goal2\":\"").append(SECOND_IDS.getValue(level.secondGoal)).append('"')
        append(",\"goal3\":\"").append(THIRD_IDS.getValue(level.thirdGoal)).append('"')
        append(",\"combo\":").append(level.combo)
        append(",\"stages\":[")
        for ((i, s) in level.stages.withIndex()) {
            if (i > 0) append(',')
            append("{\"rows\":").append(s.rows)
            append(",\"door\":").append(s.door)
            append(",\"quota\":").append(s.quota)
            append(",\"theme\":").append(s.theme)
            append(",\"cx\":").append(s.currentX)
            append(",\"cy\":").append(s.currentY)
            append(",\"cells\":\"")
            for (b in s.cells) append(cellChar(b))
            append('"')
            append(",\"fish\":[").append(s.fish.joinToString(",") { "[${if (it.blue) 1 else 0},${it.col},${it.row}]" }).append(']')
            append(",\"golden\":[").append(s.golden.joinToString(",") { "[${it.delay},${it.col},${it.row}]" }).append(']')
            append(",\"enemies\":[").append(s.enemies.joinToString(",") { "[\"${enemyId(it.kind)}\",${it.col},${it.row},${it.dir}]" }).append(']')
            append(",\"crabs\":[").append(s.crabs.joinToString(",") { "[${it.col},${it.row}]" }).append(']')
            append('}')
        }
        append("]}")
    }

    private fun cellChar(code: Byte): Char = when {
        code == SAND -> 's'
        code == ROCK -> 'r'
        isSand(code) -> sandPalette(code).let { if (it == Worlds.FINALE) 'f' else '0' + it }
        else -> '.'
    }

    private fun StringBuilder.appendJson(text: String): StringBuilder {
        append('"')
        for (ch in text) {
            when {
                ch == '"' -> append("\\\"")
                ch == '\\' -> append("\\\\")
                ch < ' ' -> append("\\u%04x".format(ch.code))
                else -> append(ch)
            }
        }
        return append('"')
    }

    /** Odczyt poziomu z zapisu; null, gdy zapis jest uszkodzony albo wykracza poza limity kreatora. */
    fun decode(text: String): UserLevel? = try {
        decode(JSONObject(text))
    } catch (_: Exception) {
        null
    }

    fun decode(o: JSONObject): UserLevel? = try {
        require(o.getInt("v") == FORMAT)
        val stages = o.getJSONArray("stages").let { arr -> List(arr.length()) { decodeStage(arr.getJSONObject(it)) } }
        require(stages.size in 1..MAX_STAGES)
        val theme = o.getInt("theme")
        require(theme in Worlds.FINALE..9)
        val speed = o.getInt("speed")
        require(speed in MIN_SPEED..MAX_SPEED)
        val time = o.getInt("time")
        require(time == 0 || time in MIN_TIME..MAX_TIME)
        val title = o.getString("title")
        require(title.length <= TITLE_MAX)
        UserLevel(
            title = title,
            theme = theme,
            speed = speed,
            time = time,
            secondGoal = SECOND_IDS.entries.first { it.value == o.getString("goal2") }.key,
            thirdGoal = THIRD_IDS.entries.first { it.value == o.getString("goal3") }.key,
            combo = o.getInt("combo").also { require(it in 2..LevelConstants.MAX_COMBO) },
            stages = stages,
        )
    } catch (_: Exception) {
        null
    }

    private fun decodeStage(o: JSONObject): UserStage {
        val rows = o.getInt("rows")
        require(rows in MIN_ROWS..MAX_ROWS)
        val text = o.getString("cells")
        require(text.length == rows * WIDTH)
        val cells = ByteArray(text.length) { i ->
            when (val ch = text[i]) {
                '.' -> SEA
                's' -> SAND
                'r' -> ROCK
                'f' -> sandCode(Worlds.FINALE)
                in '0'..'9' -> sandCode(ch - '0')
                else -> throw IllegalArgumentException("pole")
            }
        }
        fun coords(a: JSONArray, from: Int): Pair<Int, Int> {
            val col = a.getInt(from)
            val row = a.getInt(from + 1)
            require(col in 0 until WIDTH && row in 0 until rows)
            return col to row
        }
        val fish = o.getJSONArray("fish").let { arr ->
            List(arr.length()) { i ->
                val a = arr.getJSONArray(i)
                val (c, r) = coords(a, 1)
                UserFish(a.getInt(0) == 1, c, r)
            }
        }
        val golden = o.getJSONArray("golden").let { arr ->
            List(arr.length()) { i ->
                val a = arr.getJSONArray(i)
                val (c, r) = coords(a, 1)
                UserGolden(a.getInt(0).also { require(it in 0..MAX_DELAY) }, c, r)
            }
        }
        val enemies = o.getJSONArray("enemies").let { arr ->
            List(arr.length()) { i ->
                val a = arr.getJSONArray(i)
                val (c, r) = coords(a, 1)
                UserEnemy(ENEMY_BY_ID.getValue(a.getString(0)), c, r, a.getInt(3))
            }
        }
        val crabs = o.getJSONArray("crabs").let { arr ->
            List(arr.length()) { i -> coords(arr.getJSONArray(i), 0).let { (c, r) -> UserCrab(c, r) } }
        }
        require(fish.size <= MAX_FISH && golden.size <= MAX_GOLDEN && enemies.size <= MAX_ENEMIES && crabs.size <= MAX_CRABS)
        val door = o.getInt("door")
        require(door in 1..WIDTH - 2)
        val quota = o.getInt("quota")
        require(quota in 0..MAX_QUOTA)
        val theme = o.getInt("theme")
        require(theme == SAME_THEME || theme in Worlds.FINALE..9)
        val cx = o.getInt("cx")
        val cy = o.getInt("cy")
        require(abs(cx) <= MAX_CURRENT && abs(cy) <= MAX_CURRENT)
        return UserStage(rows, door, quota, theme, cx, cy, cells, fish, golden, enemies, crabs)
    }
}

/**
 * Trudność poziomu z kreatora liczona z jego treści (tak samo liczy ją serwer rankingu). Liczą się tylko
 * potwory, które mogą dopłynąć do łodzi: ich „koszt” (jak w budżecie generatora) razy tempo na pole wody
 * etapu – najgroźniejszy etap waży najwięcej. Dochodzą widmokudy, liczba etapów, prąd i ciasny limit czasu.
 *
 * Serwer bierze z tego stopień trudności i punkty rankingu za ukończenie, a gdy poziom zagra już kilku
 * graczy – obniża stopień, jeśli w praktyce przechodzi go większość (nie da się „nabić” punktów poziomem,
 * który tylko wygląda na trudny).
 */
object UserLevelRating {
    /** Stopnie: 0 spacer (bez potworów), 1 łatwy … 6 demon. */
    const val TIERS = 7

    /** Punkty rankingu za ukończenie poziomu danego stopnia (liczy serwer – tu do podglądu). */
    val POINTS = intArrayOf(5, 25, 50, 100, 200, 350, 500)

    /** Progi trudności kolejnych stopni (od łatwego). */
    private val THRESHOLDS = intArrayOf(15, 30, 50, 75, 110)

    private const val MIN_AREA = 20

    class Estimate(val difficulty: Int, val tier: Int)

    fun estimate(level: UserLevel): Estimate {
        val built = UserLevels.build(level)
        val n = level.stages.size
        var maxDanger = 0
        var sumDanger = 0
        var stalkers = 0
        var threatTotal = 0
        for (layout in built.layout.stages) {
            val reach = layout.reachable.toHashSet()
            var threat = 0
            for (e in layout.enemies) {
                if ((e.row * LevelConstants.WIDTH + e.col) !in reach) continue
                threat += e.kind.costTenths
                if (e.kind == EnemyKind.STALKER) stalkers++
            }
            val danger = threat * level.speed / (maxOf(layout.reachable.size, MIN_AREA) * 2)
            maxDanger = maxOf(maxDanger, danger)
            sumDanger += danger
            threatTotal += threat
        }
        var d = (maxDanger * 6 * n + sumDanger * 4) / (10 * n)
        d += (n - 1) * 3
        d += minOf(stalkers * 6, 30)
        d += level.stages.maxOf { abs(it.currentX) + abs(it.currentY) } / 3
        if (level.time > 0) {
            val ratio = level.time * 100 / maxOf(built.plan.spec.parSeconds, 1)
            d += maxOf(0, 160 - ratio) / 4
        }
        val tier = if (threatTotal == 0 && level.time == 0) 0 else 1 + THRESHOLDS.count { d >= it }
        return Estimate(d, tier)
    }
}
