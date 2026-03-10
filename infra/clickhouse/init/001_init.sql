CREATE DATABASE IF NOT EXISTS trading;

CREATE TABLE IF NOT EXISTS trading.raw_order_events
(
    event_time       DateTime,
    event_id         String,
    order_id         String,
    account_id       String,
    symbol           String,
    event_type       String,
    quantity         UInt32,
    price            UInt64,
    payload          String
)
ENGINE = MergeTree
ORDER BY (symbol, event_time, order_id);