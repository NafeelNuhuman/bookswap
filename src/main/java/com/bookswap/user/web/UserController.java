package com.bookswap.user.web;

import com.bookswap.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/me")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
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
