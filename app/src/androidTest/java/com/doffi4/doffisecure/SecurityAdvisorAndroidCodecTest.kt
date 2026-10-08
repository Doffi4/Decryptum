package com.doffi4.doffisecure

import com.doffi4.doffisecure.data.advisor.ClaudeAdvisorCodec
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.fail
import org.junit.Test

/** Runs against Android's JSON implementation, whose getString coercion differs from JVM org.json. */
class SecurityAdvisorAndroidCodecTest {
    private fun envelope(text: Any) = JSONObject().put("stop_reason", "end_turn")
        .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text))).toString()
    private fun output() = JSONObject().put("overview", "Synthetic overview").put("checklist", JSONArray().put("Review local findings"))
    private fun rejected(body: String) {
        try { ClaudeAdvisorCodec.parseResponse(body); fail("Unsupported response accepted") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun objectIsNotCoercedIntoText() { rejected(envelope(output())) }
    @Test fun completeEnvelopeAndGuidanceAreRequired() {
        rejected(envelope(output().toString()) + "garbage")
        rejected(envelope(output().toString() + "garbage"))
    }
    @Test fun rawNulIsNotEndOfInput() {
        rejected(envelope(output().toString()) + '\u0000' + "garbage")
        rejected(envelope(output().toString() + '\u0000' + "garbage"))
    }
}
