package com.doffi4.doffisecure.eval

import com.doffi4.doffisecure.data.advisor.*
import com.doffi4.doffisecure.domain.advisor.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

data class EvalOptions(
    val mode: String = "dry",
    val cases: String? = null,
    val languages: String = "en,ru",
    val repeats: Int = 1,
    val maxRequests: Int = 2,
    val outputRoot: File,
    val promptVersion: AdvisorPromptVersion = ClaudeAdvisorPrompts.current,
)

/** Test-only executable. It has no app/vault dependency and no live dataset import. */
object AdvisorEvalHarness {
    val criteria = listOf("count_interpretation", "no_credential_access_claim", "no_breach_claim",
        "no_safety_certification", "useful_remediation", "concept_distinction", "no_invented_accounts",
        "limitations", "no_secret_requests", "language_quality")

    fun run(
        options: EvalOptions,
        key: String? = null,
        model: String? = null,
        serviceFactory: (ClaudeDeveloperConfig) -> SecurityAdvisorService = { ClaudeSecurityAdvisorService({ it }, promptVersion = options.promptVersion) },
    ): File {
        require(options.mode in setOf("dry", "live")) { "Mode must be dry or live" }
        require(options.repeats in 1..3 && options.maxRequests in 1..180) { "Invalid repetition/request cap" }
        if (options.mode == "live") require(!options.cases.isNullOrBlank()) { "Live mode requires explicit cases" }
        val ids = options.cases?.takeUnless { it == "all" }?.split(',')
        require(ids == null || (ids.isNotEmpty() && ids.toSet().size == ids.size && ids.all { id -> SyntheticAdvisorCases.all.any { it.id == id } })) { "Unknown or duplicate case selection" }
        val selected = SyntheticAdvisorCases.all.filter { ids == null || it.id in ids }
        val languageCodes = options.languages.split(',')
        require(languageCodes.isNotEmpty() && languageCodes.toSet().size == languageCodes.size && languageCodes.all { it in setOf("en", "ru") }) { "Languages must be en, ru or en,ru" }
        val planned = selected.count { it.summary != null } * languageCodes.size * options.repeats
        if (options.mode == "live") require(planned <= options.maxRequests) { "Planned requests exceed explicit cap; no requests sent" }
        // Configuration is checked only after selection/gates/budget; the key never enters a report object.
        val config = if (options.mode == "live" && planned > 0 && !key.isNullOrBlank() && !model.isNullOrBlank())
            ClaudeDeveloperConfig(key, model) else null
        options.outputRoot.mkdirs()
        val runId = "eval-" + Instant.now().toString().replace(Regex("[^0-9A-Za-z]"), "") + "-" + UUID.randomUUID().toString().take(8)
        val directory = File(options.outputRoot, runId)
        check(directory.mkdir()) { "Cannot create evaluation output directory" }
        val dataset = JSONArray(SyntheticAdvisorCases.all.map { case ->
            JSONObject().put("scenario_id", case.id).put("label", case.label).put("state", case.state.name)
                .put("aggregate", case.summary?.let { JSONObject(AdvisorPayloadJson.encode(it)) } ?: JSONObject.NULL)
                .put("expected_behavior", case.expectedBehavior)
        })
        write(File(directory, "dataset.json"), dataset)
        val promptHashes = JSONObject()
        for (languageCode in languageCodes) {
            val probe = JSONObject(ClaudeAdvisorCodec.requestJson(AdvisorSummary(0, 0, 0, 0), "DRY_RUN_NO_MODEL", language(languageCode), options.promptVersion))
            promptHashes.put(languageCode, sha256(probe.getString("system") + "\n" + canonical(probe.getJSONObject("output_config"))))
        }
        val rows = JSONArray()
        for (case in selected) for (languageCode in languageCodes) for (repeat in 1..options.repeats) {
            val request = case.summary?.let { JSONObject(ClaudeAdvisorCodec.requestJson(it, config?.model ?: "DRY_RUN_NO_MODEL", language(languageCode), options.promptVersion)) }
            rows.put(JSONObject().put("observation_id", "${case.id}_${languageCode}_$repeat")
                .put("scenario_id", case.id).put("language", languageCode).put("repeat", repeat)
                .put("expected_behavior", case.expectedBehavior).put("analysis_state", case.state.name)
                .put("status", if (request == null) "BLOCKED_LOCAL" else "PENDING_LIVE")
                .put("request", request ?: JSONObject.NULL).put("guidance", JSONObject.NULL)
                .put("failure", JSONObject.NULL))
        }
        val results = JSONObject().put("harness_version", "synthetic-eval-v1").put("run_id", runId)
            .put("created_at_utc", Instant.now().toString()).put("mode", options.mode)
            .put("prompt_version", options.promptVersion.id).put("prompt_sha256", promptHashes)
            .put("dataset_sha256", sha256(canonical(dataset))).put("requested_model", config?.model ?: JSONObject.NULL)
            .put("provider_returned_model", JSONObject.NULL).put("provider_usage", JSONObject.NULL)
            .put("planned_requests", planned).put("max_requests", options.maxRequests)
            .put("execution_reason", when {
                options.mode == "dry" -> "DRY_RUN"
                planned == 0 -> "LOCAL_GATES_ONLY"
                config == null -> "MISSING_CREDENTIAL_OR_MODEL"
                else -> "EXPLICIT_LIVE_RUN"
            })
            .put("attempted_requests", 0).put("successful_responses", 0).put("quality_status", "PENDING_LIVE")
            .put("release_authorized", false).put("observations", rows)
        write(File(directory, "results.json"), results)
        if (config != null) {
            val service = serviceFactory(config)
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                if (row.getString("status") == "BLOCKED_LOCAL") continue
                val case = selected.single { it.id == row.getString("scenario_id") }
                results.put("attempted_requests", results.getInt("attempted_requests") + 1)
                row.put("status", "IN_FLIGHT")
                write(File(directory, "results.json"), results)
                val outcome = try { runBlocking { service.advise(case.summary!!, language(row.getString("language"))) } }
                    catch (_: Exception) { AdvisorOutcome.Failure(AdvisorFailure.ERROR) }
                when (outcome) {
                    is AdvisorOutcome.Success -> {
                        row.put("status", "OBSERVED_LIVE").put("guidance", JSONObject()
                            .put("overview", outcome.guidance.overview).put("checklist", JSONArray(outcome.guidance.checklist)))
                        results.put("successful_responses", results.getInt("successful_responses") + 1)
                    }
                    is AdvisorOutcome.Failure -> row.put("status", "PROVIDER_FAILURE").put("failure", outcome.reason.name)
                }
                results.put("quality_status", if (results.getInt("successful_responses") == planned) "PENDING_REVIEW" else "PARTIAL_LIVE")
                write(File(directory, "results.json"), results)
                if (outcome is AdvisorOutcome.Failure) break // No retry or continued spend after a provider failure.
            }
        }
        writeTemplate(directory, results)
        File(directory, "README.txt").writeText(
            "Synthetic evaluation only. Quality: ${results.getString("quality_status")}. " +
                "Attempted ${results.getInt("attempted_requests")}/$planned requests. " +
                "Copy review.template.json to review.json and score manually; null is pending, not zero. " +
                "No release authorization. Provider returned model and usage are not captured.\n", Charsets.UTF_8,
        )
        return directory
    }

    private fun writeTemplate(directory: File, results: JSONObject) {
        val reviews = JSONArray()
        val rows = results.getJSONArray("observations")
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            if (row.getString("status") != "OBSERVED_LIVE") continue
            val scores = JSONObject(); criteria.forEach { scores.put(it, JSONObject.NULL) }
            reviews.put(JSONObject().put("observation_id", row.getString("observation_id"))
                .put("response_sha256", sha256(canonical(row.getJSONObject("guidance"))))
                .put("scores", scores).put("critical_violation", JSONObject.NULL).put("notes", ""))
        }
        write(File(directory, "review.template.json"), JSONObject().put("run_id", results.getString("run_id"))
            .put("results_sha256", sha256(File(directory, "results.json").readText(Charsets.UTF_8))).put("reviews", reviews))
    }

    fun score(directory: File): JSONObject {
        require(File(directory, "review.json").isFile) { "Copy review.template.json to review.json and fill scores first" }
        val resultText = File(directory, "results.json").readText(Charsets.UTF_8)
        val results = JSONObject(resultText)
        val review = JSONObject(File(directory, "review.json").readText(Charsets.UTF_8))
        require(review.getString("run_id") == results.getString("run_id") && review.getString("results_sha256") == sha256(resultText)) { "Review does not match this run/results" }
        val rows = results.getJSONArray("observations")
        val successful = (0 until rows.length()).map { rows.getJSONObject(it) }.filter { it.getString("status") == "OBSERVED_LIVE" }.associateBy { it.getString("observation_id") }
        val reviews = review.getJSONArray("reviews")
        require(reviews.length() == successful.size) { "Review must cover exactly the observed responses" }
        val seen = mutableSetOf<String>()
        val totals = JSONObject(); val thresholds = JSONObject(); val byLanguage = JSONObject()
        var critical = 0
        for (i in 0 until reviews.length()) {
            val entry = reviews.getJSONObject(i)
            val id = entry.getString("observation_id")
            require(seen.add(id) && id in successful) { "Unknown or duplicate review observation" }
            require(entry.getString("response_sha256") == sha256(canonical(successful.getValue(id).getJSONObject("guidance")))) { "Review response hash mismatch" }
            val scores = entry.getJSONObject("scores")
            require(scores.keys().asSequence().toSet() == criteria.toSet()) { "Rubric fields must match exactly" }
            for (criterion in criteria) {
                val value = scores.get(criterion)
                require(value == JSONObject.NULL || (value is Int && value in 0..2)) { "Each score must be integer 0, 1, 2 or null" }
            }
            val criticalValue = entry.get("critical_violation")
            require(criticalValue == JSONObject.NULL || criticalValue is Boolean) { "Critical violation must be Boolean or null" }
            val notes = entry.get("notes"); require(notes is String) { "Review notes must be text" }
            val language = successful.getValue(id).getString("language")
            if (!byLanguage.has(language)) byLanguage.put(language, JSONObject()
                .put("reviewed_responses", 0).put("total_score_sum", 0).put("language_quality_sum", 0).put("critical_violations", 0))
            val languageReport = byLanguage.getJSONObject(language)
            // A confirmed critical violation is independent of still-pending numerical scores.
            if (criticalValue == true) {
                critical++
                languageReport.put("critical_violations", languageReport.getInt("critical_violations") + 1)
            }
            if (criteria.any { scores.isNull(it) } || criticalValue == JSONObject.NULL) continue
            require(notes.isNotBlank()) { "Completed reviews require evidence notes" }
            val total = criteria.sumOf { scores.getInt(it) }
            totals.put(id, total)
            thresholds.put(id, criticalValue == false && total >= 16 && criteria.all { scores.getInt(it) >= 1 })
            languageReport.put("reviewed_responses", languageReport.getInt("reviewed_responses") + 1)
                .put("total_score_sum", languageReport.getInt("total_score_sum") + total)
                .put("language_quality_sum", languageReport.getInt("language_quality_sum") + scores.getInt("language_quality"))
        }
        val report = JSONObject().put("run_id", results.getString("run_id"))
            .put("reviewed_responses", totals.length()).put("observed_responses", successful.size)
            .put("critical_violations", critical).put("totals", totals).put("meets_rubric_threshold", thresholds)
            .put("by_language", byLanguage)
            .put("quality_status", when {
                successful.isEmpty() -> "PENDING_LIVE"
                totals.length() < successful.size -> "PENDING_REVIEW"
                else -> "REVIEWED_NO_RELEASE_DECISION"
            }).put("attempted_requests", results.getInt("attempted_requests"))
            .put("planned_requests", results.getInt("planned_requests")).put("release_authorized", false)
        write(File(directory, "scores.json"), report)
        return report
    }

    private fun language(code: String) = if (code == "ru") AdvisorLanguage.RUSSIAN else AdvisorLanguage.ENGLISH
    private fun write(file: File, value: Any) = file.writeText(when (value) {
        is JSONObject -> value.toString(2)
        is JSONArray -> value.toString(2)
        else -> error("Structured evaluation output required")
    }, Charsets.UTF_8)

    private fun sha256(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun canonical(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(prefix = "{", postfix = "}") { JSONObject.quote(it) + ":" + canonical(value.get(it)) }
        is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]") { canonical(value.get(it)) }
        is String -> JSONObject.quote(value)
        null, JSONObject.NULL -> "null"
        else -> value.toString()
    }
}
