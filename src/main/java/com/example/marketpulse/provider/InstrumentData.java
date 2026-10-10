package com.example.marketpulse.provider;

public record InstrumentData(
        String symbol,
        String name,
        String exchange,
        String currency
) {
}
