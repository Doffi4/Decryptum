package com.doffi4.doffisecure.data.advisor

import android.content.Context
import com.doffi4.doffisecure.domain.advisor.DisabledSecurityAdvisorService
import com.doffi4.doffisecure.domain.advisor.SecurityAdvisorService

/** Production enablement requires a separately reviewed authenticated proxy. */
object AdvisorServiceFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context, canSend: () -> Boolean): SecurityAdvisorService = DisabledSecurityAdvisorService()
}
