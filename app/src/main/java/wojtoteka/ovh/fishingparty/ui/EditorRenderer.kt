package wojtoteka.ovh.fishingparty.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import wojtoteka.ovh.fishingparty.gfx.PixelText
import wojtoteka.ovh.fishingparty.gfx.ShipLook
import wojtoteka.ovh.fishingparty.gfx.ShipPainter
import wojtoteka.ovh.fishingparty.gfx.Sprites
import wojtoteka.ovh.fishingparty.gfx.TileRenderer
import wojtoteka.ovh.fishingparty.gfx.TileSet
import wojtoteka.ovh.fishingparty.level.EnemyKind
import wojtoteka.ovh.fishingparty.level.LevelConstants.TILE
import wojtoteka.ovh.fishingparty.level.LevelConstants.WIDTH
import wojtoteka.ovh.fishingparty.level.UserLevel
import wojtoteka.ovh.fishingparty.level.UserLevels
import wojtoteka.ovh.fishingparty.level.Worlds
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max

/** Co poza samą planszą pokazuje kreator: zaznaczoną postać, pędzel pod palcem, pola z problemami. */
class EditorOverlay(
    /** Zaznaczone pole (bezwzględny wiersz siatki, kolumna) albo null. */
    val selected: Pair<Int, Int>? = null,
    /** Pola pod pędzlem (bezwzględne wiersze). */
    val brush: List<Pair<Int, Int>> = emptyList(),
    /** Pola z błędami (bezwzględne wiersze). */
    val problems: List<Pair<Int, Int>> = emptyList(),
    /** Przeciągana postać: jej sprite rysujemy pod palcem (współrzędne świata). */
    val dragX: Float = 0f,
    val dragY: Float = 0f,
    val dragging: Bitmap? = null,
    val activeStage: Int = 0,
)

/**
 * Rysowanie planszy w kreatorze – tymi samymi kafelkami i spritami co gra (WYSIWYG): woda z falami,
 * brzegi, bramy, ryby, potwory zwrócone w kierunku startowym i łódź gracza na starcie.
 * Dochodzą tylko pomoce kreatora: siatka pól, pola zawsze z wodą, zaznaczenie i podpisy etapów.
 */
class EditorRenderer {
    private val tiles = TileRenderer()
    private val painter = ShipPainter()
    private val text = PixelText()
    private val fill = Paint().apply { isAntiAlias = false }
    private val line = Paint().apply {
        isAntiAlias = false
        style = Paint.Style.STROKE
    }

    private val fish = Sprites.sprite("fish1")
    private val blue = Sprites.sprite("fishblue1")
    private val golden = Sprites.sprite("fishgold1")
    private val arrow = Sprites.sprite("arrowup")
    private val enemies = EnemyKind.entries.associateWith { Sprites.sprite(enemyIcon(it)) }
    private val piranhaUp = Sprites.sprite("piranhaback0")

    private var rowSets: Array<TileSet> = emptyArray()
    private var rowKey: IntArray? = null

    /** Skala (piksele ekranu na piksel świata) i przesunięcie planszy w poziomie – ustawiane przy rysowaniu. */
    var scale = 6f
        private set
    var offsetX = 0f
        private set

    fun scaleFor(width: Int): Int = max(2, width / (WIDTH * TILE))

