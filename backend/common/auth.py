"""Login checks for Flask routes.

The user service is the only place that knows whether a token is valid (it
owns the sessions table). So each protected request asks it, via
GET /api/users/me, who the token belongs to. A logged-out token stops
working everywhere at once.
"""
import functools
import hmac

import requests
from flask import g, request

from . import config
from .errors import ApiError

CUSTOMER = "CUSTOMER"
MANAGER = "MANAGER"

_http = requests.Session()


def _lookup_user(auth_header: str) -> dict:
    try:
        resp = _http.get(
            f"{config.USER_SERVICE_URL}/api/users/me",
            headers={"Authorization": auth_header},
            timeout=5,
        )
    except requests.RequestException:
        raise ApiError(503, "User service is unavailable")
    if resp.status_code == 401:
        raise ApiError(401, resp.json().get("error", "Please log in again"))
    if resp.status_code != 200:
        raise ApiError(502, "User service gave an unexpected reply")
    return resp.json()["user"]


def require_login(*roles: str):
    """Route decorator. Puts the caller in g.user; refuses roles not listed (if any are)."""

    def decorator(view):
        @functools.wraps(view)
        def wrapper(*args, **kwargs):
            header = request.headers.get("Authorization", "")
            if not header.lower().startswith("bearer ") or len(header) <= 7:
                raise ApiError(401, "Missing login token")
            user = _lookup_user(header)
            if roles and user["role"] not in roles:
                who = " or ".join(r.lower() + "s" for r in roles)
                raise ApiError(403, f"Only {who} can do this")
            g.user = user
            return view(*args, **kwargs)

        return wrapper

    return decorator


def require_service_key(view):
    """Route decorator for service-to-service endpoints."""

    @functools.wraps(view)
    def wrapper(*args, **kwargs):
        key = request.headers.get("X-Service-Key", "")
        if not hmac.compare_digest(key, config.SERVICE_KEY):
            raise ApiError(403, "Service key required")
        return view(*args, **kwargs)

    return wrapper
