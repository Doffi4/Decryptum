package com.doffi4.doffisecure

import com.doffi4.doffisecure.data.advisor.*
import com.doffi4.doffisecure.domain.advisor.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

/** Guards approved prompt text and actual wire routing; does not measure model obedience. */
class ClaudeAdvisorPromptTest {
    @Test fun `baseline instructions stay byte-for-byte unchanged for both languages`() {
        assertEquals("eac9aa664e7d7f3a77db427b90d104d213b4dc3751c7374d697a2dbf868c972c",
            sha256(ClaudeAdvisorPrompts.system(AdvisorLanguage.ENGLISH, AdvisorPromptVersion.BASELINE_V1)))
        assertEquals("c173ccd3e1d282279068e0da90e555a0cc2268e3c2f63f2a2f02cf59fb18b295",
            sha256(ClaudeAdvisorPrompts.system(AdvisorLanguage.RUSSIAN, AdvisorPromptVersion.BASELINE_V1)))
    }

    @Test fun `candidate snapshot requires an explicit version change for future text edits`() {
        assertEquals("a2af752a681cb4430c54c91045bb066863d9639f92018a48f2f862cc06bffd69",
            sha256(ClaudeAdvisorPrompts.system(AdvisorLanguage.ENGLISH, AdvisorPromptVersion.CANDIDATE_V2)))
        assertEquals("880722cec8088e1c180b20bf0beca2e5342608e9e8ce88b40e2801147fd6bd23",
            sha256(ClaudeAdvisorPrompts.system(AdvisorLanguage.RUSSIAN, AdvisorPromptVersion.CANDIDATE_V2)))
    }

    @Test fun `candidate role and prohibitions are present in the actual system message`() {
        val request = JSONObject(ClaudeAdvisorCodec.requestJson(AdvisorSummary(10, 10, 10, 10), "synthetic-model", AdvisorLanguage.ENGLISH))
        val prompt = request.getString("system")
        assertEquals("aggregate-v2-candidate", ClaudeAdvisorCodec.PROMPT_VERSION)
        assertEquals(ClaudeAdvisorPrompts.system(AdvisorLanguage.ENGLISH), prompt)
        for (rule in listOf(
            "already-computed LOCAL", "NOT an antivirus", "four integer counts", "categories may overlap",
            "Never claim you inspected passwords", "Never claim a password is breached",
            "Never claim the vault is secure", "Never invent accounts", "Never estimate secret entropy",
            "Never ask the user to paste credentials", "Never request TOTP seeds",
            "Never recommend unsafe export", "Never produce a universal security score",
            "not proof of security", "not evidence of an empty vault", "never automatic deletion",
        )) assertTrue("Missing prompt rule: $rule", prompt.contains(rule))
        assertEquals(setOf("model", "max_tokens", "system", "messages", "output_config"), request.keys().asSequence().toSet())
        assertEquals("""{"password_entry_count":10,"weak_password_count":10,"reused_password_count":10,"duplicate_credential_count":10}""",
            request.getJSONArray("messages").getJSONObject(0).getString("content"))
    }

    @Test fun `language selection keeps JSON keys fixed and schema unchanged across versions`() {
        val reference = JSONObject(ClaudeAdvisorCodec.requestJson(AdvisorSummary(0, 0, 0, 0), "synthetic-model", AdvisorLanguage.ENGLISH, AdvisorPromptVersion.BASELINE_V1))
        for (version in AdvisorPromptVersion.entries) for (language in AdvisorLanguage.entries) {
            val request = JSONObject(ClaudeAdvisorCodec.requestJson(AdvisorSummary(0, 0, 0, 0), "synthetic-model", language, version))
            val instructions = request.getString("system")
            assertTrue(instructions.endsWith(if (language == AdvisorLanguage.RUSSIAN) " Respond in Russian." else " Respond in English."))
            assertEquals(reference.getJSONObject("output_config").toString(), request.getJSONObject("output_config").toString())
            assertEquals(1200, request.getInt("max_tokens"))
            assertEquals(reference.getJSONArray("messages").toString(), request.getJSONArray("messages").toString())
            assertFalse(request.has("prompt_version"))
        }
    }

    private fun sha256(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
