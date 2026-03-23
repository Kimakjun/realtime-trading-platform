package com.example.demo.config

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

@Component
class ExecutionWebSocketHandler : TextWebSocketHandler() {
    private val logger = LoggerFactory.getLogger(ExecutionWebSocketHandler::class.java)
    
    // accountId -> Session
    private val sessionsForAccount = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()
    // SessionId -> accountId
    private val sessionToAccount = ConcurrentHashMap<String, String>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        // expecting query param: ?accountId=xxxx
        val accountId = session.uri?.query?.split("=")?.getOrNull(1)
        if (accountId != null) {
            sessionsForAccount.computeIfAbsent(accountId) { mutableSetOf() }.add(session)
            sessionToAccount[session.id] = accountId
            logger.info("WebSocket connected for accountId: {}", accountId)
        } else {
            logger.warn("WebSocket connected without accountId. Closing.")
            session.close()
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val accountId = sessionToAccount.remove(session.id)
        if (accountId != null) {
            sessionsForAccount[accountId]?.remove(session)
            if (sessionsForAccount[accountId]?.isEmpty() == true) {
                sessionsForAccount.remove(accountId)
            }
            logger.info("WebSocket disconnected for accountId: {}", accountId)
        }
    }

    fun pushMessageToAccount(accountId: String, message: String) {
        sessionsForAccount[accountId]?.forEach { session ->
            if (session.isOpen) {
                try {
                    session.sendMessage(TextMessage(message))
                } catch (e: Exception) {
                    logger.error("Failed to send message to WebSocket session", e)
                }
            }
        }
    }

    fun broadcastMessage(message: String) {
        sessionsForAccount.values.flatten().forEach { session ->
            if (session.isOpen) {
                try {
                    session.sendMessage(TextMessage(message))
                } catch (e: Exception) {
                    logger.error("Failed to broadcast message to WebSocket session", e)
                }
            }
        }
    }
}
