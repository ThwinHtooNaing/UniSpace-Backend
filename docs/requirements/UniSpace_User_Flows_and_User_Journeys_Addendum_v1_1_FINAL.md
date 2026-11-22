# UniSpace - User Flows & User Journeys Addendum v1.1
**Controlled extension to User Flows v1.0 | APPROVED | 22 November 2026**

**Approval record.** User approved this controlled v1.1 addendum on 22 November 2026. Original **67 approved flow IDs remain unchanged**. Four additional MVP journeys use stable **ADD-FR** extension identifiers; they are approved addendum journeys, not renumbered v1.0 flows. This approves the journeys and acceptance criteria, **not** UI layout, APIs, database models, or test completion.

## 1. Scope, sources and governance

- **Authoritative FRs:** Functional Requirements v1.5 (FR-001–137) and Approved Addendum v1.0 (FR-138–167). Total 167: 150 MVP, 17 post-MVP.
- **Original journeys:** User Flows & User Journeys v1.0 (67 approved flows, retained as-is).
- **Navigation authority:** Information Architecture v1.0 + approved IA Addendum v1.0. Stage 3.5 approved policies supersede historical "unresolved" flow notes.
- **Supporting:** Product Decisions & MVP Scope review consolidation; Non-Functional Requirements v1.0 (42 objectives); B0-01 traceability workbook v0.2 (final approved mapping).
- **This revision:** Four approved MVP addendum journeys, 21 existing-flow amendments, 2 system-level acceptance rules, 3 post-MVP coverage backlogs and a 31-item traceability index.

## 2. Four approved MVP journeys (addendum identifiers)

### 2.1 ADD-FR-167 - Link Google and Apple Sign-In Providers
**Requirements:** FR-167  |  **Actor:** Authenticated UniSpace user
**Entry:** Profile > Settings > Connected Accounts > Linked Sign-In Providers (approved IA area; final controls in Stage 4)
**Preconditions:** The user is signed in to an existing UniSpace account; the second provider identity is not already assigned to a different account.

**Main journey**
1. Open Linked Sign-In Providers and review current Google/Apple association.
2. Choose to link the other supported provider and complete that provider's secure sign-in or ownership-proof flow.
3. Server verifies the provider assertion and identifies the immutable provider subject, not just an email address.
4. If the identity is unassigned, associate it with the current UniSpace account; preserve account ID, role, registration status and channel memberships.
5. Return to the same signed-in account; display the newly linked provider.

**Alternates, failures and authorization**
- User cancels provider authorization: retain current session and all existing links.
- Provider token is invalid, expired or unverifiable: reject without changing identity associations.
- Identity is already attached to another UniSpace account: block linking; never merge based on matching email.
- Repeated link request or an already-linked identity: do not create a duplicate association; report existing status.
- Network interruption or concurrent linking: show retry/status without a partially linked identity.

**Server-side enforcement:** Validate current session and provider proof, enforce unique (provider, provider-subject) ownership, and commit association atomically. Never trust an email-only match.
**Exit state:** The additional provider is safely linked to exactly one UniSpace account, or no links changed.
**Acceptance checks (approved criteria; tests not yet executed):**
- AC-ADD-FR-167-01: Valid Google-to-Apple and Apple-to-Google linking preserves the same UniSpace user ID and role.
- AC-ADD-FR-167-02: Cancellation, invalid proof and identity-in-use never merge or corrupt accounts.
- AC-ADD-FR-167-03: Concurrent/repeated linking produces no duplicate provider association.

### 2.2 ADD-FR-157 - Configure Institutional Student Identity Rules
**Requirements:** FR-157  |  **Actor:** ADMIN
**Entry:** Admin tab > Dashboard > Institutional Settings (approved IA subroute; specific form controls in Stage 4)
**Preconditions:** Actor is an authenticated, currently authorized ADMIN; existing student ID and email policy is readable.

**Main journey**
1. Open Institutional Settings; review currently configured permitted university-email domains and Student ID validation patterns.
2. Choose an edit action and enter proposed domain/pattern changes.
3. Application presents validation feedback for malformed or unusable configuration.
4. Admin confirms; server rechecks Admin privileges, validates and saves the new active policy.
5. Show confirmation and the new configuration; subsequent registrations use the current policy.

