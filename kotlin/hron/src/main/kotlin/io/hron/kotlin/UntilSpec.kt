package io.hron.kotlin

public sealed interface UntilSpec {
    /** Written `YYYY-MM-DD`. */
    @ConsistentCopyVisibility
    public data class Iso internal constructor(public val date: String) : UntilSpec

    /** The first such date on or after the schedule's `starting` date. */
    @ConsistentCopyVisibility
    public data class Named internal constructor(public val month: MonthName, public val day: Int) :
        UntilSpec
}
