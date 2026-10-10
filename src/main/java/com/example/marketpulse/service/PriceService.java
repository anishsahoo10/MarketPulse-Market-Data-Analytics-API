package com.example.marketpulse.service;

import com.example.marketpulse.dto.HistoricalPricesResponse;
import com.example.marketpulse.dto.PriceRecordDto;
import com.example.marketpulse.dto.SyncResponse;
import com.example.marketpulse.entity.DailyPrice;
import com.example.marketpulse.entity.Instrument;
import com.example.marketpulse.exception.InvalidDateRangeException;
import com.example.marketpulse.provider.MarketDataProvider;
import com.example.marketpulse.provider.ProviderPrice;
import com.example.marketpulse.repository.DailyPriceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class PriceService {

    private static final Logger log = LoggerFactory.getLogger(PriceService.class);

    private final InstrumentService instrumentService;
    private final DailyPriceRepository dailyPriceRepository;
    private final MarketDataProvider marketDataProvider;

    public PriceService(InstrumentService instrumentService,
                        DailyPriceRepository dailyPriceRepository,
                        MarketDataProvider marketDataProvider) {
        this.instrumentService = instrumentService;
        this.dailyPriceRepository = dailyPriceRepository;
        this.marketDataProvider = marketDataProvider;
    }

    /**
     * Query historical prices for an instrument within an inclusive date range.
     */
    @Transactional(readOnly = true)
    public HistoricalPricesResponse getHistoricalPrices(String symbol, LocalDate from, LocalDate to) {
        String normalizedSymbol = instrumentService.normalizeSymbol(symbol);
        Instrument instrument = instrumentService.getInstrumentEntity(normalizedSymbol);

        // Validation for date range
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidDateRangeException("'from' must be on or before 'to'");
        }

        LocalDate effectiveFrom = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate effectiveTo = to != null ? to : LocalDate.now().plusYears(10);

        List<DailyPrice> prices = dailyPriceRepository
                .findByInstrumentAndPriceDateBetweenOrderByPriceDateAsc(instrument, effectiveFrom, effectiveTo);

        List<PriceRecordDto> dtos = prices.stream()
                .map(this::toPriceRecordDto)
                .toList();

        return new HistoricalPricesResponse(
                normalizedSymbol,
                from,
                to,
                dtos.size(),
                dtos
        );
    }

    /**
     * Synchronize price history from the external provider and persist into PostgreSQL.
     * External HTTP call is intentionally executed outside of a database transaction.
     */
    public SyncResponse syncPrices(String symbol, LocalDate from, LocalDate to) {
        String normalizedSymbol = instrumentService.normalizeSymbol(symbol);

        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidDateRangeException("'from' must be on or before 'to'");
        }

        // 1. Ensure instrument exists (or fetch metadata from provider)
        Instrument instrument = instrumentService.findOrCreateInstrument(normalizedSymbol);

        // 2. Fetch price series from provider (WITHOUT holding open a DB transaction)
        log.info("Fetching price data from external provider for {}", normalizedSymbol);
        List<ProviderPrice> providerPrices = marketDataProvider.fetchDailyPrices(normalizedSymbol, from, to);

        // 3. Persist and upsert rows inside a transaction
        return persistProviderPrices(instrument, providerPrices);
    }

    @Transactional
    public SyncResponse persistProviderPrices(Instrument instrument, List<ProviderPrice> providerPrices) {
        int recordsReceived = providerPrices != null ? providerPrices.size() : 0;
        int recordsInserted = 0;
        int recordsUpdated = 0;
        int recordsRejected = 0;

        if (providerPrices != null && !providerPrices.isEmpty()) {
            // Load existing prices for this instrument to perform fast in-memory upsert comparison
            List<DailyPrice> existingPrices = dailyPriceRepository.findByInstrumentOrderByPriceDateAsc(instrument);
            Map<LocalDate, DailyPrice> existingMap = new HashMap<>();
            for (DailyPrice p : existingPrices) {
                existingMap.put(p.getPriceDate(), p);
            }

            List<DailyPrice> toSave = new ArrayList<>();

            for (ProviderPrice row : providerPrices) {
                if (!isValidRow(row)) {
                    log.warn("Rejecting invalid price row for {}: date={}, close={}",
                            instrument.getSymbol(), row.date(), row.close());
                    recordsRejected++;
                    continue;
                }

                DailyPrice existing = existingMap.get(row.date());
                if (existing != null) {
                    boolean modified = false;
                    if (!Objects.equals(existing.getOpenPrice(), row.open())) {
                        existing.setOpenPrice(row.open());
                        modified = true;
                    }
                    if (!Objects.equals(existing.getHighPrice(), row.high())) {
                        existing.setHighPrice(row.high());
                        modified = true;
                    }
                    if (!Objects.equals(existing.getLowPrice(), row.low())) {
                        existing.setLowPrice(row.low());
                        modified = true;
                    }
                    if (!Objects.equals(existing.getClosePrice(), row.close())) {
                        existing.setClosePrice(row.close());
                        modified = true;
                    }
                    if (!Objects.equals(existing.getVolume(), row.volume())) {
                        existing.setVolume(row.volume());
                        modified = true;
                    }

                    if (modified) {
                        toSave.add(existing);
                        recordsUpdated++;
                    }
                } else {
                    DailyPrice newPrice = new DailyPrice(
                            instrument,
                            row.date(),
                            row.open(),
                            row.high(),
                            row.low(),
                            row.close(),
                            row.volume()
                    );
                    toSave.add(newPrice);
                    recordsInserted++;
                    existingMap.put(row.date(), newPrice); // track newly added in current loop
                }
            }

            if (!toSave.isEmpty()) {
                dailyPriceRepository.saveAll(toSave);
            }
        }

        log.info("Sync completed for {}: received={}, inserted={}, updated={}, rejected={}",
                instrument.getSymbol(), recordsReceived, recordsInserted, recordsUpdated, recordsRejected);

        return new SyncResponse(
                instrument.getSymbol(),
                "SUCCESS",
                recordsReceived,
                recordsInserted,
                recordsUpdated,
                recordsRejected,
                Instant.now()
        );
    }

    private boolean isValidRow(ProviderPrice row) {
        if (row == null || row.date() == null || row.close() == null) {
            return false;
        }
        if (row.close().compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }
        if (row.open() != null && row.open().compareTo(BigDecimal.ZERO) < 0) {
            return false;
        }
        if (row.high() != null && row.low() != null && row.high().compareTo(row.low()) < 0) {
            return false;
        }
        if (row.volume() != null && row.volume() < 0) {
            return false;
        }
        return true;
    }

    private PriceRecordDto toPriceRecordDto(DailyPrice entity) {
        return new PriceRecordDto(
                entity.getPriceDate(),
                entity.getOpenPrice(),
                entity.getHighPrice(),
                entity.getLowPrice(),
                entity.getClosePrice(),
                entity.getVolume()
        );
    }
}
