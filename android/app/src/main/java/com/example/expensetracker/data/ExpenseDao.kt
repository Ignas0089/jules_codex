package com.example.expensetracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY occurred_on DESC")
    fun observeExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE occurred_on BETWEEN :start AND :end ORDER BY occurred_on DESC")
    fun observeExpensesBetween(start: LocalDate, end: LocalDate): Flow<List<Expense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("DELETE FROM expenses")
    suspend fun clearAll()
}
