package wojtoteka.ovh.fishingparty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wojtoteka.ovh.fishingparty.core.Rng
import wojtoteka.ovh.fishingparty.level.ChallengeKind
import wojtoteka.ovh.fishingparty.level.Challenges
import wojtoteka.ovh.fishingparty.level.FishKind
import wojtoteka.ovh.fishingparty.level.LevelPlan
import wojtoteka.ovh.fishingparty.level.LevelSeed
import wojtoteka.ovh.fishingparty.level.LevelConstants
import wojtoteka.ovh.fishingparty.level.LevelGenerator
import wojtoteka.ovh.fishingparty.level.ScoreValidator
import java.io.File
import java.security.MessageDigest

/**
 * Eksportuje limity wszystkich poziomów do `app/build/level-limits.json`.
 * Serwer rankingu (Top_FishingParty, plik `data/level-limits.json`) sprawdza
 * przesłane wyniki na tych samych danych co [ScoreValidator] w grze.
 * Po każdej zmianie generatora poziomów plik trzeba skopiować na serwer.
 */
class LevelLimitsExportTest {

    private fun row(n: Int): String = "    [$n,${limits(LevelGenerator.plan(n))}]"

    private fun limits(plan: LevelPlan): String {
        val minMs = (ScoreValidator.minimumSeconds(plan) * 1000f).toLong()
        return "${LevelGenerator.maxScore(plan)},$minMs,${plan.stages.sumOf { it.common }}," +
            "${plan.stages.sumOf { it.blue }},${plan.goldenCount},${plan.stages.size},${plan.spec.parSeconds}"
    }

    /**
     * Poziomy dnia i tygodnia mają seed z serwera – serwer liczy ich limity sam (src/challenges.ts).
     * Próbka trafia do `test/fixtures/challenge-sample.json` serwera: seed (tekst, Long ze znakiem),
     * rodzaj i te same kolumny co level-limits.json (bez numeru poziomu).
     */
    @Test
    fun exportChallengeSample() {
        val rows = ChallengeKind.entries.flatMap { kind ->
            (0 until 150).map { i ->
                val seed = LevelSeed(Rng.mix(0x53_41_4D_50L + kind.ordinal, i.toLong()))
                "    [\"${seed.value}\",\"${kind.id}\",${limits(Challenges.plan(kind, seed))}]"
            }
        }
        val out = File("build/challenge-sample.json")
        out.parentFile?.mkdirs()
        out.writeText("{\n  \"levels\": [\n${rows.joinToString(",\n")}\n  ]\n}\n")
        assertTrue(out.length() > 0)
    }

    /**
     * Poziomów Bezkresnego Morza (1001+) nie da się wypisać wszystkich – serwer liczy je sam
     * (kopia [wojtoteka.ovh.fishingparty.level.SeedLevels]). Ta próbka trafia do testów
     * serwera (`test/fixtures/endless-sample.json`) i pilnuje, że obie strony liczą to samo.
     */
    @Test
    fun exportEndlessSample() {
        val first = LevelConstants.LEVEL_COUNT + 1
        val levels = ((first until first + 400) + (5000 until 5050) + (LevelConstants.MAX_LEVEL - 49..LevelConstants.MAX_LEVEL))
            .joinToString(",\n") { row(it) }
        val out = File("build/endless-sample.json")
        out.parentFile?.mkdirs()
        out.writeText("{\n  \"levels\": [\n$levels\n  ]\n}\n")
        assertTrue(out.length() > 0)
    }

    @Test
    fun exportLevelLimits() {
        val levels = (1..LevelConstants.LEVEL_COUNT).joinToString(",\n") { row(it) }
        val body = buildString {
            append("{\n")
            append("  \"columns\": [\"level\",\"maxScore\",\"minMs\",\"common\",\"blue\",\"golden\",\"stages\",\"parSeconds\"],\n")
            append("  \"constants\": {")
            append("\"levelCount\":${LevelConstants.LEVEL_COUNT},")
            append("\"maxLevel\":${LevelConstants.MAX_LEVEL},")
            append("\"maxCombo\":${LevelConstants.MAX_COMBO},")
            append("\"stageBonus\":${LevelConstants.STAGE_BONUS},")
            append("\"noDamageBonus\":${LevelConstants.NO_DAMAGE_BONUS},")
            append("\"timeBonusPerSecond\":${LevelConstants.TIME_BONUS_PER_SECOND},")
            append("\"commonPoints\":${FishKind.COMMON.points},")
            append("\"bluePoints\":${FishKind.BLUE.points},")
            append("\"goldenPoints\":${FishKind.GOLDEN.points}")
            append("},\n")
            append("  \"levels\": [\n").append(levels).append("\n  ]\n}\n")
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(body.toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals(64, digest.length)
        val out = File("build/level-limits.json")
        out.parentFile?.mkdirs()
        out.writeText(body)
        assertTrue(out.length() > 0)
    }
}
