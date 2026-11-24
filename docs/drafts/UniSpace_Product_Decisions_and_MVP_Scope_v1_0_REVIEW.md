# UniSpace Product Decisions and MVP Scope v1 0 REVIEW

> **Source:** `UniSpace_Product_Decisions_and_MVP_Scope_v1_0_REVIEW(3).pdf`  
> **Original pages:** 9  
> **Document classification:** drafts  
> **Conversion:** text-only Markdown transcription; original PDF remains the visual/layout record.  
> **Status note:** Technical design draft: not an approved API or database contract.

---


<!-- Source PDF page 01 -->

**UNISPACE | PRODUCT DECISIONS & MVP SCOPE**

UNISPACE / CONTROLLED PRODUCT PLANNING

## Product Decisions

## & MVP Scope

Stage 3.5 | Consolidated Review Draft v1.0

Approved decisions: 30 product policies + 3 MVP planning decisions

Existing baseline: 137 FRs / 67 approved user flows

Release split: 123 baseline FRs in MVP; 14 deferred

Prepared: 20 November 2026

REVIEW DRAFT - NOT A SILENT REVISION OF FR v1.5

The underlying decisions were approved in conversation. This document assembles them for careful review and identifies where separately numbered requirements or IA amendments must be formally accepted.

Source documents: UniSpace Functional Requirements v1.5 (17 Nov 2026); approved Information Architecture v1.0; User Flows & User Journeys v1.0.


<!-- Source PDF page 02 -->

## 1. Approval status and governing rules

- Already approved: Stage 3.5 Decisions 01-08, 9A-9V (30); Balanced MVP (10A); baseline FR split (10B); initial MVP release gates (10C).

- Not yet approved: This exact consolidated wording, new FR numbering and any alteration to previously approved FR v1.5 or IA v1.0.

- Scope safeguard: Deferred requirements remain valid future-release requirements, not deletions. 123+14 counts only FR-001 through FR-137; new approved behaviors proposed for an addendum are not yet assigned FR IDs.

- Sequencing: Review this consolidation, approve controlled addenda, and then begin Stage 3.6 NFR specification (approx. 42 proposed, not individually approved).

### Release structure

- MVP core: Provider sign-in and verified onboarding; roles; channels/membership; video/PDF/PPT upload and core viewing; file management and Trash; inbox and push; offline exports to native files; core Admin actions and audit capture.

- Post-MVP: Google Drive resource integration (FR-090-100), in-app system monitoring/statistics and audit-log browsing (FR-105-107), enhanced PDF reader (Decision 9S), optional seasonal themes (not yet a baseline FR).

- All releases: Security and authorization, accessibility, data integrity, useful error handling and continuous testing are baseline quality obligations.

## 2. Baseline FR allocation (137 total)

| Functional area | FR references | MVP | Later |
| --- | --- | --- | --- |
| Authentication, registration & roles | FR-001-013; FR-113-137 | 38 | 0 |
| Channels & membership | FR-014-035; FR-110-111 | 24 | 0 |
| Central uploads | FR-036-055 | 20 | 0 |
| Channel media viewing | FR-056-066 | 11 | 0 |
| File management & Trash | FR-067-079 | 13 | 0 |
| Notifications | FR-080-089 | 10 | 0 |
| Google Drive teaching resources | FR-090-100 | 0 | 11 |
| Administration & audit | FR-101-104, 108-109, 112 / FR-105-107 | 7 | 3 |
| TOTAL | FR-001 to FR-137 | 123 | 14 |

Exact post-MVP baseline IDs: FR-090-100 (11 Google Drive requirements) and FR-105-107 (3 Admin visibility/monitoring requirements). FR-108 audit-event recording stays in MVP.

The above is a 1-to-1 classification of the approved baseline; any eventual new FR addendum items will have additional identifiers and be traced separately.


<!-- Source PDF page 03 -->

## 3. Product decision register: all 30 approvals

The following policies were approved one at a time in Stage 3.5. The suffixes 9A-9V match the conversation decision IDs. The release tags apply the approved Balanced MVP scope; they do not introduce a new FR number.

### 3.1 Core rules: Decisions 01-08

Decision 01 Owner voluntarily leaves or switches [MVP] If other members remain, the Channel Owner selects an active-member successor before departure. Transfer ownership and remove/switch the departing owner atomically. Sole owner may leave; channel remains 0/6.

Decision 02 First entrant to an empty channel [MVP] Admin approves requests for a 0/6 channel. Warn requesters they may become owner. The first successfully admitted student becomes owner atomically; later joins follow owner approval.

