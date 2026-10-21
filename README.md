# Orbit Authentication API

Spring Boot and MongoDB backend containing account registration and authentication only.

## Registration

1. Request an email verification code with `POST /api/v1/auth/registrations/email/request-otp`, or start Google registration with `POST /api/v1/auth/registrations/google/request-otp`.
2. Verify the emailed code with `POST /api/v1/auth/registrations/verify-otp` to create the account.

All registration methods verify an email code. Product managers and senior managers must provide their configured company access code; developers do not need one. Google ID tokens are verified server-side against `GOOGLE_OAUTH_CLIENT_ID`.

## Login and tokens

- `POST /api/v1/auth/login` accepts `{ "identifier": "email-or-username", "password": "..." }`.
- `POST /api/v1/auth/login/google` accepts `{ "providerToken": "google-id-token" }`.
- `POST /api/v1/auth/refresh` accepts `{ "refreshToken": "..." }` and rotates the token.
- `POST /api/v1/auth/logout` accepts `{ "refreshToken": "..." }` and revokes that session.

Successful login and refresh return a 15-minute JWT access token and a 30-day opaque refresh token. Refresh tokens are stored as SHA-256 hashes and rotate on refresh. Social login works only for accounts registered through the same provider; provider linking is not implemented.

## Local setup

1. Start MongoDB locally or set `MONGODB_URI`.
2. Configure the mail server, `OTP_HMAC_SECRET`, `JWT_SECRET`, and role access codes in environment variables or a local `.env` file. Both secrets must be at least 32 characters.
3. Set `GOOGLE_OAUTH_CLIENT_ID` to enable Google registration/login.
4. Start the API with `mvn spring-boot:run`.

Swagger UI is available at `/swagger-ui.html`; health is available at `/actuator/health`.
