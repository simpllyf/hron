package io.hron.kotlin.spec

import com.fasterxml.jackson.databind.JsonNode
import io.hron.kotlin.ErrorKind
import io.hron.kotlin.HronException
import io.hron.kotlin.Schedule
import io.hron.kotlin.Span
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * Every case of spec/tests.json, with the runner rules of spec/README.md, "Writing a runner". It
 * reads the JSON given, so the JVM tests and the Android tests share it.
 */
class SpecCases(testsJson: String) {
    private val spec = readJson(testsJson)

    fun parse(): List<SpecCase> =
        sections(spec["parse"]).flatMap { (section, cases) ->
            cases(cases, "parse/$section", setOf("input", "canonical")) { case, label ->
                val input = case.text("input", label)
                val canonical = case.text("canonical", label)
                val schedule = Schedule.parse(input)
                assertEquals(canonical, schedule.toString(), "$label: $input")
                assertEqualSchedules(schedule, Schedule.parse(canonical), label)
                assertEquals(canonical, Schedule.parse(canonical).toString(), "$label: roundtrip")
            }
        }

    fun parseErrors(): List<SpecCase> =
        cases(spec["parse_errors"], "parse_errors", setOf("input", "error", "display")) {
            case,
            label ->
            val input = case.text("input", label)
            val expected = case.required("error", label)
            expected.assertKnownFields(setOf("kind", "message", "span", "suggestion"), label)
            val span = expected.required("span", label)
            assertTrue(
                span.isArray && span.size() == 2 && span.all { it.isInt },
                "$label: span is not [start, end]: $span",
            )
            val error = assertFailsWith<HronException>("$label: $input") { Schedule.parse(input) }
            assertEquals(expected.text("kind", label), error.kind.name.lowercase(), "$label: kind")
            assertEquals(expected.text("message", label), error.message, "$label: message")
            assertEquals(Span(span[0].asInt(), span[1].asInt()), error.span, "$label: span")
            val suggestion =
                if (expected.has("suggestion")) expected.text("suggestion", label) else null
            assertEquals(suggestion, error.suggestion, "$label: suggestion")
            assertEquals(input, error.input, "$label: input")
            assertFalse(Schedule.validate(input), "$label: validate($input)")
            if (case.has("display")) {
                assertEquals(
                    case.text("display", label),
                    error.displayRich(),
                    "$label: displayRich",
                )
            }
        }

    fun knownSections(): List<SpecCase> =
        listOf(
            SpecCase("tests.json has only known sections") {
                spec.assertKnownFields(TOP_LEVEL_KEYS, "tests.json")
                spec["cron"].assertKnownFields(CRON_SECTIONS, "cron")
                val unknown =
                    sections(spec["eval"]).map { it.first }.filter { it !in EVAL_SECTIONS }
                assertEquals(emptyList(), unknown, "unknown eval sections")
            }
        )

    fun eval(): List<SpecCase> =
        sections(spec["eval"]).flatMap { (section, cases) ->
            cases["tests"].map { case ->
                val label = "eval/$section/${case["name"].asText()}"
                val expression = case["expression"]?.asText()
                SpecCase(label, expression) { checkEval(section, case, "$label ($expression)") }
            }
        }

