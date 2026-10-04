package io.hron.kotlin.android

import android.icu.util.TimeZone
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.hron.kotlin.spec.SpecCase
import io.hron.kotlin.spec.SpecCases
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every case of spec/tests.json on the device, with its own tz data and java.time. JUnit 4 has no
 * dynamic tests, so each test runs a section's cases and lists the ones that fail.
 */
@RunWith(AndroidJUnit4::class)
class SpecOnDeviceTest {
    private val spec =
        SpecCases(
            InstrumentationRegistry.getInstrumentation().context.assets.open("tests.json").use {
                it.readBytes().decodeToString()
            }
        )

    @Test fun parse() = runAll(spec.parse())

    @Test fun parseErrors() = runAll(spec.parseErrors())

    @Test fun knownSections() = runAll(spec.knownSections())

    @Test fun eval() = runAll(spec.eval())

    @Test fun cron() = runAll(spec.cron())

    @Test fun invariants() = runAll(spec.invariants())

    /**
     * tzdata 2023c gave Greenland's America/Nuuk its current rules, which the spec's Nuuk cases
     * assume. Phones get newer tz data from Google Play, but an emulator image keeps the data it
     * shipped with, as Android 12's predates 2023c. With older data only those cases may fail.
     */
    private fun dependsOnOlderTzData(case: SpecCase): Boolean =
        TimeZone.getTZDataVersion() < "2023c" && case.expression?.contains("America/Nuuk") == true

    private fun runAll(cases: List<SpecCase>) {
        // JUnit 4 would pass a section with no cases, as one that failed to load.
        assertTrue(cases.isNotEmpty(), "the section has no cases")
        val failures = cases.mapNotNull { case ->
            val error = runCatching(case.check).exceptionOrNull()
            if (error == null || dependsOnOlderTzData(case)) null
            else "${case.name}: ${error.message}"
        }
        assertEquals(
            emptyList(),
            failures.take(20),
            "${failures.size} of ${cases.size} cases failed, with tzdata ${TimeZone.getTZDataVersion()}",
        )
    }
}
