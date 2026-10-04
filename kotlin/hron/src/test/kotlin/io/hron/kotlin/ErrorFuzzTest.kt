package io.hron.kotlin

import io.hron.kotlin.spec.readJson
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Generated inputs never crash `parse`, and every one it rejects fails with an error the spec
 * describes, its span within the input in code points.
 */
class ErrorFuzzTest {
    private class Failure(val input: String, val span: Span, val spanned: String)

    private class Template(
        val kind: ErrorKind,
        val pattern: String,
        val check: (MatchResult, Failure) -> Unit = { _, _ -> },
    ) {
        // DOTALL and \z make `.` and the end anchor behave as in the Rust reference's regexes:
        // Java's `.` skips \r, U+0085, U+2028 and U+2029, and its `$` also matches before a final
        // newline.
        val regex = Regex("(?s)$pattern\\z")
    }

    private class Problem(message: String) : RuntimeException(message)

    private fun ensure(holds: Boolean, problem: () -> String) {
        if (!holds) throw Problem(problem())
    }

    /** A named group the pattern lacks reads as empty, as one that did not take part. */
    private fun MatchResult.group(name: String): String =
        try {
            groups[name]?.value ?: ""
        } catch (e: IllegalArgumentException) {
            ""
        }

    private val templates =
        listOf(
            Template(ErrorKind.LEX, "^unexpected character '(?<span>[!-&(-~])'") { _, f ->
                val c = f.spanned.firstOrNull() ?: ' '
                val startsToken = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == ','
                ensure(!startsToken) { "'$c' starts a token, so it is never unexpected" }
            },
            Template(ErrorKind.LEX, "^unexpected character U\\+(?<code>[0-9A-F]{4,})") { m, f ->
                val shown = m.group("code").toInt(16)
                val quotable = shown in 0x21..0x7e && shown != 0x27
                val s = f.spanned
                ensure(
                    s.codePointCount(0, s.length) == 1 && s.codePointAt(0) == shown && !quotable
                ) {
                    "U+${m.group("code")} does not describe '$s'"
                }
            },
            Template(ErrorKind.LEX, "^unknown keyword '(?<span>[A-Za-z][A-Za-z0-9_]*)'"),
            Template(
                ErrorKind.LEX,
                "^time must be H:MM or HH:MM, got (?<span>(?<hour>[0-9]+):(?<minute>[0-9]*))",
            ) { m, _ ->
                val hour = m.group("hour")
                val minute = m.group("minute")
                ensure(hour.isEmpty() || hour.length > 2 || minute.length != 2) {
                    "$hour:$minute is H:MM or HH:MM"
                }
            },
            Template(
                ErrorKind.LEX,
                "^time must be 00:00-23:59, got (?<span>(?<hour>[0-9]{1,2}):(?<minute>[0-9]{2}))",
            ) { m, _ ->
                val hour = value(m.group("hour"))
                val minute = value(m.group("minute"))
                ensure(hour > 23 || minute > 59) { "$hour:$minute is in range" }
            },
            Template(ErrorKind.LEX, "^number must be at most 2147483647") { _, f ->
                val s = f.spanned
                ensure(s.isNotEmpty() && s.all { it in '0'..'9' } && value(s) > Int.MAX_VALUE) {
                    "'$s' is not digits above 2147483647"
                }
            },
            Template(ErrorKind.PARSE, "^empty expression") { _, f ->
                ensure(trimmedEnd(f.input) == 0 && f.span == Span(0, 0)) {
                    "empty expression with span ${f.span} for ${f.input}"
                }
            },
            Template(
                ErrorKind.PARSE,
                "^expected (?:$WHAT), got (?:'(?<span>.+)'|(?<end>end of input))",
            ) { m, f ->
                if (m.group("end").isNotEmpty()) {
                    val end = f.input.codePointCount(0, trimmedEnd(f.input))
                    ensure(f.span == Span(end, end)) {
                        "end of input at ${f.span}, expected $end..$end"
                    }
                }
            },
            Template(ErrorKind.PARSE, "^interval must be 1-2147483647, got (?<span>[0-9]+)") { _, f
                ->
                ensure(value(f.spanned) == 0L) { "interval ${f.spanned} is valid" }
            },
            Template(ErrorKind.PARSE, "^day must be 1-31, got (?<span>$DAY)") { _, f ->
                val day = value(f.spanned)
                ensure(day == 0L || day > 31) { "day $day is within 1-31" }
            },
            Template(
                ErrorKind.PARSE,
                "^day must be 1-(?<max>[0-9]+) for (?<month>$MONTH), got (?<span>$DAY)",
            ) { m, f ->
                val month = m.group("month")
                val length =
                    when (month) {
                        "feb" -> 29L
                        "apr",
                        "jun",
                        "sep",
                        "nov" -> 30L
                        else -> 31L
                    }
                val max = value(m.group("max"))
                val day = value(f.spanned)
                ensure(max == length && day > max && day <= 31) {
                    "day $day against 1-$max for $month"
                }
            },
            Template(
                ErrorKind.PARSE,
                "^day range must not run backwards: (?<a>$DAY) to (?<b>$DAY)",
            ) { m, f ->
                val a = m.group("a")
                val b = m.group("b")
                ensure(f.spanned.startsWith(a) && f.spanned.endsWith(b) && value(a) > value(b)) {
                    "$a to $b against the span '${f.spanned}'"
                }
            },
            Template(
                ErrorKind.PARSE,
                "^time window must not run backwards: (?<from>$TIME) to (?<to>$TIME)" +
                    " \\(a window cannot cross midnight\\)",
            ) { m, f ->
                val from = m.group("from")
                val to = m.group("to")
                ensure(
                    f.spanned.startsWith(from) &&
                        f.spanned.endsWith(to) &&
                        minutes(from) > minutes(to)
                ) {
                    "$from to $to against the span '${f.spanned}'"
                }
            },
            Template(
                ErrorKind.PARSE,
                "^date must be a calendar date from 0001-01-01 to 9999-12-31, got" +
                    " (?<span>[0-9]{4}-[0-9]{2}-[0-9]{2})",
            ) { _, f ->
                ensure(!isCalendarDate(f.spanned)) { "${f.spanned} is a calendar date" }
            },
            Template(
                ErrorKind.PARSE,
                "^timezone must be UTC or an Area/Location name such as America/New_York, got" +
                    " (?<span>.+)",
            ),
            Template(
                ErrorKind.PARSE,
                "^duplicate '(?<keyword>except|until|starting|during|in)' clause",
            ) { m, f ->
                ensure(m.group("keyword") == f.spanned.asciiLowercase()) {
                    "duplicate '${m.group("keyword")}' but the span holds '${f.spanned}'"
                }
            },
            Template(ErrorKind.PARSE, "^'(?<keyword>[a-z]+)' must come before '(?<last>[a-z]+)'") {
                m,
                f ->
                val keyword = m.group("keyword")
                val last = m.group("last")
                val k = CLAUSE_ORDER.indexOf(keyword)
                val l = CLAUSE_ORDER.indexOf(last)
                ensure(k >= 0 && l >= 0 && k < l && keyword == f.spanned.asciiLowercase()) {
                    "'$keyword' before '$last' with the span '${f.spanned}'"
                }
            },
            Template(ErrorKind.PARSE, "^unexpected '(?<span>.+)' after the schedule"),
            Template(
                ErrorKind.PARSE,
                "^until (?<month>$MONTH) (?<day>[1-9][0-9]?) has no year: add a starting date, or use" +
                    " an ISO date",
            ) { m, f ->
                val spanned = f.spanned
                val words = spanned.split(' ', '\t', '\r', '\n').filter { it.isNotEmpty() }
                val endsAtDay = spanned.isNotEmpty() && !isSeparator(spanned.last())
                val matchesMessage =
                    endsAtDay &&
                        words.size == 3 &&
                        words[0].asciiLowercase() == "until" &&
                        words[1].asciiLowercase().startsWith(m.group("month")) &&
                        words[2][0] in '0'..'9' &&
                        value(words[2]).toString() == m.group("day")
                ensure(matchesMessage) {
                    "the span '$spanned' is not 'until ${m.group("month")} ${m.group("day")}'"
                }
            },
        )