    private fun checkEval(section: String, case: JsonNode, label: String) {
        val schedule = Schedule.parse(case.text("expression", label))
        when (section) {
            "matches" -> {
                case.assertKnownFields(setOf("expression", "datetime", "expected"), label)
                val datetime = parseTimestamp(case.text("datetime", label))
                val expected = case.required("expected", label)
                assertTrue(expected.isBoolean, "$label: expected is not a boolean")
                assertEquals(
                    expected.asBoolean(),
                    schedule.matches(datetime),
                    "$label: matches($datetime)",
                )
            }
            "previous_from" -> {
                case.assertKnownFields(setOf("expression", "now", "expected"), label)
                val now = parseTimestamp(case.text("now", label))
                assertEquals(
                    timestamp(case.required("expected", label), label),
                    schedule.previousFrom(now)?.let(::formatTimestamp),
                    "$label: previousFrom($now)",
                )
            }
            "occurrences" -> {
                case.assertKnownFields(setOf("expression", "from", "take", "expected"), label)
                val from = parseTimestamp(case.text("from", label))
                assertEquals(
                    case.required("expected", label).textList(label),
                    schedule
                        .occurrences(from)
                        .take(case.int("take", label))
                        .map(::formatTimestamp)
                        .toList(),
                    "$label: occurrences($from)",
                )
            }
            "between" -> {
                case.assertKnownFields(
                    setOf("expression", "from", "to", "expected", "expected_count"),
                    label,
                )
                val from = parseTimestamp(case.text("from", label))
                val to = parseTimestamp(case.text("to", label))
                val results = schedule.between(from, to).map(::formatTimestamp).toList()
                assertTrue(
                    case.has("expected") || case.has("expected_count"),
                    "$label: no expected or expected_count",
                )
                if (case.has("expected")) {
                    assertEquals(case["expected"].textList(label), results, "$label: between()")
                }
                if (case.has("expected_count")) {
                    assertEquals(
                        case.int("expected_count", label),
                        results.size,
                        "$label: between() count",
                    )
                }
            }
            else -> {
                assertTrue(section in NEXT_SECTIONS, "unknown eval section: $section")
                checkNext(schedule, case, label)
            }
        }
    }

    private fun checkNext(schedule: Schedule, case: JsonNode, label: String) {
        case.assertKnownFields(
            setOf(
                "expression",
                "now",
                "next",
                "next_date",
                "next_n",
                "next_n_count",
                "next_n_length",
            ),
            label,
        )
        assertTrue(
            NEXT_ASSERTIONS.any(case::has),
            "$label: no assertion field among $NEXT_ASSERTIONS",
        )
        val now = if (case.has("now")) parseTimestamp(case.text("now", label)) else defaultNow
        val next = schedule.nextFrom(now)
        if (case.has("next")) {
            assertEquals(
                timestamp(case["next"], label),
                next?.let(::formatTimestamp),
                "$label: nextFrom()",
            )
        }
        if (case.has("next_date")) {
            assertEquals(
                timestamp(case["next_date"], label),
                next?.toLocalDate()?.toString(),
                "$label: nextFrom() date",
            )
        }
        if (case.has("next_n")) {
            val expected = case["next_n"].textList(label)
            val n = if (case.has("next_n_count")) case.int("next_n_count", label) else expected.size
            assertEquals(
                expected,
                schedule.nextNFrom(now, n).map(::formatTimestamp),
                "$label: nextNFrom($n)",
            )
        }
        if (case.has("next_n_length")) {
            val n = case.int("next_n_count", label)
            assertEquals(
                case.int("next_n_length", label),
                schedule.nextNFrom(now, n).size,
                "$label: nextNFrom($n) length",
            )
        }
    }

    fun cron(): List<SpecCase> {
        val cron = spec["cron"]
        return cases(cron["to_cron"], "cron/to_cron", setOf("hron", "cron")) { case, label ->
            assertEquals(
                case.text("cron", label),
                Schedule.parse(case.text("hron", label)).toCron(),
                label,
            )
        } +
            cases(cron["to_cron_errors"], "cron/to_cron_errors", setOf("hron", "error")) {
                case,
                label ->
                val schedule = Schedule.parse(case.text("hron", label))
                assertCronError(case.text("error", label), label) { schedule.toCron() }
            } +
            cases(cron["from_cron"], "cron/from_cron", setOf("cron", "hron")) { case, label ->
                val schedule = Schedule.fromCron(case.text("cron", label))
                assertEquals(case.text("hron", label), schedule.toString(), label)
                assertEqualSchedules(schedule, Schedule.parse(schedule.toString()), label)
            } +
            cases(cron["from_cron_errors"], "cron/from_cron_errors", setOf("cron", "error")) {
                case,
                label ->
                assertCronError(case.text("error", label), label) {
                    Schedule.fromCron(case.text("cron", label))
                }
            } +
            cases(cron["roundtrip"], "cron/roundtrip", setOf("hron")) { case, label ->
                val cronExpr = Schedule.parse(case.text("hron", label)).toCron()
                assertEquals(cronExpr, Schedule.fromCron(cronExpr).toCron(), label)
            }
    }

