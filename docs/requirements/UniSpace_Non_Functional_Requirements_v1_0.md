# UniSpace Non Functional Requirements v1 0

> **Source:** `UniSpace_Non_Functional_Requirements_v1_0(2).pdf`  
> **Original pages:** 12  
> **Document classification:** requirements  
> **Conversion:** text-only Markdown transcription; original PDF remains the visual/layout record.  
> **Status note:** Preserve original PDF approval/status wording; later addenda govern newer decisions.

---


<!-- Source PDF page 01 -->

NON-FUNCTIONAL REQUIREMENTS / v1.0

**APPROVED SPECIFICATION / PILOT + PRODUCTION OBJECTIVES**

UniSpace

## Non-Functional Requirements

## Specification

Flutter Mobile Application | Android & iOS | Version 1.0

This specification defines how reliably, securely and accessibly UniSpace must operate. It distinguishes the initial laptop-based test environment from the later university-scale production objectives.

| 42 | 11 | 2 |
| --- | --- | --- |
| Approved NFRs | Quality categories | Validation environments |

### Document control

Approved scope: 42 NFR objectives across 11 categories. Approved Functional Requirements: 167 (150 MVP / 17 post-MVP).

Validation status: Objectives approved; load-test configurations, OS support matrix, backup recovery targets and measured results remain pending.

Change control: NFR targets do not silently revise the approved FR v1.5 plus controlled FR addendum.


<!-- Source PDF page 02 -->

## 01 Document scope and verification

The NFRs below apply to the UniSpace Flutter mobile app, Spring Boot API, MySQL data layer and Cloudflare R2 media storage. Essential security, privacy, integrity and accessibility protections apply from the initial test phase; production-only capacity and uptime objectives are not laptop-pilot release gates.

### Approval meaning

APPROVED means the stated quality objective is accepted as a design and testing requirement. It does not mean the requirement has been implemented, achieved, benchmarked or independently audited. Where an environment or metric is unspecified, the appropriate stage must define it before a pass/fail claim is possible.

### Requirement inventory

| Category | IDs | Count |
| --- | --- | --- |
| 1. Performance | NFR-PERF-001–005 | 5 |
| 2. Reliability & Availability | NFR-REL-001–005 | 5 |
| 3. Security | NFR-SEC-001–007 | 7 |
| 4. Privacy & Data Protection | NFR-PRIV-001–003 | 3 |
| 5. Scalability | NFR-SCAL-001–003 | 3 |
| 6. Accessibility | NFR-ACC-001–004 | 4 |
| 7. Usability | NFR-USE-001–003 | 3 |
| 8. Compatibility | NFR-COMP-001–003 | 3 |
| 9. Data Integrity & Recovery | NFR-DATA-001–004 | 4 |
| 10. Maintainability & Testability | NFR-MAINT-001–002 | 2 |
| 11. Monitoring & Operations | NFR-OPS-001–003 | 3 |
| Total | 11 categories | 42 |

### Related approved baselines

Functional Requirements v1.5 (FR-001–137) + Controlled Requirements Addendum (FR-138–167); Information Architecture v1.0 and approved updates; User Flows & Journeys v1.0 (67 flows); Stage 3.5 product decisions and Balanced MVP classification.


<!-- Source PDF page 03 -->

## 01 Performance

| 5 approved requirements | NFR-PERF-001–005 |
| --- | --- |
| ID / TOPIC | Requirement, acceptance objective and verification |

### NFR-PERF-001 — Application startup

Home becomes usable within 3 seconds on a supported reference device with a valid trusted session.

**Verify:** Measure warm/cold starts on declared reference devices; confirm valid-session context.

### NFR-PERF-002 — API responsiveness

At least 95% of ordinary synchronous API requests complete within 500 ms under a specified representative load.

**Verify:** Measure p95 server/API latency; exclude long-running upload and background jobs from the ordinary-API class.

### NFR-PERF-003 — Video startup

An authorized supported video begins playback within 3 seconds under suitable, specified network conditions.

**Verify:** Measure tap-to-play to first rendered frame using reference clips, device, and network.

### NFR-PERF-004 — Channel search

At least 95% of ordinary channel searches return within 1 second under the defined test workload.

**Verify:** Measure p95 end-to-end search response on seeded channel data.

### NFR-PERF-005 — Upload responsiveness

File upload shows recurring progress information without blocking normal app interactions.

**Verify:** Test active upload + navigation/scroll/tap responsiveness; agree update cadence in test plan.


<!-- Source PDF page 04 -->

## 02 Reliability & Availability

| 5 approved requirements | NFR-REL-001–005 |
| --- | --- |
| ID / TOPIC | Requirement, acceptance objective and verification |

