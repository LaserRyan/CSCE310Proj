"""Order service HTTP endpoints."""
from datetime import datetime, timezone

from flask import Blueprint, Response, g, request

from common.auth import CUSTOMER, MANAGER, require_login
from common.errors import ApiError, json_body

from .orders import OrderService
from .repository import order_json


def create_blueprint(service: OrderService) -> Blueprint:
    bp = Blueprint("orders", __name__)

    @bp.get("/health")
    def health():
        return {"status": "ok"}

    # --------------------------------------------------------- customers
    @bp.post("/api/orders")
    @require_login(CUSTOMER)
    def place_order():
        """{items: [{book_id, type: BUY|RENT, quantity}]} -> 201 {order}"""
        order = service.place_order(g.user, json_body().get("items"))
        return {"order": order_json(order)}, 201

    @bp.get("/api/orders/mine")
    @require_login(CUSTOMER)
    def my_orders():
        return {"orders": [order_json(o) for o in service.orders_for(g.user)]}

    # ------------------------------------------- either (own orders only)
    @bp.get("/api/orders/<int:order_id>")
    @require_login()
    def get_order(order_id):
        return {"order": order_json(service.order_for(g.user, order_id))}

    @bp.get("/api/orders/<int:order_id>/bill")
    @require_login()
    def get_bill(order_id):
        return Response(service.bill_for(g.user, order_id), mimetype="text/html")

    # ---------------------------------------------------------- managers
    @bp.get("/api/orders")
    @require_login(MANAGER)
    def all_orders():
        """?status=PENDING|PAID&from=<iso>&to=<iso>"""
        start, end = _range()
        orders = service.all_orders(request.args.get("status") or None, start, end)
        return {"orders": [order_json(o) for o in orders]}

    @bp.patch("/api/orders/<int:order_id>/payment")
    @require_login(MANAGER)
    def set_payment(order_id):
        """{payment_status: PENDING|PAID} -> {order}"""
        status = str(json_body().get("payment_status", "")).upper()
        return {"order": order_json(service.set_payment_status(order_id, status))}

    @bp.post("/api/orders/<int:order_id>/items/<int:item_id>/return")
    @require_login(MANAGER)
    def return_rental(order_id, item_id):
        return {"order": order_json(service.return_rental(order_id, item_id))}

    @bp.get("/api/reports/sales")
    @require_login(MANAGER)
    def sales_report():
        start, end = _range()
        report = service.sales_report(start, end)
        report["orders"] = [order_json(o) for o in report["orders"]]
        return {"from": request.args.get("from"), "to": request.args.get("to"), **report}

    @bp.get("/api/reports/bestsellers")
    @require_login(MANAGER)
    def bestsellers():
        start, end = _range()
        try:
            limit = max(1, min(int(request.args.get("limit", 10)), 100))
        except ValueError:
            raise ApiError(400, "limit must be a number")
        return {"books": service.bestsellers(start, end, limit)}

    return bp


def _range() -> tuple[datetime | None, datetime | None]:
    """Reads ?from= and ?to= (ISO date or date-time; `to` is exclusive) as naive UTC."""
    return _parse_time("from"), _parse_time("to")


def _parse_time(name: str) -> datetime | None:
    raw = request.args.get(name)
    if not raw:
        return None
    try:
        value = datetime.fromisoformat(raw.replace("Z", "+00:00"))
    except ValueError:
        raise ApiError(400, f"{name} must be a date like 2026-11-19 or 2026-11-19T06:00:00Z")
    if value.tzinfo is not None:
        value = value.astimezone(timezone.utc).replace(tzinfo=None)
    return value
