package io.hron.kotlin.internal

import java.time.ZoneId

/**
 * `UTC` or an IANA `Area/Location` name in any case (spec/README.md, "Parse-time validation"), kept
 * as java.time spells it: its tz data lists every zone and link name in IANA capitalization.
 */
internal object TimeZones {
    private val REJECTED_AREAS = listOf("systemv/", "posix/", "right/")

    private val available: Map<String, String> =
        ZoneId.getAvailableZoneIds().associateBy { it.lowercase() }

    fun canonical(name: String): String? {
        // Timezone names are ASCII; Unicode lowercasing would map a Kelvin sign to k.
        if (name.any { it.code >= 128 }) return null
        val lowercase = name.lowercase()
        val wellFormed =
            lowercase == "utc" || ('/' in lowercase && REJECTED_AREAS.none(lowercase::startsWith))
        return if (wellFormed) available[lowercase] else null
    }
}
