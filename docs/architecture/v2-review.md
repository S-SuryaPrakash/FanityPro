# V2 — Secure review workflow

Active only with the `v2` Spring profile (PostgreSQL + Flyway). Without it the
application behaves exactly as V1: no database, no accounts, open V1 endpoints.

## What it adds

- **Accounts and login.** Local accounts with BCrypt password hashes and two
  roles, `ADMIN` and `ANALYST`. Login returns a short-lived HS256 JWT (30 min
  by default) that must be sent as `Authorization: Bearer <token>`.
- **Review cases.** Uploading a workbook to `/api/v2/batches` runs the same V1
  classification pipeline, then saves every *review-required* result as a
  review case. Messages with no automated flag are never persisted.
- **Decisions.** An analyst resolves each case exactly once:

  | Decision | Final category | Note |
  |---|---|---|
  | `CONFIRMED` | the automated category (not allowed for `MANUAL_REVIEW` results) | optional |
  | `OVERRIDDEN` | a different risk category, given as `finalCategory` | required |
  | `DISMISSED` | `NO_AUTOMATED_FLAG` (false positive) | required |

  A second decision gets `409 CASE_ALREADY_RESOLVED`. Two simultaneous
  decisions are caught by optimistic locking (`409 CONCURRENT_MODIFICATION`).
- **Audit trail.** Account creation, logins, uploads, and decisions are
  recorded in `audit_events`, in the same transaction as the action itself. A
  database trigger rejects any UPDATE or DELETE on that table. Message text,
  passwords, and tokens are never written to it.

## Endpoints

| Method | Path | Role |
|---|---|---|
| POST | `/api/v2/auth/login` | public |
| GET | `/api/v2/auth/me` | any |
| POST, GET | `/api/v2/users` | ADMIN |
| POST (multipart `file`) | `/api/v2/batches` | any |
| GET | `/api/v2/batches`, `/api/v2/batches/{id}` | any |
| GET | `/api/v2/cases?status=&batchId=&severity=&category=&page=&size=` | any |
| GET | `/api/v2/cases/{id}` | any |
| POST | `/api/v2/cases/{id}/decision` | any |
| GET | `/api/v2/audit-events?entityType=&entityId=&page=&size=` | ADMIN |

"Any" means any signed-in account (`ADMIN` or `ANALYST`). The case queue is
sorted most severe first, then by confidence, then oldest first. Errors use the
same ProblemDetail shape as V1 (`errorCode`, `correlationId`).

## Configuration

| Variable | Purpose |
|---|---|
| `CONTENT_FILTER_JWT_SECRET` | HMAC signing key, at least 32 bytes. Startup fails without it. |
| `CONTENT_FILTER_JWT_TTL` | Access-token lifetime (default `30m`). |
| `CONTENT_FILTER_BOOTSTRAP_ADMIN_EMAIL` / `_PASSWORD` | Creates the first admin, only while no account exists. |

`compose.yaml` supplies dev-only fallbacks for all three; override them for any
shared deployment.

## Code map

| Package | Contents |
|---|---|
| `account` | `AppUser` entity, `UserRole`, `UserAccountService` |
| `security` | `V2SecurityConfiguration` (JWT resource server, role rules), `OpenSecurityConfiguration` (non-v2: permit all), `AuthenticationService`, `AccessTokenService`, bootstrap admin |
| `review` | `ClassificationBatch`, `ReviewCase` (decision rules in `resolve`), batch and case services |
| `audit` | `AuditEvent` (immutable), `AuditService` |
| `controller` | `AuthController`, `UserController`, `BatchController`, `ReviewCaseController`, `AuditEventController` |
| `db/migration` | `V1__create_v2_auth_schema.sql`, `V2__create_review_schema.sql` |

## Known limits (next steps)

- V1 endpoints stay unauthenticated even under `v2`, for backward
  compatibility. Put them behind auth before exposing the API publicly.
- Tokens can't be revoked before they expire, and a disabled account's token
  stays valid until then. The short lifetime limits the exposure.
- The login endpoint has no rate limiting or lockout yet.
- Upload and classification are synchronous (see the roadmap's async-jobs item).
