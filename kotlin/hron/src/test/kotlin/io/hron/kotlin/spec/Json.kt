package io.hron.kotlin.spec

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

private val MAPPER = ObjectMapper()

fun readJson(text: String): JsonNode = MAPPER.readTree(text)

val JsonNode.fieldNameList: List<String>
    get() = buildList { fieldNames().forEachRemaining(::add) }

fun JsonNode.required(field: String, label: String): JsonNode =
    get(field) ?: fail("$label: missing $field")

fun JsonNode.text(field: String, label: String): String {
    val value = required(field, label)
    assertTrue(value.isTextual, "$label: $field is not a string")
    return value.asText()
}

fun JsonNode.int(field: String, label: String): Int {
    val value = required(field, label)
    assertTrue(value.isInt, "$label: $field is not an integer")
    return value.asInt()
}

/** A field the runner does not check is a case it would pass unchecked (spec/README.md). */
fun JsonNode.assertKnownFields(allowed: Set<String>, label: String) {
    val unknown = fieldNameList.filter { it !in allowed && it != "name" && it != "description" }
    assertEquals(emptyList(), unknown, "$label: unknown fields")
}

fun JsonNode.textList(label: String): List<String> {
    assertTrue(isArray, "$label: expected a list, got $this")
    return map {
        assertTrue(it.isTextual, "$label: $it is not a string")
        it.asText()
    }
}
