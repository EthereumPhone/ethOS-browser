package de.baumann.browser.activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager

class WCReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val wcUrl = intent.data.toString()
        sharedPreferences.edit().putString("walletconnect_uri", wcUrl).apply()
    }
}