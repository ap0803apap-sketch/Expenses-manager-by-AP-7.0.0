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
import com.ap.expenses.manager.databinding.FragmentOnlineBinding
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import com.ap.expenses.manager.R

class OnlineFragment : Fragment(), OnlineTransactionAdapter.OnTransactionClickListener {

    private var _binding: FragmentOnlineBinding? = null
    private val binding get() = _binding!!

    private lateinit var db: AppDatabase
    private lateinit var transactionAdapter: OnlineTransactionAdapter
    private val paymentApps = mutableListOf<String>()
    private lateinit var spinnerAdapter: ArrayAdapter<String>

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOnlineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = Room.databaseBuilder(requireContext(), AppDatabase::class.java, "expense-manager-db").fallbackToDestructiveMigration().build()
        setupSpinner()
        loadPaymentApps()
        setupRecyclerView()

        updateDateButtonText()
        loadTransactions()

        binding.fabAddOnline.setOnClickListener { showAddTransactionDialog() }
        binding.btnDatePickerOnline.setOnClickListener { showDatePickerDialog() }
        binding.swipeRefreshLayoutOnline.setOnRefreshListener {
            binding.swipeRefreshLayoutOnline.isRefreshing = true
            loadTransactions()
        }
        binding.tvFilterStatus.setOnClickListener { showFilterDialog() }
    }

    private fun setupSpinner() {
        spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, paymentApps)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPaymentApp.adapter = spinnerAdapter
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
        spinnerAdapter.notifyDataSetChanged()
    }

    private fun setupRecyclerView() {
        transactionAdapter = OnlineTransactionAdapter(emptyList(), this)
        binding.recyclerViewOnline.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = transactionAdapter
        }
    }

    private fun loadTransactions() {
        viewLifecycleOwner.lifecycleScope.launch {
            db.onlineTransactionDao().getFilteredTransactions(
                filterStartDate,
                filterEndDate,
                currentFilterType,
                currentFilterPerson,
                currentFilterDesc,
                currentFilterMin,
                currentFilterMax,
                currentFilterApp
            ).collectLatest { transactions ->
                val sortedTransactions = sortTransactions(transactions, currentSortOrder)
                val groupedTransactions = if (isDateRangeFilter) {
                    sortedTransactions.groupBy { it.date }.flatMap { (date, txns) ->
                        listOf(OnlineTransaction(date = date, type = "HEADER", amount = 0.0, person = "", paymentApp = "", description = null, time = null, id = -1)) + txns
                    }
                } else {
                    sortedTransactions
                }
                transactionAdapter.updateData(groupedTransactions)
                updateTotalsUI(sortedTransactions)
                binding.swipeRefreshLayoutOnline.isRefreshing = false
            }
        }
    }

    private fun sortTransactions(transactions: List<OnlineTransaction>, sortOrder: Int): List<OnlineTransaction> {
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

    private fun updateTotalsUI(transactions: List<OnlineTransaction>) {
        val totalIn = transactions.filter { it.type == "IN" }.sumOf { it.amount }
        val totalOut = transactions.filter { it.type == "OUT" }.sumOf { it.amount }
        binding.tvTotalIn.text = String.format("₹%.2f", totalIn)
        binding.tvTotalOut.text = String.format("₹%.2f", totalOut)
    }

    private fun showFilterDialog() {
        val dialogBinding = DialogFilterBinding.inflate(layoutInflater)

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
        dialogBinding.spinnerPaymentAppFilter.visibility = View.VISIBLE
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
                currentFilterApp = dialogBinding.spinnerPaymentAppFilter.selectedItem.toString().let {
                    if (it == "All Apps") null else it
                }
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
        binding.tvFilterStatus.text = "No active filters. Tap to add."
        updateDateButtonText()
    }

    override fun onEditClick(transaction: OnlineTransaction) {
        if (transaction.type != "HEADER") {
            showAddTransactionDialog(transaction)
        }
    }

    override fun onDeleteClick(transaction: OnlineTransaction) {
        if (transaction.type == "HEADER") return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Transaction")
            .setMessage("Are you sure you want to delete this transaction?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    db.onlineTransactionDao().delete(transaction)
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
            binding.btnDatePickerOnline.text = "${uiDateFormat.format(apiDateFormat.parse(filterStartDate)!!)} - ${uiDateFormat.format(apiDateFormat.parse(filterEndDate)!!)}"
        } else {
            binding.btnDatePickerOnline.text = uiDateFormat.format(apiDateFormat.parse(filterStartDate)!!)
        }
    }

    private fun showAddTransactionDialog(transactionToEdit: OnlineTransaction? = null) {
        val dialogBinding = DialogAddTransactionBinding.inflate(layoutInflater)
        val title = if (transactionToEdit == null) "Add Online Transaction" else "Edit Online Transaction"

        val sdfTime = SimpleDateFormat("hh:mm a", Locale.getDefault())
        var selectedTime: String? = transactionToEdit?.time

        val selectedDateForTransaction = if(isDateRangeFilter) apiDateFormat.format(Date()) else filterStartDate

        val dialogSpinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, paymentApps)
        dialogBinding.spinnerPaymentAppDialog.adapter = dialogSpinnerAdapter
        dialogBinding.spinnerPaymentAppDialog.visibility = View.VISIBLE

        transactionToEdit?.let {
            dialogBinding.etAmount.setText(it.amount.toString())
            dialogBinding.etPerson.setText(it.person)
            dialogBinding.etDescription.setText(it.description)
            if (it.type == "IN") dialogBinding.rbMoneyIn.isChecked = true else dialogBinding.rbMoneyOut.isChecked = true
            val spinnerPosition = dialogSpinnerAdapter.getPosition(it.paymentApp)
            if(spinnerPosition >= 0) dialogBinding.spinnerPaymentAppDialog.setSelection(spinnerPosition)
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
                if (paymentApps.isEmpty()){
                    Toast.makeText(context, "Please add a payment app first in Settings.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val amount = amountStr.toDouble()
                val type = if (dialogBinding.rbMoneyIn.isChecked) "IN" else "OUT"
                val description = dialogBinding.etDescription.text.toString().ifBlank { null }
                val paymentApp = dialogBinding.spinnerPaymentAppDialog.selectedItem.toString()

                lifecycleScope.launch {
                    if (transactionToEdit == null) {
                        val newTransaction = OnlineTransaction(date = selectedDateForTransaction, type = type, amount = amount, person = person, paymentApp = paymentApp, description = description, time = selectedTime)
                        db.onlineTransactionDao().insert(newTransaction)
                    } else {
                        val updatedTransaction = transactionToEdit.copy(type = type, amount = amount, person = person, paymentApp = paymentApp, description = description, time = selectedTime)
                        db.onlineTransactionDao().update(updatedTransaction)
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