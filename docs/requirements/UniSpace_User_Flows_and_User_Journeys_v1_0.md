# UniSpace User Flows and User Journeys v1 0

> **Source:** `UniSpace_User_Flows_and_User_Journeys_v1_0(2).pdf`  
> **Original pages:** 36  
> **Document classification:** requirements  
> **Conversion:** text-only Markdown transcription; original PDF remains the visual/layout record.  
> **Status note:** Preserve original PDF approval/status wording; later addenda govern newer decisions.

---


<!-- Source PDF page 01 -->

**UNISPACE / DESIGN DELIVERY**

## UniSpace

## User Flows &

## User Journeys

Stage 3 consolidated specification | Version 1.0

| 67 APPROVED FLOWS | 9 MODULES | 137 BASELINE FRs |
| --- | --- | --- |
| Detailed primary and exception paths | Authentication to Administration | Functional Requirements v1.5 |

Document status: Consolidation of user-approved Stage 3 journeys, including later approved edits to channel member counts, notifications, Profile placement and Admin navigation. Not a change to Functional Requirements v1.5.

Sources: UniSpace Functional Requirements v1.5 (approved); UniSpace Information Architecture v1.0 (approved); and the subsequent approved Stage 3 conversations.

### Scope protection

Approved user-flow behavior is distinguished from optional UX proposals and unresolved product policies. Where the underlying v1.5 Functional Requirements leave a decision open, the flow document retains that open decision rather than creating an implementation rule.

Designed for incremental Flutter + Spring Boot implementation, acceptance testing, and separate focused feature-development sessions.

UniSpace | User flows & user journeys | Approved baseline | 17 November 2026


<!-- Source PDF page 02 -->

## 01 Document Control & Approval Record

This document preserves all nine approved modules and the exact 67 flow identifiers. It reflects the final conversation decisions, not merely the first draft shown for each module.

| Approved module | Flows | Status |
| --- | --- | --- |
| 01 Authentication & Registration | 6 | Approved |
| 02 Channels & Membership | 7 | Approved |
| 03 File Uploading & Management | 7 | Approved |
| 04 Video Playback & Document Viewing | 6 | Approved |
| 05 Trash & Recovery | 6 | Approved |
| 06 Google Drive Resources | 8 | Approved |
| 07 Notifications | 8 | Approved |
| 08 Profile & Settings | 10 | Approved |
| 09 Administration | 9 | Approved |

### Post-draft changes incorporated

| Area | Approved adjustment / safeguard |
| --- | --- |
| Channel labels | Show current active student count as n/6 (such as 3/6) on discovery cards, previews and listings; update after joins/leaves/removals. |
| Notifications | AppBar bell (not a bottom tab). Always store in-app event; foreground updates inbox/badge without redundant banner; background/closed attempts push when permitted; push-disabled still gets in-app history. |
| Profile placement | Display Mode (Follow System / Light / Dark) directly on Profile ABOVE Settings; Sign Out directly on Profile BELOW Settings. Themes and Accessibility stay INSIDE Settings. |
| Admin destination | Admin Dashboard is ROOT of the Admin-only bottom-navigation tab. Admin subpages are pushed routes with Back to dashboard. |
| Theme scope | Seasonal themes and advanced Settings preferences remain proposals/future features, not silently added to 137 approved FRs. |

### Document conventions

Main path: the standard successful journey. Exceptions: validation, authorization, interruption and alternate paths. Exit: outcome visible to the actor. FR refs: relevant approved requirements, or a clearly identified IA/future design decision.

The label UNRESOLVED means the corresponding behavior is intentionally not approved. A flow can be approved without settling a related policy.


<!-- Source PDF page 03 -->

## 02 Navigation & Roles

One Flutter app for all users. Permissions vary by verified system role and channel ownership; there is no separate Channel Owner role and no Super Admin.

| Account context | Primary bottom navigation | AppBar |
| --- | --- | --- |
| Student / Channel Owner | Home · Channels · Resources · Profile | Notification bell |
| Sub-Management | Home · Channels · Resources · Profile | Notification bell |
| Management | Home · Channels · Resources · Profile | Notification bell |
| Admin | Home · Channels · Resources · Admin · Profile | Notification bell |

Admin: The Admin tab opens Admin Dashboard as its root page, not as an additional screen requiring Back. Its child pages use a nested route stack. Notifications: AppBar bell opens the inbox as a secondary route and returns to the previous authorized view.

**Route Overview**

Launch | Session check | Sign-In or Onboarding | Home / Primary tabs

### Authoritative access rule

Flutter exposes only relevant actions, but Spring Boot must enforce registration ACTIVE state, staff role, current channel membership, original uploader ownership and current resource access on every protected operation. A visible button or notification link never creates authorization.

Channel folder model: UniSpace -> Channel -> Media files, with human-readable names in the app and centralized storage backed by metadata. Google Drive Resources are external and read-only.


<!-- Source PDF page 04 -->

## 03 Module 1: Authentication & Registration

Provider authentication, provisional accounts, student onboarding, university-email verification, trusted devices, registration recovery, and session exit.

**Route Overview**

Splash/session router | Sign-In or Onboarding | Home; Profile | Sign Out | Sign-In or Device Verification

### Approved flow register

| Flow ID | Journey |
| --- | --- |
| UF-AUTH-01 | First-time registration |
| UF-AUTH-02 | Returning user on trusted device |
| UF-AUTH-03 | Sign-in on new or untrusted device |
| UF-AUTH-04 | Resume interrupted registration |
| UF-AUTH-05 | Restart incomplete registration after reinstall |
| UF-AUTH-06 | Sign-out, session expiry & access restriction |

### UF-AUTH-01 First-time registration

Actor: New user Entry: Splash/session router -> Welcome / Sign-In

**Main Journey**

1. Open UniSpace and choose Google or Apple; complete provider authentication.

2. Create or retrieve a provisional STUDENT account. Prefill first and last name when provided, with both fields editable.

3. Enter required Student ID; validate format and uniqueness, preserving unrelated valid inputs when validation fails.

4. Enter organizational university email; validate permitted domain and request a verification code.

5. Submit valid code; activate the account only after profile and email checks pass.

6. Establish a trusted session and enter the Home destination.

**Alternates / Guardrails**

- Provider cancels/fails -> remain on Sign-In with retry.

- Invalid or duplicate Student ID -> field error; preserve name and other entries.

- Invalid email, wrong or expired code -> correction, retry or resend under security limits.

- Until ACTIVE, protected channel, file, and Admin routes remain unavailable.

Exit: ACTIVE registered account; Home reached.

Trace: FR-002–004, FR-113–124, FR-132–134


<!-- Source PDF page 05 -->

### UF-AUTH-02 Returning user on trusted device

Actor: Registered ACTIVE user Entry: Launch -> AUTH-01 Session routing

**Main Journey**

1. Open the app on a device holding an existing trusted session.

2. Restore sign-in state and check trusted-session validity and account status.

3. If valid and account ACTIVE, navigate to Home without provider or student onboarding screens.

**Alternates / Guardrails**

- Expired, revoked or invalid session -> provider reauthentication.

- DISABLED account -> restrict protected application access.

- Ordinary app restart, device restart or OS process removal does not itself require sign-in while session remains valid.

Exit: Home reached with current role-based navigation.

Trace: FR-006, FR-125–126, FR-130, FR-137

### UF-AUTH-03 Sign-in on new or untrusted device

Actor: Existing ACTIVE user Entry: Sign-In -> provider authentication -> device verification

**Main Journey**

1. Authenticate again with Google or Apple on the new or untrusted device.

2. Identify the already ACTIVE UniSpace account without repeating student registration.

3. Send a one-time code to the university email previously verified for that account.

4. Validate the entered code; establish persistent trusted-device session on success.

5. Open Home with user permissions refreshed.

**Alternates / Guardrails**