### NFR-REL-001 — Service availability

Laptop pilot runs on a best-effort basis, with no 24/7 service commitment. Future production target is 99.5% uptime per calendar month, including planned maintenance.

**Verify:** Pilot: restart/recovery checks. Production: instrument service uptime and calculate monthly percentage.

### NFR-REL-002 — Upload recovery

Interrupted uploads resume from completed parts when the original file and transfer session remain valid.

**Verify:** Interrupt Wi-Fi/process; reopen app; resume; confirm no re-transfer of completed parts where supported.

### NFR-REL-003 — Incomplete upload protection

No failed, rejected, incomplete, or unverified upload becomes published content.

**Verify:** Inject failures at each upload stage; verify 100% of tested unsafe/incomplete items stay unavailable.

### NFR-REL-004 — Network failure handling

Temporary connectivity loss gives understandable feedback and recovery actions without crashing the application.

**Verify:** Test offline/online transitions during key screens and media operations.

### NFR-REL-005 — Safe operation retries

Retries must not create duplicate memberships, file records, approvals, or administrative changes.

**Verify:** Repeat identical requests and concurrent retries; inspect resulting database state.


<!-- Source PDF page 05 -->

## 03 Security

| 7 approved requirements | NFR-SEC-001–007 |
| --- | --- |
| ID / TOPIC | Requirement, acceptance objective and verification |

### NFR-SEC-001 — Secure communication

Sensitive network exchanges use HTTPS with TLS 1.2 or higher; TLS 1.3 is preferred where supported.

**Verify:** Check mobile API/storage endpoints, TLS negotiation, and insecure-request rejection.

### NFR-SEC-002 — Server-side authorization

Every protected Spring Boot operation enforces role- and resource-level permissions; unauthorized requests are denied.

**Verify:** Exercise role, ownership, channel, and revoked-access test matrix against the backend.

### NFR-SEC-003 — Credential protection

Authentication tokens and secrets use appropriate secure storage and never appear hardcoded in Flutter or in application logs.

**Verify:** Review code/build artifacts, device credential storage, log samples and server secrets configuration.

### NFR-SEC-004 — Authentication protection

OTP replay, excessive verification attempts and unauthorized session reuse are prevented.

**Verify:** Test approved OTP policy, replay, resend, sign-out, expired token and revoked device conditions.

### NFR-SEC-005 — Private media storage

Cloudflare R2 objects remain private; access requires current authorization and time-limited delivery mechanisms.

**Verify:** Attempt direct unauthorized object access; test expiring and renewed authorized URLs.

### NFR-SEC-006 — File and dependency safety

Uploaded files pass required safety checks before publication; no known critical exploitable dependency vulnerability remains unresolved at release.

**Verify:** Test prohibited/malicious samples in controlled environment; run software-composition/dependency checks.

### NFR-SEC-007 — Auditing and revocation

Important security-sensitive operations are audited; revoked sessions or permissions cannot authorize subsequent protected server actions.

**Verify:** Revoke role/session/device trust, retry API calls; inspect security events and audit capture.


<!-- Source PDF page 06 -->

## 04 Privacy & Data Protection

| 3 approved requirements | NFR-PRIV-001–003 |
| --- | --- |
| ID / TOPIC | Requirement, acceptance objective and verification |

### NFR-PRIV-001 — Personal-data access

Student names, IDs, verified university emails and account information are accessible only to authorized users.

**Verify:** Access-control tests cover user profiles, account-directory endpoints, and error payloads.

### NFR-PRIV-002 — Minimization and retention

Collect only personal data needed for approved functionality; account-data retention/deletion follows documented institutional and applicable legal rules.

**Verify:** Document data inventory; confirm notifications 90 days, audits 365 days, Trash no later than 30 days; define account retention with institution.

### NFR-PRIV-003 — Sensitive-data exposure

Do not expose sensitive details in logs, crash reports or unauthorized API responses; protect stored authentication credentials.

**Verify:** Review log/crash-report redaction and negative-path API responses; inspect secret storage.


<!-- Source PDF page 07 -->

## 05 Scalability

| 3 approved requirements | NFR-SCAL-001–003 |
| --- | --- |
| ID / TOPIC | Requirement, acceptance objective and verification |

### NFR-SCAL-001 — User and concurrency growth

Begin pilot testing with approximately 10 students and increase as resources allow, with no artificial 10-user cap. Production objective: 4,000 registered and 500 concurrently active users meeting applicable API targets.

**Verify:** Load-test realistic activity mixes at increasing loads; track errors, p95 latency, CPU/RAM, network and database health.

