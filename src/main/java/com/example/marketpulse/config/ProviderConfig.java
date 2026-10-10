package com.example.marketpulse.config;

import com.example.marketpulse.provider.AlphaVantageMarketDataProvider;
import com.example.marketpulse.provider.MarketDataProvider;
import com.example.marketpulse.provider.MockMarketDataProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProviderConfig {

    private static final Logger log = LoggerFactory.getLogger(ProviderConfig.class);

    @Value("${marketpulse.provider.type:mock}")
    private String providerType;

    @Value("${marketpulse.provider.alpha-vantage.base-url:https://www.alphavantage.co}")
    private String alphaVantageBaseUrl;

    @Value("${marketpulse.provider.alpha-vantage.api-key:demo}")
    private String alphaVantageApiKey;

    @Value("${marketpulse.provider.alpha-vantage.timeout-seconds:10}")
    private int timeoutSeconds;

    @Bean
    public MarketDataProvider marketDataProvider(ObjectMapper objectMapper) {
        if ("alphavantage".equalsIgnoreCase(providerType)) {
            log.info("Initializing AlphaVantageMarketDataProvider (baseUrl: {}, timeout: {}s)",
                    alphaVantageBaseUrl, timeoutSeconds);
            return new AlphaVantageMarketDataProvider(
                    alphaVantageBaseUrl, alphaVantageApiKey, timeoutSeconds, objectMapper);
        } else {
            log.info("Initializing MockMarketDataProvider for local/demo market data simulation");
            return new MockMarketDataProvider();
        }
    }
}
