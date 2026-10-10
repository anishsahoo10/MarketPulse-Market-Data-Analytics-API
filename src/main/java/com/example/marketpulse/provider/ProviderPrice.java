package com.example.marketpulse.provider;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProviderPrice(
        LocalDate date,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        Long volume
) {
}
