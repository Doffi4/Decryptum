package com.doffi4.doffisecure.security

import android.content.Context
import android.net.Uri
import android.util.Log
import com.doffi4.doffisecure.domain.model.Passkey
import com.doffi4.doffisecure.domain.model.Password
import com.doffi4.doffisecure.domain.model.VaultData
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStreamWriter
import java.net.URI
import java.util.Base64

/**
 * Handles importing and exporting passwords and passkeys as CSV or JSON through Android's
 * Storage Access Framework (SAF).
 *
 * Supports CSV formats from Google Chrome, Firefox, Bitwarden, LastPass,
 * KeePass, 1Password, Safari, Samsung Pass, as well as Russian and European
 * locales with comma, semicolon, or tab separators and UTF-8 BOM.
 */
object CsvManager {

    private const val TAG = "CsvManager"
    private const val CSV_HEADER = "service,username,password,url,createdAt,totp,passkey_rp_id,passkey_credential_id,passkey_private_key,passkey_public_key_cose,passkey_algorithm,passkey_sign_count"

    private fun logI(msg: String) {
        try { Log.i(TAG, msg) } catch (_: Throwable) {}
    }

    private fun logW(msg: String) {
        try { Log.w(TAG, msg) } catch (_: Throwable) {}
    }

    /**
     * Writes all [passwords] and optional [passkeys] to a CSV file at the given [uri].
     * If [crypto] is provided and unlocked, passkey private keys are decrypted to raw PKCS#8 before Base64 encoding.
     */
    fun export(
        context: Context,
        uri: Uri,
        passwords: List<Password>,
        passkeys: List<Passkey> = emptyList(),
        crypto: PasswordCrypto? = null
    ): Int {
        val outputStream = context.contentResolver.openOutputStream(uri)
            ?: throw IllegalArgumentException("Could not open destination file")
        val writer = OutputStreamWriter(outputStream, Charsets.UTF_8)
        val exportedCount = writer.use { out ->
            out.appendLine(CSV_HEADER)
            var count = 0

            // 1. Export passwords and their linked passkeys
            val handledPasskeyIds = mutableSetOf<Long>()
            for (p in passwords) {
                val linked = passkeys.filter {
                    it.linkedPasswordId == p.id ||
                        (it.linkedPasswordId == null && it.rpId.equals(p.service, true) && it.userName.equals(p.username, true))
                }
                if (linked.isNotEmpty()) {
                    for (pk in linked) {
                        handledPasskeyIds.add(pk.id)
                        val (privKeyB64, pubCoseB64, credIdB64) = encodePasskeyFields(pk, crypto)
                        out.appendLine(
                            listOf(
                                escapeCsv(p.service),
                                escapeCsv(p.username),
                                escapeCsv(p.password),
                                escapeCsv(p.url ?: ""),
                                p.createdAt.toString(),
                                escapeCsv(p.totpSecret ?: ""),
                                escapeCsv(pk.rpId),
                                credIdB64,
                                privKeyB64,
                                pubCoseB64,
                                pk.algorithm.toString(),
                                pk.signCount.toString()
                            ).joinToString(",")
                        )
                        count++
                    }
                } else {
                    out.appendLine(
                        listOf(
                            escapeCsv(p.service),
                            escapeCsv(p.username),
                            escapeCsv(p.password),
                            escapeCsv(p.url ?: ""),
                            p.createdAt.toString(),
                            escapeCsv(p.totpSecret ?: ""),
                            "", "", "", "", "", ""
                        ).joinToString(",")
                    )
                    count++
                }
            }

            // 2. Export standalone passkeys (not linked to any password)
            val standalone = passkeys.filter { it.id !in handledPasskeyIds }
            for (pk in standalone) {
                val (privKeyB64, pubCoseB64, credIdB64) = encodePasskeyFields(pk, crypto)
                out.appendLine(
                    listOf(
                        escapeCsv(pk.rpName.ifBlank { pk.rpId }),
                        escapeCsv(pk.userName),
                        "", // empty password
                        escapeCsv("https://${pk.rpId}"),
                        pk.createdAt.toString(),
                        "", // empty totp
                        escapeCsv(pk.rpId),
                        credIdB64,
                        privKeyB64,
                        pubCoseB64,
                        pk.algorithm.toString(),
                        pk.signCount.toString()
                    ).joinToString(",")
                )
                count++
            }
            count
        }
        return exportedCount
    }

