package io.hron.kotlin

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import io.hron.kotlin.spec.fieldNameList
import io.hron.kotlin.spec.parseTimestamp
import io.hron.kotlin.spec.readJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Every member of spec/api.json, called through its Kotlin name from the `kotlin` note. Without
 * kotlin-reflect a class's members cannot be listed, so each name maps to a call by hand.
 */
class ApiConformanceTest {
    private val api = readJson(specFile("api.json"))
    private val now = parseTimestamp("2026-02-06T12:00:00+00:00[UTC]")
    private val schedule = Schedule.parse("every day at 09:00 except dec 25 in America/New_York")
    private val error =
        HronException.parse(
            "expected 'at', got end of input",
            Span(9, 9),
            "every day",
            "every day at 09:00",
        )

    private val calls: Map<String, () -> Unit> =
        mapOf(
            "schedule.staticMethods.parse" to
                {
                    assertEquals(
                        "every day at 09:00",
                        Schedule.parse("every day at 9:00").toString(),
                    )
                },
            "schedule.staticMethods.fromCron" to
                {
                    assertEquals(
                        "every weekday at 09:00",
                        Schedule.fromCron("0 9 * * 1-5").toString(),
                    )
                },
            "schedule.staticMethods.validate" to
                {
                    assertTrue(Schedule.validate("every day at 09:00"))
                    assertFalse(Schedule.validate("every day"))
                },
            "schedule.instanceMethods.nextFrom" to
                {
                    assertEquals(
                        parseTimestamp("2026-02-06T09:00:00-05:00[America/New_York]"),
                        schedule.nextFrom(now),
                    )
                },
            "schedule.instanceMethods.nextNFrom" to
                {
                    assertEquals(2, schedule.nextNFrom(now, 2).size)
                },
            "schedule.instanceMethods.previousFrom" to
                {
                    assertEquals(
                        parseTimestamp("2026-02-05T09:00:00-05:00[America/New_York]"),
                        schedule.previousFrom(now),
                    )
                },
            "schedule.instanceMethods.matches" to
                {
                    assertTrue(schedule.matches(parseTimestamp("2026-02-06T14:00:00+00:00[UTC]")))
                },
            "schedule.instanceMethods.occurrences" to
                {
                    assertEquals(
                        schedule.nextNFrom(now, 3),
                        schedule.occurrences(now).take(3).toList(),
                    )
                },
            "schedule.instanceMethods.between" to
                {
                    val to = parseTimestamp("2026-02-09T14:00:00+00:00[UTC]")
                    assertEquals(4, schedule.between(now, to).count())
                },
            "schedule.instanceMethods.toCron" to
                {
                    assertEquals("0 9 * * *", Schedule.parse("every day at 09:00").toCron())
                },
            "schedule.instanceMethods.toString" to
                {
                    assertEquals(
                        "every day at 09:00 except dec 25 in America/New_York",
                        schedule.toString(),
                    )
                },
            "schedule.instanceMethods.equals" to
                {
                    assertEquals(schedule, Schedule.parse(schedule.toString()))
                    assertFalse(schedule.equals(schedule.toString()))
                    assertFalse(schedule.equals(null))
                },
            "schedule.getters.timezone" to { assertEquals("America/New_York", schedule.timezone) },
            "schedule.getters.expression" to
                {
                    assertEquals(
                        Schedule.parse("every day at 09:00").expression,
                        schedule.expression,
                    )
                },
            "schedule.getters.except" to
                {
                    assertEquals(
                        listOf(ExceptionSpec.Named(MonthName.DECEMBER, 25)),
                        schedule.except,
                    )
                },
            "schedule.getters.until" to { assertNull(schedule.until) },
            "schedule.getters.starting" to { assertNull(schedule.starting) },
            "schedule.getters.during" to { assertEquals(emptyList(), schedule.during) },
            "error.kinds.lex" to
                {
                    assertEquals(ErrorKind.LEX, HronException.lex("m", Span(0, 1), "x").kind)
                },
            "error.kinds.parse" to { assertEquals(ErrorKind.PARSE, error.kind) },
            "error.kinds.eval" to { assertEquals(ErrorKind.EVAL, HronException.eval("m").kind) },
            "error.kinds.cron" to { assertEquals(ErrorKind.CRON, HronException.cron("m").kind) },
            "error.properties.kind" to { assertEquals(ErrorKind.PARSE, error.kind) },
            "error.properties.message" to
                {
                    assertEquals("expected 'at', got end of input", error.message)
                },
            "error.properties.span" to { assertEquals(Span(9, 9), error.span) },
            "error.properties.input" to { assertEquals("every day", error.input) },
            "error.properties.suggestion" to
                {
                    assertEquals("every day at 09:00", error.suggestion)
                },
            "error.methods.displayRich" to
                {
                    assertEquals(
                        "error: expected 'at', got end of input\n  every day\n           ^ try: \"every day at 09:00\"",
                        error.displayRich(),
                    )
                },
            "error.constructors.lex" to
                {
                    val lex = HronException.lex("m", Span(0, 1), "x")
                    assertEquals(
                        listOf(ErrorKind.LEX, Span(0, 1), "x", null),
                        listOf(lex.kind, lex.span, lex.input, lex.suggestion),
                    )
                },
            "error.constructors.parse" to
                {
                    assertEquals(
                        listOf(ErrorKind.PARSE, "every day at 09:00"),
                        listOf(error.kind, error.suggestion),
                    )
                },
            "error.constructors.eval" to
                {
                    val eval = HronException.eval("m")
                    assertEquals(
                        listOf(ErrorKind.EVAL, "m", null, null),
                        listOf(eval.kind, eval.message, eval.span, eval.input),
                    )
                },
            "error.constructors.cron" to
                {
                    val cron = HronException.cron("m")
                    assertEquals(
                        listOf(ErrorKind.CRON, "m", null, null),
                        listOf(cron.kind, cron.message, cron.span, cron.input),
                    )
                },
        )

