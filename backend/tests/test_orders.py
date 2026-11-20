"""FR6-FR9, FR12, FR13, renting, N5 and the order-history / report extra credits."""
from datetime import date, datetime, timedelta
from pathlib import Path

from conftest import BOOKS, ORDERS


def place(client, *items):
    return client.post(f"{ORDERS}/api/orders", json={"items": list(items)})


def copies(client, book_id) -> int:
    return client.get(f"{BOOKS}/api/books/{book_id}").json()["book"]["copies"]


def test_buy_and_rent_in_one_order(customer, fresh_book):  # FR6, FR7, FR8
    resp = place(customer,
                 {"book_id": fresh_book["id"], "type": "BUY", "quantity": 1},
                 {"book_id": fresh_book["id"], "type": "RENT", "quantity": 1})
    assert resp.status_code == 201, resp.text
    order = resp.json()["order"]
    assert order["payment_status"] == "PENDING"
    assert order["total"] == 25.50  # 20.00 buy + 5.50 rent
    kinds = {i["type"]: i for i in order["items"]}
    assert kinds["BUY"]["due_date"] is None
    ordered_on = datetime.fromisoformat(order["created_at"]).date()  # UTC date
    assert kinds["RENT"]["due_date"] == (ordered_on + timedelta(days=30)).isoformat()
    assert copies(customer, fresh_book["id"]) == 1  # 3 - 2


def test_bill_is_saved_as_html(customer, fresh_book):  # FR8, FR9
    order = place(customer, {"book_id": fresh_book["id"], "type": "BUY", "quantity": 2}).json()["order"]
    resp = customer.get(f"{ORDERS}/api/orders/{order['id']}/bill")
    assert resp.status_code == 200
    assert resp.headers["Content-Type"].startswith("text/html")
    page = resp.text
    assert f"Order #{order['id']}" in page
    assert fresh_book["title"] in page
    assert "$40.00" in page
    saved = Path(__file__).parent.parent / "order_service" / "bills" / f"order-{order['id']}.html"
    assert saved.exists()


def test_not_enough_copies_takes_nothing(customer, fresh_book):
    resp = place(customer, {"book_id": fresh_book["id"], "type": "BUY", "quantity": 4})
    assert resp.status_code == 409
    assert copies(customer, fresh_book["id"]) == 3


def test_order_history_shows_only_own_orders(customer, fresh_book, manager):  # extra credit, N5
    from conftest import login, register
    other = login(*register())
    mine = place(customer, {"book_id": fresh_book["id"], "type": "BUY"}).json()["order"]

    history = customer.get(f"{ORDERS}/api/orders/mine").json()["orders"]
    assert [o["id"] for o in history] == [mine["id"]]
    assert other.get(f"{ORDERS}/api/orders/mine").json()["orders"] == []
    assert other.get(f"{ORDERS}/api/orders/{mine['id']}").status_code == 404
    assert other.get(f"{ORDERS}/api/orders/{mine['id']}/bill").status_code == 404


def test_manager_lists_and_marks_paid(customer, manager, fresh_book):  # FR12, FR13
    order = place(customer, {"book_id": fresh_book["id"], "type": "BUY"}).json()["order"]

    listed = manager.get(f"{ORDERS}/api/orders").json()["orders"]
    assert order["id"] in [o["id"] for o in listed]

    resp = manager.patch(f"{ORDERS}/api/orders/{order['id']}/payment", json={"payment_status": "PAID"})
    assert resp.status_code == 200
    assert resp.json()["order"]["payment_status"] == "PAID"
    assert resp.json()["order"]["paid_at"]

    pending = manager.get(f"{ORDERS}/api/orders", params={"status": "PENDING"}).json()["orders"]
    assert order["id"] not in [o["id"] for o in pending]


def test_return_rental_restocks(customer, manager, fresh_book):  # renting
    order = place(customer, {"book_id": fresh_book["id"], "type": "RENT"}).json()["order"]
    item = order["items"][0]
    assert copies(manager, fresh_book["id"]) == 2

    url = f"{ORDERS}/api/orders/{order['id']}/items/{item['id']}/return"
    resp = manager.post(url)
    assert resp.status_code == 200
    assert resp.json()["order"]["items"][0]["returned_at"]
    assert copies(manager, fresh_book["id"]) == 3
    assert manager.post(url).status_code == 409  # can't return twice


def test_roles_are_kept_apart(customer, manager, fresh_book):  # N5
    assert place(manager, {"book_id": fresh_book["id"], "type": "BUY"}).status_code == 403
    assert customer.get(f"{ORDERS}/api/orders").status_code == 403
    order = place(customer, {"book_id": fresh_book["id"], "type": "BUY"}).json()["order"]
    assert customer.patch(f"{ORDERS}/api/orders/{order['id']}/payment",
                          json={"payment_status": "PAID"}).status_code == 403


def test_sales_report_and_bestsellers(customer, manager, fresh_book):  # extra credit: reports
    place(customer, {"book_id": fresh_book["id"], "type": "BUY", "quantity": 2},
          {"book_id": fresh_book["id"], "type": "RENT"})
    today = date.today()
    params = {"from": (today - timedelta(days=1)).isoformat(), "to": (today + timedelta(days=2)).isoformat()}

    report = manager.get(f"{ORDERS}/api/reports/sales", params=params).json()
    assert report["order_count"] >= 1
    assert report["revenue"] >= 45.50

    best = manager.get(f"{ORDERS}/api/reports/bestsellers", params={**params, "limit": 100}).json()["books"]
    row = next(b for b in best if b["book_id"] == fresh_book["id"])
    assert row["units_bought"] == 2 and row["units_rented"] == 1 and row["revenue"] == 45.50


def test_empty_order_is_refused(customer):
    assert customer.post(f"{ORDERS}/api/orders", json={"items": []}).status_code == 400
