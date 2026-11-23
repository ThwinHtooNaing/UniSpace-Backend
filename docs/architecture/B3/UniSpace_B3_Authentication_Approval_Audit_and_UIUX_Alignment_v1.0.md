# UniSpace — B3 Authentication Approval Audit & UI/UX Alignment

**Document ID:** `UNISPACE-B3-AUTH-DECISION-REGISTER`  
**Version:** `1.0 — audited working baseline`  
**Audit date:** 24 November 2026  
**Scope:** B3-D01–D09 and B3-D10.1–D10.16, authentication/onboarding/provider-linking only  
**Target:** Java 21 / Spring Boot 4.2 / Spring Security / MySQL 8.4 / Flutter (Android + iOS)  
**Implementation authorization:** **NONE — design and traceability record only**

> **Decision boundary:** This document preserves approvals from the B3 discussion; it does **not** turn working REST paths or illustrative JSON payloads into finalized OpenAPI, authorize a Flyway migration, add a new Functional Requirement (FR), or implement a feature. The approved product baseline remains **167 FRs: 150 MVP and 17 post-MVP**. Where a backend technical decision exceeds what an FR explicitly requires, it is labeled separately. Where a subsequent decision narrows or clarifies an earlier proposal, use the later approved decision, **without retroactively claiming the old document had already been updated**.

**B3-I02 review status.** This v1.0 audit preserves the earlier decisions at their original scope. D10.25–D10.28 govern current sign-out, device proof, replay and rollout. The synchronized OpenAPI remains a review candidate. [Current authentication decisions](UniSpace_B3_Current_Authentication_Decisions.md)

## 1. Executive checkpoint

- **Preserve:** B3-D01–D09 and approved B3-D10.1–D10.15 *at their recorded scope*. These are architecture/security/contract-direction approvals, not completed code or final OpenAPI schemas.
- **Do not approve by implication:** B3-D10.16 (provider **unlinking** and lost-email **recovery workflow**) was reviewed but **not approved as an MVP implementation contract**. The previously approved *security principles* remain available as constraints for a future separately approved design.
- **Core in-scope behavior:** Google/Apple provider authentication; onboarding; university email OTP; returning/refreshing sessions; trusted-device enrollment and proof; current-device sign-out; viewing/linking providers.
- **MVP working API inventory:** **14 endpoints**. An older **15-endpoint** working catalog additionally included `DELETE /me/linked-providers/{provider}`. That DELETE remains **deferred / not exposed**. Older drafts also listed session and trusted-device management endpoints that are **not approved MVP**.
- **UX authority:** Stage 4.3 Group A authentication UX **v0.2**, including `AUTH-01`–`AUTH-06`, approved three-step post-provider onboarding, silent refresh, and provider-link placement under **Profile → Settings → Security → Linked Sign-In Providers**.
- **Outstanding:** final DTOs/validation, result/error enums, provider-specific Apple Android handoff, signing/nonce details, restricted-flow/session binding and recovery, session replay/refresh edge cases, device signing interoperability, and any forward-only database migration.

### Vocabulary for approval status

| Status | Meaning |
|---|---|
| **PRODUCT APPROVED** | Explicitly required by controlled FRs / approved user flows. |
| **B3 APPROVED** | User accepted a specific backend security or API *design direction*, possibly including numeric policy. Does not automatically become an FR or authorize code. |
| **UX APPROVED** | Stage 4.3 Group A behavior approved; does not finalize the backend JSON contract. |
| **WORKING ROUTE** | Selected route for UI↔backend coordination, still subject to consolidated OpenAPI sign-off. |
| **PENDING** | Requires an explicit decision, test/security review, or schema elaboration. |
| **DEFERRED / EXCLUDED** | Do not add to the MVP endpoint/UI scope without separate authorization. |

## 2. Source hierarchy and traceability

**Primary controlled product sources**

