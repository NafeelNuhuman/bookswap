package com.bookswap.discovery.web;

import com.bookswap.common.web.PageResponse;
import com.bookswap.discovery.service.DiscoveryService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@SecurityRequirement(name = "bearerAuth")
@RestController
public class DiscoveryController {
    private final DiscoveryService discoveryService;

    public DiscoveryController(DiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    /** Other people's available copies in a city (default: the caller's own), newest first. */
    @GetMapping("/api/feed")
    public PageResponse<CopyCard> feed(Principal principal,
                                       @RequestParam(required = false) String city,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return discoveryService.feed(UUID.fromString(principal.getName()), city, page, size);
    }

    /** The same cards, narrowed by title/author text or an exact ISBN. */
    @GetMapping("/api/search")
    public PageResponse<CopyCard> search(Principal principal,
                                         @RequestParam String q,
                                         @RequestParam(required = false) String city,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return discoveryService.search(UUID.fromString(principal.getName()), q, city, page, size);
    }
}
