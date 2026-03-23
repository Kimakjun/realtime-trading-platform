package com.example.demo.repository

import com.example.demo.entity.ExecutionHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ExecutionHistoryRepository : JpaRepository<ExecutionHistory, Long> {
    fun findByAccountIdOrderByExecutedAtDesc(accountId: String): List<ExecutionHistory>
}