1. `UniSpace_Functional_Requirements_v1_5(3).pdf` — original FR-001–137, particularly FR-002–008 and FR-113–137.
2. `UniSpace_Functional_Requirements_Addendum_v1_0(1).pdf` — FR-138–167, particularly FR-155–158 and FR-167.
3. `UniSpace_User_Flows_and_User_Journeys_v1_0(2).pdf` — 67 flows; `UF-AUTH-01`–`UF-AUTH-06`.
4. `UniSpace_User_Flows_and_User_Journeys_Addendum_v1_1_FINAL(1).md` — approved `ADD-FR-167` provider-linking journey and reconciliation amendments.
5. Approved Information Architecture and subsequent controlled UX/navigation refinements.

**UI/UX implementation-alignment reference**

- `UniSpace_Stage4_3_Authentication_Backend_B3_1_Alignment_v0_2(1).md` — **current approved Group A behavior**. Supersedes its v0.1 *for UX alignment*. Its suggested routing labels and HTTP paths are not final DTO/schema values.

**Backend design references**

- `B1_Backend_Architecture_v1.0.md`; `B2_Database_Architecture_v1.0.md`.
- `UniSpace_Backend_Progress_Checkpoint_2026-11-23.md`.
- `unispace_api_docs/B3_Security_Authentication_Design_PROPOSAL.md` and `unispace_api_docs/UniSpace_Backend_API_Contract_Catalog_PROPOSAL.md` — **older proposals**, useful for origin/trace, **not** authority over later B3 approvals.
- `UniSpace_Authentication_UIUX_Alignment_Handoff_v0.1.md` — older backend-to-UX handoff; should not override the approved UX v0.2.

**Resolution rule:** FRs and approved user flows control **product obligations**; subsequent approved Stage 4 decisions control **presentation/navigation**, so long as product requirements remain satisfied; explicit B3 decisions govern **security architecture**; final OpenAPI remains separately pending.

## 3. B3-D01–D09: approved core security architecture

| ID | Recorded decision | Status / traceability | Remaining implementation detail |
|---|---|---|---|
| **D01** | Sign access JWTs using **RS256**, **3072-bit RSA** signing key, backend-only private key, `kid`/rotation readiness. | **B3 APPROVED**; technical choice. | Key lifecycle, truststore, JWT claim validators and deployment secret management. |
| **D02** | UniSpace access JWT expires after **20 minutes**. | **B3 APPROVED**. | Clock skew/configuration; silent client refresh. |
| **D03** | Refresh session has **30-day** expiry, clarified by D08 as a *sliding inactivity* window. | **B3 APPROVED**. | Atomic renewal and expiry fields. |
| **D04** | Refresh-token rotation on each successful refresh, store digest only, identify replay and revoke affected session, serialize concurrent refresh requests. | **B3 APPROVED**, replay governed by D10.27. | Persist consumed-token history; retry/race/security tests. |
| **D05** | On protected requests, validate JWT **and** authoritative session validity, user/account state and current role/resource permissions. Revocations take effect immediately. | **B3 APPROVED**; supports FR-013, FR-124, FR-130. | Central Spring Security filters/service checks and efficient MySQL lookups. |
| **D06** | Google/Apple provider sign-in; backend proof verification (`iss`, `aud`, signature, expiry, appropriate nonce/code); use unique `(provider, subject)`; **never merge by email**; permit second provider linking. | **PRODUCT APPROVED + B3 APPROVED**; FR-002/003/113/167. | Provider/platform details in D10.13, final client IDs and callback. |
| **D07** | Trust based on device-bound asymmetric private-key possession challenge; Android Keystore/iOS secure key storage, optional descriptive device metadata only. | **B3 APPROVED**; supports FR-125–129/158. | Enrollment, attestation/fallback, public-key and challenge persistence. |
| **D08** | Multiple simultaneous trusted-device sessions; **no numeric maximum**; refresh inactivity sliding 30 days; trusted device persists independently of refresh expiry. | **B3 APPROVED**; FR-125/126/158. | Per-session isolation; refresh-expiry reauth behavior. |
| **D09** | Sign-out ends **current session and current-device trust**; disabled account invalidates sessions; role changes immediate; compromised-device revocation scoped appropriately; account linking does not merge identities. Unlinking last usable login method is prohibited as a *security principle*. Lost verified email requires secure assisted handling rather than OTP bypass. | **B3 APPROVED PRINCIPLES**; FR-131/158/167 intersect, but **unlink/recovery workflow is not an explicit FR**. | Unlinking implementation and lost-mailbox recovery **DEFERRED**, not automatically MVP. |