**Alternates, failures and authorization**
- Non-Admin or revoked Admin is denied; no policy is revealed or modified beyond permitted access.
- Invalid domain/pattern or unsupported configuration: reject and show specific correction guidance; retain existing policy.
- Concurrent configuration edit or server failure: avoid partial updates and show current effective values.
- Existing ACTIVE user no longer matches updated patterns: preserve that account and its access under the approved non-retroactive rule.

**Server-side enforcement:** Admin-only authorization, validation of policy input, consistent persisted active configuration, current-rule enforcement for later registrations, and no retroactive invalidation.
**Exit state:** A valid new policy is stored and applied to future registrations, or the previous policy remains unchanged.
**Acceptance checks (approved criteria; tests not yet executed):**
- AC-ADD-FR-157-01: Only authorized Admin can change allowed domains and Student ID patterns.
- AC-ADD-FR-157-02: Invalid input cannot replace a valid active policy.
- AC-ADD-FR-157-03: Existing registered accounts remain valid after policy changes; new registrations use updated checks.

### 2.3 ADD-FR-166 - Exceptional Admin Removal of a Channel Owner
**Requirements:** FR-166  |  **Actor:** ADMIN
**Entry:** Admin tab > Dashboard > Channel Administration > Channel details (approved IA area; final confirmation UI in Stage 4)
**Preconditions:** Actor is an authorized ADMIN; channel and current owner exist; membership roster is current or revalidated on submission.

**Main journey**
1. Open channel administration and select exceptional Owner Removal for the current owner.
2. Review membership roster, affected user, channel n/6 count, preserved files and consequences.
3. If other members remain, choose an eligible active member to become the successor.
4. Confirm the exceptional action; server rechecks Admin privilege, current owner, membership and successor eligibility.
5. Commit ownership transfer (if needed) and removal atomically; retain channel media and update n/6.
6. Record an audit event and notify the affected user; display the committed result.

**Alternates, failures and authorization**
- Sole owner/member: remove owner without successor; preserve channel visibility at 0/6 and all files.
- Selected successor left, membership changed, or owner changed: reject stale request; refresh roster without partial removal.
- Unauthorized requester or attempt to remove a non-owner under this exception: deny; this is NOT broad Admin member-removal authority.
- Transaction fails: owner, successor, membership and count remain consistent; no partial confirmation.
- Notification delivery fails after a successful removal: preserve committed state/audit and use safe retry of notification delivery, without repeating removal.

**Server-side enforcement:** Narrowly scoped ADMIN-only owner-removal authorization; transactional owner reassignment + membership removal with concurrency checks; durable audit and notification follow-up.
**Exit state:** Owner removed with a valid successor or an empty discoverable 0/6 channel; existing files preserved.
**Acceptance checks (approved criteria; tests not yet executed):**
- AC-ADD-FR-166-01: With remaining members, one valid owner remains after atomic transfer/removal.
- AC-ADD-FR-166-02: With sole owner, channel becomes 0/6 and retains files.
- AC-ADD-FR-166-03: Stale, conflicting, unauthorized or broader non-owner removal attempts cannot partially execute.
- AC-ADD-FR-166-04: Successful action records audit and notifies affected user.

### 2.4 ADD-FR-164-165 - Download and Export Central Media to Native Device Storage
**Requirements:** FR-164; FR-165  |  **Actor:** Currently authorized user
**Entry:** Channel library > File details/player/document viewer > Download or Export (approved IA area; destination controls in Stage 4)
**Preconditions:** User is ACTIVE and currently authorized to access/download the selected UniSpace-hosted video or document; file is available.

**Main journey**
1. Open an eligible central UniSpace file and choose Download / Export.
2. Explain that the saved device copy will be independent of UniSpace and cannot later be remotely revoked.
3. Backend verifies current file permissions and supplies a short-lived authorized download method.
4. Flutter transfers the file with progress and invokes platform-appropriate native storage/export interaction: user-accessible Android Downloads or iOS Files destination.
5. Confirm successful file write/export and allow opening the saved copy without UniSpace internet access.

