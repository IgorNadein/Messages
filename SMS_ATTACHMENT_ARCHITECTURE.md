# SMS attachment transport architecture

Status: implementation specification for protocol version 1.

## Existing boundaries

The app already has two independent SMS paths:

* text SMS, transformed by `SecureOutboundSmsPolicy` and the repaired Double Ratchet;
* binary SMS through `SmsManager.sendDataMessage()` on destination port 8200.

The old binary path inserts every packet into `Conversations`, renders its Base64 form,
and treats every data payload as a key exchange. Attachment frames must therefore be
consumed before that legacy persistence path. Key exchange packets continue through the
existing receiver unchanged.

The secure-session resolver remains the authority. An attachment offer is permitted only
for `SECURE_ESTABLISHED`; pending, changed-identity, and broken sessions fail closed.

## Data SMS budget

3GPP TS 23.040 limits TP-UD to 140 octets. Android uses 16-bit application port
addressing for `sendDataMessage`: UDHL (1), IEI (1), IEDL (1), destination/origin ports
(4), leaving a platform maximum of 133 application bytes. AOSP's GSM implementation
enforces the same `140 - 7` limit.

Protocol v1 deliberately sends no more than 120 application bytes. The 13-byte margin is
for conservative interop with carrier and OEM stacks; it is not treated as additional
protocol storage.

```
TP-UD                                      140 bytes
16-bit port-addressing UDH                  -7 bytes
Android theoretical application maximum    133 bytes
Protocol conservative frame maximum        120 bytes
SmsFrame header                             -26 bytes
AES-GCM authentication tag                 -16 bytes
File bytes per TRANSFER_CHUNK                78 bytes
```

No Base64 is used on the Data SMS path.

## Frame format (network byte order)

```
magic             u16       0x444b ("DK")
protocolVersion   u8        1
packetType        u8
flags             u8
transferId        16 bytes  cryptographically random
chunkIndex        u16
totalChunks       u16
payloadLength     u8
payload           0..94 bytes
```

The fixed header is 26 bytes. Decoding requires an exact length match, known version,
known packet type, valid per-type index bounds, and configured resource limits. Unknown
versions/types are ignored without allocating from their declared values.

Packet types: `TRANSFER_OFFER`, `TRANSFER_ACCEPT`, `TRANSFER_REJECT`,
`TRANSFER_CHUNK`, `TRANSFER_COMPLETE`, `ACK`, `NACK`, `CANCEL`, and `ERROR`.

## Cryptographic construction

1. The sender generates a random 32-byte attachment master key and 16-byte transfer ID.
2. A bounded binary manifest and that key form the transfer context.
3. The complete context is encrypted/authenticated once by the established Double
   Ratchet. The resulting binary envelope is split into bounded `TRANSFER_OFFER` frames.
   No offer is shown until every fragment is present and the complete envelope passes
   Double Ratchet authentication.
4. Data and control packets use AES-256-GCM. HKDF-SHA-256 derives an independent key for
   `(protocol version, transfer ID, direction, packet type, index/sequence)`. Each derived
   key is used once with an all-zero 96-bit nonce. Retransmission reuses the identical
   ciphertext. A different plaintext can never be encrypted under that derived key.
5. The exact 26-byte frame header is AEAD associated data. Changing type, transfer ID,
   index, total count, flags, or payload length invalidates the tag.
6. A SHA-256 digest in the authenticated manifest verifies the fully assembled file in
   addition to per-frame AEAD.

Transfer master keys are stored through Android Keystore-backed encrypted storage and are
removed after the authenticated completion handshake or cancellation cleanup. A key is
never stored beside a Telephony SMS row.

## Reliability and persistence

The encrypted Room database stores one transfer row, compact received/sent/ack bitmaps,
monotonic control sequence numbers, paths, metadata, status, SIM subscription, and
timestamps. Verified plaintext chunks are streamed to a bounded partial file at their
fixed offset; chunks are not accumulated in RAM.

ACK/NACK packets describe a 64-chunk window as `baseIndex + bitCount + bitmap`. ACKs are
aggregated (window completion, pacing interval, or transfer completion), never emitted per
SMS. Duplicate and out-of-order chunks are authenticated and idempotent. Retry sends only
missing chunks.

WorkManager resumes `PREPARING`, `WAITING_ACCEPT`, `SENDING`, `RECEIVING`, `PAUSED`, and
`VERIFYING` transfers after process death or reboot. The SMS transport sends a small
window and advances only from SENT callbacks; rate-limit/no-service failures back off.

## Resource and trust limits

* maximum encoded transfer: 256 KiB;
* warning/extra confirmation: 32 KiB or 500 estimated SMS;
* maximum chunks: 4096;
* maximum authenticated manifest: 1024 bytes;
* maximum filename: 128 UTF-8 bytes after NFC normalization;
* maximum MIME type: 96 ASCII bytes;
* maximum active transfers per contact: 4;
* maximum pending unauthenticated offer contexts globally: 16;
* maximum offer fragments: 32;
* no file allocation before an authenticated offer is accepted.

Filenames are display metadata, never paths. Separators, traversal components, controls,
invalid Unicode, and executable/script extensions are neutralized. Completed files are
never opened automatically.

## Media decisions

Photos use iteratively resized lossy WebP. Android has mandatory WebP encode/decode support
well below this app's API 24 minimum, while AVIF encoding is only mandatory from Android
14. Presets target byte budgets rather than assuming a fixed quality value.

For voice, platform Opus/Ogg is available from Android 10 and provides maintained,
interoperable decoding. Android 7-9 use AMR-NB. Codec2 was evaluated: it reaches
700-3200 bit/s, but most modes are currently maintenance-limited upstream, it requires a
new native ABI surface, and LGPL-2.1 dynamic-linking/compliance work. Protocol v1 treats
voice as an opaque file, so a separately packaged Codec2 encoder can be added without a
wire-format change. The MVP uses Opus at a speech-oriented low bitrate where the device
encoder accepts it, and reports the actual encoded size/SMS count before sending.

## Transport abstraction

Attachment state, framing, AEAD, reliability, and storage depend only on:

```
interface BinaryTransport { suspend fun send(frame, route): SendResult }
```

`SmsBinaryTransport` is the first implementation. A future `AudioModemTransport` can reuse
the protocol without accessing `SmsManager` or changing attachment cryptography.
