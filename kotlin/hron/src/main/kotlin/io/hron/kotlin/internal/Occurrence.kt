package io.hron.kotlin.internal

import io.hron.kotlin.DateSpec
import io.hron.kotlin.ScheduleExpr
import io.hron.kotlin.Weekday
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * A date the expression fires on, with the month whose day it names. They differ only when a
 * directional nearest weekday crosses into the adjacent month.
 */
internal data class Candidate(val date: LocalDate, val targetMonth: Month = date.month) {
    companion object {
        fun inPeriod(expression: ScheduleExpr, start: LocalDate): List<Candidate> =
            when (expression) {
                is ScheduleExpr.IntervalRepeat -> {
                    val days = expression.dayFilter
                    if (days == null || CalendarDates.matches(start, days)) {
                        listOf(Candidate(start))
                    } else {
                        emptyList()
                    }
                }
                is ScheduleExpr.DayRepeat ->
                    if (CalendarDates.matches(start, expression.days)) {
                        listOf(Candidate(start))
                    } else {
                        emptyList()
                    }
                is ScheduleExpr.WeekRepeat ->
                    Weekday.entries
                        .filter { it in expression.days }
                        .map { Candidate(start.plusDays(it.ordinal.toLong())) }
                is ScheduleExpr.MonthRepeat ->
                    CalendarDates.monthTargetDates(YearMonth.from(start), expression.target).map {
                        Candidate(it, start.month)
                    }
                is ScheduleExpr.YearRepeat ->
                    listOfNotNull(
                        CalendarDates.yearTargetDate(start.year, expression.target)
                            ?.let(::Candidate)
                    )
                is ScheduleExpr.SingleDate ->
                    when (val date = expression.date) {
                        is DateSpec.Named ->
                            listOfNotNull(
                                CalendarDates.dateOf(start.year, date.month.number, date.day)
                                    ?.let(::Candidate)
                            )
                        is DateSpec.Iso -> listOf(Candidate(start))
                    }
            }
    }
}

/**
 * [couldBeat] and [isBehind] rest on one fact: an occurrence lands on a first pass, from its
 * scheduled date to `shift` dates after it ([DailyTimes.maxShiftDays]), and first passes keep
 * wall-clock order.
 */
internal data class Occurrence(val instant: ZonedDateTime, val landing: LocalDate) {
    companion object {
        /**
         * How many dates behind a date that has begun now's wall date can read: from the second
         * pass of a fall-back overlap that crosses midnight, one.
         */
        private const val MAX_OVERLAP_DAYS = 1L

        fun couldBeat(date: LocalDate, landing: LocalDate, direction: Direction, shift: Long) =
            when (direction) {
                Direction.FORWARD -> date <= landing
                Direction.BACKWARD -> ChronoUnit.DAYS.between(date, landing) <= shift
            }

        fun isBehind(date: LocalDate, nowDate: LocalDate, direction: Direction, shift: Long) =
            when (direction) {
                Direction.FORWARD -> ChronoUnit.DAYS.between(date, nowDate) > shift
                Direction.BACKWARD -> ChronoUnit.DAYS.between(nowDate, date) > MAX_OVERLAP_DAYS
            }
    }
}
