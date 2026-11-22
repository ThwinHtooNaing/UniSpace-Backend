# UniSpace Functional Requirements Addendum v1 0

> **Source:** `UniSpace_Functional_Requirements_Addendum_v1_0(2).pdf`  
> **Original pages:** 6  
> **Document classification:** requirements  
> **Conversion:** text-only Markdown transcription; original PDF remains the visual/layout record.  
> **Status note:** Preserve original PDF approval/status wording; later addenda govern newer decisions.

---


<!-- Source PDF page 01 -->

FUNCTIONAL REQUIREMENTS ADDENDUM V1.0

**UNISPACE / CONTROLLED REQUIREMENTS**

## Functional Requirements

## Addendum v1.0

Approved additional functionality | Flutter for Android & iOS Controlled extension to Functional Requirements v1.5

| 137 | +30 | 167 |
| --- | --- | --- |
| Original FRs | New approved FRs | Total approved FRs |

**DOCUMENT STATUS — APPROVED**

Approved by user in the UniSpace Stage 3.5 conversation on 20 November 2026. This supplement does not replace or renumber FR-001 through FR-137.

Release allocation: 150 MVP (123 baseline + 27 new) and 17 post-MVP (14 baseline + 3 new).

Supporting references: UniSpace FR v1.5; Information Architecture v1.0 and controlled addendum; 67 User Flows v1.0; Stage 3.5 approved product decisions; NFR v1.0.

UNISPACE / CONTROLLED ADDENDUM / 20 NOVEMBER 2026


<!-- Source PDF page 02 -->

## 01 Scope & change control

This addendum contains exactly 30 new approved functional requirements, FR-138 through FR-167. The original 137 approved statements in v1.5 remain intact. Where an approved Stage 3.5 decision clarifies or extends previous behavior, the new statement governs the additional behavior while the original remains the historical baseline.

| Release group | Baseline FRs | New addendum FRs | Total |
| --- | --- | --- | --- |
| MVP | 123 | 27 | 150 |
| Post-MVP | 14 | 3 | 17 |
| Total | 137 | 30 | 167 |

Post-MVP additions: FR-150, FR-151 and FR-163. All other new requirements are designated MVP. Original post-MVP baseline:

FR-090–100 and FR-105–107.

### Requirement group index

| Sectio | Topic | New FRs n |
| --- | --- | --- |
| A | Channel ownership & membership | FR-138–144 |
| B | Uploads & file management | FR-145–149 |
| C | Google Drive resources (post-MVP) | FR-150–151 |
| D | Notifications, identity & audit | FR-152–158 |
| E | Media viewing | FR-159–163 |
| F | Offline exports | FR-164–165 |
| G | Exceptional administration | FR-166 |
| H | Provider linking | FR-167 |

Implementation safeguard The addendum approves product behavior, not specific endpoints, database tables, storage vendors, credential lifetimes or UI widget implementations. Engineering design must preserve the approved permissions and release gates.


<!-- Source PDF page 03 -->

## A Channels & Membership

Original context: FR-014–035, FR-110–111; approved Stage 3.5 ownership, staff-transition, deletion and restoration decisions.

- **FR-138** — — Ownership Transfer [MVP] A Channel Owner leaving or switching channels must select an active-member successor when other members remain. Ownership transfer and departure must be atomic. The final member may leave without assigning a successor.

- **FR-139** — — Empty Channel Ownership [MVP] The first successfully approved member joining an empty channel automatically becomes its owner. The student must be informed before requesting membership.

- **FR-140** — — Channel Deletion Eligibility [MVP] Admin may delete a channel only when it has zero members, zero active media files, and zero files in Trash.

- **FR-141** — — Pending Requests During Deletion [MVP] Deleting an eligible channel must cancel its pending membership requests, notify affected users, and record the operation.

- **FR-142** — — Former Member Access [MVP] Leaving or being removed from a channel revokes its file-management and restoration privileges unless another authorized system role provides access.

- **FR-143** — — File Restoration Destination [MVP] Restoring a trashed file must return it to its last channel before deletion, subject to authorization and destination validity.

- **FR-144** — — Staff Promotion & Membership [MVP] A student must resolve existing channel membership, including necessary ownership transfer, before promotion to a staff role. Demotion does not automatically restore membership.

## B Uploads & File Management

Original context: FR-036–055, FR-067–079. Files remain centrally managed and retain their original uploader.

- **FR-145** — — Document Size Limits [MVP] PDF uploads are limited to 100 MB and PPT/PPTX uploads to 200 MB per file. The existing 1 GB video limit remains unchanged.

- **FR-146** — — Channel Folder Structure [MVP] Files are organized directly under channels. User-created subfolders are not supported in the MVP.

- **FR-147** — — Filename Conflicts [MVP] Upload, move, and rename operations must resolve filename conflicts by assigning a unique numbered suffix without overwriting existing files.

- **FR-148** — — Persistent Upload Recovery [MVP] Interrupted uploads must retain recoverable transfer state across ordinary app termination when the source file and upload session remain valid.


<!-- Source PDF page 04 -->

- **FR-149** — — Upload Concurrency [MVP] UniSpace must support a maximum of three active uploads per device, queue additional files, and start queued operations when capacity becomes available while the app is active.

## C Google Drive Resources

