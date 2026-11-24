# Current B3 authentication decisions

**Review status.** D10.25–D10.28 are approved security and scope decisions supplied for B3-I02. The synchronized OpenAPI 3.1 document is a review candidate. Its field names, response codes and identifier handoffs remain subject to final contract review. [OpenAPI candidate](../../drafts/B3/UniSpace_B3_D10_20_Authentication_OpenAPI_3_1_REVIEW.yaml)

## D10.25 — Current-device sign-out

A valid access JWT identifies the current session. After access expiry, a verified current refresh credential can authorize the same sign-out. Revoke the identified session and current-device trust. The client can clear local credentials and protected navigation even when offline. It may report server-side success only when the server confirms both revocations. Expired or revoked credentials alone cannot establish that outcome.

## D10.26 — Trusted-device verification

Use ECDSA P-256 with SHA-256. Identify a validated public key by its RFC 7638 SHA-256 JWK thumbprint. The server generates canonical challenge bytes and binds each challenge to the user, authentication flow, device key and operation. Each signature challenge expires after two minutes and can be consumed once. Verify the signature over the issued bytes and check all bindings before establishing trust. A device identifier alone grants no access.

Successful registration OTP establishes eligibility to enroll the first key. Possession proof completes enrollment without another OTP. For an existing account on an untrusted device, send the OTP to the previously verified university email before enrolling the key. An existing trusted key can prove possession after provider reauthentication. Trust has its own lifecycle independent of refresh inactivity. [Group A journeys](UniSpace_Stage4_3_Authentication_Backend_B3_1_Alignment_v0_2.md)

## D10.27 — Strict refresh rotation and replay

Rotate the opaque refresh secret on every success and renew the 30-day sliding inactivity period. Reject every recognized reuse of a consumed token and revoke its entire session/token family, including the successor. This applies to immediate duplicates, concurrent requests and retries after a lost response. Other devices keep their unaffected sessions. Commit the revocation before returning a replay result.

D10.27 supersedes D10.15's 15-second no-revocation exception and the corresponding duplicate-window instruction in the historical D10.24 runbook. The earlier static SQL results retain their original scope. [Historical decision audit](UniSpace_B3_Authentication_Approval_Audit_and_UIUX_Alignment_v1.0.md) · [Historical D10.24 runbook](../../drafts/B3/UniSpace_B3_D10_24_Test_Results_and_Runbook.md)

## D10.28 — Provider and mobile scope

Implement Google first. Preserve Apple authentication and secure linking contracts for deferred live integration. Resolve provider identity by verified provider and subject. Linking requires current access authorization and fresh proof of the second identity. Matching email addresses never establish account ownership.

The candidate retains 14 mobile operations on 13 paths. The baseline remains 167 approved FRs and the existing Stage 4 Group A screens and flows. Spring Security must check current server-side authorization. The 20-minute RS256 access JWT and 15-minute restricted flow JWT serve distinct audiences and purposes. A restricted flow carries no refresh token. University email OTP remains single-use, expires after 10 minutes, permits resend after 60 seconds and becomes invalid on the fifth incorrect attempt. Replacement invalidates the prior code. [Group A alignment](UniSpace_Stage4_3_Authentication_Backend_B3_1_Alignment_v0_2.md) · [Functional requirements addendum](../../requirements/UniSpace_Functional_Requirements_Addendum_v1_0.md)

## Remaining review items

- Confirm provider nonce conventions and client audiences for mobile platforms. Apple's Android browser handoff belongs to later live Apple review.
- Verify canonical challenge serialization with Android and iOS test vectors, device-handle storage, and binding to the submitted key through verification. Legacy key re-enrollment and secure-storage fallback need review.
- Determine digest retention and verify atomic family revocation under concurrency. Confirm how the review-only schema represents the stable family and the device proof bindings.
- Review conflicting sign-out credentials, delivery failures, flow recovery, OTP resend details and remaining per-operation error mappings. The candidate already distinguishes confirmed sign-out, recognized replay and an unconfirmed outcome. [Contract review notes](../../drafts/B3/UniSpace_B3_D10_20_OpenAPI_Review_Notes.md)
