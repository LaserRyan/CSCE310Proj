import logging

from flask import Flask
from pymongo import MongoClient

from common.config import env
from common.errors import register_error_handlers

from .repository import BookRepository
from .routes import create_blueprint
from .seed import seed_if_empty


def create_app(mongo_url: str | None = None, db_name: str | None = None) -> Flask:
    logging.basicConfig(level=logging.INFO)
    # tz_aware: hand back UTC datetimes with their time zone, so JSON times end in +00:00.
    client = MongoClient(mongo_url or env("MONGO_URL", "mongodb://localhost:27018"), tz_aware=True)
    repo = BookRepository(client[db_name or env("MONGO_DB", "bookstore")])
    repo.ensure_indexes()
    seed_if_empty(repo)

    app = Flask(__name__)
    app.json.sort_keys = False
    register_error_handlers(app)
    app.register_blueprint(create_blueprint(repo))
    return app
