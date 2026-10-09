package com.doffi4.doffisecure.eval

import com.doffi4.doffisecure.domain.advisor.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AdvisorEvalHarnessTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `selected prompt metadata hashes and exported instructions agree`() {
        val versions = com.doffi4.doffisecure.data.advisor.AdvisorPromptVersion.entries
        val hashes = mutableSetOf<String>()
        for (version in versions) {
            val run = AdvisorEvalHarness.run(EvalOptions(cases = "S01", outputRoot = temporary.root, promptVersion = version))
            val results = JSONObject(File(run, "results.json").readText())
            assertEquals(version.id, results.getString("prompt_version"))
            assertEquals("PENDING_LIVE", results.getString("quality_status"))
            val rows = results.getJSONArray("observations")
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                val language = if (row.getString("language") == "ru") AdvisorLanguage.RUSSIAN else AdvisorLanguage.ENGLISH
                assertEquals(com.doffi4.doffisecure.data.advisor.ClaudeAdvisorPrompts.system(language, version), row.getJSONObject("request").getString("system"))
            }
            hashes.add(results.getJSONObject("prompt_sha256").getString("en"))
        }
        assertEquals(2, hashes.size)
        assertThrows(IllegalArgumentException::class.java) { com.doffi4.doffisecure.data.advisor.AdvisorPromptVersion.fromId("free-form-private-prompt") }
    }

    @Test fun `dataset has thirty ready and six fail-closed cases with expectations`() {
        val cases = SyntheticAdvisorCases.all
        assertEquals(36, cases.size)
        assertEquals(36, cases.map { it.id }.toSet().size)
        assertEquals(30, cases.count { it.summary != null })
        assertTrue(cases.all { it.expectedBehavior.isNotBlank() })
        assertTrue(cases.filter { it.state != AnalysisState.COMPLETE }.all { it.summary == null })
        assertTrue(cases.any { it.summary?.passwordEntryCount == Int.MAX_VALUE })
    }

    @Test fun `dry export has only four counts and pending model observations`() {
        val run = AdvisorEvalHarness.run(EvalOptions("dry", outputRoot = temporary.root))
        val results = JSONObject(File(run, "results.json").readText())
        assertEquals(72, results.getJSONArray("observations").length())
        assertTrue(results.isNull("requested_model"))
        assertEquals("PENDING_LIVE", results.getString("quality_status"))
        val rows = results.getJSONArray("observations")
        val allowed = setOf("password_entry_count", "weak_password_count", "reused_password_count", "duplicate_credential_count")
        var requests = 0
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            assertTrue(row.isNull("guidance"))
            if (!row.isNull("request")) {
                requests++
                val aggregate = JSONObject(row.getJSONObject("request").getJSONArray("messages").getJSONObject(0).getString("content"))
                assertEquals(allowed, aggregate.keys().asSequence().toSet())
                assertFalse(aggregate.has("scenario_id"))
            } else assertEquals("BLOCKED_LOCAL", row.getString("status"))
        }
        assertEquals(60, requests)
    }

    @Test fun `live requires explicit selection and checks cap before constructing client`() {
        var constructed = false
        val factory = { _: com.doffi4.doffisecure.data.advisor.ClaudeDeveloperConfig ->
            constructed = true
            SecurityAdvisorService { _, _ -> error("Must not send") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            AdvisorEvalHarness.run(EvalOptions("live", outputRoot = temporary.root), "synthetic", "synthetic-model", factory)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AdvisorEvalHarness.run(EvalOptions("live", "all", maxRequests = 2, outputRoot = temporary.root), "synthetic", "synthetic-model", factory)
        }
        assertFalse(constructed)
    }

    @Test fun `blocked and missing credential runs never construct a client`() {
        var constructed = false
        val factory = { _: com.doffi4.doffisecure.data.advisor.ClaudeDeveloperConfig ->
            constructed = true
            SecurityAdvisorService { _, _ -> error("Must not send") }
        }
        val blocked = AdvisorEvalHarness.run(EvalOptions("live", "S31,S32,S33,S34,S35,S36", outputRoot = temporary.root), "synthetic", "synthetic-model", factory)
        assertEquals(0, JSONObject(File(blocked, "results.json").readText()).getInt("attempted_requests"))
        val pending = AdvisorEvalHarness.run(EvalOptions("live", "S01", outputRoot = temporary.root), null, "synthetic-model", factory)
        assertEquals("PENDING_LIVE", JSONObject(File(pending, "results.json").readText()).getString("quality_status"))
        assertFalse(constructed)
    }

    @Test fun `provider failure stops remaining calls and records safe reason only`() {
        var calls = 0
        val run = AdvisorEvalHarness.run(EvalOptions("live", "S01,S02", maxRequests = 4, outputRoot = temporary.root), "PRIVATE_KEY_CANARY", "synthetic-model") {
            SecurityAdvisorService { _, _ -> calls++; AdvisorOutcome.Failure(AdvisorFailure.UNAVAILABLE) }
        }
        val text = File(run, "results.json").readText()
        assertEquals(1, calls)
        assertFalse(text.contains("PRIVATE_KEY_CANARY"))
        assertEquals("PARTIAL_LIVE", JSONObject(text).getString("quality_status"))
        assertEquals("UNAVAILABLE", JSONObject(text).getJSONArray("observations").getJSONObject(0).getString("failure"))
    }

    @Test fun `manual review stays pending until every applicable criterion is scored`() {
        val run = successfulRun()
        val template = JSONObject(File(run, "review.template.json").readText())
        File(run, "review.json").writeText(template.toString())
        var report = AdvisorEvalHarness.score(run)
        assertEquals(0, report.getInt("reviewed_responses"))
        assertEquals("PENDING_REVIEW", report.getString("quality_status"))
        val review = template.getJSONArray("reviews").getJSONObject(0)
        val scores = review.getJSONObject("scores")
        scores.keys().forEach { scores.put(it, 2) }
        review.put("critical_violation", true).put("notes", "Synthetic critical failure exercise")
        File(run, "review.json").writeText(template.toString())
        report = AdvisorEvalHarness.score(run)
        assertEquals(1, report.getInt("reviewed_responses"))
        assertEquals(1, report.getInt("critical_violations"))
        assertFalse(report.getBoolean("release_authorized"))
        assertEquals(20, report.getJSONObject("totals").getInt(review.getString("observation_id")))
        assertFalse(report.getJSONObject("meets_rubric_threshold").getBoolean(review.getString("observation_id")))
        assertEquals(1, report.getJSONObject("by_language").getJSONObject("en").getInt("critical_violations"))
    }

    @Test fun `changed results cannot reuse a stale review`() {
        val run = successfulRun()
        File(run, "review.json").writeText(File(run, "review.template.json").readText())
        File(run, "results.json").appendText("\n")
        assertThrows(IllegalArgumentException::class.java) { AdvisorEvalHarness.score(run) }
    }

    @Test fun `critical violation is counted even when numerical review is incomplete`() {
        val run = successfulRun()
        val review = JSONObject(File(run, "review.template.json").readText())
        review.getJSONArray("reviews").getJSONObject(0)
            .put("critical_violation", true).put("notes", "Synthetic confirmed critical failure; numeric review pending")
        File(run, "review.json").writeText(review.toString())
        val report = AdvisorEvalHarness.score(run)
        assertEquals(0, report.getInt("reviewed_responses"))
        assertEquals("PENDING_REVIEW", report.getString("quality_status"))
        assertEquals(1, report.getInt("critical_violations"))
        assertEquals(1, report.getJSONObject("by_language").getJSONObject("en").getInt("critical_violations"))
        assertFalse(report.getBoolean("release_authorized"))
    }

    @Test fun `unexpected errors are reduced to fixed failure and never exported`() {
        val run = AdvisorEvalHarness.run(EvalOptions("live", "S01", outputRoot = temporary.root), "PRIVATE_KEY_CANARY", "synthetic-model") {
            SecurityAdvisorService { _, _ -> throw IllegalStateException("PRIVATE_EXCEPTION_CANARY") }
        }
        val text = File(run, "results.json").readText()
        assertFalse(text.contains("PRIVATE_EXCEPTION_CANARY"))
        assertFalse(text.contains("PRIVATE_KEY_CANARY"))
        assertEquals("ERROR", JSONObject(text).getJSONArray("observations").getJSONObject(0).getString("failure"))
        assertEquals(1, JSONObject(text).getInt("attempted_requests"))
    }

    @Test fun `bad selection repetition language and mode are rejected before any send`() {
        for (options in listOf(
            EvalOptions("unknown", outputRoot = temporary.root),
            EvalOptions(cases = "S99", outputRoot = temporary.root),
            EvalOptions(cases = "S01,S01", outputRoot = temporary.root),
            EvalOptions(languages = "en,en", outputRoot = temporary.root),
            EvalOptions(languages = "de", outputRoot = temporary.root),
            EvalOptions(repeats = 4, outputRoot = temporary.root),
        )) assertThrows(IllegalArgumentException::class.java) { AdvisorEvalHarness.run(options) }
    }

    @Test fun `selected cases languages and repeats bound actual calls`() {
        var calls = 0
        val run = AdvisorEvalHarness.run(EvalOptions("live", "S07", "ru", 3, 3, temporary.root), "synthetic-test-key", "synthetic-model") {
            SecurityAdvisorService { summary, language ->
                calls++
                assertEquals(18, summary.reusedPasswordCount)
                assertEquals(AdvisorLanguage.RUSSIAN, language)
                AdvisorOutcome.Success(AdvisorGuidance("Synthetic", listOf("Synthetic")))
            }
        }
        assertEquals(3, calls)
        val results = JSONObject(File(run, "results.json").readText())
        assertEquals(3, results.getInt("planned_requests"))
        assertEquals(3, results.getInt("successful_responses"))
        assertEquals("PENDING_REVIEW", results.getString("quality_status"))
    }

    @Test fun `manual scoring rejects wrong linkage extra criteria and invalid scores`() {
        val run = successfulRun()
        val original = File(run, "review.template.json").readText()
        for (mutate in listOf<(JSONObject) -> Unit>(
            { it.getJSONArray("reviews").getJSONObject(0).put("observation_id", "S99_en_1") },
            { it.getJSONArray("reviews").getJSONObject(0).getJSONObject("scores").put("invented", 2) },
            { it.getJSONArray("reviews").getJSONObject(0).getJSONObject("scores").put("count_interpretation", "2") },
            { it.getJSONArray("reviews").getJSONObject(0).getJSONObject("scores").put("count_interpretation", 3) },
        )) {
            val review = JSONObject(original); mutate(review)
            File(run, "review.json").writeText(review.toString())
            assertThrows(IllegalArgumentException::class.java) { AdvisorEvalHarness.score(run) }
        }
    }

    private fun successfulRun(): File = AdvisorEvalHarness.run(
        EvalOptions("live", "S01", outputRoot = temporary.root), "synthetic-test-key", "synthetic-model",
    ) { SecurityAdvisorService { _, _ -> AdvisorOutcome.Success(AdvisorGuidance("Synthetic test response", listOf("Synthetic test step"))) } }
}
