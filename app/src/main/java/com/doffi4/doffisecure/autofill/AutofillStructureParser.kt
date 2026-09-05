package com.doffi4.doffisecure.autofill

import android.app.assist.AssistStructure
import android.app.assist.AssistStructure.ViewNode
import android.service.autofill.FillContext
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId

/**
 * Generic result of parsing an AssistStructure during an Autofill fill or save request.
 * Decoupled by type parameter [ID] to allow fast, pure JVM unit testing without Android runtime classes.
 */
data class ParsedStructure<ID>(
    val packageName: String?,
    val webDomain: String?,
    val usernameId: ID? = null,
    val passwordId: ID? = null,
    val newPasswordId: ID? = null,
    val enteredUsername: String? = null,
    val enteredPassword: String? = null,
    val allAutofillIds: List<ID> = emptyList()
)

typealias ParsedAutofillStructure = ParsedStructure<AutofillId>

/**
 * Lightweight node descriptor representing an input field in AssistStructure or HTML DOM.
 */
data class AutofillNodeDescriptor<ID>(
    val id: ID,
    val isFocused: Boolean = false,
    val hints: List<String> = emptyList(),
    val inputType: Int = 0,
    val idEntry: String? = null,
    val hintText: String? = null,
    val className: String? = null,
    val htmlTag: String? = null,
    val htmlType: String? = null,
    val htmlName: String? = null,
    val htmlId: String? = null,
    val htmlAutocomplete: String? = null,
    val htmlPlaceholder: String? = null,
    val text: String? = null,
    val webDomain: String? = null,
    val childCount: Int = 0,
    val children: List<AutofillNodeDescriptor<ID>> = emptyList()
)

object AutofillStructureParser {

    data class InputCandidate<ID>(
        val autofillId: ID,
        val isPassword: Boolean,
        val isNewPassword: Boolean,
        val isUsername: Boolean,
        val isFocused: Boolean,
        val text: String?,
        val isTextInput: Boolean,
        val isExcluded: Boolean = false,
        val isMultiLine: Boolean = false
    )

    /**
     * Traverses all [contexts] starting from the latest, falling back to earlier contexts
     * for multi-step login flows (e.g. username on screen 1, password on screen 2).
     */
    fun parse(contexts: List<FillContext>): ParsedAutofillStructure {
        if (contexts.isEmpty()) return ParsedAutofillStructure(null, null)

        val latest = parse(contexts.last().structure)
        if (contexts.size == 1 || (latest.usernameId != null && latest.passwordId != null && latest.webDomain != null)) {
            return latest
        }

        var usernameId = latest.usernameId
        var passwordId = latest.passwordId
        var newPasswordId = latest.newPasswordId
        var webDomain = latest.webDomain
        var packageName = latest.packageName
        var enteredUsername = latest.enteredUsername
        var enteredPassword = latest.enteredPassword
        val allIds = latest.allAutofillIds.toMutableList()

        for (i in contexts.size - 2 downTo 0) {
            val prev = parse(contexts[i].structure)
            if (webDomain.isNullOrBlank()) webDomain = prev.webDomain
            if (packageName.isNullOrBlank()) packageName = prev.packageName
            if (usernameId == null) {
                usernameId = prev.usernameId
                if (enteredUsername.isNullOrBlank()) enteredUsername = prev.enteredUsername
            }
            if (passwordId == null) {
                passwordId = prev.passwordId
                if (enteredPassword.isNullOrBlank()) enteredPassword = prev.enteredPassword
            }
            if (newPasswordId == null) newPasswordId = prev.newPasswordId
            allIds.addAll(prev.allAutofillIds)
        }

        return ParsedAutofillStructure(
            packageName = packageName,
            webDomain = webDomain,
            usernameId = usernameId,
            passwordId = passwordId,
            newPasswordId = newPasswordId,
            enteredUsername = enteredUsername,
            enteredPassword = enteredPassword,
            allAutofillIds = allIds.distinct()
        )
    }

