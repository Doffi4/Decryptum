package com.doffi4.doffisecure.data.advisor

import com.doffi4.doffisecure.domain.advisor.*
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Provider envelope/response codec. Has no dependency on the app or vault models. */
object ClaudeAdvisorCodec {
    val PROMPT_VERSION: String get() = ClaudeAdvisorPrompts.current.id
    fun requestJson(summary: AdvisorSummary, model: String, language: AdvisorLanguage,
        promptVersion: AdvisorPromptVersion = ClaudeAdvisorPrompts.current): String {
        val schema = JSONObject().put("type", "object")
            .put("properties", JSONObject()
                .put("overview", JSONObject().put("type", "string"))
                .put("checklist", JSONObject().put("type", "array").put("items", JSONObject().put("type", "string"))))
            .put("required", JSONArray(listOf("overview", "checklist")))
            .put("additionalProperties", false)
        val instructions = ClaudeAdvisorPrompts.system(language, promptVersion)
        return JSONObject().put("model", model).put("max_tokens", 1200).put("system", instructions)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", AdvisorPayloadJson.encode(summary))))
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
