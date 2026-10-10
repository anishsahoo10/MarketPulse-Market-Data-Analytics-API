-- Schema definition for MarketPulse

CREATE TABLE IF NOT EXISTS instruments (
    id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    name VARCHAR(200) NOT NULL,
    exchange VARCHAR(80),
    currency VARCHAR(10),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    CONSTRAINT uk_instruments_symbol UNIQUE (symbol)
);

CREATE TABLE IF NOT EXISTS daily_prices (
    id BIGSERIAL PRIMARY KEY,
    instrument_id BIGINT NOT NULL,
    price_date DATE NOT NULL,
    open_price NUMERIC(19, 6),
    high_price NUMERIC(19, 6),
    low_price NUMERIC(19, 6),
    close_price NUMERIC(19, 6) NOT NULL,
    volume BIGINT,
    CONSTRAINT fk_daily_prices_instrument FOREIGN KEY (instrument_id) REFERENCES instruments (id) ON DELETE CASCADE,
    CONSTRAINT uk_daily_prices_instrument_date UNIQUE (instrument_id, price_date)
);

CREATE INDEX IF NOT EXISTS idx_daily_prices_instrument_date ON daily_prices (instrument_id, price_date);
