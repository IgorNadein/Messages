# SMS ATTACHMENT IMPLEMENTATION REPORT

Date: 2026-08-31

## Architecture

The attachment stack is independent of the UI and of `SmsManager`:

```
AttachmentManager / media preparation
        ↓
versioned manifest + Double Ratchet protected transfer context
        ↓
per-transfer AES-GCM sub-session + ACK/NACK reliability
        ↓
BinaryTransport
        ↓
SmsBinaryTransport (port 8200)
```

The old key-exchange Data SMS path remains intact. Attachment frames are intercepted
before the legacy conversation/Telephony insert, so hundreds of ciphertext chunks do not
appear as individual messages or Base64 strings. One Room transfer row drives one bubble.

Room schema 32 persists metadata, status, compact chunk bitmaps, retry state, SIM route,
and paths. WorkManager resumes outgoing work after process death or reboot. Chunks stream
to a pre-sized private partial file only after the user accepts an authenticated offer.

## Protocol

Protocol version 1 uses a strict 26-byte network-order header:

```
magic(2) version(1) type(1) flags(1) transferId(16)
chunkIndex(2) totalChunks(2) payloadLength(1)
```

Packet types are OFFER, ACCEPT, REJECT, CHUNK, COMPLETE, ACK, NACK, CANCEL, and ERROR.
Unknown versions/types, length mismatches, invalid indices, oversized declarations, and
non-canonical metadata are rejected before transfer allocation.

The transfer ID is 128 random bits. The authenticated manifest includes the ID, media
type, normalized filename, MIME type, original/encoded sizes, exact chunk count, SHA-256,
codec, dimensions, sample rate, and duration.

## Usable bytes per SMS

* 3GPP TP-UD maximum: 140 bytes.
* 16-bit application-port UDH: 7 bytes.
* Android theoretical application payload: 133 bytes.
* Conservative protocol frame: 120 bytes.
* Protocol header: 26 bytes.
* AES-GCM tag: 16 bytes.
* File plaintext per CHUNK SMS: **78 bytes**.

The cost preview counts data chunks, an estimated 300-byte authenticated offer, ACCEPT,
and COMPLETE. ACK/retry traffic is variable and is therefore described as approximate.

Examples (data chunks only): 1 B = 1, 100 B = 2, 1 KiB = 14, 10 KiB = 132,
100 KiB = 1,313 SMS.

