# UniSpace Functional Requirements v1 5

> **Source:** `UniSpace_Functional_Requirements_v1_5(3).pdf`  
> **Original pages:** 17  
> **Document classification:** requirements  
> **Conversion:** text-only Markdown transcription; original PDF remains the visual/layout record.  
> **Status note:** Preserve original PDF approval/status wording; later addenda govern newer decisions.

---


<!-- Source PDF page 01 -->

FUNCTIONAL REQUIREMENTS / v1.5

APPROVED SPECIFICATION / CONTROLLED REVISION

## UniSpace

## Functional Requirements Specification

Flutter Mobile Application | Android & iOS | Version 1.5

Version 1.5 adds student onboarding, university email verification, trusted-device sign-in, and interrupted-registration recovery. All other previously approved functional requirements remain unchanged.

| 137 | 25 | 2 |
| --- | --- | --- |
| Total requirements | New: FR-113 to FR-137 | Clarified: FR-004 and FR-006 |

Revision controls

- No existing requirement IDs have been renumbered.

- FR-001 to FR-112 are preserved verbatim except approved edits to FR-004 and FR-006.

- FR-113 to FR-137 record the 25 approved authentication and onboarding requirements.

- Four system roles remain: STUDENT, SUB_MANAGEMENT, MANAGEMENT and ADMIN. No SUPER_ADMIN.


<!-- Source PDF page 02 -->

## 00 Change control & scope

Approved v1.5 amendment

Reference | Action / approved change

- **FR-004** — Provisional account after first sign-in; activate only after completed registration and university email verification.

- **FR-006** — Completed users restore valid trusted-device sessions; incomplete users resume according to recovery policy.

| FR-113–124 | New: profile details, Student ID, organizational email and verification, account activation and protected-feature restrictions. |
| --- | --- |
| FR-125–131 | New: persistent sign-in on trusted devices and additional verification for new or untrusted devices. |
| FR-132–137 | New: incomplete-registration preservation, correction, restart after reinstall, and provisional-account reconciliation. |

Scope preserved

Channel membership and deletion, role permissions, video and document uploads, media library, Trash and recovery, notifications, read-only Google Drive resources, and all Flutter-based staff and Admin functions are unchanged from v1.4. The complete requirement statements follow.

Interpretation

A channel is the logical top-level folder containing members and media. Channel ownership is a channel-specific responsibility, not an additional system role. This document records functional behavior; it does not by itself finalize visual UI design or implementation choices.

Requirement index


<!-- Source PDF page 03 -->

| ID range | Functional area | Count |
| --- | --- | --- |
| FR-001–013 | Authentication, user profiles & roles | 13 |
| FR-113–137 | Authentication & registration amendment | 25 |
| FR-014–035, 110–111 | Channels & membership | 24 |
| FR-036–055 | Central media & file uploads | 20 |
| FR-056–066 | Channel library, playback & previews | 11 |
| FR-067–079 | File actions, Trash & recovery | 13 |
| FR-080–089 | User notifications | 10 |
| FR-090–100 | Google Drive teaching resources | 11 |
| FR-101–109, 112 | Staff, administration & audit | 10 |


<!-- Source PDF page 04 -->

## 01 Authentication, user profiles & roles

ID | Functional requirement

- **FR-001** — All end-user functions, including staff and administration, are available through the Flutter mobile app for Android and iOS; no separate web dashboard is required.

- **FR-002** — Users can sign in with a Google account.

- **FR-003** — Users can sign in with an Apple account.

- **FR-004** — Following initial Google or Apple authentication, the system creates a provisional user account with the STUDENT role unless the identity has been explicitly provisioned as staff. The account becomes ACTIVE only after required registration and university email verification succeed.

- **FR-005** — The system has an initially provisioned ADMIN account; ordinary registration cannot grant ADMIN privileges.

- **FR-006** — Users with completed registration and valid trusted-device sessions can resume UniSpace without signing in again. Users with incomplete registration resume onboarding according to the registration recovery policy.

- **FR-007** — Users can explicitly sign out.

- **FR-008** — Users can view their own profile and linked sign-in identity.

- **FR-009** — Users can view their current system role and active channel membership.

- **FR-010** — A STUDENT may request promotion to SUB_MANAGEMENT or MANAGEMENT from within the app.

