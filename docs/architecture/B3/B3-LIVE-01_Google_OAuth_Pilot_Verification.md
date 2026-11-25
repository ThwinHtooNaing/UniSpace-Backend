# B3-LIVE-01 Google OAuth pilot verification

**Passed on November 25, 2026.** The first two live sign-ins on `emulator-5554` displayed `Result: PASS`, as confirmed by the operator. The latest repeat also displayed `Result: PASS` when inspected. The Flutter pilot reaches that result after HTTP 201 from challenge issuance, HTTP 200 from Google token exchange with a restricted flow credential, and HTTP 200 from `GET /api/v1/me/onboarding` using that credential.

## Confirmed Google configuration

| Control | Verified configuration |
|---|---|
| Audience | `UNISPACE_AUTH_GOOGLE_AUDIENCE` is the Web OAuth client ID used as the Android Credential Manager server client ID. |
| Authorized presenter | The pilot's `UNISPACE_AUTH_GOOGLE_PRESENTERS` contains one entry, the Android OAuth client ID supplied for `com.unispace.pilot.unispace_oauth_pilot`. The installed debug APK signer SHA-1 was `78:D5:CC:E1:BD:7D:00:AA:E5:2C:3D:8D:D6:9B:EF:89:40:FF:4F:F0`. A signature-verified token's `azp` matched the supplied Android client ID. Direct inspection of the Google Cloud registration remained unavailable. |
| Fresh nonce | The backend issues a fresh, five-minute, single-use challenge. Flutter passes the exact server nonce to Credential Manager. The exchange checks the signed nonce against the stored challenge digest before consuming the challenge. |
| Token verification | The existing verifier checks the Google RS256 signature against Google's JWKS, issuer, exact audience, authorized presenter, signed string subject, nonce, and issuance and expiration times. The exchange also checks issuance time relative to the challenge and rechecks expiry when consuming it. |

The initial 401 `AUTH_PROVIDER_INVALID` was reproduced with a pass/fail-only debugger. Signature, issuer, audience, subject, nonce binding and time checks passed. The signed Android presenter was absent from the allowlist. Adding that single presenter to the isolated runtime resolved the failure with the existing verifier. [Verifier](../../../src/main/java/com/unispace/infrastructure/security/GoogleIdTokenVerifier.java#L49) · [Challenge and exchange](../../../src/main/java/com/unispace/identity/application/GoogleSignInService.java#L69)

## Live evidence

| Successful exchange | Challenge issued (UTC) | Challenge consumed (UTC) | Onboarding result |
|---|---|---|---|
| 1 | 00:27:20 | 00:28:03 | Operator confirmed Flutter PASS. |
| 2 | 00:40:36 | 00:40:44 | Operator confirmed Flutter PASS. |
| 3 | 00:41:33 | 00:41:36 | Latest Flutter screen showed PASS, inspected directly. |

The disposable MySQL 8.4 instance recorded three consumed challenges with three distinct nonce digests and three distinct restricted flows. Each flow was tied to its own consumed challenge and had a 15-minute expiry. All three flows belonged to the same provisional account and Google provider identity. The database recorded zero full sessions and zero trusted devices. The readout used aggregate counts and timestamps.

## Remaining production integration

Verification covers Google Sign-In through restricted onboarding on one Android debug build and emulator. Confirm production OAuth registrations for the release Android signing identity and iOS, deployment HTTPS and signing key provisioning, final API contract approval, and live SMTP, device proof, secure storage and session handling. Public challenge rate limiting, authentication audit retention, refresh HMAC key rotation, Apple sign-in and FR-167 remain tracked separately. The recorded B3-I09 result remains 118/118 passing fixture tests. [B3-I09 verification and release checklist](B3-I09_Authentication_Verification.md)

## Pilot shutdown

After the evidence was saved, the pilot Flutter activity and JVM were stopped, the pilot `adb reverse` rule was removed, and Docker auto-removed the disposable MySQL container and its `tmpfs` data. Generated private keys and credentials were removed from the pilot directory. The emulator and persistent services were left running as found.
