package io.hron.kotlin.internal

import io.hron.kotlin.IntervalUnit
import io.hron.kotlin.ScheduleExpr
import io.hron.kotlin.TimeOfDay
import java.time.LocalTime

internal sealed interface DailyTimes {
    /**
     * How many dates past its scheduled date an occurrence can land: a gap pushes a fixed time
     * forward, and skips a slot.
     */
    val maxShiftDays: Long

    class Fixed(val times: List<LocalTime>) : DailyTimes {
        /** A fixed time shifted out of a gap before midnight lands on the next date. */
        override val maxShiftDays: Long = 1
    }

    /** An IntArray keeps the binary search over the slots free of boxing. */
    class Slots(val minutes: IntArray) : DailyTimes {
        override val maxShiftDays: Long = 0
    }

    companion object {
        fun of(expression: ScheduleExpr): DailyTimes =
            when (expression) {
                is ScheduleExpr.IntervalRepeat -> Slots(intervalSlots(expression))
                is ScheduleExpr.DayRepeat -> fixed(expression.times)
                is ScheduleExpr.WeekRepeat -> fixed(expression.times)
                is ScheduleExpr.MonthRepeat -> fixed(expression.times)
                is ScheduleExpr.SingleDate -> fixed(expression.times)
                is ScheduleExpr.YearRepeat -> fixed(expression.times)
            }

        private fun fixed(times: List<TimeOfDay>) =
            Fixed(times.map { LocalTime.of(it.hour, it.minute) })
    }
}

/**
 * The minutes of the day each slot falls on. The step is a Long, as 2147483647 hours in minutes
 * overflows an Int. toCron writes these slots too, so the two cannot disagree.
 */
internal fun intervalSlots(expression: ScheduleExpr.IntervalRepeat): IntArray {
    val minutesPerUnit = if (expression.unit == IntervalUnit.HOURS) 60L else 1L
    val step = expression.interval * minutesPerUnit
    val from = expression.from.minuteOfDay
    val to = expression.to.minuteOfDay
    return IntArray(((to - from) / step).toInt() + 1) { k -> (from + k * step).toInt() }
}
