"""All MongoDB access for books and reviews.

Collections:
  books    {_id, title, author, isbn, genre, description, published_year,
            price, rent_price, copies, avg_rating, review_count, created_at}
  reviews  {_id, book_id, user_id, username, rating, comment, created_at}
           one review per (book_id, user_id); writing again replaces it.
"""
import re
from datetime import datetime, timezone

from bson import ObjectId
from bson.errors import InvalidId
from pymongo import ASCENDING, DESCENDING, ReturnDocument
from pymongo.database import Database

from common.errors import ApiError

MAX_RESULTS = 200


def to_object_id(book_id: str) -> ObjectId:
    try:
        return ObjectId(book_id)
    except (InvalidId, TypeError):
        raise ApiError(404, "Book not found")


def book_json(doc: dict) -> dict:
    return {
        "id": str(doc["_id"]),
        "title": doc["title"],
        "author": doc["author"],
        "isbn": doc.get("isbn"),
        "genre": doc.get("genre"),
        "description": doc.get("description"),
        "published_year": doc.get("published_year"),
        "price": doc["price"],
        "rent_price": doc["rent_price"],
        "copies": doc["copies"],
        "avg_rating": doc.get("avg_rating"),
        "review_count": doc.get("review_count", 0),
    }


def review_json(doc: dict) -> dict:
    return {
        "id": str(doc["_id"]),
        "book_id": str(doc["book_id"]),
        "user_id": doc["user_id"],
        "username": doc["username"],
        "rating": doc["rating"],
        "comment": doc.get("comment", ""),
        "created_at": doc["created_at"].isoformat(),
    }


class BookRepository:
    def __init__(self, db: Database):
        self.books = db["books"]
        self.reviews = db["reviews"]

    def ensure_indexes(self) -> None:
        self.books.create_index([("title", ASCENDING)])
        self.books.create_index([("author", ASCENDING)])
        self.reviews.create_index([("book_id", ASCENDING), ("user_id", ASCENDING)], unique=True)

    # ------------------------------------------------------------ books
    def count(self) -> int:
        return self.books.count_documents({})

    def search(self, keyword: str) -> list[dict]:
        """Books whose title or author contains the keyword, ignoring case. Blank keyword = all."""
        keyword = (keyword or "").strip()
        query = {}
        if keyword:
            pattern = re.compile(re.escape(keyword), re.IGNORECASE)
            query = {"$or": [{"title": pattern}, {"author": pattern}]}
        return list(self.books.find(query).sort("title", ASCENDING).limit(MAX_RESULTS))

    def get(self, book_id: str) -> dict:
        doc = self.books.find_one({"_id": to_object_id(book_id)})
        if doc is None:
            raise ApiError(404, "Book not found")
        return doc

    def insert(self, fields: dict) -> dict:
        doc = {**fields, "avg_rating": None, "review_count": 0, "created_at": _now()}
        doc["_id"] = self.books.insert_one(doc).inserted_id
        return doc

    def insert_many(self, docs: list[dict]) -> None:
        now = _now()
        self.books.insert_many([{**d, "avg_rating": None, "review_count": 0, "created_at": now} for d in docs])

    def update(self, book_id: str, fields: dict) -> dict:
        doc = self.books.find_one_and_update(
            {"_id": to_object_id(book_id)},
            {"$set": fields},
            return_document=ReturnDocument.AFTER,
        )
        if doc is None:
            raise ApiError(404, "Book not found")
        return doc

    # ------------------------------------------------------------ stock
    def reserve(self, wanted: dict[str, int]) -> list[dict]:
        """Takes copies out of stock for every book in `wanted` ({book_id: quantity}), or none.

        Each decrement only succeeds while enough copies are left, so two
        customers can never both get the last copy. If any book fails, the
        ones already taken are put back.
        """
        taken: dict[str, int] = {}
        result = []
        try:
            for book_id, qty in wanted.items():
                doc = self.books.find_one_and_update(
                    {"_id": to_object_id(book_id), "copies": {"$gte": qty}},
                    {"$inc": {"copies": -qty}},
                    return_document=ReturnDocument.AFTER,
                )
                if doc is None:
                    current = self.get(book_id)  # raises 404 if the book doesn't exist
                    raise ApiError(
                        409,
                        f'Not enough copies of "{current["title"]}" '
                        f'({current["copies"]} left, {qty} requested)',
                    )
                taken[book_id] = qty
                result.append(doc)
        except Exception:
            self.release(taken)
            raise
        return result

    def release(self, returned: dict[str, int]) -> None:
        """Puts copies back in stock ({book_id: quantity})."""
        for book_id, qty in returned.items():
            self.books.update_one({"_id": to_object_id(book_id)}, {"$inc": {"copies": qty}})

    # ---------------------------------------------------------- reviews
    def reviews_for(self, book_id: str) -> list[dict]:
        oid = self.get(book_id)["_id"]
        return list(self.reviews.find({"book_id": oid}).sort("created_at", DESCENDING))

    def upsert_review(self, book_id: str, user: dict, rating: int, comment: str) -> dict:
        oid = self.get(book_id)["_id"]
        review = self.reviews.find_one_and_update(
            {"book_id": oid, "user_id": user["id"]},
            {"$set": {
                "username": user["username"],
                "rating": rating,
                "comment": comment,
                "created_at": _now(),
            }},
            upsert=True,
            return_document=ReturnDocument.AFTER,
        )
        self._refresh_rating(oid)
        return review

    def _refresh_rating(self, oid: ObjectId) -> None:
        stats = list(self.reviews.aggregate([
            {"$match": {"book_id": oid}},
            {"$group": {"_id": None, "avg": {"$avg": "$rating"}, "n": {"$sum": 1}}},
        ]))
        avg, n = (round(stats[0]["avg"], 2), stats[0]["n"]) if stats else (None, 0)
        self.books.update_one({"_id": oid}, {"$set": {"avg_rating": avg, "review_count": n}})


def _now() -> datetime:
    return datetime.now(timezone.utc)
