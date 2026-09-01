# ONE UI MIGRATION REPORT

Status: in progress. All user-reachable messaging and auxiliary screens now use the Compose-native One UI path. Final sign-off remains gated on the approved physical carrier/two-device matrix and the non-destructive device checks listed below.

## UI architecture

The active path is:

`Compose UI -> Presentation/ViewModel -> MessageService -> persistence/security domain -> SMS/Data-SMS transport`

The new `messages/ui` and `messages/presentation` packages do not call the database, `SmsManager`, Double Ratchet, Data SMS, or attachment packet APIs. Android and crypto details are contained by `AndroidMessageService` and the existing policy/transport layer.

## Audit decisions

### KEEP

- encrypted Room database and existing entities;
- Telephony import/provider integration;
- inbound/outbound SMS receivers and status callbacks;
- fail-closed E2EE policies, Double Ratchet state, signed key exchange;
- attachment ACK/NACK/retry/resume transport;
- gateway and remote-listener backend;
- typed navigation route models retained to preserve existing serialized navigation identities across process death and app updates.

### REWRITE

- inbox, onboarding/import state, conversation timeline and composer;
- search, settings, contact details and new-message screens;
- attachment picker/preview, transfer cards and security-details UI;
- large-screen navigation and motion.

### MOVE TO DOMAIN/SERVICE

- thread/timeline mapping and hiding transport/control packets;
- sending, secure-session request/recovery, SIM selection and draft persistence;
- contact lookup and import progress;
- delivery/security state exposed to UI as enums.

### REMOVED AFTER REGRESSION GATES

- legacy main inbox/chat composables and their direct backend calls after the matching regression/instrumentation scenarios passed;
- obsolete presentation helpers duplicated by `MessageService`;
- dead UI-only crypto controls.

The unreachable legacy gateway list and HTTP/SMTP modal composables were deleted only after their commands were covered by mapper/ViewModel regressions and the replacement passed a Samsung device smoke test. The routing worker/backend remains unchanged.

The legacy remote-listener list/editor/queue composables and presentation ViewModels were deleted after mapper/ViewModel regressions and a Samsung list/editor smoke test. Room entities, RabbitMQ connection service, worker, queue handler and SMS transport were retained. The unreachable Java image Activity, layout and image-over-SMS helper were removed after the active MMS route gained a Compose One UI viewer and regression coverage; no direct `SmsManager` logic was moved into UI.

The legacy routing-history composable and standalone About Activity/layout were deleted after the replacements gained unit/instrumentation coverage and passed a Samsung smoke test. Routing history now subscribes once through a ViewModel/service boundary, safely skips deleted or malformed WorkManager records and keys rows by work ID so one message may target multiple gateways. The existing routing scheduler, worker and inbound SMS hook were not changed.

Notification replies now cross the same `MessageService` and fail-closed outbound policy as foreground sends. The broadcast receiver no longer constructs the legacy conversation ViewModel, validates Android `RemoteInput` and SIM metadata before dispatch, uses `goAsync()` for process-safe asynchronous work, and never retries a blocked secure reply as plaintext.

The migrated secure UI originally collapsed both an outgoing request and an incoming request into `NEGOTIATING`, which removed the old explicit Accept action. The regression is now fixed: domain UI state distinguishes `REQUEST_RECEIVED`, the service maps persisted request mode without exposing crypto to UI, the One UI security sheet opens for an inbound request, and acceptance is dispatched through `MessageService` with the conversation thread and selected SIM. The unreachable legacy secure composable, modal, identity panel, key-exchange row and `SecureConversationViewModel` were then deleted; this removes the last direct `SmsManager`/ratchet manipulation from the old UI package while retaining the existing E2EE backend and persistent state.

The old developer screen was also found to contain three raw Android SMS/MMS provider tools missing from its One UI replacement. Raw export, import and provider clear are now restored through `DeveloperToolsService`; document URIs and import counts cross the presentation boundary, while local-history and system-provider deletion have distinct confirmations. A non-destructive Samsung instrumentation regression verifies that the native tools and confirmation render without executing them. The unreachable legacy developer composable and its isolated generated Material theme were then deleted, leaving no Kotlin source under the old `DefaultSMS/ui` package.

