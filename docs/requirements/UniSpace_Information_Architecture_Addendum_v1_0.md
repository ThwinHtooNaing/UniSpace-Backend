# UniSpace Information Architecture Addendum v1 0

> **Source:** `UniSpace_Information_Architecture_Addendum_v1_0(2).pdf`  
> **Original pages:** 5  
> **Document classification:** requirements  
> **Conversion:** text-only Markdown transcription; original PDF remains the visual/layout record.  
> **Status note:** Preserve original PDF approval/status wording; later addenda govern newer decisions.

---


<!-- Source PDF page 01 -->

INFORMATION ARCHITECTURE ADDENDUM V1.0

**UNISPACE / CONTROLLED INFORMATION ARCHITECTURE**

## Information Architecture

## Addendum v1.0

Approved navigation and screen-responsibility updates Extension to Information Architecture v1.0

4 + 1 | 30 | 150

New approved functional Bottom tabs: 4 shared + Admin | MVP functional requirements requirements

**DOCUMENT STATUS — APPROVED IA UPDATES**

Records IA changes accepted through the UniSpace Stage 3 and Stage 3.5 conversations, including the approved FR-138–167 addendum. Original IA v1.0 is retained as the baseline.

Scope: one Flutter mobile app; four system roles, no SUPER_ADMIN; Channel Owner is a relationship, not a system role. The backend remains authoritative for every protected operation.

Note: the original IA PDF retains its historical wording and original screen identifiers. This addendum specifies approved changes; it does not assert that previously counted 37 screen responsibilities remain exhaustive.

UNISPACE / CONTROLLED IA ADDENDUM / 20 NOVEMBER 2026


<!-- Source PDF page 02 -->

## 01 Purpose and navigation controls

This addendum resolves later-approved navigation, permissions, and screen responsibilities after the original IA v1.0 was prepared. Where it differs from the original navigation draft, apply this controlled addendum while preserving earlier unaffected IA decisions.

### Primary navigation

| Role / context | Bottom navigation | AppBar |
| --- | --- | --- |
| STUDENT / Channel Owner | Home · Channels · Resources · Profile | Notifications bell |
| SUB_MANAGEMENT | Home · Channels · Resources · Profile | Notifications bell |
| MANAGEMENT | Home · Channels · Resources · Profile | Notifications bell |
| ADMIN | Home · Channels · Resources · Admin · Profile | Notifications bell |

- Admin Dashboard is the root of the Admin bottom tab; it does not require Back navigation to reach.

- Users & Roles, Role Requests, Channel Administration, Institutional Settings and other Admin destinations open as nested secondary pages with Back to their prior Admin context.

- Notifications open from an AppBar bell, not a separate bottom tab. Permission checks apply when following notification deep links.

- Screen visibility is never authorization: the Spring Boot backend independently enforces roles, ownership and channel membership.

### Profile navigation — final approved hierarchy

**PROFILE (ROOT)**

Personal Details → Role Promotion → Display Mode → Settings → Sign Out

- Display Mode is directly on Profile and appears above Settings: Follow System (default), Light and Dark.

- Settings contains Themes, Accessibility, Notifications, Connected Accounts, Security and About UniSpace.

- Sign Out is a direct Profile action below Settings, not nested inside it.

## 02 Changed IA responsibilities

The following mappings extend existing IA route responsibilities. Detailed route names, identifiers and visual page compositions may be finalized in Stage 4 without changing the approved authorization rules.

| IA area | Approved extension | Related FRs / release |
| --- | --- | --- |
| CH-05 / CH-06 / CH-08 | Owner-initiated transfer before leave/switch; first approved entrant to empty channel becomes owner; former-member permissions end. | FR-138, 139, 142, 144 · MVP |


<!-- Source PDF page 03 -->

| IA area | Approved extension | Related FRs / release |
| --- | --- | --- |
| ADM-05 / ADM-06 | Shared pending request queue for empty channels; channel deletion eligibility includes Trash; cancel pending requests; Admin exceptional owner-removal flow. | FR-140, 141, 154, 166 · MVP |
| New Admin settings subroute | Institutional Settings: Admin configures student-ID validation patterns and permitted university-email domains. | FR-157 · MVP |
| MED-01 | Up to 3 concurrent uploads; visible queue/progress; persisted resume/retry state; size/type checks and numbered collision-safe names. | FR-145–149 · MVP |
| MED-02 | Paused-by-default video, per-user position memory, manual landscape fullscreen; native download action. | FR-159–161, 164–165 · MVP |
| MED-03 / MED-04 | Basic PDF + supported PPT preview/fallback; native file export. Enhanced PDF controls deferred. | FR-162, 164–165 · MVP; FR-163 · Later |
| NOT-01 | In-app 90-day inbox, read/mark-all-read/delete; foreground badge and background push policy. | FR-152–154 · MVP |
| PRO-03 Settings | Connected Accounts includes linked Google/Apple sign-in identities as a distinct section from Google Drive authorization. | FR-167 · MVP; FR-151 · Later |
| RES-01..05 | Personal vs Shared Drive resource references; one Google Drive account authorization; external/read-only links. | FR-150–151 · Later |
| ADM-07 / ADM-08 | Advanced health/statistics and audit-log browsing UI postponed; audit EVENT RECORDING remains MVP. | FR-105–107 · Later; FR-108/155 · MVP |

