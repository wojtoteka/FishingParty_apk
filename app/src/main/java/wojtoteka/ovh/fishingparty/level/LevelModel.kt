package wojtoteka.ovh.fishingparty.level

/** Rodzaj pola siatki poziomu. */
enum class Tile {
    SEA,
    SAND,
    ROCK,

    /** Mur bramy (kamień z wodą u dołu). */
    WALL,

    /** Mur zamykający poziom od dołu (rysowany odwrócony – woda u góry). */
    WALL_BOTTOM,

    /** Wrota bramy – przepuszczają łódź tylko po otwarciu. */
    DOOR,
}

/** Prostokątna siatka pól. Wiersz 0 to sama góra poziomu. */
class TileGrid(val width: Int, val height: Int) {
    private val cells = Array(width * height) { Tile.SEA }

    /** Pola morza, na których rysujemy animowaną falę (czysto dekoracyjne). */
    val waves = BooleanArray(width * height)

    /** Siatka zawinięta w pionie (nieskończenie przewijane tło menu). */
    var wrapRows = false

    /** Kolory piasku pól (poziomy z kreatora); null – cały piasek w palecie wiersza. */
    private var sand: ByteArray? = null

    fun wrap(row: Int): Int = if (wrapRows) Math.floorMod(row, height) else row

    /**
     * Paleta piasku pola (jak numer świata) albo [DEFAULT_SAND] – piasek w palecie wiersza. Poza kolumnami
     * piasek ciągnie się w kolorze brzegu.
     */
    fun sandColor(col: Int, row: Int): Int {
        val r = wrap(row)
        val colors = sand ?: return DEFAULT_SAND
        if (r !in 0 until height) return DEFAULT_SAND
        val c = col.coerceIn(0, width - 1)
        if (c != col && cells[r * width + c] != Tile.SAND) return DEFAULT_SAND
        return colors[r * width + c].toInt()
    }

    fun setSandColor(col: Int, row: Int, palette: Int) {
        if (!inside(col, row)) return
        val colors = sand ?: if (palette == DEFAULT_SAND) return else ByteArray(width * height) { DEFAULT_SAND.toByte() }.also { sand = it }
        colors[row * width + col] = palette.toByte()
    }

    operator fun get(col: Int, row: Int): Tile {
        val r = wrap(row)
        if (r < 0 || r >= height) return Tile.WALL
        if (col < 0 || col >= width) return Tile.SAND
        return cells[r * width + col]
    }

    operator fun set(col: Int, row: Int, tile: Tile) {
        if (col in 0 until width && row in 0 until height) cells[row * width + col] = tile
    }

    fun isWave(col: Int, row: Int): Boolean {
        val r = wrap(row)
        return col in 0 until width && r in 0 until height && waves[r * width + col]
    }

    fun inside(col: Int, row: Int) = col in 0 until width && row in 0 until height

    companion object {
        /** Piasek w palecie wiersza (jak [UserLevels.SAME_THEME]). */
        const val DEFAULT_SAND = -2
    }
}

enum class LevelType {
    CLASSIC,
    ROCKY,
    ARCHIPELAGO,
    CANAL,
    CURRENT,
    GOLD_RUSH,
    TIME_ATTACK,
    SWARM,
    BOSS,

    /** Poziom 1000 – Wielki Finał. */
    FINALE,

    /** Poziom z kreatora (od wersji 2.4). */
    CUSTOM,
}

enum class EnemyKind(
    /** Koszt w „budżecie trudności” etapu. */
    val cost: Float,
    /** Pierwszy poziom, na którym się pojawia. */
    val firstLevel: Int,
) {
    PIRANHA(1f, 2),
    PIRANHA_VERTICAL(1f, 4),
    OCTOPUS(1.5f, 8),
    SHARK(2f, 12),
    RED_PIRANHA(1.5f, 76),
    RED_OCTOPUS(2f, 126),
    DARK_SHARK(3f, 176),

    /**
     * Widmokuda – najgroźniejszy potwór (od wersji 2.3). Nie trafia do losowania z budżetu:
     * dokłada ją [Stalkers] już po policzeniu planu, w miejsce kilku słabszych potworów.
     */
    STALKER(4f, Stalkers.FIRST_LEVEL),
    ;

    /** Koszt w dziesiątych częściach – poziomy z seeda liczą budżet na liczbach całkowitych. */
    val costTenths: Int get() = Math.round(cost * 10f)

    companion object {
        /**
         * Potwory losowane z budżetu – w tej kolejności, bo kolejność listy jest częścią formatu
         * poziomów (serwer rankingu powtarza to losowanie u siebie).
         */
        val rolled: List<EnemyKind> = entries.filter { it != STALKER }
    }
}

