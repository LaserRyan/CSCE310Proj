"""End-to-end tests. They talk to the three running services over HTTP, with
real MySQL and MongoDB behind them. Start everything first (see README), then:

    cd backend
    ..\\.venv\\Scripts\\python -m pytest tests -v
"""
import uuid

import pytest
import requests

import cleanup

USERS = "http://127.0.0.1:8081"
BOOKS = "http://127.0.0.1:5001"
ORDERS = "http://127.0.0.1:5002"
MANAGER = ("manager", "manager123")


class Client:
    """A logged-in user, with helpers that add the Bearer token."""

    def __init__(self, token: str, user: dict):
        self.token = token
        self.user = user
        self.headers = {"Authorization": f"Bearer {token}"}

    def get(self, url, **kw):
        return requests.get(url, headers=self.headers, timeout=10, **kw)

    def post(self, url, **kw):
        return requests.post(url, headers=self.headers, timeout=10, **kw)

    def put(self, url, **kw):
        return requests.put(url, headers=self.headers, timeout=10, **kw)

    def patch(self, url, **kw):
        return requests.patch(url, headers=self.headers, timeout=10, **kw)


def login(username: str, password: str, role: str | None = None) -> Client:
    body = {"username": username, "password": password}
    if role:
        body["role"] = role
    resp = requests.post(f"{USERS}/api/auth/login", json=body, timeout=10)
    assert resp.status_code == 200, resp.text
    data = resp.json()
    return Client(data["token"], data["user"])


def register() -> tuple[str, str]:
    username = f"{cleanup.USER_PREFIX}{uuid.uuid4().hex[:8]}"
    password = "secret123"
    resp = requests.post(f"{USERS}/api/auth/register", json={
        "username": username, "password": password, "email": f"{username}@example.test",
    }, timeout=10)
    assert resp.status_code == 201, resp.text
    return username, password


@pytest.fixture(scope="session", autouse=True)
def services_running():
    for url in (USERS, BOOKS, ORDERS):
        try:
            requests.get(f"{url}/health", timeout=3)
        except requests.ConnectionError:
            pytest.exit(f"{url} is not running. Start the services first.")
    yield
    cleanup.clean()


@pytest.fixture
def customer() -> Client:
    return login(*register(), role="CUSTOMER")


@pytest.fixture
def manager() -> Client:
    return login(*MANAGER, role="MANAGER")


@pytest.fixture
def fresh_book(manager) -> dict:
    """A new book with known stock, so tests don't depend on each other."""
    resp = manager.post(f"{BOOKS}/api/books", json={
        "title": f"{cleanup.BOOK_PREFIX}{uuid.uuid4().hex[:6]}",
        "author": "Test Author",
        "price": 20.00,
        "rent_price": 5.50,
        "copies": 3,
    })
    assert resp.status_code == 201, resp.text
    return resp.json()["book"]
