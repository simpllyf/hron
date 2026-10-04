package io.hron.kotlin.internal

import io.hron.kotlin.DateSpec
import io.hron.kotlin.ScheduleExpr
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

internal class Cadence(
    val period: Period,
    private val origin: LocalDate,
    private val interval: Long,
    private val single: Boolean,
) {
    /** [per400Years] periods in 400 years, after which the proleptic Gregorian calendar repeats. */
    enum class Period(val per400Years: Long) {
        DAY(146_097),
        WEEK(20_871),
        MONTH(4_800),
        YEAR(400),
    }

    fun periodOf(date: LocalDate): Long =
        when (period) {
            Period.DAY -> ChronoUnit.DAYS.between(origin, date)
            Period.WEEK -> Math.floorDiv(ChronoUnit.DAYS.between(origin, date), 7L)
            Period.MONTH -> ChronoUnit.MONTHS.between(YearMonth.from(origin), YearMonth.from(date))
            Period.YEAR -> (date.year - origin.year).toLong()
        }

    private fun startOf(k: Long): LocalDate =
        when (period) {
            Period.DAY -> origin.plusDays(k)
            Period.WEEK -> origin.plusWeeks(k)
            Period.MONTH -> origin.plusMonths(k)
            Period.YEAR -> origin.plusYears(k)
        }

    /**
     * spec/README.md, "Search horizon". The periods' dropWhile(not in calendar), then takeWhile(in
     * calendar), without the cost a Sequence pipeline adds to every search.
     */
    fun periodStarts(firstPeriod: Long, reach: Long, direction: Direction): Iterator<LocalDate> {
        if (single) return listOf(origin).iterator()
        val first = align(firstPeriod, direction)
        val beyond = direction.sign * (align(reach, direction) - first)
        val step = direction.sign * interval
        val firstInCalendar = periodOf(FIRST_DATE)
        val lastInCalendar = periodOf(LAST_DATE)
        return object : Iterator<LocalDate> {
            var k = first
            var left = horizonPeriods() + HORIZON_MARGIN_PERIODS + maxOf(beyond, 0) / interval

            init {
                while (left > 0 && k !in firstInCalendar..lastInCalendar) {
                    k += step
                    left--
                }
            }

            override fun hasNext(): Boolean = left > 0 && k in firstInCalendar..lastInCalendar

            override fun next(): LocalDate {
                if (!hasNext()) throw NoSuchElementException()
                val start = startOf(k)
                k += step
                left--
                return start
            }
        }
    }

    private fun align(k: Long, direction: Direction): Long =
        when (direction) {
            Direction.FORWARD -> k + (-k).mod(interval)
            Direction.BACKWARD -> k - k.mod(interval)
        }

    /**
     * Aligned periods in lcm(400 years, interval units), after which both the calendar and the
     * alignment repeat.
     */
    private fun horizonPeriods(): Long = period.per400Years / gcd(period.per400Years, interval)

    companion object {
        /** Default anchor for week intervals (spec/README.md, "WeekRepeat epoch alignment"). */
        private val EPOCH_MONDAY = LocalDate.of(1970, 1, 5)

        private val EPOCH_DATE = LocalDate.of(1970, 1, 1)

        /**
         * Slack beyond the horizon for the period one behind the first date's, where a search
         * starts, and for a horizon that starts mid-period.
         */
        private const val HORIZON_MARGIN_PERIODS = 2L

        /**
         * Every period holding an occurrence in the supported range, in any zone, holds a date from
         * FIRST_DATE to LAST_DATE, allowing for a DST shift past midnight and a nearest weekday two
         * days outside its period. So they are the edges of the calendar a search walks, which also
         * keeps it inside LocalDate's own range: past year 999,999,999, which a huge interval
         * reaches, [LocalDate.plusYears] throws.
         */
        private val FIRST_DATE = LocalDate.of(0, 12, 29)

        private val LAST_DATE = LocalDate.of(10000, 1, 1)

        fun of(expression: ScheduleExpr, starting: LocalDate?): Cadence =
            when (expression) {
                is ScheduleExpr.SingleDate ->
                    when (val date = expression.date) {
                        is DateSpec.Iso -> Cadence(Period.DAY, LocalDate.parse(date.date), 1, true)
                        is DateSpec.Named -> repeating(Period.YEAR, 1, starting)
                    }
                is ScheduleExpr.IntervalRepeat -> repeating(Period.DAY, 1, starting)
                is ScheduleExpr.DayRepeat -> repeating(Period.DAY, expression.interval, starting)
                is ScheduleExpr.WeekRepeat -> repeating(Period.WEEK, expression.interval, starting)
                is ScheduleExpr.MonthRepeat ->
                    repeating(Period.MONTH, expression.interval, starting)
                is ScheduleExpr.YearRepeat -> repeating(Period.YEAR, expression.interval, starting)
            }

        private fun repeating(period: Period, interval: Int, starting: LocalDate?): Cadence {
            val anchor = starting ?: if (period == Period.WEEK) EPOCH_MONDAY else EPOCH_DATE
            val origin =
                when (period) {
                    Period.DAY -> anchor
                    Period.WEEK -> CalendarDates.mondayOf(anchor)
                    Period.MONTH -> anchor.withDayOfMonth(1)
                    Period.YEAR -> anchor.withDayOfYear(1)
                }
            return Cadence(period, origin, interval.toLong(), false)
        }

        private tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
    }
}
