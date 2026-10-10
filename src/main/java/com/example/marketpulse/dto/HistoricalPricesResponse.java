package com.example.marketpulse.dto;

import java.time.LocalDate;
import java.util.List;

public record HistoricalPricesResponse(
        String symbol,
        LocalDate from,
        LocalDate to,
        int count,
        List<PriceRecordDto> prices
) {
}