    fun draw(
        canvas: Canvas,
        level: UserLevel,
        built: UserLevels.Built,
        width: Int,
        height: Int,
        scroll: Float,
        time: Float,
        look: ShipLook,
        overlay: EditorOverlay,
        labels: (stage: Int) -> String,
    ) {
        val s = scaleFor(width).toFloat()
        scale = s
        offsetX = floor((width - WIDTH * TILE * s) / 2f)
        val layout = built.layout
        val grid = layout.grid
        val worlds = layout.rowWorlds ?: IntArray(grid.height) { level.theme }
        if (!worlds.contentEquals(rowKey)) {
            rowKey = worlds
            rowSets = Array(worlds.size) { Sprites.tiles(Worlds.theme(worlds[it])) }
        }
        val viewH = height / s
        val center = ((scroll + viewH / 2f) / TILE).toInt().coerceIn(0, grid.height - 1)
        canvas.drawColor(Worlds.seaColor(worlds[center]))
        canvas.save()
        canvas.translate(offsetX, 0f)
        canvas.scale(s, s)
        canvas.translate(0f, -scroll)
        tiles.draw(canvas, grid, 0f, scroll, (WIDTH * TILE).toFloat(), viewH, time, { r -> rowSets[r.coerceIn(0, rowSets.size - 1)] }, { false })

        val firstRow = floor(scroll / TILE).toInt().coerceAtLeast(0)
        val lastRow = ((scroll + viewH) / TILE).toInt().coerceAtMost(grid.height - 1)
        // Siatka pól i pola zawsze z wodą – tylko w etapach.
        line.strokeWidth = 1f / s
        line.color = GRID
        fill.color = LOCKED
        for ((k, stage) in layout.stages.withIndex()) {
            if (stage.bottomRow < firstRow || stage.topRow > lastRow) continue
            for (r in stage.topRow..stage.bottomRow) {
                if (r < firstRow || r > lastRow) continue
                canvas.drawLine(0f, r * TILE.toFloat(), (WIDTH * TILE).toFloat(), r * TILE.toFloat(), line)
                for (c in 0 until WIDTH) {
                    if (UserLevels.locked(level, k, c, r - stage.topRow)) canvas.drawRect(c * TILE.toFloat(), r * TILE.toFloat(), (c + 1) * TILE.toFloat(), (r + 1) * TILE.toFloat(), fill)
                }
            }
            for (c in 1 until WIDTH) {
                canvas.drawLine(c * TILE.toFloat(), stage.topRow * TILE.toFloat(), c * TILE.toFloat(), (stage.bottomRow + 1) * TILE.toFloat(), line)
            }
        }
        // Aktywny etap w złotej ramce.
        layout.stages.getOrNull(overlay.activeStage)?.let { st ->
            line.color = ACTIVE
            line.strokeWidth = 1f
            canvas.drawRect(0.5f, st.topRow * TILE + 0.5f, WIDTH * TILE - 0.5f, (st.bottomRow + 1) * TILE - 0.5f, line)
        }

        // Postacie.
        for ((k, stage) in level.stages.withIndex()) {
            val top = layout.stages[k].topRow
            if (top + stage.rows < firstRow || top > lastRow + 1) continue
            val set = rowSets[top.coerceIn(0, rowSets.size - 1)]
            for (c in stage.crabs) painter.sprite(canvas, set.crab, cx(c.col), cy(top + c.row), 0f)
            for (f in stage.fish) painter.sprite(canvas, if (f.blue) blue else fish, cx(f.col), cy(top + f.row), 0f)
            for (g in stage.golden) painter.sprite(canvas, golden, cx(g.col), cy(top + g.row), 0f)
            for (e in stage.enemies) drawEnemy(canvas, e.kind, e.dir, cx(e.col), cy(top + e.row))
        }
        painter.draw(canvas, cx(layout.startCol), cy(layout.startRow), (-PI / 2).toFloat(), false, time, look)

        // Problemy, zaznaczenie, pędzel.
        line.strokeWidth = 1f
        line.color = PROBLEM
        for ((r, c) in overlay.problems) cell(canvas, c, r, 0.5f)
        line.color = BRUSH
        for ((r, c) in overlay.brush) cell(canvas, c, r, 0.5f)
        line.color = ACTIVE
        overlay.selected?.let { (r, c) ->
            line.strokeWidth = 2f
            cell(canvas, c, r, 1f)
        }
        overlay.dragging?.let { painter.sprite(canvas, it, overlay.dragX, overlay.dragY, 0f, alpha = 200) }
        canvas.restore()

        // Podpisy w ekranie: etap i ryby na murze bramy, opóźnienia złotych ryb.
        val p = max(2f, floor(s / 2f))
        for ((k, stage) in layout.stages.withIndex()) {
            val y = (stage.gateRow * TILE - scroll) * s
            if (y < -TILE * s || y > height) continue
            text.draw(canvas, labels(k), offsetX + 3 * p, y + (TILE * s - 7 * p) / 2f, p, if (k == overlay.activeStage) PixelText.GOLD else PixelText.WHITE)
        }
        for ((k, stage) in level.stages.withIndex()) {
            val top = layout.stages[k].topRow
            for (g in stage.golden) {
                val y = ((top + g.row) * TILE - scroll) * s
                if (y < -TILE * s || y > height) continue
                text.draw(canvas, "${g.delay}s", offsetX + (g.col * TILE + TILE - 1) * s, y + TILE * s - 8 * p, p, PixelText.GOLD, align = 1)
            }
        }
    }

    private fun cx(col: Int) = col * TILE + TILE / 2f

    private fun cy(row: Int) = row * TILE + TILE / 2f

    private fun cell(canvas: Canvas, c: Int, r: Int, inset: Float) {
        canvas.drawRect(c * TILE + inset, r * TILE + inset, (c + 1) * TILE - inset, (r + 1) * TILE - inset, line)
    }

    /** Sprite potwora zwrócony tak jak na starcie (bez kierunku – jak domyślnie w grze), ze strzałką przy kierunku z kreatora. */
    fun drawEnemy(canvas: Canvas, kind: EnemyKind, dir: Int, x: Float, y: Float) {
        val dx = when (dir) {
            0 -> 1
            2 -> -1
            else -> 0
        }
        val dy = when (dir) {
            1 -> 1
            3 -> -1
            else -> 0
        }
        val sprite = enemies.getValue(kind)
        when (kind) {
            EnemyKind.SHARK, EnemyKind.DARK_SHARK -> painter.sprite(canvas, sprite, x, y - 4f, 0f, flipX = (if (dx != 0) dx else -dy) < 0)
            EnemyKind.PIRANHA, EnemyKind.RED_PIRANHA -> painter.sprite(canvas, sprite, x, y, 0f, flipX = dx < 0)
            EnemyKind.PIRANHA_VERTICAL -> painter.sprite(canvas, if (dy < 0) piranhaUp else sprite, x, y, 0f)
            EnemyKind.OCTOPUS, EnemyKind.RED_OCTOPUS -> painter.sprite(canvas, sprite, x, y, 0f, flipX = (if (dx != 0) -dx else dy) < 0)
            EnemyKind.STALKER -> painter.sprite(canvas, sprite, x, y, 0f, flipX = dx < 0)
        }
        if (dir in 0..3) {
            val rotation = when (dir) {
                0 -> 90f
                1 -> 180f
                2 -> 270f
                else -> 0f
            }
            painter.sprite(canvas, arrow, x + dx * 9f, y + dy * 9f, rotation, alpha = 210)
        }
    }

    companion object {
        private const val GRID = 0x33E3ECFF
        private const val LOCKED = 0x2EFFFFFF
        private const val ACTIVE = 0xFFFFC420.toInt()
        private const val PROBLEM = 0xFFFF3C28.toInt()
        private const val BRUSH = 0xCCFFFFFF.toInt()
    }
}
