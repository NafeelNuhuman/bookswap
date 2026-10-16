package com.bookswap.copy.domain;

import com.bookswap.common.error.ApiException;
import org.springframework.http.HttpStatus;

public class ProfileIncompleteException extends ApiException {
    public ProfileIncompleteException() {
        super(HttpStatus.CONFLICT, "PROFILE_INCOMPLETE",
                "Complete your profile (display name, city, postal code) before listing a copy");
    }
}
