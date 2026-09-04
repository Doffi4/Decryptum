package com.doffi4.doffisecure.security

import android.content.Context
import android.net.Uri
import com.doffi4.doffisecure.domain.model.Password
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Handles importing and exporting passwords as CSV through Android's
 * Storage Access Framework (SAF).
 */
object CsvManager {

    private const val CSV_HEADER = "service,username,password,url,createdAt"
    private const val MIME_TYPE_CSV = "text/csv"

    /**
     * Writes all [passwords] to a CSV file at the given [uri].
     * The caller should first obtain the [uri] via SAF's
     * [androidx.activity.result.contract.ActivityResultContracts.CreateDocument].
     */
    fun export(context: Context, uri: Uri, passwords: List<Password>): Int {
        val writer = OutputStreamWriter(context.contentResolver.openOutputStream(uri)!!)
        writer.use { out ->
            out.appendLine(CSV_HEADER)
            passwords.forEach { p ->
                out.appendLine(
                    listOf(
                        escapeCsv(p.service),
                        escapeCsv(p.username),
                        escapeCsv(p.password),
                        escapeCsv(p.url ?: ""),
                        p.createdAt.toString()
                    ).joinToString(",")
                )
            }
        }
        return passwords.size
    }

    /**
     * Reads a CSV file from [uri], maps common header names to our fields,
     * and returns the list of parsed [Password] objects. Supports formats
     * from Chrome, Bitwarden, LastPass, KeePass, and other password managers.
     * Skips malformed lines.
     */
    fun import(context: Context, uri: Uri): List<Password> {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Could not open the selected file")
        val reader = BufferedReader(InputStreamReader(stream))
        val passwords = mutableListOf<Password>()

        reader.useLines { lines ->
            val iterator = lines.iterator()
            if (!iterator.hasNext()) return@useLines

            // Parse header and build column index map
            val rawHeaders = parseCsvLine(iterator.next()).map { it.trim().lowercase() }
            val colIndex = mutableMapOf<String, Int>()
            rawHeaders.forEachIndexed { idx, name ->
                when {
                    name in setOf("service", "name", "website", "site", "title", "login name") -> colIndex["service"] = idx
                    name in setOf("username", "login_username", "login", "user", "email", "login name") -> colIndex["username"] = idx
                    name in setOf("password", "login_password", "pass", "passphrase") -> colIndex["password"] = idx
                    name in setOf("url", "uri", "login_uri", "website", "link", "login_url") -> colIndex["url"] = idx
                    name in setOf("createdat", "created_at", "created", "date", "timestamp", "time") -> colIndex["createdAt"] = idx
                }
            }

            // Require at least service/name and password
            val serviceIdx = colIndex["service"] ?: throw IllegalArgumentException(
                "Could not find 'service' or 'name' column. Found: ${rawHeaders.joinToString(", ")}"
            )
            val passwordIdx = colIndex["password"] ?: throw IllegalArgumentException(
                "Could not find 'password' column. Found: ${rawHeaders.joinToString(", ")}"
            )
            val usernameIdx = colIndex["username"] ?: -1
            val urlIdx = colIndex["url"] ?: -1
            val createdAtIdx = colIndex["createdAt"] ?: -1

            while (iterator.hasNext()) {
                val line = iterator.next().trim()
                if (line.isEmpty()) continue

                val fields = parseCsvLine(line)
                if (fields.size <= maxOf(serviceIdx, passwordIdx)) continue

                val service = fields[serviceIdx].trim()
                val password = fields[passwordIdx].trim()
                if (service.isBlank() || password.isBlank()) continue

                val username = if (usernameIdx in fields.indices) fields[usernameIdx].trim() else ""
                val url = if (urlIdx in fields.indices) fields[urlIdx].trim() else ""
                val createdAt = if (createdAtIdx in fields.indices) {
                    // Try to parse as long timestamp, else use current time
                    val raw = fields[createdAtIdx].trim()
                    raw.replace("[^0-9]".toRegex(), "").toLongOrNull()
                        ?: if (raw.isNotBlank()) parseTimestamp(raw) else System.currentTimeMillis()
                } else System.currentTimeMillis()

                passwords.add(
                    Password(
                        id = 0L,
                        service = service,
                        username = username.ifBlank { "unknown" },
                        password = password,
                        url = url.ifBlank { null },
                        createdAt = createdAt
                    )
                )
            }
        }
        return passwords
    }

    /** Best-effort parse of human-readable timestamps. Returns epoch millis. */
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
        return if (field.contains(',') || field.contains('"') || field.contains('\n')) {
            "\"${field.replace("\"", "\"\"")}\""
        } else field
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && !inQuotes -> inQuotes = true
                ch == '"' && inQuotes -> {
                    // Escaped quote ("" inside a quoted field) -> literal "
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++ // skip the second quote
                    } else {
                        inQuotes = false
                    }
                }
                ch == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        result.add(current.toString())
        return result
    }
}