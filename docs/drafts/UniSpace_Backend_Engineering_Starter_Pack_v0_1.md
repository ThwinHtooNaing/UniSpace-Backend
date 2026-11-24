# UniSpace Backend Engineering Starter Pack v0 1

> **Source:** `UniSpace_Backend_Engineering_Starter_Pack_v0_1(2).pdf`  
> **Original pages:** 9  
> **Document classification:** drafts  
> **Conversion:** text-only Markdown transcription; original PDF remains the visual/layout record.  
> **Status note:** Technical design draft: not an approved API or database contract.

---


<!-- Source PDF page 01 -->

UniSpace Backend Engineering Starter Pack

B0–B4 design blueprint · Stage 3.6 approved baselines

UniSpace · Technical Design Draft v0.1

20 November 2026

## Contents

1. Executive build rules (PDF p. 1)
2. B0 — Requirements-to-backend ownership map (PDF p. 2)
3. B1 — Backend architecture (proposal) (PDF p. 3)
4. B2 — Logical database model (proposed, not final DDL) (PDF pp. 3–4)
5. B3 — Authentication and authorization design (PDF pp. 4–5)
6. B3 — API contract rules for Flutter compatibility (PDF pp. 5–6)
7. B7/B8 — R2 upload, file lifecycle and recovery (PDF p. 7)
8. Backend delivery plan B0–B12 with hard exit gates (PDF pp. 7–8)
9. Testing & pilot operations checklist (PDF p. 8)
10. Decisions to lock before major coding (PDF p. 8)

> **Original draft status:** DOCUMENT STATUS: TECHNICAL DESIGN DRAFT — NOT AN APPROVED API OR DATABASE CONTRACT. Approved product behavior is taken from Functional Requirements v1.5 (FR-001–137), Functional Requirements Addendum v1.0 (FR-138–167), IA and its addendum, 67 user flows, Stage 3.5 decisions, and 42 NFRs. Proposed table names, endpoint paths, technology choices and token formats require technical review before implementation. No existing FR is replaced.


### 1. Executive build rules

| Constraint | Agreed baseline / engineering consequence |
| --- | --- |
| Scope | 167 approved FRs: 150 MVP + 17 post-MVP; 42 NFR objectives. Do not build Google Drive (FR-090–100, 150–151), enhanced PDF reading (FR-163) or advanced in-app Admin monitoring/audit search (FR-105–107) in the MVP. Audit capture (FR-108 and 155) is MVP. |
| Platform | One Flutter mobile client for Android/iOS; Spring Boot provides authoritative data and access control. |
| Roles | STUDENT, SUB_MANAGEMENT, MANAGEMENT, ADMIN. Channel Owner is a channel relationship, not a role. No SUPER_ADMIN. |
| Pilot →production | Start on developer laptop with ~10 students, increase participation only after measurements. No 10-user cap. Later production validation: 4,000 registered / 500 active, 2× bursts, 100,000 metadata records; production 99.5% uptime objective. |
| Integrity | One active channel per student, six active members maximum per channel; all membership/owner decisions transactionally enforced by backend. |


<!-- Source PDF page 02 -->

| Constraint | Agreed baseline / engineering consequence |
| --- | --- |
| Media | Private Cloudflare R2; video ≤1 GB, PDF ≤100 MB, PPT/PPTX ≤200 MB; multipart resume, maximum three active uploads per device; publish only after complete validation and safety checks. |
| Recovery | Trash expires no later than 30 days; in-app notifications 90 days; audit records 365 days, subject to legal/institutional rules. |
| Offline files | Authenticated download permission check before export; downloaded copies go to Android Downloads / iOS Files and cannot reliably be remotely revoked. |

Use this decision hierarchy: (1) approved FR and addendum; (2) approved IA and addendum; (3) user flows and Stage 3.5 decision register; (4) NFR targets; (5) this technical proposal. Escalate conflicts instead of silently changing product rules.

