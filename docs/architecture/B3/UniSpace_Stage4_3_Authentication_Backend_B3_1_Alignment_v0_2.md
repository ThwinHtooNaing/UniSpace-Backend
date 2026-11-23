# UniSpace — Authentication UI/UX ↔ Backend B3 Alignment

**Version:** v0.2 | **Date:** 23 November 2026  
**UI/UX:** Stage 4.3, Group A — **approved UX behavior**  
**Backend:** B3 Authentication & API Security — architecture decisions recorded; final OpenAPI schemas **pending**  
**Scope:** Flutter Android/iOS auth screens, state transitions and backend compatibility; **no backend implementation authorization**.

## 1. Source authority and change control

- **Product baseline:** Functional Requirements v1.5, FR Addendum v1.0, approved Information Architecture and addendum, 67 original User Flows v1.0, and approved User Flows Addendum v1.1.
- **UX baseline:** Stage 4.1 principles; Stage 4.2 navigation; Stage 4.3 Groups A–G screen-state decisions.
- **Backend source:** `UniSpace_Authentication_UIUX_Alignment_Handoff_v0.1(1).md` (B3-D01–D09 approved security decisions; D10 directions and **working** endpoint inventory).
- **Status discipline:** *Approved product* ≠ *approved backend direction* ≠ *working API route* ≠ *final OpenAPI schema*. Preserve these distinctions.
- **Scope stays:** 167 approved FRs (150 MVP / 17 post-MVP). No product requirement or approved user-flow ID is changed here.

## 2. Final Group A experience: authentication journeys

| Journey | Required user experience | End condition |
|---|---|---|
| **UF-AUTH-01 — First registration** | Google/Apple sign-in → Personal Details → University Email → registration OTP | Home **only after** ACTIVE + valid trusted session |
| **UF-AUTH-02 — Trusted return** | App splash → validate/renew current session quietly; do not force provider sign-in for a merely expired access JWT | Home when backend confirms ACTIVE/authorized |
| **UF-AUTH-03 — New/untrusted device** | Provider sign-in → OTP to **previously verified university email** → register device proof/trust | Home only after successful verification/session issuance |
| **UF-AUTH-04 — Interrupted onboarding** | Resume last appropriate unfinished step; preserve accepted inputs after ordinary restart/process termination | Continue onboarding |
| **UF-AUTH-05 — Reinstall during onboarding** | Require provider authentication; restart onboarding from Personal Details; reconcile provisional account to prevent duplicates | Continue onboarding |
| **UF-AUTH-06 — Sign-out/expiry** | Revoke current session and device trust on explicit sign-out; clear locally protected navigation/secrets | Sign-in/restricted state |
| **ADD-FR-167 — Provider linking** | From Security settings, verify second Google/Apple identity; show linked status without switching accounts | Remain on same UniSpace account |

**Approved three-step onboarding display:** The progress indicator starts **after provider sign-in**: **1/3 Personal Details (AUTH-03)** → **2/3 University Email (AUTH-04)** → **3/3 Email Verification (AUTH-05)**. First enrollment uses the registration email OTP to establish first-device eligibility; do **not** demand a redundant second new-device OTP for the same enrollment.

## 3. Precise session and device routing

| Condition confirmed by backend | Flutter behavior |
|---|---|
| Access JWT valid; account ACTIVE; authorized session | Navigate to Home |
| Access JWT expired, refresh session valid | Attempt **silent refresh**, coordinate concurrent requests; stay in loading/context, do not show OTP |
| Refresh session expired, but device remains trusted | Require provider reauthentication; backend may accept **device-key possession proof** without OTP |
| Device new/untrusted/revoked | Require provider authentication **and** university-email OTP; enroll device key only after appropriate checks |
| Provisional account | Route to correct onboarding step, never to protected tabs |
| Account DISABLED | Show restricted/access-unavailable state, never Home based on cached state |
| Explicit sign-out | Invalidate current session **and** current-device trust; clear local sensitive state; next sign-in may require OTP |
| Lost verified university mailbox | Show secure admin-assisted support/recovery guidance; **no self-service verification bypass** |

**B3 security design recorded in the handoff:** RS256 access JWT (20-minute validity), opaque rotated refresh secret (30-day **sliding inactivity** session), simultaneous trusted sessions permitted (no approved numeric limit), and device-bound asymmetric key pair with challenge-signature proof using platform-secure key storage. These technical parameters are **not additional UX requirements**. Device trust has **no fixed 30-day expiration**; refresh-session inactivity and device trust are separate lifecycles.

