package com.doffi4.doffisecure

import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.security.LocalSecurityAnalyzer
import com.doffi4.doffisecure.domain.advisor.*
import com.doffi4.doffisecure.data.advisor.ClaudeAdvisorCodec
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Modifier

class AdvisorPrivacyBoundaryTest {
    @Test fun `only approved numeric fields survive secret bearing vault input`() {
        val entries = listOf(
            Password(7, "PRIVATE_SERVICE", "PRIVATE_USER", "s3cr3t!", "https://private.invalid", 123, "PRIVATE_SEED"),
            Password(8, "PRIVATE_SERVICE", "PRIVATE_USER", "s3cr3t!", "https://private.invalid", 124, "PRIVATE_SEED"),
            Password(9, "OTHER_PRIVATE_SERVICE", "PRIVATE_USER_2", "s3cr3t!", null, 0),
            Password(10, "OTP_PRIVATE", "OTP_USER", "", null, 0, "OTP_SEED"),
        )
        val dto = AdvisorSanitizer.sanitize(LocalSecurityAnalyzer().analyze(entries))
        val json = JSONObject(ClaudeAdvisorCodec.summaryJson(dto))
        assertEquals(setOf("password_entry_count", "weak_password_count", "reused_password_count", "duplicate_credential_count"), json.keys().asSequence().toSet())
        assertEquals(3, json.getInt("password_entry_count"))
        assertEquals(3, json.getInt("weak_password_count"))
        assertEquals(3, json.getInt("reused_password_count"))
        assertEquals(2, json.getInt("duplicate_credential_count"))
        val body = ClaudeAdvisorCodec.requestJson(dto, "developer-model", AdvisorLanguage.RUSSIAN)
        entries.flatMap { listOf(it.service, it.username, it.password, it.totpSecret.orEmpty(), it.url.orEmpty()) }
            .filter { it.isNotEmpty() }.forEach { assertFalse(body.contains(it)) }
        assertEquals(json.toString(), JSONObject(body).getJSONArray("messages").getJSONObject(0).getString("content"))
    }

    @Test fun `DTO cannot carry vault models or arbitrary text`() {
        val fields = AdvisorSummary::class.java.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }
        assertEquals(4, fields.size)
        assertTrue(fields.all { it.type == Int::class.javaPrimitiveType })
        val writer = ClaudeAdvisorCodec::class.java.methods.single { it.name == "summaryJson" }
        assertArrayEquals(arrayOf(AdvisorSummary::class.java), writer.parameterTypes)
    }

    @Test fun `empty analysis is a zero count summary without invented coverage`() {
        assertEquals(AdvisorSummary(0, 0, 0, 0), AdvisorSanitizer.sanitize(LocalSecurityAnalyzer().analyze(emptyList())))
    }

    @Test fun `invalid negative or impossible counts are rejected`() {
        listOf({ AdvisorSummary(-1, 0, 0, 0) }, { AdvisorSummary(1, 2, 0, 0) }, { AdvisorSummary(1, 0, -1, 0) }).forEach {
            try { it(); fail("Invalid DTO accepted") } catch (_: IllegalArgumentException) { }
        }
    }
}
