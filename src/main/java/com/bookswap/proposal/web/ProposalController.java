package com.bookswap.proposal.web;

import com.bookswap.proposal.service.ProposalService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/proposals")
public class ProposalController {
    private final ProposalService proposalService;

    public ProposalController(ProposalService proposalService) {
        this.proposalService = proposalService;
    }

    /** Offers 1-5 of the caller's available copies for 1-5 available copies of one other user. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProposalView propose(Principal principal, @RequestBody ProposalRequest request) {
        return proposalService.propose(UUID.fromString(principal.getName()), request);
    }

    @GetMapping("/{id}")
    public ProposalView get(Principal principal, @PathVariable UUID id) {
        return proposalService.get(UUID.fromString(principal.getName()), id);
    }
}
