package com.bookswap.copy.web;

import com.bookswap.common.web.PageResponse;
import com.bookswap.copy.domain.CopyStatus;
import com.bookswap.copy.service.CopyService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/copies")
public class CopyController {
    private final CopyService copyService;

    public CopyController(CopyService copyService) {
        this.copyService = copyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CopyResponse add(Principal principal, @Valid @RequestBody AddCopyRequest request) {
        return copyService.add(owner(principal), request);
    }

    @GetMapping
    public PageResponse<CopyResponse> list(Principal principal,
                                           @RequestParam(required = false) CopyStatus status,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return copyService.list(owner(principal), status, page, size);
    }

    @GetMapping("/{id}")
    public CopyResponse get(Principal principal, @PathVariable UUID id) {
        return copyService.get(owner(principal), id);
    }

    @PutMapping("/{id}")
    public CopyResponse update(Principal principal, @PathVariable UUID id, @Valid @RequestBody UpdateCopyRequest request) {
        return copyService.changeGrade(owner(principal), id, request.grade());
    }

    @PostMapping("/{id}/list")
    public CopyResponse list(Principal principal, @PathVariable UUID id) {
        return copyService.listCopy(owner(principal), id);
    }

    @PostMapping("/{id}/unlist")
    public CopyResponse unlist(Principal principal, @PathVariable UUID id) {
        return copyService.unlistCopy(owner(principal), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Principal principal, @PathVariable UUID id) {
        copyService.remove(owner(principal), id);
    }

    private static UUID owner(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
