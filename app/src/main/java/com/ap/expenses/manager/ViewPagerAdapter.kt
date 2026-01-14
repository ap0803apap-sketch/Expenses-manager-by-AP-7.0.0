package com.ap.expenses.manager

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    // Change item count to 3 (Transactions, Export, Settings)
    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> TransactionFragment()
            1 -> ExportFragment()
            else -> SettingsFragment()
        }
    }
}