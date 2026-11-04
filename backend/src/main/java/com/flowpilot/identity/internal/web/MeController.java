package com.flowpilot.identity.internal.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flowpilot.identity.IdentityApi;
import com.flowpilot.identity.internal.web.AuthDtos.UserResponse;
import com.flowpilot.shared.security.AuthenticatedUser;
import com.flowpilot.shared.web.ApiException;

@RestController
@RequestMapping("/api/v1/me")
class MeController {

    private final IdentityApi identity;

    MeController(IdentityApi identity) {
        this.identity = identity;
    }

    @GetMapping
    UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return identity.findUser(user.userId())
                .map(UserResponse::from)
                .orElseThrow(() -> ApiException.notFound("User not found."));
    }
}
