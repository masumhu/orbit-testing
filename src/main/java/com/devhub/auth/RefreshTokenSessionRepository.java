package com.devhub.auth;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RefreshTokenSessionRepository extends MongoRepository<RefreshTokenSession, String> {
    Optional<RefreshTokenSession> findByTokenHash(String tokenHash);
}