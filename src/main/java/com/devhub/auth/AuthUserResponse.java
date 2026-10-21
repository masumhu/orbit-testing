package com.devhub.auth;

public record AuthUserResponse(String id, String username, String email, RegistrationRole role, String organizationId) { }
