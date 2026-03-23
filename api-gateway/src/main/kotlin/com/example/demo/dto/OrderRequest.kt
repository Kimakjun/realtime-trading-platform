package com.example.demo.dto

data class OrderRequest(
    val accountId: String,
    val symbol: String,
    val side: String,
    val quantity: Int,
    val price: Long
)
