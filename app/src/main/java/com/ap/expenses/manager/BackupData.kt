package com.ap.expenses.manager

data class BackupData(
    val cashTransactions: List<CashTransaction>,
    val onlineTransactions: List<OnlineTransaction>,
    val paymentApps: List<String>,
    val version: String
)
