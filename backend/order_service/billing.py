"""Turns an order into an HTML bill and saves it to disk (FR8, FR9)."""
import html
from datetime import datetime, timezone
from pathlib import Path

STYLE = """
body { font-family: Georgia, 'Times New Roman', serif; color: #222; max-width: 760px; margin: 32px auto; padding: 0 16px; }
header { display: flex; justify-content: space-between; align-items: flex-start; border-bottom: 2px solid #222; padding-bottom: 12px; }
h1 { margin: 0; font-size: 28px; letter-spacing: 1px; }
.meta { text-align: right; font-size: 14px; line-height: 1.6; }
.to { margin: 20px 0; font-size: 14px; line-height: 1.6; }
table { width: 100%; border-collapse: collapse; font-size: 14px; }
th { text-align: left; border-bottom: 1px solid #222; padding: 8px 6px; }
td { border-bottom: 1px solid #ddd; padding: 8px 6px; vertical-align: top; }
td.num, th.num { text-align: right; white-space: nowrap; }
.tag { font-size: 11px; padding: 1px 6px; border: 1px solid #888; border-radius: 3px; }
tfoot td { border: none; font-weight: bold; font-size: 16px; padding-top: 14px; }
.status { display: inline-block; padding: 2px 10px; border: 2px solid; font-weight: bold; }
.PAID { color: #1d6b2f; } .PENDING { color: #a35a00; }
footer { margin-top: 32px; font-size: 12px; color: #666; }
@media print { body { margin: 0; } }
"""


def _money(value) -> str:
    return f"${float(value):,.2f}"


def _local(value: datetime | None) -> str:
    if value is None:
        return ""
    return value.replace(tzinfo=timezone.utc).astimezone().strftime("%Y-%m-%d %H:%M")


def render(order: dict) -> str:
    """The bill as a complete, printable HTML page. `order` is a repository row with items."""
    e = html.escape
    rows = []
    for item in order["items"]:
        kind = "Rent" if item["type"] == "RENT" else "Buy"
        note = ""
        if item["type"] == "RENT":
            note = f"<br><small>Due back {item['due_date']}"
            if item["returned_at"]:
                note += f" &middot; returned {_local(item['returned_at'])}"
            note += "</small>"
        rows.append(
            f"<tr><td>{e(item['title'])}<br><small>{e(item['author'])}</small>{note}</td>"
            f"<td><span class='tag'>{kind}</span></td>"
            f"<td class='num'>{item['quantity']}</td>"
            f"<td class='num'>{_money(item['unit_price'])}</td>"
            f"<td class='num'>{_money(item['line_total'])}</td></tr>"
        )
    status = order["payment_status"]
    paid = f" on {_local(order['paid_at'])}" if order["paid_at"] else ""
    return f"""<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8">
<title>Bill - Order #{order['id']}</title>
<style>{STYLE}</style></head>
<body>
<header>
  <div><h1>BOOKSTORE</h1><div>Bill / Invoice</div></div>
  <div class="meta">
    <div><b>Order #{order['id']}</b></div>
    <div>Date: {_local(order['created_at'])}</div>
    <div>Payment: <span class="status {status}">{status}</span>{paid}</div>
  </div>
</header>
<div class="to"><b>Bill to:</b><br>{e(order['customer_username'])}<br>{e(order['customer_email'])}</div>
<table>
  <thead><tr><th>Item</th><th>Type</th><th class="num">Qty</th><th class="num">Price</th><th class="num">Amount</th></tr></thead>
  <tbody>{''.join(rows)}</tbody>
  <tfoot><tr><td colspan="4" class="num">Total due</td><td class="num">{_money(order['total'])}</td></tr></tfoot>
</table>
<footer>Rented books are due back by the date shown. Thank you for your order.</footer>
</body></html>
"""


class BillWriter:
    def __init__(self, directory: Path):
        self.directory = directory
        self.directory.mkdir(parents=True, exist_ok=True)

    def save(self, order: dict) -> tuple[str, str]:
        """Writes bills/order-<id>.html. Returns (path, html)."""
        page = render(order)
        path = self.directory / f"order-{order['id']}.html"
        path.write_text(page, encoding="utf-8")
        return str(path.resolve()), page