    /** SplitMix64: a fixed seed gives the same inputs on every platform. */
    private class Rng(private var state: Long) {
        fun next(): Long {
            state += -0x61c8864680b583ebL
            var z = state
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }

        fun below(n: Int): Int = java.lang.Long.remainderUnsigned(next(), n.toLong()).toInt()

        fun <T> pick(items: List<T>): T = items[below(items.size)]
    }

    private val corpus: List<String> by lazy {
        val spec = readJson(specFile("tests.json"))
        spec["parse"]
            .filter { it.has("tests") }
            .flatMap { section -> section["tests"].map { it["input"].asText() } } +
            spec["parse_errors"]["tests"].map { it["input"].asText() }
    }

    private fun randomText(rng: Rng): String = buildString {
        repeat(rng.below(12) + 1) { append(rng.pick(SEPARATORS)).append(rng.pick(FRAGMENTS)) }
    }

    private fun mutate(rng: Rng, input: String): String {
        val words = input.split(" ").toMutableList()
        val i = rng.below(words.size)
        when (rng.below(7)) {
            0 -> words.removeAt(i)
            1 -> {
                val j = rng.below(words.size)
                words[i] = words[j].also { words[j] = words[i] }
            }
            2 -> words.add(rng.below(words.size + 1), words[i])
            3 -> {
                val keep = rng.below(input.codePointCount(0, input.length) + 1)
                return input.substring(0, input.offsetByCodePoints(0, keep))
            }
            4 -> words[i] = words[i].map { if (it in 'a'..'z') it - 32 else it }.joinToString("")
            5 -> words[i] = rng.pick(FRAGMENTS)
            else -> {
                val fragment = rng.pick(FRAGMENTS)
                val word = words[i]
                val at =
                    word.offsetByCodePoints(0, rng.below(word.codePointCount(0, word.length) + 1))
                words[i] = word.substring(0, at) + fragment + word.substring(at)
            }
        }
        return words.joinToString(" ")
    }