### 2. B0 — Requirements-to-backend ownership map

```text
                                  Relevant approved
Backend module                    requirements                        API / data responsibilities
Identity & Security               FR-002–013, FR-113–137,             Google/Apple provider proof;
                                  FR-144, FR-156–158, FR-167          provisional onboarding;
                                                                      university email/OTP; linked
                                                                      identities; trusted
                                                                      device/session; role
                                                                      management.
Channels & Membership             FR-014–035, FR-110–111,             Create/find channels; approval
                                  FR-138–141, FR-144, FR-154,         queue;
                                  FR-166                              one-membership/six-seat
                                                                      rules; voluntary and Admin
                                                                      owner transfer; deletion
                                                                      restrictions.
Media Uploads                     FR-036–055, FR-145–149              Upload initiation, signed parts,
                                                                      resume/finalize/cancel;
                                                                      type/size/safety validation;
                                                                      name collision handling.
Library & Media Access            FR-056–079, FR-142–143,             Library/search, authorized
                                  FR-159–162, FR-164–165              playback/export, per-user
                                                                      video progress, file
                                                                      moves/Trash/restores.
Notifications & Audit             FR-080–089, FR-108,                 Durable inbox, category
                                  FR-152–155                          preferences, push attempts,
                                                                      event capture, retention jobs.
Administration & Policy           FR-005, 011–013, FR-101–104,        Registered user directory
                                  FR-109, FR-112, FR-157,             (read-only), role requests,
                                  FR-166                              institutional validation rules,
                                                                      exceptional owner-removal.
Deferred                          FR-090–100, FR-105–107,             No MVP delivery requirement;
                                  FR-150–151, FR-163                  keep future interfaces
                                                                      architecturally possible.
```

The accompanying UniSpace_FR_Traceability_Seed.csv enumerates every FR-001–167 with re-lease priority and proposed module ownership. Endpoint/test mappings begin blank deliberately; populate them during each feature’s design review.


<!-- Source PDF page 03 -->

### 3. B1 — Backend architecture (proposal)

Choose a modular monolith: one deployable Spring Boot application organized into cohesive business modules; MySQL stores state/metadata, private Cloudflare R2 stores original files, and delivery workers handle email, push, expiry, cleanup and malware scanning. Avoid microservices and Kubernetes in the laptop pilot.

`Flutter (Android / iOS)`

`| HTTPS JSON + Bearer session | | short-lived authorized media URLs`

`v | v`

`Spring Boot REST API ---------------------> Private Cloudflare R2`

`| auth / users / channels / media / notifications / admin`

`| domain rules + resource authorization`

`v`

`MySQL (Flyway migrations) <-- outbox/background jobs --> email OTP / FCM / cleanup / scanne`

Proposed Java package boundaries (domain-first; public use-case services, private persistence):

`com.unispace`

`auth/ | user/ | channel/`

`upload/ | media/ | notification/`

`administration/ audit/ | infrastructure/`

`common/ | security/`

Boundaries to enforce: Controllers validate HTTP shape; services enforce transactions and busi-ness invariants; repositories handle persistence; storage adapters are replaceable; no controller issues arbitrary direct object keys. API DTOs are not JPA entities. Modules communicate through explicit services or domain events, not repository-to-repository shortcuts. Use application/domain services and asynchronous outbox delivery for notifications. Suggested application profiles: local (localhost/known development network), test (isolated database and R2 substitute), pilot (restricted LAN/secured remote access, HTTPS), prod (later). Never commit credentials or allow public database connections.

### 4. B2 — Logical database model (proposed, not final DDL)

**Proposed logical entity tables — PDF pp. 3–4:**

