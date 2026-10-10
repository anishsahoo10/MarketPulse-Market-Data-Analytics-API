package com.example.marketpulse.provider;

import com.example.marketpulse.exception.MarketDataProviderException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketDataProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("MockMarketDataProvider generates valid prices with high >= low and ascending dates")
    void testMockProviderGeneratesValidPrices() {
        MockMarketDataProvider provider = new MockMarketDataProvider();

        InstrumentData instrument = provider.fetchInstrument("AAPL");
        assertThat(instrument.symbol()).isEqualTo("AAPL");
        assertThat(instrument.name()).isEqualTo("Apple Inc.");

        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);
        List<ProviderPrice> prices = provider.fetchDailyPrices("AAPL", from, to);

        assertThat(prices).isNotEmpty();
        for (ProviderPrice p : prices) {
            assertThat(p.date()).isAfterOrEqualTo(from).isBeforeOrEqualTo(to);
            assertThat(p.high()).isGreaterThanOrEqualTo(p.low());
            assertThat(p.close()).isGreaterThan(BigDecimal.ZERO);
        }
    }

    @Test
    @DisplayName("MockMarketDataProvider throws MarketDataProviderException on failure simulation")
    void testMockProviderSimulatesFailure() {
        MockMarketDataProvider provider = new MockMarketDataProvider();

        assertThatThrownBy(() -> provider.fetchInstrument("FAIL_PROVIDER"))
                .isInstanceOf(MarketDataProviderException.class);

        assertThatThrownBy(() -> provider.fetchDailyPrices("FAIL_PROVIDER", LocalDate.now().minusDays(10), LocalDate.now()))
                .isInstanceOf(MarketDataProviderException.class);
    }
}