- Wrong/expired code -> retry or resend, subject to security limits.

- Cannot access verified mailbox -> do not bypass additional verification.

- Provider identity must match the correct existing account; do not create an unnecessary second ACTIVE registration.

Exit: New trusted-device session; Home reached.

Trace: FR-127–130, FR-137

### UF-AUTH-04 Resume interrupted registration

Actor: Provisional user Entry: AUTH-01 routing -> last appropriate onboarding step

**Main Journey**

1. Begin student profile or university-email onboarding.

2. Leave the app temporarily (Back, background, process termination or ordinary restart).

3. Reopen app and restore authenticated provisional account state and preserved registration progress.

4. Resume the last appropriate incomplete step, correct any invalid fields and complete registration.

**Alternates / Guardrails**

- Duplicate Student ID or other form error -> preserve unrelated valid entries.

- Expired email code -> resend without redoing profile.

- No protected application access before activation.

Exit: User resumes onboarding instead of starting over.

Trace: FR-004, FR-006, FR-124, FR-132–134


<!-- Source PDF page 06 -->

### UF-AUTH-05 Restart incomplete registration after reinstall

Actor: Provisional user Entry: Fresh installation -> Sign-In -> Profile onboarding

**Main Journey**

1. Uninstall while registration remains incomplete, then reinstall UniSpace.

2. Require Google/Apple authentication again.

3. Restart onboarding from the first registration step.

4. Safely reuse or reconcile existing provisional account data to prevent duplicate records.

5. Proceed to university email verification and activation.

**Alternates / Guardrails**

- Reinstallation may retain platform credentials (especially iOS Keychain); implementation must still meet the fresh-authentication policy.

- A previously ACTIVE account must never be forced to complete student onboarding again.

Exit: Clean registration restart with no duplicate provisional accounts.

Trace: FR-135–137

### UF-AUTH-06 Sign-out, session expiry & access restriction

Actor: Any user / system Entry: Profile -> Sign Out; or access check from any protected route

**Main Journey**

1. User selects Sign Out, or a protected operation detects invalid session/account state.

2. End the current authenticated session on explicit sign-out; clear protected navigation state.

3. Redirect to Welcome / Sign-In when reauthentication is required.

4. After new authentication, apply onboarding or device-verification routing only when required.

**Alternates / Guardrails**

- DISABLED account -> deny protected access.

- Unfinished registration -> route to onboarding, not Home.

- Local navigation visibility is not sufficient: backend independently checks all protected requests.

Exit: Secure route exit and correct re-entry path.

Trace: FR-007, FR-124–131


<!-- Source PDF page 07 -->

## 04 Module 2: Channels & Membership

Discoverable channel folders, unique creation, one-active-channel rule, six-student cap, owner approvals, switching, empty-channel recovery, and Admin-only rename/deletion.

**Route Overview**

Channels tab | Discovery / My Channel | Workspace (Files, Members, | Channel Administration Owner requests); Admin

Approved refinement: Always show active student membership count n/6 on channel cards, lists and previews; update after membership changes.

### Approved flow register

| Flow ID | Journey |
| --- | --- |
| UF-CH-01 | Discover & create channel |
| UF-CH-02 | Request to join channel |
| UF-CH-03 | Approve or reject membership request |
| UF-CH-04 | Leave or switch channel |
| UF-CH-05 | Remove a channel member |
| UF-CH-06 | Reactivate zero-member channel |
| UF-CH-07 | Rename or delete channel |

### UF-CH-01 Discover & create channel

Actor: STUDENT Entry: Channels tab -> Discovery / Create

**Main Journey**

1. Open Channels; list or search discoverable channels and show active counts in n/6 format, e.g., 3/6.

2. If not an active channel member, select Create Channel and enter the name.

3. Check uniqueness case-insensitively after trimming surrounding whitespace.

4. Create channel; creator automatically joins and becomes channel owner.

5. Open Channel Workspace with initial membership count 1/6.

**Alternates / Guardrails**

- Already active member -> new channel creation is not available.

- Duplicate name -> explain conflict and retain entered name.

- Existing channels show 0/6 when empty; 6/6 when full.

Exit: New channel and member-owner relationship exist.

Trace: FR-014–020, FR-031–033


<!-- Source PDF page 08 -->

### UF-CH-02 Request to join channel

Actor: STUDENT Entry: Channels -> Discovery -> Channel preview

**Main Journey**

1. Browse/search channels with member count; open a preview to view channel name and n/6.

2. Select Request to Join; validate student membership and pending-request rules.

3. Submit a pending request without immediate workspace access.

4. Show pending status until decision; notify result and provide authorized destination after approval.

**Alternates / Guardrails**

- Existing pending request -> show that request rather than duplicate it.

- Student already belongs to channel -> no duplicate membership.

- Joining from an existing channel follows switch-flow, not two simultaneous memberships.

- Full channel cannot approve a seventh member.

Exit: Pending request submitted; approved request grants authorized membership.

Trace: FR-019–025, FR-031, FR-081

### UF-CH-03 Approve or reject membership request

Actor: Channel owner for occupied channel; ADMIN for zero-member channel Entry: Owner requests or Admin empty-channel queue

**Main Journey**

1. When request is created, route it to the occupied channel owner, or to Admin if zero members.

2. Approver opens queue and reviews applicant and target channel (show n/6).

3. Approve or reject; upon approval recheck one-channel and six-member constraints.

4. If eligible, add student, update n/6 and notify requester; otherwise explain failure.

**Alternates / Guardrails**

- Reject -> preserve current membership and notify rejection.

- Concurrent approvals cannot overfill channel.

- Unavailable owner -> request remains pending; alternate handling not yet decided.

Exit: Request is decided; membership reflects validated outcome.

Trace: FR-022–025, FR-080–081, FR-110

### UF-CH-04 Leave or switch channel

Actor: STUDENT Entry: Channel actions -> Leave; Discovery -> Request to Switch

**Main Journey**

1. To leave: open current channel actions, choose Leave and confirm; revoke former workspace access.

2. To switch: discover destination and submit request to its owner or Admin if destination is empty.

3. Keep current membership while switch is pending; show pending status.

4. On approval, validate eligibility, atomically leave source and join destination; update both n/6 counts.

5. After rejection, remain in original channel.

**Alternates / Guardrails**

- Last member leaving does not delete channel; it remains discoverable as 0/6.

- Owner departing with remaining members -> ownership transfer is unresolved.

- No transient dual-active-channel membership may occur.

Exit: Membership removed or switched without invalid simultaneous memberships.

Trace: FR-026–028, FR-030, FR-033, FR-083


<!-- Source PDF page 09 -->

### UF-CH-05 Remove a channel member

Actor: Channel Owner Entry: Channel Workspace -> Members

**Main Journey**

1. Open Members, display active roster and current n/6.

2. Owner selects a member and chooses Remove Member.

3. Confirm action; verify current user is the owner of that channel.

4. Remove the membership, reduce count, revoke former access and notify affected student.

5. Preserve original uploader metadata for files uploaded by the removed member.

**Alternates / Guardrails**

- Non-owner action -> denied by backend.

- Member already left -> refresh roster and prevent stale action.

- Owner self-removal/owner succession requires separate policy; do not invent it.

Exit: Member removed; count and authorization updated.

Trace: FR-029–031, FR-046, FR-082, FR-108

### UF-CH-06 Reactivate zero-member channel

Actor: STUDENT + ADMIN Entry: Discovery -> Empty channel (0/6) -> Admin approvals

**Main Journey**

1. Keep zero-member channels visible and displaying 0/6.

2. Student requests to join; create pending request routed to Admin.

3. Admin reviews and approves or rejects after eligibility check.

4. On approval add the student, update to 1/6, notify and enable workspace access.

**Alternates / Guardrails**

- First returning member ownership assignment remains UNDECIDED.

