# UniSpace Information Architecture v1 0

> **Source:** `UniSpace_Information_Architecture_v1_0.pdf`  
> **Original pages:** 16  
> **Document classification:** requirements  
> **Conversion:** text-only Markdown transcription; original PDF remains the visual/layout record.  
> **Status note:** Preserve original PDF approval/status wording; later addenda govern newer decisions.

---


<!-- Source PDF page 01 -->

**MOBILE PRODUCT / STRUCTURE & NAVIGATION**

## UniSpace

## Information Architecture

## Revised Draft v1.0

Flutter mobile · Android and iOS · 17 November 2026

Purpose Define the complete mobile navigation structure, screen responsibilities, route access, and entry/return behavior for UniSpace. This document refines the prior IA discussion; it does not modify the 137 approved functional requirements.

| 137 | 4 / 5 | 37 | 5 |
| --- | --- | --- | --- |
| Approved FRs | Bottom tabs | Core route entries | Proposed settings surfaces |

Document status: IA design proposal for review. Source of functional truth: UniSpace Functional Requirements v1.5. Brand references: UniSpace brand guidelines board (teal, mint, Inter). Screen count counts navigational destinations and screen responsibilities, not necessarily unique page widgets. Some destinations may ultimately be dialogs, sheets, nested tabs, or external authorization surfaces.


<!-- Source PDF page 02 -->

## 01 Design controls & change log

The changes below update the previous IA discussion only. They do not introduce new approved application capabilities.

| Change | IA refinement | Status |
| --- | --- | --- |
| IA-01 | Keep shared application destinations: Home, Channels, Resources, Profile; add Administration only for ADMIN. | Selected IA direction |
| IA-02 | Move Notification Inbox entry from bottom navigation to the primary AppBar. | Selected IA direction |
| IA-03 | Split Resources entry into public link flow and connected-account restricted access flow. | Maps FR-090 to FR-100 |
| IA-04 | Organize Profile into Personal Details, Role Promotion, and Settings. | Selected IA direction |
| IA-05 | Define Settings categories: Appearance, Notifications, Connected Accounts, Security, About, Sign Out. | Mix of approved/proposed |
| IA-06 | Keep Admin as extra role-gated tab, with dedicated routes for approvals, roles, channels, monitoring and audit. | Maps FR-101 to FR-112 |
| IA-07 | Explicitly separate supported routes, future UI ideas, and unresolved business policy. | Scope safeguard |

Scope boundaries

- Only one Flutter application; no desktop or separate web administration interface (FR-001).

- Four system roles: STUDENT, SUB_MANAGEMENT, MANAGEMENT and ADMIN. Channel Owner is a relationship, not a fifth role.

- A route is not permission. Spring Boot remains authoritative for access, ownership and role policies.

- Existing v1.5 requirements remain unchanged. Proposed Settings controls are not automatically in development scope.

- Notifications remain a full inbox screen despite no longer occupying a bottom-navigation tab.

Status labels used in this document APPROVED FR = behavior required by v1.5. IA PROPOSAL = navigation/presentation recommendation that can be refined without changing business logic. FUTURE FR = behavior requiring separate functional approval before implementation.


<!-- Source PDF page 03 -->

## 02 Navigation model

Primary navigation

| User context | Bottom navigation | AppBar |
| --- | --- | --- |
| STUDENT / channel owner | Home · Channels · Resources · Profile | Notifications (bell) |
| SUB_MANAGEMENT | Home · Channels · Resources · Profile | Notifications (bell) |
| MANAGEMENT | Home · Channels · Resources · Profile | Notifications (bell) |
| ADMIN | Home · Channels · Resources · Admin · Profile | Notifications (bell) |

There is no Owner tab. An owner sees contextual membership controls inside the channel they own. Sub-Management and Management navigate the same tabs as students but can access more channels and file actions.

Primary navigation behavior (IA proposal)

- Bottom tabs remain visible on primary destinations; full-screen media playback and sensitive authentication/onboarding surfaces may hide them.

- The AppBar bell opens NOT-01 Notification Inbox as a secondary route; returning restores the previous view when still allowed.

- Each tab may preserve its last list position, search query and navigation stack while the session remains valid.

