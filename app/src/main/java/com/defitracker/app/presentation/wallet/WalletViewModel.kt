package com.defitracker.app.presentation.wallet

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.defitracker.app.data.local.WalletEntity
import com.defitracker.app.data.remote.dto.CoinStatsBalanceDto
import com.defitracker.app.data.repository.CryptoRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WalletState(
    val wallets: List<WalletEntity> = emptyList(),
    val selectedAddress: String = "",
    val balances: Map<String, List<CoinStatsBalanceDto>> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val repository: CryptoRepositoryImpl
) : ViewModel() {

    private val _state = mutableStateOf(WalletState())
    val state: State<WalletState> = _state

    private var fetchJob: Job? = null

    init {
        getSavedWallets()
    }

    private fun getSavedWallets() {
        repository.getSavedWallets()
            .onEach { wallets ->
                val selectedAddress = _state.value.selectedAddress
                val nextSelectedAddress = when {
                    wallets.isEmpty() -> ""
                    selectedAddress.isBlank() -> wallets.first().address
                    wallets.any { it.address == selectedAddress } -> selectedAddress
                    else -> wallets.first().address
                }

                _state.value = _state.value.copy(
                    wallets = wallets,
                    selectedAddress = nextSelectedAddress
                )

                if (nextSelectedAddress.isNotBlank() && nextSelectedAddress != selectedAddress) {
                    fetchBalances(nextSelectedAddress)
                }
            }
            .launchIn(viewModelScope)
    }

    fun onAddressSelected(address: String) {
        _state.value = _state.value.copy(selectedAddress = address)
        fetchBalances(address)
    }

    fun fetchBalances(address: String) {
        if (address.isEmpty()) return
        
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val balances = repository.getWalletBalances(address)
                _state.value = _state.value.copy(balances = balances, isLoading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun saveWallet(address: String, name: String) {
        viewModelScope.launch {
            repository.saveWallet(address, name)
        }
    }

    fun deleteWallet(address: String) {
        viewModelScope.launch {
            repository.deleteWallet(address)
        }
    }
}
