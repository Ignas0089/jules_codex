package com.example.expensetracker.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FileUploadResponse(
    val id: String,
    val filename: String,
    val purpose: String
)

@Serializable
data class ResponseMessage(
    val id: String,
    val status: String,
    val output: List<ResponseOutput>? = null
)

@Serializable
data class ResponseOutput(
    val id: String,
    val content: List<OutputContent>
)

@Serializable
sealed class OutputContent {
    @Serializable
    @SerialName("output_text")
    data class Text(val text: TextSegment) : OutputContent()
}

@Serializable
data class TextSegment(val value: String)
