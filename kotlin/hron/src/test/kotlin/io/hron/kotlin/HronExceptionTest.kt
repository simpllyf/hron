package io.hron.kotlin

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class HronExceptionTest {
    @Test
    fun evalAndCronErrorsRenderTheirMessageAlone() {
        assertEquals("error: no zone", HronException.eval("no zone").displayRich())
        assertEquals("error: bad cron", HronException.cron("bad cron").displayRich())
    }

    @Test
    fun onlyLexAndParseErrorsHaveASpanAndInput() {
        val lex = assertFailsWith<HronException> { Schedule.parse("every day at 9h") }
        assertEquals(
            listOf(ErrorKind.LEX, "every day at 9h", null),
            listOf(lex.kind, lex.input, lex.suggestion),
        )
        val cron = assertFailsWith<HronException> { Schedule.fromCron("* *") }
        assertEquals(ErrorKind.CRON, cron.kind)
        assertNull(cron.span)
        assertNull(cron.input)
    }

    @ParameterizedTest
    @MethodSource("codePointSpans")
    fun spansCountCodePoints(input: String, start: Int, end: Int, message: String) {
        val error = assertFailsWith<HronException> { Schedule.parse(input) }
        assertEquals(message, error.message)
        assertEquals(Span(start, end), error.span)
    }

    @Test
    fun displayRichAlignsCaretsByCodePoint() {
        val error = assertFailsWith<HronException> { Schedule.parse("😀\tevery") }
        assertEquals("error: unexpected character U+1F600\n  😀 every\n  ^", error.displayRich())
    }

    @Test
    fun displayRichAlignsCaretsAfterALoneSurrogate() {
        val error = assertFailsWith<HronException> { Schedule.parse("every \ud800 day") }
        assertEquals(
            "error: unexpected character U+D800\n  every \ud800 day\n        ^",
            error.displayRich(),
        )
    }

    /** Turkish lowercases I to dotless ı, so a default-locale fold would break `IN`. */
    @Test
    fun wordsFoldOnlyAsciiCaseWhateverTheDefaultLocale() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(
                "every day at 09:00 in UTC",
                Schedule.parse("EVERY DAY AT 09:00 IN UTC").toString(),
            )
            val error =
                assertFailsWith<HronException> {
                    Schedule.parse("every day at 09:00 in UTC \u0130N")
                }
            assertEquals("unexpected character U+0130", error.message)
            assertEquals(Span(26, 27), error.span)
        } finally {
            Locale.setDefault(saved)
        }
    }

    @ParameterizedTest
    @MethodSource("foreignSpans")
    fun displayRichToleratesASpanItDidNotMake(start: Int, end: Int, carets: String) {
        val error = HronException.lex("odd", Span(start, end), "xy")
        assertEquals("error: odd\n  xy\n  $carets", error.displayRich())
    }

    private companion object {
        /**
         * An emoji is one code point, though it is two UTF-16 units, and a combining accent is one
         * of its own, though it joins the `e` before it.
         */
        @JvmStatic
        fun codePointSpans() =
            listOf(
                Arguments.of("😀 every", 0, 1, "unexpected character U+1F600"),
                Arguments.of("évery", 0, 1, "unknown keyword 'e'"),
                Arguments.of("everý day", 5, 6, "unexpected character U+0301"),
                Arguments.of("every \ud800 day", 6, 7, "unexpected character U+D800"),
                Arguments.of("\udfff", 0, 1, "unexpected character U+DFFF"),
                Arguments.of("every day at 09:00 in \ud800😀 x", 25, 26, "unknown keyword 'x'"),
                Arguments.of("in 😀 every 😀", 11, 12, "unexpected character U+1F600"),
                Arguments.of(
                    "every day at 09:00 in 😀x",
                    22,
                    24,
                    "timezone must be UTC or an Area/Location name such as America/New_York, got 😀x",
                ),
            )

        @JvmStatic
        fun foreignSpans() =
            listOf(
                Arguments.of(-3, -5, "^"),
                Arguments.of(Int.MIN_VALUE, 0, "^"),
                Arguments.of(Int.MIN_VALUE, Int.MAX_VALUE, "^^"),
                Arguments.of(0, Int.MAX_VALUE, "^^"),
                Arguments.of(1, Int.MAX_VALUE, " ^"),
                Arguments.of(Int.MAX_VALUE, Int.MAX_VALUE, "  ^"),
                Arguments.of(Int.MAX_VALUE, Int.MIN_VALUE, "  ^"),
                Arguments.of(2, 1, "  ^"),
            )
    }
}
