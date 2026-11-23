# UniSpace — B3-D10.20 Authentication OpenAPI 3.1 Review Notes

**Version:** 0.2.0-review (B3-I02)

**Date:** 24 November 2026  
**Status:** REVIEW CANDIDATE — not final-approved, not implementation authorization  
**Companion:** `UniSpace_B3_D10_20_Authentication_OpenAPI_3_1_REVIEW.yaml`

## 1. Scope and approval boundary

The D10.20 candidate now incorporates D10.25–D10.28. The earlier D10.20 submission remains the historical baseline. Wire fields and HTTP mappings below are proposals for final review. [Current authentication decisions](../../architecture/B3/UniSpace_B3_Current_Authentication_Decisions.md) The controlled product baseline remains 167 approved FRs (150 MVP, 17 post-MVP). The approved Stage 4.3 Group A UX v0.2 is the UI routing authority. **This draft does not expand FRs, authorize code, or authorize a Flyway migration.**

**Not in MVP:** provider unlinking, lost-university-email administrative recovery API, sign-out-all, remote session/device management, and a hard maximum trusted-device count. These are absent from the YAML.

## 2. Endpoint inventory

| # | Method | Route | Contract response | UX / purpose |
|---:|---|---|---|---|
| 1 | `POST` | `/api/v1/auth/challenges` | `201` | AUTH-02 / provider-link |
| 2 | `POST` | `/api/v1/auth/provider-exchange` | `200` | AUTH-02 |
| 3 | `GET` | `/api/v1/me/onboarding` | `200` | AUTH-01 / AUTH-03–05 |
| 4 | `PUT` | `/api/v1/me/onboarding/profile` | `200` | AUTH-03 |
| 5 | `POST` | `/api/v1/me/university-email/challenges` | `201` | AUTH-04 / AUTH-05 |
| 6 | `POST` | `/api/v1/me/university-email/verify` | `200` | AUTH-05 |
| 7 | `POST` | `/api/v1/auth/device-challenges` | `201` | AUTH-06 / silent proof |
| 8 | `POST` | `/api/v1/auth/device-challenges/{id}/verify-otp` | `200` | AUTH-06 |
| 9 | `POST` | `/api/v1/auth/device-challenges/{id}/register-key` | `200` | Silent device-key enrollment |
| 10 | `POST` | `/api/v1/auth/device-challenges/{id}/verify-proof` | `200` | Silent possession proof |
| 11 | `POST` | `/api/v1/auth/refresh` | `200` | AUTH-01 silent refresh |
| 12 | `POST` | `/api/v1/auth/sign-out` | `204` | Profile → Sign Out |
| 13 | `GET` | `/api/v1/me/linked-providers` | `200` | Settings → Security → Linked Providers |
| 14 | `POST` | `/api/v1/me/linked-providers` | `200` | Settings → Security → Linked Providers |

**Endpoint count: 14 operations on 13 paths.** Google is the first live provider. Native Apple contracts remain in the candidate for later integration. The Apple browser callback required on Android is an unresolved external auth-integration boundary, not silently included in that number.

## 3. Approved security constants

| Policy | Approved |
|---|---|
| Access JWT | RS256, RSA 3072-bit signing key, 20 minutes |
| Restricted flow JWT | RS256, 15 minutes, distinct audience/purpose; no refresh token |
| Refresh session | 30-day sliding inactivity, opaque rotation every successful refresh |
| Provider authentication challenge | 5 minutes, >=256-bit nonce, single use |
| Device signature challenge | 2 minutes, single use |
| Device proof | ECDSA P-256 / SHA-256, base64url-encoded DER signature, RFC 7638 SHA-256 public JWK thumbprint and server-generated canonical bytes |
| University email OTP | 10 minutes, 60-second resend, five failures invalidate, single use |
| Refresh replay | Every recognized spent token revokes its session/family, including an immediate duplicate. Flutter serializes refresh calls. |
| Device trust | No fixed expiry; ends on sign-out, explicit revocation or security event |
| Error transport | RFC 9457 Problem Details + stable application code and traceId |

