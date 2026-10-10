package com.example.marketpulse.service;

import com.example.marketpulse.dto.InstrumentResponse;
import com.example.marketpulse.entity.Instrument;
import com.example.marketpulse.exception.InstrumentNotFoundException;
import com.example.marketpulse.exception.InvalidRequestException;
import com.example.marketpulse.provider.InstrumentData;
import com.example.marketpulse.provider.MarketDataProvider;
import com.example.marketpulse.repository.InstrumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.regex.Pattern;

@Service
public class InstrumentService {

    private static final Logger log = LoggerFactory.getLogger(InstrumentService.class);
    private static final Pattern SYMBOL_PATTERN = Pattern.compile("^[A-Za-z0-9.\\-]{1,20}$");

    private final InstrumentRepository instrumentRepository;
    private final MarketDataProvider marketDataProvider;

    public InstrumentService(InstrumentRepository instrumentRepository, MarketDataProvider marketDataProvider) {
        this.instrumentRepository = instrumentRepository;
        this.marketDataProvider = marketDataProvider;
    }

    /**
     * Normalize and validate symbol input.
     */
    public String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            throw new InvalidRequestException("Symbol cannot be null or blank");
        }
        String normalized = symbol.trim().toUpperCase();
        if (!SYMBOL_PATTERN.matcher(normalized).matches()) {
            throw new InvalidRequestException("Invalid symbol format: '" + symbol + "'. Must be 1-20 alphanumeric characters.");
        }
        return normalized;
    }

    @Transactional(readOnly = true)
    public InstrumentResponse getInstrument(String symbol) {
        String normalized = normalizeSymbol(symbol);
        Instrument instrument = instrumentRepository.findBySymbol(normalized)
                .orElseThrow(() -> new InstrumentNotFoundException(normalized));

        return toResponse(instrument);
    }

    @Transactional(readOnly = true)
    public Instrument getInstrumentEntity(String symbol) {
        String normalized = normalizeSymbol(symbol);
        return instrumentRepository.findBySymbol(normalized)
                .orElseThrow(() -> new InstrumentNotFoundException(normalized));
    }

    @Transactional
    public Instrument findOrCreateInstrument(String symbol) {
        String normalized = normalizeSymbol(symbol);
        return instrumentRepository.findBySymbol(normalized)
                .orElseGet(() -> {
                    log.info("Instrument {} not found in local DB. Fetching metadata from provider...", normalized);
                    InstrumentData data = marketDataProvider.fetchInstrument(normalized);
                    Instrument newInstrument = new Instrument(
                            data.symbol(),
                            data.name(),
                            data.exchange(),
                            data.currency()
                    );
                    newInstrument.setCreatedAt(Instant.now());
                    newInstrument.setUpdatedAt(Instant.now());
                    return instrumentRepository.save(newInstrument);
                });
    }

    @Transactional
    public void updateInstrumentMetadata(Instrument instrument, InstrumentData data) {
        if (data != null) {
            boolean updated = false;
            if (data.name() != null && !data.name().equals(instrument.getName())) {
                instrument.setName(data.name());
                updated = true;
            }
            if (data.exchange() != null && !data.exchange().equals(instrument.getExchange())) {
                instrument.setExchange(data.exchange());
                updated = true;
            }
            if (data.currency() != null && !data.currency().equals(instrument.getCurrency())) {
                instrument.setCurrency(data.currency());
                updated = true;
            }
            if (updated) {
                instrument.setUpdatedAt(Instant.now());
                instrumentRepository.save(instrument);
            }
        }
    }

    private InstrumentResponse toResponse(Instrument instrument) {
        return new InstrumentResponse(
                instrument.getSymbol(),
                instrument.getName(),
                instrument.getExchange(),
                instrument.getCurrency(),
                instrument.getCreatedAt(),
                instrument.getUpdatedAt()
        );
    }
}
