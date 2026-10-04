package io.hron.kotlin.internal

import io.hron.kotlin.Schedule
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

internal object Evaluator {
    /** spec/README.md, "Supported range": from RANGE_START inclusive to RANGE_END exclusive. */
    private val RANGE_START = Instant.parse("0001-01-02T00:00:00Z")

    private val RANGE_END = Instant.parse("9999-12-30T00:00:00Z")

    fun nextFrom(schedule: Schedule, now: ZonedDateTime): ZonedDateTime? =
        search(schedule, now, Direction.FORWARD)

    fun previousFrom(schedule: Schedule, now: ZonedDateTime): ZonedDateTime? =
        search(schedule, now, Direction.BACKWARD)

    fun nextNFrom(schedule: Schedule, now: ZonedDateTime, n: Int): List<ZonedDateTime> =
        occurrences(schedule, now).take(maxOf(n, 0)).toList()

    fun occurrences(schedule: Schedule, from: ZonedDateTime): Sequence<ZonedDateTime> {
        if (!inSupportedRange(from)) return emptySequence()
        val search = Search.of(schedule)
        return generateSequence({ search.nearest(from, Direction.FORWARD) }) {
            search.nearest(it, Direction.FORWARD)
        }
    }

    fun between(
        schedule: Schedule,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): Sequence<ZonedDateTime> {
        if (!inSupportedRange(to)) return emptySequence()
        return occurrences(schedule, from).takeWhile { !it.isAfter(to) }
    }

    /**
     * Defined through the forward search, so the two can never disagree about what an occurrence is
     * (spec/README.md, "matches is true exactly when the minute containing t is an occurrence").
     */
    fun matches(schedule: Schedule, datetime: ZonedDateTime): Boolean {
        if (!inSupportedRange(datetime)) return false
        val search = Search.of(schedule)
        val minute = datetime.withZoneSameInstant(search.zone).truncatedTo(ChronoUnit.MINUTES)
        // An occurrence never lands before the date it is scheduled on, so one at this minute is
        // scheduled on or before the minute's wall date.
        val next =
            search.endOn(minute.toLocalDate()).nearest(minute.minusNanos(1), Direction.FORWARD)
        return next != null && next.isEqual(minute)
    }

    fun inSupportedRange(t: ZonedDateTime): Boolean {
        val instant = t.toInstant()
        return instant >= RANGE_START && instant < RANGE_END
    }

    private fun search(
        schedule: Schedule,
        now: ZonedDateTime,
        direction: Direction,
    ): ZonedDateTime? {
        if (!inSupportedRange(now)) return null
        return Search.of(schedule).nearest(now, direction)
    }
}