- The app updates available tabs and actions when the verified role changes. A revoked Admin loses Admin routes immediately after authorization refresh.

- After sign-out or invalidation, authenticated navigation history must not remain accessible.


<!-- Source PDF page 04 -->

## 03 Application hierarchy

This hierarchy defines content grouping and route relationships, not physical file-system directories.

`UniSpace`

`Authentication / Onboarding (outside main tabs)`

`AUTH-01 Session routing`

`AUTH-02 Google / Apple sign-in`

`AUTH-03 Profile + Student ID`

`AUTH-04 Organizational email`

`AUTH-05 Registration code`

`AUTH-06 New-device verification`

`Main Application`

`Home HOME-01 Role-aware overview`

`Channels CH-01..08`

`Discover / Create / Preview / Request status`

`Workspace -> Files / Members / Owner requests`

`Media -> Upload / Player / Document / Details / Trash`

`Resources RES-01..05`

`Saved -> Add link -> Connect Google -> Browse -> View`

`Profile PRO-01..03`

`Personal Details / Role Promotion / Settings`

`Settings categories (five proposed sub-surfaces)`

`Admin* ADM-01..08 (*ADMIN only)`

`Users & roles / Approvals / Channels / Health / Audit`

`AppBar bell NOT-01 Notification Inbox`

Important separation

Channel workspace files use central UniSpace media storage. Resources use external Google Drive access and bookmarks. The two areas should be visually distinguishable and should not imply that Google Drive files were copied into UniSpace.

## 04 Core screen inventory: authentication + channels

IDs are stable IA identifiers for scoped design and development sessions. “All active” means the account must be ACTIVE and have sufficient authorization.

| ID | Screen / surface | Scope / related FR |
| --- | --- | --- |
| AUTH-01 | Splash & session router | All; FR-006, 124–137 |
| AUTH-02 | Welcome / provider sign-in | Signed-out; FR-002, 003, 113 |
| AUTH-03 | Complete student profile | Provisional; FR-114–117, 132–136 |
| AUTH-04 | University email entry | Provisional; FR-118–120 |
| AUTH-05 | Registration code entry | Provisional; FR-121–124 |
| AUTH-06 | New-device verification | ACTIVE, untrusted; FR-127–130 |


<!-- Source PDF page 05 -->

| ID | Screen / surface | Scope / related FR |
| --- | --- | --- |
| HOME-01 | Personalized home | All active; role-aware entry |
| CH-01 | Channel discovery | All active; FR-020, 032–033 |
| CH-02 | Channel preview / details | All active; FR-019–021, 031 |
| CH-03 | Create channel | Eligible STUDENT; FR-014–016 |
| CH-04 | Membership / switch request status | STUDENT; FR-021, 025–028 |
| CH-05 | Channel workspace / library | Authorized members or staff; FR-044, 056–059 |
| CH-06 | Channel members | Authorized users; FR-029, 031 |
| CH-07 | Owner membership requests | Current channel owner; FR-022–024 |
| CH-08 | Channel actions / context | Authorized users; FR-026–030 |

## 05 Core screen inventory: media + resources

| ID | Screen / surface | Scope / related FR |
| --- | --- | --- |
| MED-01 | Upload queue / progress | Authorized uploader; FR-036–055 |
| MED-02 | Full-screen video player | Authorized viewer; FR-060–063 |
| MED-03 | PDF / presentation viewer | Authorized viewer; FR-064–066 |
| MED-04 | File details and actions | Authorized by ownership/role; FR-059, 067–070 |
| MED-05 | Trash and recovery | Authorized owner/staff; FR-071–079 |
| RES-01 | Saved Resources library | All active; FR-090–098 |
| RES-02 | Add Resource (link or connection) | All active; FR-090–093 |
| RES-03 | Connect existing Google account | When restricted resource needs OAuth; FR-091–092; may use system consent |
| RES-04 | Drive folder browser | Authorized resources; FR-094–095 |
| RES-05 | External resource viewer | Authorized files; FR-097–100 |


<!-- Source PDF page 06 -->

## 06 Core screen inventory: Profile + Admin

