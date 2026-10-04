package io.hron.kotlin.internal

import io.hron.kotlin.Schedule
import io.hron.kotlin.ScheduleExpr
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

internal class Search
private constructor(
    private val expression: ScheduleExpr,
    val zone: ZoneId,
    private val cadence: Cadence,
    private val times: DailyTimes,
    private val clauses: Clauses,
) {
    fun endOn(date: LocalDate): Search =
        Search(expression, zone, cadence, times, clauses.endOn(date))

    fun nearest(now: ZonedDateTime, direction: Direction): ZonedDateTime? {
        val local = now.withZoneSameInstant(zone)
        val nowDate = local.toLocalDate()
        val firstDate = clauses.clamp(nowDate, direction)
        // A nearest weekday or a DST shift can move an occurrence out of the period it is scheduled
        // in, so the search starts one period back.
        val firstPeriod = cadence.periodOf(firstDate) - direction.sign
        val reach = clauses.farthestExceptDate(direction)?.let(cadence::periodOf) ?: firstPeriod
        val shift = times.maxShiftDays
        var best: Occurrence? = null
        val starts = cadence.periodStarts(firstPeriod, reach, direction)
        search@ for (start in starts) {
            if (rejectsPeriod(start)) continue
            for (candidate in direction.inOrder(Candidate.inPeriod(expression, start))) {
                val found = best
                val beaten =
                    found != null &&
                        !Occurrence.couldBeat(candidate.date, found.landing, direction, shift)
                if (beaten || clauses.endsSearch(candidate.date, direction)) break@search
                if (
                    Occurrence.isBehind(candidate.date, nowDate, direction, shift) ||
                        !clauses.allows(candidate)
                ) {
                    continue
                }
                val time = nearestOnDate(candidate.date, local, direction) ?: continue
                if (found == null || direction.precedes(time, found.time)) {
                    best = Occurrence(time, time.toLocalDate())
                }
            }
        }
        return best?.time?.takeIf(Evaluator::inSupportedRange)
    }

    /**
     * A day or month period's candidates all target its own month, so one whose month `during`
     * rejects holds nothing.
     */
    private fun rejectsPeriod(start: LocalDate): Boolean =
        (cadence.period == Cadence.Period.DAY || cadence.period == Cadence.Period.MONTH) &&
            !clauses.allowsMonth(start.month)

    private fun nearestOnDate(date: LocalDate, now: ZonedDateTime, direction: Direction) =
        when (times) {
            is DailyTimes.Fixed -> nearestFixedTime(times.times, date, now, direction)
            is DailyTimes.Slots -> nearestSlot(times.minutes, date, now, direction)
        }

    /** A time shifted out of a gap can land after a later wall time, so every time is compared. */
    private fun nearestFixedTime(
        fixed: List<LocalTime>,
        date: LocalDate,
        now: ZonedDateTime,
        direction: Direction,
    ): ZonedDateTime? {
        var nearest: ZonedDateTime? = null
        for (time in fixed) {
            val instant = WallClock.fixedTimeOn(date, time, zone)
            if (
                direction.precedes(now, instant) &&
                    (nearest == null || direction.precedes(instant, nearest))
            ) {
                nearest = instant
            }
        }
        return nearest
    }

    /**
     * A date's slot keys never decrease in wall-clock order ([WallClock.Slot]), so one binary
     * search finds where `now` falls among them; a scan in [direction] then steps past slots a gap
     * skips.
     */
    private fun nearestSlot(
        slots: IntArray,
        date: LocalDate,
        now: ZonedDateTime,
        direction: Direction,
    ): ZonedDateTime? {
        val target = now.toInstant()
        // low becomes the first slot of the upper part: forward, the slots after now; backward, the
        // slots at or after it.
        var low = 0
        var high = slots.size
        while (low < high) {
            val mid = (low + high) ushr 1
            val key = WallClock.slotOn(date, slots[mid], zone).key
            val inUpperPart = if (direction == Direction.FORWARD) key > target else key >= target
            if (inUpperPart) high = mid else low = mid + 1
        }
        val step = direction.sign.toInt()
        var i = if (direction == Direction.FORWARD) low else low - 1
        while (i in slots.indices) {
            WallClock.slotOn(date, slots[i], zone).time?.let {
                return it
            }
            i += step
        }
        return null
    }

    companion object {
        fun of(schedule: Schedule): Search {
            val starting = schedule.starting?.let(LocalDate::parse)
            val expression = schedule.expression
            return Search(
                expression,
                ZoneId.of(schedule.timezone ?: "UTC"),
                Cadence.of(expression, starting),
                DailyTimes.of(expression),
                Clauses.of(schedule, starting),
            )
        }
    }
}
