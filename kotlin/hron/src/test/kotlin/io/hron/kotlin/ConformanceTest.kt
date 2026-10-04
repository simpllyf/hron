package io.hron.kotlin

import io.hron.kotlin.spec.SpecCase
import io.hron.kotlin.spec.SpecCases
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class ConformanceTest {
    private val spec = SpecCases(specFile("tests.json"))

    @TestFactory fun parse() = tests(spec.parse())

    @TestFactory fun parseErrors() = tests(spec.parseErrors())

    @TestFactory fun knownSections() = tests(spec.knownSections())

    @TestFactory fun eval() = tests(spec.eval())

    @TestFactory fun cron() = tests(spec.cron())

    @TestFactory fun invariants() = tests(spec.invariants())

    private fun tests(cases: List<SpecCase>): List<DynamicTest> = cases.map {
        DynamicTest.dynamicTest(it.name, it.check)
    }
}
