package com.doffi4.doffisecure

import android.text.InputType
import android.view.View
import com.doffi4.doffisecure.autofill.AutofillDecision
import com.doffi4.doffisecure.autofill.AutofillNodeDescriptor
import com.doffi4.doffisecure.autofill.AutofillResponsePlanner
import com.doffi4.doffisecure.autofill.AutofillStructureParser
import com.doffi4.doffisecure.domain.model.Password
import org.junit.Assert.*
import org.junit.Test

class AutofillStructureParserTest {

    // =========================================================================
    // Negative cases: Messenger chats, search bars, notes, translators
    // Requirement R1: usernameId == null && passwordId == null
    // =========================================================================

    @Test
    fun testTelegramMessageInput_returnsNullForBoth() {
        val tgInput = AutofillNodeDescriptor(
            id = "chat_text_edit",
            idEntry = "chat_text_edit",
            className = "org.telegram.ui.Components.EditTextCaption",
            hintText = "Message",
            isFocused = true,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        )

        val result = AutofillStructureParser.parseNodes(
            packageName = "org.telegram.messenger",
            webDomain = null,
            nodes = listOf(tgInput)
        )

        assertNull("Telegram chat input must not be usernameId", result.usernameId)
        assertNull("Telegram chat input must not be passwordId", result.passwordId)
        assertNull("Telegram chat input must not be newPasswordId", result.newPasswordId)
    }

    @Test
    fun testViberMessageInput_returnsNullForBoth() {
        val viberInput = AutofillNodeDescriptor(
            id = "viber_msg_input",
            idEntry = "message_edit_text",
            className = "android.widget.EditText",
            hintText = "Type a message",
            isFocused = true,
            inputType = InputType.TYPE_CLASS_TEXT
        )

        val result = AutofillStructureParser.parseNodes(
            packageName = "com.viber.voip",
            webDomain = null,
            nodes = listOf(viberInput)
        )

        assertNull("Viber message box must not be usernameId", result.usernameId)
        assertNull("Viber message box must not be passwordId", result.passwordId)
    }

    @Test
    fun testGoogleTranslateSourceText_returnsNullForBoth() {
        val translateInput = AutofillNodeDescriptor(
            id = "translate_source",
            idEntry = "source_text",
            className = "android.widget.EditText",
            hintText = "Введіть текст",
            isFocused = true,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        )

        val result = AutofillStructureParser.parseNodes(
            packageName = "com.google.android.apps.translate",
            webDomain = null,
            nodes = listOf(translateInput)
        )

        assertNull("Google Translate source text must not be usernameId", result.usernameId)
        assertNull("Google Translate source text must not be passwordId", result.passwordId)
    }

    @Test
    fun testSearchBarHtml_returnsNullForBoth() {
        val searchInput = AutofillNodeDescriptor(
            id = "search_box",
            htmlTag = "input",
            htmlType = "search",
            htmlPlaceholder = "Search items, articles, news...",
            isFocused = true
        )

        val result = AutofillStructureParser.parseNodes(
            packageName = null,
            webDomain = "example.com",
            nodes = listOf(searchInput)
        )

        assertNull("HTML search bar must not be usernameId", result.usernameId)
        assertNull("HTML search bar must not be passwordId", result.passwordId)
    }

    @Test
    fun testSearchBarUkrainianHint_returnsNullForBoth() {
        val searchInput = AutofillNodeDescriptor(
            id = "app_search_src",
            idEntry = "search_src_text",
            className = "android.widget.EditText",
            hintText = "Пошук товарів або категорій",
            isFocused = true,
            inputType = InputType.TYPE_CLASS_TEXT
        )

        val result = AutofillStructureParser.parseNodes(
            packageName = "com.store.app",
            webDomain = null,
            nodes = listOf(searchInput)
        )

        assertNull("Search bar with Ukrainian hint must not be usernameId", result.usernameId)
        assertNull("Search bar with Ukrainian hint must not be passwordId", result.passwordId)
    }

    @Test
    fun testNotesOrMemoMultiLine_returnsNullForBoth() {
        val noteInput = AutofillNodeDescriptor(
            id = "note_body",
            idEntry = "note_content_text",
            className = "android.widget.EditText",
            hintText = "Note details",
            isFocused = true,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        )

        val result = AutofillStructureParser.parseNodes(
            packageName = "com.google.android.keep",
            webDomain = null,
            nodes = listOf(noteInput)
        )

        assertNull("Multi-line note field must not be usernameId", result.usernameId)
        assertNull("Multi-line note field must not be passwordId", result.passwordId)
    }

