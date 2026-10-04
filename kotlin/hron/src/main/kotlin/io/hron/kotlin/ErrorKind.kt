package io.hron.kotlin

public enum class ErrorKind {
    LEX,
    PARSE,
    /**
     * A schedule built in code from parts that break a rule (spec/README.md, "Error Types"). Never
     * thrown here, as Kotlin builds schedules only through `parse` and `fromCron`.
     */
    EVAL,
    /** A cron expression `fromCron` rejects, or a schedule `toCron` cannot express. */
    CRON,
}
