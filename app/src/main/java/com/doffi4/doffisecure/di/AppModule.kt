package com.doffi4.doffisecure.di

import androidx.room.Room
import androidx.room.RoomDatabase
import com.doffi4.doffisecure.data.local.database.AppDatabase
import com.doffi4.doffisecure.data.repository.PasswordRepositoryImpl
import com.doffi4.doffisecure.data.repository.PasskeyRepositoryImpl
import com.doffi4.doffisecure.data.repository.IPwnedPasswordsRepository
import com.doffi4.doffisecure.data.repository.PwnedPasswordsRepository
import com.doffi4.doffisecure.domain.repository.IPasswordRepository
import com.doffi4.doffisecure.domain.repository.IPasskeyRepository
import com.doffi4.doffisecure.security.webauthn.WebAuthnCryptoEngine
import com.doffi4.doffisecure.domain.usecase.AddPasswordUseCase
import com.doffi4.doffisecure.domain.usecase.CheckPasswordBreachUseCase
import com.doffi4.doffisecure.domain.usecase.CheckEncryptionIntegrityUseCase
import com.doffi4.doffisecure.domain.usecase.CountEncryptedPasswordsUseCase
import com.doffi4.doffisecure.domain.usecase.CountPasswordsUseCase
import com.doffi4.doffisecure.domain.usecase.DeleteAllPasswordsUseCase
import com.doffi4.doffisecure.domain.usecase.DeleteDuplicatesUseCase
import com.doffi4.doffisecure.domain.usecase.DeletePasswordUseCase
import com.doffi4.doffisecure.domain.usecase.GetDuplicateGroupsUseCase
import com.doffi4.doffisecure.domain.usecase.GetPasswordByIdUseCase
import com.doffi4.doffisecure.domain.usecase.GetPasswordsUseCase
import com.doffi4.doffisecure.domain.usecase.GeneratePasswordUseCase
import com.doffi4.doffisecure.domain.usecase.ImportPasswordsUseCase
import com.doffi4.doffisecure.domain.usecase.SearchPasswordsUseCase
import com.doffi4.doffisecure.domain.usecase.UpdatePasswordUseCase
import com.doffi4.doffisecure.dev.CpuMonitor
import com.doffi4.doffisecure.dev.RefreshRateController
import com.doffi4.doffisecure.data.local.database.DatabaseMigrator
import com.doffi4.doffisecure.security.AppLockManager
import com.doffi4.doffisecure.security.DatabaseKeyManager
import com.doffi4.doffisecure.security.DevModeManager
import com.doffi4.doffisecure.security.PasswordCrypto
import com.doffi4.doffisecure.security.SecureClipboard
import com.doffi4.doffisecure.security.UserSettingsManager
import com.doffi4.doffisecure.security.VaultWarmup
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

import com.doffi4.doffisecure.ui.lock.AppLockViewModel
import com.doffi4.doffisecure.ui.password.DevToolsViewModel
import com.doffi4.doffisecure.ui.password.GeneratorViewModel
import com.doffi4.doffisecure.ui.password.PasswordViewModel
import com.doffi4.doffisecure.ui.password.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Password encryption (Android Keystore + in-memory envelope key)
    single { PasswordCrypto(androidContext()) }

    // Application lock (master password)
    single { AppLockManager(androidContext(), get()) }

    // Hidden developer mode (toggled by tapping the app title 6 times)
    single { DevModeManager(androidContext()) }

    // User-facing settings (password strength meter, etc.)
    single { UserSettingsManager(androidContext()) }

    // Secure clipboard with auto-clear
    single { SecureClipboard(androidContext()) }

    // Vault warm-up: pre-warms cache and prefetches favicons once unlocked
    single { VaultWarmup(androidContext(), get(), get()) }

    // Idle frame-rate governor: steps the app down 120 -> 60 -> 30 fps when
    // nothing is pressed and applies Android 15 LTPO power-savings hints.
    single { RefreshRateController() }

    // Reads CPU temperature / load / frequency from sysfs + /proc/stat for the
    // developer tools (no Android APIs involved, so it lives as a plain single).
    single { CpuMonitor() }

    // Database key manager & migration
    single { DatabaseKeyManager(androidContext()) }
    single { DatabaseMigrator() }

    // Database instance. Encrypted at rest via SQLCipher. WAL keeps reads cheap
    // and headlines writes during bulk import / VaultWarmup reads.
    single {
        val context = androidContext()
        val keyManager = get<DatabaseKeyManager>()
        val migrator = get<DatabaseMigrator>()
        val passphrase = keyManager.getPassphrase()

        // Migrate any existing unencrypted database before Room initializes
        migrator.migrateIfNeeded(context, passphrase)

        val hexKey = passphrase.joinToString("") { "%02x".format(it) }
        val rawKeyBytes = "x'$hexKey'".toByteArray(Charsets.US_ASCII)
        val factory = SupportOpenHelperFactory(rawKeyBytes)
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "password_db",
        )
            .openHelperFactory(factory)
            .addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
    }


    // DAO instances
    single { get<AppDatabase>().passwordDao() }
    single { get<AppDatabase>().passkeyDao() }

    // Repository implementations
    single<IPasswordRepository> { PasswordRepositoryImpl(get(), get()) }
    single<IPasskeyRepository> { PasskeyRepositoryImpl(get()) }
    single<IPwnedPasswordsRepository> { PwnedPasswordsRepository() }

    // WebAuthn Crypto Engine
    single { WebAuthnCryptoEngine(get()) }

    // Use Cases
    factory { GetPasswordsUseCase(get()) }
    factory { GetPasswordByIdUseCase(get()) }
    factory { AddPasswordUseCase(get()) }
    factory { CountPasswordsUseCase(get()) }
    factory { CountEncryptedPasswordsUseCase(get()) }
    factory { CheckEncryptionIntegrityUseCase(get()) }
    factory { GetDuplicateGroupsUseCase(get()) }
    factory { DeleteDuplicatesUseCase(get()) }
    factory { DeletePasswordUseCase(get()) }
    factory { DeleteAllPasswordsUseCase(get()) }
    factory { ImportPasswordsUseCase(get()) }
    factory { SearchPasswordsUseCase(get()) }
    factory { UpdatePasswordUseCase(get()) }
    factory { GeneratePasswordUseCase() }
    factory { CheckPasswordBreachUseCase(get()) }

    // ViewModels
    viewModel { AppLockViewModel(get(), get(), get(), get(), get()) }
    viewModel {
        PasswordViewModel(
            getPasswordsUseCase = get(),
            getPasswordByIdUseCase = get(),
            addPasswordUseCase = get(),
            deletePasswordUseCase = get(),
            searchPasswordsUseCase = get(),
            updatePasswordUseCase = get(),
            countPasswordsUseCase = get(),
            secureClipboard = get(),
            devModeManager = get(),
            vaultWarmup = get(),
            refreshRateController = get(),
            userSettings = get(),
            passkeyRepository = get(),
            checkPasswordBreachUseCase = get(),
        )
    }
    viewModel { GeneratorViewModel(get(), get(), get()) }
    viewModel {
        SettingsViewModel(
            lockManager = get(),
            getPasswordsUseCase = get(),
            importPasswordsUseCase = get(),
            deleteAllPasswordsUseCase = get(),
            devModeManager = get(),
            userSettings = get(),
            passkeyRepository = get(),
            passwordCrypto = get(),
        )
    }
    viewModel {
        DevToolsViewModel(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
        )
    }
}
