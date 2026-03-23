import { atom } from 'jotai';

export interface Order {
  id: number;
  orderId: string;
  accountId: string;
  symbol: string;
  side: string;
  quantity: number;
  price: number;
  status: string;
  filledQuantity: number;
  createdAt: string;
  updatedAt: string;
}

export interface Execution {
  id: number;
  executionId: string;
  orderId: string;
  accountId: string;
  symbol: string;
  side: string;
  price: number;
  quantity: number;
  executedAt: string;
}

export interface OrderBookEntry {
  side: 'BUY' | 'SELL';
  price: number;
  remainingQuantity: number;
}

// Global account session (uuid)
export const accountIdAtom = atom<string>("");

// Global arrays for user records
export const ordersAtom = atom<Order[]>([]);
export const executionsAtom = atom<Execution[]>([]);

// Global object for Order Book grouped by Symbol
// Record<SymbolName, OrderBookEntry[]>
export const orderBookAtom = atom<Record<string, OrderBookEntry[]>>({});

// Available symbols for the platform
export const SYMBOLS = ["BTC", "ETH", "NVDA", "AAPL", "QCOM", "TSLA"];

// Theme colors to give a Toss-like impression
export const THEME = {
  blue: "#3182f6",
  red: "#f04452",
  bgLight: "#f2f4f6", // soft gray background
  bgCard: "#ffffff",
  textPrimary: "#191f28",
  textSecondary: "#8b95a1",
}
