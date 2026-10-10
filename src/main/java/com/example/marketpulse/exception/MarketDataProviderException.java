package com.example.marketpulse.exception;

import org.springframework.http.HttpStatus;

public class MarketDataProviderException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public MarketDataProviderException(String message, HttpStatus status, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static MarketDataProviderException unavailable(String message) {
        return new MarketDataProviderException(message, HttpStatus.SERVICE_UNAVAILABLE, "MARKET_DATA_PROVIDER_UNAVAILABLE");
    }

    public static MarketDataProviderException badGateway(String message) {
        return new MarketDataProviderException(message, HttpStatus.BAD_GATEWAY, "MARKET_DATA_PROVIDER_UNAVAILABLE");
    }

    public static MarketDataProviderException invalidResponse(String message) {
        return new MarketDataProviderException(message, HttpStatus.BAD_GATEWAY, "INVALID_PROVIDER_RESPONSE");
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