- **FR-011** — ADMIN can review, approve or reject role-promotion requests.

- **FR-012** — ADMIN can assign, change or revoke staff roles, subject to preserving at least one functioning administrator.

- **FR-013** — Role and permission changes take effect for subsequent protected actions without requiring creation of a new account.


<!-- Source PDF page 05 -->

## 01A Student registration (new in v1.5)

ID | Functional requirement

- **FR-113** — First-time users authenticate using Google or Apple before starting UniSpace registration.

- **FR-114** — The system retrieves first and last names from the authentication provider when available.

- **FR-115** — First and last names are displayed in editable fields. Missing names must be entered manually.

- **FR-116** — Users must provide their Student ID during registration.

- **FR-117** — The system validates Student ID format and uniqueness before registration can continue.

- **FR-118** — Both Google and Apple users must provide a university organizational email address.

- **FR-119** — The system validates the organizational email against permitted university email domains.

- **FR-120** — The system sends a verification code to the provided university email address.

- **FR-121** — Users must enter a valid verification code to confirm ownership of that email address.

- **FR-122** — Users can request another code when the previous code expires or is not received, subject to security limits.

- **FR-123** — Registration is completed only after the required profile information and university email verification succeed.

- **FR-124** — Users with incomplete registration cannot access protected UniSpace features.


<!-- Source PDF page 06 -->

## 01B Persistent login & device verification (new)

ID | Functional requirement

- **FR-125** — Successfully registered users can remain signed in on their trusted devices while their sessions remain valid.

- **FR-126** — Reopening UniSpace on the same device with a valid trusted session navigates directly to Home.

- **FR-127** — Signing in from a new or untrusted device requires Google or Apple authentication.

- **FR-128** — New or untrusted devices require an additional verification code sent to the user's previously verified university email.

- **FR-129** — After successful device verification, the system can establish a persistent trusted session on that device.

- **FR-130** — Expired, invalid, or revoked sessions must undergo the appropriate reauthentication process.

- **FR-131** — Users can explicitly sign out, ending their current authenticated session.

## 01C Incomplete registration & recovery (new)

ID | Functional requirement

- **FR-132** — The system preserves incomplete registration progress during temporary interruptions, including backgrounding, navigation, and application restarts.

- **FR-133** — Users can resume registration from the last appropriate onboarding step following an ordinary application restart or operating-system process termination.

- **FR-134** — Invalid or duplicate Student IDs and other validation errors must not erase unrelated valid form inputs.

- **FR-135** — Following an uninstall and reinstall during incomplete registration, users must authenticate again with Google or Apple and restart onboarding from the first registration step.

- **FR-136** — Restarting onboarding must safely reuse or reconcile an existing provisional account without creating duplicate account records.

- **FR-137** — Users who have already completed registration must not be required to repeat student onboarding when signing in again.


<!-- Source PDF page 07 -->

## 01D Account registration states

| State | Meaning |
| --- | --- |
| PENDING_PROFILE | Provider authentication succeeded; profile/Student ID is not yet complete. |
| PENDING_EMAIL_VERIFICATION | Profile has been provided; university email verification is pending. |
| ACTIVE | Registration and university email verification are complete. |
| DISABLED | Account access is disabled. |

Protected UniSpace features must remain unavailable until the account is ACTIVE. This is a backend authorization requirement, not only a Flutter navigation rule.

User journey summary

- First-time user: Google/Apple sign-in → editable name and Student ID → organizational email → verification code → account ACTIVE → Home.

- Same trusted device: restore valid session → Home; invalid or revoked session → reauthenticate.

- New or untrusted device: Google/Apple sign-in → verification code to previously verified university email → trusted session → Home.

- Interrupted registration: restore appropriate step after ordinary restart; following reinstall, reauthenticate and restart onboarding without duplicating provisional accounts.


<!-- Source PDF page 08 -->

## 02 Channels & membership

ID | Functional requirement

- **FR-014** — A STUDENT who does not belong to a channel can create one new channel.

- **FR-015** — Channel names must be unique across active and empty channels, ignoring case and surrounding whitespace; duplicate-name creation is rejected.

- **FR-016** — The channel creator automatically joins as a member and becomes the channel's initial owner.

