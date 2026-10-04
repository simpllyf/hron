package io.hron.kotlin

import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The examples of kotlin/README.md, as written there. Each test records what the example prints and
 * checks it against the README's comments.
 */
class ReadmeTest {
    private val lines = mutableListOf<String>()

    private fun println(value: Any?) {
        lines += value.toString()
    }

    @Test
    fun usage() {
        val schedule = Schedule.parse("every weekday at 9:00 in America/New_York")
        val now = ZonedDateTime.parse("2026-02-06T12:00:00Z")

        println(schedule.nextFrom(now)) // 2026-02-06T09:00-05:00[America/New_York]
        println(schedule.previousFrom(now)) // 2026-02-05T09:00-05:00[America/New_York]
        println(schedule.matches(now)) // false
        println(schedule.nextNFrom(now, 5).size) // 5

        for (time in schedule.occurrences(now).drop(1).take(2)) {
            println(time)
        }

        println(schedule) // every weekday at 09:00 in America/New_York
        println(Schedule.parse("every day at 9:00").toCron()) // 0 9 * * *
        println(Schedule.fromCron("0 16 * * 5L")) // every month on the last friday at 16:00
        println(Schedule.validate("every day at 25:00")) // false

        assertEquals(
            listOf(
                "2026-02-06T09:00-05:00[America/New_York]",
                "2026-02-05T09:00-05:00[America/New_York]",
                "false",
                "5",
                "2026-02-09T09:00-05:00[America/New_York]",
                "2026-02-10T09:00-05:00[America/New_York]",
                "every weekday at 09:00 in America/New_York",
                "0 9 * * *",
                "every month on the last friday at 16:00",
                "false",
            ),
            lines,
        )
    }

    @Test
    fun parts() {
        val parts =
            Schedule.parse(
                "every weekday at 9:00 except dec 25 starting 2026-01-01 during jan, dec in america/new_york"
            )
        println(parts.timezone) // America/New_York
        println(parts.starting) // 2026-01-01
        println(parts.until) // null
        println(parts.during) // [JANUARY, DECEMBER]

        when (val expression = parts.expression) {
            is ScheduleExpr.DayRepeat -> println(expression.times) // [09:00]
            else -> println("another kind")
        }

        when (val exception = parts.except[0]) {
            is ExceptionSpec.Named -> println("${exception.month} ${exception.day}") // DECEMBER 25
            is ExceptionSpec.Iso -> println(exception.date)
        }

        assertEquals(
            listOf(
                "America/New_York",
                "2026-01-01",
                "null",
                "[JANUARY, DECEMBER]",
                "[09:00]",
                "DECEMBER 25",
            ),
            lines,
        )
    }

    @Test
    fun equality() {
        val nine = Schedule.parse("every day at 9:00")
        println(nine == Schedule.parse("every day at 09:00")) // true
        println(nine.hashCode() == Schedule.parse("every day at 09:00").hashCode()) // true
        println(nine == Schedule.fromCron("0 9 * * *")) // true
        println(
            Schedule.parse("every monday, friday at 09:00") ==
                Schedule.parse("every friday, monday at 09:00")
        ) // false

        assertEquals(listOf("true", "true", "true", "false"), lines)
    }

    @Test
    fun errors() {
        try {
            Schedule.parse("every weekday at 09:00 until dec 31")
        } catch (error: HronException) {
            println(error.kind) // PARSE
            println(error.displayRich())
        }

        assertEquals(
            listOf(
                "PARSE",
                """
                error: until dec 31 has no year: add a starting date, or use an ISO date
                  every weekday at 09:00 until dec 31
                                         ^^^^^^^^^^^^ try: "until dec 31 starting YYYY-MM-DD"
                """
                    .trimIndent(),
            ),
            lines,
        )
    }
}