    private fun withClauses(rng: Rng, input: String): String = buildString {
        append(input)
        repeat(rng.below(4) + 1) { append(' ').append(rng.pick(CLAUSES)) }
    }

    private fun generate(rng: Rng): String =
        when (rng.below(4)) {
            0 -> randomText(rng)
            1 -> withClauses(rng, rng.pick(corpus))
            else -> {
                var input = rng.pick(corpus)
                repeat(rng.below(4)) { input = mutate(rng, input) }
                input
            }
        }

    private fun checkedTemplateIndex(input: String, error: HronException): Int {
        ensure(error.kind == ErrorKind.LEX || error.kind == ErrorKind.PARSE) {
            "neither lex nor parse: ${error.kind}"
        }
        ensure(!Schedule.validate(input)) { "validate is true" }
        ensure(error.input == input) { "error input is ${error.input}" }
        val span = error.span ?: throw Problem("no span")
        val length = input.codePointCount(0, input.length)
        ensure(span.start in 0..span.end && span.end <= length) { "span $span outside 0..=$length" }
        val spanned =
            input.substring(
                input.offsetByCodePoints(0, span.start),
                input.offsetByCodePoints(0, span.end),
            )
        val failure = Failure(input, span, spanned)

        val message = error.message
        val index = templates.indexOfFirst {
            it.kind == error.kind && it.regex.containsMatchIn(message)
        }
        ensure(index >= 0) { "${error.kind} message '$message' matches no template" }
        val match = templates[index].regex.find(message)!!
        val echoed = match.group("span")
        if ("(?<span>" in templates[index].pattern && match.groups["span"] != null) {
            ensure(echoed == spanned) { "message echoes '$echoed' but the span holds '$spanned'" }
        }
        templates[index].check(match, failure)

        val expectedSuggestion =
            if (message.startsWith("until ")) {
                "until ${match.group("month")} ${match.group("day")} starting YYYY-MM-DD"
            } else {
                null
            }
        ensure(error.suggestion == expectedSuggestion) {
            "suggestion ${error.suggestion}, expected $expectedSuggestion"
        }
        val rich = error.displayRich()
        ensure(rich.split("\n").size == 3 && rich.startsWith("error: $message\n")) {
            "displayRich is not three lines: $rich"
        }
        return index
    }

    @Test
    fun generatedInputsFailOnlyWithSpecErrors() {
        val rng = Rng(SEED)
        val hits = IntArray(templates.size)
        var parsed = 0
        val failures = mutableListOf<String>()
        repeat(INPUTS) {
            val input = generate(rng)
            try {
                Schedule.parse(input)
                parsed++
            } catch (error: HronException) {
                try {
                    hits[checkedTemplateIndex(input, error)]++
                } catch (problem: Problem) {
                    failures += "${quoted(input)}: ${problem.message}"
                }
            } catch (e: RuntimeException) {
                failures += "${quoted(input)}: parse threw $e"
            } catch (e: StackOverflowError) {
                failures += "${quoted(input)}: parse threw $e"
            }
        }
        assertEquals(emptyList(), failures.take(20), "${failures.size} failures, first ones")
        assertTrue(parsed > INPUTS / 20, "only $parsed inputs parsed; the generator has drifted")
        val unused = templates.indices.filter { hits[it] == 0 }.map { templates[it].pattern }
        assertEquals(emptyList(), unused, "templates no input produced")
    }

