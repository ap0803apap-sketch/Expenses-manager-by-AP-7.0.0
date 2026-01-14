package com.ap.expenses.manager

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.room.Room
import com.ap.expenses.manager.databinding.FragmentSettingsBinding
import com.ap.expenses.manager.databinding.DialogManagePaymentAppsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var db: AppDatabase
    private val paymentApps = mutableListOf<String>()
    private var paymentAppAdapter: PaymentAppAdapter? = null
    private val gson = Gson()

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName

    // For file picker
    private val importRequestCode = 101

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        db = Room.databaseBuilder(requireContext(), AppDatabase::class.java, "expense-manager-db")
            .fallbackToDestructiveMigration().build()

        devicePolicyManager =
            requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(requireContext(), MyDeviceAdminReceiver::class.java)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadSettings()
        setupThemeListeners()
        setupBiometricListener()
        setupDeviceAdminListener()
        setupPaymentAppsButton()
        setupBackupRestoreButtons()
        setupDeveloperInfoActions()   // ✅ THIS WAS MISSING
        loadPaymentApps()
    }


    private fun loadSettings() {
        val prefs = requireActivity().getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        when (prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)) {
            AppCompatDelegate.MODE_NIGHT_NO -> binding.rbThemeLight.isChecked = true
            AppCompatDelegate.MODE_NIGHT_YES -> binding.rbThemeDark.isChecked = true
            else -> binding.rbThemeSystem.isChecked = true
        }

        binding.switchBiometric.isChecked = prefs.getBoolean("biometric_enabled", false)
    }

    private fun setupThemeListeners() {
        binding.rgTheme.setOnCheckedChangeListener { _, checkedId ->
            val newMode = when (checkedId) {
                R.id.rb_theme_light -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.rb_theme_dark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            AppCompatDelegate.setDefaultNightMode(newMode)
            val prefs = requireActivity().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
            prefs.edit { putInt("theme_mode", newMode) }
        }
    }

    private fun setupBiometricListener() {
        binding.switchBiometric.setOnClickListener {
            val isEnabling = binding.switchBiometric.isChecked
            binding.switchBiometric.isChecked = !isEnabling

            if (BiometricManager.from(requireContext())
                    .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                != BiometricManager.BIOMETRIC_SUCCESS
            ) {
                Toast.makeText(
                    context,
                    "Biometric authentication is not available.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val executor = ContextCompat.getMainExecutor(requireContext())
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Authentication")
                .setSubtitle("Confirm your identity to change security settings")
                .setNegativeButtonText("Cancel")
                .build()

            val biometricPrompt = BiometricPrompt(this, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        binding.switchBiometric.isChecked = isEnabling
                        val prefs =
                            requireActivity().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                        prefs.edit { putBoolean("biometric_enabled", isEnabling) }
                        val status = if (isEnabling) "enabled" else "disabled"
                        Toast.makeText(context, "Biometric lock $status", Toast.LENGTH_SHORT).show()
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        Toast.makeText(context, "Authentication failed", Toast.LENGTH_SHORT).show()
                    }
                })
            biometricPrompt.authenticate(promptInfo)
        }
    }

    private fun setupDeviceAdminListener() {
        binding.switchDeviceAdmin.setOnClickListener {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Grant device admin permission to prevent unauthorized uninstalls or clearing app data."
                )
            }
            startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN)
        }
    }

    private fun updateDeviceAdminSwitchState() {
        val isActive = devicePolicyManager.isAdminActive(adminComponent)
        binding.switchDeviceAdmin.isChecked = isActive
        binding.switchDeviceAdmin.isEnabled = !isActive
    }

    override fun onResume() {
        super.onResume()
        updateDeviceAdminSwitchState()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_CODE_ENABLE_ADMIN -> {
                updateDeviceAdminSwitchState()
                val isActive = devicePolicyManager.isAdminActive(adminComponent)
                Toast.makeText(
                    context,
                    if (isActive) "Device admin enabled" else "Device admin not enabled",
                    Toast.LENGTH_SHORT
                ).show()
            }
            importRequestCode -> {
                if (resultCode == android.app.Activity.RESULT_OK) {
                    data?.data?.let { uri ->
                        restoreDatabase(uri)
                    }
                }
            }
        }
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

    private fun savePaymentApps() {
        val prefs = requireActivity().getSharedPreferences("payment_apps", Context.MODE_PRIVATE)
        prefs.edit { putStringSet("apps_list", paymentApps.toSet()) }
    }

    private fun setupPaymentAppsButton() {
        binding.btnManagePaymentApps.setOnClickListener {
            showManageAppsDialog()
        }
    }

    private fun showManageAppsDialog() {
        val dialogBinding = DialogManagePaymentAppsBinding.inflate(layoutInflater)

        paymentAppAdapter = PaymentAppAdapter(paymentApps) { appToDelete ->
            paymentApps.remove(appToDelete)
            savePaymentApps()
            loadPaymentApps()
            paymentAppAdapter?.notifyDataSetChanged()
            Toast.makeText(context, "$appToDelete removed", Toast.LENGTH_SHORT).show()
        }

        dialogBinding.recyclerViewPaymentApps.layoutManager = LinearLayoutManager(context)
        dialogBinding.recyclerViewPaymentApps.adapter = paymentAppAdapter

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Manage Payment Apps")
            .setView(dialogBinding.root)
            .setPositiveButton("Add") { _, _ ->
                val newAppName = dialogBinding.etNewPaymentApp.text.toString().trim()
                if (newAppName.isNotEmpty() && !paymentApps.contains(newAppName)) {
                    paymentApps.add(newAppName)
                    savePaymentApps()
                    loadPaymentApps()
                    paymentAppAdapter?.notifyDataSetChanged()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun setupBackupRestoreButtons() {
        binding.btnExportDatabase.setOnClickListener {
            exportDatabase()
        }
        binding.btnImportDatabase.setOnClickListener {
            openFileChooser()
        }
    }

    private fun exportDatabase() {
        lifecycleScope.launch {
            try {
                val backupData = withContext(Dispatchers.IO) {
                    val cashTransactions = db.cashTransactionDao().getAll()
                    val onlineTransactions = db.onlineTransactionDao().getAll()
                    val prefs = requireActivity().getSharedPreferences("payment_apps", Context.MODE_PRIVATE)
                    val savedApps = prefs.getStringSet("apps_list", null)?.toList() ?: emptyList()

                    BackupData(
                        cashTransactions = cashTransactions,
                        onlineTransactions = onlineTransactions,
                        paymentApps = savedApps,
                        version = "1.0"
                    )
                }

                val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                val fileName = "ExpensesManager_Backup_${sdf.format(Date())}.json"
                val backupJson = gson.toJson(backupData)

                // Save to both locations
                val backupFile = withContext(Dispatchers.IO) {
                    // 1. Save to cache/exports for FileProvider sharing
                    val exportsDir = File(requireContext().cacheDir, "exports")
                    if (!exportsDir.exists()) {
                        exportsDir.mkdirs()
                    }
                    val cacheFile = File(exportsDir, fileName)
                    cacheFile.writeText(backupJson)

                    // 2. Also save to Downloads/Expenses Manager by AP+Backup Database
                    try {
                        val downloadDir = File(
                            android.os.Environment.getExternalStoragePublicDirectory(
                                android.os.Environment.DIRECTORY_DOWNLOADS
                            ),
                            "Expenses Manager by AP - Backup Database"
                        )
                        if (!downloadDir.exists()) {
                            downloadDir.mkdirs()
                        }
                        val downloadsFile = File(downloadDir, fileName)
                        downloadsFile.writeText(backupJson)
                    } catch (e: Exception) {
                        // If Downloads fails, that's okay - we still have cache version
                        e.printStackTrace()
                    }

                    cacheFile
                }

                if (!backupFile.exists()) {
                    Toast.makeText(context, "Failed to create backup file", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Get URI for the file using FileProvider
                val fileUri = try {
                    androidx.core.content.FileProvider.getUriForFile(
                        requireContext(),
                        "${requireContext().packageName}.provider",
                        backupFile
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    return@launch
                }

                // Share the file
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, fileUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                try {
                    startActivity(Intent.createChooser(shareIntent, "Export Expenses Manager Backup"))
                    Toast.makeText(
                        context,
                        "✓ Backup saved to Downloads!\nFolder: Expenses Manager by AP - Backup Database\n\nCash: ${backupData.cashTransactions.size}\nOnline: ${backupData.onlineTransactions.size}",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error sharing file: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Failed to export: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openFileChooser() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, importRequestCode)
    }

    private fun restoreDatabase(uri: Uri) {
        lifecycleScope.launch {
            try {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Restore Database")
                    .setMessage("This will replace all your current data with the backup. This action cannot be undone. Continue?")
                    .setPositiveButton("Restore") { _, _ ->
                        performRestore(uri)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performRestore(uri: Uri) {
        lifecycleScope.launch {
            try {
                var backupData: BackupData? = null

                withContext(Dispatchers.IO) {
                    val inputStream = requireContext().contentResolver.openInputStream(uri)
                    val backupJson = inputStream?.bufferedReader().use { it?.readText() ?: "" }
                    inputStream?.close()

                    if (backupJson.isEmpty()) {
                        throw Exception("Backup file is empty")
                    }

                    backupData = try {
                        gson.fromJson(backupJson, BackupData::class.java)
                    } catch (e: Exception) {
                        throw Exception("Invalid backup file format: ${e.message}")
                    }

                    if (backupData == null) {
                        throw Exception("Failed to parse backup data")
                    }

                    // Clear existing data
                    db.cashTransactionDao().clearTable()
                    db.onlineTransactionDao().clearTable()

                    // Insert backup data
                    if (backupData!!.cashTransactions.isNotEmpty()) {
                        db.cashTransactionDao().insertAll(backupData!!.cashTransactions)
                    }
                    if (backupData!!.onlineTransactions.isNotEmpty()) {
                        db.onlineTransactionDao().insertAll(backupData!!.onlineTransactions)
                    }

                    // Restore payment apps
                    val prefs = requireActivity().getSharedPreferences("payment_apps", Context.MODE_PRIVATE)
                    if (backupData!!.paymentApps.isNotEmpty()) {
                        prefs.edit { putStringSet("apps_list", backupData!!.paymentApps.toSet()) }
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Database restored successfully!\nCash: ${backupData?.cashTransactions?.size ?: 0}\nOnline: ${backupData?.onlineTransactions?.size ?: 0}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to restore: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private fun setupDeveloperInfoActions() {
        binding.layoutGithub.setOnClickListener {
            openUrl("https://github.com/ap0803apap-sketch/Expenses-manager")
        }
        binding.layoutEmail.setOnClickListener {
            startActivity(
                Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:ap0803apap@gmail.com")
                }
            )
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            toast("No app found to open link")
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val REQUEST_CODE_ENABLE_ADMIN = 1001
    }
}