**Important distinction:** `20 minutes` is the **normal access JWT** expiration; the subsequently approved `15 minutes` applies to a **separate, restricted authentication-flow JWT** (D10.12).

## 4. B3-D10.1–D10.16 decision audit

| ID | Recorded outcome | Approval status | Caution / boundary |
|---|---|---|---|
| **D10.1** | One provider-exchange concept, secure challenge binding, distinct onboarding/device/authenticated outcomes; Spring Security foundation. | **B3 APPROVED DIRECTION** | Exact payload not finalized here. |
| **D10.2** | Separate registration OTP and new-device OTP interactions; server-side 10m/60s/five-failure/single-use rules. | **B3 APPROVED DIRECTION**, FR-120–122, 128, 156 | Provider/sender and exact delivery errors pending. |
| **D10.3** | Device challenges: separate proof, OTP and key-enrollment actions. | **B3 APPROVED DIRECTION** | Later D10.10/14 refine formats and lifetimes. |
| **D10.4** | `POST /auth/refresh`, `POST /auth/sign-out`; rotation, current-session/device consequences. | **B3 APPROVED DIRECTION** | D10.25 approves verified current refresh fallback after access expiry. Final DTO review remains. |
| **D10.5** | Spring Security entry point/access-denied handling, RFC 9457 Problem Details, stable UniSpace `code` + `traceId`. | **B3 APPROVED DIRECTION** | Exact code/HTTP mapping registry pending. |
| **D10.6** | Consolidated initial API inventory as a working baseline; no feature creep. | **B3 APPROVED WORKING BASELINE** | Not final OpenAPI. |
| **D10.7** | `POST /auth/challenges`, secure server-issued ≥256-bit nonce, **5-minute**, single-use challenge. | **B3 APPROVED** | Google/Apple-specific nonce/state handling must be verified. |
| **D10.8** | `POST /auth/provider-exchange`; provider proof verified server-side; restricted credential before completion. | **B3 APPROVED DIRECTION** | Exact Google and Apple payloads/callback pending. |
| **D10.9** | Four registration/onboarding endpoints: progress, profile, registration OTP send, verification; no redundant first-device OTP. | **B3 APPROVED DIRECTION**, UX APPROVED | Exact DTO validation, activation/device enrollment boundary pending. |
| **D10.10** | Four device endpoints; P-256 asymmetric key, SHA-256 signatures, proof of possession, private key device-only. | **B3 APPROVED DIRECTION** | Wire encoding and failure policy elaborated D10.14. |
| **D10.11** | Consolidated **15-route** *working* inventory, which included unlinking; only scope-level reconciliation, not final schema. | **B3 APPROVED WORKING BASELINE** | After FR audit, keep **14 in-scope**; defer unlink DELETE. |
| **D10.12** | **15-minute** restricted RS256 flow JWT; *no refresh token* for flow credential; recover saved onboarding via provider reauth when expired; never grant full application access. | **B3 APPROVED** | Token purpose, `aud`/scope, flow revocation/DB binding, reauth routing pending. |
| **D10.13** | Official Google/Apple identity verification; Android Apple browser flow; backend callback/handoff expected. | **B3 APPROVED DIRECTION** | Exact Android callback, Google/Apple nonce and code exchange pending. |
| **D10.14** | **2-minute** device-signature challenge; P-256 + SHA-256; Base64url-encoded DER signature; single use; rejected failed proof; lost-key reauth + email OTP; hardware attestation deferred. | **B3 APPROVED** | Android/iOS interoperable encoding, risk controls; non-key fallback policy not settled. |
| **D10.15 (historical)** | The earlier design rejected spent tokens but deferred revocation for 15 seconds to tolerate duplicate refresh requests. | **Exception superseded by D10.27.** | Retain client single-flight and atomic rotation. Every recognized reuse now revokes the affected family. |
| **D10.16** | Fresh-authenticated unlink (only if another provider remains), and institutionally controlled lost-mailbox recovery. | **REVIEWED, NOT APPROVED AS MVP FEATURE/CONTRACT** | Preserve high-level D09 safety principles only. No Unlink UI/API or recovery endpoint/workflow for MVP without new approval. |

