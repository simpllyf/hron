package io.hron.kotlin

import io.hron.kotlin.spec.parseTimestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ScheduleTest {
    private val everyMinute = Schedule.parse("every 1 min from 00:00 to 23:59")
    private val daily = Schedule.parse("every day at 09:00")
    private val epoch = Instant.EPOCH.atZone(ZoneId.of("UTC"))

    @ParameterizedTest
    @ValueSource(ints = [0, -1, Int.MIN_VALUE])
    fun nextOfNoneOrFewerIsEmpty(n: Int) {
        assertEquals(emptyList(), daily.nextNFrom(epoch, n))
    }

    /**
     * `n` only caps the count, so no room is reserved for it (spec/README.md, "Timestamps and
     * counts").
     */
    @Test
    fun nextOfAHugeCountReturnsEveryOccurrenceAtOnce() {
        val schedule = Schedule.parse("every year on jan 1 at 00:00 starting 9990-01-01")
        val all = schedule.nextNFrom(epoch, Int.MAX_VALUE)
        assertEquals(10, all.size)
        assertEquals(parseTimestamp("9999-01-01T00:00:00+00:00[UTC]"), all.last())
    }

    @Test
    fun aTimestampOutsideTheSupportedRangeHasNoOccurrences() {
        val inRange = parseTimestamp("2026-02-06T12:00:00+00:00[UTC]")
        val outside =
            listOf(
                ZonedDateTime.of(LocalDateTime.MIN, ZoneOffset.MAX),
                ZonedDateTime.of(LocalDateTime.MAX, ZoneOffset.MIN),
                parseTimestamp("0001-01-01T23:59:59+00:00[UTC]"),
                parseTimestamp("9999-12-30T00:00:00+00:00[UTC]"),
            )
        for (t in outside) {
            assertNull(everyMinute.nextFrom(t), "$t")
            assertNull(everyMinute.previousFrom(t), "$t")
            assertFalse(everyMinute.matches(t), "$t")
            assertEquals(emptyList(), everyMinute.nextNFrom(t, 3), "$t")
            assertEquals(emptyList(), everyMinute.occurrences(t).take(3).toList(), "$t")
            assertEquals(emptyList(), everyMinute.between(t, inRange).toList(), "$t")
            assertEquals(emptyList(), everyMinute.between(inRange, t).take(3).toList(), "$t")
        }
    }

    @Test
    fun theSupportedRangeIncludesItsStart() {
        val start = parseTimestamp("0001-01-02T00:00:00+00:00[UTC]")
        assertTrue(everyMinute.matches(start))
        assertEquals(start.plusMinutes(1), everyMinute.nextFrom(start))
    }

    @Test
    fun aFractionOfASecondCounts() {
        val justBeforeEpoch = epoch.minusNanos(1)
        assertEquals(epoch, everyMinute.nextFrom(justBeforeEpoch))
        assertEquals(epoch.minusMinutes(1), everyMinute.previousFrom(justBeforeEpoch))
        assertTrue(everyMinute.matches(justBeforeEpoch))
        assertEquals(epoch, everyMinute.previousFrom(epoch.plusNanos(1)))
        assertEquals(epoch.plusMinutes(1), everyMinute.nextFrom(epoch.plusNanos(1)))
    }

    @Test
    fun matchesTheWholeMinuteOnTheWallClock() {
        assertTrue(daily.matches(parseTimestamp("2026-02-06T09:00:30+00:00[UTC]")))
        assertFalse(daily.matches(parseTimestamp("2026-02-06T09:01:30+00:00[UTC]")))
        assertFalse(daily.matches(parseTimestamp("2026-02-06T08:59:59+00:00[UTC]")))
    }

    @Test
    fun onlyTheInstantOfAnArgumentMatters() {
        val tokyo = parseTimestamp("2026-02-06T21:00:00+09:00[Asia/Tokyo]")
        val utc = parseTimestamp("2026-02-06T12:00:00+00:00[UTC]")
        assertEquals(daily.nextFrom(utc), daily.nextFrom(tokyo))
        assertEquals(ZoneId.of("UTC"), daily.nextFrom(tokyo)?.zone)
    }

    @Test
    fun resultsAreInTheSchedulesZone() {
        val schedule = Schedule.parse("every day at 09:00 in asia/tokyo")
        assertEquals(
            parseTimestamp("1970-01-02T09:00:00+09:00[Asia/Tokyo]"),
            schedule.nextFrom(epoch),
        )
        assertEquals(ZoneId.of("Asia/Tokyo"), schedule.nextFrom(epoch)?.zone)
    }

    @Test
    fun occurrencesEndAtUntilOrTheEndOfTheSupportedRange() {
        val until = Schedule.parse("every day at 09:00 until 2026-02-10")
        assertEquals(4, until.occurrences(parseTimestamp("2026-02-06T12:00:00+00:00[UTC]")).count())
        val late = Schedule.parse("every year on jan 1 at 00:00")
        assertEquals(9, late.occurrences(parseTimestamp("9990-06-01T00:00:00+00:00[UTC]")).count())
    }

    @Test
    fun betweenIncludesTheEndButNotTheStart() {
        val from = parseTimestamp("2026-02-06T09:00:00+00:00[UTC]")
        val to = parseTimestamp("2026-02-08T09:00:00+00:00[UTC]")
        assertEquals(
            listOf(parseTimestamp("2026-02-07T09:00:00+00:00[UTC]"), to),
            daily.between(from, to).toList(),
        )
        assertEquals(emptyList(), daily.between(to, from).toList())
    }

    @Test
    fun iteratingTwiceGivesTheSameOccurrences() {
        val occurrences = daily.occurrences(epoch).take(3)
        assertEquals(occurrences.toList(), occurrences.toList())
    }

    @Test
    fun betweenIsLazyAndCanBeIteratedTwice() {
        var computed = 0
        val window = daily.between(epoch, epoch.plusDays(10)).onEach { computed++ }
        assertEquals(0, computed)
        assertEquals(window.take(2).toList(), window.take(2).toList())
        assertEquals(4, computed)
    }

    @Test
    fun occurrencesAreComputedOnlyWhenAsked() {
        var computed = 0
        val occurrences = daily.occurrences(epoch).onEach { computed++ }
        assertEquals(0, computed)
        occurrences.take(2).toList()
        assertEquals(2, computed)
    }

    @Test
    fun toCronLeavesTheZoneOut() {
        assertEquals("0 9 * * *", Schedule.parse("every day at 09:00 in America/New_York").toCron())
    }
}
