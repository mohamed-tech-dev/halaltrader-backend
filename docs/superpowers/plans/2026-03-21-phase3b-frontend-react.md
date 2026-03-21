# Phase 3B — Frontend React Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a dark-mode React dashboard at `C:\Users\alkao\OneDrive\Bureau\perso\halaltrader-frontend` that visualises portfolio, trades, AI reasoning and performance data from the HalalTrader Spring Boot backend.

**Architecture:** New Vite + React 18 + TypeScript repo, separate from the backend. Vite proxies `/api` to `http://localhost:8080`. TanStack Query v5 handles all server state with 30s auto-refetch on live pages. Dark Pro is the default theme (toggle to Clean Light in Settings). React Router v6 with a sidebar layout wrapping 7 feature pages.

**Tech Stack:** React 18, TypeScript, Vite 5, TanStack Query v5, React Router v6, Recharts 2, Tailwind CSS v3, Lucide React

---

## File Structure

```
halaltrader-frontend/
├── src/
│   ├── types/api.ts              ← TypeScript interfaces matching backend DTOs
│   ├── api/
│   │   ├── client.ts             ← base fetch helper
│   │   ├── portfolio.ts          ← usePortfolio(), usePositions()
│   │   ├── trades.ts             ← useTrades(page, size), useTradeDetail(id)
│   │   ├── assets.ts             ← useAssets()
│   │   └── performance.ts        ← usePerformance()
│   ├── theme/ThemeContext.tsx     ← ThemeProvider + useTheme hook
│   ├── components/
│   │   ├── AppLayout.tsx         ← sidebar + <Outlet /> wrapper
│   │   ├── Card.tsx
│   │   ├── Badge.tsx             ← BUY/SELL/HOLD + APPROVED/REJECTED/PENDING
│   │   ├── DataTable.tsx         ← generic table with optional pagination
│   │   ├── PnlChart.tsx          ← Recharts LineChart cumulative P&L
│   │   ├── HalalDonut.tsx        ← Recharts PieChart halal approval rate
│   │   └── ThemeToggle.tsx
│   ├── features/
│   │   ├── overview/index.tsx
│   │   ├── positions/index.tsx
│   │   ├── trades/index.tsx
│   │   ├── trades/TradeDetailModal.tsx
│   │   ├── ai-reasoning/index.tsx
│   │   ├── halal-screening/index.tsx
│   │   ├── performance/index.tsx
│   │   └── settings/index.tsx
│   ├── router/index.tsx
│   └── main.tsx
├── index.html                    ← Vite default, keep as-is
├── src/index.css                 ← Tailwind directives + body base styles
├── tailwind.config.ts
├── vite.config.ts
└── package.json
```

---

## Task 1: Project scaffold

**Files:**
- Create: `halaltrader-frontend/` (entire directory via npm create vite)
- Modify: `vite.config.ts`
- Modify: `tailwind.config.ts`
- Modify: `src/index.css`

- [ ] **Step 1: Create Vite project**

  ```bash
  cd "/c/Users/alkao/OneDrive/Bureau/perso"
  npm create vite@latest halaltrader-frontend -- --template react-ts
  cd halaltrader-frontend
  ```

- [ ] **Step 2: Install dependencies**

  ```bash
  npm install
  npm install @tanstack/react-query@^5.0.0 react-router-dom@^6.0.0 recharts@^2.0.0 lucide-react
  npm install -D tailwindcss@^3.0.0 postcss autoprefixer
  npx tailwindcss init -p
  ```

- [ ] **Step 3: Configure Tailwind**

  Replace `tailwind.config.js` (rename to `.ts` if needed, or keep `.js`) with:

  ```js
  /** @type {import('tailwindcss').Config} */
  export default {
    content: ['./index.html', './src/**/*.{ts,tsx}'],
    darkMode: 'class',
    theme: {
      extend: {},
    },
    plugins: [],
  }
  ```

- [ ] **Step 4: Configure Vite proxy**

  Replace `vite.config.ts`:

  ```ts
  import { defineConfig } from 'vite'
  import react from '@vitejs/plugin-react'

  export default defineConfig({
    plugins: [react()],
    server: {
      proxy: {
        '/api': 'http://localhost:8080',
      },
    },
  })
  ```

- [ ] **Step 5: Set up index.css**

  Replace `src/index.css` entirely:

  ```css
  @tailwind base;
  @tailwind components;
  @tailwind utilities;

  body {
    @apply bg-slate-50 dark:bg-[#0f1117] text-slate-900 dark:text-[#e6edf3] transition-colors;
  }

  * {
    @apply border-slate-200 dark:border-[#1e2d3d];
  }
  ```

- [ ] **Step 6: Remove boilerplate**

  Delete `src/App.tsx`, `src/App.css`, `src/assets/react.svg`. Keep `public/vite.svg`.

- [ ] **Step 7: Initialise git**

  ```bash
  git init
  echo "node_modules\ndist\n.env" > .gitignore
  git add -A
  git commit -m "feat: scaffold Vite React TypeScript project with Tailwind and TanStack Query"
  ```

- [ ] **Step 8: Verify build compiles**

  ```bash
  npm run build 2>&1 | tail -10
  ```
  Expected: `built in Xs` — no TypeScript errors (there will be "cannot find module" errors at this point because src/main.tsx still references App.tsx — that is fine, we fix it in the next tasks).

  If main.tsx errors, temporarily replace `src/main.tsx` with a minimal stub:

  ```tsx
  import { StrictMode } from 'react'
  import { createRoot } from 'react-dom/client'
  import './index.css'

  createRoot(document.getElementById('root')!).render(
    <StrictMode><div className="dark:bg-[#0f1117] min-h-screen p-8 text-white">HalalTrader</div></StrictMode>
  )
  ```

  Run `npm run build` — must succeed before committing.

---

## Task 2: TypeScript types + API client

**Files:**
- Create: `src/types/api.ts`
- Create: `src/api/client.ts`

- [ ] **Step 1: Create `src/types/api.ts`**

  ```ts
  // Matches Spring Boot backend DTOs exactly

  export interface PortfolioSummary {
    id: string
    name: string
    cashBalance: number
    totalValue: number
    totalPnl: number
    totalPnlPct: number
    positionCount: number
  }

  export interface Position {
    symbol: string
    name: string
    assetType: string
    quantity: number
    avgPrice: number
    value: number
  }

  export interface Trade {
    id: string
    symbol: string
    action: 'BUY' | 'SELL' | 'HOLD'
    quantity: number
    price: number
    totalAmount: number
    simulatedPnl: number | null
    executedAt: string
  }

  export interface TradeDetail extends Trade {
    aiReasoning: string | null
    technicalData: string | null
  }

  export interface Asset {
    id: string
    symbol: string
    name: string
    assetType: string
    halalScreening: 'APPROVED' | 'REJECTED' | 'PENDING'
    halalReason: string | null
    sector: string | null
  }

  export interface DailyPnlEntry {
    date: string
    cumulativePnl: number
  }

  export interface AssetPnlEntry {
    symbol: string
    totalPnl: number
  }

  export interface Performance {
    dailyPnl: DailyPnlEntry[]
    winRate: number
    totalTrades: number
    bestAsset: AssetPnlEntry | null
    worstAsset: AssetPnlEntry | null
    halalApprovalRate: number
    lastCycleAt: string | null
    approvedAssets: number
    totalAssets: number
  }

  /** Spring Data Page<T> serialization */
  export interface PagedResponse<T> {
    content: T[]
    totalElements: number
    totalPages: number
    number: number
    size: number
  }
  ```

- [ ] **Step 2: Create `src/api/client.ts`**

  ```ts
  const BASE = '/api'

  export async function apiFetch<T>(path: string): Promise<T> {
    const res = await fetch(`${BASE}${path}`)
    if (!res.ok) throw new Error(`API ${res.status} — ${path}`)
    return res.json() as Promise<T>
  }
  ```

- [ ] **Step 3: Verify TypeScript compiles**

  ```bash
  npm run build 2>&1 | tail -5
  ```

