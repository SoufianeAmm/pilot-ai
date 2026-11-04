package com.flowpilot.identity.internal.application;

public record AuthTokens(AccessToken accessToken, IssuedRefreshToken refreshToken) {
}
