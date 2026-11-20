import logging
from pathlib import Path

from flask import Flask

from common.config import env
from common.errors import register_error_handlers

from .billing import BillWriter
from .books_client import BooksClient
from .notifier import Notifier
from .orders import OrderService
from .repository import Database, OrderRepository
from .routes import create_blueprint


def create_app() -> Flask:
    logging.basicConfig(level=logging.INFO)
    db = Database(
        host=env("MYSQL_HOST", "localhost"),
        port=int(env("MYSQL_PORT", "3307")),
        user=env("MYSQL_USER", "bookstore"),
        password=env("MYSQL_PASSWORD", "bookstore"),
        database=env("ORDER_DB", "bookstore_orders"),
    )
    bills_dir = Path(env("BILLS_DIR", str(Path(__file__).parent / "bills")))
    service = OrderService(
        repo=OrderRepository(db),
        books=BooksClient(),
        bills=BillWriter(bills_dir),
        notifier=Notifier.from_env(),
        rental_days=int(env("RENTAL_DAYS", "30")),
    )

    app = Flask(__name__)
    app.json.sort_keys = False
    register_error_handlers(app)
    app.register_blueprint(create_blueprint(service))
    return app
