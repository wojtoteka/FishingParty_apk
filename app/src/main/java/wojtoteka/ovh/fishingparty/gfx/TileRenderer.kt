package wojtoteka.ovh.fishingparty.gfx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import wojtoteka.ovh.fishingparty.level.LevelConstants.TILE
import wojtoteka.ovh.fishingparty.level.Tile
import wojtoteka.ovh.fishingparty.level.TileGrid
import kotlin.math.floor

/**
 * Rysuje siatkę kafelków tak jak animowane mapy oryginału: fale, piana przy
 * brzegu, skały i wrota zmieniają klatkę co 125 ms z przesunięciem zależnym
 * od położenia pola. Brzegi dobierane są automatycznie po sąsiadach.
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
        val frame = (floor(time * 8f).toInt() % 10 + 10) % 10
        val c0 = floor(camX / TILE).toInt() - 1
        val c1 = floor((camX + viewW) / TILE).toInt() + 1
        val r0 = floor(camY / TILE).toInt() - 1
        val r1 = floor((camY + viewH) / TILE).toInt() + 1
        for (r in r0..r1) {
            val rowSet = tilesForRow(grid.wrap(r).coerceIn(0, grid.height - 1))
            val wrapped = grid.wrap(r)
            for (c in c0..c1) {
                val x = (c * TILE).toFloat()
                val y = (r * TILE).toFloat()
                if (wrapped < 0 || wrapped >= grid.height || c < 0 || c >= grid.width) {
                    blit(canvas, rowSet.sandFull, x, y)
                    continue
                }
                when (grid[c, r]) {
                    Tile.SEA -> blit(canvas, seaSprite(grid, rowSet, c, r, frame), x, y)
                    Tile.SAND -> blit(canvas, sandSprite(grid, rowSet, c, r, frame), x, y)
                    Tile.ROCK -> blit(canvas, rowSet.rocks[anim(r * 3 + c + frame, 3)], x, y)
                    Tile.WALL -> {
                        val below = grid[c, r + 1]
                        val sprite = if (below == Tile.SAND) rowSet.wallSand
                        else rowSet.walls[anim(r * 3 + c + frame, 2)]
                        blit(canvas, sprite, x, y)
                    }
                    Tile.WALL_BOTTOM -> {
                        val sprite = rowSet.walls[anim(r * 3 + c + frame, 2)]
                        canvas.save()
                        canvas.scale(1f, -1f, x + TILE / 2f, y + TILE / 2f)
                        blit(canvas, sprite, x, y)
                        canvas.restore()
                    }
                    Tile.DOOR -> {
                        if (doorOpen(r)) blit(canvas, seaSprite(grid, rowSet, c, r, frame), x, y)
                        else blit(canvas, rowSet.doors[anim(r * 3 + c + frame, 2)], x, y)
                    }
                }
            }
        }
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