### What an approval does not mean

An approved *direction* does **not** imply every sample body, enum, header, HTTP response code, challenge storage layout, algorithm/provider library or middleware implementation in a previous reply was frozen. Only the specifically accepted rules and numeric decisions above are retained; unresolved details appear in §10.

## 5. Product FR ↔ UI ↔ backend traceability (authentication)

| FR / flow | Explicit product requirement | Approved UX / backend implication |
|---|---|---|
| **FR-002/003/004; FR-113** / `UF-AUTH-01` | Google/Apple sign-in, provisional account until onboarding succeeds. | `AUTH-02` → `AUTH-03` → `AUTH-04` → `AUTH-05`; full session only after required checks. |
| **FR-114–117; FR-134** | Prefill editable names, require unique/valid Student ID, preserve unrelated good inputs. | `AUTH-03` client field states, server institution-policy validation and uniqueness. |
| **FR-118–124** | University email, permitted domain, real email OTP, no protected access until registration complete. | `AUTH-04/05`, server-side OTP and activation control, restricted flow credential. |
| **FR-125/126/130** / `UF-AUTH-02` | Returning valid trusted session goes to Home; expired/invalid requires appropriate reauth. | `AUTH-01`: attempt silent refresh when valid; otherwise route to sign-in. |
| **FR-127–129** / `UF-AUTH-03` | New/untrusted device requires Google/Apple plus OTP to **previously verified university email**. | `AUTH-06`; never redo Student ID or first-time onboarding for ACTIVE account. |
| **FR-131; FR-158** / `UF-AUTH-06` | Explicit sign-out; trust ends on sign-out/revocation/security event, independently of token expiry. | Current session + current-device trust revoke; local secret/protected state cleared. |
| **FR-132/133/135/136/137** / `UF-AUTH-04/05` | Ordinary interruption resumes saved progress; uninstall/reinstall during provisional registration forces provider reauth and onboarding restart, reconcile provisional record. | `AUTH-01/03–05`: recovery route and distinct reinstall behavior. |
| **FR-156** | OTP 10m expiry, 60s resend, 5 incorrect attempts, single-use, replacement invalidation. | Enforced server-side; precise accessible UI countdown/error states. |
| **FR-157** | Admin controls allowed email domains and Student ID format nonretroactively. | Registration APIs use current institution policy; no separate auth UI change. |
| **FR-167** / `ADD-FR-167` | Authenticated users may link Google+Apple identities, prove ownership, no email-only merging. | Linked-provider status and **Link** in Security settings; no automatic Unlink feature. |
| **FR-155** | Audit security events for required retention without exposing authentication secrets. | Server audit and log redaction. |
| **No explicit FR** | Provider unlinking, Admin lost-email recovery procedure, remote device/session inventory, sign-out-all. | **Not MVP scope** despite their appearance in older proposals. |

**Interpretation note:** The original FRs do **not** prescribe JWT algorithm/lifetime, P-256, refresh rotation window, provider callback route or email service. Those are separately approved B3 technical decisions or pending details, not invented FR additions.

## 6. Frozen numerical/cryptographic policy register

| Subject | Approved value | Source decision |
|---|---|---|
| Access JWT | RS256; signing RSA key **3072-bit**; **20-minute** expiry | D01/D02 |
| Flow JWT | Restricted RS256; **15-minute** expiry; no flow refresh token | D10.12 |
| Authentication nonce challenge | ≥256 bits of cryptographically secure randomness; **5-minute** validity; one-time | D10.7 |
| Device signature challenge | **2-minute** validity; one-time | D10.14 |
| Device-key signing | ECDSA P-256/SHA-256 over canonical server bytes, base64url DER signature, RFC 7638 public JWK thumbprint | D10.10/D10.14/D10.26 |
| Email OTP | **10-minute** expiry; **60-second** resend cooldown; **5** failed attempts invalidates; one-time; replacement invalidates former OTP | FR-156 |
| Refresh inactivity | **30-day sliding** expiry renewed on successful refresh | D03/D08 |
| Refresh rotation | Every successful refresh, hash/digest stored server-side | D04/D10.27 |
| Consumed refresh reuse | Reject and revoke the affected family immediately, including on a duplicate request | D10.27 |
| Device trust | **No fixed expiration**; independent of JWT/session lifetime | FR-158 / D07–D09 |
| Simultaneous devices | Permitted; **no numerical device cap approved** | D08 |