    private fun assertCronError(expected: String, label: String, conversion: () -> Unit) {
        val error = assertFailsWith<HronException>(label, conversion)
        assertEquals(ErrorKind.CRON, error.kind, "$label: kind")
        assertEquals(expected, error.message, "$label: message")
    }

    fun invariants(): List<SpecCase> {
        val invariants = spec["invariants"]
        val count = invariants.int("count", "invariants")
        val rules = invariants["rules"].fieldNameList
        return invariants["tests"].flatMap { case ->
            val name = "invariants/${case["name"].asText()}"
            val expression = case["expression"]?.asText()
            rules.map { rule ->
                SpecCase("$name/$rule", expression) {
                    case.assertKnownFields(setOf("expression", "now"), name)
                    val check =
                        INVARIANTS[rule] ?: throw AssertionError("rule not implemented: $rule")
                    val text = case.text("expression", name)
                    check(
                        Invariant(
                            Schedule.parse(text),
                            parseTimestamp(case.text("now", name)),
                            count,
                            "$name ($text) $rule",
                        )
                    )
                }
            }
        }
    }

    private val defaultNow: ZonedDateTime = parseTimestamp(spec.text("now", "tests.json"))

    private fun timestamp(node: JsonNode, label: String): String? {
        if (node.isNull) return null
        assertTrue(node.isTextual, "$label: $node is not a string or null")
        return node.asText()
    }

    private fun sections(node: JsonNode): List<Pair<String, JsonNode>> =
        node.fieldNameList.filter { it != "description" }.map { it to node[it] }

    private fun cases(
        section: JsonNode,
        prefix: String,
        fields: Set<String>,
        check: (JsonNode, String) -> Unit,
    ): List<SpecCase> =
        section["tests"].map { case ->
            val label = "$prefix/${case["name"].asText()}"
            SpecCase(label) {
                case.assertKnownFields(fields, label)
                check(case, label)
            }
        }

    private fun assertEqualSchedules(expected: Schedule, actual: Schedule, label: String) {
        assertNotSame(expected, actual, label)
        assertEquals(expected, actual, "$label: equals")
        assertEquals(expected.hashCode(), actual.hashCode(), "$label: hashCode")
    }

    private companion object {
        val TOP_LEVEL_KEYS =
            setOf(
                "\$schema",
                "version",
                "description",
                "now",
                "_eval_assertion_types",
                "_behavioral_notes",
                "parse",
                "parse_errors",
                "eval",
                "cron",
                "invariants",
            )

        val CRON_SECTIONS =
            setOf(
                "description",
                "to_cron",
                "to_cron_errors",
                "from_cron",
                "from_cron_errors",
                "roundtrip",
            )

        /** Every other eval section holds nextFrom cases (spec/README.md, "Writing a runner"). */
        val NEXT_SECTIONS =
            setOf(
                "day_repeat",
                "interval_repeat",
                "month_repeat",
                "week_repeat",
                "single_date",
                "year_repeat",
                "except",
                "until",
                "except_and_until",
                "n_occurrences",
                "multi_time",
                "during",
                "day_ranges",
                "leap_year",
                "dst_spring_forward",
                "dst_fall_back",
                "timezone_default",
                "contradictory",
                "edge_cases",
            )

        val EVAL_SECTIONS =
            NEXT_SECTIONS + setOf("matches", "previous_from", "occurrences", "between")

        val NEXT_ASSERTIONS = setOf("next", "next_date", "next_n", "next_n_length")
    }
}
