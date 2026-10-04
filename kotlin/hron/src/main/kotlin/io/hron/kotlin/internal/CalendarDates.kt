package io.hron.kotlin.internal

import io.hron.kotlin.DayFilter
import io.hron.kotlin.DayOfMonthSpec
import io.hron.kotlin.MonthTarget
import io.hron.kotlin.NearestDirection
import io.hron.kotlin.OrdinalPosition
import io.hron.kotlin.Weekday
import io.hron.kotlin.YearTarget
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

internal object CalendarDates {
    fun mondayOf(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun dateOf(year: Int, month: Int, day: Int): LocalDate? {
        val yearMonth = YearMonth.of(year, month)
        return if (yearMonth.isValidDay(day)) yearMonth.atDay(day) else null
    }

    fun matches(date: LocalDate, filter: DayFilter): Boolean =
        when (filter) {
            DayFilter.Every -> true
            DayFilter.Weekdays -> !isWeekend(date)
            DayFilter.Weekends -> isWeekend(date)
            is DayFilter.Days -> date.dayOfWeek.weekday in filter.days
        }

    private fun isWeekend(date: LocalDate): Boolean =
        date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY

    fun monthTargetDates(month: YearMonth, target: MonthTarget): List<LocalDate> =
        when (target) {
            is MonthTarget.Days -> daysOfMonth(month, target.specs)
            MonthTarget.LastDay -> listOf(month.atEndOfMonth())
            MonthTarget.LastWeekday -> listOf(lastWeekdayOfMonth(month))
            is MonthTarget.NearestWeekday ->
                listOfNotNull(nearestWeekday(month, target.day, target.direction))
            is MonthTarget.OrdinalWeekday ->
                listOfNotNull(ordinalWeekday(month, target.ordinal, target.weekday))
        }

    /** In date order, each day once, however the specs list or overlap them. */
    private fun daysOfMonth(month: YearMonth, specs: List<DayOfMonthSpec>): List<LocalDate> {
        val named = BooleanArray(32)
        for (spec in specs) {
            val days =
                when (spec) {
                    is DayOfMonthSpec.Single -> spec.day..spec.day
                    is DayOfMonthSpec.Range -> spec.start..spec.end
                }
            for (day in days) named[day] = true
        }
        return (1..month.lengthOfMonth()).filter { named[it] }.map(month::atDay)
    }

    fun yearTargetDate(year: Int, target: YearTarget): LocalDate? {
        val month = YearMonth.of(year, target.month.number)
        return when (target) {
            is YearTarget.Date -> dateOf(year, target.month.number, target.day)
            is YearTarget.DayOfMonth -> dateOf(year, target.month.number, target.day)
            is YearTarget.OrdinalWeekday -> ordinalWeekday(month, target.ordinal, target.weekday)
            is YearTarget.LastWeekday -> lastWeekdayOfMonth(month)
        }
    }

    private fun lastWeekdayOfMonth(month: YearMonth): LocalDate {
        val last = month.atEndOfMonth()
        val back =
            when (last.dayOfWeek) {
                DayOfWeek.SATURDAY -> 1L
                DayOfWeek.SUNDAY -> 2L
                else -> 0L
            }
        return last.minusDays(back)
    }

    private fun ordinalWeekday(
        month: YearMonth,
        ordinal: OrdinalPosition,
        weekday: Weekday,
    ): LocalDate? {
        val date =
            month.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(ordinal.n, weekday.dayOfWeek))
        return if (YearMonth.from(date) == month) date else null
    }

    /**
     * Without a direction it stays in the month, as cron's `W` does; with one it can cross into the
     * adjacent month (spec/README.md, "Nearest weekday and `during`").
     */
    private fun nearestWeekday(month: YearMonth, day: Int, toward: NearestDirection?): LocalDate? {
        if (!month.isValidDay(day)) return null
        val date = month.atDay(day)
        val shift =
            when (date.dayOfWeek) {
                DayOfWeek.SATURDAY ->
                    when (toward) {
                        NearestDirection.NEXT -> 2L
                        NearestDirection.PREVIOUS -> -1L
                        null -> if (day == 1) 2L else -1L
                    }
                DayOfWeek.SUNDAY ->
                    when (toward) {
                        NearestDirection.NEXT -> 1L
                        NearestDirection.PREVIOUS -> -2L
                        null -> if (day == month.lengthOfMonth()) -2L else 1L
                    }
                else -> 0L
            }
        return date.plusDays(shift)
    }
}
