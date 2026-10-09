package com.doffi4.doffisecure

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.doffi4.doffisecure.dev.TestDatasetRequest
import com.doffi4.doffisecure.dev.TestDataSeedState
import com.doffi4.doffisecure.ui.password.SyntheticDataPanel
import com.doffi4.doffisecure.ui.theme.DecryptumTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowDialog
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "ru-rRU-w412dp-h892dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SyntheticDataPanelTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var activity: ActivityController<ComponentActivity>? = null
    private var state by mutableStateOf<TestDataSeedState>(TestDataSeedState.Idle)
    @After fun close() { activity?.pause()?.stop()?.destroy(); RuntimeEnvironment.setFontScale(1f) }
    private fun show(dark: Boolean = true, fontScale: Float = 1f, save: (TestDatasetRequest) -> Unit = {}) {
        RuntimeEnvironment.setFontScale(fontScale)
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        activity = host
        host.get().setContent { DecryptumTheme(darkTheme = dark, dynamicColor = false) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SyntheticDataPanel(state, 0, { state = TestDataSeedState.Idle }, save)
            }
        } }
    }

    @Test fun `default dataset requires confirmation and can be declined without saving`() {
        var saved = 0
        show { saved++ }
        compose.onNodeWithTag("synthetic_preview").performScrollTo().performClick()
        compose.onNodeWithTag("synthetic_confirm").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, saved) }
        compose.onNodeWithTag("synthetic_cancel").performClick()
        compose.runOnIdle { assertEquals(0, saved) }
    }

    @Test fun `invalid counts cannot open confirmation`() {
        show()
        compose.onNodeWithTag("synthetic_entries").performTextReplacement("0")
        compose.onNodeWithTag("synthetic_preview").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("synthetic_entries").performTextReplacement("100")
        compose.onNodeWithTag("synthetic_services").performTextReplacement("101")
        compose.onNodeWithTag("synthetic_preview").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("synthetic_services").performTextReplacement("50")
        compose.onNodeWithTag("synthetic_preview").performScrollTo().assertIsEnabled()
    }

    @Test fun `pending save blocks resubmission and error keeps retry available`() {
        var saved = 0
        show { saved++; state = TestDataSeedState.Running }
        compose.onNodeWithTag("synthetic_preview").performScrollTo().performClick()
        compose.onNodeWithTag("synthetic_confirm").performClick()
        compose.onNodeWithTag("synthetic_confirm").assertIsNotEnabled()
        compose.onNodeWithTag("synthetic_cancel").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, saved); state = TestDataSeedState.Failed }
        compose.onNodeWithTag("synthetic_error").assertExists()
        compose.onNodeWithTag("synthetic_confirm").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(2, saved) }
    }

    @Test fun `fixture confirmation sends requested kind`() {
        var request: TestDatasetRequest? = null
        show { request = it; state = TestDataSeedState.Saved(4, 4) }
        compose.onNodeWithTag("synthetic_security").performScrollTo().performClick()
        compose.onNodeWithTag("synthetic_confirm").performClick()
        compose.runOnIdle { assertEquals(TestDatasetRequest.SecurityCheck, request) }
        compose.onNodeWithTag("synthetic_confirm").assertDoesNotExist()
        compose.onNodeWithTag("synthetic_result").assertExists()
    }

    @Test fun `pending work stays visible even when confirmation is not mounted`() {
        state = TestDataSeedState.Running
        show()
        compose.onNodeWithTag("synthetic_progress").assertExists()
        compose.onNodeWithTag("synthetic_preview").performScrollTo().assertIsNotEnabled()
    }

    @Test fun `closing a failed confirmation retains feedback in the card`() {
        show { state = TestDataSeedState.Failed }
        compose.onNodeWithTag("synthetic_preview").performScrollTo().performClick()
        compose.onNodeWithTag("synthetic_confirm").performClick()
        compose.onNodeWithTag("synthetic_cancel").performClick()
        compose.onNodeWithTag("synthetic_error").assertExists()
    }

    @Test fun `Russian dataset preview renders native dialog`() {
        show()
        compose.onNodeWithTag("synthetic_preview").performScrollTo().performClick()
        compose.onNodeWithTag("synthetic_confirm").assertIsDisplayed()
        capture("synthetic-dark-ru.png")
    }

    @Test @Config(qualifiers = "en-rUS-w320dp-h568dp")
    fun `compact light preview keeps confirm reachable at double font size`() {
        show(dark = false, fontScale = 2f)
        compose.onNodeWithTag("synthetic_preview").performScrollTo().performClick()
        compose.onNodeWithTag("synthetic_confirm").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("synthetic_cancel").assertIsDisplayed().assertIsEnabled()
        capture("synthetic-light-large-en.png")
    }

    private fun capture(name: String) {
        val output = File(".artifacts/tester-previews", name)
        checkNotNull(output.parentFile).mkdirs()
        compose.runOnIdle {
            val view = checkNotNull(ShadowDialog.getLatestDialog()?.window).decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        assertTrue(output.length() > 0)
    }
}
