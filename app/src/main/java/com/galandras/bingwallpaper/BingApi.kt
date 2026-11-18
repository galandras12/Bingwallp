package com.galandras.bingwallpaper

import org.json.JSONException
import org.json.JSONObject

/** A Bing napi képének metaadatai. */
data class BingImage(
    /** Egyedi azonosító (kezdődátum + hash, ennek hiányában az URL-alap). */
    val id: String,
    val title: String,
    val copyright: String,
    /** A letöltési próbálkozások sorrendben: UHD, majd 1920x1080 (végül az eredeti). */
    val candidateUrls: List<String>,
)

/** Hiba a Bing-kommunikációban; [transient] = érdemes később újra próbálni. */
class BingException(message: String, val transient: Boolean, cause: Throwable? = null) :
    Exception(message, cause)

object BingUrls {
    const val BASE = "https://www.bing.com"
    const val METADATA_URL = "$BASE/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=hu-HU"

    private val SIZE_SUFFIX = Regex("_(?:UHD|\\d{3,4}x\\d{3,4})\\.jpg", RegexOption.IGNORE_CASE)

    /** Relatív (`/th?id=...`) URL abszolúttá tétele; az abszolút URL változatlan marad. */
    fun absolute(url: String): String = when {
        url.startsWith("http://", true) || url.startsWith("https://", true) -> url
        url.startsWith("/") -> BASE + url
        else -> "$BASE/$url"
    }

    /**
     * Letöltési jelöltek: először `_UHD.jpg`, utána `_1920x1080.jpg`. Ha az URL nem tartalmaz
     * méretjelölést, az eredeti (abszolút) URL az egyetlen jelölt.
     */
    fun candidates(rawUrl: String): List<String> {
        val abs = absolute(rawUrl)
        if (!SIZE_SUFFIX.containsMatchIn(abs)) return listOf(abs)
        val uhd = abs.replace(Regex("_(?:UHD|\\d{3,4}x\\d{3,4})(?=\\.jpg|&|$)", RegexOption.IGNORE_CASE), "_UHD")
        val fhd = abs.replace(Regex("_(?:UHD|\\d{3,4}x\\d{3,4})(?=\\.jpg|&|$)", RegexOption.IGNORE_CASE), "_1920x1080")
        return listOf(uhd, fhd, abs).distinct()
    }
}

object BingParser {
    /** A HPImageArchive válasz feldolgozása. [BingException]-t dob hibás/üres válasznál. */
    fun parse(json: String): BingImage {
        if (json.isBlank()) throw BingException("Üres válasz a Bingtől", transient = true)
        try {
            val images = JSONObject(json).optJSONArray("images")
            if (images == null || images.length() == 0) {
                throw BingException("A válasz nem tartalmaz képet", transient = true)
            }
            val img = images.getJSONObject(0)
            val url = img.optString("url").ifBlank { img.optString("urlbase").let { if (it.isBlank()) "" else "${it}_1920x1080.jpg" } }
            if (url.isBlank()) throw BingException("A válaszból hiányzik a kép URL-je", transient = false)

            val startDate = img.optString("startdate")
            val hsh = img.optString("hsh")
            val id = listOf(startDate, hsh).filter { it.isNotBlank() }.joinToString("-")
                .ifBlank { img.optString("urlbase").ifBlank { url } }

            val copyright = img.optString("copyright")
            // A Bing "title" mezője gyakran csak általános ("Info"); ilyenkor a szerzői jogi sor eleje a leírás.
            val rawTitle = img.optString("title").trim()
            val fromCopyright = copyright.substringBefore(" (").trim()
            val title = if (rawTitle.isBlank() || rawTitle.equals("info", ignoreCase = true)) {
                fromCopyright.ifBlank { rawTitle }
            } else rawTitle
            return BingImage(id, title, copyright, BingUrls.candidates(url))
        } catch (e: JSONException) {
            throw BingException("Érvénytelen JSON: ${e.message}", transient = false, cause = e)
        }
    }
}