```text
Entity / table                   Critical columns (illustrative)   Constraint / purpose
users                            id, student_id_normalized,        Unique Student ID/email once
                                 university_email_normalized,      assigned; statuses
                                 first_name, last_name, role,      PENDING_PROFILE,
                                 status                            PENDING_EMAIL_VERIFICATION,
                                                                   ACTIVE, DISABLED.
provider_identities              id, user_id, provider,            Unique (provider,
                                 provider_subject                  provider_subject); supports
                                                                   Google and Apple on one
                                                                   account; never auto-link by
                                                                   matching email.
trusted_devices + sessions       user_id, device identifier,       Trust-until-sign-out/revoke;
                                 trust_revoked_at,                 separately expiring session;
                                 refresh_token_hash, expiry        store token hashes, not raw
                                                                   refresh tokens.
email_challenges                 user_id, challenge_hash,          One-use OTP, 10-minute
                                 purpose, expires_at,              validity, 60-second resend, 5
                                 attempt_count, consumed_at        wrong attempts; rate limiting.
channels                         id, name, normalized_name,        Unique normalized name;
                                 owner_user_id, version            nullable owner for 0/6; use row
                                                                   lock for membership/owner
                                                                   updates.
channel_memberships              user_id, channel_id, joined_at    Unique user_id for current
                                                                   active membership. Owner
                                                                   must be an active member
                                                                   when channel is nonempty.

Entity / table                  Critical columns (illustrative)   Constraint / purpose
membership_requests             id, user_id, target_channel_id,   Ensure one pending request
                                source_channel_id, status,        per user/target; serialized
                                decided_by                        decision and audit.
role_requests                   id, user_id, requested_role,      Track
                                status, decided_by                pending/approved/rejected;
                                                                  promotion requires
                                                                  membership resolved.
file_items                      id, channel_id,                   Original uploader immutable;
                                uploader_user_id,                 randomized storage key;
                                display_name, object_key,         states isolate unpublished
                                state, trashed_at,                uploads; last channel for
                                restore_channel_id                restore.
upload_sessions +               id, file_id,                      Recover completed parts
upload_parts                    provider_multipart_id, part_no,   without unsafe re-upload;
                                etag, expires_at, state           reconcile expired/abandoned
                                                                  sessions.
video_progress                  user_id, file_id, position_ms     Unique (user_id, file_id);
                                                                  progress is not an access
                                                                  grant.
notifications +                 user_id, category, read_at,       Inbox durable and push
notification_preferences        expires_at, push_enabled          best-effort; 90-day expiry.
audit_events +                  actor_id, action, entity_ref,     Append-only audit (365 days);
outbox_events                   timestamp, correlation_id,        resilient eventual notification
                                payload                           dispatch; no secrets in
                                                                  payloads.
institutional_policies          student_id_rule,                  Admin-managed versioned
                                permitted_email_domains,          policy; do not retroactively
                                changed_by                        invalidate valid users.
```

ERD is supplied as UniSpace_Backend_Logical_ERD.mmd. This is a logical model, not a final migration. Check relationships/constraints against all 150 MVP FRs before generating SQL.

Non-negotiable invariants and concurrency

### 1. Membership capacity: lock destination channel row, recheck active member count inside

transaction, insert only if count < 6. Enforce unique current membership by database con-straint.

### 2. Owner consistency: owner remains a current member when channel has members. Owner

transfer + leave/switch or Admin removal happen in one database transaction. Empty channel may have owner_user_id = NULL; first admitted student becomes owner atomically.

### 3. Channel deletion: lock/recheck members = 0, active files = 0 and Trash files = 0; warn

and transactionally cancel pending membership requests; notification follows via outbox.

### 4. Role promotion: STUDENT must exit channel (and transfer ownership if necessary) before

staff promotion. Last active ADMIN cannot be demoted/revoked.

### 5. File authority: uploader attribution never changes; channel access revoked upon departure

except staff privileges. Trash restores to last channel only when valid and authorized.

### 6. Unique names: normalize channel names for case/whitespace; uploads, file moves and re-names auto-suffix conflicts (e.g. (1)), never overwrite and handle races transactionally.

