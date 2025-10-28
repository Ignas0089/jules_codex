package com.example.expensetracker.ui

import com.example.expensetracker.data.AnalysisEntry
import com.example.expensetracker.data.Expense
import java.time.YearMonth

data class ExpenseUiState(
    val expenses: List<Expense> = emptyList(),
    val selectedMonth: YearMonth = YearMonth.now(),
    val selectedYear: Int = YearMonth.now().year,
    val monthlyTotal: Double = 0.0,
    val yearlyTotals: Map<String, Double> = emptyMap(),
    val apiKey: String? = null,
    val analysisHistory: List<AnalysisEntry> = emptyList(),
    val isOnline: Boolean = true,
    val isAnalyzing: Boolean = false,
    val lastAnalysisError: String? = null
)
