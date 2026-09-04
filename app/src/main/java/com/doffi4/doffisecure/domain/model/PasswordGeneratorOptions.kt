package com.doffi4.doffisecure.domain.model

/**
 * Character-class options for the password generator.
 *
 * @param length Desired password length (the generator caps it at 8..64 in the
 *   UI, but the use case only enforces a positive value).
 * @param includeUpper Latin capital letters (A-Z).
 * @param includeLower Latin small letters (a-z).
 * @param includeDigits Digits (0-9).
 * @param includeSymbols Printable symbols (!@#$\u2026).
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
enum class PasswordPreset(val label: String, val options: PasswordGeneratorOptions) {
    WEAK(
        "Слабый",
        PasswordGeneratorOptions(
            length = 8,
            includeUpper = false,
            includeLower = true,
            includeDigits = true,
            includeSymbols = false,
        )
    ),
    MEDIUM(
        "Средний",
        PasswordGeneratorOptions(
            length = 12,
            includeUpper = true,
            includeLower = true,
            includeDigits = true,
            includeSymbols = false,
        )
    ),
    STRONG(
        "Сильный",
        PasswordGeneratorOptions(
            length = 16,
            includeUpper = true,
            includeLower = true,
            includeDigits = true,
            includeSymbols = true,
        )
    ),
    VERY_STRONG(
        "Очень сильный",
        PasswordGeneratorOptions(
            length = 24,
            includeUpper = true,
            includeLower = true,
            includeDigits = true,
            includeSymbols = true,
        )
    ),
}

/** All presets in order (weak -> very strong). */
val PASSWORD_PRESETS: List<PasswordPreset> = PasswordPreset.entries