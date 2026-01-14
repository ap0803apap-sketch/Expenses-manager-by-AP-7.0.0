package com.ap.expenses.manager

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.ap.expenses.manager.databinding.ItemTransactionBinding

class CashTransactionAdapter(
    private var transactions: List<CashTransaction>,
    private val listener: OnTransactionClickListener
) : RecyclerView.Adapter<CashTransactionAdapter.TransactionViewHolder>() {

    interface OnTransactionClickListener {
        fun onEditClick(transaction: CashTransaction)
        fun onDeleteClick(transaction: CashTransaction)
    }

    class TransactionViewHolder(val binding: ItemTransactionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TransactionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = transactions[position]
        with(holder.binding) {
            val prefix = if (transaction.type == "IN") "From: " else "To: "
            tvPerson.text = prefix + transaction.person

            val amountPrefix = if (transaction.type == "IN") "+ ₹" else "- ₹"
            tvAmount.text = amountPrefix + "%.2f".format(transaction.amount)
            tvAmount.setTextColor(if (transaction.type == "IN") "#008000".toColorInt() else "#D32F2F".toColorInt())

            tvDescription.text = transaction.description ?: ""
            // **THIS IS THE UPDATE:** Show default text if time is null
            tvTimeAndApp.text = transaction.time ?: "Time not selected"

            ivEdit.setOnClickListener { listener.onEditClick(transaction) }
            ivDelete.setOnClickListener { listener.onDeleteClick(transaction) }
        }
    }

    override fun getItemCount() = transactions.size

    fun updateData(newTransactions: List<CashTransaction>) {
        transactions = newTransactions
        notifyDataSetChanged()
    }
}