package com.ap.expenses.manager

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.ap.expenses.manager.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyTheme()
        checkBiometricStatus()
    }

    private fun applyTheme() {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(themeMode)
    }

    private fun checkBiometricStatus() {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val biometricEnabled = prefs.getBoolean("biometric_enabled", false)

        if (biometricEnabled) {
            binding.progressBar.visibility = View.GONE
            binding.tvAuthStatus.visibility = View.VISIBLE
            binding.btnTryAgain.visibility = View.GONE
            showBiometricPrompt()
        } else {
            Handler(Looper.getMainLooper()).postDelayed({
                navigateToMain()
            }, 1500)
        }
    }

    private fun showBiometricPrompt() {
        if (BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "Biometric lock is enabled, but no biometrics are enrolled.", Toast.LENGTH_LONG).show()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Biometric Lock")
            .setSubtitle("Unlock Expenses Manager")
            .setNegativeButtonText("Exit App")
            .build()

        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    navigateToMain()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // Show Try Again button instead of exiting
                    binding.tvAuthStatus.visibility = View.GONE
                    binding.btnTryAgain.visibility = View.VISIBLE
                    binding.btnTryAgain.setOnClickListener {
                        binding.btnTryAgain.visibility = View.GONE
                        binding.tvAuthStatus.visibility = View.VISIBLE
                        showBiometricPrompt()
                    }
                }
            })

        biometricPrompt.authenticate(promptInfo)
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}