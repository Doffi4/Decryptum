package com.doffi4.doffisecure

import android.text.InputType
import com.doffi4.doffisecure.autofill.AutofillDecision
import com.doffi4.doffisecure.autofill.AutofillNodeDescriptor
import com.doffi4.doffisecure.autofill.AutofillResponsePlanner
import com.doffi4.doffisecure.autofill.AutofillStructureParser
import com.doffi4.doffisecure.domain.model.Password
import org.junit.Assert.*
import org.junit.Test

class AutofillAdversarialStressTest {

    // =========================================================================
    // Group 1: Tricky & Adversarial Hints (Search bars with 'password', Notes, Queries)
    // R1 Requirement: Search bars and notes MUST return usernameId = null AND passwordId = null
    // =========================================================================

    @Test
    fun testSearchBar_withPasswordInHint_mustNotBePassword() {
        val searchWithPassHint = AutofillNodeDescriptor(
            id = "search_field_1",
            idEntry = "search_box",
            htmlTag = "input",
            htmlType = "search",
            hintText = "Search by password or title",
            isFocused = true,
            inputType = InputType.TYPE_CLASS_TEXT
        )

        val result = AutofillStructureParser.parseNodes(
            packageName = "com.example.search",
            webDomain = "search.example.com",
            nodes = listOf(searchWithPassHint)
        )

        assertNull("Search bar with 'password' in hint must NOT be identified as passwordId", result.passwordId)
        assertNull("Search bar must NOT be usernameId", result.usernameId)
    }

    @Test
    fun testSearchBar_withUkrainianPasswordHint_mustNotBePassword() {
        val searchUk = AutofillNodeDescriptor(
            id = "search_field_uk",
            idEntry = "app_search",
            className = "android.widget.EditText",
            hintText = "Пошук паролів та ключів",
            isFocused = true,
            inputType = InputType.TYPE_CLASS_TEXT
        )

        val result = AutofillStructureParser.parseNodes(
            packageName = "com.store.app",
            webDomain = null,
            nodes = listOf(searchUk)
        )

        assertNull("Ukrainian search bar with 'пошук паролів' must NOT be passwordId", result.passwordId)
        assertNull("Search bar must NOT be usernameId", result.usernameId)
    }

    @Test
    fun testPassengerPasswordQuery_adversarialHint() {
        val trickyInput = AutofillNodeDescriptor(
            id = "tricky_field",
            idEntry = "passenger_query",
            className = "android.widget.EditText",
            hintText = "passenger password query",
            isFocused = true,
            inputType = InputType.TYPE_CLASS_TEXT
        )

        val result = AutofillStructureParser.parseNodes(
            packageName = "com.airline.booking",
            webDomain = null,
            nodes = listOf(trickyInput)
        )

        assertNull("Passenger query with 'password' word in query hint must NOT be passwordId", result.passwordId)
        assertNull("Passenger query must NOT be usernameId", result.usernameId)
    }

    @Test
    fun testAccountBalanceSearch_mustReturnNullForBoth() {
        val accBalanceSearch = AutofillNodeDescriptor(
            id = "acc_search",
            idEntry = "account_balance_search",
            className = "android.widget.EditText",
            hintText = "account balance search",
            isFocused = true,
            inputType = InputType.TYPE_CLASS_TEXT
        )

        val result = AutofillStructureParser.parseNodes(
            packageName = "com.bank.app",
            webDomain = null,
            nodes = listOf(accBalanceSearch)
        )

        assertNull("Account balance search must NOT be usernameId", result.usernameId)
        assertNull("Account balance search must NOT be passwordId", result.passwordId)
    }

    @Test
    fun testNoteWithPasswordHint_multiLineMustNotBePassword() {
        val noteField = AutofillNodeDescriptor(
            id = "note_body",
            idEntry = "note_content_text",
            className = "android.widget.EditText",
            hintText = "Мої збережені паролі",
            isFocused = true,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        )

        val result = AutofillStructureParser.parseNodes(
            packageName = "com.google.android.keep",
            webDomain = null,
            nodes = listOf(noteField)
        )

        assertNull("Multi-line note field must NOT be passwordId even if hint contains 'паролі'", result.passwordId)
        assertNull("Multi-line note field must NOT be usernameId", result.usernameId)
    }

