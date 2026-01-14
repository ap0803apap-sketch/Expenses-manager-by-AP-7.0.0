package com.ap.expenses.manager

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.ap.expenses.manager.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isAppVisibleAndUnlocked = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Fit app to system window (handle notches, cutouts, etc)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        setSupportActionBar(binding.toolbar)

        val adapter = ViewPagerAdapter(this)
        binding.viewPager.adapter = adapter

        // Setup bottom navigation with ViewPager2
        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_transactions -> {
                    binding.viewPager.currentItem = 0
                    true
                }

                R.id.nav_export -> {
                    binding.viewPager.currentItem = 1
                    true
                }

                R.id.nav_settings -> {
                    binding.viewPager.currentItem = 2
                    true
                }

                else -> false
            }
        }

    }


        override fun onStart() {
        super.onStart()
        if (!isAppVisibleAndUnlocked) {
            showBiometricPromptIfNeeded()
        }
    }

    override fun onStop() {
        super.onStop()
        isAppVisibleAndUnlocked = false
    }

    private fun showBiometricPromptIfNeeded() {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val biometricEnabled = prefs.getBoolean("biometric_enabled", false)

        if (!biometricEnabled) {
            isAppVisibleAndUnlocked = true
            return
        }

        binding.lockScreenOverlay.visibility = View.VISIBLE

        if (BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "Biometric hardware unavailable. App is locked.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authentication Required")
            .setSubtitle("Unlock to continue")
            .setNegativeButtonText("Exit App")
            .build()

        val biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isAppVisibleAndUnlocked = true
                    binding.lockScreenOverlay.visibility = View.GONE
                    Toast.makeText(applicationContext, "Unlocked!", Toast.LENGTH_SHORT).show()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        finishAffinity()
                    }
                }
            })

        biometricPrompt.authenticate(promptInfo)
    }
}