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

CREATE TABLE IF NOT EXISTS trading.normalized_order_events
(
    event_time DateTime,
    event_id String,
    order_id String,
    account_id String,
    symbol String,
    event_type String,
    quantity UInt32,
    price UInt64,
    raw_payload String
)
ENGINE = MergeTree
ORDER BY (symbol, event_time, order_id);

CREATE TABLE IF NOT EXISTS trading.order_state_current
(
    order_id String,
    symbol String,
    status String,
    original_qty UInt32,
    current_qty UInt32,
    filled_qty UInt32,
    remaining_qty UInt32,
    last_event_time DateTime
)
ENGINE = ReplacingMergeTree(last_event_time)
ORDER BY order_id;