# Orbit

Spring Boot and MongoDB API for organization-based project collaboration.

## Organization onboarding

Every registration request includes `organizationMode`:

- `CREATE`: provide `organizationName`. After OTP verification, the registrant becomes the organization `OWNER`; the response includes the generated `organizationId`.
- `JOIN`: provide `organizationId`, a role (`PRODUCT_MANAGER`, `SENIOR_MANAGER`, or `DEVELOPER`), and the matching company access code.

An organization can have one Product Manager. It can have multiple Senior Managers and Developers.

After creating the organization, the owner signs in and sets three distinct role access codes using `PUT /api/v1/organizations/{organizationId}/access-codes`:

```json
{
  "productManagerCode": "...",
  "seniorManagerCode": "...",
  "developerCode": "..."
}
```

Codes must be at least eight characters and are stored as BCrypt hashes. The owner can view the organization roster, projects, tasks, tickets, and submission metadata, and can change role codes. Owners cannot access chat or perform other write actions. The owner must transfer ownership before leaving; ownership transfer is not implemented yet.

## Registration and login

Request a verification code with `POST /api/v1/auth/registrations/email/request-otp` or `POST /api/v1/auth/registrations/{google|github}/request-otp`, then verify it with `POST /api/v1/auth/registrations/verify-otp`. Every registration requires email OTP verification. Google tokens are validated against `GOOGLE_OAUTH_CLIENT_ID`. GitHub registration/login uses an OAuth access token with `read:user` and `user:email` scopes.

Login and session endpoints:

- `POST /api/v1/auth/login` — email or username and password.
- `POST /api/v1/auth/login/{google|github}` — provider token (the GitHub token path is useful for native clients).
- `POST /api/v1/auth/login/github/callback` — exchange a GitHub OAuth authorization code for Orbit access and refresh tokens. The frontend must start GitHub OAuth with the configured callback URL, validate the returned `state`, then post `{"code":"..."}` to this endpoint. Existing users must have registered through the same GitHub identity.
- `POST /api/v1/auth/refresh` — rotates a 30-day opaque refresh token.
- `POST /api/v1/auth/logout` — revokes a refresh-token session.

Access tokens are 15-minute JWTs. Refresh tokens are stored only as SHA-256 hashes.

## Projects, tasks, submissions, and tickets

- A Product Manager creates projects and assigns Senior Managers: `POST /api/v1/projects`.
- A Senior Manager creates tasks in an assigned project, then assigns each task to a Developer.
- Developers submit a branch/commit, pull request, or external storage reference with `POST /api/v1/tasks/{taskId}/submissions`.
- Senior Managers review and forward submissions. Product Managers accept a forwarded submission or request changes; requesting changes creates a ticket for the task's Senior Manager.
- Product Managers can also create tickets directly. Tickets contain title, description, priority (`LOW`, `MEDIUM`, `HIGH`), status (`OPEN`, `IN_PROCESS`, `CLOSED`), and code location. The Product Manager sets priority; the assigned Senior Manager updates status and assigns the ticket to a Developer. Ticket changes are recorded in history.
- Developers and Senior Managers can leave a project after their active tasks and tickets are reassigned or closed. Leaving removes project access, including channel access.
- Organization members can leave with `POST /api/v1/organizations/{organizationId}/leave`; the owner must transfer ownership first. Project members can leave with `POST /api/v1/projects/{projectId}/leave` after closing or reassigning active work.

Code itself is not stored in MongoDB. Orbit stores submission metadata and references (repository, branch, commit, pull request, or external storage reference); source files remain in Git or the configured external storage.

## Channels and chat

Product Managers and Senior Managers can create organization or project channels, add members, and remove members. Project channels are limited to assigned project members. Organization owners have no chat access. Messages are stored through REST endpoints and delivered live over STOMP WebSocket at `/ws`; connect with `Authorization: Bearer <access-token>`, subscribe to `/topic/channels/{channelId}`, and publish to `/app/channels/{channelId}/messages`.

When a user leaves an organization or project, authorization checks immediately block access to its channels. Message authors can edit or soft-delete their own messages.

## Local setup

1. Start MongoDB locally or set `MONGODB_URI`.
2. Configure SMTP, `OTP_HMAC_SECRET`, and `JWT_SECRET` in environment variables or a local `.env` file. Both secrets must be at least 32 characters.
3. Set `GOOGLE_OAUTH_CLIENT_ID` for Google auth. For GitHub, set `GITHUB_OAUTH_CLIENT_ID`, `GITHUB_OAUTH_CLIENT_SECRET`, and `GITHUB_OAUTH_REDIRECT_URI` to the exact callback URL configured in your GitHub OAuth App. Request `read:user` and `user:email`; keep the client secret on the backend. The frontend should generate and verify an unpredictable OAuth `state` value.
4. Set `DEVHUB_WEBSOCKET_ALLOWED_ORIGIN_PATTERNS` to the client origins.
5. Run `mvn spring-boot:run`.

Swagger UI is available at `/swagger-ui.html`; health is available at `/actuator/health`.
