package io.hron.kotlin.internal

import io.hron.kotlin.DateSpec
import io.hron.kotlin.DayFilter
import io.hron.kotlin.DayOfMonthSpec
import io.hron.kotlin.HronException
import io.hron.kotlin.IntervalUnit
import io.hron.kotlin.MonthName
import io.hron.kotlin.MonthTarget
import io.hron.kotlin.OrdinalPosition
import io.hron.kotlin.Schedule
import io.hron.kotlin.ScheduleExpr
import io.hron.kotlin.TimeOfDay
import io.hron.kotlin.Weekday
import io.hron.kotlin.YearTarget

private const val MAX_LISTED_TIMES = 24
private const val MINUTES_PER_DAY = 24 * 60
private const val BOTH_DAYS_RESTRICTED =
    "not expressible in hron: cron fires on either the day of month or the day of week"
private const val INTERVAL_DAYS =
    "not expressible in hron: an interval runs only on every day, weekdays, the weekend or" +
        " listed days"

// Digit strings may be of any length. Every number at or above this cap is out of every field's
// range and steps past every range's end, so saturating at it keeps each comparison exact without
// overflow.
private const val NUMBER_CAP = 1000

private val MIDNIGHT = TimeOfDay(0, 0)
private val END_OF_DAY = TimeOfDay(23, 59)

/** Indexed by cron's day of week, Sunday = 0. */
private val CRON_WEEKDAYS =
    listOf(
        Weekday.SUNDAY,
        Weekday.MONDAY,
        Weekday.TUESDAY,
        Weekday.WEDNESDAY,
        Weekday.THURSDAY,
        Weekday.FRIDAY,
        Weekday.SATURDAY,
    )

private val ORDINALS = OrdinalPosition.entries - OrdinalPosition.LAST

private enum class Field(val label: String, val min: Int, val max: Int, val names: List<String>) {
    MINUTE("minute", 0, 59, emptyList()),
    HOUR("hour", 0, 23, emptyList()),
    DAY_OF_MONTH("day of month", 1, 31, emptyList()),
    MONTH("month", 1, 12, MonthName.entries.map { it.short }),
    DAY_OF_WEEK("day of week", 0, 7, CRON_WEEKDAYS.map { it.word.take(3) });

    /** In the day of week, 7 is Sunday only where written: `*` and `a/n` end at 6. */
    val starEnd: Int
        get() = if (this == DAY_OF_WEEK) 6 else max
}

private sealed interface Bounds {
    data object Star : Bounds

    data class Value(val a: String) : Bounds

    data class Range(val a: String, val b: String) : Bounds
}

private class Item(val bounds: Bounds, val step: String?)

private sealed interface MonthDays {
    data object Any : MonthDays

    class Days(val days: List<Int>) : MonthDays

    data object Last : MonthDays

    data object LastWeekday : MonthDays

    class Nearest(val day: Int) : MonthDays
}

private sealed interface WeekDays {
    data object Any : WeekDays

    class Days(val days: List<Int>) : WeekDays

    class Nth(val weekday: Weekday, val n: Int) : WeekDays

    class Last(val weekday: Weekday) : WeekDays
}

private sealed interface Days {
    class OfWeek(val filter: DayFilter) : Days

    class OfMonth(val target: MonthTarget) : Days
}

internal object Cron {
    fun fromCron(input: String): Schedule {
        val trimmed = input.trim(::isCronSpace)
        val text = if (trimmed.startsWith("@")) shortcut(trimmed) else trimmed
        val fields = splitFields(text)
        if (fields.size != 5) throw HronException.cron("expected 5 cron fields, got ${fields.size}")

        val minutes = values(fields[0], Field.MINUTE).sorted()
        val hours = values(fields[1], Field.HOUR).sorted()
        val monthDays = parseDayOfMonth(fields[2])
        val months = values(fields[3], Field.MONTH).sorted()
        val weekDays = parseDayOfWeek(fields[4])
        val days = dayExpression(monthDays, weekDays)
        val times = hours.flatMap { hour -> minutes.map { minute -> TimeOfDay(hour, minute) } }

        val gap = equalGap(times)
        val yearTarget = yearTarget(days, months)
        val expression =
            when {
                days is Days.OfWeek && gap != null -> interval(times, gap, days.filter)
                times.size > MAX_LISTED_TIMES -> throw tooManyTimes(times.size, gap)
                yearTarget != null -> ScheduleExpr.YearRepeat(1, yearTarget, times.readOnly())
                else ->
                    when (days) {
                        is Days.OfWeek -> ScheduleExpr.DayRepeat(1, days.filter, times.readOnly())
                        is Days.OfMonth ->
                            ScheduleExpr.MonthRepeat(1, days.target, times.readOnly())
                    }
            }
        val during =
            if (expression !is ScheduleExpr.YearRepeat && months.size < MonthName.entries.size) {
                months.map { MonthName.entries[it - 1] }
            } else {
                emptyList()
            }
        return Schedule.of(expression, null, emptyList(), null, null, during.readOnly())
    }

