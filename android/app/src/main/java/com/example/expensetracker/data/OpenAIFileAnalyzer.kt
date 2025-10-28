package com.example.expensetracker.data

import android.content.ContentResolver
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class OpenAIFileAnalyzer(
    private val client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    suspend fun analyzeFile(
        resolver: ContentResolver,
        uri: Uri,
        apiKey: String,
        model: String = "gpt-4.1-mini"
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val tempFile = createTempFileFromUri(resolver, uri)
            tempFile.use { file ->
                val fileId = uploadFile(apiKey, file)
                requestAnalysis(apiKey, fileId, model)
            }
        }
    }

    private suspend fun uploadFile(apiKey: String, file: File): String {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("purpose", "assistants")
            .addFormDataPart(
                name = "file",
                filename = file.name,
                body = file.asRequestBody("application/octet-stream".toMediaType())
            )
            .build()

        val request = Request.Builder()
            .url("https://api.openai.com/v1/files")
            .header("Authorization", "Bearer $apiKey")
            .header("OpenAI-Beta", "assistants=v2")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("File upload failed: ${response.code}")
            }
            val body = response.body ?: error("Missing upload response body")
            val parsed = json.decodeFromString<FileUploadResponse>(body.string())
            return parsed.id
        }
    }

    private suspend fun requestAnalysis(apiKey: String, fileId: String, model: String): String {
        val payload = json.encodeToString(
            mapOf(
                "model" to model,
                "input" to listOf(
                    mapOf(
                        "role" to "user",
                        "content" to listOf(
                            mapOf(
                                "type" to "input_text",
                                "text" to "Analizuok pridėtą išlaidų failą ir pateik pagrindines įžvalgas."
                            ),
                            mapOf(
                                "type" to "input_file",
                                "file_id" to fileId
                            )
                        )
                    )
                )
            )
        )

        val request = Request.Builder()
            .url("https://api.openai.com/v1/responses")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("OpenAI-Beta", "assistants=v2")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Analysis failed: ${response.code}")
            }
            val body = response.body ?: error("Missing response body")
            val parsed = json.decodeFromString<ResponseMessage>(body.string())
            val output = parsed.output?.firstOrNull()
                ?: error("OpenAI nepateikė išvados")
            val firstText = output.content.filterIsInstance<OutputContent.Text>().firstOrNull()
                ?: error("OpenAI nepateikė tekstinės išvados")
            return firstText.text.value
        }
    }

    private fun createTempFileFromUri(resolver: ContentResolver, uri: Uri): AutoCloseableFile {
        val inputStream = resolver.openInputStream(uri) ?: error("Nepavyko nuskaityti failo")
        val tempFile = File.createTempFile("expense-upload-${UUID.randomUUID()}", null)
        FileOutputStream(tempFile).use { output ->
            inputStream.copyTo(output)
        }
        inputStream.close()
        return AutoCloseableFile(tempFile)
    }

    private class AutoCloseableFile(private val file: File) : AutoCloseable {
        val name: String get() = file.name
        fun asFile(): File = file

        override fun close() {
            file.delete()
        }

        inline fun <T> use(block: (File) -> T): T {
            return try {
                block(file)
            } finally {
                close()
            }
        }
    }
}
