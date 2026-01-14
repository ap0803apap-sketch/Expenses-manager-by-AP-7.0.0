package com.ap.expenses.manager

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.util.Pair
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.room.Room
import com.ap.expenses.manager.databinding.DialogAddTransactionBinding
import com.ap.expenses.manager.databinding.DialogFilterBinding
import com.ap.expenses.manager.databinding.FragmentTransactionBinding
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

data class MixedTransaction(
    val id: Int = 0,
    val date: String,
    val time: String?,
    val type: String,
    val amount: Double,
    val person: String,
    val description: String?,
    val mode: String,
    val paymentApp: String?
)

class TransactionFragment : Fragment(), TransactionAdapter.OnTransactionClickListener {

    private var _binding: FragmentTransactionBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
    private lateinit var transactionAdapter: TransactionAdapter
    private val paymentApps = mutableListOf<String>()

    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val uiDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    // Filter and Sort State Variables
    private var filterStartDate: String = apiDateFormat.format(Date())
    private var filterEndDate: String = apiDateFormat.format(Date())
    private var isDateRangeFilter: Boolean = false
    private var currentSortOrder: Int = 0
    private var currentFilterType: String? = null
    private var currentFilterPerson: String? = null
    private var currentFilterDesc: String? = null
    private var currentFilterMin: Double? = null
    private var currentFilterMax: Double? = null
    private var currentFilterApp: String? = null
    private var showCashTransactions: Boolean = true
    private var showOnlineTransactions: Boolean = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTransactionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = Room.databaseBuilder(requireContext(), AppDatabase::class.java, "expense-manager-db").fallbackToDestructiveMigration().build()
        loadPaymentApps()
        setupRecyclerView()

        updateDateButtonText()
        loadTransactions()

