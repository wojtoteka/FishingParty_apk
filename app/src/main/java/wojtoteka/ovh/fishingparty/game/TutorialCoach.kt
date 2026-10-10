package wojtoteka.ovh.fishingparty.game

import wojtoteka.ovh.fishingparty.level.FishKind

/** Kolejne polecenia samouczka – od sterowania po metę. */
enum class TutorialStep {
    /** Etap 1: przytrzymaj palec i przesuwaj. */
    MOVE,

    /** Podpłyń do ryby, żeby ją złowić. */
    FIRST_FISH,

    /** Złów resztę ryb – otworzy się brama. */
    FILL_QUOTA,

    /** Brama otwarta – przepłyń przez nią. */
    GATE,

    /** Etap 2: pirania – omijaj ją, zderzenie zabiera życie. */
    MONSTER,

    /** Złota ryba – szybko, zaraz ucieknie. */
    GOLDEN,

    /** Złów ryby, omijając piranię. */
    DODGE_AND_FISH,

    /** Meta otwarta – wpłyń na nią. */
    FINISH,
    ;

    /** Numer kroku pokazywany graczowi (złota ryba to wtrącenie, nie osobny krok). */
    val number: Int
        get() = when (this) {
            MOVE -> 1
            FIRST_FISH -> 2
            FILL_QUOTA -> 3
            GATE -> 4
            MONSTER -> 5
            GOLDEN, DODGE_AND_FISH -> 6
            FINISH -> 7
        }

    companion object {
        const val COUNT = 7
    }
}

/**
 * Wybiera aktualne polecenie samouczka na podstawie stanu gry – nic nie
 * trzeba zapamiętywać, więc samouczek zawsze pasuje do tego, co dzieje się na ekranie.
 */
object TutorialCoach {
    fun step(world: GameWorld): TutorialStep? {
        if (!world.tutorial || world.phase != Phase.PLAYING) return null
        val stage = world.stageIndex
        val caught = world.caughtInCurrentStage
        val open = world.isDoorOpen(stage)
        if (stage == 0) {
            return when {
                world.showControls -> TutorialStep.MOVE
                caught == 0 -> TutorialStep.FIRST_FISH
                !open -> TutorialStep.FILL_QUOTA
                else -> TutorialStep.GATE
            }
        }
        return when {
            open -> TutorialStep.FINISH
            world.fish.any { it.kind == FishKind.GOLDEN && it.stage == stage } -> TutorialStep.GOLDEN
            caught == 0 -> TutorialStep.MONSTER
            else -> TutorialStep.DODGE_AND_FISH
        }
    }

    /** Ile ryb brakuje do otwarcia bramy w bieżącym etapie. */
    fun remaining(world: GameWorld): Int = (world.currentStage.quota - world.caughtInCurrentStage).coerceAtLeast(0)
}