## 4. Screen states and responsibilities

| Screen | UX states/actions | Server responsibility |
|---|---|---|
| **AUTH-01 — Splash/router** | Short loading; silent refresh; resume onboarding; sign-in, disabled, offline/error branches | Resolve valid session/account and authorize next destination |
| **AUTH-02 — Google/Apple sign-in** | Default, provider in progress, cancelled, invalid proof, retry | Validate provider assertions, nonce/challenge as designed, immutable subject; never auto-merge by email |
| **AUTH-03 — Personal Details (1/3)** | Prefilled editable first/last names, Student ID, format/duplicate errors, submitting; preserve other inputs | Validate current Admin-configured Student ID pattern/uniqueness and provisional progress |
| **AUTH-04 — University Email (2/3)** | Email entry, invalid/unsupported domain, requesting code, send-error/retry | Apply configured allowed domains; issue registration challenge |
| **AUTH-05 — Registration OTP (3/3)** | Entering, verifying, incorrect, expired, resend cooldown, five-failure invalidation, delivery failure, success | Verify single-use OTP and registration completeness; activate account |
| **AUTH-06 — New-device verification** | Existing email masked where appropriate; proof attempt normally invisible; OTP entry only when required; error/retry/recovery | Verify stored trusted-device proof, or previously verified university-email OTP; register trusted device securely |
| **Linked Sign-In Providers** | Show linked Google/Apple; link, cancellation, already-linked, identity-in-use and failure | Require current authenticated session and fresh second-provider proof; unique `(provider, subject)` association |

Flutter is a presentation client. **Spring Boot alone determines account ACTIVE state, permissions, session validity, trust, OTP validity and resource access.**

## 5. Approved authentication constraints

| Subject | Rule |
|---|---|
| Account lifecycle | `PENDING_PROFILE` → `PENDING_EMAIL_VERIFICATION` → `ACTIVE`; `DISABLED` restricts access |
| University email | Required even when provider has an email; verify with institution email OTP |
| Student ID | Required, unique and validated against Admin-configured rules |
| OTP | 10-minute expiry; 60-second resend cooldown; five incorrect attempts invalidate; single-use; replacement invalidates old code |
| Trusted-device lifetime | Ends on sign-out, explicit revocation or security event; not automatically after 30 days |
| Identity uniqueness | Google/Apple provider identity belongs to one user; never merge on email equality |
| Registration recovery | Normal restart preserves progress; reinstall triggers fresh provider sign-in and restarts onboarding safely |
| Sensitive storage | Never put OTP, private keys, refresh secrets or provider assertions in ordinary preferences, logs or analytics |

OTP countdowns are **presentational**; server expiry/cooldown/attempt rules always prevail. Error or offline states must never imply success without confirmation.

## 6. UX routing semantics — not final API fields

| Conceptual UI outcome | Destination |
|---|---|
| `SIGN_IN_REQUIRED` | AUTH-02 |
| `PROFILE_REQUIRED` | AUTH-03 |
| `EMAIL_REQUIRED` | AUTH-04 |
| `EMAIL_VERIFICATION_REQUIRED` | AUTH-05 |
| `DEVICE_VERIFICATION_REQUIRED` | AUTH-06 |
| `READY` | Home only with ACTIVE + valid authenticated/authorized session |
| `ACCOUNT_DISABLED` | Restricted screen |

These are **illustrative next-action semantics**, not approved JSON enum names or additional database state values. B3 must distinguish provider-verified-but-device-pending, trust proof required, onboarding credential limits, and session expiry/recovery responses.

## 7. API alignment — backend **working route inventory**, not final OpenAPI

All routes below have provisional `/api/v1` prefix. DTOs, exact endpoint paths and stable error codes remain subject to B3-D10 finalization.

