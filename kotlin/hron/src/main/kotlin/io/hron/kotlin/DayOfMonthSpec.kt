package io.hron.kotlin

public sealed interface DayOfMonthSpec {
    @ConsistentCopyVisibility
    public data class Single internal constructor(public val day: Int) : DayOfMonthSpec

    /** From [start] through [end], both included. */
    @ConsistentCopyVisibility
    public data class Range internal constructor(public val start: Int, public val end: Int) :
        DayOfMonthSpec
}