    private fun encodePasskeyFields(
        pk: Passkey,
        crypto: PasswordCrypto?
    ): Triple<String, String, String> {
        val rawPrivKey = try {
            if (crypto != null && crypto.isUnlocked() && pk.encryptedPrivateKey.isNotEmpty()) {
                crypto.decryptBytes(pk.encryptedPrivateKey)
            } else {
                pk.encryptedPrivateKey
            }
        } catch (_: Exception) {
            pk.encryptedPrivateKey
        }
        val privKeyB64 = if (rawPrivKey.isNotEmpty()) Base64.getEncoder().encodeToString(rawPrivKey) else ""
        val pubCoseB64 = if (pk.publicKeyCose.isNotEmpty()) Base64.getEncoder().encodeToString(pk.publicKeyCose) else ""
        val credIdB64 = if (pk.credentialId.isNotEmpty()) Base64.getEncoder().encodeToString(pk.credentialId) else ""
        return Triple(privKeyB64, pubCoseB64, credIdB64)
    }

    /**
     * Reads a CSV or JSON file from [uri], maps columns to Passwords and Passkeys,
     * and returns [VaultData].
     */
    fun importVault(context: Context, uri: Uri, crypto: PasswordCrypto? = null): VaultData {
        logI("Importing vault data from URI: $uri")
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Could not open the selected file")
        return stream.use { parseStreamVault(it, crypto) }
    }

    /**
     * Reads a CSV or JSON file from [uri], maps common header names to our fields,
     * and returns the list of parsed [Password] objects.
     */
    fun import(context: Context, uri: Uri): List<Password> {
        return importVault(context, uri).passwords
    }

    /**
     * Parses passwords from an [InputStream]. Supports CSV and JSON formats.
     */
    fun parseStream(stream: InputStream): List<Password> {
        return parseStreamVault(stream).passwords
    }

    /**
     * Parses passwords and passkeys from an [InputStream]. Supports CSV and JSON formats.
     */
    fun parseStreamVault(stream: InputStream, crypto: PasswordCrypto? = null): VaultData {
        val rawBytes = stream.readBytes()
        if (rawBytes.isEmpty()) return VaultData()

        // Strip UTF-8 BOM if present (EF BB BF)
        val offset = if (rawBytes.size >= 3 &&
            rawBytes[0] == 0xEF.toByte() &&
            rawBytes[1] == 0xBB.toByte() &&
            rawBytes[2] == 0xBF.toByte()
        ) 3 else 0

        val text = String(rawBytes, offset, rawBytes.size - offset, Charsets.UTF_8).trim()
        if (text.isEmpty()) return VaultData()

        // Detect JSON format
        if (text.startsWith("{") || text.startsWith("[")) {
            try {
                return VaultData(passwords = parseJson(text))
            } catch (e: Exception) {
                logW("Failed to parse as JSON, falling back to CSV: ${e.message}")
            }
        }

        return parseCsvVault(text, crypto)
    }

    /**
     * Parses RFC 4180 CSV text with auto-detected delimiter and multi-line support.
     */
    fun parseCsv(text: String): List<Password> {
        return parseCsvVault(text).passwords
    }

