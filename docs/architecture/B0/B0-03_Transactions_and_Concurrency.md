# UniSpace Backend — B0-03 Transactions, Concurrency & Recovery Principles

> **Document ID:** B0-03  
> **Version:** v0.1 — planning handoff  
> **Prepared:** 22 November 2026  
> **Status:** **B0.4 principles approved; transaction implementation mechanisms are not approved yet.**  
> **Related:** B0-01, B0-02, and R02/R04/R07/R10 in B0-04

## 1. What B0.4 approved

The user's 19 November 2026 B0.4 approval established these **non-negotiable outcomes**:

1. Protected state must remain valid when multiple requests execute concurrently.
2. Logically inseparable related database changes must succeed or fail atomically.
3. Retrying a request must not create duplicated memberships, uploads, role decisions, file operations or other unintended effects.
4. MySQL ↔ Cloudflare R2 and notification delivery failures need safe, observable reconciliation/retry paths; they are **not** a single atomic database transaction.
5. Exact locking/isolation strategy, schema, event system and failure-handling mechanisms will be designed and tested in B1/B2/B3 and later implementation phases.

**Approval boundary:** The required outcomes are approved. Suggested methods such as `SELECT ... FOR UPDATE`, version columns, unique DB indexes, idempotency tables, transactional outbox, schedules or saga-style compensation remain **technical candidates**, not approved product behavior or a final DB design.

## 2. Authoritative invariants (approved behavior)

| ID | Invariant | Sources | Enforcement objective |
|---|---|---|---|
| INV-01 | A student has at most one active channel membership. | FR-014–035; NFR-DATA-001 | Never complete competing active memberships. |
| INV-02 | Each channel has 0–6 active student members; the displayed count reflects actual approved membership. | FR-014–035; NFR-DATA-001 | No 7th successful admission, including simultaneous approvals. |
| INV-03 | A nonempty channel has an eligible active owner. The first approved member of a 0/6 channel becomes owner. | FR-139 | Owner and member state transition together. |
| INV-04 | Owner departure/switch with remaining members requires an active-member successor; Admin force-removal follows its exception. | FR-138, FR-166 | Transfer and departure/removal never partially persist. |
| INV-05 | An eligible channel can be deleted only when active members = 0, active media = 0, Trash media = 0. Pending membership requests cancel with the deletion. | FR-140, FR-141 | No deletion racing with admission, file operations or Trash restoration. |
| INV-06 | At least one active ADMIN remains; ordinary registration cannot grant ADMIN. | FR-005, FR-012, FR-112 | Two concurrent revocations cannot remove the final functioning Admin. |
| INV-07 | Student-to-staff promotion requires existing student channel membership to be resolved, including owner succession. | FR-144 | No completed staff promotion retaining prohibited student-channel state. |
| INV-08 | Authorization depends on current role, membership, owner/file authority and resource state; leaving invalidates former-member file privileges. | FR-030, FR-078, FR-142; NFR-SEC-002 | No stale client state or cached identifier bypasses backend checks. |
| INV-09 | Uploads never become available until complete and safe; permitted formats/sizes and current authorization remain enforced. | FR-036–055, FR-145; NFR-REL-003 | No rejected, incomplete or unverified READY file. |
| INV-10 | File upload/rename/move collisions receive an unused numbered name, never overwriting another item; original uploader remains immutable. | FR-046, FR-068, FR-147 | Safe uniqueness and attribution under simultaneous operations. |
| INV-11 | Trash prevents normal access; restoration returns to the pre-Trash last channel if authorized/valid and within 30 days. | FR-072–078, FR-143; NFR-DATA-002 | No restore after expiry, no unauthorized or wrong-channel restore. |
| INV-12 | The in-app notification record persists independently of best-effort push; audit events are captured safely and retained as approved. | FR-087–089, FR-108, FR-152, FR-155 | No falsely successful critical operation with missing required durable event state. |
| INV-13 | Upload transfer recovery survives ordinary app termination when valid; max 3 active transfers/device with queued remainder while app active. | FR-148–149; NFR-REL-002 | Recover without publishing partial files or exceeding client limit. |
| INV-14 | MySQL media metadata and private R2 objects must be reconciled safely through failures, including deletion. | NFR-DATA-002; NFR-SEC-005 | Detect/recover orphan metadata and objects. |