### NFR-SCAL-002 — Peak traffic resilience

Pilot gradually introduces burst traffic; production must gracefully handle twice normal peak without corrupting data, duplicating operations or failing uncontrollably.

**Verify:** Run burst tests; verify integrity, backpressure and recovery after load subsides.

### NFR-SCAL-003 — Media-record growth

Pilot tests progressively larger datasets. Production objective: at least 100,000 media/document metadata records without significant browse/search/management degradation.

**Verify:** Seed growing datasets; assess query plans, indexing, page performance and operation latencies.

## 06 Accessibility

| 4 approved requirements | NFR-ACC-001–004 |
| --- | --- |
| ID / TOPIC | Requirement, acceptance objective and verification |

### NFR-ACC-001 — Accessibility standard

Essential MVP screens and workflows target applicable WCAG 2.2 Level AA criteria on Android and iOS.

**Verify:** Map applicable criteria to Flutter screens; document manual and automated accessibility reviews.

### NFR-ACC-002 — Assistive technology

Interactive controls provide logical focus order, accessible names, roles and states for TalkBack and VoiceOver.

**Verify:** Navigate critical flows with Android TalkBack and iOS VoiceOver on real devices.

### NFR-ACC-003 — Readable and scalable content

Support 200% text scaling without loss of essential functionality; target contrast >=4.5:1 normal text and >=3:1 large text and relevant visual controls.

**Verify:** Test text enlargement, wrapping/overflow and theme contrast on representative screens.

### NFR-ACC-004 — Accessible interactions

Use accessible control sizes (approximately 48 dp Android, 44 pt iOS) and do not require color, gesture-only input or animation to operate essential features.

**Verify:** Audit touch targets and alternatives; test reduced-motion/platform accessibility settings.


<!-- Source PDF page 08 -->

## 07 Usability

| 3 approved requirements | NFR-USE-001–003 |
| --- | --- |
| ID / TOPIC | Requirement, acceptance objective and verification |

### NFR-USE-001 — Critical task completion

Representative students and staff can complete essential UniSpace tasks without unnecessary assistance.

**Verify:** Observe registration, channel join, finding/playing media, uploads and admin approvals; agree success thresholds in Stage 4.

### NFR-USE-002 — Clear system feedback

Relevant screens explain default, loading, empty, success, error, offline and permission conditions in understandable terms.

**Verify:** Review the Stage 4 screen-state matrix and conduct scenario-based usability tests.

### NFR-USE-003 — Recoverable mistakes

Errors show practical next actions and preserve valid inputs whenever appropriate.

**Verify:** Test invalid ID/email, network loss, expired verification, upload errors and blocked operations.


<!-- Source PDF page 09 -->

## 08 Compatibility

| 3 approved requirements | NFR-COMP-001–003 |
| --- | --- |
| ID / TOPIC | Requirement, acceptance objective and verification |

### NFR-COMP-001 — Android support

Define minimum supported Android versions and representative device profiles; verify essential functionality on those profiles.

**Verify:** Prepare OS/device matrix and run functional/regression checks, including native Downloads export.

### NFR-COMP-002 — iOS support

Define minimum supported iOS versions and verify essential functionality before releasing an iOS build.

**Verify:** Test on eligible iOS devices/build environments; Windows laptop-only testing is not equivalent to iOS verification.

### NFR-COMP-003 — Media and export compatibility

Approved video, PDF and PPT/PPTX formats, preview fallbacks, and native file export behave consistently on declared supported platforms.

**Verify:** Use format/device compatibility matrix and test open/download/fallback scenarios.

## 09 Data Integrity & Recovery

| 4 approved requirements | NFR-DATA-001–004 |
| --- | --- |
| ID / TOPIC | Requirement, acceptance objective and verification |

### NFR-DATA-001 — Transactional integrity

Membership, channel ownership, role assignments, approval outcomes and related state changes remain valid under concurrent operations.

**Verify:** Execute concurrent joins, transfers, promotions and retries; assert constraints and atomicity.

### NFR-DATA-002 — Media metadata integrity

Stored media objects and MySQL records stay consistent across upload, publication, move, Trash, restore and permanent deletion.

**Verify:** Inject object-store/database failures; inspect orphan detection and safe reconciliation.

### NFR-DATA-003 — Recoverable backups

Important database data, essential metadata and configuration are protected by a documented backup procedure.

**Verify:** Create and inspect actual backups; define schedules and protection of backup credentials.

### NFR-DATA-004 — Verified restoration

Recovery procedures are tested to restore application data to a consistent usable state.

**Verify:** Perform restore drill; define measurable recovery point/time objectives before production.


