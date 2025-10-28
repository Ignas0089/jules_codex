package com.example.expensetracker.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensetracker.data.AnalysisEntry
import com.example.expensetracker.data.AppDataStore
import com.example.expensetracker.data.Expense
import com.example.expensetracker.data.ExpenseRepository
import com.example.expensetracker.data.OpenAIFileAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class ExpenseViewModel(
    private val repository: ExpenseRepository,
    private val dataStore: AppDataStore,
    private val analyzer: OpenAIFileAnalyzer
) : ViewModel() {

    private val selectedMonth = MutableStateFlow(YearMonth.now())
    private val isOnline = MutableStateFlow(true)
    private val isAnalyzing = MutableStateFlow(false)
    private val analysisError = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(ExpenseUiState())
    val uiState: StateFlow<ExpenseUiState> = _uiState

    init {
        viewModelScope.launch {
            combine(
                selectedMonth.flatMapLatest { month ->
                    repository.expensesForMonth(month).map { expenses -> month to expenses }
                },
                selectedMonth.map { it.year }.distinctUntilChanged().flatMapLatest { year ->
                    repository.totalByCategoryForYear(year)
                },
                dataStore.apiKey,
                dataStore.analysisHistory,
                isOnline,
                isAnalyzing,
                analysisError
            ) { (month, expenses), yearlyTotals, apiKey, history, online, analyzing, error ->
                ExpenseUiState(
                    expenses = expenses,
                    selectedMonth = month,
                    selectedYear = month.year,
                    monthlyTotal = expenses.sumOf { it.amount },
                    yearlyTotals = yearlyTotals,
                    apiKey = apiKey,
                    analysisHistory = history,
                    isOnline = online,
                    isAnalyzing = analyzing,
                    lastAnalysisError = error
                )
            }.collect { _uiState.value = it }
        }
    }

    fun selectMonth(month: YearMonth) {
        selectedMonth.value = month
    }

    fun updateConnectivity(online: Boolean) {
        isOnline.value = online
    }

    fun addExpense(
        title: String,
        amount: Double,
        category: String,
        date: LocalDate,
        notes: String?
    ) {
        viewModelScope.launch {
            repository.upsert(
                Expense(
                    title = title,
                    amount = amount,
                    category = category,
                    occurredOn = date,
                    notes = notes
                )
            )
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch { repository.delete(expense) }
    }

    fun saveApiKey(value: String) {
        viewModelScope.launch { dataStore.setApiKey(value) }
    }

    fun clearApiKey() {
        viewModelScope.launch { dataStore.clearApiKey() }
    }

    fun analyzeFile(resolver: ContentResolver, uri: Uri, fileName: String) {
        val apiKey = _uiState.value.apiKey ?: run {
            analysisError.value = "Pridėkite OpenAI API raktą, kad galėtumėte analizuoti failus."
            return
        }
        if (!isOnline.value) {
            analysisError.value = "Prisijunkite prie interneto, kad pradėtumėte analizę."
            return
        }
        viewModelScope.launch {
            isAnalyzing.value = true
            analysisError.value = null
            val result = analyzer.analyzeFile(resolver, uri, apiKey)
            result.onSuccess { summary ->
                val entry = AnalysisEntry.from(fileName, summary, Instant.now())
                dataStore.appendAnalysis(entry)
            }.onFailure { throwable ->
                analysisError.value = throwable.message ?: "Nepavyko analizuoti failo"
            }
            isAnalyzing.value = false
        }
    }
}