    /**
     * Parses RFC 4180 CSV text with auto-detected delimiter, multi-line support,
     * and passkey column decoding.
     */
    fun parseCsvVault(text: String, crypto: PasswordCrypto? = null): VaultData {
        val delimiter = detectDelimiter(text)
        val records = parseCsvRecords(text, delimiter)
        if (records.isEmpty()) return VaultData()

        val rawHeaders = records[0].map { it.trim().removePrefix("\uFEFF").lowercase() }
        val colIndex = mutableMapOf<String, Int>()

        val serviceSynonyms = setOf(
            "service", "name", "title", "account", "system", "app", "company",
            "сервис", "название", "сайт", "приложение", "имя", "учетная запись", "профиль"
        )
        val usernameSynonyms = setOf(
            "username", "login_username", "login", "user", "email", "user_name",
            "user name", "id", "e-mail", "login name", "account name", "identifier",
            "пользователь", "логин", "почта", "пользователь / email", "логин / email"
        )
        val passwordSynonyms = setOf(
            "password", "login_password", "pass", "passphrase", "пароль", "pin"
        )
        val totpSynonyms = setOf(
            "totp", "otp", "2fa", "secret", "authenticator", "totp_secret", "totpsecret",
            "totp_key", "authenticator_key", "two_factor", "two-factor", "2fa_secret",
            "login_totp", "one-time password", "onetimepassword", "timeotp-secret-base32",
            "timeotp-secret", "totp_seed", "totp seed", "totp_uri", "otpauth", "otp_secret",
            "secret_key", "secret key", "two_factor_secret", "2fa_key",
            "тотп", "2фа"
        )
        val urlSynonyms = setOf(
            "url", "uri", "login_uri", "website", "web site", "link", "login_url",
            "address", "web_site", "ссылка", "веб-сайт", "адрес"
        )
        val createdAtSynonyms = setOf(
            "createdat", "created_at", "created", "date", "timestamp", "time",
            "дата", "создано", "время"
        )
        val passkeyRpIdSynonyms = setOf(
            "passkey_rp_id", "passkey_rpid", "passkey_rp", "rpid", "rp_id"
        )
        val passkeyCredIdSynonyms = setOf(
            "passkey_credential_id", "passkey_credentialid", "credential_id", "credentialid"
        )
        val passkeyPrivKeySynonyms = setOf(
            "passkey_private_key", "passkey_privatekey", "private_key", "privatekey"
        )
        val passkeyPubCoseSynonyms = setOf(
            "passkey_public_key_cose", "passkey_public_key", "public_key_cose", "public_key"
        )
        val passkeyAlgSynonyms = setOf(
            "passkey_algorithm", "passkey_alg", "algorithm"
        )
        val passkeySignCountSynonyms = setOf(
            "passkey_sign_count", "sign_count", "signcount"
        )

        // Map column indices with precedence
        rawHeaders.forEachIndexed { idx, name ->
            when {
                name in passwordSynonyms && !colIndex.containsKey("password") -> colIndex["password"] = idx
                name in usernameSynonyms && !colIndex.containsKey("username") -> colIndex["username"] = idx
                name in serviceSynonyms && !colIndex.containsKey("service") -> colIndex["service"] = idx
                name in urlSynonyms && !colIndex.containsKey("url") -> colIndex["url"] = idx
                name in createdAtSynonyms && !colIndex.containsKey("createdAt") -> colIndex["createdAt"] = idx
                name in totpSynonyms && !colIndex.containsKey("totp") -> colIndex["totp"] = idx
                name in passkeyRpIdSynonyms && !colIndex.containsKey("passkey_rp_id") -> colIndex["passkey_rp_id"] = idx
                name in passkeyCredIdSynonyms && !colIndex.containsKey("passkey_credential_id") -> colIndex["passkey_credential_id"] = idx
                name in passkeyPrivKeySynonyms && !colIndex.containsKey("passkey_private_key") -> colIndex["passkey_private_key"] = idx
                name in passkeyPubCoseSynonyms && !colIndex.containsKey("passkey_public_key_cose") -> colIndex["passkey_public_key_cose"] = idx
                name in passkeyAlgSynonyms && !colIndex.containsKey("passkey_algorithm") -> colIndex["passkey_algorithm"] = idx
                name in passkeySignCountSynonyms && !colIndex.containsKey("passkey_sign_count") -> colIndex["passkey_sign_count"] = idx
            }
        }

        // Secondary fallback for dual-meaning columns (e.g. "website" or "site")
        if (!colIndex.containsKey("service") && !colIndex.containsKey("url")) {
            rawHeaders.forEachIndexed { idx, name ->
                if (name in setOf("website", "site", "сайт")) {
                    colIndex["service"] = idx
                }
            }
        }

        val passwordIdx = colIndex["password"] ?: -1
        val passkeyCredIdIdx = colIndex["passkey_credential_id"] ?: -1
        val serviceIdx = colIndex["service"] ?: -1
        val urlIdx = colIndex["url"] ?: -1
        val usernameIdx = colIndex["username"] ?: -1
        val createdAtIdx = colIndex["createdAt"] ?: -1
        val totpIdx = colIndex["totp"] ?: -1
        val passkeyRpIdIdx = colIndex["passkey_rp_id"] ?: -1
        val passkeyPrivKeyIdx = colIndex["passkey_private_key"] ?: -1
        val passkeyPubCoseIdx = colIndex["passkey_public_key_cose"] ?: -1
        val passkeyAlgIdx = colIndex["passkey_algorithm"] ?: -1
        val passkeySignCountIdx = colIndex["passkey_sign_count"] ?: -1

        if (passwordIdx == -1 && passkeyCredIdIdx == -1) {
            throw IllegalArgumentException(
                "Не найдена колонка с паролем (password) или Passkey. Найденные заголовки: ${rawHeaders.joinToString(", ")}"
            )
        }

        if (serviceIdx == -1 && urlIdx == -1 && passkeyRpIdIdx == -1) {
            throw IllegalArgumentException(
                "Не найдена колонка с названием сервиса или URL. Найденные заголовки: ${rawHeaders.joinToString(", ")}"
            )
        }

        val passwords = mutableListOf<Password>()
        val passkeys = mutableListOf<Passkey>()

        for (i in 1 until records.size) {
            val fields = records[i]
            if (fields.all { it.isBlank() }) continue

            val password = if (passwordIdx != -1 && passwordIdx in fields.indices) fields[passwordIdx].trim() else ""
            val passkeyCredIdB64 = if (passkeyCredIdIdx != -1 && passkeyCredIdIdx in fields.indices) fields[passkeyCredIdIdx].trim() else ""

            if (password.isBlank() && passkeyCredIdB64.isBlank()) continue

            val rawService = if (serviceIdx != -1 && serviceIdx in fields.indices) fields[serviceIdx].trim() else ""
            val url = if (urlIdx != -1 && urlIdx in fields.indices) fields[urlIdx].trim() else ""
            val passkeyRpId = if (passkeyRpIdIdx != -1 && passkeyRpIdIdx in fields.indices) fields[passkeyRpIdIdx].trim() else ""

            val service = when {
                rawService.isNotBlank() -> rawService
                url.isNotBlank() -> extractServiceFromUrl(url)
                passkeyRpId.isNotBlank() -> passkeyRpId
                else -> "Unknown Service"
            }

            val username = if (usernameIdx != -1 && usernameIdx in fields.indices) fields[usernameIdx].trim() else ""
            val createdAt = if (createdAtIdx != -1 && createdAtIdx in fields.indices) {
                val raw = fields[createdAtIdx].trim()
                raw.replace("[^0-9]".toRegex(), "").toLongOrNull()
                    ?: if (raw.isNotBlank()) parseTimestamp(raw) else System.currentTimeMillis()
            } else System.currentTimeMillis()

            val totp = if (totpIdx != -1 && totpIdx in fields.indices) {
                fields[totpIdx].trim().ifBlank { null }
            } else null

            val passwordObj = Password(
                id = 0L,
                service = service,
                username = username.ifBlank { "unknown" },
                password = password,
                url = url.ifBlank { if (passkeyRpId.isNotBlank()) "https://$passkeyRpId" else null },
                createdAt = createdAt,
                totpSecret = totp
            )
            passwords.add(passwordObj)

            if (passkeyCredIdB64.isNotBlank()) {
                try {
                    val credId = Base64.getDecoder().decode(passkeyCredIdB64)
                    val passkeyPrivKeyB64 = if (passkeyPrivKeyIdx != -1 && passkeyPrivKeyIdx in fields.indices) fields[passkeyPrivKeyIdx].trim() else ""
                    val passkeyPubCoseB64 = if (passkeyPubCoseIdx != -1 && passkeyPubCoseIdx in fields.indices) fields[passkeyPubCoseIdx].trim() else ""
                    val passkeyAlg = if (passkeyAlgIdx != -1 && passkeyAlgIdx in fields.indices) fields[passkeyAlgIdx].trim().toIntOrNull() ?: -7 else -7
                    val passkeySignCount = if (passkeySignCountIdx != -1 && passkeySignCountIdx in fields.indices) fields[passkeySignCountIdx].trim().toLongOrNull() ?: 0L else 0L

                    val rawPrivKey = if (passkeyPrivKeyB64.isNotBlank()) {
                        Base64.getDecoder().decode(passkeyPrivKeyB64)
                    } else ByteArray(0)

                    val encryptedPrivKey = if (crypto != null && crypto.isUnlocked() && rawPrivKey.isNotEmpty()) {
                        crypto.encryptBytes(rawPrivKey)
                    } else {
                        rawPrivKey
                    }

                    val pubCose = if (passkeyPubCoseB64.isNotBlank()) {
                        Base64.getDecoder().decode(passkeyPubCoseB64)
                    } else ByteArray(0)

                    val rpId = passkeyRpId.ifBlank {
                        if (url.isNotBlank()) extractServiceFromUrl(url) else service
                    }

                    val passkey = Passkey(
                        id = 0L,
                        credentialId = credId,
                        rpId = rpId,
                        rpName = service,
                        userId = ByteArray(0),
                        userName = username.ifBlank { "unknown" },
                        userDisplayName = null,
                        encryptedPrivateKey = encryptedPrivKey,
                        publicKeyCose = pubCose,
                        algorithm = passkeyAlg,
                        signCount = passkeySignCount,
                        linkedPasswordId = null,
                        createdAt = createdAt
                    )
                    passkeys.add(passkey)
                } catch (e: Exception) {
                    logW("Failed to parse passkey record: ${e.message}")
                }
            }
        }

        logI("Parsed ${passwords.size} passwords and ${passkeys.size} passkeys from CSV")
        return VaultData(passwords = passwords, passkeys = passkeys)
    }