- Existing channel media stays associated with that channel.

- Zero members never triggers automatic deletion.

Exit: Previously empty channel gains an authorized member.

Trace: FR-033, FR-110–111

### UF-CH-07 Rename or delete channel

Actor: ADMIN Entry: Admin tab -> Channel Administration

**Main Journey**

1. Select channel and inspect current member and media-file counts.

2. Rename path: enter new unique name, validate, save displayed channel name.

3. Delete path: allow only when active student member count = 0 AND media file count = 0.

4. Require explicit Admin initiation and confirmation; delete eligible channel and record audit event.

**Alternates / Guardrails**

- Duplicate name -> reject rename.

- Member count or media count nonzero -> block deletion with reason.

- Channel archiving and automatic deletion are prohibited.

- Whether Trash counts toward media-file count, and how pending requests are handled, remain undecided.

Exit: Renamed channel or eligible channel deleted by Admin only.

Trace: FR-015, FR-034–035, FR-103, FR-108, FR-111


<!-- Source PDF page 10 -->

## 05 Module 3: File Uploading & Management

Selecting allowed media, safe validation, upload state and recovery, library search, rename permissions, management-only moves, and Trash entry.

**Route Overview**

Channel Workspace | Upload / Search / File options | Viewer / Move / Rename / Trash

Approved refinement: File category safety checks are mandatory. No per-channel quota; 1 GB maximum applies to video only. PDF/PPT file limits are open.

### Approved flow register

| Flow ID | Journey |
| --- | --- |
| UF-MED-01 | Select and validate file |
| UF-MED-02 | Transfer file and show progress |
| UF-MED-03 | Cancel, retry or recover upload |
| UF-MED-04 | Browse, search & sort files |
| UF-MED-05 | Rename uploaded file |
| UF-MED-06 | Move file between channels |
| UF-MED-07 | Delete file & enter Trash experience |

### UF-MED-01 Select and validate file

Actor: Authorized channel uploader Entry: Channel Workspace -> Upload -> system file picker

**Main Journey**

1. Enter a channel where upload permission exists; select Upload and choose a device file.

2. Check format: compatible iPhone/Android video (MP4/MOV), PDF, PPT or PPTX.

3. Reject executable/prohibited or identified malicious files; verify user can upload to destination.

4. If video, enforce single-video maximum of 1 GB; submit eligible file for transfer.

**Alternates / Guardrails**

- Unsupported/malicious file -> reject and explain.

- Video over 1 GB -> block and show limit.

- No approved per-channel quota exists; PDF/PPT maximum size remains undecided.

- Unsafe content must never be published.

Exit: Validated upload candidate or explicit rejection reason.

Trace: FR-036–043, FR-047, FR-049


<!-- Source PDF page 11 -->

### UF-MED-02 Transfer file and show progress

Actor: Authorized uploader Entry: Upload candidate -> MED-01 queue/progress

**Main Journey**

1. Create pending upload associated with channel and original uploader.

2. Start transfer; show pending and uploading states with progress.

3. When bytes arrive, show verification/safety-processing state.

4. Publish only after successful completion and safety checks; mark READY.

5. Show file in channel library; notify uploader of completion.

**Alternates / Guardrails**

- Transfer failure -> FAILED/interrupted state and retry path.

- Verification rejection -> never mark READY or expose to others.

- Approval from owner/moderator is NOT required for safe eligible files.

Exit: Safe READY item appears in authorized channel library.

Trace: FR-044–055, FR-086

### UF-MED-03 Cancel, retry or recover upload

Actor: Uploader Entry: MED-01 queue / interrupted upload state

**Main Journey**

1. User observes pending or uploading state; may choose Cancel.

2. On network interruption or transient failure, display recoverable status.

3. Where supported, retry or resume transfer; large video flow may use multipart/resumable transfer.

4. Verify completed file before switching to READY.

**Alternates / Guardrails**

- Cancel -> stop active attempt and clear or retire pending session safely.

- Interrupted transfer -> do not show as successfully published.

- App termination behavior and maximum parallel uploads are still undecided.

Exit: Transfer cancelled or safely completed/recovered.

Trace: FR-048, FR-050–055

### UF-MED-04 Browse, search & sort files

Actor: Authorized channel viewer Entry: Channel Workspace -> file library

**Main Journey**

1. Open authorized channel workspace and see media items plus channel member count n/6.

2. Search by available title/metadata; sort by date, name or file size.

3. Inspect title, original uploader, upload date, size and duration when video metadata is available.

4. Select file to enter its appropriate video or document viewer.

**Alternates / Guardrails**

- Empty library -> clear empty state.

- Former member or unauthorized user -> no private library access.

- Trashed and failed files must not appear as READY library items.

Exit: User locates and opens authorized files.

Trace: FR-031, FR-056–066, FR-075


<!-- Source PDF page 12 -->

### UF-MED-05 Rename uploaded file

Actor: Student/Owner/Sub-Management (own files); Management/Admin (all) Entry: File options -> Rename

**Main Journey**

1. Open file options; expose Rename only where ownership/role permits.

2. Enter replacement display name and validate.

3. Persist changed display title; leave original uploader and underlying storage object identity intact.

4. Refresh library listing and record relevant audit event.

**Alternates / Guardrails**

- Different uploader and insufficient staff privilege -> deny.

- Permission revoked between display and save -> backend rejects.

- File missing/trashed -> show current state; do not falsely confirm rename.

Exit: Authorized metadata rename completed.

Trace: FR-046, FR-067, FR-078, FR-101–103, FR-108

### UF-MED-06 Move file between channels

Actor: MANAGEMENT or ADMIN Entry: File options -> Move -> choose destination

**Main Journey**

1. Open file options and choose Move (management/admin only).

2. Select a valid destination channel.

3. Validate role and destination, then update channel association.

4. Preserve original uploader and storage identity; refresh source/destination libraries and audit the change.

**Alternates / Guardrails**

- Student, owner and Sub-Management have no move permission.

- Destination invalid/inaccessible -> reject and keep source unchanged.

- Moved file becomes accessible according to the destination channel rules.

Exit: File belongs to new channel without change of uploader.

Trace: FR-046, FR-068, FR-102–103, FR-108

### UF-MED-07 Delete file & enter Trash experience

Actor: Authorized uploader or privileged staff Entry: File options -> Move to Trash or Delete Permanently

**Main Journey**

1. Open authorized file options and choose Move to Trash or Delete Permanently.

2. Check current permission and ask for appropriate destructive-action confirmation.

3. Trash path removes file from normal library and starts recovery period.

4. Permanent deletion path bypasses normal app recovery.

5. Provide entry to MED-05 Trash for authorized users.

**Alternates / Guardrails**

- Unauthorised delete -> denied.

- Trashed item is not available through normal playback.

- Detailed restore/deletion lifecycle is defined in Module 5.

Exit: Item moved to Trash or permanently deleted according to permission.

Trace: FR-069–078, FR-101–103


<!-- Source PDF page 13 -->

## 06 Module 4: Video Playback & Document Viewing

In-app video streaming, player controls, authorization refresh, PDF and compatible presentation previews, unavailable-content handling.

**Route Overview**

Channel Workspace | file | Video Player or Document | Back to source Viewer

### Approved flow register

| Flow ID | Journey |
| --- | --- |
| UF-VIEW-01 | Open supported video or document |
| UF-VIEW-02 | Control video playback |
| UF-VIEW-03 | Handle buffering and playback recovery |
| UF-VIEW-04 | Read PDF in-app |
| UF-VIEW-05 | Preview PowerPoint |
| UF-VIEW-06 | Handle unavailable or unauthorized file |

### UF-VIEW-01 Open supported video or document

Actor: Authorized viewer Entry: Channel Workspace -> file selection

**Main Journey**

1. Choose a visible READY file in a channel library.

2. Recheck current access and object availability.

