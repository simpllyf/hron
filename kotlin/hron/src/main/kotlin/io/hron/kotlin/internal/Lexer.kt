package io.hron.kotlin.internal

import io.hron.kotlin.HronException
import io.hron.kotlin.IntervalUnit
import io.hron.kotlin.MonthName
import io.hron.kotlin.OrdinalPosition
import io.hron.kotlin.Span
import io.hron.kotlin.TimeOfDay
import io.hron.kotlin.Weekday

internal enum class TokenKind {
    EVERY,
    ON,
    AT,
    FROM,
    TO,
    IN,
    OF,
    THE,
    LAST,
    EXCEPT,
    UNTIL,
    STARTING,
    DURING,
    YEAR,
    DAY,
    WEEKDAY,
    WEEKEND,
    WEEKS,
    MONTH,
    NEAREST,
    NEXT,
    PREVIOUS,
    DAY_NAME,
    MONTH_NAME,
    ORDINAL,
    INTERVAL_UNIT,
    NUMBER,
    ORDINAL_NUMBER,
    TIME,
    ISO_DATE,
    COMMA,
    TIMEZONE,
}

/**
 * [start] and [end] are UTF-16 offsets into the input; [Lexer.span] converts them to code points.
 */
internal data class Token(
    val kind: TokenKind,
    val start: Int = 0,
    val end: Int = 0,
    val weekday: Weekday? = null,
    val month: MonthName? = null,
    val ordinal: OrdinalPosition? = null,
    val unit: IntervalUnit? = null,
    val number: Int = 0,
    val time: TimeOfDay? = null,
)

internal class Lexer private constructor(private val input: String) {
    private var pos = 0

