package com.example.marketpulse.service;

import com.example.marketpulse.dto.DailyChangeResponse;
import com.example.marketpulse.dto.MovingAverageResponse;
import com.example.marketpulse.entity.DailyPrice;
import com.example.marketpulse.entity.Instrument;
import com.example.marketpulse.exception.InsufficientHistoryException;
import com.example.marketpulse.exception.InvalidRequestException;
import com.example.marketpulse.repository.DailyPriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private InstrumentService instrumentService;

    @Mock
    private DailyPriceRepository dailyPriceRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    private Instrument instrument;

    @BeforeEach
    void setUp() {
        instrument = new Instrument("AAPL", "Apple Inc.", "NASDAQ", "USD");
        instrument.setId(1L);
    }

    @Test
    @DisplayName("Daily change for 100 -> 105 returns absolute change 5 and percentage change 5%")
    void testDailyChangeSuccess() {
        when(instrumentService.normalizeSymbol("AAPL")).thenReturn("AAPL");
        when(instrumentService.getInstrumentEntity("AAPL")).thenReturn(instrument);

        LocalDate date1 = LocalDate.of(2026, 1, 29);
        LocalDate date2 = LocalDate.of(2026, 1, 30);

        DailyPrice prevSession = new DailyPrice(instrument, date1, null, null, null, new BigDecimal("100.00"), 1000L);
        DailyPrice currSession = new DailyPrice(instrument, date2, null, null, null, new BigDecimal("105.00"), 1000L);

        // findTop2 returns newest first
        when(dailyPriceRepository.findTop2ByInstrumentOrderByPriceDateDesc(instrument))
                .thenReturn(List.of(currSession, prevSession));

        DailyChangeResponse response = analyticsService.getDailyChange("AAPL");

        assertThat(response.symbol()).isEqualTo("AAPL");
        assertThat(response.previousTradingDate()).isEqualTo(date1);
        assertThat(response.currentTradingDate()).isEqualTo(date2);
        assertThat(response.previousClose()).isEqualByComparingTo(new BigDecimal("100.0000"));
        assertThat(response.currentClose()).isEqualByComparingTo(new BigDecimal("105.0000"));
        assertThat(response.absoluteChange()).isEqualByComparingTo(new BigDecimal("5.0000"));
        assertThat(response.percentageChange()).isEqualByComparingTo(new BigDecimal("5.0000"));
    }

    @Test
    @DisplayName("Daily change with fewer than 2 trading sessions throws InsufficientHistoryException")
    void testDailyChangeInsufficientHistory() {
        when(instrumentService.normalizeSymbol("AAPL")).thenReturn("AAPL");
        when(instrumentService.getInstrumentEntity("AAPL")).thenReturn(instrument);
        when(dailyPriceRepository.findTop2ByInstrumentOrderByPriceDateDesc(instrument))
                .thenReturn(Collections.singletonList(
                        new DailyPrice(instrument, LocalDate.now(), null, null, null, new BigDecimal("100.00"), 1000L)));

        assertThatThrownBy(() -> analyticsService.getDailyChange("AAPL"))
                .isInstanceOf(InsufficientHistoryException.class)
                .hasMessageContaining("At least 2 trading sessions are required");
    }

    @Test
    @DisplayName("Daily change with zero previous close throws InsufficientHistoryException")
    void testDailyChangeZeroPreviousClose() {
        when(instrumentService.normalizeSymbol("AAPL")).thenReturn("AAPL");
        when(instrumentService.getInstrumentEntity("AAPL")).thenReturn(instrument);

        DailyPrice prev = new DailyPrice(instrument, LocalDate.of(2026, 1, 29), null, null, null, BigDecimal.ZERO, 100L);
        DailyPrice curr = new DailyPrice(instrument, LocalDate.of(2026, 1, 30), null, null, null, new BigDecimal("100.00"), 100L);

        when(dailyPriceRepository.findTop2ByInstrumentOrderByPriceDateDesc(instrument))
                .thenReturn(List.of(curr, prev));

        assertThatThrownBy(() -> analyticsService.getDailyChange("AAPL"))
                .isInstanceOf(InsufficientHistoryException.class)
                .hasMessageContaining("Previous close is zero");
    }

    @Test
    @DisplayName("SMA(3) for closes 102, 108 and 110 returns 106.6667")
    void testMovingAverageCalculation() {
        when(instrumentService.normalizeSymbol("AAPL")).thenReturn("AAPL");
        when(instrumentService.getInstrumentEntity("AAPL")).thenReturn(instrument);

        LocalDate asOfDate = LocalDate.of(2026, 1, 30);
        DailyPrice p3 = new DailyPrice(instrument, LocalDate.of(2026, 1, 30), null, null, null, new BigDecimal("110.00"), 1000L);
        DailyPrice p2 = new DailyPrice(instrument, LocalDate.of(2026, 1, 29), null, null, null, new BigDecimal("108.00"), 1000L);
        DailyPrice p1 = new DailyPrice(instrument, LocalDate.of(2026, 1, 28), null, null, null, new BigDecimal("102.00"), 1000L);

        when(dailyPriceRepository.findByInstrumentAndPriceDateLessThanEqualOrderByPriceDateDesc(
                eq(instrument), eq(asOfDate), eq(PageRequest.of(0, 3))))
                .thenReturn(List.of(p3, p2, p1));

        MovingAverageResponse response = analyticsService.getMovingAverage("AAPL", 3, asOfDate);

        assertThat(response.symbol()).isEqualTo("AAPL");
        assertThat(response.period()).isEqualTo(3);
        assertThat(response.asOfDate()).isEqualTo(asOfDate);
        assertThat(response.dataPointsUsed()).isEqualTo(3);
        // (102 + 108 + 110) / 3 = 320 / 3 = 106.66666... rounded to 4 decimals = 106.6667
        assertThat(response.simpleMovingAverage()).isEqualByComparingTo(new BigDecimal("106.6667"));
    }

    @Test
    @DisplayName("SMA with insufficient rows throws InsufficientHistoryException")
    void testMovingAverageInsufficientRows() {
        when(instrumentService.normalizeSymbol("AAPL")).thenReturn("AAPL");
        when(instrumentService.getInstrumentEntity("AAPL")).thenReturn(instrument);

        LocalDate asOfDate = LocalDate.of(2026, 1, 30);
        DailyPrice p1 = new DailyPrice(instrument, LocalDate.of(2026, 1, 30), null, null, null, new BigDecimal("110.00"), 1000L);

        when(dailyPriceRepository.findByInstrumentAndPriceDateLessThanEqualOrderByPriceDateDesc(
                eq(instrument), eq(asOfDate), eq(PageRequest.of(0, 5))))
                .thenReturn(List.of(p1));

        assertThatThrownBy(() -> analyticsService.getMovingAverage("AAPL", 5, asOfDate))
                .isInstanceOf(InsufficientHistoryException.class)
                .hasMessageContaining("Insufficient history: requested period is 5 but only 1");
    }

    @Test
    @DisplayName("SMA with invalid period (outside 2-200) throws InvalidRequestException")
    void testMovingAverageInvalidPeriod() {
        when(instrumentService.normalizeSymbol("AAPL")).thenReturn("AAPL");

        assertThatThrownBy(() -> analyticsService.getMovingAverage("AAPL", 1, LocalDate.now()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Period must be between 2 and 200");

        assertThatThrownBy(() -> analyticsService.getMovingAverage("AAPL", 250, LocalDate.now()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Period must be between 2 and 200");
    }

    @Test
    @DisplayName("SMA defaults to latest stored trading date when asOfDate is null")
    void testMovingAverageNullAsOfDateDefaultsToLatest() {
        when(instrumentService.normalizeSymbol("AAPL")).thenReturn("AAPL");
        when(instrumentService.getInstrumentEntity("AAPL")).thenReturn(instrument);

        LocalDate latestDate = LocalDate.of(2026, 1, 30);
        DailyPrice latest = new DailyPrice(instrument, latestDate, null, null, null, new BigDecimal("100.00"), 100L);
        when(dailyPriceRepository.findTopByInstrumentOrderByPriceDateDesc(instrument))
                .thenReturn(Optional.of(latest));

        DailyPrice p1 = new DailyPrice(instrument, LocalDate.of(2026, 1, 29), null, null, null, new BigDecimal("90.00"), 100L);

        when(dailyPriceRepository.findByInstrumentAndPriceDateLessThanEqualOrderByPriceDateDesc(
                eq(instrument), eq(latestDate), eq(PageRequest.of(0, 2))))
                .thenReturn(List.of(latest, p1));

        MovingAverageResponse response = analyticsService.getMovingAverage("AAPL", 2, null);

        assertThat(response.asOfDate()).isEqualTo(latestDate);
        assertThat(response.simpleMovingAverage()).isEqualByComparingTo(new BigDecimal("95.0000"));
    }
}
