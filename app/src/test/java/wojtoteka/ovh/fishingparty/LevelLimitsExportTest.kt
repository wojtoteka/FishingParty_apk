package wojtoteka.ovh.fishingparty

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import wojtoteka.ovh.fishingparty.level.FishKind
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

    @Test
    fun exportLevelLimits() {
        val levels = (1..LevelConstants.LEVEL_COUNT).joinToString(",\n") { n ->
            val plan = LevelGenerator.plan(n)
            val minMs = (ScoreValidator.minimumSeconds(plan) * 1000f).toLong()
            "    [$n,${LevelGenerator.maxScore(plan)},$minMs,${plan.stages.sumOf { it.common }}," +
                "${plan.stages.sumOf { it.blue }},${plan.goldenCount},${plan.stages.size},${plan.spec.parSeconds}]"
        }
        val body = buildString {
            append("{\n")
            append("  \"columns\": [\"level\",\"maxScore\",\"minMs\",\"common\",\"blue\",\"golden\",\"stages\",\"parSeconds\"],\n")
            append("  \"constants\": {")
            append("\"levelCount\":${LevelConstants.LEVEL_COUNT},")
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