- **FR-017** — Channel ownership is a channel-specific permission, not a fifth system role; the system roles remain STUDENT, SUB_MANAGEMENT, MANAGEMENT and ADMIN.

- **FR-018** — Each student may have only one active channel membership at a time.

- **FR-019** — Each channel may have no more than six student members, including its owner when that owner is a member.

- **FR-020** — Students can browse or find existing channels that accept membership requests.

- **FR-021** — Students can submit a request to join a channel; submission does not grant immediate access.

- **FR-022** — A channel owner can view pending requests for their channel.

- **FR-023** — A channel owner can approve or reject membership requests for their channel while the channel has an active owner; empty-channel approvals are handled by ADMIN (FR-110).

- **FR-024** — After approval, the system adds the student to the channel only if the membership and six-member limits still allow it.

- **FR-025** — The system prevents duplicate active memberships and duplicate pending requests to the same channel.

- **FR-026** — Students can leave their current channel voluntarily.

- **FR-027** — Students can request to switch to another channel; approval is required from the destination channel owner, or from ADMIN when that channel has no members.

- **FR-028** — While a switch request is pending, the student retains their existing membership; an approved switch changes membership without ever creating two active memberships.

- **FR-029** — Channel owners can remove members from their channel.

- **FR-030** — Users who leave or are removed lose access to their former channel content unless their system role independently grants broader access.

- **FR-031** — Authorized users can view channel information, membership roster and member count (for example, 4/6).

- **FR-032** — Staff roles can browse all channels, consistent with their role permissions.


<!-- Source PDF page 09 -->

ID | Functional requirement

- **FR-033** — A channel remains recorded, discoverable and available for new join requests after its last member leaves; reaching zero members does not itself delete the channel.

- **FR-034** — Channels cannot be archived. Only ADMIN can manually delete a channel, and only when it has zero active members AND zero media files.

- **FR-035** — Only ADMIN can rename an existing channel; renaming cannot create a duplicate channel name.

- **FR-110** — ADMIN can view, approve or reject membership requests for channels with zero active members, allowing a previously empty channel to be joined again.

- **FR-111** — The system never deletes channels automatically; only an explicit ADMIN deletion action can remove an eligible channel.


<!-- Source PDF page 10 -->

## 03 Central media & file uploads

ID | Functional requirement

- **FR-036** — Authorized users can select and upload files from their mobile devices into a channel.

- **FR-037** — The system accepts supported video recordings commonly produced by iPhone and Android devices, including compatible MP4 and MOV files.

- **FR-038** — The system accepts PDF documents.

- **FR-039** — The system accepts PowerPoint presentation files (PPT and PPTX).

- **FR-040** — The system rejects prohibited executable files, including EXE files.

- **FR-041** — The system checks uploaded content for malicious or unsafe files and rejects content identified as malware before it becomes available to other users.

- **FR-042** — The maximum permitted size for a single uploaded video is 1 GB.

- **FR-043** — The application does not impose a storage quota per channel.

- **FR-044** — The app presents each channel as a logical folder containing its uploaded files; those files are held in one centrally managed application storage system.

- **FR-045** — Each uploaded item is associated with a channel and its original uploader.

- **FR-046** — The original uploader remains recorded as the item owner unless an explicit future policy changes this rule.

- **FR-047** — Uploads do not require moderator or owner content approval; eligible items become visible once upload and safety checks succeed.

- **FR-048** — The system maintains upload states such as pending, uploading, verifying, ready and failed.

- **FR-049** — Before starting an upload, the system validates the applicable file category, permitted size and destination-channel access.

- **FR-050** — Users can view upload progress for ongoing transfers.

- **FR-051** — Users can cancel an in-progress upload.

- **FR-052** — Users can retry interrupted or failed uploads when the operation can safely be resumed.

- **FR-053** — Large video uploads can use a resumable or multipart transfer flow to recover from mobile network interruptions.

- **FR-054** — The system verifies successful completion before making an uploaded item available.

- **FR-055** — Incomplete, rejected or failed uploads are not shown as successfully published content.


<!-- Source PDF page 11 -->

## 04 Channel library, playback & previews

ID | Functional requirement

- **FR-056** — Users can list files and media in channels they are authorized to access.

- **FR-057** — Users can search files by available metadata such as title.

- **FR-058** — Users can sort channel items by available fields such as upload date, name or file size.

