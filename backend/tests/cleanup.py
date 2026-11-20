"""Deletes everything the end-to-end tests create, straight from the databases.

Test users are named e2e_<random> and test books are titled "E2E Test Book <random>",
so real data is never touched. Runs automatically after the test session; can also
be run by hand:  python tests/cleanup.py   (from the backend folder)
"""
from pathlib import Path

import pymysql
from pymongo import MongoClient

from common.config import env

USER_PREFIX = "e2e_"
BOOK_PREFIX = "E2E Test Book "
BILLS = Path(__file__).parent.parent / "order_service" / "bills"


def _mysql(database: str):
    return pymysql.connect(
        host=env("MYSQL_HOST", "localhost"), port=int(env("MYSQL_PORT", "3307")),
        user=env("MYSQL_USER", "bookstore"), password=env("MYSQL_PASSWORD", "bookstore"),
        database=database, autocommit=True)


def clean() -> None:
    mongo = MongoClient(env("MONGO_URL", "mongodb://localhost:27018"))[env("MONGO_DB", "bookstore")]
    book_ids = [str(b["_id"]) for b in mongo.books.find({"title": {"$regex": f"^{BOOK_PREFIX}"}}, {"_id": 1})]

    with _mysql("bookstore_orders") as conn, conn.cursor() as cur:
        cur.execute("SELECT id FROM orders WHERE customer_username LIKE %s", (USER_PREFIX + "%",))
        order_ids = [row[0] for row in cur.fetchall()]
        if book_ids:
            marks = ", ".join(["%s"] * len(book_ids))
            cur.execute(f"SELECT DISTINCT order_id FROM order_items WHERE book_id IN ({marks})", book_ids)
            order_ids += [row[0] for row in cur.fetchall()]
        order_ids = sorted(set(order_ids))
        if order_ids:
            marks = ", ".join(["%s"] * len(order_ids))
            cur.execute(f"DELETE FROM orders WHERE id IN ({marks})", order_ids)  # items cascade
    for order_id in order_ids:
        (BILLS / f"order-{order_id}.html").unlink(missing_ok=True)

    oids = [b["_id"] for b in mongo.books.find({"title": {"$regex": f"^{BOOK_PREFIX}"}}, {"_id": 1})]
    mongo.reviews.delete_many({"$or": [{"book_id": {"$in": oids}}, {"username": {"$regex": f"^{USER_PREFIX}"}}]})
    mongo.books.delete_many({"_id": {"$in": oids}})

    with _mysql("bookstore_users") as conn, conn.cursor() as cur:
        cur.execute("DELETE FROM users WHERE username LIKE %s", (USER_PREFIX + "%",))  # sessions cascade


if __name__ == "__main__":
    clean()
    print("Removed end-to-end test data")
