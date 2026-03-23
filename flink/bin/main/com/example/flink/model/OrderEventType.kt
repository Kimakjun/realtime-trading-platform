package com.example.flink.model

/**
 * 주문 이벤트의 유형을 정의하는 enum.
 * Kafka에서 수신한 raw JSON의 "type" 필드를 이 enum으로 변환하여 타입 안전하게 처리한다.
 *
 * 주문 생명주기: CREATED → MODIFIED/PARTIALLY_FILLED → FILLED/CANCELED
 */
enum class OrderEventType {
    CREATED,            // 신규 주문 생성
    MODIFIED,           // 주문 수량/가격 변경
    PARTIALLY_FILLED,   // 부분 체결 (일부 수량만 거래 완료)
    FILLED,             // 완전 체결 (전체 수량 거래 완료)
    CANCELED;           // 주문 취소

    companion object {
        /**
         * 문자열을 OrderEventType으로 변환한다.
         * 대소문자를 무시하고 매칭하며, 매칭 실패 시 IllegalArgumentException을 던진다.
         *
         * @param value Kafka 이벤트의 type 문자열 (예: "created", "FILLED")
         * @return 매칭된 OrderEventType
         * @throws IllegalArgumentException 알 수 없는 이벤트 타입인 경우
         *
         * 사용 예: OrderEventType.from("CREATED") → OrderEventType.CREATED
         */
        fun from(value: String): OrderEventType {
            val upper = value.uppercase()
            if (upper == "NEW") return CREATED
            return entries.firstOrNull { it.name == upper }
                        ?: throw IllegalArgumentException("Unknown event type: $value")
        }


    }
}
