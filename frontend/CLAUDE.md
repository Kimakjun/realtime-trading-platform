# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this directory.

## Overview

실시간 트레이딩 UI. WebSocket으로 체결/호가 실시간 수신, REST API로 주문 제출.

## Build & Dev

```bash
npm install          # 의존성 설치
npm run dev          # 개발 서버 (port 5173, HMR)
npm run build        # 프로덕션 빌드 (tsc -b && vite build)
npm run lint         # ESLint
npm run preview      # 빌드 결과 미리보기
```

## Tech Stack

- React 19, TypeScript 5.9
- Vite 8 (Oxc compiler via @vitejs/plugin-react)
- Jotai (atomic state management)
- Ant Design 6 (UI components)
- Tailwind CSS 4
- react-router-dom 7
- lucide-react (icons)

## State Management (Jotai Atoms)

`src/store/atoms.ts`:
- `accountIdAtom`: 세션 ID (sessionStorage, 랜덤 생성)
- `ordersAtom`: 주문 목록
- `executionsAtom`: 체결 목록
- `orderBookAtom`: Record<symbol, OrderBookEntry[]>
- `SYMBOLS`: ["BTC", "ETH", "NVDA", "AAPL", "QCOM", "TSLA"]
- `THEME`: Toss 스타일 컬러 팔레트

## Pages

- `HomePage`: 주문/체결 이력 표시
- `TradePage`: 심볼별 트레이딩 (주문 제출 + 호가창)

## Backend Connection

- REST: `http://localhost:8082/api/v1/orders/*`
- WebSocket: `ws://localhost:8082/ws/orders?accountId={sessionId}`
- WebSocket 메시지: `EXECUTION` (체결), `ORDER_BOOK_UPDATE` (호가)
- 300ms fallback refetch: WebSocket 수신 후 DB 트랜잭션 지연 보상
