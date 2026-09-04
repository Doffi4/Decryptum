package com.doffi4.doffisecure.di

import androidx.room.Room
import androidx.room.RoomDatabase
import com.doffi4.doffisecure.data.local.dao.PasswordDao
import com.doffi4.doffisecure.data.local.database.AppDatabase
import com.doffi4.doffisecure.data.repository.PasswordRepositoryImpl
import com.doffi4.doffisecure.domain.repository.IPasswordRepository
import com.doffi4.doffisecure.domain.usecase.AddPasswordUseCase
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
import com.doffi4.doffisecure.security.AppLockManager
import com.doffi4.doffisecure.security.DevModeManager
import com.doffi4.doffisecure.security.PasswordCrypto
import com.doffi4.doffisecure.security.SecureClipboard
import com.doffi4.doffisecure.security.UserSettingsManager
import com.doffi4.doffisecure.security.VaultWarmup
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
    single { AppLockManager(androidContext()) }

    // Hidden developer mode (toggled by tapping the app title 6 times)
    single { DevModeManager(androidContext()) }

    // User-facing settings (password strength meter, etc.)
    single { UserSettingsManager(androidContext()) }

    // Secure clipboard with auto-clear
    single { SecureClipboard(androidContext()) }

    // Cold-start warm-up: converts legacy rows and prefetches the vault while
    // the lock screen is visible, so the main screen opens smooth.
    single { VaultWarmup(androidContext(), get()) }

    // Idle frame-rate governor: steps the app down 120 -> 60 -> 30 fps when
    // nothing is pressed and applies Android 15 LTPO power-savings hints.
    single { RefreshRateController() }

    // Reads CPU temperature / load / frequency from sysfs + /proc/stat for the
    // developer tools (no Android APIs involved, so it lives as a plain single).
    single { CpuMonitor() }

    // Database instance. WAL keeps reads cheap and headlines writes during the
    // bulk import / one-time VaultWarmup reads of large vaults (500+ rows).
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "password_db"
        ).setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
    }

    // DAO instance
    single { get<AppDatabase>().passwordDao() }

    // Repository implementation
    single<IPasswordRepository> { PasswordRepositoryImpl(get(), get()) }

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

    // ViewModels
    viewModel { AppLockViewModel(get(), get(), get()) }
    viewModel {
        PasswordViewModel(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()
        )
    }
    viewModel { GeneratorViewModel(get(), get(), get()) }
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel {
        DevToolsViewModel(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get()
        )
    }
}
