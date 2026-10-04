package io.hron.kotlin.internal

import io.hron.kotlin.DateSpec
import io.hron.kotlin.DayFilter
import io.hron.kotlin.DayOfMonthSpec
import io.hron.kotlin.ExceptionSpec
import io.hron.kotlin.IntervalUnit
import io.hron.kotlin.MonthTarget
import io.hron.kotlin.NearestDirection
import io.hron.kotlin.Schedule
import io.hron.kotlin.ScheduleExpr
import io.hron.kotlin.TimeOfDay
import io.hron.kotlin.UntilSpec
import io.hron.kotlin.Weekday
import io.hron.kotlin.YearTarget

internal object Display {
    fun render(schedule: Schedule): String = buildString {
        append(expression(schedule.expression))
        if (schedule.except.isNotEmpty()) {
            append(" except ")
            append(schedule.except.joinToString(", ", transform = ::exception))
        }
        schedule.until?.let { append(" until ").append(until(it)) }
        schedule.starting?.let { append(" starting ").append(it) }
        if (schedule.during.isNotEmpty()) {
            append(" during ")
            append(schedule.during.joinToString(", ") { it.short })
        }
        schedule.timezone?.let { append(" in ").append(it) }
    }

    private fun expression(expression: ScheduleExpr): String =
        when (expression) {
            is ScheduleExpr.DayRepeat ->
                if (expression.interval > 1) {
                    "every ${expression.interval} days at ${times(expression.times)}"
                } else {
                    "every ${dayFilter(expression.days)} at ${times(expression.times)}"
                }
            is ScheduleExpr.IntervalRepeat -> {
                val window =
                    "every ${expression.interval} ${unit(expression.unit, expression.interval)}" +
                        " from ${expression.from} to ${expression.to}"
                val days = expression.dayFilter
                if (days == null) window else "$window on ${dayFilter(days)}"
            }
            is ScheduleExpr.WeekRepeat -> {
                val every =
                    if (expression.interval > 1) "every ${expression.interval} weeks"
                    else "every week"
                "$every on ${days(expression.days)} at ${times(expression.times)}"
            }
            is ScheduleExpr.MonthRepeat -> {
                val every =
                    if (expression.interval > 1) "every ${expression.interval} months"
                    else "every month"
                "$every on the ${monthTarget(expression.target)} at ${times(expression.times)}"
            }
            is ScheduleExpr.SingleDate ->
                "on ${date(expression.date)} at ${times(expression.times)}"
            is ScheduleExpr.YearRepeat -> {
                val every =
                    if (expression.interval > 1) "every ${expression.interval} years"
                    else "every year"
                "$every on ${yearTarget(expression.target)} at ${times(expression.times)}"
            }
        }

    private fun unit(unit: IntervalUnit, interval: Int): String =
        when (unit) {
            IntervalUnit.MINUTES -> if (interval == 1) "minute" else "min"
            IntervalUnit.HOURS -> if (interval == 1) "hour" else "hours"
        }

    private fun dayFilter(filter: DayFilter): String =
        when (filter) {
            DayFilter.Every -> "day"
            DayFilter.Weekdays -> "weekday"
            DayFilter.Weekends -> "weekend"
            is DayFilter.Days -> days(filter.days)
        }

    private fun monthTarget(target: MonthTarget): String =
        when (target) {
            MonthTarget.LastDay -> "last day"
            MonthTarget.LastWeekday -> "last weekday"
            is MonthTarget.Days -> target.specs.joinToString(", ", transform = ::dayOfMonth)
            is MonthTarget.NearestWeekday -> {
                val direction =
                    when (target.direction) {
                        NearestDirection.NEXT -> "next "
                        NearestDirection.PREVIOUS -> "previous "
                        null -> ""
                    }
                "${direction}nearest weekday to ${ordinalNumber(target.day)}"
            }
            is MonthTarget.OrdinalWeekday -> "${target.ordinal.word} ${target.weekday.word}"
        }

    private fun yearTarget(target: YearTarget): String =
        when (target) {
            is YearTarget.Date -> "${target.month.short} ${target.day}"
            is YearTarget.OrdinalWeekday ->
                "the ${target.ordinal.word} ${target.weekday.word} of ${target.month.short}"
            is YearTarget.DayOfMonth -> "the ${ordinalNumber(target.day)} of ${target.month.short}"
            is YearTarget.LastWeekday -> "the last weekday of ${target.month.short}"
        }

    private fun date(date: DateSpec): String =
        when (date) {
            is DateSpec.Named -> "${date.month.short} ${date.day}"
            is DateSpec.Iso -> date.date
        }

    private fun exception(exception: ExceptionSpec): String =
        when (exception) {
            is ExceptionSpec.Named -> "${exception.month.short} ${exception.day}"
            is ExceptionSpec.Iso -> exception.date
        }

    private fun until(until: UntilSpec): String =
        when (until) {
            is UntilSpec.Iso -> until.date
            is UntilSpec.Named -> "${until.month.short} ${until.day}"
        }

    private fun dayOfMonth(spec: DayOfMonthSpec): String =
        when (spec) {
            is DayOfMonthSpec.Single -> ordinalNumber(spec.day)
            is DayOfMonthSpec.Range -> "${ordinalNumber(spec.start)} to ${ordinalNumber(spec.end)}"
        }

    private fun times(times: List<TimeOfDay>): String = times.joinToString(", ")

    private fun days(days: List<Weekday>): String = days.joinToString(", ") { it.word }

    private fun ordinalNumber(n: Int): String {
        val suffix =
            when {
                n % 100 in 11..13 -> "th"
                n % 10 == 1 -> "st"
                n % 10 == 2 -> "nd"
                n % 10 == 3 -> "rd"
                else -> "th"
            }
        return "$n$suffix"
    }
}
