"""Book service HTTP endpoints."""
from flask import Blueprint, g, jsonify, request

from common.auth import CUSTOMER, MANAGER, require_login, require_service_key
from common.errors import ApiError, json_body

from . import validation
from .repository import BookRepository, book_json, review_json


def create_blueprint(repo: BookRepository) -> Blueprint:
    bp = Blueprint("books", __name__)

    @bp.get("/health")
    def health():
        return {"status": "ok"}

    # ------------------------------------------------- catalog (FR4, FR5)
    @bp.get("/api/books")
    @require_login()
    def search():
        """GET /api/books?q=<keyword>  -> {books: [...]}. Matches title or author."""
        books = repo.search(request.args.get("q", ""))
        return {"books": [book_json(b) for b in books]}

    @bp.get("/api/books/<book_id>")
    @require_login()
    def get_book(book_id):
        return {"book": book_json(repo.get(book_id))}

    # ---------------------------------------------- manager edits (FR14)
    @bp.post("/api/books")
    @require_login(MANAGER)
    def add_book():
        doc = repo.insert(validation.book_fields(json_body(), partial=False))
        return {"book": book_json(doc)}, 201

    @bp.put("/api/books/<book_id>")
    @require_login(MANAGER)
    def update_book(book_id):
        doc = repo.update(book_id, validation.book_fields(json_body(), partial=True))
        return {"book": book_json(doc)}

    # --------------------------------------------- reviews (extra credit)
    @bp.get("/api/books/<book_id>/reviews")
    @require_login()
    def list_reviews(book_id):
        return {"reviews": [review_json(r) for r in repo.reviews_for(book_id)]}

    @bp.post("/api/books/<book_id>/reviews")
    @require_login(CUSTOMER)
    def write_review(book_id):
        body = json_body()
        rating = validation.rating(body.get("rating"))
        comment = str(body.get("comment") or "").strip()
        if len(comment) > 2000:
            raise ApiError(400, "comment must be at most 2000 characters")
        review = repo.upsert_review(book_id, g.user, rating, comment)
        return {"review": review_json(review), "book": book_json(repo.get(book_id))}, 201

    # ------------------------------- stock, called only by order service
    @bp.post("/internal/stock/reserve")
    @require_service_key
    def reserve():
        """{items: [{book_id, quantity}]} -> {books: [...]} with prices, or 409 if out of stock."""
        books = repo.reserve(_quantities(json_body()))
        return {"books": [book_json(b) for b in books]}

    @bp.post("/internal/stock/release")
    @require_service_key
    def release():
        repo.release(_quantities(json_body()))
        return {"message": "Released"}

    return bp


def _quantities(body: dict) -> dict[str, int]:
    """Sums quantities per book from {items: [{book_id, quantity}]}."""
    items = body.get("items")
    if not isinstance(items, list) or not items:
        raise ApiError(400, "items must be a non-empty list")
    totals: dict[str, int] = {}
    for item in items:
        if not isinstance(item, dict) or not isinstance(item.get("book_id"), str):
            raise ApiError(400, "Each item needs a book_id")
        qty = item.get("quantity", 1)
        if not isinstance(qty, int) or isinstance(qty, bool) or qty < 1:
            raise ApiError(400, "quantity must be a whole number of at least 1")
        totals[item["book_id"]] = totals.get(item["book_id"], 0) + qty
    return totals
