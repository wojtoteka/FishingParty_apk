package wojtoteka.ovh.fishingparty.gfx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import wojtoteka.ovh.fishingparty.level.LevelConstants.TILE
import wojtoteka.ovh.fishingparty.level.Tile
import wojtoteka.ovh.fishingparty.level.TileGrid
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Rysuje siatkę kafelków tak jak animowane mapy oryginału: fale, piana przy
 * brzegu, skały i wrota zmieniają klatkę co 125 ms z przesunięciem zależnym
 * od położenia pola. Brzegi dobierane są automatycznie po sąsiadach. Piasek pola może mieć kolor innej
 * palety ([TileGrid.sandColor], poziomy z kreatora) – woda i piana przy brzegu zostają w barwach wiersza.
 */
class TileRenderer {
    val paint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
        isDither = false
    }
    private val dst = RectF()

    /**
     * Rysuje w układzie świata (canvas musi być już przeskalowany i przesunięty o kamerę).
     * @param tilesForRow paleta kafelków dla danego wiersza (mapa świata zmienia ją co świat)
     * @param doorOpen czy wrota w danym wierszu są otwarte (rysujemy wtedy wodę)
     */
    fun draw(
        canvas: Canvas,
        grid: TileGrid,
        camX: Float,
        camY: Float,
        viewW: Float,
        viewH: Float,
        time: Float,
        tilesForRow: (Int) -> TileSet,
        doorOpen: (Int) -> Boolean,
    ) {
        val frame = frameAt(time)
        val c0 = floor(camX / TILE).toInt() - 1
        val c1 = floor((camX + viewW) / TILE).toInt() + 1
        val r0 = floor(camY / TILE).toInt() - 1
        val r1 = floor((camY + viewH) / TILE).toInt() + 1
        for (r in r0..r1) {
            val rowSet = tilesForRow(grid.wrap(r).coerceIn(0, grid.height - 1))
            val open = doorOpen(r)
            for (c in c0..c1) {
                place(canvas, spriteAt(grid, rowSet, open, c, r, frame), (c * TILE).toFloat(), (r * TILE).toFloat(), grid[c, r] == Tile.WALL_BOTTOM)
            }
        }
    }

    /**
     * Widoczne kafelki jako jedna bitmapa w rozdzielczości świata (kafelki są nieprzezroczyste i mają po 16 pikseli).
     * [sprites] i [flips] pamiętają, co leży na każdym polu, więc odświeżenie maluje tylko pola, które się zmieniły.
     */
    private class Layer(val cols: Int, val rows: Int) {
        val bitmap: Bitmap = Bitmap.createBitmap(cols * TILE, rows * TILE, Bitmap.Config.ARGB_8888).apply { setHasAlpha(false) }
        val canvas = Canvas(bitmap)
        val sprites = arrayOfNulls<Bitmap>(cols * rows)
        val flips = BooleanArray(cols * rows)
        val rowSets = arrayOfNulls<TileSet>(rows)
        val rowDoors = BooleanArray(rows)
        var grid: TileGrid? = null
        var frame = -1
        var c0 = 0
        var r0 = 0
    }

    private var layer: Layer? = null

    /**
     * Daje ten sam obraz co [draw], ale jednym poleceniem rysowania: warstwa odświeża się tylko przy zmianie klatki
     * animacji (8 razy na sekundę), przejściu kamery na kolejne pole albo otwarciu wrót. Siatka nie może się zmieniać
     * między klatkami – kreator, w którym gracz maluje po planszy, używa [draw].
     */
    fun drawCached(
        canvas: Canvas,
        grid: TileGrid,
        camX: Float,
        camY: Float,
        viewW: Float,
        viewH: Float,
        time: Float,
        tilesForRow: (Int) -> TileSet,
        doorOpen: (Int) -> Boolean,
    ) {
        val cols = ceil(viewW / TILE).toInt() + 1
        val rows = ceil(viewH / TILE).toInt() + 1
        val l = layer?.takeIf { it.cols == cols && it.rows == rows } ?: Layer(cols, rows).also { layer = it }
        val frame = frameAt(time)
        val c0 = floor(camX / TILE).toInt()
        val r0 = floor(camY / TILE).toInt()
        var stale = l.grid !== grid || l.frame != frame || l.c0 != c0 || l.r0 != r0
        for (i in 0 until rows) {
            val set = tilesForRow(grid.wrap(r0 + i).coerceIn(0, grid.height - 1))
            val open = doorOpen(r0 + i)
            if (l.rowSets[i] !== set || l.rowDoors[i] != open) {
                l.rowSets[i] = set
                l.rowDoors[i] = open
                stale = true
            }
        }
        if (stale) {
            l.grid = grid
            l.frame = frame
            l.c0 = c0
            l.r0 = r0
            refresh(l, grid, frame)
        }
        dst.set((c0 * TILE).toFloat(), (r0 * TILE).toFloat(), ((c0 + cols) * TILE).toFloat(), ((r0 + rows) * TILE).toFloat())
        canvas.drawBitmap(l.bitmap, null, dst, paint)
    }

    private fun refresh(l: Layer, grid: TileGrid, frame: Int) {
        for (i in 0 until l.rows) {
            val r = l.r0 + i
            val set = l.rowSets[i] ?: continue
            for (j in 0 until l.cols) {
                val c = l.c0 + j
                val sprite = spriteAt(grid, set, l.rowDoors[i], c, r, frame)
                val flip = grid[c, r] == Tile.WALL_BOTTOM
                val cell = i * l.cols + j
                if (l.sprites[cell] === sprite && l.flips[cell] == flip) continue
                l.sprites[cell] = sprite
                l.flips[cell] = flip
                place(l.canvas, sprite, (j * TILE).toFloat(), (i * TILE).toFloat(), flip)
            }
        }
    }

    private fun frameAt(time: Float) = (floor(time * 8f).toInt() % 10 + 10) % 10

    /** Rysunek pola (c, r) w klatce [frame]; poza planszą ciągnie się piasek. */
    private fun spriteAt(grid: TileGrid, set: TileSet, doorOpen: Boolean, c: Int, r: Int, frame: Int): Bitmap {
        val wrapped = grid.wrap(r)
        if (wrapped < 0 || wrapped >= grid.height || c < 0 || c >= grid.width) return sandSet(grid, set, c, r).sandFull
        return when (grid[c, r]) {
            Tile.SEA -> seaSprite(grid, set, c, r, frame)
            Tile.SAND -> sandSprite(grid, sandSet(grid, set, c, r), c, r, frame)
            Tile.ROCK -> set.rocks[anim(r * 3 + c + frame, 3)]
            Tile.WALL -> if (grid[c, r + 1] == Tile.SAND) sandSet(grid, set, c, r + 1).wallSand else set.walls[anim(r * 3 + c + frame, 2)]
            Tile.WALL_BOTTOM -> set.walls[anim(r * 3 + c + frame, 2)]
            Tile.DOOR -> if (doorOpen) seaSprite(grid, set, c, r, frame) else set.doors[anim(r * 3 + c + frame, 2)]
        }
    }

    /** Dolny mur to górny odbity w pionie. */
    private fun place(canvas: Canvas, sprite: Bitmap, x: Float, y: Float, flip: Boolean) {
        if (flip) {
            canvas.save()
            canvas.scale(1f, -1f, x + TILE / 2f, y + TILE / 2f)
            blit(canvas, sprite, x, y)
            canvas.restore()
        } else {
            blit(canvas, sprite, x, y)
        }
    }

    /** Kafelki piasku pola: w barwach wiersza albo przemalowane na kolor z kreatora. */
    private fun sandSet(grid: TileGrid, set: TileSet, c: Int, r: Int): TileSet {
        val palette = grid.sandColor(c, r)
        return if (palette == TileGrid.DEFAULT_SAND) set else set.withSand(palette)
    }

    private fun seaSprite(grid: TileGrid, set: TileSet, c: Int, r: Int, frame: Int): Bitmap =
        if (grid.isWave(c, r)) set.waves[Math.floorMod(c * 3 + r * 7 + Math.floorDiv(c + r + frame, 2), 5)] else set.sea

    /** Klatka animacji co 2 kroki; wiersze bywają ujemne (zapętlone tło), stąd floorMod. */
    private fun anim(v: Int, n: Int) = Math.floorMod(Math.floorDiv(v, 2), n)

    private fun waterLike(t: Tile) = t == Tile.SEA || t == Tile.ROCK

    fun sandSprite(grid: TileGrid, set: TileSet, c: Int, r: Int, frame: Int): Bitmap {
        val n = waterLike(grid[c, r - 1])
        val s = waterLike(grid[c, r + 1])
        val w = waterLike(grid[c - 1, r])
        val e = waterLike(grid[c + 1, r])
        val f = anim(r * 3 + c * 7 + frame, 2)
        return when {
            n && w -> set.sandBottomRight[f]
            n && e -> set.sandLeftBottom[f]
            s && w -> set.sandTopRight[f]
            s && e -> set.sandTopLeft[f]
            n -> set.sandBottom[f]
            s -> set.sandTop[f]
            w -> set.sandRight[f]
            e -> set.sandLeft[f]
            else -> set.sandFull
        }
    }

    fun blit(canvas: Canvas, bitmap: Bitmap, x: Float, y: Float) {
        dst.set(x, y, x + bitmap.width, y + bitmap.height)
        canvas.drawBitmap(bitmap, null, dst, paint)
    }
}
