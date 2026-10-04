import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.hron.kotlin.HronException
import io.hron.kotlin.Schedule
import java.io.PrintStream
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val mapper = ObjectMapper()
private val zoned = Regex("(.+)\\[(.+)]")
private val iso = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssxxxxx")

fun main() {
    val out = PrintStream(System.out, true, Charsets.UTF_8)
    for (line in System.`in`.bufferedReader(Charsets.UTF_8).lineSequence()) {
        val case = mapper.readTree(line)
        val start = System.nanoTime()
        val outcome = outcome(case)
        val micros = (System.nanoTime() - start) / 1000
        out.println(
            mapper.writeValueAsString(
                mapOf("id" to case["id"].asText()) + outcome + mapOf("micros" to micros)
            )
        )
    }
}

private fun outcome(case: JsonNode): Map<String, Any?> =
    try {
        mapOf("ok" to true, "result" to evaluate(case))
    } catch (e: HronException) {
        val error =
            mapOf(
                "kind" to e.kind.name.lowercase(),
                "message" to e.message,
                "span" to e.span?.let { listOf(it.start, it.end) },
                "suggestion" to e.suggestion,
            )
        mapOf("ok" to false, "error" to error)
    } catch (e: Exception) {
        crash(e)
    } catch (e: StackOverflowError) {
        crash(e)
    }

private fun crash(e: Throwable) =
    mapOf("ok" to false, "error" to mapOf("kind" to "crash", "message" to e.toString()))

private fun evaluate(case: JsonNode): Any? {
    val op = case["op"].asText()
    if (op == "fromCron") return Schedule.fromCron(case["expr"].asText()).toString()
    val schedule = Schedule.parse(case["expr"].asText())
    return when (op) {
        "parse" -> schedule.toString()
        "toCron" -> schedule.toCron()
        "next" -> schedule.nextFrom(zoned(case, "now"))?.let(::format)
        "nextN" -> schedule.nextNFrom(zoned(case, "now"), case["n"].asInt()).map(::format)
        "prev" -> schedule.previousFrom(zoned(case, "now"))?.let(::format)
        "matches" -> schedule.matches(zoned(case, "datetime"))
        "between" -> schedule.between(zoned(case, "from"), zoned(case, "to")).map(::format).toList()
        "occurrences" ->
            schedule.occurrences(zoned(case, "from")).take(case["n"].asInt()).map(::format).toList()
        else -> throw IllegalArgumentException("unknown op $op")
    }
}

private fun zoned(case: JsonNode, field: String): ZonedDateTime {
    val text = case[field].asText()
    val (instant, zone) =
        zoned.matchEntire(text)?.destructured
            ?: throw IllegalArgumentException("not a zoned timestamp: $text")
    return ZonedDateTime.parse(instant, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        .withZoneSameInstant(ZoneId.of(zone))
}

private fun format(t: ZonedDateTime): String = "${iso.format(t)}[${t.zone.id}]"
