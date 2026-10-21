package com.devhub.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.devhub.auth.RegistrationRequests.EmailOtpRequest;
import com.devhub.auth.RegistrationRequests.OtpIssuedResponse;
import com.devhub.auth.RegistrationRequests.RegistrationResponse;
import com.devhub.auth.RegistrationRequests.SocialOtpRequest;
import com.devhub.auth.RegistrationRequests.VerifyOtpRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth/registrations")
public class RegistrationController {
    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) { this.registrationService = registrationService; }

    @PostMapping("/email/request-otp")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OtpIssuedResponse requestEmailOtp(@Valid @RequestBody EmailOtpRequest request) {
        return registrationService.requestEmailOtp(request);
    }

    @PostMapping("/{provider}/request-otp")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OtpIssuedResponse requestSocialOtp(@PathVariable String provider, @Valid @RequestBody SocialOtpRequest request) {
        RegistrationMethod method = switch (provider.toLowerCase()) {
            case "google" -> RegistrationMethod.GOOGLE;
            default -> throw new IllegalArgumentException("Provider must be google");
        };
        return registrationService.requestSocialOtp(method, request);
    }

    @PostMapping("/verify-otp")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        return registrationService.verifyOtp(request);
    }
}