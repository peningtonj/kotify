package com.dzirbel.kotify.util

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

internal class StringUtilsTestPage {
    data class ByteSizeCase(val bytes: Long, val formatted: String)
    data class FormatDateTimeCase(
        val timestamp: Long,
        val formatted: String,
        val includeDate: Boolean,
        val includeTime: Boolean,
        val includeMillis: Boolean,
    )

    data class FormatDurationCase(val duration: Duration, val formatted: String)

    @ParameterizedTest
    @MethodSource
    fun formatByteSize(case: ByteSizeCase) {
        assertThat(formatByteSize(case.bytes)).isEqualTo(case.formatted)
    }

    @ParameterizedTest
    @MethodSource
    fun formatDateTime(case: FormatDateTimeCase) {
        assertThat(
            formatDateTime(
                timestamp = case.timestamp,
                includeDate = case.includeDate,
                includeTime = case.includeTime,
                includeMillis = case.includeMillis,
                locale = Locale.US,
                zone = ZoneId.of("America/Los_Angeles"),
            ),
        ).isEqualTo(case.formatted)
    }

    @ParameterizedTest
    @MethodSource
    fun formatDuration(case: FormatDurationCase) {
        assertThat(formatDuration(durationMs = case.duration.inWholeMilliseconds)).isEqualTo(case.formatted)
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        }

        @JvmStatic
        fun formatByteSize(): List<ByteSizeCase> {
            return listOf(
                ByteSizeCase(bytes = 0, formatted = "0 B"),
                ByteSizeCase(bytes = 1, formatted = "1 B"),
                ByteSizeCase(bytes = 1_023, formatted = "1023 B"),
                ByteSizeCase(bytes = 1_024, formatted = "1.0 KiB"),
                ByteSizeCase(bytes = 1_025, formatted = "1.0 KiB"),
                ByteSizeCase(bytes = 1_400, formatted = "1.4 KiB"),
                ByteSizeCase(bytes = 1_449, formatted = "1.4 KiB"),
                ByteSizeCase(bytes = 1_450, formatted = "1.4 KiB"),
                ByteSizeCase(bytes = 1_451, formatted = "1.4 KiB"),
                ByteSizeCase(bytes = 1_500, formatted = "1.5 KiB"),
                ByteSizeCase(bytes = 1_600, formatted = "1.6 KiB"),
                ByteSizeCase(bytes = 2_000, formatted = "2.0 KiB"),
                ByteSizeCase(bytes = 1_000_000, formatted = "976.6 KiB"),
                ByteSizeCase(bytes = 1_048_576, formatted = "1.0 MiB"),
                ByteSizeCase(bytes = 1e10.toLong(), formatted = "9.3 GiB"),
                ByteSizeCase(bytes = 1e11.toLong(), formatted = "93.1 GiB"),
                ByteSizeCase(bytes = 1e12.toLong(), formatted = "931.3 GiB"),
                ByteSizeCase(bytes = 1e13.toLong(), formatted = "9.1 TiB"),
                ByteSizeCase(bytes = 1e16.toLong(), formatted = "8.9 PiB"),
                ByteSizeCase(bytes = Long.MAX_VALUE, formatted = "8.0 EiB"),
            )
        }

        @JvmStatic
        fun formatDateTime(): List<FormatDateTimeCase> {
            return listOf(
                FormatDateTimeCase(
                    timestamp = 1_615_772_944_610,
                    formatted = "2021-03-14 18:49:04.610",
                    includeDate = true,
                    includeTime = true,
                    includeMillis = true,
                ),
                FormatDateTimeCase(
                    timestamp = 1_615_772_944_610,
                    formatted = "18:49:04.610",
                    includeDate = false,
                    includeTime = true,
                    includeMillis = true,
                ),
                FormatDateTimeCase(
                    timestamp = 1_615_772_944_610,
                    formatted = "2021-03-14 18:49:04",
                    includeDate = true,
                    includeTime = true,
                    includeMillis = false,
                ),
                FormatDateTimeCase(
                    timestamp = 1_615_772_944_610,
                    formatted = "18:49:04",
                    includeDate = false,
                    includeTime = true,
                    includeMillis = false,
                ),
                FormatDateTimeCase(
                    timestamp = 1_615_772_944_610,
                    formatted = "2021-03-14",
                    includeDate = true,
                    includeTime = false,
                    includeMillis = false,
                ),
            )
        }

        @JvmStatic
        fun formatDuration(): List<FormatDurationCase> {
            return listOf(
                FormatDurationCase(duration = 0.toDuration(DurationUnit.SECONDS), formatted = "0:00"),
                FormatDurationCase(duration = 1.toDuration(DurationUnit.SECONDS), formatted = "0:01"),
                FormatDurationCase(duration = 2.toDuration(DurationUnit.SECONDS), formatted = "0:02"),
                FormatDurationCase(duration = 2.toDuration(DurationUnit.MINUTES), formatted = "2:00"),
                FormatDurationCase(
                    duration = 2.toDuration(DurationUnit.MINUTES).plus(30.toDuration(DurationUnit.SECONDS)),
                    formatted = "2:30",
                ),
                FormatDurationCase(
                    duration = 2.toDuration(DurationUnit.HOURS),
                    formatted = "2:00:00",
                ),
            )
        }
    }
}
