-- Runs once, the first time the MySQL container starts with an empty volume.
-- Two separate schemas: the user service owns bookstore_users, the order
-- service owns bookstore_orders. Neither service touches the other's schema.

CREATE DATABASE IF NOT EXISTS bookstore_users;
CREATE DATABASE IF NOT EXISTS bookstore_orders;

CREATE USER IF NOT EXISTS 'bookstore'@'%' IDENTIFIED BY 'bookstore';
GRANT ALL PRIVILEGES ON bookstore_users.*  TO 'bookstore'@'%';
GRANT ALL PRIVILEGES ON bookstore_orders.* TO 'bookstore'@'%';
FLUSH PRIVILEGES;

-- ---------------------------------------------------------------- users
USE bookstore_users;

CREATE TABLE IF NOT EXISTS users (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(30)  NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash CHAR(60)     NOT NULL,          -- bcrypt
    full_name     VARCHAR(100) NULL,
    role          ENUM('CUSTOMER', 'MANAGER') NOT NULL DEFAULT 'CUSTOMER',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- One row per login. The token itself is never stored, only its SHA-256.
-- A session ends when the user logs out (revoked_at is set).
CREATE TABLE IF NOT EXISTS sessions (
    token_hash CHAR(64)  PRIMARY KEY,
    user_id    INT       NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP NULL,
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- --------------------------------------------------------------- orders
USE bookstore_orders;

-- user_id points at bookstore_users.users(id). It is not a foreign key on
-- purpose: the two schemas belong to different services. Username and email
-- are copied in at order time so a bill never needs the user service.
CREATE TABLE IF NOT EXISTS orders (
    id                INT AUTO_INCREMENT PRIMARY KEY,
    user_id           INT           NOT NULL,
    customer_username VARCHAR(30)   NOT NULL,
    customer_email    VARCHAR(255)  NOT NULL,
    payment_status    ENUM('PENDING', 'PAID') NOT NULL DEFAULT 'PENDING',
    total             DECIMAL(10,2) NOT NULL,
    created_at        DATETIME      NOT NULL,
    paid_at           DATETIME      NULL,
    bill_path         VARCHAR(500)  NULL,
    INDEX idx_orders_user (user_id),
    INDEX idx_orders_created (created_at)
);

-- book_id is a MongoDB ObjectId (24 hex chars) from the book service.
-- Title, author and price are copied in so old bills stay correct after a
-- book is edited.
CREATE TABLE IF NOT EXISTS order_items (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    order_id    INT           NOT NULL,
    book_id     CHAR(24)      NOT NULL,
    title       VARCHAR(255)  NOT NULL,
    author      VARCHAR(255)  NOT NULL,
    type        ENUM('BUY', 'RENT') NOT NULL,
    quantity    INT           NOT NULL,
    unit_price  DECIMAL(10,2) NOT NULL,
    line_total  DECIMAL(10,2) NOT NULL,
    due_date    DATE          NULL,      -- RENT only
    returned_at DATETIME      NULL,      -- RENT only, set when manager marks it returned
    CONSTRAINT fk_items_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    INDEX idx_items_book (book_id)
);