3. Route video to MED-02, PDF and supported presentations to MED-03.

4. Return from viewer to previous channel context.

**Alternates / Guardrails**

- No authorization -> access denied.

- Unsupported file -> explain format incompatibility.

- Missing/deleted/trashed file -> unavailable rather than stale content.

Exit: Appropriate in-app viewer opens for authorized content.

Trace: FR-056, FR-060, FR-064–066


<!-- Source PDF page 14 -->

### UF-VIEW-02 Control video playback

Actor: Authorized video viewer Entry: MED-02 Video Player

**Main Journey**

1. Open video; display loading state until player is ready.

2. Support play/pause, seeking and full-screen viewing.

3. During playback show controls and progress in a usable manner.

4. Return to channel library with expected back navigation.

**Alternates / Guardrails**

- Autoplay, orientation behavior and additional controls are future UI decisions.

- Loading or codec failure -> error rather than blank player.

Exit: User watches and navigates supported video.

Trace: FR-060–062

### UF-VIEW-03 Handle buffering and playback recovery

Actor: Authorized video viewer Entry: Video Player -> buffering or playback error

**Main Journey**

1. When playback stalls, present buffering state without losing context.

2. If network returns, continue where possible; otherwise provide recovery/retry.

3. If signed playback access expires, request new authorization from backend.

4. Resume only when renewed authorization is granted.

**Alternates / Guardrails**

- Access revoked -> stop protected playback.

- Unsupported codec or deleted object -> show clear error.

- Exact last-position persistence is not yet approved.

Exit: Playback recovers if still authorized and supported.

Trace: FR-062–063, FR-066

### UF-VIEW-04 Read PDF in-app

Actor: Authorized document viewer Entry: Channel Library -> select PDF -> MED-03

**Main Journey**

1. Select PDF and pass access check.

2. Load document inside UniSpace and present readable pages.

3. Navigate document; return to preceding channel context.

**Alternates / Guardrails**

- Unavailable or corrupt PDF -> clear error.

- Pinch zoom, page counter and document search are UI proposals, not new FR commitments.

Exit: PDF content previewed in app.

Trace: FR-064, FR-066


<!-- Source PDF page 15 -->

### UF-VIEW-05 Preview PowerPoint

Actor: Authorized document viewer Entry: Channel Library -> select PPT/PPTX -> MED-03

**Main Journey**

1. Select PPT/PPTX and confirm access.

2. Check whether compatible preview is available.

3. If supported, load presentation and navigate its slides.

4. Return to source workspace.

**Alternates / Guardrails**

- Incompatible rendering -> explain preview unavailable.

- Embedded animations and complex content need not be fully reproduced in initial implementation.

Exit: Compatible PowerPoint preview or clear fallback information.

Trace: FR-065–066

### UF-VIEW-06 Handle unavailable or unauthorized file

Actor: Any file-viewing user Entry: Open file from library, link or notification

**Main Journey**

1. Attempt file open; verify current authorization and existence.

2. If allowed and available, navigate to viewer.

3. Otherwise show permission/unavailable/unsupported explanation and safe return path.

**Alternates / Guardrails**

- Membership removed, channel move, Trash, deletion or revoked role may make stale links invalid.

- Do not allow a cached file identifier to bypass Spring Boot access checks.

Exit: No protected content exposed to unauthorized or invalid destinations.

Trace: FR-030, FR-063, FR-066, FR-075


<!-- Source PDF page 16 -->

## 07 Module 5: Trash & Recovery

Ownership-scoped Trash, direct and deferred permanent deletion, up-to-30-day recovery, automatic expiry and error handling.

**Route Overview**

Channel Workspace | File Options | Trash | Restore / Delete Permanently; System

### Approved flow register

| Flow ID | Journey |
| --- | --- |
| UF-TRASH-01 | Move an authorized file to Trash |
| UF-TRASH-02 | Browse authorized Trash |
| UF-TRASH-03 | Restore trashed file |
| UF-TRASH-04 | Permanently delete file |
| UF-TRASH-05 | Expire Trash automatically at 30 days |
| UF-TRASH-06 | Handle Trash permission and recovery errors |

### UF-TRASH-01 Move an authorized file to Trash

Actor: File owner; MANAGEMENT; ADMIN Entry: File Options -> Move to Trash

**Main Journey**

1. Locate a file and choose Move to Trash.

2. Check permission (owner or privileged role) and confirm where needed.

3. Mark file trashed, record trash timestamp and remove it from ordinary listings/playback.

4. Make it available in authorized Trash view during retention period.

**Alternates / Guardrails**

- Non-owner Student/Sub-Management cannot trash another uploader’s file.

- Already unavailable/trash file -> explain current state.

Exit: File is hidden from library and recoverable for up to 30 days.

Trace: FR-069, FR-071–075, FR-078


<!-- Source PDF page 17 -->

### UF-TRASH-02 Browse authorized Trash

Actor: Authorized file manager Entry: Channel Workspace -> Trash (MED-05)

**Main Journey**

1. Open Trash for items user may manage.

2. Display file title, original channel/uploader, deletion date and recovery deadline when known.

3. Select an item for Restore or Delete Permanently according to permission.

**Alternates / Guardrails**

- No matching items -> clear empty state.

- Permission changed since item was trashed -> no unauthorized access.

Exit: User finds eligible deleted items and actions.

Trace: FR-071, FR-075–078

### UF-TRASH-03 Restore trashed file

Actor: Authorized file manager Entry: MED-05 Trash -> file -> Restore

**Main Journey**

1. Select a trashed file and choose Restore.

2. Check current permission, 30-day window and valid accessible destination channel.

3. Restore file to a valid channel location preserving original uploader.

4. Remove Trash state and return item to ordinary authorized library.

**Alternates / Guardrails**

- Expired/revoked permission -> deny restore.

- Invalid destination -> explain and do not claim success.

- Restoration after uploader leaves or after a cross-channel move requires a policy decision.

Exit: Authorized file restored within retention window.

Trace: FR-072, FR-076, FR-078

### UF-TRASH-04 Permanently delete file

Actor: Authorized file manager Entry: File Options or Trash -> Delete Permanently

**Main Journey**

1. Choose permanent deletion directly from library or from an already trashed item.

2. Warn that app recovery is not available and request confirmation.

3. Check permission; permanently delete item and stop its visibility.

4. Show success only after the operation is confirmed by system.

**Alternates / Guardrails**

- Cancel confirmation -> no changes.

- Deletion failure -> explain; avoid false success.

- After permanent deletion, normal UniSpace restore is unavailable.

Exit: Item permanently deleted without ordinary recovery.

Trace: FR-070, FR-074, FR-077–078


<!-- Source PDF page 18 -->

### UF-TRASH-05 Expire Trash automatically at 30 days

Actor: System Entry: Background expiry process -> Trash records

**Main Journey**

1. Record start of retention when file enters Trash.

2. Allow authorized restoration before expiration.

3. Automatically permanently remove items no later than day 30.

4. Make expired items unavailable for restoration.

**Alternates / Guardrails**

- Exact cleanup scheduling/implementation is technical design.

- Automatic Trash expiry does NOT imply automatic channel deletion.

Exit: Trash retention never exceeds 30 days.

Trace: FR-072–074, FR-077, FR-111

### UF-TRASH-06 Handle Trash permission and recovery errors

Actor: Any affected user Entry: Trash operations or stale file link

**Main Journey**

1. For each restore/delete request, check account, role, ownership, channel access and item state.

2. Provide clear errors for expired retention, invalid destination, or no permission.

3. Allow retry after transient server/network failure where safe.

**Alternates / Guardrails**

- Former channel member restore permissions remain undecided.

- If media is in Trash, eligibility of deleting the empty channel is not finalized.

- Never reveal inaccessible content through Trash listings.

Exit: Failure is safe, understandable and does not modify unauthorized items.

