package com.example.expensetracker.data

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ExpenseConverters {
    @TypeConverter
    fun fromEpoch(epochSeconds: Long?): LocalDate? = epochSeconds?.let {
        Instant.ofEpochSecond(it).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    @TypeConverter
    fun toEpoch(localDate: LocalDate?): Long? = localDate?.atStartOfDay(ZoneId.systemDefault())?.toEpochSecond()
}
