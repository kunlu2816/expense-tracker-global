# Frontend Architecture & Implementation

> **Audience:** Developers working on the frontend  
> **Last Updated:** 2026-03-08  
> **Stack:** React 19 · Vite 7 · Ant Design 6 · Recharts 3 · Axios · React Router 7

---

## Overview

The frontend is a React **Single Page Application (SPA)** that communicates with the Spring Boot backend via REST API. It provides:

- **Authentication** — Login/Register forms with JWT token management
- **Dashboard** — Summary stats (income/expense/balance), spending pie chart, recent transactions
- **Transaction Management** — Full CRUD with filtering, pagination, and CSV export
- **Category Management** — CRUD with emoji icon support
- **Bank Account Management** — Link/sync/unlink banks via GoCardless
- **Profile** — Edit name, change password

---

## Why These Technologies?

| Choice | Reason |
|--------|--------|
| **React 19** | Component-based, massive ecosystem, hot module replacement via Vite |
| **Vite 7** | Near-instant startup and HMR (vs. Webpack's slow builds) |
| **Ant Design 6** | Professional UI component library — tables, forms, modals, notifications out of the box |
| **Recharts 3** | React-native charting library, composable, works well with Ant Design |
| **Axios** | HTTP client with interceptor support — essential for JWT token injection |
| **React Router 7** | Client-side routing with nested layouts and protected routes |
| **dayjs** | Lightweight date manipulation (Ant Design's DatePicker depends on it) |

---

## Project Structure

```
frontend/
├── index.html                    # Entry point — loads /src/main.jsx
├── package.json                  # Dependencies and scripts
├── vite.config.js                # Vite + React plugin configuration
│
└── src/
    ├── main.jsx                  # ReactDOM.createRoot() — renders <App />
    ├── App.jsx                   # Router + Ant Design theme + AuthProvider
    ├── index.css                 # Design system — all CSS variables and styles
    │
    ├── api/
    │   └── axios.js              # Axios instance with JWT interceptor
    │
    ├── contexts/
    │   └── AuthContext.jsx        # React Context for auth state (user, login, logout)
    │
    ├── components/
    │   ├── AppLayout.jsx          # Sidebar + header shell (wraps all protected pages)
    │   └── ProtectedRoute.jsx     # Redirects to /login if not authenticated
    │
    └── pages/
        ├── Login.jsx              # Email + password form
        ├── Register.jsx           # Name + email + password + confirm form
        ├── Dashboard.jsx          # Stat cards, pie chart, recent transactions
        ├── Transactions.jsx       # Filterable table + CRUD modal + CSV export
        ├── Categories.jsx         # Category table + add/edit modal
        ├── BankAccounts.jsx       # Bank cards, link flow, sync, history
        └── Profile.jsx            # Edit name + change password forms
```

---

## How It Works

### 1. Entry Point Flow

```
index.html
  └── loads /src/main.jsx
        └── renders <App />
              └── <ConfigProvider>        ← Ant Design green theme (#0D9F6E)
                    └── <BrowserRouter>   ← React Router
                          └── <AuthProvider>  ← Auth context (JWT state)
                                └── <Routes>
                                      ├── /login     → <Login />
                                      ├── /register  → <Register />
                                      └── / (protected)
                                            └── <AppLayout>  ← Sidebar + Header
                                                  ├── /           → <Dashboard />
                                                  ├── /transactions → <Transactions />
                                                  ├── /categories  → <Categories />
                                                  ├── /banks       → <BankAccounts />
                                                  └── /profile     → <Profile />
```

### 2. Authentication Flow

```
User opens app
  → AuthContext checks localStorage for JWT token
  → Token exists? → Call GET /api/user/profile to validate
    → Valid → set user state, show dashboard
    → Invalid/expired → clear token, redirect to /login
  → No token → show /login page

Login:
  → POST /api/auth/login { email, password }
  → Backend returns { token, fullName }
  → Store token in localStorage
  → Load user profile → set auth state → redirect to /

Register:
  → POST /api/auth/register { email, password, fullName }
  → Success → redirect to /login with success message

Logout:
  → Clear localStorage (token + user)
  → Reset auth state → redirect to /login
```

### 3. API Client (axios.js)

The Axios instance is the bridge between frontend and backend:

```javascript
// Base URL: http://localhost:8080/api
// Every request automatically includes the JWT token:
//   Authorization: Bearer <token>

// Request interceptor:
//   → Reads token from localStorage
//   → Attaches to Authorization header

// Response interceptor:
//   → If 401 (Unauthorized) → token expired
//   → Auto-clear localStorage → redirect to /login
```

**Why interceptors?** Without them, every API call would need manual token handling. The interceptor centralizes this — write `api.get('/transactions')` and auth is automatic.

### 4. Protected Routes

`ProtectedRoute.jsx` wraps all authenticated pages:
- If `loading` → shows spinner (waiting for auth check)
- If `!isAuthenticated` → redirects to `/login`
- If authenticated → renders the child component

### 5. Layout (AppLayout.jsx)

The sidebar + header layout wraps all protected pages:

```
┌──────────────────────────────────────────────┐
│  Header (user name, logout button)           │
├──────────┬───────────────────────────────────┤
│          │                                   │
│ Sidebar  │         Page Content              │
│          │         (via <Outlet />)           │
│ Dashboard│                                   │
│ Txns     │                                   │
│ Cats     │                                   │
│ Banks    │                                   │
│ Profile  │                                   │
│          │                                   │
└──────────┴───────────────────────────────────┘
```

Uses React Router's `<Outlet />` to render the active child route inside the layout.

---

## Design System (index.css)

All styling is centralized in `index.css` with CSS custom properties:

### Color Palette
| Variable | Value | Usage |
|----------|-------|-------|
| `--color-primary` | `#0D9F6E` | Buttons, links, active states |
| `--color-primary-hover` | `#087F5B` | Button hover states |
| `--color-bg-page` | `#F5F5F5` | Page background |
| `--color-bg-card` | `#FFFFFF` | Cards, modals |
| `--color-text-primary` | `#1A1A2E` | Main text |
| `--color-text-secondary` | `#6B7280` | Subtitles, labels |
| `--color-text-muted` | `#9CA3AF` | Hints, timestamps |
| `--color-border` | `#E5E7EB` | Card borders, dividers |
| `--color-income` | `#0D9F6E` | Income amounts (green) |
| `--color-expense` | `#E53935` | Expense amounts (red) |

### Typography
- **Font:** Inter (Google Fonts), with system font fallbacks
- **Headings:** 600 weight, dark navy (#1A1A2E)
- **Body:** 400 weight, 14px base

### Key Design Rules
- **Light/white theme** — no dark mode
- **Currency:** GBP (£)
- **Income:** Green text
- **Expense:** Red text
- **Card borders:** 1px solid, 12px border-radius
- **Buttons:** 8px border-radius, green primary color
- **Spacing:** Consistent 16px/24px padding system

---

## Page Details

### Login (`/login`)
- Email + password form with Ant Design validation
- On success: stores JWT → loads profile → redirects to `/`
- Link to register page

### Register (`/register`)
- Full name + email + password + confirm password
- Client-side validation matches backend rules (email format, min 6 chars)
- On success: redirects to `/login` with success message

### Dashboard (`/`)
- **3 stat cards:** Total Income, Total Expense, Balance
- **Pie chart:** Spending breakdown by category (Recharts donut)
- **Recent transactions table:** Last 5 transactions
- Fetches from `GET /transactions/dashboard` + `GET /transactions?page=0&size=5`

### Transactions (`/transactions`)
- **Filter bar:** Category dropdown + date range picker + Apply button
- **Table:** Date, Category (tag), Description, Type (IN/OUT tag), Amount, Actions
- **Pagination:** Page size selector, total count display
- **Add/Edit modal:** Type (Income/Expense), Category, Amount (£), Description, Date
- **Delete:** Confirmation popover before deletion
- **Export CSV:** Downloads via `GET /transactions/export`

### Categories (`/categories`)
- **Table:** Icon (emoji) + Name, Type (Income/Expense tag), Actions
- **Add/Edit modal:** Name, Type (Income/Expense), Icon (emoji input)
- Auto-created categories appear here when transactions are created

### Bank Accounts (`/banks`)
- **Empty state:** "Link Your First Bank" button
- **Account cards:** Bank logo/name, status tag, masked IBAN, last sync time
- **Card actions:** Sync, History, Unlink
- **Link modal:** Country selector (UK, DE, FR, etc.) → Bank dropdown → Connect
- **History modal:** Table of past syncs (date, status, fetched/new counts, errors)
- **Sync:** Triggers `POST /banks/{id}/sync`, shows new transaction count

### Profile (`/profile`)
- **Two-column layout:**
  - Left: Profile info (email read-only, editable name)
  - Right: Change password (current + new + confirm)

---

## API Endpoints Used by Frontend

| Page | Endpoint | Method | Purpose |
|------|----------|--------|---------|
| Login | `/auth/login` | POST | Authenticate user |
| Register | `/auth/register` | POST | Create account |
| Auth Check | `/user/profile` | GET | Validate token on page load |
| Dashboard | `/transactions/dashboard` | GET | Income/expense/balance stats |
| Dashboard | `/transactions?page=0&size=5` | GET | Recent transactions |
| Transactions | `/transactions` | GET | Paginated filtered list |
| Transactions | `/transactions` | POST | Create transaction |
| Transactions | `/transactions/{id}` | PUT | Update transaction |
| Transactions | `/transactions/{id}` | DELETE | Delete transaction |
| Transactions | `/transactions/export` | GET | CSV download |
| Categories | `/categories` | GET | List categories |
| Categories | `/categories` | POST | Create category |
| Categories | `/categories/{id}` | PUT | Update category |
| Categories | `/categories/{id}` | DELETE | Delete category |
| Banks | `/banks` | GET | List linked accounts |
| Banks | `/banks/institutions?country=` | GET | List available banks |
| Banks | `/banks/link` | POST | Start linking flow |
| Banks | `/banks/{id}/sync` | POST | Manual sync |
| Banks | `/banks/{id}/sync-history` | GET | Sync history |
| Banks | `/banks/{id}` | DELETE | Unlink bank |
| Profile | `/user/profile` | PUT | Update name |
| Profile | `/user/change-password` | PUT | Change password |

---

## Running the Frontend

### Prerequisites
- Node.js 18+ installed
- Backend running on `http://localhost:8080`

### Commands
```bash
cd frontend
npm install          # Install dependencies (first time only)
npm run dev          # Start dev server → http://localhost:5173
npm run build        # Production build → dist/
npm run preview      # Preview production build
```

### CORS
The Spring Boot backend allows `http://localhost:*` origins via `SecurityConfig.java`. The Vite dev server runs on port 5173 by default.

---

## Architecture Decisions

### Why SPA instead of server-rendered (Thymeleaf)?
- **Better UX:** No full page reloads, instant navigation
- **Separation of concerns:** Frontend and backend are independent, deployable separately
- **Modern tooling:** Hot module replacement, component dev tools
- **Team scalability:** Frontend and backend developers can work independently

### Why Ant Design instead of custom CSS?
- **Speed:** Production-quality tables, forms, modals, notifications out of the box
- **Consistency:** Unified design language across all pages
- **Configurability:** Theme tokens override colors/fonts globally
- **Accessibility:** Built-in ARIA attributes and keyboard navigation

### Why Context API instead of Redux?
- **Simplicity:** Auth state is the only global state needed
- **Minimal boilerplate:** No actions, reducers, or store configuration
- **Right tool for the job:** Redux is overkill when state is simple and local to components

### Why Axios instead of fetch()?
- **Interceptors:** Automatic JWT injection and 401 handling
- **Request cancellation:** Built-in AbortController support
- **Response parsing:** Automatic JSON parsing
- **Base URL:** Set once, all requests use it