Decision 03 Channel deletion and Trash [MVP] Admin may delete a channel only with zero active members, zero active media and zero trashed media. Deletion must not bypass the up-to-30-day Trash recovery policy.

Decision 04 Pending requests on channel deletion [MVP] Warn Admin with pending join/switch count. Confirmed eligible deletion cancels all pending requests in the same transaction; notify requesters and audit the action.

Decision 05 Restore by former members [MVP] Former channel members cannot access or restore their old uploads solely because they are uploader. Rejoining restores applicable rights; authorized Management/Admin may restore.

Decision 06 Restore destination after a move [MVP] Restore trashed media to the channel it belonged to immediately before entering Trash. Persist prior channel ID; require valid authorization/destination, never choose a fallback silently.

Decision 07 Student-to-staff promotion [MVP] Before promotion to SUB_MANAGEMENT, MANAGEMENT or ADMIN, resolve student channel membership; owner must transfer first where necessary. Staff-to-staff changes do not require leaving; demotion to STUDENT never auto-rejoins.

Decision 08 Document upload size caps [MVP] Per-file: video 1 GB (existing), PDF 100 MB, PPT/PPTX 200 MB. Flutter prevalidation; authoritative Spring Boot validation; no channel storage quota.

### 3.2 Structural and transfer rules: Decisions 9A-9H

Decision 9A Channel folder depth [MVP] Flat Channel -> files structure for MVP. No user-created subfolders; search and sorting are used for organization. Future nested folders are not approved functionality.

Decision 9B Duplicate names during upload [MVP] On an in-channel filename collision, append a numeric suffix such as (1), (2), retaining extension and independent file ID. No overwriting; server-side concurrency safety.


<!-- Source PDF page 04 -->

Decision 9C Upload recovery after app termination [MVP] Persist upload-session metadata and completed multipart parts; on app reopen users can resume if original local file and session remain valid. Expired sessions explain restart. No closed-app background transfer guarantee.

Decision 9D Parallel upload limit [MVP] At most three active uploads per device. Excess files queue; start next while app is active when a slot frees. Cancel individually; interrupted items retain recoverability.

Decision 9E Name collisions on move or rename [MVP] Apply the same numeric-suffix rule to file move and manual rename collisions. Preserve unique ID, uploader and extension; do not overwrite existing content.

Decision 9F Personal versus shared Drive resources [Post-MVP] Resources: My Resources (personal bookmarks) and Shared Resources (published by authorized MANAGEMENT/ADMIN, shown to eligible users). Drive access remains subject to Google permissions and stores references only.

Decision 9G Connected Drive account count [Post-MVP] One connected Google Drive account per UniSpace user; connect, disconnect or replace. Remove stored authorization on disconnect, attempt revocation where supported; bookmarks remain but may be inaccessible.

Decision 9H In-app inbox retention [MVP] Retain notifications 90 days. Individual read, mark-all-read and individual delete controls. Inbox deletions never reverse events or remove audit records.

### 3.3 Notifications, identity and media: Decisions 9I-9P

Decision 9I Push notification preferences [MVP] Per-user category preferences for optional push (channel requests, role updates, file activities, announcements where available). OS permission overrides; in-app inbox remains; security verification stays on required channel.

Decision 9J Admin alerts for empty-channel joins [MVP] All active Admins receive in-app notifications; optional push respects preferences. Requests appear in Admin Dashboard pending queue; only one authorized decision succeeds and is audited.

Decision 9K Audit log retention [MVP] Retain important audit events for 365 days, subject to applicable institutional/legal obligations. Entries immutable to app users; never log passwords, OTPs or tokens. Audit capture remains even if audit viewer is deferred.

Decision 9L Email OTP policy [MVP] OTP valid 10 minutes; resend cooldown 60 seconds; five incorrect attempts invalidate code; newer code invalidates previous; single-use; server-side abuse rate limits.

Decision 9M Institutional identity rules [MVP] Authorized Admins can configure student-ID format and permitted university email domains. Student ID/email uniqueness enforced on backend; changes do not auto-invalidate existing accounts.


<!-- Source PDF page 05 -->

Decision 9N Trusted device lifetime [MVP] No fixed device-trust expiration. Trust persists until sign-out, explicit revocation or a security event. Session/access-token validity is separate; valid trust never overrides expired credentials.

