package io.hron.kotlin.spec

import io.hron.kotlin.Schedule
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Invariant(val schedule: Schedule, val now: ZonedDateTime, val count: Int, val label: String)

val INVARIANTS: Map<String, (Invariant) -> Unit> =
    mapOf(
        "next_matches" to
            { inv ->
                inv.schedule.nextFrom(inv.now)?.let {
                    assertTrue(inv.schedule.matches(it), "${inv.label}: matches($it) is false")
                }
            },
        "next_after_now" to
            { inv ->
                inv.schedule.nextFrom(inv.now)?.let {
                    assertTrue(it.isAfter(inv.now), "${inv.label}: nextFrom is $it")
                }
            },
        "next_n_chain" to
            { inv ->
                val nextN = inv.schedule.nextNFrom(inv.now, inv.count)
                val first = inv.schedule.nextFrom(inv.now)
                assertEquals(
                    first == null,
                    nextN.isEmpty(),
                    "${inv.label}: emptiness differs from nextFrom(now)",
                )
                if (first != null) {
                    assertEquals(
                        first.toInstant(),
                        nextN.first().toInstant(),
                        "${inv.label}: first element",
                    )
                }
                for ((before, after) in nextN.zipWithNext()) {
                    assertTrue(
                        before.isBefore(after),
                        "${inv.label}: not strictly increasing after $before",
                    )
                    assertEquals(
                        after.toInstant(),
                        inv.schedule.nextFrom(before)?.toInstant(),
                        "${inv.label}: $after is not nextFrom($before)",
                    )
                }
            },
        "occurrences_prefix" to
            { inv ->
                assertEquals(
                    inv.schedule.nextNFrom(inv.now, inv.count).map { it.toInstant() },
                    inv.schedule
                        .occurrences(inv.now)
                        .take(inv.count)
                        .map { it.toInstant() }
                        .toList(),
                    inv.label,
                )
            },
        "between_window" to
            { inv ->
                val nextN = inv.schedule.nextNFrom(inv.now, inv.count)
                if (nextN.isNotEmpty()) {
                    assertEquals(
                        nextN.map { it.toInstant() },
                        inv.schedule.between(inv.now, nextN.last()).map { it.toInstant() }.toList(),
                        inv.label,
                    )
                }
            },
        "prev_inverse" to
            { inv ->
                for ((before, after) in inv.schedule.nextNFrom(inv.now, inv.count).zipWithNext()) {
                    assertEquals(
                        before.toInstant(),
                        inv.schedule.previousFrom(after)?.toInstant(),
                        "${inv.label}: previousFrom($after)",
                    )
                }
            },
        "prev_before_now" to
            { inv ->
                inv.schedule.previousFrom(inv.now)?.let { p ->
                    val label = "${inv.label}: previousFrom(now) is $p"
                    assertTrue(p.isBefore(inv.now), "$label, not before now")
                    assertTrue(inv.schedule.matches(p), "$label, which does not match")
                    inv.schedule.nextFrom(p)?.let {
                        assertFalse(it.isBefore(inv.now), "$label, but nextFrom(p) is $it")
                    }
                }
            },
        "display_roundtrip" to
            { inv ->
                val display = inv.schedule.toString()
                assertEquals(display, Schedule.parse(display).toString(), inv.label)
            },
    )
