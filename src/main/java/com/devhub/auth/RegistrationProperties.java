package com.devhub.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("devhub.registration")
public record RegistrationProperties(
        Duration otpTtl,
        int maxOtpAttempts,
        String otpHmacSecret,
        String productManagerAccessCode,
        String seniorManagerAccessCode,
        String googleClientId
) { }
