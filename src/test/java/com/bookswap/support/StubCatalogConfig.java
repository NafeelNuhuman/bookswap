package com.bookswap.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class StubCatalogConfig {
    @Bean
    @Primary
    StubBookCatalog stubBookCatalog() {
        return new StubBookCatalog();
    }
}
