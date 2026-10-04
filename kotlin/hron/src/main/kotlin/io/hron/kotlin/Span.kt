package io.hron.kotlin

/**
 * The part of an error's input it points at, from [start] up to but not including [end], counted in
 * Unicode code points, not the UTF-16 units [String.length] counts.
 */
public data class Span(public val start: Int, public val end: Int)
