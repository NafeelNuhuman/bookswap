package com.bookswap.common.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/_probe")
public class ErrorProbeController {

    public record Body(@NotBlank String name, @Min(1) int count) {}

    @PostMapping("/validate")
    public void validate(@Valid @RequestBody Body body) {}

    @GetMapping("/api-error")
    public void apiError() {
        throw new ApiException(HttpStatus.CONFLICT, "PROBE_CONFLICT", "probe conflict");
    }

    @GetMapping("/lock")
    public void lock() {
        throw new ObjectOptimisticLockingFailureException(Object.class, 1L);
    }

    @GetMapping("/boom")
    public void boom() {
        throw new IllegalStateException("secret internals");
    }
}