- [ ] **Step 4: Commit**

  ```bash
  git add src/types/api.ts src/api/client.ts
  git commit -m "feat: add TypeScript API types and base fetch client"
  ```

---

## Task 3: API hooks

**Files:**
- Create: `src/api/portfolio.ts`
- Create: `src/api/trades.ts`
- Create: `src/api/assets.ts`
- Create: `src/api/performance.ts`

- [ ] **Step 1: Create `src/api/portfolio.ts`**

  ```ts
  import { useQuery } from '@tanstack/react-query'
  import { apiFetch } from './client'
  import type { PortfolioSummary, Position } from '../types/api'

  export function usePortfolio() {
    return useQuery({
      queryKey: ['portfolio'],
      queryFn: () => apiFetch<PortfolioSummary>('/portfolio'),
      refetchInterval: 30_000,
    })
  }

  export function usePositions() {
    return useQuery({
      queryKey: ['positions'],
      queryFn: () => apiFetch<Position[]>('/portfolio/positions'),
      refetchInterval: 30_000,
    })
  }
  ```

- [ ] **Step 2: Create `src/api/trades.ts`**

  ```ts
  import { useQuery } from '@tanstack/react-query'
  import { apiFetch } from './client'
  import type { Trade, TradeDetail, PagedResponse } from '../types/api'

  export function useTrades(page: number, size = 20) {
    return useQuery({
      queryKey: ['trades', page, size],
      queryFn: () => apiFetch<PagedResponse<Trade>>(`/trades?page=${page}&size=${size}`),
    })
  }

  export function useTradeDetail(id: string | null) {
    return useQuery({
      queryKey: ['trade', id],
      queryFn: () => apiFetch<TradeDetail>(`/trades/${id}`),
      enabled: id !== null,
    })
  }
  ```

- [ ] **Step 3: Create `src/api/assets.ts`**

  ```ts
  import { useQuery } from '@tanstack/react-query'
  import { apiFetch } from './client'
  import type { Asset } from '../types/api'

  export function useAssets() {
    return useQuery({
      queryKey: ['assets'],
      queryFn: () => apiFetch<Asset[]>('/assets'),
    })
  }
  ```

- [ ] **Step 4: Create `src/api/performance.ts`**

  ```ts
  import { useQuery } from '@tanstack/react-query'
  import { apiFetch } from './client'
  import type { Performance } from '../types/api'

  export function usePerformance() {
    return useQuery({
      queryKey: ['performance'],
      queryFn: () => apiFetch<Performance>('/performance'),
      refetchInterval: 30_000,
    })
  }
  ```

  Note: `Performance` is a reserved word in some contexts but is fine as a type name.

- [ ] **Step 5: Update `src/api/client.ts`** — add named export (the import above uses named `{ apiFetch }`)

  Ensure `client.ts` has: `export async function apiFetch<T>` (not `export default`).

- [ ] **Step 6: Verify build**

  ```bash
  npm run build 2>&1 | tail -5
  ```

- [ ] **Step 7: Commit**

  ```bash
  git add src/api/
  git commit -m "feat: add TanStack Query hooks for all API endpoints"
  ```

---

## Task 4: Theme system

**Files:**
- Create: `src/theme/ThemeContext.tsx`

- [ ] **Step 1: Create `src/theme/ThemeContext.tsx`**

  ```tsx
  import { createContext, useContext, useEffect, useState } from 'react'

  type Theme = 'dark' | 'light'

  interface ThemeContextValue {
    theme: Theme
    toggleTheme: () => void
  }

  const ThemeContext = createContext<ThemeContextValue>({
    theme: 'dark',
    toggleTheme: () => {},
  })

  export function ThemeProvider({ children }: { children: React.ReactNode }) {
    const [theme, setTheme] = useState<Theme>(() => {
      return (localStorage.getItem('ht-theme') as Theme) ?? 'dark'
    })

    useEffect(() => {
      const root = document.documentElement
      if (theme === 'dark') {
        root.classList.add('dark')
      } else {
        root.classList.remove('dark')
      }
      localStorage.setItem('ht-theme', theme)
    }, [theme])

    const toggleTheme = () => setTheme(t => (t === 'dark' ? 'light' : 'dark'))

    return (
      <ThemeContext.Provider value={{ theme, toggleTheme }}>
        {children}
      </ThemeContext.Provider>
    )
  }

  export function useTheme() {
    return useContext(ThemeContext)
  }
  ```

- [ ] **Step 2: Verify build**

  ```bash
  npm run build 2>&1 | tail -5
  ```

- [ ] **Step 3: Commit**

  ```bash
  git add src/theme/ThemeContext.tsx
  git commit -m "feat: add ThemeProvider with dark/light toggle persisted to localStorage"
  ```

---

## Task 5: Layout + Router + main.tsx

**Files:**
- Create: `src/components/AppLayout.tsx`
- Create: `src/router/index.tsx`
- Modify: `src/main.tsx`

- [ ] **Step 1: Create `src/components/AppLayout.tsx`**

  ```tsx
  import { NavLink, Outlet } from 'react-router-dom'
  import {
    LayoutDashboard,
    TrendingUp,
    ArrowLeftRight,
    Brain,
    Star,
    BarChart2,
    Settings,
  } from 'lucide-react'

  const navItems = [
    { to: '/', icon: LayoutDashboard, label: "Vue d'ensemble", end: true },
    { to: '/positions', icon: TrendingUp, label: 'Positions', end: false },
    { to: '/trades', icon: ArrowLeftRight, label: 'Trades', end: false },
    { to: '/ai-reasoning', icon: Brain, label: 'Raisonnements IA', end: false },
    { to: '/halal-screening', icon: Star, label: 'Screening Halal', end: false },
    { to: '/performance', icon: BarChart2, label: 'Performance', end: false },
  ]

  function NavItem({ to, icon: Icon, label, end }: typeof navItems[0]) {
    return (
      <NavLink
        to={to}
        end={end}
        className={({ isActive }) =>
          `flex items-center gap-3 px-4 py-2 text-sm transition-colors ${
            isActive
              ? 'bg-teal-50 dark:bg-[#00d4aa15] text-teal-700 dark:text-[#00d4aa] border-l-2 border-teal-500 dark:border-[#00d4aa]'
              : 'text-slate-500 dark:text-[#3d5a6e] hover:text-slate-800 dark:hover:text-[#e6edf3] border-l-2 border-transparent'
          }`
        }
      >
        <Icon size={16} />
        {label}
      </NavLink>
    )
  }

  export default function AppLayout() {
    return (
      <div className="flex h-screen overflow-hidden bg-slate-50 dark:bg-[#0f1117]">
        {/* Sidebar */}
        <aside className="w-52 flex-shrink-0 flex flex-col bg-white dark:bg-[#0d1117] border-r border-slate-200 dark:border-[#1e2d3d]">
          <div className="px-4 py-4 border-b border-slate-200 dark:border-[#1e2d3d]">
            <div className="text-teal-600 dark:text-[#00d4aa] font-bold font-mono text-sm">
              ⬡ HALALTRADER
            </div>
            <div className="text-slate-400 dark:text-[#3d5a6e] text-xs mt-1">v3.0 — Simulation</div>
          </div>
          <nav className="flex-1 py-2 overflow-y-auto">
            {navItems.map(item => (
              <NavItem key={item.to} {...item} />
            ))}
          </nav>
          <div className="py-2 border-t border-slate-200 dark:border-[#1e2d3d]">
            <NavLink
              to="/settings"
              className={({ isActive }) =>
                `flex items-center gap-3 px-4 py-2 text-sm transition-colors border-l-2 ${
                  isActive
                    ? 'text-teal-700 dark:text-[#00d4aa] border-teal-500 dark:border-[#00d4aa]'
                    : 'text-slate-500 dark:text-[#3d5a6e] hover:text-slate-800 dark:hover:text-[#e6edf3] border-transparent'
                }`
              }
            >
              <Settings size={16} />
              Réglages
            </NavLink>
          </div>
        </aside>

        {/* Main content */}
        <main className="flex-1 overflow-y-auto p-6">
          <Outlet />
        </main>
      </div>
    )
  }
  ```

