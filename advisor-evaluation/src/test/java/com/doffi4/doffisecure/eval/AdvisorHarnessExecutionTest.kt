package com.doffi4.doffisecure.eval

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import com.doffi4.doffisecure.data.advisor.AdvisorPromptVersion
import com.doffi4.doffisecure.data.advisor.ClaudeAdvisorPrompts

/** Ordinary test suites skip this entry point, even if an API key is in the environment. */
class AdvisorHarnessExecutionTest {
    @Test fun executeExplicitEvaluation() {
        assumeTrue(System.getProperty("decryptum.advisor.eval.enabled") == "true")
        val outputRoot = File(requireNotNull(System.getProperty("decryptum.advisor.eval.output")))
        val mode = property("mode", "dry")
        if (mode == "score") {
            val runId = property("run", "")
            require(runId.matches(Regex("[A-Za-z0-9_-]{1,100}"))) { "Use a run directory name, not a path" }
            val report = AdvisorEvalHarness.score(File(outputRoot, runId))
            println("Review status: ${report.getString("quality_status")}; see ${File(outputRoot, runId)}")
        } else {
            val options = EvalOptions(mode, System.getProperty("decryptum.advisor.eval.cases"),
                property("languages", "en,ru"), property("repeats", "1").toInt(),
                property("maxRequests", "2").toInt(), outputRoot,
                AdvisorPromptVersion.fromId(property("prompt", ClaudeAdvisorPrompts.current.id)))
            val run = AdvisorEvalHarness.run(options,
                if (mode == "live") System.getenv("DECRYPTUM_ADVISOR_EVAL_KEY") else null,
                if (mode == "live") System.getenv("DECRYPTUM_ADVISOR_EVAL_MODEL") else null)
            println("Evaluation artifacts: $run (synthetic only; see results.json for pending/observed status)")
        }
    }

    private fun property(name: String, fallback: String) = System.getProperty("decryptum.advisor.eval.$name") ?: fallback
}
