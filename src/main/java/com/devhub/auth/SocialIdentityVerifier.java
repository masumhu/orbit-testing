package com.devhub.auth;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class SocialIdentityVerifier {
    private final RestClient restClient = RestClient.create();
    private final RegistrationProperties properties;

    public SocialIdentityVerifier(RegistrationProperties properties) {
        this.properties = properties;
    }

    public ProviderIdentity verify(RegistrationMethod method, String token) {
        try {
            return switch (method) {
                case GOOGLE -> verifyGoogle(token);
                default -> throw new IllegalArgumentException("Unsupported social registration method");
            };
        } catch (RestClientException ex) {
            throw new IllegalArgumentException("Could not verify identity with the selected provider");
        }
    }

    private ProviderIdentity verifyGoogle(String idToken) {
        if (properties.googleClientId() == null || properties.googleClientId().isBlank()) {
            throw new IllegalStateException("Google sign-in is not configured");
        }
        Map<?, ?> claims = restClient.get()
                .uri("https://oauth2.googleapis.com/tokeninfo?id_token={token}", idToken)
                .retrieve().body(Map.class);
        if (claims == null || !properties.googleClientId().equals(claims.get("aud")) || !"true".equals(String.valueOf(claims.get("email_verified")))) {
            throw new IllegalArgumentException("Google identity token is invalid or email is not verified");
        }
        return verifiedIdentity(claims.get("sub"), claims.get("email"), valueOrEmpty(claims.get("name")));
    }

    private ProviderIdentity verifiedIdentity(Object subject, Object email, String username) {
        if (subject == null || subject.toString().isBlank() || email == null || !email.toString().contains("@")) {
            throw new IllegalArgumentException("Provider did not return a verified account identity");
        }
        return new ProviderIdentity(subject.toString(), email.toString().trim().toLowerCase(java.util.Locale.ROOT), username);
    }

    private String valueOrEmpty(Object value) {
         return value == null ? "" : value.toString(); }
}