Trace: FR-030, FR-071–078


<!-- Source PDF page 19 -->

## 08 Module 6: Google Drive Resources

Public viewable links, separate authorized Google-account access, read-only external bookmarks, browsing, refresh, supported previews and error recovery.

**Route Overview**

Resources | My Resources | Add public link OR | Bookmark | Browse / Viewer authorize existing

Approved refinement: Google Drive files are external and read-only, not part of UniSpace central media storage. Public link access and restricted authorized account access are separate paths.

### Approved flow register

| Flow ID | Journey |
| --- | --- |
| UF-RES-01 | Open Resources |
| UF-RES-02 | Add public Google Drive link |
| UF-RES-03 | Authorize Google account for restricted resources |
| UF-RES-04 | Bookmark Drive file or folder |
| UF-RES-05 | Browse and refresh saved folder |
| UF-RES-06 | Preview external Drive file |
| UF-RES-07 | Remove saved resource bookmark |
| UF-RES-08 | Handle Drive permission and connection errors |

### UF-RES-01 Open Resources

Actor: All ACTIVE users Entry: Bottom tab -> Resources

**Main Journey**

1. Tap Resources; open My Resources section.

2. Show saved folders/files as external resource references and an Add Resource entry.

3. Open saved items after access checks or display empty list if none.

**Alternates / Guardrails**

- Stale/inaccessible bookmarks -> show availability state rather than claiming Drive content is stored locally.

Exit: Resources library displayed.

Trace: FR-090–100


<!-- Source PDF page 20 -->

### UF-RES-02 Add public Google Drive link

Actor: Any ACTIVE user Entry: Resources -> Add Resource -> Paste link

**Main Journey**

1. Choose Add Resource and paste a Google Drive file/folder shared with anyone having link.

2. Validate URL format and actual access without assuming public visibility from the URL alone.

3. Save bookmark to accessible resource; display in Resources.

4. Open corresponding folder browser or viewer.

**Alternates / Guardrails**

- Invalid, restricted, removed or unsupported link -> explain; may suggest account authorization.

- Public link normally does not require an additional Google sign-in when truly viewable.

Exit: Accessible external resource bookmark saved.

Trace: FR-090, FR-092–093

### UF-RES-03 Authorize Google account for restricted resources

Actor: Any ACTIVE user needing restricted Drive access Entry: Resources -> Add -> Connect Google Drive

**Main Journey**

1. Choose existing Google account (may differ from UniSpace provider identity).

2. Request appropriate read access with Google authorization/consent.

3. After consent, browse/select authorized resource and save bookmark.

4. Return to Resources context.

**Alternates / Guardrails**

- Consent denied/cancelled -> restricted Drive path unavailable; ordinary UniSpace remains usable.

- Expired authorization -> request renewal when needed.

- Exact scopes and arbitrary folder selection implementation remain technical decisions.

Exit: Restricted accessible item can be viewed with authorized account.

Trace: FR-091–092, FR-096

### UF-RES-04 Bookmark Drive file or folder

Actor: Any authorized resource viewer Entry: Drive file or folder -> Save bookmark

**Main Journey**

1. Choose an accessible folder or individual file.

2. Save its external reference, not its contents, in My Resources.

3. Reopen bookmark later after a fresh access/availability check.

**Alternates / Guardrails**

- Owner restricts/removes file -> bookmark remains but content may become unavailable.

- Personal versus globally shared bookmarks remain an open product decision.

Exit: External reference stored without copying source bytes.

Trace: FR-093, FR-096, FR-100


<!-- Source PDF page 21 -->

### UF-RES-05 Browse and refresh saved folder

Actor: Authorized Drive resource viewer Entry: Resources -> Saved Folders -> folder browser

**Main Journey**

1. Open bookmarked folder; verify Google permissions and load accessible children.

2. Show supported teaching files and loading state.

3. Select Refresh to retrieve current accessible contents.

4. Open selected item in supported in-app viewer.

**Alternates / Guardrails**

- Removed/restricted folder -> explain and preserve safe navigation.

- Network/API failure -> display retry without implying content was cached offline.

Exit: Current accessible folder list displayed.

Trace: FR-092, FR-094–095, FR-099

### UF-RES-06 Preview external Drive file

Actor: Authorized Drive resource viewer Entry: Saved file / folder item -> RES-05 viewer

**Main Journey**

1. Select Drive PDF, image, video or presentation.

2. Recheck Google permissions and preview support.

3. Open supported item inside UniSpace in read-only mode.

4. Return to resource list on exit.

**Alternates / Guardrails**

- PPT/PPTX preview may have compatibility limitations.

- Access revoked or file unsupported -> explain and do not attempt edit/upload/delete.

Exit: Permitted Drive content previewed without external modification.

Trace: FR-097, FR-099–100

### UF-RES-07 Remove saved resource bookmark

Actor: Any bookmark owner / authorized user Entry: My Resources -> resource options -> Remove

**Main Journey**

1. Select an existing bookmarked file/folder.

2. Choose Remove Bookmark.

3. Remove reference from My Resources; update list.

**Alternates / Guardrails**

- This does not delete, edit, move or alter any Google Drive source file.

- Resource-sharing scope for bookmarks is not yet finalized.

Exit: Bookmark removed, external source unchanged.

Trace: FR-098, FR-100


<!-- Source PDF page 22 -->

### UF-RES-08 Handle Drive permission and connection errors

Actor: Any Resources user Entry: Any Resources access/authorization/preview operation

**Main Journey**

1. Recognize invalid links, expired Google consent, denied folder access, removed item, or unsupported preview.

2. Display error with relevant guidance (reauthorize, retry, or return).

3. Keep Resources separate from UniSpace channel file permissions/storage.

**Alternates / Guardrails**

- Restricted folders may require Google consent even if user signed in to UniSpace with Google.

- Offline viewing is not approved.

- User can always access other permitted UniSpace areas when Drive authorization is denied.

Exit: Safe recovery with accurate external-access status.

Trace: FR-091–092, FR-099–100


<!-- Source PDF page 23 -->

## 09 Module 7: Notifications

Database-backed in-app inbox via AppBar bell, push behavior by foreground/background state, member/role/upload events, deep-link authorization and read state.

**Route Overview**

AppBar bell | NOT-01 Inbox | authorized destination | prior navigation context

Approved refinement: APPROVED clarification: both in-app + push. Foreground: update inbox/badge, avoid duplicate OS banner; background/closed: attempt push when permitted; disabled push: inbox remains.

### Approved flow register

| Flow ID | Journey |
| --- | --- |
| UF-NOT-01 | Open notification inbox |
| UF-NOT-02 | Handle channel membership notifications |
| UF-NOT-03 | Handle role promotion notifications |
| UF-NOT-04 | Notify upload outcomes |
| UF-NOT-05 | Deliver device push by app state |
| UF-NOT-06 | Navigate from notification |
| UF-NOT-07 | Maintain read & unread state |
| UF-NOT-08 | Handle notification failures |

### UF-NOT-01 Open notification inbox

Actor: All ACTIVE users Entry: AppBar bell -> NOT-01 Inbox

**Main Journey**

1. Tap AppBar notification bell from a primary screen.

2. Open full in-app inbox with unread/read entries and an unread indicator on the bell.

3. Select an entry or go Back to previous route.

**Alternates / Guardrails**

- No notifications -> clear empty state.

- Notification is NOT a bottom navigation destination.

- Returning restores prior allowed tab or screen where feasible.

Exit: Notification Inbox reachable without extra bottom tab.

Trace: FR-087, FR-089


<!-- Source PDF page 24 -->

### UF-NOT-02 Handle channel membership notifications

Actor: Owner, student and Admin for zero-member approvals Entry: Join / reject / remove / switch event

**Main Journey**

1. On occupied-channel join request, save in-app notification for channel owner.