**Alternates, failures and authorization**
- Membership revoked, session expired, file unavailable, or URL expired before authorized transfer: deny or request fresh authorization; do not release a new unauthorized download.
- Network interruption during transfer: indicate incomplete transfer and safe retry; do not report completed export prematurely.
- Insufficient device capacity, cancelled destination selection, unsupported destination or OS storage error: show recoverable failure and avoid false success.
- User signs out or loses channel access after a completed export: existing device-owned copy remains accessible; UniSpace does not claim to delete it.
- Google Drive external resources are excluded from this MVP central-storage journey.

**Server-side enforcement:** Current resource-level authorization before download delivery; no public R2 object exposure or perpetual URL; no claim of post-export revocation.
**Exit state:** A user-readable offline copy exists at native device storage, or the operation failed without claiming success.
**Acceptance checks (approved criteria; tests not yet executed):**
- AC-ADD-FR-164-165-01: Authorized Android/iOS export produces an independently usable offline file.
- AC-ADD-FR-164-165-02: Unauthorized, revoked, expired or unavailable-media requests are denied.
- AC-ADD-FR-164-165-03: Interruption, disk-full and OS cancellation do not display false export success.
- AC-ADD-FR-164-165-04: Sign-out or later access revocation does not falsely promise remote deletion of exported copies.

## 3. Amendments to 21 existing approved flows

These are controlled cross-references, **not 21 new user journeys**. Where v1.0 marked a decision unresolved, the specified later-approved FR now governs. FR and original UF IDs do not change.

### FR-138 - UF-CH-04
- **Amendment:** When an owner leaves/switches while members remain, require active-member successor selection and atomic transfer/departure.
- **Exception / guardrail:** No eligible successor/stale roster blocks departure; sole owner may leave 0/6.
- **Acceptance:** Owner and membership always consistent; no abandoned occupied channel.

### FR-139 - UF-CH-06; UF-ADM-05
- **Amendment:** Warn 0/6 applicants of possible ownership; assign ownership to first successfully admitted member.
- **Exception / guardrail:** Concurrent approvals cannot assign multiple owners; later requests follow owner approval.
- **Acceptance:** Exactly first admitted member becomes owner, with 1/6 shown.

### FR-140 - UF-CH-07; UF-ADM-06
- **Amendment:** Before Admin channel deletion verify 0 active members, 0 active files, 0 trashed files.
- **Exception / guardrail:** Nonzero count or stale eligibility prevents deletion.
- **Acceptance:** Deleting a channel with any Trash file is rejected.

### FR-141 - UF-CH-07; UF-ADM-06; UF-NOT-02
- **Amendment:** Show pending-request count; on eligible deletion cancel pending requests, notify applicants and audit.
- **Exception / guardrail:** Concurrent request decisions are resolved consistently; no orphaned pending request.
- **Acceptance:** All affected requests end cancelled and notification/audit actions are recorded.

### FR-142 - UF-CH-04; UF-CH-05; UF-TRASH-06; UF-VIEW-06
- **Amendment:** Leaving/removal revokes prior-channel file operations and restore rights absent independent staff authorization.
- **Exception / guardrail:** Stale links, former uploader status and cached views do not bypass current permissions.
- **Acceptance:** Former member cannot restore or manage former-channel media.

### FR-143 - UF-TRASH-03; UF-TRASH-06
- **Amendment:** Restore to the channel immediately before Trash, with destination validity and current permission checks.
- **Exception / guardrail:** Missing destination or access denied leaves file safely in Trash; no silent alternate destination.
- **Acceptance:** Restored file appears only in last channel, with preserved file identity.

### FR-144 - UF-ADM-03; UF-ADM-04; UF-PRO-02; UF-CH-04
- **Amendment:** Block student-to-staff promotion until student channel membership is resolved; owner transfers when required.
- **Exception / guardrail:** Staff-to-staff transition unaffected; demotion never auto-rejoins student channel.
- **Acceptance:** No promoted staff account retains an invalid student membership.

### FR-145 - UF-MED-01
- **Amendment:** Validate video <=1 GB; PDF <=100 MB; PPT/PPTX <=200 MB on client and server.
- **Exception / guardrail:** Oversize or spoofed-type file is rejected before publication.
- **Acceptance:** Exact limit boundary accepted; over-limit rejected without published file.

