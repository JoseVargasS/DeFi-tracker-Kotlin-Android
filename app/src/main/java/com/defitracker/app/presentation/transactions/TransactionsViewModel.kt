package com.defitracker.app.presentation.transactions

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.defitracker.app.data.local.WalletEntity
import com.defitracker.app.data.remote.dto.EtherscanTransactionDto
import com.defitracker.app.data.repository.CryptoRepositoryImpl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val INITIAL_TRANSACTION_LIMIT = 5
private const val TRANSACTION_PAGE_INCREMENT = 5

data class TransactionsState(
    val wallets: List<WalletEntity> = emptyList(),
    val selectedAddress: String = "",
    val transactions: List<EtherscanTransactionDto> = emptyList(),
    val requestedLimit: Int = INITIAL_TRANSACTION_LIMIT,
    val canLoadMore: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class TransactionsViewModel @Inject constructor(
    private val repository: CryptoRepositoryImpl
) : ViewModel() {

    private val _state = mutableStateOf(TransactionsState())
    val state: State<TransactionsState> = _state
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
                    fetchTransactions(nextSelectedAddress)
                }
            }
            .launchIn(viewModelScope)
    }

    fun onAddressSelected(address: String) {
        _state.value = _state.value.copy(
            selectedAddress = address,
            requestedLimit = INITIAL_TRANSACTION_LIMIT,
            canLoadMore = false
        )
        fetchTransactions(address, INITIAL_TRANSACTION_LIMIT, forceRefresh = true)
    }

    fun fetchTransactions(
        address: String,
        limit: Int = INITIAL_TRANSACTION_LIMIT,
        forceRefresh: Boolean = true
    ) {
        if (address.isEmpty()) return
        
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                requestedLimit = limit,
                isLoading = true,
                error = null
            )
            try {
                val txs = repository.getWalletTransactions(
                    address = address,
                    limit = limit,
                    forceRefresh = forceRefresh
                )
                _state.value = _state.value.copy(
                    transactions = txs,
                    canLoadMore = txs.size >= limit,
                    isLoading = false
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun loadMoreTransactions() {
        val address = _state.value.selectedAddress
        if (address.isBlank() || _state.value.isLoading) return

        fetchTransactions(
            address = address,
            limit = _state.value.requestedLimit + TRANSACTION_PAGE_INCREMENT,
            forceRefresh = false
        )
    }

}