| ID | Screen / surface | Scope / related FR |
| --- | --- | --- |
| NOT-01 | Notification inbox | All active; FR-080–089 |
| PRO-01 | Personal Details | All active; FR-008–009 |
| PRO-02 | Role Promotion Request | STUDENT; FR-010–011 |
| PRO-03 | Settings | All active; FR-007, 131; other categories proposed |
| ADM-01 | Administration overview | ADMIN; role-specific entry |
| ADM-02 | Role requests | ADMIN; FR-011–013 |
| ADM-03 | User directory | ADMIN; FR-104 |
| ADM-04 | User details / role management | ADMIN; FR-012–013, 112 |
| ADM-05 | Zero-member channel approvals | ADMIN; FR-110 |
| ADM-06 | Channel administration | ADMIN; FR-034–035, 103 |
| ADM-07 | Health, storage & statistics | ADMIN; FR-105–106 |
| ADM-08 | Audit explorer | ADMIN; FR-107–108 |

Inventory count 37 core destination responsibilities: 6 authentication, 1 Home, 8 Channel, 5 Media, 5 Resource, 1 Notification, 3 Profile, and 8 Admin. The five Settings categories listed later are provisional sub-surfaces, not additional approved features.


<!-- Source PDF page 07 -->

## 07 Resources: detailed architecture

The Resources tab is a read-only bridge to teaching files stored in Google Drive. It supports a viewable link and an existing Google account with appropriate authorization (FR-090–100).

Two access paths

| Entry | Proposed route sequence | User experience / constraint |
| --- | --- | --- |
| Anyone-with-link resource | RES-01 → RES-02 Paste link → validate → RES-04 or RES-05 | No extra Google login normally needed when resource is actually accessible to link viewers. Access must still be tested. |
| Restricted resource | RES-01 → RES-02 Add → RES-03 Connect Google → select/authorize → RES-04 / RES-05 | Use an existing Google account; explicitly request necessary Drive access. Do not require new Google account creation. |
| Previously saved resource | RES-01 → RES-04 or RES-05 | Open last selected item; recheck permissions and handle expired access. |

Resource entry structure

`Resources [bottom tab]`

`My Resources (RES-01)`

`Saved folders / Saved individual files`

`Add resource (RES-02)`

`Paste shared link -> Validate -> Save bookmark`

`Connect Google (RES-03) -> Consent -> Select`

`Open saved folder -> RES-04 Folder browser`

`Refresh / Choose supported file -> RES-05 Viewer`

`Open saved file -> RES-05 Viewer`

`Connection status / permission error -> route recovery`

Entry and permission rules

- UniSpace sign-in and Google Drive authorization are distinct. A user who registered with Google does not automatically grant Drive scopes; an Apple sign-in user may connect a Google account.

- A public sharing link must be validated; knowing a Drive URL does not grant permission when the owner restricts the resource.

- Request the narrowest available Google authorization. Selection of arbitrary restricted shared folders may require different scopes and a later integration decision.

- Resource listings and previews must handle removed, forbidden, expired, unsupported-format, and offline states.

- Removing a bookmark removes UniSpace’s saved reference, not the external Google Drive file.

- Whether bookmarks are personal or globally published is still an unresolved requirement; personal list organization is the provisional IA presentation only.


<!-- Source PDF page 08 -->

## 08 Resources: screen responsibilities

| ID | Must show / allow | States / route outcomes |
| --- | --- | --- |
| RES-01 | Saved folder/file entries, Add entry, open/remove bookmark actions (where authorized) | Empty list; loading; stale; inaccessible resource |
| RES-02 | Shared link field and “Connect Google” path; explain permission model | Invalid link; unsupported link; permission required |
| RES-03 | Google account selection/consent surface or platform hand-off; return to resource context | Canceled; denied; expired authorization; reconnect |
| RES-04 | Folder name, accessible child list, refresh, open supported file | Empty; partial access; removed folder; offline |
| RES-05 | Read-only PDF/image/presentation/video viewer where supported | Unsupported preview; loading; access revoked; error |

No change to approved scope Resources remain external and read-only. UniSpace does not upload, edit, delete, or automatically copy Drive content into its central channel media storage (FR-100).


<!-- Source PDF page 09 -->

## 09 Profile: detailed architecture

