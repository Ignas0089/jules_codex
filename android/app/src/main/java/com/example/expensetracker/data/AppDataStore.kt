package com.example.expensetracker.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "expense_tracker")

class AppDataStore(private val context: Context) {
    private val apiKeyKey = preferencesKey<String>("openai_api_key")
    private val analysisHistoryKey = preferencesKey<String>("analysis_history")

    private val json = Json { ignoreUnknownKeys = true }

    val apiKey: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[apiKeyKey]
    }

    val analysisHistory: Flow<List<AnalysisEntry>> = context.dataStore.data.map { prefs ->
        prefs[analysisHistoryKey]?.let { stored ->
            runCatching {
                json.decodeFromString(ListSerializer(AnalysisEntry.serializer()), stored)
            }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    suspend fun setApiKey(value: String) {
        context.dataStore.edit { prefs ->
            prefs[apiKeyKey] = value
        }
    }

    suspend fun clearApiKey() {
        context.dataStore.edit { prefs ->
            prefs.remove(apiKeyKey)
        }
    }

    suspend fun appendAnalysis(entry: AnalysisEntry) {
        context.dataStore.edit { prefs ->
            val current = prefs[analysisHistoryKey]?.let { stored ->
                runCatching {
                    json.decodeFromString(ListSerializer(AnalysisEntry.serializer()), stored)
                }.getOrDefault(emptyList())
            } ?: emptyList()
            val updated = (listOf(entry) + current).take(20)
            prefs[analysisHistoryKey] = json.encodeToString(
                ListSerializer(AnalysisEntry.serializer()),
                updated
            )
        }
    }
}
