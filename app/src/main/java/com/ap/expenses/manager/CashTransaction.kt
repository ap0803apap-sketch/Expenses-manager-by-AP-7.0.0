package com.ap.expenses.manager

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cash_transactions")
data class CashTransaction(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: String,
    val type: String, // "IN" or "OUT"
    val amount: Double,
    val person: String,
    val description: String?,
    val time: String?
)