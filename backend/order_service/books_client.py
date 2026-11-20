"""Talks to the book service's stock endpoints."""
import requests

from common import config
from common.errors import ApiError


class BooksClient:
    def __init__(self, base_url: str | None = None):
        self.base_url = base_url or config.BOOK_SERVICE_URL
        self.http = requests.Session()
        self.http.headers["X-Service-Key"] = config.SERVICE_KEY

    def reserve(self, quantities: dict[str, int]) -> dict[str, dict]:
        """Takes copies out of stock. Returns {book_id: book}. Passes 404/409 straight through."""
        resp = self._post("/internal/stock/reserve", quantities)
        if resp.status_code in (400, 404, 409):
            raise ApiError(resp.status_code, resp.json().get("error", "Could not reserve books"))
        if resp.status_code != 200:
            raise ApiError(502, "Book service gave an unexpected reply")
        return {b["id"]: b for b in resp.json()["books"]}

    def release(self, quantities: dict[str, int]) -> None:
        if not quantities:
            return
        resp = self._post("/internal/stock/release", quantities)
        if resp.status_code != 200:
            raise ApiError(502, "Book service could not put copies back in stock")

    def _post(self, path: str, quantities: dict[str, int]):
        body = {"items": [{"book_id": b, "quantity": q} for b, q in quantities.items()]}
        try:
            return self.http.post(self.base_url + path, json=body, timeout=10)
        except requests.RequestException:
            raise ApiError(503, "Book service is unavailable")