Profile is one shared tab with three primary sections. Each section can begin as a subpage or content card; precise layout will be resolved in wireframes.

`Profile [bottom tab]`

`PRO-01 Personal Details`

`Name / Student ID / verified organizational email`

`Sign-in provider / role / channel affiliation`

`PRO-02 Role Promotion`

`Request SUB_MANAGEMENT or MANAGEMENT (STUDENT)`

`Request status and result`

`PRO-03 Settings`

`Appearance | Notifications | Connected Accounts`

`Security | About | Sign Out`

PRO-01 Personal Details

| Displayed information | Current scope |
| --- | --- |
| First name / last name | Collected during onboarding (FR-114–115); display after registration |
| Student ID | Submitted and validated during onboarding (FR-116–117) |
| Verified university email | Display verification status / university email used for onboarding |
| Linked sign-in identity | Show Google or Apple sign-in provider (FR-008) |
| System role and channel | Reflect current role and active channel, when any (FR-009) |

Editing limitation The approved FRs permit name editing during initial registration. Post-registration editing of names, Student ID, university email, and account identity has NOT been approved; Personal Details should be read-only until that policy is defined.

PRO-02 Role Promotion

- STUDENT may request SUB_MANAGEMENT or MANAGEMENT (FR-010).

- If a request exists, show pending / approved / rejected status and relevant feedback. The exact resubmission policy remains an interaction detail to decide later.

- ADMIN role assignment is managed by existing Admin accounts, not by regular student promotion requests (FR-112).

- Sub-Management and Management should not see a misleading “request promotion” action unless a later FR explicitly permits it.

## 10 Settings: structure and approval boundaries

This is an IA menu proposal, not approval to build all listed settings. “Current FR” items are already supported; “Future FR” items require separate business-function approval.


<!-- Source PDF page 10 -->

| Settings category | What it can contain | Approval status |
| --- | --- | --- |
| Appearance (SET-01) | Display mode: Follow System / Light / Dark; later seasonal theme selection | Future FR for user-controlled themes |
| Notifications (SET-02) | OS permission status; preferences / opt-out by notification type | Inbox + push approved; preferences future FR |
| Connected Accounts (SET-03) | Google Drive authorization status, reconnect or disconnect controls | Access authorization approved; account-management controls proposed |
| Security (SET-04) | Trusted-device info, revoke devices, session controls | Persistent trusted sessions approved; device-management UI future FR |
| About (SET-05) | App version, information, privacy / help routes | IA content proposal, subject to publishing decisions |
| Sign Out (action) | Terminate current authenticated session; return to Sign-In | Approved FR-007, FR-131 |

Seasonal themes (Thingyan, Halloween, Christmas) should be treated as optional Design System variants layered over the default brand theme. Their selection, scheduling and persistence behavior remain outside approved v1.5 functionality.


<!-- Source PDF page 11 -->

## 11 Role-based experience model

The application uses shared screens wherever possible. Actions are enabled only when the verified role, active channel membership, ownership, and resource permissions allow them.

| Capability | Student | Owner* | Sub-mgmt | Mgmt | Admin |
| --- | --- | --- | --- | --- | --- |
| Main tabs | 4 | 4 | 4 | 4 | 5 |
| Channel discovery | Yes | Yes | Yes | Yes | Yes |
| Create channel | If eligible | If eligible | No | No | No |
| View channel files | Own | Own | All | All | All |
| Upload to channel | Own | Own | All | All | All |
| Approve occupied joins | No | Own | No | No | No |
| Approve empty joins | No | No | No | No | Yes |
| Remove channel member | No | Own | No | No | Not set |
| Manage others’ files | No | No | No | Yes | Yes |
| Rename channel | No | No | No | No | Yes |
| Delete eligible channel | No | No | No | No | If 0/0 |
| Role assignment | No | No | No | No | Yes |
| System monitoring | No | No | No | No | Yes |

*Owner = a channel-specific relationship, not a system role. “If 0/0” = zero members AND zero media files. “Not set” = no approved permission; do not implement. Screen exposure never replaces server-side authorization.

Context-sensitive home behavior

