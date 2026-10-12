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
import wojtoteka.ovh.fishingparty.gfx.TileSet
import wojtoteka.ovh.fishingparty.level.EnemyKind
import wojtoteka.ovh.fishingparty.level.FishKind
import wojtoteka.ovh.fishingparty.level.LevelConstants.TILE
import wojtoteka.ovh.fishingparty.level.LevelSpec
import wojtoteka.ovh.fishingparty.level.LevelType
import wojtoteka.ovh.fishingparty.level.Worlds
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

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
    /** Podpowiedź samouczka dla sterowania joystickiem. */
    val tutorialJoystick: String = tutorial,
    /** Napis na start Wielkiego Finału i nazwy światów jego kolejnych etapów. */
    val finale: String = "",
    val finaleStages: List<String> = emptyList(),
)

/**
 * Stan sterowania dotykiem w pikselach ekranu. Domyślnie pływający punkt (przesuwanie
 * palcem po całym ekranie), a w trybie [joystick] – stała baza w dolnym rogu.
 */
class TouchStick {
    var active = false
    var originX = 0f
    var originY = 0f
    var x = 0f
    var y = 0f
    var engaged = false
    var angle = 0f

    /** Tryb joysticka i jego strona ekranu. */
    var joystick = false
    var left = true

    /** Miejsce spoczynku bazy joysticka, jej promień i ekran – ustawiane co klatkę przez [layout]. */
    var homeX = 0f
    var homeY = 0f
    var radius = 0f
    private var margin = 0f
    private var width = 0f

    fun layout(width: Float, height: Float, bottomInset: Float, margin: Float, radius: Float) {
        this.width = width
        this.margin = margin
        this.radius = radius
        homeX = if (left) margin + radius else width - margin - radius
        homeY = height - bottomInset - margin - radius
    }

    /**
     * Początek dotyku. Joystick przeskakuje pod palec w dowolnej połowie ekranu (jak w popularnych
     * grach mobilnych), ale cała baza zostaje na ekranie i nie schodzi niżej niż jej miejsce spoczynku.
     * Strona z ustawień decyduje tylko o tym, gdzie baza czeka, gdy nikt jej nie dotyka.
     */
    fun press(touchX: Float, touchY: Float) {
        active = true
        engaged = false
        x = touchX
        y = touchY
        if (joystick) {
            val edge = margin / 2f + radius
            originX = touchX.coerceIn(edge, max(edge, width - edge))
            originY = touchY.coerceIn(edge, max(edge, homeY))
        } else {
            originX = touchX
            originY = touchY
        }
    }
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
    private val smooth = Paint().apply { isAntiAlias = true }
    private val rect = RectF()
    private var themeSpec: LevelSpec? = null
    private var currentTiles = Sprites.tiles(Worlds.theme(0))

    /** Paleta każdego wiersza – w Wielkim Finale każdy etap leży w innym świecie (null = jedna paleta). */
    private var rowWorlds: IntArray? = null
    private var rowTiles: Array<TileSet>? = null

    /** Skala: ile pikseli ekranu na piksel spritu. */
    var scale = 6
        private set

