package com.ap.expenses.manager

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.ap.expenses.manager.databinding.FragmentExportBinding
import com.google.android.material.datepicker.MaterialDatePicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFCellStyle
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

class ExportFragment : Fragment() {

    private var _binding: FragmentExportBinding? = null
    private val binding get() = _binding!!

    private var startDate = MaterialDatePicker.todayInUtcMilliseconds()
    private var endDate = MaterialDatePicker.todayInUtcMilliseconds()
    private lateinit var db: AppDatabase

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        db = Room.databaseBuilder(requireContext(), AppDatabase::class.java, "expense-manager-db")
            .fallbackToDestructiveMigration().build()
        setupListeners()
        updateDateRangeTextView()
    }

    private fun setupListeners() {
        binding.btnStartDate.setOnClickListener { showDatePickerDialog(isStartDate = true) }
        binding.btnEndDate.setOnClickListener { showDatePickerDialog(isStartDate = false) }

        binding.rgExportScope.setOnCheckedChangeListener { _, checkedId ->
            binding.rgExportSpecificChoice.visibility =
                if (checkedId == R.id.rb_export_specific) View.VISIBLE else View.GONE
        }

        binding.btnSaveExport.setOnClickListener {
            exportData(saveToDownloads = true)
        }

        binding.btnShareExport.setOnClickListener {
            exportData(saveToDownloads = false)
        }
    }

    private fun exportData(saveToDownloads: Boolean) {
        lifecycleScope.launch {
            val workbook = createWorkbookWithData()
            if (workbook == null) {
                Toast.makeText(context, "No data to export for the selected range.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            if (saveToDownloads) {
                saveWorkbookToDownloads(workbook)
            } else {
                shareWorkbook(workbook)
            }
        }
    }

    private fun showDatePickerDialog(isStartDate: Boolean) {
        val selectedDateInMillis = if (isStartDate) startDate else endDate
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(if (isStartDate) "Select Start Date" else "Select End Date")
            .setSelection(selectedDateInMillis)
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val timeZone = TimeZone.getDefault()
            val offset = timeZone.getOffset(selection)
            val adjustedSelection = selection - offset

            if (isStartDate) {
                if (adjustedSelection > endDate) {
                    Toast.makeText(context, "Start date cannot be after the end date.", Toast.LENGTH_SHORT).show()
                    return@addOnPositiveButtonClickListener
                }
                startDate = adjustedSelection
            } else {
                if (adjustedSelection < startDate) {
                    Toast.makeText(context, "End date cannot be before the start date.", Toast.LENGTH_SHORT).show()
                    return@addOnPositiveButtonClickListener
                }
                endDate = adjustedSelection
            }
            updateDateRangeTextView()
        }
        datePicker.show(childFragmentManager, if (isStartDate) "START_DATE_PICKER" else "END_DATE_PICKER")
    }

    private fun updateDateRangeTextView() {
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        binding.btnStartDate.text = sdf.format(Date(startDate))
        binding.btnEndDate.text = sdf.format(Date(endDate))
        binding.tvDateRange.text = "${binding.btnStartDate.text} - ${binding.btnEndDate.text}"
    }

    private suspend fun createWorkbookWithData(): Workbook? = withContext(Dispatchers.IO) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val startDateStr = sdf.format(Date(startDate))
        val endDateStr = sdf.format(Date(endDate))

        val exportBoth = binding.rbExportBoth.isChecked
        val exportCash = binding.rbExportCash.isChecked
        val exportOnline = binding.rbExportOnline.isChecked
        val exportSpecific = binding.rbExportSpecific.isChecked

        val cashTransactions = if (exportBoth || (exportSpecific && exportCash)) {
            db.cashTransactionDao().getTransactionsBetweenDates(startDateStr, endDateStr)
        } else emptyList()
        val onlineTransactions = if (exportBoth || (exportSpecific && exportOnline)) {
            db.onlineTransactionDao().getTransactionsBetweenDates(startDateStr, endDateStr)
        } else emptyList()

        if (cashTransactions.isEmpty() && onlineTransactions.isEmpty()) return@withContext null

        // Combine cash and online transactions
        data class MixedTransaction(
            val date: String,
            val time: String?,
            val type: String,
            val amount: Double,
            val person: String,
            val description: String?,
            val mode: String,  // "cash" or "online"
            val paymentApp: String? = null
        )

        val allTransactions = mutableListOf<MixedTransaction>()

        // Add cash transactions
        cashTransactions.forEach {
            allTransactions.add(MixedTransaction(
                date = it.date,
                time = it.time,
                type = it.type,
                amount = it.amount,
                person = it.person,
                description = it.description,
                mode = "cash",
                paymentApp = null
            ))
        }

        // Add online transactions
        onlineTransactions.forEach {
            allTransactions.add(MixedTransaction(
                date = it.date,
                time = it.time,
                type = it.type,
                amount = it.amount,
                person = it.person,
                description = it.description,
                mode = "online",
                paymentApp = it.paymentApp
            ))
        }

        // Sort all transactions together
        allTransactions.sortWith(compareBy(
            { it.date },
            { it.time == null },  // Transactions with time first
            { it.time ?: "" },     // Sort by time
            { it.person }          // Alphabetical fallback
        ))

        val workbook: Workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Exported Data") as XSSFSheet

        // Create styles
        val titleStyle = createTitleStyle(workbook)
        val metadataLabelStyle = createMetadataLabelStyle(workbook)
        val metadataValueStyle = createMetadataValueStyle(workbook)
        val tableHeaderStyle = createTableHeaderStyle(workbook)
        val dataCellStyle = createDataCellStyle(workbook)
        val amountCellStyle = createAmountCellStyle(workbook)
        val typeCellStyleIn = createTypeCellStyleIn(workbook)
        val typeCellStyleOut = createTypeCellStyleOut(workbook)

        val titleRow = sheet.createRow(0)
        titleRow.heightInPoints = 25f
        val titleCell = titleRow.createCell(0)
        titleCell.setCellValue("${getString(R.string.app_name)} - Transaction Report")
        titleCell.cellStyle = titleStyle
        sheet.addMergedRegion(CellRangeAddress(0, 0, 0, 7))

        val sdfExport = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val dateRange = "${sdf.format(Date(startDate))} to ${sdf.format(Date(endDate))}"
        val dataType = when {
            exportBoth -> "Cash & Online Transactions"
            exportCash -> "Cash Transactions Only"
            exportOnline -> "Online Transactions Only"
            else -> "N/A"
        }

        val metadata = mapOf(
            "Export Date" to sdfExport.format(Date()),
            "Date Range" to dateRange,
            "Data Type" to dataType,
            "Developer" to "AP (ap0803apap@gmail.com)"
        )

        var rowIndex = 2
        metadata.forEach { (key, value) ->
            val row = sheet.createRow(rowIndex)
            row.heightInPoints = 18f
            row.createCell(0).apply { setCellValue(key); cellStyle = metadataLabelStyle }
            row.createCell(1).apply { setCellValue(value); cellStyle = metadataValueStyle }
            rowIndex++
        }

        rowIndex += 1
        val tableHeaderTitles = listOf("Date", "Time", "Type", "Amount", "Person", "Description", "Transaction Mode", "Payment App")
        val tableHeaderRow = sheet.createRow(rowIndex)
        tableHeaderRow.heightInPoints = 20f
        tableHeaderTitles.forEachIndexed { index, title ->
            tableHeaderRow.createCell(index).apply {
                setCellValue(title)
                cellStyle = tableHeaderStyle
            }
        }

        // Set optimized column widths
        sheet.setColumnWidth(0, 3500)   // Date
        sheet.setColumnWidth(1, 3000)   // Time
        sheet.setColumnWidth(2, 3500)   // Type
        sheet.setColumnWidth(3, 3500)   // Amount
        sheet.setColumnWidth(4, 4500)   // Person
        sheet.setColumnWidth(5, 7500)   // Description
        sheet.setColumnWidth(6, 4000)   // Transaction Mode
        sheet.setColumnWidth(7, 4500)   // Payment App

        rowIndex += 1
        allTransactions.forEach { transaction ->
            val row = sheet.createRow(rowIndex++)
            row.heightInPoints = 25f
            row.createCell(0).apply { setCellValue(transaction.date); cellStyle = dataCellStyle }
            row.createCell(1).apply { setCellValue(transaction.time ?: "Time not selected"); cellStyle = dataCellStyle }
            row.createCell(2).apply {
                setCellValue(transaction.type)
                cellStyle = if (transaction.type == "IN") typeCellStyleIn else typeCellStyleOut
            }
            row.createCell(3).apply { setCellValue(transaction.amount); cellStyle = amountCellStyle }
            row.createCell(4).apply { setCellValue(transaction.person); cellStyle = dataCellStyle }
            row.createCell(5).apply { setCellValue(transaction.description); cellStyle = dataCellStyle }
            row.createCell(6).apply { setCellValue(if (transaction.mode == "cash") "Cash" else "Online"); cellStyle = dataCellStyle }
            row.createCell(7).apply { setCellValue(transaction.paymentApp ?: "N/A"); cellStyle = dataCellStyle }
        }

        return@withContext workbook
    }

    private fun createTitleStyle(workbook: Workbook): XSSFCellStyle {
        return workbook.createCellStyle().apply {
            val font = workbook.createFont().apply {
                bold = true
                fontHeightInPoints = 16
                color = IndexedColors.WHITE.index
            }
            setFont(font)
            fillForegroundColor = IndexedColors.DARK_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            borderBottom = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
            borderTop = BorderStyle.THIN
        } as XSSFCellStyle
    }

    private fun createMetadataLabelStyle(workbook: Workbook): XSSFCellStyle {
        return workbook.createCellStyle().apply {
            val font = workbook.createFont().apply {
                bold = true
                fontHeightInPoints = 10
                color = IndexedColors.BLUE.index
            }
            setFont(font)
            fillForegroundColor = IndexedColors.LIGHT_CORNFLOWER_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.LEFT
            verticalAlignment = VerticalAlignment.CENTER
            borderBottom = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
            borderTop = BorderStyle.THIN
        } as XSSFCellStyle
    }

    private fun createMetadataValueStyle(workbook: Workbook): XSSFCellStyle {
        return workbook.createCellStyle().apply {
            val font = workbook.createFont().apply {
                fontHeightInPoints = 10
            }
            setFont(font)
            fillForegroundColor = IndexedColors.LIGHT_YELLOW.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.LEFT
            verticalAlignment = VerticalAlignment.TOP
            wrapText = true
            borderBottom = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
            borderTop = BorderStyle.THIN
        } as XSSFCellStyle
    }

    private fun createTableHeaderStyle(workbook: Workbook): XSSFCellStyle {
        return workbook.createCellStyle().apply {
            val font = workbook.createFont().apply {
                bold = true
                fontHeightInPoints = 11
                color = IndexedColors.WHITE.index
            }
            setFont(font)
            fillForegroundColor = IndexedColors.DARK_TEAL.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            wrapText = true
            borderBottom = BorderStyle.MEDIUM
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
            borderTop = BorderStyle.MEDIUM
        } as XSSFCellStyle
    }

    private fun createDataCellStyle(workbook: Workbook): XSSFCellStyle {
        return workbook.createCellStyle().apply {
            val font = workbook.createFont().apply {
                fontHeightInPoints = 10
            }
            setFont(font)
            fillForegroundColor = IndexedColors.WHITE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.LEFT
            verticalAlignment = VerticalAlignment.TOP
            wrapText = true
            borderBottom = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
            borderTop = BorderStyle.THIN
        } as XSSFCellStyle
    }

    private fun createAmountCellStyle(workbook: Workbook): XSSFCellStyle {
        return workbook.createCellStyle().apply {
            val font = workbook.createFont().apply {
                bold = true
                fontHeightInPoints = 10
                color = IndexedColors.RED.index
            }
            setFont(font)
            fillForegroundColor = IndexedColors.LIGHT_YELLOW.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.RIGHT
            verticalAlignment = VerticalAlignment.CENTER
            dataFormat = workbook.createDataFormat().getFormat("#,##0.00")
            borderBottom = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
            borderTop = BorderStyle.THIN
        } as XSSFCellStyle
    }

    private fun createTypeCellStyleIn(workbook: Workbook): XSSFCellStyle {
        return workbook.createCellStyle().apply {
            val font = workbook.createFont().apply {
                bold = true
                fontHeightInPoints = 10
                color = IndexedColors.WHITE.index
            }
            setFont(font)
            fillForegroundColor = IndexedColors.GREEN.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            borderBottom = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
            borderTop = BorderStyle.THIN
        } as XSSFCellStyle
    }

    private fun createTypeCellStyleOut(workbook: Workbook): XSSFCellStyle {
        return workbook.createCellStyle().apply {
            val font = workbook.createFont().apply {
                bold = true
                fontHeightInPoints = 10
                color = IndexedColors.WHITE.index
            }
            setFont(font)
            fillForegroundColor = IndexedColors.RED.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
            borderBottom = BorderStyle.THIN
            borderLeft = BorderStyle.THIN
            borderRight = BorderStyle.THIN
            borderTop = BorderStyle.THIN
        } as XSSFCellStyle
    }

    private suspend fun saveWorkbookToDownloads(workbook: Workbook) = withContext(Dispatchers.IO) {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = "Export_${sdf.format(Date())}.xlsx"
        var outputStream: OutputStream? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = requireContext().contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Expenses manager by AP")
                }
                val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                outputStream = uri?.let { resolver.openOutputStream(it) }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val appDir = File(downloadsDir, "Expenses manager by AP")
                if (!appDir.exists()) appDir.mkdirs()
                val file = File(appDir, fileName)
                outputStream = FileOutputStream(file)
            }

            outputStream?.use {
                workbook.write(it)
            }
            workbook.close()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Saved to Downloads/Expenses manager by AP", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to save file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun shareWorkbook(workbook: Workbook) = withContext(Dispatchers.IO) {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val fileName = "Export_${sdf.format(Date())}.xlsx"

        val cachePath = File(requireContext().cacheDir, "exports")
        if (!cachePath.exists()) cachePath.mkdirs()
        val file = File(cachePath, fileName)

        try {
            FileOutputStream(file).use { outputStream ->
                workbook.write(outputStream)
            }
            workbook.close()

            val fileUri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.provider", file)

            withContext(Dispatchers.Main) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share Excel File"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed to prepare file for sharing", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}