| User state | HOME-01 content priority |
| --- | --- |
| New active student; no channel | Discover channels / Create channel entry |
| Pending channel membership | Request progress and discovery entry (avoid presenting unapproved access) |
| Member of a channel | My Channel, files and relevant activity |
| Channel Owner | Channel workspace plus pending join approvals when relevant |
| Sub-Management | All-channel access and own-upload actions |
| Management | All-channel access, cross-channel media management |
| Admin | Admin overview shortcut; dedicated Admin tab for admin workflows |


<!-- Source PDF page 12 -->

## 12 Administration navigation

`Admin [ADMIN-only bottom tab]`

`ADM-01 Overview`

`Users & Roles`

`ADM-02 Role-promotion requests`

`ADM-03 User directory`

`ADM-04 Role management (including ADMIN)`

`Channels`

`ADM-05 Zero-member join approvals`

`ADM-06 Channel rename / eligible deletion`

`System`

`ADM-07 Health, storage and aggregate metrics`

`ADM-08 Audit logs`

- ADMIN has the five-tab navigation; other roles must not see or navigate to the Admin tab.

- APPROVED: Admin can grant/revoke ADMIN privileges while ensuring at least one active Admin remains; role changes are audited.

- APPROVED: Admin can manually delete a channel only if member count is zero and media-file count is zero. No automatic deletion.

- APPROVED: Admin approves join requests to zero-member channels, whereas the channel owner handles occupied-channel requests.

- NOT APPROVED: Admin removal of channel members in general. Do not infer this from channel management access.


<!-- Source PDF page 13 -->

## 13 Route entry, exit & deep-link policy

| Context | Entry / transition | Exit / access rule |
| --- | --- | --- |
| Launch / session routing | AUTH-01 evaluates session, registration state and device trust | Route to AUTH-02, AUTH-03–05, AUTH-06, or HOME-01 |
| New user onboarding | AUTH-02 → AUTH-03 → AUTH-04 → AUTH-05 | Only after ACTIVE may enter main app |
| Returning trusted user | AUTH-01 → HOME-01 | If session invalid/revoked, reauthenticate |
| Returning untrusted device | AUTH-02 → AUTH-06 → HOME-01 | University email code required before Home |
| Channel discovery | CH-01 → CH-02 → CH-04 or CH-03 | Private CH-05 requires active membership/staff scope |
| Channel workspace | CH-05 → CH-06 / CH-07 / MED-01..05 | Actions verified against ownership and role |
| Resource opening | RES-01/02 → RES-03 if needed → RES-04/05 | On expired Drive permission, reauthorize or show error |
| Notifications | AppBar → NOT-01 → permitted destination | Denied/removed target opens error, never leaks content |
| Admin operations | Admin tab → ADM-01..08 | Only valid ADMIN; denied after revocation |
| Sign out | PRO-03 → Sign Out → AUTH-02 | Clear protected navigation; require valid login |

Back navigation & restoration (IA proposal)

- Return from Notification Inbox or resource viewer to the previous accessible screen rather than always forcing Home.

- Keep user form data when moving back inside onboarding; restore interrupted onboarding after ordinary app process termination in accordance with FR-132–133.

- On uninstall/reinstall during incomplete setup, require fresh provider sign-in and restart onboarding; reconcile provisional record per FR-135–136.

- If the user loses channel membership or staff privileges while viewing a protected route, return to an allowed destination and show a clear access-change message.

- A tapped external/deep link must pass the same authentication, account activation, and authorization checks as normal navigation.

- Prefer an in-place state or short dialog for file rename and similar focused actions rather than creating unnecessary independent screens.


<!-- Source PDF page 14 -->

## 14 States that every route must accommodate

| Area | States / exceptional outcomes to expose |
| --- | --- |
| Authentication | Provider canceled; session expired; device untrusted; provisional registration; disabled account |
| Registration | Missing name; invalid/duplicate Student ID; wrong/expired code; resend cooldown; interrupted/reinstalled |
| Channels | No channel; join pending; owner approval; admin approval for empty channel; full channel; no access |
| Files & uploads | Empty library; queued / transferring / verifying / failed; malware rejection; retry; upload too large |
| Playback & preview | Buffering; unsupported codec / preview; expired signed URL; forbidden or removed media |
| Trash | No trashed files; recoverable; expired / permanently deleted; restore forbidden |
| Resources | No bookmarks; valid public link; Google access required; denied; removed; authorization expired |
| Notifications | Empty / unread / read inbox; stale link; target access revoked |
| Admin | No pending approvals; ineligible channel deletion; last-Admin protection; unavailable health metrics |


