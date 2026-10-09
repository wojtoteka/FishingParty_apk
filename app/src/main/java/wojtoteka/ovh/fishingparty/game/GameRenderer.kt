package wojtoteka.ovh.fishingparty.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import wojtoteka.ovh.fishingparty.gfx.PixelText
import wojtoteka.ovh.fishingparty.gfx.ShipLook
import wojtoteka.ovh.fishingparty.gfx.ShipPainter
import wojtoteka.ovh.fishingparty.gfx.Sprites
import wojtoteka.ovh.fishingparty.gfx.TileRenderer
import wojtoteka.ovh.fishingparty.level.EnemyKind
import wojtoteka.ovh.fishingparty.level.FishKind
import wojtoteka.ovh.fishingparty.level.LevelConstants.TILE
import wojtoteka.ovh.fishingparty.level.Worlds
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Teksty HUD-u (z zasobów, żeby renderer nie zależał od Context). */
class HudStrings(
    val go: String,
    val stage: String,
    val gateOpen: String,
    val finishOpen: String,
    val finish: String,
    val lastLife: String,
    val shield: String,
    val timeLow: String,
    val combo: String,
    val tutorial: String,
)

/** Pływający joystick – stan dotyku w pikselach ekranu. */
class TouchStick {
    var active = false
    var originX = 0f
    var originY = 0f
    var x = 0f
    var y = 0f
    var engaged = false
    var angle = 0f
}