| UX operation | Working method/path | Notes |
|---|---|---|
| Start provider challenge | `POST /auth/challenges` | Nonce/replay binding to be finalized |
| Provider sign-in result | `POST /auth/provider-exchange` | Server returns next authorized step |
| Resume onboarding | `GET /me/onboarding` | Provisional route/state |
| Save personal details | `PUT /me/onboarding/profile` | Preserve valid fields on rejection |
| Send/resend registration OTP | `POST /me/university-email/challenges` | Distinct from device OTP |
| Verify registration OTP | `POST /me/university-email/verify` | Activation only on successful completion |
| Start device challenge | `POST /auth/device-challenges` | Proof vs new-device OTP |
| Verify device signature | `POST /auth/device-challenges/{id}/verify-proof` | Normally invisible to user |
| Verify new-device OTP | `POST /auth/device-challenges/{id}/verify-otp` | Only if needed |
| Register device key | `POST /auth/device-challenges/{id}/register-key` | Complete verified trust enrollment |
| Silent refresh | `POST /auth/refresh` | Rotation and replay handling still being specified |
| Sign out | `POST /auth/sign-out` | Current session and device trust revoked |
| Linked provider status | `GET /me/linked-providers` | Working naming |
| Link provider | `POST /me/linked-providers` | Require provider ownership proof |
| Unlink eligible provider | `DELETE /me/linked-providers/{provider}` | **Do not expose yet** until re-verification protocol finalized |

**Do not implement the older combined `.../device-challenges/{id}/verify` variant alongside the newer separated proof/OTP/key registration structure without backend reconciliation.**

**Error transport direction:** RFC 9457 `application/problem+json` plus stable `code` and `traceId` fields. Exact registry pending. Flutter must use machine-readable codes rather than comparing English error text; distinguish `401`, `403`, `409`, `429`, and network/5xx outcomes safely.

## 8. Navigation decision — newer Stage 4 approval takes precedence

**Approved Stage 4 route:** **Profile → Settings → Security → Linked Sign-In Providers**.

The earlier User Flows Addendum v1.1 and backend handoff mention **Connected Accounts**. Record this as a *controlled Stage 4 navigation refinement*, not an FR-167 change. **Connected Accounts** remains the separate setting for post-MVP Google Drive authorization. Update relevant traceability references, not original 67 flow IDs.

## 9. Explicitly pending or excluded

**Backend B3 / OpenAPI pending:** Exact Google/Apple provider proof DTOs and nonce protocol; provisional credential policy; device-key algorithm/attestation or fallback details; OTP delivery/error semantics and retry metadata; refresh rotation races and replay protection; session identifier/expiry response fields; provider unlink re-verification; lost-mailbox admin-assisted recovery protocol; final route/DTO/problem-code registry; email provider and sender. Applied Flyway `V1__initial_schema.sql` must remain immutable; changes use forward-only migrations.

**Not approved MVP UI:** All-device sign-out, remote session list/kill, trusted-device inventory/revoke UI, arbitrary device count limits, 30-day device-trust expiry, email-only auto-merging, self-service OTP bypass. A last usable provider must not be unlinked; until the unlink contract is settled, display **linked status and Link** only, with no misleading Unlink control.

## 10. Group A approval and downstream acceptance

**Approved UX scope:** six original AUTH screens, seven listed journey contexts (including ADD-FR-167), three-step post-provider onboarding progress, all user-visible waiting/errors/recovery, silent refresh, device trust vs token validity, sign-out consequences, and Security placement for provider linking.

**Not approved by this UX decision:** endpoint/DTO/error enums, encryption/key mechanisms, backend implementation, database migrations, exact visual copy/layout, or Figma components.

### Verification checklist for B3 and Flutter implementation

- [ ] First sign-in → three steps → ACTIVE Home; registration OTP suffices for initial trusted enrollment.
- [ ] Valid trusted return and silent refresh do not unnecessarily prompt provider sign-in/OTP.
- [ ] Expired refresh with trusted device follows provider sign-in + valid possession proof where backend permits.
- [ ] Untrusted/revoked device follows provider sign-in + university-email OTP and verified key enrollment.
- [ ] OTP expiry, resend, attempt lockout and replacement produce accurate accessible states.
- [ ] Ordinary restart preserves provisional progress; reinstall requires fresh provider sign-in and onboarding restart.
- [ ] Disabled/revoked/unauthorized user never reaches protected UI; no cached Home bypass.
- [ ] Provider linking preserves original user ID and fails safely for cancellation/conflict/repeated requests.
- [ ] Backend and Flutter share final OpenAPI schemas and stable error codes once B3-D10 is signed off.

**Next UI/UX:** Stage 4.4 — Feedback, Errors & Recovery; later Stage 4 consolidated specification and Figma.  
**Next backend:** Complete B3-D10/OpenAPI decisions independently; this document is a *UX alignment record*, not permission to implement unreviewed contracts.
