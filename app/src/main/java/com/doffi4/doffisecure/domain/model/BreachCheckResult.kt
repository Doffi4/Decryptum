package com.doffi4.doffisecure.domain.model

/**
 * Result state for HaveIBeenPwned k-Anonymity breach check.
 */
sealed interface BreachCheckResult {
    /** Initial state when no check has been performed yet */
    data object Idle : BreachCheckResult

    /** Network check is currently in progress */
    data object Checking : BreachCheckResult

    /** Password was not found in any known public data breaches */
    data class Clean(val checkedAt: Long = System.currentTimeMillis()) : BreachCheckResult

    /** Password was found in public data breaches [count] times */
    data class Compromised(val count: Int) : BreachCheckResult

    /** Check failed due to network connectivity, timeout, or server error */
    data class Error(val message: String? = null) : BreachCheckResult
}