External message entry points were audited after the Compose migration and exposed two reachable regressions: the `ACTION_SEND` branch was empty, and shared text was discarded after recipient selection whenever the calling app had not supplied a SIM. A pure route mapper now validates `sms:`, `smsto:`, `mms:` and `mmsto:` input, preserves Unicode and literal plus signs, applies the Android body-extra precedence and rejects malformed or unrelated schemes. Shared text without an explicit SIM is saved as a draft using the conversation's selected subscription and is never auto-sent; explicit-SIM behavior retains the existing fail-closed send policy. Cold-start and `onNewIntent` use the same one-shot route path, and a real Android share-intent instrumentation regression passed on Samsung without selecting a recipient or sending SMS.

The same entry-point audit found that the manifest had advertised `MainActivity` as the system `RESPOND_VIA_MESSAGE` service since the original app. PackageManager regression reproduced the absence of a real service. The entry now points to a dedicated permission-protected, process-safe Android `Service`; it rejects malformed/blank requests before touching transport, resolves the conversation-selected SIM and delegates exactly once to the same high-level fail-closed send path. Unit tests cover Unicode input, invalid schemes, SIM selection and secure blocking, while Samsung instrumentation verifies the concrete service class and required `SEND_RESPOND_VIA_MESSAGE` permission without sending a carrier SMS.

The first Compose vertical slice also exposed two UI state regressions. Returning from the contacts permission dialog could restart import and erase the final Ready step, and a Paging refresh indicator could cover an already populated inbox. Both failures were reproduced in regression tests before their fixes: lifecycle resume now preserves the active Contacts/Ready pane, import cannot restart beneath either pane, and the full-screen refresh indicator is limited to an empty list. The corrected fake-data screens were re-captured on Samsung Android 16.

The final legacy-UI audit found one more behavioral gap before removal could be accepted: the Compose inbox exposed pin, mute and archive but not destructive conversation deletion. A regression was first added for request, cancellation, exactly-once confirmation and service dispatch. The replacement now presents a separate confirmation dialog and delegates `DELETE` through `InboxViewModel` and `MessageService`. The service preserves the old local-Room-first and optional-system-SMS-second order controlled by the existing setting; an isolated coordinator regression covers both branches and local-failure short-circuiting. Samsung instrumentation exercised cancellation and confirmation against a fake service only, so no user or provider data was deleted.

After the replacement new-message flow, shared-draft handoff and deletion path were covered, the remaining vendor legacy composables and ViewModels were removed. Backend callers now depend on the transport-facing `SmsSender` contract instead of constructing UI ViewModels, and the vendor module no longer declares Compose, Navigation, Media3, Coil, Accompanist, Preference or UI ViewModel dependencies. Only the lightweight route data classes remain in the vendor `ui/navigation` package for serialized navigation compatibility.

## Design system

- custom Compose light/dark theme with restrained blue accent and true black dark background;
- semantic spacing, dimensions, shapes, typography and dedicated Conversation List surface/control/FAB/unread colours;
- edge-to-edge compact bars that explicitly reserve status-bar insets;
- a Samsung Messages-reference Conversation List measured on the connected Galaxy: 10 dp outer list inset, 30 dp surface radius, 64 dp control touch region with a 48 dp visual island, 84 dp minimum rows, 36 dp avatars, content/divider inset alignment, 56 dp circular FAB and floating bottom navigation;
- One UI-like expanded title/viewing zone, avatars, grouped bubbles, composer, security events and bottom sheet;
- dynamic accent remains opt-in and cannot replace security/error semantics.

No Samsung proprietary code or assets are used. No SESL dependency was added to the primary Compose slice; the needed components are small custom implementations, avoiding a large dependency for a limited surface.

## Libraries used

- existing AndroidX Compose, Material 3, Navigation Compose and Paging 3 stack;
- existing Coil Compose integration for contact/media thumbnails;
- existing Room/SQLCipher and application crypto/transport modules behind `MessageService`;
- no new Maven repository, GitHub Packages source or large UI dependency.

## SESL components

SESL and `oneui-design` were used as architecture/visual references only. No proprietary Samsung resource and no SESL runtime component is shipped in this slice.

## Custom components