2. On approval/rejection, save outcome for requesting student.

3. On removal, notify affected member; on completed switch, notify switching student.

4. Notification opens relevant authorized request/status/channel route.

**Alternates / Guardrails**

- Empty channel decisions are made by Admin; specific Admin alert for these requests is a proposed addition not explicitly in FR-080.

- Permission can change before notification is opened; recheck.

Exit: Membership changes are communicated via in-app inbox, with push where appropriate.

Trace: FR-080–083, FR-089, FR-110

### UF-NOT-03 Handle role promotion notifications

Actor: Student / ADMIN Entry: Role promotion submission/decision

**Main Journey**

1. Student requests Sub-Management or Management.

2. Store/Admin notification for new role request.

3. Admin reviews and decides request.

4. Save result notification for student and route to authorized request detail/status.

**Alternates / Guardrails**

- Admin role is assigned directly by an existing Admin; ordinary student promotion requests do not offer ADMIN.

- Role-specific navigation must refresh after approval.

Exit: Both parties informed of role-request lifecycle.

Trace: FR-010–013, FR-084–085, FR-089

### UF-NOT-04 Notify upload outcomes

Actor: Uploader Entry: Upload completes or fails

**Main Journey**

1. On significant completion or failure, save an in-app notification for uploader.

2. Update bell unread indicator.

3. When selected, open available uploaded file or upload/recovery context.

**Alternates / Guardrails**

- Routine percentage-by-percentage upload push events are not required.

- File no longer accessible -> show unavailable/permission result.

Exit: Uploader receives outcome without irrelevant frequent alerts.

Trace: FR-086–087, FR-089


<!-- Source PDF page 25 -->

### UF-NOT-05 Deliver device push by app state

Actor: All opted-in users Entry: Backend notification event -> inbox + push delivery

**Main Journey**

1. Always save important event in database-backed in-app inbox.

2. When app is foregrounded, update inbox/badge; avoid redundant OS notification banner for already-visible event.

3. When app is backgrounded or closed, attempt OS push if permission and delivery are available.

4. When push is disabled or unavailable, retain in-app notification history.

**Alternates / Guardrails**

- Push is best effort; OS/device delivery is not guaranteed.

- Foreground banner specifics are later UI decisions; avoid duplicating an event actively displayed.

- OS push permission is separate from in-app inbox access.

Exit: Consistent in-app record; appropriate best-effort device push.

Trace: FR-087–089 Note: Explicitly approved post-draft notification refinement: foreground = in-app/badge, background/closed = push if allowed, permission disabled = in-app remains.

### UF-NOT-06 Navigate from notification

Actor: Authenticated notification recipient Entry: Inbox item or OS notification tap

**Main Journey**

1. User selects notification item/push.

2. Restore/require authenticated ACTIVE session as necessary.

3. Verify current role, channel/resource authorization and destination availability.

4. Navigate to permitted request, channel, upload or profile status screen.

**Alternates / Guardrails**

- Deleted/unavailable destination -> show safe error.

- Revoked permission -> deny, even if notification was previously issued.

- Return/back should respect originating navigation context.

Exit: Authorized deep-link navigation only.

Trace: FR-089, FR-124–131

### UF-NOT-07 Maintain read & unread state

Actor: All ACTIVE users Entry: Inbox -> open notification

**Main Journey**

1. Display inbox entries with read/unread status.

2. New event increments or updates unread indicator.

3. Opening item can mark it read (UI proposal) and navigate when authorized.

4. Returning shows coherent inbox state.

**Alternates / Guardrails**

- Mark-all-read, deletion and advanced filtering have not been approved as functional capabilities.

- Exact auto-mark-read policy is an interface decision.

Exit: Notification read state remains understandable.

Trace: FR-087


<!-- Source PDF page 26 -->

### UF-NOT-08 Handle notification failures

Actor: All users Entry: Inbox loading, push receipt or deep link

**Main Journey**

1. Show empty, loading or error state while fetching inbox.

2. On transient failure, permit retry.

3. If device push is off, continue inbox delivery.

4. If deep link is invalid, provide safe fallback.

**Alternates / Guardrails**

- Expired sessions -> reauthenticate before protected target.

- Role revoked or channel deleted -> deny stale destination.

- Push failure must not erase in-app record.

Exit: Safe, recoverable notification experience.

Trace: FR-087–089


<!-- Source PDF page 27 -->

## 10 Module 8: Profile & Settings

Profile display, promotion requests, read-only identity details, top-level Display Mode and Sign Out, Settings sub-sections including accessibility.

**Route Overview**