    // =========================================================================
    // Group 2: Substring Collisions ("pass_" and "_pass" in non-password IDs)
    // =========================================================================

    @Test
    fun testCompassInput_mustNotBePassword() {
        val compassInput = AutofillNodeDescriptor(
            id = "compass_field",
            idEntry = "compass_input",
            className = "android.widget.EditText",
            hintText = "Enter bearing degrees",
            isFocused = true,
            inputType = InputType.TYPE_CLASS_TEXT
        )

        val result = AutofillStructureParser.parseNodes(
            packageName = "com.navigation.compass",
            webDomain = null,
            nodes = listOf(compassInput)
        )

        assertNull("Input with id 'compass_input' contains 'pass_' but must NOT be passwordId", result.passwordId)
    }

    @Test
    fun testBoardingPass_mustNotBePassword() {
        val boardingPass = AutofillNodeDescriptor(
            id = "boarding_field",
            idEntry = "boarding_pass",
            className = "android.widget.EditText",
            hintText = "Scan or type pass number",
            isFocused = true,
            inputType = InputType.TYPE_CLASS_TEXT
        )

        val result = AutofillStructureParser.parseNodes(
            packageName = "com.airline.fly",
            webDomain = null,
            nodes = listOf(boardingPass)
        )

        assertNull("Input with id 'boarding_pass' contains '_pass' but must NOT be passwordId", result.passwordId)
    }

    // =========================================================================
    // Group 3: Cyrillic & Ukrainian Variations and Search Combinations
    // =========================================================================

    @Test
    fun testSearchByLoginOrEmail_ukrainian_mustNotBeUsername() {
        val searchLogin = AutofillNodeDescriptor(
            id = "user_search",
            idEntry = "search_user_query",
            className = "android.widget.EditText",
            hintText = "Пошук за логіном або email",
            isFocused = true,
            inputType = InputType.TYPE_CLASS_TEXT
        )

        val result = AutofillStructureParser.parseNodes(
            packageName = "com.social.app",
            webDomain = null,
            nodes = listOf(searchLogin)
        )

        assertNull("Search input with 'Пошук за логіном' must NOT be usernameId", result.usernameId)
        assertNull("Search input must NOT be passwordId", result.passwordId)
    }

    @Test
    fun testValidUkrainianLoginForm_detectsBoth() {
        val userInput = AutofillNodeDescriptor(
            id = "login_ua",
            idEntry = "user_name_field",
            className = "android.widget.EditText",
            hintText = "Електронна пошта або логін",
            inputType = InputType.TYPE_CLASS_TEXT
        )
        val passInput = AutofillNodeDescriptor(
            id = "pass_ua",
            idEntry = "user_password_field",
            className = "android.widget.EditText",
            hintText = "Введіть пароль",
            isFocused = true,
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )

        val result = AutofillStructureParser.parseNodes(
            packageName = "ua.bank.my",
            webDomain = null,
            nodes = listOf(userInput, passInput)
        )

        assertEquals("login_ua", result.usernameId)
        assertEquals("pass_ua", result.passwordId)
    }

    // =========================================================================
    // Group 4: Inverted DOM Orders & Hidden Inputs Interleaving
    // =========================================================================

    @Test
    fun testInvertedDOMOrder_explicitAttributes_stillDetected() {
        // Form where password input appears physically before username in DOM
        val passInput = AutofillNodeDescriptor(
            id = "inv_pass",
            htmlTag = "input",
            htmlType = "password",
            htmlAutocomplete = "current-password"
        )
        val userInput = AutofillNodeDescriptor(
            id = "inv_user",
            htmlTag = "input",
            htmlType = "text",
            htmlAutocomplete = "username",
            isFocused = true
        )

        val result = AutofillStructureParser.parseNodes(
            packageName = null,
            webDomain = "reversed.dom.com",
            nodes = listOf(passInput, userInput)
        )

        assertEquals("inv_user", result.usernameId)
        assertEquals("inv_pass", result.passwordId)
    }

