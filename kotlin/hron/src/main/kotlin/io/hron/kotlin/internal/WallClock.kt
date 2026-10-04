package io.hron.kotlin.internal

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A wall time a fall-back repeats takes its first pass (spec/README.md, "DST fall-back (ambiguous
 * times)"), as [ZonedDateTime.of] resolves it.
 */
internal object WallClock {
    /**
     * A time in a spring-forward gap shifts forward by the gap's length (spec/README.md, "DST
     * spring-forward (gaps)"), as [ZonedDateTime.of] shifts it.
     */
    fun fixedTimeOn(date: LocalDate, time: LocalTime, zone: ZoneId): ZonedDateTime =
        ZonedDateTime.of(date.atTime(time), zone)

    /**
     * An interval slot on a date: where it sits in time, and its instant unless a spring-forward
     * gap skips it (spec/README.md, "Interval slots in a spring-forward gap"). A skipped slot sits
     * at the instant its gap ends, so keys never decrease in wall-clock order and one binary search
     * finds the slots on either side of an instant.
     */
    class Slot(val key: Instant, val instant: ZonedDateTime?)

    fun slotOn(date: LocalDate, minute: Int, zone: ZoneId): Slot {
        val wallTime = date.atTime(minute / 60, minute % 60)
        val rules = zone.rules
        val offsets = rules.getValidOffsets(wallTime)
        if (offsets.isEmpty()) return Slot(rules.getTransition(wallTime).instant, null)
        val instant = ZonedDateTime.ofLocal(wallTime, zone, offsets[0])
        return Slot(instant.toInstant(), instant)
    }
}
