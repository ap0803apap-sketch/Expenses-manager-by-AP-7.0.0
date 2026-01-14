package com.ap.expenses.manager

import android.app.Application
import com.google.android.material.color.DynamicColors

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // This line applies the user's wallpaper colors to your app
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}