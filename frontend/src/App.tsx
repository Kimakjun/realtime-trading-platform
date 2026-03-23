import { useEffect, useRef } from 'react'
import { Routes, Route, Link } from 'react-router-dom'
import { useAtom } from 'jotai'
import { accountIdAtom, executionsAtom, ordersAtom, orderBookAtom } from './store/atoms'
import type { Execution } from './store/atoms'

import HomePage from './pages/HomePage'
import TradePage from './pages/TradePage'

export default function App() {
  const [accountId, setAccountId] = useAtom(accountIdAtom)
  const [, setOrders] = useAtom(ordersAtom)
  const [, setExecutions] = useAtom(executionsAtom)
  const [, setOrderBook] = useAtom(orderBookAtom)

  const wsRef = useRef<WebSocket | null>(null)

  useEffect(() => {
    // 1. Initialize anonymous session
    let savedId = sessionStorage.getItem('accountId')
    if (!savedId) {
      savedId = 'user-' + Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2, 15)
      sessionStorage.setItem('accountId', savedId)
    }
    setAccountId(savedId)

    // 2. Fetch initial global history
    Promise.all([
      fetch(`http://localhost:8082/api/v1/orders/history?accountId=${savedId}`).then(res => res.json()),
      fetch(`http://localhost:8082/api/v1/orders/executions?accountId=${savedId}`).then(res => res.json())
    ]).then(([ordersData, executionsData]) => {
      setOrders(ordersData)
      setExecutions(executionsData)
    })

    // 3. Connect WebSocket for live updates
    const ws = new WebSocket(`ws://localhost:8082/ws/orders?accountId=${savedId}`)
    ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data)
        if (msg.type === 'EXECUTION') {
          const exec = msg.data
          const newExecution: Execution = {
            id: Date.now(),
            executionId: exec.execution_id,
            orderId: exec.order_id,
            accountId: exec.account_id,
            symbol: exec.symbol,
            side: exec.side,
            quantity: exec.quantity,
            price: exec.price,
            executedAt: exec.timestamp
          }
          setExecutions(prev => [newExecution, ...prev])

          // In-memory UI update prevents race condition with PostgreSQL flush
          setOrders(prevOrders => prevOrders.map(o => {
            if (o.orderId === exec.order_id) {
              const newFilled = o.filledQuantity + exec.quantity
              const newStatus = newFilled >= o.quantity ? 'FILLED' : 'PARTIALLY_FILLED'
              return { ...o, filledQuantity: newFilled, status: newStatus }
            }
            return o
          }))

          // Fallback fetch after 300ms to catch DB transactions that settle late,
          // or to fetch the order if it executed before the UI even received the 'NEW' POST response.
          setTimeout(() => {
            fetch(`http://localhost:8082/api/v1/orders/history?accountId=${savedId}`)
              .then(res => res.json())
              .then(data => setOrders(data))
          }, 300)
        } else if (msg.type === 'ORDER_BOOK_UPDATE') {
          // data is OrderBookResponse { symbol, entries }
          const ob = msg.data
          setOrderBook(prev => ({
            ...prev,
            [ob.symbol]: ob.entries
          }))
        }
      } catch (e) {
        console.error('Failed to parse websocket message', e)
      }
    }
    wsRef.current = ws

    return () => {
      ws.close()
    }
  }, [setAccountId, setExecutions, setOrders, setOrderBook])

  return (
    <div className="min-h-screen">
      <header className="bg-white px-6 py-4 flex justify-between items-center shadow-sm sticky top-0 z-50">
        <Link to="/" style={{ textDecoration: 'none' }}>
          <h1 className="text-2xl font-extrabold text-[#3182f6]">HJ증권</h1>
        </Link>
        <div className="text-sm bg-[#f2f4f6] px-3 py-1.5 rounded-full text-[#8b95a1] font-medium flex items-center gap-2">
          나의 세션 아이디 <span className="font-mono text-[#191f28] text-xs bg-white px-2 py-0.5 rounded-full shadow-sm">{accountId.substring(0, 10)}</span>
        </div>
      </header>

      <main className="max-w-5xl mx-auto p-4 sm:p-6 md:p-8">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/trade/:symbol" element={<TradePage />} />
        </Routes>
      </main>
    </div>
  )
}