## 03 Updated navigation hierarchy

The hierarchy below is conceptual navigation, not an implementation file tree. Role-gated and post-MVP areas remain visible here to preserve the overall information architecture.

Unauthenticated: Splash/session router → Google/Apple sign-in → complete profile + Student ID → university email OTP → Home; new-device verification when required. Shared main tabs: Home / Channels / Resources / Profile. Admin users also see the Admin tab.

### Channels and media

- Channels → Discover / Preview / Create / Request status → Channel workspace → File library / Members / Owner approvals / Leave & transfer

- Channel media → Upload queue / File details / Video player / PDF-PPT viewer / Trash / Export to Files or Downloads

### Profile

- Personal Details; Role Promotion request/status; Display Mode; Settings; Sign Out

- Settings → Themes / Accessibility / Notifications (push categories) / Connected Accounts (provider linking) / Security / About


<!-- Source PDF page 04 -->

- Connected Accounts → Linked sign-in providers (MVP) and Google Drive authorization management (post-MVP). Distinct credentials and access scopes.

### Admin tab

- Admin Dashboard (root) → Users & Roles / Role Requests / Pending Empty-Channel Requests / Channel Administration / Institutional Settings

- Channel Administration → Rename / eligible deletion / exceptional owner removal with successor selection

- Advanced Monitoring & Audit Explorer → Post-MVP UI; audit recording remains active for MVP

### Resources — release gating

- Resources → My Resources / Shared Resources / Connected Google Drive / Read-only Drive file & folder viewer (post-MVP). Release dependency: Resources remains in the approved overall tab architecture, but all FR-090–100 and addendum FR-150–151 are post-MVP. The MVP presentation of this tab (for example an unavailable state) must be settled in Stage 4; do not implement working Drive features early or silently change approved navigation.

## 04 Permission-sensitive flows

| Flow / actor | Required experience | Server-enforced rule |
| --- | --- | --- |
| Owner leaves / switches | Request successor if channel has remaining members; confirm departure. | Atomic owner reassignment and membership update. |
| Admin removes owner | Show active successor choice where needed; confirm exceptional removal. | ADMIN only; narrowly scoped owner removal; audit + notify. |
| Delete channel | Show 0 active members / 0 active media / 0 Trash files; warn pending request count. | Revalidate eligibility; cancel outstanding requests atomically. |
| Staff promotion | Show blocking membership state and leave/transfer actions. | STUDENT cannot become staff until membership resolved. |
| Provider link | Require active session and additional provider proof; show already-linked/conflict states. | Provider identity unique to one UniSpace user; no email-only auto merge. |
| Export/download | Show authorized file actions, OS destination picker/Downloads integration as appropriate. | Authorization before export; exported native copies cannot be remotely revoked. |
| Retry upload / approve | Show retry or pending status without duplicate success. | Idempotency or equivalent state protection for critical operations. |

**NARROW ADMIN PERMISSION**

FR-166 adds only exceptional removal of a Channel Owner. It must not be interpreted as a general permission for Admins to remove any channel member unless a separate requirement is approved.


<!-- Source PDF page 05 -->

## 05 MVP and post-MVP screen coverage

| MVP: included | Post-MVP: deferred |
| --- | --- |
| Provider authentication / onboarding / provider linking | Google Drive saved resource library and account connection |
| Channels, owner approvals, transfers, empty-channel Admin approvals | My Resources / Shared Resources with external Drive access |
| Media upload queue, core viewers, Trash, native exports | Enhanced PDF reader: search, page jump, reading memory |
| Notification inbox and push-category preferences | Advanced Admin operational dashboards |
| Core Admin users, roles, channels, institutional settings | Admin audit search/browse interface (record events in MVP) |

The original IA screen inventory includes some destinations now release-gated to post-MVP. This addendum does not delete those responsibilities from the final product.

### Interface states to detail in Stage 4

Every important surface should specify at least: default, loading, empty, success, error, offline, access-denied, interrupted/retry and stale-data states where relevant. Detailed interactive specifications and accessible focus/announcements remain Stage 4–5 work.

## 06 Verification and change control

- Map each FR-138–167 to its affected user flow, route/screen, backend authorization rule, endpoint contract and test case.

- Do not treat AppBar visibility, bottom tabs, client-side role checks or hidden buttons as access control.

- Finalize route IDs, component details and UX states in Stage 4; add new functionality only via controlled requirements updates.

- Apply NFR v1.0 quality objectives to each affected surface, especially accessibility, error recovery, security and network interruption handling.

- Keep original IA v1.0 and FR v1.5 documents intact for historical traceability. Document lineage: IA v1.0 (original) → Stage 3 approved navigation revisions → Stage 3.5 approved product decisions → FR Addendum v1.0 (FR-138–167) → this Controlled IA Addendum v1.0.