References: [Android SmsManager](https://developer.android.com/reference/android/telephony/SmsManager.html),
[3GPP/ETSI TS 23.040](https://www.etsi.org/deliver/etsi_ts/123000_123099/123040/14.00.00_60/ts_123040v140000p.pdf),
[AOSP 133-byte port-addressed limit](https://android.googlesource.com/platform/frameworks/base/%2B/767a662ecde33c3979bf02b793d392aca0403162/telephony/java/com/android/internal/telephony/cdma/SmsMessage.java).

## Encryption

The established, signed Double Ratchet session encrypts/authenticates the manifest and a
random 256-bit attachment master key once. Every data/control packet then uses AES-256-GCM.
HKDF-SHA-256 derives a separate one-use packet key from the master key, transfer ID,
direction, packet type, and packet index. The entire frame header is AEAD associated data.
Retransmission reuses identical ciphertext; a derived key is never used for changed
plaintext. The final SHA-256 is checked after all independently authenticated chunks.

Each transfer stores a SHA-256 fingerprint of the remote signed identity key. A session or
identity change pauses/rejects further frames fail-closed. Transfer keys are wrapped using
Android Keystore-backed storage and removed after the authenticated completion handshake,
cancellation, or expiry.

## Reliability

* duplicate and out-of-order chunks are idempotent;
* a compact 64-bit ACK window costs 4–11 plaintext bytes;
* ACKs are aggregated every 16 chunks and after a quiet timeout;
* sender ACK timeouts send an authenticated request for a specific 64-chunk window;
* only absent/unacknowledged chunks are retried;
* completion uses an authenticated two-way handshake;
* one frame is dispatched at a time and the next is scheduled only from the SENT callback;
* default pacing is 1.5 seconds, with bounded exponential failure backoff;
* SENT and carrier-provided DELIVERED callbacks are persisted separately;
* the selected subscription ID is used for every frame.

## Media

Photos support LOW (12 KiB/320 px), MEDIUM (32 KiB/640 px), HIGH (96 KiB/1280 px), and
ORIGINAL presets. Compressed presets iteratively vary WebP quality and dimensions to hit a
byte budget. ORIGINAL is passed through only when it is already within the hard limit.
Android WebP is available below the app's API 24 minimum; AVIF encoding is not a suitable
cross-version baseline. See [Android supported formats](https://developer.android.com/media/platform/supported-formats)
and [Bitmap WebP formats](https://developer.android.com/reference/android/graphics/Bitmap.CompressFormat).

Voice uses Ogg/Opus at a requested 6 kbit/s, 16 kHz on Android 10+ and AMR-NB at a requested
4.75 kbit/s on Android 7–9. A 10-second Opus payload is approximately 7.5 KiB before Ogg
container overhead, about 97 data chunks; the device encoder may choose a different actual
rate, and the UI always estimates from the resulting file. Codec2 was evaluated: its
700–3200 bit/s modes are substantially smaller, but upstream identifies most modes as
maintenance-limited, and shipping it adds a native ABI plus LGPL compliance surface. It is
not bundled in v1; voice is an opaque protocol file so it can be added without a wire change.
References: [Android Opus encoder](https://developer.android.com/reference/android/media/MediaRecorder.AudioEncoder.html),
[Codec2 upstream](https://github.com/drowe67/codec2).

## Security limits

* hard encoded transfer limit: 256 KiB;
* additional warning: 32 KiB or about 500 SMS;
* maximum chunks: 4,096;
* maximum manifest: 1,024 bytes;
* maximum filename/MIME/codec: 128/96/32 bytes;
* maximum active transfers per contact: 4;
* maximum pending unauthenticated offers globally: 16;
* maximum offer fragments: 32;
* no large file allocation before ACCEPT;
* dangerous filenames/extensions are neutralized; files never auto-open.

## Tests and builds

Sixty-three JVM tests across the app and two vendor modules pass with zero failures. The
attachment-specific suite has 23 tests covering 1 byte through 100 KiB, random/zero/0xff
data, Unicode/dangerous filenames, exact overhead, random/reversed/duplicate/missing chunks,
corrupted ciphertext/tag, wrong transfer/index/direction, huge declarations, ACK bitmaps,
and final-digest failure. Debug and minified release APKs both build successfully.

Real two-phone/carrier testing and Android instrumentation were not run in this session
because no ADB device was connected at the verification stage. No test SMS was sent.

## Compatibility limitations

`sendDataMessage()` is an Android API available across the app's API 24–36 range, but Data
SMS routing depends on carrier/SMSC and OEM telephony behavior. The receiver is explicitly
bound to `sms://localhost:8200`; Samsung, Pixel, Xiaomi, multiple carriers, dual SIM, radio
loss, reboot, and Android 7/12/14/15/16 still require real-device matrix validation. There
is deliberately no less-efficient text fallback until testing proves it is necessary.

Carrier delivery reports are optional and may never arrive; queue flow control relies on
the local SENT callback. MediaRecorder may reject the requested Opus bitrate on some OEMs.

## Can the mobile operator recover the file content from saved SMS traffic?

**No, not from saved traffic alone**, assuming endpoint keys remain secret and the
cryptographic implementations are sound. The operator sees numbers, time, number of SMS,
traffic pattern, destination port, and ciphertext.

* Passive interception: provides ciphertext and metadata, not the manifest key or file.
* Active MITM: signed identity keys prevent silent key substitution after identity
  verification. Before users compare/scan the safety number, first-contact key authenticity
  is not externally guaranteed; the app exposes verification and blocks changed identities.
* Verified identity keys: a key change becomes a fail-closed event, including during a
  transfer.
* Compromised endpoint: an unlocked/compromised sender or receiver can access plaintext and
  keys while the attachment is being prepared, viewed, recorded, or received. E2EE cannot
  protect against endpoint compromise.
