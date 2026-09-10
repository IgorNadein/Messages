# Media transport architecture

## Invariants

- The setting selects only the sender's outgoing route. Inbound dispatch is always enabled for
  every supported route, so a recipient does not have to mirror the sender's preference.
- Transport and protection are separate properties. `MMS`, `DATA_SMS`, and `CLOUD_STORAGE` say
  how bytes move; `SECURE` and `UNPROTECTED` say what security guarantees those bytes have.
- A secure transfer is never silently downgraded. If the chosen secure route is unavailable,
  sending stops or uses an explicitly documented secure fallback.
- Every envelope is versioned and contains a random transfer id, part index/count, original
  manifest digest, payload digest, size limits, and expiry. Unknown versions or flags fail closed.
- OAuth credentials, refresh tokens, WebDAV passwords, and provider-internal object credentials
  never leave the sender's device.

## Coordinator boundary

```text
MediaTransferCoordinator
  -> MmsMediaTransport
  -> SmsPacketMediaTransport
  -> CloudLinkMediaTransport

InboundMediaDispatcher
  <- MMS application/vnd.deku.media-part
  <- Data SMS DK frame
  <- versioned cloud-link control envelope
```

The coordinator accepts one logical attachment and produces one logical timeline item regardless
of how many carrier messages or HTTP requests are required. Route implementations report progress
through the same transfer record; the UI never renders raw parts.

## MMS

Ordinary mode sends a conventional MMS attachment. Secure mode first creates a local encrypted
container and sends that container as an MMS binary part. The container uses a random per-file key,
AEAD per part, and binds the manifest plus part index/count as associated data. Its key is delivered
inside the existing authenticated secure channel, never in an MMS text field.

Large files are split into multiple independent MMS messages. Each message contains one bounded
binary part with the same transfer id and digest plus its own index/count. The receiver stores parts
until complete, verifies every AEAD tag and the final digest, then commits the file atomically.
Partial or expired batches remain unavailable to the media viewer and are eventually deleted.

Carrier and OEM MMS gateways can impose size and MIME restrictions. The implementation therefore
needs an operator-tested per-message ceiling, resumable batching, and an explicit failure/fallback
choice instead of assuming that an arbitrarily large MMS is supported.

## Data SMS packets

The existing `DK` binary frame protocol is used. Protected transfers wrap the manifest and file key
with the established Double Ratchet channel. Unprotected transfers intentionally send that context
in the clear; their chunks still use AEAD with the disclosed key to detect accidental corruption,
but this provides **no confidentiality or peer authentication** and must be labelled accordingly.

Inbound handling reads the protocol flag carried by the sender. It never consults the recipient's
outgoing transport preference. Unsupported flags, mixed flags between fragments, a changed SIM,
address, secure identity, digest, or chunk metadata are rejected.

## Internet storage

```text
CloudObjectStore
  upload(source, metadata) -> ObjectLocator(downloadUrl, expiresAt, deleteToken?)
  delete(locator)

Providers:
  Presigned HTTPS / app server
  Yandex Disk adapter
  Google Drive adapter
  WebDAV adapter plus a provider-specific share-link capability
```

For secure chats the app encrypts locally before upload. The provider sees only ciphertext. The
download URL, per-file key, manifest digest, MIME type, size, and expiry travel inside an
authenticated end-to-end encrypted control message. The recipient downloads without receiving the
sender's account token, verifies and decrypts locally, and exposes the file only after atomic commit.

Ordinary chats also upload ciphertext, so the storage provider never receives plaintext. Because
the control offer is not protected by an established chat identity in this mode, its object key is
sent over the carrier control channel and the UI must still label the transfer as unprotected. A
recipient does not need an account with the same provider: the sender creates a public read link or
an app-server download capability that exposes ciphertext only.

Provider notes:

- Google Drive should use the least-privilege `drive.file` scope. An `anyone`/`reader` permission is
  effectively a public link, so secure mode must upload ciphertext.
- Yandex Disk uses its REST API for upload and publish/share operations; authorization remains only
  on the sender device.
- Generic WebDAV handles file transfer but has no standard expiring anonymous share link. A plain
  WebDAV server therefore needs a companion share-link API or a pre-signed HTTPS gateway.

### Own-server contract

The configured HTTPS base URL exposes a deliberately small bearer-authenticated API:

- `GET /v1/capabilities` returns any successful 2xx response after validating the bearer token.
- `PUT /v1/objects/{folder}/{object}` accepts `application/octet-stream` and returns either an
  absolute HTTPS `Location` header or JSON `{"downloadUrl":"https://..."}`. JSON may additionally
  contain an authenticated `deleteUrl`.
- `DELETE` on `deleteUrl` (or the original object URL when omitted) removes the object.
- The download URL is a scoped capability usable without the sender's bearer token. It should be
  unguessable and expiring. Its contents are still an authenticated encrypted container.

## Implementation status

- MMS remains the default. Ordinary MMS uses the carrier-compatible path; secure MMS uses bounded,
  independently authenticated parts and a hidden internal MIME type. A strict transfer-id filename
  pattern is the fallback when a carrier rewrites that MIME type; both live receive and later import
  apply the same filter, so internal ciphertext is not rendered as a user attachment.
- Protected and explicitly unprotected Data SMS packet transfers share one receiver and integrity
  protocol.
- The cloud interface has self-hosted HTTPS, Yandex Disk and Google Drive upload adapters. Objects
  are encrypted before upload; credentials are Keystore-wrapped; the recipient receives no account
  token; completed/cancelled/expired sender transfers attempt remote cleanup.
- Provider authorization currently accepts a token supplied in settings. A production OAuth flow
  still needs provider application identifiers and redirect URIs.
- Upload/download jobs are unique per transfer and cancellable. Coroutine cancellation is not
  converted into a retry, and provider/object identifiers use stable explicit wire codes.
- Operator, provider, process-death and migration validation is intentionally deferred until a test
  phone is connected. Cloud and multipart MMS must not be considered production-verified before
  that pass.
