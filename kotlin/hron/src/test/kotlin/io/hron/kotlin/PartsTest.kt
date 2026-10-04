package io.hron.kotlin

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PartsTest {
    @Test
    fun gettersReturnTheParts() {
        val schedule =
            Schedule.parse(
                "every monday, friday at 9:00 except dec 25, 2026-07-04 until 2027-01-01" +
                    " starting 2026-02-09 during jul, jan in america/new_york"
            )
        val expression = schedule.expression
        assertTrue(expression is ScheduleExpr.DayRepeat)
        assertEquals(1, expression.interval)
        assertEquals(DayFilter.Days(listOf(Weekday.MONDAY, Weekday.FRIDAY)), expression.days)
        assertEquals("09:00", expression.times.single().toString())
        assertEquals(
            listOf(ExceptionSpec.Named(MonthName.DECEMBER, 25), ExceptionSpec.Iso("2026-07-04")),
            schedule.except,
        )
        assertEquals(UntilSpec.Iso("2027-01-01"), schedule.until)
        assertEquals("2026-02-09", schedule.starting)
        assertEquals(listOf(MonthName.JULY, MonthName.JANUARY), schedule.during)
        assertEquals("America/New_York", schedule.timezone)
    }

    @Test
    fun eachExpressionKindHasItsPart() {
        val kinds =
            listOf(
                    "every 30 min from 09:00 to 17:00 on weekdays",
                    "every 2 days at 09:00",
                    "every 2 weeks on monday at 09:00",
                    "every month on the next nearest weekday to 15th at 09:00",
                    "on 2026-03-01 at 09:00",
                    "every year on the last friday of dec at 09:00",
                )
                .map { Schedule.parse(it).expression::class }
        assertEquals(
            listOf(
                ScheduleExpr.IntervalRepeat::class,
                ScheduleExpr.DayRepeat::class,
                ScheduleExpr.WeekRepeat::class,
                ScheduleExpr.MonthRepeat::class,
                ScheduleExpr.SingleDate::class,
                ScheduleExpr.YearRepeat::class,
            ),
            kinds,
        )
    }

    /** The README's advice for a `when` that keeps working when a part gains a case. */
    @Test
    fun anElseBranchCompilesWithTheWarningSuppressed() {
        val exception = Schedule.parse("every day at 09:00 except dec 25").except.single()
        val shown =
            @Suppress("REDUNDANT_ELSE_IN_WHEN")
            when (exception) {
                is ExceptionSpec.Named -> "${exception.month} ${exception.day}"
                is ExceptionSpec.Iso -> exception.date
                else -> "a kind of date added later"
            }
        assertEquals("DECEMBER 25", shown)
    }

    /** spec/README.md, "Equality": lists compare in order, duplicates included. */
    @Test
    fun equalSchedulesHaveEqualParts() {
        assertEquals(Schedule.parse("every day at 9:00"), Schedule.parse("every day at 09:00"))
        assertEquals(
            Schedule.parse("every day at 9:00").hashCode(),
            Schedule.parse("every day at 09:00").hashCode(),
        )
        assertEquals(Schedule.fromCron("0 9 * * *"), Schedule.parse("every day at 09:00"))
        assertNotEquals(
            Schedule.parse("every monday, friday at 09:00"),
            Schedule.parse("every friday, monday at 09:00"),
        )
        assertNotEquals(
            Schedule.parse("every day at 09:00, 09:00"),
            Schedule.parse("every day at 09:00"),
        )
    }

    /**
     * spec/README.md, "Schedules built in code": a schedule cannot change after it is built, so no
     * list a getter returns can be changed through a cast.
     */
    @Test
    fun noListAGetterReturnsCanChangeTheSchedule() {
        val parsed =
            listOf(
                    "every monday, friday at 09:00, 10:00 except dec 25, 2026-07-04 during jan, feb",
                    "every 30 min from 09:00 to 17:00 on monday, friday",
                    "every 2 weeks on monday, friday at 09:00, 10:00",
                    "every month on the 1st, 15th to 20th at 09:00, 10:00",
                    "on 2026-03-01 at 09:00, 10:00",
                    "every year on dec 25 at 09:00, 10:00",
                )
                .map(Schedule::parse)
        val converted =
            listOf("0,30 9 * * 1,5", "0 9,10 1,15-20 * *", "0 9 * 1,2 *", "*/15 9-17 * * 1,5")
                .map(Schedule::fromCron)
        for (schedule in parsed + converted) {
            val text = schedule.toString()
            for (list in lists(schedule)) {
                // An empty list is Kotlin's EmptyList, which refuses the cast itself.
                @Suppress("UNCHECKED_CAST")
                val error = runCatching { (list as MutableList<Any?>).add(null) }.exceptionOrNull()
                assertTrue(
                    error is UnsupportedOperationException || error is ClassCastException,
                    "$text: $error",
                )
            }
            assertEquals(text, schedule.toString())
        }
    }

    private fun lists(schedule: Schedule): List<List<*>> {
        val parts: List<List<*>> =
            when (val expression = schedule.expression) {
                is ScheduleExpr.IntervalRepeat ->
                    listOfNotNull((expression.dayFilter as? DayFilter.Days)?.days)
                is ScheduleExpr.DayRepeat ->
                    listOfNotNull(expression.times, (expression.days as? DayFilter.Days)?.days)
                is ScheduleExpr.WeekRepeat -> listOf(expression.days, expression.times)
                is ScheduleExpr.MonthRepeat ->
                    listOfNotNull(expression.times, (expression.target as? MonthTarget.Days)?.specs)
                is ScheduleExpr.SingleDate -> listOf(expression.times)
                is ScheduleExpr.YearRepeat -> listOf(expression.times)
            }
        return parts + listOf(schedule.except, schedule.during)
    }

    /** A Kotlin `internal` constructor is public in bytecode, so Java could call it. */
    @Test
    fun javaCannotBuildASchedule() {
        val constructors = Schedule::class.java.declaredConstructors
        assertTrue(
            constructors.isNotEmpty() &&
                constructors.all { Modifier.isPrivate(it.modifiers) || it.isSynthetic }
        )
        val factories =
            Schedule.Companion::class.java.declaredMethods.filter {
                it.returnType == Schedule::class.java && it.name.startsWith("of")
            }
        assertTrue(factories.all { it.isSynthetic }, "$factories")
    }
}
