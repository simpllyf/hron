package io.hron.kotlin.internal

import io.hron.kotlin.DateSpec
import io.hron.kotlin.DayFilter
import io.hron.kotlin.DayOfMonthSpec
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
import io.hron.kotlin.TimeOfDay
import io.hron.kotlin.UntilSpec
import io.hron.kotlin.Weekday
import io.hron.kotlin.YearTarget
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * The `{what}` of each `expected {what}, got ...` error, one per phrase in the position table of
 * spec/README.md, "Parse errors".
 */
private object Expected {
    const val EVERY_OR_ON = "'every' or 'on'"
    const val REPEATER =
        "'day', 'weekday', 'weekend', a day name, 'week', 'month', 'year' or a number"
    const val UNIT = "a unit ('min', 'hours', 'days', 'weeks', 'months' or 'years')"
    const val AT = "'at'"
    const val TIME = "a time (HH:MM)"
    const val FROM = "'from'"
    const val TO = "'to'"
    const val DAY_TARGET = "'day', 'weekday', 'weekend' or a day name"
    const val ON = "'on'"
    const val DAY_NAME = "a day name"
    const val THE = "'the'"
    const val MONTH_TARGET =
        "a day such as 15th, 'last', an ordinal such as 'first', 'next', 'previous' or 'nearest'"
    const val MONTH_LAST = "'day', 'weekday' or a day name"
    const val NEAREST = "'nearest'"
    const val WEEKDAY = "'weekday'"
    const val DAY_OF_MONTH = "a day such as 15th"
    const val YEAR_TARGET = "a month name or 'the'"
    const val YEAR_THE = "a day such as 15th, 'last' or an ordinal such as 'first'"
    const val YEAR_LAST = "'weekday' or a day name"
    const val OF = "'of'"
    const val MONTH_NAME = "a month name"
    const val DAY_NUMBER = "a day number"
    const val DATE = "a date (YYYY-MM-DD, or a month and day)"
    const val ISO_DATE = "a date (YYYY-MM-DD)"
    const val TIMEZONE = "a timezone"
}

private val CLAUSE_ORDER =
    listOf(TokenKind.EXCEPT, TokenKind.UNTIL, TokenKind.STARTING, TokenKind.DURING, TokenKind.IN)

