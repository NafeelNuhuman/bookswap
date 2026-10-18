package com.bookswap.user.web;

import com.bookswap.user.service.AccountService;
import com.bookswap.user.service.UserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/users/me")
public class UserController {
    private final UserService userService;

    private final AccountService accountService;

    public UserController(UserService userService, AccountService accountService) {
        this.userService = userService;
        this.accountService = accountService;
    }

    /** Irreversible: anonymizes the account. Needs the current password. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Principal principal, @Valid @RequestBody DeleteAccountRequest request) {
        accountService.delete(UUID.fromString(principal.getName()), request.password());
    }

    @GetMapping
    public ProfileResponse me(Principal principal) {
        return userService.getProfile(UUID.fromString(principal.getName()));
    }

    @PutMapping
    public ProfileResponse update(Principal principal, @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(UUID.fromString(principal.getName()), request);
    }
}
