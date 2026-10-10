package com.example.marketpulse.provider;

import java.time.LocalDate;
import java.util.List;

public interface MarketDataProvider {

    /**
     * Fetch instrument metadata for the given symbol.
     *
     * @param symbol normalized ticker symbol (e.g. AAPL)
     * @return instrument details
     */
    InstrumentData fetchInstrument(String symbol);

    /**
     * Fetch daily historical price series for the given symbol in the specified date range.
     *
     * @param symbol normalized ticker symbol
     * @param from   start date (inclusive, nullable)
     * @param to     end date (inclusive, nullable)
     * @return list of provider price entries in ascending date order
     */
    List<ProviderPrice> fetchDailyPrices(String symbol, LocalDate from, LocalDate to);
}