### FR-146 - UF-MED-02; UF-MED-04
- **Amendment:** Present flat channel > files organization; no user-created nested folders in MVP.
- **Exception / guardrail:** Folder-creation attempt is not available or is rejected.
- **Acceptance:** Only direct channel-file placement is supported.

### FR-147 - UF-MED-02; UF-MED-05; UF-MED-06
- **Amendment:** On upload, move and rename conflict, apply next numbered filename suffix and keep original file ID.
- **Exception / guardrail:** Simultaneous conflicts do not overwrite or create duplicate destination names.
- **Acceptance:** Both files survive, with unique names and preserved uploader identity.

### FR-148 - UF-MED-03
- **Amendment:** Persist resumable upload state across ordinary application/process termination while local source and session stay valid.
- **Exception / guardrail:** Expired session or missing source explains restart; no closed-app transfer guarantee.
- **Acceptance:** Previously completed parts can be resumed without publishing incomplete file.

### FR-149 - UF-MED-02; UF-MED-03
- **Amendment:** Limit to three active uploads per device; queue extras and advance when a slot opens while app is active.
- **Exception / guardrail:** Cancel/failure frees a slot safely; recoverable state remains when interrupted.
- **Acceptance:** Never more than three active transfers; queued item starts when eligible.

### FR-152 - UF-NOT-01; UF-NOT-07
- **Amendment:** Retain inbox notifications 90 days; individual read, mark-all-read and individual delete.
- **Exception / guardrail:** Deleting inbox item does not reverse original action or erase audit.
- **Acceptance:** Inbox state and 90-day expiry follow policy.

### FR-153 - UF-NOT-05; UF-PRO-06
- **Amendment:** Expose optional push categories; category/OS settings change push delivery, not in-app inbox persistence.
- **Exception / guardrail:** Disabled category or denied OS permission suppresses optional push only.
- **Acceptance:** All required in-app events remain accessible despite disabled push.

### FR-154 - UF-NOT-02; UF-ADM-05
- **Amendment:** Notify every active Admin of 0/6 channel join requests; use one shared pending decision.
- **Exception / guardrail:** Two Admins resolving same request cannot both succeed; role revocation blocks action.
- **Acceptance:** One decision recorded; all Admin notifications remain informative, not separate approvals.

### FR-156 - UF-AUTH-01; UF-AUTH-03
- **Amendment:** Implement 10-minute OTP validity, 60-second resend interval, five failed attempts, single use and replacement invalidation.
- **Exception / guardrail:** Expired/replayed/old/too-many-attempt OTP fails without activation/trust.
- **Acceptance:** Boundary and replay tests confirm server enforcement.

### FR-158 - UF-AUTH-02; UF-PRO-08
- **Amendment:** Device trust has no fixed expiry; ends on sign-out, revocation or security event, independent of token lifetime.
- **Exception / guardrail:** Expired session cannot access content just because device trust persists.
- **Acceptance:** Trust and session checks are separate; revoked trust requires appropriate re-verification.

### FR-159 - UF-VIEW-02; UF-VIEW-03
- **Amendment:** Save playback progress by user and video; resume partial videos, restart effectively completed videos.
- **Exception / guardrail:** Other users/videos do not inherit progress; current access rechecked.
- **Acceptance:** Resumption is user-specific and completion resets to start.

### FR-160 - UF-VIEW-02
- **Amendment:** Open video paused at stored point (or 00:00); require Play tap before playback.
- **Exception / guardrail:** Switching to another video also opens paused.
- **Acceptance:** No automatically started video on opening.

### FR-161 - UF-VIEW-02
- **Amendment:** Open in portrait; enter landscape fullscreen only by explicit action where supported, without restarting playback.
- **Exception / guardrail:** Platform rotation restrictions handled without lost playback state.
- **Acceptance:** Position/state preserved entering and leaving fullscreen.

### FR-162 - UF-VIEW-05
- **Amendment:** Attempt in-app PPT/PPTX preview; if not possible provide external open/download alternative.
- **Exception / guardrail:** Unsupported presentation never falsely appears playable; current authorization still required.
- **Acceptance:** Supported preview or usable fallback exists for eligible file.

## 4. System-level acceptance without invented UI flows

