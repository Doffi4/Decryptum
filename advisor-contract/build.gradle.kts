plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.doffi4.doffisecure.advisor.contract"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// No app/vault/JSON/network dependencies: only the four-Int contract.
dependencies {
    testImplementation(libs.junit)
}