- **FR-059** — Each file entry displays available metadata, including title, uploader, upload date and size; video duration is shown when available.

- **FR-060** — Users can open and play supported videos directly within the Flutter app.

- **FR-061** — The video player supports pause, resume, seeking and full-screen playback.

- **FR-062** — Playback presents understandable loading, buffering and playback-error states.

- **FR-063** — When an authorized playback link expires, the app can request renewed access and resume where feasible.

- **FR-064** — Users can preview supported PDF files inside the app.

- **FR-065** — Users can preview supported PowerPoint files inside the app where a compatible preview mechanism exists; unsupported content shows an explanatory message.

- **FR-066** — The app displays a clear message for unsupported formats, unavailable objects and access-denied items.


<!-- Source PDF page 12 -->

## 05 File actions, Trash & recovery

ID | Functional requirement

- **FR-067** — Users with edit rights can rename uploaded items; renaming changes their displayed title.

- **FR-068** — MANAGEMENT and ADMIN can move an uploaded item between channels without changing its original uploader.

- **FR-069** — An authorized user can move an uploaded item to Trash instead of immediately destroying it.

- **FR-070** — An authorized user can choose permanent deletion directly, bypassing Trash and recovery.

- **FR-071** — Authorized users can access a Trash view containing items they are permitted to manage.

- **FR-072** — An authorized user can restore a trashed item during its retention period, up to 30 days after it entered Trash.

- **FR-073** — The system permanently removes items from Trash no later than 30 days after their trash date.

- **FR-074** — An authorized user can permanently delete an item from Trash before the 30-day limit.

- **FR-075** — Trashed items are hidden from normal channel libraries and playback.

- **FR-076** — Restored items retain their original uploader and are returned to an accessible valid channel location.

- **FR-077** — Permanently deleted items cannot be recovered through the normal app recovery flow.

- **FR-078** — The same item-ownership and management permission rules apply to rename, delete, restore and permanent deletion.

- **FR-079** — The system cleans up empty media-folder records or storage organization artifacts when applicable, but this cleanup must never delete a channel; channel deletion follows FR-034.


<!-- Source PDF page 13 -->

## 06 User notifications

ID | Functional requirement

- **FR-080** — Channel owners receive a notification when a student requests to join their channel.

- **FR-081** — Students receive a notification when their channel join request is approved or rejected.

- **FR-082** — Members receive a notification when they are removed from a channel.

- **FR-083** — Students receive a notification when a channel switch has been approved and completed.

- **FR-084** — ADMIN receives notifications about new SUB_MANAGEMENT and MANAGEMENT promotion requests.

- **FR-085** — Requesters receive a notification when a role-promotion request is approved or rejected.

- **FR-086** — Users receive notifications about important upload outcomes, including completion and failure.

- **FR-087** — Users can view an in-app notification inbox, including read and unread status.

- **FR-088** — The application can deliver device push notifications when notification permission and device delivery are available.

- **FR-089** — Notification links open the relevant in-app destination only if the user still has permission to access it.


<!-- Source PDF page 14 -->

## 07 Google Drive teaching resources (read-only)

ID | Functional requirement

- **FR-090** — Users can save a link to a Google Drive teaching-resource folder shared with anyone who has the link.

- **FR-091** — Users can access approved Google Drive folders restricted to an authorized Google account, after completing the needed Google authorization.

- **FR-092** — The app checks the availability and permissions of a saved Drive resource before attempting to display its contents.

- **FR-093** — Users can bookmark accessible Google Drive folders.

- **FR-094** — Users can browse accessible files within bookmarked teaching-resource folders.

- **FR-095** — Users can refresh a bookmarked folder to see its latest accessible contents.

- **FR-096** — Users can save and reopen selected individual Google Drive files.

- **FR-097** — Users can view supported Drive PDFs, images, presentations and videos inside UniSpace when permissions and preview support allow.

- **FR-098** — Users can remove their saved Google Drive bookmarks.

- **FR-099** — The app shows clear errors for access denied, removed resources, unsupported previews and expired Google authorization.

- **FR-100** — Google Drive resources remain external and read-only: UniSpace does not edit, delete, upload or automatically copy them into central application storage.


<!-- Source PDF page 15 -->

## 08 Staff, administration & audit

