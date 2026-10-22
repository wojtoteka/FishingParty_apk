package wojtoteka.ovh.fishingparty.game

import wojtoteka.ovh.fishingparty.core.GuardedLong
import wojtoteka.ovh.fishingparty.core.Rng
import wojtoteka.ovh.fishingparty.level.EnemyKind
import wojtoteka.ovh.fishingparty.level.FishKind
import wojtoteka.ovh.fishingparty.level.LevelConstants
import wojtoteka.ovh.fishingparty.level.LevelConstants.TILE
import wojtoteka.ovh.fishingparty.level.LevelLayout
import wojtoteka.ovh.fishingparty.level.LevelType
import wojtoteka.ovh.fishingparty.level.RunResult
import wojtoteka.ovh.fishingparty.level.Scoring
import wojtoteka.ovh.fishingparty.level.Stalkers
import wojtoteka.ovh.fishingparty.level.StageLayout
import wojtoteka.ovh.fishingparty.level.Tile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

interface GameListener {
    fun onSound(sfx: Sfx)
    fun onVibrate(millis: Long)

    /** Wibracja z rytmem (melodyjka wygranej i przegranej); bez obsługi – jeden impuls tej samej długości. */
    fun onRumble(rumble: Rumble) = onVibrate(rumble.millis)
    fun onFinished(result: RunResult, reason: LoseReason?)
}

/**
 * Cała symulacja jednego poziomu. Jednostka świata = 1 piksel spritu,
 * kafelek ma 16 jednostek. Oś Y rośnie w dół, łódź płynie „w górę” mapy.
 */
