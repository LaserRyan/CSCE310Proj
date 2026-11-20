# Online Bookstore (CSCE 310 project)

A bookstore made of three REST services and two JavaFX desktop apps.

| Part | Tech | Port | Data |
|---|---|---|---|
| User service: accounts, login, sessions | Java `HttpServer` | 8081 | MySQL `bookstore_users` |
| Book service: catalog, search, stock, reviews | Python Flask | 5001 | MongoDB `bookstore` |
| Order service: orders, bills, payments, rentals, reports | Python Flask | 5002 | MySQL `bookstore_orders` |
| Customer app | JavaFX | — | — |
| Manager app | JavaFX | — | — |

Design write-up: [docs/REPORT.md](docs/REPORT.md). Video test script: [docs/UAT-SCRIPT.md](docs/UAT-SCRIPT.md).

## What you need

- **Docker Desktop**, running (for MySQL 8.4 and MongoDB 7)
- **JDK 21**
- **Python 3.11+**

Maven isn't needed: `mvnw.cmd` downloads it on first use.

## Run it (Windows PowerShell, from this folder)

```powershell
powershell -ExecutionPolicy Bypass -File scripts\setup.ps1
```

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-backend.ps1
```

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-customer.ps1
```

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-manager.ps1
```

`setup.ps1` is needed once. `start-backend.ps1` opens one window per service; close those windows to stop them.

### Accounts made on first start

| App | Username | Password |
|---|---|---|
| Manager | `manager` | `manager123` |
| Customer | `alice` | `alice123` |

The book service loads 35 sample books the first time it finds an empty catalog.

### Emailed bills (optional, FR10)

Copy `.env.example` to `.env` and fill in the SMTP settings, then restart the backend. Without them,
orders still work and the bill is still saved to `backend/order_service/bills/`. The order service
log just says the email was skipped.

## Tests

End-to-end tests call the running services over HTTP, with real databases behind them. Start the
backend first, then:

```powershell
cd backend; ..\.venv\Scripts\python -m pytest tests -v
```

They create users named `e2e_…` and books titled `E2E Test Book …`, and delete them when done.

## Reset to a fresh start

```powershell
docker compose down -v
```

This deletes all data. The next `start-backend.ps1` recreates the accounts and sample books.

## Screenshots

`clients/screenshots` is a dev tool that opens every screen and saves PNGs to `docs/screenshots`.
It needs the backend running, and it places a couple of demo orders as `alice`.

```powershell
.\mvnw.cmd -q -pl clients/screenshots javafx:run
```

## Folder map

```
backend/
  user-service/      Java user service (Maven module)
  common/            Python code shared by both Flask services (login check, errors, settings)
  book_service/      Flask book service
  order_service/     Flask order service (bills are written to order_service/bills/)
  tests/             end-to-end API tests (pytest)
clients/
  common/            API client, data records, shared JavaFX pieces (login screen, bill window…)
  customer-app/      customer JavaFX app
  manager-app/       manager JavaFX app
  screenshots/       dev tool that captures the screens
db/mysql/            schema, run when the MySQL container is first created
docs/                report, test script, screenshots
scripts/             setup and start scripts
```
