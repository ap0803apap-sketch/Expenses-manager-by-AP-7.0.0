package com.ap.expenses.manager

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ap.expenses.manager.databinding.ItemPaymentAppBinding

class PaymentAppAdapter(
    private var paymentApps: MutableList<String>,
    private val onDeleteClick: (String) -> Unit
) : RecyclerView.Adapter<PaymentAppAdapter.PaymentAppViewHolder>() {

    class PaymentAppViewHolder(val binding: ItemPaymentAppBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PaymentAppViewHolder {
        val binding = ItemPaymentAppBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PaymentAppViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PaymentAppViewHolder, position: Int) {
        val appName = paymentApps[position]
        holder.binding.tvAppName.text = appName
        holder.binding.ivDeleteApp.setOnClickListener {
            onDeleteClick(appName)
        }
    }

    override fun getItemCount() = paymentApps.size
}