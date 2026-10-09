package com.doffi4.doffisecure

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelStore
import com.doffi4.doffisecure.dev.*
import com.doffi4.doffisecure.domain.model.*
import com.doffi4.doffisecure.domain.repository.*
import com.doffi4.doffisecure.domain.security.LocalSecurityAnalyzer
import com.doffi4.doffisecure.domain.usecase.*
import com.doffi4.doffisecure.security.*
import com.doffi4.doffisecure.ui.password.*
import com.doffi4.doffisecure.ui.security.*
import com.doffi4.doffisecure.ui.theme.DecryptumTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.security.MessageDigest

/** Opt-in capture of real screens; fixtures and repositories are synthetic and have no I/O. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "en-rUS-w412dp-h892dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReleaseScreenshotsTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun captureSyntheticReleaseScreens() {
        val output = System.getProperty("decryptum.screenshots")
        Assume.assumeTrue("Capture is explicitly enabled by the maintainer", !output.isNullOrBlank())
        val host = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        val store = ViewModelStore()
        try {
            val context = RuntimeEnvironment.getApplication()
            val settings = UserSettingsManager(context).apply { setLoadFavicons(false) }
            val now = 1_791_547_200_000L
            val vaultRows = SyntheticVaultGenerator.generate(SyntheticVaultGenerator.plan(100, 70), "demo", now)
                .mapIndexed { index, row -> row.copy(id = index.toLong() + 1, username = "demo-account-${index + 1}@example.invalid") }
            val otpRows = (1..3).map { index -> SyntheticVaultGenerator.totpFixture(now)
                .copy(id = 1000L + index, service = "Demo TOTP $index", username = "demo-user-$index") }
            val repository = ScreenshotRepository(vaultRows + otpRows)
            val crypto = PasswordCrypto(FakeSharedPreferences()) { password, salt ->
                MessageDigest.getInstance("SHA-256").digest(salt + password.toByteArray())
            }
            val vm = PasswordViewModel(GetPasswordsUseCase(repository), GetPasswordByIdUseCase(repository),
                AddPasswordUseCase(repository), DeletePasswordUseCase(repository), SearchPasswordsUseCase(repository),
                UpdatePasswordUseCase(repository), CountPasswordsUseCase(repository), SecureClipboard(context),
                DevModeManager(FakeSharedPreferences()), VaultWarmup(repository, crypto), RefreshRateController(),
                settings, ScreenshotPasskeys(), GeneratePasswordUseCase())
            store.put("screenshots", vm)
            val fixture = SyntheticVaultGenerator.securityFixture(now).mapIndexed { index, row -> row.copy(id = index.toLong() + 1) }
            val summary = LocalSecurityAnalyzer().analyze(fixture)
            Assert.assertEquals(4, summary.passwordEntries)
            var page by mutableIntStateOf(0)
            host.get().setContent {
                DecryptumTheme(darkTheme = true, dynamicColor = false) {
                    when (page) {
                        0 -> PasswordScreen(viewModel = vm, onNavigateToDetail = {})
                        1 -> TotpScreen(viewModel = vm, onNavigateToDetail = {})
                        2 -> SecurityCenterScreen(SecurityCenterState.Ready(summary), {}, {}, {}, {})
                        else -> Surface(Modifier.fillMaxSize()) { Column(Modifier.verticalScroll(rememberScrollState())) {
                            SyntheticDataPanel(TestDataSeedState.Idle, 0, {}, {})
                        } }
                    }
                }
            }
            for ((index, name) in listOf("vault-current", "totp-current", "security-center", "test-data").withIndex()) {
                compose.runOnIdle { page = index }
                compose.waitForIdle()
                compose.runOnIdle {
                    val view = host.get().window.decorView
                    Assert.assertTrue(view.width > 0 && view.height > 0)
                    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap))
                    val target = File(output!!, "$name.webp")
                    target.parentFile!!.mkdirs()
                    target.outputStream().use { Assert.assertTrue(bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, it)) }
                    bitmap.recycle()
                    Assert.assertTrue(target.length() > 0)
                }
            }
        } finally {
            store.clear()
            host.pause().stop().destroy()
        }
    }

    private class ScreenshotRepository(private val rows: List<Password>) : IPasswordRepository {
        override fun getAllPasswords() = flowOf(rows)
        override fun getAutofillHeaders() = flowOf(rows)
        override fun countPasswords() = flowOf(rows.size)
        override fun countEncryptedPasswords() = flowOf(0)
        override fun getDuplicateGroups() = flowOf(emptyList<DuplicateGroup>())
        override fun searchPasswords(query: String) = flowOf(rows.filter { it.service.contains(query, true) })
        override suspend fun getPasswordById(id: Long) = rows.firstOrNull { it.id == id }
        override suspend fun checkEncryptionIntegrity() = 0
        override suspend fun deleteDuplicates() = error("Read-only screenshot fixture")
        override suspend fun migrateLegacyEncryption() = 0
        override suspend fun addPassword(password: Password) = error("Read-only screenshot fixture")
        override suspend fun addPasswords(passwords: List<Password>): Int = error("Read-only screenshot fixture")
        override suspend fun updatePassword(password: Password) = error("Read-only screenshot fixture")
        override suspend fun deletePassword(id: Long) = error("Read-only screenshot fixture")
        override suspend fun deleteAllPasswords() = error("Read-only screenshot fixture")
    }

    private class ScreenshotPasskeys : IPasskeyRepository {
        override fun getAllPasskeys() = flowOf(emptyList<Passkey>())
        override fun getPasskeysForRpId(rpId: String) = flowOf(emptyList<Passkey>())
        override fun getPasskeysByLinkedPasswordId(passwordId: Long) = flowOf(emptyList<Passkey>())
        override suspend fun getPasskeysForRpIdSync(rpId: String) = emptyList<Passkey>()
        override suspend fun getPasskeyByCredentialId(credentialId: ByteArray): Passkey? = null
        override suspend fun getPasskeyById(id: Long): Passkey? = null
        override suspend fun addPasskey(passkey: Passkey): Long = error("Read-only screenshot fixture")
        override suspend fun addPasskeys(passkeys: List<Passkey>): Int = error("Read-only screenshot fixture")
        override suspend fun updateSignCount(id: Long, newCount: Long) = error("Read-only screenshot fixture")
        override suspend fun linkToPassword(passkeyId: Long, passwordId: Long?) = error("Read-only screenshot fixture")
        override suspend fun deletePasskey(id: Long) = error("Read-only screenshot fixture")
        override suspend fun deleteAllPasskeys() = error("Read-only screenshot fixture")
    }
}