    /**
     * Recursively traverses [structure] to extract relevant fields and target identifiers.
     */
    fun parse(structure: AssistStructure): ParsedAutofillStructure {
        val packageName = structure.activityComponent?.packageName
        var webDomain: String? = null
        val allIds = mutableListOf<AutofillId>()
        val candidates = mutableListOf<InputCandidate<AutofillId>>()

        fun traverse(node: ViewNode) {
            val nodeDomain = node.webDomain
            if (!nodeDomain.isNullOrBlank() && webDomain == null) {
                webDomain = normalizeDomain(nodeDomain)
            }

            val autofillId = node.autofillId
            if (autofillId != null) {
                allIds.add(autofillId)

                var htmlTag = ""
                var htmlType = ""
                var htmlName = ""
                var htmlId = ""
                var htmlAutocomplete = ""
                var htmlPlaceholder = ""

                val htmlInfo = node.htmlInfo
                if (htmlInfo != null) {
                    htmlTag = htmlInfo.tag.lowercase()
                    val attributes = htmlInfo.attributes
                    if (attributes != null) {
                        for (pair in attributes) {
                            val key = pair.first?.lowercase().orEmpty()
                            val value = pair.second?.lowercase().orEmpty()
                            when (key) {
                                "type" -> htmlType = value
                                "name" -> htmlName = value
                                "id" -> htmlId = value
                                "autocomplete" -> htmlAutocomplete = value
                                "placeholder" -> htmlPlaceholder = value
                            }
                        }
                    }
                }

                val descriptor = AutofillNodeDescriptor(
                    id = autofillId,
                    isFocused = node.isFocused,
                    hints = node.autofillHints?.toList().orEmpty(),
                    inputType = node.inputType,
                    idEntry = node.idEntry,
                    hintText = node.hint?.toString(),
                    className = node.className,
                    htmlTag = htmlTag,
                    htmlType = htmlType,
                    htmlName = htmlName,
                    htmlId = htmlId,
                    htmlAutocomplete = htmlAutocomplete,
                    htmlPlaceholder = htmlPlaceholder,
                    text = node.text?.toString() ?: node.autofillValue?.textValue?.toString(),
                    childCount = node.childCount
                )

                val candidate = buildCandidate(descriptor)
                val isLeafNode = node.childCount == 0

                if (isLeafNode && (candidate.isTextInput || candidate.isFocused)) {
                    candidates.add(candidate)
                }
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChildAt(i))
            }
        }

        for (i in 0 until structure.windowNodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            traverse(windowNode.rootViewNode)
        }

