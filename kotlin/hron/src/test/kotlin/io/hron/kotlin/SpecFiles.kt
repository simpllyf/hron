package io.hron.kotlin

import java.io.File

/**
 * Gradle passes the repository's spec/ directory, as tests run with kotlin/hron as their directory.
 */
fun specFile(name: String): String = File(System.getProperty("hron.spec"), name).readText()
