package com.defitracker.app.data.remote.dto

data class CoinStatsTransactionsResponse(
    val result: List<CoinStatsTransactionDto> = emptyList()
)

data class CoinStatsTransactionSyncRequest(
    val wallets: List<CoinStatsTransactionSyncWalletDto>
)

data class CoinStatsTransactionSyncWalletDto(
    val address: String,
    val connectionId: String
)

data class CoinStatsTransactionSyncResponse(
    val status: String? = null
)

data class CoinStatsTransactionDto(
    val type: String? = null,
    val date: String? = null,
    val coinData: CoinStatsTransactionCoinDataDto? = null,
    val transactions: List<CoinStatsTransactionGroupDto> = emptyList(),
    val hash: CoinStatsTransactionHashDto? = null
)

data class CoinStatsTransactionCoinDataDto(
    val count: Double? = null,
    val symbol: String? = null
)

data class CoinStatsTransactionGroupDto(
    val action: String? = null,
    val items: List<CoinStatsTransactionItemDto> = emptyList()
)

data class CoinStatsTransactionItemDto(
    val id: String? = null,
    val count: Double? = null,
    val toAddress: String? = null,
    val fromAddress: String? = null,
    val coin: CoinStatsTransactionCoinDto? = null
)

data class CoinStatsTransactionCoinDto(
    val id: String? = null,
    val name: String? = null,
    val symbol: String? = null
)

data class CoinStatsTransactionHashDto(
    val id: String? = null
)
