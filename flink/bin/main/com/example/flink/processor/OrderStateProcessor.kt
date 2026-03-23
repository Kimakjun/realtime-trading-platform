package com.example.flink.processor

import com.example.flink.model.NormalizedOrderEvent
import com.example.flink.model.OrderEventType
import com.example.flink.model.OrderState
import com.example.flink.model.OrderStatus
import org.apache.flink.api.common.state.ValueState
import org.apache.flink.api.common.state.ValueStateDescriptor
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.apache.flink.util.Collector
import org.slf4j.LoggerFactory

/**
 * 주문 이벤트를 받아서 주문의 현재 상태(OrderState)를 관리하는 Stateful Processor.
 *
 * Flink의 KeyedProcessFunction을 상속하여 orderId별로 상태를 유지한다.
 * - Key: orderId (String)
 * - Input: NormalizedOrderEvent (파싱/검증 완료된 이벤트)
 * - Output: OrderState (주문의 최신 상태 스냅샷)
 *
 * 상태 전이 흐름:
 *   CREATED → MODIFIED (수량 변경)
 *            → PARTIALLY_FILLED (부분 체결)
 *            → FILLED (완전 체결, 종료)
 *            → CANCELED (취소, 종료)
 *
 * ValueState를 사용해 각 orderId의 최신 상태를 Flink 내부 state backend에 저장하며,
 * 이벤트가 들어올 때마다 상태를 업데이트하고 downstream으로 내보낸다.
 */
class OrderStateProcessor : KeyedProcessFunction<String, NormalizedOrderEvent, OrderState>() {
    private val log = LoggerFactory.getLogger(OrderStateProcessor::class.java)

    /**
     * Flink가 관리하는 Keyed State.
     * orderId별로 하나의 OrderState를 저장한다.
     * 체크포인트 시 자동으로 영속화되어 장애 복구 시에도 상태가 유지된다.
     */
    @Transient private lateinit var orderState: ValueState<OrderState>

    /**
     * 태스크 초기화 시 1회 호출.
     * ValueState를 등록하여 Flink state backend에서 관리되도록 한다.
     */
    override fun open(parameters: Configuration) {
        val descriptor =
                ValueStateDescriptor("orderState", TypeInformation.of(OrderState::class.java))
        orderState = runtimeContext.getState(descriptor)
    }

    /**
     * 이벤트가 들어올 때마다 호출되는 핵심 로직.
     * 현재 상태가 없으면(첫 이벤트) 새로 생성하고,
     * 있으면 이벤트 타입에 따라 상태를 업데이트한다.
     */
    override fun processElement(
            event: NormalizedOrderEvent,
            ctx: Context,
            out: Collector<OrderState>
    ) {
        // 현재 저장된 상태 조회 (없으면 null)
        val currentState = orderState.value()

        val newState =
                if (currentState == null) {
                    // ── 이 orderId를 처음 본 경우 ──
                    handleFirstEvent(event)
                } else {
                    // ── 기존 상태가 있는 경우 — 이벤트 타입에 따라 업데이트 ──
                    handleSubsequentEvent(currentState, event)
                }

        // 상태 저장 (Flink state backend에 영속화)
        orderState.update(newState)
        // 다음 operator(ClickHouse Sink 등)로 전달
        out.collect(newState)
    }

    /**
     * 해당 orderId의 첫 번째 이벤트 처리.
     * 정상적으로는 CREATED 이벤트가 와야 하지만,
     * Kafka offset이 밀리거나 장애 복구 시 중간 이벤트부터 올 수 있으므로
     * 비정상 케이스도 degraded state로 처리한다.
     */
    private fun handleFirstEvent(event: NormalizedOrderEvent): OrderState {
        return when (event.eventType) {
            OrderEventType.CREATED -> {
                // 정상 케이스: 신규 주문 생성
                OrderState(
                        orderId = event.orderId,
                        symbol = event.symbol,
                        status = OrderStatus.CREATED,
                        originalQty = event.quantity,
                        currentQty = event.quantity,
                        filledQty = 0,
                        remainingQty = event.quantity,
                        lastEventTime = event.eventTime
                )
            }
            else -> {
                // 비정상 케이스: CREATED 없이 다른 이벤트가 먼저 도착
                // 드랍하지 않고 이벤트 기반으로 추정 상태를 생성 (데이터 유실 방지)
                log.warn(
                        "Received non-CREATED event for unknown order {}: {}",
                        event.orderId,
                        event.eventType
                )
                val status =
                        when (event.eventType) {
                            OrderEventType.PARTIALLY_FILLED -> OrderStatus.PARTIALLY_FILLED
                            OrderEventType.FILLED -> OrderStatus.FILLED
                            OrderEventType.CANCELED -> OrderStatus.CANCELED
                            else -> OrderStatus.CREATED
                        }
                val currentQty = event.quantity
                val filledQty =
                        if (status == OrderStatus.FILLED) currentQty
                        else if (status == OrderStatus.PARTIALLY_FILLED) currentQty
                        else 0

                OrderState(
                        orderId = event.orderId,
                        symbol = event.symbol,
                        status = status,
                        originalQty = currentQty,
                        currentQty = currentQty,
                        filledQty = filledQty,
                        remainingQty = Math.max(0, currentQty - filledQty),
                        lastEventTime = event.eventTime
                )
            }
        }
    }

    /**
     * 기존 상태가 있는 주문에 대한 이벤트 처리.
     * 이벤트 타입별로 수량/상태를 업데이트한다.
     *
     * - CREATED: 중복 이벤트로 간주, 무시
     * - MODIFIED: 주문 수량 변경, 잔여 수량 재계산
     * - PARTIALLY_FILLED: 체결 수량 누적, 잔여 수량 차감
     * - FILLED: 전량 체결 처리 (잔여 수량 = 0)
     * - CANCELED: 취소 처리 (현재 잔여 수량 유지)
     */
    private fun handleSubsequentEvent(
            currentState: OrderState,
            event: NormalizedOrderEvent
    ): OrderState {
        val updatedState = currentState.copy(lastEventTime = event.eventTime)

        when (event.eventType) {
            OrderEventType.CREATED -> {
                // 중복 CREATED 이벤트 — Kafka 재전송 등으로 발생 가능
                log.info("Duplicate CREATED event for {}", event.orderId)
            }
            OrderEventType.MODIFIED -> {
                // 주문 수량 변경 (예: 100 → 150)
                updatedState.currentQty = event.quantity
                updatedState.remainingQty =
                        Math.max(0, updatedState.currentQty - updatedState.filledQty)
                // status는 기존 유지
            }
            OrderEventType.PARTIALLY_FILLED -> {
                // 부분 체결: 이번에 체결된 수량을 누적
                updatedState.filledQty += event.quantity
                updatedState.remainingQty =
                        Math.max(0, updatedState.currentQty - updatedState.filledQty)
                updatedState.status = OrderStatus.PARTIALLY_FILLED
            }
            OrderEventType.FILLED -> {
                // 완전 체결: 전체 수량 체결 완료
                updatedState.filledQty = updatedState.currentQty
                updatedState.remainingQty = 0
                updatedState.status = OrderStatus.FILLED
            }
            OrderEventType.CANCELED -> {
                // 주문 취소: 미체결 수량은 그대로 남김 (부분 체결 후 취소 가능)
                updatedState.remainingQty =
                        Math.max(0, updatedState.currentQty - updatedState.filledQty)
                updatedState.status = OrderStatus.CANCELED
            }
        }
        return updatedState
    }
}