ID | Functional requirement

- **FR-101** — SUB_MANAGEMENT can view and upload content in any channel but can rename, trash or permanently delete only items they uploaded.

- **FR-102** — MANAGEMENT can view, upload, rename, move, trash and permanently delete items across all channels.

- **FR-103** — ADMIN has MANAGEMENT-level file permissions and can also rename channels, delete eligible channels under FR-034, handle join requests for empty channels and manage staff role assignments.

- **FR-104** — ADMIN can view a read-only directory of registered user accounts and their assigned roles.

- **FR-105** — ADMIN can view application-health and operational-status information within the Flutter app.

- **FR-106** — ADMIN can view aggregate channel membership, file counts and central-storage usage.

- **FR-107** — ADMIN can search or inspect audit records for important account, membership and file operations.

- **FR-108** — The system records audit entries for role changes, join decisions, member removals, uploads, file moves, renames, restores, file deletions and channel deletions.

- **FR-109** — User account editing or deletion is not available as a general administrative function; changing roles is the defined exception.

- **FR-112** — An existing ADMIN can promote a registered user to ADMIN or revoke another ADMIN's ADMIN privileges. The system must retain at least one active ADMIN at all times and record every such role change in the audit log.

## 09 Access-control summary

Channel owner is a channel-level permission; it is not a fifth system role. The table summarizes approved behavior without creating new requirements.

| Capability | Student | Owner* | Sub-mgmt | Mgmt | Admin |
| --- | --- | --- | --- | --- | --- |
| Create channel | Yes** | Yes** | — | — | — |
| Request join / switch | Yes | Yes | — | — | — |
| Approve occupied-channel joins | No | Own | No | No | — |
| Approve empty-channel joins | No | No | No | No | Yes |
| Remove channel members | No | Own | No | No | Not set |
| View channel media | Own | Own | All | All | All |


<!-- Source PDF page 16 -->

| Capability | Student | Owner* | Sub-mgmt | Mgmt | Admin |
| --- | --- | --- | --- | --- | --- |
| Upload to channel | Own | Own | All | All | All |
| Manage own uploaded files | Yes | Yes | Yes | Yes | Yes |
| Manage others' uploaded files | No | No | No | Yes | Yes |
| Move media between channels | No | No | No | Yes | Yes |
| Rename channel | No | No | No | No | Yes |
| Delete eligible channel | No | No | No | No | If 0/0 |
| Approve role-promotion requests | No | No | No | No | Yes |
| View accounts and system audit | No | No | No | No | Yes |
| Grant / revoke ADMIN role | No | No | No | No | Yes |

* Owner is a channel relationship. ** Students can create only if not already a channel member. Own = authorized own channel or upload. All = all channels for permitted staff actions. If 0/0 = zero active members and zero media files. Not set = permission not yet approved.

## 10 Open decisions (not approved requirements)

The following topics remain decisions for later discussion and do not change the 137 approved functional requirements.

| Topic | Decision still needed |
| --- | --- |
| Owner departure with | Ownership transfer when the current channel owner leaves or switches |
| remaining members | while other members remain. |
| Reactivating an empty | Whether the first newly approved member becomes owner automatically or |
| channel | Admin assigns one. |
| Trash versus channel | Whether files in Trash count as media for channel deletion eligibility. deletion |
| Pending requests when | How pending join or switch requests are handled if Admin deletes an |
| deleting | eligible channel. |
| Non-video file limits | Maximum file size for PDFs and PowerPoint files. |
| Nested folders | Whether users may create additional media folders beneath a channel. |
| Notification preferences | Push notification opt-out and channel-specific notification controls. |
| Google Drive sharing | Who may publish shared teaching-resource bookmarks globally. |
| Identity linking | Whether one UniSpace account can link Google and Apple sign-in identities. |


<!-- Source PDF page 17 -->

| Topic | Decision still needed |
| --- | --- |
| Student identity validation | The exact Student ID format and accepted university email domains, plus whether directory verification is available. |
| Verification controls | Code expiry, resend cooldowns, rate limits and trusted-session expiration policy. |

Version basis

Based on the approved UniSpace Functional Requirements v1.4 (112 requirements). The authentication and registration amendment was approved on 17 November 2026. This v1.5 document incorporates only that approved amendment, preserving all other requirements.
