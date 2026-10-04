package io.hron.kotlin

import io.hron.kotlin.spec.parseTimestamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

/**
 * Each case takes a value past 2147483647 through hron's arithmetic, where an Int would overflow.
 */
class LargeValueTest {
    private val now = parseTimestamp("2026-02-06T12:00:00+00:00[UTC]")

    @Test
    fun theFirstSupportedSecondIsReached() {
        val first = parseTimestamp("0001-01-02T00:00:00+00:00[UTC]")
        val schedule = Schedule.parse("every 1 min from 00:00 to 23:59")
        assertEquals(first, schedule.previousFrom(first.plusSeconds(30)))
        assertNull(schedule.previousFrom(first))
    }

    @Test
    fun theLastSupportedMinuteIsReached() {
        val last = parseTimestamp("9999-12-29T23:59:00+00:00[UTC]")
        val schedule = Schedule.parse("every 1 min from 00:00 to 23:59")
        assertEquals(last, schedule.nextFrom(last.minusMinutes(1)))
        assertTrue(schedule.matches(last.plusSeconds(59)))
        assertNull(schedule.nextFrom(last))
    }

    @ParameterizedTest
    @CsvSource(
        "every 2147483647 days at 00:00, 1970-01-01T00:00:00+00:00[UTC]",
        "every 2147483647 weeks on monday at 00:00, 1970-01-05T00:00:00+00:00[UTC]",
        "every 2147483647 months on the 1st at 00:00, 1970-01-01T00:00:00+00:00[UTC]",
        "every 2147483647 years on jan 1 at 00:00, 1970-01-01T00:00:00+00:00[UTC]",
    )
    fun theLargestIntervalFiresOnlyAtItsOrigin(expression: String, origin: String) {
        val schedule = Schedule.parse(expression)
        assertEquals(parseTimestamp(origin), schedule.previousFrom(now))
        assertNull(schedule.previousFrom(parseTimestamp(origin)))
        assertNull(schedule.nextFrom(now))
    }

    @ParameterizedTest
    @ValueSource(
        strings =
            [
                "every 2147483647 min from 00:00 to 23:59",
                "every 2147483647 hours from 00:00 to 23:59",
            ]
    )
    fun theLargestIntervalWithinADayFiresOnlyAtItsStart(expression: String) {
        val schedule = Schedule.parse(expression)
        assertEquals(parseTimestamp("2026-02-07T00:00:00+00:00[UTC]"), schedule.nextFrom(now))
        assertEquals("0 0 * * *", schedule.toCron())
    }

    @ParameterizedTest
    @ValueSource(strings = ["2147483648", "9999999999"])
    fun aTenDigitNumberAboveTheLimitIsALexError(number: String) {
        val error = assertFailsWith<HronException> { Schedule.parse("every $number days at 00:00") }
        assertEquals(ErrorKind.LEX, error.kind)
        assertEquals("number must be at most 2147483647", error.message)
        assertEquals(Span(6, 16), error.span)
    }
}
