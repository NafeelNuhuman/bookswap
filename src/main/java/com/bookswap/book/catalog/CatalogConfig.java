package com.bookswap.book.catalog;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(CatalogProperties.class)
class CatalogConfig {

    @Bean
    BookCatalog openLibraryCatalog(CatalogProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.connectTimeout());
        factory.setReadTimeout(props.readTimeout());
        return new OpenLibraryCatalog(RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory)
                .build());
    }
}