<!-- Source PDF page 10 -->

## 10 Maintainability & Testability

| 2 approved requirements | NFR-MAINT-001–002 |
| --- | --- |
| ID / TOPIC | Requirement, acceptance objective and verification |

### NFR-MAINT-001 — Maintainable components

Flutter and Spring Boot code is organized into understandable, documented modules with clear responsibilities.

**Verify:** Review feature boundaries, API contracts, component reuse, and maintainability conventions.

### NFR-MAINT-002 — Automated verification

Critical features have appropriate unit, integration, Flutter widget, security and regression tests.

**Verify:** Track test cases to FR/NFR/user flows and require passing gates in the feature Definition of Done.


<!-- Source PDF page 11 -->

## 11 Monitoring & Operations

| 3 approved requirements | NFR-OPS-001–003 |
| --- | --- |
| ID / TOPIC | Requirement, acceptance objective and verification |

### NFR-OPS-001 — Actionable diagnostic logs

Application events and failures produce useful diagnostic records without exposing private data or credentials.

**Verify:** Inspect correlation IDs, key errors, privacy redaction and log access protection.

### NFR-OPS-002 — Basic health and resource visibility

The pilot includes usable service health checks and indicators for resource use and performance.

**Verify:** Observe Spring Boot readiness, CPU/RAM, DB connectivity and API errors during pilot testing.

### NFR-OPS-003 — Failure detection and recovery

Important operational failures can be detected, investigated and handled using documented procedures; production alerting may be added later.

**Verify:** Simulate backend/storage outages and verify operator can identify and recover from the issue.

## 12 Deployment phases and targets

Pilot infrastructure is a test environment, not a product user cap. Institutional service-level goals remain future production objectives.

| Phase | Environment | Validation emphasis |
| --- | --- | --- |
| 1. Initial pilot | Developer laptop + existing subscriptions; | Core end-to-end journeys, security, backup/recovery, |

about 10 students to start. | usability and stable local functionality.

2. Expanded pilot | Same environment while practical; | Record p95 latency, throughput, memory/CPU,

| gradually increase real students and | database load, network, errors, media handling; stop |
| --- | --- |
| simulated concurrency. | increasing load when unsafe or unstable. |

3. Production | Appropriately sized, monitored cloud | Validate 4,000 registered / 500 active, 2x peak bursts,

infrastructure selected from pilot evidence. | 100,000 metadata records, and 99.5% monthly uptime.

### Pilot infrastructure guardrails

- Do not impose a hard 10-user account limit or pretend the laptop has production availability.

- Do not expose local database or administration services publicly without appropriate safeguards; use secure, authorized network access.

- Measure each increase in load, preserve database and file safety, and use realistic representative student and staff activities.

- Native Android/iOS Downloads/Files exports remain under the device owner’s control and cannot be remotely revoked by UniSpace.


<!-- Source PDF page 12 -->

## 13 Outstanding implementation verification

The following test-design details remain open. They are required to demonstrate compliance, not additional unapproved product features.

| Performance | Reference devices; cold/warm launch definitions; network quality; p95 measurement boundaries; upload progress cadence. |
| --- | --- |
| Scale and availability | Pilot ramp plan; realistic workload mixes; production hosting topology; uptime measurement period; 2x peak baseline. |
| Platforms and media | Minimum supported Android/iOS versions; device matrix; video encoding profiles; PPT/PPTX fallback compatibility. |
| Recovery | Backup frequency, retention and access protection; restore drills; recovery point objective (RPO) and recovery time objective (RTO). |
| Accessibility and usability | Stage 5 WCAG applicability matrix; assistive-technology testing; Stage 4 quantitative task success criteria. |
| Security and privacy | Threat model; token lifecycle and signed URL durations; institution-specific retention obligations; privacy reviews. |
| Traceability | Map every NFR to its test cases and supported components; connect applicable NFRs to FRs and User Flows. |

### Previously approved data retention

| Notification inbox | 90 days |
| --- | --- |
| Audit records | 365 days |
| Trash | No later than 30 days |

### Document completion status

[APPROVED] 42/42 NFR objectives, 11/11 categories.

[APPROVED] Pilot-first approach without a fixed 10-user cap.

[PRESERVED] University production targets: 4,000 registered, 500 active, 2x peak, 100,000 media records, 99.5% uptime.

[PENDING] Test details, quantitative verification, platform release matrix and measured conformity.

### Next project stage

Stage 4 — UI/UX Requirements, Screen-State Specifications, and Requirements Traceability Matrix. Accessibility requirements continue through Stage 5 and all subsequent design and implementation phases.
