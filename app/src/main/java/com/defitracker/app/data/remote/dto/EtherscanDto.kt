package com.defitracker.app.data.remote.dto

data class EtherscanTransactionDto(
    val hash: String,
    val from: String,
    val to: String,
    val value: String,
    val timeStamp: String,
    val tokenSymbol: String?,
    val tokenDecimal: String?,
    val network: String? = null
)