- [ ] **Step 2: Create `src/router/index.tsx`**

  Create placeholder pages inline for now (they will be replaced in later tasks):

  ```tsx
  import { createBrowserRouter } from 'react-router-dom'
  import AppLayout from '../components/AppLayout'

  // Placeholder — will be replaced by real feature pages in later tasks
  const Placeholder = ({ name }: { name: string }) => (
    <div className="text-slate-400 dark:text-[#3d5a6e] p-4">{name} — coming soon</div>
  )

  export const router = createBrowserRouter([
    {
      element: <AppLayout />,
      children: [
        { path: '/', element: <Placeholder name="Vue d'ensemble" /> },
        { path: '/positions', element: <Placeholder name="Positions" /> },
        { path: '/trades', element: <Placeholder name="Trades" /> },
        { path: '/ai-reasoning', element: <Placeholder name="Raisonnements IA" /> },
        { path: '/halal-screening', element: <Placeholder name="Screening Halal" /> },
        { path: '/performance', element: <Placeholder name="Performance" /> },
        { path: '/settings', element: <Placeholder name="Réglages" /> },
      ],
    },
  ])
  ```

- [ ] **Step 3: Replace `src/main.tsx`**

  ```tsx
  import { StrictMode } from 'react'
  import { createRoot } from 'react-dom/client'
  import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
  import { RouterProvider } from 'react-router-dom'
  import { ThemeProvider } from './theme/ThemeContext'
  import { router } from './router'
  import './index.css'

  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        retry: 1,
      },
    },
  })

  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider>
          <RouterProvider router={router} />
        </ThemeProvider>
      </QueryClientProvider>
    </StrictMode>
  )
  ```

