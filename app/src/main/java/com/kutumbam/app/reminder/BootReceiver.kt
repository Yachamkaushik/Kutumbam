package com.kutumbam.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Alarms are wiped by a reboot, an app update, or a clock/time-zone change, so re-arm them all. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { ReminderScheduler.scheduleAll(context) } finally { pending.finish() }
        }
    }
}
