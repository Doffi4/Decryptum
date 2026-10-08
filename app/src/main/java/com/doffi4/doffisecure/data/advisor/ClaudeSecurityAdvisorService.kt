package com.doffi4.doffisecure.data.advisor

import com.doffi4.doffisecure.domain.advisor.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Runtime-only developer credential. Deliberately not a data class (no key-bearing toString). */
class ClaudeDeveloperConfig(val apiKey: String, val model: String) {
    init {
        require(apiKey.length in 1..256 && apiKey.all { it.code in 33..126 })
        require(model.matches(Regex("[A-Za-z0-9._-]{1,100}")))
    }
    override fun toString() = "ClaudeDeveloperConfig(redacted)"
}

class ClaudeSecurityAdvisorService(
    private val configuration: suspend () -> ClaudeDeveloperConfig?,
    private val calls: Call.Factory = defaultClient(),
    private val canSend: () -> Boolean = { true },
) : SecurityAdvisorService {
    override suspend fun advise(summary: AdvisorSummary, language: AdvisorLanguage): AdvisorOutcome {
        val config = try { configuration() } catch (e: CancellationException) { throw e }
            catch (_: Exception) { return AdvisorOutcome.Failure(AdvisorFailure.CONFIGURATION) }
            ?: return AdvisorOutcome.Failure(AdvisorFailure.MISSING_CONFIGURATION)
        val request = Request.Builder().url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", config.apiKey).header("anthropic-version", "2023-06-01")
            .post(ClaudeAdvisorCodec.requestJson(summary, config.model, language).toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        // Configuration can suspend. Recheck cancellation and authorization at the send boundary.
        currentCoroutineContext().ensureActive()
        if (!canSend()) return AdvisorOutcome.Failure(AdvisorFailure.UNAVAILABLE)
        return suspendCancellableCoroutine { continuation ->
            val call = calls.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // Never propagate throwable messages, bodies or authentication headers.
                    if (continuation.isActive) continuation.resume(AdvisorOutcome.Failure(
                        if (e is InterruptedIOException) AdvisorFailure.UNAVAILABLE else AdvisorFailure.OFFLINE))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = response.use {
                        try {
                            if (!it.isSuccessful) return@use AdvisorOutcome.Failure(when (it.code) {
                                401, 403 -> AdvisorFailure.CONFIGURATION
                                429, in 500..599 -> AdvisorFailure.UNAVAILABLE
                                else -> AdvisorFailure.ERROR
                            })
                            val body = it.body ?: return@use AdvisorOutcome.Failure(AdvisorFailure.INVALID_RESPONSE)
                            // Bound bytes before decoding; do not read arbitrary remote text into memory.
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(4096)
                            body.byteStream().use { stream ->
                                while (true) {
                                    if (!continuation.isActive) throw IOException()
                                    val count = stream.read(buffer)
                                    if (count == -1) break
                                    require(output.size() + count <= 65536)
                                    output.write(buffer, 0, count)
                                }
                            }
                            AdvisorOutcome.Success(ClaudeAdvisorCodec.parseResponse(output.toString("UTF-8")))
                        } catch (_: IOException) {
                            AdvisorOutcome.Failure(AdvisorFailure.UNAVAILABLE)
                        } catch (_: Exception) {
                            AdvisorOutcome.Failure(AdvisorFailure.INVALID_RESPONSE)
                        }
                    }
                    if (continuation.isActive) continuation.resume(result)
                }
            })
        }
    }

    companion object {
        internal fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .build()
    }
}
