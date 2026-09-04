# --- DoffiSecure release R8 keep rules ---
#
# R8 is enabled for release to strip dead code (including the unused icons from
# material-icons-extended) which shrinks the APK and makes DEX class-loading and
# cold start faster. These rules keep the parts that are resolved at runtime
# (Koin, Room reflection, developer mode) so everything keeps working in release.

# Developer mode + shared managers / ViewModels created via Koin are kept intact
# so the 6-tap dev mode and the developer tools survive shrinking and
# obfuscation.
-keep class com.doffi4.doffisecure.security.DevModeManager { *; }
-keep class com.doffi4.doffisecure.security.UserSettingsManager { *; }
-keep class com.doffi4.doffisecure.ui.password.PasswordViewModel { *; }
-keep class com.doffi4.doffisecure.ui.password.GeneratorViewModel { *; }
-keep class com.doffi4.doffisecure.ui.password.SettingsViewModel { *; }
-keep class com.doffi4.doffisecure.ui.password.DevToolsViewModel { *; }
-keep class com.doffi4.doffisecure.ui.lock.AppLockViewModel { *; }

# Koin: module/definition lookups happen at runtime by type.
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# Room instantiates the generated *_Impl helpers reflectively.
-keep class com.doffi4.doffisecure.data.local.database.AppDatabase_Impl { *; }
-keep class com.doffi4.doffisecure.data.local.dao.PasswordDao_Impl { *; }
-keepclasseswithmembers class * {
    @androidx.room.* <methods>;
}