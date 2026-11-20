"""All MySQL access for orders. Times are stored as UTC."""
from contextlib import contextmanager
from datetime import date, datetime, timezone
from decimal import Decimal

import pymysql
from pymysql.cursors import DictCursor


class Database:
    def __init__(self, **connect_args):
        self._args = connect_args

    @contextmanager
    def transaction(self):
        """A connection whose work is committed together, or rolled back on any error."""
        conn = pymysql.connect(**self._args, cursorclass=DictCursor, autocommit=False, charset="utf8mb4")
        try:
            yield conn
            conn.commit()
        except Exception:
            conn.rollback()
            raise
        finally:
            conn.close()


ORDER_COLUMNS = ("id, user_id, customer_username, customer_email, payment_status, total, "
                 "created_at, paid_at, bill_path")


class OrderRepository:
    def __init__(self, db: Database):
        self.db = db

    def insert(self, user: dict, total: Decimal, created_at: datetime, lines: list[dict]) -> int:
        with self.db.transaction() as conn, conn.cursor() as cur:
            cur.execute(
                "INSERT INTO orders (user_id, customer_username, customer_email, total, created_at) "
                "VALUES (%s, %s, %s, %s, %s)",
                (user["id"], user["username"], user["email"], total, created_at),
            )
            order_id = cur.lastrowid
            cur.executemany(
                "INSERT INTO order_items (order_id, book_id, title, author, type, quantity, "
                "unit_price, line_total, due_date) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)",
                [(order_id, l["book_id"], l["title"], l["author"], l["type"], l["quantity"],
                  l["unit_price"], l["line_total"], l["due_date"]) for l in lines],
            )
            return order_id

    def get(self, order_id: int) -> dict | None:
        orders = self._query(f"SELECT {ORDER_COLUMNS} FROM orders WHERE id = %s", (order_id,))
        return orders[0] if orders else None

    def find(self, user_id: int | None = None, status: str | None = None,
             start: datetime | None = None, end: datetime | None = None) -> list[dict]:
        where, params = [], []
        if user_id is not None:
            where.append("user_id = %s"); params.append(user_id)
        if status:
            where.append("payment_status = %s"); params.append(status)
        if start:
            where.append("created_at >= %s"); params.append(start)
        if end:
            where.append("created_at < %s"); params.append(end)
        sql = f"SELECT {ORDER_COLUMNS} FROM orders"
        if where:
            sql += " WHERE " + " AND ".join(where)
        return self._query(sql + " ORDER BY created_at DESC, id DESC", params)

    def set_payment_status(self, order_id: int, status: str, paid_at: datetime | None) -> None:
        with self.db.transaction() as conn, conn.cursor() as cur:
            cur.execute("UPDATE orders SET payment_status = %s, paid_at = %s WHERE id = %s",
                        (status, paid_at, order_id))

    def set_bill_path(self, order_id: int, path: str) -> None:
        with self.db.transaction() as conn, conn.cursor() as cur:
            cur.execute("UPDATE orders SET bill_path = %s WHERE id = %s", (path, order_id))

    def mark_returned(self, order_id: int, item_id: int, when: datetime) -> bool:
        """Marks a rented line returned. False if it isn't an unreturned rental in this order."""
        with self.db.transaction() as conn, conn.cursor() as cur:
            changed = cur.execute(
                "UPDATE order_items SET returned_at = %s "
                "WHERE id = %s AND order_id = %s AND type = 'RENT' AND returned_at IS NULL",
                (when, item_id, order_id),
            )
            return changed == 1

    def bestsellers(self, start: datetime | None, end: datetime | None, limit: int) -> list[dict]:
        where, params = [], []
        if start:
            where.append("o.created_at >= %s"); params.append(start)
        if end:
            where.append("o.created_at < %s"); params.append(end)
        sql = (
            "SELECT i.book_id, MAX(i.title) AS title, MAX(i.author) AS author, "
            "SUM(CASE WHEN i.type = 'BUY' THEN i.quantity ELSE 0 END) AS units_bought, "
            "SUM(CASE WHEN i.type = 'RENT' THEN i.quantity ELSE 0 END) AS units_rented, "
            "SUM(i.line_total) AS revenue "
            "FROM order_items i JOIN orders o ON o.id = i.order_id"
        )
        if where:
            sql += " WHERE " + " AND ".join(where)
        sql += " GROUP BY i.book_id ORDER BY units_bought + units_rented DESC, revenue DESC LIMIT %s"
        params.append(limit)
        with self.db.transaction() as conn, conn.cursor() as cur:
            cur.execute(sql, params)
            return cur.fetchall()

    def _query(self, sql: str, params) -> list[dict]:
        """Runs an orders query and attaches each order's items."""
        with self.db.transaction() as conn, conn.cursor() as cur:
            cur.execute(sql, params)
            orders = cur.fetchall()
            if not orders:
                return []
            ids = [o["id"] for o in orders]
            marks = ", ".join(["%s"] * len(ids))
            cur.execute(
                "SELECT id, order_id, book_id, title, author, type, quantity, unit_price, line_total, "
                f"due_date, returned_at FROM order_items WHERE order_id IN ({marks}) ORDER BY id",
                ids,
            )
            by_order: dict[int, list] = {}
            for item in cur.fetchall():
                by_order.setdefault(item["order_id"], []).append(item)
            for o in orders:
                o["items"] = by_order.get(o["id"], [])
            return orders


# ------------------------------------------------------------- JSON shape
def order_json(o: dict) -> dict:
    return {
        "id": o["id"],
        "user_id": o["user_id"],
        "customer_username": o["customer_username"],
        "customer_email": o["customer_email"],
        "payment_status": o["payment_status"],
        "total": float(o["total"]),
        "created_at": utc_iso(o["created_at"]),
        "paid_at": utc_iso(o["paid_at"]),
        "items": [item_json(i) for i in o["items"]],
    }


def item_json(i: dict) -> dict:
    return {
        "id": i["id"],
        "book_id": i["book_id"],
        "title": i["title"],
        "author": i["author"],
        "type": i["type"],
        "quantity": i["quantity"],
        "unit_price": float(i["unit_price"]),
        "line_total": float(i["line_total"]),
        "due_date": i["due_date"].isoformat() if isinstance(i["due_date"], date) else None,
        "returned_at": utc_iso(i["returned_at"]),
    }


def utc_iso(value: datetime | None) -> str | None:
    """MySQL gives back naive datetimes; ours are UTC, so label them as such."""
    return value.replace(tzinfo=timezone.utc).isoformat() if value else None
