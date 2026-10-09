package com.doffi4.doffisecure

import com.doffi4.doffisecure.data.advisor.ClaudeAdvisorCodec
import com.doffi4.doffisecure.domain.advisor.*
import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.security.LocalSecurityAnalyzer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Synthetic vault -> real analyzer -> sanitizer -> final Claude envelope. No network or real vault. */
class AdvisorEvaluationPrivacyTest {
    @Test fun `prohibited record data never enters any field of the final request`() {
        val prohibited = listOf(
            "CANARY_PASSWORD_HASH", "CANARY_HIBP_PREFIX", "CANARY_TOTP_SECRET",
            "CANARY_PRIVATE_PASSKEY", "CANARY_PUBLIC_PASSKEY", "CANARY_USERNAME",
            "canary-email@private.invalid", "CANARY_ACCOUNT_SERVICE", "private-domain.invalid",
            "https://private-url.invalid/path", "private.android.package",
            "CANARY_DATABASE_IDENTIFIER", "CANARY_MASTER_PASSWORD", "CANARY_ENCRYPTION_KEY",
            "CANARY_RAW_EXCEPTION", "CANARY_CLIPBOARD",
        )
        // Inject each sentinel through secret-bearing local fields; none may appear anywhere in the envelope.
        // Names are synthetic strings, not an attempt to provide real hash/passkey objects to the client.
        for (sentinel in prohibited) {
            val entries = listOf(
                Password(912345678901, sentinel, sentinel, "s3cr3t!", sentinel, 9876543210, sentinel),
                Password(912345678902, sentinel, sentinel, "s3cr3t!", sentinel, 9876543211, sentinel),
                Password(912345678903, "OTHER_$sentinel", sentinel, "s3cr3t!", sentinel, 9876543212, sentinel),
            )
            val summary = AdvisorSanitizer.sanitize(LocalSecurityAnalyzer().analyze(entries))
            for (language in AdvisorLanguage.entries) {
                val body = ClaudeAdvisorCodec.requestJson(summary, "synthetic-model", language)
                assertFalse("Prohibited local data escaped: $sentinel", body.contains(sentinel))
                assertFalse(body.contains("s3cr3t!"))
                assertFalse(body.contains("912345678901"))
                assertFalse(body.contains("9876543210"))
                val envelope = JSONObject(body)
                assertEquals(setOf("model", "max_tokens", "system", "messages", "output_config"), envelope.keys().asSequence().toSet())
                val messages = envelope.getJSONArray("messages")
                assertEquals(1, messages.length())
                val message = messages.getJSONObject(0)
                assertEquals(setOf("role", "content"), message.keys().asSequence().toSet())
                assertEquals("user", message.get("role"))
                assertEquals(
                    """{"password_entry_count":3,"weak_password_count":3,"reused_password_count":3,"duplicate_credential_count":2}""",
                    message.get("content"),
                )
            }
        }
    }

    @Test fun `mapper snapshot does not retain mutable local finding groups`() {
        val references = mutableListOf(com.doffi4.doffisecure.domain.security.SecurityItemReference(42, "PRIVATE_SERVICE", "PRIVATE_USER"))
        val local = com.doffi4.doffisecure.domain.security.SecuritySummary(1, 1, listOf(
            com.doffi4.doffisecure.domain.security.SecurityFinding(
                com.doffi4.doffisecure.domain.security.SecurityFindingType.WEAK_PASSWORD,
                com.doffi4.doffisecure.domain.security.SecurityPriority.HIGH,
                listOf(com.doffi4.doffisecure.domain.security.SecurityFindingGroup(references)),
            ),
        ))
        val counts = AdvisorSanitizer.sanitize(local)
        references.clear()
        assertEquals(
            """{"password_entry_count":1,"weak_password_count":1,"reused_password_count":0,"duplicate_credential_count":0}""",
            AdvisorPayloadJson.encode(counts),
        )
    }
}