    fun draw(canvas: Canvas, world: GameWorld, width: Int, height: Int, topInset: Float, stick: TouchStick) {
        if (themeSpec !== world.spec) {
            themeSpec = world.spec
            currentTiles = Sprites.tiles(Worlds.theme(world.spec.world))
            rowWorlds = if (world.spec.type == LevelType.FINALE) finaleRowWorlds(world) else null
            rowTiles = rowWorlds?.let { rows -> Array(rows.size) { Sprites.tiles(Worlds.theme(rows[it])) } }
        }
        scale = scaleFor(width, height)
        val s = scale.toFloat()
        val viewW = width / s
        val viewH = height / s
        world.setViewport(viewW, viewH)
        val camX = (world.camX * s).roundToInt() / s
        val camY = (world.camY * s).roundToInt() / s
        val t = world.animTime

        val centerRow = ((camY + viewH / 2f) / TILE).toInt()
        canvas.drawColor(Worlds.seaColor(rowWorlds?.let { it[centerRow.coerceIn(0, it.size - 1)] } ?: world.spec.world))
        canvas.save()
        canvas.scale(s, s)
        canvas.translate(-camX, -camY)

        tiles.draw(
            canvas, world.grid, camX, camY, viewW, viewH, t,
            tilesForRow = { r -> tilesAt(r) },
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
        if (stick.joystick) drawJoystick(canvas, stick, s, t, hint = world.showControls) else drawStick(canvas, stick, s)
        drawHud(canvas, world, width, topInset, s)
        drawMessage(canvas, world, width, height, s)
        if (world.showControls) {
            if (stick.joystick) drawJoystickTutorial(canvas, stick, width, s) else drawTutorial(canvas, width, height, s, t)
        }
        if (world.hitFlash > 0f) {
            fill.color = 0xFFFF0000.toInt()
            fill.alpha = (world.hitFlash * 110).toInt().coerceIn(0, 255)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        }
    }

    private fun tilesAt(row: Int): TileSet = rowTiles?.let { it[row.coerceIn(0, it.size - 1)] } ?: currentTiles

    /**
     * Świat (paleta) każdego wiersza Wielkiego Finału: etap razem z bramą nad nim ma
     * barwy swojego świata, a port na mecie – złote barwy finału.
     */
    private fun finaleRowWorlds(world: GameWorld): IntArray {
        val rows = IntArray(world.grid.height) { Worlds.FINALE }
        for ((k, stage) in world.stages.withIndex()) {
            val w = Worlds.FINALE_STAGE_WORLDS[k % Worlds.FINALE_STAGE_WORLDS.size]
            for (r in stage.gateRow..stage.bottomRow) rows[r] = w
        }
        rows[rows.lastIndex] = Worlds.FINALE_STAGE_WORLDS[0]
        return rows
    }

    private fun drawCrabs(canvas: Canvas, world: GameWorld, t: Float) {
        for ((i, pos) in world.layout.crabs.withIndex()) {
            val (c, r) = pos
            val walk = sin(t * 1.3f + i * 2.1f) * 3f
            val x = c * TILE + 8f + walk
            val y = r * TILE + 8f
            ship.sprite(canvas, tilesAt(r).crab, x, y, if ((t * 4f + i).toInt() % 2 == 0) -8f else 8f)
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

    /** Stały joystick: półprzezroczysta baza i gałka, która idzie za palcem (najdalej do krawędzi bazy). */
    private fun drawJoystick(canvas: Canvas, stick: TouchStick, s: Float, t: Float, hint: Boolean) {
        val r = stick.radius
        if (r <= 0f) return
        val line = max(2f, floor(s / 2f))
        val baseX = if (stick.active) stick.originX else stick.homeX
        val baseY = if (stick.active) stick.originY else stick.homeY
        var knobX = baseX
        var knobY = baseY
        if (stick.active) {
            val dx = stick.x - stick.originX
            val dy = stick.y - stick.originY
            val d = sqrt(dx * dx + dy * dy)
            val k = if (d > r) r / d else 1f
            knobX += dx * k
            knobY += dy * k
        } else if (hint) {
            // Samouczek: gałka sama krąży, pokazując, że trzeba ją przesunąć.
            val phase = (t % 2f) / 2f * 2f * PI.toFloat()
            knobX += cos(phase) * r * 0.55f
            knobY += sin(phase) * r * 0.55f
        }
        val strong = stick.active || hint

        smooth.style = Paint.Style.FILL
        smooth.color = JOY_DARK
        smooth.alpha = if (strong) 110 else 70
        canvas.drawCircle(baseX, baseY, r, smooth)
        smooth.style = Paint.Style.STROKE
        smooth.strokeWidth = line
        smooth.color = JOY_LIGHT
        smooth.alpha = if (strong) 190 else 110
        canvas.drawCircle(baseX, baseY, r - line / 2f, smooth)

        if (stick.active && stick.engaged) {
            // Strzałka na krawędzi bazy pokazuje kierunek płynięcia.
            val arrow = Sprites.sprite("arrowup")
            val k = max(2f, (s * 0.5f).roundToInt().toFloat())
            paint.alpha = 170
            canvas.save()
            canvas.translate(baseX, baseY)
            canvas.rotate(Math.toDegrees(stick.angle.toDouble()).toFloat() + 90f)
            rect.set(-arrow.width * k / 2f, -r - arrow.height * k - line, arrow.width * k / 2f, -r - line)
            canvas.drawBitmap(arrow, null, rect, paint)
            canvas.restore()
            paint.alpha = 255
        }

        val knob = r * 0.45f
        smooth.style = Paint.Style.FILL
        smooth.color = JOY_LIGHT
        smooth.alpha = if (strong) 220 else 140
        canvas.drawCircle(knobX, knobY, knob, smooth)
        smooth.style = Paint.Style.STROKE
        smooth.color = JOY_DARK
        smooth.alpha = if (strong) 230 else 150
        canvas.drawCircle(knobX, knobY, knob - line / 2f, smooth)

        if (hint && !stick.active) {
            val doigt = Sprites.sprite("doigt")
            val dk = (s * 0.75f).roundToInt().toFloat()
            rect.set(knobX - doigt.width * dk / 2f, knobY - doigt.height * dk / 2f, knobX + doigt.width * dk / 2f, knobY + doigt.height * dk / 2f)
            canvas.drawBitmap(doigt, null, rect, paint)
        }
    }

    /** Napis samouczka nad joystickiem. */
    private fun drawJoystickTutorial(canvas: Canvas, stick: TouchStick, width: Int, s: Float) {
        val p = max(2f, floor(s / 2f))
        val y = stick.homeY - stick.radius - 16 * p
        text.draw(canvas, strings.tutorialJoystick, width / 2f, y, p * 1.5f, PixelText.WHITE, align = 0)
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
        val door = tilesAt(world.currentStage.gateRow).doors[0]
        val doorK = max(1f, floor(p1 * 0.67f))
        icon(canvas, door, margin, stageTop, doorK)
        text.draw(
            canvas, "${world.stageIndex + 1}/${world.stages.size}", margin + door.width * doorK + 2 * p1,
            stageTop + (door.height * doorK - 7 * p2) / 2f, p2, PixelText.WHITE,
        )

        // Prawa strona (miejsce na przycisk pauzy zostaje): wynik i licznik ryb etapu.
        val right = width - margin - 40 * p1
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
            MessageKind.FINALE -> strings.finale
            MessageKind.STAGE -> strings.finaleStages.getOrNull(msg.arg - 1) ?: strings.stage.format(msg.arg, msg.arg2)
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
            MessageKind.COMBO, MessageKind.FINISH, MessageKind.FINISH_OPEN, MessageKind.FINALE -> PixelText.GOLD
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
        private const val JOY_DARK = 0xFF0A1E38.toInt()
        private const val JOY_LIGHT = 0xFFE3ECFF.toInt()

        /**
         * Całkowita skala pikseli – pokój ma 11 kafelków (176 px) szerokości. Na niskim ekranie
         * (tablet w poziomie) skala maleje, żeby z przodu łodzi było widać co najmniej 15 kafelków,
         * a rzeka staje na środku. Na telefonie w pionie decyduje sama szerokość.
         */
        fun scaleFor(width: Int, height: Int): Int = max(2, min(width / 176, height / 240))
    }
}
