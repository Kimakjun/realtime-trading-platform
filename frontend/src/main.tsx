import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'

import { BrowserRouter } from 'react-router-dom'
import { Provider as JotaiProvider } from 'jotai'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <JotaiProvider>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </JotaiProvider>
  </StrictMode>,
)
