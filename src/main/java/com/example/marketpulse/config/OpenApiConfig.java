package com.example.marketpulse.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI marketPulseOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("MarketPulse API")
                        .description("Market Data Analytics REST API - Provides instrument metadata, historical price tracking, and quantitative analytics (Daily Change, SMA).")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("MarketPulse Engineering")
                                .url("https://github.com/example/marketpulse"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")));
    }
}
