package com.example.marketpulse.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MovingAverageResponse(
        String symbol,
        int period,
        LocalDate asOfDate,
        int dataPointsUsed,
        BigDecimal simpleMovingAverage
) {
}
