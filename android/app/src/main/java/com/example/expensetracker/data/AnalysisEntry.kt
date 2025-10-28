package com.example.expensetracker.data

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class AnalysisEntry(
    val fileName: String,
    val summary: String,
    val analyzedAt: Long
) {
    companion object {
        fun from(fileName: String, summary: String, timestamp: Instant): AnalysisEntry =
            AnalysisEntry(fileName, summary, timestamp.epochSecond)
    }
}
