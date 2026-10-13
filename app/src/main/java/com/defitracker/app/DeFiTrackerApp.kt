package com.defitracker.app

import android.app.Application
import com.defitracker.app.alerts.DivAlertNotifier
import com.defitracker.app.alerts.DivMonitorPrefs
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class DeFiTrackerApp : Application() {

    @Inject lateinit var notifier: DivAlertNotifier
    @Inject lateinit var monitorPrefs: DivMonitorPrefs

    override fun onCreate() {
        super.onCreate()
        // canales de noti listos desde el arranque
        try {
            notifier.ensureChannels()
        } catch (_: Exception) {}
        // el canal de precio ya queda con tu tono antes de que suene algo
        try {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    notifier.ensurePriceChannel(monitorPrefs.current().priceSoundUri)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }
}