    @Test
    fun testHiddenInputsBeforeAndAfterPassword_pairsCorrectly() {
        val hiddenBefore = AutofillNodeDescriptor(
            id = "csrf_1",
            htmlTag = "input",
            htmlType = "hidden",
            htmlName = "csrf_token"
        )
        val userField = AutofillNodeDescriptor(
            id = "login_text",
            htmlTag = "input",
            htmlType = "text",
            htmlName = "login"
        )
        val hiddenBetween = AutofillNodeDescriptor(
            id = "csrf_2",
            htmlTag = "input",
            htmlType = "hidden",
            htmlName = "security_nonce"
        )
        val passField = AutofillNodeDescriptor(
            id = "password_text",
            htmlTag = "input",
            htmlType = "password",
            isFocused = true
        )
        val hiddenAfter = AutofillNodeDescriptor(
            id = "csrf_3",
            htmlTag = "input",
            htmlType = "hidden",
            htmlName = "client_state"
        )

        val result = AutofillStructureParser.parseNodes(
            packageName = null,
            webDomain = "secure.portal.org",
            nodes = listOf(hiddenBefore, userField, hiddenBetween, passField, hiddenAfter)
        )

        assertEquals("login_text", result.usernameId)
        assertEquals("password_text", result.passwordId)
    }

    // =========================================================================
    // Group 5: Nested Form Hierarchies & Tree Traversal
    // =========================================================================

    @Test
    fun testNestedTreeHierarchy_isolatesAuthFields() {
        // Layout:
        // Root
        //  ├── Header (Search container)
        //  │     └── Search input
        //  ├── Body (Form container)
        //  │     ├── Username input
        //  │     └── Password input
        //  └── Footer (Comments container)
        //        └── Comment multiline

        val searchLeaf = AutofillNodeDescriptor(
            id = "header_search",
            idEntry = "search_box",
            htmlTag = "input",
            htmlType = "search",
            hintText = "Search..."
        )
        val headerContainer = AutofillNodeDescriptor(
            id = "header_group",
            childCount = 1,
            children = listOf(searchLeaf)
        )

        val userLeaf = AutofillNodeDescriptor(
            id = "form_user",
            htmlTag = "input",
            htmlType = "text",
            htmlAutocomplete = "username"
        )
        val passLeaf = AutofillNodeDescriptor(
            id = "form_pass",
            htmlTag = "input",
            htmlType = "password",
            isFocused = true
        )
        val formContainer = AutofillNodeDescriptor(
            id = "form_group",
            childCount = 2,
            children = listOf(userLeaf, passLeaf)
        )

        val commentLeaf = AutofillNodeDescriptor(
            id = "footer_comment",
            idEntry = "comment_text",
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        )
        val footerContainer = AutofillNodeDescriptor(
            id = "footer_group",
            childCount = 1,
            children = listOf(commentLeaf)
        )

        val rootNode = AutofillNodeDescriptor(
            id = "root_view",
            childCount = 3,
            children = listOf(headerContainer, formContainer, footerContainer)
        )

        val result = AutofillStructureParser.parseNodes(
            packageName = "com.complex.layout",
            webDomain = "complex.layout.com",
            nodes = listOf(rootNode)
        )

        assertEquals("form_user", result.usernameId)
        assertEquals("form_pass", result.passwordId)
    }

    // =========================================================================
    // Group 6: 0-Character Hints & Blank Attributes
    // =========================================================================

