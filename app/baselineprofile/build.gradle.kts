plugins {
    // AGP is already on the classpath (via :app), so apply these without a version.
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.baselineprofile)
}

kotlin { jvmToolchain(11) }

android {
    namespace = "com.github.aendryn.unchainedunleashed.baselineprofile"
    compileSdk = 37

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    defaultConfig {
        // BaselineProfileRule / Macrobenchmark require API 28+ on the generating device.
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
}

// Generate against a real/emulated connected device instead of a Gradle Managed Device, so
// `./gradlew :app:generateBaselineProfile` uses whatever device/emulator is attached.
baselineProfile { useConnectedDevices = true }

dependencies {
    implementation(libs.test.junit)
    implementation(libs.test.espresso)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
