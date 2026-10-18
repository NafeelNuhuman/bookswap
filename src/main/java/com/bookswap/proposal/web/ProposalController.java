package com.bookswap.proposal.web;

import com.bookswap.common.web.PageResponse;
import com.bookswap.proposal.domain.ProposalStatus;
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

    /** Recipient only: reserves every copy on both sides and cancels competing proposals. */
    @PostMapping("/{id}/accept")
    public ProposalView accept(Principal principal, @PathVariable UUID id) {
        return proposalService.accept(UUID.fromString(principal.getName()), id);
    }

    @PostMapping("/{id}/reject")
    public ProposalView reject(Principal principal, @PathVariable UUID id,
                               @RequestBody(required = false) RejectRequest request) {
        return proposalService.reject(UUID.fromString(principal.getName()), id,
                request == null ? null : request.reason());
    }

    /** Proposer only, while the proposal is still open. */
    @PostMapping("/{id}/cancel")
    public ProposalView cancel(Principal principal, @PathVariable UUID id) {
        return proposalService.cancel(UUID.fromString(principal.getName()), id);
    }

    /** Proposals made to the caller ({@code incoming}) or by the caller ({@code outgoing}), newest first. */
    @GetMapping
    public PageResponse<ProposalView> list(Principal principal,
                                           @RequestParam String role,
                                           @RequestParam(required = false) ProposalStatus status,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return proposalService.list(UUID.fromString(principal.getName()), role, status, page, size);
    }

    @GetMapping("/{id}")
    public ProposalView get(Principal principal, @PathVariable UUID id) {
        return proposalService.get(UUID.fromString(principal.getName()), id);
    }
}
