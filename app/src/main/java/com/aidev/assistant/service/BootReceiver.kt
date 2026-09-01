package com.aidev.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Optionally restart background service after reboot
            // context.startForegroundService(Intent(context, AIBackgroundService::class.java))
        }
    }
}
