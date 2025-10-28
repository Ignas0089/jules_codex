package com.example.expensetracker.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth

class ExpenseRepository(private val dao: ExpenseDao) {
    fun expensesForRange(start: LocalDate, end: LocalDate): Flow<List<Expense>> =
        dao.observeExpensesBetween(start, end)

    fun expensesForMonth(yearMonth: YearMonth): Flow<List<Expense>> {
        val start = yearMonth.atDay(1)
        val end = yearMonth.atEndOfMonth()
        return expensesForRange(start, end)
    }

    suspend fun upsert(expense: Expense) = dao.upsert(expense)
    suspend fun delete(expense: Expense) = dao.delete(expense)

    fun totalByCategoryForYear(year: Int): Flow<Map<String, Double>> =
        dao.observeExpensesBetween(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
            .map { expenses ->
                expenses.groupBy { it.category }
                    .mapValues { (_, grouped) -> grouped.sumOf { it.amount } }
            }
}
