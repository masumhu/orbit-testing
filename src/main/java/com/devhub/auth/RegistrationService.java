package com.devhub.auth;

import com.devhub.auth.RegistrationRequests.EmailOtpRequest;
import com.devhub.auth.RegistrationRequests.OtpIssuedResponse;
import com.devhub.auth.RegistrationRequests.RegistrationResponse;
import com.devhub.auth.RegistrationRequests.SocialOtpRequest;
import com.devhub.auth.RegistrationRequests.VerifyOtpRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class RegistrationService {
    private final UserAccountRepository users;
    private final RegistrationChallengeRepository challenges;
    private final RegistrationProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final OtpEmailSender emailSender;
    private final SocialIdentityVerifier identityVerifier;
    private final SecureRandom random = new SecureRandom();

    public RegistrationService(UserAccountRepository users, RegistrationChallengeRepository challenges,
                               RegistrationProperties properties, PasswordEncoder passwordEncoder,
                               OtpEmailSender emailSender, SocialIdentityVerifier identityVerifier) {
        this.users = users;
        this.challenges = challenges;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.identityVerifier = identityVerifier;
    }

    public OtpIssuedResponse requestEmailOtp(EmailOtpRequest request) {
        String email = normalizeEmail(request.email());
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        checkRoleCode(request.role(), request.companyAccessCode());
        if (users.existsByEmail(email)) throw new IllegalStateException("An account already exists for this email");
        if (users.existsByUsername(username)) throw new IllegalStateException("Username is unavailable");
        String otp = generateOtp();
        RegistrationChallenge challenge = new RegistrationChallenge(UUID.randomUUID().toString(), email, username,
                passwordEncoder.encode(request.password()), request.role(), RegistrationMethod.EMAIL, null,
                otpHash(otp), 0, Instant.now().plus(properties.otpTtl()), Instant.now());
        challenges.save(challenge);
        emailSender.send(email, otp);
        return new OtpIssuedResponse(challenge.id(), challenge.expiresAt());
    }

    public OtpIssuedResponse requestSocialOtp(RegistrationMethod method, SocialOtpRequest request) {
        checkRoleCode(request.role(), request.companyAccessCode());
        ProviderIdentity identity = identityVerifier.verify(method, request.providerToken());
        if (users.existsByEmail(identity.email())) throw new IllegalStateException("An account already exists for this email");
        String username = uniqueUsername(identity.username(), identity.email());
        String otp = generateOtp();
        RegistrationChallenge challenge = new RegistrationChallenge(UUID.randomUUID().toString(), identity.email(), username,
                null, request.role(), method, identity.subject(), otpHash(otp), 0,
                Instant.now().plus(properties.otpTtl()), Instant.now());
        challenges.save(challenge);
        emailSender.send(identity.email(), otp);
        return new OtpIssuedResponse(challenge.id(), challenge.expiresAt());
    }

    public RegistrationResponse verifyOtp(VerifyOtpRequest request) {
        RegistrationChallenge challenge = challenges.findById(request.challengeId())
                .orElseThrow(() -> new NoSuchElementException("Registration challenge was not found or has expired"));
        if (!challenge.expiresAt().isAfter(Instant.now())) {
            challenges.delete(challenge);
            throw new IllegalArgumentException("Verification code expired");
        }
        if (challenge.attempts() >= properties.maxOtpAttempts()) {
            challenges.delete(challenge);
            throw new IllegalArgumentException("Too many verification attempts");
        }
        if (!MessageDigest.isEqual(challenge.otpHash().getBytes(StandardCharsets.UTF_8), otpHash(request.otp()).getBytes(StandardCharsets.UTF_8))) {
            challenges.save(new RegistrationChallenge(challenge.id(), challenge.email(), challenge.username(), challenge.passwordHash(),
                    challenge.role(), challenge.method(), challenge.providerSubject(), challenge.otpHash(), challenge.attempts() + 1,
                    challenge.expiresAt(), challenge.createdAt()));
            throw new IllegalArgumentException("Verification code is invalid");
        }
        if (users.existsByEmail(challenge.email()) || (challenge.username() != null && users.existsByUsername(challenge.username()))) {
            challenges.delete(challenge);
            throw new IllegalStateException("Email or username is already registered");
        }
        Instant verifiedAt = Instant.now();
        UserAccount user = users.save(new UserAccount(null, challenge.email(), challenge.username(), challenge.passwordHash(),
                challenge.role(), challenge.method(), challenge.providerSubject(), verifiedAt, verifiedAt));
        challenges.delete(challenge);
        return new RegistrationResponse(user.id(), user.username(), user.email(), user.role(), user.registrationMethod(), user.emailVerifiedAt());
    }

    private void checkRoleCode(RegistrationRole role, String suppliedCode) {
        String expected = switch (role) {
            case PRODUCT_MANAGER -> properties.productManagerAccessCode();
            case SENIOR_MANAGER -> properties.seniorManagerAccessCode();
            case DEVELOPER -> null;
        };
        if (expected == null) return;
        if (expected.isBlank() || suppliedCode == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), suppliedCode.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("A valid company access code is required for this role");
        }
    }

    private String otpHash(String otp) {
        try {
            if (properties.otpHmacSecret() == null || properties.otpHmacSecret().length() < 32) throw new IllegalStateException("OTP_HMAC_SECRET must be configured with at least 32 characters");
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.otpHmacSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(otp.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("OTP hashing is unavailable", ex);
        }
    }

    private String generateOtp() { return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000)); }
    private String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private String uniqueUsername(String suggested, String email) {
        String base = (suggested == null || suggested.isBlank() ? email.substring(0, email.indexOf('@')) : suggested)
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "");
        if (base.length() < 3) base = "user";
        base = base.substring(0, Math.min(base.length(), 32));
        String candidate = base;
        for (int i = 1; users.existsByUsername(candidate); i++) candidate = base.substring(0, Math.min(base.length(), 34)) + i;
        return candidate;
    }
}
