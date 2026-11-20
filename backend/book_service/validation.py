"""Checks the fields a manager sends when adding or editing a book."""
from common.errors import ApiError

TEXT_FIELDS = {"title": 255, "author": 255, "isbn": 20, "genre": 50, "description": 2000}
REQUIRED = ("title", "author", "price", "rent_price", "copies")


def book_fields(body: dict, partial: bool) -> dict:
    """The cleaned-up fields from `body`. With partial=False every required field must be there."""
    if not partial:
        missing = [f for f in REQUIRED if body.get(f) in (None, "")]
        if missing:
            raise ApiError(400, "Missing: " + ", ".join(missing))

    out: dict = {}
    for name, max_len in TEXT_FIELDS.items():
        if name in body:
            value = body[name]
            value = "" if value is None else str(value).strip()
            if name in ("title", "author") and not value:
                raise ApiError(400, f"{name} cannot be empty")
            if len(value) > max_len:
                raise ApiError(400, f"{name} must be at most {max_len} characters")
            out[name] = value or None
    for name in ("price", "rent_price"):
        if name in body:
            out[name] = _money(name, body[name])
    if "copies" in body:
        out["copies"] = _whole(body["copies"], "copies", 0, 1_000_000)
    if "published_year" in body and body["published_year"] not in (None, ""):
        out["published_year"] = _whole(body["published_year"], "published_year", 0, 3000)
    if not out:
        raise ApiError(400, "Nothing to update")
    return out


def rating(value) -> int:
    return _whole(value, "rating", 1, 5)


def _money(name: str, value) -> float:
    try:
        amount = round(float(value), 2)
    except (TypeError, ValueError):
        raise ApiError(400, f"{name} must be a number")
    if amount < 0 or amount > 100_000:
        raise ApiError(400, f"{name} must be between 0 and 100000")
    return amount


def _whole(value, name: str, low: int, high: int) -> int:
    if isinstance(value, bool):
        raise ApiError(400, f"{name} must be a whole number")
    try:
        number = int(value)
    except (TypeError, ValueError):
        raise ApiError(400, f"{name} must be a whole number")
    if str(number) != str(value).strip() and float(value) != number:
        raise ApiError(400, f"{name} must be a whole number")
    if not low <= number <= high:
        raise ApiError(400, f"{name} must be between {low} and {high}")
    return number
