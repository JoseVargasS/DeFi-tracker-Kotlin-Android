package com.defitracker.app.alerts

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.ui.graphics.toArgb
import com.defitracker.app.MainActivity
import com.defitracker.app.R
import com.defitracker.app.ui.theme.AppGold
import com.defitracker.app.ui.theme.AppGreen
import com.defitracker.app.ui.theme.AppRed
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

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

    // Cada tono vive en su propio canal: en algunas capas (MIUI/HyperOS) borrar y
    // recrear el mismo ID restaura el sonido viejo, con ID fresco el tono si pega.
    fun priceChannelIdFor(soundUri: String): String {
        if (soundUri.isBlank()) return CHANNEL_PRICE
        val h = resolveSound(soundUri).toString().hashCode()
        val tag = if (h == Int.MIN_VALUE) "min" else abs(h).toString()
        return "${CHANNEL_PRICE}_$tag"
    }

    // canal propio de alertas de precio con el tono elegido.
    // El sonido se fija al crear el canal: cada tono tiene su ID, no se pelea con el viejo.
    fun ensurePriceChannel(soundUri: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val sound = resolveSound(soundUri)
        val id = priceChannelIdFor(soundUri)
        if (nm.getNotificationChannel(id) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    id, "Alertas de precio",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Avisos LONG/SHORT cuando el precio toca tu zona"
                    setSound(
                        sound,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                }
            )
        }
        // canales de tonos viejos pa' fuera, que no estorben en ajustes
        try {
            nm.notificationChannels.forEach { ch ->
                val cid = ch.id.orEmpty()
                if (cid.startsWith(CHANNEL_PRICE) && cid != id) {
                    nm.deleteNotificationChannel(cid)
                }
            }
        } catch (_: Exception) {}
    }

    private fun resolveSound(soundUri: String): Uri {
        if (soundUri.isBlank()) return Settings.System.DEFAULT_NOTIFICATION_URI
        return try {
            val parsed = Uri.parse(soundUri.trim())
            if (parsed == Uri.EMPTY || parsed.scheme.isNullOrBlank()) {
                Settings.System.DEFAULT_NOTIFICATION_URI
            } else {
                parsed
            }
        } catch (_: Exception) {
            Settings.System.DEFAULT_NOTIFICATION_URI
        }
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

    // un solo armado de push: permiso + canal + intent + flecha verde/roja + builder
    private fun post(
        alertIdHash: Int,
        bullish: Boolean,
        color: Int,
        title: String,
        content: String,
        big: String,
        channel: String = CHANNEL_ALERTS,
        soundUri: String = "",
        putExtras: Intent.() -> Unit
    ) {
        if (ActivityCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannels()
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_ALERT
            putExtras()
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, alertIdHash, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val noti = NotificationCompat.Builder(context, channel)
            // pre-O no hay canales: el tono va en la noti misma
            .apply {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O && soundUri.isNotBlank()) {
                    setSound(resolveSound(soundUri))
                }
            }
            // flecha arriba/abajo, color verde/rojo segun direccion
            .setSmallIcon(
                if (bullish) R.drawable.ic_arrow_up_bold
                else R.drawable.ic_arrow_down_bold
            )
            .setColor(color)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(big))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        NotificationManagerCompat.from(context).notify(alertIdHash, noti)
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
        // MEXC usa DOGE_USDT, pero se muestra junto DOGEUSDT
        val displaySymbol = symbol.replace("_", "")
        val message = "¡$tag en $interval en $displaySymbol!"
        val text = "$kindLabel · Toca para ver la gráfica en $interval"
        post(
            alertIdHash, bullish,
            when {
                pre -> AppGold.toArgb()
                bullish -> AppGreen.toArgb()
                else -> AppRed.toArgb()
            },
            message, text, text
        ) {
            putExtra(MainActivity.EXTRA_ALERT_SYMBOL, symbol)
            putExtra(MainActivity.EXTRA_ALERT_SOURCE, source)
            putExtra(MainActivity.EXTRA_ALERT_INTERVAL, interval)
        }
    }

    // alerta de precio LONG/SHORT: mismo id por alerta, si la noti sigue en la barra
    // solo se actualiza, no duplica. El throttle de 5 min vive en el service.
    fun notifyPriceAlert(
        symbol: String,
        source: String,
        direction: String,
        touchedPrice: Double,
        targetTxt: String,
        alertIdHash: Int,
        soundUri: String = ""
    ) {
        val displaySymbol = symbol.replace("_", "")
        val dir = direction.uppercase().takeIf { it == "SHORT" } ?: "LONG"
        val priceTxt = if (touchedPrice > 0) com.defitracker.app.presentation.crypto_detail.formatPriceForChart(touchedPrice) else "tu zona"
        val title = "$displaySymbol llegó a tu zona de $dir!"
        val text = "Objetivo $targetTxt · tocó $priceTxt · Toca para ver la gráfica"
        val bullish = dir == "LONG"
        ensurePriceChannel(soundUri)
        post(
            alertIdHash, bullish,
            if (bullish) AppGreen.toArgb() else AppRed.toArgb(),
            title, text, text,
            channel = priceChannelIdFor(soundUri),
            soundUri = soundUri
        ) {
            putExtra(MainActivity.EXTRA_ALERT_SYMBOL, symbol)
            putExtra(MainActivity.EXTRA_ALERT_SOURCE, source)
        }
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
        post(
            alertIdHash, bullish,
            if (bullish) AppGreen.toArgb() else AppRed.toArgb(),
            title, text, "$text · Se abre el análisis al entrar"
        ) {
            putExtra(MainActivity.EXTRA_ALERT_SYMBOL, symbol)
            putExtra(MainActivity.EXTRA_ALERT_SOURCE, source)
            putExtra(MainActivity.EXTRA_ALERT_INTERVAL, interval)
            putExtra(MainActivity.EXTRA_OPEN_ANALYSIS, true)
        }
    }

    companion object {
        const val CHANNEL_ALERTS = "div_alerts"
        const val CHANNEL_PRICE = "price_alerts"
        const val CHANNEL_SERVICE = "div_monitor_min"
        // canal legacy LOW que ya quedo creado en instalaciones previas
        const val CHANNEL_SERVICE_LEGACY = "div_monitor"
        const val SERVICE_NOTI_ID = 7701
    }
}
