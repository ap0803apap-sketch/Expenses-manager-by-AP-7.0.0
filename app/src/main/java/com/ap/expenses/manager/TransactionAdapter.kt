package com.ap.expenses.manager

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.ap.expenses.manager.databinding.ItemTransactionBinding
import com.ap.expenses.manager.databinding.ItemDateHeaderBinding
import java.text.SimpleDateFormat
import java.util.*

class TransactionAdapter(
    private var transactions: List<MixedTransaction>,
    private val listener: OnTransactionClickListener
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface OnTransactionClickListener {
        fun onEditClick(transaction: MixedTransaction)
        fun onDeleteClick(transaction: MixedTransaction)
    }

    private val HEADER_TYPE = 0
    private val TRANSACTION_TYPE = 1
    private val displayDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    class TransactionViewHolder(val binding: ItemTransactionBinding) : RecyclerView.ViewHolder(binding.root)
    class DateHeaderViewHolder(val binding: ItemDateHeaderBinding) : RecyclerView.ViewHolder(binding.root)

    override fun getItemViewType(position: Int): Int {
        return if (transactions[position].type == "HEADER") HEADER_TYPE else TRANSACTION_TYPE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == HEADER_TYPE) {
            val binding = ItemDateHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            DateHeaderViewHolder(binding)
        } else {
            val binding = ItemTransactionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            TransactionViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val transaction = transactions[position]

        if (holder is DateHeaderViewHolder) {
            holder.binding.tvDateHeader.text = displayDateFormat.format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(transaction.date)!!)
        } else if (holder is TransactionViewHolder) {
            with(holder.binding) {
                val prefix = if (transaction.type == "IN") "From: " else "To: "
                tvPerson.text = prefix + transaction.person

                val amountPrefix = if (transaction.type == "IN") "+ ₹" else "- ₹"
                tvAmount.text = amountPrefix + "%.2f".format(transaction.amount)
                tvAmount.setTextColor(if (transaction.type == "IN") "#008000".toColorInt() else "#D32F2F".toColorInt())

                tvDescription.text = transaction.description ?: ""

                val timeText = transaction.time ?: "Time not selected"
                tvTimeAndApp.text = if (transaction.mode == "online") {
                    "$timeText via ${transaction.paymentApp}"
                } else {
                    timeText
                }

                ivEdit.setOnClickListener { listener.onEditClick(transaction) }
                ivDelete.setOnClickListener { listener.onDeleteClick(transaction) }
            }
        }
    }

    override fun getItemCount() = transactions.size

    fun updateData(newTransactions: List<MixedTransaction>) {
        transactions = newTransactions
        notifyDataSetChanged()
    }
}