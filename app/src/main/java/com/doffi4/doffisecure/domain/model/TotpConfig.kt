package com.doffi4.doffisecure.domain.model

enum class TotpAlgorithm(val hmacAlgorithm: String) {
    SHA1("HmacSHA1"),
    SHA256("HmacSHA256"),
    SHA512("HmacSHA512");

    companion object {
        fun fromString(value: String?): TotpAlgorithm {
            return when (value?.trim()?.uppercase()) {
                "SHA256", "HMAC-SHA256", "HMACSHA256" -> SHA256
                "SHA512", "HMAC-SHA512", "HMACSHA512" -> SHA512
                else -> SHA1
            }
        }
    }
}

/**
 * Parsed configuration for TOTP (Time-Based One-Time Password) generation.
 */
data class TotpConfig(
    val secret: String,
    val digits: Int = 6,
    val periodSeconds: Int = 30,
    val algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
    val issuer: String? = null,
    val accountName: String? = null
) {
    /**
     * Serializes this configuration back to a standard otpauth://totp URI.
     */
    fun toOtpAuthUri(): String {
        val label = when {
            !issuer.isNullOrBlank() && !accountName.isNullOrBlank() ->
                "${java.net.URLEncoder.encode(issuer, "UTF-8")}:${java.net.URLEncoder.encode(accountName, "UTF-8")}"
            !accountName.isNullOrBlank() ->
                java.net.URLEncoder.encode(accountName, "UTF-8")
            !issuer.isNullOrBlank() ->
                java.net.URLEncoder.encode(issuer, "UTF-8")
            else -> "Account"
        }
        val sb = StringBuilder("otpauth://totp/$label?secret=$secret")
        if (digits != 6) sb.append("&digits=$digits")
        if (periodSeconds != 30) sb.append("&period=$periodSeconds")
        if (algorithm != TotpAlgorithm.SHA1) sb.append("&algorithm=${algorithm.name}")
        if (!issuer.isNullOrBlank()) sb.append("&issuer=${java.net.URLEncoder.encode(issuer, "UTF-8")}")
        return sb.toString()
    }
}