- [ ] **Step 4: Verify build + dev server**

  ```bash
  npm run build 2>&1 | tail -5
  ```
  Expected: BUILD SUCCESS (no TypeScript errors).

  Then start dev server and confirm it loads (open browser to http://localhost:5173 — should show sidebar with placeholder content):
  ```bash
  npm run dev &
  sleep 3
  curl -s http://localhost:5173 | head -5
  kill %1
  ```

- [ ] **Step 5: Commit**

  ```bash
  git add src/components/AppLayout.tsx src/router/index.tsx src/main.tsx
  git commit -m "feat: add AppLayout sidebar, React Router, QueryClient and ThemeProvider wiring"
  ```

---

## Task 6: Shared components

**Files:**
- Create: `src/components/Card.tsx`
- Create: `src/components/Badge.tsx`
- Create: `src/components/DataTable.tsx`
- Create: `src/components/ThemeToggle.tsx`

- [ ] **Step 1: Create `src/components/Card.tsx`**

  ```tsx
  interface CardProps {
    children: React.ReactNode
    className?: string
    title?: string
  }

  export default function Card({ children, className = '', title }: CardProps) {
    return (
      <div className={`bg-white dark:bg-[#161b22] border border-slate-200 dark:border-[#1e2d3d] rounded-lg p-4 ${className}`}>
        {title && (
          <div className="text-xs text-slate-500 dark:text-[#3d5a6e] uppercase tracking-wide mb-3 font-medium">
            {title}
          </div>
        )}
        {children}
      </div>
    )
  }
  ```

- [ ] **Step 2: Create `src/components/Badge.tsx`**

  ```tsx
  type BadgeVariant = 'BUY' | 'SELL' | 'HOLD' | 'APPROVED' | 'REJECTED' | 'PENDING'

  const styles: Record<BadgeVariant, string> = {
    BUY: 'bg-teal-100 dark:bg-[#00d4aa22] text-teal-700 dark:text-[#00d4aa]',
    SELL: 'bg-red-100 dark:bg-[#ff444422] text-red-600 dark:text-[#ff4444]',
    HOLD: 'bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-[#3d5a6e]',
    APPROVED: 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400',
    REJECTED: 'bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-[#ff4444]',
    PENDING: 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-400',
  }

  export default function Badge({ variant }: { variant: BadgeVariant }) {
    return (
      <span className={`inline-block px-2 py-0.5 rounded text-xs font-medium ${styles[variant]}`}>
        {variant}
      </span>
    )
  }
  ```

- [ ] **Step 3: Create `src/components/DataTable.tsx`**

  ```tsx
  interface Column<T> {
    key: string
    header: string
    render: (row: T) => React.ReactNode
  }

  interface DataTableProps<T> {
    columns: Column<T>[]
    data: T[]
    onRowClick?: (row: T) => void
    page?: number
    totalPages?: number
    onPageChange?: (page: number) => void
    emptyMessage?: string
  }

  export default function DataTable<T>({
    columns,
    data,
    onRowClick,
    page,
    totalPages,
    onPageChange,
    emptyMessage = 'Aucune donnée',
  }: DataTableProps<T>) {
    return (
      <div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200 dark:border-[#1e2d3d]">
                {columns.map(col => (
                  <th
                    key={col.key}
                    className="text-left py-2 px-3 text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-[#3d5a6e]"
                  >
                    {col.header}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {data.length === 0 ? (
                <tr>
                  <td
                    colSpan={columns.length}
                    className="py-8 text-center text-slate-400 dark:text-[#3d5a6e]"
                  >
                    {emptyMessage}
                  </td>
                </tr>
              ) : (
                data.map((row, i) => (
                  <tr
                    key={i}
                    onClick={() => onRowClick?.(row)}
                    className={`border-b border-slate-100 dark:border-[#1e2d3d] text-slate-700 dark:text-[#e6edf3] last:border-0 ${
                      onRowClick ? 'cursor-pointer hover:bg-slate-50 dark:hover:bg-white/5' : ''
                    }`}
                  >
                    {columns.map(col => (
                      <td key={col.key} className="py-3 px-3">
                        {col.render(row)}
                      </td>
                    ))}
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* Pagination */}
        {totalPages !== undefined && totalPages > 1 && (
          <div className="flex justify-center items-center gap-3 mt-4">
            <button
              onClick={() => onPageChange?.(page! - 1)}
              disabled={page === 0}
              className="px-3 py-1 text-sm rounded border border-slate-200 dark:border-[#1e2d3d] disabled:opacity-40 text-slate-600 dark:text-[#e6edf3] hover:bg-slate-50 dark:hover:bg-white/5"
            >
              ←
            </button>
            <span className="text-sm text-slate-500 dark:text-[#3d5a6e]">
              {(page ?? 0) + 1} / {totalPages}
            </span>
            <button
              onClick={() => onPageChange?.(page! + 1)}
              disabled={page === totalPages - 1}
              className="px-3 py-1 text-sm rounded border border-slate-200 dark:border-[#1e2d3d] disabled:opacity-40 text-slate-600 dark:text-[#e6edf3] hover:bg-slate-50 dark:hover:bg-white/5"
            >
              →
            </button>
          </div>
        )}
      </div>
    )
  }
  ```

- [ ] **Step 4: Create `src/components/ThemeToggle.tsx`**

  ```tsx
  import { Moon, Sun } from 'lucide-react'
  import { useTheme } from '../theme/ThemeContext'

  export default function ThemeToggle() {
    const { theme, toggleTheme } = useTheme()
    return (
      <button
        onClick={toggleTheme}
        className="flex items-center gap-2 px-4 py-2 rounded-lg border border-slate-200 dark:border-[#1e2d3d] text-sm text-slate-600 dark:text-[#e6edf3] hover:bg-slate-50 dark:hover:bg-white/5 transition-colors"
      >
        {theme === 'dark' ? <Sun size={16} /> : <Moon size={16} />}
        {theme === 'dark' ? 'Passer en mode clair' : 'Passer en mode sombre'}
      </button>
    )
  }
  ```

- [ ] **Step 5: Verify build**

  ```bash
  npm run build 2>&1 | tail -5
  ```

- [ ] **Step 6: Commit**

  ```bash
  git add src/components/Card.tsx src/components/Badge.tsx src/components/DataTable.tsx src/components/ThemeToggle.tsx
  git commit -m "feat: add Card, Badge, DataTable and ThemeToggle shared components"
  ```

---

## Task 7: Overview page

**Files:**
- Create: `src/features/overview/index.tsx`
- Modify: `src/router/index.tsx`

- [ ] **Step 1: Create `src/features/overview/index.tsx`**

  ```tsx
  import { TrendingUp, TrendingDown } from 'lucide-react'
  import { usePortfolio } from '../../api/portfolio'
  import { useTrades } from '../../api/trades'
  import Card from '../../components/Card'
  import Badge from '../../components/Badge'

  function fmt(n: number) {
    return n.toLocaleString('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  }

  export default function Overview() {
    const { data: portfolio, isLoading } = usePortfolio()
    const { data: trades } = useTrades(0, 5)

    const pnlPositive = (portfolio?.totalPnl ?? 0) >= 0

    if (isLoading) {
      return (
        <div className="space-y-6 animate-pulse">
          <div className="h-8 w-48 bg-slate-200 dark:bg-[#161b22] rounded" />
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            {[...Array(4)].map((_, i) => (
              <div key={i} className="h-24 bg-slate-200 dark:bg-[#161b22] rounded-lg" />
            ))}
          </div>
        </div>
      )
    }

    return (
      <div className="space-y-6">
        <h1 className="text-xl font-semibold text-slate-900 dark:text-[#e6edf3]">Vue d'ensemble</h1>

        {/* KPI cards */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <Card>
            <div className="text-xs text-slate-500 dark:text-[#3d5a6e] uppercase tracking-wide mb-1">Portfolio total</div>
            <div className="text-2xl font-bold text-slate-900 dark:text-[#e6edf3]">
              €{fmt(portfolio?.totalValue ?? 0)}
            </div>
            <div className={`text-sm mt-1 flex items-center gap-1 ${pnlPositive ? 'text-teal-600 dark:text-[#00d4aa]' : 'text-red-500'}`}>
              {pnlPositive ? <TrendingUp size={14} /> : <TrendingDown size={14} />}
              {portfolio?.totalPnlPct.toFixed(2)}%
            </div>
          </Card>

          <Card>
            <div className="text-xs text-slate-500 dark:text-[#3d5a6e] uppercase tracking-wide mb-1">Cash disponible</div>
            <div className="text-2xl font-bold text-slate-900 dark:text-[#e6edf3]">
              €{fmt(portfolio?.cashBalance ?? 0)}
            </div>
            <div className="text-sm text-slate-400 dark:text-[#3d5a6e] mt-1">
              {portfolio
                ? ((portfolio.cashBalance / portfolio.totalValue) * 100).toFixed(0)
                : 0}
              % du portfolio
            </div>
          </Card>

          <Card>
            <div className="text-xs text-slate-500 dark:text-[#3d5a6e] uppercase tracking-wide mb-1">P&L total</div>
            <div className={`text-2xl font-bold ${pnlPositive ? 'text-teal-600 dark:text-[#00d4aa]' : 'text-red-500'}`}>
              {pnlPositive ? '+' : ''}€{fmt(portfolio?.totalPnl ?? 0)}
            </div>
            <div className="text-sm text-slate-400 dark:text-[#3d5a6e] mt-1">depuis le début</div>
          </Card>

          <Card>
            <div className="text-xs text-slate-500 dark:text-[#3d5a6e] uppercase tracking-wide mb-1">Positions actives</div>
            <div className="text-2xl font-bold text-slate-900 dark:text-[#e6edf3]">
              {portfolio?.positionCount ?? 0}
            </div>
            <div className="text-sm text-slate-400 dark:text-[#3d5a6e] mt-1">actifs détenus</div>
          </Card>
        </div>

        {/* Last 5 trades */}
        <Card title="Dernières décisions IA">
          {trades?.content.length === 0 ? (
            <div className="text-center py-4 text-slate-400 dark:text-[#3d5a6e]">Aucun trade exécuté</div>
          ) : (
            <div className="divide-y divide-slate-100 dark:divide-[#1e2d3d]">
              {trades?.content.map(trade => (
                <div key={trade.id} className="flex items-center justify-between py-3">
                  <div>
                    <span className="font-medium text-slate-900 dark:text-[#e6edf3] text-sm">{trade.symbol}</span>
                    <span className="text-xs text-slate-400 dark:text-[#3d5a6e] ml-2">
                      {new Date(trade.executedAt).toLocaleString('fr-FR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })}
                    </span>
                  </div>
                  <div className="flex items-center gap-3">
                    {trade.simulatedPnl !== null && (
                      <span className={`text-sm font-medium ${trade.simulatedPnl >= 0 ? 'text-teal-600 dark:text-[#00d4aa]' : 'text-red-500'}`}>
                        {trade.simulatedPnl >= 0 ? '+' : ''}€{fmt(trade.simulatedPnl)}
                      </span>
                    )}
                    <Badge variant={trade.action} />
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>
    )
  }
  ```

- [ ] **Step 2: Update `src/router/index.tsx` — replace Overview placeholder**

  Import `Overview` and use it:

  ```tsx
  import { createBrowserRouter } from 'react-router-dom'
  import AppLayout from '../components/AppLayout'
  import Overview from '../features/overview'

  const Placeholder = ({ name }: { name: string }) => (
    <div className="text-slate-400 dark:text-[#3d5a6e] p-4">{name} — coming soon</div>
  )

  export const router = createBrowserRouter([
    {
      element: <AppLayout />,
      children: [
        { path: '/', element: <Overview /> },
        { path: '/positions', element: <Placeholder name="Positions" /> },
        { path: '/trades', element: <Placeholder name="Trades" /> },
        { path: '/ai-reasoning', element: <Placeholder name="Raisonnements IA" /> },
        { path: '/halal-screening', element: <Placeholder name="Screening Halal" /> },
        { path: '/performance', element: <Placeholder name="Performance" /> },
        { path: '/settings', element: <Placeholder name="Réglages" /> },
      ],
    },
  ])
  ```

- [ ] **Step 3: Verify build**

  ```bash
  npm run build 2>&1 | tail -5
  ```

- [ ] **Step 4: Commit**

  ```bash
  git add src/features/overview/index.tsx src/router/index.tsx
  git commit -m "feat: add Overview page with KPI cards and last 5 trades"
  ```

---

## Task 8: Positions page

**Files:**
- Create: `src/features/positions/index.tsx`
- Modify: `src/router/index.tsx`

- [ ] **Step 1: Create `src/features/positions/index.tsx`**

  ```tsx
  import { usePositions } from '../../api/portfolio'
  import Card from '../../components/Card'
  import DataTable from '../../components/DataTable'
  import type { Position } from '../../types/api'

  export default function Positions() {
    const { data: positions = [], isLoading } = usePositions()

    if (isLoading) {
      return <div className="animate-pulse h-48 bg-slate-200 dark:bg-[#161b22] rounded-lg" />
    }

    const columns = [
      {
        key: 'symbol',
        header: 'Symbole',
        render: (p: Position) => (
          <span className="font-semibold text-slate-900 dark:text-[#e6edf3]">{p.symbol}</span>
        ),
      },
      {
        key: 'name',
        header: 'Nom',
        render: (p: Position) => (
          <span className="text-slate-500 dark:text-[#3d5a6e]">{p.name}</span>
        ),
      },
      {
        key: 'assetType',
        header: 'Type',
        render: (p: Position) => (
          <span className="text-xs bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-[#3d5a6e] px-2 py-0.5 rounded">
            {p.assetType}
          </span>
        ),
      },
      {
        key: 'quantity',
        header: 'Quantité',
        render: (p: Position) => (
          <span className="text-slate-700 dark:text-[#e6edf3]">{p.quantity}</span>
        ),
      },
      {
        key: 'avgPrice',
        header: 'Prix moyen',
        render: (p: Position) => (
          <span className="text-slate-700 dark:text-[#e6edf3]">€{p.avgPrice.toFixed(2)}</span>
        ),
      },
      {
        key: 'value',
        header: 'Valeur',
        render: (p: Position) => (
          <span className="font-medium text-slate-900 dark:text-[#e6edf3]">€{p.value.toFixed(2)}</span>
        ),
      },
    ]

    return (
      <div className="space-y-6">
        <h1 className="text-xl font-semibold text-slate-900 dark:text-[#e6edf3]">Positions actives</h1>
        <Card>
          <DataTable
            columns={columns}
            data={positions}
            emptyMessage="Aucune position ouverte"
          />
        </Card>
      </div>
    )
  }
  ```

- [ ] **Step 2: Update router — replace Positions placeholder with real component**

  Add import: `import Positions from '../features/positions'`
  Replace: `{ path: '/positions', element: <Placeholder name="Positions" /> }` → `{ path: '/positions', element: <Positions /> }`

- [ ] **Step 3: Verify build + commit**

  ```bash
  npm run build 2>&1 | tail -5
  git add src/features/positions/index.tsx src/router/index.tsx
  git commit -m "feat: add Positions page with active holdings table"
  ```

---

## Task 9: Trades page + detail modal

**Files:**
- Create: `src/features/trades/index.tsx`
- Create: `src/features/trades/TradeDetailModal.tsx`
- Modify: `src/router/index.tsx`

- [ ] **Step 1: Create `src/features/trades/TradeDetailModal.tsx`**

  ```tsx
  import { X } from 'lucide-react'
  import { useTradeDetail } from '../../api/trades'
  import Badge from '../../components/Badge'

  function fmt(n: number) {
    return n.toLocaleString('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  }

  interface Props {
    tradeId: string
    onClose: () => void
  }

  export default function TradeDetailModal({ tradeId, onClose }: Props) {
    const { data: trade, isLoading } = useTradeDetail(tradeId)

    let parsedReasoning: unknown = null
    if (trade?.aiReasoning) {
      try {
        parsedReasoning = JSON.parse(trade.aiReasoning)
      } catch {
        parsedReasoning = null
      }
    }

    return (
      <div
        className="fixed inset-0 bg-black/60 flex items-center justify-center z-50 p-4"
        onClick={onClose}
      >
        <div
          className="bg-white dark:bg-[#161b22] border border-slate-200 dark:border-[#1e2d3d] rounded-xl w-full max-w-2xl max-h-[85vh] overflow-y-auto"
          onClick={e => e.stopPropagation()}
        >
          <div className="flex justify-between items-center p-4 border-b border-slate-200 dark:border-[#1e2d3d]">
            <h2 className="font-semibold text-slate-900 dark:text-[#e6edf3]">Détail du trade</h2>
            <button
              onClick={onClose}
              className="text-slate-400 hover:text-slate-700 dark:hover:text-[#e6edf3]"
            >
              <X size={20} />
            </button>
          </div>

          {isLoading ? (
            <div className="p-8 text-center text-slate-400 dark:text-[#3d5a6e]">Chargement...</div>
          ) : trade ? (
            <div className="p-4 space-y-5">
              {/* Trade info grid */}
              <div className="grid grid-cols-2 gap-4 text-sm">
                {[
                  { label: 'Symbole', value: <span className="font-medium dark:text-[#e6edf3]">{trade.symbol}</span> },
                  { label: 'Action', value: <Badge variant={trade.action} /> },
                  { label: 'Quantité', value: trade.quantity },
                  { label: 'Prix', value: `€${fmt(trade.price)}` },
                  { label: 'Montant total', value: `€${fmt(trade.totalAmount)}` },
                  {
                    label: 'P&L simulé',
                    value: trade.simulatedPnl !== null ? (
                      <span className={trade.simulatedPnl >= 0 ? 'text-teal-600 dark:text-[#00d4aa]' : 'text-red-500'}>
                        {trade.simulatedPnl >= 0 ? '+' : ''}€{fmt(trade.simulatedPnl)}
                      </span>
                    ) : '—',
                  },
                  {
                    label: 'Date',
                    value: new Date(trade.executedAt).toLocaleString('fr-FR'),
                  },
                ].map(({ label, value }) => (
                  <div key={label}>
                    <div className="text-xs text-slate-500 dark:text-[#3d5a6e] mb-1">{label}</div>
                    <div className="text-slate-800 dark:text-[#e6edf3]">{value}</div>
                  </div>
                ))}
              </div>

              {/* AI Reasoning */}
              {trade.aiReasoning && (
                <div>
                  <div className="text-xs text-slate-500 dark:text-[#3d5a6e] uppercase tracking-wide mb-2">
                    Raisonnement IA
                  </div>
                  <pre className="text-xs bg-slate-50 dark:bg-[#0f1117] text-slate-700 dark:text-[#e6edf3] p-3 rounded-lg overflow-auto max-h-60 border border-slate-200 dark:border-[#1e2d3d] whitespace-pre-wrap">
                    {parsedReasoning
                      ? JSON.stringify(parsedReasoning, null, 2)
                      : trade.aiReasoning}
                  </pre>
                </div>
              )}

              {/* Technical data */}
              {trade.technicalData && (
                <div>
                  <div className="text-xs text-slate-500 dark:text-[#3d5a6e] uppercase tracking-wide mb-2">
                    Données techniques
                  </div>
                  <pre className="text-xs bg-slate-50 dark:bg-[#0f1117] text-slate-700 dark:text-[#e6edf3] p-3 rounded-lg overflow-auto max-h-40 border border-slate-200 dark:border-[#1e2d3d] whitespace-pre-wrap">
                    {trade.technicalData}
                  </pre>
                </div>
              )}
            </div>
          ) : null}
        </div>
      </div>
    )
  }
  ```

- [ ] **Step 2: Create `src/features/trades/index.tsx`**

  ```tsx
  import { useState } from 'react'
  import { useTrades } from '../../api/trades'
  import Card from '../../components/Card'
  import DataTable from '../../components/DataTable'
  import Badge from '../../components/Badge'
  import TradeDetailModal from './TradeDetailModal'
  import type { Trade } from '../../types/api'

  function fmt(n: number) {
    return n.toLocaleString('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
  }

  export default function Trades() {
    const [page, setPage] = useState(0)
    const [selectedId, setSelectedId] = useState<string | null>(null)
    const { data, isLoading } = useTrades(page, 20)

    if (isLoading) {
      return <div className="animate-pulse h-64 bg-slate-200 dark:bg-[#161b22] rounded-lg" />
    }

    const columns = [
      {
        key: 'date',
        header: 'Date',
        render: (t: Trade) => (
          <span className="text-slate-500 dark:text-[#3d5a6e] text-xs">
            {new Date(t.executedAt).toLocaleString('fr-FR', { day: '2-digit', month: '2-digit', year: '2-digit', hour: '2-digit', minute: '2-digit' })}
          </span>
        ),
      },
      {
        key: 'symbol',
        header: 'Symbole',
        render: (t: Trade) => (
          <span className="font-semibold text-slate-900 dark:text-[#e6edf3]">{t.symbol}</span>
        ),
      },
      {
        key: 'action',
        header: 'Action',
        render: (t: Trade) => <Badge variant={t.action} />,
      },
      {
        key: 'quantity',
        header: 'Quantité',
        render: (t: Trade) => t.quantity,
      },
      {
        key: 'price',
        header: 'Prix',
        render: (t: Trade) => `€${fmt(t.price)}`,
      },
      {
        key: 'total',
        header: 'Montant',
        render: (t: Trade) => `€${fmt(t.totalAmount)}`,
      },
      {
        key: 'pnl',
        header: 'P&L',
        render: (t: Trade) =>
          t.simulatedPnl !== null ? (
            <span className={`font-medium ${t.simulatedPnl >= 0 ? 'text-teal-600 dark:text-[#00d4aa]' : 'text-red-500'}`}>
              {t.simulatedPnl >= 0 ? '+' : ''}€{fmt(t.simulatedPnl)}
            </span>
          ) : (
            <span className="text-slate-400">—</span>
          ),
      },
    ]

    return (
      <div className="space-y-6">
        <h1 className="text-xl font-semibold text-slate-900 dark:text-[#e6edf3]">Historique des trades</h1>
        <Card>
          <DataTable
            columns={columns}
            data={data?.content ?? []}
            onRowClick={t => setSelectedId(t.id)}
            page={page}
            totalPages={data?.totalPages}
            onPageChange={setPage}
            emptyMessage="Aucun trade exécuté"
          />
        </Card>

        {selectedId && (
          <TradeDetailModal tradeId={selectedId} onClose={() => setSelectedId(null)} />
        )}
      </div>
    )
  }
  ```

- [ ] **Step 3: Update router — replace Trades placeholder**

  Add `import Trades from '../features/trades'` and replace the placeholder.

- [ ] **Step 4: Verify build + commit**

  ```bash
  npm run build 2>&1 | tail -5
  git add src/features/trades/ src/router/index.tsx
  git commit -m "feat: add Trades page with paginated table and detail modal"
  ```

---

## Task 10: AI Reasoning page

**Files:**
- Create: `src/features/ai-reasoning/index.tsx`
- Modify: `src/router/index.tsx`

- [ ] **Step 1: Create `src/features/ai-reasoning/index.tsx`**

  This page shows all trades with expandable AI reasoning panels. Clicking a row fetches the detail lazily.

  ```tsx
  import { useState } from 'react'
  import { ChevronDown, ChevronRight } from 'lucide-react'
  import { useTrades, useTradeDetail } from '../../api/trades'
  import Badge from '../../components/Badge'
  import Card from '../../components/Card'
  import type { Trade } from '../../types/api'

  function TradeReasoningRow({ trade }: { trade: Trade }) {
    const [expanded, setExpanded] = useState(false)
    const { data: detail, isLoading } = useTradeDetail(expanded ? trade.id : null)

    let parsedReasoning: unknown = null
    if (detail?.aiReasoning) {
      try { parsedReasoning = JSON.parse(detail.aiReasoning) } catch { /* raw string */ }
    }

    return (
      <div className="border-b border-slate-100 dark:border-[#1e2d3d] last:border-0">
        <button
          onClick={() => setExpanded(e => !e)}
          className="w-full flex items-center justify-between py-3 px-1 text-left hover:bg-slate-50 dark:hover:bg-white/5 transition-colors"
        >
          <div className="flex items-center gap-3">
            {expanded ? <ChevronDown size={16} className="text-slate-400" /> : <ChevronRight size={16} className="text-slate-400" />}
            <span className="font-semibold text-slate-900 dark:text-[#e6edf3] text-sm w-14">{trade.symbol}</span>
            <Badge variant={trade.action} />
            <span className="text-xs text-slate-400 dark:text-[#3d5a6e]">
              {new Date(trade.executedAt).toLocaleString('fr-FR', { day: '2-digit', month: '2-digit', year: '2-digit', hour: '2-digit', minute: '2-digit' })}
            </span>
          </div>
          {trade.simulatedPnl !== null && (
            <span className={`text-sm font-medium ${trade.simulatedPnl >= 0 ? 'text-teal-600 dark:text-[#00d4aa]' : 'text-red-500'}`}>
              {trade.simulatedPnl >= 0 ? '+' : ''}€{trade.simulatedPnl.toFixed(2)}
            </span>
          )}
        </button>

        {expanded && (
          <div className="px-7 pb-4">
            {isLoading ? (
              <div className="text-xs text-slate-400 dark:text-[#3d5a6e] py-2">Chargement du raisonnement...</div>
            ) : detail?.aiReasoning ? (
              <pre className="text-xs bg-slate-50 dark:bg-[#0f1117] text-slate-700 dark:text-[#e6edf3] p-3 rounded-lg overflow-auto max-h-72 border border-slate-200 dark:border-[#1e2d3d] whitespace-pre-wrap">
                {parsedReasoning ? JSON.stringify(parsedReasoning, null, 2) : detail.aiReasoning}
              </pre>
            ) : (
              <div className="text-xs text-slate-400 dark:text-[#3d5a6e] py-2">Aucun raisonnement disponible</div>
            )}
          </div>
        )}
      </div>
    )
  }

  export default function AiReasoning() {
    const [page, setPage] = useState(0)
    const { data, isLoading } = useTrades(page, 20)

    if (isLoading) {
      return <div className="animate-pulse h-64 bg-slate-200 dark:bg-[#161b22] rounded-lg" />
    }

    return (
      <div className="space-y-6">
        <h1 className="text-xl font-semibold text-slate-900 dark:text-[#e6edf3]">Raisonnements IA</h1>
        <p className="text-sm text-slate-500 dark:text-[#3d5a6e]">
          Cliquez sur un trade pour voir les décisions des agents.
        </p>
        <Card>
          {data?.content.length === 0 ? (
            <div className="text-center py-8 text-slate-400 dark:text-[#3d5a6e]">Aucun trade exécuté</div>
          ) : (
            data?.content.map(trade => (
              <TradeReasoningRow key={trade.id} trade={trade} />
            ))
          )}
        </Card>

        {/* Simple pagination */}
        {(data?.totalPages ?? 0) > 1 && (
          <div className="flex justify-center items-center gap-3">
            <button onClick={() => setPage(p => p - 1)} disabled={page === 0}
              className="px-3 py-1 text-sm rounded border border-slate-200 dark:border-[#1e2d3d] disabled:opacity-40 text-slate-600 dark:text-[#e6edf3]">←</button>
            <span className="text-sm text-slate-500 dark:text-[#3d5a6e]">{page + 1} / {data?.totalPages}</span>
            <button onClick={() => setPage(p => p + 1)} disabled={page === (data?.totalPages ?? 1) - 1}
              className="px-3 py-1 text-sm rounded border border-slate-200 dark:border-[#1e2d3d] disabled:opacity-40 text-slate-600 dark:text-[#e6edf3]">→</button>
          </div>
        )}
      </div>
    )
  }
  ```

- [ ] **Step 2: Update router — replace AI Reasoning placeholder**

  Add `import AiReasoning from '../features/ai-reasoning'` and replace the placeholder.

- [ ] **Step 3: Verify build + commit**

  ```bash
  npm run build 2>&1 | tail -5
  git add src/features/ai-reasoning/index.tsx src/router/index.tsx
  git commit -m "feat: add AI Reasoning page with expandable per-trade reasoning panels"
  ```

---

## Task 11: Halal Screening page

**Files:**
- Create: `src/features/halal-screening/index.tsx`
- Modify: `src/router/index.tsx`

- [ ] **Step 1: Create `src/features/halal-screening/index.tsx`**

  ```tsx
  import { useAssets } from '../../api/assets'
  import Card from '../../components/Card'
  import Badge from '../../components/Badge'
  import DataTable from '../../components/DataTable'
  import type { Asset } from '../../types/api'

  export default function HalalScreening() {
    const { data: assets = [], isLoading } = useAssets()

    if (isLoading) {
      return <div className="animate-pulse h-64 bg-slate-200 dark:bg-[#161b22] rounded-lg" />
    }

    const approved = assets.filter(a => a.halalScreening === 'APPROVED').length
    const total = assets.length

    const columns = [
      {
        key: 'symbol',
        header: 'Symbole',
        render: (a: Asset) => (
          <span className="font-semibold text-slate-900 dark:text-[#e6edf3]">{a.symbol}</span>
        ),
      },
      {
        key: 'name',
        header: 'Nom',
        render: (a: Asset) => (
          <span className="text-slate-500 dark:text-[#3d5a6e]">{a.name}</span>
        ),
      },
      {
        key: 'sector',
        header: 'Secteur',
        render: (a: Asset) => (
          <span className="text-slate-500 dark:text-[#3d5a6e]">{a.sector ?? '—'}</span>
        ),
      },
      {
        key: 'status',
        header: 'Statut Halal',
        render: (a: Asset) => <Badge variant={a.halalScreening} />,
      },
      {
        key: 'reason',
        header: 'Raison',
        render: (a: Asset) => (
          <span className="text-sm text-slate-500 dark:text-[#3d5a6e]">{a.halalReason ?? '—'}</span>
        ),
      },
    ]

    return (
      <div className="space-y-6">
        <h1 className="text-xl font-semibold text-slate-900 dark:text-[#e6edf3]">Screening Halal</h1>

        {/* Summary */}
        <div className="flex items-center gap-4">
          <div className="text-2xl font-bold text-teal-600 dark:text-[#00d4aa]">{approved}</div>
          <div className="text-slate-500 dark:text-[#3d5a6e] text-sm">actifs approuvés sur {total}</div>
          <div className="flex-1 h-2 bg-slate-200 dark:bg-[#1e2d3d] rounded-full overflow-hidden">
            <div
              className="h-full bg-teal-500 dark:bg-[#00d4aa] rounded-full transition-all"
              style={{ width: total > 0 ? `${(approved / total) * 100}%` : '0%' }}
            />
          </div>
          <div className="text-sm font-medium text-slate-700 dark:text-[#e6edf3]">
            {total > 0 ? Math.round((approved / total) * 100) : 0}%
          </div>
        </div>

        <Card>
          <DataTable
            columns={columns}
            data={assets}
            emptyMessage="Aucun actif enregistré"
          />
        </Card>
      </div>
    )
  }
  ```

- [ ] **Step 2: Update router — replace Halal Screening placeholder**

  Add `import HalalScreening from '../features/halal-screening'` and replace the placeholder.

- [ ] **Step 3: Verify build + commit**

  ```bash
  npm run build 2>&1 | tail -5
  git add src/features/halal-screening/index.tsx src/router/index.tsx
  git commit -m "feat: add Halal Screening page with asset status and approval rate"
  ```

---

## Task 12: Charts + Performance page

**Files:**
- Create: `src/components/PnlChart.tsx`
- Create: `src/components/HalalDonut.tsx`
- Create: `src/features/performance/index.tsx`
- Modify: `src/router/index.tsx`

- [ ] **Step 1: Create `src/components/PnlChart.tsx`**

  ```tsx
  import {
    LineChart,
    Line,
    XAxis,
    YAxis,
    CartesianGrid,
    Tooltip,
    ResponsiveContainer,
    ReferenceLine,
  } from 'recharts'
  import type { DailyPnlEntry } from '../types/api'

  interface Props {
    data: DailyPnlEntry[]
  }

  export default function PnlChart({ data }: Props) {
    const lastValue = data[data.length - 1]?.cumulativePnl ?? 0
    const color = lastValue >= 0 ? '#00d4aa' : '#ff4444'

    if (data.length === 0) {
      return (
        <div className="h-48 flex items-center justify-center text-slate-400 dark:text-[#3d5a6e] text-sm">
          Aucune donnée disponible
        </div>
      )
    }

    return (
      <ResponsiveContainer width="100%" height={220}>
        <LineChart data={data} margin={{ top: 5, right: 15, bottom: 5, left: 15 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#1e2d3d" vertical={false} />
          <XAxis
            dataKey="date"
            tick={{ fontSize: 10, fill: '#3d5a6e' }}
            tickLine={false}
            axisLine={false}
          />
          <YAxis
            tick={{ fontSize: 10, fill: '#3d5a6e' }}
            tickLine={false}
            axisLine={false}
            tickFormatter={v => `€${v}`}
          />
          <Tooltip
            contentStyle={{
              background: '#161b22',
              border: '1px solid #1e2d3d',
              borderRadius: 6,
              fontSize: 12,
            }}
            labelStyle={{ color: '#e6edf3' }}
            itemStyle={{ color }}
            formatter={(v: number) => [`€${v.toFixed(2)}`, 'P&L cumulatif']}
          />
          <ReferenceLine y={0} stroke="#3d5a6e" strokeDasharray="3 3" />
          <Line
            type="monotone"
            dataKey="cumulativePnl"
            stroke={color}
            strokeWidth={2}
            dot={false}
            activeDot={{ r: 4, fill: color }}
          />
        </LineChart>
      </ResponsiveContainer>
    )
  }
  ```

- [ ] **Step 2: Create `src/components/HalalDonut.tsx`**

  ```tsx
  import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer } from 'recharts'

  interface Props {
    approvedAssets: number
    totalAssets: number
  }

  const COLORS = ['#00d4aa', '#ff4444']

  export default function HalalDonut({ approvedAssets, totalAssets }: Props) {
    const rejected = totalAssets - approvedAssets
    const data = [
      { name: 'Approuvé', value: approvedAssets },
      { name: 'Rejeté', value: rejected },
    ].filter(d => d.value > 0)

    if (totalAssets === 0) {
      return (
        <div className="h-40 flex items-center justify-center text-slate-400 dark:text-[#3d5a6e] text-sm">
          Aucun actif
        </div>
      )
    }

    return (
      <ResponsiveContainer width="100%" height={170}>
        <PieChart>
          <Pie
            data={data}
            innerRadius={45}
            outerRadius={65}
            dataKey="value"
            paddingAngle={3}
          >
            {data.map((_, index) => (
              <Cell key={index} fill={COLORS[index % COLORS.length]} />
            ))}
          </Pie>
          <Tooltip
            contentStyle={{
              background: '#161b22',
              border: '1px solid #1e2d3d',
              borderRadius: 6,
              fontSize: 12,
            }}
            itemStyle={{ color: '#e6edf3' }}
          />
          <Legend
            formatter={(value) => (
              <span style={{ color: '#3d5a6e', fontSize: 11 }}>{value}</span>
            )}
          />
        </PieChart>
      </ResponsiveContainer>
    )
  }
  ```

- [ ] **Step 3: Create `src/features/performance/index.tsx`**

  ```tsx
  import { usePerformance } from '../../api/performance'
  import Card from '../../components/Card'
  import PnlChart from '../../components/PnlChart'
  import HalalDonut from '../../components/HalalDonut'
  import { TrendingUp, TrendingDown, Clock } from 'lucide-react'

  export default function Performance() {
    const { data: perf, isLoading } = usePerformance()

    if (isLoading) {
      return <div className="animate-pulse h-96 bg-slate-200 dark:bg-[#161b22] rounded-lg" />
    }

    const winRatePct = perf?.winRate ?? 0
    const pnlPositive = (perf?.dailyPnl[perf.dailyPnl.length - 1]?.cumulativePnl ?? 0) >= 0

    return (
      <div className="space-y-6">
        <h1 className="text-xl font-semibold text-slate-900 dark:text-[#e6edf3]">Performance</h1>

        {/* P&L chart */}
        <Card title="P&L cumulatif depuis le premier trade">
          <PnlChart data={perf?.dailyPnl ?? []} />
        </Card>

        {/* Stats row */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          {/* Win rate */}
          <Card title="Win rate">
            <div className="flex items-baseline gap-2 mb-3">
              <span className={`text-3xl font-bold ${winRatePct >= 50 ? 'text-teal-600 dark:text-[#00d4aa]' : 'text-red-500'}`}>
                {winRatePct.toFixed(1)}%
              </span>
              <span className="text-sm text-slate-400 dark:text-[#3d5a6e]">trades profitables</span>
            </div>
            <div className="h-2 bg-slate-200 dark:bg-[#1e2d3d] rounded-full overflow-hidden">
              <div
                className={`h-full rounded-full transition-all ${winRatePct >= 50 ? 'bg-teal-500 dark:bg-[#00d4aa]' : 'bg-red-500'}`}
                style={{ width: `${winRatePct}%` }}
              />
            </div>
          </Card>

          {/* Best / worst */}
          <Card title="Actifs">
            <div className="space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <TrendingUp size={14} className="text-teal-500 dark:text-[#00d4aa]" />
                  <span className="text-sm text-slate-600 dark:text-[#3d5a6e]">Meilleur</span>
                </div>
                {perf?.bestAsset ? (
                  <div className="text-right">
                    <div className="text-sm font-semibold text-slate-900 dark:text-[#e6edf3]">{perf.bestAsset.symbol}</div>
                    <div className="text-xs text-teal-600 dark:text-[#00d4aa]">+€{perf.bestAsset.totalPnl.toFixed(2)}</div>
                  </div>
                ) : <span className="text-slate-400 text-sm">—</span>}
              </div>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <TrendingDown size={14} className="text-red-500" />
                  <span className="text-sm text-slate-600 dark:text-[#3d5a6e]">Pire</span>
                </div>
                {perf?.worstAsset ? (
                  <div className="text-right">
                    <div className="text-sm font-semibold text-slate-900 dark:text-[#e6edf3]">{perf.worstAsset.symbol}</div>
                    <div className="text-xs text-red-500">€{perf.worstAsset.totalPnl.toFixed(2)}</div>
                  </div>
                ) : <span className="text-slate-400 text-sm">—</span>}
              </div>
            </div>
          </Card>

          {/* Total trades + last cycle */}
          <Card title="Statistiques">
            <div className="space-y-3">
              <div className="flex justify-between items-baseline">
                <span className="text-sm text-slate-500 dark:text-[#3d5a6e]">Total trades</span>
                <span className="text-xl font-bold text-slate-900 dark:text-[#e6edf3]">{perf?.totalTrades ?? 0}</span>
              </div>
              {perf?.lastCycleAt && (
                <div className="flex items-center gap-2 text-xs text-slate-400 dark:text-[#3d5a6e]">
                  <Clock size={12} />
                  Dernier cycle : {new Date(perf.lastCycleAt).toLocaleString('fr-FR')}
                </div>
              )}
            </div>
          </Card>
        </div>

        {/* Halal donut */}
        <Card title={`Taux d'approbation halal — ${perf?.halalApprovalRate.toFixed(1) ?? 0}%`}>
          <HalalDonut
            approvedAssets={perf?.approvedAssets ?? 0}
            totalAssets={perf?.totalAssets ?? 0}
          />
        </Card>
      </div>
    )
  }
  ```

- [ ] **Step 4: Update router — replace Performance placeholder**

  Add `import Performance from '../features/performance'` and replace the placeholder.

- [ ] **Step 5: Verify build**

  ```bash
  npm run build 2>&1 | tail -5
  ```

- [ ] **Step 6: Commit**

  ```bash
  git add src/components/PnlChart.tsx src/components/HalalDonut.tsx src/features/performance/index.tsx src/router/index.tsx
  git commit -m "feat: add Performance page with P&L chart, win rate, best/worst asset and halal donut"
  ```

---

## Task 13: Settings page + final router wiring

**Files:**
- Create: `src/features/settings/index.tsx`
- Modify: `src/router/index.tsx` (final — remove all Placeholder imports)

- [ ] **Step 1: Create `src/features/settings/index.tsx`**

  ```tsx
  import { useTheme } from '../../theme/ThemeContext'
  import { useAssets } from '../../api/assets'
  import { usePerformance } from '../../api/performance'
  import ThemeToggle from '../../components/ThemeToggle'
  import Card from '../../components/Card'
  import Badge from '../../components/Badge'

  export default function Settings() {
    const { theme } = useTheme()
    const { data: assets = [] } = useAssets()
    const { data: perf } = usePerformance()

    return (
      <div className="space-y-6 max-w-2xl">
        <h1 className="text-xl font-semibold text-slate-900 dark:text-[#e6edf3]">Réglages</h1>

        {/* Theme */}
        <Card title="Apparence">
          <div className="flex items-center justify-between">
            <div>
              <div className="text-sm text-slate-700 dark:text-[#e6edf3] font-medium">Thème actuel</div>
              <div className="text-xs text-slate-400 dark:text-[#3d5a6e] mt-0.5">
                {theme === 'dark' ? 'Dark Pro — Bloomberg / TradingView' : 'Clean Light — Fintech moderne'}
              </div>
            </div>
            <ThemeToggle />
          </div>
        </Card>

        {/* Scheduler info */}
        <Card title="Scheduler">
          <div className="space-y-2 text-sm">
            <div className="flex justify-between">
              <span className="text-slate-500 dark:text-[#3d5a6e]">Dernier cycle</span>
              <span className="text-slate-700 dark:text-[#e6edf3]">
                {perf?.lastCycleAt
                  ? new Date(perf.lastCycleAt).toLocaleString('fr-FR')
                  : 'Aucun cycle exécuté'}
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500 dark:text-[#3d5a6e]">Total trades exécutés</span>
              <span className="text-slate-700 dark:text-[#e6edf3] font-medium">{perf?.totalTrades ?? 0}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500 dark:text-[#3d5a6e]">Fréquence</span>
              <span className="text-slate-400 dark:text-[#3d5a6e]">Configurable via scheduler Spring</span>
            </div>
          </div>
        </Card>

        {/* Monitored assets */}
        <Card title={`Actifs surveillés (${assets.length})`}>
          <div className="space-y-2">
            {assets.length === 0 ? (
              <div className="text-slate-400 dark:text-[#3d5a6e] text-sm">Aucun actif enregistré</div>
            ) : (
              assets.map(a => (
                <div key={a.id} className="flex items-center justify-between py-2 border-b border-slate-100 dark:border-[#1e2d3d] last:border-0">
                  <div>
                    <span className="font-medium text-slate-900 dark:text-[#e6edf3] text-sm">{a.symbol}</span>
                    <span className="text-xs text-slate-400 dark:text-[#3d5a6e] ml-2">{a.name}</span>
                  </div>
                  <Badge variant={a.halalScreening} />
                </div>
              ))
            )}
          </div>
        </Card>

        {/* Backend info */}
        <Card title="API Backend">
          <div className="text-xs text-slate-400 dark:text-[#3d5a6e] space-y-1 font-mono">
            <div>Backend: http://localhost:8080</div>
            <div>Frontend: http://localhost:5173</div>
            <div>Proxy: /api → http://localhost:8080</div>
          </div>
        </Card>
      </div>
    )
  }
  ```

- [ ] **Step 2: Final `src/router/index.tsx` — replace all remaining placeholders**

  ```tsx
  import { createBrowserRouter } from 'react-router-dom'
  import AppLayout from '../components/AppLayout'
  import Overview from '../features/overview'
  import Positions from '../features/positions'
  import Trades from '../features/trades'
  import AiReasoning from '../features/ai-reasoning'
  import HalalScreening from '../features/halal-screening'
  import Performance from '../features/performance'
  import Settings from '../features/settings'

  export const router = createBrowserRouter([
    {
      element: <AppLayout />,
      children: [
        { path: '/', element: <Overview /> },
        { path: '/positions', element: <Positions /> },
        { path: '/trades', element: <Trades /> },
        { path: '/ai-reasoning', element: <AiReasoning /> },
        { path: '/halal-screening', element: <HalalScreening /> },
        { path: '/performance', element: <Performance /> },
        { path: '/settings', element: <Settings /> },
      ],
    },
  ])
  ```

- [ ] **Step 3: Final build check**

  ```bash
  npm run build 2>&1 | tail -10
  ```
  Expected: BUILD SUCCESS, zero TypeScript errors.

- [ ] **Step 4: Commit**

  ```bash
  git add src/features/settings/index.tsx src/router/index.tsx
  git commit -m "feat: add Settings page and complete router wiring — all 7 pages active"
  ```

---

## Vérification finale

After all tasks are complete:

1. Start the Spring Boot backend (port 8080) and the Python market-data service (port 5001) if needed.
2. Start the frontend:
   ```bash
   cd "/c/Users/alkao/OneDrive/Bureau/perso/halaltrader-frontend"
   npm run dev
   ```
3. Open http://localhost:5173 — should see the Dark Pro sidebar dashboard.
4. Verify each page navigates correctly and API calls return data (Network tab).
5. Go to Réglages and toggle the theme — should switch to Clean Light and persist on reload.

---

## Décisions intentionnelles

- **Dark Pro as default** — `ThemeProvider` reads `localStorage` on mount; default is `'dark'`, so `document.documentElement.classList.add('dark')` fires immediately.
- **No pnl/currentPrice in Positions** — backend intentionally omits live prices. `value = quantity × avgPrice` (cost basis).
- **AI Reasoning page uses lazy fetch** — `useTradeDetail(id)` only fires when `enabled: id !== null`, i.e. when the user expands a row.
- **Recharts dark colors are hardcoded** — Recharts doesn't read Tailwind CSS variables, so we use hex values directly. The chart always renders in Dark Pro palette regardless of theme toggle (acceptable for Phase 3B).
- **Page<T> deserialization** — Spring's `Page<TradeDto>` serialises with a `content` array + pagination metadata. The `PagedResponse<T>` interface matches this exactly.