## 4. Authentication/UX routing contract

The proposed `AuthFlowResponse` returns `status`, `accountStatus`, `nextAction` and restricted `flowToken` where more steps remain; `SessionResponse` returns the ACTIVE session, 20-minute access token and rotating refresh token only after verification. API enums below are **proposed wire names**, not database states.

| `nextAction` | Flutter route/behavior |
|---|---|
| `PROFILE_REQUIRED` | AUTH-03 Personal Details (1/3) |
| `EMAIL_REQUIRED` | AUTH-04 University Email (2/3) |
| `EMAIL_VERIFICATION_REQUIRED` | AUTH-05 OTP (3/3) |
| `DEVICE_PROOF_REQUIRED` | Silent trusted-key signature |
| `DEVICE_OTP_REQUIRED` | AUTH-06 existing-user university OTP |
| `DEVICE_KEY_REGISTRATION_REQUIRED` | Silent key enrollment after verified email |
| `READY` | Home only after authorized session |

Account statuses remain `PENDING_PROFILE`, `PENDING_EMAIL_VERIFICATION`, `ACTIVE`, `DISABLED`. Google/Apple provider email **does not** satisfy university email verification. First-registration OTP also authorizes initial key enrollment; do not request an additional device OTP. Ordinary restart preserves incomplete registration; reinstall requires fresh provider auth and restarts onboarding safely. No compromised/expired token accesses private resources.

## 5. Blocking and review-pending items

| Priority | Item | Why it blocks final sign-off |
|---|---|---|
| DEFERRED | Live Apple integration | Preserve Apple proof and linking schemas. Review the Android callback, state and one-time handoff before live Apple work. |
| BLOCKER | Credential and challenge sequencing | Confirm provider-specific nonce representation (native iOS vs Android Google Credential Manager vs Apple browser), challenge consumption, flow-token renewal/recovery and new-device OTP resend semantics. |
| REVIEW | Sign-out diagnostics | The verified refresh fallback and confirmation rule are approved. Review error precedence for conflicting credentials. |
| REVIEW | Refresh persistence | Verify atomic family revocation for immediate duplicates and lost responses. Determine digest retention for sliding sessions. |
| BLOCKER | Persistence and migrations | Check existing Flyway V1 schema for key storage, auth-flow challenges and consumed refresh history; approve only forward-only V2 if necessary. |
| REVIEW | Exact validation rules | Final name lengths, Student ID rules from active Admin policy, OTP code format/length, exact JWK and DER interoperability. |
| REVIEW | Device security fallback | Secure software-key fallback and interruption/cancellation behavior; hardware attestation remains deferred. |
| REVIEW | Error registry | Finalize per-operation code/status mapping and response after five wrong OTP attempts vs request rate-limiting. |
| REVIEW | Integration tests | Golden OpenAPI contract tests, provider token negatives, expired-flow routing, current-status verification, device concurrency and Flutter SDK checks. |

## 6. Specific transport and security notes

1. `POST /auth/challenges` has conditional security: `SIGN_IN` is public; `LINK_PROVIDER` requires an ACTIVE access JWT. Standard OpenAPI security alternatives alone do **not** express a request-body-dependent guard; the YAML records this as an `x-unispace-conditional-security` contract invariant.
2. `POST /auth/refresh` uses an opaque refresh credential in the body. Its `security: []` means there is no bearer JWT prerequisite, **not** that refresh is an anonymous, unvalidated operation. The service validates the opaque token and authoritative MySQL session/account state.
3. `POST /auth/sign-out` accepts an ACTIVE bearer JWT, or a verified refresh credential for expired access JWT use, and revokes only its current device/session. A revoked session or absence of a usable credential yields no confirmed sign-out. An offline client clears protected local state and must distinguish that from confirmed server revocation.
4. Restricted and normal JWTs use different validation policies, even though both transport by the Bearer HTTP scheme; neither may substitute for the other. Enforce `iss`, `aud`, `exp`, `nbf`, `kid`, token purpose, and active flow/session state server-side.
5. Token-bearing success responses must include `Cache-Control: no-store`. OTPs, private keys, refresh digests, provider assertions and security secrets are never returned in nonessential payloads or logged.
6. Several wire-level details in the YAML are draft constraints (e.g., challenge field names and key JWK encoding) despite the approved architecture. Do not run code generation or implement the API as if they were final without B3-D10.20 explicit sign-off.