Decision 9O Playback-position memory [MVP] Save position separately per user and video. Reopen partially viewed videos at saved point; near-complete videos start from beginning; current file/channel authorization always required.

Decision 9P Video autoplay [MVP] Player opens paused (at remembered point or 00:00); user presses Play. Opening another video never auto-plays it.

### 3.4 Media, offline and Admin recovery: Decisions 9Q-9V

Decision 9Q Video rotation and fullscreen [MVP] Open videos in portrait. Fullscreen user action enters landscape where supported; exiting restores prior orientation without restarting playback, respecting platform restrictions.

Decision 9R PowerPoint preview and fallback [MVP] Attempt read-only in-app PPT/PPTX slide preview with slide navigation when technically supported; otherwise offer open-external/download fallback. Retain original source and enforce access rights.

Decision 9S Enhanced PDF viewer [Post-MVP] Scroll, zoom, searchable-text search, page jump, current/total page count and last-viewed page memory per user/document. Scanned PDFs may not support text search; original remains unchanged.

Decision 9T Full offline media access [MVP] Enable deliberate download of authorized videos and documents for use without internet. Backend actions still require connection; upload recovery follows established policies.

Decision 9U Native OS download destination [MVP] Save exports to normal user-accessible device storage (Android shared Downloads; iOS Files/user-selected destination), not an app-private encrypted library. Exported files may survive sign-out/revocation/uninstall and cannot be remotely revoked; check permissions before export.

Decision 9V Exceptional Admin removal of owner [MVP] Admin may choose an active-member successor and atomically transfer ownership while forcibly removing a Channel Owner; if sole member, remove and leave 0/6. Notify affected user; retain files and audit. Requires explicit FR permission addendum.

## 4. MVP acceptance gates - approved high-level criteria

A gate is a release condition, not evidence that testing has already passed. Detailed numerical thresholds and test-case IDs will be agreed in Stages 3.6, 4, 5 and 9-10.

01 Authentication and onboarding Google/Apple onboarding, institutional ID/email validation, OTP, trusted-device returns, recovery and sign-out pass authorized and error scenarios.


<!-- Source PDF page 06 -->

02 Channels and membership Creation, one-membership maximum, six-member capacity, requests, switching, owner transfer, empty channel reopening and Admin deletion are enforced transactionally.

03 File uploads Supported video/PDF/PPT uploads respect type, safety and size rules; progress, queue, cancellation, app-termination resume and incomplete-publication guard work.

04 Media playback and previews Authorized videos play/pause/seek/fullscreen; MVP PDF and PowerPoint previews or fallback work with loading, error and access-denied states.

05 File management and Trash Authorized rename, move, delete, restore and permanent delete follow uploader rights, last-channel restore, trash retention and deletion constraints.

06 Offline exports After permission checks, students can save videos/documents to native device Files/Downloads and independently open them without a connection.

07 Notifications In-app inbox, read/unread actions, optional category-controlled push, empty-channel Admin queue and retention behavior work; failures never expose unauthorized links.

08 Core Admin tools Authorized Admin can review requests and users, assign/revoke roles, administer eligible channels and preserve last-Admin protection; actions audited.

09 Security and privacy Every protected API enforces server-side authorization; verification/secrets/session storage and native-download disclosures pass agreed security tests.

10 Accessibility and UX MVP screens meet accessibility criteria to be finalized in Stage 5 and pass the critical student/staff journeys in usability testing.

11 Release quality Every MVP baseline FR mapped to >=1 verification test; critical E2E journeys work on Android/iOS; zero unresolved release-blocking defects and Stage 3.6 NFR thresholds met.

### Cross-cutting release rule

- Every one of 123 baseline MVP FRs must have at least one linked verification test (and approved addendum items need their own coverage).

- Critical journeys must pass on Android and iOS. Security, authorization, data integrity and relevant accessibility checks are release blockers.

- Performance, resilience and availability must meet future measurable NFR targets; there must be no unresolved release-blocking defects.


<!-- Source PDF page 07 -->

## 5. Controlled requirements-addendum register

These are proposed change-control packages, not yet numbered or inserted into FR v1.5. Some merely operationalize existing FRs; others add an explicit product capability and need additional authorization/screen requirements.

