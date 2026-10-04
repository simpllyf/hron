package io.hron.kotlin

public sealed interface YearTarget {
    public val month: MonthName

    /** Written `dec 25`. */
    @ConsistentCopyVisibility
    public data class Date
    internal constructor(override val month: MonthName, public val day: Int) : YearTarget

    @ConsistentCopyVisibility
    public data class OrdinalWeekday
    internal constructor(
        public val ordinal: OrdinalPosition,
        public val weekday: Weekday,
        override val month: MonthName,
    ) : YearTarget

    /** Written `the 25th of dec`. */
    @ConsistentCopyVisibility
    public data class DayOfMonth
    internal constructor(public val day: Int, override val month: MonthName) : YearTarget

    @ConsistentCopyVisibility
    public data class LastWeekday internal constructor(override val month: MonthName) : YearTarget
}
