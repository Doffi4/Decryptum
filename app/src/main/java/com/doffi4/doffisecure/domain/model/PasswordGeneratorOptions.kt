package com.doffi4.doffisecure.domain.model

import androidx.annotation.StringRes
import com.doffi4.doffisecure.R

/**
 * Character-class options for the password generator.
 *
 * @param length Desired password length (the generator caps it at 8..64 in the
 *   UI, but the use case only enforces a positive value).
 * @param includeUpper Latin capital letters (A-Z).
 * @param includeLower Latin small letters (a-z).
 * @param includeDigits Digits (0-9).
 * @param includeSymbols Printable symbols (!@#$…).
 * @param excludeLookalikes Prevent ambiguities caused by 0/O/1/l/I.
 */
data class PasswordGeneratorOptions(
    val length: Int = 16,
    val includeUpper: Boolean = true,
    val includeLower: Boolean = true,
    val includeDigits: Boolean = true,
    val includeSymbols: Boolean = true,
    val excludeLookalikes: Boolean = false,
) {

    /** True when at least one character class is selected. */
    val hasCharset: Boolean
        get() = includeUpper || includeLower || includeDigits || includeSymbols
}

/**
 * One-tap presets for the generator: each one configures the length and the
 * character sets so the result lands in the matching strength bracket.
 */
enum class PasswordPreset(
    @StringRes val labelRes: Int,
    val options: PasswordGeneratorOptions,
    val label: String = ""
) {
    WEAK(
        R.string.strength_weak,
        PasswordGeneratorOptions(
            length = 8,
            includeUpper = false,
            includeLower = true,
            includeDigits = true,
            includeSymbols = false,
        ),
        "Weak"
    ),
    MEDIUM(
        R.string.strength_medium,
        PasswordGeneratorOptions(
            length = 12,
            includeUpper = true,
            includeLower = true,
            includeDigits = true,
            includeSymbols = false,
        ),
        "Medium"
    ),
    STRONG(
        R.string.strength_strong,
        PasswordGeneratorOptions(
            length = 16,
            includeUpper = true,
            includeLower = true,
            includeDigits = true,
            includeSymbols = true,
        ),
        "Strong"
    ),
    VERY_STRONG(
        R.string.strength_very_strong,
        PasswordGeneratorOptions(
            length = 24,
            includeUpper = true,
            includeLower = true,
            includeDigits = true,
            includeSymbols = true,
        ),
        "Very strong"
    ),
}

/** All presets in order (weak -> very strong). */
val PASSWORD_PRESETS: List<PasswordPreset> = PasswordPreset.entries