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

    /** Recipient only: answers with a changed offer, which is a new proposal in the other direction. */
    @PostMapping("/{id}/counter")
    @ResponseStatus(HttpStatus.CREATED)
    public ProposalView counter(Principal principal, @PathVariable UUID id, @RequestBody ProposalRequest request) {
        return proposalService.counter(UUID.fromString(principal.getName()), id, request);
    }

    @PostMapping("/{id}/reject")
    public ProposalView reject(Principal principal, @PathVariable UUID id,
                               @RequestBody(required = false) RejectRequest request) {
        return proposalService.reject(UUID.fromString(principal.getName()), id,
                request == null ? null : request.reason());
    }

    /** Either party, while the exchange is accepted: where and when to meet. Visible to the two parties only. */
    @PutMapping("/{id}/pickup-note")
    public ProposalView pickupNote(Principal principal, @PathVariable UUID id,
                                   @RequestBody(required = false) PickupNoteRequest request) {
        return proposalService.setPickupNote(UUID.fromString(principal.getName()), id,
                request == null ? null : request.note());
    }

    /** Either party: "the swap happened". The second confirmation completes the exchange and moves the books. */
    @PostMapping("/{id}/confirm")
    public ProposalView confirm(Principal principal, @PathVariable UUID id) {
        return proposalService.confirm(UUID.fromString(principal.getName()), id);
    }

    /** The proposer while the proposal is open; either party after acceptance (see the exchange rules). */
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
