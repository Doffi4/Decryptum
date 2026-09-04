package com.doffi4.doffisecure.domain.usecase

import com.doffi4.doffisecure.domain.model.PasswordGeneratorOptions
import java.security.SecureRandom

/**
 * Generates a cryptographically random password for the given [options].
 *
 * Every enabled character class is guaranteed to appear at least once (so the
 * generated password always matches the selected checkboxes), the rest is
 * filled uniformly and the result is Fisher-Yates shuffled. Randomness comes
 * from [SecureRandom], never the plain [kotlin.random.Random].
 *
 * Returns an empty string when no character class is selected or the length is
 * non-positive (the UI prevents both cases).
 */
class GeneratePasswordUseCase {

    private val random = SecureRandom()

    private companion object {
        const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        const val LOWER = "abcdefghijklmnopqrstuvwxyz"
        const val DIGITS = "0123456789"
        const val SYMBOLS = "!@#$%^&*()_+-=[]{}|;:,.<>?~"
        const val LOOKALIKES = "0O1lI"
    }

    operator fun invoke(options: PasswordGeneratorOptions): String {
        if (!options.hasCharset || options.length <= 0) return ""

        val enabledSets = buildList {
            if (options.includeUpper) add(filterSet(UPPER, options.excludeLookalikes))
            if (options.includeLower) add(filterSet(LOWER, options.excludeLookalikes))
            if (options.includeDigits) add(filterSet(DIGITS, options.excludeLookalikes))
            if (options.includeSymbols) add(SYMBOLS)
        }.filter { it.isNotEmpty() }

        if (enabledSets.isEmpty()) return ""

        val pool = enabledSets.joinToString("")

        // Put one character from every enabled class first, then fill the rest
        // from the joint pool, and finally shuffle so order carries no bias.
        val password = CharArray(options.length)
        var index = 0
        enabledSets.forEach { set ->
            password[index++] = set[random.nextInt(set.length)]
        }
        while (index < password.size) {
            password[index++] = pool[random.nextInt(pool.length)]
        }
        for (i in password.indices.reversed()) {
            val j = random.nextInt(i + 1)
            val tmp = password[i]
            password[i] = password[j]
            password[j] = tmp
        }
        return String(password)
    }

    /** Optionally strips ambiguous 0/O/1/l/I from a class. */
    private fun filterSet(set: String, excludeLookalikes: Boolean): String =
        if (excludeLookalikes) set.filterNot { it in LOOKALIKES } else set
}