**Replay consequence.** If a successful response is lost, retrying its spent credential revokes the successor as well. Other device sessions remain valid.

## 7. UX alignment: six AUTH screens and account-linking

| UI screen | Approved user-facing behavior | Backend contract responsibility |
|---|---|---|
| `AUTH-01` Splash/session router | Quiet check/refresh; show loading/error/disabled; route to Home, sign-in or saved onboarding. | Confirm ACTIVE/session validity and next permitted action. |
| `AUTH-02` Provider sign-in | Google/Apple buttons, progress, cancel/failure/retry. | Issue provider challenge, verify provider proof and resolve account without email merge. |
| `AUTH-03` Personal Details (**1/3**) | Editable first/last names and Student ID; preserve entries after validation error. | Validate institution-policy Student ID uniqueness/format; persist progress. |
| `AUTH-04` University Email (**2/3**) | Email entry; domain advice, sending/error/retry. | Validate permitted domain, request registration OTP. |
| `AUTH-05` Registration OTP (**3/3**) | Code entry; cooldown, expiry, attempt lockout, resend; no second OTP in same first enrollment. | Validate OTP, complete onboarding when eligible; hand off to initial device-key enrollment. |
| `AUTH-06` New-device verification | OTP only when needed; cryptographic proof normally invisible; safe reauth/recovery guidance. | Verify trusted-device signature or send OTP to **stored verified email**, then enroll key. |
| Linked Sign-In Providers | **Profile → Settings → Security → Linked Sign-In Providers**; show linked status and Link, errors/cancellation/conflict. | Protect link operation with current session + fresh second-provider proof; no Unlink UI yet. |

### Approved navigation/route expectations

- After provider sign-in, onboarding progress is **three steps**; provider sign-in is not counted as a step.
- `PENDING_PROFILE` → `PENDING_EMAIL_VERIFICATION` → `ACTIVE`; `DISABLED` never grants Home based on cached data. Avoid inventing an extra persistent `EMAIL_REQUIRED` database status from a UI routing label.
- A valid refresh session supports **silent refresh**; merely expiring a 20-minute access JWT must not force provider login or OTP.
- A trusted device with expired refresh needs provider reauthentication and acceptable key-possession proof; an untrusted/new device needs provider reauthentication **and** institutional email OTP.
- First registration email OTP already satisfies the email-verification factor for initial trusted enrollment: **no redundant second OTP**; still verify private-key possession.
- Server determines all authorization. Client countdowns and labels are presentational only.
- Unavailable previously verified university mailbox: show **informational/help guidance**, but **do not promise an approved self-service or Admin recovery action** yet.

**UX routing labels** `SIGN_IN_REQUIRED`, `PROFILE_REQUIRED`, `EMAIL_REQUIRED`, `EMAIL_VERIFICATION_REQUIRED`, `DEVICE_VERIFICATION_REQUIRED`, `READY`, `ACCOUNT_DISABLED` are **conceptual**, not approved OpenAPI enum strings or database statuses.

## 8. Authentication endpoint inventory: in-scope working routes

**Common prefix:** `/api/v1`. All paths/methods below are *working contract directions*; exact JSON, headers and status codes are not finalized. No Spring Security built-in route is implied by these names.