<!-- Source PDF page 15 -->

## 15 Traceability to approved Functional Requirements

This summary maps IA destinations to v1.5 FR groups. It is an index for the later user-flow and Flutter implementation work; it does not repeat every individual FR.

| IA area | Relevant v1.5 FR | What UI must cover |
| --- | --- | --- |
| AUTH-01..06 | FR-002–009, 113–137 | Provider sign-in, profile, student ID, university email OTP, trusted sessions and recovery |
| HOME-01 | FR-009, 020, 031, 087; role scopes | Correct contextual entry points, not extra business functions |
| CH-01..08 | FR-014–035, 110–111 | Discovery, membership, approval, ownership, leave/switch, channel administration |
| MED-01..05 | FR-036–079 | Uploads, library, playback, preview, file actions and Trash |
| RES-01..05 | FR-090–100 | Public links, authorized Google files, bookmarks, browsing and read-only viewing |
| NOT-01 | FR-080–089 | Notification inbox, read state and permission-aware destinations |
| PRO-01..03 | FR-007–010, 131 | Account details, role request, sign-out; additional settings marked as proposals |
| ADM-01..08 | FR-011–013, 034–035, 101–112 | Staff approvals, Admin roles, channel management, metrics and audit |

Source documents

- Functional baseline: UniSpace Functional Requirements Specification v1.5, 17 November 2026 (137 approved FRs).

- Visual identity reference: UniSpace Brand Guidelines Board (primary teal #047A98, secondary mint #6CCDB9, Inter typeface).

- Prior IA draft: information architecture and screen mapping developed in the UniSpace planning discussion; revised here to incorporate Resources and Profile/Settings choices.

## 16 Open decisions and approval gates

The items below are specifically excluded from being silently treated as approved. They may affect later detailed flows or UI design.

| Decision group | Currently undecided / future FR |
| --- | --- |
| Channel lifecycle | Owner transfer on departure; ownership of first member joining an empty channel; Trash accounting for channel deletion; pending requests at deletion |
| Resources | Are bookmarks private, globally published, or both? How should arbitrary restricted Google Drive folders be selected and authorized? |
| Profile edits | Can a user change registered name, Student ID, or verified university email after activation? Not in v1.5. |


<!-- Source PDF page 16 -->

| Decision group | Currently undecided / future FR |
| --- | --- |
| Appearance | Are Light/Dark, manual theme selection, seasonal themes and preference persistence to be implemented? |
| Notification preferences | Which push categories can be disabled? What are the global / channel-level opt-in settings? |
| Connected accounts | May users disconnect/reconnect accounts? Can multiple Google accounts be connected? |
| Security management | Will users be able to see and revoke trusted devices? Persistent sessions exist, management UI not yet approved. |
| Other v1.5 gaps | Non-video file limits, nested folders, identity linking, verification policy details, student-directory validation |

## 17 IA acceptance checklist & handoff

- All 137 approved functional requirements have an appropriate entry point or defined system-owned behavior.

- The regular app uses 4 primary tabs; Admin uses 5; Notifications is reachable by AppBar on primary screens.

- One shared set of screens covers all four roles with role-aware actions; Owner remains channel-specific.

- Public Google Drive link and restricted Google authorization paths are distinguishable and recoverable.

- Profile separates personal details, role promotion, and Settings, with unapproved behavior clearly labeled.

- Each route has a permission gate and handles unavailable, empty, loading and error states.

- No unfinished onboarding or untrusted-device state can bypass account activation or required verification.

- Open policy decisions are tracked explicitly rather than being decided implicitly in the Flutter UI.

Next stage after IA approval Stage 3: User Flows. Start with authentication/onboarding, channel join/approval/switch, media upload/playback/Trash, Google Drive public/restricted access, and Admin role management. Then define detailed UI/UX rules, accessibility requirements and visual components.
