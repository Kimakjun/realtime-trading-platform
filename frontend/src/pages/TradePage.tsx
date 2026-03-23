import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useAtom } from 'jotai'
import { accountIdAtom, orderBookAtom, ordersAtom, executionsAtom } from '../store/atoms'
import { ArrowLeft } from 'lucide-react'
import { Layout, Row, Col, Card, Typography, Button, InputNumber, Tabs, Table, message, Tag, Space } from 'antd'
import { HomeOutlined } from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'

const { Title, Text } = Typography

export default function TradePage() {
  const { symbol } = useParams()
  const navigate = useNavigate()
  
  const [accountId] = useAtom(accountIdAtom)
  const [globalOrderBook, setGlobalOrderBook] = useAtom(orderBookAtom)
  const [orders, setOrders] = useAtom(ordersAtom)
  const [executions] = useAtom(executionsAtom)

  const [quantity, setQuantity] = useState<number | null>(10)
  const [price, setPrice] = useState<number | null>(15000)

  const [messageApi, contextHolder] = message.useMessage()

  // Fetch initial order book for symbol
  useEffect(() => {
    if (!symbol) return;
    fetch(`http://localhost:8082/api/v1/orders/book?symbol=${symbol}`)
      .then(res => res.json())
      .then(data => {
        setGlobalOrderBook(prev => ({
          ...prev,
          [symbol]: data.entries
        }))
      })
      .catch(console.error)
  }, [symbol, setGlobalOrderBook])

  const symbolOrders = orders.filter(o => o.symbol === symbol)
  const pendingOrders = symbolOrders.filter(o => o.status === 'NEW' || o.status === 'PARTIALLY_FILLED')
  const myExecutions = executions.filter(e => e.symbol === symbol)

  const currentOrderBook = globalOrderBook[symbol || ''] || []
  
  // Split into buys and sells
  const sells = currentOrderBook.filter(e => e.side === 'SELL').sort((a, b) => b.price - a.price)
  const buys = currentOrderBook.filter(e => e.side === 'BUY').sort((a, b) => b.price - a.price)

  const maxVolume = Math.max(
    ...currentOrderBook.map(e => Number(e.remainingQuantity)),
    1 // prevent division by zero
  )

  const submitOrder = async (side: 'BUY' | 'SELL') => {
    if (!price || !quantity) return;
    try {
      const res = await fetch('http://localhost:8082/api/v1/orders', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ accountId, symbol, side, quantity, price })
      })
      if (res.ok) {
        const newOrder = await res.json()
        setOrders(prev => {
          if (prev.some(o => o.orderId === newOrder.orderId)) {
            return prev;
          }
          return [newOrder, ...prev]
        })
        messageApi.success(`[${symbol}] ${side === 'BUY' ? '매수' : '매도'} 주문이 접수되었습니다.`)
      }
    } catch (e) {
      console.error(e)
      messageApi.error("주문 처리에 실패했습니다.")
    }
  }

  const formatTime = (isoString: string) => {
    return new Date(isoString).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  }

  const pendingColumns: ColumnsType<any> = [
    { title: '시간', dataIndex: 'createdAt', key: 'createdAt', render: (text: string) => formatTime(text) },
    { title: '구분', dataIndex: 'side', key: 'side', render: (side: string) => <Tag color={side === 'BUY' ? 'red' : 'blue'}>{side === 'BUY' ? '매수' : '매도'}</Tag> },
    { title: '가격', dataIndex: 'price', key: 'price', render: (price: number) => price.toLocaleString(), align: 'right' },
    { title: '미체결/전체', key: 'qty', render: (_: any, record: any) => `${record.quantity - record.filledQuantity} / ${record.quantity}`, align: 'right' },
    { title: '상태', key: 'status', render: () => <Tag>대기중</Tag>, align: 'center' }
  ]
  
  const historyColumns: ColumnsType<any> = [
    { title: '시간', dataIndex: 'executedAt', key: 'executedAt', render: (text: string) => formatTime(text) },
    { title: '구분', dataIndex: 'side', key: 'side', render: (side: string) => <Tag color={side === 'BUY' ? 'red' : 'blue'}>{side === 'BUY' ? '매수' : '매도'}</Tag> },
    { title: '가격', dataIndex: 'price', key: 'price', render: (price: number) => price.toLocaleString(), align: 'right' },
    { title: '수량', dataIndex: 'quantity', key: 'quantity', render: (qty: number) => qty.toLocaleString(), align: 'right' },
    { title: '상태', key: 'status', render: () => <Tag color="green">체결완료</Tag>, align: 'center' }
  ]

  const tabItems = [
    {
      key: '1',
      label: `미체결 주문 (${pendingOrders.length})`,
      children: <Table dataSource={pendingOrders} columns={pendingColumns} rowKey="orderId" pagination={false} size="small" scroll={{ y: 250 }}/>
    },
    {
      key: '2',
      label: `체결 내역 (${myExecutions.length})`,
      children: <Table dataSource={myExecutions} columns={historyColumns} rowKey="executionId" pagination={false} size="small" scroll={{ y: 250 }}/>
    }
  ]

  return (
    <Layout style={{ backgroundColor: '#ffffff', minHeight: '100vh', padding: '32px 24px', animation: 'fadeIn 0.5s' }}>
      {contextHolder}
      <div style={{ maxWidth: '1200px', margin: '0 auto', width: '100%' }}>
        
        {/* Header */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '24px' }}>
          <Space size="middle" style={{ cursor: 'pointer' }} onClick={() => navigate('/')}>
            <Button shape="circle" icon={<ArrowLeft size={16} />} type="text" />
            <div>
              <Title level={3} style={{ margin: 0 }}>{symbol} 호가 및 주문</Title>
            </div>
          </Space>
          <Button icon={<HomeOutlined />} onClick={() => navigate('/')}>홈으로 가기</Button>
        </div>

        <Row gutter={[24, 24]}>
          {/* Left: Order Book */}
          <Col xs={24} lg={10}>
            <Card title="실시간 호가" bordered={false} style={{ boxShadow: '0 1px 2px -2px rgba(0, 0, 0, 0.16), 0 3px 6px 0 rgba(0, 0, 0, 0.12)' }} bodyStyle={{ padding: 0 }}>
              <div style={{ maxHeight: '600px', overflowY: 'auto', userSelect: 'none', backgroundColor: '#fafafa' }}>
                
                {/* Sells */}
                <div style={{ display: 'flex', flexDirection: 'column', justifyContent: 'flex-end', minHeight: '300px' }}>
                  {sells.length === 0 && <div style={{ textAlign: 'center', padding: '32px 0', color: '#bfbfbf', fontSize: '13px' }}>매도 잔량이 없습니다.</div>}
                  {sells.map((s, idx) => (
                    <div 
                      key={`sell-${idx}`} 
                      onClick={() => setPrice(s.price)}
                      style={{ display: 'flex', height: '40px', alignItems: 'center', cursor: 'pointer', position: 'relative', borderBottom: '1px solid #f0f0f0', backgroundColor: '#fff' }}
                    >
                      <div style={{ position: 'absolute', right: 0, top: 0, bottom: 0, width: `${(s.remainingQuantity / maxVolume) * 100}%`, backgroundColor: '#e6f7ff', zIndex: 0 }} />
                      <div style={{ width: '50%', padding: '0 16px', zIndex: 1, color: '#1890ff', fontWeight: 'bold' }}>{s.price.toLocaleString()}</div>
                      <div style={{ width: '50%', padding: '0 16px', zIndex: 1, textAlign: 'right', color: '#595959' }}>{s.remainingQuantity.toLocaleString()}</div>
                    </div>
                  ))}
                </div>

                <div style={{ height: '3px', backgroundColor: '#d9d9d9', width: '100%' }} />

                {/* Buys */}
                <div style={{ display: 'flex', flexDirection: 'column', minHeight: '300px' }}>
                  {buys.length === 0 && <div style={{ textAlign: 'center', padding: '32px 0', color: '#bfbfbf', fontSize: '13px' }}>매수 잔량이 없습니다.</div>}
                  {buys.map((b, idx) => (
                    <div 
                      key={`buy-${idx}`} 
                      onClick={() => setPrice(b.price)}
                      style={{ display: 'flex', height: '40px', alignItems: 'center', cursor: 'pointer', position: 'relative', borderBottom: '1px solid #f0f0f0', backgroundColor: '#fff' }}
                    >
                      <div style={{ position: 'absolute', right: 0, top: 0, bottom: 0, width: `${(b.remainingQuantity / maxVolume) * 100}%`, backgroundColor: '#fff1f0', zIndex: 0 }} />
                      <div style={{ width: '50%', padding: '0 16px', zIndex: 1, color: '#f5222d', fontWeight: 'bold' }}>{b.price.toLocaleString()}</div>
                      <div style={{ width: '50%', padding: '0 16px', zIndex: 1, textAlign: 'right', color: '#595959' }}>{b.remainingQuantity.toLocaleString()}</div>
                    </div>
                  ))}
                </div>

              </div>
            </Card>
          </Col>

          {/* Right: Order Form & History */}
          <Col xs={24} lg={14}>
            <Row gutter={[0, 24]}>
              <Col span={24}>
                <Card title="주문하기" bordered={false} style={{ boxShadow: '0 1px 2px -2px rgba(0, 0, 0, 0.16), 0 3px 6px 0 rgba(0, 0, 0, 0.12)' }}>
                  <Space direction="vertical" size="large" style={{ width: '100%' }}>
                    <div>
                      <Text strong style={{ display: 'block', marginBottom: '8px' }}>가격 (원)</Text>
                      <InputNumber 
                        size="large" 
                        style={{ width: '100%' }} 
                        value={price} 
                        onChange={setPrice} 
                        formatter={value => `${value}`.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}
                        parser={value => value?.replace(/\$\s?|(,*)/g, '') as unknown as number}
                      />
                    </div>
                    <div>
                      <Text strong style={{ display: 'block', marginBottom: '8px' }}>수량 (주)</Text>
                      <InputNumber 
                        size="large" 
                        style={{ width: '100%' }} 
                        value={quantity} 
                        onChange={setQuantity}
                      />
                    </div>
                    <Row gutter={16} style={{ marginTop: '8px' }}>
                      <Col span={12}>
                        <Button 
                          type="primary" 
                          danger 
                          size="large" 
                          block 
                          onClick={() => submitOrder('BUY')}
                          style={{ height: '48px', fontSize: '16px', fontWeight: 'bold' }}
                        >
                          매수
                        </Button>
                      </Col>
                      <Col span={12}>
                        <Button 
                          type="primary" 
                          size="large" 
                          block 
                          onClick={() => submitOrder('SELL')}
                          style={{ height: '48px', fontSize: '16px', fontWeight: 'bold' }}
                        >
                          매도
                        </Button>
                      </Col>
                    </Row>
                  </Space>
                </Card>
              </Col>
              
              <Col span={24}>
                <Card bordered={false} style={{ boxShadow: '0 1px 2px -2px rgba(0, 0, 0, 0.16), 0 3px 6px 0 rgba(0, 0, 0, 0.12)' }} bodyStyle={{ padding: '0 24px 24px' }}>
                  <Tabs defaultActiveKey="1" items={tabItems} />
                </Card>
              </Col>
            </Row>
          </Col>
          
        </Row>
      </div>
    </Layout>
  )
}