| No. | Method | Path (after prefix) | Intended role in approved UX |
|---:|---|---|---|
| 01 | `POST` | `/auth/challenges` | Server-issued nonce/challenge before provider sign-in. |
| 02 | `POST` | `/auth/provider-exchange` | Validate Google/Apple identity; return restricted next step. |
| 03 | `GET` | `/me/onboarding` | Resume authoritative provisional progress. |
| 04 | `PUT` | `/me/onboarding/profile` | Validate/store personal details and Student ID. |
| 05 | `POST` | `/me/university-email/challenges` | Send/resend registration email OTP. |
| 06 | `POST` | `/me/university-email/verify` | Verify registration OTP and continue activation. |
| 07 | `POST` | `/auth/device-challenges` | Begin device possession or new-device verification. |
| 08 | `POST` | `/auth/device-challenges/{id}/verify-proof` | Verify trusted/enrolling key's signature. |
| 09 | `POST` | `/auth/device-challenges/{id}/verify-otp` | Validate new-device OTP (not redundant first-registration OTP). |
| 10 | `POST` | `/auth/device-challenges/{id}/register-key` | Submit verified-enrollment public key; trust after proof. |
| 11 | `POST` | `/auth/refresh` | Atomically rotate refresh token / issue new session credentials. |
| 12 | `POST` | `/auth/sign-out` | Revoke current session + device trust. |
| 13 | `GET` | `/me/linked-providers` | View own Google/Apple link status. |
| 14 | `POST` | `/me/linked-providers` | Link second identity after proof; preserve account. |

**Not part of 14-route MVP working inventory:**

| Proposed/old route | Treatment |
|---|---|
| `DELETE /me/linked-providers/{provider}` | **DEFERRED**: No explicit unlink FR; no Unlink UI. Preserve D09 no-last-provider security invariant if future feature approved. |
| `POST /auth/sign-out-all` | **EXCLUDED**: No approved all-device sign-out MVP behavior. |
| `GET /auth/sessions`; `DELETE /auth/sessions/{sessionId}` | **EXCLUDED**: No approved remote session inventory/kill UI. |
| `GET /auth/trusted-devices`; `DELETE /auth/trusted-devices/{deviceId}` | **EXCLUDED**: No approved device inventory/remote revoke UI. |
| `POST /auth/device-challenges/{id}/verify` (older combined route) | **SUPERSEDED WORKING PROPOSAL** by separate verify-proof/verify-otp/register-key paths; do not implement both. |
| Recovery/replace-university-email API | **NOT SPECIFIED**; no endpoint or procedure authorized. |

**Scope note:** B3-D10.11 approved a **15-route consolidated working list** *before* the FR audit clarified Unlink's position. Retaining the 15th as **deferred** and keeping 14 *in-scope* is a scope correction, **not** a claim the original D10.11 approval did not happen.

## 9. Typical flow sequences (behavioral, not final wire schema)

### 9.1 First-time user (`UF-AUTH-01`)

`AUTH-02` → auth challenge → Google/Apple assertion → provider exchange → restricted flow JWT → `AUTH-03` profile → `AUTH-04` university email challenge → `AUTH-05` OTP verification → device public-key enrollment **and** possession proof → ACTIVE authenticated session → Home.

- Provisional user cannot access Channels/Media/Admin endpoints.
- Names are prefilled when available and editable. Student ID validation is authoritative in backend.
- OTP success and eventual session issuance must not be conflated; if key registration/proof still pending, keep restricted flow.

### 9.2 Returning registered user (`UF-AUTH-02`)

`AUTH-01` → check valid access JWT/session/account → Home; if access JWT expired with valid refresh → **single-flight silent refresh** → Home. If refresh has expired but device remains trusted → provider reauth and fresh device-key proof; **do not demand OTP solely because refresh expired**.

### 9.3 Existing user on new/untrusted device (`UF-AUTH-03`)

`AUTH-02` → server provider verification → restricted flow → `AUTH-06` OTP sent to **previously verified university email** → OTP verification → register new key + possession proof → current device trusted/session issued → Home. No student-registration repeat.

### 9.4 Onboarding interrupted / reinstalled (`UF-AUTH-04/05`)

- Normal restart: retain persisted valid form inputs; resume corresponding step when restricted credential remains valid. If credential expired, use provider reauth and recover server state.
- Reinstall during provisional onboarding: require **fresh** provider sign-in and restart UI onboarding at Personal Details; reconcile preexisting provisional record without creating duplicate user identity.
- Account already ACTIVE: never force onboarding again merely because of reinstall.

### 9.5 Provider linking (`ADD-FR-167`)

Currently ACTIVE session → Security / Linked Sign-In Providers → choose unlinked Google/Apple provider → prove ownership via its official flow → backend atomically link unique provider subject → return to same UniSpace account; repeated, canceled, conflict and unverifiable credentials must not corrupt links. **Unlink is not an approved feature.**