internal class Parser
private constructor(private val input: String, private val tokens: List<Token>) {
    private var pos = 0
    private var untilSpan: Span? = null

    private fun peek(): Token? = tokens.getOrNull(pos)

    private fun at(kind: TokenKind): Boolean = peek()?.kind == kind

    private fun advance(): Token = tokens[pos++]

    private fun previous(): Token = tokens[pos - 1]

    private fun eat(kind: TokenKind): Boolean = at(kind).also { if (it) pos++ }

    private fun expect(kind: TokenKind, what: String) {
        if (!eat(kind)) throw expected(what)
    }

    private fun text(token: Token): String = input.substring(token.start, token.end)

    private fun error(message: String, start: Int, end: Int): HronException =
        HronException.parse(message, Lexer.span(input, start, end), input)

    private fun error(message: String, token: Token): HronException =
        error(message, token.start, token.end)

    private fun expected(what: String): HronException {
        val token = peek()
        if (token != null) return error("expected $what, got '${text(token)}'", token)
        val end = tokens.last().end
        return error("expected $what, got end of input", end, end)
    }

    private fun parseExpression(): ScheduleExpr =
        when {
            eat(TokenKind.EVERY) -> parseEvery()
            eat(TokenKind.ON) -> parseOn()
            else -> throw expected(Expected.EVERY_OR_ON)
        }

    private fun parseClauses(expression: ScheduleExpr): Schedule {
        var except = emptyList<ExceptionSpec>()
        var until: UntilSpec? = null
        var starting: String? = null
        var during = emptyList<MonthName>()
        var timezone: String? = null

        if (eat(TokenKind.EXCEPT)) except = parseExceptionList()

        if (at(TokenKind.UNTIL)) {
            val untilStart = advance().start
            until =
                when (val date = parseDate()) {
                    is DateSpec.Iso -> UntilSpec.Iso(date.date)
                    is DateSpec.Named -> UntilSpec.Named(date.month, date.day)
                }
            untilSpan = Lexer.span(input, untilStart, previous().end)
        }

        if (eat(TokenKind.STARTING)) {
            if (!at(TokenKind.ISO_DATE)) throw expected(Expected.ISO_DATE)
            starting = isoDate(advance())
        }

        if (eat(TokenKind.DURING)) during = parseMonthList()

        if (eat(TokenKind.IN)) {
            if (!at(TokenKind.TIMEZONE)) throw expected(Expected.TIMEZONE)
            timezone = timezone(advance())
        }

        return Schedule.of(expression, timezone, except, until, starting, during)
    }

    private fun leftover(schedule: Schedule): HronException {
        val token = peek()!!
        // Every clause holds at least one item, so a clause was read exactly when its field is set.
        val read =
            listOf(
                schedule.except.isNotEmpty(),
                schedule.until != null,
                schedule.starting != null,
                schedule.during.isNotEmpty(),
                schedule.timezone != null,
            )
        val clause = CLAUSE_ORDER.indexOf(token.kind)
        val lastRead = read.lastIndexOf(true)
        val message =
            when {
                clause >= 0 && read[clause] -> "duplicate '${keyword(clause)}' clause"
                clause >= 0 && lastRead >= 0 ->
                    "'${keyword(clause)}' must come before '${keyword(lastRead)}'"
                else -> "unexpected '${text(token)}' after the schedule"
            }
        return error(message, token)
    }

    private fun keyword(clause: Int): String = CLAUSE_ORDER[clause].name.lowercase()

    private fun checkNamedUntil(schedule: Schedule) {
        val until = schedule.until
        if (until !is UntilSpec.Named || schedule.starting != null) return
        val date = "${until.month.short} ${until.day}"
        throw HronException.parse(
            "until $date has no year: add a starting date, or use an ISO date",
            untilSpan!!,
            input,
            "until $date starting YYYY-MM-DD",
        )
    }

    private fun parseExceptionList(): List<ExceptionSpec> = commaList {
        when (val date = parseDate()) {
            is DateSpec.Iso -> ExceptionSpec.Iso(date.date)
            is DateSpec.Named -> ExceptionSpec.Named(date.month, date.day)
        }
    }

    private fun parseDate(): DateSpec {
        if (at(TokenKind.ISO_DATE)) return DateSpec.Iso(isoDate(advance()))
        if (at(TokenKind.MONTH_NAME)) {
            val month = advance().month!!
            return DateSpec.Named(month, parseDayOf(month))
        }
        throw expected(Expected.DATE)
    }

    private fun isoDate(token: Token): String {
        val date = text(token)
        if (!isCalendarDate(date)) {
            throw error(
                "date must be a calendar date from 0001-01-01 to 9999-12-31, got $date",
                token,
            )
        }
        return date
    }

    private fun timezone(token: Token): String {
        val name = text(token)
        return TimeZones.canonical(name)
            ?: throw error(
                "timezone must be UTC or an Area/Location name such as America/New_York, got $name",
                token,
            )
    }

    private fun parseEvery(): ScheduleExpr =
        when (peek()?.kind) {
            TokenKind.DAY -> {
                advance()
                parseDayRepeat(1, DayFilter.Every)
            }
            TokenKind.WEEKDAY -> {
                advance()
                parseDayRepeat(1, DayFilter.Weekdays)
            }
            TokenKind.WEEKEND -> {
                advance()
                parseDayRepeat(1, DayFilter.Weekends)
            }
            TokenKind.DAY_NAME -> parseDayRepeat(1, DayFilter.Days(parseDayList()))
            TokenKind.WEEKS -> {
                advance()
                parseWeekRepeat(1)
            }
            TokenKind.MONTH -> {
                advance()
                parseMonthRepeat(1)
            }
            TokenKind.YEAR -> {
                advance()
                parseYearRepeat(1)
            }
            TokenKind.NUMBER -> parseNumberRepeat()
            else -> throw expected(Expected.REPEATER)
        }

    private fun parseDayRepeat(interval: Int, days: DayFilter): ScheduleExpr {
        expect(TokenKind.AT, Expected.AT)
        return ScheduleExpr.DayRepeat(interval, days, parseTimeList())
    }

    private fun parseNumberRepeat(): ScheduleExpr {
        val number = advance()
        val interval = number.number
        if (interval == 0) throw error("interval must be 1-2147483647, got ${text(number)}", number)
        return when (peek()?.kind) {
            TokenKind.WEEKS -> {
                advance()
                parseWeekRepeat(interval)
            }
            TokenKind.INTERVAL_UNIT -> parseIntervalRepeat(interval, advance().unit!!)
            TokenKind.DAY -> {
                advance()
                parseDayRepeat(interval, DayFilter.Every)
            }
            TokenKind.MONTH -> {
                advance()
                parseMonthRepeat(interval)
            }
            TokenKind.YEAR -> {
                advance()
                parseYearRepeat(interval)
            }
            else -> throw expected(Expected.UNIT)
        }
    }

    private fun parseIntervalRepeat(interval: Int, unit: IntervalUnit): ScheduleExpr {
        expect(TokenKind.FROM, Expected.FROM)
        val from = parseTime()
        val fromToken = previous()
        expect(TokenKind.TO, Expected.TO)
        val to = parseTime()
        val toToken = previous()
        if (from.minuteOfDay > to.minuteOfDay) {
            throw error(
                "time window must not run backwards: ${text(fromToken)} to ${text(toToken)}" +
                    " (a window cannot cross midnight)",
                fromToken.start,
                toToken.end,
            )
        }
        val dayFilter = if (eat(TokenKind.ON)) parseDayTarget() else null
        return ScheduleExpr.IntervalRepeat(interval, unit, from, to, dayFilter)
    }

    private fun parseWeekRepeat(interval: Int): ScheduleExpr {
        expect(TokenKind.ON, Expected.ON)
        val days = parseDayList()
        expect(TokenKind.AT, Expected.AT)
        return ScheduleExpr.WeekRepeat(interval, days, parseTimeList())
    }

    private fun parseMonthRepeat(interval: Int): ScheduleExpr {
        expect(TokenKind.ON, Expected.ON)
        expect(TokenKind.THE, Expected.THE)
        val target =
            when (peek()?.kind) {
                TokenKind.LAST -> {
                    advance()
                    parseMonthLast()
                }
                TokenKind.ORDINAL -> {
                    val ordinal = advance().ordinal!!
                    MonthTarget.OrdinalWeekday(ordinal, parseDayName())
                }
                TokenKind.ORDINAL_NUMBER -> MonthTarget.Days(commaList(::parseOrdinalDaySpec))
                TokenKind.NEXT,
                TokenKind.PREVIOUS,
                TokenKind.NEAREST -> parseNearestWeekdayTarget()
                else -> throw expected(Expected.MONTH_TARGET)
            }
        expect(TokenKind.AT, Expected.AT)
        return ScheduleExpr.MonthRepeat(interval, target, parseTimeList())
    }

    private fun parseMonthLast(): MonthTarget {
        val token = peek()
        val target =
            when (token?.kind) {
                TokenKind.DAY -> MonthTarget.LastDay
                TokenKind.WEEKDAY -> MonthTarget.LastWeekday
                TokenKind.DAY_NAME ->
                    MonthTarget.OrdinalWeekday(OrdinalPosition.LAST, token.weekday!!)
                else -> throw expected(Expected.MONTH_LAST)
            }
        advance()
        return target
    }

    private fun parseNearestWeekdayTarget(): MonthTarget {
        val direction =
            when {
                eat(TokenKind.NEXT) -> NearestDirection.NEXT
                eat(TokenKind.PREVIOUS) -> NearestDirection.PREVIOUS
                else -> null
            }
        expect(TokenKind.NEAREST, Expected.NEAREST)
        expect(TokenKind.WEEKDAY, Expected.WEEKDAY)
        expect(TokenKind.TO, Expected.TO)
        return MonthTarget.NearestWeekday(parseOrdinalDay(), direction)
    }

    private fun parseOrdinalDaySpec(): DayOfMonthSpec {
        val start = parseOrdinalDay()
        val startToken = previous()
        if (!eat(TokenKind.TO)) return DayOfMonthSpec.Single(start)
        val end = parseOrdinalDay()
        val endToken = previous()
        if (start > end) {
            throw error(
                "day range must not run backwards: ${text(startToken)} to ${text(endToken)}",
                startToken.start,
                endToken.end,
            )
        }
        return DayOfMonthSpec.Range(start, end)
    }

    private fun parseOrdinalDay(): Int {
        if (!at(TokenKind.ORDINAL_NUMBER)) throw expected(Expected.DAY_OF_MONTH)
        return dayOfMonth(advance())
    }

    private fun parseDayOf(month: MonthName): Int {
        if (!at(TokenKind.NUMBER) && !at(TokenKind.ORDINAL_NUMBER)) {
            throw expected(Expected.DAY_NUMBER)
        }
        val token = advance()
        val day = dayOfMonth(token)
        checkDayInMonth(day, token, month)
        return day
    }

    private fun dayOfMonth(token: Token): Int {
        val day = token.number
        if (day !in 1..31) throw error("day must be 1-31, got ${text(token)}", token)
        return day
    }

    private fun checkDayInMonth(day: Int, token: Token, month: MonthName) {
        if (day > month.maxDay) {
            throw error(
                "day must be 1-${month.maxDay} for ${month.short}, got ${text(token)}",
                token,
            )
        }
    }

    private fun parseYearRepeat(interval: Int): ScheduleExpr {
        expect(TokenKind.ON, Expected.ON)
        val target =
            when {
                eat(TokenKind.THE) -> parseYearTargetAfterThe()
                at(TokenKind.MONTH_NAME) -> {
                    val month = advance().month!!
                    YearTarget.Date(month, parseDayOf(month))
                }
                else -> throw expected(Expected.YEAR_TARGET)
            }
        expect(TokenKind.AT, Expected.AT)
        return ScheduleExpr.YearRepeat(interval, target, parseTimeList())
    }

    private fun parseYearTargetAfterThe(): YearTarget {
        if (eat(TokenKind.LAST)) {
            if (eat(TokenKind.WEEKDAY)) {
                expect(TokenKind.OF, Expected.OF)
                return YearTarget.LastWeekday(parseMonthName())
            }
            if (at(TokenKind.DAY_NAME)) {
                val weekday = advance().weekday!!
                expect(TokenKind.OF, Expected.OF)
                return YearTarget.OrdinalWeekday(OrdinalPosition.LAST, weekday, parseMonthName())
            }
            throw expected(Expected.YEAR_LAST)
        }
        if (at(TokenKind.ORDINAL)) {
            val ordinal = advance().ordinal!!
            val weekday = parseDayName()
            expect(TokenKind.OF, Expected.OF)
            return YearTarget.OrdinalWeekday(ordinal, weekday, parseMonthName())
        }
        if (at(TokenKind.ORDINAL_NUMBER)) {
            val day = parseOrdinalDay()
            val dayToken = previous()
            expect(TokenKind.OF, Expected.OF)
            val month = parseMonthName()
            checkDayInMonth(day, dayToken, month)
            return YearTarget.DayOfMonth(day, month)
        }
        throw expected(Expected.YEAR_THE)
    }

    private fun parseMonthName(): MonthName {
        if (!at(TokenKind.MONTH_NAME)) throw expected(Expected.MONTH_NAME)
        return advance().month!!
    }

    private fun parseMonthList(): List<MonthName> = commaList(::parseMonthName)

    private fun parseOn(): ScheduleExpr {
        val date = parseDate()
        expect(TokenKind.AT, Expected.AT)
        return ScheduleExpr.SingleDate(date, parseTimeList())
    }

    private fun parseDayTarget(): DayFilter =
        when {
            eat(TokenKind.DAY) -> DayFilter.Every
            eat(TokenKind.WEEKDAY) -> DayFilter.Weekdays
            eat(TokenKind.WEEKEND) -> DayFilter.Weekends
            at(TokenKind.DAY_NAME) -> DayFilter.Days(parseDayList())
            else -> throw expected(Expected.DAY_TARGET)
        }

    private fun parseDayName(): Weekday {
        if (!at(TokenKind.DAY_NAME)) throw expected(Expected.DAY_NAME)
        return advance().weekday!!
    }

    private fun parseDayList(): List<Weekday> = commaList(::parseDayName)

    private fun parseTimeList(): List<TimeOfDay> = commaList(::parseTime)

    private fun parseTime(): TimeOfDay {
        if (!at(TokenKind.TIME)) throw expected(Expected.TIME)
        return advance().time!!
    }

    /** Read-only, as nothing a schedule's getters return may change it. */
    private inline fun <T> commaList(item: () -> T): List<T> {
        val items = ArrayList<T>()
        do {
            items += item()
        } while (eat(TokenKind.COMMA))
        return items.readOnly()
    }

    companion object {
        fun parse(input: String): Schedule {
            val tokens = Lexer.tokenize(input)
            if (tokens.isEmpty()) throw HronException.parse("empty expression", Span(0, 0), input)
            val parser = Parser(input, tokens)
            val schedule = parser.parseClauses(parser.parseExpression())
            if (parser.peek() != null) throw parser.leftover(schedule)
            // spec/README.md, "Parse errors": every other error wins over a named until without
            // starting.
            parser.checkNamedUntil(schedule)
            return schedule
        }

        private fun isCalendarDate(date: String): Boolean =
            try {
                LocalDate.parse(date).year >= 1
            } catch (e: DateTimeParseException) {
                false
            }
    }
}
