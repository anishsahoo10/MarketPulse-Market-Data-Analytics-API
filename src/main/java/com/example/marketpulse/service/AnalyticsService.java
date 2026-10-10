package com.example.marketpulse.service;

import com.example.marketpulse.dto.DailyChangeResponse;
import com.example.marketpulse.dto.MovingAverageResponse;
import com.example.marketpulse.entity.DailyPrice;
import com.example.marketpulse.entity.Instrument;
import com.example.marketpulse.exception.InsufficientHistoryException;
import com.example.marketpulse.exception.InvalidRequestException;
import com.example.marketpulse.repository.DailyPriceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);
    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    private final InstrumentService instrumentService;
    private final DailyPriceRepository dailyPriceRepository;

    public AnalyticsService(InstrumentService instrumentService, DailyPriceRepository dailyPriceRepository) {
        this.instrumentService = instrumentService;
        this.dailyPriceRepository = dailyPriceRepository;
    }

    /**
     * Calculate daily price change based on the two latest available trading sessions.
     */
    @Transactional(readOnly = true)
    public DailyChangeResponse getDailyChange(String symbol) {
        String normalizedSymbol = instrumentService.normalizeSymbol(symbol);
        Instrument instrument = instrumentService.getInstrumentEntity(normalizedSymbol);

        List<DailyPrice> latestPrices = dailyPriceRepository.findTop2ByInstrumentOrderByPriceDateDesc(instrument);

        if (latestPrices.size() < 2) {
            throw new InsufficientHistoryException(
                    "At least 2 trading sessions are required to calculate daily price change. Found: " + latestPrices.size());
        }

        DailyPrice currentSession = latestPrices.get(0);
        DailyPrice previousSession = latestPrices.get(1);

        BigDecimal currentClose = currentSession.getClosePrice();
        BigDecimal previousClose = previousSession.getClosePrice();

        if (previousClose == null || previousClose.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InsufficientHistoryException("Previous close is zero or invalid, percentage change cannot be calculated");
        }

        BigDecimal absoluteChange = currentClose.subtract(previousClose).setScale(SCALE, ROUNDING_MODE);
        BigDecimal percentageChange = absoluteChange
                .multiply(BigDecimal.valueOf(100))
                .divide(previousClose, SCALE, ROUNDING_MODE);

        return new DailyChangeResponse(
                normalizedSymbol,
                previousSession.getPriceDate(),
                currentSession.getPriceDate(),
                previousClose.setScale(SCALE, ROUNDING_MODE),
                currentClose.setScale(SCALE, ROUNDING_MODE),
                absoluteChange,
                percentageChange
        );
    }

    /**
     * Calculate Simple Moving Average (SMA) over the latest N valid trading sessions on or before asOfDate.
     */
    @Transactional(readOnly = true)
    public MovingAverageResponse getMovingAverage(String symbol, int period, LocalDate asOfDate) {
        String normalizedSymbol = instrumentService.normalizeSymbol(symbol);

        if (period < 2 || period > 200) {
            throw new InvalidRequestException("Period must be between 2 and 200 trading sessions");
        }

        Instrument instrument = instrumentService.getInstrumentEntity(normalizedSymbol);

        LocalDate effectiveAsOfDate = asOfDate;
        if (effectiveAsOfDate == null) {
            DailyPrice latest = dailyPriceRepository.findTopByInstrumentOrderByPriceDateDesc(instrument)
                    .orElseThrow(() -> new InsufficientHistoryException("No price history available for instrument: " + normalizedSymbol));
            effectiveAsOfDate = latest.getPriceDate();
        }

        List<DailyPrice> sessions = dailyPriceRepository
                .findByInstrumentAndPriceDateLessThanEqualOrderByPriceDateDesc(
                        instrument, effectiveAsOfDate, PageRequest.of(0, period));

        if (sessions.size() < period) {
            throw new InsufficientHistoryException(
                    String.format("Insufficient history: requested period is %d but only %d trading sessions available on or before %s",
                            period, sessions.size(), effectiveAsOfDate));
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (DailyPrice price : sessions) {
            if (price.getClosePrice() == null) {
                throw new InsufficientHistoryException("Found null close price in historical session on " + price.getPriceDate());
            }
            sum = sum.add(price.getClosePrice());
        }

        BigDecimal sma = sum.divide(BigDecimal.valueOf(period), SCALE, ROUNDING_MODE);

        return new MovingAverageResponse(
                normalizedSymbol,
                period,
                effectiveAsOfDate,
                sessions.size(),
                sma
        );
    }
}
