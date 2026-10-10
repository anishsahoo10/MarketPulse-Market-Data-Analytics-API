package com.example.marketpulse.provider;

import com.example.marketpulse.exception.MarketDataProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * MockMarketDataProvider simulates real market data for local development,
 * reviewer demonstration, and offline automated testing.
 */
public class MockMarketDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(MockMarketDataProvider.class);

    private static final Map<String, InstrumentData> KNOWN_INSTRUMENTS = Map.of(
            "AAPL", new InstrumentData("AAPL", "Apple Inc.", "NASDAQ", "USD"),
            "MSFT", new InstrumentData("MSFT", "Microsoft Corporation", "NASDAQ", "USD"),
            "GOOGL", new InstrumentData("GOOGL", "Alphabet Inc.", "NASDAQ", "USD"),
            "NVDA", new InstrumentData("NVDA", "NVIDIA Corporation", "NASDAQ", "USD"),
            "TSLA", new InstrumentData("TSLA", "Tesla Inc.", "NASDAQ", "USD"),
            "AMZN", new InstrumentData("AMZN", "Amazon.com Inc.", "NASDAQ", "USD")
    );

    private static final Map<String, Double> BASE_PRICES = Map.of(
            "AAPL", 230.50,
            "MSFT", 420.75,
            "GOOGL", 185.20,
            "NVDA", 135.80,
            "TSLA", 255.40,
            "AMZN", 190.60
    );

    @Override
    public InstrumentData fetchInstrument(String symbol) {
        log.info("Fetching mock instrument metadata for symbol: {}", symbol);
        if ("FAIL_PROVIDER".equalsIgnoreCase(symbol)) {
            throw MarketDataProviderException.unavailable("Simulated provider outage");
        }

        if (KNOWN_INSTRUMENTS.containsKey(symbol)) {
            return KNOWN_INSTRUMENTS.get(symbol);
        }

        return new InstrumentData(symbol, symbol + " Corporation", "NYSE", "USD");
    }

    @Override
    public List<ProviderPrice> fetchDailyPrices(String symbol, LocalDate from, LocalDate to) {
        log.info("Generating mock daily prices for symbol: {}, from: {}, to: {}", symbol, from, to);
        if ("FAIL_PROVIDER".equalsIgnoreCase(symbol)) {
            throw MarketDataProviderException.unavailable("Simulated provider outage");
        }

        LocalDate endDate = to != null ? to : LocalDate.now();
        LocalDate startDate = from != null ? from : endDate.minusMonths(6);

        if (startDate.isAfter(endDate)) {
            return Collections.emptyList();
        }

        double basePrice = BASE_PRICES.getOrDefault(symbol, 100.00);
        long seed = symbol.hashCode() * 31L;
        Random random = new Random(seed);

        List<ProviderPrice> prices = new ArrayList<>();
        double currentPrice = basePrice * 0.85; // start lower and drift

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue; // Skip weekends
            }

            // Daily volatility between -2.5% and +2.8%
            double changePercent = (random.nextDouble() * 0.053) - 0.025;
            double open = currentPrice;
            double close = Math.max(1.0, currentPrice * (1.0 + changePercent));

            double high = Math.max(open, close) + (random.nextDouble() * 0.015 * currentPrice);
            double low = Math.min(open, close) - (random.nextDouble() * 0.015 * currentPrice);
            low = Math.max(0.5, low);

            long volume = 1_000_000L + (long) (random.nextDouble() * 25_000_000L);

            BigDecimal openBd = BigDecimal.valueOf(open).setScale(4, RoundingMode.HALF_UP);
            BigDecimal highBd = BigDecimal.valueOf(high).setScale(4, RoundingMode.HALF_UP);
            BigDecimal lowBd = BigDecimal.valueOf(low).setScale(4, RoundingMode.HALF_UP);
            BigDecimal closeBd = BigDecimal.valueOf(close).setScale(4, RoundingMode.HALF_UP);

            prices.add(new ProviderPrice(date, openBd, highBd, lowBd, closeBd, volume));
            currentPrice = close;
        }

        return prices;
    }
}
