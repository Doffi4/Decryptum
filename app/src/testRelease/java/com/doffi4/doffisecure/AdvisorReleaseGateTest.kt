package com.doffi4.doffisecure

import android.content.ContextWrapper
import com.doffi4.doffisecure.data.advisor.AdvisorServiceFactory
import com.doffi4.doffisecure.domain.advisor.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class AdvisorReleaseGateTest {
    @Test fun `release classpath excludes the evaluation transport and provider codec`() {
        for (name in listOf(
            "com.doffi4.doffisecure.data.advisor.ClaudeSecurityAdvisorService",
            "com.doffi4.doffisecure.data.advisor.ClaudeDeveloperConfig",
            "com.doffi4.doffisecure.data.advisor.ClaudeAdvisorCodec",
            "com.doffi4.doffisecure.data.advisor.ClaudeAdvisorPrompts",
            "com.doffi4.doffisecure.data.advisor.AdvisorPromptVersion",
        )) {
            val loaded = try { Class.forName(name); true } catch (_: ClassNotFoundException) { false }
            org.junit.Assert.assertFalse("Evaluation class is accessible in release: $name", loaded)
        }
    }

    @Test fun `release never reads developer configuration or checks network authorization`() = runTest {
        val context = object : ContextWrapper(null) {
            override fun getFilesDir(): File = error("Release must never read developer configuration")
        }
        val service = AdvisorServiceFactory.create(context) { error("Release must never attempt remote access") }
        assertEquals(AdvisorOutcome.Failure(AdvisorFailure.MISSING_CONFIGURATION), service.advise(AdvisorSummary(1, 1, 0, 0), AdvisorLanguage.ENGLISH))
    }
}
