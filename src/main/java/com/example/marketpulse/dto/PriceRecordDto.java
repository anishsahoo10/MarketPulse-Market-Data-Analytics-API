package com.example.marketpulse.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PriceRecordDto(
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Long volume
) {
}
