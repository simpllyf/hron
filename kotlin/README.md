# hron (Kotlin)

[![Maven Central](https://img.shields.io/maven-central/v/io.hron/hron-kotlin)](https://central.sonatype.com/artifact/io.hron/hron-kotlin)

Native Kotlin implementation of [hron](https://github.com/simpllyf/hron) — human-readable cron expressions, for Android and the JVM.

## Install

Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("io.hron:hron-kotlin:2.2.0")
}
```

Maven:

```xml
<dependency>
    <groupId>io.hron</groupId>
    <artifactId>hron-kotlin</artifactId>
    <version>2.2.0</version>
</dependency>
```

## Usage

```kotlin
import io.hron.kotlin.Schedule
import java.time.ZonedDateTime

val schedule = Schedule.parse("every weekday at 9:00 in America/New_York")
val now = ZonedDateTime.parse("2026-02-06T12:00:00Z")

println(schedule.nextFrom(now))      // 2026-02-06T09:00-05:00[America/New_York]
println(schedule.previousFrom(now))  // 2026-02-05T09:00-05:00[America/New_York]
println(schedule.matches(now))       // false
println(schedule.nextNFrom(now, 5).size)  // 5

// Occurrences are computed lazily, one at a time.
for (time in schedule.occurrences(now).drop(1).take(2)) {
    println(time)  // 2026-02-09T09:00-05:00[America/New_York], then 2026-02-10T09:00-05:00[America/New_York]
}

println(schedule)                                      // every weekday at 09:00 in America/New_York
println(Schedule.parse("every day at 9:00").toCron())  // 0 9 * * *
println(Schedule.fromCron("0 16 * * 5L"))              // every month on the last friday at 16:00
println(Schedule.validate("every day at 25:00"))       // false
```

## API

A `Schedule` is built only by `Schedule.parse` or `Schedule.fromCron`, and its properties cannot change. From Java, the companion's functions are static: `Schedule.parse(...)`. The spec's names (`spec/api.json`) are the Kotlin names:

| Spec | Kotlin | Result |
| --- | --- | --- |
| `parse` | `Schedule.parse(input: String)` | `Schedule`; throws `HronException` |
| `fromCron` | `Schedule.fromCron(cronExpr: String)` | `Schedule`; throws `HronException` |
| `validate` | `Schedule.validate(input: String)` | `Boolean`: `false` for anything `parse` rejects |
| `nextFrom` | `nextFrom(now: ZonedDateTime)` | `ZonedDateTime?`: the next occurrence after `now` |
| `nextNFrom` | `nextNFrom(now: ZonedDateTime, n: Int)` | `List<ZonedDateTime>`: at most `n` occurrences after `now` |
| `previousFrom` | `previousFrom(now: ZonedDateTime)` | `ZonedDateTime?`: the last occurrence before `now` |
| `matches` | `matches(datetime: ZonedDateTime)` | `Boolean`: whether the minute containing `datetime` is an occurrence |
| `occurrences` | `occurrences(from: ZonedDateTime)` | `Sequence<ZonedDateTime>`: the occurrences after `from`, lazily |
| `between` | `between(from: ZonedDateTime, to: ZonedDateTime)` | `Sequence<ZonedDateTime>`: the occurrences in `from < t <= to`, lazily |
| `toCron` | `toCron()` | `String`; throws `HronException` |
| `toString` | `toString()` | `String`: the canonical expression, which `parse` reads back to an equal schedule |
| `equals` | `equals`, `hashCode` | equal when the parts are equal |
| `timezone` | `timezone` | `String?`: the IANA name in its canonical capitalization |
| `expression` | `expression` | `ScheduleExpr`: the repeat, without its clauses |
| `except` | `except` | `List<ExceptionSpec>`: empty without an `except` clause |
| `until` | `until` | `UntilSpec?` |
| `starting` | `starting` | `String?`: a `YYYY-MM-DD` date |
| `during` | `during` | `List<MonthName>`: empty without a `during` clause |

The sequences can be iterated more than once. Without a `between` bound, `occurrences` ends only at an `until` clause or the end of the supported range, so take a prefix of it rather than collecting it into a list.

### Parts

The properties return the parts of the schedule: `ScheduleExpr` (`IntervalRepeat`, `DayRepeat`, `WeekRepeat`, `MonthRepeat`, `SingleDate`, `YearRepeat`), `DayFilter`, `MonthTarget`, `YearTarget`, `DayOfMonthSpec`, `DateSpec`, `ExceptionSpec` and `UntilSpec`, the data class `TimeOfDay`, and the enums `Weekday`, `MonthName`, `OrdinalPosition`, `NearestDirection`, `IntervalUnit` and `ErrorKind`. The sealed interfaces hold data classes and data objects, so `when` over them is exhaustive. Only hron creates them, and the lists they hold are read-only.

```kotlin
val parts = Schedule.parse(
    "every weekday at 9:00 except dec 25 starting 2026-01-01 during jan, dec in america/new_york")
println(parts.timezone)  // America/New_York
println(parts.starting)  // 2026-01-01
println(parts.until)     // null
println(parts.during)    // [JANUARY, DECEMBER]

when (val expression = parts.expression) {
    is ScheduleExpr.DayRepeat -> println(expression.times)  // [09:00]
    else -> println("another kind")
}

when (val exception = parts.except[0]) {
    is ExceptionSpec.Named -> println("${exception.month} ${exception.day}")  // DECEMBER 25
    is ExceptionSpec.Iso -> println(exception.date)
}
```

`Weekday` and `MonthName` are closed sets. The other sealed interfaces and enums may gain cases in a minor release, and Kotlin has no way to mark them as open. A `when` that lists every case then stops compiling, and one already compiled against the older release throws `NoWhenBranchMatchedException` when it meets the new case. To keep working across minor releases, add an `else` branch and suppress the compiler's warning that it is redundant with `@Suppress("REDUNDANT_ELSE_IN_WHEN")`.

### Equality

Two schedules are equal when their parts are, with lists compared in order and duplicates counted. Equal schedules have equal hash codes, so a schedule can be a map key or a set element:

```kotlin
val nine = Schedule.parse("every day at 9:00")
println(nine == Schedule.parse("every day at 09:00"))  // true
println(nine.hashCode() == Schedule.parse("every day at 09:00").hashCode())  // true
println(nine == Schedule.fromCron("0 9 * * *"))  // true
println(Schedule.parse("every monday, friday at 09:00") == Schedule.parse("every friday, monday at 09:00"))  // false
```

## Timestamps

Every method takes and returns `java.time.ZonedDateTime`. Only the instant of an argument matters, not its zone. Every result is in the schedule's timezone, or in `ZoneId.of("UTC")` when the expression has no `in` clause.

Offsets are exact to the second: `java.time` keeps the offsets of local mean time and of Monrovia's `-00:44:30` until 1972, so `every day at 09:00 in Africa/Monrovia` fires at 09:44:30 UTC in 1971. A fraction of a second counts: `nextFrom` returns an occurrence strictly after the exact instant.

`nextNFrom` returns no more than `n` occurrences, and none when `n <= 0`. A large `n` only caps the count, with no room reserved for it, so `nextNFrom(now, Int.MAX_VALUE)` returns every occurrence through the end of the supported range.

The supported range is `0001-01-02T00:00:00Z <= t < 9999-12-30T00:00:00Z`, in the proleptic Gregorian calendar. A timestamp outside it is not an error: `nextFrom` and `previousFrom` return `null`, `matches` returns `false`, and `nextNFrom`, `occurrences` and `between` return nothing.

## Errors

Every error hron throws is a `HronException`, an unchecked exception:

| Property | Type | Holds |
| --- | --- | --- |
| `kind` | `ErrorKind` | `LEX`, `PARSE`, `EVAL` or `CRON` |
| `message` | `String` | what went wrong |
| `span` | `Span?` | the part of `input` it points at, for `LEX` and `PARSE` |
| `input` | `String?` | the expression, for `LEX` and `PARSE` |
| `suggestion` | `String?` | a fix, for some `PARSE` errors |

`displayRich()` formats it for a terminal, and `HronException.lex`, `parse`, `eval` and `cron` build one of each kind.

`Schedule.parse` throws a `LEX` or `PARSE` error with the exact message of the [spec](https://github.com/simpllyf/hron/blob/main/spec/README.md#error-message-format), the `input` and a `span`; a parse error may carry a `suggestion`:

```kotlin
try {
    Schedule.parse("every weekday at 09:00 until dec 31")
} catch (error: HronException) {
    println(error.kind)  // PARSE
    println(error.displayRich())
}
```

```text
error: until dec 31 has no year: add a starting date, or use an ISO date
  every weekday at 09:00 until dec 31
                         ^^^^^^^^^^^^ try: "until dec 31 starting YYYY-MM-DD"
```

A span counts Unicode code points, not the UTF-16 units of a `String`'s indices, so take the spanned text with `input.offsetByCodePoints`.

A schedule is built only by `Schedule.parse` or `Schedule.fromCron`, so evaluating it never throws. `EVAL` is for schedules that other hron languages build from their parts in code; this package never throws it.

## Cron Conversion

`toCron()` and `Schedule.fromCron` convert exactly: the result fires at the same times on the same dates, or they throw a `CRON` error whose message says why. This ignores the timezone and DST transitions, where cron schedulers differ. Yearly dates, ordinal weekdays such as `5L` and `1#2`, and intervals over part of the day convert too: `every 15 min from 09:00 to 17:45` is `*/15 9-17 * * *`.

`toCron()` throws for `except`, `until` and `starting`, ISO dates, repeats every `n` days, weeks, months or years with `n > 1`, directional nearest weekdays, a `during` that excludes a yearly or named date's month, and times that are not every combination of their minutes and hours (`at 09:00, 17:30`). A schedule's timezone is not part of the cron: run the cron in the schedule's timezone.

`Schedule.fromCron` throws for crons that restrict both the day of month and the day of week (`0 9 15 * 1`), and for more than 24 times a day, unless they are evenly spaced on days an interval can carry (`*/7 * * * *` fires 216 times at uneven gaps). The [spec](https://github.com/simpllyf/hron/blob/main/spec/README.md#cron-conversion) has the full rules and every error message.

## Timezones

Names match in any case and display with the IANA capitalization: `in america/new_york` displays `in America/New_York`, a link keeps its own name (`in us/eastern` displays `in US/Eastern`), and `in utc` displays `in UTC`.

`java.time` computes the offsets from the platform's tz data: the JDK's, or on Android the phone's, which Google Play keeps up to date. The names come from the same data, so an `Area/Location` name the platform knows matches however it is written, and any other name is rejected.

## Platforms and toolchain

- Android 12 (API 31) or later, and Java 17 or later.
- Kotlin 2.2 or later in the app. The library is built for Kotlin 2.2 and asks for no newer standard library.
- No dependencies beyond the Kotlin standard library.

## Tests

From the repository root:

```sh
just test-kotlin            # the JVM tests
just test-kotlin-android    # the spec's cases on a running emulator or device
just check-kotlin-client
```

`ConformanceTest` runs every case of `spec/tests.json`, `ApiConformanceTest` checks each member of `spec/api.json`, and `ReadmeTest` runs the examples above. `just test-kotlin-android` runs the same spec cases on Android, with its own `java.time` and tz data; CI runs them on Android 12 and the newest Android. `just check-kotlin-client` builds `kotlin/client-check` with Kotlin 2.2 against the jar, and `just test-kotlin-17 "$(mise where java@temurin-17)"` runs the tests on JDK 17.

## License

MIT
