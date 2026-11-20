"""Run with:  python -m order_service   (from the backend folder)"""
from common.config import env

from .app import create_app

if __name__ == "__main__":
    create_app().run(port=int(env("ORDER_SERVICE_PORT", "5002")), threaded=True)