    private fun splitFields(text: String): List<String> {
        val fields = ArrayList<String>(5)
        var start = 0
        for (i in 0..text.length) {
            if (i == text.length || text[i] == ' ' || text[i] == '\t') {
                if (i > start) fields += text.substring(start, i)
                start = i + 1
            }
        }
        return fields
    }

    private fun isCronSpace(ch: Char) = ch == ' ' || ch == '\t' || ch == '\r' || ch == '\n'

    private fun shortcut(input: String): String =
        when (input.asciiLowercase()) {
            "@yearly",
            "@annually" -> "0 0 1 1 *"
            "@monthly" -> "0 0 1 * *"
            "@weekly" -> "0 0 * * 0"
            "@daily",
            "@midnight" -> "0 0 * * *"
            "@hourly" -> "0 * * * *"
            else -> throw HronException.cron("unknown cron shortcut: $input")
        }

    private fun parseDayOfMonth(text: String): MonthDays =
        when {
            text == "*" || text == "?" -> MonthDays.Any
            text.equalsAscii("L") -> MonthDays.Last
            text.equalsAscii("LW") -> MonthDays.LastWeekday
            text.endsWithAscii('w') && isNumber(text.dropLast(1)) ->
                MonthDays.Nearest(fieldValue(text.dropLast(1), Field.DAY_OF_MONTH))
            else -> MonthDays.Days(values(text, Field.DAY_OF_MONTH))
        }

    private fun parseDayOfWeek(text: String): WeekDays {
        val field = Field.DAY_OF_WEEK
        if (text == "*" || text == "?") return WeekDays.Any
        val hash = text.indexOf('#')
        if (
            hash >= 0 &&
                isValue(text.substring(0, hash), field) &&
                isNumber(text.substring(hash + 1))
        ) {
            val weekday = CRON_WEEKDAYS[fieldValue(text.substring(0, hash), field) % 7]
            val nth = text.substring(hash + 1)
            val n = number(nth)
            if (n !in 1..5) throw HronException.cron("day of week ordinal must be 1-5, got $nth")
            return WeekDays.Nth(weekday, n)
        }
        if (text.endsWithAscii('l') && isValue(text.dropLast(1), field)) {
            return WeekDays.Last(CRON_WEEKDAYS[fieldValue(text.dropLast(1), field) % 7])
        }
        return WeekDays.Days(values(text, field))
    }

    /** In the order of first appearance, in which fromCron lists days of the week. */
    private fun values(text: String, field: Field): List<Int> {
        val items = items(text, field) ?: throw HronException.cron("invalid ${field.label}: $text")
        // A field holds at most 60 values, so a list's scan beats a hash set's hashing and boxing.
        val values = ArrayList<Int>()
        for (item in items) {
            val first: Int
            val last: Int
            when (val bounds = item.bounds) {
                Bounds.Star -> {
                    first = field.min
                    last = field.starEnd
                }
                is Bounds.Value -> {
                    first = fieldValue(bounds.a, field)
                    // `7/n` starts past the end of `*`, so it is Sunday alone.
                    last = if (item.step != null) maxOf(first, field.starEnd) else first
                }
                is Bounds.Range -> {
                    first = fieldValue(bounds.a, field)
                    last = fieldValue(bounds.b, field)
                    if (first > last) {
                        throw HronException.cron(
                            "${field.label} range must not run backwards: ${bounds.a}-${bounds.b}"
                        )
                    }
                }
            }
            val step = item.step?.let(::number) ?: 1
            if (step == 0) throw HronException.cron("${field.label} step must be at least 1")
            for (value in first..last step step) {
                val kept = if (field == Field.DAY_OF_WEEK) value % 7 else value
                if (kept !in values) values += kept
            }
        }
        return values
    }

