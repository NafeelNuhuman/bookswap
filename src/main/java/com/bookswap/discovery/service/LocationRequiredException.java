package com.bookswap.discovery.service;

import com.bookswap.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class LocationRequiredException extends ApiException {
    public LocationRequiredException() {
        super(HttpStatus.BAD_REQUEST, "LOCATION_REQUIRED",
                "Set a city in your profile or pass a city to search in");
    }
}
