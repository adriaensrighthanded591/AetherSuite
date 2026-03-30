package com.aethersms

import android.app.Application
import com.aethersms.service.NotificationService

class AetherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationService.createChannel(this)
    }
}