    private companion object {
        const val INPUTS = 6000
        const val SEED = 0x5EED_4A0EL

        val WHAT =
            listOf(
                    "'every' or 'on'",
                    "'day', 'weekday', 'weekend', a day name, 'week', 'month', 'year' or a number",
                    "a unit \\('min', 'hours', 'days', 'weeks', 'months' or 'years'\\)",
                    "'at'",
                    "a time \\(HH:MM\\)",
                    "'from'",
                    "'to'",
                    "'day', 'weekday', 'weekend' or a day name",
                    "'on'",
                    "a day name",
                    "'the'",
                    "a day such as 15th, 'last', an ordinal such as 'first', 'next', 'previous' or 'nearest'",
                    "'day', 'weekday' or a day name",
                    "'nearest'",
                    "'weekday'",
                    "a day such as 15th",
                    "a month name or 'the'",
                    "a day such as 15th, 'last' or an ordinal such as 'first'",
                    "'weekday' or a day name",
                    "'of'",
                    "a month name",
                    "a day number",
                    "a date \\(YYYY-MM-DD, or a month and day\\)",
                    "a date \\(YYYY-MM-DD\\)",
                    "a timezone",
                )
                .joinToString("|")
        const val MONTH = "jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec"
        const val DAY = "[0-9]+(?i:st|nd|rd|th)?"
        const val TIME = "[0-9]{1,2}:[0-9]{2}"
        val CLAUSE_ORDER = listOf("except", "until", "starting", "during", "in")

        val FRAGMENTS =
            listOf(
                "every",
                "on",
                "at",
                "from",
                "to",
                "in",
                "IN",
                "of",
                "the",
                "last",
                "except",
                "until",
                "starting",
                "during",
                "nearest",
                "next",
                "previous",
                "day",
                "Days",
                "weekdays",
                "weekend",
                "week",
                "month",
                "years",
                "min",
                "hrs",
                "monday",
                "FRI",
                "jan",
                "february",
                "first",
                "fifth",
                "0",
                "1",
                "00",
                "15th",
                "31ST",
                "2nd",
                "2147483647",
                "2147483648",
                "99999999999999999999",
                "09:00",
                "9:5",
                "24:00",
                "9:",
                "17:30",
                "2026-02-28",
                "2026-02-30",
                "0000-01-01",
                "12026-03-15",
                ",",
                ":",
                "-",
                "/",
                "'",
                "\"",
                "#",
                "~",
                "_",
                "UTC",
                "America/New_York",
                "Nope/Zone",
                "Europe/İstanbul",
                "é",
                "é",
                "K",
                " ",
                " ",
                "﻿",
                "０",
                "😀",
                String(Character.toChars(0x10FFFF)),
                String(Character.toChars(0x1D7D8)),
                "\ud800",
                "\udfff",
                "\u0000",
                "\u000b",
                "\u000c",
                "\u007f",
                "\u001b",
            )
        val SEPARATORS = listOf("", " ", " ", " ", "  ", "\t", "\r\n", "\n")
        val CLAUSES =
            listOf(
                "except dec 25",
                "except 2026-12-25, jan 1",
                "until 2027-12-31",
                "until dec 31",
                "starting 2026-01-01",
                "during jan, jul",
                "in UTC",
                "IN America/New_York",
            )

        /** Saturates, since a digit run can be thousands of digits long. */
        fun value(text: String): Long {
            var n = 0L
            for (c in text) {
                if (c !in '0'..'9') break
                n = if (n > (Long.MAX_VALUE - 9) / 10) Long.MAX_VALUE else n * 10 + (c - '0')
            }
            return n
        }

        fun isSeparator(c: Char) = c == ' ' || c == '\t' || c == '\r' || c == '\n'

        fun String.asciiLowercase() = map { if (it in 'A'..'Z') it + 32 else it }.joinToString("")

        fun trimmedEnd(input: String): Int {
            var end = input.length
            while (end > 0 && isSeparator(input[end - 1])) end--
            return end
        }

        fun isCalendarDate(date: String): Boolean =
            try {
                LocalDate.parse(date).year >= 1
            } catch (e: DateTimeParseException) {
                false
            }

        fun minutes(time: String): Long {
            val (hour, minute) = time.split(":")
            return value(hour) * 60 + value(minute)
        }

        fun quoted(input: String): String = buildString {
            append('"')
            input.codePoints().forEach { c ->
                if (c < 0x20 || c == 0x7f || c in 0xD800..0xDFFF) append("\\u%04x".format(c))
                else appendCodePoint(c)
            }
            append('"')
        }
    }
}
