package com.devhub.auth;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface RegistrationChallengeRepository extends MongoRepository<RegistrationChallenge, String> { }