### FR-079 (MVP)
- **Rule:** Internal empty media-folder/storage-organization cleanup must never automatically delete the parent channel.
- **Verification:** After deleting or expiring every file in a channel, verify channel record remains discoverable until eligible explicit Admin deletion.

### FR-155 (MVP)
- **Rule:** Retain audit events 365 days unless overriding institutional/legal duty applies; exclude OTPs, passwords, tokens and other authentication secrets.
- **Verification:** Seed dated events and sensitive-action samples; verify 365-day retention/expiry policy and no authentication secrets in stored audit. Audit explorer UI remains post-MVP.

## 5. Deferred post-MVP flow backlog

| FR | Existing contextual flows | Deferred coverage | Scope rule |
|---|---|---|---|
| FR-150 | UF-RES-01; UF-RES-04 | My Resources vs Shared Resources and Management/Admin publishing of external read-only references. | Post-MVP; separate resource-view/publish journeys required. |
| FR-151 | UF-RES-03; UF-RES-08; UF-PRO-07 | Connect, disconnect and replace exactly one Google Drive authorization; keep bookmarks on disconnect. | Post-MVP; distinct from MVP provider identity linking. |
| FR-163 | UF-VIEW-04 | PDF text search, page jump, page indicator and per-user reading position. | Post-MVP; basic PDF preview remains in MVP. |

## 6. FR-to-flow traceability index (31 reconciliation items)

| FR ID | Release | Existing v1.0 contextual flow(s) | Addendum reference | Coverage status |
|---|---|---|---|---|
| FR-079 | MVP | N/A - system level | UF-ADD-1.1 §4 | Approved system acceptance criteria - v1.1 |
| FR-138 | MVP | UF-CH-04 | UF-ADD-1.1 §3 | Approved amendment - v1.1 |
| FR-139 | MVP | UF-CH-06; UF-ADM-05 | UF-ADD-1.1 §3 | Approved amendment - v1.1 |
| FR-140 | MVP | UF-CH-07; UF-ADM-06 | UF-ADD-1.1 §3 | Approved amendment - v1.1 |
| FR-141 | MVP | UF-CH-07; UF-ADM-06; UF-NOT-02 | UF-ADD-1.1 §3 | Approved amendment - v1.1 |
| FR-142 | MVP | UF-CH-04; UF-CH-05; UF-TRASH-06; UF-VIEW-06 | UF-ADD-1.1 §3 | Approved amendment - v1.1 |
| FR-143 | MVP | UF-TRASH-03; UF-TRASH-06 | UF-ADD-1.1 §3 | Approved amendment - v1.1 |
| FR-144 | MVP | UF-ADM-03; UF-ADM-04; UF-PRO-02; UF-CH-04 | UF-ADD-1.1 §3 | Approved amendment - v1.1 |
| FR-145 | MVP | UF-MED-01 | UF-ADD-1.1 §3 | Approved amendment - v1.1 |
| FR-146 | MVP | UF-MED-02; UF-MED-04 | UF-ADD-1.1 §3 | Approved amendment - v1.1 |
| FR-147 | MVP | UF-MED-02; UF-MED-05; UF-MED-06 | UF-ADD-1.1 §3 | Approved amendment - v1.1 |
| FR-148 | MVP | UF-MED-03 | UF-ADD-1.1 §3 | Approved amendment - v1.1 |
| FR-149 | MVP | UF-MED-02; UF-MED-03 | UF-ADD-1.1 §3 | Approved amendment - v1.1 |
| FR-150 | Post-MVP | UF-RES-01; UF-RES-04 | UF-ADD-1.1 §5 | Deferred - backlog documented |
| FR-151 | Post-MVP | UF-RES-03; UF-RES-08; UF-PRO-07 | UF-ADD-1.1 §5 | Deferred - backlog documented |
| FR-152 | MVP | UF-NOT-01; UF-NOT-07 | UF-ADD-1.1 §3 | Approved amendment - v1.1 |
| FR-153 | MVP | UF-NOT-05; UF-PRO-06 | UF-ADD-1.1 §3 | Approved amendment - v1.1 |
| FR-154 | MVP | UF-NOT-02; UF-ADM-05 | UF-ADD-1.1 §3 | Approved amendment - v1.1 |
| FR-155 | MVP | N/A - system level | UF-ADD-1.1 §4 | Approved system acceptance criteria - v1.1 |
| FR-156 | MVP | UF-AUTH-01; UF-AUTH-03 | UF-ADD-1.1 §3 | Approved amendment - v1.1 |
| FR-157 | MVP | See original contextual link in B0-01 v0.2 | UF-ADD-1.1 §2 / ADD-FR-157 | Approved addendum journey - v1.1 |
| FR-158 | MVP | UF-AUTH-02; UF-PRO-08 | UF-ADD-1.1 §3 | Approved amendment - v1.1 |
| FR-159 | MVP | UF-VIEW-02; UF-VIEW-03 | UF-ADD-1.1 §3 | Approved amendment - v1.1 |
| FR-160 | MVP | UF-VIEW-02 | UF-ADD-1.1 §3 | Approved amendment - v1.1 |
| FR-161 | MVP | UF-VIEW-02 | UF-ADD-1.1 §3 | Approved amendment - v1.1 |
| FR-162 | MVP | UF-VIEW-05 | UF-ADD-1.1 §3 | Approved amendment - v1.1 |
| FR-163 | Post-MVP | UF-VIEW-04 | UF-ADD-1.1 §5 | Deferred - backlog documented |
| FR-164 | MVP | See original contextual link in B0-01 v0.2 | UF-ADD-1.1 §2 / ADD-FR-164-165 | Approved addendum journey - v1.1 |
| FR-165 | MVP | See original contextual link in B0-01 v0.2 | UF-ADD-1.1 §2 / ADD-FR-164-165 | Approved addendum journey - v1.1 |
| FR-166 | MVP | See original contextual link in B0-01 v0.2 | UF-ADD-1.1 §2 / ADD-FR-166 | Approved addendum journey - v1.1 |
| FR-167 | MVP | See original contextual link in B0-01 v0.2 | UF-ADD-1.1 §2 / ADD-FR-167 | Approved addendum journey - v1.1 |