## 10. Outstanding technical details / final security gates

The following are *not* resolved just because an overall contract direction was approved:

| Gate | What must be finalized | Why it matters / owner |
|---|---|---|
| **API-01** | Full OpenAPI 3.x DTOs for every in-scope route: required fields, field limits, authentication headers, success/empty/invalid responses, timestamps, wire enums, versioning. | Backend↔Flutter contract; current request/response examples are illustrative. |
| **API-02** | Restricted-flow JWT audience/`typ`/purpose/scope, per-flow binding, persistence, invalidation and exact recovery; no flow refresh. | Prevent temporary JWT being accepted as full access; D10.12 approved policy intact. |
| **API-03** | Exact Google nonce use on Flutter Android/iOS, app/client audience IDs, Apple native vs Android browser auth-code callback/deep-link handoff, state and PKCE rules where applicable. | Provider protocol correctness; D10.13 approval is not an implementation recipe. |
| **API-04** | Verify the canonical server byte layout and mobile DER interoperability. Confirm persistence of the user, flow, key and operation binding. | D10.26 fixes the algorithm, RFC 7638 thumbprint and two-minute single-use rule. |
| **API-05** | Device-key unavailable / secure-storage unsupported policy. | D10.14 proposed a limited fallback, but **no complete authorization/lifetime semantics were approved**; never silently trust keyless device. |
| **API-06** | Verify atomic rotation and replay-triggered family revocation, digest retention, concurrent requests and lost responses. | D10.27 and DB-G06. |
| **API-07** | Verify confirmed current-session and device-trust revocation, including refresh fallback after access expiry and failure to confirm while offline. | D10.25 and FR-131/158. |
| **API-08** | OTP delivery adapter/sender/provider, error states, privacy and abuse limits. | Local **Mailpit recommended** only; real-mail provider/domain **not selected**. 10m/60s/five attempts remain mandatory. |
| **API-09** | Stable RFC 9457 problem-code registry, HTTP status mappings, `Retry-After` vs cooldown timestamps, accessible error cases. | Flutter must not parse English prose to decide routing. |
| **API-10** | Schema diff against applied Flyway V1: `auth_sessions` consumed-token history, `trusted_devices` public key/ownership, challenge persistence, audit/outbox. | **Do not edit V1**. Any DB change requires reviewed forward-only **V2+** migration. |
| **API-11** | Confirmation of role/account state checks on every protected request, especially DISABLED and newly revoked sessions. | Spring Security Resource Server + UniSpace authoritative MySQL checks, not JWT claim alone. |
| **API-12** | Provider-link conflicts, idempotency/concurrency and fresh proof details. | FR-167 explicitly approved; no provider-unlinking API needed. |

### Required security test matrix before implementation completion

| Domain | Minimum cases |
|---|---|
| Provider identity | Bad signature, wrong issuer/audience, expired provider token, nonce mismatch/replay, Apple callback substitution, already-linked-to-another-user, no email-only merge. |
| Temporary credentials | Expired/tampered flow JWT, wrong token purpose/audience, replayed/disallowed flow step, cancellation, restricted flow denied for Channels/Admin. |
| OTP | Valid/expired, 60s resend, five attempts, old-code invalidation, single-use and concurrent verification, university email domain checks. |
| Trust enrollment | Correct signature, wrong key, replay/expiry, owner mismatch, concurrent register/verify, device lost key, no redundant first-registration OTP. |
| Refresh | Concurrent same-token refresh, double rotation, request lost after commit, recognized spent-token reuse at any delay, successor rejected after revocation, cross-device separation, session revoked/expired/disabled. |
| Session | Silent refresh, expired access with valid refresh, expired refresh with trusted device, sign-out current device trust, role change immediate. |
| UX recovery | Normal interruption preserves registration; reinstall causes proper reauth and start-over UI without duplicate provisional accounts; inaccessible email shows guidance, no bypass. |
| Auditing | No provider assertions, tokens, OTP, device private keys or other secrets logged; security event captured appropriately. |

## 11. Platform, framework and infrastructure boundaries

