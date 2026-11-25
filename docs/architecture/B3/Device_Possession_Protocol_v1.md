# B3-I07 device possession protocol

B3 D10.26 requires a P-256 possession proof bound to the user, flow, key, and operation. This document records the version 1 serialization used by the backend and a portable verification fixture for mobile clients. [Current B3 decisions](UniSpace_B3_Current_Authentication_Decisions.md) · [OpenAPI review candidate](../../drafts/B3/UniSpace_B3_D10_20_Authentication_OpenAPI_3_1_REVIEW.yaml)

## Client sequence

1. Present the restricted flow JWT and start a device challenge. Send the saved `deviceId` when its private key is still available. Send an empty object after key loss or on a fresh installation.
2. A verified registration or device OTP grants enrollment eligibility for the current flow. A device OTP goes to the account's verified university address. Resend through the start operation and use the enrollment challenge ID for OTP verification and key registration.
3. Send a public JWK with exactly `kty=EC`, `crv=P-256`, `x`, and `y`. Each coordinate uses 32 big-endian bytes encoded as unpadded base64url. Optional `deviceDescription` is advisory and is discarded. Key registration returns the proof challenge ID and bytes. A returning trusted device receives them directly from the start operation.
4. Base64url-decode `payloadToSign` once and sign the resulting bytes with ECDSA using SHA-256. Send the unpadded base64url encoding of the ASN.1 DER sequence of signature integers `r` and `s` as `signature` to the proof endpoint. Keep the private key in the platform key store. Use the returned `deviceId` as an opaque handle on future sign-ins.
5. When a flow expires, obtain a fresh flow through provider authentication. Enrollment eligibility is bound to its originating flow. A revoked or legacy device requires OTP and a fresh key. After a proof commits, retries with the same flow are denied. If the response is lost, reauthenticate and use the saved device key.

Android can use `Signature.getInstance("SHA256withECDSA")` with an Android Keystore key. Apple Security can use `SecKeyCreateSignature` with `ecdsaSignatureMessageX962SHA256`. CryptoKit clients should submit `derRepresentation`. These APIs take the challenge bytes as the message. Passing a precomputed hash to a message-signing API would hash twice. The fixture is verified in the JVM. Android and iOS device-level interoperability still needs a client test. [Android Keystore](https://developer.android.com/privacy-and-security/keystore) · [Apple Security signature algorithm](https://developer.apple.com/documentation/security/seckeyalgorithm/ecdsasignaturex962sha256)

## Canonical challenge bytes

Join these eight ASCII fields using one LF byte (`0A`) between fields. The last line has no trailing LF.

| Field | Encoding |
|---|---|
| Protocol | Literal `UniSpace/device-proof/v1` |
| Operation | `ENROLL` or `SIGN_IN` |
| User ID | Unsigned decimal database ID without leading zeros |
| Flow ID | Unsigned decimal database ID without leading zeros |
| Proof ID | Unsigned decimal proof-row ID without the wire prefix |
| Target ID | Enrollment-row ID for `ENROLL`, trusted-device-row ID for `SIGN_IN` |
| Key thumbprint | Unpadded base64url of SHA-256 over the RFC 7638 canonical public JWK |
| Nonce | Unpadded base64url of 32 random bytes |

The server reconstructs the bytes from locked database state and repeats the key and ownership checks before consuming the proof. Proofs last up to two minutes and end sooner if the flow expires. Wire challenge identifiers distinguish enrollment from proof. Clients treat all identifiers as opaque. [RFC 7638](https://www.rfc-editor.org/rfc/rfc7638)

## Public test vector

This vector was signed independently with Python cryptography/OpenSSL. The fixture contains only a public key, a public payload, and a signature. The JVM tests verify the exact serialization, signature, and operation separation.

```json
{"crv":"P-256","kty":"EC","x":"VzffRIs5SGLrb8NWyOJk1BFuMixMa3bRz7br8TDfqi0","y":"mFOahLuEpyL0aPeps_Ix7zB-wtCyUjbeX8xThtCRZB8"}
```

Thumbprint: `U3irnePz9a_NaEBDGfou3_WAT24KKeWMHSbc2SWaRAY`.

```text
payloadToSign
VW5pU3BhY2UvZGV2aWNlLXByb29mL3YxCkVOUk9MTAoxMQoyMgozMwo0NApVM2lybmVQejlhX05hRUJER2ZvdTNfV0FUMjRLS2VXTUhTYmMyU1dhUkFZCkFBRUNBd1FGQmdjSUNRb0xEQTBPRHhBUkVoTVVGUllYR0JrYUd4d2RIaDg

signature
MEQCIDeM53PK3wHLoTBwYB3537CQdBX1ilR70njyy-wFxKPiAiBg-wkAfmzmPzV7EOd4yLwd3BG6SLBvT3f-RjmUnZ4ECA
```

## Initial session credential

Successful proof commits trust or device use, proof consumption, flow completion, and session creation together. The service issues the existing 20-minute access JWT and a 256-bit opaque refresh credential with a 30-day inactivity window. Configure `UNISPACE_AUTH_REFRESH_HMAC_KEY` with a separate Base64-encoded random secret of at least 32 bytes. The stored session digest is lowercase hex of HMAC-SHA-256 over the ASCII literal `unispace:refresh:v1`, one NUL byte, and the exact refresh token. Session expiry leaves device trust intact.

Refresh uses the same digest format. The session ID remains the family ID across rotations. Each rotation stores the spent digest as 32 bytes in `consumed_refresh_tokens`. Every recognized replay revokes that session before the error response and leaves device trust and other families intact. Serialize refresh calls in the client. A lost refresh response requires reauthentication with the trusted device because retrying the spent credential revokes the family.

Sign-out takes either a valid access bearer with an empty body or the current refresh credential in the JSON body with no bearer header. Omit an expired bearer when using the fallback. The server rejects a request that combines the two forms and confirms sign-out only after revoking both the selected session and its device trust. [Current B3 decisions](UniSpace_B3_Current_Authentication_Decisions.md)