**Traceability interpretation:** The 67 original flow definitions and their original FR Trace lines remain approved. The v1.1 addendum journeys and amendments are approved and separately traceable in B0-01 v0.2. The four ADD-FR identifiers do not renumber the 67 v1.0 IDs. Technical tests and screen/component mappings will be completed in later implementation and Stage 4.

## 7. Change log, conflicts and Stage 4 decisions

| Item | Controlled resolution |
|---|---|
| v1.0 UF-CH-04 owner-exit "unresolved" | FR-138 successor selection and atomic owner exit/switch now govern. |
| v1.0 UF-CH-06 empty-channel ownership "undecided" | FR-139 first admitted member becomes owner. |
| v1.0 UF-CH-07 Trash/pending requests "undecided" | FR-140/141 require zero Trash files and cancellation of requests on deletion. |
| v1.0 UF-TRASH-03/06 former-member/restore questions | FR-142/143 require current authorization and restore to last channel. |
| v1.0 Admin owner-removal permission "not set" | FR-166 is narrow exceptional Admin owner removal, not generic Admin removal. |
| v1.0 role-change membership question | FR-144 resolves student channel membership before staff promotion. |
| Google Drive versus provider linking | FR-167 is MVP sign-in linking; FR-150/151 are post-MVP external Drive functions. |

**Stage 4 presentation decisions remaining (NOT new product rules):** exact UI layout of provider linking within Connected Accounts; Admin Institutional Settings forms; confirmation/error components for exceptional owner removal; Android/iOS export feedback and destination affordances; inactive MVP Resources tab experience. Do not silently finalize these in this flow addendum.

### Finalization checklist
- [x] 167 FRs unchanged (150 MVP / 17 post-MVP)
- [x] 67 original approved flows preserved
- [x] Four approved journeys cover five MVP FRs: 157, 164, 165, 166 and 167
- [x] All 21 supplemental FR mappings documented
- [x] FR-079 and FR-155 system checks documented
- [x] FR-150, FR-151 and FR-163 remain post-MVP
- [x] No new security, business or permission policy invented
- [x] Approved as the controlled v1.1 addendum; implementation/test evidence is still pending

**Final status:** Approved controlled User Flows Addendum v1.1, 22 November 2026. Original v1.0 remains intact for historical traceability; Stage 4 visual design and engineering verification are not implied approved.
