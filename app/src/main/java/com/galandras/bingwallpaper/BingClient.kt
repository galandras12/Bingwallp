package com.galandras.bingwallpaper

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Minimális HTTP-kliens (HttpURLConnection), időkorlátokkal és méretkorláttal. */
object BingClient {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val MAX_JSON_BYTES = 1L * 1024 * 1024
    private const val MAX_IMAGE_BYTES = 40L * 1024 * 1024
    private const val UA = "BingHatter/1.0 (Android)"

    fun fetchTodayImage(): BingImage {
        val body = try {
            open(BingUrls.METADATA_URL).useStream { readLimited(it, MAX_JSON_BYTES).toString(Charsets.UTF_8) }
        } catch (e: BingException) {
            throw e
        } catch (e: IOException) {
            throw BingException("Nem érhető el a Bing: ${e.message ?: e.javaClass.simpleName}", true, e)
        }
        return BingParser.parse(body)
    }

    /**
     * Letölti a képet a jelöltek közül az elsőként működőt a [dest] fájlba (memóriába nem tölt).
     * Hibánál a következő jelölttel próbálkozik; ha mind hibás, az utolsó hibát dobja.
     */
    fun downloadImage(image: BingImage, dest: File) {
        var last: BingException? = null
        for (url in image.candidateUrls) {
            try {
                open(url).useStream { input ->
                    dest.outputStream().use { out ->
                        var total = 0L
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > MAX_IMAGE_BYTES) throw BingException("Túl nagy kép", false)
                            out.write(buf, 0, n)
                        }
                        if (total == 0L) throw BingException("Üres képválasz", true)
                    }
                }
                return
            } catch (e: BingException) {
                last = e
            } catch (e: IOException) {
                last = BingException("Hálózati hiba: ${e.message ?: e.javaClass.simpleName}", true, e)
            }
        }
        dest.delete()
        throw last ?: BingException("Nincs letölthető kép-URL", false)
    }

    private class Conn(val c: HttpURLConnection) {
        fun <T> useStream(block: (InputStream) -> T): T = try {
            c.inputStream.use(block)
        } finally {
            c.disconnect()
        }
    }

    private fun open(url: String): Conn {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = CONNECT_TIMEOUT_MS
        c.readTimeout = READ_TIMEOUT_MS
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", UA)
        val code = c.responseCode
        if (code !in 200..299) {
            c.disconnect()
            throw BingException("HTTP $code", transient = code == 429 || code >= 500 || code == 408)
        }
        return Conn(c)
    }

    private fun readLimited(input: InputStream, max: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > max) throw BingException("A válasz túl nagy", false)
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
