package com.doffi4.doffisecure.domain.security

import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.model.PasswordStrength
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Stateless, offline analysis. Caller owns the unlocked input and its lifetime. */
class LocalSecurityAnalyzer {
    fun analyze(passwords: List<Password>, checkActive: () -> Unit = {}): SecuritySummary {
        // Per-run keyed fingerprints prevent keeping additional plaintext map keys or
        // reusable unsalted password hashes. Neither key nor fingerprints leave this call.
        val key = ByteArray(32).apply { SecureRandom().nextBytes(this) }
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }
        val reuse = linkedMapOf<String, MutableList<Pair<String, SecurityItemReference>>>()
        val duplicates = linkedMapOf<String, MutableList<SecurityItemReference>>()
        val weak = mutableListOf<SecurityItemReference>()
        val seen = hashSetOf<Long>()
        var passwordCount = 0
        fun fingerprint(vararg values: String?): String {
            mac.reset()
            for (value in values) {
                val bytes = value?.toByteArray(Charsets.UTF_8)
                mac.update(ByteBuffer.allocate(4).putInt(bytes?.size ?: -1).array())
                if (bytes != null) { try { mac.update(bytes) } finally { bytes.fill(0) } }
            }
            return Base64.getEncoder().encodeToString(mac.doFinal())
        }
        try {
            for (item in passwords) {
                checkActive()
                if (!seen.add(item.id) || item.password.isEmpty()) continue
                passwordCount++
                val reference = SecurityItemReference(item.id, item.service, item.username)
                if (isWeak(item.password)) weak.add(reference)
                // Account identity is conservative: exact username/URL, normalized service.
                // We do not claim these labels prove a website/app identity.
                val account = fingerprint(item.service.trim().lowercase(Locale.ROOT), item.username, item.url)
                reuse.getOrPut(fingerprint(item.password)) { mutableListOf() }.add(account to reference)
                duplicates.getOrPut(fingerprint(item.service, item.username, item.url, item.password, item.totpSecret)) {
                    mutableListOf()
                }.add(reference)
            }
            val findings = mutableListOf<SecurityFinding>()
            if (weak.isNotEmpty()) findings.add(SecurityFinding(SecurityFindingType.WEAK_PASSWORD, SecurityPriority.HIGH,
                listOf(SecurityFindingGroup(weak.toList()))))
            val reusedGroups = reuse.values.filter { group -> group.asSequence().map { it.first }.distinct().take(2).count() > 1 }
                .map { SecurityFindingGroup(it.map { pair -> pair.second }) }
            if (reusedGroups.isNotEmpty()) findings.add(SecurityFinding(SecurityFindingType.REUSED_PASSWORD, SecurityPriority.HIGH, reusedGroups))
            val duplicateGroups = duplicates.values.filter { it.size > 1 }.map { SecurityFindingGroup(it.toList()) }
            if (duplicateGroups.isNotEmpty()) findings.add(SecurityFinding(SecurityFindingType.DUPLICATE_CREDENTIAL, SecurityPriority.REVIEW, duplicateGroups))
            checkActive()
            return SecuritySummary(seen.size, passwordCount, findings.toList())
        } finally {
            key.fill(0)
            reuse.clear()
            duplicates.clear()
        }
    }

    private fun isWeak(password: String): Boolean {
        if (password.length < 12 || PasswordStrength.fromPassword(password) == PasswordStrength.WEAK) return true
        if (password.lowercase(Locale.ROOT) in commonPasswords) return true
        // Catch repeated short motifs (including long same-character strings).
        return (1..4).any { period -> password.indices.all { password[it] == password[it % period] } }
    }

    private companion object {
        val commonPasswords = setOf("password", "password123", "password123!", "qwerty123456", "123456789012")
    }
}
