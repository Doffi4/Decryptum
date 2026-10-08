package com.doffi4.doffisecure

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import com.doffi4.doffisecure.ui.password.PasswordEntrySheet
import com.doffi4.doffisecure.ui.theme.DecryptumTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w412dp-h892dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PasswordEntrySheetTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var activity: ActivityController<ComponentActivity>? = null
    @After fun closeActivity() { activity?.pause()?.stop()?.destroy() }
    private fun show(save: suspend (String, String, String) -> Unit = { _, _, _ -> },
                     dismiss: () -> Unit = {}, dark: Boolean = true, fontScale: Float = 1f) {
        RuntimeEnvironment.setFontScale(fontScale)
        // Own the JVM host directly; do not add a test activity to the release manifest.
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        activity = host
        host.get().setContent { DecryptumTheme(darkTheme = dark, dynamicColor = false) {
            PasswordEntrySheet(dismiss, save, { "aB7!generated" }, true)
        } }
    }
    private fun fill() {
        compose.onNodeWithTag("entry_service").performTextInput("  example.com  ")
        compose.onNodeWithTag("entry_username").performTextInput("  alice  ")
        compose.onNodeWithTag("entry_password").performTextInput(" secret with spaces ")
    }
    @Test fun `required fields gate save and generation supplies a masked password`() {
        show()
        compose.onNodeWithTag("entry_save").assertIsNotEnabled()
        compose.onNodeWithTag("entry_service").performTextInput("example.com")
        compose.onNodeWithTag("entry_username").performTextInput("alice")
        compose.onNodeWithTag("entry_generate").performScrollTo().performClick()
        compose.onNodeWithTag("entry_save").assertIsEnabled()
        compose.onNodeWithTag("entry_password").assertTextContains("aB7!generated", substring = true)
        compose.onNodeWithTag("entry_password").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
    }
    @Test fun `save failure retains fields and supports retry without showing exception details`() {
        var attempts = 0
        var dismissed = 0
        var saved: List<String>? = null
        show(save = { s, u, p ->
            attempts++
            if (attempts == 1) error("PRIVATE-EXCEPTION-DETAIL")
            saved = listOf(s, u, p)
        }, dismiss = { dismissed++ })
        fill()
        compose.onNodeWithTag("entry_save").performScrollTo().performClick()
        compose.onNodeWithTag("entry_error").assertExists()
        compose.onNodeWithText("PRIVATE-EXCEPTION-DETAIL").assertDoesNotExist()
        compose.onNodeWithTag("entry_service").assertTextContains("  example.com  ", substring = true)
        compose.onNodeWithTag("entry_username").assertTextContains("  alice  ", substring = true)
        compose.onNodeWithTag("entry_save").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(2, attempts)
            assertEquals(1, dismissed)
            assertEquals(listOf("example.com", "alice", " secret with spaces "), saved)
        }
    }
    @Test fun `in flight save prevents duplicate submits and closes only after persistence`() {
        val gate = CompletableDeferred<Unit>()
        var writes = 0
        var dismissed = 0
        show(save = { _, _, _ -> writes++; gate.await() }, dismiss = { dismissed++ })
        fill()
        compose.onNodeWithTag("entry_save").performScrollTo().performClick()
        compose.onNodeWithTag("entry_save").assertIsNotEnabled()
        compose.onNodeWithTag("entry_service").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, writes); assertEquals(0, dismissed); gate.complete(Unit) }
        compose.runOnIdle { assertEquals(1, writes); assertEquals(1, dismissed) }
    }

    @Test fun `next skips icon buttons and invalid done returns to first missing field`() {
        show()
        compose.onNodeWithTag("entry_service").performClick().performImeAction()
        compose.onNodeWithTag("entry_username").assertIsFocused().performImeAction()
        compose.onNodeWithTag("entry_password").assertIsFocused().performImeAction()
        compose.onNodeWithTag("entry_service").assertIsFocused()
        compose.onNodeWithTag("entry_save").assertIsNotEnabled()
    }

    @Test @Config(qualifiers = "ru-rRU-w412dp-h892dp")
    fun `dark Russian sheet renders with native Material controls`() {
        show()
        fill()
        compose.onNodeWithTag("entry_save").performScrollTo().assertIsDisplayed()
        capture("add-password-dark-ru.png")
    }

    @Test @Config(qualifiers = "ru-rRU-w320dp-h568dp")
    fun `light compact sheet with large text keeps save reachable`() {
        show(dark = false, fontScale = 2f)
        compose.runOnIdle { assertEquals(2f, ShadowDialog.getLatestDialog().context.resources.configuration.fontScale) }
        fill()
        compose.onNodeWithTag("entry_save").performScrollTo().assertIsDisplayed().assertIsEnabled()
        capture("add-password-light-large-ru.png")
    }

    private fun capture(name: String) {
        val output = File(System.getProperty("decryptum.preview.dir", ".artifacts/previews"), name)
        checkNotNull(output.parentFile).mkdirs()
        // PixelCopy forceRedraw waits for a physical window frame; render the JVM dialog's
        // actual measured view instead. This is a Robolectric preview, not a device capture.
        compose.runOnIdle {
            val view = checkNotNull(ShadowDialog.getLatestDialog()?.window).decorView
            check(view.width > 0 && view.height > 0)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        assertTrue(output.length() > 0)
    }
}
