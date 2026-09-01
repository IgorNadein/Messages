# SECURITY AUDIT RESULTS

Audit date: 2026-08-31  
Application baseline: Deku SMS 0.77.0, commit `2a8b4fb68c5c4a173e142f109fedbc48a6af8f08`  
Fork branch/name: `messages` / «Сообщения»

## Scope and threat model

The review followed every outbound SMS path, the inbound SMS/data-SMS path, the
double-ratchet lifecycle and persistence, SQLCipher/Android Keystore storage,
notification replies, sharing, retries, remote listeners, and Router gateways.

The secure transport remains ordinary SMS/data SMS and requires no Internet.
The mobile operator is assumed able to observe SMS metadata, passively read or
modify transport packets, delay/reorder/drop/replay SMS, and actively replace an
initial key exchange. Endpoint compromise, a malicious OS, and unlocked-device
screen capture are outside the guarantees of application-level E2EE.

## Dependency review

The application pinned
`com.github.smswithoutborders:lib_signal_double_ratchet_java:ad727a57b3`
(`ad727a57b31e1b22a3e41cb89e3270a99dd55a41`, 2026-02-26).

The later upstream `dev` commit
`c4a12f8fcd966f2c52427e512e417b76028362e3` (2026-04-23) is not a compatible
drop-in update. Its diff replaces `States.java`, `Headers.java`, `Protocols.java`,
`Ratchets.java`, `EncryptionController.kt`, and the crypto/storage API. It changes
the state model to Kotlin serialization, introduces header-encryption state, and
changes counters to `UByte`. Existing serialized sessions and the app-facing API
have no migration path in that branch. Updating blindly would discard or corrupt
active sessions and would add a 255-message counter boundary.

Decision: vendor the exact pinned commit as a controlled source module and apply
reviewed, migration-aware fixes. A future move to upstream or libsignal requires a
versioned protocol and state migration, interoperability fixtures, and a staged
rollout.