    private fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (true) {
            advanceWhile(::isWhitespace)
            if (pos >= input.length) return tokens
            val start = pos
            val c = input[pos]
            val token =
                when {
                    tokens.lastOrNull()?.kind == TokenKind.IN -> {
                        advanceWhile { !isWhitespace(it) }
                        Token(TokenKind.TIMEZONE)
                    }
                    c == ',' -> {
                        pos++
                        Token(TokenKind.COMMA)
                    }
                    isAsciiLetter(c) -> word(start)
                    isAsciiDigit(c) -> digits(start)
                    else -> throw unexpectedCharacter(start)
                }
            tokens += token.copy(start = start, end = pos)
        }
    }

    private inline fun advanceWhile(matches: (Char) -> Boolean) {
        while (pos < input.length && matches(input[pos])) pos++
    }

    private fun error(message: String, start: Int): HronException =
        HronException.lex(message, span(input, start, pos), input)

    private fun word(start: Int): Token {
        advanceWhile { isAsciiLetter(it) || isAsciiDigit(it) || it == '_' }
        val text = input.substring(start, pos)
        return KEYWORDS[text.lowercase()] ?: throw error("unknown keyword '$text'", start)
    }

    private fun digits(start: Int): Token {
        advanceWhile(::isAsciiDigit)
        val digits = input.substring(start, pos)
        if (digits.length == 4 && isIsoDateTail()) {
            pos += "-MM-DD".length
            return Token(TokenKind.ISO_DATE)
        }
        if (pos < input.length && input[pos] == ':') return time(start)
        val value = numberValue(digits)
        if (value > Int.MAX_VALUE) throw error("number must be at most 2147483647", start)
        val suffix = input.substring(pos, minOf(pos + 2, input.length)).asciiLowercase()
        if (suffix in ORDINAL_SUFFIXES) {
            pos += 2
            return Token(TokenKind.ORDINAL_NUMBER, number = value.toInt())
        }
        return Token(TokenKind.NUMBER, number = value.toInt())
    }

    private fun time(start: Int): Token {
        val colon = pos
        pos++
        advanceWhile(::isAsciiDigit)
        val hour = input.substring(start, colon)
        val minute = input.substring(colon + 1, pos)
        val text = input.substring(start, pos)
        if (hour.length > 2 || minute.length != 2) {
            throw error("time must be H:MM or HH:MM, got $text", start)
        }
        val h = numberValue(hour).toInt()
        val m = numberValue(minute).toInt()
        if (h > 23 || m > 59) throw error("time must be 00:00-23:59, got $text", start)
        return Token(TokenKind.TIME, time = TimeOfDay(h, m))
    }

    private fun unexpectedCharacter(start: Int): HronException {
        val c = input.codePointAt(start)
        // `'` is excluded because `'''` would not read as a quoted character.
        val shown =
            if (c in '!'.code..'~'.code && c != '\''.code) {
                "'${c.toChar()}'"
            } else {
                "U+" + c.toString(16).uppercase().padStart(4, '0')
            }
        val span = span(input, start, start + Character.charCount(c))
        return HronException.lex("unexpected character $shown", span, input)
    }

    private fun isIsoDateTail(): Boolean =
        pos + 6 <= input.length &&
            input[pos] == '-' &&
            isAsciiDigit(input[pos + 1]) &&
            isAsciiDigit(input[pos + 2]) &&
            input[pos + 3] == '-' &&
            isAsciiDigit(input[pos + 4]) &&
            isAsciiDigit(input[pos + 5])

    companion object {
        fun tokenize(input: String): List<Token> = Lexer(input).tokenize()

        /** A lone surrogate counts as one code point. */
        fun span(input: String, start: Int, end: Int): Span {
            val first = input.codePointCount(0, start)
            return Span(first, first + input.codePointCount(start, end))
        }

        private val ORDINAL_SUFFIXES = setOf("st", "nd", "rd", "th")

        /** Stops past Int.MAX_VALUE, so a run of any length cannot overflow. */
        private fun numberValue(digits: String): Long {
            var n = 0L
            for (c in digits) {
                n = n * 10 + (c - '0')
                if (n > Int.MAX_VALUE) return n
            }
            return n
        }

        private fun isAsciiDigit(c: Char) = c in '0'..'9'

        private fun isAsciiLetter(c: Char) = c in 'a'..'z' || c in 'A'..'Z'

        /** Only these four separate tokens; any other whitespace is an unexpected character. */
        private fun isWhitespace(c: Char) = c == ' ' || c == '\t' || c == '\r' || c == '\n'

        private val KEYWORDS: Map<String, Token> = buildMap {
            fun keyword(kind: TokenKind, vararg words: String) {
                for (word in words) put(word, Token(kind))
            }
            keyword(TokenKind.EVERY, "every")
            keyword(TokenKind.ON, "on")
            keyword(TokenKind.AT, "at")
            keyword(TokenKind.FROM, "from")
            keyword(TokenKind.TO, "to")
            keyword(TokenKind.IN, "in")
            keyword(TokenKind.OF, "of")
            keyword(TokenKind.THE, "the")
            keyword(TokenKind.LAST, "last")
            keyword(TokenKind.EXCEPT, "except")
            keyword(TokenKind.UNTIL, "until")
            keyword(TokenKind.STARTING, "starting")
            keyword(TokenKind.DURING, "during")
            keyword(TokenKind.YEAR, "year", "years")
            keyword(TokenKind.DAY, "day", "days")
            keyword(TokenKind.WEEKDAY, "weekday", "weekdays")
            keyword(TokenKind.WEEKEND, "weekend", "weekends")
            keyword(TokenKind.WEEKS, "week", "weeks")
            keyword(TokenKind.MONTH, "month", "months")
            keyword(TokenKind.NEAREST, "nearest")
            keyword(TokenKind.NEXT, "next")
            keyword(TokenKind.PREVIOUS, "previous")
            for (day in Weekday.entries) {
                val token = Token(TokenKind.DAY_NAME, weekday = day)
                put(day.word, token)
                put(day.word.take(3), token)
            }
            for (month in MonthName.entries) {
                val token = Token(TokenKind.MONTH_NAME, month = month)
                put(month.word, token)
                put(month.short, token)
            }
            for (ordinal in OrdinalPosition.entries - OrdinalPosition.LAST) {
                put(ordinal.word, Token(TokenKind.ORDINAL, ordinal = ordinal))
            }
            for (word in listOf("min", "mins", "minute", "minutes")) {
                put(word, Token(TokenKind.INTERVAL_UNIT, unit = IntervalUnit.MINUTES))
            }
            for (word in listOf("hour", "hours", "hr", "hrs")) {
                put(word, Token(TokenKind.INTERVAL_UNIT, unit = IntervalUnit.HOURS))
            }
        }
    }
}
