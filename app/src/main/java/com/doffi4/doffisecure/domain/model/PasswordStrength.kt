package com.doffi4.doffisecure.domain.model

/**
 * Heuristic password-strength levels behind the 4-segment meter.
 *
 * Scored as (length points) × (unique character-class points) so the result is
 * easy to reason about: short digit-only strings land in [WEAK], a mixed
 * ~12-character password in [MEDIUM]/[STRONG], and 14+ characters with several
 * classes reach [VERY_STRONG].
 */
enum class PasswordStrength(val level: Int, val label: String) {
    WEAK(1, "Слабый"),
    MEDIUM(2, "Средний"),
    STRONG(3, "Сильный"),
    VERY_STRONG(4, "Очень сильный");

    companion object {

        /** Evaluates [password] and returns its strength level. */
        fun fromPassword(password: String): PasswordStrength {
            if (password.isBlank()) return WEAK

            val length = password.length
            val classCount = listOf(
                password.any { it.isUpperCase() },
                password.any { it.isLowerCase() },
                password.any { it.isDigit() },
                password.any { !it.isLetterOrDigit() }
            ).count { it }

            val lengthPoints = when {
                length >= 20 -> 12
                length >= 14 -> 9
                length >= 10 -> 6
                length >= 8 -> 4
                length >= 6 -> 2
                else -> 1
            }
            val classPoints = when (classCount) {
                4 -> 4
                3 -> 3
                2 -> 2
                else -> 1
            }

            val score = lengthPoints * classPoints
            return when {
                score >= 28 -> VERY_STRONG
                score >= 18 -> STRONG
                score >= 8 -> MEDIUM
                else -> WEAK
            }
        }
    }
}