References: [Deku SMS](https://github.com/deku-messaging/Deku-SMS-Android),
[ratchet library](https://github.com/smswithoutborders/lib_signal_double_ratchet_java),
[Tink Java](https://github.com/tink-crypto/tink-java).

## Critical findings

### C-1: secure conversations could send plaintext through alternate send paths

Notification quick reply constructed a fresh `SecureConversationViewModel` with
`REQUEST_NONE`. Share, retry, remote-listener, and other paths could also use the
plain sender directly. Encryption policy lived in UI state rather than at the
transport boundary.

Status: fixed. Every text/data SMS now crosses one persistent-state policy directly
before Telephony insertion and `android.telephony.SmsManager`. States are
`PLAIN`, `SECURE_PENDING`, `SECURE_ESTABLISHED`, and `SECURE_BROKEN`. Pending,
broken, key-changed, corrupted, and failed-encryption states block sending. The
same boundary blocks MMS/media in a secure conversation. Only a cryptographically
valid key-exchange packet signed by this device may bypass a pending session.

### C-2: `No value for Ns` after R8/minification

Root cause confirmed: Gson serialized public fields by runtime names while the
constructor read literal JSON names (`Ns`, `Nr`, `PN`). R8 could rename the fields,
and no keep rule protected the format.

Status: fixed. Ratchet state, mode state, encrypted storage wrappers, pending-send
envelopes, and identity records now use explicit versioned field names. Legacy
well-formed JSON remains readable. Obfuscated/corrupted legacy state fails closed.
The release APK builds with R8 and without ratchet field keep rules.

### C-3: ratchet state advanced irreversibly before SMS success

Encryption advanced and persisted the sending chain before the Android SMS call.
A failed send followed by ordinary re-encryption skipped a key and could desync
the peers.

Status: fixed. State and an exact pending ciphertext are committed together before
transport. A failed message retains that ciphertext in the encrypted envelope and
in the SQLCipher message row; retry must match and reuse that exact ciphertext.
New messages, including identical plaintext messages, always get distinct ratchet
steps. Successful `SMS_SENT` acknowledgement removes the pending entry. Any state
persistence failure blocks transport.

### C-4: SQLCipher password was zeroed before lazy database open

`getDatabasePassword()` returned the same array that a `finally` block cleared.
After the first local fix, Room still retained a password array that was cleared
before its lazy first open.

Status: fixed in both application databases. `SecretBytes` remains valid only in a
bounded ownership scope. Because SQLCipher's connection pool needs the passphrase
when opening additional connections, Room retains it only for the database lifetime
and clears it when the database closes. Databases accidentally created with a
32-byte zero password are detected, opened with the legacy password, and rekeyed to
the random Keystore-protected password. Device instrumentation verifies non-zero,
stable retrieval, post-close clearing, and concurrent pooled reads.

### C-5: initial key exchange was unauthenticated and downgradeable

The original X25519 public key had no identity signature. An active operator could
substitute it. A legacy unsigned packet could also downgrade a later verified
contact.

Status: fixed for new exchanges. Each installation has a persistent Ed25519 identity
key protected by Android Keystore-backed encrypted storage. Tink 1.23.0 signs the
message type, protocol version, X25519 session key, and Ed25519 public key. The
signature is verified before any ratchet reset or mode transition. New unsigned
legacy key exchanges are rejected; already-established legacy sessions remain
readable but visibly `LEGACY_UNVERIFIED` until renewed.

### C-6: outbound success broadcasts were processed as inbound key exchanges

Inbound notifications and SMS-sent acknowledgements share an action. An outbound
request could be fed back into `receiveRequest`, corrupting its own mode. Also, a
structurally valid but invalidly signed request could reset ratchet state before
signature verification.

Status: fixed. `self` broadcasts only acknowledge pending sends. Inbound processing
is separate, and state reset happens only after successful signature and identity
checks.

### R-1: first-run import failed or appeared to hang on MIUI/HyperOS

The upstream importer performed a synchronous
`Telephony.Threads.getOrCreateThreadId` provider call for every message, depended
on provider-specific `GROUP BY` query arguments, and treated the nullable SMS
`creator` column as non-null. HyperOS exposed `NULL` for that column, so one row
aborted the complete import. The exception was swallowed and the app could still
persist a false "loaded" marker.

Status: fixed. Import preserves the authoritative provider `thread_id`, discovers
threads with a portable one-column query plus in-memory de-duplication, accepts a
null `creator`, skips records without a usable address, logs failures, and records
completion only after success. A versioned marker retries installations affected
by the old false-success state. On the affected Xiaomi device, 4,665 SMS across 99
system threads imported into the encrypted database in 9.1 seconds with no crash.

## High findings

- **H-1 parser length and signed-byte bugs — fixed.** Legacy lengths are unsigned;
  v2 uses a fixed marker/version, `uint16` header length and `uint32` ciphertext
  length. Exact packet length, exact 40-byte header, minimum MAC/ciphertext size,
  maximum size, truncation, trailing bytes, and malformed Base64 are checked.
  Outbound Base64 uses no line wrapping; inbound multipart whitespace is tolerated.
- **H-2 skipped-key lookup used `byte[]` reference identity — fixed.** Restored
  skipped keys are matched in constant content time semantics and removed after use.
- **H-3 MAC comparison was timing-sensitive — fixed.** HMAC-SHA256 verification now
  uses `MessageDigest.isEqual`, authenticates before CBC decryption, rejects truncated
  input, and clears temporary key material.
- **H-4 session keypair was stored in an unordered `StringSet` — fixed.** The new
  ordered versioned blob stores public/private fields explicitly. Legacy migration
  tries both orders and validates that X25519(private) equals the stored public key.
- **H-5 repeated state saves regenerated the wrapping RSA key — fixed.** The existing
  Android Keystore key is reused, preventing a crash between key replacement and
  DataStore commit from making the previous ciphertext unrecoverable.
- **H-6 decrypted secure SMS could be routed to HTTP/SMTP/FTP — fixed.** Routing is
  denied for every non-plain session both when work is queued and again when it
  executes. HTTP gateway URLs must be valid HTTPS. Plaintext JSON/body logging was
  removed.
- **H-7 simultaneous requests could make both peers initiators — fixed.** A
  deterministic unsigned-byte comparison of simultaneous X25519 request keys keeps
  exactly one initiator; the other remains responder. A responder cannot send until
  the initiator's first ratchet message establishes its sending chain.
- **H-8 legacy key-exchange downgrade — fixed.** Unsigned request/accept packets can
  be structurally recognized for migration diagnostics but cannot change a session.

## Medium findings

- Sensitive SMS/image/RMQ/router body logging and `printStackTrace()` calls in the
  relevant paths were removed. Release logging is stripped by R8 rules.
- X25519 key lengths and all-zero/low-order shared secrets are rejected.
- Identity changes create `KEY_CHANGED`, retain the old trusted key, block sending,
  and require explicit acceptance plus a fresh signed key exchange.
- Safety numbers are derived symmetrically from both Ed25519 public keys. The UI
  shows verified/unverified/key-changed status and supports local QR display and
  contact QR scanning.
- Debug builds use an isolated `.audit` application ID so device instrumentation
  does not replace the installed production package.
- The WorkManager foreground service now declares the required `dataSync` type.

## Cryptographic construction review

The retained wire-compatible ratchet uses X25519, HKDF, HMAC-SHA256 chain keys,
AES-256-CBC with PKCS padding, and HMAC-SHA256 in encrypt-then-MAC order. Encryption
and MAC keys plus the deterministic IV are separated from a per-message key through
HKDF. The associated data binds the recipient session public key and serialized
ratchet header. Duplicate messages fail authentication/key lookup; out-of-order
messages use bounded skipped keys (`MAX_SKIP = 100`). DH ratchet steps provide the
intended forward-secrecy and post-compromise-recovery mechanics.

This is still a custom, non-audited Double Ratchet implementation. The changes fix
identified correctness and implementation flaws but are not a substitute for a
formal protocol audit or migration to a mature interoperable libsignal stack.

## Test matrix

Automated coverage added or extended:

1. signed key exchange success and tamper rejection;
2. first and second encrypted messages;
3. Bob-to-Alice reply;
4. 100 alternating messages;
5. serialize/persist/restore after every message (process/reboot simulation);
6. out-of-order and missing-message skipped-key recovery;
7. duplicate/replay rejection;
8. Russian, emoji, combining Unicode, and very long messages;
9. legacy and v2 framing, lengths above 127/255, and multipart whitespace;
10. 20,000 malformed/fuzzed packets without parser exceptions;
11. quick reply, share, retry, gateway, binary/MMS bypass, pending and broken states;
12. exact ciphertext at the Robolectric `android.telephony.SmsManager` boundary for
    single and multipart sends;
13. pending ciphertext persistence and exact retry matching;
14. changed identity, QR round-trip, signature/data tampering, and simultaneous
    request role selection;
15. corrupted/obfuscated/unknown-version ratchet state;
16. SQLCipher password lifetime on a real Android 16 device;
17. concurrent SQLCipher connection-pool reads and application startup policy on a
    real Android device;
18. debug APK, unsigned minified release APK, R8 resource shrinking, debug/release
    lint, and Room schema 4→5 migration generation;
19. OEM-compatible thread discovery, preservation of provider thread IDs, and a
    nullable SMS `creator` regression matching MIUI/HyperOS behavior.

Device result: all 7 ratchet instrumentation tests, the targeted SQLCipher password
test, and the targeted application startup policy test passed on Android API 36.
Five unrelated pre-existing UI tests in the vendored SMS library still fail when
run as one suite because they assume contacts permission, old package names/text,
and outdated Compose nodes; the new security test in that module passes separately.

## Not fixed / requires architectural work

1. Replace the custom ratchet with audited libsignal or another mature protocol.
   This requires a new wire version, state migration, old/new interoperability, and
   substantially more SMS fragmentation work; it cannot be done as a safe dependency
   bump.
2. Existing legacy established sessions are cryptographically confidential against
   passive interception but have no authenticated identity. Users must renew them
   and verify the new safety number/QR.
3. A first-use signed identity is still TOFU. Until the safety number or QR is
   verified out of band, an active operator can substitute its own identity on the
   very first exchange. Self-signing prevents tampering, not first-use trust.
4. Session serialization is protected at rest but in-process byte arrays cannot be
   guaranteed erased by the managed runtime. High-value temporary arrays are cleared
   where practical.
5. Release R8 reports Kotlin-metadata compatibility warnings inherited from the
   project's Kotlin 2.3.x / Android Gradle Plugin pairing. The minified APK completes
   successfully, but the build toolchain should be upgraded together in a dedicated
   compatibility change.
6. No source-level change can hide sender, recipient, timestamps, SMS count, payload
   size, or segmentation from the mobile operator.
7. Android lint still reports 8 pre-existing non-security errors (legacy image
   back handling, one `DiffUtil` equality issue, two Compose resource calls, and
   four ViewModel-construction warnings). Debug/release lint tasks complete because
   the upstream project config has `abortOnError false`. No lint error points to the
   new security, identity, transport-boundary, parser, storage, or Router code.

## Can the mobile operator read secure message content?

**Passive interception: NO**, for a correctly established secure session. The SMS
body presented to the modem is authenticated ciphertext. The operator still sees
all SMS metadata listed above.

**Active MITM during the initial key exchange: YES, before out-of-band verification.**
The first identity is trust-on-first-use, so an active operator can replace the first
identity and session key and sign with the attacker's identity. The UI will show that
identity as unverified.

**After QR/safety-number verification: NO**, assuming uncompromised endpoints and
Ed25519 keys. A substituted identity becomes `KEY_CHANGED`; state reset and sending
are blocked until explicit acceptance and a new signed exchange.

## Main files changed

- `app/src/main/java/com/afkanerd/deku/security/`: persistent session resolver,
  strict codec, and fail-closed outbound decision/policy.
- `app/src/main/java/com/afkanerd/deku/MessagesApplication.kt`: installs the policy
  before activities, receivers, and workers.
- secure conversation UI/components: verification state, safety number, QR display,
  scanner, and key-change acceptance.
- notification receiver and Router/Network paths: inbound/outbound separation,
  pending acknowledgement, secure routing denial, and HTTPS enforcement.
- `app/src/main/java/com/afkanerd/deku/Datastore.kt`: SQLCipher password lifetime and
  zero-password migration.
- `vendor/lib_signal_double_ratchet_java/`: versioned state/envelope storage,
  ratchet/parser fixes, X25519 validation, Tink Ed25519 identities, signed exchange,
  role collision handling, and tests.
- `vendor/lib_smsmms_android/`: single SMS/MMS policy boundary, exact retry transport,
  SQLCipher fix, Android transport test seam, Room schema 5, and tests.
