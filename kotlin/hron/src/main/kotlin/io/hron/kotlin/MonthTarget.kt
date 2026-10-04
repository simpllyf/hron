package io.hron.kotlin

public sealed interface MonthTarget {
    @ConsistentCopyVisibility
    public data class Days internal constructor(public val specs: List<DayOfMonthSpec>) :
        MonthTarget

    public data object LastDay : MonthTarget

    public data object LastWeekday : MonthTarget

    /**
     * Without a [direction] the weekday stays within the month, as cron's `W` does; with one it can
     * cross into the adjacent month.
     */
    @ConsistentCopyVisibility
    public data class NearestWeekday
    internal constructor(public val day: Int, public val direction: NearestDirection?) : MonthTarget

    @ConsistentCopyVisibility
    public data class OrdinalWeekday
    internal constructor(public val ordinal: OrdinalPosition, public val weekday: Weekday) :
        MonthTarget
}
