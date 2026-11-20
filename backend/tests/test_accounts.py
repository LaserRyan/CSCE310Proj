"""FR1, FR2, FR3, FR11, N3, N4 and the profile extra credit."""
import requests

from conftest import BOOKS, MANAGER, USERS, login, register


def test_register_then_login(customer):  # FR1, FR3
    assert customer.user["role"] == "CUSTOMER"
    assert "password_hash" not in customer.user


def test_register_rejects_bad_email():  # FR3
    resp = requests.post(f"{USERS}/api/auth/register", json={
        "username": "someone_new", "password": "secret123", "email": "not-an-email"})
    assert resp.status_code == 400


def test_register_rejects_duplicate_username():
    username, _ = register()
    resp = requests.post(f"{USERS}/api/auth/register", json={
        "username": username, "password": "secret123", "email": "other@example.test"})
    assert resp.status_code == 409


def test_wrong_password_is_refused():  # FR1
    username, _ = register()
    resp = requests.post(f"{USERS}/api/auth/login", json={"username": username, "password": "wrong!!"})
    assert resp.status_code == 401


def test_session_lasts_until_logout(customer):  # FR2
    assert customer.get(f"{USERS}/api/users/me").status_code == 200
    assert customer.get(f"{BOOKS}/api/books").status_code == 200
    assert customer.post(f"{USERS}/api/auth/logout").status_code == 200
    # The same token is now refused by every service.
    assert customer.get(f"{USERS}/api/users/me").status_code == 401
    assert customer.get(f"{BOOKS}/api/books").status_code == 401


def test_no_token_is_refused():  # N4
    assert requests.get(f"{BOOKS}/api/books?q=a").status_code == 401
    assert requests.get(f"{USERS}/api/users/me").status_code == 401


def test_customer_cannot_use_manager_app():  # FR11
    username, password = register()
    resp = requests.post(f"{USERS}/api/auth/login",
                         json={"username": username, "password": password, "role": "MANAGER"})
    assert resp.status_code == 403


def test_manager_cannot_use_customer_app():  # FR11, N5
    resp = requests.post(f"{USERS}/api/auth/login",
                         json={"username": MANAGER[0], "password": MANAGER[1], "role": "CUSTOMER"})
    assert resp.status_code == 403


def test_update_profile_and_password(customer):  # extra credit: profiles
    resp = customer.put(f"{USERS}/api/users/me", json={"full_name": "New Name"})
    assert resp.status_code == 200
    assert resp.json()["user"]["full_name"] == "New Name"

    resp = customer.put(f"{USERS}/api/users/me/password",
                        json={"current_password": "secret123", "new_password": "secret456"})
    assert resp.status_code == 200
    login(customer.user["username"], "secret456")
