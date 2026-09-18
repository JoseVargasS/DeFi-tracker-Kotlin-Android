package com.defitracker.app.alerts

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.divMonitorDataStore by preferencesDataStore("div_monitor")

data class DivMonitorConfig(
    val enabled: Boolean = true,
    val intervals: Set<String> = setOf("5m", "15m", "30m", "1h")
)

@Singleton
class DivMonitorPrefs @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val configFlow: Flow<DivMonitorConfig> = context.divMonitorDataStore.data.map { p ->
        DivMonitorConfig(
            enabled = p[booleanPreferencesKey("enabled")] ?: true,
            intervals = p[stringPreferencesKey("intervals")]
                ?.split(",").orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                .ifEmpty { DivMonitorConfig().intervals }
        )
    }

    suspend fun current(): DivMonitorConfig = configFlow.first()

    suspend fun setEnabled(enabled: Boolean) {
        context.divMonitorDataStore.edit { it[booleanPreferencesKey("enabled")] = enabled }
    }

    suspend fun setIntervals(intervals: Set<String>) {
        val clean = intervals.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            .ifEmpty { DivMonitorConfig().intervals }
        context.divMonitorDataStore.edit { it[stringPreferencesKey("intervals")] = clean.joinToString(",") }
    }

    companion object {
        val ALL_INTERVALS = listOf(
            "1m", "5m", "15m", "30m", "1h", "2h", "4h",
            "6h", "12h", "1d", "3d", "5d", "1w", "2w", "1mo"
        )
    }
}
