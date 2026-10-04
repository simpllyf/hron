package io.hron.kotlin.internal

import java.time.LocalDate
import java.time.ZonedDateTime

internal enum class Direction {
    FORWARD,
    BACKWARD;

    val sign: Long
        get() = if (this == FORWARD) 1 else -1

    fun precedes(a: LocalDate, b: LocalDate): Boolean = if (this == FORWARD) a < b else a > b

    fun precedes(a: ZonedDateTime, b: ZonedDateTime): Boolean =
        if (this == FORWARD) a.isBefore(b) else a.isAfter(b)

    fun <T> inOrder(items: List<T>): List<T> = if (this == FORWARD) items else items.asReversed()
}
