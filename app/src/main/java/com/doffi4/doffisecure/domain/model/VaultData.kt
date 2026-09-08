package com.doffi4.doffisecure.domain.model

/**
 * Encapsulates full vault data (passwords and passkeys) for export, import,
 * and backup operations.
 */
data class VaultData(
    val passwords: List<Password> = emptyList(),
    val passkeys: List<Passkey> = emptyList()
)
