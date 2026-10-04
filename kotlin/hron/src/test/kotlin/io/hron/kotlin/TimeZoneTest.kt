package io.hron.kotlin

import io.hron.kotlin.spec.parseTimestamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

/** spec/README.md, "Parse-time validation": names match in any case and keep the IANA spelling. */
class TimeZoneTest {
    @ParameterizedTest
    @CsvSource(
        "America/New_York, America/New_York",
        "america/new_york, America/New_York",
        "AMERICA/NEW_YORK, America/New_York",
        "us/eastern, US/Eastern",
        "US/Eastern, US/Eastern",
        "Etc/GMT+5, Etc/GMT+5",
        "etc/gmt+5, Etc/GMT+5",
        "UTC, UTC",
        "utc, UTC",
        "Etc/UTC, Etc/UTC",
        "asia/kolkata, Asia/Kolkata",
        "Asia/Calcutta, Asia/Calcutta",
    )
    fun acceptsInAnyCase(written: String, identifier: String) {
        val schedule = Schedule.parse("every day at 09:00 in $written")
        assertEquals(identifier, schedule.timezone)
        assertEquals("every day at 09:00 in $identifier", schedule.toString())
    }

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "EST",
                "GMT",
                "Z",
                "+05:30",
                "UTC+01:00",
                "Factory",
                "Nope/Zone",
                "Etc/Unknown",
                "SystemV/EST5",
                "systemv/est5",
                "posix/America/New_York",
                "right/UTC",
                "Europe/İstanbul",
                "Etc/GMT+５",
                "Kiev/Europe",
                "America/New_Yorḱ",
            ]
    )
    fun rejects(name: String) {
        assertFailsWith<HronException> { Schedule.parse("every day at 09:00 in $name") }
    }

    @Test
    fun theNamedZoneComputesTheOffset() {
        val schedule = Schedule.parse("every day at 09:00 in Etc/GMT+5")
        assertEquals(
            parseTimestamp("2026-07-01T09:00:00-05:00[Etc/GMT+5]"),
            schedule.nextFrom(parseTimestamp("2026-07-01T00:00:00+00:00[UTC]")),
        )
    }

    /**
     * Monrovia kept -00:44:30 until 1972; spec/README.md, "Timezone data", leaves such offsets out,
     * but java.time keeps them to the second, so 09:00 is 09:44:30Z.
     */
    @Test
    fun subMinuteOffsetsAreExact() {
        val schedule = Schedule.parse("every day at 09:00 in Africa/Monrovia")
        val next = schedule.nextFrom(parseTimestamp("1971-06-01T00:00:00+00:00[UTC]"))
        assertEquals(
            parseTimestamp("1971-06-01T09:44:30+00:00[UTC]").toInstant(),
            next?.toInstant(),
        )
        assertTrue(schedule.matches(parseTimestamp("1971-06-01T09:44:30+00:00[UTC]")))
        assertTrue(schedule.matches(parseTimestamp("1971-06-01T09:45:29+00:00[UTC]")))
        assertFalse(schedule.matches(parseTimestamp("1971-06-01T09:44:29+00:00[UTC]")))
        assertFalse(schedule.matches(parseTimestamp("1971-06-01T09:45:30+00:00[UTC]")))
    }
}
