# UniSpace Backend — B0-04 Technical Risk Register

> **Document ID:** B0-04  
> **Version:** v0.1 — planning handoff  
> **Prepared:** 22 November 2026  
> **Status:** **B0.5 risk set, severity and mitigation directions approved; proposed verification/ownership details require later review.**  
> **Links:** B0-01 traceability, B0-02 ownership, B0-03 consistency principles

## 1. Scope and approval boundary

The user approved **R01–R10** in B0.5 on 19 November 2026. This register preserves the **ten approved risk names, severity ratings and concise mitigation directions** exactly in substance. Expanded failure signals, suggested tests, proposed owners and lifecycle status are a handoff aid, **not newly approved requirements**.

The approved quality baseline is 42 NFR objectives. **Critical / High / Medium** are the approved qualitative severity labels, **not** calculated probability scores. No likelihood numbers, priority algorithm or named risk owner has been approved. “OPEN (planning)” means the risk remains to be handled by implementation; it does **not** mean a known security incident exists.

## 2. Approved R01–R10 register

| ID | Risk | Approved severity | Approved mitigation direction |
|---|---|---|---|
| R01 | Unauthorized access | **Critical** | Server-side role and resource authorization |
| R02 | Concurrent membership conflicts | **Critical** | Database transactions and constraints |
| R03 | Incomplete or malicious uploads | **Critical** | Validation and safety checks before publication |
| R04 | MySQL and R2 inconsistency | **High** | State tracking and reconciliation |
| R05 | Interrupted mobile uploads | **High** | Resumable multipart uploads |
| R06 | Authentication/session vulnerabilities | **Critical** | Verified provider identities, secure sessions and revocation |
| R07 | Duplicate operations after retries | **High** | Idempotency controls |
| R08 | Notification delivery failures | **Medium** | Reliable asynchronous delivery and retries |
| R09 | Laptop performance limitations | **Medium** | Resource monitoring and gradual load testing |
| R10 | Accidental data loss | **High** | Database backups, recovery procedures and retention controls |

## 3. Expanded review entries — technical elaboration only

### R01 — Unauthorized access (Critical)

**Approved mitigation:** Server-side role and resource authorization.  
**Possible failure signal (proposed):** Unauthorized user opens/stores/exports protected file; role revocation not enforced.  
**Reference:** NFR-SEC-002; NFR-SEC-007; FR-030, FR-078, FR-142.  
**Suggested verification (not yet executed):** Permission matrix across STUDENT/OWNER/SUB_MANAGEMENT/MANAGEMENT/ADMIN; attempt stale-link access; verify denial.  
**Risk owner:** TBD during B1; **implementation state:** not assessed; **risk tracking state:** OPEN (planning).

### R02 — Concurrent membership conflicts (Critical)

**Approved mitigation:** Database transactions and constraints.  
**Possible failure signal (proposed):** 7/6 channel membership, simultaneous first owners, orphan owner after switch.  
**Reference:** NFR-DATA-001; FR-138–141, FR-166.  
**Suggested verification (not yet executed):** True concurrent last-seat joins and owner transfer; enforce exactly one accepted decision.  
**Risk owner:** TBD during B1; **implementation state:** not assessed; **risk tracking state:** OPEN (planning).

### R03 — Incomplete or malicious uploads (Critical)

**Approved mitigation:** Validation and safety checks before publication.  
**Possible failure signal (proposed):** Incomplete/rejected/malicious object appears READY; invalid format or oversize accepted.  
**Reference:** NFR-REL-003; NFR-SEC-006; FR-036–055, FR-145.  
**Suggested verification (not yet executed):** Failure-injection/unsafe-sample tests; unpublished status until checks pass.  
**Risk owner:** TBD during B1; **implementation state:** not assessed; **risk tracking state:** OPEN (planning).

### R04 — MySQL and R2 inconsistency (High)

**Approved mitigation:** State tracking and reconciliation.  
**Possible failure signal (proposed):** Orphan remote object; READY metadata without object; deletion only applied in one system.  
**Reference:** NFR-DATA-002; FR-036–055, FR-069–079.  
**Suggested verification (not yet executed):** Failure-injection at upload/finalize/delete; orphan detection and reconciliation drill.  
**Risk owner:** TBD during B1; **implementation state:** not assessed; **risk tracking state:** OPEN (planning).

### R05 — Interrupted mobile uploads (High)

**Approved mitigation:** Resumable multipart uploads.  
**Possible failure signal (proposed):** Network/app termination loses progress or creates duplicate/partial published file.  
**Reference:** NFR-REL-002; FR-148–149.  
**Suggested verification (not yet executed):** Kill app/restart/intermittent connectivity; resume valid session safely.  
**Risk owner:** TBD during B1; **implementation state:** not assessed; **risk tracking state:** OPEN (planning).

### R06 — Authentication/session vulnerabilities (Critical)

**Approved mitigation:** Verified provider identities, secure sessions and revocation.  
**Possible failure signal (proposed):** Forged provider identity; OTP reuse; revoked device/session keeps accessing backend.  
**Reference:** NFR-SEC-003–004; FR-113–137, FR-156, FR-158, FR-167.  
**Suggested verification (not yet executed):** Provider ownership, OTP attempts/expiry, revoked token, account-link uniqueness tests.  
**Risk owner:** TBD during B1; **implementation state:** not assessed; **risk tracking state:** OPEN (planning).

### R07 — Duplicate operations after retries (High)

**Approved mitigation:** Idempotency controls.  
**Possible failure signal (proposed):** Repeated approval, upload-finalization, role decision or delete creates second side effect.  
**Reference:** NFR-REL-005; NFR-SCAL-002; NFR-DATA-001; FR-022–025, FR-055, FR-108.  
**Suggested verification (not yet executed):** Replay same request after timeout and concurrently; verify one canonical outcome.  
**Risk owner:** TBD during B1; **implementation state:** not assessed; **risk tracking state:** OPEN (planning).

