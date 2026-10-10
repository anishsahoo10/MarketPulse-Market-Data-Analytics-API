package com.example.marketpulse.controller;

import com.example.marketpulse.dto.DailyChangeResponse;
import com.example.marketpulse.dto.MovingAverageResponse;
import com.example.marketpulse.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/instruments/{symbol}/analytics")
@Tag(name = "Analytics", description = "Quantitative calculations and statistical metrics for financial instruments")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/daily-change")
    @Operation(summary = "Calculate daily price change", description = "Calculate absolute and percentage change between the two latest available trading sessions.")
    public ResponseEntity<DailyChangeResponse> getDailyChange(
            @Parameter(description = "Ticker symbol (e.g. AAPL)", example = "AAPL")
            @PathVariable String symbol) {
        DailyChangeResponse response = analyticsService.getDailyChange(symbol);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/moving-average")
    @Operation(summary = "Calculate simple moving average (SMA)", description = "Calculate SMA for period N (2-200) using latest N trading sessions on or before asOfDate.")
    public ResponseEntity<MovingAverageResponse> getMovingAverage(
            @Parameter(description = "Ticker symbol (e.g. AAPL)", example = "AAPL")
            @PathVariable String symbol,
            @Parameter(description = "Moving average period (e.g. 7, 20, 50)", example = "20")
            @RequestParam(defaultValue = "20") int period,
            @Parameter(description = "Optional as-of date (YYYY-MM-DD)", example = "2026-01-30")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        MovingAverageResponse response = analyticsService.getMovingAverage(symbol, period, asOfDate);
        return ResponseEntity.ok(response);
    }
}
