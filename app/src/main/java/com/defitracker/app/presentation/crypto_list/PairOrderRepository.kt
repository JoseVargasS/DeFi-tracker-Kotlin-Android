package com.defitracker.app.presentation.crypto_list

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// ponytail: orden manual de pares sin tocar Room (keys "symbol|source")
private val Context.pairOrderDataStore: DataStore<Preferences> by preferencesDataStore("pairs_order")

@Singleton
class PairOrderRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val orderFlow: Flow<List<String>> = context.pairOrderDataStore.data.map { prefs ->
        prefs[stringPreferencesKey("order")]?.split("|")?.filter { it.isNotEmpty() } ?: emptyList()
    }

    suspend fun save(order: List<String>) {
        context.pairOrderDataStore.edit { prefs ->
            prefs[stringPreferencesKey("order")] = order.joinToString("|")
        }
    }
}
