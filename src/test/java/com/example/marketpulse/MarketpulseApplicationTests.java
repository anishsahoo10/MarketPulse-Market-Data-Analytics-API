package com.example.marketpulse;

import com.example.marketpulse.dto.DailyChangeResponse;
import com.example.marketpulse.dto.HistoricalPricesResponse;
import com.example.marketpulse.dto.InstrumentResponse;
import com.example.marketpulse.dto.MovingAverageResponse;
import com.example.marketpulse.dto.SyncResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MarketpulseApplicationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("Context loads and health actuator returns UP")
    void contextLoadsAndHealthIsUp() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/health", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("End-to-end flow: sync instrument, query details, query prices, calculate daily change & SMA")
    void testEndToEndSyncAndAnalytics() {
        String baseUrl = "http://localhost:" + port + "/api/v1/instruments/MSFT";

        // 1. Sync provider data
        ResponseEntity<SyncResponse> syncRes = restTemplate.postForEntity(
                baseUrl + "/sync", null, SyncResponse.class);
        assertThat(syncRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(syncRes.getBody()).isNotNull();
        assertThat(syncRes.getBody().symbol()).isEqualTo("MSFT");
        assertThat(syncRes.getBody().recordsInserted()).isGreaterThan(0);

        // 2. Query instrument metadata
        ResponseEntity<InstrumentResponse> instRes = restTemplate.getForEntity(
                baseUrl, InstrumentResponse.class);
        assertThat(instRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(instRes.getBody()).isNotNull();
        assertThat(instRes.getBody().symbol()).isEqualTo("MSFT");
        assertThat(instRes.getBody().name()).isEqualTo("Microsoft Corporation");

        // 3. Query historical prices
        ResponseEntity<HistoricalPricesResponse> pricesRes = restTemplate.getForEntity(
                baseUrl + "/prices", HistoricalPricesResponse.class);
        assertThat(pricesRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pricesRes.getBody()).isNotNull();
        assertThat(pricesRes.getBody().count()).isGreaterThan(0);

        // 4. Calculate daily price change
        ResponseEntity<DailyChangeResponse> changeRes = restTemplate.getForEntity(
                baseUrl + "/analytics/daily-change", DailyChangeResponse.class);
        assertThat(changeRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(changeRes.getBody()).isNotNull();
        assertThat(changeRes.getBody().symbol()).isEqualTo("MSFT");
        assertThat(changeRes.getBody().currentClose()).isNotNull();
        assertThat(changeRes.getBody().previousClose()).isNotNull();

        // 5. Calculate 20-period moving average
        ResponseEntity<MovingAverageResponse> smaRes = restTemplate.getForEntity(
                baseUrl + "/analytics/moving-average?period=20", MovingAverageResponse.class);
        assertThat(smaRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(smaRes.getBody()).isNotNull();
        assertThat(smaRes.getBody().period()).isEqualTo(20);
        assertThat(smaRes.getBody().simpleMovingAverage()).isNotNull();
    }
}
