package com.example.marketpulse.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyChangeResponse(
        String symbol,
        LocalDate previousTradingDate,
        LocalDate currentTradingDate,
        BigDecimal previousClose,
        BigDecimal currentClose,
        BigDecimal absoluteChange,
        BigDecimal percentageChange
) {
}
