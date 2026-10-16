package com.payflow.common.security;

/**
 * The logged-in user, as read from a verified JWT.
 * Controllers receive this through {@code @AuthenticationPrincipal}.
 */
public record AuthUser(Long id, String email, Role role) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
