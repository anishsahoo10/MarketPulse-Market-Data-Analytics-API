package com.example.marketpulse.exception;

public class InstrumentNotFoundException extends RuntimeException {

    private final String symbol;

    public InstrumentNotFoundException(String symbol) {
        super("Instrument not found for symbol: " + symbol);
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }
}
