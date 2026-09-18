package com.defitracker.app

import android.app.Application
import com.defitracker.app.alerts.DivAlertNotifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DeFiTrackerApp : Application() {

    @Inject lateinit var notifier: DivAlertNotifier

    override fun onCreate() {
        super.onCreate()
        // canales de noti listos desde el arranque
        try {
            notifier.ensureChannels()
        } catch (_: Exception) {}
    }
}
