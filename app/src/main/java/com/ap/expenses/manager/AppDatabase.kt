package com.ap.expenses.manager

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [CashTransaction::class, OnlineTransaction::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cashTransactionDao(): CashTransactionDao
    abstract fun onlineTransactionDao(): OnlineTransactionDao
}
