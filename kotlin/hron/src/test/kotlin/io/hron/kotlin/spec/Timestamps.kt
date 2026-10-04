package io.hron.kotlin.spec

import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val OFFSET_TIMESTAMP = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssxxxxx")

private val ZONED = Regex("""^(.+?)\[([^\]]+)]$""")

/** The spec's `2026-02-06T12:00:00+00:00[UTC]`: the offset gives the instant, read in the zone. */
fun parseTimestamp(text: String): ZonedDateTime {
    val match =
        ZONED.matchEntire(text)
            ?: return ZonedDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    val (instant, zone) = match.destructured
    return ZonedDateTime.parse(instant, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        .withZoneSameInstant(ZoneId.of(zone))
}

/**
 * Compared in full with the expected string (spec/README.md, "Writing a runner"). The offset keeps
 * any seconds, so a sub-minute offset cannot pass as its rounded one.
 */
fun formatTimestamp(t: ZonedDateTime): String = "${OFFSET_TIMESTAMP.format(t)}[${t.zone.id}]"
