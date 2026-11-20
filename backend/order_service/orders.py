"""Order rules: placing orders, payment status, rental returns and reports.

Knows nothing about HTTP; routes.py turns requests into these calls.
"""
from datetime import datetime, timedelta, timezone
from decimal import ROUND_HALF_UP, Decimal

from common.errors import ApiError

from .billing import BillWriter
from .books_client import BooksClient
from .notifier import Notifier
from .repository import OrderRepository

BUY, RENT = "BUY", "RENT"
PENDING, PAID = "PENDING", "PAID"
MAX_QUANTITY = 20
CENT = Decimal("0.01")


def utc_now() -> datetime:
    """Naive UTC, the form stored in MySQL. Microseconds dropped to match DATETIME."""
    return datetime.now(timezone.utc).replace(tzinfo=None, microsecond=0)


class OrderService:
    def __init__(self, repo: OrderRepository, books: BooksClient, bills: BillWriter,
                 notifier: Notifier, rental_days: int):
        self.repo = repo
        self.books = books
        self.bills = bills
        self.notifier = notifier
        self.rental_days = rental_days

    # --------------------------------------------------------- customers
    def place_order(self, user: dict, raw_items) -> dict:
        """FR6-FR10: one order for several books, each bought or rented."""
        wanted = parse_items(raw_items)
        quantities: dict[str, int] = {}
        for (book_id, _), qty in wanted.items():
            quantities[book_id] = quantities.get(book_id, 0) + qty

        books = self.books.reserve(quantities)  # all or nothing; raises 409 if short
        try:
            now = utc_now()
            due = (now + timedelta(days=self.rental_days)).date()
            lines = []
            for (book_id, kind), qty in wanted.items():
                book = books[book_id]
                unit = money(book["price"] if kind == BUY else book["rent_price"])
                lines.append({
                    "book_id": book_id,
                    "title": book["title"],
                    "author": book["author"],
                    "type": kind,
                    "quantity": qty,
                    "unit_price": unit,
                    "line_total": (unit * qty).quantize(CENT),
                    "due_date": due if kind == RENT else None,
                })
            total = sum((l["line_total"] for l in lines), Decimal("0.00"))
            order_id = self.repo.insert(user, total, now, lines)
        except Exception:
            self.books.release(quantities)
            raise

        order = self.repo.get(order_id)
        page = self._save_bill(order)
        self.notifier.send_bill_later(order["customer_email"], order_id, page)
        return order

    def orders_for(self, user: dict) -> list[dict]:
        return self.repo.find(user_id=user["id"])

    def order_for(self, user: dict, order_id: int) -> dict:
        """An order the caller may see: their own, or any order for a manager (N5)."""
        order = self.repo.get(order_id)
        if order is None or (user["role"] != "MANAGER" and order["user_id"] != user["id"]):
            raise ApiError(404, "Order not found")
        return order

    def bill_for(self, user: dict, order_id: int) -> str:
        return self.bills.save(self.order_for(user, order_id))[1]

    # ---------------------------------------------------------- managers
    def all_orders(self, status: str | None, start: datetime | None, end: datetime | None) -> list[dict]:
        if status and status not in (PENDING, PAID):
            raise ApiError(400, "status must be PENDING or PAID")
        return self.repo.find(status=status, start=start, end=end)

    def set_payment_status(self, order_id: int, status: str) -> dict:
        """FR13."""
        if status not in (PENDING, PAID):
            raise ApiError(400, "payment_status must be PENDING or PAID")
        if self.repo.get(order_id) is None:
            raise ApiError(404, "Order not found")
        self.repo.set_payment_status(order_id, status, utc_now() if status == PAID else None)
        order = self.repo.get(order_id)
        self._save_bill(order)
        return order

    def return_rental(self, order_id: int, item_id: int) -> dict:
        """Marks a rented book returned and puts its copies back in stock."""
        order = self.repo.get(order_id)
        if order is None:
            raise ApiError(404, "Order not found")
        item = next((i for i in order["items"] if i["id"] == item_id), None)
        if item is None:
            raise ApiError(404, "Item not found in this order")
        if item["type"] != RENT:
            raise ApiError(400, "Only rented books can be returned")
        if not self.repo.mark_returned(order_id, item_id, utc_now()):
            raise ApiError(409, "This rental was already returned")
        self.books.release({item["book_id"]: item["quantity"]})
        order = self.repo.get(order_id)
        self._save_bill(order)
        return order

    def sales_report(self, start: datetime | None, end: datetime | None) -> dict:
        orders = self.repo.find(start=start, end=end)
        revenue = sum((o["total"] for o in orders), Decimal("0"))
        paid = sum((o["total"] for o in orders if o["payment_status"] == PAID), Decimal("0"))
        items = [i for o in orders for i in o["items"]]
        return {
            "order_count": len(orders),
            "revenue": float(revenue),
            "paid_revenue": float(paid),
            "pending_revenue": float(revenue - paid),
            "units_bought": sum(i["quantity"] for i in items if i["type"] == BUY),
            "units_rented": sum(i["quantity"] for i in items if i["type"] == RENT),
            "orders": orders,
        }

    def bestsellers(self, start: datetime | None, end: datetime | None, limit: int) -> list[dict]:
        rows = self.repo.bestsellers(start, end, limit)
        return [{
            "book_id": r["book_id"],
            "title": r["title"],
            "author": r["author"],
            "units_bought": int(r["units_bought"]),
            "units_rented": int(r["units_rented"]),
            "revenue": float(r["revenue"]),
        } for r in rows]

    def _save_bill(self, order: dict) -> str:
        path, page = self.bills.save(order)
        if order.get("bill_path") != path:
            self.repo.set_bill_path(order["id"], path)
        return page


def parse_items(raw) -> dict[tuple[str, str], int]:
    """[{book_id, type, quantity}] -> {(book_id, type): quantity}, merging repeats."""
    if not isinstance(raw, list) or not raw:
        raise ApiError(400, "Choose at least one book")
    wanted: dict[tuple[str, str], int] = {}
    for item in raw:
        if not isinstance(item, dict) or not isinstance(item.get("book_id"), str):
            raise ApiError(400, "Each item needs a book_id")
        kind = str(item.get("type", BUY)).upper()
        if kind not in (BUY, RENT):
            raise ApiError(400, "type must be BUY or RENT")
        qty = item.get("quantity", 1)
        if not isinstance(qty, int) or isinstance(qty, bool) or not 1 <= qty <= MAX_QUANTITY:
            raise ApiError(400, f"quantity must be between 1 and {MAX_QUANTITY}")
        key = (item["book_id"], kind)
        wanted[key] = wanted.get(key, 0) + qty
    return wanted


def money(value) -> Decimal:
    return Decimal(str(value)).quantize(CENT, rounding=ROUND_HALF_UP)