Profile | Personal Details / Role Promotion / Display Mode / Settings (Themes, Accessibility, Notifications, Connected Accounts,

Approved refinement: Approved post-IA-PDF correction: Display Mode and Sign Out are direct Profile actions, above/below Settings respectively. Accessibility sits inside Settings. Themes are separate from Display Mode.

### Approved flow register

| Flow ID | Journey |
| --- | --- |
| UF-PRO-01 | View personal details |
| UF-PRO-02 | Request role promotion |
| UF-PRO-03 | Review role request status |
| UF-PRO-04 | Open Settings |
| UF-PRO-05 | Select Display Mode / plan theme variations |
| UF-PRO-06 | Open notification settings |
| UF-PRO-07 | View connected Google account status |
| UF-PRO-08 | View security and session context |
| UF-PRO-09 | Sign out from Profile |
| UF-PRO-10 | Access accessibility options |

### UF-PRO-01 View personal details

Actor: All ACTIVE users Entry: Profile tab -> Personal Details

**Main Journey**

1. Open Profile and Personal Details.

2. Display registered first/last name, Student ID, verified university email, sign-in provider, current role and current channel where relevant.

3. Return to Profile.

**Alternates / Guardrails**

- Editing registered names, Student ID or verified email AFTER registration is NOT approved.

- Missing/unavailable profile data -> error/retry rather than fabricated fields.

Exit: Accurate read-only account overview.

Trace: FR-008–009, FR-114–123


<!-- Source PDF page 28 -->

### UF-PRO-02 Request role promotion

Actor: STUDENT Entry: Profile -> Role Promotion

**Main Journey**

1. Open Role Promotion and select SUB_MANAGEMENT or MANAGEMENT.

2. Submit request for Admin review.

3. Show Pending state while waiting.

4. Admin decision changes role only if approved; notify requester.

**Alternates / Guardrails**

- ADMIN cannot be requested through ordinary student promotion action.

- Submission error -> preserve choice and allow retry; no silent role update.

Exit: Role request saved for Admin, no immediate privilege escalation.

Trace: FR-010–013, FR-084–085

### UF-PRO-03 Review role request status

Actor: STUDENT requestor Entry: Profile -> Role Promotion -> Request status

**Main Journey**

1. Open Role Promotion and inspect current/requested role.

2. Display Pending, Approved or Rejected status.

3. On approval, refresh available permissions and navigation.

**Alternates / Guardrails**

- Decision notification can also deep link to this screen.

- Status must be backend-confirmed.

Exit: User understands status without searching inbox.

Trace: FR-010–013, FR-085

### UF-PRO-04 Open Settings

Actor: All ACTIVE users Entry: Profile -> Settings

**Main Journey**

1. Open Profile and select Settings.

2. Show sections: Themes, Accessibility, Notifications, Connected Accounts, Security and About UniSpace.

3. Open selected section and return to Profile via Back.

**Alternates / Guardrails**

- Display Mode is ABOVE Settings on Profile, not inside Settings.

- Sign Out is BELOW Settings on Profile, not inside Settings.

- Not every listed Settings category has approved user controls yet.

Exit: Settings categories available within agreed profile hierarchy.

Trace: FR-008–009, FR-007, FR-131


<!-- Source PDF page 29 -->

### UF-PRO-05 Select Display Mode / plan theme variations

Actor: All ACTIVE users Entry: Profile -> Display Mode; Settings -> Themes

**Main Journey**

1. Open top-level Profile Display Mode and choose Follow System (default), Light or Dark (approved UI placement).

2. Themes is a separate item WITHIN Settings, conceptually holding UniSpace Default and future seasonal themes.

3. Maintain shared screen structure, readability and accessible contrast across supported visual modes.

**Alternates / Guardrails**

- Manual display-mode persistence and specific theme-switching behavior still require confirmation for implementation scope.

- Thingyan, Halloween and Christmas are planned future themes, not active shipped requirements.

- Do not put Display Mode inside Settings.

Exit: Distinct Profile Display Mode placement and expandable Themes area.

Trace: IA decision; future FR for user-controlled appearance Note: Approved IA correction after the IA PDF: Display Mode sits on Profile above Settings; Sign Out sits below Settings.

### UF-PRO-06 Open notification settings

Actor: All ACTIVE users Entry: Profile -> Settings -> Notifications

**Main Journey**

1. Navigate to Notifications Settings.

2. Provide appropriate status of system notification permission if implemented.

3. Keep approved in-app inbox functional regardless of OS push permission.

**Alternates / Guardrails**

- Channel-, role-and upload-category switches are proposals only.

- Push-disabled users still receive in-app notifications.

Exit: Settings route consistent with approved notification delivery.

Trace: FR-087–089; notification preferences future FR

### UF-PRO-07 View connected Google account status

Actor: All ACTIVE users Entry: Profile -> Settings -> Connected Accounts

**Main Journey**

1. Open Connected Accounts section.

2. Show existing Google Drive authorization status where available.

3. If user needs access, launch approved Resources Google authorization flow.

4. Return to Settings with appropriate status.

**Alternates / Guardrails**

- Disconnect controls, linking multiple Drive accounts and detailed account administration are not approved.

- Google Drive authorization is separate from Google/Apple UniSpace login.

Exit: Clear external Drive authorization status and path.

Trace: FR-090–100


<!-- Source PDF page 30 -->

### UF-PRO-08 View security and session context

Actor: All ACTIVE users Entry: Profile -> Settings -> Security

**Main Journey**

1. Open Security category.

2. Describe current trusted-session/security state where possible.

3. Use approved session routing and device verification when authentication is invalid.

**Alternates / Guardrails**

- List/revoke other trusted devices is a possible FUTURE feature, not approved.

- Never store Google/Apple passwords in client UI.

Exit: Security entry point without unapproved device-management capability.

Trace: FR-125–131

### UF-PRO-09 Sign out from Profile

Actor: All ACTIVE users Entry: Profile -> Sign Out (top-level, after Settings)

**Main Journey**

1. Open Profile, scroll to Sign Out action directly BELOW Settings.

2. Choose Sign Out and confirm where applicable.

3. End authenticated session and protected app state.

4. Navigate to Welcome / Sign-In without resetting completed registration.

**Alternates / Guardrails**

- Cancel confirmation -> remain signed in.

- Sign Out must not be hidden inside Settings.

Exit: Current device signed out; registration data preserved.

Trace: FR-007, FR-131

### UF-PRO-10 Access accessibility options

Actor: All ACTIVE users Entry: Profile -> Settings -> Accessibility

**Main Journey**

1. Open dedicated Accessibility section under Settings.

2. Provide understandable organization for text/display, motion/animation and visual accessibility preferences where implemented.

3. Respect device-level accessibility services regardless of whether any extra in-app setting is enabled.

4. Return to Settings/Profile without losing preference context.

**Alternates / Guardrails**

- VoiceOver/TalkBack support, readable contrast, scalable text and accessible targets must work without opt-in toggles.

- Extra text-size controls or explicit reduced-motion switches require subsequent specification; they are proposals, not v1.5 FRs.

Exit: Accessibility remains discoverable; essential support is not conditional on switches.

Trace: Approved IA/user-flow design; later Accessibility specification


<!-- Source PDF page 31 -->

## 11 Module 9: Administration

Admin-only root dashboard, user and role management, zero-member channel approvals, channel deletion, cross-channel media, monitoring and audit inspection.

**Route Overview**

Admin bottom tab | Dashboard ROOT | Users & Roles / Requests / | Dashboard Channel Admin / Monitoring

Approved refinement: Approved after Module 9 draft: Admin Dashboard is root page of Admin tab. It is not a separate pushed page. Secondary Admin routes have Back.

### Approved flow register

| Flow ID | Journey |
| --- | --- |
| UF-ADM-01 | Open Administration Dashboard |
| UF-ADM-02 | Browse user directory & account details |
| UF-ADM-03 | Review student promotion requests |
| UF-ADM-04 | Assign, promote, demote or revoke roles |
| UF-ADM-05 | Approve join requests for empty channels |
| UF-ADM-06 | Rename / manually delete eligible channel |
| UF-ADM-07 | Manage media across channels |
| UF-ADM-08 | Inspect operational health & statistics |
| UF-ADM-09 | Explore audit records |

### UF-ADM-01 Open Administration Dashboard

Actor: ADMIN Entry: Admin-only bottom tab -> Dashboard root

**Main Journey**

1. Render Admin tab only for authenticated users whose current role is ADMIN.

2. Tap Admin; show ADM-01 Dashboard as ROOT screen, not a pushed secondary screen.

3. Provide entry cards/rows for Users & Roles, Role Requests, Channel Administration, System Monitoring and Audit Logs.

4. Open a subpage, then Back returns to Admin Dashboard.

**Alternates / Guardrails**

- Role revoked -> hide Admin tab and deny protected navigation.

- Other users use same four shared bottom tabs without Admin.

- Nested Admin navigation stack is proposed to preserve context.

Exit: Admin can reach all permitted admin workflows from dashboard root.

Trace: FR-001, FR-013, FR-103–107 Note: Explicitly approved: Admin Dashboard is the primary/root screen of the Admin bottom tab; detail routes have Back to it.


<!-- Source PDF page 32 -->

### UF-ADM-02 Browse user directory & account details

Actor: ADMIN Entry: Admin Dashboard -> Users & Roles -> Directory

**Main Journey**

1. Open read-only directory of registered users.

2. Select user to view first/last name, Student ID, university email, current role, account status and channel where available.

3. Enter a separate Manage Role action when appropriate.

**Alternates / Guardrails**

- No general editing of personal information or account deletion.

- Role changes are approved exception to read-only account management.

Exit: Admin sees verified account metadata without broad edit rights.

Trace: FR-104, FR-109, FR-112

### UF-ADM-03 Review student promotion requests

Actor: ADMIN Entry: Admin Dashboard -> Role Requests

**Main Journey**

1. Open pending SUB_MANAGEMENT/MANAGEMENT requests.

2. Select request and inspect applicant/current role/requested role.

3. Approve or reject, validating that request remains eligible.

4. Update role on approval, notify requester and write audit event.

**Alternates / Guardrails**

- Already processed request -> refresh status; prevent duplicate decision.

- Reject -> preserve prior role.

- Student cannot self-request ADMIN through this path.

Exit: Role request resolved and communicated.

Trace: FR-010–013, FR-084–085, FR-108

### UF-ADM-04 Assign, promote, demote or revoke roles

Actor: ADMIN Entry: User Directory -> User Details -> Manage Role

**Main Journey**

1. Select a registered user and a target role among the four approved roles.

2. Before committing, check that at least one active ADMIN will remain.

3. Confirm role change and persist authorization update.

4. Write audit entry and refresh affected user permissions and navigation.

**Alternates / Guardrails**

- Cannot demote/revoke final active ADMIN.

- There is no SUPER_ADMIN role.

- Effect on any preexisting channel membership/ownership when staff role changes is unresolved.

Exit: Authorized role change without removal of last Admin.

Trace: FR-005, FR-012–013, FR-108, FR-112


<!-- Source PDF page 33 -->

### UF-ADM-05 Approve join requests for empty channels

Actor: ADMIN Entry: Admin Dashboard -> Empty Channel Approvals

**Main Journey**

1. Queue requests for channels with zero active members (0/6).

2. Open request and inspect student, channel and join/switch context.

3. Approve or reject and validate membership/capacity again at decision time.

4. On success add first member and update count to 1/6; notify student.

**Alternates / Guardrails**

- Ownership assignment to first approved member is unresolved.

- If another request is already approved, reroute further joins according to occupied-channel owner rule when possible.

- Ineligible/stale requests -> show failure without improper access.

Exit: Empty channel safely reopened to approved member.

Trace: FR-023–025, FR-027–028, FR-033, FR-110

### UF-ADM-06 Rename / manually delete eligible channel

Actor: ADMIN Entry: Admin Dashboard -> Channel Administration

**Main Journey**

1. Choose channel and inspect membership/media counts.

2. Rename: validate unique new display name and save.

3. Delete: permit only zero active student members AND zero media files; require explicit confirmation.

4. Record channel rename/delete in audit logs.

**Alternates / Guardrails**

- Any members or media -> prevent delete.

- No automatic deletion and no archive capability.

- Trash counting and pending-request treatment remain undecided.

Exit: Authorized Admin rename or eligible manual deletion only.

Trace: FR-015, FR-034–035, FR-103, FR-108, FR-111

### UF-ADM-07 Manage media across channels

Actor: ADMIN Entry: Admin or Channels -> channel workspace -> File Options

**Main Journey**

1. Browse any channel using shared workspace.

2. View/upload, rename, move, trash or permanently delete authorized media across channels.

3. Restore eligible files through shared Trash workflow.

4. Preserve original uploader on cross-channel moves and audit operations.

**Alternates / Guardrails**

- Reuse student/staff file screens, not a second standalone media app.

- File-specific safety, 1 GB video upload maximum and 30-day Trash rules still apply.

Exit: Admin media operations completed through shared channel surfaces.

Trace: FR-036–078, FR-102–103, FR-108


<!-- Source PDF page 34 -->

### UF-ADM-08 Inspect operational health & statistics

Actor: ADMIN Entry: Admin Dashboard -> System Monitoring

**Main Journey**

1. Open operational dashboard.

2. Inspect application/API health and available operational indicators.

3. Inspect aggregate file count, centralized storage consumption and channel/membership statistics.

4. Return to Dashboard or other admin functions.

**Alternates / Guardrails**

- Unavailable monitoring data must be distinguished from healthy/zero metrics.

- Precise chart choices and thresholds belong to later UI/technical specification.

Exit: Admin sees aggregated system status without raw backend credentials.

Trace: FR-105–106

### UF-ADM-09 Explore audit records

Actor: ADMIN Entry: Admin Dashboard -> Audit Logs

**Main Journey**

1. Open audit-event list and search or inspect supported events.

2. Select event to view actor, operation, target resource, time and relevant result/context when recorded.

3. Review role changes, membership decisions, file changes, Trash and channel deletion.

**Alternates / Guardrails**

- Audit records are inspected, not edited or deleted via Admin app.

- Retention/filtering specifics not finalized.

- Read failure -> show error and safe retry.

Exit: Admin can inspect important recorded activity.

Trace: FR-107–108


<!-- Source PDF page 35 -->

## 12 Unresolved Product Decisions

The following decisions are deliberately excluded from the approved flow behavior. They should be resolved by explicit change control before the affected feature is implemented.

| Area | Decision to be approved |
| --- | --- |
| Channel ownership transfer | What happens when a channel owner leaves or switches while other members remain? |
| Empty channel new owner | Does Admin approval of the first new member automatically assign ownership, or does Admin assign an owner? |
| Channel deletion / Trash | Do files in Trash count as media when evaluating Admin-only deletion of an otherwise empty channel? |
| Pending requests on channel deletion | How are join/switch requests resolved when an eligible channel is deleted? |
| Former member file restoration | Can an original uploader who has left or been removed restore their previous upload without independent channel access? |
| Restore after file moves | If a file moved between channels before entering Trash, which valid channel receives it on restore? |
| Owner removal / self-exit | What happens if the channel owner is also subject to a member-removal or self-exit action? |
| Staff role / membership interaction | How does reassignment to a staff role affect an existing channel membership or channel-ownership relationship? |
| Non-video size limits | What maximum applies to uploaded PDF and PPT/PPTX files? |
| Upload lifecycle controls | Upload behavior on OS/app termination, simultaneous upload limits, duplicate names and replacement/versioning policy. |
| Nested folders | Are extra user-created folders allowed inside a channel beyond one level of Channel -> files? |
| Drive bookmark sharing | Are saved teaching bookmarks personal, channel-scoped or publishable globally by Management? |
| Drive authorization and control | Exact Google Drive scopes, supported restricted-folder selection and disconnect/multi-account controls. |
| Notification details | Retention, deletion, mark-all-read, filtering and category preferences; specific empty-channel Admin alert. |
| Appearance and accessibility controls | Which appearance settings and seasonal themes actually ship; persistence and extra accessibility toggles. |
| Student identity, OTP and trust | Exact Student ID format, university email domains, code expiry/resend limits and session expiration. |
| Video/document UX | Autoplay, orientation, video position memory, precise PDF controls and PowerPoint preview strategy. |
| Audit log retention | Retention interval and filtering specifics. |


<!-- Source PDF page 36 -->

## 13 Traceability & Acceptance Checklist

Each flow is suitable for a focused Codex work session. Implementation should be accepted only when normal, unsuccessful and authorization paths have been exercised.

Scope safeguard: A proposed interface treatment can appear in a prototype, but cannot grant new privileges, change file retention or introduce new account/identity rules without explicit functional approval.

| Module | Essential test/UX acceptance coverage |
| --- | --- |
| Authentication | Provider sign-in, provisional states, university email verification, returning trusted device, new-device OTP, interrupted onboarding, reinstall restart and explicit sign-out. |
| Channels | Unique names, one membership, max six under concurrency, n/6 display on every channel listing/preview, owner vs Admin approvals, leaving/switching, zero-member visibility and Admin-only deletion. |
| Uploads | Allowed video/doc types, 1 GB video cap, EXE/malware rejection, progress, cancellation, retries, no unauthorized READY publication, ownership retention. |
| Media playback | Authorized video/PDF/PPT preview, play/pause/seek/full-screen, expired URL renewal with reauthorization, errors for unavailable items. |
| Trash | Owner/staff authorization, Restore before 30 days, direct permanent deletion, automatic expiry by 30 days, no channel auto-delete. |
| Google Drive | Public link and restricted Google-account paths; refresh, bookmark, supported read-only preview, never copy/edit/delete external source. |
| Notifications | AppBar bell, inbox status, membership/role/upload events, foreground de-duplication, background best-effort push, push-disabled inbox, permission-safe links. |
| Profile | Read-only personal details, Student promotion, Display Mode above Settings, Themes/Accessibility inside Settings, Sign Out below Settings. |
| Admin | Admin tab dashboard root, nested child/back navigation, role requests, multiple Admins with last-Admin safeguard, channel management, system health and audit logs. |

### Minimum implementation evidence per flow

- Linked FR and IA route(s) recorded in task description.

- Tested success path and at least one relevant error/permission path.

- No unauthorized behavior or unapproved assumptions added.

- Backend tests for security/data rules; Flutter widget/integration tests for visible interactions.

- Documented actual result, known limitations and regression-test status.

### Approval record

Modules 1 through 9 were approved in the Stage 3 conversation, including the four later refinements documented in Section 01. This consolidated PDF is an editorial assembly of those approved flows; it does not replace Functional Requirements v1.5.

Coverage audit: 67 unique IDs across 9 modules; no duplicate flow IDs.