- **Spring Security** is the security foundation (JWT Resource Server authentication, route authorization, filter/handler integration). It does **not** automatically implement `/api/v1/auth/refresh` or the above UniSpace-specific controllers.
- **Provider integration** uses established Google/Apple identity validation flows and trusted key verification. Provider ID tokens are not UniSpace access JWTs.
- **Spring Boot services** implement institution policy, OTP, provider identity mapping, one-time challenges, device possession verification, session rotation/revocation, audit and consistency.
- **Flutter** presents the approved journey and holds credentials securely, coordinates single-flight refresh and asks platform APIs to sign device challenges. Flutter never asserts `ACTIVE`, `ADMIN`, or `trusted` status as authority.
- **Database:** MySQL authoritative; existing `V1__initial_schema.sql` already applied. Review DB-G06/DB-G07 before any forward-only V2+ migration. Do not reset real local DB or modify applied migrations.
- **Email:** Local **Mailpit is a recommendation, not installed/approved infrastructure**; a real transactional provider and sender domain remain undecided. For 1–3 real Flutter testers, real university mailbox OTP delivery can be configured separately without changing OTP API behavior.
- **No auth business APIs or provider integration code are recorded as implemented at this checkpoint.** Existing MySQL schema and reported 19 integration tests only validate that foundation, not these new auth services.

## 12. Deferred and out-of-scope register

| Idea | Current treatment | Reason |
|---|---|---|
| Unlink Google/Apple provider | **DEFERRED**; no visible button or active `DELETE` API. | FR-167 requires *linking*, not unlinking. Preserve no-last-provider safety if later approved. |
| Lost verified-university-email Admin recovery workflow/API | **DEFERRED protocol**; informational support guidance permitted; no bypass. | No explicit controlled FR defines evidence, approver or replacement-email sequence; needs separate product/security approval. |
| All-device sign-out, session inventory/remote kill | **EXCLUDED MVP** | Not required by approved current user flows. |
| Trusted-device list/remote revoke and arbitrary numeric device cap | **EXCLUDED MVP** | FR-158 defines trust lifecycle; no approved management UI/limit. |
| Hardware attestation | **DEFERRED MVP** | D10.14 explicitly deferred; use platform secure keys where available. |
| Automatic identity merging by email | **PROHIBITED** | Violates FR-167. |
| Treating 30-day session inactivity as device-trust TTL | **PROHIBITED** | Violates FR-158. |
| Self-service OTP bypass or auto-recovery without verified mailbox | **PROHIBITED** | Conflicts with new-device verification policy. |
| Editing applied Flyway V1 | **PROHIBITED** | Forward-only migration policy. |

## 13. Readiness, next review and change-control checklist

**Design work completed:** B3-D01–D09 security direction; B3-D10.1–D10.15 as recorded; Stage 4.3 Group A authenticated UX.  
**Design not completed:** final OpenAPI schemas/security review; D10.16 additional-feature decision; approved migration plan; actual auth implementation.

Before implementing any authentication feature:

1. Reconcile **14 in-scope routes** into reviewed OpenAPI schemas and stable machine-readable status/error enums; explicitly mark deferred routes.
2. Resolve API-01 through API-12, especially **D10.27 replay persistence** and **device fallback**.
3. Verify provider/platform official protocol conformance, Spring Security access/flow-token separation, and threat model.
4. Inspect current repository and existing Flyway schema; propose minimal forward-only changes with tests before migration.
5. Obtain explicit sign-off for the consolidated B3-D10 API/DB changes and separately scoped implementation; do not give Codex broad implementation permission from a proposal.
6. Synchronize the final OpenAPI to Flutter Stage 4.3 Group A only after backend contract approval; revise screen-state mappings without rewriting FR IDs.

### Audit result

D10.27 supersedes the historical D10.15 replay exception. D10.25 settles sign-out fallback and D10.26 specifies key thumbprints and bound device proof. D10.28 keeps Google first, preserves Apple contracts and defers live Apple integration. The 14-operation mobile inventory and approved Group A UX remain the baseline.

**Approval of this document as a record is not implied.** It is an auditable handoff based on the conversation's approved decisions and inspected sources. Any corrections or new product scope should be recorded in a subsequent version with an explicit approval entry.
