package com.defitracker.app.presentation.crypto_detail

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class MaConfig(
    val period: Int,
    val colorHex: String,
    val width: Float,
    val visible: Boolean
)

data class IndicatorPrefs(
    val bbVisible: Boolean = true,
    val profileVisible: Boolean = true,
    val volumeVisible: Boolean = true,
    val stochVisible: Boolean = true,
    val rsiVisible: Boolean = true,
    val mas: List<MaConfig> = defaultMas()
) {
    // ponytail: cambia si cambia cualquier ajuste -> el chart reconstruye sin resetear zoom
    fun prefsKey(): String = buildString {
        append(bbVisible).append(profileVisible).append(volumeVisible).append(stochVisible).append(rsiVisible)
        mas.forEach { append(it.period).append(it.visible).append(it.colorHex).append(it.width) }
    }

    companion object {
        val MA_PERIODS = listOf(20, 55, 75, 100, 200)
        val PRESET_COLORS = listOf(
            "#E91E63", "#22D8EB", "#FFFFFF", "#FFD60A",
            "#26A69A", "#FF9800", "#B39DDB", "#F6465D"
        )

        fun defaultMas() = listOf(
            MaConfig(20, "#E91E63", 1.2f, true),
            MaConfig(55, "#22D8EB", 1.2f, true),
            MaConfig(75, "#FFFFFF", 1.2f, true),
            MaConfig(100, "#FFD60A", 1.2f, true),
            MaConfig(200, "#26A69A", 1.4f, true)
        )

        val DEFAULT = IndicatorPrefs()
    }
}

private val Context.indicatorDataStore: DataStore<Preferences> by preferencesDataStore("indicators")

@Singleton
class IndicatorPrefsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val defaults = IndicatorPrefs.DEFAULT

    val prefsFlow: Flow<IndicatorPrefs> = context.indicatorDataStore.data.map { p ->
        defaults.copy(
            bbVisible = p[booleanPreferencesKey("bb_visible")] ?: true,
            profileVisible = p[booleanPreferencesKey("profile_visible")] ?: true,
            volumeVisible = p[booleanPreferencesKey("volume_visible")] ?: true,
            stochVisible = p[booleanPreferencesKey("stoch_visible")] ?: true,
            rsiVisible = p[booleanPreferencesKey("rsi_visible")] ?: true,
            mas = IndicatorPrefs.MA_PERIODS.map { period ->
                val d = defaults.mas.first { it.period == period }
                MaConfig(
                    period = period,
                    colorHex = p[stringPreferencesKey("ma_${period}_color")] ?: d.colorHex,
                    width = p[floatPreferencesKey("ma_${period}_width")] ?: d.width,
                    visible = p[booleanPreferencesKey("ma_${period}_visible")] ?: d.visible
                )
            }
        )
    }

    suspend fun save(prefs: IndicatorPrefs) {
        context.indicatorDataStore.edit { e ->
            e[booleanPreferencesKey("bb_visible")] = prefs.bbVisible
            e[booleanPreferencesKey("profile_visible")] = prefs.profileVisible
            e[booleanPreferencesKey("volume_visible")] = prefs.volumeVisible
            e[booleanPreferencesKey("stoch_visible")] = prefs.stochVisible
            e[booleanPreferencesKey("rsi_visible")] = prefs.rsiVisible
            prefs.mas.forEach { ma ->
                e[booleanPreferencesKey("ma_${ma.period}_visible")] = ma.visible
                e[stringPreferencesKey("ma_${ma.period}_color")] = ma.colorHex
                e[floatPreferencesKey("ma_${ma.period}_width")] = ma.width
            }
        }
    }
}
