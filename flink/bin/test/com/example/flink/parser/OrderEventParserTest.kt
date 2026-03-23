package com.example.flink.parser

import com.example.flink.model.NormalizedOrderEvent
import com.example.flink.model.OrderEventType
import org.apache.flink.util.Collector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.apache.flink.configuration.Configuration

class OrderEventParserTest {

    private val parser = OrderEventParser().also {
        it.open(Configuration())
    }

    /**
     * JSON 문자열을 파싱하고 결과를 리스트로 반환하는 테스트 헬퍼.
     *
     * Flink의 flatMap()은 결과를 Collector에 넘기는 구조라서
     * 직접 return 값을 받을 수 없다. 그래서 가짜 Collector를 만들어서
     * 파싱 결과를 리스트에 담아 테스트에서 검증할 수 있게 한다.
     *
     * 흐름: JSON → parser.flatMap() → Collector.collect() → results 리스트에 추가
     *
     * - 파싱 성공 → results에 1개 추가 → 리스트 크기 1
     * - 유효성 실패/파싱 에러 → collect() 호출 안 됨 → 빈 리스트 반환
     */
    private fun parse(json: String): List<NormalizedOrderEvent> {
        val results = mutableListOf<NormalizedOrderEvent>()
        // Flink Collector 인터페이스의 익명 구현체 — collect()가 호출되면 results에 추가
        val collector = object : Collector<NormalizedOrderEvent> {
            override fun collect(record: NormalizedOrderEvent) { results.add(record) }
            override fun close() {}
        }
        // OrderEventParser.flatMap() 호출 — 내부에서 유효하면 collector.collect(), 아니면 아무것도 안 함
        parser.flatMap(json, collector)
        return results
    }

    @Test
    fun `정상 이벤트 파싱`() {
        val json = """
            {
                "event_id": "evt-001",
                "order_id": "ORD-001",
                "account_id": "ACC_00001",
                "symbol": "BTC/USD",
                "event_type": "CREATED",
                "side": "BUY",
                "quantity": 100,
                "price": 50000,
                "event_time": "2026-03-22 10:00:00"
            }
        """.trimIndent()

        val results = parse(json)

        assertEquals(1, results.size)
        val event = results[0]
        assertEquals("ORD-001", event.orderId)
        assertEquals("ACC_00001", event.accountId)
        assertEquals("BTC/USD", event.symbol)
        assertEquals(OrderEventType.CREATED, event.eventType)
        assertEquals(100, event.quantity)
        assertEquals(50000L, event.price)
    }

    @Test
    fun `orderId 없으면 드랍`() {
        val json = """
            {
                "event_id": "evt-001",
                "symbol": "BTC/USD",
                "event_type": "CREATED",
                "side": "BUY",
                "quantity": 100,
                "price": 50000
            }
        """.trimIndent()

        val results = parse(json)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `eventType 없으면 드랍`() {
        val json = """
            {
                "event_id": "evt-001",
                "order_id": "ORD-001",
                "symbol": "BTC/USD",
                "side": "BUY",
                "quantity": 100,
                "price": 50000
            }
        """.trimIndent()

        val results = parse(json)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `잘못된 eventType이면 드랍`() {
        val json = """
            {
                "order_id": "ORD-001",
                "event_type": "INVALID_TYPE",
                "side": "BUY",
                "quantity": 100,
                "price": 50000
            }
        """.trimIndent()

        val results = parse(json)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `quantity 0 이하면 드랍`() {
        val json = """
            {
                "order_id": "ORD-001",
                "event_type": "CREATED",
                "side": "BUY",
                "quantity": 0,
                "price": 50000
            }
        """.trimIndent()

        val results = parse(json)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `price 음수면 드랍`() {
        val json = """
            {
                "order_id": "ORD-001",
                "event_type": "CREATED",
                "side": "BUY",
                "quantity": 100,
                "price": -1
            }
        """.trimIndent()

        val results = parse(json)
        assertTrue(results.isEmpty())
    }

    @Test
    fun `잘못된 JSON이면 드랍`() {
        val results = parse("not a json")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `eventTime 없으면 현재시간으로 대체`() {
        val json = """
            {
                "order_id": "ORD-001",
                "event_type": "CREATED",
                "side": "BUY",
                "quantity": 100,
                "price": 50000
            }
        """.trimIndent()

        val results = parse(json)
        assertEquals(1, results.size)
        // eventTime이 null이 아닌지만 확인 (현재 시간이므로 정확한 값 비교 불가)
        assertTrue(results[0].eventTime != null)
    }
}
