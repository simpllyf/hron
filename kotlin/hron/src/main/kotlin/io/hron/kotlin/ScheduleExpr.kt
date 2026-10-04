package io.hron.kotlin

/** The repeat of a schedule, without its trailing clauses. */
public sealed interface ScheduleExpr {
    /**
     * Slots [from], `from + interval`, ... through [to], on the days of [dayFilter], or every day
     * when it is null.
     */
    @ConsistentCopyVisibility
    public data class IntervalRepeat
    internal constructor(
        public val interval: Int,
        public val unit: IntervalUnit,
        public val from: TimeOfDay,
        public val to: TimeOfDay,
        public val dayFilter: DayFilter?,
    ) : ScheduleExpr

    /** [days] is [DayFilter.Every] whenever [interval] is above 1. */
    @ConsistentCopyVisibility
    public data class DayRepeat
    internal constructor(
        public val interval: Int,
        public val days: DayFilter,
        public val times: List<TimeOfDay>,
    ) : ScheduleExpr

    @ConsistentCopyVisibility
    public data class WeekRepeat
    internal constructor(
        public val interval: Int,
        public val days: List<Weekday>,
        public val times: List<TimeOfDay>,
    ) : ScheduleExpr

    @ConsistentCopyVisibility
    public data class MonthRepeat
    internal constructor(
        public val interval: Int,
        public val target: MonthTarget,
        public val times: List<TimeOfDay>,
    ) : ScheduleExpr

    @ConsistentCopyVisibility
    public data class SingleDate
    internal constructor(public val date: DateSpec, public val times: List<TimeOfDay>) :
        ScheduleExpr

    @ConsistentCopyVisibility
    public data class YearRepeat
    internal constructor(
        public val interval: Int,
        public val target: YearTarget,
        public val times: List<TimeOfDay>,
    ) : ScheduleExpr
}
