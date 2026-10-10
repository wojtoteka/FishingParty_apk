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
    val maxSpeed = if (loadout.turbo) SHIP_SPEED * 1.2f else SHIP_SPEED

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
        if (thrust) {
            var diff = targetHeading - heading
            while (diff > PI) diff -= (2 * PI).toFloat()
            while (diff < -PI) diff += (2 * PI).toFloat()
            heading += diff * (1f - (1f - TURN_RATE).pow(ticks))
            while (heading > PI) heading -= (2 * PI).toFloat()
            while (heading < -PI) heading += (2 * PI).toFloat()
            val accel = if (speed < maxSpeed * 0.66f) 0.05f else 0.02f
            speed += (maxSpeed - speed) * (1f - (1f - accel).pow(ticks))
        } else {
            speed += (0f - speed) * (1f - 0.95f.pow(ticks))
        }
        if (controlled && showControls) {
            if (speed > 30f) movedTime += dt
            if (movedTime > 1.2f) showControls = false
        }

        var dx = cos(heading) * speed * dt + knockX * dt
        var dy = sin(heading) * speed * dt + knockY * dt
        if (phase == Phase.PLAYING) {
            dx += spec.currentX * dt
            dy += spec.currentY * dt
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
                shipX = nx; bump(abs(dy) / dt)
            }
            !shipBlocked(shipX, ny) -> {
                shipY = ny; bump(abs(dx) / dt)
            }
            else -> bump(speed)
        }
    }

    private fun bump(impactSpeed: Float) {
        if (impactSpeed > 35f && bumpCooldown <= 0f) {
            listener.onSound(Sfx.BUMP)
            bumpCooldown = 0.5f
        }
        speed *= 0.6f
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

    private fun shipBlocked(x: Float, y: Float): Boolean {
        val c0 = floor((x - SHIP_RADIUS) / TILE).toInt()
        val c1 = floor((x + SHIP_RADIUS) / TILE).toInt()
        val r0 = floor((y - SHIP_RADIUS) / TILE).toInt()
        val r1 = floor((y + SHIP_RADIUS) / TILE).toInt()
        for (r in r0..r1) for (c in c0..c1) {
            if (!solidForShip(c, r)) continue
            val nearestX = x.coerceIn(c * TILE.toFloat(), (c + 1) * TILE.toFloat())
            val nearestY = y.coerceIn(r * TILE.toFloat(), (r + 1) * TILE.toFloat())
            val ddx = x - nearestX
            val ddy = y - nearestY
            if (ddx * ddx + ddy * ddy < SHIP_RADIUS * SHIP_RADIUS) return true
        }
        return false
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
            val dir = rng.nextInt(4)
            pendingEnemies += Enemy(
                kind = s.kind,
                x = s.col * TILE + TILE / 2f,
                y = s.row * TILE + TILE / 2f,
                stage = k,
                dirX = when {
                    horizontal -> rng.sign()
                    vertical -> 0
                    else -> DIR_X[dir]
                },
                dirY = when {
                    horizontal -> 0
                    vertical -> rng.sign()
                    else -> DIR_Y[dir]
                },
                phase = rng.nextFloat() * 0.4f,
            ).also { it.fadeIn = fadeIn }
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
                        if (dist < GOLDEN_FLEE_RADIUS) {
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

            if (phase == Phase.PLAYING && dist < CATCH_RADIUS) {
                it.remove()
                catchFish(f)
            }
        }
    }

    private fun sign(v: Float) = if (v > 0f) 1f else if (v < 0f) -1f else 0f

    private fun catchFish(f: Fish) {
        combo = if (elapsed - lastCatch <= Scoring.COMBO_WINDOW_SECONDS && combo > 0) {
            min(LevelConstants.MAX_COMBO, combo + 1)
        } else 1
        lastCatch = elapsed
        comboTimer = Scoring.COMBO_WINDOW_SECONDS
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
            if (elapsed - stageEnter[k] < g.delaySeconds) continue
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
            pendingFish += Fish(gx, gy, FishKind.GOLDEN, k, rng.sign(), 1, rng.nextFloat()).also { it.life = GOLDEN_LIFE }
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
        listener.onVibrate(40)
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
        listener.onVibrate(220)
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
        }
        return base * spec.enemySpeed
    }

    private fun hitRadius(kind: EnemyKind): Float = when (kind) {
        EnemyKind.SHARK, EnemyKind.DARK_SHARK -> 12f
        EnemyKind.OCTOPUS, EnemyKind.RED_OCTOPUS -> 11f
        EnemyKind.PIRANHA, EnemyKind.RED_PIRANHA -> 9f
        EnemyKind.PIRANHA_VERTICAL -> 7f
    }

    private fun updateEnemies(dt: Float) {
        for (e in enemies) {
            if (e.fadeIn > 0f) {
                e.fadeIn -= dt
                continue
            }
            val stage = stages[e.stage]
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
            if (phase != Phase.PLAYING || invulnerable > 0f) continue
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
                if (rng.nextFloat() < hunt && e.stage == stageIndex) {
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

    private fun hitShip(e: Enemy) {
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
        private const val TURN_RATE = 0.09f
        private const val FISH_SPEED = 12f
        private const val BLUE_SPEED = 20f
        private const val GOLDEN_SPEED = 20f
        private const val GOLDEN_FLEE_SPEED = 46f
        private const val GOLDEN_FLEE_RADIUS = 56f
        private const val GOLDEN_LIFE = 10f
        private const val CATCH_RADIUS = 13f
        private const val MAGNET_RADIUS = 44f
        private const val MAGNET_SPEED = 55f
        private const val CAUGHT_ANIM = 0.33f
        private const val TURN_CHANCE = 0.3f
        const val MESSAGE_TIME = 2f
        private val DIR_X = intArrayOf(1, 0, -1, 0)
        private val DIR_Y = intArrayOf(0, 1, 0, -1)
    }
}