        binding.fabAddTransaction.setOnClickListener { showAddTransactionDialog() }
        binding.btnDatePickerTransaction.setOnClickListener { showDatePickerDialog() }
        binding.swipeRefreshLayoutTransaction.setOnRefreshListener {
            binding.swipeRefreshLayoutTransaction.isRefreshing = true
            loadTransactions()
        }
        binding.tvFilterStatus.setOnClickListener { showFilterDialog() }
    }

    private fun loadPaymentApps() {
        val prefs = requireActivity().getSharedPreferences("payment_apps", Context.MODE_PRIVATE)
        val savedApps = prefs.getStringSet("apps_list", null)
        paymentApps.clear()
        if (savedApps.isNullOrEmpty()) {
            paymentApps.addAll(listOf("Google Pay", "Paytm", "PhonePe"))
        } else {
            paymentApps.addAll(savedApps)
        }
        paymentApps.sort()
    }

    private fun setupRecyclerView() {
        transactionAdapter = TransactionAdapter(emptyList(), this)
        binding.recyclerViewTransaction.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = transactionAdapter
        }
    }

    private fun loadTransactions() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Get transactions based on checkbox states
                val allTransactions = mutableListOf<MixedTransaction>()

                if (showCashTransactions) {
                    db.cashTransactionDao().getFilteredTransactions(
                        filterStartDate,
                        filterEndDate,
                        currentFilterType,
                        currentFilterPerson,
                        currentFilterDesc,
                        currentFilterMin,
                        currentFilterMax
                    ).collectLatest { cashList ->
                        if (!isAdded) return@collectLatest

                        allTransactions.clear()
                        allTransactions.addAll(cashList.map {
                            MixedTransaction(
                                id = it.id,
                                date = it.date,
                                time = it.time,
                                type = it.type,
                                amount = it.amount,
                                person = it.person,
                                description = it.description,
                                mode = "cash",
                                paymentApp = null
                            )
                        })

                        // Then get online transactions if needed
                        if (showOnlineTransactions) {
                            db.onlineTransactionDao().getFilteredTransactions(
                                filterStartDate,
                                filterEndDate,
                                currentFilterType,
                                currentFilterPerson,
                                currentFilterDesc,
                                currentFilterMin,
                                currentFilterMax,
                                currentFilterApp
                            ).collectLatest { onlineList ->
                                if (!isAdded) return@collectLatest

                                allTransactions.addAll(onlineList.map {
                                    MixedTransaction(
                                        id = it.id,
                                        date = it.date,
                                        time = it.time,
                                        type = it.type,
                                        amount = it.amount,
                                        person = it.person,
                                        description = it.description,
                                        mode = "online",
                                        paymentApp = it.paymentApp
                                    )
                                })
                                updateUI(allTransactions)
                            }
                        } else {
                            updateUI(allTransactions)
                        }
                    }
                } else if (showOnlineTransactions) {
                    db.onlineTransactionDao().getFilteredTransactions(
                        filterStartDate,
                        filterEndDate,
                        currentFilterType,
                        currentFilterPerson,
                        currentFilterDesc,
                        currentFilterMin,
                        currentFilterMax,
                        currentFilterApp
                    ).collectLatest { onlineList ->
                        if (!isAdded) return@collectLatest

                        allTransactions.clear()
                        allTransactions.addAll(onlineList.map {
                            MixedTransaction(
                                id = it.id,
                                date = it.date,
                                time = it.time,
                                type = it.type,
                                amount = it.amount,
                                person = it.person,
                                description = it.description,
                                mode = "online",
                                paymentApp = it.paymentApp
                            )
                        })
                        updateUI(allTransactions)
                    }
                } else {
                    // Both unchecked - show empty
                    if (isAdded) {
                        updateUI(emptyList())
                    }
                }
            } finally {
                if (isAdded) {
                    _binding?.swipeRefreshLayoutTransaction?.isRefreshing = false
                }
            }
        }
    }

    private fun updateUI(allTransactions: List<MixedTransaction>) {
        if (!isAdded) return

        val sortedTransactions = sortTransactions(allTransactions, currentSortOrder)
        val groupedTransactions = if (isDateRangeFilter) {
            sortedTransactions.groupBy { it.date }.flatMap { (date, txns) ->
                listOf(MixedTransaction(date = date, time = null, type = "HEADER", amount = 0.0, person = "", description = null, mode = "cash", paymentApp = null)) + txns
            }
        } else {
            sortedTransactions
        }
        transactionAdapter.updateData(groupedTransactions)
        updateTotalsUI(sortedTransactions)
    }

    private fun sortTransactions(transactions: List<MixedTransaction>, sortOrder: Int): List<MixedTransaction> {
        val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd hh:mm a", Locale.getDefault())

        return when (sortOrder) {
            0 -> transactions.sortedByDescending { it.id }
            1 -> transactions.sortedBy { it.id }
            2 -> transactions.sortedByDescending { parseDateTime(it.date, it.time, dateTimeFormat) }
            3 -> transactions.sortedBy { parseDateTime(it.date, it.time, dateTimeFormat) }
            4 -> transactions.sortedBy { it.person }
            5 -> transactions.sortedByDescending { it.person }
            6 -> transactions.sortedBy { it.description }
            7 -> transactions.sortedByDescending { it.description }
            else -> transactions
        }
    }

    private fun parseDateTime(date: String, time: String?, format: SimpleDateFormat): Date {
        val safeTime = time ?: "12:00 AM"
        return try {
            format.parse("$date $safeTime") ?: Date(0)
        } catch (e: ParseException) {
            Date(0)
        }
    }

    private fun updateTotalsUI(transactions: List<MixedTransaction>) {
        val totalIn = transactions.filter { it.type == "IN" }.sumOf { it.amount }
        val totalOut = transactions.filter { it.type == "OUT" }.sumOf { it.amount }
        binding.tvTotalIn.text = String.format("₹%.2f", totalIn)
        binding.tvTotalOut.text = String.format("₹%.2f", totalOut)
    }

    private fun showFilterDialog() {
        val dialogBinding = DialogFilterBinding.inflate(layoutInflater)

        // Setup cash and online checkboxes - IMPORTANT: Read current state FIRST
        dialogBinding.cbCashTransactions.isChecked = showCashTransactions
        dialogBinding.cbOnlineTransactions.isChecked = showOnlineTransactions

        // Handle visibility of payment app spinner based on online checkbox
        dialogBinding.spinnerPaymentAppFilter.visibility = if (dialogBinding.cbOnlineTransactions.isChecked) View.VISIBLE else View.GONE

        dialogBinding.cbOnlineTransactions.setOnCheckedChangeListener { _, isChecked ->
            dialogBinding.spinnerPaymentAppFilter.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.sort_options,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            dialogBinding.spinnerSortOrder.adapter = adapter
        }
        dialogBinding.spinnerSortOrder.setSelection(currentSortOrder)

        val appsWithAll = listOf("All Apps") + paymentApps
        val appSpinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, appsWithAll)
        dialogBinding.spinnerPaymentAppFilter.adapter = appSpinnerAdapter
        currentFilterApp?.let {
            val pos = appSpinnerAdapter.getPosition(it)
            if (pos >= 0) dialogBinding.spinnerPaymentAppFilter.setSelection(pos)
        }

        dialogBinding.rgDateFilter.setOnCheckedChangeListener { _, checkedId ->
            isDateRangeFilter = checkedId == R.id.rb_date_range
            dialogBinding.btnSelectDateRange.visibility = if (isDateRangeFilter) View.VISIBLE else View.GONE
        }
        dialogBinding.btnSelectDateRange.setOnClickListener {
            val dateRangePicker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText("Select Date Range")
                .setSelection(Pair(
                    uiDateFormat.parse(uiDateFormat.format(apiDateFormat.parse(filterStartDate)!!))!!.time,
                    uiDateFormat.parse(uiDateFormat.format(apiDateFormat.parse(filterEndDate)!!))!!.time
                ))
                .build()
            dateRangePicker.addOnPositiveButtonClickListener { selection ->
                filterStartDate = apiDateFormat.format(Date(selection.first))
                filterEndDate = apiDateFormat.format(Date(selection.second))
                dialogBinding.btnSelectDateRange.text = "${uiDateFormat.format(Date(selection.first))} - ${uiDateFormat.format(Date(selection.second))}"
            }
            dateRangePicker.show(childFragmentManager, "DATE_RANGE_PICKER")
        }

        if (isDateRangeFilter) {
            dialogBinding.rbDateRange.isChecked = true
            dialogBinding.btnSelectDateRange.visibility = View.VISIBLE
            dialogBinding.btnSelectDateRange.text = "${uiDateFormat.format(apiDateFormat.parse(filterStartDate)!!)} - ${uiDateFormat.format(apiDateFormat.parse(filterEndDate)!!)}"
        } else {
            dialogBinding.rbToday.isChecked = true
        }

        dialogBinding.etPersonFilter.setText(currentFilterPerson)
        dialogBinding.etDescriptionFilter.setText(currentFilterDesc)
        currentFilterMin?.let { dialogBinding.etMinAmountFilter.setText(it.toString()) }
        currentFilterMax?.let { dialogBinding.etMaxAmountFilter.setText(it.toString()) }
        when(currentFilterType) {
            "IN" -> dialogBinding.chipIn.isChecked = true
            "OUT" -> dialogBinding.chipOut.isChecked = true
            else -> dialogBinding.chipAll.isChecked = true
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filter & Sort")
            .setView(dialogBinding.root)
            .setPositiveButton("Apply") { _, _ ->
                if (!isDateRangeFilter) {
                    val today = apiDateFormat.format(Date())
                    filterStartDate = today
                    filterEndDate = today
                }
                currentFilterType = when (dialogBinding.chipGroupType.checkedChipId) {
                    R.id.chip_in -> "IN"
                    R.id.chip_out -> "OUT"
                    else -> null
                }
                currentFilterPerson = dialogBinding.etPersonFilter.text.toString().ifBlank { null }
                currentFilterDesc = dialogBinding.etDescriptionFilter.text.toString().ifBlank { null }
                currentFilterMin = dialogBinding.etMinAmountFilter.text.toString().toDoubleOrNull()
                currentFilterMax = dialogBinding.etMaxAmountFilter.text.toString().toDoubleOrNull()

                // IMPORTANT: Update these BEFORE loadTransactions()
                showCashTransactions = dialogBinding.cbCashTransactions.isChecked
                showOnlineTransactions = dialogBinding.cbOnlineTransactions.isChecked

                currentFilterApp = if (showOnlineTransactions) {
                    dialogBinding.spinnerPaymentAppFilter.selectedItem.toString().let {
                        if (it == "All Apps") null else it
                    }
                } else null

                currentSortOrder = dialogBinding.spinnerSortOrder.selectedItemPosition

                binding.tvFilterStatus.text = "Filters Applied. Tap to change."
                updateDateButtonText()
                loadTransactions()
            }
            .setNegativeButton("Clear") { _, _ ->
                clearFilters()
                loadTransactions()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun clearFilters() {
        val today = apiDateFormat.format(Date())
        filterStartDate = today
        filterEndDate = today
        isDateRangeFilter = false
        currentFilterType = null
        currentFilterPerson = null
        currentFilterDesc = null
        currentFilterMin = null
        currentFilterMax = null
        currentFilterApp = null
        currentSortOrder = 0
        showCashTransactions = true
        showOnlineTransactions = true
        binding.tvFilterStatus.text = "No active filters. Tap to add."
        updateDateButtonText()
    }

    override fun onEditClick(transaction: MixedTransaction) {
        if (transaction.type != "HEADER") {
            showAddTransactionDialog(transaction)
        }
    }

    override fun onDeleteClick(transaction: MixedTransaction) {
        if (transaction.type == "HEADER") return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Transaction")
            .setMessage("Are you sure you want to delete this transaction?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    if (transaction.mode == "cash") {
                        val cashTx = CashTransaction(
                            id = transaction.id,
                            date = transaction.date,
                            type = transaction.type,
                            amount = transaction.amount,
                            person = transaction.person,
                            description = transaction.description,
                            time = transaction.time
                        )
                        db.cashTransactionDao().delete(cashTx)
                    } else {
                        val onlineTx = OnlineTransaction(
                            id = transaction.id,
                            date = transaction.date,
                            type = transaction.type,
                            amount = transaction.amount,
                            person = transaction.person,
                            paymentApp = transaction.paymentApp ?: "",
                            description = transaction.description,
                            time = transaction.time
                        )
                        db.onlineTransactionDao().delete(onlineTx)
                    }
                    Toast.makeText(context, "Transaction deleted", Toast.LENGTH_SHORT).show()
                    loadTransactions()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDatePickerDialog() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select date")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val selected = apiDateFormat.format(Date(selection))
            filterStartDate = selected
            filterEndDate = selected
            isDateRangeFilter = false
            updateDateButtonText()
            loadTransactions()
        }
        datePicker.show(childFragmentManager, "DATE_PICKER")
    }

    private fun updateDateButtonText() {
        if (isDateRangeFilter) {
            binding.btnDatePickerTransaction.text = "${uiDateFormat.format(apiDateFormat.parse(filterStartDate)!!)} - ${uiDateFormat.format(apiDateFormat.parse(filterEndDate)!!)}"
        } else {
            binding.btnDatePickerTransaction.text = uiDateFormat.format(apiDateFormat.parse(filterStartDate)!!)
        }
    }

    private fun showAddTransactionDialog(transactionToEdit: MixedTransaction? = null) {
        val dialogBinding = DialogAddTransactionBinding.inflate(layoutInflater)

        var isOnlineMode = true
        if (transactionToEdit != null) {
            isOnlineMode = transactionToEdit.mode == "online"
        }

        if (isOnlineMode) {
            dialogBinding.rbOnline.isChecked = true
        } else {
            dialogBinding.rbCash.isChecked = true
        }

        val title = if (transactionToEdit == null) "Add Transaction" else "Edit Transaction"

        val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())
        var selectedTime: String? = transactionToEdit?.time

        val selectedDateForTransaction = if(isDateRangeFilter) apiDateFormat.format(Date()) else filterStartDate

        val dialogSpinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, paymentApps)
        dialogBinding.spinnerPaymentAppDialog.adapter = dialogSpinnerAdapter
        dialogBinding.spinnerPaymentAppDialog.visibility = if (isOnlineMode) View.VISIBLE else View.GONE

        transactionToEdit?.let {
            dialogBinding.etAmount.setText(it.amount.toString())
            dialogBinding.etPerson.setText(it.person)
            dialogBinding.etDescription.setText(it.description)
            if (it.type == "IN") dialogBinding.rbMoneyIn.isChecked = true else dialogBinding.rbMoneyOut.isChecked = true
            if (it.mode == "online") {
                val spinnerPosition = dialogSpinnerAdapter.getPosition(it.paymentApp)
                if(spinnerPosition >= 0) dialogBinding.spinnerPaymentAppDialog.setSelection(spinnerPosition)
            }
        }

        dialogBinding.rgTransactionMode.setOnCheckedChangeListener { _, checkedId ->
            isOnlineMode = checkedId == R.id.rb_online
            dialogBinding.spinnerPaymentAppDialog.visibility = if (isOnlineMode) View.VISIBLE else View.GONE
        }

        dialogBinding.btnTimePicker.text = selectedTime ?: "No time selected"

        lifecycleScope.launch {
            val cashPeople = db.cashTransactionDao().getDistinctPeople()
            val onlinePeople = db.onlineTransactionDao().getDistinctPeople()
            val allPeople = (cashPeople + onlinePeople).distinct()
            val personAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, allPeople)
            (dialogBinding.etPerson as? AutoCompleteTextView)?.setAdapter(personAdapter)

            val cashDescs = db.cashTransactionDao().getDistinctDescriptions()
            val onlineDescs = db.onlineTransactionDao().getDistinctDescriptions()
            val allDescs = (cashDescs + onlineDescs).distinct()
            val descAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, allDescs)
            (dialogBinding.etDescription as? AutoCompleteTextView)?.setAdapter(descAdapter)
        }

        dialogBinding.btnTimePicker.setOnClickListener {
            val calendar = Calendar.getInstance()
            var defaultHour = calendar.get(Calendar.HOUR_OF_DAY)
            var defaultMinute = calendar.get(Calendar.MINUTE)

            selectedTime?.let { timeStr ->
                try {
                    val date = sdfTime.parse(timeStr)
                    if (date != null) {
                        val cal = Calendar.getInstance()
                        cal.time = date
                        defaultHour = cal.get(Calendar.HOUR_OF_DAY)
                        defaultMinute = cal.get(Calendar.MINUTE)
                    }
                } catch (e: ParseException) {
                }
            }

            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(defaultHour)
                .setMinute(defaultMinute)
                .setTitleText("Select Time")
                .build()

            timePicker.addOnPositiveButtonClickListener {
                val pickedCalendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, timePicker.hour)
                    set(Calendar.MINUTE, timePicker.minute)
                }
                selectedTime = sdfTime.format(pickedCalendar.time)
                dialogBinding.btnTimePicker.text = selectedTime
            }
            timePicker.show(childFragmentManager, "TIME_PICKER")
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(dialogBinding.root)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val amountStr = dialogBinding.etAmount.text.toString()
                val person = dialogBinding.etPerson.text.toString()
                if (amountStr.isBlank() || person.isBlank()) {
                    Toast.makeText(context, "Amount and Person fields cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (isOnlineMode && paymentApps.isEmpty()){
                    Toast.makeText(context, "Please add a payment app first in Settings.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val amount = amountStr.toDouble()
                val type = if (dialogBinding.rbMoneyIn.isChecked) "IN" else "OUT"
                val description = dialogBinding.etDescription.text.toString().ifBlank { null }

                lifecycleScope.launch {
                    if (isOnlineMode) {
                        val paymentApp = dialogBinding.spinnerPaymentAppDialog.selectedItem.toString()
                        if (transactionToEdit == null) {
                            val newTransaction = OnlineTransaction(date = selectedDateForTransaction, type = type, amount = amount, person = person, paymentApp = paymentApp, description = description, time = selectedTime)
                            db.onlineTransactionDao().insert(newTransaction)
                        } else {
                            val updatedTransaction = OnlineTransaction(
                                id = transactionToEdit.id,
                                date = selectedDateForTransaction,
                                type = type,
                                amount = amount,
                                person = person,
                                paymentApp = paymentApp,
                                description = description,
                                time = selectedTime
                            )
                            db.onlineTransactionDao().update(updatedTransaction)
                        }
                    } else {
                        if (transactionToEdit == null) {
                            val newTransaction = CashTransaction(date = selectedDateForTransaction, type = type, amount = amount, person = person, description = description, time = selectedTime)
                            db.cashTransactionDao().insert(newTransaction)
                        } else {
                            val updatedTransaction = CashTransaction(
                                id = transactionToEdit.id,
                                date = selectedDateForTransaction,
                                type = type,
                                amount = amount,
                                person = person,
                                description = description,
                                time = selectedTime
                            )
                            db.cashTransactionDao().update(updatedTransaction)
                        }
                    }
                    dialog.dismiss()
                    loadTransactions()
                }
            }
        }
        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}