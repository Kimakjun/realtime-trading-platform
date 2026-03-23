package com.example.demo.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "execution_histories")
data class ExecutionHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val executionId: String,   // Unique trade fill ID

    @Column(nullable = false)
    val orderId: String,

    @Column(nullable = false)
    val accountId: String,

    @Column(nullable = false)
    val symbol: String,

    @Column(nullable = false)
    val side: String,

    @Column(nullable = false)
    val price: Long,

    @Column(nullable = false)
    val quantity: Int,

    @Column(nullable = false)
    val executedAt: LocalDateTime = LocalDateTime.now()
) {
    protected constructor() : this(
        executionId = "",
        orderId = "",
        accountId = "",
        symbol = "",
        side = "",
        price = 0,
        quantity = 0
    )
}
