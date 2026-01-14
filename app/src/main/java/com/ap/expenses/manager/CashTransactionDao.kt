package com.ap.expenses.manager

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CashTransactionDao {
    @Insert
    suspend fun insert(transaction: CashTransaction)

    @Update
    suspend fun update(transaction: CashTransaction)

    @Delete
    suspend fun delete(transaction: CashTransaction)

    @Query("""
        SELECT * FROM cash_transactions
        WHERE date BETWEEN :startDate AND :endDate
        AND (:type IS NULL OR type = :type)
        AND (:personName IS NULL OR person LIKE '%' || :personName || '%')
        AND (:descriptionQuery IS NULL OR description LIKE '%' || :descriptionQuery || '%')
        AND (:minAmount IS NULL OR amount >= :minAmount)
        AND (:maxAmount IS NULL OR amount <= :maxAmount)
    """)
    fun getFilteredTransactions(
        startDate: String,
        endDate: String,
        type: String?,
        personName: String?,
        descriptionQuery: String?,
        minAmount: Double?,
        maxAmount: Double?
    ): Flow<List<CashTransaction>>

    @Query("SELECT * FROM cash_transactions")
    suspend fun getAllTransactions(): List<CashTransaction>

    @Query("SELECT DISTINCT person FROM cash_transactions ORDER BY person ASC")
    suspend fun getDistinctPeople(): List<String>

    @Query("SELECT DISTINCT description FROM cash_transactions WHERE description IS NOT NULL ORDER BY description ASC")
    suspend fun getDistinctDescriptions(): List<String>

    @Query("SELECT * FROM cash_transactions WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getTransactionsBetweenDates(startDate: String, endDate: String): List<CashTransaction>

    @Query("SELECT * FROM cash_transactions")
    suspend fun getAll(): List<CashTransaction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<CashTransaction>)

    @Query("DELETE FROM cash_transactions")
    suspend fun clearTable()
}