class GameRenderer(private val strings: HudStrings, private val look: ShipLook) {
    private val tiles = TileRenderer()
    private val ship = ShipPainter()
    private val text = PixelText()
    private val paint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
    }
    private val fill = Paint()
    private val rect = RectF()
    private var themeWorld = -1
    private var currentTiles = Sprites.tiles(Worlds.theme(0))

    /** Skala: ile pikseli ekranu na piksel spritu. */
    var scale = 6
        private set

    fun draw(canvas: Canvas, world: GameWorld, width: Int, height: Int, topInset: Float, stick: TouchStick) {
        if (themeWorld != world.spec.world) {
            themeWorld = world.spec.world
            currentTiles = Sprites.tiles(Worlds.theme(themeWorld))
        }
        scale = scaleFor(width)
        val s = scale.toFloat()
        val viewW = width / s
        val viewH = height / s
        world.setViewport(viewW, viewH)
        val camX = (world.camX * s).roundToInt() / s
        val camY = (world.camY * s).roundToInt() / s
        val t = world.animTime

        canvas.drawColor(Worlds.seaColor(world.spec.world))
        canvas.save()
        canvas.scale(s, s)
        canvas.translate(-camX, -camY)

        tiles.draw(
            canvas, world.grid, camX, camY, viewW, viewH, t,
            tilesForRow = { currentTiles },
            doorOpen = { row -> world.stageOfGateRow(row).let { it >= 0 && world.isDoorOpen(it) } },
        )
        drawCrabs(canvas, world, t)
        drawGateArrow(canvas, world, t, camY)
        drawFish(canvas, world, t)
        drawEnemies(canvas, world, t)

        val blink = world.invulnerable > 0f && (t % 0.2f) < 0.1f
        if (!blink) {
            ship.draw(canvas, world.shipX, world.shipY, world.heading, world.speed > 6f, t, look)
        }
        val caughtSprite = Sprites.sprite("fish")
        for (c in world.caught) ship.sprite(canvas, caughtSprite, c.x, c.y, 0f)
        canvas.restore()

        drawPopups(canvas, world, camX, camY, s)
        drawStick(canvas, stick, s)
        drawHud(canvas, world, width, topInset, s)
        drawMessage(canvas, world, width, height, s)
        if (world.showControls) drawTutorial(canvas, width, height, s, t)
        if (world.hitFlash > 0f) {
            fill.color = 0xFFFF0000.toInt()
            fill.alpha = (world.hitFlash * 110).toInt().coerceIn(0, 255)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        }
    }

    private fun drawCrabs(canvas: Canvas, world: GameWorld, t: Float) {
        val crab = currentTiles.crab
        for ((i, pos) in world.layout.crabs.withIndex()) {
            val (c, r) = pos
            val walk = sin(t * 1.3f + i * 2.1f) * 3f
            val x = c * TILE + 8f + walk
            val y = r * TILE + 8f
            ship.sprite(canvas, crab, x, y, if ((t * 4f + i).toInt() % 2 == 0) -8f else 8f)
        }
    }

    private fun drawGateArrow(canvas: Canvas, world: GameWorld, t: Float, camY: Float) {
        if (!world.showGateArrow || t % 0.5f >= 0.25f) return
        val stage = world.currentStage
        val x = (stage.doorCols.first + stage.doorCols.last + 1) * TILE / 2f
        val y = max(stage.gateRow * TILE - 12f, camY + 46f)
        ship.sprite(canvas, Sprites.sprite("arrowup"), x, y, 0f, alpha = 150)
    }

    private fun drawFish(canvas: Canvas, world: GameWorld, t: Float) {
        for (f in world.fish) {
            if (f.kind == FishKind.GOLDEN && f.life < 2.5f && (t % 0.2f) < 0.08f) continue
            val second = (t + f.phase) % 0.4f >= 0.2f
            val name = when (f.kind) {
                FishKind.COMMON -> if (second) "fish2" else "fish1"
                FishKind.BLUE -> if (second) "fishblue2" else "fishblue1"
                FishKind.GOLDEN -> if (second) "fishgold2" else "fishgold1"
            }
            // Sprite ryby patrzy w lewo – odwracamy, gdy płynie w prawo (jak w oryginale).
            ship.sprite(canvas, Sprites.sprite(name), f.x, f.y, 0f, flipX = f.facing > 0)
        }
    }

    private fun drawEnemies(canvas: Canvas, world: GameWorld, t: Float) {
        for (e in world.enemies) {
            if (e.harmless && (t % 0.2f) < 0.1f) continue
            val second = (t + e.phase) % 0.4f >= 0.2f
            val horizontalSign = if (e.dirX != 0) e.dirX else -e.dirY
            when (e.kind) {
                EnemyKind.SHARK, EnemyKind.DARK_SHARK -> {
                    val base = if (e.kind == EnemyKind.SHARK) "shark" else "sharkdark"
                    ship.sprite(canvas, Sprites.sprite(base + if (second) "2" else "1"), e.x, e.y - 4f, 0f, flipX = horizontalSign < 0)
                }
                EnemyKind.PIRANHA, EnemyKind.RED_PIRANHA -> {
                    val base = if (e.kind == EnemyKind.PIRANHA) "piranha" else "piranhared"
                    ship.sprite(canvas, Sprites.sprite(base + if (second) "2" else "1"), e.x, e.y, 0f, flipX = e.dirX < 0)
                }
                EnemyKind.PIRANHA_VERTICAL -> {
                    val name = (if (e.dirY < 0) "piranhaback" else "piranhafront") + if (second) "1" else "0"
                    ship.sprite(canvas, Sprites.sprite(name), e.x, e.y, 0f)
                }
                EnemyKind.OCTOPUS, EnemyKind.RED_OCTOPUS -> {
                    val base = if (e.kind == EnemyKind.OCTOPUS) "octopus" else "octopusred"
                    val flip = if (e.dirX != 0) -e.dirX else e.dirY
                    ship.sprite(canvas, Sprites.sprite(base + if (second) "2" else "1"), e.x, e.y, 0f, flipX = flip < 0)
                }
            }
        }
    }

    private fun drawPopups(canvas: Canvas, world: GameWorld, camX: Float, camY: Float, s: Float) {
        val p = max(2f, floor(s / 2f))
        for (pop in world.popups) {
            val alpha = if (pop.age > 0.8f) ((1.1f - pop.age) / 0.3f * 255).toInt().coerceIn(0, 255) else 255
            text.draw(
                canvas, "+${pop.value}", (pop.x - camX) * s, (pop.y - camY) * s - 7 * p, p,
                if (pop.golden) PixelText.GOLD else PixelText.WHITE, align = 0, alpha = alpha,
            )
        }
    }

    private fun drawStick(canvas: Canvas, stick: TouchStick, s: Float) {
        if (!stick.active || !stick.engaged) return
        val arrow = Sprites.sprite("arrowup")
        paint.alpha = 110
        canvas.save()
        canvas.translate(stick.originX, stick.originY)
        canvas.rotate(Math.toDegrees(stick.angle.toDouble()).toFloat() + 90f)
        val k = max(3f, s * 0.75f).roundToInt().toFloat()
        rect.set(-arrow.width * k / 2f, -arrow.height * k / 2f - arrow.height * k, arrow.width * k / 2f, arrow.height * k / 2f - arrow.height * k)
        canvas.drawBitmap(arrow, null, rect, paint)
        canvas.restore()
        paint.alpha = 255
    }

    private fun icon(canvas: Canvas, bitmap: Bitmap, left: Float, top: Float, k: Float, alpha: Int = 255) {
        paint.alpha = alpha
        rect.set(left, top, left + bitmap.width * k, top + bitmap.height * k)
        canvas.drawBitmap(bitmap, null, rect, paint)
        paint.alpha = 255
    }

    private fun drawHud(canvas: Canvas, world: GameWorld, width: Int, topInset: Float, s: Float) {
        // Rozmiary fontu jako całkowite piksele – BitCell zostaje ostry.
        val p1 = max(2f, floor(s / 2f))
        val p2 = (p1 * 4f / 3f).roundToInt().toFloat()
        val p3 = p1 * 2f
        val margin = 4f * p1
        val top = topInset + margin

        // Lewa strona: „Lv” i numer jak w oryginale, życia, etap.
        val lvWidth = text.width("Lv", p2) + 2 * p1
        text.draw(canvas, "Lv", margin, top + 7 * p3 - 7 * p2, p2, PixelText.LEVEL_BLUE, outline = PixelText.WHITE)
        text.draw(canvas, world.spec.number.toString(), margin + lvWidth, top, p3, PixelText.WHITE)

        val lifeTop = top + 7 * p3 + 4 * p1
        val lifeIcon = Sprites.boat(look.boat, "ship")
        val lifeStep = lifeIcon.width * p1 + p1
        for (i in 0 until world.maxLives) {
            icon(canvas, lifeIcon, margin + i * lifeStep, lifeTop, p1, if (i < world.lives) 255 else 70)
        }
        if (world.shield) icon(canvas, Sprites.boat("navy", "ship"), margin + world.maxLives * lifeStep, lifeTop, p1)

        val stageTop = lifeTop + lifeIcon.height * p1 + 3 * p1
        val door = currentTiles.doors[0]
        val doorK = max(1f, floor(p1 * 0.67f))
        icon(canvas, door, margin, stageTop, doorK)
        text.draw(
            canvas, "${world.stageIndex + 1}/${world.stages.size}", margin + door.width * doorK + 2 * p1,
            stageTop + (door.height * doorK - 7 * p2) / 2f, p2, PixelText.WHITE,
        )

        // Prawa strona (miejsce na przycisk pauzy zostaje): wynik i licznik ryb etapu.
        val right = width - margin - 20 * p2
        val scoreIcon = Sprites.sprite("fishscore")
        icon(canvas, scoreIcon, right - scoreIcon.width * p1 * 1.5f, top, p1 * 1.5f)
        val scoreRight = right - scoreIcon.width * p1 * 1.5f - 2 * p1
        text.draw(canvas, world.score.toString(), scoreRight, top, p3, PixelText.WHITE, align = 1)

        val quotaTop = top + 7 * p3 + 4 * p1
        val stage = world.currentStage
        val done = world.isDoorOpen(world.stageIndex)
        val caught = if (done) stage.quota else world.caughtInCurrentStage
        val qIcon = Sprites.sprite("fish1")
        icon(canvas, qIcon, right - qIcon.width * p2, quotaTop - p1, p2)
        text.draw(canvas, "$caught/${stage.quota}", right - qIcon.width * p2 - 2 * p1, quotaTop, p2, if (done) PixelText.GOLD else PixelText.WHITE, align = 1)
        if (world.combo >= 2 && world.comboTimer > 0f) {
            text.draw(canvas, "x${world.combo}", scoreRight, quotaTop + 7 * p2 + 4 * p1, p2, PixelText.GOLD, align = 1)
        }

        // Środek: w wyścigu z czasem odliczanie, w pozostałych – czas gry (blednie po przekroczeniu wzorca).
        val limit = world.timeLimit
        if (limit > 0) {
            val left = max(0f, limit - world.elapsed)
            text.draw(canvas, formatTime(left), width / 2f, top, p3, if (left < 10f) PixelText.RED else PixelText.WHITE, align = 0)
        } else {
            val color = if (world.elapsed <= world.spec.parSeconds) PixelText.WHITE else PixelText.PALE
            text.draw(canvas, formatTime(world.elapsed), width / 2f, top, p2, color, align = 0)
        }
    }

    private fun formatTime(seconds: Float): String {
        val total = seconds.toInt()
        return "%d:%02d".format(total / 60, total % 60)
    }

    private fun drawMessage(canvas: Canvas, world: GameWorld, width: Int, height: Int, s: Float) {
        val msg = world.message ?: return
        val label = when (msg.kind) {
            MessageKind.GO -> strings.go
            MessageKind.STAGE -> strings.stage.format(msg.arg, msg.arg2)
            MessageKind.GATE_OPEN -> strings.gateOpen
            MessageKind.FINISH_OPEN -> strings.finishOpen
            MessageKind.FINISH -> strings.finish
            MessageKind.LAST_LIFE -> strings.lastLife
            MessageKind.SHIELD -> strings.shield
            MessageKind.TIME_LOW -> strings.timeLow
            MessageKind.COMBO -> strings.combo.format(msg.arg)
        }
        // Sprężynujące powiększenie jak w oryginale, a na końcu szybkie zniknięcie.
        val age = msg.age
        val outStart = GameWorld.MESSAGE_TIME - 0.3f
        val k = if (age < outStart) 1f + exp(-5f * age) * cos(13f * age) * 0.9f
        else max(0f, 1f - (age - outStart) / 0.3f)
        val base = max(3f, s * 0.75f)
        var p = base * k
        val w = text.width(label, p)
        if (w > width * 0.92f) p *= width * 0.92f / w
        if (p < 0.5f) return
        val color = when (msg.kind) {
            MessageKind.LAST_LIFE, MessageKind.TIME_LOW -> PixelText.RED
            MessageKind.COMBO, MessageKind.FINISH, MessageKind.FINISH_OPEN -> PixelText.GOLD
            else -> PixelText.WHITE
        }
        text.draw(canvas, label, width / 2f, height * 0.3f - 3.5f * p, p, color, align = 0)
    }

    private fun drawTutorial(canvas: Canvas, width: Int, height: Int, s: Float, t: Float) {
        val huit = Sprites.sprite("huit")
        val k = s
        val cx = width / 2f
        val cy = height * 0.72f
        paint.alpha = 128
        rect.set(cx - huit.width * k / 2f, cy - huit.height * k / 2f, cx + huit.width * k / 2f, cy + huit.height * k / 2f)
        canvas.drawBitmap(huit, null, rect, paint)
        paint.alpha = 255
        val phase = (t % 2f) / 2f * 2f * PI.toFloat()
        val hw = huit.width * k
        val fx = cx + sin(phase) * hw * 35f / 80f + hw * 5f / 80f
        val fy = cy - (sin(phase * 2f) * hw * 15f / 80f - hw * 10f / 80f)
        val doigt = Sprites.sprite("doigt")
        val dk = (k * 0.75f).roundToInt().toFloat()
        rect.set(fx - doigt.width * dk / 2f, fy - doigt.height * dk / 2f, fx + doigt.width * dk / 2f, fy + doigt.height * dk / 2f)
        canvas.drawBitmap(doigt, null, rect, paint)
        val p = max(2f, floor(s / 2f))
        text.draw(canvas, strings.tutorial, cx, cy + huit.height * k / 2f + 10 * p, p * 1.5f, PixelText.WHITE, align = 0)
    }

    companion object {
        /** Całkowita skala pikseli – pokój ma 11 kafelków (176 px) szerokości. */
        fun scaleFor(width: Int): Int = max(2, width / 176)
    }
}