| CR-01 Channel ownership & Admin exception [MVP] Decisions: Decisions 01, 02, 9V | Related baseline: FR-023, 026-030, 103, 110 New Admin forced-removal permission conflicts with v1.5 access table "Not set"; explicit controlled change required. |
| --- | --- |
| CR-02 Channel deletion & pending-request closure [MVP] Decisions: Decisions 03-04 | Related baseline: FR-033-034, 069-079, 111 Clarify zero media includes Trash; add warning, cancellation and notification transaction. |
| CR-03 Upload rules, queue and names [MVP] Decisions: Decisions 08, 9A-9E | Related baseline: FR-036-055, 067-068 Add document size caps, auto-suffix names, max 3 active uploads/device and post-termination resume behavior. |
| CR-04 Role-transition policy [MVP] Decisions: Decision 07 | Related baseline: FR-010-013, 017-030 Must leave student channel and transfer ownership before student-to-staff promotion. |
| CR-05 Notifications [MVP] Decisions: Decisions 9H-9J | Related baseline: FR-080-089, 110 Add inbox controls/90-day retention, per-category push preferences and empty-channel Admin alerts. |
| CR-06 Security and institutional policy [MVP] Decisions: Decisions 9L-9N | Related baseline: FR-006, 117-130 Add OTP security parameters, Admin-managed identity formats/domains and trust-until-revoked policy. |
| CR-07 Video and document experience [MVP] Decisions: Decisions 9O-9R | Related baseline: FR-060-066 Add playback position, no-autoplay, manual fullscreen and PPT fallback; verify preview feasibility. |
| CR-08 Native downloadable offline copies [MVP] Decisions: Decisions 9T-9U | Related baseline: FR-036-066, 075, 100 New explicit user-download feature. Applies to central UniSpace files; Drive downloads not assumed. Local copies cannot be revoked after export. |
| CR-09 Audit retention [MVP (capture)] Decisions: Decision 9K | Related baseline: FR-107-108 Specify 365-day event retention; recording FR-108 in MVP, admin search/view FR-107 deferred. |


<!-- Source PDF page 08 -->

| CR-10 Enhanced PDF controls [Post-MVP] Decisions: Decision 9S | Related baseline: FR-064 Enhanced search/page jump/reading position deferred, despite approved eventual behavior; MVP retains core PDF preview. |
| --- | --- |
| CR-11 Google Drive resource model [Post-MVP] Decisions: Decisions 9F-9G | Related baseline: FR-090-100 Personal and shared references, one connected Drive account, permission-preserving access. |

## 6. Consistency findings and remaining validation

Explicit permission change FR v1.5 lists Admin member-removal privilege as "Not set". Decision 9V approves a narrowly scoped owner-removal exception; it needs a formally approved amendment, tests and an IA/Admin route update.

Offline-file control Native exports remain readable after UniSpace sign-out or membership revocation. This is approved; ensure clear download disclosure and do not describe these files as remotely revocable.

MVP count semantics 123 / 14 classifies existing FR-001-137 only. Approved new capabilities in this register need addendum IDs and may increase the future numbered FR total; do not claim the MVP is exactly 123 total behaviors.

Identity linking FR v1.5 left connecting both Google and Apple identities to a single account unresolved. This does not invalidate the provider sign-in requirements, but needs a policy before account-linking is implemented.

Administrator-configured validation

Decision 9M introduces an Admin configuration capability not present in the original IA screen inventory; update the IA via controlled change before detailed Admin screen design.

Feasibility and targets Cloudflare R2 multipart uploads, PPT rendering, provider verification, offline export and mobile resource consumption require short technical spikes. Quantitative security/performance/reliability targets belong in Stage 3.6.

Proposed settings vs approved scope Seasonal themes, unapproved multi-account Drive access and any general-announcement publishing function are not created by this decision register. Do not invent new publisher functions from notification category labels.

## 7. Review and next-stage checklist

- APPROVED - 30 previously approved product policies represented.

- APPROVED - Balanced MVP, 123 / 14 classification and high-level acceptance gates recorded.


<!-- Source PDF page 09 -->

- PENDING - Confirm this draft consolidation is accurate and approve its wording as the controlled Stage 3.5 record.

- PENDING - Approve new/additional FR IDs and IA changes through explicit change control, especially Admin owner removal.

- PENDING - Resolve Google + Apple account linking policy before adding dual-provider account-linking behavior.

- PENDING - Create traceability links to User Flow, Screen, Component and Test IDs; avoid unapproved features.

- PENDING - Begin Stage 3.6 NFR specification; define measurable feasibility and quality thresholds.

Next user approval requested: Accept this consolidated Stage 3.5 review draft as faithful to the approvals, or point out corrections. Approving this consolidation is not equivalent to silently modifying the original FR v1.5 PDF.
