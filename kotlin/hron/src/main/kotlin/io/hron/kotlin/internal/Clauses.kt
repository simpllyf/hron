package io.hron.kotlin.internal

import io.hron.kotlin.ExceptionSpec
import io.hron.kotlin.Schedule
import io.hron.kotlin.UntilSpec
import java.time.LocalDate
import java.time.Month
import java.time.MonthDay
import java.util.EnumSet
import java.util.TreeSet

/**
 * The trailing clauses, resolved once. `during` applies to a candidate's target month; `except`,
 * `until` and `starting` to its date (spec/README.md, "Nearest weekday and `during`", "The
 * `starting` clause").
 */
internal data class Clauses(
    val during: Set<Month>,
    val exceptMonthDays: Set<MonthDay>,
    val exceptDates: TreeSet<LocalDate>,
    val until: LocalDate?,
    val starting: LocalDate?,
) {
    fun allows(candidate: Candidate): Boolean {
        val date = candidate.date
        return allowsMonth(candidate.targetMonth) &&
            (exceptMonthDays.isEmpty() || MonthDay.from(date) !in exceptMonthDays) &&
            date !in exceptDates &&
            (until == null || date <= until) &&
            (starting == null || date >= starting)
    }

    fun allowsMonth(month: Month): Boolean = during.isEmpty() || month in during

    fun endOn(date: LocalDate): Clauses =
        copy(until = if (until == null || date < until) date else until)

    /**
     * The one-off except date farthest along [direction]: the calendar repeats only beyond it
     * (spec/README.md, "Search horizon").
     */
    fun farthestExceptDate(direction: Direction): LocalDate? =
        when {
            exceptDates.isEmpty() -> null
            direction == Direction.FORWARD -> exceptDates.last()
            else -> exceptDates.first()
        }

    fun clamp(date: LocalDate, direction: Direction): LocalDate {
        val bound = if (direction == Direction.FORWARD) starting else until
        return if (bound != null && direction.precedes(date, bound)) bound else date
    }

    fun endsSearch(date: LocalDate, direction: Direction): Boolean {
        val bound = if (direction == Direction.FORWARD) until else starting
        return bound != null && direction.precedes(bound, date)
    }

    companion object {
        /** Feb 29 can be eight years away, as from 2096-03-01 to 2104-02-29. */
        private const val NAMED_UNTIL_MAX_YEARS = 8

        fun of(schedule: Schedule, starting: LocalDate?): Clauses {
            val during = EnumSet.noneOf(Month::class.java)
            schedule.during.mapTo(during) { it.month }
            val exceptMonthDays = HashSet<MonthDay>()
            val exceptDates = TreeSet<LocalDate>()
            for (exception in schedule.except) {
                when (exception) {
                    is ExceptionSpec.Named ->
                        exceptMonthDays += MonthDay.of(exception.month.number, exception.day)
                    is ExceptionSpec.Iso -> exceptDates += LocalDate.parse(exception.date)
                }
            }
            val until = schedule.until?.let { resolveUntil(it, starting) }
            return Clauses(during, exceptMonthDays, exceptDates, until, starting)
        }

        /**
         * A named until date is the first such date on or after the starting date (spec/README.md,
         * "Named `until`"), which a named until always has.
         */
        private fun resolveUntil(until: UntilSpec, starting: LocalDate?): LocalDate? =
            when (until) {
                is UntilSpec.Iso -> LocalDate.parse(until.date)
                is UntilSpec.Named -> {
                    val from =
                        checkNotNull(starting) { "parse rejects a named until without starting" }
                    (0..NAMED_UNTIL_MAX_YEARS).firstNotNullOfOrNull { k ->
                        CalendarDates.dateOf(from.year + k, until.month.number, until.day)?.takeIf {
                            it >= from
                        }
                    }
                }
            }
    }
}
