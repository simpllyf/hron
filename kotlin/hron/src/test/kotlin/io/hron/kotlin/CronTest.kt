package io.hron.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Timeout

/** Cron fields past what spec/tests.json can hold: digit runs and lists of any length. */
class CronTest {
    private fun fromCron(cron: String): String = Schedule.fromCron(cron).toString()

    private fun fromCronError(cron: String): String {
        val error = assertFailsWith<HronException> { Schedule.fromCron(cron) }
        assertEquals(ErrorKind.CRON, error.kind)
        return error.message
    }

    @Test
    fun valuesOfAnyLengthNeverOverflow() {
        val longZeros = "0".repeat(10_000)
        assertEquals("every day at 09:09", fromCron("${longZeros}9 ${longZeros}9 * * *"))
        val huge = "9".repeat(10_000)
        assertEquals("day of week ordinal must be 1-5, got $huge", fromCronError("0 9 * * 1#$huge"))
        assertEquals("day of month must be 1-31, got $huge", fromCronError("0 9 ${huge}W * *"))
        assertEquals("hour must be 0-23, got $huge", fromCronError("0 $huge-1 * * *"))
        assertEquals("every monday at 09:00", fromCron("0 9 * * 1-5/$huge"))
        assertEquals("day of week step must be at least 1", fromCronError("0 9 * * */$longZeros"))
        assertEquals("every sunday at 09:00", fromCron("0 9 * * 0-7/${longZeros}7"))
    }

    /** A quadratic scan of the values seen so far would take minutes here. */
    @Test
    @Timeout(10)
    fun aLongFieldIsParsedInLinearTime() {
        val items = List(200_000) { "1" }.joinToString(",")
        assertEquals("every month on the 1st at 09:00", fromCron("0 9 $items * *"))
        val ranges = List(50_000) { "0-59/1" }.joinToString(",")
        assertEquals("every 1 minute from 09:00 to 09:59", fromCron("$ranges 9 * * *"))
    }

    @Test
    fun sevenMinuteStepsConvertOnlyWithinOneHour() {
        assertEquals("every 7 min from 09:00 to 09:56", fromCron("*/7 9 * * *"))
        assertEquals(
            "not expressible in hron: 216 times a day are too many to list",
            fromCronError("*/7 * * * *"),
        )
    }
}