*`INV-*` labels are **engineering trace identifiers introduced in this document**; they are not new approved FRs.*

## 3. Critical operation boundaries — behavior and later design work

| Operation | Required all-or-nothing / serialized result | Cross-system follow-up | Tests to define |
|---|---|---|---|
| Approve occupied channel join | One final decision; correct authorized owner; recheck capacity and one-membership state | Inbox/push, audit | Two simultaneous approvals for last seat; stale owner; duplicate decision |
| Approve first entrant to 0/6 | Admit one valid student and set owner consistently | All-Admin inbox request state; applicant notification, audit | Two Admins decide same request; two candidates enter concurrently |
| Leave channel / switch channel | Transfer owner first when required and remove/switch membership consistently; destination capacity remains valid | Notify affected members; audit | Racing switches; owner leaves during another join |
| Exceptional Admin owner removal | Verify special Admin privilege; active successor if applicable; atomic removal+owner change; preserve files | Notify removed owner; audit | Last-member removal; simultaneous owner transfer |
| Delete eligible channel | Recheck zero members/active files/Trash under concurrency; cancel pending requests in same transaction | Notify cancelled requesters; audit | Upload/restore/join races with deletion |
| Staff role promotion | Verify request/current role and resolved membership; protect last Admin; commit one outcome | Role outcome notification; audit | Concurrent promotion/revocation; duplicate approval |
| Assign/revoke ADMIN | Final active Admin count cannot fall to zero | Audit and permission invalidation | Two Admins each try to revoke the other simultaneously |
| Upload initiation/finalization | Valid unique item/session identity; never publish unverified content | R2 transfer/scan/cleanup and outcome notification | Duplicate complete/cancel; truncated file; scanner outage |
| Rename/move | Authorization + destination+unique filename and immutable uploader | Audit where required | Two concurrent same-name moves/renames |
| Move to Trash / restore | State, rights and target channel validated at time of action | Retention cleanup, audit | Restore vs expiry/delete; former member attempts restore |
| Permanent deletion | Prevent future normal access and ensure remote-object deletion is recoverably tracked | Asynchronous R2 removal/reconciliation where needed | DB commit with R2 unavailable; repeated delete |
| Notification creation | In-app record durably available regardless of push provider | Best-effort push | Fail push provider during successful transaction |

**Design caveat:** An operation involving multiple logical modules does not mean each module needs direct access to the other's tables. Implement a coordinated domain/application operation with explicit interfaces and a reviewed persistence/transaction boundary in B1/B2.

## 4. Idempotency contract — approved goal, proposed techniques

**Approved:** A repeated operation must not create extra effects. Treat retry as a separate axis from concurrency: the same user may retry while the original request is processing, after timeout, or after a successful commit whose response was lost.

**B1/B3 candidate controls:** Define a stable client operation identifier or server-issued workflow identifier for mutating requests; persist decision outcomes; constrain uniqueness in MySQL; return the existing result for an equivalent retry; reject conflicting payload reuse; make worker processing re-entrant. These are **proposals** requiring API and schema design.

**Acceptance examples:** Re-send a successful approval, finalization or permanent deletion request; verify at most one membership state change, one file publication, one role decision and no duplicated durable event records. Exact event-deduplication semantics will be decided in B1/B3.

## 5. Database versus Cloudflare R2: consistency boundary

MySQL commits do **not** make R2 operations transactional. B0 approves recoverability, not a specific distributed transaction architecture.

**B1/B7 design candidate state machine (illustrative only):**

```text
PENDING -> UPLOADING -> VERIFYING -> READY
   |           |           |
   +---------> FAILED / CANCELLED (recoverable or terminal as appropriate)
READY -> TRASHED -> RESTORED (READY) OR PENDING_DELETION -> DELETED
```

These are **example states**, not an approved database enum. A user must not gain file access merely because a metadata row exists or a temporary upload finished transferring. Ownership, verified safety, current role/membership and storage availability all matter.

**Failure scenarios requiring explicit handling:**