    @Test
    fun testOverbroadSubstringRejection_passengerAndContactName() {
        val passengerNode = AutofillNodeDescriptor(
            id = "passenger_field",
            idEntry = "passenger_name",
            hintText = "Passenger Full Name",
            className = "android.widget.EditText",
            inputType = InputType.TYPE_CLASS_TEXT
        )
        val contactNameNode = AutofillNodeDescriptor(
            id = "name_field",
            idEntry = "first_name",
            hintText = "Ваше ім'я",
            className = "android.widget.EditText",
            inputType = InputType.TYPE_CLASS_TEXT
        )
        val phoneNode = AutofillNodeDescriptor(
            id = "phone_field",
            idEntry = "contact_phone",
            hintText = "Номер телефону",
            className = "android.widget.EditText",
            inputType = InputType.TYPE_CLASS_TEXT
        )

        val result = AutofillStructureParser.parseNodes(
            packageName = "com.travel.tickets",
            webDomain = null,
            nodes = listOf(passengerNode, contactNameNode, phoneNode)
        )

        assertNull("Field with 'pass' in 'passenger' must NOT be passwordId", result.passwordId)
        assertNull("Generic name or phone fields must NOT be usernameId", result.usernameId)
    }

    // =========================================================================
    // Positive cases: Login forms, DOM pairing, Step 1, Password-only
    // =========================================================================

    @Test
    fun testLoginForm_Crunchyroll_explicitAttributes() {
        val userInput = AutofillNodeDescriptor(
            id = "cr_username_field",
            htmlTag = "input",
            htmlType = "text",
            htmlAutocomplete = "username",
            htmlName = "username",
            webDomain = "sso.crunchyroll.com"
        )
        val passInput = AutofillNodeDescriptor(
            id = "cr_password_field",
            htmlTag = "input",
            htmlType = "password",
            htmlAutocomplete = "current-password",
            htmlName = "password",
            isFocused = true
        )

        val result = AutofillStructureParser.parseNodes(
            packageName = "com.android.chrome",
            webDomain = "sso.crunchyroll.com",
            nodes = listOf(userInput, passInput)
        )

        assertEquals("cr_username_field", result.usernameId)
        assertEquals("cr_password_field", result.passwordId)
        assertEquals("sso.crunchyroll.com", result.webDomain)
    }

    @Test
    fun testLoginForm_JutSu_russianHintsAndLoginName() {
        val loginInput = AutofillNodeDescriptor(
            id = "jut_login_field",
            htmlTag = "input",
            htmlType = "text",
            htmlName = "login",
            htmlPlaceholder = "Логин или email"
        )
        val passInput = AutofillNodeDescriptor(
            id = "jut_pass_field",
            htmlTag = "input",
            htmlType = "password",
            htmlName = "password",
            htmlPlaceholder = "Пароль"
        )

        val result = AutofillStructureParser.parseNodes(
            packageName = null,
            webDomain = "jut.su",
            nodes = listOf(loginInput, passInput)
        )

        assertEquals("jut_login_field", result.usernameId)
        assertEquals("jut_pass_field", result.passwordId)
        assertEquals("jut.su", result.webDomain)
    }

    @Test
    fun testPrecedingCandidate_unmarkedInputBeforePassword() {
        // Many web forms omit autocomplete="username" and use generic <input type="text">
        val unmarkedInput = AutofillNodeDescriptor(
            id = "unmarked_text",
            htmlTag = "input",
            htmlType = "text",
            className = "android.widget.EditText"
        )
        val passInput = AutofillNodeDescriptor(
            id = "login_pass",
            htmlTag = "input",
            htmlType = "password",
            isFocused = true
        )

        val result = AutofillStructureParser.parseNodes(
            packageName = null,
            webDomain = "secure.auth.org",
            nodes = listOf(unmarkedInput, passInput)
        )

        assertEquals("Candidate preceding password in DOM must be paired as usernameId", "unmarked_text", result.usernameId)
        assertEquals("login_pass", result.passwordId)
    }

    @Test
    fun testHiddenCsrfToken_notChosenAsPrecedingCandidate() {
        val realUsername = AutofillNodeDescriptor(
            id = "real_user",
            htmlTag = "input",
            htmlType = "text",
            htmlName = "user_login"
        )
        val hiddenCsrf = AutofillNodeDescriptor(
            id = "csrf_token_field",
            htmlTag = "input",
            htmlType = "hidden",
            htmlName = "csrf_token"
        )
        val passInput = AutofillNodeDescriptor(
            id = "real_pass",
            htmlTag = "input",
            htmlType = "password"
        )

        val result = AutofillStructureParser.parseNodes(
            packageName = null,
            webDomain = "banking.example.com",
            nodes = listOf(realUsername, hiddenCsrf, passInput)
        )

        assertEquals("real_user", result.usernameId)
        assertEquals("real_pass", result.passwordId)
    }

    @Test
    fun testUsernameOnlyStep1_autocompleteUsername() {
        val emailInput = AutofillNodeDescriptor(
            id = "google_email",
            htmlTag = "input",
            htmlType = "email",
            htmlAutocomplete = "username",
            isFocused = true
        )

        val result = AutofillStructureParser.parseNodes(
            packageName = null,
            webDomain = "accounts.google.com",
            nodes = listOf(emailInput)
        )

        assertEquals("google_email", result.usernameId)
        assertNull("Step 1 form has no password field", result.passwordId)
    }

