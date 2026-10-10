package com.example.marketpulse.dto;

import java.time.Instant;

public record InstrumentResponse(
        String symbol,
        String name,
        String exchange,
        String currency,
        Instant createdAt,
        Instant updatedAt
) {
}
