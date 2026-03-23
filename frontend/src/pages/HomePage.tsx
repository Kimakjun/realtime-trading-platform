import { useNavigate } from 'react-router-dom'
import { SYMBOLS } from '../store/atoms'
import { ChevronRight } from 'lucide-react'
import { Card, Typography, Row, Col, Space } from 'antd'

const { Title, Text } = Typography

export default function HomePage() {
  const navigate = useNavigate()

  return (
    <div style={{ padding: '32px 24px', maxWidth: '1200px', margin: '0 auto', animation: 'fadeIn 0.5s' }}>
      <Typography style={{ marginBottom: '32px' }}>
        <Title level={2} style={{ margin: 0, color: '#262626' }}>어떤 종목을 거래하시겠습니까?</Title>
        <Text type="secondary" style={{ fontSize: '16px', marginTop: '8px', display: 'block' }}>
          실시간 호가 기반의 빠르고 정확한 엔터프라이즈 매매 시스템
        </Text>
      </Typography>

      <Row gutter={[24, 24]}>
        {SYMBOLS.map(symbol => (
          <Col xs={24} sm={12} lg={8} key={symbol}>
            <Card 
              hoverable 
              onClick={() => navigate(`/trade/${symbol}`)}
              style={{ borderRadius: '12px', border: '1px solid #f0f0f0', boxShadow: '0 2px 8px rgba(0,0,0,0.04)' }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Space size="large">
                  <div style={{ 
                    width: '48px', height: '48px', borderRadius: '50%', 
                    backgroundColor: '#e6f7ff', display: 'flex', 
                    alignItems: 'center', justifyContent: 'center',
                    fontSize: '20px', fontWeight: 'bold', color: '#1890ff'
                  }}>
                    {symbol[0]}
                  </div>
                  <div>
                    <Title level={4} style={{ margin: 0, color: '#262626' }}>{symbol}</Title>
                    <Text type="secondary" style={{ fontSize: '13px' }}>실시간 매매 호가 제공</Text>
                  </div>
                </Space>
                <ChevronRight size={20} color="#bfbfbf" />
              </div>
            </Card>
          </Col>
        ))}
      </Row>
    </div>
  )
}
