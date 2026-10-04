package io.hron.kotlin.spec

/** [expression] is the schedule a case evaluates, so a runner can tell which zone it uses. */
class SpecCase(val name: String, val expression: String? = null, val check: () -> Unit)
