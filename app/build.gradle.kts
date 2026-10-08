plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.doffi4.doffisecure"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.doffi4.doffisecure"
        minSdk = 26
        targetSdk = 37
        versionCode = 7
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                // R8 shrinking for release: strips dead code (including the
                // unused icons from material-icons-extended) and shrinks the
                // APK, which also makes DEX class-loading / cold start faster.
                // Developer mode survives thanks to the keep rules in
                // proguard-rules.pro (Koin, Room, VMs, dev managers).
                enable = true
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Private candidate only. Public release is gated on owner-approved
            // signing identity and verified certificate/update continuity.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)


    // New dependencies for Password Manager (Room, Security, Koin)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler) 
    implementation(libs.androidx.security.crypto)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.coil.compose)
    implementation(libs.androidx.profileinstaller)
    implementation("androidx.autofill:autofill:1.1.0")
    implementation(libs.androidx.credentials)
    implementation(libs.argon2kt)
    implementation(libs.sqlcipher.android)
    implementation(libs.sqlite)

    // CameraX and ML Kit (2FA QR Code Scanning)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.google.mlkit.barcode)
    implementation(libs.zxing.core)



    testImplementation(libs.junit)
    // Runs Android Navigation's real saved-stack behavior without an attached phone.
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
