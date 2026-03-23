package com.example.demo.repository

import com.example.demo.entity.OrderTransaction
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface OrderTransactionRepository : JpaRepository<OrderTransaction, Long> {
    fun findByAccountIdOrderByCreatedAtDesc(accountId: String): List<OrderTransaction>
    fun findByOrderId(orderId: String): OrderTransaction?

    @org.springframework.data.jpa.repository.Query("SELECT new com.example.demo.dto.OrderBookEntry(o.side, o.price, SUM(o.quantity - o.filledQuantity)) " +
            "FROM OrderTransaction o " +
            "WHERE o.symbol = :symbol AND o.status IN ('NEW', 'PARTIALLY_FILLED') " +
            "GROUP BY o.side, o.price " +
            "ORDER BY o.side DESC, o.price DESC")
    fun getOrderBook(symbol: String): List<com.example.demo.dto.OrderBookEntry>
}
