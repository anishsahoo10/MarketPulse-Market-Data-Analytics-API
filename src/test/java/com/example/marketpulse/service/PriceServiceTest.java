package com.example.marketpulse.service;

import com.example.marketpulse.dto.HistoricalPricesResponse;
import com.example.marketpulse.dto.SyncResponse;
import com.example.marketpulse.entity.DailyPrice;
import com.example.marketpulse.entity.Instrument;
import com.example.marketpulse.exception.InvalidDateRangeException;
import com.example.marketpulse.exception.MarketDataProviderException;
import com.example.marketpulse.provider.MarketDataProvider;
import com.example.marketpulse.provider.ProviderPrice;
import com.example.marketpulse.repository.DailyPriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceServiceTest {

    @Mock
    private InstrumentService instrumentService;

    @Mock
    private DailyPriceRepository dailyPriceRepository;

    @Mock
    private MarketDataProvider marketDataProvider;

    @InjectMocks
    private PriceService priceService;

    private Instrument instrument;

    @BeforeEach
    void setUp() {
        instrument = new Instrument("AAPL", "Apple Inc.", "NASDAQ", "USD");
        instrument.setId(1L);
    }

    @Test
    @DisplayName("Historical price query with from date later than to date throws InvalidDateRangeException")
    void testHistoricalPricesInvalidDateRange() {
        when(instrumentService.normalizeSymbol("AAPL")).thenReturn("AAPL");
        when(instrumentService.getInstrumentEntity("AAPL")).thenReturn(instrument);

        LocalDate from = LocalDate.of(2026, 1, 31);
        LocalDate to = LocalDate.of(2026, 1, 1);

        assertThatThrownBy(() -> priceService.getHistoricalPrices("AAPL", from, to))
                .isInstanceOf(InvalidDateRangeException.class)
                .hasMessageContaining("'from' must be on or before 'to'");
    }

    @Test
    @DisplayName("Sync duplicate prices updates existing row and does not create duplicates")
    void testSyncDuplicateUpsert() {
        when(instrumentService.normalizeSymbol("AAPL")).thenReturn("AAPL");
        when(instrumentService.findOrCreateInstrument("AAPL")).thenReturn(instrument);

        LocalDate date = LocalDate.of(2026, 1, 30);
        DailyPrice existingPrice = new DailyPrice(instrument, date,
                new BigDecimal("100.00"), new BigDecimal("105.00"), new BigDecimal("99.00"), new BigDecimal("102.00"), 1000L);

        when(dailyPriceRepository.findByInstrumentOrderByPriceDateAsc(instrument))
                .thenReturn(new ArrayList<>(List.of(existingPrice)));

        // Provider sends updated close price for the SAME date
        ProviderPrice providerRow = new ProviderPrice(date,
                new BigDecimal("100.00"), new BigDecimal("105.00"), new BigDecimal("99.00"), new BigDecimal("103.50"), 1200L);

        when(marketDataProvider.fetchDailyPrices("AAPL", null, null))
                .thenReturn(List.of(providerRow));

        SyncResponse response = priceService.syncPrices("AAPL", null, null);

        assertThat(response.symbol()).isEqualTo("AAPL");
        assertThat(response.recordsReceived()).isEqualTo(1);
        assertThat(response.recordsInserted()).isEqualTo(0);
        assertThat(response.recordsUpdated()).isEqualTo(1);
        assertThat(response.recordsRejected()).isEqualTo(0);

        // Verify update on existing entity
        assertThat(existingPrice.getClosePrice()).isEqualByComparingTo(new BigDecimal("103.50"));
        assertThat(existingPrice.getVolume()).isEqualTo(1200L);

        verify(dailyPriceRepository).saveAll(any());
    }

    @Test
    @DisplayName("Sync inserts new rows and handles invalid rows safely")
    void testSyncInsertsNewRowsAndRejectsInvalid() {
        when(instrumentService.normalizeSymbol("AAPL")).thenReturn("AAPL");
        when(instrumentService.findOrCreateInstrument("AAPL")).thenReturn(instrument);
        when(dailyPriceRepository.findByInstrumentOrderByPriceDateAsc(instrument))
                .thenReturn(new ArrayList<>());

        LocalDate validDate = LocalDate.of(2026, 1, 30);
        ProviderPrice validRow = new ProviderPrice(validDate,
                new BigDecimal("100.00"), new BigDecimal("105.00"), new BigDecimal("99.00"), new BigDecimal("102.00"), 1000L);

        LocalDate invalidDate = LocalDate.of(2026, 1, 31);
        // Invalid: high (95.00) < low (99.00)
        ProviderPrice invalidRow = new ProviderPrice(invalidDate,
                new BigDecimal("100.00"), new BigDecimal("95.00"), new BigDecimal("99.00"), new BigDecimal("96.00"), 1000L);

        when(marketDataProvider.fetchDailyPrices("AAPL", null, null))
                .thenReturn(List.of(validRow, invalidRow));

        SyncResponse response = priceService.syncPrices("AAPL", null, null);

        assertThat(response.recordsReceived()).isEqualTo(2);
        assertThat(response.recordsInserted()).isEqualTo(1);
        assertThat(response.recordsUpdated()).isEqualTo(0);
        assertThat(response.recordsRejected()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DailyPrice>> captor = ArgumentCaptor.forClass(List.class);
        verify(dailyPriceRepository).saveAll(captor.capture());
        List<DailyPrice> saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getPriceDate()).isEqualTo(validDate);
    }

    @Test
    @DisplayName("Provider failure during sync does not alter stored prices")
    void testProviderFailurePreservesExistingData() {
        when(instrumentService.normalizeSymbol("AAPL")).thenReturn("AAPL");
        when(instrumentService.findOrCreateInstrument("AAPL")).thenReturn(instrument);
        when(marketDataProvider.fetchDailyPrices("AAPL", null, null))
                .thenThrow(MarketDataProviderException.unavailable("Provider timeout"));

        assertThatThrownBy(() -> priceService.syncPrices("AAPL", null, null))
                .isInstanceOf(MarketDataProviderException.class);

        // Verify save was never called
        verify(dailyPriceRepository, never()).save(any());
        verify(dailyPriceRepository, never()).saveAll(any());
        verify(dailyPriceRepository, never()).deleteAll(any());
    }
}
