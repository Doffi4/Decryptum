package com.doffi4.doffisecure

import com.doffi4.doffisecure.domain.advisor.AdvisorPayloadJson
import com.doffi4.doffisecure.domain.advisor.AdvisorSummary
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Modifier

class AdvisorPayloadJsonTest {
    @Test fun `writer emits exactly four integer fields including boundary values`() {
        assertEquals(
            """{"password_entry_count":5,"weak_password_count":2,"reused_password_count":3,"duplicate_credential_count":0}""",
            AdvisorPayloadJson.encode(AdvisorSummary(5, 2, 3, 0)),
        )
        assertEquals(
            """{"password_entry_count":0,"weak_password_count":0,"reused_password_count":0,"duplicate_credential_count":0}""",
            AdvisorPayloadJson.encode(AdvisorSummary(0, 0, 0, 0)),
        )
        assertEquals(
            """{"password_entry_count":2147483647,"weak_password_count":2147483647,"reused_password_count":2147483647,"duplicate_credential_count":2147483647}""",
            AdvisorPayloadJson.encode(AdvisorSummary(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)),
        )
    }

    @Test fun `contract has no storage for prohibited text binary or vault references`() {
        val fields = AdvisorSummary::class.java.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }
        assertEquals(setOf("passwordEntryCount", "weakPasswordCount", "reusedPasswordCount", "duplicateCredentialCount"), fields.map { it.name }.toSet())
        assertTrue(fields.all { it.type == Int::class.javaPrimitiveType && Modifier.isFinal(it.modifiers) })
        assertTrue(Modifier.isFinal(AdvisorSummary::class.java.modifiers))
    }

    @Test fun `every count rejects negatives or values exceeding entry count`() {
        listOf(
            { AdvisorSummary(-1, 0, 0, 0) },
            { AdvisorSummary(1, -1, 0, 0) }, { AdvisorSummary(1, 2, 0, 0) },
            { AdvisorSummary(1, 0, -1, 0) }, { AdvisorSummary(1, 0, 2, 0) },
            { AdvisorSummary(1, 0, 0, -1) }, { AdvisorSummary(1, 0, 0, 2) },
        ).forEach { create -> assertThrows(IllegalArgumentException::class.java) { create() } }
    }
}
