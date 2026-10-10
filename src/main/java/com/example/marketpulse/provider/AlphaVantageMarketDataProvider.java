package com.example.marketpulse.provider;

import com.example.marketpulse.exception.MarketDataProviderException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AlphaVantageMarketDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(AlphaVantageMarketDataProvider.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final RestClient restClient;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    public AlphaVantageMarketDataProvider(String baseUrl, String apiKey, int timeoutSeconds, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(timeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public InstrumentData fetchInstrument(String symbol) {
        log.info("Fetching instrument metadata from Alpha Vantage for symbol: {}", symbol);
        try {
            String responseBody = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/query")
                            .queryParam("function", "OVERVIEW")
                            .queryParam("symbol", symbol)
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                throw MarketDataProviderException.invalidResponse("Empty response from market data provider");
            }

            JsonNode root = objectMapper.readTree(responseBody);
            checkRateLimitOrError(root);

            String name = root.path("Name").asText(null);
            String exchange = root.path("Exchange").asText(null);
            String currency = root.path("Currency").asText(null);

            if (name == null || name.isBlank()) {
                // If OVERVIEW does not provide name (e.g., standard free tier or non-US), provide sensible default
                name = symbol + " Inc.";
                exchange = exchange != null && !exchange.isBlank() ? exchange : "US";
                currency = currency != null && !currency.isBlank() ? currency : "USD";
            }

            return new InstrumentData(symbol, name, exchange, currency);
        } catch (MarketDataProviderException e) {
            throw e;
        } catch (ResourceAccessException e) {
            log.error("Network or timeout error contacting Alpha Vantage for symbol {}: {}", symbol, e.getMessage());
            throw MarketDataProviderException.unavailable("Market data provider is unreachable or timed out");
        } catch (RestClientResponseException e) {
            log.error("HTTP error from Alpha Vantage: status {}", e.getStatusCode());
            throw MarketDataProviderException.badGateway("Provider returned HTTP status: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("Unexpected error fetching instrument for symbol {}: {}", symbol, e.getMessage());
            throw MarketDataProviderException.invalidResponse("Unable to parse market data provider instrument response");
        }
    }

    @Override
    public List<ProviderPrice> fetchDailyPrices(String symbol, LocalDate from, LocalDate to) {
        log.info("Fetching daily prices from Alpha Vantage for symbol: {}", symbol);
        try {
            String responseBody = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/query")
                            .queryParam("function", "TIME_SERIES_DAILY")
                            .queryParam("symbol", symbol)
                            .queryParam("outputsize", "compact")
                            .queryParam("apikey", apiKey)
                            .build())
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                throw MarketDataProviderException.invalidResponse("Empty response from market data provider");
            }

            JsonNode root = objectMapper.readTree(responseBody);
            checkRateLimitOrError(root);

            JsonNode timeSeries = root.path("Time Series (Daily)");
            if (timeSeries.isMissingNode() || !timeSeries.isObject()) {
                throw MarketDataProviderException.invalidResponse("Missing 'Time Series (Daily)' in provider response");
            }

            List<ProviderPrice> result = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> fields = timeSeries.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                LocalDate date;
                try {
                    date = LocalDate.parse(entry.getKey(), DATE_FORMATTER);
                } catch (Exception ex) {
                    continue; // Skip malformed date
                }

                if (from != null && date.isBefore(from)) {
                    continue;
                }
                if (to != null && date.isAfter(to)) {
                    continue;
                }

                JsonNode dayNode = entry.getValue();
                BigDecimal open = parseDecimal(dayNode.path("1. open").asText(null));
                BigDecimal high = parseDecimal(dayNode.path("2. high").asText(null));
                BigDecimal low = parseDecimal(dayNode.path("3. low").asText(null));
                BigDecimal close = parseDecimal(dayNode.path("4. close").asText(null));
                Long volume = parseLong(dayNode.path("5. volume").asText(null));

                if (close == null) {
                    continue; // Close price is mandatory
                }

                // Integrity check: high must not be less than low
                if (high != null && low != null && high.compareTo(low) < 0) {
                    continue; // Skip inconsistent data
                }

                result.add(new ProviderPrice(date, open, high, low, close, volume));
            }

            // Return in ascending date order
            result.sort(Comparator.comparing(ProviderPrice::date));
            return result;
        } catch (MarketDataProviderException e) {
            throw e;
        } catch (ResourceAccessException e) {
            log.error("Network or timeout error contacting Alpha Vantage for symbol {}: {}", symbol, e.getMessage());
            throw MarketDataProviderException.unavailable("Market data provider is unreachable or timed out");
        } catch (RestClientResponseException e) {
            log.error("HTTP error from Alpha Vantage: status {}", e.getStatusCode());
            throw MarketDataProviderException.badGateway("Provider returned HTTP status: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("Unexpected error fetching daily prices for symbol {}: {}", symbol, e.getMessage());
            throw MarketDataProviderException.invalidResponse("Unable to parse market data provider prices response");
        }
    }

    private void checkRateLimitOrError(JsonNode root) {
        if (root.has("Note")) {
            String note = root.path("Note").asText();
            log.warn("Alpha Vantage rate limit note received: {}", note);
            throw MarketDataProviderException.unavailable("Market data provider call limit reached. " + note);
        }
        if (root.has("Information")) {
            String info = root.path("Information").asText();
            log.warn("Alpha Vantage information note received: {}", info);
            throw MarketDataProviderException.unavailable("Market data provider call limit reached. " + info);
        }
        if (root.has("Error Message")) {
            String errMsg = root.path("Error Message").asText();
            log.error("Alpha Vantage returned error: {}", errMsg);
            throw MarketDataProviderException.invalidResponse("Provider rejected query: Invalid symbol or request");
        }
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