### 7. Remote storage: MySQL and R2 are not one transaction; use persisted state, reconciliation

and cleanup. Never set file READY before content verification and safety checks succeed.

### 5. B3 — Authentication and authorization design

Do not put provider secrets in Flutter. Proposed safe sequence:

`Google/Apple sign-in on device`

`-> provider proof to backend -> verify issuer/audience/signature/nonce as applicable`

`-> find provider identity OR create provisional user`

`-> collect names, unique Student ID, organizational email`


<!-- Source PDF page 05 -->

`-> verify 10-minute, one-use university-email OTP`

`-> user ACTIVE -> register trusted device -> issue secure UniSpace session`

`-> Flutter stores permitted credentials in OS secure storage`

Returning trusted device: valid session may reopen Home without login. Trusted status has no fixed age, but session tokens still expire. On sign-out or security revocation, invalidate session/trust. New or untrusted device: provider authentication plus OTP to previously verified university email. Provider linking: only a signed-in user can explicitly link another verified Google/Apple subject; deny subject already linked elsewhere and never merge by email-only match. Authorization pattern: (authenticated ACTIVE user) + (system role) + (current channel membership/owner) + (file uploader or staff privilege) + (resource state) on each protected request, not merely on Flutter button visibility. Where revealing a resource’s existence is unsafe, consider not-found responses rather than disclosing it.

```text
                                          Sub-
Operation            Student / owner      management            Management           Admin
Join approval,       Current owner        No                    No                   No ordinary
occupied channel     only                                                            override
                                                                                     approved
Join approval,       No                   No                    No                   Yes
empty channel
Upload / view        Own channel          All channels          All channels         All channels
channel files
Modify files         Own uploads in       Own uploads           Any file             Any file
                     authorized
                     channel
Move files across    No                   No                    Yes                  Yes
channels
Rename / delete      No                   No                    No                   Yes
eligible channel
Force-remove         No                   No                    No                   Yes, narrow
Channel Owner                                                                        exception
                                                                                     (FR-166)
Modify staff roles   No                   No                    No                   Yes, never
/ Admin roles                                                                        remove last
                                                                                     Admin
```

Threats to test first: forged provider proof, duplicate identity linking, OTP brute-force/replay, token theft, revoked membership using stale file URLs, student crossing channel boundaries, last- Admin demotion, multipart upload tampering and direct R2 object access. Exported native OS copies cannot be revoked after download. Unsettled technical choices (do not guess): provider integration details, signing/refresh ro-tation, token and signed-URL TTL, trust/device identifier, session revocation mechanism and se-cret/key management. Document these in Architecture Decision Records (ADRs) before B5.

### 6. B3 — API contract rules for Flutter compatibility

OpenAPI is the shared contract. See bundled UniSpace_OpenAPI_Starter_v0_1.yaml: a starter design, not a comprehensive or approved API. Decide the path shapes and schemas before building both clients.

| Rule | Proposed convention |
| --- | --- |
| Prefix | /api/v1, JSON request/response except signed-object transfer |
| Identity | Opaque IDs (UUIDs are an option); backend derives actor from verified session, never trusts userId in request as authority |


<!-- Source PDF page 06 -->

| Rule | Proposed convention |
| --- | --- |
| Time | ISO 8601 UTC timestamps; explicit timezone handling |
| Pagination | Cursor or page/size; choose one for list contracts, no unbounded user/file listings |
| Errors | Standard application/problem+json shape with stable code, status, detail, traceId; field validation details when appropriate |
| Auth errors | 401 missing/expired authentication; 403 authenticated but unauthorized; 409 conflicting channel name/capacity/duplicate request |
| Retries | Idempotency key or equivalent unique operation state for joins, decisions, uploads and other repeated mutations |
| Long-running tasks | Upload state transitions; no false synchronous READY response before verification |
| Compatibility | Version OpenAPI alongside backend; generate Dart API types/client where practical; contract tests in CI |

