package com.doffi4.doffisecure

import com.doffi4.doffisecure.data.advisor.*
import com.doffi4.doffisecure.domain.advisor.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ClaudeSecurityAdvisorServiceTest {
    private val summary = AdvisorSummary(5, 2, 3, 0)
    private val valid = """{"stop_reason":"end_turn","content":[{"type":"text","text":"{\"overview\":\"Review local findings\",\"checklist\":[\"Replace reused passwords first\",\"Review weak passwords\"]}"}]}"""
    private val config = { ClaudeDeveloperConfig("synthetic-test-key", "developer-model") }

    @Test fun `request contains only sanitized summary in user content and fixed transport headers`() = runTest {
        val factory = FakeCalls(valid)
        val result = ClaudeSecurityAdvisorService(config, factory).advise(summary, AdvisorLanguage.ENGLISH)
        assertTrue(result is AdvisorOutcome.Success)
        assertEquals(listOf("Replace reused passwords first", "Review weak passwords"), (result as AdvisorOutcome.Success).guidance.checklist)
        val request = factory.last!!.request()
        assertEquals("https://api.anthropic.com/v1/messages", request.url.toString())
        assertEquals("synthetic-test-key", request.header("x-api-key"))
        assertEquals("2023-06-01", request.header("anthropic-version"))
        val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
        val body = org.json.JSONObject(buffer.readUtf8())
        assertEquals(setOf("model", "max_tokens", "system", "messages", "output_config"), body.keys().asSequence().toSet())
        assertEquals("json_schema", body.getJSONObject("output_config").getJSONObject("format").getString("type"))
        assertFalse(body.toString().contains("synthetic-test-key"))
    }

    @Test fun `missing configuration performs no network call`() = runTest {
        val calls = FakeCalls(valid)
        assertEquals(AdvisorOutcome.Failure(AdvisorFailure.MISSING_CONFIGURATION), ClaudeSecurityAdvisorService({ null }, calls).advise(summary, AdvisorLanguage.RUSSIAN))
        assertNull(calls.last)
    }

    @Test fun `http failures do not expose body or auth secrets`() = runTest {
        for ((code, reason) in listOf(401 to AdvisorFailure.CONFIGURATION, 429 to AdvisorFailure.UNAVAILABLE, 500 to AdvisorFailure.UNAVAILABLE, 400 to AdvisorFailure.ERROR)) {
            val result = ClaudeSecurityAdvisorService(config, FakeCalls("sensitive-server-message", code)).advise(summary, AdvisorLanguage.ENGLISH)
            assertEquals(AdvisorOutcome.Failure(reason), result)
            assertFalse(result.toString().contains("sensitive"))
        }
    }

    @Test fun `offline exceptions become fixed safe reason`() = runTest {
        val result = ClaudeSecurityAdvisorService(config, FakeCalls(valid, failure = IOException("secret-exception"))).advise(summary, AdvisorLanguage.ENGLISH)
        assertEquals(AdvisorOutcome.Failure(AdvisorFailure.OFFLINE), result)
        assertFalse(result.toString().contains("secret-exception"))
    }

    @Test fun `malformed refused truncated empty extra fields and oversized output fail closed`() = runTest {
        val invalid = listOf("not JSON", valid.replace("end_turn", "max_tokens"), valid.replace("end_turn", "refusal"),
            valid.replace("Review local findings", ""), valid.replace("\\\"overview\\\"", "\\\"extra\\\":1,\\\"overview\\\""), " ".repeat(65537))
        for (body in invalid) {
            assertEquals(AdvisorOutcome.Failure(AdvisorFailure.INVALID_RESPONSE), ClaudeSecurityAdvisorService(config, FakeCalls(body)).advise(summary, AdvisorLanguage.ENGLISH))
        }
    }

    @Test fun `cancellation cancels the actual HTTP call`() = runTest {
        val calls = FakeCalls(valid, hold = true)
        val job = launch { ClaudeSecurityAdvisorService(config, calls).advise(summary, AdvisorLanguage.ENGLISH) }
        runCurrent()
        job.cancel(); job.join()
        assertTrue(calls.last!!.isCanceled())
    }

    @Test fun `configuration representation never includes key`() {
        assertFalse(ClaudeDeveloperConfig("synthetic-test-key", "developer-model").toString().contains("synthetic-test-key"))
    }

    @Test fun `trailing garbage after envelope or guidance is rejected`() = runTest {
        for (body in listOf(valid + "garbage", valid.replace("Review weak passwords\\\"]}", "Review weak passwords\\\"]}garbage"))) {
            assertEquals(AdvisorOutcome.Failure(AdvisorFailure.INVALID_RESPONSE), ClaudeSecurityAdvisorService(config, FakeCalls(body)).advise(summary, AdvisorLanguage.ENGLISH))
        }
    }

    @Test fun `text content block must be a string even on coercing Android JSON implementation`() = runTest {
        val envelope = org.json.JSONObject(valid)
        val block = envelope.getJSONArray("content").getJSONObject(0)
        block.put("text", org.json.JSONObject(block.getString("text")))
        assertEquals(AdvisorOutcome.Failure(AdvisorFailure.INVALID_RESPONSE), ClaudeSecurityAdvisorService(config, FakeCalls(envelope.toString())).advise(summary, AdvisorLanguage.ENGLISH))
    }

    @Test fun `raw NUL cannot masquerade as the end of a JSON response`() = runTest {
        val envelope = org.json.JSONObject(valid)
        val block = envelope.getJSONArray("content").getJSONObject(0)
        block.put("text", block.getString("text") + '\u0000' + "garbage")
        for (body in listOf(valid + '\u0000' + "garbage", envelope.toString())) {
            assertEquals(AdvisorOutcome.Failure(AdvisorFailure.INVALID_RESPONSE), ClaudeSecurityAdvisorService(config, FakeCalls(body)).advise(summary, AdvisorLanguage.ENGLISH))
        }
    }

    @Test fun `cancellation while reading configuration must not enqueue a request`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val calls = FakeCalls(valid)
        val service = ClaudeSecurityAdvisorService({ withContext(NonCancellable) { gate.await(); config() } }, calls)
        val job = launch { service.advise(summary, AdvisorLanguage.ENGLISH) }
        runCurrent(); job.cancel(); gate.complete(Unit); job.join()
        assertNull(calls.last)
    }

    @Test fun `lock while reading configuration blocks request before enqueue`() = runTest {
        var unlocked = true
        val gate = CompletableDeferred<Unit>()
        val calls = FakeCalls(valid)
        val service = ClaudeSecurityAdvisorService({ gate.await(); config() }, calls, canSend = { unlocked })
        val result = async { service.advise(summary, AdvisorLanguage.ENGLISH) }
        runCurrent(); unlocked = false; gate.complete(Unit)
        assertEquals(AdvisorOutcome.Failure(AdvisorFailure.UNAVAILABLE), result.await())
        assertNull(calls.last)
    }

    private class FakeCalls(val body: String, val code: Int = 200, val failure: IOException? = null, val hold: Boolean = false) : Call.Factory {
        var last: FakeCall? = null
        override fun newCall(request: Request): Call = FakeCall(request).also { last = it }
        inner class FakeCall(private val request: Request) : Call {
            private var cancelled = false
            private var executed = false
            override fun request() = request
            override fun execute(): Response = error("Async only")
            override fun enqueue(responseCallback: Callback) {
                executed = true
                if (hold) return
                if (failure != null) responseCallback.onFailure(this, failure)
                else responseCallback.onResponse(this, Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("synthetic")
                    .body(body.toResponseBody("application/json".toMediaType())).build())
            }
            override fun cancel() { cancelled = true }
            override fun isExecuted() = executed
            override fun isCanceled() = cancelled
            override fun timeout() = Timeout.NONE
            override fun clone(): Call = FakeCall(request)
        }
    }
}