    /**
     * Parses JSON exports (e.g. Bitwarden JSON or generic list of password objects).
     */
    private fun parseJson(text: String): List<Password> {
        val passwords = mutableListOf<Password>()
        if (text.startsWith("{")) {
            val obj = JSONObject(text)
            if (obj.has("items")) {
                // Bitwarden export
                val items = obj.getJSONArray("items")
                for (i in 0 until items.length()) {
                    val item = items.getJSONObject(i)
                    val login = item.optJSONObject("login") ?: continue
                    val password = login.optString("password", "")
                    if (password.isBlank()) continue

                    val name = item.optString("name", "Unknown")
                    val username = login.optString("username", "unknown")
                    val uris = login.optJSONArray("uris")
                    val uri = if (uris != null && uris.length() > 0) {
                        uris.getJSONObject(0).optString("uri", "").ifBlank { null }
                    } else null
                    val totp = login.optString("totp", "").ifBlank { null }

                    val creationDate = item.optString("creationDate", "")
                    val createdAt = if (creationDate.isNotBlank()) parseTimestamp(creationDate) else System.currentTimeMillis()

                    passwords.add(
                        Password(
                            id = 0L,
                            service = name,
                            username = username.ifBlank { "unknown" },
                            password = password,
                            url = uri,
                            createdAt = createdAt,
                            totpSecret = totp
                        )
                    )
                }
            }
        } else if (text.startsWith("[")) {
            val array = JSONArray(text)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val password = item.optString("password", item.optString("login_password", ""))
                if (password.isBlank()) continue

                val service = item.optString("service", item.optString("name", item.optString("title", "Unknown")))
                val username = item.optString("username", item.optString("login", "unknown"))
                val url = item.optString("url", item.optString("uri", "")).ifBlank { null }
                val totp = item.optString(
                    "totp",
                    item.optString(
                        "totpSecret",
                        item.optString(
                            "totp_secret",
                            item.optString(
                                "login_totp",
                                item.optString(
                                    "otp",
                                    item.optString(
                                        "otpauth",
                                        item.optString("secret", "")
                                    )
                                )
                            )
                        )
                    )
                ).ifBlank { null }

                val createdAt = if (item.has("createdAt")) {
                    item.optLong("createdAt", System.currentTimeMillis())
                } else if (item.has("created_at")) {
                    item.optLong("created_at", System.currentTimeMillis())
                } else if (item.has("timestamp")) {
                    item.optLong("timestamp", System.currentTimeMillis())
                } else if (item.has("date")) {
                    val d = item.optString("date", "")
                    if (d.isNotBlank()) parseTimestamp(d) else System.currentTimeMillis()
                } else {
                    System.currentTimeMillis()
                }

                passwords.add(
                    Password(
                        id = 0L,
                        service = service,
                        username = username.ifBlank { "unknown" },
                        password = password,
                        url = url,
                        createdAt = createdAt,
                        totpSecret = totp
                    )
                )
            }
        }
        return passwords
    }

    /**
     * Extracts a human-friendly service name from a URL.
     * Example: "https://accounts.google.com/signin" -> "accounts.google.com"
     */
    private fun extractServiceFromUrl(url: String): String {
        return try {
            val normalized = if (url.contains("://")) url else "https://$url"
            val uri = URI(normalized)
            val host = uri.host ?: return url
            host.removePrefix("www.")
        } catch (_: Exception) {
            url
        }
    }

    /**
     * Auto-detects the delimiter by counting commas, semicolons, and tabs
     * outside quotes on the first non-empty line.
     */
    private fun detectDelimiter(text: String): Char {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return ','
        var commaCount = 0
        var semicolonCount = 0
        var tabCount = 0
        var inQuotes = false

        for (ch in firstLine) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                !inQuotes && ch == ',' -> commaCount++
                !inQuotes && ch == ';' -> semicolonCount++
                !inQuotes && ch == '\t' -> tabCount++
            }
        }

        return when {
            semicolonCount > commaCount && semicolonCount >= tabCount -> ';'
            tabCount > commaCount && tabCount > semicolonCount -> '\t'
            else -> ','
        }
    }

    /**
     * Full RFC 4180 tokenizer supporting newlines inside quoted fields and escaped quotes ("").
     */
    private fun parseCsvRecords(text: String, delimiter: Char): List<List<String>> {
        val records = mutableListOf<List<String>>()
        val currentRecord = mutableListOf<String>()
        val currentField = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < text.length) {
            val ch = text[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < text.length && text[i + 1] == '"') {
                        currentField.append('"')
                        i++ // Skip escaped quote
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == delimiter && !inQuotes -> {
                    currentRecord.add(currentField.toString())
                    currentField.clear()
                }
                (ch == '\n' || ch == '\r') && !inQuotes -> {
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') {
                        i++ // Skip \r\n
                    }
                    currentRecord.add(currentField.toString())
                    currentField.clear()
                    if (currentRecord.isNotEmpty()) {
                        records.add(currentRecord.toList())
                        currentRecord.clear()
                    }
                }
                else -> {
                    currentField.append(ch)
                }
            }
            i++
        }

        if (currentField.isNotEmpty() || currentRecord.isNotEmpty()) {
            currentRecord.add(currentField.toString())
            records.add(currentRecord.toList())
        }

        return records
    }

    private fun parseTimestamp(raw: String): Long {
        return try {
            java.time.Instant.parse(raw).toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.LocalDateTime.parse(raw)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            } catch (_: Exception) {
                try {
                    java.time.LocalDate.parse(raw)
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                } catch (_: Exception) {
                    System.currentTimeMillis()
                }
            }
        }
    }

    private fun escapeCsv(field: String): String {
        return if (field.contains(',') || field.contains('"') || field.contains('\n') || field.contains('\r') || field.contains(';')) {
            "\"${field.replace("\"", "\"\"")}\""
        } else field
    }
}