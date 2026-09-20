package com.defitracker.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.appwidget.*
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import com.defitracker.app.domain.model.CryptoPair
import com.defitracker.app.domain.repository.CryptoRepository
import com.defitracker.app.ui.theme.AppGreen
import com.defitracker.app.ui.theme.AppRed
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private val WidgetDarkGray = fixedColorProvider(Color(0xFF16171A))
private val WidgetWhite = fixedColorProvider(Color(0xFFFFFFFF))
private val WidgetGreen = fixedColorProvider(AppGreen)
private val WidgetRed = fixedColorProvider(AppRed)

private fun fixedColorProvider(color: Color): ColorProvider = object : ColorProvider {
    override fun getColor(context: Context): Color = context.applicationContext.let { color }
}

class CryptoWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CryptoWidgetEntryPoint {
        fun repository(): CryptoRepository
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext ?: context
        val entryPoint = EntryPointAccessors.fromApplication(
            appContext,
            CryptoWidgetEntryPoint::class.java
        )
        val repository = entryPoint.repository()
        
        val trackedPairs = repository.getTrackedPairs().first().take(4)
        val updatedPairs = trackedPairs.map { pair ->
            try {
                val detail = repository.getPairDetail(pair.symbol, pair.source)
                pair.copy(price = detail.price, priceChangePercent = detail.priceChangePercent, isPositive = detail.isPositive)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                pair
            }
        }

        provideContent {
            GlanceTheme {
                WidgetContent(updatedPairs)
            }
        }
    }

    @Composable
    private fun WidgetContent(pairs: List<CryptoPair>) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(WidgetDarkGray)
                .padding(12.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DeFi Tracker Live",
                    style = TextStyle(
                        color = WidgetWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = "Refresh",
                    modifier = GlanceModifier
                        .padding(4.dp)
                        .clickable(actionRunCallback<RefreshActionCallback>()),
                    style = TextStyle(
                        color = WidgetGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(modifier = GlanceModifier.height(12.dp))
            if (pairs.isEmpty()) {
                Text(text = "No pairs added", style = TextStyle(color = WidgetWhite))
            } else {
                pairs.forEach { pair ->
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${pair.baseAsset}/${pair.quoteAsset}",
                            style = TextStyle(color = WidgetWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        )
                        Spacer(modifier = GlanceModifier.defaultWeight())
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${pair.price} ${pair.quoteAsset}",
                                style = TextStyle(color = WidgetWhite, fontSize = 14.sp)
                            )
                            val priceChangeColor = if (pair.isPositive) WidgetGreen else WidgetRed
                            Text(
                                text = "${if(pair.isPositive) "+" else ""}${pair.priceChangePercent}%",
                                style = TextStyle(color = priceChangeColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            )
                        }
                    }
                }
            }
        }
    }
}

class RefreshActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        CryptoWidget().update(context, glanceId)
    }
}

class CryptoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CryptoWidget()
}
