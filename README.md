# 💸 ExpenseSplitter

A console-based expense-splitting application (Splitwise-style) built with **Java, JDBC, and MySQL**. It lets groups of users log shared expenses, split them equally / exactly / by percentage / with custom contributions, and settle up with the **minimum possible number of transactions**, computed using a greedy Priority Queue algorithm.

```
Java + MySQL + JDBC + Priority Queue Algorithm
```

---

## ✨ Features

### 👤 Users
- Registration & login with SHA-256 password hashing
- Account lockout after repeated failed login attempts
- Profile update and password change

### 👥 Groups & Expenses
- Create groups and add members
- Add expenses with category (Food, Travel, Hotel, Shopping, Entertainment, Other)
- Multiple split types:
  - **Equal** — shared evenly among members
  - **Exact / Custom** — enter each member's share manually, or let each member add their own share (with a *pending contribution* workflow and finalize step)
  - **Percentage** — split by percentage of the total
- 3-attempt confirmation loop before saving an expense, with the option to edit amount, split type, payer, category, or members before committing
- Read-only expense history per group

### 🧮 Debt Simplification (Core Algorithm)
- Balances are netted per group (`total_paid − total_share`)
- A **greedy two-max-heap algorithm** repeatedly matches the largest creditor with the largest debtor to produce the fewest possible settlement transactions
- Settled amounts are excluded from future balance recalculations, so a debt is never re-suggested after it's paid

### 💰 Settlements
- View suggested settlements and pending settlements
- Mark settlements as paid (authorization enforced — only the debtor can mark their own payment as settled)
- Full settlement history per group

### 📊 Reports & Export
- Generate expense reports and personal user reports, stored as `LONGTEXT`/CLOB in the database
- Export reports to `.txt` files, either to a default `exports/` folder or a custom path chosen by the user
- Post-transaction export prompts after key actions

### 🔔 Notifications & Activity Log
- In-app notifications (e.g. when a settlement payment is blocked)
- Full activity logging (user actions, target user, group context) backed by a MySQL trigger that auto-generates notifications from log entries

### 🛠️ Admin Panel
- Separate admin login
- View all registered users (name & email)
- View per-user activity history

### ⚙️ Self-Initializing Database
- On first run, the app automatically creates the `expense_splitter` database and all required tables (with foreign keys, checks, and triggers) — no manual schema setup needed
- Idempotent schema migrations (`ALTER TABLE ... ADD COLUMN IF NOT EXISTS`-style checks) keep existing databases up to date across versions
- A default admin account is seeded automatically on first run

---

## 🏗️ Tech Stack

| Layer            | Technology                                   |
|-------------------|-----------------------------------------------|
| Language          | Java (JDK 17+)                                |
| Database          | MySQL 8.x                                     |
| Connectivity      | JDBC (`mysql-connector-j`)                    |
| Architecture      | Layered — DAO → Service → Menu (console UI)   |
| Core Algorithm    | Greedy debt simplification via `PriorityQueue`|
| Security          | SHA-256 password hashing                      |

---

## 📁 Project Structure

```
com.expensesplitter
├── Main.java              # Entry point — boots DB init and the main menu
├── algorithm/              # Debt simplification (Priority Queue based)
├── dao/                    # Data-access layer (JDBC, one DAO per table/concern)
├── database/                # Connection management + self-initializing schema
├── menu/                    # Console UI (Main, User, Group, Admin menus)
├── model/                   # Domain entities (User, Group, Expense, Balance, ...)
├── service/                 # Business logic layer
└── utility/                  # Validation, hashing, console helpers, session, transactions
```

**Package breakdown:** 3 algorithm classes · 15 DAOs · 2 database classes · 4 menus · 9 models · 13 services · 6 utilities — roughly 50 source files.

### Database Schema (auto-created)

| Table | Purpose |
|---|---|
| `users` | Accounts, credentials, lockout tracking |
| `groups` / `group_members` | Groups and membership |
| `expenses` / `expense_splits` | Expenses and per-member share breakdown |
| `balances` | Net balance per user, per group |
| `settlements` | Suggested and completed settlements |
| `notifications` | In-app notifications |
| `activity_logs` | Full audit trail, with a trigger that raises notifications on blocked actions |
| `user_data_files` / `user_reports` | Exported reports stored as `LONGTEXT` |
| `admins` | Admin accounts |
| `user_storage_paths` | Custom export folder preferences |

---

## 🚀 Getting Started

### Prerequisites
- JDK 17 or later
- MySQL Server 8.x running locally
- `mysql-connector-j` on the classpath

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/<your-username>/ExpenseSplitter.git
   cd ExpenseSplitter
   ```

2. **Start MySQL** and make sure it's reachable at `localhost:3306` with a `root` user (default password is empty — update `DBConnection.java` if yours differs).

3. **Run the app.** On first launch, the app automatically:
   - Creates the `expense_splitter` database
   - Creates all tables, constraints, and triggers
   - Seeds a default admin account

   ```bash
   javac -cp .:mysql-connector-j-9.x.jar -d out $(find expensesplitter -name "*.java")
   java -cp out:mysql-connector-j-9.x.jar com.expensesplitter.Main
   ```

   *(or simply open the project in IntelliJ IDEA, add `mysql-connector-j` as a project library, and run `Main.java`)*

### Default Admin Credentials
```
Username: admin
Password: admin123
```
> ⚠️ Change this immediately in any non-local/demo environment — it is a seeded default for first-run convenience.

---

## 🧠 How the Debt Simplification Works

Instead of settling every individual expense between every pair of members, the app:
1. Computes each member's **net balance** in a group (`amount paid − amount owed`).
2. Pushes creditors (positive balance) and debtors (negative balance) into two max-heaps.
3. Repeatedly pairs the largest creditor with the largest debtor, settles the smaller of the two amounts, and re-inserts any remainder.
4. Produces the **minimum number of transactions** needed to settle the whole group — turning what could be dozens of pairwise debts into a handful of suggested payments.

---

## 📌 Project Context

This project was built as a Java + Data Structures + DBMS coursework project, with an emphasis on:
- Realistic layered application architecture (DAO/Service/UI separation)
- Transactional integrity for multi-step operations (via a shared `TransactionHelper`)
- A genuine algorithmic core (greedy heap-based debt simplification) rather than a purely CRUD app

---

## 📄 License

This project is available for educational and portfolio purposes. Feel free to fork and build on it.

---

## 🙋 Author

**Prince**
B.Tech IT Student
