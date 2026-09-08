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

# Autofill & Credential Provider services and activities
-keep class com.doffi4.doffisecure.autofill.** { *; }

# Argon2Kt JNI native bindings and classes
-keep class com.lambdapioneer.argon2kt.** { *; }
-dontwarn com.lambdapioneer.argon2kt.**

# SQLCipher native bindings and classes
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**

# ML Kit Barcode Scanning & Vision Rules
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode_bundled.** { *; }
-keep class com.google.android.gms.vision.** { *; }
-dontwarn com.google.android.gms.**

-keepclassmembers class * extends com.google.android.gms.internal.mlkit_vision_barcode_bundled.zzeh {
    <fields>;
}

-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclassmembers class * {
    native <methods>;
}

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# CameraX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ZXing
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
