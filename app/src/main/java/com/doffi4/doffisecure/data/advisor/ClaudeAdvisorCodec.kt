package com.doffi4.doffisecure.data.advisor

import com.doffi4.doffisecure.domain.advisor.*
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Explicit writer, not reflective serialization. Also used by the consent preview. */
object ClaudeAdvisorCodec {
    fun summaryJson(summary: AdvisorSummary): String = JSONObject()
        .put("password_entry_count", summary.passwordEntryCount)
        .put("weak_password_count", summary.weakPasswordCount)
        .put("reused_password_count", summary.reusedPasswordCount)
        .put("duplicate_credential_count", summary.duplicateCredentialCount)
        .toString()

    fun requestJson(summary: AdvisorSummary, model: String, language: AdvisorLanguage): String {
        val schema = JSONObject().put("type", "object")
            .put("properties", JSONObject()
                .put("overview", JSONObject().put("type", "string"))
                .put("checklist", JSONObject().put("type", "array").put("items", JSONObject().put("type", "string"))))
            .put("required", JSONArray(listOf("overview", "checklist")))
            .put("additionalProperties", false)
        val instructions = """
            You explain aggregate local password hygiene findings, not account-specific facts.
            Counts are affected stored entries, not distinct passwords; categories may overlap.
            Weakness is a local heuristic. Reuse means equality across stored account labels;
            duplicate credentials are copies requiring manual review, never automatic deletion.
            Breaches, password age, TOTP/passkey coverage and service support were NOT checked.
            Give a concise overview (at most 600 characters) and 1-6 short prioritized checklist
            steps (at most 400 characters each). Do not invent accounts or findings, ask for
            passwords or any sensitive data, suggest cryptographic key handling, claim to have
            inspected secrets, certify security or say the user is fully secure. No links.
            You have no vault access, tools or authority to change credentials.
        """.trimIndent() + if (language == AdvisorLanguage.RUSSIAN) " Respond in Russian." else " Respond in English."
        return JSONObject().put("model", model).put("max_tokens", 1200).put("system", instructions)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", summaryJson(summary))))
            .put("output_config", JSONObject().put("format", JSONObject().put("type", "json_schema").put("schema", schema)))
            .toString()
    }

    /** Provider output is untrusted. Truncated/refused/unexpected results are not guidance. */
    fun parseResponse(body: String): AdvisorGuidance {
        require(body.length <= 65536)
        val envelope = completeObject(body)
        require(envelope.get("stop_reason") == "end_turn")
        val content = envelope.getJSONArray("content")
        require(content.length() == 1)
        val block = content.getJSONObject(0)
        require(block.get("type") == "text")
        // Android getString coerces arbitrary JSON values; require the actual wire type.
        val rawText = block.get("text")
        require(rawText is String)
        val output = completeObject(rawText)
        require(output.keys().asSequence().toSet() == setOf("overview", "checklist"))
        fun text(value: Any, limit: Int): String {
            require(value is String && value.isNotBlank() && value.length <= limit)
            require(value.none { it.isISOControl() && it != '\n' && it != '\t' })
            return value
        }
        val overview = text(output.get("overview"), 600)
        val steps = output.getJSONArray("checklist")
        require(steps.length() in 1..6)
        return AdvisorGuidance(overview, (0 until steps.length()).map { text(steps.get(it), 400) })
    }

    private fun completeObject(text: String): JSONObject {
        // Android uses the same sentinel for EOF and an actual NUL input character.
        require('\u0000' !in text)
        val tokener = JSONTokener(text)
        val value = tokener.nextValue()
        require(value is JSONObject && tokener.nextClean() == '\u0000')
        return value
    }
}