- DB commit succeeds but R2 upload/cleanup fails → retain sufficient state for investigation and safe retry; never falsely publish, irreversibly delete or leak objects.
- R2 transfer succeeds but DB commit fails → detect/reconcile orphaned remote parts/objects without assigning unauthorized access.
- App terminates during multipart upload → if source/session are valid, resume from recorded progress; if not, show a recovery failure (FR-148).
- File is moved/trashed/deleted while a viewer has a temporary media URL → enforce current permission before issuing new URLs; lifetime and revocation limitations must be addressed in B3/B7.
- Upload safety checking fails or scanner is temporarily unavailable → item remains unavailable rather than READY.

## 6. Notifications, audit and cross-module follow-ups

**Approved behavior:** Notification inbox history is durable (90 days), push is optional/best effort, and important mutations are audited (365 days unless obligations require otherwise). Successful domain operations cannot depend on push service availability.

**Technical candidates:** same-transaction audit writes for required events; a transactional outbox or equivalent durable dispatch record; at-least-once worker delivery with idempotent handlers and monitoring; time-bounded retries and safe dead-letter handling. B1 must decide if/how these fit the initially small laptop pilot. **Do not mark outbox architecture “approved” simply because it appears in a draft starter pack.**

## 7. Verification matrix (to implement later)

| Test ID | Test idea | Expected property | Related NFR |
|---|---|---|---|
| CT-01 | Two simultaneous approvals compete for channel seat #6 | Exactly one succeeds; count remains ≤6 | NFR-DATA-001 |
| CT-02 | Concurrent joining into empty channel | One consistent first owner with accepted membership | NFR-DATA-001 |
| CT-03 | Owner transfer and leave/switch race | No nonempty ownerless channel or orphan owner | NFR-DATA-001 |
| CT-04 | Concurrent Admin demotions | At least one active ADMIN remains | NFR-DATA-001, NFR-SEC-002 |
| CT-05 | Admin deletes a channel while file is restored | Delete blocks or restoration blocks according to serialization | NFR-DATA-001/002 |
| CT-06 | Repeat successful mutation after response timeout | No second effect | NFR-REL-005; NFR-SCAL-002 |
| CT-07 | Upload completes in R2 while MySQL is unavailable | No unsafe READY state; recoverable reconcile path | NFR-DATA-002 |
| CT-08 | Push provider fails during approved membership decision | Decision stands; inbox event preserved; retry safe | NFR-OPS-003 |
| CT-09 | Duplicate file naming under simultaneous upload/move/rename | Distinct names; original items intact | NFR-DATA-001 |
| CT-10 | Trash auto-expiry competes with restore | Exactly one coherent result; no post-expiry restore | NFR-DATA-001/002 |
| CT-11 | Resume multipart after app process termination | Valid session resumes without false publication | NFR-REL-002/003 |
| CT-12 | Backup restore drill | Consistent recovered database/configuration and documented outcomes | NFR-DATA-003/004 |

*`CT-*` identifiers are proposed test-case placeholders for later planning, not existing executed tests.*

## 8. Decisions deferred beyond B0

1. Isolation level, optimistic/pessimistic locking, uniqueness indexes and transaction orchestration (B1/B2).
2. Detailed schema, file storage identities, object lifecycle, cleanup scheduler and migration plan (B2/B7/B8).
3. Idempotency key/result semantics and retry response codes (B3).
4. Email/push worker mechanism, durable notification/audit boundaries and error handling (B1/B3/B9).
5. Signed URL time-to-live, URL renewal, revocation safety, device token storage and session/token model (B3/B7).
6. Load-test budgets, rollback procedures, database backup RPO/RTO and production recovery evidence (later gates).

## 9. Sources

- `UniSpace_Functional_Requirements_v1_5(2).pdf`, FR-001–137.
- `UniSpace_Functional_Requirements_Addendum_v1_0(1).pdf`, FR-138–167, pp. 3–6.
- `UniSpace_Non_Functional_Requirements_v1_0(1).pdf`, especially NFR-DATA-001–004, NFR-REL-002–003, NFR-SEC-002/005–007, NFR-SCAL-002, NFR-OPS-001–003.
- `UniSpace_Product_Decisions_and_MVP_Scope_v1_0_REVIEW(2).pdf`, approved individual Stage 3.5 decisions.
- Approved B0.4 planning conversation, 19 November 2026.
- `UniSpace_Backend_Engineering_Starter_Pack_v0_1(1).pdf` provides **proposed examples**, not approved transaction implementation details.
