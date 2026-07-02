package com.bragbuddy.app

import android.app.Application
import com.bragbuddy.app.notification.Notifications
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BragBuddyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
    }
}