- compact and expanded One UI bars, search surface, empty states and contact avatars;
- collapsing Conversation List control island, shared rounded list surface, responsive floating navigation and circular compose action;
- grouped text/media/security/transfer bubbles and delivery metadata;
- dual-SIM composer, secure-status surface and security recovery sheet;
- One UI settings cards/dialogs, recipient search and contact-details groups;
- adaptive list/detail layout and voice recording/playback surfaces.

## Screens rewritten and active

- default-SMS onboarding and import progress;
- conversation list;
- inbox folders for archived, drafts, muted and blocked conversations, plus pin/mute/archive/delete actions and mark-all-read; deletion has a separate confirmation;
- conversation timeline;
- text composer with persistent drafts and dual-SIM selector;
- compact secure status, secure request/recovery and safe decryption-failure event.
- secure attachment/file/photo/voice picker, confirmation and high-level transfer cards;
- direct mic-to-record flow with live timer/amplitude waveform, cancel/stop/preview, plus asynchronous voice playback with waveform, duration and progress;
- explicit changed-identity acceptance and forced secure-session renewal.
- safety-number details plus local QR display and contact QR verification through the service boundary;
- message search with transport/ciphertext redaction;
- new-conversation recipient search, direct-number entry and shared-text handoff;
- contact details, SIM choice, security state and block/unblock controls;
- contact media/file/voice transfer counts and notification mute control;
- One UI settings backed by the existing application preferences, including restored access to export, gateway, remote-listener, routing-history, developer and About destinations.
- One UI developer tools behind a high-level service boundary, retaining the sample MMS notification command and requiring explicit confirmation before local-history deletion.
- One UI gateway configuration list and HTTP/SMTP editors through `MessageService`, with password-free list models, validated drafts and confirmed deletion.
- One UI remote-listener and queue management through `MessageService`, with password-free summaries, safe string-based port editing, dual-SIM binding suggestions, explicit delete confirmation and permission-gated activation while retaining the RabbitMQ backend.
- One UI image/video viewer with timeline navigation, share and streamed save through `MessageService`; MMS bubbles with a valid content URI are clickable again.
- One UI routing history with localized worker state, safe orphan handling, gateway access and stable per-work keys.
- Compose-native About screen with localized app name/version and the preserved open-source project link.
- adaptive expanded-width layout with conversation list and active chat side by side.

## Performance

- Paging 3, stable keys and bounded page caches are used for threads and messages;
- inbox metadata is one Room paging projection rather than per-row database queries;
- contact/provider work is forced to `Dispatchers.IO`;
- contact-scoped message search uses a direct Room `PagingSource` query and performs no blocking DAO lookup on the UI thread;
- imported legacy key-exchange/base64 and OEM-decoded binary Data-SMS bodies are classified before presentation; chat, inbox previews and search never render them as user text;
- full-size attachment decoding is not part of the list/timeline path.
- attachment transfers and SMS/security events share one reverse-chronological presentation list while Paging prefetch remains active.

A 50,000-row in-memory Room/PagingSource instrumentation regression loads only the requested initial 100 rows. The shared message policy uses a 50-row page, 100-row initial/prefetch distance and a 500-row maximum cache; stable-key and newest-first ordering are covered across that maximum window. A logical 50,000-message secure Compose conversation also renders and sends through a fake `MessageService` without touching the carrier or user database. A dedicated low-end hardware macrobenchmark is still pending.

## First vertical slice evidence

- [default-SMS onboarding](artifacts/one-ui/oneui_vertical_onboarding.png);
- [populated inbox after onboarding](artifacts/one-ui/oneui_vertical_inbox.png);
- [secure long-conversation timeline and composer](artifacts/one-ui/oneui_vertical_secure_conversation.png).

All captures use synthetic contacts and messages; they contain no user SMS or provider data.

## Accessibility and localization

