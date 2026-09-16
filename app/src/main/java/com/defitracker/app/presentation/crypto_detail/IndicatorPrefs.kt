package com.defitracker.app.presentation.crypto_detail

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    val mas: List<MaConfig> = defaultMas(),
    val fib: FibConfig = FibConfig(),
    // ponytail: smart money concepts, apagados por defecto para no tapar las velas
    val smcStructure: Boolean = false,
    val smcOrderBlocks: Boolean = false,
    val smcFvg: Boolean = false,
    val smcPremium: Boolean = false,
    val smcEqhl: Boolean = false,
    val smcLiquidity: Boolean = false
) {
    // ponytail: cambia si cambia cualquier ajuste -> el chart reconstruye sin resetear zoom
    fun prefsKey(): String = buildString {
        append(bbVisible).append(profileVisible).append(volumeVisible).append(stochVisible).append(rsiVisible)
        mas.forEach { append(it.period).append(it.visible).append(it.colorHex).append(it.width) }
        append(fib.colorHex).append(fib.width).append(fib.hidden).append(fib.enabledLevels.sorted().joinToString(","))
        append(smcStructure).append(smcOrderBlocks).append(smcFvg).append(smcPremium).append(smcEqhl).append(smcLiquidity)
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
            smcStructure = p[booleanPreferencesKey("smc_structure")] ?: false,
            smcOrderBlocks = p[booleanPreferencesKey("smc_ob")] ?: false,
            smcFvg = p[booleanPreferencesKey("smc_fvg")] ?: false,
            smcPremium = p[booleanPreferencesKey("smc_premium")] ?: false,
            smcEqhl = p[booleanPreferencesKey("smc_eqhl")] ?: false,
            smcLiquidity = p[booleanPreferencesKey("smc_liq")] ?: false,
            mas = IndicatorPrefs.MA_PERIODS.map { period ->
                val d = defaults.mas.first { it.period == period }
                MaConfig(
                    period = period,
                    colorHex = p[stringPreferencesKey("ma_${period}_color")] ?: d.colorHex,
                    width = p[floatPreferencesKey("ma_${period}_width")] ?: d.width,
                    visible = p[booleanPreferencesKey("ma_${period}_visible")] ?: d.visible
                )
            },
            // ponytail: config visual del fibo, el dibujo va aparte por simbolo
            fib = FibConfig(
                colorHex = p[stringPreferencesKey("fib_color")] ?: "#FFFFFF",
                width = p[floatPreferencesKey("fib_width")] ?: 1f,
                enabledLevels = p[stringPreferencesKey("fib_levels")]
                    ?.split(",").orEmpty()
                    .mapNotNull { it.toFloatOrNull() }
                    .filter { it in ALL_FIB_LEVELS }
                    .toSet()
                    .ifEmpty { DEFAULT_FIB_LEVELS.toSet() },
                hidden = p[booleanPreferencesKey("fib_hidden")] ?: false
            )
        )
    }

    // ponytail: lista de fibos por simbolo, cada uno con su estilo+estado propio
    fun fibOverlaysFlow(symbol: String): Flow<List<FibOverlay>> =
        context.indicatorDataStore.data.map { p ->
            decodeFibOverlays(p[stringPreferencesKey("fib_overlays_$symbol")])
        }

    suspend fun saveFibOverlays(symbol: String, overlays: List<FibOverlay>) {
        context.indicatorDataStore.edit { e ->
            e[stringPreferencesKey("fib_overlays_$symbol")] = encodeFibOverlays(overlays.take(MAX_FIBS_PER_SYMBOL))
        }
    }

    // ponytail: migracion del fibo unico anterior a la lista, una sola vez
    suspend fun migrateLegacyFib(symbol: String): List<FibOverlay>? {
        val p = context.indicatorDataStore.data.first()
        val sTime = p[longPreferencesKey("fib_${symbol}_s_time")] ?: return null
        val sPrice = p[floatPreferencesKey("fib_${symbol}_s_price")]?.toDouble() ?: return null
        val eTime = p[longPreferencesKey("fib_${symbol}_e_time")] ?: return null
        val ePrice = p[floatPreferencesKey("fib_${symbol}_e_price")]?.toDouble() ?: return null
        val overlay = FibOverlay(
            id = java.util.UUID.randomUUID().toString(),
            start = FibAnchor(sTime, sPrice),
            end = FibAnchor(eTime, ePrice),
            colorHex = p[stringPreferencesKey("fib_color")] ?: "#FFFFFF",
            width = p[floatPreferencesKey("fib_width")] ?: 1f,
            enabledLevels = p[stringPreferencesKey("fib_levels")]
                ?.split(",").orEmpty().mapNotNull { it.toFloatOrNull() }
                .filter { it in ALL_FIB_LEVELS }.toSet().ifEmpty { DEFAULT_FIB_LEVELS.toSet() },
            hidden = p[booleanPreferencesKey("fib_hidden")] ?: false
        )
        context.indicatorDataStore.edit { e ->
            e.remove(longPreferencesKey("fib_${symbol}_s_time"))
            e.remove(floatPreferencesKey("fib_${symbol}_s_price"))
            e.remove(longPreferencesKey("fib_${symbol}_e_time"))
            e.remove(floatPreferencesKey("fib_${symbol}_e_price"))
            e[stringPreferencesKey("fib_overlays_$symbol")] = encodeFibOverlays(listOf(overlay))
        }
        return listOf(overlay)
    }

    suspend fun save(prefs: IndicatorPrefs) {
        context.indicatorDataStore.edit { e ->
            e[booleanPreferencesKey("bb_visible")] = prefs.bbVisible
            e[booleanPreferencesKey("profile_visible")] = prefs.profileVisible
            e[booleanPreferencesKey("volume_visible")] = prefs.volumeVisible
            e[booleanPreferencesKey("stoch_visible")] = prefs.stochVisible
            e[booleanPreferencesKey("rsi_visible")] = prefs.rsiVisible
            e[booleanPreferencesKey("smc_structure")] = prefs.smcStructure
            e[booleanPreferencesKey("smc_ob")] = prefs.smcOrderBlocks
            e[booleanPreferencesKey("smc_fvg")] = prefs.smcFvg
            e[booleanPreferencesKey("smc_premium")] = prefs.smcPremium
            e[booleanPreferencesKey("smc_eqhl")] = prefs.smcEqhl
            e[booleanPreferencesKey("smc_liq")] = prefs.smcLiquidity
            prefs.mas.forEach { ma ->
                e[booleanPreferencesKey("ma_${ma.period}_visible")] = ma.visible
                e[stringPreferencesKey("ma_${ma.period}_color")] = ma.colorHex
                e[floatPreferencesKey("ma_${ma.period}_width")] = ma.width
            }
            e[stringPreferencesKey("fib_color")] = prefs.fib.colorHex
            e[floatPreferencesKey("fib_width")] = prefs.fib.width
            e[stringPreferencesKey("fib_levels")] = prefs.fib.enabledLevels.sorted().joinToString(",")
            e[booleanPreferencesKey("fib_hidden")] = prefs.fib.hidden
        }
    }
}