    private fun items(text: String, field: Field): List<Item>? =
        text.split(',').map { item ->
            val slash = item.indexOf('/')
            val range = if (slash >= 0) item.substring(0, slash) else item
            val step = if (slash >= 0) item.substring(slash + 1) else null
            val dash = range.indexOf('-')
            val bounds =
                when {
                    range == "*" -> Bounds.Star
                    dash >= 0 -> Bounds.Range(range.substring(0, dash), range.substring(dash + 1))
                    else -> Bounds.Value(range)
                }
            val valid =
                (step == null || isNumber(step)) &&
                    when (bounds) {
                        Bounds.Star -> true
                        is Bounds.Value -> isValue(bounds.a, field)
                        is Bounds.Range -> isValue(bounds.a, field) && isValue(bounds.b, field)
                    }
            if (!valid) return null
            Item(bounds, step)
        }

    private fun isNumber(text: String) = text.isNotEmpty() && text.all { it in '0'..'9' }

    private fun isValue(text: String, field: Field) =
        isNumber(text) || nameValue(text, field) != null

    private fun nameValue(text: String, field: Field): Int? {
        val index = field.names.indexOfFirst { it.equalsAscii(text) }
        return if (index >= 0) index + field.min else null
    }

    private fun number(digits: String): Int =
        digits.fold(0) { n, c -> minOf(n * 10 + (c - '0'), NUMBER_CAP) }

    private fun fieldValue(text: String, field: Field): Int {
        val value = nameValue(text, field) ?: number(text)
        if (value !in field.min..field.max) {
            throw HronException.cron("${field.label} must be ${field.min}-${field.max}, got $text")
        }
        return value
    }

    private fun dayExpression(monthDays: MonthDays, weekDays: WeekDays): Days {
        if (monthDays != MonthDays.Any && weekDays != WeekDays.Any) {
            throw HronException.cron(BOTH_DAYS_RESTRICTED)
        }
        return when (monthDays) {
            MonthDays.Any ->
                when (weekDays) {
                    WeekDays.Any -> Days.OfWeek(DayFilter.Every)
                    is WeekDays.Days -> Days.OfWeek(weekdayFilter(weekDays.days))
                    is WeekDays.Nth ->
                        Days.OfMonth(
                            MonthTarget.OrdinalWeekday(ORDINALS[weekDays.n - 1], weekDays.weekday)
                        )
                    is WeekDays.Last ->
                        Days.OfMonth(
                            MonthTarget.OrdinalWeekday(OrdinalPosition.LAST, weekDays.weekday)
                        )
                }
            is MonthDays.Days ->
                if (monthDays.days.size == 31) {
                    Days.OfWeek(DayFilter.Every)
                } else {
                    val specs =
                        runs(monthDays.days.sorted()).map { (first, last) ->
                            if (first == last) DayOfMonthSpec.Single(first)
                            else DayOfMonthSpec.Range(first, last)
                        }
                    Days.OfMonth(MonthTarget.Days(specs.readOnly()))
                }
            MonthDays.Last -> Days.OfMonth(MonthTarget.LastDay)
            MonthDays.LastWeekday -> Days.OfMonth(MonthTarget.LastWeekday)
            is MonthDays.Nearest -> Days.OfMonth(MonthTarget.NearestWeekday(monthDays.day, null))
        }
    }

    private fun weekdayFilter(days: List<Int>): DayFilter =
        when (days.sorted()) {
            listOf(0, 1, 2, 3, 4, 5, 6) -> DayFilter.Every
            listOf(1, 2, 3, 4, 5) -> DayFilter.Weekdays
            listOf(0, 6) -> DayFilter.Weekends
            else -> DayFilter.Days(days.map { CRON_WEEKDAYS[it] }.readOnly())
        }

    private fun equalGap(times: List<TimeOfDay>): Int? {
        if (times.size < 3) return null
        val gap = times[1].minuteOfDay - times[0].minuteOfDay
        return gap.takeIf {
            times.zipWithNext().all { (a, b) -> b.minuteOfDay - a.minuteOfDay == gap }
        }
    }

