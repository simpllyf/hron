package io.hron.kotlin

import io.hron.kotlin.internal.Cron
import io.hron.kotlin.internal.Display
import io.hron.kotlin.internal.Evaluator
import io.hron.kotlin.internal.Parser
import java.time.ZonedDateTime
import java.util.Objects

/**
 * A parsed hron schedule, built only by [parse] and [fromCron]. It cannot change, and nothing its
 * properties return can change it.
 *
 * Only the instant of a timestamp argument matters, not its zone. Every returned timestamp is in
 * the schedule's [timezone], or `ZoneId.of("UTC")` without one. An argument outside the supported
 * range, `0001-01-02T00:00:00Z` up to `9999-12-30T00:00:00Z`, is not an error: there is no
 * occurrence after, before or at it.
 */
public class Schedule
private constructor(
    /** The repeat, without its trailing clauses. */
    public val expression: ScheduleExpr,
    /** The IANA timezone name in its canonical capitalization, or null without an `in` clause. */
    public val timezone: String?,
    /** The `except` dates, empty without an `except` clause. */
    public val except: List<ExceptionSpec>,
    /** The `until` date, or null without an `until` clause. */
    public val until: UntilSpec?,
    /** The `starting` date as `YYYY-MM-DD`, or null without a `starting` clause. */
    public val starting: String?,
    /** The `during` months, empty without a `during` clause. */
    public val during: List<MonthName>,
) {
    /** The next occurrence strictly after [now], or null if there is none. */
    public fun nextFrom(now: ZonedDateTime): ZonedDateTime? = Evaluator.nextFrom(this, now)

    /** The next occurrences strictly after [now], at most [n] of them, and none when `n <= 0`. */
    public fun nextNFrom(now: ZonedDateTime, n: Int): List<ZonedDateTime> =
        Evaluator.nextNFrom(this, now, n)

    /** The last occurrence strictly before [now], or null if there is none. */
    public fun previousFrom(now: ZonedDateTime): ZonedDateTime? = Evaluator.previousFrom(this, now)

    /** Whether the minute containing [datetime] is an occurrence. */
    public fun matches(datetime: ZonedDateTime): Boolean = Evaluator.matches(this, datetime)

    /**
     * The occurrences strictly after [from], computed lazily. Without an `until` clause it ends
     * only at the end of the supported range, so take a prefix of it rather than collecting it.
     */
    public fun occurrences(from: ZonedDateTime): Sequence<ZonedDateTime> =
        Evaluator.occurrences(this, from)

    /** The occurrences `t` with `from < t <= to`, computed lazily. */
    public fun between(from: ZonedDateTime, to: ZonedDateTime): Sequence<ZonedDateTime> =
        Evaluator.between(this, from, to)

    /**
     * The 5-field cron expression that fires at the same times on the same dates. The timezone is
     * not part of it: run it in the schedule's timezone. Throws a [HronException] of kind
     * [ErrorKind.CRON] if no cron expression fires as this schedule does.
     */
    public fun toCron(): String = Cron.toCron(this)

    /** The canonical expression, which [parse] reads back to an equal schedule. */
    override fun toString(): String = Display.render(this)

    /**
     * True when [other] is a schedule with equal parts, with lists compared in order
     * (spec/README.md, "Equality").
     */
    override fun equals(other: Any?): Boolean =
        other is Schedule &&
            expression == other.expression &&
            timezone == other.timezone &&
            except == other.except &&
            until == other.until &&
            starting == other.starting &&
            during == other.during

    override fun hashCode(): Int =
        Objects.hash(expression, timezone, except, until, starting, during)

    public companion object {
        /**
         * For the parser and fromCron, which check the parts. A Kotlin `internal` constructor is
         * public in bytecode, so Java could call it; this function is hidden from Java.
         */
        @JvmSynthetic
        internal fun of(
            expression: ScheduleExpr,
            timezone: String?,
            except: List<ExceptionSpec>,
            until: UntilSpec?,
            starting: String?,
            during: List<MonthName>,
        ): Schedule = Schedule(expression, timezone, except, until, starting, during)

        /** Parses an hron expression; throws [HronException] if it is invalid. */
        @JvmStatic public fun parse(input: String): Schedule = Parser.parse(input)

        /**
         * The schedule that fires at the same times on the same dates as a 5-field cron expression.
         * Throws a [HronException] of kind [ErrorKind.CRON] if the cron is invalid or no hron
         * schedule fires as it does.
         */
        @JvmStatic public fun fromCron(cronExpr: String): Schedule = Cron.fromCron(cronExpr)

        /** Whether [parse] accepts [input]. */
        @JvmStatic
        public fun validate(input: String): Boolean =
            try {
                Parser.parse(input)
                true
            } catch (e: HronException) {
                false
            }
    }
}