        return evaluateCandidates(
            packageName = packageName,
            webDomain = webDomain,
            candidates = candidates,
            allIds = allIds
        )
    }

    /**
     * Traverses a list or tree of [nodes] for testing or platform-independent parsing.
     */
    fun <ID> parseNodes(
        packageName: String?,
        webDomain: String?,
        nodes: List<AutofillNodeDescriptor<ID>>
    ): ParsedStructure<ID> {
        var detectedDomain = webDomain
        val allIds = mutableListOf<ID>()
        val candidates = mutableListOf<InputCandidate<ID>>()

        fun traverseNode(node: AutofillNodeDescriptor<ID>) {
            if (!node.webDomain.isNullOrBlank() && detectedDomain == null) {
                detectedDomain = normalizeDomain(node.webDomain)
            }
            allIds.add(node.id)

            val candidate = buildCandidate(node)
            val isLeafNode = node.childCount == 0 && node.children.isEmpty()
            if (isLeafNode && (candidate.isTextInput || candidate.isFocused)) {
                candidates.add(candidate)
            }

            for (child in node.children) {
                traverseNode(child)
            }
        }

        for (node in nodes) {
            traverseNode(node)
        }

        return evaluateCandidates(
            packageName = packageName,
            webDomain = detectedDomain,
            candidates = candidates,
            allIds = allIds
        )
    }

    /**
     * Builds an [InputCandidate] from a node descriptor by evaluating attributes and hints.
     */
    fun <ID> buildCandidate(node: AutofillNodeDescriptor<ID>): InputCandidate<ID> {
        val hints = node.hints
        val inputType = node.inputType
        val idEntry = node.idEntry?.lowercase().orEmpty()
        val hintText = node.hintText?.lowercase().orEmpty()
        val className = node.className?.lowercase().orEmpty()
        val htmlTag = node.htmlTag?.lowercase().orEmpty()
        val htmlType = node.htmlType?.lowercase().orEmpty()
        val htmlName = node.htmlName?.lowercase().orEmpty()
        val htmlId = node.htmlId?.lowercase().orEmpty()
        val htmlAutocomplete = node.htmlAutocomplete?.lowercase().orEmpty()
        val htmlPlaceholder = node.htmlPlaceholder?.lowercase().orEmpty()
        val combinedHint = if (htmlPlaceholder.isNotEmpty()) "$hintText $htmlPlaceholder" else hintText

        val isMultiLine = (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0 || htmlTag == "textarea"

        val isExcluded = isExcludedNonAuthField(
            inputType = inputType,
            idEntry = idEntry,
            hintText = combinedHint,
            htmlType = htmlType,
            htmlName = htmlName,
            htmlId = htmlId,
            className = className,
            isMultiLine = isMultiLine
        )

        val isPassword = isPasswordField(
            hints = hints,
            inputType = inputType,
            idEntry = idEntry,
            hintText = combinedHint,
            htmlType = htmlType,
            htmlAutocomplete = htmlAutocomplete,
            htmlName = htmlName,
            htmlId = htmlId,
            isExcluded = isExcluded,
            isMultiLine = isMultiLine
        )

        val isNewPassword = isNewPasswordField(
            hints = hints,
            idEntry = idEntry,
            hintText = combinedHint,
            htmlAutocomplete = htmlAutocomplete,
            htmlName = htmlName,
            htmlId = htmlId,
            isExcluded = isExcluded,
            isMultiLine = isMultiLine
        )

        val isUsername = isUsernameField(
            hints = hints,
            inputType = inputType,
            idEntry = idEntry,
            hintText = combinedHint,
            htmlType = htmlType,
            htmlAutocomplete = htmlAutocomplete,
            htmlName = htmlName,
            htmlId = htmlId,
            isExcluded = isExcluded,
            isMultiLine = isMultiLine
        )

        val isHiddenOrNonText = htmlType in listOf("hidden", "submit", "button", "checkbox", "radio", "image", "reset", "file")
        val isLeafNode = node.childCount == 0 && node.children.isEmpty()

        val isTextInput = isLeafNode && !isHiddenOrNonText && (isPassword || isUsername || isNewPassword ||
                className.contains("edittext") ||
                (htmlTag == "input" && !isHiddenOrNonText) ||
                (inputType and InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_TEXT)

        return InputCandidate(
            autofillId = node.id,
            isPassword = isPassword,
            isNewPassword = isNewPassword,
            isUsername = isUsername,
            isFocused = node.isFocused,
            text = node.text,
            isTextInput = isTextInput,
            isExcluded = isExcluded,
            isMultiLine = isMultiLine
        )
    }

    /**
     * Determines whether a field belongs to non-auth inputs such as search bars, chat composers,
     * translators, notes, and comments.
     */
    fun isExcludedNonAuthField(
        inputType: Int,
        idEntry: String,
        hintText: String,
        htmlType: String,
        htmlName: String,
        htmlId: String,
        className: String,
        isMultiLine: Boolean
    ): Boolean {
        // 1. Explicit HTML or Android search input
        if (htmlType.equals("search", ignoreCase = true)) return true
        val isSearchVariation = (inputType and InputType.TYPE_MASK_VARIATION) == InputType.TYPE_TEXT_VARIATION_FILTER
        if (isSearchVariation) return true

        val allIdsAndNames = "$idEntry $htmlName $htmlId $className".lowercase()

        // 2. Search inputs
        if (allIdsAndNames.contains("search") || allIdsAndNames.contains("query") || allIdsAndNames.contains("find")) {
            return true
        }
        if (hintText.contains("пошук") || hintText.contains("поиск") || hintText.contains("search") ||
            hintText.contains("знайти") || hintText.contains("найти") ||
            hintText.contains("query") || hintText.contains("find")
        ) return true

        // 3. Messenger chats & message composers
        if (allIdsAndNames.contains("chat_text") || allIdsAndNames.contains("message_edit") ||
            allIdsAndNames.contains("msg_edit") || allIdsAndNames.contains("composer") ||
            allIdsAndNames.contains("caption")
        ) return true
        if (hintText.contains("повідомлення") || hintText.contains("сообщение") || hintText.contains("message") ||
            hintText.contains("написати") || hintText.contains("напишите") || hintText.contains("відповісти")
        ) return true

        // 4. Translators (Google Translate, etc.)
        if (allIdsAndNames.contains("source_text") || allIdsAndNames.contains("target_text") ||
            allIdsAndNames.contains("translate")
        ) return true
        if (hintText.contains("введіть текст") || hintText.contains("введите текст") ||
            hintText.contains("type to translate") || hintText.contains("enter text")
        ) return true

        // 5. Notes, descriptions, and comments (multi-line non-auth)
        if (isMultiLine && (allIdsAndNames.contains("note") || allIdsAndNames.contains("memo") ||
                    allIdsAndNames.contains("content") || allIdsAndNames.contains("comment") ||
                    allIdsAndNames.contains("desc") || allIdsAndNames.contains("body"))
        ) return true

        return false
    }

    /**
     * Evaluates collected candidates using strict authentication form heuristics.
     */
    fun <ID> evaluateCandidates(
        packageName: String?,
        webDomain: String?,
        candidates: List<InputCandidate<ID>>,
        allIds: List<ID>
    ): ParsedStructure<ID> {
        var newPasswordId: ID? = null
        var passwordId: ID? = null
        var usernameId: ID? = null
        var enteredUsername: String? = null
        var enteredPassword: String? = null

        // 1. Identify new password field (signup forms)
        val newPasswordCandidate = candidates.firstOrNull { it.isNewPassword && !it.isExcluded && !it.isMultiLine && it.isFocused }
            ?: candidates.firstOrNull { it.isNewPassword && !it.isExcluded && !it.isMultiLine }
        if (newPasswordCandidate != null) {
            newPasswordId = newPasswordCandidate.autofillId
            if (!newPasswordCandidate.text.isNullOrBlank()) enteredPassword = newPasswordCandidate.text
        }

        // 2. Identify password field (login forms)
        val passwordCandidate = candidates.firstOrNull { it.isPassword && !it.isExcluded && !it.isMultiLine && it.autofillId != newPasswordId && it.isFocused }
            ?: candidates.firstOrNull { it.isPassword && !it.isExcluded && !it.isMultiLine && it.autofillId != newPasswordId }
        if (passwordCandidate != null) {
            passwordId = passwordCandidate.autofillId
            if (!passwordCandidate.text.isNullOrBlank()) enteredPassword = passwordCandidate.text
        }

        // 3. Identify username / login field (Criterion A)
        val usernameCandidate = candidates.firstOrNull { it.isUsername && !it.isExcluded && !it.isMultiLine && it.autofillId != passwordId && it.autofillId != newPasswordId && it.isFocused }
            ?: candidates.firstOrNull { it.isUsername && !it.isExcluded && !it.isMultiLine && it.autofillId != passwordId && it.autofillId != newPasswordId }
        if (usernameCandidate != null) {
            usernameId = usernameCandidate.autofillId
            if (!usernameCandidate.text.isNullOrBlank()) enteredUsername = usernameCandidate.text
        }

        // 4. Fallback pairing by DOM candidate order (Criterion B):
        // When a password field exists but no explicit username field was identified,
        // take the valid text candidate immediately preceding the password in the DOM as usernameId.
        val targetPassCandidate = passwordCandidate ?: newPasswordCandidate
        if (targetPassCandidate != null && usernameId == null) {
            val passIndex = candidates.indexOf(targetPassCandidate)
            for (i in passIndex - 1 downTo 0) {
                val prevCandidate = candidates[i]
                if (!prevCandidate.isPassword &&
                    !prevCandidate.isNewPassword &&
                    prevCandidate.isTextInput &&
                    !prevCandidate.isExcluded &&
                    !prevCandidate.isMultiLine &&
                    prevCandidate.autofillId != passwordId &&
                    prevCandidate.autofillId != newPasswordId
                ) {
                    usernameId = prevCandidate.autofillId
                    if (!prevCandidate.text.isNullOrBlank()) enteredUsername = prevCandidate.text
                    break
                }
            }
        }

        // NOTE: Step 5 (arbitrary focused field fallback) is intentionally eliminated to prevent
        // non-auth fields (chats, search bars, translators) from ever being designated as usernameId.

        return ParsedStructure(
            packageName = packageName,
            webDomain = webDomain,
            usernameId = usernameId,
            passwordId = passwordId,
            newPasswordId = newPasswordId,
            enteredUsername = enteredUsername,
            enteredPassword = enteredPassword,
            allAutofillIds = allIds
        )
    }

    fun isPasswordField(
        hints: List<String>,
        inputType: Int,
        idEntry: String,
        hintText: String,
        htmlType: String,
        htmlAutocomplete: String,
        htmlName: String,
        htmlId: String,
        isExcluded: Boolean = false,
        isMultiLine: Boolean = false
    ): Boolean {
        if (isExcluded || isMultiLine) return false

        // 1. HTML Type attribute (Chrome / WebViews)
        if (htmlType.equals("password", ignoreCase = true)) return true

        // 2. HTML Autocomplete attribute
        if (htmlAutocomplete.contains("password", ignoreCase = true) ||
            htmlAutocomplete.contains("current-password", ignoreCase = true)
        ) return true

        // 3. Android Autofill hints
        if (hints.any { it.equals(View.AUTOFILL_HINT_PASSWORD, ignoreCase = true) }) return true

        // 4. Android InputType password variations
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val isPasswordVariation = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        if (isPasswordVariation) return true

        // Reject non-password domains that contain "pass" or similar substrings
        val nonPasswordTerms = listOf(
            "compass", "boarding", "transit", "bypass",
            "passenger", "passport", "ticket", "boarding_pass", "transit_pass"
        )
        val nameOrId = "$htmlName $htmlId".lowercase()
        if (nonPasswordTerms.any { idEntry.contains(it) || nameOrId.contains(it) }) {
            return false
        }

        // 5. HTML name / id attributes
        if (nameOrId.contains("password") || nameOrId.contains("passwd") || nameOrId.contains("pwd") || nameOrId.contains("passcode")) return true

        // 6. Native Android resource ID (bounded token matches)
        if (idEntry.contains("password") || idEntry.contains("passwd") || idEntry.contains("pwd") || idEntry.contains("passcode")) return true
        val idTokens = idEntry.split('_', '-', '.', ':', ' ')
        val validPassTokens = setOf("pass", "password", "passwd", "pwd", "passcode")
        if (idTokens.any { it in validPassTokens }) return true

        // 7. Hint text
        if (hintText.contains("password") || hintText.contains("пароль") || hintText.contains("pwd")) return true

        return false
    }

    fun isNewPasswordField(
        hints: List<String>,
        idEntry: String,
        hintText: String,
        htmlAutocomplete: String,
        htmlName: String,
        htmlId: String,
        isExcluded: Boolean = false,
        isMultiLine: Boolean = false
    ): Boolean {
        if (isExcluded || isMultiLine) return false

        val nonPasswordTerms = listOf(
            "compass", "boarding", "transit", "bypass",
            "passenger", "passport", "ticket", "boarding_pass", "transit_pass"
        )
        val nameOrId = "$htmlName $htmlId $idEntry".lowercase()
        if (nonPasswordTerms.any { nameOrId.contains(it) }) return false

        if (htmlAutocomplete.contains("new-password", ignoreCase = true) ||
            htmlAutocomplete.contains("newpassword", ignoreCase = true)
        ) return true

        if (hints.any {
                it.contains("newPassword", ignoreCase = true) ||
                        it.contains("new_password", ignoreCase = true) ||
                        it.contains("new-password", ignoreCase = true)
            }) return true

        if (nameOrId.contains("new_password") || nameOrId.contains("newpassword") || nameOrId.contains("signup_password")) return true
        if (hintText.contains("new password") || hintText.contains("новый пароль") || hintText.contains("новий пароль")) return true

        return false
    }

    fun isUsernameField(
        hints: List<String>,
        inputType: Int,
        idEntry: String,
        hintText: String,
        htmlType: String,
        htmlAutocomplete: String,
        htmlName: String,
        htmlId: String,
        isExcluded: Boolean = false,
        isMultiLine: Boolean = false
    ): Boolean {
        if (isExcluded || isMultiLine) return false

        // 1. HTML Autocomplete attribute (standards compliant)
        if (htmlAutocomplete.contains("username", ignoreCase = true) ||
            htmlAutocomplete.contains("email", ignoreCase = true)
        ) return true

        // 2. HTML Type attribute (explicit email)
        if (htmlType.equals("email", ignoreCase = true)) return true

        // 3. Android Autofill hints
        if (hints.any {
                it.equals(View.AUTOFILL_HINT_USERNAME, ignoreCase = true) ||
                        it.equals(View.AUTOFILL_HINT_EMAIL_ADDRESS, ignoreCase = true)
            }) return true

        // 4. Android InputType email variations
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        ) return true

        // 5. HTML name / id attributes (specific keywords, avoiding overbroad substrings)
        val nameOrId = "$htmlName $htmlId".lowercase()
        if (nameOrId.contains("username") || nameOrId.contains("user_name") ||
            nameOrId.contains("login") || nameOrId.contains("email") ||
            nameOrId.contains("e-mail") || nameOrId.contains("user_email") ||
            nameOrId.contains("login_id") || nameOrId.contains("signin") ||
            nameOrId.contains("user_login")
        ) return true

        // 6. Native Android resource ID (specific keywords)
        if (idEntry.contains("username") || idEntry.contains("user_name") ||
            idEntry.contains("login") || idEntry.contains("email") ||
            idEntry.contains("user_login") || idEntry.contains("account_name") ||
            idEntry.contains("signin")
        ) return true

        // 7. Hint text (unambiguous login phrases, avoiding generic 'ім'я', 'телефон')
        if (hintText.contains("username") || hintText.contains("login") ||
            hintText.contains("логин") || hintText.contains("логін") ||
            hintText.contains("email") || hintText.contains("e-mail") ||
            hintText.contains("почта") || hintText.contains("пошта") ||
            hintText.contains("електронна пошта") || hintText.contains("электронная почта") ||
            hintText.contains("ім'я користувача") || hintText.contains("имя пользователя")
        ) return true

        return false
    }

    fun normalizeDomain(rawDomain: String): String {
        var clean = rawDomain.trim().lowercase()
        clean = clean.removePrefix("https://").removePrefix("http://")
        val slashIndex = clean.indexOf('/')
        if (slashIndex != -1) {
            clean = clean.substring(0, slashIndex)
        }
        val colonIndex = clean.indexOf(':')
        if (colonIndex != -1) {
            clean = clean.substring(0, colonIndex)
        }
        clean = clean.removePrefix("www.")
        return clean
    }
}