class GameWorld(
    val layout: LevelLayout,
    val loadout: Loadout,
    val tutorial: Boolean,
    private val listener: GameListener,
) {
    val spec = layout.spec
    val grid = layout.grid
    val stages: List<StageLayout> = layout.stages
    private val rng = Rng(Rng.mix(spec.seed, 0x5EA))

    // ---------------------------------------------------------------- łódź
    var shipX = layout.startCol * TILE + TILE / 2f
    var shipY = layout.startRow * TILE + TILE / 2f
    var heading = (-PI / 2).toFloat()
    var speed = 0f
    private var targetHeading = heading
    private var thrust = false
    private var knockX = 0f
    private var knockY = 0f
    private var bumpCooldown = 0f

    /**
     * Łódź wpłynęła na piasek przy brzegu. Stoi tam prawie w miejscu (może się obrócić i wypłynąć),
     * ale potwory jej nie dosięgną, a widmokuda jej nie widzi – awaryjna kryjówka.
     */
    var beached = false
        private set
    val maxSpeed = if (loadout.turbo) SHIP_SPEED * 1.2f else SHIP_SPEED
    private val catchRadius = if (loadout.net) NET_CATCH_RADIUS else CATCH_RADIUS

    /** Czas na złowienie kolejnej ryby, żeby combo trwało (Rytm go wydłuża). */
    val comboWindow = if (loadout.rhythm) Scoring.COMBO_WINDOW_SECONDS * RHYTHM_FACTOR else Scoring.COMBO_WINDOW_SECONDS

    var lives = if (loadout.extraLife) 4 else 3
        private set
    val maxLives = lives
    var shield = loadout.shield
        private set
    var invulnerable = 0f
        private set
    var hitFlash = 0f
        private set

    // ---------------------------------------------------------------- stan
    var phase = Phase.PLAYING
        private set
    private var phaseTimer = 0f
    var stageIndex = 0
        private set
    private val cleared = BooleanArray(stages.size)
    private val doorOpen = BooleanArray(stages.size)
    private val caughtInStage = IntArray(stages.size)
    private val stageEnter = FloatArray(stages.size)
    private val goldenSpawned = Array(stages.size) { BooleanArray(stages[it].golden.size) }
    private val spawnedStages = BooleanArray(stages.size)

    val fish = ArrayList<Fish>()
    private val pendingFish = ArrayList<Fish>()
    private val pendingEnemies = ArrayList<Enemy>()
    val caught = ArrayList<CaughtFish>()
    val enemies = ArrayList<Enemy>()
    val popups = ArrayList<Popup>()
    var message: GameMessage? = null
        private set

    private val scoreValue = GuardedLong(0)
    val score: Int get() = scoreValue.get().toInt()
    var combo = 0
        private set
    var comboTimer = 0f
        private set
    private var lastCatch = -10f
    var maxCombo = 0
        private set
    private var commonCaught = 0
    private var blueCaught = 0
    private var goldenCaught = 0
    var hits = 0
        private set
    var stagesCleared = 0
        private set

    /** Czas gry (bez pauz) w sekundach. */
    var elapsed = 0f
        private set
    private var finishTime = 0f
    private var timeBonusExtra = 0
    var continuesUsed = 0
        private set
    var loseReason: LoseReason? = null
        private set

    /** Czas animacji (płynie też w trakcie animacji końcowych). */
    var animTime = 0f
        private set
    var paused = false

    var showControls = tutorial
        private set
    private var movedTime = 0f
    var showGateArrow = false
        private set

    /** Czy któraś widmokuda już namierzyła łódź w tym przejeździe. */
    private var stalkerSpotted = false

    // ---------------------------------------------------------------- kamera
    var camX = 0f
        private set
    var camY = 0f
        private set
    private var viewW = 180f
    private var viewH = 390f
    private var cameraReady = false

    private var accumulator = 0f

    val currentStage: StageLayout get() = stages[stageIndex]
    val caughtInCurrentStage: Int get() = caughtInStage[stageIndex]
    val timeLimit: Int get() = if (spec.timeLimitSeconds > 0) spec.timeLimitSeconds + timeBonusExtra else 0

    init {
        spawnStage(0, fadeIn = 1.6f)
        flushSpawns()
        stageEnter[0] = 0f
        // Wielki Finał zaczyna się od własnego napisu, polecenie łowienia pojawia się po nim.
        message = if (spec.type == LevelType.FINALE) GameMessage(MessageKind.FINALE, 0, 0)
        else GameMessage(MessageKind.GO, stages[0].quota, 0)
        openEmptyGate()
    }

    /** Etap bez ryb do złowienia (poziomy z kreatora) – brama otwiera się od razu po wpłynięciu. */
    private fun openEmptyGate() {
        if (!cleared[stageIndex] && stages[stageIndex].quota <= 0) openGate(stageIndex)
    }

    fun setViewport(width: Float, height: Float) {
        viewW = width
        viewH = height
        if (!cameraReady) {
            updateCamera(1f, snap = true)
            cameraReady = true
        }
    }

    fun setInput(active: Boolean, angle: Float) {
        thrust = active
        if (active) targetHeading = angle
    }

    fun isDoorOpen(stage: Int) = doorOpen[stage]

    fun stageOfGateRow(row: Int): Int = stages.indexOfFirst { it.gateRow == row }

    /** Wywoływane raz na klatkę – symulacja idzie stałym krokiem. */
    fun advance(frameSeconds: Float) {
        if (paused) return
        accumulator = min(accumulator + frameSeconds, STEP * MAX_STEPS)
        while (accumulator >= STEP) {
            step(STEP)
            accumulator -= STEP
        }
    }

    private fun step(dt: Float) {
        animTime += dt
        when (phase) {
            Phase.PLAYING -> {
                elapsed += dt
                updateShip(dt, controlled = true)
                updateFish(dt)
                updateEnemies(dt)
                updateGolden()
                checkGates()
                checkTime()
            }
            Phase.WINNING -> {
                targetHeading = (-PI / 2).toFloat()
                thrust = true
                updateShip(dt, controlled = false)
                updateFish(dt)
                phaseTimer -= dt
                if (phaseTimer <= 0f) {
                    phase = Phase.WON
                    listener.onFinished(buildResult(true), null)
                }
            }
            Phase.LOSING -> {
                speed *= 0.9f
                phaseTimer -= dt
                if (phaseTimer <= 0f) {
                    phase = Phase.LOST
                    listener.onFinished(buildResult(false), loseReason)
                }
            }
            Phase.WON, Phase.LOST -> Unit
        }
        flushSpawns()
        updateEffects(dt)
        updateCamera(dt, snap = false)
    }

    /** Nowe ryby i wrogowie trafiają do list dopiero po zakończeniu kroku (nie w trakcie iteracji). */
    private fun flushSpawns() {
        if (pendingFish.isNotEmpty()) {
            fish += pendingFish
            pendingFish.clear()
        }
        if (pendingEnemies.isNotEmpty()) {
            enemies += pendingEnemies
            pendingEnemies.clear()
        }
    }

    // ================================================================ łódź

    private fun updateShip(dt: Float, controlled: Boolean) {
        val ticks = dt * 60f
        beached = beachDepth(shipX, shipY) > 0f
        if (thrust) {
            var diff = targetHeading - heading
            while (diff > PI) diff -= (2 * PI).toFloat()
            while (diff < -PI) diff += (2 * PI).toFloat()
            heading += diff * (1f - (1f - TURN_RATE).pow(ticks))
            while (heading > PI) heading -= (2 * PI).toFloat()
            while (heading < -PI) heading += (2 * PI).toFloat()
        }
        // Na piasku łódź nie ma napędu – wpływa na niego tylko rozpędem i szybko go traci. Napęd wraca, gdy dziób
        // celuje w wodę: można się obrócić i wypłynąć, ale nie da się płynąć po piasku.
        if (thrust && !(beached && sandAhead())) {
            val accel = if (speed < maxSpeed * 0.66f) 0.05f else 0.02f
            speed += (maxSpeed - speed) * (1f - (1f - accel).pow(ticks))
            if (beached && speed > BEACH_EXIT_SPEED) speed += (BEACH_EXIT_SPEED - speed) * (1f - 0.8f.pow(ticks))
        } else {
            speed += (0f - speed) * (1f - (if (beached) BEACH_DRAG else 0.95f).pow(ticks))
        }
        if (controlled && showControls) {
            if (speed > 30f) movedTime += dt
            if (movedTime > 1.2f) showControls = false
        }

        val ownX = cos(heading) * speed * dt
        val ownY = sin(heading) * speed * dt
        var dx = ownX + knockX * dt
        var dy = ownY + knockY * dt
        // Prąd nie wpycha łodzi na piasek (na plażę wpływa się tylko samemu), a łodzi na piasku już nie znosi.
        if (phase == Phase.PLAYING && !beached) {
            // Poziom z kreatora może mieć inny prąd w każdym etapie.
            val stage = stages[stageIndex]
            val cx = (stage.currentX ?: spec.currentX) * dt
            val cy = (stage.currentY ?: spec.currentY) * dt
            val own = beachDepth(shipX + dx, shipY + dy)
            if (beachDepth(shipX + dx + cx, shipY + dy) <= own) dx += cx
            if (beachDepth(shipX + dx, shipY + dy + cy) <= own) dy += cy
        }
        knockX *= 0.9f.pow(ticks)
        knockY *= 0.9f.pow(ticks)
        bumpCooldown -= dt

        val nx = shipX + dx
        val ny = shipY + dy
        when {
            !shipBlocked(nx, ny) -> {
                shipX = nx; shipY = ny
            }
            !shipBlocked(nx, shipY) -> {
                shipX = nx; bump(abs(dy) / dt, intoWall = ownY * dy > 0f)
            }
            !shipBlocked(shipX, ny) -> {
                shipY = ny; bump(abs(dx) / dt, intoWall = ownX * dx > 0f)
            }
            // Prąd wciska łódź w narożnik – sama łódź (bez prądu) może jeszcze z niego wypłynąć, choćby bokiem.
            !shipBlocked(shipX + ownX, shipY + ownY) -> {
                shipX += ownX; shipY += ownY
            }
            !shipBlocked(shipX + ownX, shipY) -> shipX += ownX
            !shipBlocked(shipX, shipY + ownY) -> shipY += ownY
            else -> bump(speed)
        }
    }

    /**
     * Otarcie o brzeg. Łódź zwalnia tylko wtedy, gdy sama płynie w przeszkodę – gdy dociska ją
     * prąd morski, a gracz odpływa od brzegu, musi móc się rozpędzić (inaczej utknęłaby przy ścianie).
     */
    private fun bump(impactSpeed: Float, intoWall: Boolean = true) {
        if (impactSpeed > 35f && bumpCooldown <= 0f) {
            listener.onSound(Sfx.BUMP)
            bumpCooldown = 0.5f
        }
        if (intoWall) speed *= 0.6f
    }

    private fun solidForShip(c: Int, r: Int): Boolean {
        return when (grid[c, r]) {
            Tile.SEA -> false
            Tile.DOOR -> {
                val s = stageOfGateRow(r)
                s < 0 || !doorOpen[s]
            }
            else -> true
        }
    }

    /** Piasek nie zatrzymuje łodzi od razu – może na niego wpłynąć na [BEACH_REACH] od wody. */
    private fun shipBlocked(x: Float, y: Float): Boolean {
        if (beachDepth(x, y) > BEACH_REACH) return true
        val c0 = floor((x - SHIP_RADIUS) / TILE).toInt()
        val c1 = floor((x + SHIP_RADIUS) / TILE).toInt()
        val r0 = floor((y - SHIP_RADIUS) / TILE).toInt()
        val r1 = floor((y + SHIP_RADIUS) / TILE).toInt()
        for (r in r0..r1) for (c in c0..c1) {
            if (!solidForShip(c, r) || grid[c, r] == Tile.SAND) continue
            val nearestX = x.coerceIn(c * TILE.toFloat(), (c + 1) * TILE.toFloat())
            val nearestY = y.coerceIn(r * TILE.toFloat(), (r + 1) * TILE.toFloat())
            val ddx = x - nearestX
            val ddy = y - nearestY
            if (ddx * ddx + ddy * ddy < SHIP_RADIUS * SHIP_RADIUS) return true
        }
        return false
    }

    /** Czy punkt kawałek przed dziobem to piasek – wtedy łódź stojąca na piasku nie dostaje napędu. */
    private fun sandAhead() = beachDepth(shipX + cos(heading) * BEACH_LOOK, shipY + sin(heading) * BEACH_LOOK) > 0f

    /** Jak głęboko na piasku jest punkt: odległość do najbliższej wody, 0 – punkt jest na wodzie. */
    private fun beachDepth(x: Float, y: Float): Float {
        val c = floor(x / TILE).toInt()
        val r = floor(y / TILE).toInt()
        if (grid[c, r] != Tile.SAND) return 0f
        var best = Float.MAX_VALUE
        for (rr in r - 1..r + 1) for (cc in c - 1..c + 1) {
            if (solidForShip(cc, rr)) continue
            val ddx = x - x.coerceIn(cc * TILE.toFloat(), (cc + 1) * TILE.toFloat())
            val ddy = y - y.coerceIn(rr * TILE.toFloat(), (rr + 1) * TILE.toFloat())
            best = min(best, sqrt(ddx * ddx + ddy * ddy))
        }
        return best
    }

    // ================================================================ ryby

    private fun spawnStage(k: Int, fadeIn: Float) {
        if (spawnedStages[k]) return
        spawnedStages[k] = true
        val stage = stages[k]
        for (s in stage.fish) {
            pendingFish += Fish(
                x = s.col * TILE + TILE / 2f,
                y = s.row * TILE + TILE / 2f,
                kind = s.kind,
                stage = k,
                facing = rng.sign(),
                vertical = rng.sign(),
                phase = rng.nextFloat() * 0.4f,
            )
        }
        for (s in stage.enemies) {
            val horizontal = s.kind == EnemyKind.PIRANHA || s.kind == EnemyKind.RED_PIRANHA
            val vertical = s.kind == EnemyKind.PIRANHA_VERTICAL
            // Losujemy zawsze, także przy kierunku z kreatora – kolejne losowania zostają te same.
            val rolled = rng.nextInt(4)
            val randomX = if (horizontal) rng.sign() else 0
            val randomY = if (vertical) rng.sign() else 0
            val dir = if (s.dir in 0..3) s.dir else rolled
            val fixed = s.dir in 0..3
            pendingEnemies += Enemy(
                kind = s.kind,
                x = s.col * TILE + TILE / 2f,
                y = s.row * TILE + TILE / 2f,
                stage = k,
                dirX = when {
                    fixed -> DIR_X[dir]
                    horizontal -> randomX
                    vertical -> 0
                    else -> DIR_X[dir]
                },
                dirY = when {
                    fixed -> DIR_Y[dir]
                    horizontal -> 0
                    vertical -> randomY
                    else -> DIR_Y[dir]
                },
                phase = rng.nextFloat() * 0.4f,
            ).also {
                it.fadeIn = fadeIn
                it.vx = it.dirX.toFloat()
                it.vy = it.dirY.toFloat()
            }
        }
    }

    private fun waterFor(x: Float, y: Float, stage: StageLayout): Boolean {
        val c = floor(x / TILE).toInt()
        val r = floor(y / TILE).toInt()
        return r >= stage.topRow && r <= stage.bottomRow && grid[c, r] == Tile.SEA
    }

    private fun updateFish(dt: Float) {
        val it = fish.iterator()
        while (it.hasNext()) {
            val f = it.next()
            val stage = stages[f.stage]
            val dx = f.x - shipX
            val dy = f.y - shipY
            val dist = sqrt(dx * dx + dy * dy)

            if (loadout.magnet && dist < MAGNET_RADIUS && f.stage == stageIndex && phase == Phase.PLAYING) {
                val pull = MAGNET_SPEED * dt / max(dist, 1f)
                f.x -= dx * pull
                f.y -= dy * pull
            } else {
                when (f.kind) {
                    FishKind.COMMON -> {
                        if (!waterFor(f.x + f.facing * 6f, f.y, stage)) f.facing = -f.facing
                        else f.x += f.facing * FISH_SPEED * dt
                    }
                    FishKind.BLUE -> {
                        if (!waterFor(f.x + f.facing * 6f, f.y, stage)) f.facing = -f.facing
                        else f.x += f.facing * BLUE_SPEED * dt
                        if (!waterFor(f.x, f.y + f.vertical * 6f, stage)) f.vertical = -f.vertical
                        else f.y += f.vertical * BLUE_SPEED * 0.5f * dt
                    }
                    FishKind.GOLDEN -> {
                        f.life -= dt
                        if (f.life <= 0f) {
                            it.remove()
                            continue
                        }
                        var vx: Float
                        var vy: Float
                        if (dist < (if (loadout.lure) LURE_FLEE_RADIUS else GOLDEN_FLEE_RADIUS)) {
                            vx = dx / max(dist, 1f) * GOLDEN_FLEE_SPEED
                            vy = dy / max(dist, 1f) * GOLDEN_FLEE_SPEED
                            f.facing = if (vx >= 0) 1 else -1
                        } else {
                            vx = f.facing * GOLDEN_SPEED
                            vy = sin(animTime * 2f + f.phase * 10f) * 8f
                        }
                        if (waterFor(f.x + vx * dt + sign(vx) * 5f, f.y, stage)) f.x += vx * dt else f.facing = -f.facing
                        if (waterFor(f.x, f.y + vy * dt + sign(vy) * 5f, stage)) f.y += vy * dt
                    }
                }
            }

            if (phase == Phase.PLAYING && dist < catchRadius) {
                it.remove()
                catchFish(f)
            }
        }
    }

    private fun sign(v: Float) = if (v > 0f) 1f else if (v < 0f) -1f else 0f

    private fun catchFish(f: Fish) {
        combo = if (elapsed - lastCatch <= comboWindow && combo > 0) {
            min(LevelConstants.MAX_COMBO, combo + 1)
        } else 1
        lastCatch = elapsed
        comboTimer = comboWindow
        maxCombo = max(maxCombo, combo)
        val points = Scoring.fishPoints(f.kind, combo)
        scoreValue.add(points.toLong())
        when (f.kind) {
            FishKind.COMMON -> commonCaught++
            FishKind.BLUE -> blueCaught++
            FishKind.GOLDEN -> goldenCaught++
        }
        caughtInStage[f.stage]++
        popups += Popup(f.x, f.y - 6f, points, f.kind == FishKind.GOLDEN)
        caught += CaughtFish(f.x, f.y, CAUGHT_ANIM)
        when {
            f.kind == FishKind.GOLDEN -> {
                listener.onSound(Sfx.FISH_GOLD)
                listener.onVibrate(20)
            }
            combo >= 2 -> listener.onSound(Sfx.COMBO)
            else -> listener.onSound(Sfx.FISH)
        }
        if (combo >= 3) message = GameMessage(MessageKind.COMBO, combo, 0)
        if (f.stage == stageIndex && !cleared[stageIndex] && caughtInStage[stageIndex] >= stages[stageIndex].quota) {
            openGate(stageIndex)
        }
    }

    private fun openGate(k: Int) {
        cleared[k] = true
        doorOpen[k] = true
        stagesCleared++
        scoreValue.add(LevelConstants.STAGE_BONUS.toLong())
        val last = k == stages.lastIndex
        message = GameMessage(if (last) MessageKind.FINISH_OPEN else MessageKind.GATE_OPEN, 0, 0)
        showGateArrow = true
        listener.onSound(Sfx.GATE_OPEN)
        listener.onVibrate(30)
        if (!last) spawnStage(k + 1, fadeIn = 1.2f)
    }

    private fun updateGolden() {
        val k = stageIndex
        val stage = stages[k]
        for (i in stage.golden.indices) {
            if (goldenSpawned[k][i]) continue
            val g = stage.golden[i]
            val delay = if (loadout.lure) g.delaySeconds * LURE_DELAY_FACTOR else g.delaySeconds
            if (elapsed - stageEnter[k] < delay) continue
            goldenSpawned[k][i] = true
            var gx = g.col * TILE + TILE / 2f
            var gy = g.row * TILE + TILE / 2f
            if (hypot(gx - shipX, gy - shipY) < 60f && stage.reachable.isNotEmpty()) {
                // Nie pojawiamy się tuż pod nosem łodzi – szukamy dalszego pola.
                for (attempt in 0 until 10) {
                    val cell = stage.reachable[rng.nextInt(stage.reachable.size)]
                    val cx = (cell % grid.width) * TILE + TILE / 2f
                    val cy = (cell / grid.width) * TILE + TILE / 2f
                    if (hypot(cx - shipX, cy - shipY) >= 60f) {
                        gx = cx; gy = cy
                        break
                    }
                }
            }
            pendingFish += Fish(gx, gy, FishKind.GOLDEN, k, rng.sign(), 1, rng.nextFloat()).also {
                it.life = if (loadout.lure) GOLDEN_LIFE * LURE_LIFE_FACTOR else GOLDEN_LIFE
            }
        }
    }

    private fun hypot(x: Float, y: Float) = sqrt(x * x + y * y)

    // ================================================================ bramy

    private fun checkGates() {
        val stage = stages[stageIndex]
        if (!doorOpen[stageIndex]) return
        if (shipY >= stage.gateRow * TILE - 6f) return
        if (stageIndex == stages.lastIndex) {
            win()
            return
        }
        doorOpen[stageIndex] = false
        fish.removeAll { it.stage == stageIndex }
        enemies.removeAll { it.stage == stageIndex }
        stageIndex++
        stageEnter[stageIndex] = elapsed
        showGateArrow = false
        message = GameMessage(MessageKind.STAGE, stageIndex + 1, stages.size)
        listener.onSound(Sfx.STAGE)
        openEmptyGate()
    }

    private fun win() {
        finishTime = elapsed
        val bonus = Scoring.timeBonus(spec, elapsed) + if (hits == 0) LevelConstants.NO_DAMAGE_BONUS else 0
        scoreValue.add(bonus.toLong())
        if (bonus > 0) popups += Popup(shipX, shipY - 14f, bonus, true)
        showGateArrow = false
        message = GameMessage(MessageKind.FINISH, 0, 0)
        phase = Phase.WINNING
        phaseTimer = 1.4f
        listener.onSound(Sfx.WIN)
        listener.onRumble(Rumbles.WON)
    }

    private fun checkTime() {
        val limit = timeLimit
        if (limit <= 0) return
        val left = limit - elapsed
        if (left <= 10f && left + STEP > 10f) {
            message = GameMessage(MessageKind.TIME_LOW, 10, 0)
        }
        if (left <= 0f) lose(LoseReason.TIME)
    }

    private fun lose(reason: LoseReason) {
        if (phase != Phase.PLAYING) return
        loseReason = reason
        phase = Phase.LOSING
        phaseTimer = 1.1f
        hitFlash = 1f
        listener.onSound(Sfx.LOST)
        listener.onRumble(Rumbles.LOST)
    }

    // ================================================================ wrogowie

    private fun enemySpeed(e: Enemy): Float {
        val base = when (e.kind) {
            EnemyKind.PIRANHA, EnemyKind.PIRANHA_VERTICAL -> 24f
            EnemyKind.RED_PIRANHA -> 34f
            EnemyKind.SHARK -> 36f
            EnemyKind.DARK_SHARK -> 44f
            EnemyKind.OCTOPUS -> if (e.dash > 0f) 60f else 12f
            EnemyKind.RED_OCTOPUS -> if (e.dash > 0f) 72f else 14f
            EnemyKind.STALKER -> when (e.hunt) {
                Hunt.PATROL -> STALKER_PATROL
                Hunt.AIM -> 0f
                Hunt.DASH -> STALKER_DASH
                Hunt.FLEE -> STALKER_FLEE
            }
        }
        return base * spec.enemySpeed * if (loadout.frost) FROST_SLOWDOWN else 1f
    }

    private fun hitRadius(kind: EnemyKind): Float = when (kind) {
        EnemyKind.SHARK, EnemyKind.DARK_SHARK -> 12f
        EnemyKind.OCTOPUS, EnemyKind.RED_OCTOPUS -> 11f
        EnemyKind.PIRANHA, EnemyKind.RED_PIRANHA -> 9f
        EnemyKind.PIRANHA_VERTICAL -> 7f
        EnemyKind.STALKER -> 10f
    }

    private fun updateEnemies(dt: Float) {
        for (e in enemies) {
            if (e.fadeIn > 0f) {
                e.fadeIn -= dt
                continue
            }
            val stage = stages[e.stage]
            if (e.kind == EnemyKind.STALKER) {
                updateStalker(e, stage, dt)
            } else {
                val look = if (e.kind == EnemyKind.SHARK || e.kind == EnemyKind.DARK_SHARK) 12f else 8f
                val ahead = waterFor(e.x + e.dirX * look, e.y + e.dirY * look, stage)
                if (!ahead || rng.nextFloat() < TURN_CHANCE * dt) {
                    turnEnemy(e)
                } else {
                    val v = enemySpeed(e)
                    e.x += e.dirX * v * dt
                    e.y += e.dirY * v * dt
                    if (e.dash > 0f) e.dash -= dt
                }
            }
            // Łodzi na piasku potwory nie dosięgną.
            if (phase != Phase.PLAYING || invulnerable > 0f || beached) continue
            val dx = e.x - shipX
            val dy = (e.y - shipY) * 1.5f
            val r = hitRadius(e.kind)
            if (dx * dx + dy * dy < r * r) hitShip(e)
        }
    }

    private fun turnEnemy(e: Enemy) {
        when (e.kind) {
            EnemyKind.PIRANHA, EnemyKind.RED_PIRANHA -> {
                e.dirX = rng.sign(); e.dirY = 0
            }
            EnemyKind.PIRANHA_VERTICAL -> {
                e.dirY = rng.sign(); e.dirX = 0
            }
            else -> {
                val hunt = when (e.kind) {
                    EnemyKind.DARK_SHARK -> 0.75f
                    EnemyKind.RED_OCTOPUS -> 0.6f
                    else -> 0.5f
                }
                if (rng.nextFloat() < hunt && e.stage == stageIndex && !beached) {
                    if (abs(e.x - shipX) > abs(e.y - shipY)) {
                        e.dirX = if (e.x < shipX) 1 else -1; e.dirY = 0
                    } else {
                        e.dirY = if (e.y < shipY) 1 else -1; e.dirX = 0
                    }
                } else {
                    val d = rng.nextInt(4)
                    e.dirX = DIR_X[d]; e.dirY = DIR_Y[d]
                }
                if (e.kind == EnemyKind.OCTOPUS || e.kind == EnemyKind.RED_OCTOPUS) {
                    val dashChance = if (e.kind == EnemyKind.RED_OCTOPUS) 0.7f else 0.5f
                    e.dash = if (rng.nextFloat() < dashChance) 1f else 0f
                }
            }
        }
    }

    // ================================================================ widmokuda

    /**
     * Widmokuda patroluje jak rekin, ale patrzy przed siebie: gdy zobaczy łódź (w zasięgu wzroku,
     * w polu widzenia i bez skały, wyspy ani brzegu na linii), staje, świeci oczami i po chwili
     * skacze w miejsce, gdzie łódź była. Przez przeszkody nie widzi – za skałą można się schować.
     * Po skoku, trafionym czy nie, odpływa od łodzi i przez chwilę pływa spokojnie, nie polując.
     */
    private fun updateStalker(e: Enemy, stage: StageLayout, dt: Float) {
        if (e.calm > 0f) e.calm -= dt
        val sees = phase == Phase.PLAYING && e.stage == stageIndex && e.calm <= 0f && sees(e)
        if (sees) {
            e.seenX = shipX
            e.seenY = shipY
        }
        e.huntTimer -= dt
        if (abs(e.vx) > 0.2f) e.faceRight = e.vx > 0f
        when (e.hunt) {
            Hunt.PATROL -> {
                if (sees) return aim(e)
                // Skręca losowo albo gdy dalej jest brzeg – ten sam test co przy ruchu, więc nie utknie przy ścianie.
                if (rng.nextFloat() < TURN_CHANCE * dt || !swim(e, e.dirX.toFloat(), e.dirY.toFloat(), enemySpeed(e) * dt, stage)) {
                    val d = rng.nextInt(4)
                    e.dirX = DIR_X[d]
                    e.dirY = DIR_Y[d]
                }
                e.vx = e.dirX.toFloat()
                e.vy = e.dirY.toFloat()
            }
            Hunt.AIM -> {
                faceSeen(e)
                if (e.huntTimer <= 0f) {
                    val d = max(1f, hypot(e.seenX - e.x, e.seenY - e.y))
                    e.hunt = Hunt.DASH
                    // Skacze trochę dalej niż łódź – kto stoi w miejscu, zostanie trafiony.
                    e.huntTimer = min(d / (STALKER_DASH * spec.enemySpeed) + 0.2f, STALKER_DASH_MAX)
                }
            }
            Hunt.DASH -> {
                if (!swim(e, e.vx, e.vy, enemySpeed(e) * dt, stage) || e.huntTimer <= 0f) flee(e)
            }
            Hunt.FLEE -> {
                fleeStep(e, enemySpeed(e) * dt, stage)
                if (e.huntTimer <= 0f) {
                    e.hunt = Hunt.PATROL
                    e.dirX = if (abs(e.vx) >= abs(e.vy)) (if (e.vx >= 0f) 1 else -1) else 0
                    e.dirY = if (e.dirX == 0) (if (e.vy >= 0f) 1 else -1) else 0
                }
            }
        }
    }

    private fun aim(e: Enemy) {
        e.hunt = Hunt.AIM
        e.huntTimer = STALKER_AIM_TIME
        faceSeen(e)
        if (!stalkerSpotted) {
            stalkerSpotted = true
            listener.onVibrate(25)
            // Na poziomach, które przedstawiają widmokudę, pierwszy raz pokazujemy też napis.
            if (Stalkers.isIntro(spec.number)) message = GameMessage(MessageKind.SPOTTED, 0, 0)
        }
    }

    /** Koniec ataku: odpływa od łodzi, a po ucieczce ma przerwę, zanim znów zacznie polować. */
    private fun flee(e: Enemy) {
        e.hunt = Hunt.FLEE
        e.huntTimer = STALKER_FLEE_TIME
        e.calm = STALKER_FLEE_TIME + STALKER_CALM_TIME
    }

    /** Krok ucieczki: prosto od łodzi, a gdy tam brzeg – wzdłuż niego, w bok albo choćby gdziekolwiek. */
    private fun fleeStep(e: Enemy, distance: Float, stage: StageLayout) {
        var ax = e.x - shipX
        var ay = e.y - shipY
        val d = hypot(ax, ay)
        if (d < 0.5f) {
            ax = e.vx; ay = e.vy
        } else {
            ax /= d; ay /= d
        }
        val sx = if (ax >= 0f) 1f else -1f
        val sy = if (ay >= 0f) 1f else -1f
        val tries = arrayOf(ax to ay, sx to 0f, 0f to sy, -ay to ax, ay to -ax, -sx to 0f, 0f to -sy)
        for ((tx, ty) in tries) {
            if (swim(e, tx, ty, distance, stage)) {
                val len = hypot(tx, ty)
                e.vx = tx / len
                e.vy = ty / len
                return
            }
        }
    }

    private fun faceSeen(e: Enemy) {
        val dx = e.seenX - e.x
        val dy = e.seenY - e.y
        val d = hypot(dx, dy)
        if (d > 0.5f) {
            e.vx = dx / d
            e.vy = dy / d
        }
    }

    /** Ruch o [distance] w kierunku (dx, dy); false – dalej jest już brzeg albo skała. */
    private fun swim(e: Enemy, dx: Float, dy: Float, distance: Float, stage: StageLayout): Boolean {
        val len = hypot(dx, dy)
        if (len < 0.01f) return true
        val ux = dx / len
        val uy = dy / len
        if (!waterFor(e.x + ux * (STALKER_LOOK + distance), e.y + uy * (STALKER_LOOK + distance), stage)) return false
        e.x += ux * distance
        e.y += uy * distance
        return true
    }

    /** Czy widmokuda widzi łódź: zasięg wzroku, pole widzenia (z bliska wyczuje też z tyłu) i czysta linia. */
    private fun sees(e: Enemy): Boolean {
        if (beached) return false
        val dx = shipX - e.x
        val dy = shipY - e.y
        val d = hypot(dx, dy)
        if (d > STALKER_SIGHT) return false
        if (d > STALKER_SENSE) {
            val f = hypot(e.vx, e.vy)
            if (f > 0.01f && (dx * e.vx + dy * e.vy) / (d * f) < STALKER_FOV_COS) return false
        }
        return clearLine(e.x, e.y, shipX, shipY)
    }

    /** Linia wzroku: co 4 jednostki sprawdzamy, czy po drodze jest tylko woda (skała, wyspa, brzeg i brama zasłaniają). */
    fun clearLine(x0: Float, y0: Float, x1: Float, y1: Float): Boolean {
        val steps = (hypot(x1 - x0, y1 - y0) / 4f).toInt() + 1
        for (i in 1 until steps) {
            val t = i / steps.toFloat()
            val c = floor((x0 + (x1 - x0) * t) / TILE).toInt()
            val r = floor((y0 + (y1 - y0) * t) / TILE).toInt()
            if (grid[c, r] != Tile.SEA) return false
        }
        return true
    }

    private fun hitShip(e: Enemy) {
        // Po trafieniu widmokuda odpływa i ma przerwę – nie poluje na łódź w czasie nietykalności.
        if (e.kind == EnemyKind.STALKER) flee(e)
        val dx = shipX - e.x
        val dy = shipY - e.y
        val d = max(1f, sqrt(dx * dx + dy * dy))
        knockX = dx / d * 140f
        knockY = dy / d * 140f
        speed *= 0.3f
        combo = 0
        if (shield) {
            shield = false
            invulnerable = 1.5f
            message = GameMessage(MessageKind.SHIELD, 0, 0)
            listener.onSound(Sfx.SHIELD)
            listener.onVibrate(40)
            return
        }
        lives--
        hits++
        invulnerable = 2f
        hitFlash = 1f
        listener.onSound(Sfx.HIT)
        listener.onVibrate(70)
        if (lives <= 0) lose(LoseReason.CAUGHT)
        else if (lives == 1) message = GameMessage(MessageKind.LAST_LIFE, 0, 0)
    }

    /** Kontynuacja po porażce (za monety). */
    fun revive() {
        if (phase != Phase.LOST) return
        continuesUsed++
        if (loseReason == LoseReason.TIME) timeBonusExtra += 15
        if (lives <= 0) lives = 1
        invulnerable = 3f
        hitFlash = 0f
        loseReason = null
        phase = Phase.PLAYING
    }

    // ================================================================ efekty

    private fun updateEffects(dt: Float) {
        if (invulnerable > 0f) invulnerable -= dt
        if (hitFlash > 0f) hitFlash = max(0f, hitFlash - dt * 1.6f)
        if (comboTimer > 0f) comboTimer -= dt
        val ci = caught.iterator()
        while (ci.hasNext()) {
            val c = ci.next()
            val k = 1f - 0.85f.pow(dt * 60f)
            c.x += (shipX - c.x) * k
            c.y += (shipY - c.y) * k
            c.timer -= dt
            if (c.timer <= 0f) ci.remove()
        }
        val pi = popups.iterator()
        while (pi.hasNext()) {
            val p = pi.next()
            p.age += dt
            p.y -= 14f * dt
            if (p.age > 1.1f) pi.remove()
        }
        message?.let {
            it.age += dt
            if (it.age > MESSAGE_TIME) {
                message = if (it.kind == MessageKind.FINALE) GameMessage(MessageKind.GO, stages[0].quota, 0) else null
            }
        }
    }

    private fun updateCamera(dt: Float, snap: Boolean) {
        val roomW = grid.width * TILE.toFloat()
        val targetX = if (viewW >= roomW) (roomW - viewW) / 2f
        else (shipX - viewW / 2f).coerceIn(0f, roomW - viewW)
        val worldH = grid.height * TILE.toFloat()
        val targetY = (shipY - viewH * 0.62f).coerceIn(0f, max(0f, worldH - viewH))
        if (snap) {
            camX = targetX
            camY = targetY
        } else {
            val k = 1f - 0.86f.pow(dt * 60f)
            camX += (targetX - camX) * k
            camY += (targetY - camY) * k
        }
    }

    // ================================================================ wynik

    private fun buildResult(completed: Boolean): RunResult {
        val seconds = if (completed) finishTime else elapsed
        val stars = Scoring.stars(
            layout = layout,
            completed = completed,
            score = score,
            seconds = seconds,
            hits = hits,
            regularCaught = commonCaught + blueCaught,
            goldenCaught = goldenCaught,
            maxCombo = maxCombo,
        )
        return RunResult(
            level = spec.number,
            completed = completed,
            score = score,
            stars = stars.count { it },
            starFlags = stars,
            timeMillis = (seconds * 1000).toLong(),
            commonCaught = commonCaught,
            blueCaught = blueCaught,
            goldenCaught = goldenCaught,
            hits = hits,
            maxCombo = maxCombo,
            stagesCleared = stagesCleared,
        )
    }

    /** Stan celów gwiazdkowych w trakcie gry (do podglądu w pauzie). */
    fun liveStarFlags(): BooleanArray = Scoring.stars(
        layout, true, score, elapsed, hits, commonCaught + blueCaught, goldenCaught, maxCombo,
    )

    val isTimeAttack: Boolean get() = spec.type == LevelType.TIME_ATTACK

    companion object {
        const val STEP = 1f / 120f
        private const val MAX_STEPS = 12
        const val SHIP_SPEED = 72f
        const val SHIP_RADIUS = 5f
        /** Jak daleko od wody może wpłynąć na piasek środek łodzi (kafelek ma 16). */
        const val BEACH_REACH = 7f
        /** Jak daleko przed dziobem sprawdzamy, czy jest tam piasek (kafelek ma 16). */
        private const val BEACH_LOOK = 14f
        /** Jak szybko łódź bez napędu traci prędkość na piasku (mnożnik na klatkę 60 Hz) – woda hamuje ją wolniej. */
        private const val BEACH_DRAG = 0.8f
        private const val BEACH_EXIT_SPEED = 26f
        private const val TURN_RATE = 0.09f
        private const val FISH_SPEED = 12f
        private const val BLUE_SPEED = 20f
        private const val GOLDEN_SPEED = 20f
        private const val GOLDEN_FLEE_SPEED = 46f
        private const val GOLDEN_FLEE_RADIUS = 56f
        private const val GOLDEN_LIFE = 10f
        private const val CATCH_RADIUS = 13f
        private const val NET_CATCH_RADIUS = 20f
        private const val FROST_SLOWDOWN = 0.65f
        private const val LURE_DELAY_FACTOR = 0.5f
        private const val LURE_LIFE_FACTOR = 1.6f
        private const val LURE_FLEE_RADIUS = 30f
        private const val RHYTHM_FACTOR = 1.6f
        private const val MAGNET_RADIUS = 44f
        private const val MAGNET_SPEED = 55f
        private const val CAUGHT_ANIM = 0.33f
        private const val TURN_CHANCE = 0.3f
        const val MESSAGE_TIME = 2f
        // Widmokuda: prędkości (× tempo potworów poziomu), czasy faz polowania, przerwa po ataku i wzrok.
        private const val STALKER_PATROL = 22f
        private const val STALKER_DASH = 105f
        private const val STALKER_FLEE = 46f
        private const val STALKER_AIM_TIME = 0.55f
        private const val STALKER_DASH_MAX = 1f
        const val STALKER_FLEE_TIME = 1.4f
        const val STALKER_CALM_TIME = 3.5f
        private const val STALKER_LOOK = 8f
        const val STALKER_SIGHT = 120f
        private const val STALKER_SENSE = 28f

        /** Pole widzenia ±70° od kierunku pływania. */
        private val STALKER_FOV_COS = cos(Math.toRadians(70.0)).toFloat()
        private val DIR_X = intArrayOf(1, 0, -1, 0)
        private val DIR_Y = intArrayOf(0, 1, 0, -1)
    }
}
