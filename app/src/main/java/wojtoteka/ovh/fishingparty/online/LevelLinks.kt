package wojtoteka.ovh.fishingparty.online

import java.net.URI

/** Link do poziomu z kreatora otwarty w grze. */
sealed interface LevelLink {
    /** Opublikowany poziom gracza: https://<host>/l/<numer>. */
    data class Published(val id: Int) : LevelLink

    /** Poziom udostępniony bez publikacji (np. znajomym): https://<host>/s/<kod>. */
    data class Shared(val code: String) : LevelLink
}

/**
 * Linki do poziomów generuje serwer (POST /shares). Gra otwiera tylko adresy własnego serwera – w manifeście
 * jest ten sam host ([wojtoteka.ovh.fishingparty.BuildConfig.FP_LINK_HOST]) i początek ścieżki
 * ([wojtoteka.ovh.fishingparty.BuildConfig.FP_LINK_PREFIX]: "" w grze, "/beta" w wersji debug – linki z bety
 * prowadzą do poziomów z osobnej bazy).
 */
object LevelLinks {
    private val PUBLISHED = Regex("^/l/(\\d{1,9})/?$")

    /** Te same znaki co w kodach serwera (bez 0/O, 1/l/I). */
    private val SHARED = Regex("^/s/([2-9a-km-np-zA-HJ-NP-Z]{10})/?$")

    fun parse(link: String?, host: String, prefix: String = ""): LevelLink? {
        if (link.isNullOrBlank()) return null
        val uri = try {
            URI(link.trim())
        } catch (_: Exception) {
            return null
        }
        if (uri.scheme?.lowercase() !in setOf("https", "http") || !uri.host.equals(host, ignoreCase = true)) return null
        val full = uri.path ?: return null
        if (prefix.isNotEmpty() && !full.startsWith("$prefix/")) return null
        val path = full.removePrefix(prefix)
        PUBLISHED.find(path)?.let { m -> return m.groupValues[1].toIntOrNull()?.takeIf { it > 0 }?.let { LevelLink.Published(it) } }
        SHARED.find(path)?.let { return LevelLink.Shared(it.groupValues[1]) }
        return null
    }
}