### Device handles and proof sequencing

After registration OTP, send `{}` to `POST /auth/device-challenges`. Use the returned enrollment `challengeId` for `register-key`. For a new device on an existing account, use the enrollment ID for `verify-otp`, then reuse it for `register-key`. Key registration returns a **fresh signature challenge ID**. Use that ID with `verify-proof`. A returning trusted device can obtain a signature challenge directly by supplying its saved `deviceId`.

Decode `payloadToSign` from base64url and sign those exact server bytes. The server checks user, flow, operation and RFC 7638 key thumbprint before consuming the two-minute challenge. The successful proof response issues the opaque `deviceId`. Refresh returns the same device handle. The client retrieves it locally for subsequent sign-ins. Without a handle, submit `{}` and follow the server's flow decision. These field and ID conventions are candidate wire details.

### Credential results and errors

Provider exchange returns only a restricted flow credential for onboarding or device verification. Only successful device proof and refresh return `SessionResponse` with `AUTHENTICATED`, `ACTIVE`, `READY`, the two credentials and the current device handle. Disabled accounts cannot enter either success branch. The eight onboarding/device operations require a live, step-authorized flow. Both linked-provider operations require current access authorization, and linking additionally requires fresh provider proof.

All documented errors share RFC 9457 `application/problem+json` with `type`, `title`, HTTP-matching `status`, nonempty `code` and `traceId`. In this candidate, `401 REFRESH_TOKEN_REUSED` is terminal for the entire affected family even after an immediate retry. Its successor is unusable. An unknown credential yields `401 REFRESH_INVALID`. Other devices' families remain valid. Sign-out returns `204` only when session and current-device trust revocation are confirmed. A replay error on sign-out establishes no such confirmation for device trust.

## 7. Traceability

| Decision / behavior | Source |
|---|---|
| Provider sign-in/registration | FR-002–004, FR-113–124; UF-AUTH-01; AUTH-02–05 |
| Sessions/trusted devices | FR-006–007, FR-125–131; FR-158; UF-AUTH-02/03/06 |
| Onboarding recovery | FR-132–137; UF-AUTH-04/05 |
| University OTP policy | FR-120–123, FR-156; Stage 4.3 Group A v0.2 |
| Provider linking | FR-167; ADD-FR-167; Stage 4.3 Group A v0.2 |
| JWT, refresh, device signature, challenge limits | B3-D01–D09 and D10.7–D10.14, as updated by D10.25–D10.28 |
| Exact REST paths/DTOs | B3-D10.18A–C proposed wire format; D10.19 consolidation approved; this document D10.20 review |

## 8. Primary sources and protocol references

- `UniSpace_Functional_Requirements_v1_5(3).pdf` and `UniSpace_Functional_Requirements_Addendum_v1_0(1).pdf`.
- `UniSpace_User_Flows_and_User_Journeys_v1_0(2).pdf` and `UniSpace_User_Flows_and_User_Journeys_Addendum_v1_1_FINAL(1).md`.
- `UniSpace_Stage4_3_Authentication_Backend_B3_1_Alignment_v0_2(1).md`.
- `UniSpace_B3_Authentication_Approval_Audit_and_UIUX_Alignment_v1.0.md`.
- OpenAPI 3.1 Specification: https://spec.openapis.org/oas/v3.1.0.html
- Spring Security Resource Server JWT: https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html
- Google Credential Manager Sign in with Google: https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation
- Apple Sign in with Apple other platforms: https://developer.apple.com/documentation/signinwithapple/incorporating-sign-in-with-apple-into-other-platforms

**Approval record:** This D10.20 submission is a machine-readable review candidate. No release, deployment, backend code changes, migration, generated SDK integration or test execution implied by design approval.
