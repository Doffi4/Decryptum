package com.doffi4.doffisecure.domain.model

import java.time.Instant

/**
 * Domain model for a Password.
 * This is what the UI and UseCases will interact with.
 */
data class Password(
    val id: Long,
    val service: String,
    val username: String,
    val password: String,
    val url: String?,
    val createdAt: Long
)