    @Test
    fun testZeroCharacterAndBlankHints_unmarkedFields() {
        val blank1 = AutofillNodeDescriptor(
            id = "blank_text_1",
            hintText = "",
            htmlPlaceholder = "",
            idEntry = "",
            className = "android.widget.EditText",
            inputType = InputType.TYPE_CLASS_TEXT
        )
        val passField = AutofillNodeDescriptor(
            id = "blank_pass",
            hintText = "",
            htmlTag = "input",
            htmlType = "password",
            isFocused = true
        )

        val result = AutofillStructureParser.parseNodes(
            packageName = null,
            webDomain = "blank.com",
            nodes = listOf(blank1, passField)
        )

        // Preceding candidate pairing should pair blank_text_1 with blank_pass
        assertEquals("blank_text_1", result.usernameId)
        assertEquals("blank_pass", result.passwordId)
    }

    // =========================================================================
    // Group 7: AutofillResponsePlanner Boundary Stress Testing
    // =========================================================================

    @Test
    fun testDecisionPlanner_nullAndBlankDomains() {
        // targetPasswordId present, no matches, null webDomain & blank packageName
        val decision = AutofillResponsePlanner.plan(
            targetUsernameId = "user1",
            targetPasswordId = "pass1",
            webDomain = null,
            packageName = "   ",
            matches = emptyList(),
            defaultPickerTitle = "Select account to fill"
        )

        assertTrue(decision is AutofillDecision.ShowPicker)
        val show = decision as AutofillDecision.ShowPicker<String>
        assertEquals("Select account to fill", show.displayTitle)
        assertEquals("", show.inlineSubtitle)
        assertFalse(show.isSavedAccount)
    }

    @Test
    fun testDecisionPlanner_blankPackageName_doesNotCrash() {
        val decision = AutofillResponsePlanner.plan(
            targetUsernameId = "user2",
            targetPasswordId = "pass2",
            webDomain = "",
            packageName = "",
            matches = emptyList()
        )

        assertTrue(decision is AutofillDecision.ShowPicker)
        val show = decision as AutofillDecision.ShowPicker<String>
        assertEquals("Select account to fill", show.displayTitle)
    }

    @Test
    fun testDecisionPlanner_matchesWithEmptyUsernameOrService() {
        val blankAcc = Password(
            id = 500L,
            service = "",
            username = "",
            password = "",
            url = "",
            createdAt = 0L
        )

        val decision = AutofillResponsePlanner.plan(
            targetUsernameId = "u",
            targetPasswordId = "p",
            webDomain = "site.com",
            packageName = "com.site",
            matches = listOf(blankAcc)
        )

        assertTrue(decision is AutofillDecision.ShowPicker)
        val show = decision as AutofillDecision.ShowPicker<String>
        assertEquals("", show.displayTitle)
        assertEquals("", show.inlineSubtitle)
        assertTrue(show.isSavedAccount)
    }

    @Test
    fun testDecisionPlanner_multipleMatches_picksFirst() {
        val acc1 = Password(1L, "Primary", "admin@site.com", "pass1", "https://site.com", 0L)
        val acc2 = Password(2L, "Secondary", "user@site.com", "pass2", "https://site.com", 0L)
        val acc3 = Password(3L, "Tertiary", "guest@site.com", "pass3", "https://site.com", 0L)

        val decision = AutofillResponsePlanner.plan(
            targetUsernameId = "u",
            targetPasswordId = "p",
            webDomain = "site.com",
            packageName = null,
            matches = listOf(acc1, acc2, acc3)
        )

        assertTrue(decision is AutofillDecision.ShowPicker)
        val show = decision as AutofillDecision.ShowPicker<String>
        assertEquals("Primary", show.displayTitle)
        assertEquals("admin@site.com", show.inlineSubtitle)
    }

    @Test
    fun testDecisionPlanner_r2Enforcement_whenUsernameDetectedOnNonAuthScreen_noMatches_returnsEmpty() {
        // If an input somehow has username keyword on a screen with NO password field and NO matches in vault,
        // AutofillResponsePlanner MUST return Empty
        val decision = AutofillResponsePlanner.plan(
            targetUsernameId = "orphan_user",
            targetPasswordId = null,
            webDomain = "read.wikipedia.org",
            packageName = "org.wikipedia",
            matches = emptyList()
        )

        assertTrue("Must be Empty when targetPasswordId is null and matches is empty", decision is AutofillDecision.Empty)
    }
}
