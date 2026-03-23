package com.example.demo.controller

import com.example.demo.dto.OrderRequest
import com.example.demo.entity.ExecutionHistory
import com.example.demo.entity.OrderTransaction
import com.example.demo.service.ExecutionService
import com.example.demo.service.OrderService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/orders")
@CrossOrigin(origins = ["*"])
class OrderController(
    private val orderService: OrderService,
    private val executionService: ExecutionService
) {

    @PostMapping
    fun submitOrder(@RequestBody request: OrderRequest): ResponseEntity<OrderTransaction> {
        val order = orderService.submitOrder(request)
        return ResponseEntity.ok(order)
    }

    @GetMapping("/history")
    fun getOrderHistory(@RequestParam accountId: String): ResponseEntity<List<OrderTransaction>> {
        val history = orderService.getOrderHistory(accountId)
        return ResponseEntity.ok(history)
    }

    @GetMapping("/executions")
    fun getExecutionHistory(@RequestParam accountId: String): ResponseEntity<List<ExecutionHistory>> {
        val executions = executionService.getExecutionHistory(accountId)
        return ResponseEntity.ok(executions)
    }

    @GetMapping("/book")
    fun getOrderBook(@RequestParam symbol: String): ResponseEntity<com.example.demo.dto.OrderBookResponse> {
        val book = orderService.getOrderBook(symbol)
        return ResponseEntity.ok(book)
    }
}
