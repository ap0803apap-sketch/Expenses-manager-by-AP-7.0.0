package com.ap.expenses.manager

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OnlineTransactionDao {
    @Insert
    suspend fun insert(transaction: OnlineTransaction)

    @Update
    suspend fun update(transaction: OnlineTransaction)

    @Delete
    suspend fun delete(transaction: OnlineTransaction)

    @Query("""
        SELECT * FROM online_transactions
        WHERE date BETWEEN :startDate AND :endDate
        AND (:type IS NULL OR type = :type)
        AND (:personName IS NULL OR person LIKE '%' || :personName || '%')
        AND (:descriptionQuery IS NULL OR description LIKE '%' || :descriptionQuery || '%')
        AND (:minAmount IS NULL OR amount >= :minAmount)
        AND (:maxAmount IS NULL OR amount <= :maxAmount)
        AND (:paymentApp IS NULL OR paymentApp = :paymentApp)
    """)
    fun getFilteredTransactions(
        startDate: String,
        endDate: String,
        type: String?,
        personName: String?,
        descriptionQuery: String?,
        minAmount: Double?,
        maxAmount: Double?,
        paymentApp: String?
    ): Flow<List<OnlineTransaction>>

    @Query("SELECT * FROM online_transactions")
    suspend fun getAllTransactions(): List<OnlineTransaction>


    @Query("SELECT DISTINCT person FROM online_transactions ORDER BY person ASC")
    suspend fun getDistinctPeople(): List<String>

    @Query("SELECT DISTINCT description FROM online_transactions WHERE description IS NOT NULL ORDER BY description ASC")
    suspend fun getDistinctDescriptions(): List<String>

    @Query("SELECT * FROM online_transactions WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getTransactionsBetweenDates(startDate: String, endDate: String): List<OnlineTransaction>

    @Query("SELECT * FROM online_transactions")
    suspend fun getAll(): List<OnlineTransaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<OnlineTransaction>)

    @Query("DELETE FROM online_transactions")
    suspend fun clearTable()
}

