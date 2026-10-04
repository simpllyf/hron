package io.hron.kotlin

import io.hron.kotlin.spec.SpecCases
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith

/**
 * The runner fails what it cannot check (spec/README.md, "Writing a runner"). Each case is
 * checkable but for the one thing under test, and the failure must name the rule that caught it.
 */
class RunnerRulesTest {
    private val now = "2026-02-06T12:00:00+00:00[UTC]"
    private val next = "2026-02-07T09:00:00+00:00[UTC]"

    private fun spec(
        eval: String = "{}",
        parse: String = "{}",
        cron: String = CRON,
        invariants: String = """{"count": 1, "rules": {}, "tests": []}""",
        extra: String = "",
    ) =
        SpecCases(
            """{"now": "$now", "parse": $parse, "parse_errors": {"tests": []}, "cron": $cron,
            "eval": $eval, "invariants": $invariants $extra}"""
        )

    private fun evalCase(fields: String, section: String = "day_repeat") =
        spec(
            eval =
                """{"$section": {"tests": [{"name": "x", "expression": "every day at 09:00", $fields}]}}"""
        )

    private fun SpecCases.all() =
        knownSections() + parse() + parseErrors() + eval() + cron() + invariants()

    private fun assertFailsOn(cases: SpecCases, rule: String) {
        val error = assertFailsWith<AssertionError> { cases.all().forEach { it.check() } }
        assertContains(error.message.orEmpty(), rule)
    }

    @Test fun aCheckableCasePasses() = evalCase(""""next": "$next"""").all().forEach { it.check() }

    @Test
    fun anUnknownEvalSectionFails() =
        assertFailsOn(
            evalCase(""""next": "$next"""", section = "new_section"),
            "unknown eval section",
        )

    @Test
    fun anUnknownTopLevelKeyFails() =
        assertFailsOn(spec(extra = """, "new_key": {}"""), "tests.json: unknown fields")

    @Test
    fun anUnknownCronSectionFails() =
        assertFailsOn(
            spec(cron = CRON.dropLast(1) + """, "new_section": {"tests": []}}"""),
            "cron: unknown fields",
        )

    @Test
    fun anUnknownFieldFails() =
        assertFailsOn(evalCase(""""next": "$next", "next_weekday": "saturday""""), "unknown fields")

    @Test
    fun anUnknownFieldInAParseCaseFails() =
        assertFailsOn(
            spec(
                parse =
                    """{"days": {"tests": [{"name": "x", "input": "every day at 9:00",
                    "canonical": "every day at 09:00", "display": "every day at 09:00"}]}}"""
            ),
            "unknown fields",
        )

    @Test
    fun anUnknownFieldInACronCaseFails() =
        assertFailsOn(
            spec(
                cron =
                    CRON.replace(
                        """"to_cron": {"tests": []}""",
                        """"to_cron": {"tests": [{"name": "x",
                "hron": "every day at 09:00", "cron": "0 9 * * *", "zone": "UTC"}]}""",
                    )
            ),
            "unknown fields",
        )

    @Test
    fun aCaseWithNoAssertionFails() =
        assertFailsOn(evalCase(""""now": "$now""""), "no assertion field")

    /** A null is an assertion of no occurrence, not an absent field. */
    @Test
    fun aNullAssertionIsChecked() =
        assertFailsOn(evalCase(""""next": null, "next_date": "2026-02-07""""), "nextFrom()")

    @Test
    fun aNonStringTimestampFails() =
        assertFailsOn(evalCase(""""next": false"""), "is not a string or null")

    @Test fun aNullListFails() = assertFailsOn(evalCase(""""next_n": null"""), "expected a list")

    @Test
    fun aNullBetweenListFails() =
        assertFailsOn(
            evalCase(""""from": "$now", "to": "$next", "expected": null""", section = "between"),
            "expected a list",
        )

    @Test
    fun aFractionalCountFails() =
        assertFailsOn(
            evalCase(
                """"from": "$now", "to": "$next", "expected_count": 0.5""",
                section = "between",
            ),
            "is not an integer",
        )

    @Test
    fun aNullBooleanFails() =
        assertFailsOn(
            evalCase(""""datetime": "$now", "expected": null""", section = "matches"),
            "is not a boolean",
        )

    @Test
    fun anUnimplementedInvariantFails() =
        assertFailsOn(
            spec(
                invariants =
                    """{"count": 1, "rules": {"new_rule": "x"},
                    "tests": [{"name": "x", "expression": "every day at 09:00", "now": "$now"}]}"""
            ),
            "rule not implemented",
        )

    private companion object {
        const val CRON =
            """{"to_cron": {"tests": []}, "to_cron_errors": {"tests": []}, "from_cron": {"tests": []},
            "from_cron_errors": {"tests": []}, "roundtrip": {"tests": []}}"""
    }
}
