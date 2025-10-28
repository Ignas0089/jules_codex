package com.example.expensetracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.ZoneOffset

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val amount: Double,
    val category: String,
    @ColumnInfo(name = "occurred_on")
    val occurredOn: LocalDate,
    val notes: String?
) {
    fun occurredOnEpochSeconds(): Long = occurredOn.atStartOfDay().toEpochSecond(ZoneOffset.UTC)
}
