package com.example.marketpulse.controller;

import com.example.marketpulse.dto.HistoricalPricesResponse;
import com.example.marketpulse.dto.InstrumentResponse;
import com.example.marketpulse.dto.SyncResponse;
import com.example.marketpulse.service.InstrumentService;
import com.example.marketpulse.service.PriceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/instruments")
@Tag(name = "Instruments", description = "Instrument metadata and historical market price operations")
public class InstrumentController {

    private final InstrumentService instrumentService;
    private final PriceService priceService;

    public InstrumentController(InstrumentService instrumentService, PriceService priceService) {
        this.instrumentService = instrumentService;
        this.priceService = priceService;
    }

    @GetMapping("/{symbol}")
    @Operation(summary = "Get instrument metadata", description = "Retrieve stored metadata for a financial instrument by symbol.")
    public ResponseEntity<InstrumentResponse> getInstrument(
            @Parameter(description = "Ticker symbol (e.g. AAPL)", example = "AAPL")
            @PathVariable String symbol) {
        InstrumentResponse response = instrumentService.getInstrument(symbol);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{symbol}/prices")
    @Operation(summary = "Query historical prices", description = "Query stored daily prices within an inclusive date range in ascending order.")
    public ResponseEntity<HistoricalPricesResponse> getHistoricalPrices(
            @Parameter(description = "Ticker symbol (e.g. AAPL)", example = "AAPL")
            @PathVariable String symbol,
            @Parameter(description = "Start date (YYYY-MM-DD)", example = "2026-01-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "End date (YYYY-MM-DD)", example = "2026-01-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        HistoricalPricesResponse response = priceService.getHistoricalPrices(symbol, from, to);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{symbol}/sync")
    @Operation(summary = "Synchronize prices from provider", description = "Fetch daily price history from external provider and persist idempotent updates.")
    public ResponseEntity<SyncResponse> syncPrices(
            @Parameter(description = "Ticker symbol (e.g. AAPL)", example = "AAPL")
            @PathVariable String symbol,
            @Parameter(description = "Optional start date filter (YYYY-MM-DD)", example = "2026-01-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Optional end date filter (YYYY-MM-DD)", example = "2026-01-31")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        SyncResponse response = priceService.syncPrices(symbol, from, to);
        return ResponseEntity.ok(response);
    }
}