    private fun interval(times: List<TimeOfDay>, gap: Int, days: DayFilter): ScheduleExpr {
        val from = times.first()
        val last = times.last()
        val to =
            if (from == MIDNIGHT && last.minuteOfDay + gap >= MINUTES_PER_DAY) END_OF_DAY else last
        val hours = gap % 60 == 0
        return ScheduleExpr.IntervalRepeat(
            if (hours) gap / 60 else gap,
            if (hours) IntervalUnit.HOURS else IntervalUnit.MINUTES,
            from,
            to,
            days.takeIf { it != DayFilter.Every },
        )
    }

    private fun tooManyTimes(count: Int, gap: Int?): HronException =
        if (gap != null) {
            HronException.cron(INTERVAL_DAYS)
        } else {
            HronException.cron("not expressible in hron: $count times a day are too many to list")
        }

    private fun yearTarget(days: Days, months: List<Int>): YearTarget? {
        if (days !is Days.OfMonth || months.size != 1) return null
        val month = MonthName.entries[months.single() - 1]
        return when (val target = days.target) {
            is MonthTarget.Days -> {
                val spec = target.specs.singleOrNull()
                if (spec is DayOfMonthSpec.Single && spec.day <= month.maxDay) {
                    YearTarget.Date(month, spec.day)
                } else {
                    null
                }
            }
            MonthTarget.LastWeekday -> YearTarget.LastWeekday(month)
            is MonthTarget.OrdinalWeekday ->
                YearTarget.OrdinalWeekday(target.ordinal, target.weekday, month)
            MonthTarget.LastDay,
            is MonthTarget.NearestWeekday -> null
        }
    }

    fun toCron(schedule: Schedule): String {
        if (schedule.except.isNotEmpty()) throw notExpressible("except clauses not supported")
        if (schedule.until != null) throw notExpressible("until clauses not supported")
        if (schedule.starting != null) throw notExpressible("starting clauses not supported")
        val (dayOfMonth, dayOfWeek) = dayFields(schedule.expression)
        val month = monthField(schedule)
        val (minute, hour) = timeFields(schedule)
        return "$minute $hour $dayOfMonth $month $dayOfWeek"
    }

    private fun notExpressible(reason: String) =
        HronException.cron("not expressible as cron: $reason")

    private fun repeatsOnce(interval: Int, unit: String) {
        if (interval > 1) throw notExpressible("multi-$unit repeats not supported")
    }

    private fun dayFields(expression: ScheduleExpr): Pair<String, String> =
        when (expression) {
            is ScheduleExpr.IntervalRepeat ->
                "*" to (expression.dayFilter?.let(::filterField) ?: "*")
            is ScheduleExpr.DayRepeat -> {
                repeatsOnce(expression.interval, "day")
                "*" to filterField(expression.days)
            }
            is ScheduleExpr.WeekRepeat -> {
                repeatsOnce(expression.interval, "week")
                "*" to weekdaysField(expression.days)
            }
            is ScheduleExpr.MonthRepeat -> {
                repeatsOnce(expression.interval, "month")
                when (val target = expression.target) {
                    is MonthTarget.Days -> listField(expandDays(target.specs), 31) to "*"
                    MonthTarget.LastDay -> "L" to "*"
                    MonthTarget.LastWeekday -> "LW" to "*"
                    is MonthTarget.NearestWeekday -> {
                        if (target.direction != null) {
                            throw notExpressible("directional nearest weekday not supported")
                        }
                        "${target.day}W" to "*"
                    }
                    is MonthTarget.OrdinalWeekday ->
                        "*" to ordinalField(target.ordinal, target.weekday)
                }
            }
            is ScheduleExpr.YearRepeat -> {
                repeatsOnce(expression.interval, "year")
                when (val target = expression.target) {
                    is YearTarget.Date -> "${target.day}" to "*"
                    is YearTarget.DayOfMonth -> "${target.day}" to "*"
                    is YearTarget.OrdinalWeekday ->
                        "*" to ordinalField(target.ordinal, target.weekday)
                    is YearTarget.LastWeekday -> "LW" to "*"
                }
            }
            is ScheduleExpr.SingleDate ->
                when (val date = expression.date) {
                    is DateSpec.Iso -> throw notExpressible("ISO dates do not repeat")
                    is DateSpec.Named -> "${date.day}" to "*"
                }
        }

