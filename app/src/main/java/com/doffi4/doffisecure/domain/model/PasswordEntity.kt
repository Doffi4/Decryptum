package com.doffi4.doffisecure.domain.model

data class PasswordEntity(
    val id: Long = 0,
    val service: String,
    val username: String,
    val password: String,
    val url: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
