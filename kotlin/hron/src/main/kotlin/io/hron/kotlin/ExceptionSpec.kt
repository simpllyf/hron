package io.hron.kotlin

/** A date an `except` clause skips. Named so, as `Exception` is Kotlin's own. */
public sealed interface ExceptionSpec {
    /** This month and day in every year. */
    @ConsistentCopyVisibility
    public data class Named internal constructor(public val month: MonthName, public val day: Int) :
        ExceptionSpec

    /** Only this date, written `YYYY-MM-DD`. */
    @ConsistentCopyVisibility
    public data class Iso internal constructor(public val date: String) : ExceptionSpec
}
