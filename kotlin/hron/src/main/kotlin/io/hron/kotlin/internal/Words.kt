package io.hron.kotlin.internal

import io.hron.kotlin.MonthName
import io.hron.kotlin.OrdinalPosition
import io.hron.kotlin.TimeOfDay
import io.hron.kotlin.Weekday
import java.time.DayOfWeek
import java.time.Month
import java.util.Collections

internal val Weekday.word: String
    get() = name.lowercase()

internal val MonthName.word: String
    get() = name.lowercase()

internal val MonthName.short: String
    get() = word.take(3)

internal val MonthName.number: Int
    get() = ordinal + 1

/** February counts 29, as its day can name Feb 29. */
internal val MonthName.maxDay: Int
    get() =
        when (this) {
            MonthName.FEBRUARY -> 29
            MonthName.APRIL,
            MonthName.JUNE,
            MonthName.SEPTEMBER,
            MonthName.NOVEMBER -> 30
            else -> 31
        }

internal val OrdinalPosition.word: String
    get() = name.lowercase()

internal val TimeOfDay.minuteOfDay: Int
    get() = hour * 60 + minute

/** A cast to MutableList cannot change it. Only for a list nothing else holds. */
internal fun <T> List<T>.readOnly(): List<T> = Collections.unmodifiableList(this)

internal val Weekday.dayOfWeek: DayOfWeek
    get() = DayOfWeek.of(ordinal + 1)

internal val DayOfWeek.weekday: Weekday
    get() = Weekday.entries[value - 1]

internal val MonthName.month: Month
    get() = Month.of(number)

/** 1 to 5, or -1 for [OrdinalPosition.LAST], as `TemporalAdjusters.dayOfWeekInMonth` counts. */
internal val OrdinalPosition.n: Int
    get() = if (this == OrdinalPosition.LAST) -1 else ordinal + 1

/**
 * Folds only A-Z, whatever the default locale: Turkish would lowercase I to dotless ı, and Unicode
 * folding (as in `equals(ignoreCase = true)`) would match a Kelvin sign to k.
 */
internal fun Char.asciiLowercase(): Char = if (this in 'A'..'Z') this + ('a' - 'A') else this

internal fun String.asciiLowercase(): String {
    val chars = CharArray(length) { this[it].asciiLowercase() }
    return String(chars)
}

internal fun String.equalsAscii(other: String): Boolean =
    length == other.length &&
        indices.all { this[it].asciiLowercase() == other[it].asciiLowercase() }
