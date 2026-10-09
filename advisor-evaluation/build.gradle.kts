plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.doffi4.doffisecure.advisor.evaluation"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":advisor-contract"))
    // Existing runtime libraries, now explicit instead of app transitive dependencies.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

// Standalone JVM evaluation: ordinary unit tests never enable this execution entry point.
tasks.register<Test>("runAdvisorEvaluation") {
    val offlineTests = tasks.named<Test>("testDebugUnitTest")
    dependsOn(offlineTests)
    testClassesDirs = offlineTests.get().testClassesDirs
    classpath = offlineTests.get().classpath
    filter.includeTestsMatching("com.doffi4.doffisecure.eval.AdvisorHarnessExecutionTest")
    systemProperty("decryptum.advisor.eval.enabled", "true")
    systemProperty("decryptum.advisor.eval.output", rootProject.file(".artifacts/advisor-eval").absolutePath)
    systemProperty("decryptum.advisor.eval.mode", providers.gradleProperty("advisorEvalMode").getOrElse("dry"))
    for (option in listOf("cases", "languages", "repeats", "maxRequests", "run", "prompt")) {
        val property = "advisorEval" + option.replaceFirstChar { it.uppercase() }
        providers.gradleProperty(property).orNull?.let { systemProperty("decryptum.advisor.eval.$option", it) }
    }
    outputs.upToDateWhen { false }
    testLogging.showStandardStreams = true
}
