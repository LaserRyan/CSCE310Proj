"""FR4, FR5, FR14 and the reviews extra credit."""
from conftest import BOOKS


def test_search_matches_title(customer):  # FR4, FR5
    books = customer.get(f"{BOOKS}/api/books", params={"q": "gatsby"}).json()["books"]
    assert [b["title"] for b in books] == ["The Great Gatsby"]
    assert {"title", "author", "price"} <= books[0].keys()


def test_search_matches_author(customer):  # FR4
    titles = {b["title"] for b in customer.get(f"{BOOKS}/api/books", params={"q": "austen"}).json()["books"]}
    assert {"Pride and Prejudice", "Emma", "Sense and Sensibility"} <= titles


def test_search_answers_within_500ms(customer):  # N1
    import time
    customer.get(f"{BOOKS}/api/books", params={"q": "the"})  # warm up the connection
    start = time.perf_counter()
    resp = customer.get(f"{BOOKS}/api/books", params={"q": "the"})
    assert resp.status_code == 200
    assert time.perf_counter() - start < 0.5


def test_search_with_regex_characters_is_literal(customer):
    resp = customer.get(f"{BOOKS}/api/books", params={"q": ".*("})
    assert resp.status_code == 200
    assert resp.json()["books"] == []


def test_manager_adds_and_edits_book(manager, fresh_book):  # FR14
    resp = manager.put(f"{BOOKS}/api/books/{fresh_book['id']}", json={"copies": 42, "price": 25})
    assert resp.status_code == 200
    book = resp.json()["book"]
    assert book["copies"] == 42 and book["price"] == 25.0


def test_add_book_requires_fields(manager):  # FR14
    resp = manager.post(f"{BOOKS}/api/books", json={"title": "No author"})
    assert resp.status_code == 400


def test_customer_cannot_add_books(customer):  # N5
    resp = customer.post(f"{BOOKS}/api/books", json={
        "title": "X", "author": "Y", "price": 1, "rent_price": 1, "copies": 1})
    assert resp.status_code == 403


def test_review_updates_rating(customer, fresh_book):  # extra credit: reviews
    url = f"{BOOKS}/api/books/{fresh_book['id']}/reviews"
    resp = customer.post(url, json={"rating": 4, "comment": "Good"})
    assert resp.status_code == 201
    assert resp.json()["book"]["avg_rating"] == 4.0

    # Writing again replaces the earlier review instead of adding a second one.
    customer.post(url, json={"rating": 2, "comment": "Changed my mind"})
    reviews = customer.get(url).json()["reviews"]
    assert len(reviews) == 1 and reviews[0]["rating"] == 2
    # Times must carry their time zone, or the desktop app can't read them.
    assert reviews[0]["created_at"].endswith("+00:00")


def test_review_rating_must_be_1_to_5(customer, fresh_book):
    resp = customer.post(f"{BOOKS}/api/books/{fresh_book['id']}/reviews", json={"rating": 9})
    assert resp.status_code == 400


def test_stock_endpoints_need_service_key(customer, fresh_book):
    resp = customer.post(f"{BOOKS}/internal/stock/reserve",
                         json={"items": [{"book_id": fresh_book["id"], "quantity": 1}]})
    assert resp.status_code == 403
