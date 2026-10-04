package io.hron.kotlin

public sealed interface DayFilter {
    public data object Every : DayFilter

    public data object Weekdays : DayFilter

    public data object Weekends : DayFilter

    @ConsistentCopyVisibility
    public data class Days internal constructor(public val days: List<Weekday>) : DayFilter
}