Post-MVP; extends FR-090–100. Resource references remain external, permission-controlled and read-only.

- **FR-150** — — Personal & Shared Resources [Post-MVP] Users may maintain personal Google Drive bookmarks. Authorized Management/Admin users may publish shared resource references subject to Google Drive permissions.

- **FR-151** — — Connected Google Drive Accounts [Post-MVP] Each UniSpace user may connect, disconnect, or replace one Google Drive account. Disconnecting must remove stored authorization credentials without automatically deleting bookmarks.

## D Notifications, Audit & Account Security

Original context: FR-006–013, FR-080–089, FR-107–108, FR-113–137.

- **FR-152** — — Notification Retention [MVP] The in-app inbox must retain notifications for 90 days and support individual read, mark-all-read, and individual deletion.

- **FR-153** — — Push Preferences [MVP] Users may configure optional push notifications by category without disabling their in-app notification inbox.

- **FR-154** — — Empty-Channel Admin Notifications [MVP] All active Admins must receive in-app notifications for empty-channel membership requests. Requests must be processed at most once through a shared pending queue.

- **FR-155** — — Audit Retention [MVP] Audit events must be retained for 365 days unless applicable institutional or legal obligations require otherwise. Audit records must not expose authentication secrets.

- **FR-156** — — OTP Security [MVP] Email OTPs expire after 10 minutes, permit resending after 60 seconds, and become invalid after five incorrect attempts. Codes are single-use; replacement codes invalidate earlier codes.

- **FR-157** — — Institutional Validation Configuration [MVP] Authorized Admins may configure Student ID validation patterns and permitted university email domains. Configuration changes must not automatically invalidate registered accounts.

- **FR-158** — — Trusted Device Policy [MVP] Trusted-device status persists without a fixed expiration until sign-out, explicit revocation, or a security event. Device trust does not override session or token expiration.


<!-- Source PDF page 05 -->

## E Media Viewing

Original context: FR-060–066; FR-163 enhanced PDF capabilities are post-MVP.

- **FR-159** — — Playback Position [MVP] UniSpace must remember playback position by user and video, resume partially watched videos, and restart effectively completed videos from the beginning.

- **FR-160** — — Video Autoplay [MVP] Videos must open paused and require explicit user interaction to begin playback.

- **FR-161** — — Fullscreen Orientation [MVP] Video playback initially opens in portrait. Users may enter landscape fullscreen where supported without restarting playback.

- **FR-162** — — PowerPoint Preview Fallback [MVP] UniSpace must attempt supported in-app presentation previews and provide an external viewer or download alternative when preview is unavailable.

- **FR-163** — — Enhanced PDF Reading [Post-MVP] The PDF viewer must support searchable-text search, page jumping, page indicators, and per-user reading-position memory. Basic PDF preview remains part of the MVP.

## F Offline Media Downloads

Explicit new central-UniSpace media export functionality, distinct from post-MVP Google Drive resources.

- **FR-164** — — Offline Media Downloads [MVP] Authorized users may download supported videos and documents for offline access.

- **FR-165** — — Native Device Storage [MVP] Downloaded files must be exportable to user-accessible Android/iOS storage, remain independent of UniSpace, and require authorization before export. UniSpace cannot guarantee revocation of exported copies.

## G Exceptional Administration

Narrow Admin permission exception, required to reconcile the earlier FR v1.5 permission table.

- **FR-166** — — Exceptional Owner Removal [MVP] Admin may forcibly remove a Channel Owner, assigning an active-member successor when necessary. Ownership transfer and removal must be atomic, preserve existing files, notify the affected user, and create an audit record.


<!-- Source PDF page 06 -->

## H Authentication Provider Linking

MVP Google/Apple sign-in identity linking is separate from post-MVP Google Drive account authorization.

- **FR-167** — — Google & Apple Account Linking [MVP] Authenticated users may securely link both Google and Apple sign-in identities to one UniSpace account. Linking requires proof of provider ownership, preserves account uniqueness, and prohibits automatic merging based only on matching email addresses.

## 02 Approved policy interpretation notes

The requirements above reproduce the accepted controlled addendum. The following details summarize already-approved Stage 3.5 decisions relevant to implementation and do not add new requirement IDs.

- Empty-channel owner: the first successfully admitted student to a 0/6 channel becomes owner; subsequent requests use owner approval.

- Exceptional owner removal: Admin selects an active successor if one exists; if the owner is the sole member, removal leaves the channel at 0/6. The Admin exception does not grant unrestricted removal of any member.

- Uploads: duplicate filename collisions use numeric suffixes, including on move and rename; file access is governed by current permissions.

- Notifications: push is optional and subject to device permission; the in-app notification inbox remains available.

- Native exports: files saved into ordinary Android Downloads / iOS Files become user-controlled copies. Revocation of online channel access cannot reliably revoke them.

- Account linking: an identity is linked only after provider-ownership verification; email equality alone never merges accounts.

### Acceptance and traceability

Before implementation, map each addendum FR to one or more approved user flows, the relevant IA surface, backend enforcement rule, API contract and verification test. NFRs apply across the implementation; the approved 42 NFRs are specified separately.

Cross-document references: UniSpace Information Architecture Controlled Addendum v1.0; UniSpace Product Decisions & MVP Scope (Stage 3.5); UniSpace NFR v1.0.
