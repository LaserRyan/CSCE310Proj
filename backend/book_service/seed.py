"""Starting catalog, loaded the first time the book service sees an empty database.

Reset to this list any time with:  python -m book_service.seed --reset
"""
import logging
import sys

log = logging.getLogger(__name__)

# (title, author, genre, year, price, rent_price, copies)
BOOKS = [
    ("Pride and Prejudice", "Jane Austen", "Classic", 1813, 12.99, 3.99, 8),
    ("Emma", "Jane Austen", "Classic", 1815, 11.50, 3.50, 5),
    ("Sense and Sensibility", "Jane Austen", "Classic", 1811, 10.99, 3.25, 4),
    ("1984", "George Orwell", "Dystopian", 1949, 14.99, 4.50, 10),
    ("Animal Farm", "George Orwell", "Satire", 1945, 9.99, 2.99, 7),
    ("Brave New World", "Aldous Huxley", "Dystopian", 1932, 13.99, 4.25, 6),
    ("Fahrenheit 451", "Ray Bradbury", "Dystopian", 1953, 12.49, 3.75, 6),
    ("The Great Gatsby", "F. Scott Fitzgerald", "Classic", 1925, 10.99, 3.25, 9),
    ("To Kill a Mockingbird", "Harper Lee", "Classic", 1960, 15.99, 4.99, 8),
    ("The Catcher in the Rye", "J. D. Salinger", "Classic", 1951, 13.49, 3.99, 5),
    ("Moby-Dick", "Herman Melville", "Adventure", 1851, 16.99, 4.99, 3),
    ("The Adventures of Tom Sawyer", "Mark Twain", "Adventure", 1876, 8.99, 2.75, 6),
    ("Adventures of Huckleberry Finn", "Mark Twain", "Adventure", 1884, 9.49, 2.99, 6),
    ("Frankenstein", "Mary Shelley", "Horror", 1818, 9.99, 2.99, 7),
    ("Dracula", "Bram Stoker", "Horror", 1897, 11.99, 3.50, 5),
    ("The Hobbit", "J. R. R. Tolkien", "Fantasy", 1937, 17.99, 5.25, 12),
    ("The Fellowship of the Ring", "J. R. R. Tolkien", "Fantasy", 1954, 19.99, 5.99, 8),
    ("Dune", "Frank Herbert", "Science Fiction", 1965, 18.99, 5.50, 9),
    ("Foundation", "Isaac Asimov", "Science Fiction", 1951, 15.49, 4.50, 6),
    ("I, Robot", "Isaac Asimov", "Science Fiction", 1950, 13.99, 3.99, 5),
    ("Neuromancer", "William Gibson", "Science Fiction", 1984, 16.49, 4.75, 4),
    ("The Hitchhiker's Guide to the Galaxy", "Douglas Adams", "Science Fiction", 1979, 12.99, 3.75, 10),
    ("Crime and Punishment", "Fyodor Dostoevsky", "Classic", 1866, 14.49, 4.25, 4),
    ("The Brothers Karamazov", "Fyodor Dostoevsky", "Classic", 1880, 17.49, 5.00, 3),
    ("War and Peace", "Leo Tolstoy", "Classic", 1869, 21.99, 6.50, 3),
    ("Anna Karenina", "Leo Tolstoy", "Classic", 1878, 18.49, 5.50, 4),
    ("Jane Eyre", "Charlotte Bronte", "Classic", 1847, 11.99, 3.50, 6),
    ("Wuthering Heights", "Emily Bronte", "Classic", 1847, 11.49, 3.50, 5),
    ("The Old Man and the Sea", "Ernest Hemingway", "Classic", 1952, 10.49, 3.00, 7),
    ("Of Mice and Men", "John Steinbeck", "Classic", 1937, 9.99, 2.99, 8),
    ("The Grapes of Wrath", "John Steinbeck", "Classic", 1939, 15.99, 4.75, 5),
    ("Clean Code", "Robert C. Martin", "Computing", 2008, 39.99, 9.99, 6),
    ("Design Patterns", "Erich Gamma", "Computing", 1994, 49.99, 12.50, 4),
    ("Database System Concepts", "Abraham Silberschatz", "Computing", 2019, 89.99, 19.99, 5),
    ("The Pragmatic Programmer", "David Thomas", "Computing", 1999, 44.99, 10.99, 6),
]


def seed_docs() -> list[dict]:
    return [
        {
            "title": title,
            "author": author,
            "genre": genre,
            "published_year": year,
            "price": price,
            "rent_price": rent,
            "copies": copies,
            "isbn": None,
            "description": None,
        }
        for title, author, genre, year, price, rent, copies in BOOKS
    ]


def seed_if_empty(repo) -> None:
    if repo.count() == 0:
        repo.insert_many(seed_docs())
        log.info("Seeded %d books", len(BOOKS))


if __name__ == "__main__":
    from pymongo import MongoClient

    from common.config import env

    from .repository import BookRepository

    logging.basicConfig(level=logging.INFO)
    db = MongoClient(env("MONGO_URL", "mongodb://localhost:27018"))[env("MONGO_DB", "bookstore")]
    if "--reset" in sys.argv:
        db["books"].delete_many({})
        db["reviews"].delete_many({})
    seed_if_empty(BookRepository(db))
