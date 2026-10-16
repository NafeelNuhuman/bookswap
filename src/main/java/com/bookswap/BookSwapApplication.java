package com.bookswap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BookSwapApplication {
    public static void main(String[] args) {
        SpringApplication.run(BookSwapApplication.class, args);
    }
}