Illustrative channel create:

`POST /api/v1/channels`

`Authorization: Bearer <session-token>`

`Content-Type: application/json`

`{"name":"Programming"}`

`{"id":"channel-uuid","name":"Programming","memberCount":1,"capacity":6,"ownerUserId":"user-`

Example conflict response:

`{"type":"about:blank","title":"Conflict","status":409,"code":"CHANNEL_NAME_EXISTS","detail"`

Priority API families (design first, implement by phase)

| Family | Example routes (not yet ratified) |
| --- | --- |
| Identity | /auth/provider-exchange, /auth/verification, /auth/sessions, /auth/provider-links, /me |
| Channels | GET/POST /channels, /channels/{id}/join-requests, /channels/{id}/owner-departure, /channels/{id}/leave |
| Media | /uploads, /uploads/{id}/parts, /uploads/{id}/complete, /files/{id}/playback-access, /files/{id}/download-access |
| Files | /files/{id}, /files/{id}/trash, /trash/{id}/restore, /trash/{id}/permanent-delete |
| Notifications | /notifications, /notifications/mark-all-read, /me/notification-preferences |
| Admin | /admin/users, /admin/role-requests, /admin/channels/{id}/force-remove-owner, /admin/institutional-policy |


<!-- Source PDF page 07 -->

### 7. B7/B8 — R2 upload, file lifecycle and recovery

`Select local file -> Flutter validates type/size -> POST initiate (backend checks role/chan`

`-> backend creates PENDING upload + temporary private multipart session`

`-> Flutter requests short-lived signed URLs for individual parts`

`-> upload 1..N parts directly to private R2; client saves resumable session metadata`

`-> complete -> backend verifies uploaded parts, size, type and required safety checks`

`-> READY & visible only on success; otherwise REJECTED/FAILED with cleanup/retry plan`

Critical implementation details: (a) no 1 GB in-memory buffering in Spring Boot, (b) three con-current files per device; individual parts may require separate concurrency governance, (c) secure part-resume reconciliation on app restart, (d) server-side verification of claimed file type and size, (e) malware scanning/quarantine is an early feasibility spike (PDF/PPT and large videos), (f) publish only after verification and safe scan result, (g) automatic cleanup of abandoned multipart sessions and orphaned R2 objects. Trash: moving to Trash hides file from ordinary library; direct permanent delete supported for authorized users; restore before expiry back to stored last channel and only with valid authorization; permanent cleanup no later than 30 days. Files in Trash block channel deletion. Native exports: backend authorizes issuance; Flutter saves to user-chosen OS-visible storage; the downloaded copy is outside the server’s revocation control.

### 8. Backend delivery plan B0–B12 with hard exit gates

