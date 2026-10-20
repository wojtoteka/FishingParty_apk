package wojtoteka.ovh.fishingparty.data

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONObject
import wojtoteka.ovh.fishingparty.level.RunResult
import wojtoteka.ovh.fishingparty.level.UserLevel
import wojtoteka.ovh.fishingparty.level.UserLevels
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.Executors

/** Przejazd weryfikacyjny – autor przeszedł poziom od pierwszego etapu, bez wzmocnień i bez kontynuacji. */
class VerifyRun(
    val score: Int,
    val timeMillis: Long,
    val common: Int,
    val blue: Int,
    val golden: Int,
    val hits: Int,
    val maxCombo: Int,
    val stages: Int,
    val at: Long,
) {
    companion object {
        fun of(result: RunResult, at: Long) = VerifyRun(
            result.score, result.timeMillis, result.commonCaught, result.blueCaught, result.goldenCaught,
            result.hits, result.maxCombo, result.stagesCleared, at,
        )
    }
}

/**
 * Szkic z kreatora poziomów.
 * @param verifiedHash skrót treści poziomu, który autor przeszedł – po każdej zmianie poziomu trzeba go przejść od nowa
 * @param publishedId numer poziomu na serwerze, jeśli został opublikowany (i [publishedHash] – którą wersję opublikowano)
 */
class LevelDraft(
    val id: String,
    val level: UserLevel,
    val updatedAt: Long,
    val verifiedHash: String? = null,
    val verifyRun: VerifyRun? = null,
    val publishedId: Int? = null,
    val publishedHash: String? = null,
) {
    val hash: String by lazy { UserLevels.hashOf(level) }
    val verified: Boolean get() = verifiedHash == hash && verifyRun != null
    val published: Boolean get() = publishedId != null && publishedHash == hash

    fun with(
        level: UserLevel = this.level,
        updatedAt: Long = this.updatedAt,
        verifiedHash: String? = this.verifiedHash,
        verifyRun: VerifyRun? = this.verifyRun,
        publishedId: Int? = this.publishedId,
        publishedHash: String? = this.publishedHash,
    ) = LevelDraft(id, level, updatedAt, verifiedHash, verifyRun, publishedId, publishedHash)
}

/**
 * Szkice poziomów na tym telefonie – każdy w osobnym pliku (katalog `levels`). To twórczość gracza,
 * a nie wynik, więc nie trafiają do szyfrowanego zapisu gry; opublikowany poziom i tak leży na serwerze.
 */
class LevelDrafts(root: File) {
    private val dir = File(root, "levels").apply { mkdirs() }
    private val io = Executors.newSingleThreadExecutor()

    /** Od ostatnio zmienionego. */
    val drafts = mutableStateListOf<LevelDraft>()

    init {
        val loaded = (dir.listFiles { f -> f.name.endsWith(EXT) } ?: emptyArray()).mapNotNull { read(it) }
        drafts.addAll(loaded.sortedByDescending { it.updatedAt })
    }

    fun get(id: String): LevelDraft? = drafts.firstOrNull { it.id == id }

    fun create(level: UserLevel): LevelDraft {
        val draft = LevelDraft(newId(), UserLevels.normalize(level), System.currentTimeMillis())
        save(draft)
        return draft
    }

    fun save(draft: LevelDraft) {
        val index = drafts.indexOfFirst { it.id == draft.id }
        if (index >= 0) drafts.removeAt(index)
        drafts.add(0, draft)
        val text = encode(draft)
        io.execute {
            try {
                val file = File(dir, draft.id + EXT)
                val tmp = File(dir, draft.id + ".tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(file)) {
                    tmp.copyTo(file, overwrite = true)
                    tmp.delete()
                }
            } catch (e: Exception) {
                Log.e("FishingParty", "Nie można zapisać szkicu poziomu", e)
            }
        }
    }

    fun delete(id: String) {
        drafts.removeAll { it.id == id }
        io.execute { File(dir, id + EXT).delete() }
    }

    private fun read(file: File): LevelDraft? = try {
        val o = JSONObject(file.readText())
        val level = UserLevels.decode(o.getJSONObject("level")) ?: throw IllegalArgumentException("poziom")
        val run = o.optJSONObject("run")?.let {
            VerifyRun(
                it.getInt("score"), it.getLong("timeMs"), it.getInt("common"), it.getInt("blue"), it.getInt("golden"),
                it.getInt("hits"), it.getInt("maxCombo"), it.getInt("stages"), it.getLong("at"),
            )
        }
        LevelDraft(
            id = o.getString("id"),
            level = UserLevels.normalize(level),
            updatedAt = o.getLong("updated"),
            verifiedHash = o.optString("verified").takeIf { it.isNotEmpty() },
            verifyRun = run,
            publishedId = if (o.has("published")) o.getInt("published") else null,
            publishedHash = o.optString("publishedHash").takeIf { it.isNotEmpty() },
        )
    } catch (e: Exception) {
        Log.w("FishingParty", "Uszkodzony szkic poziomu ${file.name}", e)
        null
    }

    private fun encode(d: LevelDraft): String {
        val o = JSONObject()
            .put("id", d.id)
            .put("updated", d.updatedAt)
            .put("level", JSONObject(UserLevels.encode(d.level)))
        d.verifiedHash?.let { o.put("verified", it) }
        d.verifyRun?.let {
            o.put(
                "run",
                JSONObject().put("score", it.score).put("timeMs", it.timeMillis).put("common", it.common).put("blue", it.blue)
                    .put("golden", it.golden).put("hits", it.hits).put("maxCombo", it.maxCombo).put("stages", it.stages).put("at", it.at),
            )
        }
        d.publishedId?.let { o.put("published", it) }
        d.publishedHash?.let { o.put("publishedHash", it) }
        return o.toString()
    }

    private fun newId(): String = ByteArray(8).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

    companion object {
        private const val EXT = ".fpl"

        /** Najwięcej szkiców na telefonie. */
        const val MAX_DRAFTS = 100
    }
}
