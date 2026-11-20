"""Settings from environment variables. Defaults match docker-compose.yml."""
import os


def env(name: str, default: str) -> str:
    value = os.environ.get(name)
    return value if value else default


USER_SERVICE_URL = env("USER_SERVICE_URL", "http://127.0.0.1:8081")
BOOK_SERVICE_URL = env("BOOK_SERVICE_URL", "http://127.0.0.1:5001")
ORDER_SERVICE_URL = env("ORDER_SERVICE_URL", "http://127.0.0.1:5002")

# Shared secret the order service sends when it asks the book service to
# take books out of stock or put them back. Customers never see it.
SERVICE_KEY = env("SERVICE_KEY", "dev-service-key-change-me")