- minimum 48 dp action targets and semantic icon descriptions are used on the primary path;
- composer, dual-SIM selection and text/media/attachment-transfer timeline cards expose stable TalkBack names/state; direction, content or filename, timestamp and delivery/transfer state no longer depend only on alignment or color;
- SMTP and RabbitMQ password controls share one localized show/hide-password accessibility action;
- Russian and English primary-flow strings are present; the launcher name and new Conversation List controls are localized in Arabic, German, Spanish, French and Polish; other newly added UI strings currently fall back to English;
- the primary list, settings and chat/recovery/composer flow passed a 200% font-scale check on Samsung Android 16; clipped Russian Conversation/Contacts labels were reproduced and are now covered by adaptive 288×72 dp large-text navigation while normal text retains the measured 202×56 dp geometry;
- the inbox mirrors correctly under an Arabic RTL app locale; full translations and a manual TalkBack walkthrough remain pending, while automated primary-chat and credential-control semantics pass on-device.

## Dark mode

System/light/dark modes use the same semantic tokens. Both light and true-black dark primary flows were rendered on Samsung Android 16; the device was restored to its original system dark mode after validation.

## Build and test results

- 169 unit tests across app (136), SMS/MMS (12) and Double Ratchet (21) modules; zero failures;
- application and SMS/MMS instrumentation APKs compile successfully; 18 targeted instrumentation regressions passed on Samsung Android 16 via manual test-APK installation: Samsung-reference Conversation List geometry/actions and its 200% font adaptation, long localized compact-bar actions, About rendering/link dispatch, Android `RemoteInput` parsing with Unicode/incoming/default SIM selection/empty-input rejection, the inbound secure-request Accept action, native developer-tool visibility/confirmation, localized credential visibility actions, real `ACTION_SEND` routing into the Compose recipient picker, the permission-protected `RESPOND_VIA_MESSAGE` service contract, first-run-to-inbox, new-message shared-draft handoff, confirmed conversation deletion, primary-chat TalkBack semantics and secure-conversation vertical slices, and bounded paging from a 50,000-row Room dataset. Destructive actions used fake services; no carrier SMS was sent and no provider data was mutated;
- debug APK and minified release APKs build successfully for arm64-v8a, armeabi-v7a and x86_64;
- `lintDebug` completes. The project report contains 124 errors and 593 warnings, dominated by pre-existing missing translations and retained backend/legacy resources. No lint issue points to the new Conversation List, semantic design tokens, request-state/intent mappers, accessibility semantics, developer service/UI, policy or instrumentation files;
- Samsung Android 16 smoke checks passed for inbox, search, settings, contact details, chat, secure recovery, routing history, About, 200% font, RTL, light/dark and expanded two-pane rendering;
- full Gradle-connected instrumentation was not run on the user-data-bearing installation because that runner uninstalls the tested debug package afterward; only 18 targeted, non-destructive tests were dispatched manually.

## Known limitations

- the stricter live Samsung Messages visual pass is complete for Conversation List; Contacts currently opens the real existing recipient/contact flow, but its dedicated Samsung-reference destination and the corresponding Conversation/Composer/Search/Settings/Attachment/Security visual passes are the next migration stages;
- routing history rendered its empty state on the current device; no synthetic route was queued, so a live HTTP/SMTP status transition still requires a controlled gateway test;
- the new viewer route is covered and builds, but a real MMS media item was not available in the current Samsung history for an end-to-end open/save/share smoke; no test MMS was created;
- listener list/editor rendering is device-verified, but persistence, activation and live RabbitMQ forwarding were deliberately not triggered on the user device;
- physical carrier send/receive tests are not automated and must not be triggered without an approved destination;
- physical tablet/foldable hardware validation, complete motion and a dedicated low-end macrobenchmark remain; fake-data screenshot regressions and a 50,000-message paging/profile test now cover the first vertical slice;
- physical voice capture/playback remains pending explicit microphone-test consent;
- debug and minified release builds succeed; lint still reports pre-existing legacy errors and missing translations for newly added non-Russian locales;
- the full device matrix is not yet signed off; connected instrumentation must use an isolated package/profile because the current runner removes the tested debug package after execution.

## Files changed

The migration is concentrated under `app/src/main/java/com/afkanerd/deku/messages/`, `app/src/main/java/com/afkanerd/deku/attachments/`, `app/src/main/java/com/afkanerd/deku/security/`, their unit/instrumentation tests, localized resources, and the two vendor DAO/transport modules retained by the fork. Detailed behavioral status is tracked in `REGRESSION_MATRIX.md`.
