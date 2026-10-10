package com.example.marketpulse.controller;

import com.example.marketpulse.dto.DailyChangeResponse;
import com.example.marketpulse.dto.InstrumentResponse;
import com.example.marketpulse.dto.MovingAverageResponse;
import com.example.marketpulse.dto.SyncResponse;
import com.example.marketpulse.exception.InsufficientHistoryException;
import com.example.marketpulse.exception.InstrumentNotFoundException;
import com.example.marketpulse.exception.InvalidDateRangeException;
import com.example.marketpulse.exception.InvalidRequestException;
import com.example.marketpulse.service.AnalyticsService;
import com.example.marketpulse.service.InstrumentService;
import com.example.marketpulse.service.PriceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({InstrumentController.class, AnalyticsController.class})
class InstrumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InstrumentService instrumentService;

    @MockBean
    private PriceService priceService;

    @MockBean
    private AnalyticsService analyticsService;

    @Test
    @DisplayName("GET /instruments/{symbol} returns 200 and metadata for existing symbol")
    void testGetInstrumentSuccess() throws Exception {
        InstrumentResponse response = new InstrumentResponse(
                "AAPL", "Apple Inc.", "NASDAQ", "USD", Instant.now(), Instant.now());
        when(instrumentService.getInstrument("AAPL")).thenReturn(response);

        mockMvc.perform(get("/api/v1/instruments/AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol", is("AAPL")))
                .andExpect(jsonPath("$.name", is("Apple Inc.")))
                .andExpect(jsonPath("$.exchange", is("NASDAQ")))
                .andExpect(jsonPath("$.currency", is("USD")));
    }

    @Test
    @DisplayName("GET /instruments/{symbol} returns 404 INSTRUMENT_NOT_FOUND when unknown")
    void testGetInstrumentNotFound() throws Exception {
        when(instrumentService.getInstrument("UNKNOWN"))
                .thenThrow(new InstrumentNotFoundException("UNKNOWN"));

        mockMvc.perform(get("/api/v1/instruments/UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.code", is("INSTRUMENT_NOT_FOUND")))
                .andExpect(jsonPath("$.message", is("Instrument not found for symbol: UNKNOWN")))
                .andExpect(jsonPath("$.path", is("/api/v1/instruments/UNKNOWN")));
    }

    @Test
    @DisplayName("GET /instruments/{symbol}/prices with from > to returns 400 INVALID_DATE_RANGE")
    void testGetPricesInvalidDateRange() throws Exception {
        LocalDate from = LocalDate.of(2026, 1, 31);
        LocalDate to = LocalDate.of(2026, 1, 1);

        when(priceService.getHistoricalPrices("AAPL", from, to))
                .thenThrow(new InvalidDateRangeException("'from' must be on or before 'to'"));

        mockMvc.perform(get("/api/v1/instruments/AAPL/prices")
                        .param("from", "2026-01-31")
                        .param("to", "2026-01-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.code", is("INVALID_DATE_RANGE")))
                .andExpect(jsonPath("$.message", is("'from' must be on or before 'to'")));
    }

    @Test
    @DisplayName("GET /instruments/{symbol}/analytics/moving-average with insufficient data returns 422 INSUFFICIENT_HISTORY")
    void testGetMovingAverageInsufficientHistory() throws Exception {
        when(analyticsService.getMovingAverage(eq("AAPL"), eq(20), eq(null)))
                .thenThrow(new InsufficientHistoryException("Insufficient history: requested period is 20 but only 5 available"));

        mockMvc.perform(get("/api/v1/instruments/AAPL/analytics/moving-average")
                        .param("period", "20"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status", is(422)))
                .andExpect(jsonPath("$.code", is("INSUFFICIENT_HISTORY")))
                .andExpect(jsonPath("$.message", is("Insufficient history: requested period is 20 but only 5 available")));
    }

    @Test
    @DisplayName("POST /instruments/{symbol}/sync returns 200 with sync statistics")
    void testSyncPricesSuccess() throws Exception {
        SyncResponse syncResponse = new SyncResponse(
                "AAPL", "SUCCESS", 30, 25, 5, 0, Instant.now());
        when(priceService.syncPrices("AAPL", null, null)).thenReturn(syncResponse);

        mockMvc.perform(post("/api/v1/instruments/AAPL/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.symbol", is("AAPL")))
                .andExpect(jsonPath("$.status", is("SUCCESS")))
                .andExpect(jsonPath("$.recordsReceived", is(30)))
                .andExpect(jsonPath("$.recordsInserted", is(25)))
                .andExpect(jsonPath("$.recordsUpdated", is(5)))
                .andExpect(jsonPath("$.recordsRejected", is(0)));
    }
}
