"""Emails the bill to the customer (FR10).

Needs SMTP settings in the environment. Without SMTP_HOST it only logs that it
skipped, so orders still work on a machine with no email set up. Sending runs
on a background thread so placing an order never waits on the mail server.
"""
import logging
import smtplib
import threading
from email.message import EmailMessage

from common.config import env

log = logging.getLogger(__name__)


class Notifier:
    def __init__(self, host: str, port: int, user: str, password: str, sender: str):
        self.host, self.port, self.user, self.password, self.sender = host, port, user, password, sender

    @classmethod
    def from_env(cls) -> "Notifier":
        user = env("SMTP_USER", "")
        return cls(
            host=env("SMTP_HOST", ""),
            port=int(env("SMTP_PORT", "587")),
            user=user,
            password=env("SMTP_PASSWORD", ""),
            sender=env("SMTP_FROM", user or "bookstore@localhost"),
        )

    @property
    def enabled(self) -> bool:
        return bool(self.host)

    def send_bill_later(self, to: str, order_id: int, page: str) -> None:
        if not self.enabled:
            log.info("Email not set up (no SMTP_HOST); bill for order %s not emailed", order_id)
            return
        threading.Thread(target=self._send, args=(to, order_id, page), daemon=True).start()

    def _send(self, to: str, order_id: int, page: str) -> None:
        msg = EmailMessage()
        msg["Subject"] = f"Your bookstore bill - Order #{order_id}"
        msg["From"] = self.sender
        msg["To"] = to
        msg.set_content(f"Thank you for your order #{order_id}. Your bill is attached as HTML.")
        msg.add_alternative(page, subtype="html")
        try:
            with smtplib.SMTP(self.host, self.port, timeout=20) as smtp:
                smtp.starttls()
                if self.user:
                    smtp.login(self.user, self.password)
                smtp.send_message(msg)
            log.info("Emailed bill for order %s to %s", order_id, to)
        except Exception:
            log.exception("Could not email bill for order %s", order_id)
