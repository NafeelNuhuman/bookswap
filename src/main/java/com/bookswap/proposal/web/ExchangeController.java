package com.bookswap.proposal.web;

import com.bookswap.common.web.PageResponse;
import com.bookswap.proposal.service.ProposalService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/exchanges")
public class ExchangeController {
    private final ProposalService proposalService;

    public ExchangeController(ProposalService proposalService) {
        this.proposalService = proposalService;
    }

    /** The caller's completed exchanges, most recently completed first. */
    @GetMapping
    public PageResponse<ExchangeView> history(Principal principal,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return proposalService.history(UUID.fromString(principal.getName()), page, size);
    }
}
