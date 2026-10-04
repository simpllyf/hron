import io.hron.kotlin.DateSpec
import io.hron.kotlin.DayFilter
import io.hron.kotlin.DayOfMonthSpec
import io.hron.kotlin.ErrorKind
import io.hron.kotlin.ExceptionSpec
import io.hron.kotlin.HronException
import io.hron.kotlin.IntervalUnit
import io.hron.kotlin.MonthName
import io.hron.kotlin.MonthTarget
import io.hron.kotlin.NearestDirection
import io.hron.kotlin.OrdinalPosition
import io.hron.kotlin.Schedule
import io.hron.kotlin.ScheduleExpr
import io.hron.kotlin.Span
import io.hron.kotlin.UntilSpec
import io.hron.kotlin.Weekday
import io.hron.kotlin.YearTarget
import java.time.ZonedDateTime

// An app on the oldest Kotlin hron supports, using every public function. It matches every part
// exhaustively, with no else branch, so a case added to a part fails this check and gets noticed.

fun describe(expression: ScheduleExpr): String =
    when (expression) {
        is ScheduleExpr.IntervalRepeat ->
            "${expression.interval} ${unit(expression.unit)} ${expression.dayFilter?.let(::days)}"
        is ScheduleExpr.DayRepeat -> days(expression.days)
        is ScheduleExpr.WeekRepeat -> expression.days.joinToString { weekday(it) }
        is ScheduleExpr.MonthRepeat -> month(expression.target)
        is ScheduleExpr.SingleDate -> date(expression.date)
        is ScheduleExpr.YearRepeat -> year(expression.target)
    }

fun days(filter: DayFilter): String =
    when (filter) {
        DayFilter.Every -> "every"
        DayFilter.Weekdays -> "weekdays"
        DayFilter.Weekends -> "weekends"
        is DayFilter.Days -> filter.days.joinToString { weekday(it) }
    }

fun month(target: MonthTarget): String =
    when (target) {
        is MonthTarget.Days ->
            target.specs.joinToString {
                when (it) {
                    is DayOfMonthSpec.Single -> "${it.day}"
                    is DayOfMonthSpec.Range -> "${it.start}-${it.end}"
                }
            }
        MonthTarget.LastDay -> "last day"
        MonthTarget.LastWeekday -> "last weekday"
        is MonthTarget.NearestWeekday ->
            when (target.direction) {
                NearestDirection.NEXT -> "next"
                NearestDirection.PREVIOUS -> "previous"
                null -> "nearest"
            }
        is MonthTarget.OrdinalWeekday -> "${ordinal(target.ordinal)} ${weekday(target.weekday)}"
    }

fun year(target: YearTarget): String =
    when (target) {
        is YearTarget.Date -> "${monthName(target.month)} ${target.day}"
        is YearTarget.OrdinalWeekday -> "${ordinal(target.ordinal)} ${weekday(target.weekday)}"
        is YearTarget.DayOfMonth -> "${target.day} ${monthName(target.month)}"
        is YearTarget.LastWeekday -> monthName(target.month)
    }

fun date(spec: DateSpec): String =
    when (spec) {
        is DateSpec.Named -> "${monthName(spec.month)} ${spec.day}"
        is DateSpec.Iso -> spec.date
    }

fun exception(spec: ExceptionSpec): String =
    when (spec) {
        is ExceptionSpec.Named -> "${monthName(spec.month)} ${spec.day}"
        is ExceptionSpec.Iso -> spec.date
    }

fun until(spec: UntilSpec): String =
    when (spec) {
        is UntilSpec.Iso -> spec.date
        is UntilSpec.Named -> "${monthName(spec.month)} ${spec.day}"
    }

fun unit(unit: IntervalUnit): String =
    when (unit) {
        IntervalUnit.MINUTES -> "min"
        IntervalUnit.HOURS -> "hours"
    }

fun weekday(day: Weekday): String =
    when (day) {
        Weekday.MONDAY -> "mon"
        Weekday.TUESDAY -> "tue"
        Weekday.WEDNESDAY -> "wed"
        Weekday.THURSDAY -> "thu"
        Weekday.FRIDAY -> "fri"
        Weekday.SATURDAY -> "sat"
        Weekday.SUNDAY -> "sun"
    }

fun monthName(month: MonthName): String = month.name.lowercase().take(3)

fun ordinal(position: OrdinalPosition): String =
    when (position) {
        OrdinalPosition.FIRST -> "1st"
        OrdinalPosition.SECOND -> "2nd"
        OrdinalPosition.THIRD -> "3rd"
        OrdinalPosition.FOURTH -> "4th"
        OrdinalPosition.FIFTH -> "5th"
        OrdinalPosition.LAST -> "last"
    }

fun kind(kind: ErrorKind): String =
    when (kind) {
        ErrorKind.LEX -> "lex"
        ErrorKind.PARSE -> "parse"
        ErrorKind.EVAL -> "eval"
        ErrorKind.CRON -> "cron"
    }

fun main() {
    val schedule =
        Schedule.parse(
            "every weekday at 9:00 except dec 25 until 2027-01-01 during feb in America/New_York"
        )
    val now = ZonedDateTime.parse("2026-02-06T12:00:00Z")
    val next = schedule.nextFrom(now)
    check(next.toString() == "2026-02-06T09:00-05:00[America/New_York]") { "nextFrom is $next" }
    check(schedule.nextNFrom(now, 2).size == 2)
    check(schedule.previousFrom(now) != null && !schedule.matches(now))
    check(schedule.occurrences(now).take(3).toList() == schedule.nextNFrom(now, 3))
    check(schedule.between(now, next!!).toList() == listOf(next))
    check(
        schedule == Schedule.parse(schedule.toString()) && schedule.timezone == "America/New_York"
    )
    check(
        Schedule.fromCron("0 9 * * 1-5").toCron() == "0 9 * * 1-5" &&
            Schedule.validate("every day at 09:00")
    )
    println(
        describe(schedule.expression) +
            " " +
            schedule.except.map(::exception) +
            " " +
            schedule.until?.let(::until)
    )
    println("${schedule.during.map(::monthName)} ${schedule.starting}")
    try {
        Schedule.parse("every day")
    } catch (e: HronException) {
        check(
            kind(e.kind) == "parse" &&
                e.span != null &&
                e.input == "every day" &&
                e.suggestion == null
        )
        println(e.displayRich())
    }
    check(
        HronException.lex("m", Span(0, 1), "x").kind == ErrorKind.LEX &&
            HronException.parse("m", Span(0, 1), "x", "y").suggestion == "y" &&
            HronException.cron("m").kind == ErrorKind.CRON &&
            HronException.eval("m").kind == ErrorKind.EVAL
    )
}