enum class FishKind(val points: Int, val coins: Int) {
    COMMON(10, 1),
    BLUE(25, 2),
    GOLDEN(100, 5),
}

/** Cel na drugą gwiazdkę. */
enum class SecondGoal { NO_DAMAGE, ALL_FISH }

/** Cel na trzecią gwiazdkę. */
enum class ThirdGoal { PAR_TIME, SCORE, ALL_GOLDEN, COMBO }

/** [dir] – kierunek na start (0 prawo, 1 dół, 2 lewo, 3 góra), -1 – losowy. */
class EnemySpawn(val kind: EnemyKind, val col: Int, val row: Int, val dir: Int = -1)

class FishSpawn(val kind: FishKind, val col: Int, val row: Int)

/** Złota ryba pojawia się z opóźnieniem po wpłynięciu do etapu. */
class GoldenSpawn(val delaySeconds: Float, val col: Int, val row: Int)

class StageLayout(
    val index: Int,
    /** Wiersz bramy nad etapem (przejście do kolejnego etapu albo meta). */
    val gateRow: Int,
    /** Pierwszy (najwyższy) i ostatni (najniższy) wiersz wody etapu. */
    val topRow: Int,
    val bottomRow: Int,
    val doorCols: IntRange,
    /** Ile ryb trzeba złowić, żeby otworzyć bramę. */
    val quota: Int,
    val fish: List<FishSpawn>,
    val golden: List<GoldenSpawn>,
    val enemies: List<EnemySpawn>,
    /** Pola morza osiągalne z wejścia do etapu (do losowania odrodzeń). */
    val reachable: IntArray,
    /** Prąd morski tylko w tym etapie (poziomy z kreatora); null – prąd całego poziomu ze [LevelSpec]. */
    val currentX: Float? = null,
    val currentY: Float? = null,
) {
    val fishTotal: Int get() = fish.size + golden.size
}

class LevelSpec(
    val number: Int,
    val world: Int,
    val type: LevelType,
    val seed: Long,
    val secondGoal: SecondGoal,
    val thirdGoal: ThirdGoal,
    /** Czas wzorcowy w sekundach (trzecia gwiazdka / bonus czasowy). */
    val parSeconds: Int,
    /** Limit czasu dla trybu wyścigu, 0 = brak. */
    val timeLimitSeconds: Int,
    val scoreTarget: Int,
    val comboTarget: Int,
    /** Mnożnik prędkości przeciwników. */
    val enemySpeed: Float,
    /** Prąd morski (jednostki świata na sekundę). */
    val currentX: Float,
    val currentY: Float,
)

class LevelLayout(
    val spec: LevelSpec,
    val grid: TileGrid,
    val stages: List<StageLayout>,
    /** Pole startowe łodzi. */
    val startCol: Int,
    val startRow: Int,
    /** Kraby na plaży – tylko dekoracja. */
    val crabs: List<Pair<Int, Int>>,
    /** Paleta (świat) każdego wiersza, gdy etapy mają różne barwy (poziomy z kreatora); null – jedna paleta. */
    val rowWorlds: IntArray? = null,
) {
    val fishCount: Int get() = stages.sumOf { it.fish.size }
    val goldenCount: Int get() = stages.sumOf { it.golden.size }
}

object LevelConstants {
    const val TILE = 16
    const val WIDTH = 11
    /** Poziomy kampanii: 1–999 i Wielki Finał. Dalej zaczyna się Bezkresne Morze ([EndlessLevels]). */
    const val LEVEL_COUNT = 1000

    /**
     * Ostatni poziom Bezkresnego Morza. Praktycznie bez końca (ponad 19 000 poziomów z seeda),
     * a zapis gry i serwer rankingu (numer poziomu jako SMALLINT UNSIGNED) mają spory zapas.
     */
    const val MAX_LEVEL = 20_000

    /** Pierwsze 500 poziomów (pierwsza wyprawa) ma niezmienioną krzywą trudności. */
    const val FIRST_VOYAGE_LEVELS = 500
    const val LEVELS_PER_WORLD = 25
    const val WORLD_COUNT = LEVEL_COUNT / LEVELS_PER_WORLD
    const val FINISH_ROWS = 6
    const val MAX_COMBO = 5
    const val STAGE_BONUS = 50
    const val NO_DAMAGE_BONUS = 300
    const val TIME_BONUS_PER_SECOND = 10
}
