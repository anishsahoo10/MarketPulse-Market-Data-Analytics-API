package com.example.marketpulse.dto;

import java.time.Instant;

public record SyncResponse(
        String symbol,
        String status,
        int recordsReceived,
        int recordsInserted,
        int recordsUpdated,
        int recordsRejected,
        Instant syncedAt
) {
}
