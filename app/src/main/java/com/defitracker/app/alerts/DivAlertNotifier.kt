package com.defitracker.app.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.defitracker.app.MainActivity
import com.defitracker.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// canal + push con flecha verde/roja segun direccion, tap abre la grafica en ese TF
@Singleton
class DivAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS, "Divergencias RSI",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Avisos de divergencias alcistas/bajistas en futuros" }
        )
        // canal viejo LOW eliminado, el MIN no muestra icono en la barra de estado
        nm.deleteNotificationChannel(CHANNEL_SERVICE_LEGACY)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE, "Monitoreo",
                NotificationManager.IMPORTANCE_MIN
            ).apply { description = "Vigilancia silenciosa de divergencias" }
        )
    }

    fun serviceNotification(): android.app.Notification {
        ensureChannels()
        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setContentTitle("Monitoreo activo")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pi)
            .setOngoing(true)
            // muda, sin hora, prioridad minima pa' que no pinte en la barra
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun notifyDivergence(
        symbol: String,
        source: String,
        interval: String,
        bullish: Boolean,
        kindLabel: String,
        alertIdHash: Int,
        tag: String,
        pre: Boolean = false
    ) {
        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannels()
        // MEXC usa DOGE_USDT, pero se muestra junto DOGEUSDT
        val displaySymbol = symbol.replace("_", "")
        val message = "¡$tag en $interval en $displaySymbol!"
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_ALERT
            putExtra(MainActivity.EXTRA_ALERT_SYMBOL, symbol)
            putExtra(MainActivity.EXTRA_ALERT_SOURCE, source)
            putExtra(MainActivity.EXTRA_ALERT_INTERVAL, interval)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, alertIdHash, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val noti = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            // flecha arriba/abajo, color verde/rojo segun direccion
            .setSmallIcon(
                if (bullish) R.drawable.ic_arrow_up_bold
                else R.drawable.ic_arrow_down_bold
            )
            .setColor(
                when {
                    pre -> 0xFFFFD60A.toInt()
                    bullish -> 0xFF1ECB81.toInt()
                    else -> 0xFFF6465D.toInt()
                }
            )
            .setContentTitle(message)
            .setContentText("$kindLabel · Toca para ver la gráfica en $interval")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$kindLabel · Toca para ver la gráfica en $interval"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        NotificationManagerCompat.from(context).notify(alertIdHash, noti)
    }

    fun notifySignal(
        symbol: String,
        source: String,
        interval: String,
        bullish: Boolean,
        title: String,
        text: String,
        alertIdHash: Int
    ) {
        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannels()
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_ALERT
            putExtra(MainActivity.EXTRA_ALERT_SYMBOL, symbol)
            putExtra(MainActivity.EXTRA_ALERT_SOURCE, source)
            putExtra(MainActivity.EXTRA_ALERT_INTERVAL, interval)
            putExtra(MainActivity.EXTRA_OPEN_ANALYSIS, true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, alertIdHash, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val noti = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(
                if (bullish) R.drawable.ic_arrow_up_bold
                else R.drawable.ic_arrow_down_bold
            )
            .setColor(if (bullish) 0xFF1ECB81.toInt() else 0xFFF6465D.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text · Se abre el análisis al entrar"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        NotificationManagerCompat.from(context).notify(alertIdHash, noti)
    }

    companion object {
        const val CHANNEL_ALERTS = "div_alerts"
        const val CHANNEL_SERVICE = "div_monitor_min"
        // canal legacy LOW que ya quedo creado en instalaciones previas
        const val CHANNEL_SERVICE_LEGACY = "div_monitor"
        const val SERVICE_NOTI_ID = 7701
    }
}