    private fun expandDays(specs: List<DayOfMonthSpec>): List<Int> =
        specs
            .flatMap {
                when (it) {
                    is DayOfMonthSpec.Single -> listOf(it.day)
                    is DayOfMonthSpec.Range -> (it.start..it.end).toList()
                }
            }
            .toSortedSet()
            .toList()

    private fun monthField(schedule: Schedule): String {
        val during = schedule.during
        val month = ownMonth(schedule.expression)
        if (month != null && during.isNotEmpty() && month !in during) {
            throw notExpressible("during excludes the schedule's month")
        }
        if (month != null) return "${month.number}"
        if (during.isEmpty()) return "*"
        return listField(during.map { it.number }.toSortedSet().toList(), 12)
    }

    private fun ownMonth(expression: ScheduleExpr): MonthName? =
        when (expression) {
            is ScheduleExpr.YearRepeat -> expression.target.month
            is ScheduleExpr.SingleDate -> (expression.date as? DateSpec.Named)?.month
            else -> null
        }

    private fun timeFields(schedule: Schedule): Pair<String, String> {
        val times = dailyTimes(schedule.expression)
        val minutes = times.map { it % 60 }.toSortedSet().toList()
        val hours = times.map { it / 60 }.toSortedSet().toList()
        if (minutes.size * hours.size != times.size) {
            throw notExpressible("times are not every combination of their minutes and hours")
        }
        return stepField(minutes, 60) to stepField(hours, 24)
    }

    private fun dailyTimes(expression: ScheduleExpr): List<Int> {
        val times =
            when (expression) {
                is ScheduleExpr.IntervalRepeat -> return intervalSlots(expression).toList()
                is ScheduleExpr.DayRepeat -> expression.times
                is ScheduleExpr.WeekRepeat -> expression.times
                is ScheduleExpr.MonthRepeat -> expression.times
                is ScheduleExpr.YearRepeat -> expression.times
                is ScheduleExpr.SingleDate -> expression.times
            }
        return times.map { it.minuteOfDay }.toSortedSet().toList()
    }

    private fun filterField(filter: DayFilter): String =
        when (filter) {
            DayFilter.Every -> "*"
            DayFilter.Weekdays ->
                weekdaysField(
                    listOf(
                        Weekday.MONDAY,
                        Weekday.TUESDAY,
                        Weekday.WEDNESDAY,
                        Weekday.THURSDAY,
                        Weekday.FRIDAY,
                    )
                )
            DayFilter.Weekends -> weekdaysField(listOf(Weekday.SATURDAY, Weekday.SUNDAY))
            is DayFilter.Days -> weekdaysField(filter.days)
        }

    private fun weekdaysField(days: List<Weekday>): String =
        listField(days.map(CRON_WEEKDAYS::indexOf).toSortedSet().toList(), 7)

    private fun ordinalField(ordinal: OrdinalPosition, weekday: Weekday): String {
        val day = CRON_WEEKDAYS.indexOf(weekday)
        return if (ordinal == OrdinalPosition.LAST) "${day}L" else "$day#${ordinal.n}"
    }

    private fun stepField(values: List<Int>, size: Int): String {
        val first = values.first()
        val last = values.last()
        val gap = if (values.size > 1) values[1] - first else null
        val equalGaps = gap != null && values.zipWithNext().all { (a, b) -> b - a == gap }
        return when {
            values.size == size -> "*"
            gap == null -> "$first"
            equalGaps && first == 0 && last + gap == size -> "*/$gap"
            equalGaps && gap == 1 -> "$first-$last"
            equalGaps && values.size >= 3 -> "$first-$last/$gap"
            else -> listField(values, size)
        }
    }

    private fun listField(values: List<Int>, size: Int): String {
        if (values.size == size) return "*"
        return runs(values).joinToString(",") { (first, last) ->
            if (first == last) "$first" else "$first-$last"
        }
    }

    private fun runs(sortedValues: List<Int>): List<Pair<Int, Int>> {
        val runs = mutableListOf<Pair<Int, Int>>()
        for (value in sortedValues) {
            val run = runs.lastOrNull()
            if (run != null && run.second + 1 == value) {
                runs[runs.lastIndex] = run.first to value
            } else {
                runs += value to value
            }
        }
        return runs
    }

    private fun String.endsWithAscii(lower: Char) = isNotEmpty() && last().asciiLowercase() == lower
}
