# User acceptance test script

One section per video. Each lists what to say and click, and what the viewer should see. Before
recording, reset to a fresh start so order numbers are small and stock counts match:

```powershell
docker compose down -v
```

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-backend.ps1
```

Keep the three service windows visible in a corner where you can. The log lines (`POST /api/orders -> 201 (35 ms)`)
show the client/server calls and their speed (N1).

---

## Video 1: Accounts (FR1, FR2, FR3, FR11, N3)

1. Open the customer app (`run-customer.ps1`).
2. **Wrong password:** log in as `alice` / `wrong`. You see *Wrong username or password*.
3. **Sign up (FR3):** click *Create an account*. First try the email `bob@` to show that a bad email is refused. Then enter
   username `bob`, email `bob@example.com`, password `bob12345` twice, and click *Create account*. You land in the store,
   signed in as bob.
4. **Password is hashed (N3):** in a terminal, run the command below. The hash starts with `$2a$10$` (bcrypt), not the
   password itself.
   ```powershell
   docker exec bookstore-mysql mysql -ubookstore -pbookstore -e "SELECT username, password_hash FROM bookstore_users.users"
   ```
5. **Session lasts until logout (FR2):** click around the tabs; you stay signed in. Click *Log out* to get back to
   the login screen.
6. **Roles are separate (FR11):** in the customer app, log in as `manager` / `manager123`. You see *Managers must use
   the manager app*. Open the manager app and log in as `alice`. You see *This account is not a manager account*.

## Video 2: Search (FR4, FR5, N2)

1. Log in to the customer app as `alice` / `alice123`. The full catalog loads.
2. **By title:** type `gatsby` and press Enter. You get *The Great Gatsby*.
3. **By author:** type `austen`. You get Emma, Pride and Prejudice, Sense and Sensibility.
4. **Partial, any case:** type `TOLK`. You get both Tolkien books.
5. Point out the columns: title, author, buy price, rent price, stock, rating (FR5).
6. **Not frozen (N2):** while a search runs, the window still moves and resizes. All calls happen in the background.

## Video 3: Ordering, buying and renting (FR6, FR7)

1. Search `orwell`. Ctrl+click *1984* and *Animal Farm*, then click *Add selected to cart: Buy* (picks several books, FR6).
2. Search `dune`, select it, and click *Add selected to cart: Rent*.
3. Open the *Cart (3)* tab. Click *+* on *1984* to make it 2 copies. The total updates.
4. Note the stock of 1984 on the Browse tab (10), then click *Place order* and confirm (one transaction, FR7).
5. The bill window opens (see Video 4). Back on *Browse books*, 1984 now shows 8 in stock.
6. **Out of stock:** add *Design Patterns* (4 in stock), set quantity 5 in the cart, and place the order. You see
   *Not enough copies… (4 left, 5 requested)*, and the stock is unchanged.

## Video 4: Bill (FR8, FR9, FR10)

1. In the bill window from Video 3, point out: order number, date, each item with Buy/Rent, quantity, unit price,
   amount, rental due date, and the total (FR8).
2. **Saved as HTML (FR9):** open `backend\order_service\bills\` in File Explorer and double-click `order-1.html`. It
   opens in a browser. Back in the app, click *Print…* to show the print dialog.
3. **Emailed (FR10, only if `.env` is set up):** show the email arriving in the customer's inbox. The order service
   window logs `Emailed bill for order 1`.

## Video 5: Manager: orders and payment (FR12, FR13)

1. Open the manager app and log in as `manager` / `manager123`.
2. The *Orders* tab lists every order from every customer (FR12). Use *Show: Pending* and *Filter by customer*.
3. Select alice's order and click *Mark as Paid*. The status turns green *PAID* with the time (FR13).
4. Click *View / print bill*. The bill now says PAID.
5. In the customer app (as alice), open *My orders* and click *Refresh*. The order shows PAID.

## Video 6: Manager: books and rentals (FR14)

1. *Books* tab, click *Add book…*. Enter title `Project Hail Mary`, author `Andy Weir`, buy `24.99`, rent `6.99`,
   copies `3`, and save. It appears in the list (FR14).
2. Double-click *Dune*, change *Copies in stock* to `20`, and save. The table shows 20 (FR14).
3. In the customer app, search `hail mary`. The new book is there.
4. **Rental return:** on the *Orders* tab, select the order with Dune, select the Dune line, and click *Mark rental
   returned*. Its status changes to *Returned …*, and Dune's stock goes up by 1 on the Books tab.

## Video 7: Extra credit: profile and order history

1. Customer app as alice, *My orders* tab: all her orders, totals, payment status, and the books in each. *View bill*
   reopens any bill.
2. *Profile* tab: change *Full name*, then *Save changes*. The top bar shows the new name after logging out and back in.
3. Change the password, log out, and log in with the new one.

## Video 8: Extra credit: reviews and ratings

1. Customer app as alice, select *1984*. The right side shows its details and reviews.
2. Pick 4 stars, write a comment, and click *Submit review*. The review appears and the rating column updates.
3. Submit again with 2 stars. It replaces alice's review rather than adding a second one.
4. Log in as bob and review the same book. The average now covers both reviews.

## Video 9: Extra credit: sales reports

1. Manager app, *Sales reports* tab. *This month* shows the order count, revenue, paid and pending amounts, and copies
   sold and rented.
2. The chart and *Best sellers* table rank books by copies sold or rented.
3. Change *From* / *To* to a range with no orders (e.g. last year) and click *Run report*. Everything shows zero
   or empty, which proves the date range filters.

## Video 10: Security checks (N4, N5), optional

Run in a terminal, with the backend running:

```powershell
curl.exe -s http://127.0.0.1:5001/api/books?q=dune
```
You get `{"error":"Missing login token"}`: no token, no data (N4).

```powershell
cd backend; ..\.venv\Scripts\python -m pytest tests -v
```
All 28 tests pass. They include customers being blocked from manager endpoints, managers being blocked from
placing orders, and customers being unable to see each other's orders or bills (N5).