    /** Each member api.json lists, as `group.list.name`. */
    private fun members(api: JsonNode): List<String> = LISTS.flatMap { (group, list) ->
        api[group][list].map {
            "$group.$list.${if (it.isTextual) it.asText() else it["name"].asText()}"
        }
    }

    @Test
    fun everyMemberHasACall() {
        assertTrue(members(api).size >= 30)
        assertEquals(emptyList(), members(api).filter { it !in calls })
    }

    /** A list api.json adds would otherwise go unchecked. */
    @Test
    fun everyListOfApiJsonIsChecked() {
        for (group in LISTS.map { it.first }.distinct()) {
            val lists = api[group].fieldNameList.filter { api[group][it].isArray }
            assertEquals(
                LISTS.filter { it.first == group }.map { it.second }.sorted(),
                lists.sorted(),
                group,
            )
        }
    }

    @Test
    fun everyCallNamesAMember() {
        assertEquals(members(api).toSet(), calls.keys)
    }

    @TestFactory
    fun call() =
        members(api).map { member -> DynamicTest.dynamicTest(member) { calls.getValue(member)() } }

    @Test
    fun aMemberWithoutACallFails() {
        val withFake = api.deepCopy<JsonNode>()
        (withFake["schedule"]["instanceMethods"] as ArrayNode).addObject().put("name", "fakeMethod")
        assertEquals(
            listOf("schedule.instanceMethods.fakeMethod"),
            members(withFake).filter { it !in calls },
        )
    }

    private companion object {
        val LISTS =
            listOf(
                "schedule" to "staticMethods",
                "schedule" to "instanceMethods",
                "schedule" to "getters",
                "error" to "kinds",
                "error" to "properties",
                "error" to "methods",
                "error" to "constructors",
            )
    }
}