### R08 — Notification delivery failures (Medium)

**Approved mitigation:** Reliable asynchronous delivery and retries.  
**Possible failure signal (proposed):** Lost push or mail blocks approved business decision, or inbox history disappears.  
**Reference:** NFR-OPS-003; FR-080–089, FR-152–154.  
**Suggested verification (not yet executed):** Simulate push outage; verify durable inbox and safe later retries.  
**Risk owner:** TBD during B1; **implementation state:** not assessed; **risk tracking state:** OPEN (planning).

### R09 — Laptop performance limitations (Medium)

**Approved mitigation:** Resource monitoring and gradual load testing.  
**Possible failure signal (proposed):** Local MySQL/API/R2 network saturation; poor responsiveness, failed upload retries or out-of-memory.  
**Reference:** NFR-SCAL-001–003; NFR-OPS-002; NFR-PERF-002.  
**Suggested verification (not yet executed):** Pilot with ~10 and grow; observe CPU/RAM, I/O, p95, failures; no artificial user cap.  
**Risk owner:** TBD during B1; **implementation state:** not assessed; **risk tracking state:** OPEN (planning).

### R10 — Accidental data loss (High)

**Approved mitigation:** Database backups, recovery procedures and retention controls.  
**Possible failure signal (proposed):** Unsafe delete, absent restore backup, premature Trash expiry or incomplete disaster recovery.  
**Reference:** NFR-DATA-003–004; FR-072–077, FR-152, FR-155.  
**Suggested verification (not yet executed):** Backup/restore drill; 30-day Trash, 90-day inbox, 365-day audit expiry validation.  
**Risk owner:** TBD during B1; **implementation state:** not assessed; **risk tracking state:** OPEN (planning).

## 4. Priority and implementation relationship

| Severity | Risk IDs | Recommended gate / reason (candidate, not formal approval) |
|---|---|---|
| **Critical** | R01, R02, R03, R06 | Require negative authorization tests, real concurrent membership tests, safe upload-publication gating and auth/session abuse-case tests before those features are accepted. |
| **High** | R04, R05, R07, R10 | Require verified recovery and duplicate-safe operations; provide reliable backup/recovery procedures before exposing real user data. |
| **Medium** | R08, R09 | Require usable in-app history under push failure and observability of local pilot resource limits. |

### Mapping to future work (not a revised milestone approval)

- **B1**: choose modular boundaries, dependency contracts, transaction model, retry/reconciliation architecture and risk-accountability approach.
- **B2**: schema constraints, migrations, uniqueness, audit/outbox/recovery data design and storage object references.
- **B3**: role/resource authorization, verified provider linking, session trust, idempotent HTTP API decisions and error contracts.
- **B6**: test channel capacity/owner/admin transitions under real concurrency.
- **B7/B8**: validate multipart R2 uploads, safety checks, READY gating, Trash/restore/delete and failure reconciliation.
- **B9+ and pilot verification**: notification resilience, recovery drills, observability and gradually increasing realistic traffic.

These associations guide review; exact stage sequencing is not mandated by this risk register.

## 5. Risk acceptance evidence checklist

The team should eventually attach demonstrable evidence, not just design descriptions:

| Evidence | Addresses | Recording format (proposed) |
|---|---|---|
| Backend permission and stale-session test results | R01, R06 | Automated security test report |
| True parallel join/transfer/last-Admin tests | R02, R07 | Integration-test logs and DB assertions |
| Unsafe/incomplete-file rejection and finalization tests | R03 | Safety-verification test log |
| R2/MySQL failure injection and reconciler results | R04, R05 | Recovery drill transcript |
| Push outage test while inbox remains durable | R08 | Test note/screenshots + DB state |
| CPU/RAM/API latency during laptop pilot | R09 | Pilot metrics and workload description |
| Database backup restoration plus retention expiry tests | R10 | Backup/restore runbook and executed results |

**No test is represented here as executed or passing.**

## 6. Open items for next review

- [ ] Assign each risk an accountable implementation owner (role/team, not necessarily an individual).
- [ ] Select technical controls that preserve the approved mitigation directions; record alternatives in B1 decisions.
- [ ] Add likelihood/exposure ratings **only if** the project elects to adopt a formal scoring scheme.
- [ ] Add acceptance evidence and record residual risks after relevant implementation/tests.
- [ ] Confirm backup retention and recovery targets before production; NFR-DATA-003/004 are approved objectives, not already-measured results.
- [ ] Confirm B0.6 coverage gaps are tracked and not silently hidden by a “B0 closed” label.

## 7. Sources

1. **Approved B0.5 discussion, 19 November 2026** — R01–R10 severity and approved mitigation directions (source of truth for this register).
2. `UniSpace_Non_Functional_Requirements_v1_0(1).pdf` — 42 approved NFRs, notably NFR-SEC, NFR-REL, NFR-DATA, NFR-SCAL, NFR-OPS.
3. `UniSpace_Functional_Requirements_v1_5(2).pdf` — approved FR-001–137.
4. `UniSpace_Functional_Requirements_Addendum_v1_0(1).pdf` — approved FR-138–167.
5. `UniSpace_Product_Decisions_and_MVP_Scope_v1_0_REVIEW(2).pdf` — approved Stage 3.5 decisions in editorial review-draft consolidation.
6. `UniSpace_Backend_Engineering_Starter_Pack_v0_1(1).pdf` — technical **draft only**; not a source of approved severity or risk acceptance.
