package io.hron.kotlin

public sealed interface DateSpec {
    /** This month and day in every year. */
    @ConsistentCopyVisibility
    public data class Named internal constructor(public val month: MonthName, public val day: Int) :
        DateSpec

    /** Only this date, written `YYYY-MM-DD`. */
    @ConsistentCopyVisibility
    public data class Iso internal constructor(public val date: String) : DateSpec
}
