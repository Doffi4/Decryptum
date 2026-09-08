package com.doffi4.doffisecure.data.repository

import com.doffi4.doffisecure.domain.model.BreachCheckResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Repository interface for checking compromised passwords against HaveIBeenPwned API.
 */
interface IPwnedPasswordsRepository {
    suspend fun checkPassword(password: String): BreachCheckResult
}

/**
 * Implementation of HaveIBeenPwned Pwned Passwords API v3 using k-Anonymity.
 *
 * Mathematical guarantee:
 * - The password is never transmitted in plaintext or full hash.
 * - Only the first 5 characters (20 bits) of the SHA-1 hash are sent to the API.
 * - The server returns ~500-1000 matching hash suffixes.
 * - The client locally compares the remaining 35 characters (140 bits) against the returned list.
 */
class PwnedPasswordsRepository(
    private val httpClient: OkHttpClient = defaultClient
) : IPwnedPasswordsRepository {

    override suspend fun checkPassword(password: String): BreachCheckResult = withContext(Dispatchers.IO) {
        if (password.isEmpty()) {
            return@withContext BreachCheckResult.Clean()
        }

        try {
            val fullHash = sha1Hex(password).uppercase(Locale.US)
            if (fullHash.length != 40) {
                return@withContext BreachCheckResult.Error("Invalid SHA-1 length")
            }

            val prefix = fullHash.substring(0, 5)
            val suffix = fullHash.substring(5)

            val url = "https://api.pwnedpasswords.com/range/$prefix"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Decryptum-Password-Manager/1.0")
                .header("Add-Padding", "true") // HIBP standard: randomized padding prevents response length leakage
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext BreachCheckResult.Error("HTTP ${response.code}")
                }

                val responseBody = response.body?.string() ?: return@withContext BreachCheckResult.Clean()
                
                // Lines are formatted as "SUFFIX:COUNT"
                var matchCount: Int? = null
                responseBody.lineSequence().forEach { rawLine ->
                    val line = rawLine.trim()
                    if (line.isNotEmpty()) {
                        val colonIndex = line.indexOf(':')
                        if (colonIndex > 0) {
                            val returnedSuffix = line.substring(0, colonIndex).trim()
                            if (returnedSuffix.equals(suffix, ignoreCase = true)) {
                                val countStr = line.substring(colonIndex + 1).trim()
                                val parsed = countStr.toIntOrNull() ?: 1
                                if (parsed > 0) {
                                    matchCount = parsed
                                    return@forEach
                                }
                            }
                        }
                    }
                }

                if (matchCount != null) {
                    BreachCheckResult.Compromised(matchCount!!)
                } else {
                    BreachCheckResult.Clean()
                }
            }
        } catch (e: IOException) {
            BreachCheckResult.Error(e.localizedMessage ?: "Network connection error")
        } catch (e: Exception) {
            BreachCheckResult.Error(e.localizedMessage ?: "Unexpected error")
        }
    }

    companion object {
        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }

        fun sha1Hex(text: String): String {
            val md = MessageDigest.getInstance("SHA-1")
            val bytes = md.digest(text.toByteArray(Charsets.UTF_8))
            val sb = StringBuilder(bytes.size * 2)
            for (b in bytes) {
                sb.append(String.format("%02X", b))
            }
            return sb.toString()
        }
    }
}
