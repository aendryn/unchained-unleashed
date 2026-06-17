package com.github.aendryn.unchainedunleashed.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's Baseline Profile: the classes/methods on the cold-start and first-list paths,
 * compiled ahead of time on install so the first launches and first scroll are noticeably faster.
 *
 * Run: `./gradlew :app:generateBaselineProfile` with a device/emulator attached (API 28+). The
 * result is written to `app/src/release/generated/baselineProfiles/` and bundled automatically.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() =
        baselineProfileRule.collect(
            packageName = "com.github.aendryn.unchainedunleashed",
            // Also emit a startup profile so the cold-start classes get an optimized dex layout.
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            // Let the first screen (accounts hub / lists) render and settle so its paths are profiled.
            device.waitForIdle()
        }
}