    @Test
    fun testPasswordOnlyForm_masterPasswordOrReauth() {
        val masterPassInput = AutofillNodeDescriptor(
            id = "master_pwd",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            hintText = "Enter Master Password",
            isFocused = true
        )

        val result = AutofillStructureParser.parseNodes(
            packageName = "com.doffi4.doffisecure",
            webDomain = null,
            nodes = listOf(masterPassInput)
        )

        assertNull("Unlock screen has no username", result.usernameId)
        assertEquals("master_pwd", result.passwordId)
    }

    @Test
    fun testRegistrationForm_newPassword() {
        val emailInput = AutofillNodeDescriptor(
            id = "reg_email",
            hints = listOf(View.AUTOFILL_HINT_EMAIL_ADDRESS),
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )
        val newPassInput = AutofillNodeDescriptor(
            id = "reg_new_pwd",
            htmlAutocomplete = "new-password"
        )

        val result = AutofillStructureParser.parseNodes(
            packageName = null,
            webDomain = "signup.service.com",
            nodes = listOf(emailInput, newPassInput)
        )

        assertEquals("reg_email", result.usernameId)
        assertEquals("reg_new_pwd", result.newPasswordId)
    }

    // =========================================================================
    // Requirement R2 & R3 AutofillResponsePlanner Decision Tests
    // =========================================================================

    @Test
    fun testR2Decision_noPasswordAndNoMatches_returnsEmpty() {
        // Case: User is on a screen with a detected username or search/text, but NO password field
        // and NO saved accounts exist -> must suppress completely (Requirement R2)
        val decision = AutofillResponsePlanner.plan(
            targetUsernameId = "some_username",
            targetPasswordId = null,
            webDomain = "translate.google.com",
            packageName = "com.google.android.apps.translate",
            matches = emptyList()
        )

        assertTrue("Must return Empty when targetPasswordId == null and matches.isEmpty()", decision is AutofillDecision.Empty)
    }

    @Test
    fun testR2Decision_bothNull_returnsEmpty() {
        val decision = AutofillResponsePlanner.plan(
            targetUsernameId = null,
            targetPasswordId = null,
            webDomain = null,
            packageName = "org.telegram.messenger",
            matches = emptyList()
        )

        assertTrue("Must return Empty when both target IDs are null", decision is AutofillDecision.Empty)
    }

    @Test
    fun testR3Decision_targetPasswordNotNull_noMatches_showsPickerWithCleanTitleAndEmptySubtitle() {
        // Case: Login form on website (e.g. sso.crunchyroll.com) with password field, but no saved credentials yet.
        // Must show picker chip opening AutofillPickerActivity with clean domain title and EMPTY subtitle (no dummy Decryptum).
        val decision = AutofillResponsePlanner.plan(
            targetUsernameId = "cr_user",
            targetPasswordId = "cr_pass",
            webDomain = "sso.crunchyroll.com",
            packageName = "com.android.chrome",
            matches = emptyList()
        )

        assertTrue(decision is AutofillDecision.ShowPicker)
        val showPicker = decision as AutofillDecision.ShowPicker<String>
        assertEquals("sso.crunchyroll.com", showPicker.displayTitle)
        assertEquals("Subtitle must be empty string to prevent Gboard ': Decryptum' duplication", "", showPicker.inlineSubtitle)
        assertFalse(showPicker.isSavedAccount)
        assertEquals("cr_user", showPicker.targetUsernameId)
        assertEquals("cr_pass", showPicker.targetPasswordId)
    }

    @Test
    fun testR3Decision_withMatchingAccount_showsServiceAndUsername() {
        val savedAccount = Password(101L, "Crunchyroll", "naruto_fan", "secret123", "https://sso.crunchyroll.com", 0L)
        val decision = AutofillResponsePlanner.plan(
            targetUsernameId = "cr_user",
            targetPasswordId = "cr_pass",
            webDomain = "sso.crunchyroll.com",
            packageName = "com.android.chrome",
            matches = listOf(savedAccount)
        )

        assertTrue(decision is AutofillDecision.ShowPicker)
        val showPicker = decision as AutofillDecision.ShowPicker<String>
        assertEquals("Crunchyroll", showPicker.displayTitle)
        assertEquals("naruto_fan", showPicker.inlineSubtitle)
        assertTrue(showPicker.isSavedAccount)
    }

    @Test
    fun testR2Decision_step1WithMatchingAccount_showsAccount() {
        // Multi-step login flow: password field is absent on Step 1, but user has a saved account
        val savedAccount = Password(202L, "Google", "user@gmail.com", "pass456", "https://accounts.google.com", 0L)
        val decision = AutofillResponsePlanner.plan(
            targetUsernameId = "google_user",
            targetPasswordId = null,
            webDomain = "accounts.google.com",
            packageName = "com.android.chrome",
            matches = listOf(savedAccount)
        )

        assertTrue(decision is AutofillDecision.ShowPicker)
        val showPicker = decision as AutofillDecision.ShowPicker<String>
        assertEquals("Google", showPicker.displayTitle)
        assertEquals("user@gmail.com", showPicker.inlineSubtitle)
        assertTrue(showPicker.isSavedAccount)
    }
}