```text
Phase                               Build                               Exit evidence (do not skip)
B0                                  MVP requirements +                  150 MVP FRs assigned
                                    dependency inventory                module/owner; unknowns
                                                                        captured; post-MVP held out.
B1                                  Architecture & ADRs                 Modular monolith boundaries;
                                                                        threat model; storage
                                                                        workflows; technical decisions
                                                                        recorded.
B2                                  Schema and ERD                      Reviewed relations,
                                                                        uniqueness/transactions,
                                                                        Flyway v1, migration/rollback
                                                                        strategy.
B3                                  OpenAPI and security contract       Auth flow, errors, permissions,
                                                                        pagination, idempotency,
                                                                        upload state contract agreed
                                                                        with Flutter track.
B4                                  Spring Boot foundation              App starts; MySQL migration
                                                                        succeeds; health check,
                                                                        configuration, tests and CI
                                                                        pass.
B5                                  Authentication & roles              Google/Apple proof verified;
                                                                        OTP/device trust/provider
                                                                        linking, last-Admin guard and
                                                                        tests pass.
B6                                  Channels                            Concurrency test prevents 7/6
                                                                        and double membership;
                                                                        owner transfer/rejoin/deletion
                                                                        atomic.
B7                                  R2 upload pipeline                  Partial resume, size checks,
                                                                        malware quarantine,
                                                                        complete/failed state and
                                                                        orphan cleanup tested.
B8                                  File actions and Trash              Owner/role matrix,
                                                                        move/rename/restore, deletion
                                                                        and 30-day cleanup tests pass.

Phase                            Build                                 Exit evidence (do not skip)
B9                               Playback & native downloads           Authorized expiring media
                                                                       access, progress, export
                                                                       permission and revoked-access
                                                                       tests.
B10                              Notifications                         Durable inbox, preferences,
                                                                       90-day expiry, at-most-once
                                                                       approvals and best-effort push.
B11                              Core Admin                            Role approvals, institutional
                                                                       policy, 0/6 queue, exceptional
                                                                       owner removal and audit
                                                                       capture.
B12                              Integration & pilot                   Flutter contract/e2e tests,
                                                                       backup restore drill, secure
                                                                       laptop access, pilot load ramp
                                                                       and regression suite.
```

Integration begins at B3, not B12: Flutter uses the same OpenAPI schema and mocks, then switches to real APIs module by module. For every feature deliver: FR linkage, contract update, success/error/revocation test, matching Flutter state, no regressions.

### 9. Testing & pilot operations checklist

Automated tests, minimum coverage of behavior:

- Unit: policies for Student/Owner/Sub-Management/Management/Admin, OTP expiry, filename suffixes and validation.

- MySQL integration: true concurrent channel approvals, owner-transfer transactions, unique identities and file-state changes; use MySQL/Testcontainers for dialect-sensitive behavior.

- HTTP security/contract: 401/403/conflict/error formats, role escalation, provider-link replay, token/permission revocation.

- R2/storage integration: multipart resume after restart, failed/fake completion, expired sign-ing, orphan cleanup, no unsafe publication.

- Flutter integration: generated API models, secure token storage, refresh/retry, of-fline/error/permission UI, native OS export.

- Release: every MVP FR (all 150) mapped to ≥1 test; NFR tests separately; no release-blocking security/integrity bugs. Laptop pilot: use isolated secrets, a protected backup destination, HTTPS when devices access backend, MySQL not publicly exposed, basic Actuator health and privacy-safe logs. Test first ~10 students, then grow while monitoring p95 latency, CPU/RAM, MySQL load, bandwidth, failed opera-tions and recovery. Cloud infrastructure and 500-concurrent validation are production-phase tasks.

### 10. Decisions to lock before major coding

```text
Decision to ratify                                    Why it matters
Provider validation, session/token format,            Wrong identity architecture causes account
refresh rotation, device binding                      duplication or insecure revocation.
UUID/storage key naming; database                     Changing these after shipping breaks all feature
normalization & locking strategy                      modules.
R2 multipart signing/cleanup and feasible             Affects maximum file size, security,
malware scan pipeline                                 performance and costs.
API response/error/ID/pagination conventions          Prevents Flutter ↔ Spring Boot mismatches.
Pagination/load-test fixture, DB backup cadence       Needed before inviting external students.
and restore procedures
Minimum Android/iOS versions and Flutter              Must be tested on supported actual devices.
native-export mechanism
```



<!-- Source PDF page 09 -->

Immediate next action: Complete B0 traceability review and B1 Architecture Decision Records, then review B2 ERD and B3 OpenAPI draft with the Flutter track. Do not implement all controllers or all entities before these reviews.

Document reference set: UniSpace FR v1.5 + FR Addendum v1.0; IA v1.0 + IA Addendum v1.0; User Flows & Journeys v1.0; Stage 3.5 approved decisions; NFR v1.0. Any conflict must be resolved through controlled design review, not silently normalized.
