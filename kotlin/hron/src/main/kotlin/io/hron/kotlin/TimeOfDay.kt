package io.hron.kotlin

@ConsistentCopyVisibility
public data class TimeOfDay internal constructor(public val hour: Int, public val minute: Int) {
    /** The time as `HH:MM`, as the schedule writes it. */
    override fun toString(): String =
        "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}
