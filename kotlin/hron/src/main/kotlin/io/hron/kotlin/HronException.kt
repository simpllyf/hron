package io.hron.kotlin

/**
 * The error hron throws when [Schedule.parse] or [Schedule.fromCron] rejects its input, or
 * [Schedule.toCron] cannot convert a schedule. [span] and [input] are set for lex and parse errors
 * only, and [suggestion] only for a parse error with a fix to offer.
 */
public class HronException
private constructor(
    public val kind: ErrorKind,
    override val message: String,
    public val span: Span?,
    public val input: String?,
    public val suggestion: String?,
) : RuntimeException(message) {
    /**
     * The message, then for lex and parse errors the input and a line of carets under the span, and
     * any suggestion as `try: "..."`. Lines are joined by `\n`, with no trailing newline.
     */
    public fun displayRich(): String {
        if (span == null || input == null) return "error: $message"
        // A tab, CR or LF would move the input off the line the carets are aligned to.
        val shown = input.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ')
        // A span made outside hron can fall outside the input, so it is clamped to it.
        val length = input.codePointCount(0, input.length)
        val start = span.start.coerceIn(0, length)
        val end = span.end.coerceIn(start, length)
        val carets = " ".repeat(start) + "^".repeat(maxOf(1, end - start))
        val hint = if (suggestion == null) "" else " try: \"$suggestion\""
        return "error: $message\n  $shown\n  $carets$hint"
    }

    public companion object {
        @JvmStatic
        public fun lex(message: String, span: Span, input: String): HronException =
            HronException(ErrorKind.LEX, message, span, input, null)

        @JvmStatic
        public fun parse(
            message: String,
            span: Span,
            input: String,
            suggestion: String? = null,
        ): HronException = HronException(ErrorKind.PARSE, message, span, input, suggestion)

        @JvmStatic
        public fun eval(message: String): HronException =
            HronException(ErrorKind.EVAL, message, null, null, null)

        @JvmStatic
        public fun cron(message: String): HronException =
            HronException(ErrorKind.CRON, message, null, null, null)
    }
}
